package com.pocketcraft.server.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.service.ServerFileManager
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.theme.PocketMotion
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pocketcraft.server.util.LocalAppStrings
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color
import com.pocketcraft.server.integrations.AccountManager
import com.pocketcraft.server.integrations.DriveBackupManager
import com.pocketcraft.server.integrations.RemoteDriveBackup
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.components.DuoButtonVariant
import com.pocketcraft.server.ui.components.GameCard

@Composable
fun StorageScreen(
    stateHolder: ServerStateHolder,
    onOpenWorldSetup: (Boolean) -> Unit = {},
    onChangeVersion: () -> Unit = {},
    onMessage: (String) -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedTab) {
        stateHolder.refreshAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = PocketColors.Primary
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, selectedContentColor = PocketColors.Primary, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant, text = { Text(LocalAppStrings.current.worlds) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, selectedContentColor = PocketColors.Primary, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant, text = { Text(LocalAppStrings.current.files) })
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally(animationSpec = PocketMotion.softIntOffsetTween(durationMillis = 420)) { it / 12 } +
                            fadeIn(PocketMotion.softFloatTween(durationMillis = 340)) togetherWith
                            slideOutHorizontally(animationSpec = PocketMotion.softIntOffsetTween(durationMillis = 360)) { -it / 14 } +
                            fadeOut(PocketMotion.softFloatTween(durationMillis = 220))
                    } else {
                        slideInHorizontally(animationSpec = PocketMotion.softIntOffsetTween(durationMillis = 420)) { -it / 12 } +
                            fadeIn(PocketMotion.softFloatTween(durationMillis = 340)) togetherWith
                            slideOutHorizontally(animationSpec = PocketMotion.softIntOffsetTween(durationMillis = 360)) { it / 14 } +
                            fadeOut(PocketMotion.softFloatTween(durationMillis = 220))
                    }
                },
                label = "storage-tab-transition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> WorldsScreen(
                        stateHolder = stateHolder,
                        onOpenWorldSetup = onOpenWorldSetup,
                        onChangeVersion = onChangeVersion,
                        onMessage = onMessage
                    )
                    else -> ServerFilesBrowser(stateHolder = stateHolder)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ServerFilesBrowser(stateHolder: ServerStateHolder) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember(stateHolder.activeWorld) {
        ServerFileManager.getServerDir(context, stateHolder.activeWorld)
    }

    var currentDir by remember(root) { mutableStateOf(root) }
    var editingFile by remember { mutableStateOf<File?>(null) }
    var deleteConfirmFile by remember { mutableStateOf<File?>(null) }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var uploadIndeterminate by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    val animatedUploadProgress by animateFloatAsState(
        targetValue = (uploadProgress / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400),
        label = "upload_progress"
    )

    val children = remember(currentDir) {
        currentDir.listFiles()
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .orEmpty()
    }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val uploadStartedAt = System.currentTimeMillis()
            isUploading = true
            uploadProgress = 0
            uploadIndeterminate = false

            runCatching {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@runCatching
                val fileName = uri.path?.substringAfterLast('/') ?: "uploaded_file"
                val outputFile = File(currentDir, fileName)
                val totalBytes = context.contentResolver.openAssetFileDescriptor(uri, "r")?.length ?: -1L
                uploadIndeterminate = totalBytes <= 0L

                withContext(Dispatchers.IO) {
                    inputStream.use { input ->
                        outputFile.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var copied = 0L
                            var bytes = input.read(buffer)
                            while (bytes != -1) {
                                output.write(buffer, 0, bytes)
                                copied += bytes
                                if (totalBytes > 0) {
                                    withContext(Dispatchers.Main) {
                                        uploadProgress = (copied * 100 / totalBytes).toInt().coerceIn(0, 100)
                                    }
                                }
                                bytes = input.read(buffer)
                            }
                        }
                    }
                }

                uploadProgress = 100
            }

            val visibleDuration = System.currentTimeMillis() - uploadStartedAt
            if (visibleDuration < 300L) {
                delay(300L - visibleDuration)
            }

            isUploading = false
            uploadProgress = 0
            uploadIndeterminate = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Backup location info
            if (currentDir == root) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Server backups are saved to Downloads/PocketCraftWorldBackups/<world>",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (currentDir != root) {
                            currentDir = currentDir.parentFile ?: root
                        }
                    },
                    enabled = currentDir != root
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Text(
                    text = currentDir.relativeTo(root).path.ifBlank { "/" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { editingFile = root }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit files")
                }
                IconButton(onClick = { uploadLauncher.launch("*/*") }) {
                    Icon(Icons.Default.UploadFile, contentDescription = "Upload file")
                }
            }

            if (isUploading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "📤 Uploading File",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (uploadIndeterminate) LocalAppStrings.current.uploading else "$uploadProgress%",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (uploadIndeterminate) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(999.dp))
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { animatedUploadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(999.dp))
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                    }
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (currentDir == root) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Server Directory Files",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (com.pocketcraft.server.ui.theme.pocketIsDarkTheme()) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                }
                items(children, key = { it.absolutePath }) { file ->
                    val ext = file.extension.lowercase()
                    val isEditable = !file.isDirectory &&
                        ext in setOf("txt", "yml", "yaml", "json", "properties", "cfg", "conf", "toml", "log", "xml", "sh", "md", "ini", "env") &&
                        file.length() <= 256 * 1024
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                if (file.isDirectory) {
                                    currentDir = file
                                } else if (isEditable) {
                                    editingFile = file
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                            contentDescription = null,
                            tint = if (file.isDirectory) PocketColors.Starting
                            else if (isEditable) PocketColors.Primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!file.isDirectory && !isEditable)
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when {
                                    file.isDirectory -> LocalAppStrings.current.folder
                                    isEditable -> "${file.length() / 1024} KB · tap to edit"
                                    else -> "${file.length() / 1024} KB · cannot edit"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!file.isDirectory) {
                            IconButton(
                                onClick = { deleteConfirmFile = file },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Full file editor — opens the in-app file manager/editor starting from root
        if (editingFile != null) {
            FileEditorScreen(
                rootDir = root,
                initialFile = if (editingFile != root) editingFile else null,
                isServerRunning = stateHolder.status != ServerStatus.OFFLINE,
                onFileSaved = { savedFile ->
                    if (savedFile.name == "server.properties") {
                        stateHolder.refreshAll()
                    }
                },
                onClose = { editingFile = null }
            )
        }
    }

    // Delete confirmation dialog
    if (deleteConfirmFile != null) {
        val file = deleteConfirmFile!!
        AlertDialog(
            onDismissRequest = { deleteConfirmFile = null },
            title = { Text("Delete File", fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "Are you sure you want to permanently delete \"${file.name}\"? This cannot be undone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        file.delete()
                        deleteConfirmFile = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmFile = null }) {
                    Text("Cancel")
                }
            }
        )
    }

}
