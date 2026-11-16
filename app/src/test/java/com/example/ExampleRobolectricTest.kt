package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.RtmpConfigEntity
import com.example.data.db.StreamHistoryEntity
import com.example.data.db.StreamPlatform
import com.example.data.db.StreamStatus
import com.example.data.db.VideoEntity
import com.example.data.storage.Formatters
import com.example.data.storage.VideoStorageRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `app name matches Makwana_Hitu_07`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Makwana_Hitu.07", appName)
    }

    @Test
    fun `manifest does not request microphone or broad storage permissions`() {
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val perms = pkgInfo.requestedPermissions?.toSet() ?: emptySet()
        assertFalse("Microphone permission must not be requested", perms.contains(Manifest.permission.RECORD_AUDIO))
        assertFalse("Broad storage permission must not be requested", perms.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
        assertTrue("INTERNET permission is required", perms.contains(Manifest.permission.INTERNET))
        assertTrue("FOREGROUND_SERVICE permission is required", perms.contains(Manifest.permission.FOREGROUND_SERVICE))
        assertTrue("WAKE_LOCK permission is required", perms.contains(Manifest.permission.WAKE_LOCK))
    }

    @Test
    fun `video limit is 50 GB and formatters work accurately`() {
        assertEquals(50L * 1024L * 1024L * 1024L, VideoStorageRepository.MAX_VIDEO_SIZE_BYTES)
        assertEquals("50.00 GB", Formatters.formatBytes(VideoStorageRepository.MAX_VIDEO_SIZE_BYTES))
        assertEquals("01:05", Formatters.formatDurationMs(65_000L))
        assertEquals("1:01:05", Formatters.formatDurationMs(3_665_000L))
    }

    @Test
    fun `room persistence stores videos, rtmp configs, and streaming history`() = runTest {
        val videoId = db.videoDao().insert(
            VideoEntity(
                title = "Concert Master",
                fileName = "concert.mp4",
                filePath = "/data/user/0/test/videos/concert.mp4",
                thumbnailPath = null,
                sizeBytes = 10_000_000_000L,
                durationMs = 3_600_000L,
                width = 1920,
                height = 1080,
                videoMimeType = "video/avc",
                hasAudio = true,
                audioMimeType = "audio/mp4a-latm"
            )
        )
        assertTrue(videoId > 0L)
        db.videoDao().rename(videoId, "Concert Master HD")
        val loaded = db.videoDao().getById(videoId)
        assertNotNull(loaded)
        assertEquals("Concert Master HD", loaded!!.title)
        assertEquals(10_000_000_000L, db.videoDao().observeTotalUsedBytes().first())

        val cfgId = db.rtmpConfigDao().insert(
            RtmpConfigEntity(
                name = "YouTube Main",
                platform = StreamPlatform.YOUTUBE.name,
                rtmpUrl = StreamPlatform.YOUTUBE.defaultRtmpUrl,
                encryptedStreamKey = "KS1:dummy",
                maskedKeyPreview = "••••••••1234"
            )
        )
        assertTrue(cfgId > 0L)

        val histId = db.streamHistoryDao().insert(
            StreamHistoryEntity(
                videoId = videoId,
                videoTitle = "Concert Master HD",
                platform = StreamPlatform.YOUTUBE.displayName,
                rtmpUrlMasked = "rtmps://a.rtmps.youtube.com/...",
                startedAtMs = 1_700_000_000_000L,
                durationMs = 120_000L,
                resolutionLabel = "1080p",
                videoBitrateKbps = 5000,
                fps = 30,
                status = StreamStatus.COMPLETED.name
            )
        )
        assertTrue(histId > 0L)
        assertEquals(1, db.streamHistoryDao().observeHistoryCount().first())

        db.videoDao().deleteById(videoId)
        assertEquals(0, db.videoDao().observeVideoCount().first())
    }
}
