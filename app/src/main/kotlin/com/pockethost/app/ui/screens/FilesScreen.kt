package com.pockethost.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.pockethost.app.integrations.AccountManager
import com.pockethost.app.integrations.DriveBackupManager
import com.pockethost.app.ui.components.GoogleGLogo
import com.pockethost.app.ui.components.DuoButton
import com.pockethost.app.ui.components.DuoButtonVariant
import com.pockethost.app.ui.components.GameCard
import com.pockethost.app.ui.components.duoOutlinedTextFieldColors
import com.pockethost.app.ui.components.duoTextFieldShape
import com.pockethost.app.ui.theme.tabularNums
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.Monocraft
import com.pockethost.app.util.LocalAppStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FilesScreen(
    stateHolder: ServerStateHolder,
    onChangeVersion: () -> Unit,
    onMessage: (String) -> Unit
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSeedDialog by remember { mutableStateOf(false) }
    var showDeleteWorldDialog by remember { mutableStateOf(false) }
    var seedDraft by remember(stateHolder.config.worldSeed) { mutableStateOf(stateHolder.config.worldSeed) }

    var cloudAccount by remember { mutableStateOf<GoogleSignInAccount?>(AccountManager.currentDriveAccount(context)) }
    var isCloudBackupBusy by remember { mutableStateOf(false) }
    var cloudBackupProgress by remember { mutableIntStateOf(0) }
    var cloudBackupStatus by remember { mutableStateOf("") }

    fun startCloudBackup(account: GoogleSignInAccount) {
        scope.launch {
            isCloudBackupBusy = true
            cloudBackupProgress = 0
            cloudBackupStatus = "Preparing Google Cloud backup..."
            try {
                val existingBackup = DriveBackupManager.latestBackupFile(stateHolder.activeWorld)
                if (existingBackup == null || !existingBackup.exists()) {
                    cloudBackupStatus = "Creating fresh world backup..."
                    stateHolder.startCreateBackup { statusMsg ->
                        cloudBackupStatus = statusMsg
                    }
                    delay(1200)
                }
                cloudBackupStatus = "Uploading to Google Drive..."
                val resultMsg = DriveBackupManager.uploadLatestWorldBackup(
                    context = context,
                    account = account,
                    worldName = stateHolder.activeWorld,
                    onProgress = { progress, msg ->
                        cloudBackupProgress = progress
                        cloudBackupStatus = msg
                    }
                )
                cloudBackupStatus = resultMsg
                Toast.makeText(context, resultMsg, Toast.LENGTH_LONG).show()
                onMessage(resultMsg)
            } catch (e: Exception) {
                cloudBackupStatus = "Cloud backup failed: ${e.message}"
                Toast.makeText(context, cloudBackupStatus, Toast.LENGTH_LONG).show()
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
            if (activeAcc != null) {
                startCloudBackup(activeAcc)
            } else if (errorMessage != null) {
                cloudBackupStatus = errorMessage
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionLabel(s.filesSectionCurrentWorld)
            Spacer(Modifier.height(4.dp))
            GameCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = PocketColors.PrimaryDark
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stateHolder.activeWorld,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = String.format(s.filesActiveFolder, stateHolder.worldSizeMb),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                DuoButton(
                    text = s.filesDeleteActiveWorld,
                    onClick = { showDeleteWorldDialog = true },
                    enabled = stateHolder.status == ServerStatus.OFFLINE && stateHolder.worlds.size > 1,
                    variant = DuoButtonVariant.Danger,
                    modifier = Modifier.fillMaxWidth()
                )
                if (stateHolder.worlds.size <= 1) {
                    Text(
                        text = s.filesAddAnotherWorld,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        item {
            SectionLabel(s.filesSectionSeed)
            Spacer(Modifier.height(4.dp))
            GameCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stateHolder.config.worldSeed.ifBlank { s.filesSeedRandom },
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(s.filesSeedUsedFor, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row {
                        IconButton(onClick = {
                            stateHolder.copySeedToClipboard()
                            onMessage(s.filesSeedCopied)
                        }) {
                            Icon(Icons.Filled.ContentCopy, "Copy", tint = PocketColors.PrimaryDark)
                        }
                        IconButton(onClick = { showSeedDialog = true }) {
                            Text(s.filesSeedEdit, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PocketColors.Primary)
                        }
                    }
                }
            }
        }

        item {
            SectionLabel(s.filesSectionCore)
            Spacer(Modifier.height(4.dp))
            GameCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onChangeVersion),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = PocketColors.PrimaryDark
                        )
                        Column {
                            Text(
                                text = String.format(s.filesMinecraftJava, stateHolder.runtimeVersionLabel),
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = s.filesSwitchOrUpgrade,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                        color = PocketColors.PrimaryMuted
                    ) {
                        Text(
                            text = s.filesChange,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            color = PocketColors.PrimaryDark,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            letterSpacing = 0.sp
                        )
                    }
                }
            }
        }

        item {
            SectionLabel(s.filesSectionBackup)
            Spacer(Modifier.height(4.dp))
            GameCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GoogleGLogo(modifier = Modifier.size(22.dp))
                            Column {
                                Text(
                                    text = s.filesGoogleCloudBackup,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (cloudAccount != null) {
                                        cloudAccount?.email ?: "Connected"
                                    } else {
                                        s.filesConnectGooglePrompt
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (cloudAccount != null) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = PocketColors.Online.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = PocketColors.Online,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "LINKED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PocketColors.Online
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = s.filesGoogleCloudBackupDesc,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isCloudBackupBusy) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cloudBackupStatus.ifBlank { "Backing up to Google Cloud..." },
                                    fontSize = 11.sp,
                                    color = PocketColors.PrimaryDark,
                                    fontWeight = FontWeight.Medium
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
                    } else {
                        DuoButton(
                            text = s.filesUploadToGoogleCloud,
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
                            variant = DuoButtonVariant.Primary
                        )
                    }

                    if (cloudBackupStatus.isNotBlank() && !isCloudBackupBusy) {
                        Text(
                            text = cloudBackupStatus,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(
                    modifier = Modifier.weight(1f),
                    label = s.filesQuickBackup,
                    icon = Icons.Default.Save,
                    onClick = {
                        stateHolder.startCreateBackup { onMessage(it) }
                    }
                )
                ActionTile(
                    modifier = Modifier.weight(1f),
                    label = s.filesAdvanced,
                    icon = Icons.Default.FolderOpen,
                    onClick = { /* Could navigate to Worlds page if we passed navigation function */ }
                )
            }
        }
    }

    if (showSeedDialog) {
        val seedSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    seedSheetState.hide()
                    showSeedDialog = false
                }
            },
            sheetState = seedSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = s.filesSeedDialogTitle, fontFamily = Monocraft, fontWeight = FontWeight.ExtraBold)
                Text(
                    text = s.filesSeedDialogDesc,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = seedDraft,
                    onValueChange = { seedDraft = it },
                    label = { Text(s.filesSeedDialogLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = duoTextFieldShape(),
                    colors = duoOutlinedTextFieldColors()
                )
                DuoButton(
                    text = s.filesSeedDialogConfirm,
                    onClick = {
                        scope.launch {
                            onMessage(stateHolder.updateSeed(seedDraft))
                            seedSheetState.hide()
                            showSeedDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            seedSheetState.hide()
                            showSeedDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(s.filesSeedDialogCancel)
                }
            }
        }
    }

    if (showDeleteWorldDialog) {
        val deleteWorldSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    deleteWorldSheetState.hide()
                    showDeleteWorldDialog = false
                }
            },
            sheetState = deleteWorldSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = s.filesDeleteWorldTitle, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(
                    text = String.format(s.filesDeleteWorldDesc, stateHolder.activeWorld),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            onMessage(stateHolder.deleteWorld(stateHolder.activeWorld))
                            deleteWorldSheetState.hide()
                            showDeleteWorldDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(s.filesDeleteAction, color = PocketColors.Offline, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            deleteWorldSheetState.hide()
                            showDeleteWorldDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(s.filesDeleteCancel)
                }
            }
        }
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GameCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = PocketColors.PrimaryDark
            )
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
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
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
