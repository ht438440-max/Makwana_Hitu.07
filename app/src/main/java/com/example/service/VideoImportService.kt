package com.example.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.MakwanaHituApp
import com.example.R
import com.example.data.storage.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class VideoImportService : Service() {

    companion object {
        private const val EXTRA_URIS = "extra_video_uris"
        private const val NOTIFICATION_ID = 7002

        fun enqueueImports(context: Context, uris: List<Uri>) {
            if (uris.isEmpty()) return
            val intent = Intent(context, VideoImportService::class.java).apply {
                putParcelableArrayListExtra(EXTRA_URIS, ArrayList(uris))
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ConcurrentLinkedQueue<Uri>()
    private val isProcessing = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        @Suppress("DEPRECATION")
        val uris = intent?.getParcelableArrayListExtra<Uri>(EXTRA_URIS)
        if (!uris.isNullOrEmpty()) {
            uris.forEach { queue.add(it) }
            val notif = buildProgressNotification("Preparing video import...", 0, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notif)
            }
            processQueueIfNeeded()
        } else if (!isProcessing.get()) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun processQueueIfNeeded() {
        if (!isProcessing.compareAndSet(false, true)) return
        val app = application as MakwanaHituApp
        val repo = app.broadcastRepository
        val nm = getSystemService(NotificationManager::class.java)

        scope.launch {
            try {
                while (true) {
                    val uri = queue.poll() ?: break
                    repo.importAndSaveVideo(uri) { progress ->
                        val text = "${progress.fileName} • ${Formatters.formatBytes(progress.bytesCopied)}"
                        nm?.notify(
                            NOTIFICATION_ID,
                            buildProgressNotification(
                                text = text,
                                percent = progress.percentage,
                                indeterminate = progress.totalBytes <= 0L
                            )
                        )
                    }
                }
            } finally {
                isProcessing.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun buildProgressNotification(
        text: String,
        percent: Int,
        indeterminate: Boolean
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            10,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MakwanaHituApp.CHANNEL_IMPORT_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Importing Video ($percent%)")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceIn(0, 100), indeterminate)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
