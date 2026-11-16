package com.example.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import com.example.data.db.VideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class StorageOverview(
    val totalDeviceBytes: Long,
    val availableDeviceBytes: Long,
    val appVideosBytes: Long,
    val cacheBytes: Long
)

data class ImportTaskProgress(
    val id: String,
    val fileName: String,
    val bytesCopied: Long,
    val totalBytes: Long,
    val status: ImportStatus,
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) (bytesCopied.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f) else 0f

    val percentage: Int
        get() = (progressFraction * 100f).toInt()
}

enum class ImportStatus {
    QUEUED,
    COPYING,
    EXTRACTING_METADATA,
    COMPLETED,
    FAILED
}

data class ExtractedMediaMetadata(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val videoMimeType: String,
    val hasAudio: Boolean,
    val audioMimeType: String?
)

class VideoStorageRepository(private val context: Context) {

    companion object {
        // 50 GB max video file size
        const val MAX_VIDEO_SIZE_BYTES: Long = 50L * 1024L * 1024L * 1024L
        private const val COPY_BUFFER_BYTES = 2 * 1024 * 1024 // 2 MB chunked streaming buffer
        private const val SAFETY_MARGIN_BYTES = 64L * 1024L * 1024L // 64 MB free space buffer
    }

    val videosDir: File by lazy {
        File(context.filesDir, "videos").apply { mkdirs() }
    }

    val thumbnailsDir: File by lazy {
        File(context.filesDir, "thumbnails").apply { mkdirs() }
    }

    private val _importTasks = MutableStateFlow<List<ImportTaskProgress>>(emptyList())
    val importTasks: StateFlow<List<ImportTaskProgress>> = _importTasks.asStateFlow()

    fun getStorageOverview(appVideosBytes: Long): StorageOverview {
        val stat = StatFs(videosDir.absolutePath)
        val total = stat.totalBytes
        val avail = stat.availableBytes
        val cacheSize = computeDirSize(context.cacheDir)
        return StorageOverview(
            totalDeviceBytes = total,
            availableDeviceBytes = avail,
            appVideosBytes = appVideosBytes,
            cacheBytes = cacheSize
        )
    }

    private fun computeDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var sum = 0L
        dir.listFiles()?.forEach { child ->
            sum += computeDirSize(child)
        }
        return sum
    }

    suspend fun clearCacheOnly(): Long = withContext(Dispatchers.IO) {
        val before = computeDirSize(context.cacheDir)
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        before
    }

    fun clearCompletedImportTasks() {
        _importTasks.value = _importTasks.value.filter {
            it.status == ImportStatus.QUEUED ||
                it.status == ImportStatus.COPYING ||
                it.status == ImportStatus.EXTRACTING_METADATA
        }
    }

    suspend fun importVideoFromUri(
        uri: Uri,
        onProgressNotification: ((ImportTaskProgress) -> Unit)? = null
    ): Result<VideoEntity> = withContext(Dispatchers.IO) {
        val taskId = UUID.randomUUID().toString()
        val (displayName, reportedSize) = queryUriNameAndSize(uri)
        val initialTask = ImportTaskProgress(
            id = taskId,
            fileName = displayName,
            bytesCopied = 0L,
            totalBytes = reportedSize,
            status = ImportStatus.QUEUED
        )
        upsertTask(initialTask)

        var destFile: File? = null
        try {
            if (reportedSize > MAX_VIDEO_SIZE_BYTES) {
                throw IllegalArgumentException("Video exceeds maximum supported limit of 50 GB.")
            }

            val availableBytes = StatFs(videosDir.absolutePath).availableBytes
            if (reportedSize > 0 && reportedSize + SAFETY_MARGIN_BYTES > availableBytes) {
                throw IllegalStateException(
                    "Insufficient device storage. Need ${Formatters.formatBytes(reportedSize)}, " +
                        "only ${Formatters.formatBytes(availableBytes)} available."
                )
            }

            val sanitized = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val uniquePrefix = UUID.randomUUID().toString().take(8)
            val file = File(videosDir, "${uniquePrefix}_$sanitized")
            destFile = file

            var copyingTask = initialTask.copy(status = ImportStatus.COPYING)
            upsertTask(copyingTask)
            onProgressNotification?.invoke(copyingTask)

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open selected video file.")

            var bytesCopied = 0L
            var lastUiUpdateMs = 0L
            val buffer = ByteArray(COPY_BUFFER_BYTES)

            inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        bytesCopied += read
                        if (bytesCopied > MAX_VIDEO_SIZE_BYTES) {
                            throw IllegalArgumentException("File exceeded maximum supported 50 GB limit during import.")
                        }
                        output.write(buffer, 0, read)

                        val now = System.currentTimeMillis()
                        if (now - lastUiUpdateMs >= 200L) {
                            lastUiUpdateMs = now
                            copyingTask = copyingTask.copy(
                                bytesCopied = bytesCopied,
                                totalBytes = if (reportedSize > 0) reportedSize else bytesCopied
                            )
                            upsertTask(copyingTask)
                            onProgressNotification?.invoke(copyingTask)
                        }
                    }
                    output.fd.sync()
                }
            }

            val finalSize = file.length()
            if (finalSize <= 0L) {
                throw IllegalStateException("Imported video file is empty.")
            }

            val extractingTask = copyingTask.copy(
                bytesCopied = finalSize,
                totalBytes = finalSize,
                status = ImportStatus.EXTRACTING_METADATA
            )
            upsertTask(extractingTask)
            onProgressNotification?.invoke(extractingTask)

            val meta = extractMetadata(file)
            val thumbPath = generateThumbnail(file, uniquePrefix)
            val titleWithoutExt = displayName.substringBeforeLast('.').ifBlank { displayName }

            val entity = VideoEntity(
                title = titleWithoutExt,
                fileName = displayName,
                filePath = file.absolutePath,
                thumbnailPath = thumbPath,
                sizeBytes = finalSize,
                durationMs = meta.durationMs,
                width = meta.width,
                height = meta.height,
                videoMimeType = meta.videoMimeType,
                hasAudio = meta.hasAudio,
                audioMimeType = meta.audioMimeType
            )

            val doneTask = extractingTask.copy(status = ImportStatus.COMPLETED)
            upsertTask(doneTask)
            onProgressNotification?.invoke(doneTask)

            Result.success(entity)
        } catch (t: Throwable) {
            destFile?.let { if (it.exists()) it.delete() }
            val failedTask = initialTask.copy(
                status = ImportStatus.FAILED,
                errorMessage = t.message ?: "Failed to import video"
            )
            upsertTask(failedTask)
            onProgressNotification?.invoke(failedTask)
            Result.failure(t)
        }
    }

    fun deleteVideoFiles(video: VideoEntity) {
        runCatching {
            val f = File(video.filePath)
            if (f.exists()) f.delete()
            video.thumbnailPath?.let { thumb ->
                val tf = File(thumb)
                if (tf.exists()) tf.delete()
            }
        }
    }

    private fun upsertTask(task: ImportTaskProgress) {
        val current = _importTasks.value.toMutableList()
        val idx = current.indexOfFirst { it.id == task.id }
        if (idx >= 0) current[idx] = task else current.add(0, task)
        _importTasks.value = current
    }

    private fun queryUriNameAndSize(uri: Uri): Pair<String, Long> {
        var name = "video_${System.currentTimeMillis()}.mp4"
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) {
                    cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    size = cursor.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }

    private fun extractMetadata(file: File): ExtractedMediaMetadata {
        var durationMs = 0L
        var width = 1280
        var height = 720
        var videoMime = MediaFormat.MIMETYPE_VIDEO_AVC
        var hasAudio = false
        var audioMime: String? = null

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    videoMime = mime
                    if (format.containsKey(MediaFormat.KEY_WIDTH)) {
                        width = format.getInteger(MediaFormat.KEY_WIDTH)
                    }
                    if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
                        height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    }
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000L
                    }
                } else if (mime.startsWith("audio/") && !hasAudio) {
                    hasAudio = true
                    audioMime = mime
                }
            }
        } catch (_: Throwable) {
            // Fallback to MediaMetadataRetriever below
        } finally {
            runCatching { extractor.release() }
        }

        if (durationMs <= 0L) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                if (w != null && w > 0) width = w
                if (h != null && h > 0) height = h
                val audioFlag = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                if (!hasAudio && audioFlag.equals("yes", ignoreCase = true)) {
                    hasAudio = true
                }
            } catch (_: Throwable) {
            } finally {
                runCatching { retriever.release() }
            }
        }

        return ExtractedMediaMetadata(
            durationMs = durationMs,
            width = width,
            height = height,
            videoMimeType = videoMime,
            hasAudio = hasAudio,
            audioMimeType = audioMime
        )
    }

    private fun generateThumbnail(file: File, uniquePrefix: String): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val targetUs = if (durMs > 1000L) (durMs * 100L) else 0L // 10% of duration in us
            val bitmap = retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: return null
            val outFile = File(thumbnailsDir, "${uniquePrefix}_thumb.jpg")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, fos)
            }
            bitmap.recycle()
            outFile.absolutePath
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
