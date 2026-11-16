package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.db.RtmpConfigEntity
import com.example.data.db.StreamPlatform
import com.example.data.db.VideoEntity
import com.example.data.prefs.StreamingPreferences
import com.example.data.prefs.VideoResolution
import com.example.data.storage.Formatters
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GlowingStudioCard
import com.example.ui.components.StudioAmbientBackground
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.VividPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveStreamSetupScreen(
    videos: List<VideoEntity>,
    initialVideoId: Long?,
    savedConfigs: List<RtmpConfigEntity>,
    preferences: StreamingPreferences,
    decryptKey: (RtmpConfigEntity) -> String,
    onSaveRtmpConfig: (name: String, platform: StreamPlatform, url: String, key: String) -> Unit,
    onStartStreaming: (
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
        autoQuality: Boolean,
        preferPassthrough: Boolean,
        savedConfigId: Long?
    ) -> String?,
    onStreamStartedNavigate: () -> Unit,
    onNavigateUpload: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var selectedVideo by remember(videos, initialVideoId) {
        mutableStateOf(
            videos.firstOrNull { it.id == initialVideoId } ?: videos.firstOrNull()
        )
    }
    var videoDropdownExpanded by remember { mutableStateOf(false) }

    var platform by remember { mutableStateOf(StreamPlatform.YOUTUBE) }
    var rtmpUrl by remember { mutableStateOf(StreamPlatform.YOUTUBE.defaultRtmpUrl) }
    var streamKey by remember { mutableStateOf("") }
    var revealKey by remember { mutableStateOf(false) }
    var selectedSavedConfigId by remember { mutableStateOf<Long?>(null) }

    var resolution by remember(preferences.resolution) { mutableStateOf(preferences.resolution) }
    var videoBitrateKbps by remember(preferences.videoBitrateKbps) { mutableIntStateOf(preferences.videoBitrateKbps) }
    var fps by remember(preferences.fps) { mutableIntStateOf(preferences.fps) }
    var audioBitrateKbps by remember(preferences.audioBitrateKbps) { mutableIntStateOf(preferences.audioBitrateKbps) }
    var loopVideo by remember(preferences.loopVideo) { mutableStateOf(preferences.loopVideo) }
    var autoReconnect by remember(preferences.autoReconnect) { mutableStateOf(preferences.autoReconnect) }
    var autoQuality by remember(preferences.autoQualityAdjustment) { mutableStateOf(preferences.autoQualityAdjustment) }
    var preferPassthrough by remember(preferences.preferPassthroughWhenCompatible) {
        mutableStateOf(preferences.preferPassthroughWhenCompatible)
    }

    var errorBanner by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var configNameInput by remember { mutableStateOf("") }

    LaunchedEffect(initialVideoId, videos) {
        if (initialVideoId != null) {
            videos.firstOrNull { it.id == initialVideoId }?.let { selectedVideo = it }
        }
    }

    fun launchStreamNow() {
        val vid = selectedVideo
        if (vid == null) {
            errorBanner = "Please upload and select a video first."
            return
        }
        val err = onStartStreaming(
            vid,
            platform,
            rtmpUrl,
            streamKey,
            resolution,
            videoBitrateKbps,
            fps,
            audioBitrateKbps,
            loopVideo,
            autoReconnect,
            autoQuality,
            preferPassthrough,
            selectedSavedConfigId
        )
        if (err != null) {
            errorBanner = err
        } else {
            errorBanner = null
            onStreamStartedNavigate()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed with foreground service regardless of notification permission decision
        launchStreamNow()
    }

    StudioAmbientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                StudioTopBar(
                    title = "Live Stream Setup",
                    subtitle = "YouTube, Facebook & Custom RTMP",
                    onBackClick = onBack
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                if (videos.isEmpty()) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        EmptyStatePanel(
                            icon = Icons.Filled.Movie,
                            title = "No Stored Videos Available",
                            description = "Upload a pre-recorded video from your gallery first to broadcast it live to YouTube, Facebook, or a custom RTMP server.",
                            actionButton = {
                                Button(
                                    onClick = onNavigateUpload,
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                ) {
                                    Text("Upload Video")
                                }
                            }
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Original Audio Only Verification Banner
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(EmeraldLive.copy(alpha = 0.12f))
                                    .border(1.dp, EmeraldLive.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VolumeOff,
                                    contentDescription = "Original video audio only",
                                    tint = EmeraldLive
                                )
                                Column {
                                    Text(
                                        text = "Original Video Audio Only",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldLive
                                    )
                                    Text(
                                        text = "Direct file-to-RTMP pipeline. Phone microphone and speaker stay 100% silent.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Step 1: Select Video
                        item {
                            GlowingStudioCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "1. Selected Video",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    ExposedDropdownMenuBox(
                                        expanded = videoDropdownExpanded,
                                        onExpandedChange = { videoDropdownExpanded = !videoDropdownExpanded }
                                    ) {
                                        OutlinedTextField(
                                            value = selectedVideo?.let {
                                                "${it.title} (${Formatters.formatDurationMs(it.durationMs)})"
                                            } ?: "Select video",
                                            onValueChange = {},
                                            readOnly = true,
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = videoDropdownExpanded)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                                .testTag("select_stream_video_dropdown")
                                        )
                                        ExposedDropdownMenu(
                                            expanded = videoDropdownExpanded,
                                            onDismissRequest = { videoDropdownExpanded = false }
                                        ) {
                                            videos.forEach { vid ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(vid.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            Text(
                                                                "${Formatters.formatBytes(vid.sizeBytes)} • ${vid.width}x${vid.height}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        selectedVideo = vid
                                                        videoDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Step 2: Platform & RTMP Destination
                        item {
                            GlowingStudioCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "2. RTMP Destination",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Lock,
                                                contentDescription = "Keystore encrypted",
                                                tint = CyanGlow,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Keystore Protected",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = CyanGlow
                                            )
                                        }
                                    }

                                    // Platform Selector Chips
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        StreamPlatform.entries.forEach { p ->
                                            FilterChip(
                                                selected = platform == p,
                                                onClick = {
                                                    platform = p
                                                    selectedSavedConfigId = null
                                                    rtmpUrl = p.defaultRtmpUrl
                                                },
                                                label = { Text(p.displayName) },
                                                modifier = Modifier.testTag("platform_${p.name.lowercase()}")
                                            )
                                        }
                                    }

                                    // Saved Presets
                                    if (savedConfigs.isNotEmpty()) {
                                        Text(
                                            text = "Saved RTMP Presets",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            savedConfigs.forEach { cfg ->
                                                FilterChip(
                                                    selected = selectedSavedConfigId == cfg.id,
                                                    onClick = {
                                                        selectedSavedConfigId = cfg.id
                                                        platform = runCatching {
                                                            StreamPlatform.valueOf(cfg.platform)
                                                        }.getOrDefault(StreamPlatform.CUSTOM)
                                                        rtmpUrl = cfg.rtmpUrl
                                                        streamKey = decryptKey(cfg)
                                                    },
                                                    label = { Text("${cfg.name} (${cfg.maskedKeyPreview})") },
                                                    modifier = Modifier.testTag("saved_rtmp_${cfg.id}")
                                                )
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = rtmpUrl,
                                        onValueChange = {
                                            rtmpUrl = it
                                            selectedSavedConfigId = null
                                        },
                                        label = { Text("RTMP Server URL") },
                                        placeholder = { Text("rtmps://a.rtmps.youtube.com/live2") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("rtmp_url_input")
                                    )

                                    OutlinedTextField(
                                        value = streamKey,
                                        onValueChange = {
                                            streamKey = it
                                            selectedSavedConfigId = null
                                        },
                                        label = { Text("Stream Key") },
                                        placeholder = { Text("Paste your stream key securely") },
                                        singleLine = true,
                                        visualTransformation = if (revealKey) {
                                            VisualTransformation.None
                                        } else {
                                            PasswordVisualTransformation()
                                        },
                                        trailingIcon = {
                                            IconButton(onClick = { revealKey = !revealKey }) {
                                                Icon(
                                                    imageVector = if (revealKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                    contentDescription = if (revealKey) "Hide stream key" else "Show stream key"
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("rtmp_stream_key_input")
                                    )

                                    OutlinedButton(
                                        onClick = {
                                            configNameInput = platform.displayName
                                            showSaveDialog = true
                                        },
                                        enabled = rtmpUrl.isNotBlank() && streamKey.isNotBlank(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("save_rtmp_preset_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.BookmarkAdd,
                                            contentDescription = "Save RTMP preset",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Save RTMP Configuration Securely")
                                    }
                                }
                            }
                        }

                        // Step 3: Streaming Quality Settings
                        item {
                            GlowingStudioCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "3. Streaming Quality & Options",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text("Video Resolution", style = MaterialTheme.typography.labelLarge)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        VideoResolution.entries.forEach { res ->
                                            FilterChip(
                                                selected = resolution == res,
                                                onClick = {
                                                    resolution = res
                                                    videoBitrateKbps = res.defaultBitrateKbps
                                                },
                                                label = { Text(res.label) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Video Bitrate", style = MaterialTheme.typography.labelLarge)
                                            Text(
                                                text = "$videoBitrateKbps kbps",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = CyanGlow
                                            )
                                        }
                                        Slider(
                                            value = videoBitrateKbps.toFloat(),
                                            onValueChange = { videoBitrateKbps = (it.toInt() / 100) * 100 },
                                            valueRange = 500f..10000f
                                        )
                                    }

                                    Text("Frame Rate (FPS)", style = MaterialTheme.typography.labelLarge)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(24, 30, 60).forEach { targetFps ->
                                            FilterChip(
                                                selected = fps == targetFps,
                                                onClick = { fps = targetFps },
                                                label = { Text("$targetFps FPS") },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    Text("Audio Bitrate (AAC)", style = MaterialTheme.typography.labelLarge)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(64, 96, 128, 192, 256).forEach { aBit ->
                                            FilterChip(
                                                selected = audioBitrateKbps == aBit,
                                                onClick = { audioBitrateKbps = aBit },
                                                label = { Text("$aBit kbps") }
                                            )
                                        }
                                    }

                                    SetupSwitchRow(
                                        title = "Loop Video Continuously",
                                        subtitle = "Restart seamlessly when video ends without stopping stream",
                                        checked = loopVideo,
                                        onCheckedChange = { loopVideo = it }
                                    )
                                    SetupSwitchRow(
                                        title = "Auto Reconnect",
                                        subtitle = "Automatically recover from network interruptions",
                                        checked = autoReconnect,
                                        onCheckedChange = { autoReconnect = it }
                                    )
                                    SetupSwitchRow(
                                        title = "Automatic Quality Adjustment",
                                        subtitle = "Dynamically adjust bitrate during network congestion",
                                        checked = autoQuality,
                                        onCheckedChange = { autoQuality = it }
                                    )
                                    SetupSwitchRow(
                                        title = "Direct Passthrough (No Re-encode)",
                                        subtitle = "Stream original H.264/AAC directly when resolution matches",
                                        checked = preferPassthrough,
                                        onCheckedChange = { preferPassthrough = it }
                                    )
                                }
                            }
                        }

                        if (errorBanner != null) {
                            item {
                                Text(
                                    text = errorBanner!!,
                                    color = LiveCrimson,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(LiveCrimson.copy(alpha = 0.14f))
                                        .padding(12.dp)
                                )
                            }
                        }

                        item {
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        launchStreamNow()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("start_streaming_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LiveTv,
                                    contentDescription = "Start Live Streaming"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Start Streaming",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save RTMP Configuration") },
            text = {
                OutlinedTextField(
                    value = configNameInput,
                    onValueChange = { configNameInput = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveRtmpConfig(configNameInput, platform, rtmpUrl, streamKey)
                        showSaveDialog = false
                    }
                ) {
                    Text("Save Securely")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SetupSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
