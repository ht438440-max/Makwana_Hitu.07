package com.example.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.data.db.StreamHistoryEntity
import com.example.data.db.StreamPlatform
import com.example.data.db.StreamStatus
import com.example.data.db.VideoEntity
import com.example.data.prefs.VideoResolution
import com.example.data.repository.BroadcastRepository
import com.example.rtmp.StreamConfig
import com.example.rtmp.StreamTelemetry
import com.example.security.RtmpCryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActiveBroadcastInfo(
    val video: VideoEntity? = null,
    val platform: StreamPlatform = StreamPlatform.YOUTUBE,
    val rtmpUrl: String = "",
    val telemetry: StreamTelemetry = StreamTelemetry(),
    val loopEnabled: Boolean = true,
    val isActiveSession: Boolean = false
)

class BroadcastSessionManager(
    private val context: Context,
    private val repository: BroadcastRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeBroadcast = MutableStateFlow(ActiveBroadcastInfo())
    val activeBroadcast: StateFlow<ActiveBroadcastInfo> = _activeBroadcast.asStateFlow()

    @Volatile
    var pendingStreamConfig: StreamConfig? = null
        private set

    @Volatile
    private var currentHistoryId: Long = -1L

    @Volatile
    var controlActionsListener: StreamControlListener? = null

    interface StreamControlListener {
        fun onPauseRequested()
        fun onResumeRequested()
        fun onStopRequested()
        fun onLoopChanged(enabled: Boolean)
    }

    fun startStream(config: StreamConfig) {
        pendingStreamConfig = config
        _activeBroadcast.value = ActiveBroadcastInfo(
            video = config.video,
            platform = config.platform,
            rtmpUrl = config.rtmpUrl,
            telemetry = StreamTelemetry(
                status = StreamStatus.LIVE,
                connectionMessage = "Initializing stream...",
                activeResolutionLabel = config.resolution.label,
                activeFps = config.fps,
                hasAudioTrack = config.video.hasAudio
            ),
            loopEnabled = config.loopVideo,
            isActiveSession = true
        )

        scope.launch {
            val historyId = repository.startHistoryRecord(
                StreamHistoryEntity(
                    videoId = config.video.id,
                    videoTitle = config.video.title,
                    platform = config.platform.displayName,
                    rtmpUrlMasked = RtmpCryptoManager.maskRtmpUrl(config.rtmpUrl),
                    startedAtMs = System.currentTimeMillis(),
                    durationMs = 0L,
                    resolutionLabel = config.resolution.label,
                    videoBitrateKbps = config.videoBitrateKbps,
                    fps = config.fps,
                    status = StreamStatus.LIVE.name
                )
            )
            currentHistoryId = historyId
        }

        val intent = Intent(context, RtmpStreamingService::class.java).apply {
            action = RtmpStreamingService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun pauseStream() {
        controlActionsListener?.onPauseRequested()
    }

    fun resumeStream() {
        controlActionsListener?.onResumeRequested()
    }

    fun stopStream() {
        controlActionsListener?.onStopRequested()
        val intent = Intent(context, RtmpStreamingService::class.java).apply {
            action = RtmpStreamingService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun toggleLoop(enabled: Boolean) {
        _activeBroadcast.value = _activeBroadcast.value.copy(loopEnabled = enabled)
        controlActionsListener?.onLoopChanged(enabled)
    }

    internal fun updateTelemetry(telemetry: StreamTelemetry) {
        val prev = _activeBroadcast.value
        val active = telemetry.status == StreamStatus.LIVE ||
            telemetry.status == StreamStatus.PAUSED ||
            telemetry.status == StreamStatus.RECONNECTING
        _activeBroadcast.value = prev.copy(
            telemetry = telemetry,
            isActiveSession = active
        )
    }

    internal fun finalizeHistory(status: StreamStatus, durationMs: Long, errorMessage: String?) {
        val hid = currentHistoryId
        if (hid > 0L) {
            scope.launch {
                repository.updateHistoryRecord(
                    id = hid,
                    durationMs = durationMs,
                    status = status.name,
                    errorMessage = errorMessage
                )
            }
        }
        val prev = _activeBroadcast.value
        _activeBroadcast.value = prev.copy(
            isActiveSession = false,
            telemetry = prev.telemetry.copy(
                status = status,
                elapsedMs = durationMs,
                errorMessage = errorMessage
            )
        )
    }
}
