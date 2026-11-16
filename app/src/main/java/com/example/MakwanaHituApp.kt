package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.db.AppDatabase
import com.example.data.prefs.StreamingPreferencesRepository
import com.example.data.repository.BroadcastRepository
import com.example.data.storage.VideoStorageRepository
import com.example.security.RtmpCryptoManager
import com.example.service.BroadcastSessionManager

class MakwanaHituApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var storageRepository: VideoStorageRepository
        private set
    lateinit var cryptoManager: RtmpCryptoManager
        private set
    lateinit var broadcastRepository: BroadcastRepository
        private set
    lateinit var preferencesRepository: StreamingPreferencesRepository
        private set
    lateinit var broadcastSessionManager: BroadcastSessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        storageRepository = VideoStorageRepository(this)
        cryptoManager = RtmpCryptoManager()
        broadcastRepository = BroadcastRepository(database, storageRepository, cryptoManager)
        preferencesRepository = StreamingPreferencesRepository(this)
        broadcastSessionManager = BroadcastSessionManager(this, broadcastRepository)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return

            val streamChannel = NotificationChannel(
                CHANNEL_STREAM_ID,
                getString(R.string.notif_channel_stream_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_stream_desc)
                setShowBadge(false)
            }

            val importChannel = NotificationChannel(
                CHANNEL_IMPORT_ID,
                getString(R.string.notif_channel_import_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_import_desc)
                setShowBadge(false)
            }

            nm.createNotificationChannel(streamChannel)
            nm.createNotificationChannel(importChannel)
        }
    }

    companion object {
        const val CHANNEL_STREAM_ID = "makwana_hitu_live_stream_channel"
        const val CHANNEL_IMPORT_ID = "makwana_hitu_video_import_channel"
    }
}
