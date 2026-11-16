package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BroadcastOnPersonal
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.StreamStatus
import com.example.data.storage.Formatters
import com.example.data.storage.StorageOverview
import com.example.service.ActiveBroadcastInfo
import com.example.ui.components.GlowingStudioCard
import com.example.ui.components.LivePillBadge
import com.example.ui.components.StudioAmbientBackground
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.VividPurple

private data class DashboardMenuItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentStart: Color,
    val accentEnd: Color,
    val onClick: () -> Unit
)

@Composable
fun DashboardScreen(
    videoCount: Int,
    totalUsedBytes: Long,
    storageOverview: StorageOverview,
    historyCount: Int,
    activeBroadcast: ActiveBroadcastInfo,
    onNavigateUpload: () -> Unit,
    onNavigateMyVideos: () -> Unit,
    onNavigateStartLive: () -> Unit,
    onNavigateLiveControl: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateStorage: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val menuItems = listOf(
        DashboardMenuItem(
            id = "upload_video",
            title = "Upload Video",
            subtitle = "Import up to 50 GB from gallery",
            icon = Icons.Filled.CloudUpload,
            accentStart = ElectricBlue,
            accentEnd = CyanGlow,
            onClick = onNavigateUpload
        ),
        DashboardMenuItem(
            id = "my_videos",
            title = "My Videos",
            subtitle = "$videoCount stored permanently",
            icon = Icons.Filled.VideoLibrary,
            accentStart = VividPurple,
            accentEnd = ElectricBlue,
            onClick = onNavigateMyVideos
        ),
        DashboardMenuItem(
            id = "start_live",
            title = "Start Live Streaming",
            subtitle = "YouTube, Facebook & RTMP",
            icon = Icons.Filled.LiveTv,
            accentStart = LiveCrimson,
            accentEnd = VividPurple,
            onClick = onNavigateStartLive
        ),
        DashboardMenuItem(
            id = "stream_history",
            title = "Streaming History",
            subtitle = "$historyCount past broadcasts",
            icon = Icons.Filled.History,
            accentStart = CyanGlow,
            accentEnd = VividPurple,
            onClick = onNavigateHistory
        ),
        DashboardMenuItem(
            id = "storage_manager",
            title = "Storage Manager",
            subtitle = Formatters.formatBytes(totalUsedBytes) + " used",
            icon = Icons.Filled.SdStorage,
            accentStart = EmeraldLive,
            accentEnd = ElectricBlue,
            onClick = onNavigateStorage
        ),
        DashboardMenuItem(
            id = "settings",
            title = "Settings",
            subtitle = "Quality, RTMP keys & theme",
            icon = Icons.Filled.Settings,
            accentStart = NeonViolet,
            accentEnd = ElectricBlue,
            onClick = onNavigateSettings
        )
    )

    StudioAmbientBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Branding
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Makwana_Hitu.07",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Video Vault & Live RTMP Studio",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(ElectricBlue.copy(alpha = 0.14f))
                                    .border(1.dp, ElectricBlue.copy(alpha = 0.45f), RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VolumeOff,
                                    contentDescription = "Microphone and speaker disabled during live",
                                    tint = CyanGlow,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Video Audio Only",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanGlow
                                )
                            }
                        }
                    }
                }

                // Active Live Broadcast Banner
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AnimatedVisibility(visible = activeBroadcast.isActiveSession) {
                        GlowingStudioCard(
                            onClick = onNavigateLiveControl,
                            accentBrush = Brush.linearGradient(listOf(LiveCrimson, VividPurple)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("active_stream_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    LivePillBadge(
                                        text = activeBroadcast.telemetry.status.label.uppercase(),
                                        color = if (activeBroadcast.telemetry.status == StreamStatus.LIVE) LiveCrimson else CyanGlow
                                    )
                                    Text(
                                        text = activeBroadcast.video?.title ?: "Live Stream",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${activeBroadcast.platform.displayName} • " +
                                            Formatters.formatDurationMs(activeBroadcast.telemetry.elapsedMs) +
                                            " • ${activeBroadcast.telemetry.currentBitrateKbps} kbps",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = onNavigateLiveControl,
                                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson),
                                    modifier = Modifier.testTag("open_live_console_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.BroadcastOnPersonal,
                                        contentDescription = "Open live console",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("Console")
                                }
                            }
                        }
                    }
                }

                // Telemetry Stats Overview Card
                item(span = { GridItemSpan(maxLineSpan) }) {
                    GlowingStudioCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Studio Overview",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatTile(
                                    label = "Uploaded Videos",
                                    value = videoCount.toString(),
                                    accent = ElectricBlue,
                                    modifier = Modifier.weight(1f)
                                )
                                StatTile(
                                    label = "Storage Used",
                                    value = Formatters.formatBytes(totalUsedBytes),
                                    accent = VividPurple,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatTile(
                                    label = "Available Space",
                                    value = Formatters.formatBytes(storageOverview.availableDeviceBytes),
                                    accent = EmeraldLive,
                                    modifier = Modifier.weight(1f)
                                )
                                StatTile(
                                    label = "Live Streams",
                                    value = historyCount.toString(),
                                    accent = CyanGlow,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 6 Dashboard Option Tiles
                items(menuItems, key = { it.id }) { item ->
                    GlowingStudioCard(
                        onClick = item.onClick,
                        accentBrush = Brush.linearGradient(
                            listOf(
                                item.accentStart.copy(alpha = 0.65f),
                                item.accentEnd.copy(alpha = 0.4f)
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(142.dp)
                            .testTag("dashboard_${item.id}_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                item.accentStart.copy(alpha = 0.25f),
                                                item.accentEnd.copy(alpha = 0.25f)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = item.accentStart,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
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
private fun StatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
