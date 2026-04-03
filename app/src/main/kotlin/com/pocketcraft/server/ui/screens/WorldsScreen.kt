package com.pocketcraft.server.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pocketcraft.server.WorldImporter
import com.pocketcraft.server.ui.components.FlatEmojiIcon
import com.pocketcraft.server.ui.components.GameCard
import com.pocketcraft.server.ui.components.PocketWorldIcon
import com.pocketcraft.server.ui.theme.PocketColors
import kotlinx.coroutines.launch

@Composable
fun WorldsScreen(
    stateHolder: ServerStateHolder,
    onOpenWorldSetup: (Boolean) -> Unit = {},
    onChangeVersion: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val animatedBackupProgress by animateFloatAsState(
        targetValue = (stateHolder.backupProgressPercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500),
        label = "worlds_backup_progress"
    )
    var showResetDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf<BackupEntry?>(null) }
    var showDeleteDialog by remember { mutableStateOf<BackupEntry?>(null) }
    var showDeleteWorldDialog by remember { mutableStateOf<WorldEntry?>(null) }
    var downloadActionLocked by remember { mutableStateOf(false) }
    var isImportingWorld by remember { mutableStateOf(false) }
    var importDimension by remember { mutableStateOf("overworld") }
    val worldPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                val activeWorld = stateHolder.config.worldName.ifBlank { "world" }
                val importFolder = when (importDimension) {
                    "nether" -> "${activeWorld}_nether"
                    "end" -> "${activeWorld}_the_end"
                    else -> activeWorld
                }
                isImportingWorld = true
                val result = WorldImporter.importWorld(context, it, stateHolder.versionLabel, importFolder)
                isImportingWorld = false
                if (result.isSuccess) {
                    Toast.makeText(context, "$importFolder imported successfully!", Toast.LENGTH_SHORT).show()
                    stateHolder.refreshAll()
                } else {
                    Toast.makeText(context, "Import failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Current World Card
        item {
            SectionLabel("CURRENT WORLD")
            Spacer(Modifier.height(8.dp))
            GameCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PocketColors.Primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        PocketWorldIcon(modifier = Modifier.size(32.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stateHolder.config.worldName,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Storage,
                                null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${stateHolder.worldSizeMb} MB on disk",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val msg = stateHolder.createBackup()
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !stateHolder.isBackingUp && !stateHolder.isRestoringBackup,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PocketColors.Primary)
                    ) {
                        Text(
                            text = if (stateHolder.isBackingUp) "BACKING UP..." else "BACKUP",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = stateHolder.status == ServerStatus.OFFLINE,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PocketColors.Offline)
                    ) {
                        Text("RESET", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        if (downloadActionLocked || stateHolder.isDownloadingBackup) return@OutlinedButton
                        downloadActionLocked = true
                        scope.launch {
                            try {
                                val latestBackup = stateHolder.backups.maxByOrNull { it.file.lastModified() }
                                if (latestBackup == null) {
                                    Toast.makeText(context, "Create a backup first, then download it.", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                Toast.makeText(context, stateHolder.downloadBackup(latestBackup), Toast.LENGTH_SHORT).show()
                            } finally {
                                downloadActionLocked = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !stateHolder.isDownloadingBackup && !downloadActionLocked,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PocketColors.DownloadBlue,
                        disabledContentColor = PocketColors.DownloadBlue.copy(alpha = 0.45f)
                    )
                ) {
                    Text("DOWNLOAD WORLD", fontWeight = FontWeight.Bold)
                }

                if (stateHolder.isBackingUp) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { animatedBackupProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${stateHolder.backupProgressPercent}% - ${stateHolder.backupStatusMessage}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("WORLD SLOTS")
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { onOpenWorldSetup(true) },
                    enabled = stateHolder.status == ServerStatus.OFFLINE,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ADD", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Slide left to delete. Active worlds can only be deleted when the server is stopped.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
        }

        items(stateHolder.worlds) { world ->
            SwipeableWorldSlotItem(
                world = world,
                canSwitch = stateHolder.status == ServerStatus.OFFLINE,
                canDelete = stateHolder.worlds.size > 1 && (stateHolder.status == ServerStatus.OFFLINE || !world.isActive),
                onActivate = {
                    scope.launch {
                        val msg = stateHolder.setActiveWorld(world.name)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onRequestDelete = {
                    showDeleteWorldDialog = world
                }
            )
        }

        // Upload Dimension Cards
        item {
            SectionLabel("IMPORT DIMENSIONS")
            Spacer(Modifier.height(8.dp))
            GameCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Import guide",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Aternos exports can be uploaded as 3 separate ZIPs: Overworld, Nether, and The End.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Most other world backups already include all dimensions in one archive, so a single world backup import is enough.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val activeWorld = stateHolder.config.worldName.ifBlank { "world" }
                DimensionUploadRow(
                    title = "Upload Overworld",
                    subtitle = "Main world ($activeWorld)",
                    icon = "🌍",
                    isImporting = isImportingWorld && importDimension == "overworld",
                    serverOffline = stateHolder.status == ServerStatus.OFFLINE,
                    onUpload = {
                        importDimension = "overworld"
                        worldPickerLauncher.launch("application/zip")
                    }
                )
                DimensionUploadRow(
                    title = "Upload Nether",
                    subtitle = "Nether dimension (${activeWorld}_nether)",
                    icon = "🔥",
                    isImporting = isImportingWorld && importDimension == "nether",
                    serverOffline = stateHolder.status == ServerStatus.OFFLINE,
                    onUpload = {
                        importDimension = "nether"
                        worldPickerLauncher.launch("application/zip")
                    }
                )
                DimensionUploadRow(
                    title = "Upload The End",
                    subtitle = "End dimension (${activeWorld}_the_end)",
                    icon = "🌑",
                    isImporting = isImportingWorld && importDimension == "end",
                    serverOffline = stateHolder.status == ServerStatus.OFFLINE,
                    onUpload = {
                        importDimension = "end"
                        worldPickerLauncher.launch("application/zip")
                    }
                )
            }
        }

        // Backups Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("BACKUP HISTORY")
                Spacer(Modifier.weight(1f))
                Text(
                    "${stateHolder.backups.size} files",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (stateHolder.backups.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("No backups found", color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            items(stateHolder.backups) { backup ->
                BackupItem(
                    backup = backup,
                    onRestore = { showRestoreDialog = backup },
                    onDownload = {
                        scope.launch {
                            Toast.makeText(context, stateHolder.downloadBackup(backup), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDelete = { showDeleteDialog = backup },
                    enabled = stateHolder.status == ServerStatus.OFFLINE,
                    isDownloading = stateHolder.isDownloadingBackup
                )
            }
        }
    }

    // Dialogs
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset World?") },
            text = { Text("This will permanently delete the current world folder. Make sure you have a backup if you want to keep your progress.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val msg = stateHolder.resetWorld()
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    showResetDialog = false
                }) {
                    Text("RESET WORLD", color = PocketColors.Offline, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    showRestoreDialog?.let { backup ->
        AlertDialog(
            onDismissRequest = { showRestoreDialog = null },
            title = { Text("Restore Backup?") },
            text = {
                Text(
                    "This will restore:\n" +
                        "- Worlds and dimension data\n" +
                        "- Plugins, mods, and resource packs\n" +
                        "- Server configs and settings\n\n" +
                        "Your current server files will be overwritten.\n" +
                        "Make sure the server is stopped before restoring."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val msg = stateHolder.restoreBackup(backup)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    showRestoreDialog = null
                }) {
                    Text("RESTORE", color = PocketColors.Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = null }) {
                    Text("CANCEL")
                }
            }
        )
    }

    showDeleteDialog?.let { backup ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Backup?") },
            text = { Text("Are you sure you want to delete ${backup.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val msg = stateHolder.deleteBackup(backup)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    showDeleteDialog = null
                }) {
                    Text("DELETE", color = PocketColors.Offline, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("CANCEL")
                }
            }
        )
    }

    showDeleteWorldDialog?.let { world ->
        AlertDialog(
            onDismissRequest = { showDeleteWorldDialog = null },
            title = { Text("Delete World?") },
            text = {
                Text(
                    if (world.isActive) {
                        "Delete ${world.name}? PocketCraft will switch to another saved world first."
                    } else {
                        "Delete ${world.name}? This removes the world from storage."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val msg = stateHolder.deleteWorld(world.name)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    showDeleteWorldDialog = null
                }) {
                    Text("DELETE", color = PocketColors.Offline, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteWorldDialog = null }) {
                    Text("CANCEL")
                }
            }
        )
    }

}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SwipeableWorldSlotItem(
    world: WorldEntry,
    canSwitch: Boolean,
    canDelete: Boolean,
    onActivate: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target == SwipeToDismissBoxValue.EndToStart && canDelete) {
                onRequestDelete()
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = canDelete,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PocketColors.Offline.copy(alpha = 0.14f))
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Delete",
                        color = PocketColors.Offline,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = PocketColors.Offline
                    )
                }
            }
        }
    ) {
        GameCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = canSwitch && !world.isActive, onClick = onActivate)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (world.photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = world.photoUrl,
                            contentDescription = "${world.name} icon",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        PocketWorldIcon(modifier = Modifier.size(18.dp))
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(world.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        text = "${world.sizeMb} MB on disk",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (world.isActive) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = PocketColors.Primary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            "ACTIVE",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PocketColors.Primary
                        )
                    }
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Activate world",
                        tint = if (canSwitch) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (!canSwitch && !world.isActive) {
                Text(
                    "Stop server to switch worlds",
                    fontSize = 11.sp,
                    color = PocketColors.Offline,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun BackupItem(
    backup: com.pocketcraft.server.ui.screens.BackupEntry,
    onRestore: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean,
    isDownloading: Boolean
) {
    GameCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = backup.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1
                )
                Text(
                    text = "${backup.sizeMb} MB • ${backup.date}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onRestore, enabled = enabled) {
                Icon(Icons.Default.Restore, "Restore", tint = if (enabled) PocketColors.Primary else MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = onDownload, enabled = !isDownloading) {
                Icon(Icons.Default.Download, "Download backup to phone", tint = if (!isDownloading) PocketColors.PrimaryDark else MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = PocketColors.Offline.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun DimensionUploadRow(
    title: String,
    subtitle: String,
    icon: String,
    isImporting: Boolean,
    serverOffline: Boolean,
    onUpload: () -> Unit
) {
    GameCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                FlatEmojiIcon(icon, modifier = Modifier.size(20.dp), tint = PocketColors.PrimaryDark)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Button(
                    onClick = onUpload,
                    enabled = serverOffline,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("UPLOAD", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        if (!serverOffline) {
            Text(
                "Stop server to upload",
                fontSize = 11.sp,
                color = PocketColors.Offline,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
