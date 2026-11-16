package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "makwana_hitu_streaming_prefs"
)

enum class VideoResolution(
    val label: String,
    val width: Int,
    val height: Int,
    val defaultBitrateKbps: Int
) {
    P360("360p", 640, 360, 1000),
    P480("480p", 854, 480, 1600),
    P720("720p", 1280, 720, 3000),
    P1080("1080p", 1920, 1080, 5000);

    companion object {
        fun fromName(name: String?): VideoResolution =
            entries.firstOrNull { it.name == name } ?: P720
    }
}

data class StreamingPreferences(
    val darkThemeEnabled: Boolean = true,
    val resolution: VideoResolution = VideoResolution.P720,
    val videoBitrateKbps: Int = 3000,
    val fps: Int = 30,
    val audioBitrateKbps: Int = 128,
    val loopVideo: Boolean = true,
    val autoReconnect: Boolean = true,
    val autoQualityAdjustment: Boolean = true,
    val preferPassthroughWhenCompatible: Boolean = true
)

class StreamingPreferencesRepository(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val RESOLUTION = stringPreferencesKey("video_resolution")
        val VIDEO_BITRATE_KBPS = intPreferencesKey("video_bitrate_kbps")
        val FPS = intPreferencesKey("fps")
        val AUDIO_BITRATE_KBPS = intPreferencesKey("audio_bitrate_kbps")
        val LOOP_VIDEO = booleanPreferencesKey("loop_video")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val AUTO_QUALITY = booleanPreferencesKey("auto_quality")
        val PREFER_PASSTHROUGH = booleanPreferencesKey("prefer_passthrough")
    }

    val preferencesFlow: Flow<StreamingPreferences> = context.studioDataStore.data.map { prefs ->
        StreamingPreferences(
            darkThemeEnabled = prefs[Keys.DARK_THEME] ?: true,
            resolution = VideoResolution.fromName(prefs[Keys.RESOLUTION]),
            videoBitrateKbps = prefs[Keys.VIDEO_BITRATE_KBPS] ?: 3000,
            fps = prefs[Keys.FPS] ?: 30,
            audioBitrateKbps = prefs[Keys.AUDIO_BITRATE_KBPS] ?: 128,
            loopVideo = prefs[Keys.LOOP_VIDEO] ?: true,
            autoReconnect = prefs[Keys.AUTO_RECONNECT] ?: true,
            autoQualityAdjustment = prefs[Keys.AUTO_QUALITY] ?: true,
            preferPassthroughWhenCompatible = prefs[Keys.PREFER_PASSTHROUGH] ?: true
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setResolution(resolution: VideoResolution) {
        context.studioDataStore.edit { it[Keys.RESOLUTION] = resolution.name }
    }

    suspend fun setVideoBitrateKbps(bitrateKbps: Int) {
        context.studioDataStore.edit { it[Keys.VIDEO_BITRATE_KBPS] = bitrateKbps.coerceIn(400, 15000) }
    }

    suspend fun setFps(fps: Int) {
        context.studioDataStore.edit { it[Keys.FPS] = fps }
    }

    suspend fun setAudioBitrateKbps(bitrateKbps: Int) {
        context.studioDataStore.edit { it[Keys.AUDIO_BITRATE_KBPS] = bitrateKbps.coerceIn(64, 320) }
    }

    suspend fun setLoopVideo(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.LOOP_VIDEO] = enabled }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.AUTO_RECONNECT] = enabled }
    }

    suspend fun setAutoQualityAdjustment(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.AUTO_QUALITY] = enabled }
    }

    suspend fun setPreferPassthrough(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.PREFER_PASSTHROUGH] = enabled }
    }
}
