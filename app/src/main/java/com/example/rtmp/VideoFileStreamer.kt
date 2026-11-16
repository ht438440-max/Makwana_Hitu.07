package com.example.rtmp

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Bundle
import android.os.SystemClock
import com.example.data.db.StreamPlatform
import com.example.data.db.StreamStatus
import com.example.data.db.VideoEntity
import com.example.data.prefs.VideoResolution
import com.example.rtmp.FlvPacker.toByteArrayCopy
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class StreamConfig(
    val video: VideoEntity,
    val platform: StreamPlatform,
    val rtmpUrl: String,
    val streamKey: String,
    val resolution: VideoResolution,
    val videoBitrateKbps: Int,
    val fps: Int,
    val audioBitrateKbps: Int,
    val loopVideo: Boolean,
    val autoReconnect: Boolean,
    val autoQualityAdjustment: Boolean,
    val preferPassthrough: Boolean
)

data class StreamTelemetry(
    val status: StreamStatus = StreamStatus.STOPPED,
    val connectionMessage: String = "Idle",
    val elapsedMs: Long = 0L,
    val currentVideoPositionMs: Long = 0L,
    val currentBitrateKbps: Int = 0,
    val activeResolutionLabel: String = "720p",
    val activeFps: Int = 30,
    val isPassthrough: Boolean = false,
    val hasAudioTrack: Boolean = true,
    val loopCount: Int = 0,
    val reconnectAttempt: Int = 0,
    val errorMessage: String? = null
)

/**
 * Direct file-to-RTMP streaming engine.
 *
 * CRITICAL AUDIO GUARANTEE:
 * - Reads ONLY the embedded audio track inside [StreamConfig.video] via MediaExtractor.
 * - NEVER opens AudioRecord or MediaRecorder (microphone disabled & permission not requested).
 * - NEVER opens AudioTrack or plays audio through phone loudspeaker.
 * - If the source video has no audio track, streams video-only frames.
 */
class VideoFileStreamer(
    private val config: StreamConfig,
    private val onTelemetry: (StreamTelemetry) -> Unit
) {
    private val stopRequested = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    @Volatile
    private var loopEnabled = config.loopVideo

    private val client = RtmpClient()
    private val lastTelemetry = AtomicLong(0L)

    fun setPaused(isPaused: Boolean) {
        paused.set(isPaused)
    }

    fun setLoopEnabled(enabled: Boolean) {
        loopEnabled = enabled
    }

    fun stop() {
        stopRequested.set(true)
        runCatching { client.close() }
    }

    fun runBlockingStream(): StreamStatus {
        val file = File(config.video.filePath)
        if (!file.exists() || !file.canRead()) {
            emitTelemetry(
                StreamTelemetry(
                    status = StreamStatus.FAILED,
                    connectionMessage = "File Missing",
                    errorMessage = "Video file not found on device storage."
                )
            )
            return StreamStatus.FAILED
        }

        val parsedUrl = RtmpUrlParser.parse(config.rtmpUrl).getOrElse { err ->
            emitTelemetry(
                StreamTelemetry(
                    status = StreamStatus.FAILED,
                    connectionMessage = "Invalid RTMP URL",
                    errorMessage = err.message ?: "Invalid RTMP server URL"
                )
            )
            return StreamStatus.FAILED
        }

        var reconnectAttempt = 0
        val maxReconnectAttempts = 8
        val streamOriginWallMs = SystemClock.elapsedRealtime()
        var totalPausedMs = 0L
        var loopCount = 0

        while (!stopRequested.get()) {
            try {
                emitTelemetry(
                    StreamTelemetry(
                        status = if (reconnectAttempt > 0) StreamStatus.RECONNECTING else StreamStatus.LIVE,
                        connectionMessage = if (reconnectAttempt > 0) {
                            "Reconnecting to ${config.platform.displayName} (Attempt $reconnectAttempt)..."
                        } else {
                            "Connecting to ${parsedUrl.host}..."
                        },
                        elapsedMs = (SystemClock.elapsedRealtime() - streamOriginWallMs - totalPausedMs).coerceAtLeast(0L),
                        activeResolutionLabel = config.resolution.label,
                        activeFps = config.fps,
                        reconnectAttempt = reconnectAttempt
                    )
                )

                client.connectAndPublish(parsedUrl, config.streamKey)
                reconnectAttempt = 0

                val pausedDelta = streamLoopSession(
                    file = file,
                    streamOriginWallMs = streamOriginWallMs,
                    initialPausedMs = totalPausedMs,
                    onLoopIncrement = { loopCount++ },
                    getLoopCount = { loopCount }
                )
                totalPausedMs = pausedDelta

                if (stopRequested.get()) {
                    return StreamStatus.STOPPED
                }
                // Completed cleanly without loop
                emitTelemetry(
                    StreamTelemetry(
                        status = StreamStatus.COMPLETED,
                        connectionMessage = "Broadcast Completed",
                        elapsedMs = (SystemClock.elapsedRealtime() - streamOriginWallMs - totalPausedMs).coerceAtLeast(0L),
                        activeResolutionLabel = config.resolution.label,
                        activeFps = config.fps,
                        loopCount = loopCount
                    )
                )
                return StreamStatus.COMPLETED
            } catch (t: Throwable) {
                runCatching { client.close() }
                if (stopRequested.get()) {
                    return StreamStatus.STOPPED
                }
                if (config.autoReconnect && reconnectAttempt < maxReconnectAttempts) {
                    reconnectAttempt++
                    val backoffMs = (2000L * (1 shl (reconnectAttempt - 1).coerceAtMost(4))).coerceAtMost(15000L)
                    emitTelemetry(
                        StreamTelemetry(
                            status = StreamStatus.RECONNECTING,
                            connectionMessage = "Network interrupted — reconnecting in ${backoffMs / 1000}s...",
                            elapsedMs = (SystemClock.elapsedRealtime() - streamOriginWallMs - totalPausedMs).coerceAtLeast(0L),
                            reconnectAttempt = reconnectAttempt,
                            errorMessage = t.message
                        )
                    )
                    sleepInterruptibly(backoffMs)
                } else {
                    emitTelemetry(
                        StreamTelemetry(
                            status = StreamStatus.FAILED,
                            connectionMessage = "Stream Error",
                            elapsedMs = (SystemClock.elapsedRealtime() - streamOriginWallMs - totalPausedMs).coerceAtLeast(0L),
                            errorMessage = t.message ?: "RTMP connection failed."
                        )
                    )
                    return StreamStatus.FAILED
                }
            }
        }
        return StreamStatus.STOPPED
    }

    private fun streamLoopSession(
        file: File,
        streamOriginWallMs: Long,
        initialPausedMs: Long,
        onLoopIncrement: () -> Unit,
        getLoopCount: () -> Int
    ): Long {
        val probe = MediaExtractor()
        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var videoFormat: MediaFormat? = null
        var audioFormat: MediaFormat? = null

        try {
            probe.setDataSource(file.absolutePath)
            for (i in 0 until probe.trackCount) {
                val fmt = probe.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoTrackIndex < 0) {
                    videoTrackIndex = i
                    videoFormat = fmt
                } else if (mime.startsWith("audio/") && audioTrackIndex < 0) {
                    audioTrackIndex = i
                    audioFormat = fmt
                }
            }
        } finally {
            runCatching { probe.release() }
        }

        val vFmt = videoFormat ?: throw IllegalStateException("No video track found in selected file.")
        val vMime = vFmt.getString(MediaFormat.KEY_MIME) ?: ""
        val aMime = audioFormat?.getString(MediaFormat.KEY_MIME)

        val srcWidth = if (vFmt.containsKey(MediaFormat.KEY_WIDTH)) vFmt.getInteger(MediaFormat.KEY_WIDTH) else config.video.width
        val srcHeight = if (vFmt.containsKey(MediaFormat.KEY_HEIGHT)) vFmt.getInteger(MediaFormat.KEY_HEIGHT) else config.video.height
        val hasAudio = audioTrackIndex >= 0 && audioFormat != null

        // Decide Passthrough vs Hardware Transcode
        val videoCompatible = vMime.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) &&
            vFmt.containsKey("csd-0")
        val audioCompatible = !hasAudio ||
            (aMime.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true) && audioFormat!!.containsKey("csd-0"))
        val resolutionMatches = srcHeight <= config.resolution.height + 32
        val usePassthrough = config.preferPassthrough && videoCompatible && audioCompatible && resolutionMatches

        return if (usePassthrough) {
            runPassthroughPipeline(
                file = file,
                videoTrackIndex = videoTrackIndex,
                audioTrackIndex = audioTrackIndex,
                videoFormat = vFmt,
                audioFormat = audioFormat,
                srcWidth = srcWidth,
                srcHeight = srcHeight,
                streamOriginWallMs = streamOriginWallMs,
                initialPausedMs = initialPausedMs,
                onLoopIncrement = onLoopIncrement,
                getLoopCount = getLoopCount
            )
        } else {
            runTranscodePipeline(
                file = file,
                videoTrackIndex = videoTrackIndex,
                audioTrackIndex = audioTrackIndex,
                videoFormat = vFmt,
                audioFormat = audioFormat,
                srcWidth = srcWidth,
                srcHeight = srcHeight,
                streamOriginWallMs = streamOriginWallMs,
                initialPausedMs = initialPausedMs,
                onLoopIncrement = onLoopIncrement,
                getLoopCount = getLoopCount
            )
        }
    }

    private fun runPassthroughPipeline(
        file: File,
        videoTrackIndex: Int,
        audioTrackIndex: Int,
        videoFormat: MediaFormat,
        audioFormat: MediaFormat?,
        srcWidth: Int,
        srcHeight: Int,
        streamOriginWallMs: Long,
        initialPausedMs: Long,
        onLoopIncrement: () -> Unit,
        getLoopCount: () -> Int
    ): Long {
        val hasAudio = audioTrackIndex >= 0 && audioFormat != null
        val sampleRate = audioFormat?.let {
            if (it.containsKey(MediaFormat.KEY_SAMPLE_RATE)) it.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        } ?: 44100

        client.sendMetadata(
            width = srcWidth,
            height = srcHeight,
            videoBitrateKbps = config.videoBitrateKbps,
            fps = config.fps,
            hasAudio = hasAudio,
            audioBitrateKbps = config.audioBitrateKbps,
            sampleRateHz = sampleRate
        )

        val csd0 = videoFormat.getByteBuffer("csd-0")!!.let { it.toByteArrayCopy(it.remaining()) }
        val csd1 = videoFormat.getByteBuffer("csd-1")?.let { it.toByteArrayCopy(it.remaining()) }
        client.sendVideoPacket(FlvPacker.buildAvcSequenceHeaderTag(csd0, csd1), 0L)

        if (hasAudio && audioFormat != null) {
            val acsd0 = audioFormat.getByteBuffer("csd-0")!!.let { it.toByteArrayCopy(it.remaining()) }
            client.sendAudioPacket(FlvPacker.buildAacSequenceHeaderTag(acsd0), 0L)
        }

        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        extractor.selectTrack(videoTrackIndex)
        if (hasAudio) extractor.selectTrack(audioTrackIndex)

        var totalPausedMs = initialPausedMs
        var loopBaseTsMs = 0L
        var maxSampleTsMsInLoop = 0L
        val buffer = ByteBuffer.allocate(2 * 1024 * 1024) // 2 MB reusable packet buffer

        var lastBytesCheck = client.totalBytesSent.get()
        var lastBytesCheckWall = SystemClock.elapsedRealtime()
        var measuredKbps = config.videoBitrateKbps

        try {
            while (!stopRequested.get()) {
                if (paused.get()) {
                    val pauseStart = SystemClock.elapsedRealtime()
                    emitTelemetry(
                        StreamTelemetry(
                            status = StreamStatus.PAUSED,
                            connectionMessage = "Broadcast Paused (Connection Held)",
                            elapsedMs = (pauseStart - streamOriginWallMs - totalPausedMs).coerceAtLeast(0L),
                            currentVideoPositionMs = maxSampleTsMsInLoop,
                            currentBitrateKbps = 0,
                            activeResolutionLabel = "${srcHeight}p (Direct)",
                            activeFps = config.fps,
                            isPassthrough = true,
                            hasAudioTrack = hasAudio,
                            loopCount = getLoopCount()
                        )
                    )
                    while (paused.get() && !stopRequested.get()) {
                        Thread.sleep(100)
                    }
                    totalPausedMs += SystemClock.elapsedRealtime() - pauseStart
                }

                if (stopRequested.get()) break

                val trackIdx = extractor.sampleTrackIndex
                if (trackIdx < 0) {
                    if (loopEnabled) {
                        loopBaseTsMs += maxSampleTsMsInLoop + 33L
                        maxSampleTsMsInLoop = 0L
                        extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                        onLoopIncrement()
                        continue
                    } else {
                        break
                    }
                }

                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    if (loopEnabled) {
                        loopBaseTsMs += maxSampleTsMsInLoop + 33L
                        maxSampleTsMsInLoop = 0L
                        extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                        onLoopIncrement()
                        continue
                    } else {
                        break
                    }
                }

                val samplePtsMs = (extractor.sampleTime / 1000L).coerceAtLeast(0L)
                if (samplePtsMs > maxSampleTsMsInLoop) maxSampleTsMsInLoop = samplePtsMs
                val streamTsMs = loopBaseTsMs + samplePtsMs

                // Real-time pacing against wall clock
                while (!stopRequested.get() && !paused.get()) {
                    val elapsedWallMs = SystemClock.elapsedRealtime() - streamOriginWallMs - totalPausedMs
                    val waitMs = streamTsMs - elapsedWallMs
                    if (waitMs <= 2L) break
                    Thread.sleep(waitMs.coerceAtMost(40L))
                }
                if (stopRequested.get()) break

                val data = buffer.toByteArrayCopy(sampleSize)
                if (trackIdx == videoTrackIndex) {
                    val isKey = (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                    val tag = FlvPacker.buildAvcVideoFrameTag(data, isKey, 0)
                    client.sendVideoPacket(tag, streamTsMs)
                } else if (trackIdx == audioTrackIndex) {
                    val tag = FlvPacker.buildAacFrameTag(data)
                    client.sendAudioPacket(tag, streamTsMs)
                }

                extractor.advance()

                val now = SystemClock.elapsedRealtime()
                if (now - lastBytesCheckWall >= 1000L) {
                    val sent = client.totalBytesSent.get()
                    val deltaBytes = sent - lastBytesCheck
                    val deltaMs = (now - lastBytesCheckWall).coerceAtLeast(1L)
                    measuredKbps = ((deltaBytes * 8L) / deltaMs).toInt().coerceAtLeast(0)
                    lastBytesCheck = sent
                    lastBytesCheckWall = now
                }

                if (now - lastTelemetry.get() >= 500L) {
                    lastTelemetry.set(now)
                    emitTelemetry(
                        StreamTelemetry(
                            status = StreamStatus.LIVE,
                            connectionMessage = "Live • Direct File-to-RTMP",
                            elapsedMs = (now - streamOriginWallMs - totalPausedMs).coerceAtLeast(0L),
                            currentVideoPositionMs = samplePtsMs,
                            currentBitrateKbps = measuredKbps,
                            activeResolutionLabel = "${srcHeight}p (Direct)",
                            activeFps = config.fps,
                            isPassthrough = true,
                            hasAudioTrack = hasAudio,
                            loopCount = getLoopCount()
                        )
                    )
                }
            }
        } finally {
            runCatching { extractor.release() }
        }
        return totalPausedMs
    }

    private fun runTranscodePipeline(
        file: File,
        videoTrackIndex: Int,
        audioTrackIndex: Int,
        videoFormat: MediaFormat,
        audioFormat: MediaFormat?,
        srcWidth: Int,
        srcHeight: Int,
        streamOriginWallMs: Long,
        initialPausedMs: Long,
        onLoopIncrement: () -> Unit,
        getLoopCount: () -> Int
    ): Long {
        val targetWidth = config.resolution.width
        val targetHeight = config.resolution.height
        val hasAudio = audioTrackIndex >= 0 && audioFormat != null

        var activeBitrateBps = config.videoBitrateKbps * 1000
        val vEncoderFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetWidth, targetHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, activeBitrateBps)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }

        val vEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        vEncoder.configure(vEncoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val encoderInputSurface = vEncoder.createInputSurface()
        val surfaceBridge = CodecSurfaceBridge(encoderInputSurface, targetWidth, targetHeight, srcWidth, srcHeight)
        vEncoder.start()

        val vMime = videoFormat.getString(MediaFormat.KEY_MIME)!!
        val vDecoder = MediaCodec.createDecoderByType(vMime)
        vDecoder.configure(videoFormat, surfaceBridge.decoderOutputSurface, null, 0)
        vDecoder.start()

        // Embedded Audio Transcoder (Decoder -> PCM -> AAC Encoder, ZERO speaker/mic!)
        var aDecoder: MediaCodec? = null
        var aEncoder: MediaCodec? = null
        val targetSampleRate = 44100
        val targetChannels = 2
        if (hasAudio && audioFormat != null) {
            val aMime = audioFormat.getString(MediaFormat.KEY_MIME)!!
            val srcRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val srcChannels = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            val dec = MediaCodec.createDecoderByType(aMime)
            dec.configure(audioFormat, null, null, 0)
            dec.start()
            aDecoder = dec

            val aEncFmt = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                srcRate.coerceIn(8000, 48000),
                srcChannels.coerceIn(1, 2)
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, config.audioBitrateKbps * 1000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            enc.configure(aEncFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            aEncoder = enc
        }

        client.sendMetadata(
            width = targetWidth,
            height = targetHeight,
            videoBitrateKbps = config.videoBitrateKbps,
            fps = config.fps,
            hasAudio = hasAudio,
            audioBitrateKbps = config.audioBitrateKbps,
            sampleRateHz = targetSampleRate
        )

        val videoExtractor = MediaExtractor().apply {
            setDataSource(file.absolutePath)
            selectTrack(videoTrackIndex)
        }
        val audioExtractor = if (hasAudio) {
            MediaExtractor().apply {
                setDataSource(file.absolutePath)
                selectTrack(audioTrackIndex)
            }
        } else null

        var totalPausedMs = initialPausedMs
        var videoHeaderSent = false
        var audioHeaderSent = false
        var loopOffsetUs = 0L
        var lastVideoPtsUs = 0L
        var lastAudioPtsUs = 0L

        val vInfo = MediaCodec.BufferInfo()
        val vEncInfo = MediaCodec.BufferInfo()
        val aInfo = MediaCodec.BufferInfo()
        val aEncInfo = MediaCodec.BufferInfo()

        var lastBytesCheck = client.totalBytesSent.get()
        var lastBytesCheckWall = SystemClock.elapsedRealtime()
        var measuredKbps = config.videoBitrateKbps
        var lastAdaptiveAdjustMs = 0L

        try {
            while (!stopRequested.get()) {
                if (paused.get()) {
                    val pauseStart = SystemClock.elapsedRealtime()
                    emitTelemetry(
                        StreamTelemetry(
                            status = StreamStatus.PAUSED,
                            connectionMessage = "Broadcast Paused",
                            elapsedMs = (pauseStart - streamOriginWallMs - totalPausedMs).coerceAtLeast(0L),
                            currentVideoPositionMs = lastVideoPtsUs / 1000L,
                            currentBitrateKbps = 0,
                            activeResolutionLabel = config.resolution.label,
                            activeFps = config.fps,
                            isPassthrough = false,
                            hasAudioTrack = hasAudio,
                            loopCount = getLoopCount()
                        )
                    )
                    while (paused.get() && !stopRequested.get()) {
                        Thread.sleep(100)
                    }
                    totalPausedMs += SystemClock.elapsedRealtime() - pauseStart
                }
                if (stopRequested.get()) break

                // 1. Feed video decoder
                val vInIdx = vDecoder.dequeueInputBuffer(5000)
                if (vInIdx >= 0) {
                    val inBuf = vDecoder.getInputBuffer(vInIdx)!!
                    val sz = videoExtractor.readSampleData(inBuf, 0)
                    if (sz < 0) {
                        if (loopEnabled) {
                            loopOffsetUs += maxOf(lastVideoPtsUs, lastAudioPtsUs) + 33_333L
                            lastVideoPtsUs = 0L
                            lastAudioPtsUs = 0L
                            videoExtractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                            audioExtractor?.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                            onLoopIncrement()
                            vDecoder.queueInputBuffer(vInIdx, 0, 0, 0L, 0)
                        } else {
                            vDecoder.queueInputBuffer(vInIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    } else {
                        val pts = videoExtractor.sampleTime.coerceAtLeast(0L)
                        lastVideoPtsUs = pts
                        vDecoder.queueInputBuffer(vInIdx, 0, sz, loopOffsetUs + pts, 0)
                        videoExtractor.advance()
                    }
                }

                // 2. Drain video decoder -> render to GL surface -> pace in real-time
                val vOutIdx = vDecoder.dequeueOutputBuffer(vInfo, 5000)
                if (vOutIdx >= 0) {
                    val doRender = vInfo.size > 0
                    val streamPtsMs = (vInfo.presentationTimeUs / 1000L).coerceAtLeast(0L)
                    if (doRender) {
                        while (!stopRequested.get() && !paused.get()) {
                            val elapsedWallMs = SystemClock.elapsedRealtime() - streamOriginWallMs - totalPausedMs
                            val waitMs = streamPtsMs - elapsedWallMs
                            if (waitMs <= 2L) break
                            Thread.sleep(waitMs.coerceAtMost(35L))
                        }
                    }
                    vDecoder.releaseOutputBuffer(vOutIdx, doRender)
                    if (doRender) {
                        surfaceBridge.awaitAndDrawFrame(vInfo.presentationTimeUs * 1000L)
                    }
                    if ((vInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        vEncoder.signalEndOfInputStream()
                    }
                }

                // 3. Drain H.264 encoder -> RTMP
                while (!stopRequested.get()) {
                    val encIdx = vEncoder.dequeueOutputBuffer(vEncInfo, 0)
                    if (encIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val fmt = vEncoder.outputFormat
                        val csd0 = fmt.getByteBuffer("csd-0")?.let { it.toByteArrayCopy(it.remaining()) }
                        val csd1 = fmt.getByteBuffer("csd-1")?.let { it.toByteArrayCopy(it.remaining()) }
                        if (csd0 != null) {
                            client.sendVideoPacket(FlvPacker.buildAvcSequenceHeaderTag(csd0, csd1), 0L)
                            videoHeaderSent = true
                        }
                    } else if (encIdx >= 0) {
                        val outBuf = vEncoder.getOutputBuffer(encIdx)
                        if (outBuf != null && vEncInfo.size > 0) {
                            val bytes = outBuf.toByteArrayCopy(vEncInfo.size, vEncInfo.offset)
                            if ((vEncInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                if (!videoHeaderSent) {
                                    client.sendVideoPacket(FlvPacker.buildAvcSequenceHeaderTag(bytes, null), 0L)
                                    videoHeaderSent = true
                                }
                            } else if (videoHeaderSent) {
                                val isKey = (vEncInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                                val tsMs = (vEncInfo.presentationTimeUs / 1000L).coerceAtLeast(0L)
                                client.sendVideoPacket(FlvPacker.buildAvcVideoFrameTag(bytes, isKey, 0), tsMs)
                            }
                        }
                        vEncoder.releaseOutputBuffer(encIdx, false)
                        if ((vEncInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            return totalPausedMs
                        }
                    } else {
                        break
                    }
                }

                // 4. Embedded Audio Decode -> AAC Encode -> RTMP (NO mic, NO speaker)
                if (hasAudio && audioExtractor != null && aDecoder != null && aEncoder != null) {
                    val aInIdx = aDecoder.dequeueInputBuffer(2000)
                    if (aInIdx >= 0) {
                        val aInBuf = aDecoder.getInputBuffer(aInIdx)!!
                        val sz = audioExtractor.readSampleData(aInBuf, 0)
                        if (sz < 0) {
                            if (!loopEnabled) {
                                aDecoder.queueInputBuffer(aInIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            } else {
                                aDecoder.queueInputBuffer(aInIdx, 0, 0, 0L, 0)
                            }
                        } else {
                            val pts = audioExtractor.sampleTime.coerceAtLeast(0L)
                            lastAudioPtsUs = pts
                            aDecoder.queueInputBuffer(aInIdx, 0, sz, loopOffsetUs + pts, 0)
                            audioExtractor.advance()
                        }
                    }

                    val aDecOut = aDecoder.dequeueOutputBuffer(aInfo, 2000)
                    if (aDecOut >= 0) {
                        val pcmBuf = aDecoder.getOutputBuffer(aDecOut)
                        if (pcmBuf != null && aInfo.size > 0) {
                            val pcmData = pcmBuf.toByteArrayCopy(aInfo.size, aInfo.offset)
                            val encInIdx = aEncoder.dequeueInputBuffer(5000)
                            if (encInIdx >= 0) {
                                val encInBuf = aEncoder.getInputBuffer(encInIdx)!!
                                encInBuf.clear()
                                val writeLen = pcmData.size.coerceAtMost(encInBuf.remaining())
                                encInBuf.put(pcmData, 0, writeLen)
                                aEncoder.queueInputBuffer(encInIdx, 0, writeLen, aInfo.presentationTimeUs, 0)
                            }
                        }
                        aDecoder.releaseOutputBuffer(aDecOut, false)
                    }

                    while (!stopRequested.get()) {
                        val aEncOut = aEncoder.dequeueOutputBuffer(aEncInfo, 0)
                        if (aEncOut == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            val fmt = aEncoder.outputFormat
                            val csd0 = fmt.getByteBuffer("csd-0")?.let { it.toByteArrayCopy(it.remaining()) }
                            if (csd0 != null) {
                                client.sendAudioPacket(FlvPacker.buildAacSequenceHeaderTag(csd0), 0L)
                                audioHeaderSent = true
                            }
                        } else if (aEncOut >= 0) {
                            val aOutBuf = aEncoder.getOutputBuffer(aEncOut)
                            if (aOutBuf != null && aEncInfo.size > 0) {
                                val bytes = aOutBuf.toByteArrayCopy(aEncInfo.size, aEncInfo.offset)
                                if ((aEncInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    if (!audioHeaderSent) {
                                        client.sendAudioPacket(FlvPacker.buildAacSequenceHeaderTag(bytes), 0L)
                                        audioHeaderSent = true
                                    }
                                } else if (audioHeaderSent) {
                                    val tsMs = (aEncInfo.presentationTimeUs / 1000L).coerceAtLeast(0L)
                                    client.sendAudioPacket(FlvPacker.buildAacFrameTag(bytes), tsMs)
                                }
                            }
                            aEncoder.releaseOutputBuffer(aEncOut, false)
                        } else {
                            break
                        }
                    }
                }

                // 5. Telemetry & Adaptive Quality Adjustment
                val now = SystemClock.elapsedRealtime()
                if (now - lastBytesCheckWall >= 1000L) {
                    val sent = client.totalBytesSent.get()
                    val deltaBytes = sent - lastBytesCheck
                    val deltaMs = (now - lastBytesCheckWall).coerceAtLeast(1L)
                    measuredKbps = ((deltaBytes * 8L) / deltaMs).toInt().coerceAtLeast(0)
                    lastBytesCheck = sent
                    lastBytesCheckWall = now

                    if (config.autoQualityAdjustment && now - lastAdaptiveAdjustMs >= 5000L) {
                        lastAdaptiveAdjustMs = now
                        val targetBps = config.videoBitrateKbps * 1000
                        if (measuredKbps in 100 until (config.videoBitrateKbps * 65 / 100)) {
                            activeBitrateBps = (activeBitrateBps * 80 / 100).coerceAtLeast(500_000)
                            val b = Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, activeBitrateBps) }
                            runCatching { vEncoder.setParameters(b) }
                        } else if (measuredKbps >= config.videoBitrateKbps * 90 / 100 && activeBitrateBps < targetBps) {
                            activeBitrateBps = (activeBitrateBps * 110 / 100).coerceAtMost(targetBps)
                            val b = Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, activeBitrateBps) }
                            runCatching { vEncoder.setParameters(b) }
                        }
                    }
                }

                if (now - lastTelemetry.get() >= 500L) {
                    lastTelemetry.set(now)
                    emitTelemetry(
                        StreamTelemetry(
                            status = StreamStatus.LIVE,
                            connectionMessage = "Live • Transcoded (${config.resolution.label})",
                            elapsedMs = (now - streamOriginWallMs - totalPausedMs).coerceAtLeast(0L),
                            currentVideoPositionMs = lastVideoPtsUs / 1000L,
                            currentBitrateKbps = measuredKbps,
                            activeResolutionLabel = config.resolution.label,
                            activeFps = config.fps,
                            isPassthrough = false,
                            hasAudioTrack = hasAudio,
                            loopCount = getLoopCount()
                        )
                    )
                }
            }
        } finally {
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor?.release() }
            runCatching { vDecoder.stop(); vDecoder.release() }
            runCatching { vEncoder.stop(); vEncoder.release() }
            runCatching { surfaceBridge.release() }
            runCatching { aDecoder?.stop(); aDecoder?.release() }
            runCatching { aEncoder?.stop(); aEncoder?.release() }
        }
        return totalPausedMs
    }

    private fun sleepInterruptibly(durationMs: Long) {
        val end = SystemClock.elapsedRealtime() + durationMs
        while (!stopRequested.get() && SystemClock.elapsedRealtime() < end) {
            Thread.sleep(150)
        }
    }

    private fun emitTelemetry(telemetry: StreamTelemetry) {
        onTelemetry(telemetry)
    }
}
