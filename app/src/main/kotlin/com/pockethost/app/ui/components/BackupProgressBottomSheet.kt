package com.pockethost.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.service.BackupForegroundService
import com.pockethost.app.service.BackupProgressTracker
import com.pockethost.app.ui.theme.Monocraft
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.screens.ServerStateHolder
import kotlinx.coroutines.launch

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupProgressBottomSheet(
    stateHolder: ServerStateHolder,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val autoBackupState by BackupProgressTracker.state.collectAsState()
    val autoProgress by BackupProgressTracker.progress.collectAsState()
    val autoStatusText by BackupProgressTracker.statusText.collectAsState()

    val isManual = stateHolder.isBackingUp || stateHolder.manualBackupState != BackupProgressTracker.State.IDLE

    val backupState = if (isManual) stateHolder.manualBackupState else autoBackupState
    val progress = if (isManual) stateHolder.backupProgressPercent else autoProgress
    val statusText = if (isManual) stateHolder.backupStatusMessage else autoStatusText

    fun dismissWithAnimation() {
        scope.launch {
            sheetState.hide()
            onDismissRequest()
            if (isManual) {
                stateHolder.resetManualBackupState()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            onDismissRequest()
            if (isManual) {
                stateHolder.resetManualBackupState()
            }
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { IosDragHandle() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val (icon, tint) = when (backupState) {
                BackupProgressTracker.State.COMPLETED -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
                BackupProgressTracker.State.FAILED -> Icons.Default.Error to PocketColors.DangerBg
                else -> Icons.Default.CloudUpload to PocketColors.Primary
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = when (backupState) {
                    BackupProgressTracker.State.COMPLETED -> "Backup Completed"
                    BackupProgressTracker.State.FAILED -> "Backup Stopped/Failed"
                    else -> if (isManual) "Creating Backup" else "Automatic Backup"
                },
                fontFamily = Monocraft,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = statusText.ifBlank { "Backing up server files..." },
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )

            if (backupState == BackupProgressTracker.State.RUNNING) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .padding(horizontal = 8.dp),
                    color = PocketColors.Primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (backupState == BackupProgressTracker.State.RUNNING) {
                DuoButton(
                    text = "STOP BACKUP",
                    variant = DuoButtonVariant.Danger,
                    onClick = {
                        if (isManual) {
                            stateHolder.cancelManualBackup()
                        } else {
                            BackupForegroundService.stop(context)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                DuoButton(
                    text = "DISMISS",
                    variant = DuoButtonVariant.Primary,
                    onClick = ::dismissWithAnimation,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
