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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.ui.graphics.vector.ImageVector
import com.pockethost.app.ui.components.DuoButtonVariant
import com.pockethost.app.ui.components.DuoToggle
import com.pockethost.app.ui.theme.Monocraft
import com.pockethost.app.ui.tour.LocalTourController
import com.pockethost.app.ui.tour.PocketTours
import com.pockethost.app.ui.tour.TourAnchor
import com.pockethost.app.ui.tour.TourId
import com.pockethost.app.ui.tour.tourAnchor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import android.widget.Toast
import com.pockethost.app.server.ServerJarImporter
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
            if (createMode) ""
            else stateHolder.config.gameVersion.ifBlank { "1.21.4" }
        )
    }
    var selectedServerType by remember(stateHolder.config.serverType) {
        mutableStateOf(stateHolder.config.serverType)
    }
    var selectedCustomJarPath by remember(stateHolder.config.customJarPath) {
        mutableStateOf(stateHolder.config.customJarPath)
    }
    var selectedGameMode by remember(stateHolder.config.gameMode) {
        mutableStateOf(stateHolder.config.gameMode.ifBlank { "survival" }.lowercase())
    }
    var selectedDifficulty by remember(stateHolder.config.difficulty) {
        mutableStateOf(stateHolder.config.difficulty.ifBlank { "normal" }.lowercase())
    }
    var selectedLevelType by remember(stateHolder.config.levelType) {
        mutableStateOf(stateHolder.config.levelType.ifBlank { "minecraft:normal" })
    }
    var pvpEnabled by remember(stateHolder.config.pvp) {
        mutableStateOf(stateHolder.config.pvp)
    }
    var hardcoreEnabled by remember(stateHolder.config.hardcore) {
        mutableStateOf(stateHolder.config.hardcore)
    }
    var allowFlightEnabled by remember(stateHolder.config.allowFlight) {
        mutableStateOf(stateHolder.config.allowFlight)
    }
    var bedrockCrossplayEnabled by remember(stateHolder.bedrockBridgeEnabled) {
        mutableStateOf(stateHolder.bedrockBridgeEnabled)
    }

    val tour = LocalTourController.current
    val prefs = remember { AppPreferences(context) }

    LaunchedEffect(createMode) {
        if (createMode && !prefs.createServerTourShown) {
            kotlinx.coroutines.delay(500)
            tour?.start(TourId.CREATE_SERVER, PocketTours.createServer()) { _, _ ->
                prefs.createServerTourShown = true
            }
        }
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
            gameMode = selectedGameMode,
            difficulty = if (hardcoreEnabled) "hard" else selectedDifficulty,
            levelType = selectedLevelType,
            pvp = pvpEnabled,
            hardcore = hardcoreEnabled,
            allowFlight = allowFlightEnabled
        )
        stateHolder.saveSettings(updatedConfig, targetWorldName = targetWorld)

        if (selectedServerType != ServerType.VANILLA && selectedServerType != ServerType.MODPACK) {
            if (bedrockCrossplayEnabled != stateHolder.bedrockBridgeEnabled) {
                stateHolder.toggleBedrockBridge(bedrockCrossplayEnabled)
            }
        }

        val isJarDownloaded = if (selectedServerType == ServerType.MODPACK) {
            !selectedCustomJarPath.isNullOrBlank()
        } else if (selectedServerType.supportsVersionSelect) {
            ServerFileManager.isServerJarReady(context, versionId, selectedServerType)
        } else {
            true
        }

        if (!isJarDownloaded && selectedServerType.supportsVersionSelect) {
            onMessage("Please download and select the server file for ${selectedServerType.displayName} $versionId first.")
            showVersionDialog = true
            return
        }

        if (selectedServerType.supportsVersionSelect && versionId != stateHolder.config.gameVersion && isJarDownloaded) {
            isDownloadingVersion = true
            val targetJar = ServerFileManager.getServerJarFile(context, versionId, updatedConfig.serverType)
            runCatching {
                ServerJarManager.resolveJar(updatedConfig.serverType, versionId, null, targetJar) { versionDownloadProgress = it.coerceIn(0, 100) }.collect { }
            }
            isDownloadingVersion = false
        }

        val photoUrlToSave = if (photoChanged && serverPhotoUri != null) stateHolder.importWorldServerPhoto(targetWorld, serverPhotoUri!!) else stateHolder.serverPhotoUrl
        stateHolder.updateWorldServerDetails(targetWorld, trimmedServerName, photoUrlToSave, trimmedDescription)

        AppPreferencesStore.setSelectedServerType(context, selectedServerType.name)
        AppPreferencesStore.setSelectedVersion(context, versionId)
        AppPreferences(context).firstServerTourShown = true
        AppPreferencesStore.setSetupComplete(context, true)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = if (createMode) "Create Server" else "Server Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDarkTheme) PocketColors.PrimaryLight else PocketColors.PrimaryDark,
                        modifier = Modifier.weight(1f)
                    )
                    if (createMode) {
                        IconButton(
                            onClick = {
                                tour?.start(TourId.CREATE_SERVER, PocketTours.createServer())
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.HelpOutline,
                                contentDescription = "Creation Guide",
                                tint = PocketColors.Primary
                            )
                        }
                    }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .tourAnchor(TourAnchor.CREATE_SERVER_SUBMIT)
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
                        value = serverName,
                        onValueChange = { serverName = it },
                        singleLine = true,
                        label = { Text("Server Name") },
                        placeholder = { Text("e.g. My Survival Server") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .tourAnchor(TourAnchor.CREATE_SERVER_NAME),
                        shape = duoTextFieldShape(),
                        colors = duoOutlinedTextFieldColors()
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tourAnchor(TourAnchor.CREATE_SERVER_TYPE),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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
                                                .clickable {
                                                    selectedServerType = type
                                                    tour?.completeStep(PocketTours.STEP_CREATE_TYPE)
                                                    showVersionDialog = true
                                                }
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
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                                tour?.completeStep(PocketTours.STEP_CREATE_TYPE)
                                showVersionDialog = true
                            },
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
                                    Text(
                                        text = if (selectedVersion.isNotBlank()) "Change" else "Select",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PocketColors.PrimaryDark,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // World & Gameplay Settings Card
            GameCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = PocketColors.Primary, modifier = Modifier.size(20.dp))
                        Text(
                            text = "World & Gameplay Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) PocketColors.PrimaryLight else PocketColors.PrimaryDark
                        )
                    }

                    // --- Game Mode ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tourAnchor(TourAnchor.CREATE_SERVER_GAMEMODE),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "Game Mode", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val gameModes = listOf(
                            "survival" to "Survival",
                            "creative" to "Creative",
                            "adventure" to "Adventure",
                            "spectator" to "Spectator"
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            gameModes.forEach { (mode, label) ->
                                val isSelected = selectedGameMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) PocketColors.primaryBg else PocketColors.InactiveBg)
                                        .raisedBorder(
                                            color = if (isSelected) PocketColors.primaryBorder else PocketColors.InactiveBorder,
                                            depthColor = if (isSelected) PocketColors.primaryDepth else PocketColors.InactiveBorderBottom,
                                            cornerRadius = 10.dp,
                                            borderWidth = 1.2.dp,
                                            depthWidth = 2.dp
                                        )
                                        .clickable { selectedGameMode = mode },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = if (isSelected) PocketColors.PrimaryText else PocketColors.InactiveText
                                    )
                                }
                            }
                        }
                    }

                    // --- Difficulty & World Generation ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tourAnchor(TourAnchor.CREATE_SERVER_DIFFICULTY),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Difficulty
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Difficulty", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (hardcoreEnabled) {
                                    Text(text = "(Locked: Hardcore)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PocketColors.DangerText)
                                }
                            }
                            val difficulties = listOf(
                                "peaceful" to "Peaceful",
                                "easy" to "Easy",
                                "normal" to "Normal",
                                "hard" to "Hard"
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                difficulties.forEach { (diff, label) ->
                                    val isSelected = (if (hardcoreEnabled) "hard" else selectedDifficulty) == diff
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) PocketColors.primaryBg else PocketColors.InactiveBg)
                                            .raisedBorder(
                                                color = if (isSelected) PocketColors.primaryBorder else PocketColors.InactiveBorder,
                                                depthColor = if (isSelected) PocketColors.primaryDepth else PocketColors.InactiveBorderBottom,
                                                cornerRadius = 10.dp,
                                                borderWidth = 1.2.dp,
                                                depthWidth = 2.dp
                                            )
                                            .clickable(enabled = !hardcoreEnabled) { selectedDifficulty = diff },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            fontSize = 11.sp,
                                            color = if (isSelected) PocketColors.PrimaryText else PocketColors.InactiveText
                                        )
                                    }
                                }
                            }
                        }

                        // World Generation Type
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = "World Generation Type", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val worldTypes = listOf(
                                "minecraft:normal" to ("Default" to "Standard biomes"),
                                "minecraft:flat" to ("Flat" to "Infinite flat world"),
                                "minecraft:large_biomes" to ("Large Biomes" to "Vast terrain"),
                                "minecraft:amplified" to ("Amplified" to "Massive mountains")
                            )
                            worldTypes.chunked(2).forEach { rowTypes ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    rowTypes.forEach { (typeVal, info) ->
                                        val (title, sub) = info
                                        val isSelected = selectedLevelType == typeVal
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) PocketColors.primaryBg else PocketColors.InactiveBg)
                                                .raisedBorder(
                                                    color = if (isSelected) PocketColors.primaryBorder else PocketColors.InactiveBorder,
                                                    depthColor = if (isSelected) PocketColors.primaryDepth else PocketColors.InactiveBorderBottom,
                                                    cornerRadius = 12.dp,
                                                    borderWidth = 1.2.dp,
                                                    depthWidth = 2.dp
                                                )
                                                .clickable { selectedLevelType = typeVal }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Column {
                                                Text(
                                                    text = title,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = if (isSelected) PocketColors.PrimaryText else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = sub,
                                                    fontSize = 9.sp,
                                                    color = if (isSelected) PocketColors.PrimaryText.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // --- Gameplay Rules & Bedrock Crossplay ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tourAnchor(TourAnchor.CREATE_SERVER_CROSSPLAY),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // PvP Combat
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "PvP Combat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(text = "Allow players to attack and fight each other", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DuoToggle(checked = pvpEnabled, onCheckedChange = { pvpEnabled = it })
                        }

                        // Hardcore Mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Hardcore Mode", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(text = "One life only; difficulty locks to Hard, death is permanent", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DuoToggle(
                                checked = hardcoreEnabled,
                                onCheckedChange = {
                                    hardcoreEnabled = it
                                    if (it) selectedDifficulty = "hard"
                                }
                            )
                        }

                        // Allow Player Flight
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Allow Player Flight", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(text = "Permits flight in survival mode without kicking", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DuoToggle(checked = allowFlightEnabled, onCheckedChange = { allowFlightEnabled = it })
                        }

                        // Bedrock Crossplay (Geyser)
                        if (selectedServerType != ServerType.VANILLA && selectedServerType != ServerType.MODPACK) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.Devices, contentDescription = null, tint = PocketColors.PrimaryDark, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text(text = "Bedrock Crossplay (Geyser)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            text = "Allow friends on Android, iOS, Xbox, PlayStation & Switch to join",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                DuoToggle(
                                    checked = bedrockCrossplayEnabled,
                                    onCheckedChange = { bedrockCrossplayEnabled = it }
                                )
                            }
                        }
                    }
                }
            }

            val chevronRotation by animateFloatAsState(targetValue = if (showAdvancedOptions) 180f else 0f, label = "advancedChevron")
            GameCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showAdvancedOptions = !showAdvancedOptions }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Settings, contentDescription = null, tint = PocketColors.Primary, modifier = Modifier.size(20.dp))
                            Column {
                                Text(text = "Advanced Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(text = "MOTD, seed, max players, custom IP & world import", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Icon(Icons.Filled.ExpandMore, contentDescription = if (showAdvancedOptions) "Collapse" else "Expand", modifier = Modifier.rotate(chevronRotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    AnimatedVisibility(visible = showAdvancedOptions, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // Server Profile & MOTD
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Server Profile & Branding",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                ServerDescriptionField(
                                    description = serverDescription,
                                    onDescriptionChange = { serverDescription = it },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                ServerPhotoUpload(
                                    photoUri = serverPhotoUri,
                                    onPhotoSelected = { serverPhotoUri = it; photoChanged = true },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // World Generation & Capacity
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "World Seed & Capacity",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = worldSeed,
                                    onValueChange = { worldSeed = it },
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Filled.Forest, contentDescription = null, tint = PocketColors.PrimaryDark) },
                                    label = { Text("World Seed (Optional)") },
                                    placeholder = { Text("Leave blank for random") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = duoTextFieldShape(),
                                    colors = duoOutlinedTextFieldColors()
                                )

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.Group,
                                                    contentDescription = null,
                                                    tint = PocketColors.Primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text("Max Players", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = PocketColors.Primary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "${maxPlayersValue.roundToInt()} Players",
                                                    fontFamily = Monocraft,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = PocketColors.PrimaryDark,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        Slider(
                                            value = maxPlayersValue,
                                            onValueChange = { newValue -> maxPlayersValue = newValue },
                                            valueRange = 1f..50f,
                                            steps = 48,
                                            colors = SliderDefaults.colors(
                                                thumbColor = PocketColors.Primary,
                                                activeTrackColor = PocketColors.PrimaryDark,
                                                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            // Custom IP Card
                            IpManagerCard(entitlement, true, true, onMessage, onNavigateToSignUp)

                            // Import World Section
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Import Existing World (Optional)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                DimensionImportSlotRow(
                                    dimensionName = "Overworld",
                                    dimensionSubtitle = "Upload overworld level data ZIP",
                                    icon = Icons.Filled.Public,
                                    iconTint = PocketColors.Primary,
                                    selectedFileName = mainWorldZipName,
                                    isLoading = isImporting,
                                    onUploadClick = {
                                        pendingImportSlot = WorldImportSlot.MAIN
                                        importLauncher.launch("application/zip")
                                    }
                                )
                                DimensionImportSlotRow(
                                    dimensionName = "The Nether",
                                    dimensionSubtitle = "DIM-1 Nether terrain ZIP",
                                    icon = Icons.Filled.LocalFireDepartment,
                                    iconTint = Color(0xFFE65100),
                                    selectedFileName = netherZipName,
                                    isLoading = isImporting,
                                    onUploadClick = {
                                        pendingImportSlot = WorldImportSlot.NETHER
                                        importLauncher.launch("application/zip")
                                    }
                                )
                                DimensionImportSlotRow(
                                    dimensionName = "The End",
                                    dimensionSubtitle = "DIM1 End dimension ZIP",
                                    icon = Icons.Filled.NightlightRound,
                                    iconTint = Color(0xFF7B1FA2),
                                    selectedFileName = endZipName,
                                    isLoading = isImporting,
                                    onUploadClick = {
                                        pendingImportSlot = WorldImportSlot.END
                                        importLauncher.launch("application/zip")
                                    }
                                )
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
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    versionSheetState.hide()
                    showVersionDialog = false
                    if (tour?.currentStep?.key in listOf(
                        PocketTours.STEP_SHEET_SERVER_TYPES,
                        PocketTours.STEP_SHEET_VERSION,
                        PocketTours.STEP_SHEET_CONFIRM
                    )) {
                        tour?.advanceTo(PocketTours.STEP_CREATE_TYPE)
                    }
                }
            },
            sheetState = versionSheetState,
            dragHandle = null,
            containerColor = if (isDarkTheme) PocketColors.SurfaceCardDark else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            ServerTypeVersionBottomSheet(
                onDismissRequest = {
                    scope.launch {
                        versionSheetState.hide()
                        showVersionDialog = false
                        if (tour?.currentStep?.key in listOf(
                            PocketTours.STEP_SHEET_SERVER_TYPES,
                            PocketTours.STEP_SHEET_VERSION,
                            PocketTours.STEP_SHEET_DOWNLOAD,
                            PocketTours.STEP_SHEET_DOWNLOADING,
                            PocketTours.STEP_SHEET_CONFIRM
                        )) {
                            tour?.advanceTo(PocketTours.STEP_CREATE_TYPE)
                        }
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
                        versionSheetState.hide()
                        showVersionDialog = false
                        if (tour?.runningTour == TourId.CREATE_SERVER &&
                            tour?.currentStep?.key in listOf(
                                PocketTours.STEP_SHEET_SERVER_TYPES,
                                PocketTours.STEP_SHEET_VERSION,
                                PocketTours.STEP_SHEET_CONFIRM
                            )
                        ) {
                            tour?.advanceTo(PocketTours.STEP_CREATE_GAMEMODE)
                        }
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

@Composable
private fun DimensionImportSlotRow(
    dimensionName: String,
    dimensionSubtitle: String,
    icon: ImageVector,
    iconTint: Color,
    selectedFileName: String,
    isLoading: Boolean,
    onUploadClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Text(
                        text = dimensionName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (selectedFileName.isNotBlank()) selectedFileName else dimensionSubtitle,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (selectedFileName.isNotBlank()) PocketColors.PrimaryDark else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DuoButton(
                text = if (selectedFileName.isNotBlank()) "CHANGE" else "UPLOAD",
                icon = if (selectedFileName.isNotBlank()) Icons.Filled.Check else Icons.Filled.UploadFile,
                onClick = onUploadClick,
                variant = if (selectedFileName.isNotBlank()) DuoButtonVariant.Secondary else DuoButtonVariant.Primary,
                enabled = !isLoading,
                fillMaxWidth = false,
                minHeight = 36.dp
            )
        }
    }
}
