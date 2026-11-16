package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.db.StreamStatus
import com.example.data.storage.Formatters
import com.example.service.ActiveBroadcastInfo
import com.example.ui.components.GlowingStudioCard
import com.example.ui.components.LivePillBadge
import com.example.ui.components.StudioAmbientBackground
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.VividPurple
import java.io.File

@Composable
fun LiveControlScreen(
    activeBroadcast: ActiveBroadcastInfo,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onToggleLoop: (Boolean) -> Unit,
    onNavigateSetup: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val telemetry = activeBroadcast.telemetry
    val video = activeBroadcast.video

    StudioAmbientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                StudioTopBar(
                    title = "Live Broadcast Console",
                    subtitle = "${activeBroadcast.platform.displayName} • ${video?.title ?: "Ready"}",
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Silent Visual Preview (ZERO audio output on phone speaker)
                    if (video != null && File(video.filePath).exists()) {
                        val silentPreviewPlayer = remember(video.filePath) {
                            ExoPlayer.Builder(context).build().apply {
                                // Strictly disable audio renderer & mute volume so speaker stays 100% silent
                                trackSelectionParameters = trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                                    .build()
                                volume = 0f
                                repeatMode = if (activeBroadcast.loopEnabled) {
                                    Player.REPEAT_MODE_ONE
                                } else {
                                    Player.REPEAT_MODE_OFF
                                }
                                setMediaItem(MediaItem.fromUri(Uri.fromFile(File(video.filePath))))
                                prepare()
                                playWhenReady = telemetry.status == StreamStatus.LIVE
                            }
                        }

                        LaunchedEffect(telemetry.status, activeBroadcast.loopEnabled) {
                            silentPreviewPlayer.volume = 0f
                            silentPreviewPlayer.repeatMode = if (activeBroadcast.loopEnabled) {
                                Player.REPEAT_MODE_ONE
                            } else {
                                Player.REPEAT_MODE_OFF
                            }
                            silentPreviewPlayer.playWhenReady = telemetry.status == StreamStatus.LIVE
                        }

                        DisposableEffect(silentPreviewPlayer) {
                            onDispose { silentPreviewPlayer.release() }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.Black)
                                .border(1.dp, ElectricBlue.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = silentPreviewPlayer
                                        useController = false
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            val badgeColor = when (telemetry.status) {
                                StreamStatus.LIVE -> LiveCrimson
                                StreamStatus.PAUSED -> AmberWarn
                                StreamStatus.RECONNECTING -> CyanGlow
                                StreamStatus.COMPLETED -> EmeraldLive
                                StreamStatus.FAILED -> LiveCrimson
                                StreamStatus.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            LivePillBadge(
                                text = telemetry.status.label.uppercase(),
                                color = badgeColor,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Speaker Muted • Mic Off",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldLive
                                )
                            }
                        }
                    }

                    // Live Telemetry Card
                    GlowingStudioCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Broadcast Telemetry",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = Formatters.formatDurationMs(telemetry.elapsedMs),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanGlow,
                                    modifier = Modifier.testTag("live_duration_text")
                                )
                            }

                            TelemetryLine("Connection Status", telemetry.connectionMessage)
                            TelemetryLine("Resolution / FPS", "${telemetry.activeResolutionLabel} • ${telemetry.activeFps} FPS")
                            TelemetryLine("Live Bitrate", "${telemetry.currentBitrateKbps} kbps")
                            TelemetryLine(
                                "Pipeline Mode",
                                if (telemetry.isPassthrough) "Direct Passthrough (No Re-encode)" else "Hardware H.264/AAC Transcode"
                            )
                            TelemetryLine(
                                "Audio Routing",
                                if (telemetry.hasAudioTrack) "Embedded Video Audio Only (Speaker & Mic Off)" else "Video-Only Stream (No Audio Track)"
                            )
                            TelemetryLine("Loop Cycles", telemetry.loopCount.toString())
                        }
                    }

                    // Error Message Banner
                    telemetry.errorMessage?.let { err ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(LiveCrimson.copy(alpha = 0.16f))
                                .border(1.dp, LiveCrimson.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = "Stream error",
                                tint = LiveCrimson
                            )
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodyMedium,
                                color = LiveCrimson
                            )
                        }
                    }

                    // Controls: Pause / Resume / Stop / Loop
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Loop,
                                        contentDescription = "Loop video",
                                        tint = VividPurple
                                    )
                                    Column {
                                        Text(
                                            text = "Continuous Loop Mode",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Restart video automatically without stopping stream",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = activeBroadcast.loopEnabled,
                                    onCheckedChange = onToggleLoop,
                                    modifier = Modifier.testTag("live_loop_switch")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (activeBroadcast.isActiveSession) {
                                    val isPaused = telemetry.status == StreamStatus.PAUSED
                                    OutlinedButton(
                                        onClick = { if (isPaused) onResume() else onPause() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp)
                                            .testTag("live_pause_resume_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                            contentDescription = if (isPaused) "Resume streaming" else "Pause streaming"
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isPaused) "Resume" else "Pause")
                                    }

                                    Button(
                                        onClick = onStop,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp)
                                            .testTag("live_stop_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Stop,
                                            contentDescription = "Stop streaming"
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Stop Streaming")
                                    }
                                } else {
                                    Button(
                                        onClick = onNavigateSetup,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .testTag("live_new_stream_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                    ) {
                                        Text("Configure New Stream")
                                    }
                                }
                            }
                        }
                    }

                    // Background & Screen Lock Assurance Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ElectricBlue.copy(alpha = 0.12f))
                            .border(1.dp, ElectricBlue.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BatteryChargingFull,
                            contentDescription = "Background streaming active",
                            tint = CyanGlow
                        )
                        Column {
                            Text(
                                text = "Background & Screen-Lock Ready",
                                style = MaterialTheme.typography.labelLarge,
                                color = CyanGlow
                            )
                            Text(
                                text = "Foreground broadcast service holds CPU & Wi-Fi locks so streaming continues when minimized or screen is locked.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeOff,
                            contentDescription = "Audio isolation verified",
                            tint = EmeraldLive,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Microphone & Loudspeaker strictly bypassed",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldLive
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
