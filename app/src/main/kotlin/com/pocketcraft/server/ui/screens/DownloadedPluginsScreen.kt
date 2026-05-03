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
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.pocketcraft.server.ui.components.duoTextFieldColors
import com.pocketcraft.server.ui.components.duoTextFieldShape
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.theme.pocketCardShadowColor
import com.pocketcraft.server.ui.theme.pocketHighContrastBorderColor
import kotlinx.coroutines.launch
import com.pocketcraft.server.ui.components.PocketCraftCard
import java.util.Locale

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
@OptIn(ExperimentalMaterial3Api::class)
fun DownloadedPluginsScreen(
    stateHolder: ServerStateHolder,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    val cardShadowColor = pocketCardShadowColor()
    val cardBorderColor = pocketHighContrastBorderColor()
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
    val runtimeKey = remember(stateHolder.config.serverType, stateHolder.config.gameVersion) {
        "${stateHolder.config.serverType.name.lowercase(Locale.US)}-${stateHolder.config.gameVersion}"
    }

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
                runtimeKey = runtimeKey,
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
                PocketCraftCard(
                    modifier = Modifier
                        .padding(20.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(20.dp),
                            ambientColor = cardShadowColor,
                            spotColor = cardShadowColor,
                            clip = false
                        ),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, cardBorderColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
                        borderColor = cardBorderColor,
                        shadowColor = cardShadowColor,
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
        val deleteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    deleteSheetState.hide()
                    deleteTarget = null
                }
            },
            sheetState = deleteSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Delete ${item.name}?", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text("This removes the file from the current server version.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(
                    onClick = {
                        val deleted = PluginManager.deleteContent(
                            context = context,
                            versionId = stateHolder.versionLabel,
                            type = currentTab().type,
                            plugin = item
                        )
                        scope.launch {
                            deleteSheetState.hide()
                            deleteTarget = null
                            if (deleted) {
                                refresh()
                                onMessage("${item.name} deleted.")
                            } else {
                                onMessage("Could not delete ${item.name}.")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete", color = PocketColors.Danger)
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            deleteSheetState.hide()
                            deleteTarget = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }

    if (showAddDialog) {
        val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    addSheetState.hide()
                    showAddDialog = false
                }
            },
            sheetState = addSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Add ${currentTab().label}", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                TextButton(
                    onClick = {
                        scope.launch {
                            addSheetState.hide()
                            showAddDialog = false
                            uploadLauncher.launch("*/*")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Upload from device")
                }
                TextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    singleLine = true,
                    placeholder = { Text("Paste direct download URL") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = duoTextFieldShape(),
                    colors = duoTextFieldColors()
                )
                TextButton(
                    onClick = {
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
                                runtimeKey = runtimeKey,
                                onProgress = { downloadProgress = it.coerceIn(0, 100) }
                            )
                            isDownloading = false
                            showAddDialog = false
                            urlInput = ""
                            addSheetState.hide()
                            result.onSuccess {
                                refresh()
                                onMessage("${currentTab().label.dropLastWhile { it == 's' }} downloaded. Restart the server to apply changes.")
                            }.onFailure {
                                onMessage(it.message ?: "Download failed.")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Download")
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            urlInput = ""
                            addSheetState.hide()
                            showAddDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun DownloadedItemRow(
    plugin: Plugin,
    tab: DownloadedContentTab,
    borderColor: Color,
    shadowColor: Color,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    PocketCraftCard(
        modifier = Modifier.shadow(
            elevation = 10.dp,
            shape = RoundedCornerShape(18.dp),
            ambientColor = shadowColor,
            spotColor = shadowColor,
            clip = false
        ),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
