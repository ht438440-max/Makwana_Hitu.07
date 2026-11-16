package com.example.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.MakwanaHituApp
import com.example.R
import com.example.data.db.StreamStatus
import com.example.data.storage.Formatters
import com.example.rtmp.StreamTelemetry
import com.example.rtmp.VideoFileStreamer
import java.util.concurrent.atomic.AtomicBoolean

class RtmpStreamingService : Service(), BroadcastSessionManager.StreamControlListener {

    companion object {
        const val ACTION_START = "com.example.action.START_RTMP_STREAM"
        const val ACTION_PAUSE = "com.example.action.PAUSE_RTMP_STREAM"
        const val ACTION_RESUME = "com.example.action.RESUME_RTMP_STREAM"
        const val ACTION_STOP = "com.example.action.STOP_RTMP_STREAM"
        const val NOTIFICATION_ID = 7001
    }

    private lateinit var sessionManager: BroadcastSessionManager
    private var streamer: VideoFileStreamer? = null
    private var workerThread: Thread? = null
    private val isStreaming = AtomicBoolean(false)

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var lastNotificationUpdateMs = 0L

    override fun onCreate() {
        super.onCreate()
        sessionManager = (application as MakwanaHituApp).broadcastSessionManager
        sessionManager.controlActionsListener = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startStreamingSession()
            ACTION_PAUSE -> onPauseRequested()
            ACTION_RESUME -> onResumeRequested()
            ACTION_STOP -> onStopRequested()
        }
        return START_NOT_STICKY
    }

    private fun startStreamingSession() {
        val config = sessionManager.pendingStreamConfig
        if (config == null) {
            stopSelf()
            return
        }

        // Stop any prior stream cleanly
        streamer?.stop()
        workerThread?.join(1500)

        acquireLocks()
        val initialNotif = buildNotification(
            title = "Starting Live: ${config.video.title}",
            text = "Connecting to ${config.platform.displayName} (Original audio only)",
            isPaused = false
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotif)
        }

        isStreaming.set(true)
        var latestTelemetry = StreamTelemetry(status = StreamStatus.LIVE)

        val engine = VideoFileStreamer(config) { telemetry ->
            latestTelemetry = telemetry
            sessionManager.updateTelemetry(telemetry)

            val now = System.currentTimeMillis()
            if (now - lastNotificationUpdateMs >= 1000L || telemetry.status != StreamStatus.LIVE) {
                lastNotificationUpdateMs = now
                updateNotification(config.video.title, config.platform.displayName, telemetry)
            }
        }
        streamer = engine

        val t = Thread({
            var finalStatus = StreamStatus.STOPPED
            try {
                finalStatus = engine.runBlockingStream()
            } finally {
                isStreaming.set(false)
                releaseLocks()
                sessionManager.finalizeHistory(
                    status = finalStatus,
                    durationMs = latestTelemetry.elapsedMs,
                    errorMessage = latestTelemetry.errorMessage
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }, "RtmpStreamWorker")
        workerThread = t
        t.start()
    }

    override fun onPauseRequested() {
        streamer?.setPaused(true)
        val info = sessionManager.activeBroadcast.value
        val updated = info.telemetry.copy(status = StreamStatus.PAUSED, connectionMessage = "Broadcast Paused")
        sessionManager.updateTelemetry(updated)
        updateNotification(
            info.video?.title ?: "Video Stream",
            info.platform.displayName,
            updated
        )
    }

    override fun onResumeRequested() {
        streamer?.setPaused(false)
        val info = sessionManager.activeBroadcast.value
        val updated = info.telemetry.copy(status = StreamStatus.LIVE, connectionMessage = "Live Broadcast Active")
        sessionManager.updateTelemetry(updated)
        updateNotification(
            info.video?.title ?: "Video Stream",
            info.platform.displayName,
            updated
        )
    }

    override fun onStopRequested() {
        streamer?.stop()
        if (!isStreaming.get()) {
            releaseLocks()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onLoopChanged(enabled: Boolean) {
        streamer?.setLoopEnabled(enabled)
    }

    private fun updateNotification(videoTitle: String, platformName: String, telemetry: StreamTelemetry) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val statusPrefix = when (telemetry.status) {
            StreamStatus.LIVE -> "LIVE"
            StreamStatus.PAUSED -> "PAUSED"
            StreamStatus.RECONNECTING -> "RECONNECTING"
            else -> telemetry.status.label.uppercase()
        }
        val duration = Formatters.formatDurationMs(telemetry.elapsedMs)
        val bitrate = if (telemetry.currentBitrateKbps > 0) " • ${telemetry.currentBitrateKbps} kbps" else ""
        val notif = buildNotification(
            title = "$statusPrefix • $platformName ($duration)",
            text = "$videoTitle$bitrate • Original Audio Only",
            isPaused = telemetry.status == StreamStatus.PAUSED
        )
        nm.notify(NOTIFICATION_ID, notif)
    }

    private fun buildNotification(title: String, text: String, isPaused: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPi = PendingIntent.getService(
            this,
            1,
            Intent(this, RtmpStreamingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val togglePi = PendingIntent.getService(
            this,
            2,
            Intent(this, RtmpStreamingService::class.java).apply {
                action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleLabel = if (isPaused) getString(R.string.action_resume_stream) else getString(R.string.action_pause_stream)

        return NotificationCompat.Builder(this, MakwanaHituApp.CHANNEL_STREAM_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, toggleLabel, togglePi)
            .addAction(0, getString(R.string.action_stop_stream), stopPi)
            .build()
    }

    private fun acquireLocks() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm != null && wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MakwanaHitu:RtmpStreamWakeLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        val wm = applicationContext.getSystemService(WifiManager::class.java)
        if (wm != null && wifiLock == null) {
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wm.createWifiLock(mode, "MakwanaHitu:RtmpWifiLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        runCatching {
            wakeLock?.let { if (it.isHeld) it.release() }
        }
        wakeLock = null
        runCatching {
            wifiLock?.let { if (it.isHeld) it.release() }
        }
        wifiLock = null
    }

    override fun onDestroy() {
        if (sessionManager.controlActionsListener === this) {
            sessionManager.controlActionsListener = null
        }
        streamer?.stop()
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
