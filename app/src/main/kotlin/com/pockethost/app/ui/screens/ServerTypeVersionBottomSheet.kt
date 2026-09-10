package com.pockethost.app.ui.screens

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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import com.pockethost.app.ui.theme.Monocraft
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.data.model.ServerType
import com.pockethost.app.data.preferences.AppPreferences
import com.pockethost.app.data.preferences.AppPreferencesStore
import com.pockethost.app.network.InAppDownloader
import com.pockethost.app.service.ModpackManager
import com.pockethost.app.service.ServerFileManager
import com.pockethost.app.server.ServerJarImporter
import com.pockethost.app.server.ServerTypeDownloadUrls
import com.pockethost.app.ui.components.DuoButton
import com.pockethost.app.ui.components.DuoButtonVariant
import com.pockethost.app.ui.theme.raisedBorder
import com.pockethost.app.viewmodel.ServerTypeVersionViewModel
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    var downloadError by remember { mutableStateOf<String?>(null) }
    var downloadingVersion by remember { mutableStateOf<String?>(null) }
    var downloadProgressFraction by remember { mutableFloatStateOf(0f) }
    var downloadProgressPercent by remember { mutableIntStateOf(0) }
    var downloadStatusStage by remember { mutableStateOf("") }
    var downloadSizeInfo by remember { mutableStateOf("") }
    var downloadSpeedInfo by remember { mutableStateOf("") }
    var downloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var selectedModpackPageUrl by remember { mutableStateOf<String?>(null) }
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accentTextColor = if (isDarkTheme) PocketColors.PrimaryLight else PocketColors.PrimaryDark
    val scope = rememberCoroutineScope()
    val selectedVersionNeedsImport = selectedType.supportsVersionSelect && selectedVersion != null && !downloadedVersions.contains(selectedVersion)

    fun confirmSelection() {
        if (selectedType == ServerType.MODPACK) {
            val pageUrl = selectedModpackPageUrl
            val payloadPath = if (!pageUrl.isNullOrBlank()) "$selectedModpackId|$pageUrl" else selectedModpackId
            onConfirm(selectedType, selectedVersion, payloadPath)
        } else {
            onConfirm(selectedType, selectedVersion, null)
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        downloadingVersion = null
        downloadProgressFraction = 0f
        downloadProgressPercent = 0
        downloadStatusStage = ""
        downloadSizeInfo = ""
        downloadSpeedInfo = ""
    }

    // Professional inline download with speed, size, ZIP verification, and auto-confirm.
    fun startInlineDownload(version: String) {
        if (downloadingVersion != null) return // already downloading
        downloadingVersion = version
        downloadProgressFraction = 0f
        downloadProgressPercent = 0
        downloadStatusStage = "Connecting to repository…"
        downloadSizeInfo = "Preparing…"
        downloadSpeedInfo = ""
        downloadError = null
        viewModel.setSelectedVersion(version)
        val appPrefs = AppPreferences(context)
        val relayHost = appPrefs.relayHost
        downloadJob = scope.launch {
            try {
                downloadStatusStage = "Resolving official server package…"
                val downloadUrl = withContext(Dispatchers.IO) {
                    ServerTypeDownloadUrls.resolveDownloadUrl(selectedType, version, relayHost)
                }

                val targetFile = withContext(Dispatchers.IO) {
                    ServerFileManager.getServerJarFile(
                        context = context.applicationContext,
                        gameVersion = version,
                        serverType = selectedType
                    ).also { it.parentFile?.mkdirs() }
                }
                val tempFile = withContext(Dispatchers.IO) {
                    File(targetFile.parentFile, "${targetFile.name}.part")
                }

                downloadStatusStage = "Downloading ${selectedType.displayName} $version…"

                var lastTimeMs = System.currentTimeMillis()
                var lastBytes = 0L

                val downloadResult = InAppDownloader.downloadFile(
                    url = downloadUrl,
                    targetFile = tempFile,
                    onProgress = { bytesDownloaded, totalBytes, _ ->
                        val now = System.currentTimeMillis()
                        val timeDelta = now - lastTimeMs
                        if (timeDelta >= 150 || (totalBytes > 0 && bytesDownloaded >= totalBytes)) {
                            val bytesDelta = bytesDownloaded - lastBytes
                            val speedBytesPerSec = if (timeDelta > 0) (bytesDelta * 1000f) / timeDelta else 0f
                            val speedMb = speedBytesPerSec / (1024f * 1024f)

                            val curMb = bytesDownloaded / (1024f * 1024f)
                            val totMb = if (totalBytes > 0) totalBytes / (1024f * 1024f) else -1f

                            downloadSizeInfo = if (totMb > 0) {
                                String.format(Locale.US, "%.1f MB / %.1f MB", curMb, totMb)
                            } else {
                                String.format(Locale.US, "%.1f MB", curMb)
                            }

                            if (speedMb >= 0.1f) {
                                downloadSpeedInfo = String.format(Locale.US, "%.1f MB/s", speedMb)
                            }

                            if (totalBytes > 0) {
                                val frac = (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                downloadProgressFraction = frac
                                downloadProgressPercent = (frac * 100).toInt()
                            }

                            lastTimeMs = now
                            lastBytes = bytesDownloaded
                        }
                    }
                )

                if (downloadResult.isFailure) {
                    throw downloadResult.exceptionOrNull() ?: Exception("Download stream failed")
                }

                downloadStatusStage = "Verifying package integrity…"
                downloadProgressFraction = 0.98f
                downloadProgressPercent = 98

                withContext(Dispatchers.IO) {
                    if (!tempFile.exists() || tempFile.length() < 5_000L) {
                        tempFile.delete()
                        throw IllegalStateException("Downloaded server JAR is incomplete or empty.")
                    }
                    try {
                        ZipFile(tempFile).use { zip ->
                            if (zip.size() == 0) throw IllegalStateException("Empty archive")
                        }
                    } catch (e: Exception) {
                        tempFile.delete()
                        throw IllegalStateException("Downloaded file is not a valid server JAR: ${e.message}")
                    }
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    val moved = tempFile.renameTo(targetFile)
                    if (!moved) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                }

                downloadStatusStage = "Ready!"
                downloadProgressFraction = 1f
                downloadProgressPercent = 100
                kotlinx.coroutines.delay(200)
                downloadingVersion = null

                viewModel.onServerJarImported(version)
                viewModel.setSelectedVersion(version)
                Toast.makeText(context, "${selectedType.displayName} $version ready!", Toast.LENGTH_SHORT).show()
                confirmSelection()
            } catch (e: kotlinx.coroutines.CancellationException) {
                downloadingVersion = null
                downloadError = null
            } catch (e: Exception) {
                downloadingVersion = null
                val msg = e.localizedMessage ?: "Unknown download error"
                downloadError = msg
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            } finally {
                downloadJob = null
            }
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ServerType.entries.filter { it.isEnabled }.chunked(3).forEach { rowTypes: List<ServerType> ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowTypes.forEach { type ->
                                val isSelected = selectedType == type
                                val cardBg = if (isSelected) PocketColors.primaryBg else PocketColors.InactiveBg
                                val cardBorder = if (isSelected) PocketColors.primaryBorder else PocketColors.InactiveBorder
                                val cardDepth = if (isSelected) PocketColors.primaryDepth else PocketColors.InactiveBorderBottom
                                val cardText = if (isSelected) PocketColors.PrimaryText else PocketColors.InactiveText

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
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
                                                modifier = Modifier.size(13.dp),
                                                tint = cardText
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                        }
                                        Text(
                                            text = type.displayName,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = cardText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val typeDescription = when (selectedType) {
                    ServerType.PAPER -> "Fast and optimized server software. Supports Bukkit, Spigot, and Paper plugins."
                    ServerType.PURPUR -> "Paper fork offering extensive customization and full plugin support."
                    ServerType.FABRIC -> "Lightweight modular loader compatible with Fabric mods."
                    ServerType.VANILLA -> "Standard official Minecraft server software."
                    ServerType.BEDROCK -> "Dedicated server environment built specifically for Bedrock Edition players."
                    ServerType.MODPACK -> "Curated community modpack with custom mods and configs."
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = typeDescription,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
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
                                            .clickable(enabled = downloadingVersion == null) {
                                                viewModel.setSelectedVersion(version)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (downloadingVersion == version) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp,
                                                    color = PocketColors.Primary
                                                )
                                            } else {
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
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = version,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = itemText
                                                )
                                                if (downloadingVersion == version) {
                                                    Text(
                                                        text = "Downloading: $downloadProgressPercent%",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = PocketColors.Primary
                                                    )
                                                } else if (isDownloaded) {
                                                    Text(
                                                        text = "Imported",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = itemSubtext
                                                    )
                                                }
                                            }
                                            if (isDownloaded && downloadingVersion != version) {
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
                                        .clickable(enabled = downloadingVersion == null) {
                                            viewModel.setSelectedVersion(version)
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

                val isSelectedVersionDownloaded = selectedVersion != null && downloadedVersions.contains(selectedVersion)
                val isDownloadingThisVersion = downloadingVersion != null
                val animatedProgress by animateFloatAsState(
                    targetValue = downloadProgressFraction,
                    animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
                    label = "download_progress"
                )

                AnimatedVisibility(
                    visible = isDownloadingThisVersion,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        color = if (isDarkTheme) PocketColors.Primary.copy(alpha = 0.12f) else PocketColors.Primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, PocketColors.Primary.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = PocketColors.Primary,
                                        strokeWidth = 2.5.dp
                                    )
                                    Column {
                                        Text(
                                            text = "Downloading ${selectedType.displayName} $downloadingVersion",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            fontFamily = Monocraft,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = downloadStatusStage,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = PocketColors.Primary.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text(
                                        text = "$downloadProgressPercent%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = Monocraft,
                                        color = PocketColors.Primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = PocketColors.Primary,
                                trackColor = PocketColors.Primary.copy(alpha = 0.2f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = downloadSizeInfo,
                                    fontSize = 11.sp,
                                    fontFamily = Monocraft,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                                if (downloadSpeedInfo.isNotBlank()) {
                                    Text(
                                        text = downloadSpeedInfo,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = Monocraft,
                                        color = PocketColors.Primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!downloadError.isNullOrBlank()) {
                    Text(
                        text = downloadError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                val isConfirmEnabled = when {
                    isDownloadingThisVersion -> false
                    selectedType.supportsVersionSelect -> selectedVersion != null
                    else -> selectedModpackId != null
                }

                val buttonText = when {
                    isDownloadingThisVersion -> "DOWNLOADING…"
                    isConfirmEnabled && selectedVersionNeedsImport -> "Download"
                    isConfirmEnabled -> "CONFIRM"
                    else -> "SELECT A VERSION"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DuoButton(
                        text = "CANCEL",
                        onClick = {
                            if (isDownloadingThisVersion) {
                                cancelDownload()
                            } else {
                                onDismissRequest()
                            }
                        },
                        variant = DuoButtonVariant.Danger,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    DuoButton(
                        text = buttonText,
                        onClick = {
                            if (selectedVersionNeedsImport) {
                                selectedVersion?.let { startInlineDownload(it) }
                            } else {
                                confirmSelection()
                            }
                        },
                        enabled = isConfirmEnabled,
                        modifier = Modifier.weight(1.3f),
                    )
                }
            }
        }
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
        )
    }
}
