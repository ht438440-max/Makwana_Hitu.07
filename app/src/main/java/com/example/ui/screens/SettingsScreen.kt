package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.db.RtmpConfigEntity
import com.example.data.prefs.StreamingPreferences
import com.example.data.prefs.VideoResolution
import com.example.data.storage.Formatters
import com.example.data.storage.StorageOverview
import com.example.ui.components.GlowingStudioCard
import com.example.ui.components.StudioAmbientBackground
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.LiveCrimson

@Composable
fun SettingsScreen(
    preferences: StreamingPreferences,
    storageOverview: StorageOverview,
    videoCount: Int,
    rtmpConfigs: List<RtmpConfigEntity>,
    decryptKey: (RtmpConfigEntity) -> String,
    onDeleteRtmpConfig: (Long) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetResolution: (VideoResolution) -> Unit,
    onSetBitrate: (Int) -> Unit,
    onSetFps: (Int) -> Unit,
    onSetAudioBitrate: (Int) -> Unit,
    onSetLoopVideo: (Boolean) -> Unit,
    onSetAutoReconnect: (Boolean) -> Unit,
    onSetAutoQuality: (Boolean) -> Unit,
    onSetPassthrough: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var revealedConfigIds by remember { mutableStateOf(setOf<Long>()) }

    StudioAmbientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                StudioTopBar(
                    title = "Settings",
                    subtitle = "Streaming Preferences, Security & Storage",
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Appearance
                    item {
                        GlowingStudioCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Appearance",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                SettingsToggleRow(
                                    title = "Dark Theme",
                                    subtitle = "Premium Black, Dark Blue & Purple studio theme",
                                    checked = preferences.darkThemeEnabled,
                                    onCheckedChange = onSetDarkTheme,
                                    tag = "settings_dark_theme_switch"
                                )
                            }
                        }
                    }

                    // Default Streaming Preferences
                    item {
                        GlowingStudioCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Default Streaming Preferences",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Text("Default Resolution", style = MaterialTheme.typography.labelLarge)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    VideoResolution.entries.forEach { res ->
                                        FilterChip(
                                            selected = preferences.resolution == res,
                                            onClick = { onSetResolution(res) },
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
                                            text = "${preferences.videoBitrateKbps} kbps",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = CyanGlow
                                        )
                                    }
                                    Slider(
                                        value = preferences.videoBitrateKbps.toFloat(),
                                        onValueChange = { onSetBitrate((it.toInt() / 100) * 100) },
                                        valueRange = 500f..10000f
                                    )
                                }

                                Text("Frame Rate", style = MaterialTheme.typography.labelLarge)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(24, 30, 60).forEach { fps ->
                                        FilterChip(
                                            selected = preferences.fps == fps,
                                            onClick = { onSetFps(fps) },
                                            label = { Text("$fps FPS") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                Text("AAC Audio Bitrate", style = MaterialTheme.typography.labelLarge)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(64, 96, 128, 192, 256).forEach { aBit ->
                                        FilterChip(
                                            selected = preferences.audioBitrateKbps == aBit,
                                            onClick = { onSetAudioBitrate(aBit) },
                                            label = { Text("$aBit kbps") }
                                        )
                                    }
                                }

                                SettingsToggleRow(
                                    title = "Loop Video by Default",
                                    subtitle = "Continuous loop without stopping RTMP session",
                                    checked = preferences.loopVideo,
                                    onCheckedChange = onSetLoopVideo
                                )
                                SettingsToggleRow(
                                    title = "Auto Reconnect",
                                    subtitle = "Automatically recover dropped RTMP connections",
                                    checked = preferences.autoReconnect,
                                    onCheckedChange = onSetAutoReconnect
                                )
                                SettingsToggleRow(
                                    title = "Automatic Quality Adjustment",
                                    subtitle = "Adapt encoder bitrate during weak upload bandwidth",
                                    checked = preferences.autoQualityAdjustment,
                                    onCheckedChange = onSetAutoQuality
                                )
                                SettingsToggleRow(
                                    title = "Prefer Direct Passthrough",
                                    subtitle = "Skip video re-encoding when H.264/AAC source matches",
                                    checked = preferences.preferPassthroughWhenCompatible,
                                    onCheckedChange = onSetPassthrough
                                )
                            }
                        }
                    }

                    // Saved RTMP Configurations (Keystore Encrypted)
                    item {
                        GlowingStudioCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Saved RTMP Configurations (${rtmpConfigs.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (rtmpConfigs.isEmpty()) {
                                    Text(
                                        text = "No saved RTMP presets yet. Save YouTube, Facebook, or custom RTMP destinations during Live Setup.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    rtmpConfigs.forEach { cfg ->
                                        val isRevealed = cfg.id in revealedConfigIds
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = cfg.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = cfg.rtmpUrl,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = if (isRevealed) decryptKey(cfg) else cfg.maskedKeyPreview,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = CyanGlow
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    revealedConfigIds = if (isRevealed) {
                                                        revealedConfigIds - cfg.id
                                                    } else {
                                                        revealedConfigIds + cfg.id
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (isRevealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                    contentDescription = if (isRevealed) "Hide key" else "Reveal key"
                                                )
                                            }
                                            IconButton(onClick = { onDeleteRtmpConfig(cfg.id) }) {
                                                Icon(
                                                    imageVector = Icons.Filled.Delete,
                                                    contentDescription = "Delete RTMP config",
                                                    tint = LiveCrimson
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Storage Information, Cache & Battery Optimization
                    item {
                        GlowingStudioCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Storage & Background Execution",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$videoCount permanent video(s) • ${Formatters.formatBytes(storageOverview.appVideosBytes)} used • " +
                                        "${Formatters.formatBytes(storageOverview.availableDeviceBytes)} free on device",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedButton(
                                    onClick = onClearCache,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_clear_cache_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CleaningServices,
                                        contentDescription = "Clear temporary cache"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Clear Cache (${Formatters.formatBytes(storageOverview.cacheBytes)})")
                                }

                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.BatterySaver,
                                        contentDescription = "Battery optimization settings"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Battery Optimization Settings")
                                }
                            }
                        }
                    }

                    // About Application & Version
                    item {
                        GlowingStudioCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = "About Makwana_Hitu.07",
                                        tint = EmeraldLive
                                    )
                                    Text(
                                        text = "About Makwana_Hitu.07",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CyanGlow
                                )
                                Text(
                                    text = "Native Kotlin & Jetpack Compose Video Vault (up to 50 GB/file) and Direct File-to-RTMP Live Broadcast Studio. Uses embedded video audio exclusively with microphone & loudspeaker strictly disabled during live streams.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String? = null
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier
        )
    }
}
