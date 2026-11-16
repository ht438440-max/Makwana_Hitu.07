package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MakwanaHituApp
import com.example.data.db.RtmpConfigEntity
import com.example.data.db.StreamHistoryEntity
import com.example.data.db.StreamPlatform
import com.example.data.db.VideoEntity
import com.example.data.prefs.StreamingPreferences
import com.example.data.prefs.VideoResolution
import com.example.data.storage.ImportTaskProgress
import com.example.data.storage.StorageOverview
import com.example.rtmp.RtmpUrlParser
import com.example.rtmp.StreamConfig
import com.example.service.ActiveBroadcastInfo
import com.example.service.VideoImportService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class VideoSortOrder(val label: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    LARGEST("Largest Size"),
    SMALLEST("Smallest Size"),
    NAME_ASC("Name (A-Z)")
}

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MakwanaHituApp
    private val repository = app.broadcastRepository
    private val prefsRepo = app.preferencesRepository
    private val sessionManager = app.broadcastSessionManager

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(VideoSortOrder.NEWEST)
    val sortOrder: StateFlow<VideoSortOrder> = _sortOrder.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    val allVideos: StateFlow<List<VideoEntity>> = repository.videosFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredVideos: StateFlow<List<VideoEntity>> = combine(
        repository.videosFlow,
        _searchQuery,
        _sortOrder
    ) { videos, query, sort ->
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) videos else {
            videos.filter {
                it.title.lowercase().contains(q) || it.fileName.lowercase().contains(q)
            }
        }
        when (sort) {
            VideoSortOrder.NEWEST -> filtered.sortedByDescending { it.uploadedAtMs }
            VideoSortOrder.OLDEST -> filtered.sortedBy { it.uploadedAtMs }
            VideoSortOrder.LARGEST -> filtered.sortedByDescending { it.sizeBytes }
            VideoSortOrder.SMALLEST -> filtered.sortedBy { it.sizeBytes }
            VideoSortOrder.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoCount: StateFlow<Int> = repository.videoCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalUsedBytes: StateFlow<Long> = repository.totalUsedBytesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val refreshTick = MutableStateFlow(0)

    val storageOverview: StateFlow<StorageOverview> = combine(
        repository.totalUsedBytesFlow,
        refreshTick
    ) { used, _ ->
        repository.getStorageOverview(used)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        repository.getStorageOverview(0L)
    )

    val rtmpConfigs: StateFlow<List<RtmpConfigEntity>> = repository.rtmpConfigsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streamHistory: StateFlow<List<StreamHistoryEntity>> = repository.streamHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streamHistoryCount: StateFlow<Int> = repository.streamHistoryCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val preferences: StateFlow<StreamingPreferences> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreamingPreferences())

    val importTasks: StateFlow<List<ImportTaskProgress>> = repository.importTasksFlow

    val activeBroadcast: StateFlow<ActiveBroadcastInfo> = sessionManager.activeBroadcast

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: VideoSortOrder) {
        _sortOrder.value = order
    }

    fun consumeUiMessage() {
        _uiMessage.value = null
    }

    fun refreshStorageOverview() {
        refreshTick.value += 1
    }

    fun enqueueVideoImports(uris: List<Uri>) {
        if (uris.isEmpty()) return
        VideoImportService.enqueueImports(app, uris)
        _uiMessage.value = "Importing ${uris.size} video${if (uris.size > 1) "s" else ""} into permanent vault..."
    }

    fun clearCompletedImports() {
        repository.clearCompletedImports()
    }

    fun renameVideo(id: Long, newTitle: String) {
        viewModelScope.launch {
            repository.renameVideo(id, newTitle)
            _uiMessage.value = "Video renamed."
        }
    }

    fun deleteVideo(video: VideoEntity) {
        viewModelScope.launch {
            repository.deleteVideo(video)
            refreshStorageOverview()
            _uiMessage.value = "Deleted \"${video.title}\"."
        }
    }

    fun deleteMultipleVideos(videos: List<VideoEntity>) {
        if (videos.isEmpty()) return
        viewModelScope.launch {
            repository.deleteVideosByIds(videos)
            refreshStorageOverview()
            _uiMessage.value = "Deleted ${videos.size} video(s)."
        }
    }

    fun saveRtmpConfig(
        name: String,
        platform: StreamPlatform,
        rtmpUrl: String,
        rawStreamKey: String
    ) {
        viewModelScope.launch {
            val urlCheck = RtmpUrlParser.parse(rtmpUrl)
            if (urlCheck.isFailure) {
                _uiMessage.value = urlCheck.exceptionOrNull()?.message ?: "Invalid RTMP URL."
                return@launch
            }
            if (rawStreamKey.trim().isEmpty()) {
                _uiMessage.value = "Stream Key cannot be empty."
                return@launch
            }
            repository.saveRtmpConfig(name, platform, rtmpUrl, rawStreamKey)
            _uiMessage.value = "RTMP configuration saved securely in Android Keystore."
        }
    }

    fun decryptStreamKey(config: RtmpConfigEntity): String {
        return runCatching { repository.decryptStreamKey(config) }.getOrDefault("")
    }

    fun deleteRtmpConfig(id: Long) {
        viewModelScope.launch {
            repository.deleteRtmpConfig(id)
            _uiMessage.value = "RTMP configuration removed."
        }
    }

    fun startLiveStream(
        video: VideoEntity,
        platform: StreamPlatform,
        rtmpUrl: String,
        streamKey: String,
        resolution: VideoResolution,
        videoBitrateKbps: Int,
        fps: Int,
        audioBitrateKbps: Int,
        loopVideo: Boolean,
        autoReconnect: Boolean,
        autoQualityAdjustment: Boolean,
        preferPassthrough: Boolean,
        savedConfigId: Long? = null
    ): String? {
        if (!repository.verifyVideoFileExists(video)) {
            return "Stored video file is missing from device storage."
        }
        val urlParse = RtmpUrlParser.parse(rtmpUrl)
        if (urlParse.isFailure) {
            return urlParse.exceptionOrNull()?.message ?: "Invalid RTMP Server URL."
        }
        val cleanKey = streamKey.trim()
        if (cleanKey.isEmpty()) {
            return "Please enter a valid Stream Key."
        }

        if (savedConfigId != null) {
            viewModelScope.launch { repository.touchRtmpConfigUsed(savedConfigId) }
        }

        val config = StreamConfig(
            video = video,
            platform = platform,
            rtmpUrl = rtmpUrl.trim(),
            streamKey = cleanKey,
            resolution = resolution,
            videoBitrateKbps = videoBitrateKbps,
            fps = fps,
            audioBitrateKbps = audioBitrateKbps,
            loopVideo = loopVideo,
            autoReconnect = autoReconnect,
            autoQualityAdjustment = autoQualityAdjustment,
            preferPassthrough = preferPassthrough
        )
        sessionManager.startStream(config)
        return null
    }

    fun pauseStream() = sessionManager.pauseStream()
    fun resumeStream() = sessionManager.resumeStream()
    fun stopStream() = sessionManager.stopStream()
    fun toggleLiveLoop(enabled: Boolean) = sessionManager.toggleLoop(enabled)

    fun setDarkTheme(enabled: Boolean) = viewModelScope.launch { prefsRepo.setDarkTheme(enabled) }
    fun setResolution(res: VideoResolution) = viewModelScope.launch {
        prefsRepo.setResolution(res)
        prefsRepo.setVideoBitrateKbps(res.defaultBitrateKbps)
    }
    fun setVideoBitrateKbps(kbps: Int) = viewModelScope.launch { prefsRepo.setVideoBitrateKbps(kbps) }
    fun setFps(fps: Int) = viewModelScope.launch { prefsRepo.setFps(fps) }
    fun setAudioBitrateKbps(kbps: Int) = viewModelScope.launch { prefsRepo.setAudioBitrateKbps(kbps) }
    fun setLoopVideo(enabled: Boolean) = viewModelScope.launch { prefsRepo.setLoopVideo(enabled) }
    fun setAutoReconnect(enabled: Boolean) = viewModelScope.launch { prefsRepo.setAutoReconnect(enabled) }
    fun setAutoQuality(enabled: Boolean) = viewModelScope.launch { prefsRepo.setAutoQualityAdjustment(enabled) }
    fun setPreferPassthrough(enabled: Boolean) = viewModelScope.launch { prefsRepo.setPreferPassthrough(enabled) }

    fun clearCache() {
        viewModelScope.launch {
            val freed = repository.clearCache()
            refreshStorageOverview()
            _uiMessage.value = "Cleared temporary cache (${com.example.data.storage.Formatters.formatBytes(freed)}). Stored videos untouched."
        }
    }

    fun deleteHistoryEntry(id: Long) = viewModelScope.launch { repository.deleteHistoryEntry(id) }
    fun clearAllHistory() = viewModelScope.launch {
        repository.clearAllHistory()
        _uiMessage.value = "Streaming history cleared."
    }
}
