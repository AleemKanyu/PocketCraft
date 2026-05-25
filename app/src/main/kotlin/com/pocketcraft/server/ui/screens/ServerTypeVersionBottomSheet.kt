package com.pocketcraft.server.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

import com.pocketcraft.server.data.model.ServerType
import com.pocketcraft.server.service.ModpackManager
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.components.DuoButtonVariant
import com.pocketcraft.server.ui.theme.PocketColors
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
    val selectedType by viewModel.selectedType.collectAsState()
    val availableVersions by viewModel.availableVersions.collectAsState()
    val selectedVersion by viewModel.selectedVersion.collectAsState()
    val customJarPath by viewModel.customJarPath.collectAsState()
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

    LaunchedEffect(currentServerType, currentGameVersion, currentCustomJarPath) {
        viewModel.initializeSelection(
            serverType = currentServerType,
            gameVersion = currentGameVersion,
            customJarPath = currentCustomJarPath
        )
        if (currentServerType == ServerType.MODPACK) {
            selectedModpackId = currentCustomJarPath
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.setCustomJarPath(it.toString()) }
    }

    LaunchedEffect(selectedType, modpackQuery) {
        if (selectedType != ServerType.MODPACK) return@LaunchedEffect
        modpackLoading = true
        modpackError = null
        val result = ModpackManager.searchModpacks(query = modpackQuery.trim(), limit = 20)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .border(2.dp, PocketColors.Primary.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = "Configure Server",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = PocketColors.PrimaryDark,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                Text(
                    text = "Server Type",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PocketColors.PrimaryDark,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ServerType.values().filter { it != ServerType.CUSTOM_JAR && it != ServerType.MODPACK }.forEach { type ->
                        val isSelected = selectedType == type
                        val isLockedByModpack = selectedModpackId != null
                        OutlinedCard(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .alpha(if (isLockedByModpack) 0.5f else 1f)
                                .clickable(enabled = !isLockedByModpack) { viewModel.setServerType(type) },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isSelected) PocketColors.PrimaryMuted else MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) PocketColors.Primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = PocketColors.PrimaryDark
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = type.displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) PocketColors.PrimaryDark else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                var showModpackSoonDialog by remember { mutableStateOf(false) }

                val isModpackSelected = selectedType == ServerType.MODPACK
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .alpha(0.6f)
                        .clickable { showModpackSoonDialog = true },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔒 ",
                            fontSize = 16.sp
                        )
                        Text(
                            text = ServerType.MODPACK.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = PocketColors.Warning.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "SOON",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PocketColors.Warning
                            )
                        }
                    }
                }

                if (showModpackSoonDialog) {
                    AlertDialog(
                        onDismissRequest = { showModpackSoonDialog = false },
                        title = { Text("Coming Soon! 🛠️", fontWeight = FontWeight.Bold) },
                        text = {
                            Text("Modpacks (RLCraft, Better MC, SkyFactory, etc.) are being optimized for mobile and will be available in the next major update!")
                        },
                        confirmButton = {
                            TextButton(onClick = { showModpackSoonDialog = false }) {
                                Text("AWESOME", fontWeight = FontWeight.Bold, color = PocketColors.Primary)
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                var showCustomJarSoonDialog by remember { mutableStateOf(false) }
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .alpha(0.6f)
                        .clickable { showCustomJarSoonDialog = true },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔒 ",
                            fontSize = 16.sp
                        )
                        Text(
                            text = ServerType.CUSTOM_JAR.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = PocketColors.Warning.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "SOON",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PocketColors.Warning
                            )
                        }
                    }
                }

                if (showCustomJarSoonDialog) {
                    AlertDialog(
                        onDismissRequest = { showCustomJarSoonDialog = false },
                        title = { Text("Coming Soon! 🛠️", fontWeight = FontWeight.Bold) },
                        text = {
                            Text("Uploading custom server JAR files will be available in a future update!")
                        },
                        confirmButton = {
                            TextButton(onClick = { showCustomJarSoonDialog = false }) {
                                Text("AWESOME", fontWeight = FontWeight.Bold, color = PocketColors.Primary)
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedType.supportsVersionSelect) {
                    Text(
                        text = "Game Version",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PocketColors.PrimaryDark,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (isLoading) {
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
                                    color = PocketColors.PrimaryDark,
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
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) PocketColors.Primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .background(
                                                if (isSelected) PocketColors.PrimaryMuted.copy(alpha = 0.48f) else MaterialTheme.colorScheme.surface,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable { viewModel.setSelectedVersion(version) }
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
                                            tint = if (isSelected) PocketColors.PrimaryDark else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = version,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) PocketColors.PrimaryDark else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isDownloaded) {
                                                Text(
                                                    text = "Downloaded",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = PocketColors.PrimaryDark
                                                )
                                            }
                                        }
                                        if (isDownloaded) {
                                            IconButton(onClick = { versionToDelete = version }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete downloaded version",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
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
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) PocketColors.Primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .background(
                                                if (isSelected) PocketColors.PrimaryMuted.copy(alpha = 0.48f) else MaterialTheme.colorScheme.surface,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable { viewModel.setSelectedVersion(version) }
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
                                        tint = if (isSelected) PocketColors.PrimaryDark else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = version,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) PocketColors.PrimaryDark else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isDownloaded) {
                                            Text(
                                                text = "Downloaded",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PocketColors.PrimaryDark
                                            )
                                        }
                                    }
                                    if (isDownloaded) {
                                        IconButton(onClick = { versionToDelete = version }) {
                                                Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete downloaded version",
                                                tint = MaterialTheme.colorScheme.error
                                            )
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
                        color = PocketColors.PrimaryDark,
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
                                items(modpackResults.take(12), key = { "${it.source}:${it.id}" }) { modpack ->
                                    val isSelected = selectedModpackId == modpack.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) PocketColors.Primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .background(
                                                if (isSelected) PocketColors.PrimaryMuted.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable(enabled = modpack.installSupported) { 
                                                selectedModpackId = modpack.id
                                                viewModel.setServerType(ServerType.MODPACK) 
                                            }
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
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = modpack.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${modpack.source.name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} • ${modpack.downloads} downloads",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (!modpack.supportMessage.isNullOrBlank()) {
                                                Text(
                                                    text = modpack.supportMessage,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (modpack.installSupported) PocketColors.PrimaryDark else MaterialTheme.colorScheme.error,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = PocketColors.PrimaryDark,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedType == ServerType.CUSTOM_JAR) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Custom JAR Upload",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PocketColors.PrimaryDark,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedCard(
                        onClick = {
                            filePickerLauncher.launch(arrayOf("application/java-archive", "application/zip", "application/octet-stream"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = PocketColors.PrimaryMuted.copy(alpha = 0.6f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 2.dp,
                            color = PocketColors.Primary
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Select JAR")
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = if (customJarPath != null) "JAR Selected" else "Tap to upload custom server JAR",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Used only for Custom JAR server type.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val isConfirmEnabled = if (selectedType.supportsVersionSelect) {
                    selectedVersion != null
                } else if (selectedType == ServerType.MODPACK) {
                    selectedModpackId != null
                } else {
                    customJarPath != null
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
                        onClick = {
                            val payloadPath = if (selectedType == ServerType.MODPACK) selectedModpackId else customJarPath
                            onConfirm(selectedType, selectedVersion, payloadPath)
                        },
                        enabled = isConfirmEnabled,
                        modifier = Modifier
                            .weight(1f),
                    )
                }
            }
        }
    }

    if (versionToDelete != null) {
        AlertDialog(
            onDismissRequest = { versionToDelete = null },
            title = { Text("Delete Version", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete the downloaded files for $versionToDelete? This will free up storage, but you will need to re-download it to use it.") },
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
}
