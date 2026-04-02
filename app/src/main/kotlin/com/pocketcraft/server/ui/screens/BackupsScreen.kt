package com.pocketcraft.server.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.components.GameCard
import com.pocketcraft.server.ui.theme.PocketColors
import kotlinx.coroutines.launch

@Composable
fun BackupsScreen(
    stateHolder: ServerStateHolder,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var restoreTarget by remember { mutableStateOf<BackupEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<BackupEntry?>(null) }
    val animatedBackupProgress by animateFloatAsState(
        targetValue = (stateHolder.backupProgressPercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500),
        label = "backup_progress"
    )
    val animatedRestoreProgress by animateFloatAsState(
        targetValue = (stateHolder.restoreProgressPercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500),
        label = "restore_progress"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Show backup progress if in progress
        if (stateHolder.isBackingUp) {
            item {
                GameCard(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "⏳ Creating Backup",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        LinearProgressIndicator(
                            progress = { animatedBackupProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(999.dp))
                        )
                        Text(
                            text = "${stateHolder.backupProgressPercent}% - ${stateHolder.backupStatusMessage}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Backing up: worlds, plugins, mods, resource packs, configs, and server files",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Show restore progress if in progress
        if (stateHolder.isRestoringBackup) {
            item {
                GameCard(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "📥 Restoring Backup",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        LinearProgressIndicator(
                            progress = { animatedRestoreProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(999.dp))
                        )
                        Text(
                            text = "${stateHolder.restoreProgressPercent}% - ${stateHolder.restoreStatusMessage}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "⚠️ Do not close the app while restoring",
                            fontSize = 11.sp,
                            color = PocketColors.Offline,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            DuoButton(
                text = if (!stateHolder.isBackingUp) "CREATE BACKUP NOW" else "BACKUP IN PROGRESS...",
                icon = Icons.Filled.CloudUpload,
                onClick = {
                    scope.launch {
                        val result = stateHolder.createBackup()
                        onMessage(result)
                    }
                },
                enabled = !stateHolder.isBackingUp && !stateHolder.isRestoringBackup,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Show save location if backup was just created
        if (stateHolder.backupSaveLocation.isNotEmpty() && !stateHolder.isBackingUp) {
            item {
                GameCard(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "💾 Backups Location:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = stateHolder.backupSaveLocation,
                            fontSize = 11.sp,
                            color = PocketColors.PrimaryDark
                        )
                        Text(
                            text = "Backups persist even after uninstalling the app",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = "SAVED BACKUPS",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (stateHolder.backups.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GameCard(modifier = Modifier.fillMaxWidth()) {
                        Text(text = "☁️", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No backups yet",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(stateHolder.backups, key = { it.name }) { backup ->
                GameCard(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(text = "\uD83D\uDCBE", fontSize = 28.sp)
                            androidx.compose.foundation.layout.Column {
                                Text(
                                    text = backup.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${backup.sizeMb}MB • ${backup.date}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { restoreTarget = backup },
                                enabled = !stateHolder.isBackingUp && !stateHolder.isRestoringBackup
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Restore,
                                    contentDescription = "Restore backup",
                                    tint = PocketColors.PrimaryDark
                                )
                            }
                            IconButton(
                                onClick = { deleteTarget = backup },
                                enabled = !stateHolder.isBackingUp && !stateHolder.isRestoringBackup
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete backup",
                                    tint = PocketColors.Offline
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    restoreTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text(text = "Restore backup?") },
            text = {
                Text(text = "This will restore the full server snapshot from ${target.name}. Current worlds, plugins, mods, packs, and configs will be replaced.")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { onMessage(stateHolder.restoreBackup(target)) }
                    restoreTarget = null
                }) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(text = "Delete backup?") },
            text = {
                Text(text = "This permanently removes ${target.name}.")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { onMessage(stateHolder.deleteBackup(target)) }
                    deleteTarget = null
                }) {
                    Text("Delete", color = PocketColors.Offline)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
