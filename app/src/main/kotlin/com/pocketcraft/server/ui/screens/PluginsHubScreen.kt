package com.pocketcraft.server.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.pocketcraft.server.data.model.Plugin
import com.pocketcraft.server.service.PluginManager
import com.pocketcraft.server.ui.components.PocketModsIcon
import com.pocketcraft.server.ui.components.duoTextFieldColors
import com.pocketcraft.server.ui.components.duoTextFieldShape
import com.pocketcraft.server.ui.theme.PocketColors
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pocketcraft.server.ui.components.PocketCraftCard

private enum class ContentTab(
    val label: String,
    val type: PluginManager.ContentType
) {
    PLUGINS("Plugins", PluginManager.ContentType.PLUGINS),
    MODS("Mods", PluginManager.ContentType.MODS),
    PACKS("Resource Packs", PluginManager.ContentType.RESOURCE_PACKS)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PluginsHubScreen(
    stateHolder: ServerStateHolder,
    onMessage: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Plugin?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var isUploading by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadingCatalogKey by remember { mutableStateOf<String?>(null) }
    var isDiscoverLoading by remember { mutableStateOf(false) }
    var discoveredItems by remember { mutableStateOf<List<PluginManager.RemoteCatalogItem>>(emptyList()) }
    var downloadedItems by remember(stateHolder.activeWorld, selectedTab) { mutableStateOf<List<Plugin>>(emptyList()) }
    var downloadedSectionExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var catalogRetryToken by remember { mutableIntStateOf(0) }
    var detailCard by remember { mutableStateOf<ContentDetailCard?>(null) }
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val selectedTabColor = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
    val unselectedTabColor = if (isDarkTheme) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f) else MaterialTheme.colorScheme.onSurfaceVariant

    val resourcePackIcons = remember { mutableStateMapOf<String, File?>() }

    fun currentTab(): ContentTab = ContentTab.entries[selectedTab]

    val isModsTab = currentTab().type == PluginManager.ContentType.MODS
    val runtimeKey = remember(stateHolder.config.serverType, stateHolder.config.gameVersion) {
        "${stateHolder.config.serverType.name.lowercase(Locale.US)}-${stateHolder.config.gameVersion}"
    }
    val supportsMods = remember(runtimeKey) { PluginManager.supportsMods(runtimeKey) }
    val showModsWarning = isModsTab && !supportsMods

    suspend fun loadDownloadedItems(): List<Plugin> = withContext(Dispatchers.IO) {
        PluginManager.ensureContentDirs(context, stateHolder.activeWorld)
        when (currentTab().type) {
            PluginManager.ContentType.PLUGINS -> PluginManager.listPlugins(context, stateHolder.activeWorld)
            PluginManager.ContentType.MODS -> PluginManager.listMods(context, stateHolder.activeWorld)
            PluginManager.ContentType.RESOURCE_PACKS -> PluginManager.listResourcePacks(context, stateHolder.activeWorld)
        }
    }

    fun refreshDownloadedItems() {
        scope.launch {
            downloadedItems = loadDownloadedItems()
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
                worldName = stateHolder.activeWorld,
                type = currentTab().type,
                runtimeKey = runtimeKey,
                onProgress = { uploadProgress = it.coerceIn(0, 100) }
            )
            isUploading = false
            result.onSuccess {
                refreshDownloadedItems()
                Toast.makeText(context, "Added ${currentTab().label.dropLastWhile { it == 's' }}.", Toast.LENGTH_SHORT).show()
            }.onFailure {
                onMessage(it.message ?: "Could not add file.")
            }
        }
    }

    LaunchedEffect(selectedTab, stateHolder.activeWorld) {
        resourcePackIcons.clear()
        downloadedItems = loadDownloadedItems()
    }

    LaunchedEffect(selectedTab, downloadedItems, stateHolder.activeWorld) {
        if (currentTab().type == PluginManager.ContentType.RESOURCE_PACKS) {
            val root = PluginManager.getContentDir(context, stateHolder.activeWorld, currentTab().type)
            downloadedItems.forEach { item ->
                if (!resourcePackIcons.containsKey(item.fileName)) {
                    resourcePackIcons[item.fileName] = withContext(Dispatchers.IO) {
                        PluginManager.getResourcePackIcon(File(root, item.fileName))
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedTab, query, catalogRetryToken, stateHolder.activeWorld) {
        delay(250)
        isDiscoverLoading = true
        errorMessage = null
        discoveredItems = emptyList()

        val result = PluginManager.fetchRemoteCatalog(
            context = context,
            type = currentTab().type,
            query = query,
            minecraftVersion = stateHolder.config.gameVersion,
            runtimeKey = runtimeKey,
            limit = 50
        )

        result.onSuccess { remoteItems ->
            discoveredItems = remoteItems
            if (remoteItems.isEmpty()) {
                errorMessage = "No online results found right now."
            }
        }.onFailure { error ->
            discoveredItems = emptyList()
            errorMessage = error.message ?: "Failed to load online results."
        }

        isDiscoverLoading = false
    }

    val installedKeys = remember(downloadedItems) {
        downloadedItems.flatMap { plugin ->
            listOf(
                plugin.name,
                plugin.fileName.removeSuffix(".disabled").substringBeforeLast('.', plugin.fileName.removeSuffix(".disabled"))
            )
        }.map(::normalizeInstallKey)
            .filter { it.isNotBlank() }
            .toSet()
    }
    val sortedDiscoveredItems = remember(discoveredItems, selectedTab) {
        discoveredItems
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = selectedTabColor
        ) {
            ContentTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        selectedTab = index
                        query = ""
                    },
                    selectedContentColor = selectedTabColor,
                    unselectedContentColor = unselectedTabColor,
                    text = { Text(tab.label) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Search online ${currentTab().label.lowercase(Locale.US)}..."
                )
            }

            if (showModsWarning) {
                item {
                    PocketCraftCard(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, PocketColors.Warning),
                        colors = CardDefaults.cardColors(containerColor = pluginsHubCardColor())
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Mod loader required for mods",
                                fontWeight = FontWeight.ExtraBold,
                                color = PocketColors.Warning
                            )
                            Text(
                                text = "This server version is not using a supported mod loader. Switch this server to Fabric, Quilt, Forge, or NeoForge to install mods.",
                                color = pluginsHubMutedTextColor(),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (isUploading || isDownloading) {
                item {
                    Text(
                        text = if (isUploading) {
                            "Adding file: $uploadProgress%"
                        } else {
                            "Downloading: $downloadProgress%"
                        },
                        fontSize = 12.sp,
                        color = pluginsHubMutedTextColor()
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Downloaded",
                    subtitle = "Manage what is already stored for this server version.",
                    actionLabel = "Add",
                    actionEnabled = true,
                    onAction = { showAddDialog = true },
                    isExpanded = downloadedSectionExpanded,
                    onToggleExpand = { downloadedSectionExpanded = !downloadedSectionExpanded },
                    itemCount = downloadedItems.size
                )
            }

            if (downloadedItems.isEmpty()) {
                item {
                    EmptyDownloadedCard(currentTab())
                }
            } else if (!downloadedSectionExpanded) {
                item {
                    PocketCraftCard(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = pluginsHubCardColor())
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Tap arrow to expand",
                                color = pluginsHubMutedTextColor(),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else if (downloadedItems.isNotEmpty() && downloadedSectionExpanded) {
                items(downloadedItems, key = { it.fileName }) { item ->
                    ContentRow(
                        item = item,
                        tab = currentTab(),
                        packIcon = resourcePackIcons[item.fileName],
                        onToggle = {
                            val success = PluginManager.toggleContent(
                                context = context,
                                worldName = stateHolder.activeWorld,
                                type = currentTab().type,
                                plugin = item
                            )
                            if (success) {
                                refreshDownloadedItems()
                                onMessage("${item.name} ${if (item.enabled) "disabled" else "enabled"}. Restart the server to apply changes.")
                            } else {
                                onMessage("Could not update ${item.name}.")
                            }
                        },
                        onDelete = { deleteTarget = item },
                        onShowDetails = {
                            detailCard = ContentDetailCard.Local(
                                item = item,
                                tab = currentTab(),
                                packIcon = resourcePackIcons[item.fileName]
                            )
                        }
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Discover Online",
                    subtitle = "Only online results are shown here. Installed matches are marked for you.",
                    actionLabel = if (errorMessage != null && !isDiscoverLoading) "Retry" else null,
                    onAction = { catalogRetryToken++ }
                )
            }

            when {
                isDiscoverLoading -> {
                    item {
                        PocketCraftCard(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = pluginsHubCardColor())
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Loading online results...",
                                    color = pluginsHubMutedTextColor()
                                )
                            }
                        }
                    }
                }
                errorMessage != null -> {
                    item {
                        PocketCraftCard(
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, PocketColors.Offline),
                            colors = CardDefaults.cardColors(containerColor = pluginsHubCardColor())
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = errorMessage.orEmpty(),
                                    color = PocketColors.Offline,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Searches are cached and rate-limited, so retrying in a moment usually works.",
                                    color = pluginsHubMutedTextColor(),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                sortedDiscoveredItems.isEmpty() -> {
                    item {
                        EmptyOnlineCard(currentTab())
                    }
                }
                else -> {
                    items(sortedDiscoveredItems, key = { it.catalogKey }) { remote ->
                        val installed = isRemoteInstalled(remote, installedKeys)
                        RemoteContentRow(
                            item = remote,
                            installed = installed,
                            isDownloading = downloadingCatalogKey == remote.catalogKey && isDownloading,
                            onShowDetails = {
                                detailCard = ContentDetailCard.Remote(
                                    item = remote,
                                    installed = installed,
                                    tab = currentTab()
                                )
                            },
                            onInstall = {
                                scope.launch {
                                    if (installed || !remote.canInstall) return@launch

                                    isDownloading = true
                                    downloadingCatalogKey = remote.catalogKey
                                    downloadProgress = 0
                                    val result = PluginManager.installRemoteItem(
                                        context = context,
                                        item = remote,
                                        worldName = stateHolder.activeWorld,
                                        type = currentTab().type,
                                        runtimeKey = runtimeKey,
                                        minecraftVersion = stateHolder.config.gameVersion,
                                        onProgress = { downloadProgress = it.coerceIn(0, 100) }
                                    )
                                    isDownloading = false
                                    downloadingCatalogKey = null
                                    result.onSuccess {
                                        refreshDownloadedItems()
                                        Toast.makeText(context, "Installed ${remote.title}", Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        onMessage(it.message ?: "Install failed")
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (deleteTarget != null) {
        val deleting = deleteTarget!!
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
                Text("Delete ${deleting.name}?", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text("This will permanently remove the file from this server version.", color = pluginsHubMutedTextColor())
                TextButton(
                    onClick = {
                        PluginManager.deleteContent(
                            context = context,
                            worldName = stateHolder.activeWorld,
                            type = currentTab().type,
                            plugin = deleting
                        )
                        scope.launch {
                            deleteSheetState.hide()
                            deleteTarget = null
                            refreshDownloadedItems()
                            onMessage("${deleting.name} deleted.")
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
                Text(
                    text = "Add ${currentTab().label}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            addSheetState.hide()
                            showAddDialog = false
                            uploadLauncher.launch("*/*")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp)
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                addSheetState.hide()
                                showAddDialog = false
                            }
                        }
                    ) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                isDownloading = true
                                downloadingCatalogKey = null
                                downloadProgress = 0
                                val result = PluginManager.installFromUrl(
                                    context = context,
                                    sourceUrl = urlInput,
                                    worldName = stateHolder.activeWorld,
                                    type = currentTab().type,
                                    fileNameHint = null,
                                    runtimeKey = runtimeKey,
                                    onProgress = { downloadProgress = it.coerceIn(0, 100) }
                                )
                                isDownloading = false
                                if (result.isSuccess) {
                                    addSheetState.hide()
                                    showAddDialog = false
                                }
                                result.onSuccess {
                                    refreshDownloadedItems()
                                    Toast.makeText(context, "Added ${currentTab().label.dropLastWhile { it == 's' }}.", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    onMessage(it.message ?: "Download failed")
                                }
                            }
                        },
                        enabled = urlInput.isNotBlank()
                    ) {
                        Text("Download")
                    }
                }
            }
        }
    }

    detailCard?.let { activeDetail ->
        ContentDetailDialog(
            detailCard = activeDetail,
            onDismissRequest = { detailCard = null }
        )
    }
}

private sealed interface ContentDetailCard {
    data class Local(
        val item: Plugin,
        val tab: ContentTab,
        val packIcon: File?
    ) : ContentDetailCard

    data class Remote(
        val item: PluginManager.RemoteCatalogItem,
        val installed: Boolean,
        val tab: ContentTab
    ) : ContentDetailCard
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null,
    isExpanded: Boolean = true,
    onToggleExpand: (() -> Unit)? = null,
    itemCount: Int = 0
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                if (itemCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "($itemCount)",
                        fontSize = 12.sp,
                        color = pluginsHubMutedTextColor()
                    )
                }
            }
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = pluginsHubMutedTextColor()
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onToggleExpand != null) {
                Box(
                    modifier = Modifier
                        .clickable(onClick = onToggleExpand)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                OutlinedButton(
                    onClick = onAction,
                    enabled = actionEnabled,
                    border = BorderStroke(1.dp, pluginsHubBorderColor()),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (pluginsHubIsDarkTheme()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (actionLabel == "Add") {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun EmptyDownloadedCard(tab: ContentTab) {
    PocketCraftCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = pluginsHubCardColor())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No downloaded ${tab.label.lowercase(Locale.US)} yet for this version.",
                color = pluginsHubMutedTextColor()
            )
        }
    }
}

@Composable
private fun EmptyOnlineCard(tab: ContentTab) {
    PocketCraftCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = pluginsHubCardColor())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No online ${tab.label.lowercase(Locale.US)} matched this search.",
                color = pluginsHubMutedTextColor()
            )
        }
    }
}

@Composable
private fun ContentRow(
    item: Plugin,
    tab: ContentTab,
    packIcon: File?,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onShowDetails: () -> Unit
) {
    PocketCraftCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onShowDetails),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, pluginsHubBorderColor()),
        colors = CardDefaults.cardColors(containerColor = pluginsHubCardColor())
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ItemIcon(item = item, tab = tab, packIcon = packIcon)
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = buildString {
                        if (item.version.isNotBlank()) {
                            append(item.version)
                            append(" • ")
                        }
                        append("${"%.2f".format(item.sizeMb)} MB")
                    },
                    fontSize = 12.sp,
                    color = pluginsHubMutedTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = item.enabled,
                onCheckedChange = { onToggle() }
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = PocketColors.Danger
                )
            }
        }
    }
}

@Composable
private fun ItemIcon(item: Plugin, tab: ContentTab, packIcon: File?) {
    val fallbackText = item.name.firstOrNull()?.uppercase() ?: "?"

    when {
        packIcon != null -> {
            AsyncImage(
                model = packIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    .background(PocketColors.SurfaceVarDark, RoundedCornerShape(8.dp))
            )
        }
            tab.label == "Mods" -> {
                PocketModsIcon(
                    modifier = Modifier
                        .size(34.dp)
                        .background(PocketColors.PrimaryMuted, RoundedCornerShape(10.dp))
                        .padding(6.dp),
                    tint = Color.Unspecified
                )
            }
        else -> {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(PocketColors.PrimaryMuted, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = fallbackText,
                    fontWeight = FontWeight.ExtraBold,
                    color = PocketColors.Primary
                )
            }
        }
    }
}

@Composable
private fun RemoteContentRow(
    item: PluginManager.RemoteCatalogItem,
    installed: Boolean,
    isDownloading: Boolean,
    onShowDetails: () -> Unit,
    onInstall: () -> Unit
) {
    PocketCraftCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onShowDetails),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, pluginsHubBorderColor()),
        colors = CardDefaults.cardColors(containerColor = pluginsHubCardColor())
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!item.iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.iconUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(38.dp)
                            .background(PocketColors.SurfaceVarDark, RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(PocketColors.PrimaryMuted, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.title.take(1).uppercase(),
                            fontWeight = FontWeight.ExtraBold,
                            color = PocketColors.Primary
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(
                            item.author?.takeIf { it.isNotBlank() },
                            formatDownloads(item.downloads)
                        ).joinToString(" • "),
                        fontSize = 11.sp,
                        color = pluginsHubMutedTextColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                when {
                    installed -> {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = PocketColors.PrimaryMuted
                        ) {
                            Text(
                                text = "Installed",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = PocketColors.Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    !item.canInstall && item.isSupported -> {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = pluginsHubSupportedChipContainerColor()
                        ) {
                            Text(
                                text = "Supported Mod",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = pluginsHubSupportedChipContentColor(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    !item.canInstall -> {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = pluginsHubUnsupportedChipContainerColor()
                        ) {
                            Text(
                                text = "Unsupported",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = pluginsHubUnsupportedChipContentColor(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    else -> {
                        TextButton(
                            onClick = onInstall,
                            enabled = !isDownloading,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (pluginsHubIsDarkTheme()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (isDownloading) "Installing..." else "Install")
                        }
                    }
                }
            }

            Text(
                text = item.description,
                fontSize = 12.sp,
                color = pluginsHubMutedTextColor(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!item.supportMessage.isNullOrBlank()) {
                Text(
                    text = item.supportMessage,
                    fontSize = 11.sp,
                    color = if (item.canInstall) pluginsHubMutedTextColor() else pluginsHubUnsupportedChipContentColor()
                )
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search..."
) {
    val hintColor = pluginsHubHintColor()
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder, color = hintColor, fontSize = 14.sp) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = hintColor,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        tint = hintColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        colors = duoTextFieldColors(),
        shape = duoTextFieldShape(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ContentDetailDialog(
    detailCard: ContentDetailCard,
    onDismissRequest: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            scope.launch {
                detailSheetState.hide()
                onDismissRequest()
            }
        },
        sheetState = detailSheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        PocketCraftCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(2.dp, pluginsHubBorderColor()),
            colors = CardDefaults.cardColors(containerColor = pluginsHubCardColor())
        ) {
            when (detailCard) {
                is ContentDetailCard.Local -> LocalDetailCardContent(
                    detailCard = detailCard,
                    onDismissRequest = {
                        scope.launch {
                            detailSheetState.hide()
                            onDismissRequest()
                        }
                    }
                )
                is ContentDetailCard.Remote -> RemoteDetailCardContent(
                    detailCard = detailCard,
                    onDismissRequest = {
                        scope.launch {
                            detailSheetState.hide()
                            onDismissRequest()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LocalDetailCardContent(
    detailCard: ContentDetailCard.Local,
    onDismissRequest: () -> Unit
) {
    val item = detailCard.item
    val statusLabel = if (item.enabled) "Enabled" else "Disabled"
    val statusColor = if (item.enabled) PocketColors.Primary else pluginsHubMutedTextColor()

    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ItemIcon(item = item, tab = detailCard.tab, packIcon = detailCard.packIcon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
                Text(
                    text = detailCard.tab.label.dropLastWhile { it == 's' },
                    fontSize = 12.sp,
                    color = pluginsHubMutedTextColor()
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = PocketColors.PrimaryMuted
            ) {
                Text(
                    text = statusLabel,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        DetailLine("File", item.fileName)
        DetailLine("Version", item.version.ifBlank { "Not provided" })
        DetailLine("Size", String.format(Locale.US, "%.2f MB", item.sizeMb))
        DetailLine("Apply", "Restart the server after changing plugins or packs.")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun RemoteDetailCardContent(
    detailCard: ContentDetailCard.Remote,
    onDismissRequest: () -> Unit
) {
    val item = detailCard.item
    val stateChipLabel = when {
        detailCard.installed -> "Installed"
        !item.canInstall && item.isSupported -> "Supported Mod"
        !item.canInstall -> "Unsupported"
        else -> detailCard.tab.label.dropLastWhile { it == 's' }
    }
    val stateChipColor = when {
        detailCard.installed -> PocketColors.PrimaryMuted
        !item.canInstall && item.isSupported -> pluginsHubSupportedChipContainerColor()
        !item.canInstall -> pluginsHubUnsupportedChipContainerColor()
        else -> PocketColors.PrimaryMuted
    }

    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!item.iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.iconUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .background(PocketColors.SurfaceVarDark, RoundedCornerShape(14.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(PocketColors.PrimaryMuted, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.title.take(1).uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        color = PocketColors.Primary
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
                Text(
                    text = item.author?.takeIf { it.isNotBlank() } ?: "Community listing",
                    fontSize = 12.sp,
                    color = pluginsHubMutedTextColor()
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = stateChipColor
            ) {
                Text(
                    text = stateChipLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = when {
                        detailCard.installed -> PocketColors.Primary
                        !item.canInstall && item.isSupported -> pluginsHubSupportedChipContentColor()
                        !item.canInstall -> pluginsHubUnsupportedChipContentColor()
                        else -> PocketColors.Primary
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        DetailLine("Downloads", formatDownloads(item.downloads))
        DetailLine("Source", item.source.replaceFirstChar { it.uppercase() })
        DetailLine("Slug", item.slug)
        DetailLine("Description", item.description)
        item.supportMessage?.takeIf { it.isNotBlank() }?.let { message ->
            DetailLine("Support", message)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = pluginsHubMutedTextColor()
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun pluginsHubIsDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
private fun pluginsHubCardColor(): Color = if (pluginsHubIsDarkTheme()) {
    lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant, 0.22f)
} else {
    MaterialTheme.colorScheme.surface
}

@Composable
private fun pluginsHubMutedTextColor(): Color = if (pluginsHubIsDarkTheme()) {
    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f)
} else {
    MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun pluginsHubHintColor(): Color = if (pluginsHubIsDarkTheme()) {
    Color(0xFFA7D4C3)
} else {
    MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun pluginsHubBorderColor(): Color = if (pluginsHubIsDarkTheme()) {
    PocketColors.BorderDark.copy(alpha = 0.92f)
} else {
    PocketColors.BorderDark
}

@Composable
private fun pluginsHubSupportedChipContainerColor(): Color = if (pluginsHubIsDarkTheme()) {
    Color(0xFF21483B)
} else {
    Color(0xFFE8F7EA)
}

@Composable
private fun pluginsHubSupportedChipContentColor(): Color = if (pluginsHubIsDarkTheme()) {
    Color(0xFFB9E7D4)
} else {
    Color(0xFF1D7D3B)
}

@Composable
private fun pluginsHubUnsupportedChipContainerColor(): Color = if (pluginsHubIsDarkTheme()) {
    Color(0xFF4A3F2D)
} else {
    Color(0xFFFFF2D9)
}

@Composable
private fun pluginsHubUnsupportedChipContentColor(): Color = if (pluginsHubIsDarkTheme()) {
    Color(0xFFF3D48E)
} else {
    Color(0xFF8A5B00)
}

private fun normalizeInstallKey(value: String): String {
    return value.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "")
}

private fun isRemoteInstalled(
    item: PluginManager.RemoteCatalogItem,
    installedKeys: Set<String>
): Boolean {
    val candidates = listOf(item.title, item.slug, item.projectId)
        .map(::normalizeInstallKey)
        .filter { it.isNotBlank() }

    return candidates.any { candidate ->
        installedKeys.any { installed ->
            installed == candidate || installed.contains(candidate) || candidate.contains(installed)
        }
    }
}

private fun formatDownloads(downloads: Long): String {
    if (downloads <= 0) return "New"
    return when {
        downloads >= 1_000_000 -> String.format(Locale.US, "%.1fM downloads", downloads / 1_000_000f)
        downloads >= 1_000 -> String.format(Locale.US, "%.1fK downloads", downloads / 1_000f)
        else -> "$downloads downloads"
    }
}
