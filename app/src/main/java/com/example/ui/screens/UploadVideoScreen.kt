package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.storage.Formatters
import com.example.data.storage.ImportStatus
import com.example.data.storage.ImportTaskProgress
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
fun UploadVideoScreen(
    importTasks: List<ImportTaskProgress>,
    storageOverview: StorageOverview,
    onVideosSelected: (List<Uri>) -> Unit,
    onClearCompleted: () -> Unit,
    onOpenMyVideos: () -> Unit,
    onBack: () -> Unit
) {
    // Zero-permission Android Storage Access Framework multi-video picker supporting up to 50 GB
    val safPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onVideosSelected(uris)
        }
    }

    StudioAmbientBackground {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                StudioTopBar(
                    title = "Upload Video",
                    subtitle = "Persistent Vault • Up to 50 GB per file",
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
                    // Storage & Format Card
                    item {
                        GlowingStudioCard(
                            accentBrush = Brush.linearGradient(listOf(ElectricBlue, VividPurple)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CloudUpload,
                                        contentDescription = "Upload Videos",
                                        tint = CyanGlow,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Select Videos From Phone Gallery",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Supports MP4, MKV, MOV, WebM • Multi-select supported",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.SdStorage,
                                            contentDescription = "Available Storage",
                                            tint = EmeraldLive,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Free: ${Formatters.formatBytes(storageOverview.availableDeviceBytes)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = EmeraldLive
                                        )
                                    }
                                    Text(
                                        text = "Max: 50.00 GB / file",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CyanGlow
                                    )
                                }

                                Text(
                                    text = "Videos are copied via 2 MB chunked file streaming directly into permanent app storage without loading files into RAM.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = {
                                        safPickerLauncher.launch(
                                            arrayOf(
                                                "video/*",
                                                "video/mp4",
                                                "video/x-matroska",
                                                "video/quicktime",
                                                "video/webm"
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("select_videos_saf_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FolderOpen,
                                        contentDescription = "Browse device videos"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Select Videos (Gallery / Files)",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }

                    // Active & Recent Imports Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Import Queue (${importTasks.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (importTasks.any {
                                    it.status == ImportStatus.COMPLETED || it.status == ImportStatus.FAILED
                                }
                            ) {
                                OutlinedButton(
                                    onClick = onClearCompleted,
                                    modifier = Modifier.testTag("clear_completed_imports_button")
                                ) {
                                    Text("Clear Finished")
                                }
                            }
                        }
                    }

                    if (importTasks.isEmpty()) {
                        item {
                            EmptyStatePanel(
                                icon = Icons.Filled.CloudUpload,
                                title = "Ready for Upload",
                                description = "Tap 'Select Videos' above to import one or multiple videos up to 50 GB each. Stored videos remain permanently available even after restarting your phone.",
                                actionButton = {
                                    OutlinedButton(
                                        onClick = onOpenMyVideos,
                                        modifier = Modifier.testTag("view_library_button")
                                    ) {
                                        Text("Open My Videos Library")
                                    }
                                }
                            )
                        }
                    } else {
                        items(importTasks, key = { it.id }) { task ->
                            ImportProgressCard(task = task)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportProgressCard(task: ImportTaskProgress) {
    GlowingStudioCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                when (task.status) {
                    ImportStatus.COMPLETED -> Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Import completed",
                        tint = EmeraldLive
                    )
                    ImportStatus.FAILED -> Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = "Import failed",
                        tint = LiveCrimson
                    )
                    else -> Text(
                        text = "${task.percentage}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = CyanGlow
                    )
                }
            }

            if (task.status == ImportStatus.COPYING ||
                task.status == ImportStatus.QUEUED ||
                task.status == ImportStatus.EXTRACTING_METADATA
            ) {
                LinearProgressIndicator(
                    progress = { task.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = ElectricBlue,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }

            val statusLine = when (task.status) {
                ImportStatus.QUEUED -> "Queued for import..."
                ImportStatus.COPYING ->
                    "${Formatters.formatBytes(task.bytesCopied)} / " +
                        if (task.totalBytes > 0) Formatters.formatBytes(task.totalBytes) else "Calculating..."
                ImportStatus.EXTRACTING_METADATA -> "Generating thumbnail & reading tracks..."
                ImportStatus.COMPLETED -> "Stored permanently (${Formatters.formatBytes(task.totalBytes)})"
                ImportStatus.FAILED -> task.errorMessage ?: "Import failed"
            }

            Text(
                text = statusLine,
                style = MaterialTheme.typography.bodyMedium,
                color = if (task.status == ImportStatus.FAILED) LiveCrimson else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
