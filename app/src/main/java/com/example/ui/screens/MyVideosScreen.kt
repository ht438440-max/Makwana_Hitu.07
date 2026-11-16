package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.db.VideoEntity
import com.example.data.storage.Formatters
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GlowingStudioCard
import com.example.ui.components.StudioAmbientBackground
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.VividPurple
import com.example.ui.viewmodel.VideoSortOrder
import java.io.File

@Composable
fun MyVideosScreen(
    videos: List<VideoEntity>,
    searchQuery: String,
    sortOrder: VideoSortOrder,
    onSearchChange: (String) -> Unit,
    onSortChange: (VideoSortOrder) -> Unit,
    onPreviewVideo: (VideoEntity) -> Unit,
    onGoLiveVideo: (VideoEntity) -> Unit,
    onRenameVideo: (Long, String) -> Unit,
    onDeleteVideo: (VideoEntity) -> Unit,
    onNavigateUpload: () -> Unit,
    onBack: () -> Unit
) {
    var videoToRename by remember { mutableStateOf<VideoEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var videoToDelete by remember { mutableStateOf<VideoEntity?>(null) }

    StudioAmbientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                StudioTopBar(
                    title = "My Videos",
                    subtitle = "${videos.size} video${if (videos.size == 1) "" else "s"} in permanent vault",
                    onBackClick = onBack
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onNavigateUpload,
                    containerColor = ElectricBlue,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("my_videos_upload_fab")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Upload new video"
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp)
                ) {
                    // Search & Sort controls
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("video_search_input"),
                            placeholder = { Text("Search stored videos...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search"
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VideoSortOrder.entries.forEach { order ->
                                FilterChip(
                                    selected = sortOrder == order,
                                    onClick = { onSortChange(order) },
                                    label = { Text(order.label) },
                                    modifier = Modifier.testTag("sort_${order.name.lowercase()}")
                                )
                            }
                        }
                    }

                    if (videos.isEmpty()) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            EmptyStatePanel(
                                icon = Icons.Filled.VideoLibrary,
                                title = if (searchQuery.isBlank()) "No Videos Uploaded Yet" else "No Matching Videos",
                                description = if (searchQuery.isBlank()) {
                                    "Import pre-recorded MP4, MKV, or MOV videos up to 50 GB from your phone gallery to store permanently and stream live."
                                } else {
                                    "Try clearing your search filter or uploading a new video."
                                },
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
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(videos, key = { it.id }) { video ->
                                VideoLibraryCard(
                                    video = video,
                                    onPreview = { onPreviewVideo(video) },
                                    onGoLive = { onGoLiveVideo(video) },
                                    onRename = {
                                        renameText = video.title
                                        videoToRename = video
                                    },
                                    onDelete = { videoToDelete = video }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    videoToRename?.let { target ->
        AlertDialog(
            onDismissRequest = { videoToRename = null },
            title = { Text("Rename Video") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Video Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_video_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameVideo(target.id, renameText)
                        videoToRename = null
                    },
                    enabled = renameText.isNotBlank(),
                    modifier = Modifier.testTag("confirm_rename_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    videoToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = { Text("Delete Stored Video?") },
            text = {
                Text(
                    "\"${target.title}\" (${Formatters.formatBytes(target.sizeBytes)}) will be permanently removed from app storage. This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteVideo(target)
                        videoToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson),
                    modifier = Modifier.testTag("confirm_delete_video_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VideoLibraryCard(
    video: VideoEntity,
    onPreview: () -> Unit,
    onGoLive: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    GlowingStudioCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("video_card_${video.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(width = 112.dp, height = 74.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, ElectricBlue.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val thumbFile = video.thumbnailPath?.let { File(it) }?.takeIf { it.exists() }
                    if (thumbFile != null) {
                        AsyncImage(
                            model = thumbFile,
                            contentDescription = "Thumbnail for ${video.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.VideoLibrary,
                            contentDescription = "Video thumbnail placeholder",
                            tint = CyanGlow,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = Formatters.formatDurationMs(video.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${Formatters.formatBytes(video.sizeBytes)} • ${video.width}x${video.height}",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanGlow
                    )
                    Text(
                        text = "Uploaded ${Formatters.formatDateOnly(video.uploadedAtMs)} • " +
                            if (video.hasAudio) "Embedded Audio" else "Video Only",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPreview,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("preview_video_${video.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Preview video",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Preview")
                }

                Button(
                    onClick = onGoLive,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("go_live_video_${video.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = VividPurple)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LiveTv,
                        contentDescription = "Go live with video",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Go Live")
                }

                IconButton(
                    onClick = onRename,
                    modifier = Modifier.testTag("rename_video_${video.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Rename video",
                        tint = CyanGlow
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_video_${video.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete video",
                        tint = LiveCrimson
                    )
                }
            }
        }
    }
}
