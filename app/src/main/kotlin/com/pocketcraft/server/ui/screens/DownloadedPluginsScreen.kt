package com.pocketcraft.server.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.pocketcraft.server.data.model.Plugin
import com.pocketcraft.server.service.PluginManager
import com.pocketcraft.server.ui.theme.PocketColors
import kotlinx.coroutines.launch

private enum class DownloadedContentTab(
    val label: String,
    val emptyLabel: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val type: PluginManager.ContentType
) {
    MODS(
        label = "Mods",
        emptyLabel = "No mods installed yet",
        icon = Icons.Default.Extension,
        type = PluginManager.ContentType.PLUGINS
    ),
    RESOURCE_PACKS(
        label = "Resource Packs",
        emptyLabel = "No resource packs installed yet",
        icon = Icons.Default.FolderZip,
        type = PluginManager.ContentType.RESOURCE_PACKS
    )
}

@Composable
fun DownloadedPluginsScreen(
    stateHolder: ServerStateHolder,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<Plugin?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var isUploading by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var items by remember(stateHolder.versionLabel, selectedTab) { mutableStateOf<List<Plugin>>(emptyList()) }

    fun currentTab(): DownloadedContentTab = DownloadedContentTab.entries[selectedTab]

    fun refresh() {
        items = when (currentTab().type) {
            PluginManager.ContentType.PLUGINS -> PluginManager.listPlugins(context, stateHolder.versionLabel)
            PluginManager.ContentType.MODS -> PluginManager.listMods(context, stateHolder.versionLabel)
            PluginManager.ContentType.RESOURCE_PACKS -> PluginManager.listResourcePacks(context, stateHolder.versionLabel)
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isUploading = true
            uploadProgress = 0
            val result = PluginManager.installFromUri(
                context = context,
                uri = uri,
                versionId = stateHolder.versionLabel,
                type = currentTab().type,
                onProgress = { uploadProgress = it.coerceIn(0, 100) }
            )
            isUploading = false
            result.onSuccess {
                refresh()
                onMessage("${currentTab().label.dropLastWhile { it == 's' }} added. Restart the server to apply changes.")
            }.onFailure {
                onMessage(it.message ?: "Could not add file.")
            }
        }
    }

    LaunchedEffect(stateHolder.versionLabel, selectedTab) {
        refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Downloaded",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
                Text(
                    text = "Manage installed plugins and resource packs",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = "Add",
                    modifier = Modifier.padding(start = 6.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            DownloadedContentTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(tab.label) }
                )
            }
        }

        if (isUploading || isDownloading) {
            Text(
                text = if (isUploading) {
                    "Uploading ${currentTab().label.lowercase()}: $uploadProgress%"
                } else {
                    "Downloading ${currentTab().label.lowercase()}: $downloadProgress%"
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = currentTab().icon,
                            contentDescription = null,
                            tint = PocketColors.Primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(currentTab().emptyLabel, fontWeight = FontWeight.Bold)
                        Text(
                            "Use the Add button to import a file, or install one from the discover screen.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.fileName }) { item ->
                    DownloadedItemRow(
                        plugin = item,
                        tab = currentTab(),
                        onToggle = {
                            val success = PluginManager.toggleContent(
                                context = context,
                                versionId = stateHolder.versionLabel,
                                type = currentTab().type,
                                plugin = item
                            )
                            if (success) {
                                refresh()
                                onMessage("${item.name} ${if (item.enabled) "disabled" else "enabled"}. Restart the server to apply changes.")
                            } else {
                                onMessage("Could not update ${item.name}.")
                            }
                        },
                        onDelete = { deleteTarget = item }
                    )
                }
            }
        }
    }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${item.name}?") },
            text = { Text("This removes the file from the current server version.") },
            confirmButton = {
                TextButton(onClick = {
                    val deleted = PluginManager.deleteContent(
                        context = context,
                        versionId = stateHolder.versionLabel,
                        type = currentTab().type,
                        plugin = item
                    )
                    deleteTarget = null
                    if (deleted) {
                        refresh()
                        onMessage("${item.name} deleted.")
                    } else {
                        onMessage("Could not delete ${item.name}.")
                    }
                }) {
                    Text("Delete", color = PocketColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add ${currentTab().label}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = {
                            showAddDialog = false
                            uploadLauncher.launch("*/*")
                        }
                    ) {
                        Text("Upload from device")
                    }
                    TextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        placeholder = { Text("Paste direct download URL") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = PocketColors.Primary,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cleanUrl = urlInput.trim()
                    if (cleanUrl.isBlank()) {
                        Toast.makeText(context, "Paste a direct URL first.", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    scope.launch {
                        isDownloading = true
                        downloadProgress = 0
                        val result = PluginManager.installFromUrl(
                            context = context,
                            sourceUrl = cleanUrl,
                            versionId = stateHolder.versionLabel,
                            type = currentTab().type,
                            fileNameHint = null,
                            onProgress = { downloadProgress = it.coerceIn(0, 100) }
                        )
                        isDownloading = false
                        showAddDialog = false
                        urlInput = ""
                        result.onSuccess {
                            refresh()
                            onMessage("${currentTab().label.dropLastWhile { it == 's' }} downloaded. Restart the server to apply changes.")
                        }.onFailure {
                            onMessage(it.message ?: "Download failed.")
                        }
                    }
                }) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    urlInput = ""
                    showAddDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DownloadedItemRow(
    plugin: Plugin,
    tab: DownloadedContentTab,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PocketColors.PrimaryMuted
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = PocketColors.Primary,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(plugin.name, fontWeight = FontWeight.ExtraBold)
                Text(
                    text = buildString {
                        if (plugin.version.isNotBlank()) {
                            append("v${plugin.version} • ")
                        }
                        append("${"%.2f".format(plugin.sizeMb)} MB")
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (plugin.enabled) "Enabled" else "Disabled",
                    fontSize = 12.sp,
                    color = if (plugin.enabled) PocketColors.Online else PocketColors.Offline
                )
            }

            Switch(
                checked = plugin.enabled,
                onCheckedChange = { onToggle() }
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete item",
                    tint = PocketColors.Danger
                )
            }
        }
    }
}
