package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class StreamPlatform(val displayName: String, val defaultRtmpUrl: String) {
    YOUTUBE("YouTube Live", "rtmps://a.rtmps.youtube.com/live2"),
    FACEBOOK("Facebook Live", "rtmps://live-api-s.facebook.com:443/rtmp/"),
    CUSTOM("Custom RTMP", "rtmp://")
}

enum class StreamStatus(val label: String) {
    LIVE("Live"),
    PAUSED("Paused"),
    RECONNECTING("Reconnecting"),
    COMPLETED("Completed"),
    STOPPED("Stopped"),
    FAILED("Failed")
}

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val fileName: String,
    val filePath: String,
    val thumbnailPath: String?,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val videoMimeType: String,
    val hasAudio: Boolean,
    val audioMimeType: String?,
    val uploadedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "rtmp_configs")
data class RtmpConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val platform: String,
    val rtmpUrl: String,
    val encryptedStreamKey: String,
    val maskedKeyPreview: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastUsedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "stream_history")
data class StreamHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val videoId: Long?,
    val videoTitle: String,
    val platform: String,
    val rtmpUrlMasked: String,
    val startedAtMs: Long,
    val durationMs: Long,
    val resolutionLabel: String,
    val videoBitrateKbps: Int,
    val fps: Int,
    val status: String,
    val errorMessage: String? = null
)
