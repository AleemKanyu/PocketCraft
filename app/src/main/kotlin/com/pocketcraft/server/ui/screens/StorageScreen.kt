package com.pocketcraft.server.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.service.ServerFileManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pocketcraft.server.util.LocalAppStrings

@Composable
fun StorageScreen(
    stateHolder: ServerStateHolder,
    onOpenWorldSetup: (Boolean) -> Unit = {},
    onChangeVersion: () -> Unit = {},
    onMessage: (String) -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(LocalAppStrings.current.worlds) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(LocalAppStrings.current.files) })
        }

        when (selectedTab) {
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ServerFilesBrowser(stateHolder: ServerStateHolder) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember(stateHolder.activeWorld) {
        ServerFileManager.getServerDir(context, stateHolder.activeWorld)
    }

    var currentDir by remember(root) { mutableStateOf(root) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var viewingTextFile by remember { mutableStateOf<File?>(null) }
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
            items(children, key = { it.absolutePath }) { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (file.isDirectory) {
                                currentDir = file
                            } else {
                                selectedFile = file
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (file.isDirectory) LocalAppStrings.current.folder else "${file.length() / 1024} KB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!file.isDirectory) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    if (selectedFile != null) {
        val file = selectedFile!!
        val fileActionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    fileActionSheetState.hide()
                    selectedFile = null
                }
            },
            sheetState = fileActionSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(file.name, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text(LocalAppStrings.current.chooseAction, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(
                    onClick = {
                        scope.launch {
                            fileActionSheetState.hide()
                            if (file.extension.lowercase() in setOf("txt", "log", "json", "properties", "yml", "yaml")) {
                                viewingTextFile = file
                            }
                            selectedFile = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(LocalAppStrings.current.view)
                }
                TextButton(
                    onClick = {
                        runCatching {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            context.startActivity(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, file.name)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            )
                        }
                        scope.launch {
                            fileActionSheetState.hide()
                            selectedFile = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(LocalAppStrings.current.share)
                }
                TextButton(
                    onClick = {
                        file.delete()
                        scope.launch {
                            fileActionSheetState.hide()
                            selectedFile = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(LocalAppStrings.current.delete, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (viewingTextFile != null) {
        val file = viewingTextFile!!
        val textFileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    textFileSheetState.hide()
                    viewingTextFile = null
                }
            },
            sheetState = textFileSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(file.name, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text(
                    text = runCatching { file.readText() }.getOrDefault(LocalAppStrings.current.unableToOpen),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            textFileSheetState.hide()
                            viewingTextFile = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(LocalAppStrings.current.close)
                }
            }
        }
    }
}
