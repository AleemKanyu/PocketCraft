package com.pocketcraft.server.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.pocketcraft.server.data.model.ServerType
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.service.ModpackManager
import com.pocketcraft.server.service.ServerFileManager
import com.pocketcraft.server.server.ServerJarImporter
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.components.DuoButtonVariant
import com.pocketcraft.server.ui.components.ServerJarPickerBottomSheet
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.theme.raisedBorder
import com.pocketcraft.server.viewmodel.ServerTypeVersionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerTypeVersionBottomSheet(
    onDismissRequest: () -> Unit,
    onConfirm: (ServerType, String?, String?) -> Unit,
    currentServerType: ServerType = ServerType.PAPER,
    currentGameVersion: String? = null,
    currentCustomJarPath: String? = null,
    viewModel: ServerTypeVersionViewModel = viewModel()
) {
    val context = LocalContext.current
    val selectedType by viewModel.selectedType.collectAsState()
    val availableVersions by viewModel.availableVersions.collectAsState()
    val selectedVersion by viewModel.selectedVersion.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val downloadedVersions by viewModel.downloadedVersions.collectAsState()
    var modpackQuery by remember { mutableStateOf("") }
    var modpackResults by remember { mutableStateOf<List<ModpackManager.ModpackCatalogItem>>(emptyList()) }
    var modpackLoading by remember { mutableStateOf(false) }
    var modpackError by remember { mutableStateOf<String?>(null) }
    val isOffline by viewModel.isOffline.collectAsState()
    var selectedModpackId by remember { mutableStateOf<String?>(null) }
    var versionToDelete by remember { mutableStateOf<String?>(null) }
    var versionToImport by remember { mutableStateOf<String?>(null) }
    var selectedModpackPageUrl by remember { mutableStateOf<String?>(null) }
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accentTextColor = if (isDarkTheme) PocketColors.PrimaryLight else PocketColors.PrimaryDark
    val setupGuideShown by AppPreferencesStore.isServerTypeSetupGuideShownFlow(context).collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    var showServerTypeGuide by remember { mutableStateOf(false) }

    fun confirmSelection() {
        if (selectedType == ServerType.MODPACK) {
            val pageUrl = selectedModpackPageUrl
            val payloadPath = if (!pageUrl.isNullOrBlank()) "$selectedModpackId|$pageUrl" else selectedModpackId
            onConfirm(selectedType, selectedVersion, payloadPath)
        } else {
            onConfirm(selectedType, selectedVersion, null)
        }
    }

    fun confirmWithGuideIfNeeded() {
        val changingServerType = selectedType != currentServerType
        if (!setupGuideShown && changingServerType) {
            showServerTypeGuide = true
        } else {
            confirmSelection()
        }
    }

    LaunchedEffect(currentServerType, currentGameVersion, currentCustomJarPath) {
        viewModel.initializeSelection(
            serverType = currentServerType,
            gameVersion = currentGameVersion,
            customJarPath = currentCustomJarPath
        )
        if (currentServerType == ServerType.MODPACK) {
            selectedModpackId = currentCustomJarPath
            selectedModpackPageUrl = null // page URL not stored in prefs, will be set on re-select
        }
    }


    LaunchedEffect(selectedType, modpackQuery) {
        if (selectedType != ServerType.MODPACK) return@LaunchedEffect
        if (modpackQuery.isNotBlank()) delay(350)
        modpackLoading = true
        modpackError = null
        val result = ModpackManager.searchModpacks(query = modpackQuery.trim(), limit = 48)
        result.onSuccess { items ->
            modpackResults = items
        }.onFailure {
            modpackResults = emptyList()
            modpackError = it.message ?: "Could not load modpacks right now."
        }
        modpackLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val bottomSheetBg = if (isDarkTheme) PocketColors.SurfaceCardDark else PocketColors.SurfaceCard
        val bottomSheetBorder = if (isDarkTheme) PocketColors.CardBorderDark else PocketColors.CardBorder
        val bottomSheetDepth = if (isDarkTheme) PocketColors.CardBorderBottomDark else PocketColors.CardBorderBottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(bottomSheetBg)
                .raisedBorder(
                    color = bottomSheetBorder,
                    depthColor = bottomSheetDepth,
                    cornerRadius = 18.dp,
                    borderWidth = 1.5.dp,
                    depthWidth = 3.dp
                )
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = "Configure Server",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = accentTextColor,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                Text(
                    text = "Server Type",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentTextColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ServerType.values().filter { it != ServerType.MODPACK }.forEach { type ->
                        val isSelected = selectedType == type
                        val cardBg = if (isSelected) PocketColors.primaryBg else PocketColors.InactiveBg
                        val cardBorder = if (isSelected) PocketColors.primaryBorder else PocketColors.InactiveBorder
                        val cardDepth = if (isSelected) PocketColors.primaryDepth else PocketColors.InactiveBorderBottom
                        val cardText = if (isSelected) PocketColors.PrimaryText else PocketColors.InactiveText

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardBg)
                                .raisedBorder(
                                    color = cardBorder,
                                    depthColor = cardDepth,
                                    cornerRadius = 14.dp,
                                    borderWidth = 1.5.dp,
                                    depthWidth = 3.dp
                                )
                                .clickable {
                                    selectedModpackId = null
                                    viewModel.setServerType(type)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = cardText
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = type.displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = cardText
                                )
                            }
                        }
                    }
                }

                if (selectedType != ServerType.PAPER) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PocketColors.Warning.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PocketColors.Warning),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Warning",
                                tint = PocketColors.Warning,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Paper runs the best and starts the fastest. Other versions might take much longer to start, but they also run.",
                                fontSize = 12.sp,
                                color = if (isDarkTheme) Color.White else PocketColors.TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val modpackBg = PocketColors.InactiveBg
                val modpackBorder = PocketColors.InactiveBorder
                val modpackDepth = PocketColors.InactiveBorderBottom
                val modpackText = PocketColors.InactiveText.copy(alpha = 0.6f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(modpackBg)
                        .raisedBorder(
                            color = modpackBorder,
                            depthColor = modpackDepth,
                            cornerRadius = 16.dp,
                            borderWidth = 1.5.dp,
                            depthWidth = 3.dp
                        )
                        .clickable {
                            Toast.makeText(context, "Modpack support coming soon!", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = modpackText
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = ServerType.MODPACK.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                            color = modpackText
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = PocketColors.InactiveBorder.copy(alpha = 0.5f)
                        ) {
                            Text(
                                "COMING SOON",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PocketColors.InactiveText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedType.supportsVersionSelect) {
                    Text(
                        text = "Game Version",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentTextColor,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (isLoading && availableVersions.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Loading version...",
                                fontSize = 12.sp,
                                color = accentTextColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (isLoading) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    text = if (availableVersions.isNotEmpty()) "Refreshing versions..." else "Loading versions...",
                                    fontSize = 12.sp,
                                    color = accentTextColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                            ) {
                                items(availableVersions) { version ->
                                    val isSelected = selectedVersion == version
                                    val isDownloaded = downloadedVersions.contains(version)

                                    val itemBg = when {
                                        isSelected -> PocketColors.primaryBg
                                        isDownloaded -> PocketColors.TagBg
                                        else -> if (isDarkTheme) PocketColors.SurfaceVarDark else PocketColors.SurfaceCard
                                    }
                                    val itemBorder = when {
                                        isSelected -> PocketColors.primaryBorder
                                        isDownloaded -> PocketColors.TagBorder
                                        else -> if (isDarkTheme) PocketColors.CardBorderDark else PocketColors.CardBorder
                                    }
                                    val itemDepth = when {
                                        isSelected -> PocketColors.primaryDepth
                                        isDownloaded -> PocketColors.TagBorderBottom
                                        else -> if (isDarkTheme) PocketColors.CardBorderBottomDark else PocketColors.CardBorderBottom
                                    }
                                    val itemText = when {
                                        isSelected -> PocketColors.PrimaryText
                                        isDownloaded -> PocketColors.TagText
                                        else -> if (isDarkTheme) Color.White else PocketColors.TextPrimary
                                    }
                                    val itemSubtext = when {
                                        isSelected -> PocketColors.PrimaryText.copy(alpha = 0.7f)
                                        isDownloaded -> PocketColors.TagText.copy(alpha = 0.7f)
                                        else -> if (isDarkTheme) PocketColors.TextMuted else PocketColors.TextSecondary
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(itemBg)
                                            .raisedBorder(
                                                color = itemBorder,
                                                depthColor = itemDepth,
                                                cornerRadius = 12.dp,
                                                borderWidth = 1.5.dp,
                                                depthWidth = 3.dp
                                            )
                                            .clickable {
                                                if (isDownloaded) {
                                                    viewModel.setSelectedVersion(version)
                                                } else {
                                                    versionToImport = version
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = when {
                                                    isSelected -> Icons.Default.Check
                                                    isDownloaded -> Icons.Default.Download
                                                    else -> Icons.Default.AutoAwesome
                                                },
                                                contentDescription = null,
                                                tint = itemText,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = version,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = itemText
                                                )
                                                if (isDownloaded) {
                                                    Text(
                                                        text = "Imported",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = itemSubtext
                                                    )
                                                }
                                            }
                                            if (isDownloaded) {
                                                IconButton(onClick = { versionToDelete = version }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete downloaded version",
                                                        tint = if (isSelected) PocketColors.PrimaryText else MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    } else {
                        if (isOffline || error != null) {
                            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                Text(
                                    text = if (isOffline) "No internet connection" else (error ?: "Unknown error"),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                DuoButton(
                                    text = "Try Again",
                                    onClick = { viewModel.refresh() },
                                    variant = DuoButtonVariant.Primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                )
                            }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                        ) {
                            items(availableVersions) { version ->
                                val isSelected = selectedVersion == version
                                val isDownloaded = downloadedVersions.contains(version)

                                val itemBg = when {
                                    isSelected -> PocketColors.primaryBg
                                    isDownloaded -> PocketColors.TagBg
                                    else -> if (isDarkTheme) PocketColors.SurfaceVarDark else PocketColors.SurfaceCard
                                }
                                val itemBorder = when {
                                    isSelected -> PocketColors.primaryBorder
                                    isDownloaded -> PocketColors.TagBorder
                                    else -> if (isDarkTheme) PocketColors.CardBorderDark else PocketColors.CardBorder
                                }
                                val itemDepth = when {
                                    isSelected -> PocketColors.primaryDepth
                                    isDownloaded -> PocketColors.TagBorderBottom
                                    else -> if (isDarkTheme) PocketColors.CardBorderBottomDark else PocketColors.CardBorderBottom
                                }
                                val itemText = when {
                                    isSelected -> PocketColors.PrimaryText
                                    isDownloaded -> PocketColors.TagText
                                    else -> if (isDarkTheme) Color.White else PocketColors.TextPrimary
                                }
                                val itemSubtext = when {
                                    isSelected -> PocketColors.PrimaryText.copy(alpha = 0.7f)
                                    isDownloaded -> PocketColors.TagText.copy(alpha = 0.7f)
                                    else -> if (isDarkTheme) PocketColors.TextMuted else PocketColors.TextSecondary
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(itemBg)
                                        .raisedBorder(
                                            color = itemBorder,
                                            depthColor = itemDepth,
                                            cornerRadius = 12.dp,
                                            borderWidth = 1.5.dp,
                                            depthWidth = 3.dp
                                        )
                                        .clickable {
                                            if (isDownloaded) {
                                                viewModel.setSelectedVersion(version)
                                            } else {
                                                versionToImport = version
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                isSelected -> Icons.Default.Check
                                                isDownloaded -> Icons.Default.Download
                                                else -> Icons.Default.AutoAwesome
                                            },
                                            contentDescription = null,
                                            tint = itemText,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = version,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = itemText
                                            )
                                            if (isDownloaded) {
                                                Text(
                                                    text = "Imported",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = itemSubtext
                                                )
                                            }
                                        }
                                        if (isDownloaded) {
                                            IconButton(onClick = { versionToDelete = version }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete downloaded version",
                                                    tint = if (isSelected) PocketColors.PrimaryText else MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    val selectedVersionNeedsImport =
                        selectedVersion != null && !downloadedVersions.contains(selectedVersion)
                    if (selectedVersionNeedsImport) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Download and select the server JAR for $selectedVersion before continuing.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (selectedType == ServerType.MODPACK) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Modpacks (Modrinth + CurseForge)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentTextColor,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = modpackQuery,
                        onValueChange = { modpackQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Search modpacks...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    when {
                        modpackLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        modpackError != null -> {
                            Text(
                                text = modpackError ?: "Could not load modpacks.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                            ) {
                                items(modpackResults.take(30), key = { "${it.source}:${it.id}" }) { modpack ->
                                    val isSelected = selectedModpackId == modpack.id
                                    val isDownloaded = downloadedVersions.contains(modpack.id)

                                    val itemBg = when {
                                        isSelected -> PocketColors.primaryBg
                                        isDownloaded -> PocketColors.TagBg
                                        else -> if (isDarkTheme) PocketColors.SurfaceVarDark else PocketColors.SurfaceCard
                                    }
                                    val itemBorder = when {
                                        isSelected -> PocketColors.primaryBorder
                                        isDownloaded -> PocketColors.TagBorder
                                        else -> if (isDarkTheme) PocketColors.CardBorderDark else PocketColors.CardBorder
                                    }
                                    val itemDepth = when {
                                        isSelected -> PocketColors.primaryDepth
                                        isDownloaded -> PocketColors.TagBorderBottom
                                        else -> if (isDarkTheme) PocketColors.CardBorderBottomDark else PocketColors.CardBorderBottom
                                    }
                                    val itemText = when {
                                        isSelected -> PocketColors.PrimaryText
                                        isDownloaded -> PocketColors.TagText
                                        else -> if (isDarkTheme) Color.White else PocketColors.TextPrimary
                                    }
                                    val itemSubtext = when {
                                        isSelected -> PocketColors.PrimaryText.copy(alpha = 0.7f)
                                        isDownloaded -> PocketColors.TagText.copy(alpha = 0.7f)
                                        else -> if (isDarkTheme) PocketColors.TextMuted else PocketColors.TextSecondary
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(itemBg)
                                            .raisedBorder(
                                                color = itemBorder,
                                                depthColor = itemDepth,
                                                cornerRadius = 12.dp,
                                                borderWidth = 1.5.dp,
                                                depthWidth = 3.dp
                                            )
                                            .clickable(enabled = modpack.installSupported) {
                                                selectedModpackId = modpack.id
                                                selectedModpackPageUrl = modpack.pageUrl
                                                viewModel.setServerType(ServerType.MODPACK)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (!modpack.iconUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = modpack.iconUrl,
                                                    contentDescription = modpack.title,
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = itemText,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = modpack.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    color = itemText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${modpack.source.name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} • ${modpack.downloads} downloads",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = itemSubtext
                                                )
                                                if (!modpack.supportMessage.isNullOrBlank()) {
                                                    Text(
                                                        text = if (isDownloaded) "Downloaded" else modpack.supportMessage,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (modpack.installSupported) (if (isSelected) PocketColors.PrimaryText else PocketColors.primaryBg) else MaterialTheme.colorScheme.error,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            if (isSelected || isDownloaded) {
                                                Icon(
                                                    imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Download,
                                                    contentDescription = null,
                                                    tint = itemText,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val isConfirmEnabled = if (selectedType.supportsVersionSelect) {
                    selectedVersion != null && downloadedVersions.contains(selectedVersion)
                } else {
                    // MODPACK: requires a modpack to be selected
                    selectedModpackId != null
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DuoButton(
                        text = "CANCEL",
                        onClick = onDismissRequest,
                        variant = DuoButtonVariant.Danger,
                        modifier = Modifier
                            .weight(1f),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    DuoButton(
                        text = "CONFIRM",
                        onClick = { confirmWithGuideIfNeeded() },
                        enabled = isConfirmEnabled,
                        modifier = Modifier
                            .weight(1f),
                    )
                }
            }
        }
    }

    if (showServerTypeGuide) {
        AlertDialog(
            onDismissRequest = { showServerTypeGuide = false },
            title = {
                Text(
                    text = "Before changing server type",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ServerTypeGuideLine("Paper and Purpur are best for plugin servers.")
                    ServerTypeGuideLine("Fabric is for Fabric mods and needs a matching imported server JAR.")
                    ServerTypeGuideLine("Modpacks may need a .mrpack or Server Pack ZIP, and large packs can take a while to import.")
                    ServerTypeGuideLine("Changing type can affect worlds, mods, plugins, and configs. Use a separate world if you are testing.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            AppPreferencesStore.setServerTypeSetupGuideShown(context, true)
                        }
                        showServerTypeGuide = false
                        confirmSelection()
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerTypeGuide = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (versionToDelete != null) {
        AlertDialog(
            onDismissRequest = { versionToDelete = null },
            title = { Text("Delete Version", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete the imported files for $versionToDelete? This will free up storage, but you will need to import the server JAR again to use it.") },
            confirmButton = {
                TextButton(onClick = {
                    versionToDelete?.let { viewModel.deleteDownloadedVersion(it) }
                    versionToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { versionToDelete = null }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    versionToImport?.let { version ->
        ServerJarPickerBottomSheet(
            serverType = selectedType,
            version = version,
            onDismiss = { versionToImport = null },
            onJarSelected = { uri ->
                val targetFile = ServerFileManager.getServerJarFile(
                    context = context.applicationContext,
                    gameVersion = version,
                    serverType = selectedType
                )
                when (val result = ServerJarImporter.importServerJar(
                    context = context.applicationContext,
                    uri = uri,
                    targetFile = targetFile,
                    serverType = selectedType
                )) {
                    is ServerJarImporter.ImportResult.Success -> {
                        viewModel.onServerJarImported(version)
                        versionToImport = null
                        Toast.makeText(context, "${selectedType.displayName} $version imported.", Toast.LENGTH_SHORT).show()
                    }
                    is ServerJarImporter.ImportResult.Error -> {
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun ServerTypeGuideLine(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            color = PocketColors.Primary,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}
