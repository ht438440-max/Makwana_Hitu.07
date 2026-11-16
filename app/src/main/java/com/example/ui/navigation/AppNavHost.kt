package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.screens.CinematicSplashScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LiveControlScreen
import com.example.ui.screens.LiveStreamSetupScreen
import com.example.ui.screens.MyVideosScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StorageManagerScreen
import com.example.ui.screens.StreamingHistoryScreen
import com.example.ui.screens.UploadVideoScreen
import com.example.ui.screens.VideoPreviewScreen
import com.example.ui.viewmodel.StudioViewModel
import kotlinx.serialization.Serializable

@Serializable data object SplashRoute
@Serializable data object DashboardRoute
@Serializable data object UploadRoute
@Serializable data object MyVideosRoute
@Serializable data class VideoPreviewRoute(val videoId: Long)
@Serializable data class LiveSetupRoute(val videoId: Long? = null)
@Serializable data object LiveControlRoute
@Serializable data object HistoryRoute
@Serializable data object StorageRoute
@Serializable data object SettingsRoute

@Composable
fun StudioNavHost(
    viewModel: StudioViewModel,
    skipSplashInitially: Boolean = false
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val allVideos by viewModel.allVideos.collectAsStateWithLifecycle()
    val filteredVideos by viewModel.filteredVideos.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val videoCount by viewModel.videoCount.collectAsStateWithLifecycle()
    val totalUsedBytes by viewModel.totalUsedBytes.collectAsStateWithLifecycle()
    val storageOverview by viewModel.storageOverview.collectAsStateWithLifecycle()
    val rtmpConfigs by viewModel.rtmpConfigs.collectAsStateWithLifecycle()
    val streamHistory by viewModel.streamHistory.collectAsStateWithLifecycle()
    val streamHistoryCount by viewModel.streamHistoryCount.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val importTasks by viewModel.importTasks.collectAsStateWithLifecycle()
    val activeBroadcast by viewModel.activeBroadcast.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()

    LaunchedEffect(uiMessage) {
        val msg = uiMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeUiMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = if (skipSplashInitially) DashboardRoute else SplashRoute
        ) {
            composable<SplashRoute> {
                CinematicSplashScreen(
                    onFinished = {
                        navController.navigate(DashboardRoute) {
                            popUpTo(SplashRoute) { inclusive = true }
                        }
                    }
                )
            }

            composable<DashboardRoute> {
                DashboardScreen(
                    videoCount = videoCount,
                    totalUsedBytes = totalUsedBytes,
                    storageOverview = storageOverview,
                    historyCount = streamHistoryCount,
                    activeBroadcast = activeBroadcast,
                    onNavigateUpload = { navController.navigate(UploadRoute) },
                    onNavigateMyVideos = { navController.navigate(MyVideosRoute) },
                    onNavigateStartLive = {
                        if (activeBroadcast.isActiveSession) {
                            navController.navigate(LiveControlRoute)
                        } else {
                            navController.navigate(LiveSetupRoute(null))
                        }
                    },
                    onNavigateLiveControl = { navController.navigate(LiveControlRoute) },
                    onNavigateHistory = { navController.navigate(HistoryRoute) },
                    onNavigateStorage = { navController.navigate(StorageRoute) },
                    onNavigateSettings = { navController.navigate(SettingsRoute) }
                )
            }

            composable<UploadRoute> {
                UploadVideoScreen(
                    importTasks = importTasks,
                    storageOverview = storageOverview,
                    onVideosSelected = { uris -> viewModel.enqueueVideoImports(uris) },
                    onClearCompleted = { viewModel.clearCompletedImports() },
                    onOpenMyVideos = { navController.navigate(MyVideosRoute) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<MyVideosRoute> {
                MyVideosScreen(
                    videos = filteredVideos,
                    searchQuery = searchQuery,
                    sortOrder = sortOrder,
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onSortChange = { viewModel.setSortOrder(it) },
                    onPreviewVideo = { vid -> navController.navigate(VideoPreviewRoute(vid.id)) },
                    onGoLiveVideo = { vid -> navController.navigate(LiveSetupRoute(vid.id)) },
                    onRenameVideo = { id, name -> viewModel.renameVideo(id, name) },
                    onDeleteVideo = { vid -> viewModel.deleteVideo(vid) },
                    onNavigateUpload = { navController.navigate(UploadRoute) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<VideoPreviewRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<VideoPreviewRoute>()
                val video = allVideos.firstOrNull { it.id == route.videoId }
                VideoPreviewScreen(
                    video = video,
                    onGoLive = { vid -> navController.navigate(LiveSetupRoute(vid.id)) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<LiveSetupRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<LiveSetupRoute>()
                LiveStreamSetupScreen(
                    videos = allVideos,
                    initialVideoId = route.videoId,
                    savedConfigs = rtmpConfigs,
                    preferences = preferences,
                    decryptKey = { cfg -> viewModel.decryptStreamKey(cfg) },
                    onSaveRtmpConfig = { name, platform, url, key ->
                        viewModel.saveRtmpConfig(name, platform, url, key)
                    },
                    onStartStreaming = { video, platform, url, key, res, vBit, fps, aBit, loop, reconn, autoQ, pass, cfgId ->
                        viewModel.startLiveStream(
                            video = video,
                            platform = platform,
                            rtmpUrl = url,
                            streamKey = key,
                            resolution = res,
                            videoBitrateKbps = vBit,
                            fps = fps,
                            audioBitrateKbps = aBit,
                            loopVideo = loop,
                            autoReconnect = reconn,
                            autoQualityAdjustment = autoQ,
                            preferPassthrough = pass,
                            savedConfigId = cfgId
                        )
                    },
                    onStreamStartedNavigate = {
                        navController.navigate(LiveControlRoute) {
                            popUpTo(DashboardRoute) { inclusive = false }
                        }
                    },
                    onNavigateUpload = { navController.navigate(UploadRoute) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<LiveControlRoute> {
                LiveControlScreen(
                    activeBroadcast = activeBroadcast,
                    onPause = { viewModel.pauseStream() },
                    onResume = { viewModel.resumeStream() },
                    onStop = { viewModel.stopStream() },
                    onToggleLoop = { viewModel.toggleLiveLoop(it) },
                    onNavigateSetup = { navController.navigate(LiveSetupRoute(null)) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<HistoryRoute> {
                StreamingHistoryScreen(
                    history = streamHistory,
                    onDeleteEntry = { id -> viewModel.deleteHistoryEntry(id) },
                    onClearAll = { viewModel.clearAllHistory() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<StorageRoute> {
                StorageManagerScreen(
                    videos = allVideos,
                    storageOverview = storageOverview,
                    onRefreshStorage = { viewModel.refreshStorageOverview() },
                    onDeleteSelected = { list -> viewModel.deleteMultipleVideos(list) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<SettingsRoute> {
                SettingsScreen(
                    preferences = preferences,
                    storageOverview = storageOverview,
                    videoCount = videoCount,
                    rtmpConfigs = rtmpConfigs,
                    decryptKey = { cfg -> viewModel.decryptStreamKey(cfg) },
                    onDeleteRtmpConfig = { id -> viewModel.deleteRtmpConfig(id) },
                    onSetDarkTheme = { viewModel.setDarkTheme(it) },
                    onSetResolution = { viewModel.setResolution(it) },
                    onSetBitrate = { viewModel.setVideoBitrateKbps(it) },
                    onSetFps = { viewModel.setFps(it) },
                    onSetAudioBitrate = { viewModel.setAudioBitrateKbps(it) },
                    onSetLoopVideo = { viewModel.setLoopVideo(it) },
                    onSetAutoReconnect = { viewModel.setAutoReconnect(it) },
                    onSetAutoQuality = { viewModel.setAutoQuality(it) },
                    onSetPassthrough = { viewModel.setPreferPassthrough(it) },
                    onClearCache = { viewModel.clearCache() },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}
