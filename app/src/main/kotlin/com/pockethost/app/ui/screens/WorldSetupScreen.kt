package com.pockethost.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.WorldImporter
import com.pockethost.app.billing.BillingManager
import com.pockethost.app.config.RemoteConfigManager
import com.pockethost.app.data.model.ServerType
import com.pockethost.app.data.preferences.AppPreferences
import com.pockethost.app.data.preferences.AppPreferencesStore
import com.pockethost.app.server.ServerJarManager
import com.pockethost.app.service.ServerFileManager
import com.pockethost.app.ui.components.DuoButton
import com.pockethost.app.ui.components.GameCard
import com.pockethost.app.ui.components.IpManagerCard
import com.pockethost.app.ui.components.ServerDescriptionField
import com.pockethost.app.ui.components.ServerPhotoUpload
import com.pockethost.app.ui.components.duoOutlinedTextFieldColors
import com.pockethost.app.ui.components.duoTextFieldShape
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.raisedBorder
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
    onComplete: () -> Unit,
    onNavigateToSignUp: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeWorld = stateHolder.activeWorld.ifBlank { "world" }

    var serverName by remember(stateHolder.serverName, activeWorld, createMode) {
        mutableStateOf(
            if (createMode) {
                stateHolder.serverName.ifBlank { "My Minecraft Server" }
            } else {
                stateHolder.serverName.ifBlank { activeWorld }
            }
        )
    }
    var serverDescription by remember(stateHolder.serverDescription, createMode) {
        mutableStateOf(if (createMode) ServerStateHolder.DEFAULT_SERVER_DESCRIPTION else stateHolder.serverDescription)
    }
    var serverPhotoUri by remember(stateHolder.serverPhotoUrl) {
        mutableStateOf(if (stateHolder.serverPhotoUrl.isNotBlank()) Uri.parse(stateHolder.serverPhotoUrl) else null)
    }
    var photoChanged by remember(stateHolder.serverPhotoUrl) { mutableStateOf(false) }
    var worldSeed by remember(stateHolder.config.worldSeed) { mutableStateOf(stateHolder.config.worldSeed) }
    
    val isPremium = remember {
        val prefs = AppPreferences(context)
        prefs.isPremiumUser || prefs.debugPremiumOverride
    }
    val customSubdomainEnabled by RemoteConfigManager.customSubdomainEnabled.collectAsState(initial = false)
    val billingManager = remember { BillingManager.getInstance(context) }
    val entitlement by billingManager.entitlement.collectAsState()

    var maxPlayersValue by remember(stateHolder.config.maxPlayers) {
        val initialVal = stateHolder.config.maxPlayers.toFloat()
        mutableStateOf(if (!isPremium) initialVal.coerceAtMost(10f) else initialVal)
    }
    var showPremiumBottomSheet by remember { mutableStateOf(false) }

    var selectedVersion by remember(stateHolder.config.gameVersion) {
        mutableStateOf(
            if (createMode) stateHolder.config.gameVersion.ifBlank { "1.21.4" }
            else stateHolder.config.gameVersion.ifBlank { "1.21.4" }
        )
    }
    var selectedServerType by remember(stateHolder.config.serverType) {
        mutableStateOf(stateHolder.config.serverType)
    }
    var selectedCustomJarPath by remember(stateHolder.config.customJarPath) {
        mutableStateOf(stateHolder.config.customJarPath)
    }
    var joinMessageText by remember(stateHolder.config.joinMessageText) {
        mutableStateOf(stateHolder.config.joinMessageText)
    }
    var joinMessageUrl by remember(stateHolder.config.joinMessageUrl) {
        mutableStateOf(stateHolder.config.joinMessageUrl)
    }
    
    var showVersionDialog by remember { mutableStateOf(false) }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

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
                WorldImportSlot.MAIN -> { mainWorldZipUri = uri; mainWorldZipName = fileName }
                WorldImportSlot.NETHER -> { netherZipUri = uri; netherZipName = fileName }
                WorldImportSlot.END -> { endZipUri = uri; endZipName = fileName }
            }
        }
    }

    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val isFormValid = serverName.trim().isNotBlank() && when {
        selectedServerType == ServerType.MODPACK -> !selectedCustomJarPath.isNullOrBlank()
        selectedServerType.supportsVersionSelect -> selectedVersion.isNotBlank()
        else -> true
    }

    suspend fun finishSetup() {
        val selectedModpackId = selectedCustomJarPath?.trim().orEmpty()
        val versionId = when {
            selectedServerType == ServerType.MODPACK -> selectedModpackId
            selectedServerType.supportsVersionSelect -> selectedVersion.trim()
            else -> stateHolder.config.gameVersion.ifBlank { selectedVersion.trim() }
        }
        val trimmedServerName = serverName.trim()
        val trimmedDescription = if (serverDescription.trim().isBlank()) ServerStateHolder.DEFAULT_SERVER_DESCRIPTION else serverDescription.trim()

        if (trimmedServerName.isBlank() || (selectedServerType.supportsVersionSelect && versionId.isBlank())) {
            onMessage("Please enter a valid server name and version.")
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
            val importResult = WorldImporter.importWorld(context, mainWorldZipUri!!, selectedServerType, stateHolder.versionLabel, targetWorld) { p, _ -> importProgress = p }
            isImporting = false
            if (importResult.isFailure) {
                onMessage("Main world import failed: ${importResult.exceptionOrNull()?.message ?: "error"}")
                return
            }
        }

        if (netherZipUri != null) {
            isImporting = true
            val importResult = WorldImporter.importWorld(context, netherZipUri!!, selectedServerType, stateHolder.versionLabel, targetWorld) { p, _ -> importProgress = p }
            isImporting = false
            if (importResult.isFailure) {
                onMessage("Nether import failed: ${importResult.exceptionOrNull()?.message ?: "error"}")
                return
            }
        }

        if (endZipUri != null) {
            isImporting = true
            val importResult = WorldImporter.importWorld(context, endZipUri!!, selectedServerType, stateHolder.versionLabel, targetWorld) { p, _ -> importProgress = p }
            isImporting = false
            if (importResult.isFailure) {
                onMessage("End import failed: ${importResult.exceptionOrNull()?.message ?: "error"}")
                return
            }
        }

        val updatedConfig = stateHolder.config.copy(
            worldName = targetWorld,
            worldSeed = worldSeed.trim(),
            gameVersion = versionId,
            serverType = selectedServerType,
            customJarPath = selectedCustomJarPath,
            maxPlayers = maxPlayersValue.roundToInt(),
            joinMessageText = if (isPremium) joinMessageText.trim() else stateHolder.config.joinMessageText,
            joinMessageUrl = if (isPremium) joinMessageUrl.trim() else stateHolder.config.joinMessageUrl
        )
        stateHolder.saveSettings(updatedConfig, targetWorldName = targetWorld)

        if (selectedServerType.supportsVersionSelect && versionId != stateHolder.config.gameVersion) {
            isDownloadingVersion = true
            val targetJar = ServerFileManager.getServerJarFile(context, versionId, updatedConfig.serverType)
            ServerJarManager.resolveJar(updatedConfig.serverType, versionId, null, targetJar) { versionDownloadProgress = it.coerceIn(0, 100) }.collect { }
            isDownloadingVersion = false
        }

        val photoUrlToSave = if (photoChanged && serverPhotoUri != null) stateHolder.importWorldServerPhoto(targetWorld, serverPhotoUri!!) else stateHolder.serverPhotoUrl
        stateHolder.updateWorldServerDetails(targetWorld, trimmedServerName, photoUrlToSave, trimmedDescription)

        AppPreferencesStore.setSelectedServerType(context, selectedServerType.name)
        AppPreferencesStore.setSelectedVersion(context, versionId)
        stateHolder.markActiveWorldSetupCompleted()
        if (selectedServerType == ServerType.MODPACK || (selectedServerType.supportsVersionSelect && versionId != stateHolder.config.gameVersion)) {
            onVersionSelected(versionId)
        }

        onMessage(if (createMode) "Server created successfully!" else "Settings saved.")
        onComplete()
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Text(
                        text = if (createMode) "Create Server" else "Server Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDarkTheme) PocketColors.PrimaryLight else PocketColors.PrimaryDark
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isDarkTheme) PocketColors.SurfaceCardDark else MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding()) {
                    DuoButton(
                        text = if (isSubmitting) (if (createMode) "CREATING SERVER…" else "SAVING CHANGES…") else (if (createMode) "CREATE SERVER" else "SAVE CHANGES"),
                        onClick = {
                            if (!isSubmitting && isFormValid) {
                                isSubmitting = true
                                scope.launch {
                                    runCatching { finishSetup() }.onFailure { onMessage(it.message ?: "An error occurred.") }
                                    isSubmitting = false
                                }
                            }
                        },
                        enabled = isFormValid && !isSubmitting && !isImporting && !isDownloadingVersion,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(MaterialTheme.colorScheme.background, PocketColors.PrimaryMuted.copy(alpha = 0.5f), MaterialTheme.colorScheme.background)))
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            GameCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = PocketColors.Primary, modifier = Modifier.size(20.dp))
                        Text(text = "Server Essentials", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isDarkTheme) PocketColors.PrimaryLight else PocketColors.PrimaryDark)
                    }

                    OutlinedTextField(
                        value = serverName, onValueChange = { serverName = it }, singleLine = true,
                        label = { Text("Server Name") }, placeholder = { Text("e.g. My Survival Server") },
                        modifier = Modifier.fillMaxWidth(), shape = duoTextFieldShape(), colors = duoOutlinedTextFieldColors()
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "Server Type", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ServerType.entries.chunked(3).forEach { rowTypes ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowTypes.forEach { type ->
                                    val isSelected = selectedServerType == type
                                    Box(
                                        modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) PocketColors.primaryBg else PocketColors.InactiveBg)
                                            .raisedBorder(color = if (isSelected) PocketColors.primaryBorder else PocketColors.InactiveBorder, depthColor = if (isSelected) PocketColors.primaryDepth else PocketColors.InactiveBorderBottom, cornerRadius = 12.dp, borderWidth = 1.5.dp, depthWidth = 2.5.dp)
                                            .clickable { selectedServerType = type }
                                    ) {
                                        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                            if (isSelected) { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp), tint = PocketColors.PrimaryText); Spacer(modifier = Modifier.width(3.dp)) }
                                            Text(text = type.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isSelected) PocketColors.PrimaryText else PocketColors.InactiveText)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val selectedRuntimeLabel = when {
                        selectedServerType.supportsVersionSelect -> if (selectedVersion.isNotBlank()) "${selectedServerType.displayName} $selectedVersion" else "Select Version"
                        selectedServerType == ServerType.MODPACK -> selectedCustomJarPath?.takeIf { it.isNotBlank() }?.let { "${selectedServerType.displayName} $it" } ?: "Choose Modpack"
                        else -> "${selectedServerType.displayName} (Custom JAR)"
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { showVersionDialog = true },
                        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), border = BorderStroke(1.dp, PocketColors.Primary.copy(alpha = 0.3f))
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Filled.Dns, contentDescription = null, tint = PocketColors.Primary)
                                Column {
                                    Text(text = "Game Version / Runtime", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = selectedRuntimeLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = PocketColors.Primary.copy(alpha = 0.15f)) {
                                Text(text = "Change", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PocketColors.PrimaryDark, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                        }
                    }
                }
            }

            val chevronRotation by animateFloatAsState(targetValue = if (showAdvancedOptions) 180f else 0f, label = "advancedChevron")
            GameCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().clickable { showAdvancedOptions = !showAdvancedOptions }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Text(text = "Advanced Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Icon(Icons.Filled.ExpandMore, contentDescription = if (showAdvancedOptions) "Collapse" else "Expand", modifier = Modifier.rotate(chevronRotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    AnimatedVisibility(visible = showAdvancedOptions, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ServerDescriptionField(description = serverDescription, onDescriptionChange = { serverDescription = it }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = worldSeed, onValueChange = { worldSeed = it }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Forest, contentDescription = null, tint = PocketColors.PrimaryDark) }, label = { Text("World Seed (Optional)") }, modifier = Modifier.fillMaxWidth(), shape = duoTextFieldShape(), colors = duoOutlinedTextFieldColors())
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Max Players", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(text = "${maxPlayersValue.roundToInt()} players", fontWeight = FontWeight.Bold, color = PocketColors.PrimaryDark)
                                }
                                Slider(value = maxPlayersValue, onValueChange = { newValue -> maxPlayersValue = newValue }, valueRange = 1f..50f, steps = 48)
                            }
                            IpManagerCard(entitlement, true, true, onMessage, onNavigateToSignUp)
                            ServerPhotoUpload(photoUri = serverPhotoUri, onPhotoSelected = { serverPhotoUri = it; photoChanged = true }, modifier = Modifier.fillMaxWidth())
                            
                            Text(text = "Import Existing World", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            OutlinedButton(onClick = { pendingImportSlot = WorldImportSlot.MAIN; importLauncher.launch("application/zip") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), enabled = !isImporting) { Icon(Icons.Filled.UploadFile, null); Spacer(Modifier.padding(horizontal = 4.dp)); Text(if (mainWorldZipName.isBlank()) "Upload Main World ZIP" else "Main ZIP: $mainWorldZipName") }
                            OutlinedButton(onClick = { pendingImportSlot = WorldImportSlot.NETHER; importLauncher.launch("application/zip") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), enabled = !isImporting) { Icon(Icons.Filled.UploadFile, null); Spacer(Modifier.padding(horizontal = 4.dp)); Text(if (netherZipName.isBlank()) "Upload Nether ZIP" else "Nether ZIP: $netherZipName") }
                            OutlinedButton(onClick = { pendingImportSlot = WorldImportSlot.END; importLauncher.launch("application/zip") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), enabled = !isImporting) { Icon(Icons.Filled.UploadFile, null); Spacer(Modifier.padding(horizontal = 4.dp)); Text(if (endZipName.isBlank()) "Upload The End ZIP" else "End ZIP: $endZipName") }
                            
                            Box(modifier = Modifier.fillMaxWidth().background(if (isPremium) PocketColors.Primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp)).border(1.dp, if (isPremium) PocketColors.Primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp)).padding(12.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Filled.Campaign, contentDescription = null, tint = if (isPremium) PocketColors.PrimaryDark else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(text = "In-Game Join Announcement", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Spacer(Modifier.weight(1f))
                                        if (!isPremium) {
                                            Surface(color = PocketColors.Primary.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp), modifier = Modifier.clickable { showPremiumBottomSheet = true }) {
                                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Filled.Lock, null, tint = PocketColors.PrimaryDark, modifier = Modifier.size(12.dp))
                                                    Text("PREMIUM", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = PocketColors.PrimaryDark)
                                                }
                                            }
                                        }
                                    }
                                    OutlinedTextField(value = joinMessageText, onValueChange = { if (isPremium) joinMessageText = it else showPremiumBottomSheet = true }, singleLine = true, readOnly = !isPremium, label = { Text("Announcement text") }, modifier = Modifier.fillMaxWidth(), shape = duoTextFieldShape(), colors = duoOutlinedTextFieldColors())
                                    OutlinedTextField(value = joinMessageUrl, onValueChange = { if (isPremium) joinMessageUrl = it else showPremiumBottomSheet = true }, singleLine = true, readOnly = !isPremium, label = { Text("Discord / Website URL") }, modifier = Modifier.fillMaxWidth(), shape = duoTextFieldShape(), colors = duoOutlinedTextFieldColors())
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showVersionDialog) {
        val versionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { scope.launch { versionSheetState.hide(); showVersionDialog = false } }, sheetState = versionSheetState, dragHandle = null, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            ServerTypeVersionBottomSheet(
                onDismissRequest = { scope.launch { versionSheetState.hide(); showVersionDialog = false } },
                onConfirm = { type, version, customJar ->
                    scope.launch {
                        selectedServerType = type
                        selectedCustomJarPath = customJar
                        selectedVersion = when {
                            type == ServerType.MODPACK -> customJar.orEmpty()
                            type.supportsVersionSelect -> version ?: selectedVersion
                            else -> stateHolder.config.gameVersion.ifBlank { selectedVersion }
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

    if (showPremiumBottomSheet) {
        com.pockethost.app.ui.components.PremiumUpgradeBottomSheet(onDismissRequest = { showPremiumBottomSheet = false }, onNavigateToSignUp = onNavigateToSignUp)
    }
}

private fun parseCreatedWorldName(message: String): String? {
    val directPrefix = "World added: "
    val renamedPrefix = "World added as "
    return when {
        message.startsWith(directPrefix) -> message.removePrefix(directPrefix).removeSuffix(".").trim().ifBlank { null }
        message.startsWith(renamedPrefix) -> message.removePrefix(renamedPrefix).substringBefore(" because").trim().ifBlank { null }
        else -> null
    }
}
