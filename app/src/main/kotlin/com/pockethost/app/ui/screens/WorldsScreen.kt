package com.pockethost.app.ui.screens

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.pockethost.app.WorldImporter
import com.pockethost.app.data.preferences.AppPreferences
import com.pockethost.app.data.preferences.AppPreferencesStore
import com.pockethost.app.ui.components.GameCard
import com.pockethost.app.ui.components.IosDragHandle
import com.pockethost.app.ui.components.PocketWorldIcon
import com.pockethost.app.ui.components.AnimatedEntranceContainer
import com.pockethost.app.ui.theme.tabularNums
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.util.LocalAppStrings
import kotlinx.coroutines.launch
import com.pockethost.app.ui.components.DuoButton
import com.pockethost.app.ui.components.DuoButtonVariant
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.pockethost.app.integrations.AccountManager
import com.pockethost.app.integrations.DriveBackupManager
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.FolderZip
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.pockethost.app.ui.theme.Monocraft
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun WorldsScreen(
    stateHolder: ServerStateHolder,
    onOpenWorldSetup: (Boolean) -> Unit = {},
    onChangeVersion: () -> Unit = {},
    onMessage: (String) -> Unit = {},
    onNavigateToSettings: (Int?) -> Unit = {}
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
                    onRestoringBackupNameChange = { restoringBackupName = it },
                    onNavigateToSettings = onNavigateToSettings
                )
            }
        }



        // Upload Dimension Cards (Redesigned Restore or Import card)
        item {
            AnimatedEntranceContainer(index = 5) {
                val isDark = com.pockethost.app.ui.theme.pocketIsDarkTheme()
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
                                        importMessage = stateHolder.importProgressMessage,
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
                                        importMessage = stateHolder.importProgressMessage,
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
                                        importMessage = stateHolder.importProgressMessage,
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
            dragHandle = { IosDragHandle() },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(PocketColors.Offline.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = PocketColors.Offline,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(s.worldsResetTitle, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
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
                DuoButton(
                    text = s.worldsResetDeleteSelected.uppercase(),
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
                    modifier = Modifier.fillMaxWidth(),
                    variant = DuoButtonVariant.Danger,
                    minHeight = 46.dp
                )
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            resetSheetState.hide()
                            showResetDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
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
}

@Composable
private fun BackupAutomationToggleCard(
    title: String,
    description: String,
    accent: Color,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!checked) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.History,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = accent)
            )
        }
    }
}

@Composable
private fun BackupItem(
    backup: com.pockethost.app.ui.screens.BackupEntry,
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
    importMessage: String,
    enabled: Boolean,
    onUpload: () -> Unit
) {
    val isDark = com.pockethost.app.ui.theme.pocketIsDarkTheme()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                Text(
                    text = "${importProgress.toInt().coerceIn(0, 100)}%",
                    style = tabularNums(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
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
        if (isImporting) {
            LinearProgressIndicator(
                progress = { (importProgress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = PocketColors.Primary
            )
            Text(
                text = importMessage.ifBlank { "Importing world file..." },
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    onRestoringBackupNameChange: (String?) -> Unit,
    onNavigateToSettings: (Int?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = LocalAppStrings.current
    val isDark = com.pockethost.app.ui.theme.pocketIsDarkTheme()
    var localActionBusy by remember { mutableStateOf(false) }
    var actionStatusMessage by remember { mutableStateOf("") }
    var pendingDeleteBackup by remember { mutableStateOf<BackupEntry?>(null) }
    var removingBackupKeys by remember { mutableStateOf(setOf<String>()) }
    var pendingRestoreBackup by remember { mutableStateOf<BackupEntry?>(null) }

    var cloudAccount by remember { mutableStateOf<GoogleSignInAccount?>(AccountManager.currentDriveAccount(context)) }
    var isCloudBackupBusy by remember { mutableStateOf(false) }
    var cloudBackupProgress by remember { mutableIntStateOf(0) }
    var cloudBackupStatus by remember { mutableStateOf("") }
    var pendingUploadFile by remember { mutableStateOf<File?>(null) }

    val prefs = remember { AppPreferences(context) }
    val alwaysAliveBackground by AppPreferencesStore.isAlwaysAliveBackgroundFlow(context)
        .collectAsState(initial = prefs.alwaysAliveBackground)
    var autoBackupOnStop by remember { mutableStateOf(prefs.autoBackupOnStop) }
    var autoBackupTimeEnabled by remember { mutableStateOf(prefs.autoBackupTimeEnabled) }
    var autoBackupTimeHour by remember { mutableIntStateOf(prefs.autoBackupTimeHour) }
    var autoBackupTimeMinute by remember { mutableIntStateOf(prefs.autoBackupTimeMinute) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    fun startCloudUploadForFile(file: File) {
        scope.launch {
            isCloudBackupBusy = true
            cloudBackupProgress = 0
            cloudBackupStatus = "Uploading ${file.name} to Google Drive..."
            try {
                val acc = cloudAccount ?: AccountManager.currentDriveAccount(context)
                if (acc == null) {
                    cloudBackupStatus = "Please sign in with Google first."
                    return@launch
                }
                val resultMsg = DriveBackupManager.uploadSpecificBackupFile(
                    context = context,
                    account = acc,
                    file = file,
                    onProgress = { progress, msg ->
                        cloudBackupProgress = progress
                        cloudBackupStatus = msg
                    }
                )
                cloudBackupStatus = resultMsg
                Toast.makeText(context, resultMsg, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Upload failed"
                cloudBackupStatus = "Cloud backup failed: $errorMsg"
                Toast.makeText(context, "Cloud backup failed: $errorMsg", Toast.LENGTH_LONG).show()
            } finally {
                isCloudBackupBusy = false
            }
        }
    }

    fun startCloudBackup(account: GoogleSignInAccount) {
        scope.launch {
            isCloudBackupBusy = true
            cloudBackupProgress = 0
            cloudBackupStatus = "Preparing Google Cloud backup..."
            try {
                var targetFile = stateHolder.backups.firstOrNull()?.file
                    ?: DriveBackupManager.latestBackupFile(stateHolder.activeWorld)

                if (targetFile == null || !targetFile.exists()) {
                    if (stateHolder.status == ServerStatus.OFFLINE) {
                        cloudBackupStatus = "Creating local backup before upload..."
                        stateHolder.createBackup()
                        targetFile = stateHolder.backups.firstOrNull()?.file
                            ?: DriveBackupManager.latestBackupFile(stateHolder.activeWorld)
                    } else {
                        val msg = "No local backup found. Stop the server to create a backup."
                        cloudBackupStatus = msg
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        return@launch
                    }
                }

                if (targetFile != null && targetFile.exists()) {
                    cloudBackupStatus = "Uploading to Google Drive..."
                    val resultMsg = DriveBackupManager.uploadSpecificBackupFile(
                        context = context,
                        account = account,
                        file = targetFile,
                        onProgress = { progress, msg ->
                            cloudBackupProgress = progress
                            cloudBackupStatus = msg
                        }
                    )
                    cloudBackupStatus = resultMsg
                    Toast.makeText(context, resultMsg, Toast.LENGTH_LONG).show()
                } else {
                    val msg = "Could not locate a backup file to upload."
                    cloudBackupStatus = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Upload failed"
                cloudBackupStatus = "Cloud backup failed: $errorMsg"
                Toast.makeText(context, "Cloud backup failed: $errorMsg", Toast.LENGTH_LONG).show()
            } finally {
                isCloudBackupBusy = false
            }
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        AccountManager.completeGoogleSignIn(context, result.data) { account, _, errorMessage ->
            val activeAcc = account ?: AccountManager.currentDriveAccount(context)
            cloudAccount = activeAcc
            val target = pendingUploadFile
            pendingUploadFile = null
            if (activeAcc != null) {
                if (target != null) {
                    startCloudUploadForFile(target)
                } else {
                    startCloudBackup(activeAcc)
                }
            } else if (errorMessage != null) {
                cloudBackupStatus = errorMessage
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun performRestore(backup: BackupEntry, restoreMode: RestoreMode) {
        ServerStateHolder.applicationScope.launch(Dispatchers.Main) {
            onRestoringBackupNameChange(backup.file.name)
            localActionBusy = true
            actionStatusMessage = "Restoring backup..."
            try {
                val msg = when (restoreMode) {
                    RestoreMode.FULL -> stateHolder.restoreBackup(backup)
                    RestoreMode.OVERWORLD_ONLY -> stateHolder.restoreOverworldOnly(backup)
                    RestoreMode.DIMENSIONS_ONLY -> stateHolder.restoreDimensionsOnly(backup)
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            } finally {
                onRestoringBackupNameChange(null)
                actionStatusMessage = ""
                localActionBusy = false
            }
        }
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
            DuoButton(
                text = if (localActionBusy) "BACKING UP..." else "CREATE BACKUP",
                enabled = !localActionBusy && !isCloudBackupBusy && stateHolder.status == ServerStatus.OFFLINE,
                onClick = {
                    ServerStateHolder.manualBackupJob = ServerStateHolder.applicationScope.launch(Dispatchers.Main) {
                        localActionBusy = true
                        actionStatusMessage = "Creating backup..."
                        val msg = stateHolder.createBackup()
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        actionStatusMessage = ""
                        localActionBusy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                variant = DuoButtonVariant.Primary,
                minHeight = 40.dp
            )

            DuoButton(
                text = if (isCloudBackupBusy) "UPLOADING TO CLOUD..." else "BACKUP TO CLOUD",
                enabled = !localActionBusy && !isCloudBackupBusy,
                onClick = {
                    val acc = cloudAccount ?: AccountManager.currentDriveAccount(context)
                    if (acc != null) {
                        cloudAccount = acc
                        startCloudBackup(acc)
                    } else {
                        googleSignInLauncher.launch(AccountManager.googleSignInIntent(context))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                variant = DuoButtonVariant.Secondary,
                minHeight = 40.dp
            )

            if (isCloudBackupBusy) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cloudBackupStatus.ifBlank { "Backing up to Google Drive..." },
                            fontSize = 11.sp,
                            color = PocketColors.PrimaryDark,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$cloudBackupProgress%",
                            style = tabularNums(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PocketColors.PrimaryDark
                        )
                    }
                    LinearProgressIndicator(
                        progress = { cloudBackupProgress.toFloat() / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp))
                            .height(6.dp),
                        color = PocketColors.PrimaryDark,
                        trackColor = PocketColors.PrimaryMuted
                    )
                }
            }
            
            if (stateHolder.status != ServerStatus.OFFLINE) {
                Text(
                    text = "Stop the server to enable creating new local backups & restoring.",
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

            if (cloudBackupStatus.isNotBlank() && !isCloudBackupBusy) {
                Text(
                    text = cloudBackupStatus,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            val backupsList = stateHolder.backups.filterNot { it.file.name in removingBackupKeys }

            if (backupsList.isEmpty()) {
                Text(
                    text = "No backups found",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    backupsList.forEach { backup ->
                        val itemKey = backup.file.name
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
                            Icon(
                                imageVector = Icons.Default.FolderZip,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = PocketColors.Primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val displayBackupName = backup.name.substringAfter("-").take(22).ifBlank { backup.name.take(22) }
                                Text(
                                    text = displayBackupName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                val subtext = buildString {
                                    if (backup.sizeMb > 0) {
                                        append("${backup.sizeMb} MB")
                                    }
                                    if (backup.date.isNotBlank()) {
                                        if (isNotEmpty()) append(" • ")
                                        append(backup.date)
                                    }
                                }
                                if (subtext.isNotBlank()) {
                                    Text(
                                        text = subtext,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (stateHolder.isRestoringBackup && restoringBackupName == backup.file.name) {
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

                            val actionsEnabled = stateHolder.status == ServerStatus.OFFLINE && !localActionBusy && !isCloudBackupBusy

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Cloud Upload Action
                                IconButton(
                                    onClick = {
                                        val acc = cloudAccount ?: AccountManager.currentDriveAccount(context)
                                        if (acc != null) {
                                            cloudAccount = acc
                                            startCloudUploadForFile(backup.file)
                                        } else {
                                            pendingUploadFile = backup.file
                                            googleSignInLauncher.launch(AccountManager.googleSignInIntent(context))
                                        }
                                    },
                                    enabled = !localActionBusy && !isCloudBackupBusy,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = "Backup to Cloud",
                                        modifier = Modifier.size(20.dp),
                                        tint = if (!localActionBusy && !isCloudBackupBusy) PocketColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }

                                // 1. Restore Action
                                IconButton(
                                    onClick = {
                                        pendingRestoreBackup = backup
                                    },
                                    enabled = actionsEnabled,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = "Restore",
                                        modifier = Modifier.size(20.dp),
                                        tint = if (actionsEnabled) PocketColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }

                                // 2. Delete Action
                                val deleteEnabled = !localActionBusy && !isCloudBackupBusy
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

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )

            Text(
                text = "AUTOMATIC BACKUPS",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = PocketColors.Primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Always Active notice — pinned at top, only shown when not enabled
            if (!alwaysAliveBackground) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PocketColors.Primary.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    border = BorderStroke(1.dp, PocketColors.Primary.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = PocketColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Always Active needs to be enabled for backups to run smoothly.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        DuoButton(
                            text = "ENABLE",
                            onClick = { onNavigateToSettings(1) },
                            minHeight = 32.dp,
                            fillMaxWidth = false
                        )
                    }
                }
            }

            BackupAutomationToggleCard(
                title = "Backup on Server Stop",
                description = "Automatically create a local backup whenever you stop the server.",
                accent = PocketColors.Primary,
                checked = autoBackupOnStop,
                onToggle = { newValue ->
                    autoBackupOnStop = newValue
                    prefs.autoBackupOnStop = newValue
                }
            )

            BackupAutomationToggleCard(
                title = "Scheduled Daily Backup",
                description = "Create a local backup at a particular time of the day when the server is offline.",
                accent = Color(0xFF3D8BFF),
                checked = autoBackupTimeEnabled,
                onToggle = { newValue ->
                    autoBackupTimeEnabled = newValue
                    prefs.autoBackupTimeEnabled = newValue
                    if (newValue) {
                        com.pockethost.app.service.AutoBackupScheduler.scheduleDailyBackup(
                            context,
                            autoBackupTimeHour,
                            autoBackupTimeMinute
                        )
                    } else {
                        com.pockethost.app.service.AutoBackupScheduler.cancelDailyBackup(context)
                    }
                }
            )

            if (autoBackupTimeEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Scheduled Time:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val formattedTime = remember(autoBackupTimeHour, autoBackupTimeMinute) {
                        val amPm = if (autoBackupTimeHour >= 12) "PM" else "AM"
                        val displayHour = when {
                            autoBackupTimeHour == 0 -> 12
                            autoBackupTimeHour > 12 -> autoBackupTimeHour - 12
                            else -> autoBackupTimeHour
                        }
                        val hourStr = displayHour.toString().padStart(2, '0')
                        val minuteStr = autoBackupTimeMinute.toString().padStart(2, '0')
                        "$hourStr:$minuteStr $amPm"
                    }
                    DuoButton(
                        text = formattedTime,
                        onClick = { showTimePickerDialog = true },
                        minHeight = 34.dp,
                        modifier = Modifier.width(132.dp)
                    )
                }
            }
        }
    }

    if (showTimePickerDialog) {
        IOSStyleTimePickerDialog(
            initialHour = autoBackupTimeHour,
            initialMinute = autoBackupTimeMinute,
            onTimeSelected = { hour, minute ->
                autoBackupTimeHour = hour
                autoBackupTimeMinute = minute
                prefs.autoBackupTimeHour = hour
                prefs.autoBackupTimeMinute = minute
                
                if (autoBackupTimeEnabled) {
                    com.pockethost.app.service.AutoBackupScheduler.scheduleDailyBackup(
                        context,
                        hour,
                        minute
                    )
                }
                showTimePickerDialog = false
            },
            onDismiss = {
                showTimePickerDialog = false
            }
        )
    }
    if (pendingRestoreBackup != null) {
        val backup = pendingRestoreBackup!!
        AlertDialog(
            onDismissRequest = { pendingRestoreBackup = null },
            title = { Text("Restore Options", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Select how you want to restore the backup \"${backup.name}\":",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    
                    Button(
                        onClick = {
                            val targetBackup = pendingRestoreBackup
                            pendingRestoreBackup = null
                            if (targetBackup != null) {
                                performRestore(targetBackup, RestoreMode.FULL)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PocketColors.Primary)
                    ) {
                        Text("Restore Full World (Overwrites Overworld)")
                    }

                    Button(
                        onClick = {
                            val targetBackup = pendingRestoreBackup
                            pendingRestoreBackup = null
                            if (targetBackup != null) {
                                performRestore(targetBackup, RestoreMode.OVERWORLD_ONLY)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PocketColors.Primary.copy(alpha = 0.82f))
                    ) {
                        Text("Restore Overworld Only (Keeps Nether/End)")
                    }

                    Button(
                        onClick = {
                            val targetBackup = pendingRestoreBackup
                            pendingRestoreBackup = null
                            if (targetBackup != null) {
                                performRestore(targetBackup, RestoreMode.DIMENSIONS_ONLY)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PocketColors.Starting)
                    ) {
                        Text("Restore Dimensions Only (Nether/End Only)")
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(
                        onClick = { pendingRestoreBackup = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
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
                            text = "This will permanently remove ${backup.name} from your device backups.",
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
                                val deleteKey = deleteTarget.file.name
                                pendingDeleteBackup = null
                                removingBackupKeys = removingBackupKeys + deleteKey
                                ServerStateHolder.applicationScope.launch(Dispatchers.Main) {
                                    localActionBusy = true
                                    actionStatusMessage = "Deleting backup..."
                                    try {
                                        val msg = stateHolder.deleteBackup(deleteTarget)
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        removingBackupKeys = removingBackupKeys - deleteKey
                                        Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        actionStatusMessage = ""
                                        localActionBusy = false
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
}

enum class RestoreMode {
    FULL,
    OVERWORLD_ONLY,
    DIMENSIONS_ONLY
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun <T> WheelPicker(
    items: List<T>,
    initialIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleItemsCount: Int = 3,
    itemHeight: Dp = 42.dp,
    label: (T) -> String = { it.toString() }
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val haptic = LocalHapticFeedback.current

    // Trigger onItemSelected when the center item changes
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index in items.indices) {
                    onItemSelected(index)
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } catch (e: Exception) {}
                }
            }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleItemsCount)
            .width(60.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * ((visibleItemsCount - 1) / 2)),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(items.size) { index ->
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val item = items[index]
                    Text(
                        text = label(item),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.graphicsLayer {
                            // Subtle 3D rotation and scaling depending on distance to center
                            val offset = listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.index == index }
                                ?.let { it.offset + it.size / 2f - listState.layoutInfo.viewportEndOffset / 2f }
                                ?: 0f
                            val normalized = (offset / itemHeightPx).coerceIn(-1.5f, 1.5f)
                            rotationX = normalized * -35f
                            scaleX = 1f - (Math.abs(normalized) * 0.15f)
                            scaleY = 1f - (Math.abs(normalized) * 0.15f)
                            alpha = 1f - (Math.abs(normalized) * 0.4f)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IOSStyleTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedHour12 by remember {
        val h = initialHour % 12
        mutableStateOf(if (h == 0) 12 else h)
    }
    var selectedMinute by remember { mutableStateOf(initialMinute) }
    var selectedAmPm by remember { mutableStateOf(if (initialHour < 12) 0 else 1) } // 0 = AM, 1 = PM

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select Time",
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = Monocraft,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Highlight center row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WheelPicker(
                        items = (1..12).toList(),
                        initialIndex = selectedHour12 - 1,
                        onItemSelected = { selectedHour12 = it + 1 },
                        label = { String.format("%d", it) }
                    )
                    
                    Text(
                        text = ":",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    
                    WheelPicker(
                        items = (0..59).toList(),
                        initialIndex = selectedMinute,
                        onItemSelected = { selectedMinute = it },
                        label = { String.format("%02d", it) }
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    WheelPicker(
                        items = listOf("AM", "PM"),
                        initialIndex = selectedAmPm,
                        onItemSelected = { selectedAmPm = it },
                        label = { it }
                    )
                }
            }
        },
        confirmButton = {
            DuoButton(
                text = "SET TIME",
                onClick = {
                    val hourOfDay = when {
                        selectedAmPm == 0 -> if (selectedHour12 == 12) 0 else selectedHour12
                        else -> if (selectedHour12 == 12) 12 else selectedHour12 + 12
                    }
                    onTimeSelected(hourOfDay, selectedMinute)
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    )
}
