package com.example.data.repository

import android.net.Uri
import com.example.data.db.AppDatabase
import com.example.data.db.RtmpConfigEntity
import com.example.data.db.StreamHistoryEntity
import com.example.data.db.StreamPlatform
import com.example.data.db.VideoEntity
import com.example.data.storage.ImportTaskProgress
import com.example.data.storage.StorageOverview
import com.example.data.storage.VideoStorageRepository
import com.example.security.RtmpCryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class BroadcastRepository(
    private val db: AppDatabase,
    private val storageRepo: VideoStorageRepository,
    private val cryptoManager: RtmpCryptoManager
) {
    private val videoDao = db.videoDao()
    private val rtmpDao = db.rtmpConfigDao()
    private val historyDao = db.streamHistoryDao()

    val videosFlow: Flow<List<VideoEntity>> = videoDao.observeAllVideos()
    val totalUsedBytesFlow: Flow<Long> = videoDao.observeTotalUsedBytes()
    val videoCountFlow: Flow<Int> = videoDao.observeVideoCount()
    val rtmpConfigsFlow: Flow<List<RtmpConfigEntity>> = rtmpDao.observeAllConfigs()
    val streamHistoryFlow: Flow<List<StreamHistoryEntity>> = historyDao.observeAllHistory()
    val streamHistoryCountFlow: Flow<Int> = historyDao.observeHistoryCount()
    val importTasksFlow: StateFlow<List<ImportTaskProgress>> = storageRepo.importTasks

    fun getStorageOverview(appVideosBytes: Long): StorageOverview =
        storageRepo.getStorageOverview(appVideosBytes)

    suspend fun getVideoById(id: Long): VideoEntity? = videoDao.getById(id)

    fun observeVideoById(id: Long): Flow<VideoEntity?> = videoDao.observeById(id)

    suspend fun importAndSaveVideo(
        uri: Uri,
        onProgressNotification: ((ImportTaskProgress) -> Unit)? = null
    ): Result<VideoEntity> {
        val result = storageRepo.importVideoFromUri(uri, onProgressNotification)
        return result.map { entity ->
            val generatedId = videoDao.insert(entity)
            entity.copy(id = generatedId)
        }
    }

    suspend fun renameVideo(id: Long, newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isNotEmpty()) {
            videoDao.rename(id, clean)
        }
    }

    suspend fun deleteVideo(video: VideoEntity) {
        storageRepo.deleteVideoFiles(video)
        videoDao.deleteById(video.id)
    }

    suspend fun deleteVideosByIds(videos: List<VideoEntity>) {
        videos.forEach { video ->
            storageRepo.deleteVideoFiles(video)
            videoDao.deleteById(video.id)
        }
    }

    fun clearCompletedImports() = storageRepo.clearCompletedImportTasks()

    suspend fun clearCache(): Long = storageRepo.clearCacheOnly()

    fun verifyVideoFileExists(video: VideoEntity): Boolean = File(video.filePath).exists()

    // RTMP Configuration with Android Keystore encryption
    suspend fun saveRtmpConfig(
        name: String,
        platform: StreamPlatform,
        rtmpUrl: String,
        rawStreamKey: String
    ): Long {
        val encrypted = cryptoManager.encrypt(rawStreamKey.trim())
        val masked = RtmpCryptoManager.maskStreamKey(rawStreamKey.trim())
        val entity = RtmpConfigEntity(
            name = name.trim().ifBlank { platform.displayName },
            platform = platform.name,
            rtmpUrl = rtmpUrl.trim(),
            encryptedStreamKey = encrypted,
            maskedKeyPreview = masked
        )
        return rtmpDao.insert(entity)
    }

    fun decryptStreamKey(config: RtmpConfigEntity): String =
        cryptoManager.decrypt(config.encryptedStreamKey)

    suspend fun touchRtmpConfigUsed(id: Long) = rtmpDao.touchLastUsed(id)

    suspend fun deleteRtmpConfig(id: Long) = rtmpDao.deleteById(id)

    // Streaming History
    suspend fun startHistoryRecord(entry: StreamHistoryEntity): Long = historyDao.insert(entry)

    suspend fun updateHistoryRecord(
        id: Long,
        durationMs: Long,
        status: String,
        errorMessage: String? = null
    ) = historyDao.updateSession(id, durationMs, status, errorMessage)

    suspend fun deleteHistoryEntry(id: Long) = historyDao.deleteById(id)

    suspend fun clearAllHistory() = historyDao.clearAll()
}
