package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.VideoEntity
import com.example.data.storage.Formatters
import com.example.data.storage.StorageOverview
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GlowingStudioCard
import com.example.ui.components.StudioAmbientBackground
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.VividPurple

@Composable
fun StorageManagerScreen(
    videos: List<VideoEntity>,
    storageOverview: StorageOverview,
    onRefreshStorage: () -> Unit,
    onDeleteSelected: (List<VideoEntity>) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        onRefreshStorage()
    }

    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showConfirmDelete by remember { mutableStateOf(false) }

    val selectedVideos = videos.filter { it.id in selectedIds }
    val selectedBytes = selectedVideos.sumOf { it.sizeBytes }

    StudioAmbientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                StudioTopBar(
                    title = "Storage Manager",
                    subtitle = "Manual Control • Videos Never Auto-Deleted",
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        GlowingStudioCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Vault & Device Storage",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                val total = storageOverview.totalDeviceBytes.coerceAtLeast(1L)
                                val vaultFraction = (storageOverview.appVideosBytes.toDouble() / total.toDouble())
                                    .toFloat()
                                    .coerceIn(0f, 1f)

                                LinearProgressIndicator(
                                    progress = { vaultFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                    color = VividPurple,
                                    trackColor = MaterialTheme.colorScheme.surface
                                )

                                StorageMetricRow("Total Stored Video Files", "${videos.size} files")
                                StorageMetricRow(
                                    "Total Storage Consumed",
                                    Formatters.formatBytes(storageOverview.appVideosBytes),
                                    VividPurple
                                )
                                StorageMetricRow(
                                    "Available Device Storage",
                                    Formatters.formatBytes(storageOverview.availableDeviceBytes),
                                    EmeraldLive
                                )
                                StorageMetricRow(
                                    "Temporary Cache",
                                    Formatters.formatBytes(storageOverview.cacheBytes),
                                    CyanGlow
                                )
                            }
                        }
                    }

                    if (selectedVideos.isNotEmpty()) {
                        item {
                            Button(
                                onClick = { showConfirmDelete = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("delete_selected_storage_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete selected videos"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Delete ${selectedVideos.size} Selected (${Formatters.formatBytes(selectedBytes)})"
                                )
                            }
                        }
                    }

                    if (videos.isEmpty()) {
                        item {
                            EmptyStatePanel(
                                icon = Icons.Filled.SdStorage,
                                title = "Vault Is Empty",
                                description = "No videos are currently consuming app storage. Uploaded videos appear here with individual size breakdowns."
                            )
                        }
                    } else {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Stored Video Sizes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = {
                                        selectedIds = if (selectedIds.size == videos.size) {
                                            emptySet()
                                        } else {
                                            videos.map { it.id }.toSet()
                                        }
                                    }
                                ) {
                                    Text(if (selectedIds.size == videos.size) "Unselect All" else "Select All")
                                }
                            }
                        }

                        items(videos, key = { it.id }) { video ->
                            val isSelected = video.id in selectedIds
                            GlowingStudioCard(
                                onClick = {
                                    selectedIds = if (isSelected) selectedIds - video.id else selectedIds + video.id
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedIds = if (checked) selectedIds + video.id else selectedIds - video.id
                                        }
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = video.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${Formatters.formatDurationMs(video.durationMs)} • ${video.width}x${video.height}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = Formatters.formatBytes(video.sizeBytes),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ElectricBlue,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Delete ${selectedVideos.size} Video(s)?") },
            text = {
                Text(
                    "This will permanently delete ${selectedVideos.size} stored video(s) and free ${Formatters.formatBytes(selectedBytes)} of device storage."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSelected(selectedVideos)
                        selectedIds = emptySet()
                        showConfirmDelete = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StorageMetricRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}
