package com.pockethost.app.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.card3d
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Max file size allowed for in-app editing (256 KB)
private const val MAX_EDITABLE_BYTES = 256 * 1024

private val EDITABLE_EXTENSIONS = setOf(
    "txt", "yml", "yaml", "json", "properties", "cfg", "conf",
    "toml", "log", "xml", "sh", "md", "ini", "env"
)

private val ICON_COLORS = mapOf(
    "yml" to Color(0xFF2F80ED),
    "yaml" to Color(0xFF2F80ED),
    "json" to Color(0xFFF59E0B),
    "properties" to PocketColors.Primary,
    "log" to PocketColors.ConsoleError,
    "txt" to Color(0xFF9B59B6),
    "toml" to Color(0xFF1ABC9C),
    "xml" to Color(0xFFE74C3C),
    "conf" to PocketColors.Starting,
    "cfg" to PocketColors.Starting
)

fun shareServerFile(context: Context, file: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mime = context.contentResolver.getType(uri) ?: "*/*"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share ${file.name}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun FileEditorScreen(
    rootDir: File,
    initialFile: File? = null,
    isServerRunning: Boolean,
    onFileSaved: (File) -> Unit = {},
    onClose: () -> Unit
) {
    var currentDir by remember(initialFile) {
        mutableStateOf(
            if (initialFile != null && initialFile.absolutePath.startsWith(rootDir.absolutePath)) {
                initialFile.parentFile ?: rootDir
            } else {
                rootDir
            }
        )
    }
    var directoryStack by remember(initialFile) {
        mutableStateOf(
            if (initialFile != null && initialFile.absolutePath.startsWith(rootDir.absolutePath)) {
                val stack = mutableListOf<File>()
                var p = initialFile.parentFile
                while (p != null && p.absolutePath.startsWith(rootDir.absolutePath)) {
                    if (p.absolutePath != initialFile.parentFile?.absolutePath) {
                        stack.add(0, p)
                    }
                    p = p.parentFile
                }
                stack
            } else {
                listOf()
            }
        )
    }
    var editingFile by remember(initialFile) { mutableStateOf<File?>(initialFile) }

    AnimatedVisibility(
        visible = editingFile != null,
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { it }
    ) {
        editingFile?.let { file ->
            TextFileEditor(
                file = file,
                isReadOnly = isServerRunning && !isEditableWhenRunning(file),
                onFileSaved = onFileSaved,
                onClose = { editingFile = null }
            )
        }
    }

    AnimatedVisibility(
        visible = editingFile == null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        FileBrowserContent(
            currentDir = currentDir,
            rootDir = rootDir,
            breadcrumbs = directoryStack + currentDir,
            onNavigateInto = { dir ->
                directoryStack = directoryStack + currentDir
                currentDir = dir
            },
            onNavigateUp = {
                if (directoryStack.isNotEmpty()) {
                    currentDir = directoryStack.last()
                    directoryStack = directoryStack.dropLast(1)
                } else {
                    onClose()
                }
            },
            onOpenFile = { file ->
                editingFile = file
            },
            onClose = onClose
        )
    }
}

@Composable
private fun FileBrowserContent(
    currentDir: File,
    rootDir: File,
    breadcrumbs: List<File>,
    onNavigateInto: (File) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenFile: (File) -> Unit,
    onClose: () -> Unit
) {
    var entries by remember(currentDir.absolutePath) { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentDir.absolutePath) {
        isLoading = true
        entries = withContext(Dispatchers.IO) {
            currentDir.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?: emptyList()
        }
        isLoading = false
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .card3d(elevation = 4.dp, cornerRadius = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateUp) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PocketColors.Primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "File Manager",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = currentDir.name,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Breadcrumb trail
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        breadcrumbs.forEachIndexed { index, dir ->
                            val isLast = index == breadcrumbs.lastIndex
                            val label = if (dir.absolutePath == rootDir.absolutePath) "server" else dir.name
                            Text(
                                text = if (isLast) label else "$label /",
                                fontSize = 11.sp,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                color = if (isLast) PocketColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PocketColors.Primary, modifier = Modifier.size(32.dp))
                }
            } else if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text("Empty folder", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(entries) { entry ->
                        FileEntryRow(
                            file = entry,
                            onClick = {
                                if (entry.isDirectory) {
                                    onNavigateInto(entry)
                                } else {
                                    val ext = entry.extension.lowercase()
                                    if (ext in EDITABLE_EXTENSIONS && entry.length() <= MAX_EDITABLE_BYTES) {
                                        onOpenFile(entry)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileEntryRow(file: File, onClick: () -> Unit) {
    val ext = file.extension.lowercase()
    val isEditable = !file.isDirectory && ext in EDITABLE_EXTENSIONS && file.length() <= MAX_EDITABLE_BYTES
    val iconColor = if (file.isDirectory) PocketColors.Starting else ICON_COLORS[ext] ?: MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = file.isDirectory || isEditable, onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (!file.isDirectory && !isEditable)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        file.isDirectory -> "${file.listFiles()?.size ?: 0} items"
                        isEditable -> formatFileSize(file.length())
                        else -> "${formatFileSize(file.length())} · cannot edit"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isEditable) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = PocketColors.PrimaryMuted
                ) {
                    Text(
                        text = ext.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PocketColors.PrimaryDark
                    )
                }
            }

            if (!file.isDirectory) {
                val context = LocalContext.current
                IconButton(
                    onClick = { shareServerFile(context, file) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = PocketColors.PrimaryDark,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TextFileEditor(
    file: File,
    isReadOnly: Boolean,
    onFileSaved: (File) -> Unit = {},
    onClose: () -> Unit
) {
    var content by remember { mutableStateOf("") }
    var isDirty by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(file.absolutePath) {
        isLoading = true
        try {
            content = withContext(Dispatchers.IO) { file.readText() }
        } catch (e: Exception) {
            loadError = e.message ?: "Failed to read file"
        }
        isLoading = false
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Editor top bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .card3d(elevation = 4.dp, cornerRadius = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PocketColors.Primary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = file.name,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isDirty) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(PocketColors.Warning, CircleShape)
                                )
                            }
                        }
                        Text(
                            text = if (isReadOnly) "Read-only · stop server to edit" else if (isDirty) "Unsaved changes" else "Editing",
                            fontSize = 11.sp,
                            color = if (isReadOnly) PocketColors.Warning else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!isReadOnly && isDirty && !isLoading) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isSaving = true
                                    saveError = null
                                    try {
                                        withContext(Dispatchers.IO) { file.writeText(content) }
                                        isDirty = false
                                        onFileSaved(file)
                                    } catch (e: Exception) {
                                        saveError = e.message ?: "Save failed"
                                    }
                                    isSaving = false
                                }
                            },
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = PocketColors.Primary
                                )
                            } else {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Save",
                                    tint = PocketColors.Primary
                                )
                            }
                        }
                    }
                    val context = LocalContext.current
                    IconButton(onClick = { shareServerFile(context, file) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = PocketColors.Primary
                        )
                    }
                }
            }

            // Save error banner
            saveError?.let { error ->
                Surface(color = PocketColors.Offline.copy(alpha = 0.12f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = PocketColors.Offline, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(error, color = PocketColors.Offline, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { saveError = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = PocketColors.Offline, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Editor body
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PocketColors.Primary)
                        }
                    }
                    loadError != null -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
                                Text("Failed to load file", fontWeight = FontWeight.Bold)
                                Text(loadError.orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    else -> {
                        val bgColor = MaterialTheme.colorScheme.background
                        val textColor = MaterialTheme.colorScheme.onBackground

                        BasicTextField(
                            value = content,
                            onValueChange = { newText ->
                                if (!isReadOnly) {
                                    content = newText
                                    isDirty = true
                                }
                            },
                            readOnly = isReadOnly,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.5.sp,
                                lineHeight = 20.sp,
                                color = textColor
                            ),
                            cursorBrush = SolidColor(PocketColors.Primary),
                            modifier = Modifier
                                .fillMaxSize()
                                .background(bgColor)
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun isEditableWhenRunning(file: File): Boolean {
    // These files are safe to view read-only even while server is running
    return file.extension.lowercase() in setOf("log", "txt", "md")
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes / (1024f * 1024f))} MB"
    }
}
