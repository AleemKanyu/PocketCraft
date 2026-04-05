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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.WorldImporter
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.service.VersionCatalog
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.components.GameCard
import com.pocketcraft.server.ui.components.ServerDescriptionField
import com.pocketcraft.server.ui.components.ServerPhotoUpload
import com.pocketcraft.server.ui.components.duoOutlinedTextFieldColors
import com.pocketcraft.server.ui.components.duoTextFieldShape
import com.pocketcraft.server.ui.theme.PocketColors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
    val activeWorld = stateHolder.config.worldName.ifBlank { "world" }

    var worldNameInput by remember(createMode) { mutableStateOf("") }
    var serverName by remember(stateHolder.serverName, activeWorld) {
        mutableStateOf(stateHolder.serverName.ifBlank { activeWorld })
    }
    var serverDescription by remember(stateHolder.serverDescription) {
        mutableStateOf(stateHolder.serverDescription)
    }
    var serverPhotoUri by remember(stateHolder.serverPhotoUrl) {
        mutableStateOf(if (stateHolder.serverPhotoUrl.isNotBlank()) Uri.parse(stateHolder.serverPhotoUrl) else null)
    }
    var photoChanged by remember(stateHolder.serverPhotoUrl) { mutableStateOf(false) }
    var worldSeed by remember(stateHolder.config.worldSeed) { mutableStateOf(stateHolder.config.worldSeed) }

    var renderDistanceValue by remember(stateHolder.config.viewDistance) {
        mutableStateOf(stateHolder.config.viewDistance.coerceIn(2, 32).toFloat())
    }
    var maxPlayersValue by remember(stateHolder.config.maxPlayers) {
        mutableStateOf(stateHolder.config.maxPlayers.coerceIn(1, 10).toFloat())
    }

    var selectedVersion by remember(stateHolder.versionLabel) { mutableStateOf(stateHolder.versionLabel) }
    var showVersionDialog by remember { mutableStateOf(false) }
    var loadingVersions by remember { mutableStateOf(false) }
    var availableVersions by remember { mutableStateOf(listOf(stateHolder.versionLabel)) }
    val versionFieldInteractionSource = remember { MutableInteractionSource() }

    var pendingImportSlot by remember { mutableStateOf(WorldImportSlot.MAIN) }
    var mainWorldZipUri by remember { mutableStateOf<Uri?>(null) }
    var netherZipUri by remember { mutableStateOf<Uri?>(null) }
    var endZipUri by remember { mutableStateOf<Uri?>(null) }
    var mainWorldZipName by remember { mutableStateOf("") }
    var netherZipName by remember { mutableStateOf("") }
    var endZipName by remember { mutableStateOf("") }

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

    LaunchedEffect(showVersionDialog) {
        if (!showVersionDialog) return@LaunchedEffect
        loadingVersions = true
        val fetched = runCatching { VersionCatalog.fetchStableVersions() }.getOrDefault(emptyList())
        availableVersions = (if (fetched.isNotEmpty()) fetched else listOf(stateHolder.versionLabel)).distinct()
        loadingVersions = false
    }

    LaunchedEffect(versionFieldInteractionSource) {
        versionFieldInteractionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                showVersionDialog = true
            }
        }
    }

    suspend fun finishSetup() {
        val versionId = selectedVersion.trim()
        val trimmedServerName = serverName.trim()
        val trimmedDescription = serverDescription.trim()
        if (trimmedServerName.isBlank()) {
            onMessage("Server name is required.")
            return
        }
        if (versionId.isBlank()) {
            onMessage("Game version is required.")
            return
        }

        var targetWorld = activeWorld
        if (createMode) {
            val requestedWorld = worldNameInput.trim()
            if (requestedWorld.isBlank()) {
                onMessage("World name is required.")
                return
            }
            val createMsg = stateHolder.createWorld(requestedWorld)
            val createdWorld = parseCreatedWorldName(createMsg)
            if (createdWorld == null) {
                onMessage(createMsg)
                return
            }
            val switchMsg = stateHolder.setActiveWorld(createdWorld)
            if (!switchMsg.startsWith("Active world switched")) {
                onMessage(switchMsg)
                return
            }
            targetWorld = createdWorld
        }

        if (mainWorldZipUri != null) {
            val importResult = WorldImporter.importWorld(
                context = context,
                zipUri = mainWorldZipUri!!,
                serverVersion = versionId,
                folderName = targetWorld
            )
            if (importResult.isFailure) {
                onMessage("Main world import failed: ${importResult.exceptionOrNull()?.message ?: "unknown error"}")
                return
            }
        }

        if (netherZipUri != null) {
            val importResult = WorldImporter.importWorld(
                context = context,
                zipUri = netherZipUri!!,
                serverVersion = versionId,
                folderName = "${targetWorld}_nether"
            )
            if (importResult.isFailure) {
                onMessage("Nether import failed: ${importResult.exceptionOrNull()?.message ?: "unknown error"}")
                return
            }
        }

        if (endZipUri != null) {
            val importResult = WorldImporter.importWorld(
                context = context,
                zipUri = endZipUri!!,
                serverVersion = versionId,
                folderName = "${targetWorld}_the_end"
            )
            if (importResult.isFailure) {
                onMessage("End import failed: ${importResult.exceptionOrNull()?.message ?: "unknown error"}")
                return
            }
        }

        val trimmedSeed = worldSeed.trim()
        val updatedConfig = stateHolder.config.copy(
            worldName = targetWorld,
            worldSeed = trimmedSeed,
            maxPlayers = maxPlayersValue.roundToInt(),
            viewDistance = renderDistanceValue.roundToInt()
        )
        stateHolder.saveSettings(updatedConfig)

        val existingPhoto = stateHolder.serverPhotoUrl.trim()
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

        AppPreferencesStore.setSelectedVersion(context, versionId)
        AppPreferencesStore.setWorldSeed(context, trimmedSeed)
        AppPreferencesStore.setSeedSetupShown(context, true)
        AppPreferencesStore.setInitialWorldSetupShown(context, true)
        stateHolder.markActiveWorldSetupCompleted()

        if (versionId != stateHolder.versionLabel) {
            onVersionSelected(versionId)
        }

        onMessage("Setup saved.")
        onComplete()
    }

    val canFinish = if (createMode) {
        serverName.isNotBlank() && selectedVersion.isNotBlank() && worldNameInput.isNotBlank()
    } else {
        serverName.isNotBlank() && selectedVersion.isNotBlank()
    }

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
                if (createMode) {
                    OutlinedTextField(
                        value = worldNameInput,
                        onValueChange = { worldNameInput = it },
                        singleLine = true,
                        label = { Text("World name (required)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = duoTextFieldShape(),
                        colors = duoOutlinedTextFieldColors()
                    )
                }

                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    singleLine = true,
                    label = { Text("Server name (required)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = duoTextFieldShape(),
                    colors = duoOutlinedTextFieldColors()
                )

                val hasSelectedVersion = selectedVersion.isNotBlank()
                OutlinedTextField(
                    value = if (hasSelectedVersion) selectedVersion else "Select game version",
                    onValueChange = {},
                    singleLine = true,
                    readOnly = true,
                    enabled = true,
                    interactionSource = versionFieldInteractionSource,
                    label = { Text("Game version (required)") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Dns,
                            contentDescription = null,
                            tint = PocketColors.PrimaryDark
                        )
                    },
                    colors = duoOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = duoTextFieldShape()
                )

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
                    Text("Render distance: ${renderDistanceValue.roundToInt()}", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = renderDistanceValue,
                        onValueChange = { renderDistanceValue = it },
                        valueRange = 2f..32f,
                        steps = 29
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Max players: ${maxPlayersValue.roundToInt()}", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = maxPlayersValue,
                        onValueChange = { maxPlayersValue = it },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                }

                SurfaceInfoText()

                OutlinedButton(
                    onClick = {
                        pendingImportSlot = WorldImportSlot.MAIN
                        importLauncher.launch("application/zip")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
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
                    shape = RoundedCornerShape(14.dp)
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
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(if (endZipName.isBlank()) "Upload The End ZIP (${activeWorld}_the_end)" else "End ZIP: $endZipName")
                }

                ServerDescriptionField(
                    description = serverDescription,
                    onDescriptionChange = { serverDescription = it },
                    modifier = Modifier.fillMaxWidth()
                )

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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 10.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, PocketColors.BorderLight)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(PocketColors.PrimaryMuted, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Dns, contentDescription = null, tint = PocketColors.PrimaryDark)
                        }
                        Column {
                            Text("Choose Minecraft Version", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text(
                                "Tap a version to use it for this server.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (loadingVersions) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableVersions.forEach { version ->
                                val selected = version == selectedVersion
                                Surface(
                                    onClick = {
                                        scope.launch {
                                            selectedVersion = version
                                            versionSheetState.hide()
                                            showVersionDialog = false
                                        }
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (selected) PocketColors.PrimaryMuted else MaterialTheme.colorScheme.surfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selected) PocketColors.Primary else PocketColors.BorderLight
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            version,
                                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            color = if (selected) PocketColors.PrimaryDark else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (selected) {
                                            Text(
                                                "Selected",
                                                color = PocketColors.PrimaryDark,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    versionSheetState.hide()
                                    showVersionDialog = false
                                }
                            }
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SurfaceInfoText() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketColors.PrimaryMuted.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
            Text(
                text = "Accepted upload format: .zip only.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
