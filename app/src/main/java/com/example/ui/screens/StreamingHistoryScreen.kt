package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.StreamHistoryEntity
import com.example.data.db.StreamStatus
import com.example.data.storage.Formatters
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GlowingStudioCard
import com.example.ui.components.LivePillBadge
import com.example.ui.components.StudioAmbientBackground
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.LiveCrimson

@Composable
fun StreamingHistoryScreen(
    history: List<StreamHistoryEntity>,
    onDeleteEntry: (Long) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit
) {
    var confirmClearAll by remember { mutableStateOf(false) }

    StudioAmbientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                StudioTopBar(
                    title = "Streaming History",
                    subtitle = "${history.size} recorded session${if (history.size == 1) "" else "s"}",
                    onBackClick = onBack,
                    actions = {
                        if (history.isNotEmpty()) {
                            IconButton(
                                onClick = { confirmClearAll = true },
                                modifier = Modifier.testTag("clear_all_history_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = "Clear all history",
                                    tint = LiveCrimson
                                )
                            }
                        }
                    }
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
                if (history.isEmpty()) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        EmptyStatePanel(
                            icon = Icons.Filled.History,
                            title = "No Live Streams Yet",
                            description = "Your past YouTube, Facebook, and custom RTMP broadcasts with dates, durations, and statuses will appear here."
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(history, key = { it.id }) { item ->
                            HistoryCard(item = item, onDelete = { onDeleteEntry(item.id) })
                        }
                    }
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear Streaming History?") },
            text = { Text("Remove all locally saved live stream history logs? Stored videos will not be affected.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        confirmClearAll = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HistoryCard(
    item: StreamHistoryEntity,
    onDelete: () -> Unit
) {
    val statusColor = when (item.status) {
        StreamStatus.LIVE.name -> LiveCrimson
        StreamStatus.COMPLETED.name -> EmeraldLive
        StreamStatus.PAUSED.name -> AmberWarn
        StreamStatus.FAILED.name -> LiveCrimson
        else -> CyanGlow
    }

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
                    text = item.videoTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                LivePillBadge(text = item.status, color = statusColor)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.platform,
                    style = MaterialTheme.typography.labelLarge,
                    color = CyanGlow
                )
                Text(
                    text = "Duration: ${Formatters.formatDurationMs(item.durationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${Formatters.formatDateOnly(item.startedAtMs)} • ${Formatters.formatTimeOnly(item.startedAtMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${item.resolutionLabel} • ${item.videoBitrateKbps} kbps • ${item.fps} FPS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete history entry",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item.errorMessage?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LiveCrimson
                )
            }
        }
    }
}
