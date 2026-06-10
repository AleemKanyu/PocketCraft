package com.pocketcraft.server.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.WorldImporter
import com.pocketcraft.server.data.model.ServerType
import com.pocketcraft.server.server.ServerJarManager
import com.pocketcraft.server.service.ServerFileManager
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.components.DuoToggle
import com.pocketcraft.server.ui.components.GameCard
import com.pocketcraft.server.ui.components.ServerDescriptionField
import com.pocketcraft.server.ui.components.ServerPhotoUpload
import com.pocketcraft.server.ui.components.duoOutlinedTextFieldColors
import com.pocketcraft.server.ui.components.duoTextFieldShape
import com.pocketcraft.server.ui.theme.PocketColors
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class WorldImportSlot { MAIN, NETHER, END }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun WorldSetupScreen(
    stateHolder: ServerStateHolder,
    createMode: Boolean = false,
    onVersionSelected: (String) -> Unit = {},
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeWorld = stateHolder.activeWorld.ifBlank { "world" }

    var worldNameInput by remember(createMode) { mutableStateOf("") }
    var serverName by remember(stateHolder.serverName, activeWorld, createMode) {
        mutableStateOf(if (createMode) "" else stateHolder.serverName.ifBlank { activeWorld })
    }
    var serverDescription by remember(stateHolder.serverDescription, createMode) {
        mutableStateOf(if (createMode) "" else stateHolder.serverDescription)
    }
    var serverPhotoUri by remember(stateHolder.serverPhotoUrl) {
        mutableStateOf(if (stateHolder.serverPhotoUrl.isNotBlank()) Uri.parse(stateHolder.serverPhotoUrl) else null)
    }
    var photoChanged by remember(stateHolder.serverPhotoUrl) { mutableStateOf(false) }
    var worldSeed by remember(stateHolder.config.worldSeed) { mutableStateOf(stateHolder.config.worldSeed) }
    var maxPlayersValue by remember(stateHolder.config.maxPlayers) { mutableStateOf(stateHolder.config.maxPlayers.toFloat()) }
    var selectedVersion by remember(stateHolder.config.gameVersion) {
        mutableStateOf(if (createMode) "" else stateHolder.config.gameVersion)
    }
    var selectedServerType by remember(stateHolder.config.serverType) {
        mutableStateOf(stateHolder.config.serverType)
    }
    var selectedCustomJarPath by remember(stateHolder.config.customJarPath) {
        mutableStateOf(stateHolder.config.customJarPath)
    }
    var showVersionDialog by remember { mutableStateOf(false) }
    var showServerNameError by remember { mutableStateOf(false) }
    var showVersionError by remember { mutableStateOf(false) }
    val versionFieldInteractionSource = remember { MutableInteractionSource() }

    var pendingImportSlot by remember { mutableStateOf(WorldImportSlot.MAIN) }
    var mainWorldZipUri by remember { mutableStateOf<Uri?>(null) }
    var netherZipUri by remember { mutableStateOf<Uri?>(null) }
    var endZipUri by remember { mutableStateOf<Uri?>(null) }
    var mainWorldZipName by remember { mutableStateOf("") }
    var netherZipName by remember { mutableStateOf("") }
    var endZipName by remember { mutableStateOf("") }

    var importProgress by remember { mutableStateOf(0f) }
    var isImporting by remember { mutableStateOf(false) }
    var versionDownloadProgress by remember { mutableStateOf(0) }
    var isDownloadingVersion by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "world_backup.zip"
            when (pendingImportSlot) {
                WorldImportSlot.MAIN -> {
                    mainWorldZipUri = uri
                    mainWorldZipName = fileName
                }
                WorldImportSlot.NETHER -> {
                    netherZipUri = uri
                    netherZipName = fileName
                }
                WorldImportSlot.END -> {
                    endZipUri = uri
                    endZipName = fileName
                }
            }
        }
    }

    LaunchedEffect(versionFieldInteractionSource) {
        versionFieldInteractionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                showVersionDialog = true
            }
        }
    }

    suspend fun finishSetup() {
        val selectedModpackId = selectedCustomJarPath?.trim().orEmpty()
        val versionId = when {
            selectedServerType == ServerType.MODPACK -> selectedModpackId
            selectedServerType.supportsVersionSelect -> selectedVersion.trim()
            else -> stateHolder.config.gameVersion.ifBlank { selectedVersion.trim() }
        }
        val trimmedServerName = serverName.trim()
        val trimmedDescription = if (serverDescription.trim().isBlank()) "Hosted on Pocketcraft" else serverDescription.trim()

        val isNameBlank = trimmedServerName.isBlank()
        val isVersionBlank = when {
            selectedServerType == ServerType.MODPACK -> selectedModpackId.isBlank()
            selectedServerType.supportsVersionSelect -> versionId.isBlank()
            else -> selectedCustomJarPath.isNullOrBlank()
        }

        showServerNameError = isNameBlank
        showVersionError = isVersionBlank

        if (isNameBlank || isVersionBlank) {
            onMessage("Please fill all mandatory fields.")
            return
        }

        var targetWorld = activeWorld
        if (createMode) {
            val requestedWorld = trimmedServerName.ifBlank { "world" }
            val createMsg = stateHolder.createWorld(requestedWorld)
            val createdWorld = parseCreatedWorldName(createMsg)
            if (createdWorld == null) {
                onMessage(createMsg)
                return
            }
            val switchMsg = stateHolder.setActiveWorld(createdWorld, syncPluginProfiles = false)
            if (!switchMsg.startsWith("Active world switched")) {
                onMessage(switchMsg)
                return
            }
            targetWorld = createdWorld
        }

        if (mainWorldZipUri != null) {
            isImporting = true
            val importResult = WorldImporter.importWorld(
                context = context,
                zipUri = mainWorldZipUri!!,
                serverType = selectedServerType,
                serverVersionId = stateHolder.versionLabel,
                folderName = targetWorld,
                onProgress = { importProgress = it }
            )
            isImporting = false
            if (importResult.isFailure) {
                onMessage("Main world import failed: ${importResult.exceptionOrNull()?.message ?: "unknown error"}")
                return
            }
        }

        if (netherZipUri != null) {
            isImporting = true
            val importResult = WorldImporter.importWorld(
                context = context,
                zipUri = netherZipUri!!,
                serverType = selectedServerType,
                serverVersionId = stateHolder.versionLabel,
                folderName = targetWorld, // WorldImporter handles internal mapping to DIM-1 if needed
                onProgress = { importProgress = it }
            )
            isImporting = false
            if (importResult.isFailure) {
                onMessage("Nether import failed: ${importResult.exceptionOrNull()?.message ?: "unknown error"}")
                return
            }
        }

        if (endZipUri != null) {
            isImporting = true
            val importResult = WorldImporter.importWorld(
                context = context,
                zipUri = endZipUri!!,
                serverType = selectedServerType,
                serverVersionId = stateHolder.versionLabel,
                folderName = targetWorld, // WorldImporter handles internal mapping to DIM1 if needed
                onProgress = { importProgress = it }
            )
            isImporting = false
            if (importResult.isFailure) {
                onMessage("End import failed: ${importResult.exceptionOrNull()?.message ?: "unknown error"}")
                return
            }
        }

        val trimmedSeed = worldSeed.trim()
        val updatedConfig = stateHolder.config.copy(
            worldName = targetWorld,
            worldSeed = trimmedSeed,
            gameVersion = versionId,
            serverType = selectedServerType,
            customJarPath = selectedCustomJarPath,
            maxPlayers = maxPlayersValue.roundToInt(),
            viewDistance = 6
        )
        stateHolder.saveSettings(updatedConfig, targetWorldName = targetWorld)

        if (updatedConfig.serverType == ServerType.MODPACK) {
            onComplete()
            return
        } else if (updatedConfig.serverType.supportsVersionSelect) {
            isDownloadingVersion = true
            versionDownloadProgress = 0
            val importCheckResult = runCatching {
                withContext(Dispatchers.IO) {
                    val targetJar = ServerFileManager.getServerJarFile(
                        context = context,
                        gameVersion = versionId,
                        serverType = updatedConfig.serverType
                    )
                    ServerJarManager.resolveJar(
                        serverType = updatedConfig.serverType,
                        gameVersion = versionId,
                        customJarPath = null,
                        targetFile = targetJar,
                        onProgress = { percent ->
                            scope.launch {
                                versionDownloadProgress = percent.coerceIn(0, 100)
                            }
                        }
                    ).collect { }
                }
            }
            isDownloadingVersion = false
            if (importCheckResult.isFailure) {
                val error = importCheckResult.exceptionOrNull()
                onMessage("Server JAR import required: ${error?.message ?: "unknown error"}")
                return
            }
        }

        val existingPhoto = if (createMode) "" else stateHolder.serverPhotoUrl.trim()
        val photoUrlToSave = when {
            photoChanged && serverPhotoUri != null ->
                stateHolder.importWorldServerPhoto(
                    worldName = targetWorld,
                    sourceUri = serverPhotoUri!!
                )
            photoChanged -> ""
            else -> existingPhoto
        }

        stateHolder.updateWorldServerDetails(
            worldName = targetWorld,
            displayName = trimmedServerName,
            photoUrl = photoUrlToSave,
            description = trimmedDescription
        )

        AppPreferencesStore.setSelectedServerType(context, selectedServerType.name)
        AppPreferencesStore.setSelectedVersion(context, versionId)
        AppPreferencesStore.setWorldSeed(context, trimmedSeed)
        AppPreferencesStore.setSeedSetupShown(context, true)
        AppPreferencesStore.setInitialWorldSetupShown(context, true)
        stateHolder.markActiveWorldSetupCompleted()

        if (selectedServerType == ServerType.MODPACK ||
            (selectedServerType.supportsVersionSelect && versionId != stateHolder.config.gameVersion)
        ) {
            onVersionSelected(versionId)
        }

        onMessage("Setup saved.")
        onComplete()
    }

    val canFinish = !isDownloadingVersion

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        PocketColors.PrimaryMuted.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(16.dp)
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = if (createMode) "Create World Setup" else "Server Setup",
                style = MaterialTheme.typography.titleLarge
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PocketColors.Primary.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .border(2.dp, PocketColors.Primary.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = PocketColors.PrimaryDark
                    )
                    Text(
                        text = "Minimal setup: name + version are required",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (createMode) "Creating new world" else "World: $activeWorld",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        GameCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = serverName,
                    onValueChange = { 
                        serverName = it
                        if (it.isNotBlank()) showServerNameError = false
                    },
                    singleLine = true,
                    isError = showServerNameError,
                    label = { Text("Server name (required)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = duoTextFieldShape(),
                    colors = duoOutlinedTextFieldColors()
                )
                if (showServerNameError) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Server name is required",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (!createMode) {
                    ServerDescriptionField(
                        description = serverDescription,
                        onDescriptionChange = { serverDescription = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val hasSelectedVersion = selectedVersion.isNotBlank()
                val selectedRuntimeLabel = when {
                    selectedServerType.supportsVersionSelect ->
                        if (hasSelectedVersion) "${selectedServerType.displayName} $selectedVersion" else ""
                    selectedServerType == ServerType.MODPACK ->
                        selectedCustomJarPath
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "${selectedServerType.displayName} $it" }
                            ?: ""
                    else -> "${selectedServerType.displayName} (Custom JAR)"
                }
                OutlinedTextField(
                    value = selectedRuntimeLabel,
                    onValueChange = {},
                    singleLine = true,
                    readOnly = true,
                    enabled = true,
                    isError = showVersionError,
                    interactionSource = versionFieldInteractionSource,
                    label = { Text("Game version (required)") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Dns,
                            contentDescription = null,
                            tint = if (showVersionError) MaterialTheme.colorScheme.error else PocketColors.PrimaryDark
                        )
                    },
                    colors = duoOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = duoTextFieldShape()
                )
                if (showVersionError) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Game version or custom JAR is required",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = worldSeed,
                    onValueChange = { worldSeed = it },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Forest,
                            contentDescription = null,
                            tint = PocketColors.PrimaryDark
                        )
                    },
                    label = { Text("World seed (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = duoTextFieldShape(),
                    colors = duoOutlinedTextFieldColors()
                )


                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Max players: ${maxPlayersValue.roundToInt()}", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = maxPlayersValue,
                        onValueChange = { maxPlayersValue = it },
                        valueRange = 1f..20f,
                        steps = 18
                    )
                }


                SurfaceInfoText()

                if (isImporting) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { importProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = PocketColors.Primary,
                            trackColor = PocketColors.PrimaryMuted
                        )
                        Text(
                            text = "Importing world data... ${(importProgress * 100).roundToInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PocketColors.PrimaryDark
                        )
                    }
                }

                if (isDownloadingVersion) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { (versionDownloadProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = PocketColors.Primary,
                            trackColor = PocketColors.PrimaryMuted
                        )
                        Text(
                            text = "Downloading selected server version... $versionDownloadProgress%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PocketColors.PrimaryDark
                        )
                    }
                }

                val uploadButtonColors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )

                OutlinedButton(
                    onClick = {
                        pendingImportSlot = WorldImportSlot.MAIN
                        importLauncher.launch("application/zip")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = uploadButtonColors,
                    enabled = !isImporting
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(if (mainWorldZipName.isBlank()) "Upload Main World ZIP (all backups)" else "Main ZIP: $mainWorldZipName")
                }

                OutlinedButton(
                    onClick = {
                        pendingImportSlot = WorldImportSlot.NETHER
                        importLauncher.launch("application/zip")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = uploadButtonColors,
                    enabled = !isImporting
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(if (netherZipName.isBlank()) "Upload Nether ZIP (${activeWorld}_nether)" else "Nether ZIP: $netherZipName")
                }

                OutlinedButton(
                    onClick = {
                        pendingImportSlot = WorldImportSlot.END
                        importLauncher.launch("application/zip")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = uploadButtonColors,
                    enabled = !isImporting
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(if (endZipName.isBlank()) "Upload The End ZIP (${activeWorld}_the_end)" else "End ZIP: $endZipName")
                }

                ServerPhotoUpload(
                    photoUri = serverPhotoUri,
                    onPhotoSelected = { uri ->
                        serverPhotoUri = uri
                        photoChanged = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                DuoButton(
                    text = if (createMode) "CREATE WORLD" else "FINISH SETUP",
                    onClick = {
                        scope.launch {
                            finishSetup()
                        }
                    },
                    enabled = canFinish,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding().height(80.dp))
    }

    if (showVersionDialog) {
        val versionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    versionSheetState.hide()
                    showVersionDialog = false
                }
            },
            sheetState = versionSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            ServerTypeVersionBottomSheet(
                onDismissRequest = {
                    scope.launch {
                        versionSheetState.hide()
                        showVersionDialog = false
                    }
                },
                onConfirm = { type, version, customJar ->
                    scope.launch {
                        selectedServerType = type
                        selectedCustomJarPath = customJar
                        selectedVersion = when {
                            type == ServerType.MODPACK -> customJar.orEmpty()
                            type.supportsVersionSelect -> version ?: selectedVersion
                            else -> stateHolder.config.gameVersion.ifBlank { selectedVersion }
                        }
                        val valid = when {
                            type == ServerType.MODPACK -> !customJar.isNullOrBlank()
                            type.supportsVersionSelect -> (version ?: selectedVersion).isNotBlank()
                            else -> !customJar.isNullOrBlank()
                        }
                        if (valid) {
                            showVersionError = false
                        }
                        versionSheetState.hide()
                        showVersionDialog = false
                    }
                },
                currentServerType = selectedServerType,
                currentGameVersion = selectedVersion,
                currentCustomJarPath = selectedCustomJarPath
            )
        }
    }
}

@Composable
private fun SurfaceInfoText() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .border(1.dp, PocketColors.Primary.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Aternos import",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = PocketColors.PrimaryDark
            )
            Text(
                text = "If your backup is from Aternos, upload all 3 dimensions: Main World, Nether, and The End.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 15.sp
            )
            Text(
                text = "Accepted upload format: .zip only.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Other providers",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = PocketColors.PrimaryDark
            )
            Text(
                text = "If your backup is from another site, usually upload only Main World.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 15.sp
            )
        }
    }
}

private fun parseCreatedWorldName(message: String): String? {
    val directPrefix = "World added: "
    val renamedPrefix = "World added as "

    return when {
        message.startsWith(directPrefix) -> {
            message.removePrefix(directPrefix).removeSuffix(".").trim().ifBlank { null }
        }
        message.startsWith(renamedPrefix) -> {
            message.removePrefix(renamedPrefix).substringBefore(" because").trim().ifBlank { null }
        }
        else -> null
    }
}
