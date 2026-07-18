package com.pocketcraft.server.ui.screens

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
import com.pocketcraft.server.ui.components.FlatEmojiIcon
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.components.DuoButtonVariant
import com.pocketcraft.server.ui.components.GameCard
import com.pocketcraft.server.ui.components.duoOutlinedTextFieldColors
import com.pocketcraft.server.ui.components.duoTextFieldShape
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.theme.Monocraft
import com.pocketcraft.server.util.LocalAppStrings
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
                    Text(text = "\uD83C\uDF0D", fontSize = 36.sp)
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
                        Text(text = "\uD83C\uDFAE", fontSize = 28.sp)
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(
                    modifier = Modifier.weight(1f),
                    label = s.filesQuickBackup,
                    emoji = "💾",
                    onClick = {
                        stateHolder.startCreateBackup { onMessage(it) }
                    }
                )
                ActionTile(
                    modifier = Modifier.weight(1f),
                    label = s.filesAdvanced,
                    emoji = "📂",
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
    emoji: String,
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
                FlatEmojiIcon(emoji, modifier = Modifier.size(28.dp), tint = PocketColors.PrimaryDark)
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
