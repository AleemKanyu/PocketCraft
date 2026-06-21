package com.pocketcraft.server.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import com.pocketcraft.server.service.PluginSourceUrls
import com.pocketcraft.server.ui.components.PluginInstallBottomSheet
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
import com.pocketcraft.server.util.LocalAppStrings

private enum class ContentTab(
    val type: PluginManager.ContentType
) {
    PLUGINS(PluginManager.ContentType.PLUGINS),
    MODS(PluginManager.ContentType.MODS),
    PACKS(PluginManager.ContentType.RESOURCE_PACKS)
}

private fun ContentTab.label(s: com.pocketcraft.server.util.AppStrings): String = when (this) {
    ContentTab.PLUGINS -> s.hubTabPlugins
    ContentTab.MODS    -> s.hubTabMods
    ContentTab.PACKS   -> s.hubTabResourcePacks
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PluginsHubScreen(
    stateHolder: ServerStateHolder,
    onMessage: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val s = LocalAppStrings.current

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
    var discoverPage by remember { mutableIntStateOf(0) }
    var detailCard by remember { mutableStateOf<ContentDetailCard?>(null) }
    var pendingRemoteInstall by remember { mutableStateOf<PluginManager.RemoteCatalogItem?>(null) }
    var dependenciesList by remember { mutableStateOf<List<PluginManager.ModDependency>>(emptyList()) }
    var isLoadingDependencies by remember { mutableStateOf(false) }

    val availableTabs = remember(stateHolder.config.serverType) {
        when (stateHolder.config.serverType) {
            com.pocketcraft.server.data.model.ServerType.FABRIC,
            com.pocketcraft.server.data.model.ServerType.MODPACK -> listOf(ContentTab.MODS, ContentTab.PACKS)
            com.pocketcraft.server.data.model.ServerType.PAPER,
            com.pocketcraft.server.data.model.ServerType.PURPUR -> listOf(ContentTab.PLUGINS, ContentTab.PACKS)
        }
    }

    fun currentTab(): ContentTab = availableTabs[selectedTab.coerceIn(0, availableTabs.lastIndex)]

    LaunchedEffect(pendingRemoteInstall) {
        val item = pendingRemoteInstall
        if (item != null && currentTab().type == PluginManager.ContentType.MODS) {
            isLoadingDependencies = true
            dependenciesList = emptyList()
            try {
                dependenciesList = PluginManager.fetchModDependencies(context, item.projectId)
            } catch (e: Exception) {
                android.util.Log.e("PluginsHub", "Failed to load dependencies: ${e.message}")
            } finally {
                isLoadingDependencies = false
            }
        } else {
            dependenciesList = emptyList()
            isLoadingDependencies = false
        }
    }
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val selectedTabColor = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
    val unselectedTabColor = if (isDarkTheme) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f) else MaterialTheme.colorScheme.onSurfaceVariant

    val resourcePackIcons = remember { mutableStateMapOf<String, File?>() }

    val isModsTab = currentTab().type == PluginManager.ContentType.MODS
    val runtimeKey = remember(stateHolder.activeWorld) {
        PluginManager.getRuntimeKeyForWorld(context, stateHolder.activeWorld)
    }
    val supportsMods = remember(runtimeKey) { PluginManager.supportsMods(runtimeKey) }
    val showModsWarning = isModsTab && !supportsMods

    LaunchedEffect(availableTabs) {
        if (selectedTab > availableTabs.lastIndex) {
            selectedTab = 0
        }
    }

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

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            isUploading = true
            var successCount = 0
            var failCount = 0
            var lastErrorMessage: String? = null
            uris.forEachIndexed { index, uri ->
                uploadProgress = (index * 100) / uris.size
                val result = PluginManager.installFromUri(
                    context = context,
                    uri = uri,
                    worldName = stateHolder.activeWorld,
                    type = currentTab().type,
                    runtimeKey = runtimeKey,
                    onProgress = { currentFileProgress ->
                        uploadProgress = ((index * 100) + currentFileProgress) / uris.size
                    }
                )
                result.onSuccess {
                    successCount++
                }.onFailure {
                    failCount++
                    lastErrorMessage = it.message
                }
            }
            isUploading = false
            refreshDownloadedItems()
            val label = currentTab().label(s).lowercase()
            if (successCount > 0 && failCount == 0) {
                Toast.makeText(context, "Successfully added $successCount $label.", Toast.LENGTH_SHORT).show()
            } else if (successCount > 0 && failCount > 0) {
                onMessage("Added $successCount $label, but $failCount failed (last error: ${lastErrorMessage ?: "unknown"}).")
            } else {
                onMessage("Failed to add files: ${lastErrorMessage ?: "unknown"}")
            }
        }
    }

    LaunchedEffect(selectedTab, stateHolder.activeWorld) {
        resourcePackIcons.clear()
        downloadedItems = loadDownloadedItems()
        discoverPage = 0
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
            limit = 100
        )

        result.onSuccess { remoteItems ->
            discoveredItems = remoteItems
            if (remoteItems.isEmpty()) {
                errorMessage = "No online results found right now."
            }
        }.onFailure { error ->
            discoveredItems = emptyList()
            errorMessage = error.message ?: s.hubNoOnlineResults.format(currentTab().label(s))
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
    val discoverPageSize = 10
    val discoverPageCount = ((sortedDiscoveredItems.size + discoverPageSize - 1) / discoverPageSize).coerceAtLeast(1)
    val pagedDiscoveredItems = remember(sortedDiscoveredItems, discoverPage) {
        val pageIndex = discoverPage.coerceAtLeast(0)
        sortedDiscoveredItems.drop(pageIndex * discoverPageSize).take(discoverPageSize)
    }

    LaunchedEffect(query, selectedTab, catalogRetryToken) {
        discoverPage = 0
    }

    LaunchedEffect(sortedDiscoveredItems.size) {
        val maxPageIndex = (discoverPageCount - 1).coerceAtLeast(0)
        if (discoverPage > maxPageIndex) {
            discoverPage = maxPageIndex
        }
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
            availableTabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        selectedTab = index
                        query = ""
                    },
                    selectedContentColor = selectedTabColor,
                    unselectedContentColor = unselectedTabColor,
                    text = { Text(tab.label(s)) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = s.hubSearchPlaceholder.format(currentTab().label(s).lowercase(Locale.US))
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
                                text = s.hubModLoaderRequired,
                                fontWeight = FontWeight.ExtraBold,
                                color = PocketColors.Warning
                            )
                            Text(
                                text = s.hubModLoaderDesc,
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
                            s.hubAddingFile.format(uploadProgress)
                        } else {
                            s.hubDownloading.format(downloadProgress)
                        },
                        fontSize = 12.sp,
                        color = pluginsHubMutedTextColor()
                    )
                }
            }

            item {
                SectionHeader(
                    title = s.hubSectionDownloaded,
                    subtitle = s.hubSectionDownloadedDesc,
                    actionLabel = s.hubAdd,
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
                                text = s.hubTapToExpand,
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
                                onMessage("${item.name} ${if (item.enabled) s.disabled else s.enabled}. ${s.hubDetailApplyDesc}")
                            } else {
                                onMessage("${s.hubDetailDescription}: ${item.name}")
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
                    title = if (query.isBlank()) "Trending ${currentTab().label(s)}" else s.hubSectionDiscover,
                    subtitle = if (query.isBlank()) {
                        "Top picks for ${currentTab().label(s).lowercase(Locale.US)} right now."
                    } else {
                        s.hubSectionDiscoverDesc
                    },
                    actionLabel = if (errorMessage != null && !isDiscoverLoading) s.hubRetry else null,
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
                                    text = s.hubLoadingOnline,
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
                                    text = s.hubSearchRetry,
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
                    items(pagedDiscoveredItems, key = { it.catalogKey }) { remote ->
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
                                if (!installed && remote.canInstall) {
                                    pendingRemoteInstall = remote
                                }
                            }
                        )
                    }
                    if (sortedDiscoveredItems.size > discoverPageSize) {
                        item {
                            DiscoverPagerRow(
                                currentPage = discoverPage,
                                pageCount = discoverPageCount,
                                onPrevious = { discoverPage = (discoverPage - 1).coerceAtLeast(0) },
                                onNext = { discoverPage = (discoverPage + 1).coerceAtMost(discoverPageCount - 1) }
                            )
                        }
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
                Text(s.hubDeleteTitle.format(deleting.name), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(s.hubDeleteDesc, color = pluginsHubMutedTextColor())
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
                    Text(s.hubDeleteAction, color = PocketColors.Danger)
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
                    Text(s.cancel)
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
                    text = s.hubSectionDownloaded + " ${currentTab().label(s)}",
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
                    Text(s.hubUploadFromDevice)
                }

                TextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    singleLine = true,
                    placeholder = { Text(s.hubPasteUrl) },
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
                        Text(s.cancel)
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                val cleanUrl = urlInput.trim()
                                if (cleanUrl.isNotBlank()) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)))
                                }
                                onMessage("Download the file in your browser, then use Upload from device.")
                            }
                        },
                        enabled = urlInput.isNotBlank()
                    ) {
                        Text("Open URL")
                    }
                }
            }
        }
    }

    pendingRemoteInstall?.let { item ->
        PluginInstallBottomSheet(
            itemName = item.title,
            itemPageUrl = PluginSourceUrls.getContentPage(item, currentTab().type),
            pickerMimeTypes = when (currentTab().type) {
                PluginManager.ContentType.RESOURCE_PACKS -> arrayOf("application/zip", "*/*")
                else -> arrayOf("application/java-archive", "*/*")
            },
            onDismiss = { pendingRemoteInstall = null },
            onFileSelected = { uri ->
                scope.launch {
                    isUploading = true
                    uploadProgress = 0
                    val fileName = PluginManager.getFileNameFromUri(context, uri) ?: item.title
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
                        pendingRemoteInstall = null
                        refreshDownloadedItems()
                        Toast.makeText(context, "${s.hubInstalled}: $fileName", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        onMessage(it.message ?: "Could not add file.")
                    }
                }
            },
            dependencies = dependenciesList,
            isLoadingDependencies = isLoadingDependencies,
            contentTypeLabel = when (currentTab().type) {
                PluginManager.ContentType.PLUGINS -> "PLUGIN"
                PluginManager.ContentType.MODS -> "MOD"
                PluginManager.ContentType.RESOURCE_PACKS -> "PACK"
            },
            worldName = stateHolder.activeWorld,
            onDependencyFileSelected = { dep, uri, onComplete ->
                scope.launch {
                    isUploading = true
                    uploadProgress = 0
                    val fileName = PluginManager.getFileNameFromUri(context, uri) ?: dep.title
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
                        onComplete()
                        Toast.makeText(context, "${s.hubInstalled}: $fileName", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        onMessage(it.message ?: "Could not add file.")
                    }
                }
            }
        )
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
                val rotationAngle by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    label = "arrowRotation"
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleExpand)
                        .border(BorderStroke(1.dp, pluginsHubBorderColor()), CircleShape)
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(rotationAngle),
                        tint = if (pluginsHubIsDarkTheme()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
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
    val s = LocalAppStrings.current
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
                text = s.hubNoDownloaded.format(tab.label(s).lowercase(Locale.US)),
                color = pluginsHubMutedTextColor()
            )
        }
    }
}

@Composable
private fun EmptyOnlineCard(tab: ContentTab) {
    val s = LocalAppStrings.current
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
                text = s.hubNoOnlineResults.format(tab.label(s).lowercase(Locale.US)),
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
            tab == ContentTab.MODS -> {
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
    val s = LocalAppStrings.current
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
                                text = s.hubInstalled,
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
                                text = s.hubSupportedMod,
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
                                text = s.hubUnsupported,
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
                            Text(if (isDownloading) s.hubInstalling else s.hubInstall)
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
private fun DiscoverPagerRow(
    currentPage: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    PocketCraftCard(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, pluginsHubBorderColor()),
        colors = CardDefaults.cardColors(containerColor = pluginsHubCardColor())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = currentPage > 0,
                border = BorderStroke(1.dp, pluginsHubBorderColor())
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = "Previous page",
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = "Page ${currentPage + 1} of $pageCount",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = pluginsHubMutedTextColor()
            )
            OutlinedButton(
                onClick = onNext,
                enabled = currentPage < pageCount - 1,
                border = BorderStroke(1.dp, pluginsHubBorderColor())
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Next page",
                    modifier = Modifier.size(16.dp)
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
    val s = LocalAppStrings.current
    val statusLabel = if (item.enabled) s.hubEnabledStatus else s.hubDisabledStatus
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
                    text = detailCard.tab.label(s).dropLastWhile { it == 's' },
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

        DetailLine(s.hubDetailFile, item.fileName)
        DetailLine(s.hubDetailVersion, item.version.ifBlank { s.hubDetailVersionNotProvided })
        DetailLine(s.hubDetailSize, String.format(Locale.US, "%.2f MB", item.sizeMb))
        DetailLine(s.hubDetailApply, s.hubDetailApplyDesc)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismissRequest) {
                Text(s.close)
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
    val s = LocalAppStrings.current
    val stateChipLabel = when {
        detailCard.installed -> s.hubInstalled
        !item.canInstall && item.isSupported -> s.hubSupportedMod
        !item.canInstall -> s.hubUnsupported
        else -> detailCard.tab.label(s).dropLastWhile { it == 's' }
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
                    text = item.author?.takeIf { it.isNotBlank() } ?: s.hubCommunityListing,
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

        DetailLine(s.hubDetailDownloads, formatDownloads(item.downloads))
        DetailLine(s.hubDetailSource, item.source.replaceFirstChar { it.uppercase() })
        DetailLine(s.hubDetailSlug, item.slug)
        DetailLine(s.hubDetailDescription, item.description)
        item.supportMessage?.takeIf { it.isNotBlank() }?.let { message ->
            DetailLine(s.hubDetailSupport, message)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismissRequest) {
                Text(s.close)
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
