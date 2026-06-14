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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.pocketcraft.server.WorldImporter
import com.pocketcraft.server.ui.components.FlatEmojiIcon
import com.pocketcraft.server.ui.components.GameCard
import com.pocketcraft.server.ui.components.PocketWorldIcon
import com.pocketcraft.server.ui.components.AnimatedEntranceContainer
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.util.LocalAppStrings
import kotlinx.coroutines.launch
import com.pocketcraft.server.integrations.AccountManager
import com.pocketcraft.server.integrations.DriveBackupManager
import com.pocketcraft.server.integrations.RemoteDriveBackup
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.components.DuoButtonVariant
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Cloud
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun WorldsScreen(
    stateHolder: ServerStateHolder,
    onOpenWorldSetup: (Boolean) -> Unit = {},
    onChangeVersion: () -> Unit = {},
    onMessage: (String) -> Unit = {}
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val animatedBackupProgress by animateFloatAsState(
        targetValue = (stateHolder.backupProgressPercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500),
        label = "worlds_backup_progress"
    )
    var showResetDialog by remember { mutableStateOf(false) }
    var deletePlayerData by remember { mutableStateOf(false) }
    var deleteDatapacks by remember { mutableStateOf(false) }
    var deleteLogs by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf<BackupEntry?>(null) }
    var showDeleteDialog by remember { mutableStateOf<BackupEntry?>(null) }
    var restoringBackupName by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(stateHolder.isRestoringBackup) {
        if (!stateHolder.isRestoringBackup) {
            restoringBackupName = null
        }
    }
    var showImportGuide by remember { mutableStateOf(false) }
    var importDimension by remember { mutableStateOf("overworld") }
    val worldPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val activeWorld = stateHolder.activeWorld.ifBlank { "world" }
            val importFolder = activeWorld
            stateHolder.importWorldDimension(it, importFolder)
        }
    }
    var aternosExpanded by remember { mutableStateOf(false) }
    val backupZipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                val msg = stateHolder.importBackup(it)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Current World Card
        item {
            AnimatedEntranceContainer(index = 0) {
                Column {
                    SectionLabel(s.worldsSectionCurrentWorld)
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
                                    text = stateHolder.activeWorld,
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
                                        text = String.format(s.worldsMbOnDisk, stateHolder.worldSizeMb),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(16.dp))

                        DuoButton(
                            text = "RESET WORLD",
                            onClick = {
                                if (stateHolder.status != ServerStatus.OFFLINE) {
                                    onMessage(s.worldsStopBeforeReset)
                                } else {
                                    deletePlayerData = false
                                    deleteDatapacks = false
                                    deleteLogs = false
                                    showResetDialog = true
                                }
                            },
                            enabled = stateHolder.status == ServerStatus.OFFLINE,
                            modifier = Modifier.fillMaxWidth(),
                            variant = DuoButtonVariant.Danger,
                            minHeight = 40.dp
                        )

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
            }
        }

        item {
            AnimatedEntranceContainer(index = 1) {
                BackupsManagementCard(
                    stateHolder = stateHolder,
                    restoringBackupName = restoringBackupName,
                    onRestoringBackupNameChange = { restoringBackupName = it }
                )
            }
        }



        // Upload Dimension Cards (Redesigned Restore or Import card)
        item {
            AnimatedEntranceContainer(index = 5) {
                val isDark = com.pocketcraft.server.ui.theme.pocketIsDarkTheme()
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionLabel("RESTORE OR IMPORT")
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { showImportGuide = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Guide", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    GameCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Section 1: Full World ZIP
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Full World Backup (ZIP)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Import a single zip archive containing your complete world data.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                if (stateHolder.isRestoringBackup && restoringBackupName == null) {
                                    val animatedRestoreProgress by animateFloatAsState(
                                        targetValue = (stateHolder.restoreProgressPercent / 100f).coerceIn(0f, 1f),
                                        animationSpec = tween(durationMillis = 300),
                                        label = "worlds_restore_progress"
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        LinearProgressIndicator(
                                            progress = { animatedRestoreProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(999.dp)),
                                            color = PocketColors.Primary
                                        )
                                        Text(
                                            text = "${stateHolder.restoreProgressPercent}% - ${stateHolder.restoreStatusMessage}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    DuoButton(
                                        text = "UPLOAD WORLD ZIP",
                                        onClick = { backupZipPickerLauncher.launch("application/zip") },
                                        enabled = stateHolder.status == ServerStatus.OFFLINE,
                                        minHeight = 36.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            // Section 2: Separate Dimensions
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Separate Dimensions",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "For hosts like Aternos, upload individual dimension folders as ZIP files.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val activeWorld = stateHolder.activeWorld.ifBlank { "world" }
                                    DimensionRow(
                                        title = "Overworld (world)",
                                        isImporting = stateHolder.isImportingWorld && importDimension == "overworld",
                                        importProgress = stateHolder.importProgressPercent,
                                        enabled = stateHolder.status == ServerStatus.OFFLINE,
                                        onUpload = {
                                            importDimension = "overworld"
                                            worldPickerLauncher.launch("application/zip")
                                        }
                                    )
                                    DimensionRow(
                                        title = "Nether (world_nether)",
                                        isImporting = stateHolder.isImportingWorld && importDimension == "nether",
                                        importProgress = stateHolder.importProgressPercent,
                                        enabled = stateHolder.status == ServerStatus.OFFLINE,
                                        onUpload = {
                                            importDimension = "nether"
                                            worldPickerLauncher.launch("application/zip")
                                        }
                                    )
                                    DimensionRow(
                                        title = "End (world_the_end)",
                                        isImporting = stateHolder.isImportingWorld && importDimension == "end",
                                        importProgress = stateHolder.importProgressPercent,
                                        enabled = stateHolder.status == ServerStatus.OFFLINE,
                                        onUpload = {
                                            importDimension = "end"
                                            worldPickerLauncher.launch("application/zip")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    }


    // Dialogs
    if (showResetDialog) {
        val resetSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    resetSheetState.hide()
                    showResetDialog = false
                }
            },
            sheetState = resetSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(s.worldsResetTitle, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(
                    s.worldsResetDesc,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResetDeleteRow(
                        label = s.worldsResetRowWorld,
                        checked = true,
                        enabled = false,
                        onCheckedChange = {}
                    )
                    ResetDeleteRow(
                        label = s.worldsResetRowPlayers,
                        checked = deletePlayerData,
                        enabled = true,
                        onCheckedChange = { deletePlayerData = it }
                    )
                    ResetDeleteRow(
                        label = s.worldsResetRowDatapacks,
                        checked = deleteDatapacks,
                        enabled = true,
                        onCheckedChange = { deleteDatapacks = it }
                    )
                    ResetDeleteRow(
                        label = s.worldsResetRowLogs,
                        checked = deleteLogs,
                        enabled = true,
                        onCheckedChange = { deleteLogs = it }
                    )
                }
                Text(
                    s.worldsResetPreserved,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            if (stateHolder.status != ServerStatus.OFFLINE) {
                                onMessage(s.worldsStopBeforeReset)
                            } else {
                                val msg = stateHolder.resetWorld(
                                    deletePlayerData = deletePlayerData,
                                    deleteDatapacks = deleteDatapacks,
                                    deleteLogs = deleteLogs
                                )
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                resetSheetState.hide()
                                showResetDialog = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(s.worldsResetDeleteSelected, color = PocketColors.Offline, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            resetSheetState.hide()
                            showResetDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(s.worldsCancel)
                }
            }
        }
    }

    showRestoreDialog?.let { backup ->
        val restoreSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    restoreSheetState.hide()
                    showRestoreDialog = null
                }
            },
            sheetState = restoreSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.worldsRestoreTitle, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(
                    s.worldsRestoreDesc,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            val msg = stateHolder.restoreBackup(backup)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            restoreSheetState.hide()
                            showRestoreDialog = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(s.worldsRestoreAction, color = PocketColors.Primary, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            restoreSheetState.hide()
                            showRestoreDialog = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(s.worldsCancel)
                }
            }
        }
    }

    showDeleteDialog?.let { backup ->
        val deleteBackupSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    deleteBackupSheetState.hide()
                    showDeleteDialog = null
                }
            },
            sheetState = deleteBackupSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.worldsDeleteBackupTitle, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(String.format(s.worldsDeleteBackupDesc, backup.name), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(
                    onClick = {
                        scope.launch {
                            val msg = stateHolder.deleteBackup(backup)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            deleteBackupSheetState.hide()
                            showDeleteDialog = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(s.worldsDeleteAction, color = PocketColors.Offline, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            deleteBackupSheetState.hide()
                            showDeleteDialog = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(s.worldsCancel)
                }
            }
        }
    }



    if (showImportGuide) {
        val importGuideSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    importGuideSheetState.hide()
                    showImportGuide = false
                }
            },
            sheetState = importGuideSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(s.worldsImportGuideSheetTitle, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(
                    s.worldsImportGuideText1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    s.worldsImportGuideText2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            importGuideSheetState.hide()
                            showImportGuide = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(s.worldsImportGuideClose)
                }
            }
        }
    }

}



@Composable
private fun ResetDeleteRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                onCheckedChange(!checked)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled
        )
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
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
private fun DimensionRow(
    title: String,
    isImporting: Boolean,
    importProgress: Float,
    enabled: Boolean,
    onUpload: () -> Unit
) {
    val isDark = com.pocketcraft.server.ui.theme.pocketIsDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
        )
        if (isImporting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                Text(
                    text = "${(importProgress * 100).toInt()}%",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            DuoButton(
                text = "UPLOAD",
                onClick = onUpload,
                enabled = enabled,
                minHeight = 28.dp,
                fillMaxWidth = false
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
        letterSpacing = 0.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun BackupsManagementCard(
    stateHolder: ServerStateHolder,
    restoringBackupName: String?,
    onRestoringBackupNameChange: (String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = LocalAppStrings.current
    val isDark = com.pocketcraft.server.ui.theme.pocketIsDarkTheme()

    var account by remember { mutableStateOf(AccountManager.currentDriveAccount(context)) }
    var cloudBackups by remember { mutableStateOf<List<RemoteDriveBackup>>(emptyList()) }
    var loadingCloudBackups by remember { mutableStateOf(false) }
    var cloudStatusMessage by remember { mutableStateOf("") }
    
    var localActionBusy by remember { mutableStateOf(false) }
    var cloudActionBusy by remember { mutableStateOf(false) }
    var actionStatusMessage by remember { mutableStateOf("") }
    var showCloudSignInDialog by remember { mutableStateOf(false) }
    var cloudUploadProgress by remember { mutableIntStateOf(0) }
    var cloudUploadHint by remember { mutableStateOf("") }
    var pendingDeleteBackup by remember { mutableStateOf<UnifiedBackup?>(null) }
    var removingBackupKeys by remember { mutableStateOf(setOf<String>()) }

    fun backupKey(backup: UnifiedBackup): String = if (backup.isCloud) {
        "cloud:${backup.remoteBackup?.id ?: backup.fullFileName}"
    } else {
        "local:${backup.fullFileName}"
    }

    // Fetch cloud backups
    fun refreshCloudBackups() {
        val signedInAccount = account
        if (signedInAccount != null) {
            loadingCloudBackups = true
            scope.launch(Dispatchers.IO) {
                runCatching {
                    DriveBackupManager.listWorldBackups(context, signedInAccount, stateHolder.activeWorld.ifBlank { "world" })
                }.onSuccess { list ->
                    withContext(Dispatchers.Main) {
                        cloudBackups = list
                        cloudStatusMessage = ""
                        loadingCloudBackups = false
                    }
                }.onFailure { err ->
                    withContext(Dispatchers.Main) {
                        cloudStatusMessage = "Failed: ${err.message}"
                        loadingCloudBackups = false
                    }
                }
            }
        } else {
            cloudBackups = emptyList()
            cloudStatusMessage = "Cloud account not connected in Settings"
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        cloudActionBusy = true
        AccountManager.completeGoogleSignIn(context, result.data) { googleAccount, _, errorMessage ->
            scope.launch {
                if (errorMessage != null) {
                    cloudStatusMessage = errorMessage
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                } else {
                    account = googleAccount ?: AccountManager.currentDriveAccount(context)
                    if (account != null) {
                        cloudStatusMessage = "Connected as ${account?.email ?: account?.displayName.orEmpty()}."
                        Toast.makeText(context, "Google Drive is now connected.", Toast.LENGTH_SHORT).show()
                        refreshCloudBackups()
                    } else {
                        cloudStatusMessage = "Google sign-in completed, but Drive access is not available yet."
                    }
                }
                cloudActionBusy = false
            }
        }
    }

    LaunchedEffect(stateHolder.activeWorld) {
        account = AccountManager.currentDriveAccount(context)
        refreshCloudBackups()
    }

    GameCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Backups & Restore",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
            )
            
            // Backup Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DuoButton(
                    text = if (localActionBusy) "BACKING UP..." else "BACKUP TO DEVICE",
                    enabled = !localActionBusy && !cloudActionBusy && stateHolder.status == ServerStatus.OFFLINE,
                    onClick = {
                        scope.launch {
                            localActionBusy = true
                            actionStatusMessage = "Creating device backup..."
                            val msg = stateHolder.createBackup()
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            actionStatusMessage = ""
                            localActionBusy = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    minHeight = 40.dp
                )

                // Cloud backup button — locked with a tap-interceptor overlay when not signed in
                Box(modifier = Modifier.weight(1f)) {
                    DuoButton(
                        text = if (cloudActionBusy) "UPLOADING..." else "BACKUP TO CLOUD",
                        enabled = account != null && !localActionBusy && !cloudActionBusy && stateHolder.status == ServerStatus.OFFLINE,
                        onClick = {
                            scope.launch {
                                val driveAccount = account
                                if (driveAccount == null) {
                                    Toast.makeText(context, "Connect Google Drive first.", Toast.LENGTH_LONG).show()
                                    return@launch
                                }
                                cloudActionBusy = true
                                cloudUploadProgress = 0
                                cloudUploadHint = "Keep PocketCraft open while your world is uploading."
                                try {
                                    actionStatusMessage = "Creating local backup..."
                                    val backupMsg = stateHolder.createBackup()
                                    if (!backupMsg.startsWith("Backup created:")) {
                                        Toast.makeText(context, backupMsg, Toast.LENGTH_LONG).show()
                                        cloudActionBusy = false
                                        return@launch
                                    }
                                    actionStatusMessage = "Uploading to Google Drive..."
                                    val uploadMsg = DriveBackupManager.uploadLatestWorldBackup(
                                        context,
                                        driveAccount,
                                        stateHolder.activeWorld.ifBlank { "world" }
                                    ) { progress, message ->
                                        withContext(Dispatchers.Main) {
                                            cloudUploadProgress = progress.coerceIn(0, 100)
                                            actionStatusMessage = message
                                        }
                                    }
                                    if (uploadMsg.startsWith("Uploaded ")) {
                                        cloudStatusMessage = uploadMsg
                                        Toast.makeText(context, uploadMsg, Toast.LENGTH_LONG).show()
                                        refreshCloudBackups()
                                    } else {
                                        cloudStatusMessage = uploadMsg
                                        Toast.makeText(context, uploadMsg, Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    val errMsg = e.message ?: "Backup upload failed."
                                    android.util.Log.e("WorldsScreen", "Cloud backup upload failed", e)
                                    cloudStatusMessage = "Upload failed: $errMsg"
                                    Toast.makeText(context, "Failed: $errMsg", Toast.LENGTH_LONG).show()
                                } finally {
                                    actionStatusMessage = ""
                                    cloudActionBusy = false
                                    cloudUploadProgress = 0
                                    cloudUploadHint = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        variant = if (account != null) DuoButtonVariant.Info else DuoButtonVariant.Secondary,
                        minHeight = 40.dp
                    )
                    // Transparent tap interceptor + lock badge when not signed in
                    if (account == null) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showCloudSignInDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            // Lock badge at top-end corner
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Sign in required",
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
            
            if (stateHolder.status != ServerStatus.OFFLINE) {
                Text(
                    text = "Stop the server to enable backups & restoring.",
                    fontSize = 11.sp,
                    color = PocketColors.Offline
                )
            }

            if (actionStatusMessage.isNotBlank()) {
                Text(
                    text = actionStatusMessage,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (cloudActionBusy) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { (cloudUploadProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp))
                            .height(8.dp),
                        color = PocketColors.DownloadBlue
                    )
                    Text(
                        text = "${cloudUploadProgress.coerceIn(0, 100)}% uploaded",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = cloudUploadHint.ifBlank { "Keep PocketCraft open while your cloud backup finishes." },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Unified backups list (Local + Cloud)
            val localUnified = stateHolder.backups.map {
                UnifiedBackup(
                    name = it.name.substringAfter("-").removeSuffix(".zip"),
                    fullFileName = it.name,
                    isCloud = false,
                    sizeMb = it.sizeMb,
                    dateLabel = it.date,
                    localBackup = it
                )
            }

            val cloudUnified = cloudBackups.map {
                UnifiedBackup(
                    name = it.name.substringAfter("-").removeSuffix(".zip"),
                    fullFileName = it.name,
                    isCloud = true,
                    sizeMb = 0L,
                    dateLabel = it.modifiedTime ?: "",
                    remoteBackup = it
                )
            }

            val unifiedList = remember(stateHolder.backups.toList(), cloudBackups, removingBackupKeys) {
                (localUnified + cloudUnified)
                    .filterNot { backupKey(it) in removingBackupKeys }
                    .sortedByDescending { it.name }
            }

            if (unifiedList.isEmpty()) {
                Text(
                    text = "No backups found",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    unifiedList.forEach { backup ->
                        val itemKey = backupKey(backup)
                        AnimatedVisibility(
                            visible = itemKey !in removingBackupKeys,
                            enter = fadeIn(tween(220)) + expandVertically(tween(260)),
                            exit = fadeOut(tween(180)) + shrinkVertically(tween(220))
                        ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val iconColor = if (backup.isCloud) PocketColors.DownloadBlue else PocketColors.Primary
                            Icon(
                                imageVector = if (backup.isCloud) Icons.Default.Cloud else Icons.Default.Computer,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = iconColor
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val displayBackupName = backup.name.take(18)
                                    Text(
                                        text = displayBackupName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (backup.isCloud) "(Cloud)" else "(Device)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = iconColor
                                    )
                                }
                                val subtext = buildString {
                                    if (backup.sizeMb > 0) {
                                        append("${backup.sizeMb} MB")
                                    }
                                    if (backup.dateLabel.isNotBlank()) {
                                        if (isNotEmpty()) append(" • ")
                                        append(backup.dateLabel)
                                    }
                                }
                                if (subtext.isNotBlank()) {
                                    Text(
                                        text = subtext,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (stateHolder.isRestoringBackup && restoringBackupName == backup.fullFileName) {
                                    val animatedRestoreProgress by animateFloatAsState(
                                        targetValue = (stateHolder.restoreProgressPercent / 100f).coerceIn(0f, 1f),
                                        animationSpec = tween(durationMillis = 300),
                                        label = "worlds_item_restore_progress"
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        LinearProgressIndicator(
                                            progress = { animatedRestoreProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(999.dp)),
                                            color = PocketColors.Primary
                                        )
                                        Text(
                                            text = "${stateHolder.restoreProgressPercent}% - ${stateHolder.restoreStatusMessage}",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            val actionsEnabled = stateHolder.status == ServerStatus.OFFLINE && !localActionBusy && !cloudActionBusy

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Restore Action
                                IconButton(
                                    onClick = {
                                        if (backup.isCloud) {
                                            scope.launch {
                                                onRestoringBackupNameChange(backup.fullFileName)
                                                cloudActionBusy = true
                                                actionStatusMessage = "Downloading cloud backup..."
                                                val tempFile = File(context.cacheDir, "drive_restore_${System.currentTimeMillis()}.zip")
                                                try {
                                                    DriveBackupManager.downloadBackup(context, account!!, backup.remoteBackup!!, tempFile)
                                                    actionStatusMessage = "Restoring backup..."
                                                    val msg = stateHolder.restoreBackupFile(tempFile, backup.remoteBackup.name)
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                                                } finally {
                                                    tempFile.delete()
                                                    onRestoringBackupNameChange(null)
                                                    actionStatusMessage = ""
                                                    cloudActionBusy = false
                                                }
                                            }
                                        } else {
                                            scope.launch {
                                                onRestoringBackupNameChange(backup.fullFileName)
                                                localActionBusy = true
                                                actionStatusMessage = "Restoring backup..."
                                                try {
                                                    val msg = stateHolder.restoreBackup(backup.localBackup!!)
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    onRestoringBackupNameChange(null)
                                                    actionStatusMessage = ""
                                                    localActionBusy = false
                                                }
                                            }
                                        }
                                    },
                                    enabled = actionsEnabled,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = "Restore",
                                        modifier = Modifier.size(20.dp),
                                        tint = if (actionsEnabled) iconColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }

                                // 2. Download/Export Action
                                val downloadEnabled = !localActionBusy && !cloudActionBusy
                                IconButton(
                                    onClick = {
                                        if (backup.isCloud) {
                                            scope.launch {
                                                cloudActionBusy = true
                                                actionStatusMessage = "Downloading from cloud..."
                                                val tempFile = File(context.cacheDir, "drive_download_${System.currentTimeMillis()}.zip")
                                                try {
                                                    DriveBackupManager.downloadBackup(context, account!!, backup.remoteBackup!!, tempFile)
                                                    actionStatusMessage = "Saving to Downloads..."
                                                    val entry = BackupEntry(
                                                        name = backup.remoteBackup.name,
                                                        sizeMb = (tempFile.length() / (1024L * 1024L)).coerceAtLeast(0L),
                                                        date = "",
                                                        file = tempFile
                                                    )
                                                    val msg = stateHolder.downloadBackup(entry)
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                                                } finally {
                                                    tempFile.delete()
                                                    actionStatusMessage = ""
                                                    cloudActionBusy = false
                                                }
                                            }
                                        } else {
                                            scope.launch {
                                                localActionBusy = true
                                                actionStatusMessage = "Saving to Downloads..."
                                                val msg = stateHolder.downloadBackup(backup.localBackup!!)
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                actionStatusMessage = ""
                                                localActionBusy = false
                                            }
                                        }
                                    },
                                    enabled = downloadEnabled,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download",
                                        modifier = Modifier.size(20.dp),
                                        tint = if (downloadEnabled) iconColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }

                                // 3. Delete Action
                                val deleteEnabled = !localActionBusy && !cloudActionBusy
                                IconButton(
                                    onClick = {
                                        pendingDeleteBackup = backup
                                    },
                                    enabled = deleteEnabled,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        modifier = Modifier.size(20.dp),
                                        tint = if (deleteEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                        }
                    }
                }
            }

            if (account == null) {
                Text(
                    text = "Cloud backups are disabled. Connect Google account in Settings to enable.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (loadingCloudBackups) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    Text("Loading cloud backups...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (cloudStatusMessage.isNotBlank()) {
                Text(
                    text = cloudStatusMessage,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    if (showCloudSignInDialog) {
        Dialog(onDismissRequest = { showCloudSignInDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 10.dp,
                shadowElevation = 20.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = PocketColors.Primary.copy(alpha = 0.18f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(PocketColors.Primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = PocketColors.Primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Connect Google Drive",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Sign in with Google to unlock cloud backups for this world. Your backups stay available across devices and are ready whenever you need a restore.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PremiumCloudBenefitRow("Back up directly to your Google Drive")
                        PremiumCloudBenefitRow("Restore worlds on any signed-in device")
                        PremiumCloudBenefitRow("Keep device storage lighter with cloud copies")
                    }

                    Button(
                        onClick = {
                            showCloudSignInDialog = false
                            googleSignInLauncher.launch(AccountManager.googleSignInIntent(context))
                        },
                        enabled = !cloudActionBusy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PocketColors.Primary),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.Black
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (cloudActionBusy) "Connecting..." else "Continue with Google",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    OutlinedButton(
                        onClick = { showCloudSignInDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Maybe later", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    pendingDeleteBackup?.let { backup ->
        Dialog(onDismissRequest = { pendingDeleteBackup = null }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 10.dp,
                shadowElevation = 20.dp,
                border = BorderStroke(1.dp, PocketColors.Primary.copy(alpha = 0.18f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Delete backup?",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "This will permanently remove ${backup.name} from ${if (backup.isCloud) "Google Drive" else "your device backups"}.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DuoButton(
                            text = "KEEP",
                            onClick = { pendingDeleteBackup = null },
                            variant = DuoButtonVariant.Secondary,
                            modifier = Modifier.weight(1f),
                            minHeight = 42.dp
                        )
                        DuoButton(
                            text = "DELETE",
                            onClick = {
                                val deleteTarget = backup
                                val deleteKey = backupKey(deleteTarget)
                                pendingDeleteBackup = null
                                removingBackupKeys = removingBackupKeys + deleteKey
                                scope.launch {
                                    if (deleteTarget.isCloud) {
                                        cloudActionBusy = true
                                        actionStatusMessage = "Deleting cloud backup..."
                                        try {
                                            val driveAccount = account
                                            if (driveAccount == null || deleteTarget.remoteBackup == null) {
                                                throw IllegalStateException("Google Drive account not connected.")
                                            }
                                            val msg = DriveBackupManager.deleteBackup(context, driveAccount, deleteTarget.remoteBackup.id)
                                            cloudBackups = cloudBackups.filterNot { it.id == deleteTarget.remoteBackup.id }
                                            cloudStatusMessage = msg
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            refreshCloudBackups()
                                        } catch (e: Exception) {
                                            removingBackupKeys = removingBackupKeys - deleteKey
                                            Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            actionStatusMessage = ""
                                            cloudActionBusy = false
                                        }
                                    } else {
                                        localActionBusy = true
                                        actionStatusMessage = "Deleting backup..."
                                        try {
                                            val localBackup = deleteTarget.localBackup
                                                ?: throw IllegalStateException("Backup file missing.")
                                            val msg = stateHolder.deleteBackup(localBackup)
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            removingBackupKeys = removingBackupKeys - deleteKey
                                            Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            actionStatusMessage = ""
                                            localActionBusy = false
                                        }
                                    }
                                }
                            },
                            variant = DuoButtonVariant.Danger,
                            modifier = Modifier.weight(1f),
                            minHeight = 42.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumCloudBenefitRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = PocketColors.Primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class UnifiedBackup(
    val name: String,
    val fullFileName: String,
    val isCloud: Boolean,
    val sizeMb: Long,
    val dateLabel: String,
    val localBackup: BackupEntry? = null,
    val remoteBackup: RemoteDriveBackup? = null
)
