package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.db.VideoEntity
import com.example.data.storage.Formatters
import com.example.ui.components.GlowingStudioCard
import com.example.ui.components.StudioAmbientBackground
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.VividPurple
import java.io.File

@Composable
fun VideoPreviewScreen(
    video: VideoEntity?,
    onGoLive: (VideoEntity) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    StudioAmbientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                StudioTopBar(
                    title = video?.title ?: "Video Preview",
                    subtitle = "Media3 ExoPlayer Preview",
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
                if (video == null) {
                    Text(
                        text = "Video not found.",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val exoPlayer = remember(video.filePath) {
                        ExoPlayer.Builder(context).build().apply {
                            val mediaItem = MediaItem.fromUri(Uri.fromFile(File(video.filePath)))
                            setMediaItem(mediaItem)
                            prepare()
                            playWhenReady = true
                        }
                    }

                    DisposableEffect(exoPlayer) {
                        onDispose {
                            exoPlayer.release()
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.Black)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = exoPlayer
                                        useController = true
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        GlowingStudioCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = video.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                MetadataRow("File Name", video.fileName)
                                MetadataRow("File Size", Formatters.formatBytes(video.sizeBytes))
                                MetadataRow("Duration", Formatters.formatDurationMs(video.durationMs))
                                MetadataRow("Resolution", "${video.width} × ${video.height}")
                                MetadataRow("Video Codec", video.videoMimeType)
                                MetadataRow(
                                    "Original Audio",
                                    if (video.hasAudio) video.audioMimeType ?: "Audio Track Present" else "No Audio Track"
                                )
                                MetadataRow("Uploaded", Formatters.formatDateTime(video.uploadedAtMs))
                            }
                        }

                        Button(
                            onClick = { onGoLive(video) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("preview_go_live_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = VividPurple)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LiveTv,
                                contentDescription = "Go Live"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Go Live With This Video", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
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
            style = MaterialTheme.typography.labelMedium,
            color = CyanGlow
        )
    }
}
