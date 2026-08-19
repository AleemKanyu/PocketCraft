package com.pockethost.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.Context
import android.net.Uri
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.pockethost.app.billing.BillingManager
import com.pockethost.app.billing.PremiumTier
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pockethost.app.ui.components.PocketDropdownMenu
import com.pockethost.app.ui.components.PocketDropdownMenuItem
import com.pockethost.app.BuildConfig
import com.pockethost.app.R
import com.pockethost.app.config.RelayServers
import com.pockethost.app.config.RemoteConfigManager
import com.pockethost.app.data.preferences.AppPreferences
import com.pockethost.app.data.preferences.AppPreferencesStore
import com.pockethost.app.integrations.AccountManager
import com.pockethost.app.integrations.DriveBackupManager
import com.pockethost.app.ui.components.*
import com.pockethost.app.ui.theme.Monocraft
import com.pockethost.app.ui.util.MobTheme
import com.pockethost.app.ui.util.ThemePreference
import com.pockethost.app.ui.util.ThemePreferenceStore
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.card3d
import com.pockethost.app.ui.util.playAppHaptic
import com.pockethost.app.ui.util.playTickHaptic
import com.pockethost.app.util.RamUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.pockethost.app.util.LocalAppStrings
import androidx.compose.ui.text.input.KeyboardType
import com.pockethost.app.feedback.FeedbackService
import com.pockethost.app.update.UpdateConfig
import com.pockethost.app.update.UpdateManager
import com.pockethost.app.ui.components.UpdatePopup

data class LevelTypeOption(val displayName: String, val propertyValue: String)

private val levelTypeOptions = listOf(
    LevelTypeOption("Default",       "minecraft:normal"),
    LevelTypeOption("Flat",          "minecraft:flat"),
    LevelTypeOption("Large Biomes",  "minecraft:large_biomes"),
    LevelTypeOption("Amplified",     "minecraft:amplified"),
    LevelTypeOption("Single Biome",  "minecraft:single_biome_surface")
)

enum class SaveStatus {
    IDLE,
    DIRTY,
    SAVING,
    SAVED,
    FAILED
}

data class SettingsState(
    val config: com.pockethost.app.data.model.ServerConfig = com.pockethost.app.data.model.ServerConfig(),
    val forceGamemode: Boolean = false,
    val broadcastConsoleToOps: Boolean = false,
    val hideOnlinePlayers: Boolean = false,
    val requireResourcePack: Boolean = false,
    val levelType: String = "minecraft:normal",
    val autoRestartEnabled: Boolean = false,
    val maxPowerEnabled: Boolean = false,
    val optimizationPreset: String = "none",
    val forceExternalJvm: Boolean = false
) {
    fun isDifferentFrom(other: SettingsState): Boolean {
        return config != other.config ||
               forceGamemode != other.forceGamemode ||
               broadcastConsoleToOps != other.broadcastConsoleToOps ||
               hideOnlinePlayers != other.hideOnlinePlayers ||
               requireResourcePack != other.requireResourcePack ||
               normalizeWorldType(levelType) != normalizeWorldType(other.levelType) ||
               autoRestartEnabled != other.autoRestartEnabled ||
               maxPowerEnabled != other.maxPowerEnabled ||
               optimizationPreset != other.optimizationPreset ||
               forceExternalJvm != other.forceExternalJvm
    }
}

@Composable
fun SettingsScreen(
    stateHolder: ServerStateHolder,
    onMessage: (String) -> Unit,
    onOpenConfigEditor: () -> Unit = {},
    onOpenLegalPage: () -> Unit = {},
    onDarkThemeChange: (Boolean) -> Unit = {},
    currentMobTheme: MobTheme = MobTheme.SKELETON,
    onMobThemeChange: (MobTheme) -> Unit = {},
    initialActiveTab: Int? = null,
    initialAuthTab: Int? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val preferences = remember { AppPreferences(context) }
    val billingManager = remember { BillingManager.getInstance(context) }
    val isPremium by billingManager.isPremium.collectAsState()
    val entitlement by billingManager.entitlement.collectAsState()
    val premiumPurchaseEnabled by RemoteConfigManager.premiumPurchaseEnabled.collectAsState(initial = false)
    val customSubdomainEnabled by RemoteConfigManager.customSubdomainEnabled.collectAsState(initial = false)
    val hapticFeedback = LocalHapticFeedback.current
    val appFeedbackEnabled by AppPreferencesStore.isSoundEnabledFlow(context).collectAsState(initial = true)
    
    val activeS = LocalAppStrings.current
    var activeTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(activeS.settingsServer, activeS.settingsApp, "Account", activeS.settingsAbout)
    var firebaseUser by remember { mutableStateOf(AccountManager.currentUser()) }
    var signedInAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val acc = AccountManager.currentDriveAccount(context)
            withContext(Dispatchers.Main) {
                signedInAccount = acc
            }
        }
    }
    var emailInput by rememberSaveable { mutableStateOf(firebaseUser?.email.orEmpty()) }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var authBusy by remember { mutableStateOf(false) }
    var driveActionBusy by remember { mutableStateOf(false) }
    var driveStatus by remember { mutableStateOf("") }
    var authTab by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(initialActiveTab) {
        initialActiveTab?.let { activeTab = it }
    }
    LaunchedEffect(initialAuthTab) {
        initialAuthTab?.let { authTab = it }
    }

    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    var showEmailVerifyDialog by remember { mutableStateOf(false) }
    var emailVerifyBusy by remember { mutableStateOf(false) }
    var emailVerifyError by remember { mutableStateOf("") }
    var showDeleteReAuthDialog by remember { mutableStateOf(false) }
    var deleteReAuthPassword by rememberSaveable { mutableStateOf("") }
    var deleteReAuthPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var deleteReAuthError by remember { mutableStateOf("") }
    var accountActionError by remember { mutableStateOf("") }
    android.util.Log.d("POCKETCRAFT_TEST", "SettingsScreen composition! showSignOutConfirm=$showSignOutConfirm")
    val authRecoveryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val acc = AccountManager.currentDriveAccount(context)
            if (acc != null) {
                signedInAccount = acc
                scope.launch {
                    try {
                        driveStatus = "Synchronizing app settings..."
                        val restored = DriveBackupManager.restoreAppSettings(context, acc)
                        if (restored) {
                            driveStatus = "Restored app settings from cloud backup."
                            context.findActivity()?.recreate()
                        } else {
                            DriveBackupManager.uploadAppSettings(context, acc)
                            driveStatus = "Signed in with Google as ${acc.email ?: acc.displayName.orEmpty()}. Settings backed up to cloud."
                        }
                    } catch (e: Exception) {
                        driveStatus = "Signed in, but settings sync failed: ${e.message}"
                    }
                }
            }
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authBusy = true
        AccountManager.completeGoogleSignIn(context, result.data) { account, user, errorMessage ->
            signedInAccount = account ?: AccountManager.currentDriveAccount(context)
            firebaseUser = user ?: AccountManager.currentUser()
            if (firebaseUser?.email?.isNullOrBlank() == false) {
                emailInput = firebaseUser?.email.orEmpty()
            }
            val activeAcc = signedInAccount
            driveStatus = when {
                errorMessage != null -> errorMessage
                activeAcc != null -> "Signed in with Google as ${activeAcc.email ?: activeAcc.displayName.orEmpty()}."
                else -> "Google sign-in completed, but Drive access is not available yet."
            }
            if (activeAcc != null) {
                scope.launch {
                    try {
                        driveStatus = "Synchronizing app settings..."
                        val restored = DriveBackupManager.restoreAppSettings(context, activeAcc)
                        if (restored) {
                            driveStatus = "Restored app settings from cloud backup."
                            context.findActivity()?.recreate()
                        } else {
                            DriveBackupManager.uploadAppSettings(context, activeAcc)
                            driveStatus = "Signed in with Google as ${activeAcc.email ?: activeAcc.displayName.orEmpty()}. Settings backed up to cloud."
                        }
                    } catch (e: Exception) {
                        val cause = e.cause ?: e
                        if (cause is com.google.android.gms.auth.UserRecoverableAuthException && cause.intent != null) {
                            authRecoveryLauncher.launch(cause.intent!!)
                        } else {
                            driveStatus = "Signed in, but settings sync failed: ${e.message}"
                        }
                    }
                }
            }
            authBusy = false
        }
    }

    val initialSettings = remember {
        SettingsState(
            config = stateHolder.config,
            autoRestartEnabled = preferences.autoRestart,
            maxPowerEnabled = preferences.isMaxPowerMode,
            forceExternalJvm = preferences.forceExternalJvm
        )
    }
    var currentState by remember { mutableStateOf(initialSettings) }
    var savedState by remember { mutableStateOf(initialSettings) }
    var hasLoadedInitial by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf(SaveStatus.IDLE) }
    
    var feedbackText by remember { mutableStateOf("") }
    var submittingFeedback by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showStorageManager by remember { mutableStateOf(false) }
    var showMaxPowerWarning by remember { mutableStateOf(false) }
    var showBuildHeightWarning by remember { mutableStateOf(false) }
    var pendingBuildHeightValue by remember { mutableIntStateOf(320) }
    var installedVersions by remember { mutableStateOf<List<InstalledVersionInfo>>(emptyList()) }
    var selectedForDeletion by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isDeletingVersions by remember { mutableStateOf(false) }
    var showThemeMaker by remember { mutableStateOf(false) }
    var showClearStorageManager by remember { mutableStateOf(false) }
    var deletableItems by remember { mutableStateOf<List<DeletableItem>>(emptyList()) }
    var selectedItemsForDeletion by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isClearingStorage by remember { mutableStateOf(false) }
    var showWidgetThemePicker by remember { mutableStateOf(false) }
    var showPremiumBottomSheet by remember { mutableStateOf(false) }
    var checkingForUpdate by remember { mutableStateOf(false) }
    var manualUpdateConfig by remember { mutableStateOf<UpdateConfig?>(null) }

    val hasUnsavedChanges by remember(currentState, savedState) {
        derivedStateOf {
            currentState.isDifferentFrom(savedState)
        }
    }

    LaunchedEffect(hasUnsavedChanges) {
        if (hasUnsavedChanges) {
            if (saveStatus != SaveStatus.SAVING) {
                saveStatus = SaveStatus.DIRTY
            }
        } else {
            if (saveStatus == SaveStatus.DIRTY || saveStatus == SaveStatus.FAILED) {
                saveStatus = SaveStatus.IDLE
            }
        }
    }

    fun playHaptic(doublePulse: Boolean = false) {
        if (!appFeedbackEnabled) return
        scope.launch {
            playAppHaptic(context, hapticFeedback, doublePulse)
        }
    }

    fun playTabHaptic() {
        if (!appFeedbackEnabled) return
        playTickHaptic(context)
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val versions = scanInstalledVersions(context)
            withContext(Dispatchers.Main) {
                installedVersions = versions
            }
        }
        stateHolder.refreshAll()
    }

    LaunchedEffect(stateHolder.config, isPremium) {
        withContext(Dispatchers.IO) {
            val maxPlayersLimit = if (isPremium) 50 else 15
            val forceGamemodeVal = stateHolder.readServerProperty("force-gamemode")?.toBoolean() ?: false
            val broadcastConsoleVal = stateHolder.readServerProperty("broadcast-console-to-ops")?.toBoolean() ?: false
            val hideOnlineVal = stateHolder.readServerProperty("hide-online-players")?.toBoolean() ?: false
            val requireResourcePackVal = stateHolder.readServerProperty("require-resource-pack")?.toBoolean() ?: false
            val levelTypeVal = normalizeWorldType(stateHolder.readServerProperty("level-type"))
            val optPresetVal = stateHolder.readOptimizationPreset()

            val loadedState = SettingsState(
                config = stateHolder.config.copy(maxPlayers = stateHolder.config.maxPlayers.coerceIn(1, maxPlayersLimit)),
                forceGamemode = forceGamemodeVal,
                broadcastConsoleToOps = broadcastConsoleVal,
                hideOnlinePlayers = hideOnlineVal,
                requireResourcePack = requireResourcePackVal,
                levelType = levelTypeVal,
                autoRestartEnabled = preferences.autoRestart,
                maxPowerEnabled = preferences.isMaxPowerMode,
                optimizationPreset = optPresetVal,
                forceExternalJvm = preferences.forceExternalJvm
            )
            withContext(Dispatchers.Main) {
                if (!hasLoadedInitial || !hasUnsavedChanges) {
                    currentState = loadedState
                    savedState = loadedState
                    hasLoadedInitial = true
                } else {
                    savedState = loadedState
                }
            }
        }
    }


    BackHandler(enabled = hasUnsavedChanges) {
        showUnsavedDialog = true
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved settings. Undo changes and revert back to original state?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedDialog = false
                        currentState = savedState
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF4757))
                ) {
                    Text("Undo", color = Color(0xFFFF4757), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog = false }) {
                    Text("Stay")
                }
            }
        )
    }

    if (showMaxPowerWarning) {
        AlertDialog(
            onDismissRequest = { showMaxPowerWarning = false },
            title = { Text("Max Power Mode") },
            text = { Text("Warning: Max Power Mode unlocks all RAM limiters and pushes render distances to their absolute maximum. This can cause significant device heat and battery drain. Only enable this if your device is in a cool place or you have a very high-end device.") },
            confirmButton = {
                TextButton(onClick = {
                    showMaxPowerWarning = false
                    currentState = currentState.copy(maxPowerEnabled = true)
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { showMaxPowerWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBuildHeightWarning) {
        AlertDialog(
            onDismissRequest = { showBuildHeightWarning = false },
            title = { Text("Extreme Build Height") },
            text = { Text("Warning: Setting the maximum building height to more than 1000 might break your world, cause chunk generation failures, or result in severe performance issues/crashes. Do you want to proceed?") },
            confirmButton = {
                TextButton(onClick = {
                    showBuildHeightWarning = false
                    currentState = currentState.copy(config = currentState.config.copy(maxBuildHeight = pendingBuildHeightValue))
                }) {
                    Text("Proceed", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBuildHeightWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showThemeMaker) {
        ThemeMakerBottomSheet(
            onDismiss = { showThemeMaker = false },
            onThemeApplied = { nextTheme ->
                onMobThemeChange(nextTheme)
            }
        )
    }

    if (showPremiumBottomSheet) {
        com.pockethost.app.ui.components.PremiumUpgradeBottomSheet(
            onDismissRequest = { showPremiumBottomSheet = false },
            onNavigateToSignUp = {
                activeTab = 2
                authTab = 1
                showPremiumBottomSheet = false
            }
        )
    }

    if (showWidgetThemePicker) {
        com.pockethost.app.ui.screens.WidgetThemePickerScreen(
            isPremium = isPremium,
            onBack = { showWidgetThemePicker = false },
            onPremiumUpgradeClick = { showPremiumBottomSheet = true },
            onMessage = onMessage
        )
        return
    }


    Box(modifier = Modifier.fillMaxSize()) {
        android.util.Log.d("POCKETCRAFT_TEST", "SettingsScreen Box block is composed!")
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            ScrollableTabRow(
                selectedTabIndex = activeTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                containerColor = Color.Transparent,
                contentColor = PocketColors.Primary,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = PocketColors.Primary
                    )
                },
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { playTabHaptic(); activeTab = index },
                        text = { Text(title, fontWeight = if (activeTab == index) FontWeight.ExtraBold else FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false) }
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Top)
            ) {
            // --- TAB 0: SERVER ---
            if (activeTab == 0) {
                item {
                    AnimatedEntranceContainer(index = 0) {
                        SettingsToggleRow(
                            vectorIcon = Icons.Default.Devices,
                            label = "Bedrock Crossplay Support (Geyser & Floodgate)",
                            description = if (stateHolder.bedrockBridgeEnabled) "ENABLED — Allows Bedrock (Mobile & Console) players to join." else "DISABLED — Server boots faster and uses less memory (Java players only).",
                            checked = stateHolder.bedrockBridgeEnabled,
                            onToggle = { enabled ->
                                stateHolder.toggleBedrockBridge(enabled)
                            }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 0) {
                        SettingsSection(activeS.performance, Icons.Default.Memory, isFirstSection = false)
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 1) {
                        SettingsSliderRow(
                            icon = Icons.Default.Visibility,
                            label = activeS.viewDistance,
                            description = activeS.viewDistanceDesc,
                            hint = activeS.viewDistanceHint,
                            min = 3,
                            max = 32,
                            value = currentState.config.viewDistance,
                            onValueChange = { currentState = currentState.copy(config = currentState.config.copy(viewDistance = it)) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 2) {
                        SettingsSliderRow(
                            icon = Icons.Default.Speed,
                            label = activeS.simulationDistance,
                            description = activeS.simulationDistanceDesc,
                            min = 3,
                            max = 32,
                            value = currentState.config.simulationDistance,
                            onValueChange = { currentState = currentState.copy(config = currentState.config.copy(simulationDistance = it)) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 3) {
                        SettingsToggleRow(
                            icon = "⚡",
                            label = activeS.maxPowerMode,
                            description = activeS.maxPowerModeDesc,
                            checked = currentState.maxPowerEnabled,
                            onToggle = { 
                                if (it) {
                                    showMaxPowerWarning = true
                                } else {
                                    currentState = currentState.copy(maxPowerEnabled = false)
                                }
                                playHaptic() 
                            }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 4) {
                        SettingsDropdownRow(
                            icon = Icons.Default.AutoGraph,
                            label = activeS.mobSpawning,
                            description = activeS.mobSpawningDesc,
                            options = listOf("none", "lite", "balanced", "performance"),
                            optionLabels = mapOf(
                                "none" to activeS.mobSpawningStandard,
                                "lite" to "Lite (Recommended)",
                                "balanced" to "Balanced",
                                "performance" to "Aggressive"
                            ),
                            selected = currentState.optimizationPreset,
                            onSelected = { currentState = currentState.copy(optimizationPreset = it) }
                        )
                    }
                }

                item {
                    AnimatedEntranceContainer(index = 5) {
                        SettingsSection(activeS.worldSettings, Icons.Default.Public)
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 6) {
                        val currentLevelType = sanitizeLevelType(currentState.levelType)
                        SettingsDropdownRow(
                            icon = Icons.Default.Terrain,
                            label = "World Type",
                            options = levelTypeOptions.map { it.propertyValue },
                            optionLabels = levelTypeOptions.associate { it.propertyValue to it.displayName },
                            selected = currentLevelType,
                            onSelected = { currentState = currentState.copy(levelType = it) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 7) {
                        SettingsDropdownRow(
                            icon = Icons.Default.SignalCellularAlt,
                            label = activeS.difficulty,
                            options = listOf("peaceful", "easy", "normal", "hard"),
                            selected = currentState.config.difficulty,
                            onSelected = { currentState = currentState.copy(config = currentState.config.copy(difficulty = it)) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsDropdownRow(
                            icon = Icons.Default.VideogameAsset,
                            label = "Game Mode",
                            options = listOf("survival", "creative", "adventure", "spectator"),
                            selected = currentState.config.gameMode,
                            onSelected = { currentState = currentState.copy(config = currentState.config.copy(gameMode = it)) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsSliderRow(
                            icon = Icons.Default.Height,
                            label = "Max Build Height",
                            min = 64, max = 2048, step = 16,
                            value = currentState.config.maxBuildHeight,
                            warning = if (currentState.config.maxBuildHeight > 1000) {
                                "⚠️ Warning: Building height > 1000 might break the world or cause performance issues."
                            } else null,
                            onValueChange = { height ->
                                if (height > 1000 && currentState.config.maxBuildHeight <= 1000) {
                                    pendingBuildHeightValue = height
                                    showBuildHeightWarning = true
                                } else {
                                    currentState = currentState.copy(config = currentState.config.copy(maxBuildHeight = height))
                                }
                            }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsToggleRow(
                            icon = "💀",
                            label = "Hardcore Mode",
                            description = "Players are banned upon death",
                            checked = currentState.config.hardcore,
                            onToggle = { currentState = currentState.copy(config = currentState.config.copy(hardcore = it)) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsToggleRow(
                            icon = "🔥",
                            label = "Nether Enabled",
                            checked = currentState.config.netherEnabled,
                            onToggle = { currentState = currentState.copy(config = currentState.config.copy(netherEnabled = it)) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsToggleRow(
                            icon = "👻",
                            label = "Spawn Monsters",
                            checked = currentState.config.spawnMonsters,
                            onToggle = { currentState = currentState.copy(config = currentState.config.copy(spawnMonsters = it)) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsToggleRow(
                            icon = "🐷",
                            label = "Spawn Animals",
                            checked = currentState.config.spawnAnimals,
                            onToggle = { currentState = currentState.copy(config = currentState.config.copy(spawnAnimals = it)) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsToggleRow(
                            icon = "🧑‍🌾",
                            label = "Spawn NPCs (Villagers)",
                            checked = currentState.config.spawnNpcs,
                            onToggle = { currentState = currentState.copy(config = currentState.config.copy(spawnNpcs = it)) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsToggleRow(
                            icon = "📜",
                            label = "Whitelist",
                            description = "Only allowed players can join",
                            checked = currentState.config.whiteList,
                            onToggle = {
                                currentState = currentState.copy(
                                    config = currentState.config.copy(
                                        whiteList = it,
                                        enforceWhitelist = currentState.config.enforceWhitelist && it
                                    )
                                )
                            }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsToggleRow(
                            icon = "🚫",
                            label = "Enforce Whitelist",
                            description = if (!currentState.config.whiteList)
                                "Enable Whitelist first to use this option"
                            else
                                "Kick players not on whitelist upon reload",
                            checked = currentState.config.enforceWhitelist && currentState.config.whiteList,
                            enabled = currentState.config.whiteList,
                            onToggle = {
                                currentState = currentState.copy(
                                    config = currentState.config.copy(
                                        enforceWhitelist = it && currentState.config.whiteList
                                    )
                                )
                            }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsToggleRow(
                            icon = "🏠",
                            label = "Generate Structures",
                            checked = currentState.config.generateStructures,
                            onToggle = { currentState = currentState.copy(config = currentState.config.copy(generateStructures = it)) }
                        )
                    }
                }

                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsSection(activeS.networking, Icons.Default.VpnLock)
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        val relayRegions by RemoteConfigManager.relayRegions.collectAsState(initial = RelayServers.defaultRegions())
                        val relayOptions = relayRegions.associate { it.host to it.label }
                        SettingsDropdownRow(
                            icon = Icons.Default.Router,
                            label = "Relay Server",
                            description = if (stateHolder.isNavigationLocked) {
                                "Switch relay while the server is online (players may rejoin)"
                            } else {
                                "Closest location for best ping"
                            },
                            options = relayOptions.keys.toList(),
                            optionLabels = relayOptions,
                            selected = stateHolder.relayHost,
                            onSelected = { host -> scope.launch { onMessage(stateHolder.updateRelayHost(host)) } }
                        )
                    }
                }


                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsSection("EXTRA SERVER OPTIONS", Icons.Default.Settings)
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsSliderRow(
                            icon = Icons.Default.Groups,
                            label = activeS.maxPlayers,
                            min = 1, max = 50,
                            value = currentState.config.maxPlayers,
                            onValueChange = { 
                                if (it > 15 && !isPremium) {
                                    showPremiumBottomSheet = true
                                    currentState = currentState.copy(config = currentState.config.copy(maxPlayers = 15))
                                } else {
                                    currentState = currentState.copy(config = currentState.config.copy(maxPlayers = it))
                                }
                            }
                        )
                    }
                }

                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsToggleRow(
                            vectorIcon = Icons.Default.Devices,
                            label = "Bedrock Crossplay Support",
                            description = if (stateHolder.bedrockBridgeEnabled) "Geyser & Floodgate enabled. Bedrock mobile and console players can join." else "Disabled. Server boots faster and saves memory (Java players only).",
                            checked = stateHolder.bedrockBridgeEnabled,
                            onToggle = { enabled ->
                                stateHolder.toggleBedrockBridge(enabled)
                            }
                        )
                    }
                }

                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsToggleRow(
                            icon = "⚔️",
                            label = "Player vs Player (PVP)",
                            checked = currentState.config.pvp,
                            onToggle = { currentState = currentState.copy(config = currentState.config.copy(pvp = it)) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsToggleRow(
                            icon = "✈️",
                            label = "Allow Flight",
                            checked = currentState.config.allowFlight,
                            onToggle = { currentState = currentState.copy(config = currentState.config.copy(allowFlight = it)) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsToggleRow(
                            icon = "⚙️",
                            label = "Command Blocks",
                            checked = currentState.config.commandBlocks,
                            onToggle = { currentState = currentState.copy(config = currentState.config.copy(commandBlocks = it)) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsToggleRow(
                            icon = "📣",
                            label = activeS.broadcastConsole,
                            checked = currentState.broadcastConsoleToOps,
                            onToggle = { currentState = currentState.copy(broadcastConsoleToOps = it) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsToggleRow(
                            icon = "📦",
                            label = "Require Resource Pack",
                            description = "Require connecting players to accept the server resource pack download",
                            checked = currentState.requireResourcePack,
                            onToggle = { currentState = currentState.copy(requireResourcePack = it) }
                        )
                    }
                }

                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsSection("ADVANCED SETTINGS", Icons.Default.Tune)
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsSliderRow(
                            icon = Icons.Default.Shield,
                            label = "Spawn Protection",
                            description = "Radius of protected blocks at spawn",
                            min = 0, max = 100,
                            value = currentState.config.spawnProtection,
                            onValueChange = { currentState = currentState.copy(config = currentState.config.copy(spawnProtection = it)) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsSliderRow(
                            icon = Icons.Default.Timer,
                            label = "Player Idle Timeout",
                            description = "Minutes before kicking idle players",
                            min = 0, max = 120,
                            value = currentState.config.playerIdleTimeout,
                            onValueChange = { currentState = currentState.copy(config = currentState.config.copy(playerIdleTimeout = it)) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsSliderRow(
                            icon = Icons.Default.Person,
                            label = "Entity Broadcast Range",
                            description = "How far entities are visible (%) — increasing this might increase ping",
                            min = 10, max = 100, step = 10,
                            value = currentState.config.entityBroadcastRangePercentage,
                            onValueChange = { currentState = currentState.copy(config = currentState.config.copy(entityBroadcastRangePercentage = it)) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsDropdownRow(
                            icon = Icons.Default.AdminPanelSettings,
                            label = "Op Permission Level",
                            options = listOf("1", "2", "3", "4"),
                            optionLabels = mapOf("1" to "Level 1 (Bypass)", "2" to "Level 2 (Commands)", "3" to "Level 3 (Management)", "4" to "Level 4 (Owner)"),
                            selected = currentState.config.opPermissionLevel.toString(),
                            onSelected = { currentState = currentState.copy(config = currentState.config.copy(opPermissionLevel = it.toInt())) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsToggleRow(
                            icon = "🔒",
                            label = activeS.onlineMode,
                            description = "Online Mode (Mojang Authentication)",
                            checked = currentState.config.onlineMode,
                            onToggle = { currentState = currentState.copy(config = currentState.config.copy(onlineMode = it)) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        SettingsToggleRow(
                            icon = "🌐",
                            label = "Native Transport",
                            description = "Optimized Linux networking",
                            checked = currentState.config.useNativeTransport,
                            onToggle = { currentState = currentState.copy(config = currentState.config.copy(useNativeTransport = it)) }
                        )
                    }
                }
            }

            // --- TAB 1: APP ---
            if (activeTab == 1) {
                item {
                    AnimatedEntranceContainer(index = 0) {
                        SettingsSection(activeS.appPreferences, Icons.Default.Tune, isFirstSection = true)
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 1) {
                        SettingsDropdownRow(
                            icon = Icons.Default.Palette,
                            label = "Mob Theme",
                            description = "Choose the app's theme color palette",
                            options = MobTheme.entries.map { it.id },
                            optionLabels = MobTheme.entries.associate { it.id to it.themeName },
                            selected = currentMobTheme.id,
                            onSelected = { selectedId ->
                                val selectedTheme = MobTheme.fromId(selectedId)
                                if (selectedTheme == MobTheme.CUSTOM && !isPremium) {
                                    showPremiumBottomSheet = true
                                } else {
                                    onMobThemeChange(selectedTheme)
                                    playHaptic()
                                }
                            }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 2) {
                        GameCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isPremium) {
                                        showThemeMaker = true
                                    } else {
                                        showPremiumBottomSheet = true
                                    }
                                },
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = null,
                                        tint = PocketColors.Primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("Custom Theme Maker", fontWeight = FontWeight.SemiBold, color = PocketColors.TextPrimary)
                                    if (!isPremium) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Locked",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 3) {
                        GameCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showWidgetThemePicker = true },
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Widget Themes",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "Choose whether the widget follows the app theme or uses its own style",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = PocketColors.PrimaryDark
                                )
                            }
                        }
                    }
                }

                item {
                    AnimatedEntranceContainer(index = 3) {
                        SettingsToggleRow(
                            icon = "📳",
                            label = "Haptic Feedback",
                            description = "Vibrations for UI interactions",
                            checked = appFeedbackEnabled,
                            onToggle = { enabled ->
                                scope.launch { AppPreferencesStore.setSoundEnabled(context, enabled) }
                                playHaptic()
                            }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 4) {
                        val isFloatingChatFlowState by AppPreferencesStore.isFloatingChatEnabledFlow(context).collectAsState(initial = preferences.isFloatingChatEnabled)
                        SettingsToggleRow(
                            icon = "💬",
                            label = "Floating Chat on All Screens",
                            description = "Enable access to game chat overlay from any screen",
                            checked = isFloatingChatFlowState && isPremium,
                            onToggle = { enabled ->
                                if (!isPremium) {
                                    showPremiumBottomSheet = true
                                } else {
                                    preferences.isFloatingChatEnabled = enabled
                                    scope.launch {
                                        AppPreferencesStore.setFloatingChatEnabled(context, enabled)
                                        if (firebaseUser != null) {
                                            try {
                                                DriveBackupManager.uploadAppSettingsToFirebase(context, firebaseUser!!)
                                            } catch (e: Exception) {
                                                // Silent fail
                                            }
                                        }
                                        val activeAcc = signedInAccount
                                        if (activeAcc != null) {
                                            try {
                                                DriveBackupManager.uploadAppSettings(context, activeAcc)
                                            } catch (e: Exception) {
                                                // Silent fail
                                            }
                                        }
                                    }
                                }
                                playHaptic()
                            }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 5) {
                        val alwaysAliveBackgroundFlowState by AppPreferencesStore.isAlwaysAliveBackgroundFlow(context).collectAsState(initial = preferences.alwaysAliveBackground)
                        SettingsToggleRow(
                            icon = "♾️",
                            label = "Always Alive in Background",
                            description = "Keep app helper processes active in the background even when the Minecraft server is stopped",
                            checked = alwaysAliveBackgroundFlowState,
                            onToggle = { enabled ->
                                preferences.alwaysAliveBackground = enabled
                                scope.launch {
                                    AppPreferencesStore.setAlwaysAliveBackground(context, enabled)
                                    if (enabled) {
                                        val listenerIntent = Intent(context, com.pockethost.app.server.ServerHostService::class.java).apply {
                                            action = com.pockethost.app.server.ServerHostService.ACTION_START_LISTENER
                                        }
                                        try {
                                            androidx.core.content.ContextCompat.startForegroundService(context, listenerIntent)
                                        } catch (e: Exception) {
                                            android.util.Log.e("SettingsScreen", "Failed to start listener service: ${e.message}")
                                        }
                                    } else {
                                        if (!com.pockethost.app.server.ServerHostService.serverReadyState.value) {
                                            com.pockethost.app.server.ServerHostService.stop(context)
                                        }
                                    }
                                }
                                playHaptic()
                            }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 5) {
                        var currentLanguage by remember { mutableStateOf(preferences.appLanguage) }
                        var pendingRecreate by remember { mutableStateOf(false) }

                        LaunchedEffect(pendingRecreate) {
                            if (pendingRecreate) {
                                // Wait for the DropdownMenu dismiss animation to finish
                                kotlinx.coroutines.delay(180)
                                context.findActivity()?.recreate()
                            }
                        }

                        val languageMap = remember {
                            try {
                                val jsonStr = context.assets.open("locales/languages.json").bufferedReader().use { it.readText() }
                                com.google.gson.Gson().fromJson<Map<String, String>>(
                                    jsonStr,
                                    object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                                ) ?: emptyMap()
                            } catch (e: Exception) {
                                mapOf(
                                    "system" to "System Default",
                                    "en" to "English (US)",
                                    "de" to "Deutsch (German)",
                                    "es" to "Español (Spanish)",
                                    "ru" to "Русский (Russian)",
                                    "zh" to "简体中文 (Chinese)"
                                )
                            }
                        }

                        SettingsDropdownRow(
                            icon = Icons.Default.Translate,
                            label = activeS.appLanguage,
                            description = activeS.chooseLanguage,
                            options = languageMap.keys.toList(),
                            optionLabels = languageMap,
                            selected = currentLanguage,
                            onSelected = { selectedLang ->
                                currentLanguage = selectedLang
                                preferences.appLanguage = selectedLang
                                playHaptic()
                                pendingRecreate = true
                            }
                        )
                    }
                }

                item {
                    AnimatedEntranceContainer(index = 6) {
                        SettingsSection("TROUBLESHOOTING", Icons.Default.BuildCircle)
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 6) {
                        SettingsToggleRow(
                            icon = "⚙️",
                            label = activeS.forceExternalJvm,
                            description = activeS.forceExternalJvmDesc,
                            checked = currentState.forceExternalJvm,
                            onToggle = { currentState = currentState.copy(forceExternalJvm = it) }
                        )
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 7) {
                        SettingsSection(activeS.sectionDeviceStorage, Icons.Default.Storage)
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 7) {
                        GameCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Manage Server Versions", fontWeight = FontWeight.Bold)
                                Text("Remove unused downloads to free up space.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                DuoButton(
                                    text = "MANAGE VERSIONS",
                                    onClick = {
                                        installedVersions = scanInstalledVersions(context)
                                        selectedForDeletion = emptySet()
                                        showStorageManager = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 8) {
                        GameCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Clear App Storage", fontWeight = FontWeight.Bold)
                                Text(
                                    "Delete temporary files, server logs, old backups, and world recovery archives. World data is never touched.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                DuoButton(
                                    text = "MANAGE STORAGE",
                                    onClick = {
                                        deletableItems = scanDeletableItems(context)
                                        selectedItemsForDeletion = emptySet()
                                        showClearStorageManager = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // --- TAB 2: ACCOUNT ---
            if (activeTab == 2) {
                item {
                    AnimatedEntranceContainer(index = 0) {
                        SettingsSection("VOLUNTARY DONATIONS", Icons.Default.Favorite, isFirstSection = true)
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 1) {
                        GameCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color(0xFFE91E63).copy(alpha = 0.20f),
                                                    PocketColors.PrimaryMuted.copy(alpha = 0.30f)
                                                )
                                            )
                                        )
                                        .border(1.dp, Color(0xFFE91E63).copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = Color(0xFFE91E63),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                val hasActiveDonation = entitlement.tier != PremiumTier.NONE && entitlement.tier != null

                                AccountStatusChip(
                                    label = if (hasActiveDonation) {
                                        if (entitlement.isSupportive) "Champion Supporter" else "Supporter Active"
                                    } else "Community Funded",
                                    tone = if (hasActiveDonation) PocketColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = if (hasActiveDonation) "Thank You for Supporting!" else "Support PocketHost",
                                    modifier = Modifier.fillMaxWidth(),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    fontFamily = Monocraft,
                                    textAlign = TextAlign.Center
                                )

                                if (hasActiveDonation) {
                                    val expiryDate = entitlement.expiresAtEpochMillis?.let {
                                        val date = java.util.Date(it)
                                        java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()).format(date)
                                    }
                                    if (expiryDate != null) {
                                        Text(
                                            text = "Next renewal: $expiryDate",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PocketColors.Primary,
                                            fontFamily = Monocraft,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "PocketHost is 100% free and open-source for everyone. We do not lock features behind paywalls. Your voluntary monthly contribution helps pay for server relays, high-speed download mirrors, and ongoing updates.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DuoButton(
                                        text = "DONATE ₹299/MO",
                                        onClick = {
                                            val activity = context as? Activity
                                            if (activity != null) {
                                                showPremiumBottomSheet = true
                                            } else {
                                                onMessage("Could not open donation sheet.")
                                            }
                                        },
                                        variant = DuoButtonVariant.Primary,
                                        modifier = Modifier.weight(1f),
                                        minHeight = 40.dp
                                    )
                                    DuoButton(
                                        text = "DONATE ₹499/MO",
                                        onClick = {
                                            val activity = context as? Activity
                                            if (activity != null) {
                                                showPremiumBottomSheet = true
                                            } else {
                                                onMessage("Could not open donation sheet.")
                                            }
                                        },
                                        variant = DuoButtonVariant.Warning,
                                        modifier = Modifier.weight(1f),
                                        minHeight = 40.dp
                                    )
                                }

                                if (hasActiveDonation) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    TextButton(
                                        onClick = {
                                            val subscriptionSku = when {
                                                entitlement.isSupportive -> BillingManager.PRODUCT_SUPPORTIVE
                                                else -> BillingManager.PRODUCT_PREMIUM
                                            }
                                            runCatching {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    data = Uri.parse("https://play.google.com/store/account/subscriptions?package=${context.packageName}&sku=$subscriptionSku")
                                                }
                                                context.startActivity(intent)
                                            }.onFailure {
                                                runCatching {
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        data = Uri.parse("https://play.google.com/store/account/subscriptions")
                                                    }
                                                    context.startActivity(intent)
                                                }.onFailure {
                                                    onMessage("Could not open Play Store subscription page.")
                                                }
                                            }
                                        }
                                    ) {
                                        Text("Manage Contribution in Google Play", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                val supporterDiscordId by AppPreferencesStore.supporterDiscordIdFlow(context).collectAsState(initial = "")
                                var showDiscordIdDialog by remember { mutableStateOf(false) }

                                if (showDiscordIdDialog) {
                                    var tempDiscordId by remember { mutableStateOf(supporterDiscordId) }
                                    AlertDialog(
                                        onDismissRequest = { showDiscordIdDialog = false },
                                        title = { Text("Discord Supporter Perks", fontFamily = Monocraft, fontWeight = FontWeight.Bold) },
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("Enter your Discord username or ID to claim your exclusive Supporter role and perks in our official Discord server.")
                                                OutlinedTextField(
                                                    value = tempDiscordId,
                                                    onValueChange = { tempDiscordId = it },
                                                    label = { Text("Discord Username / ID") },
                                                    placeholder = { Text("e.g. username or username#1234") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        AppPreferencesStore.setSupporterDiscordId(context, tempDiscordId)
                                                        preferences.supporterDiscordId = tempDiscordId
                                                    }
                                                    showDiscordIdDialog = false
                                                    onMessage("Discord ID saved! Join our Discord to claim your role.")
                                                    runCatching {
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/7xw3Rd2vs2"))
                                                        context.startActivity(intent)
                                                    }
                                                }
                                            ) {
                                                Text("Save & Join Discord")
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showDiscordIdDialog = false }) {
                                                Text("Cancel")
                                            }
                                        }
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { showDiscordIdDialog = true }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (supporterDiscordId.isNotBlank()) "Linked Discord: $supporterDiscordId" else "Claim Discord Supporter Role",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = Monocraft,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (supporterDiscordId.isNotBlank()) "Tap to update your Discord ID" else "Link your Discord username to get supporter perks",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = if (supporterDiscordId.isNotBlank()) "EDIT ✏️" else "LINK 🔗",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PocketColors.Primary
                                    )
                                }
                            }
                        }
                    }
                }


                item {
                    AnimatedEntranceContainer(index = 2) {
                        SettingsSection("GOOGLE ACCOUNT", Icons.Default.AccountCircle, isFirstSection = false)
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 3) {
                        GameCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    PocketColors.Primary.copy(alpha = 0.18f),
                                                    PocketColors.PrimaryMuted.copy(alpha = 0.30f)
                                                )
                                            )
                                        )
                                        .border(1.dp, PocketColors.Primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Login,
                                        contentDescription = null,
                                        tint = PocketColors.Primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                AccountStatusChip(
                                    label = if (firebaseUser == null) "Secure account access" else "Account active",
                                    tone = if (firebaseUser == null) PocketColors.Primary else PocketColors.PrimaryDark
                                )

                                Text(
                                    text = if (firebaseUser == null) "Sign in to PocketHost" else "Account connected",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    fontFamily = Monocraft
                                )
                                Text(
                                    text = if (firebaseUser == null) {
                                        "Use email or Google. Google sign-in also unlocks private Drive backups."
                                    } else {
                                        firebaseUser?.email ?: firebaseUser?.displayName ?: "Signed in"
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp
                                )

                                if (firebaseUser == null) {
                                    // Redesigned modern Tab switcher
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val activeColor = PocketColors.Primary
                                        val activeContentColor = Color.Black
                                        val inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (authTab == 0) activeColor else Color.Transparent)
                                                .clickable { playHaptic(); authTab = 0 },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "SIGN IN",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (authTab == 0) activeContentColor else inactiveContentColor
                                            )
                                        }
                                        
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (authTab == 1) activeColor else Color.Transparent)
                                                .clickable { playHaptic(); authTab = 1 },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "SIGN UP",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (authTab == 1) activeContentColor else inactiveContentColor
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Render the auth status banner if present
                                    if (driveStatus.isNotBlank()) {
                                        androidx.compose.material3.Surface(
                                            color = if (driveStatus.contains("fail", ignoreCase = true) || driveStatus.contains("error", ignoreCase = true) || driveStatus.contains("Could not", ignoreCase = true)) {
                                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                                            } else {
                                                PocketColors.PrimaryMuted.copy(alpha = 0.2f)
                                            },
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (driveStatus.contains("fail", ignoreCase = true) || driveStatus.contains("error", ignoreCase = true) || driveStatus.contains("Could not", ignoreCase = true)) {
                                                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                                } else {
                                                    PocketColors.Primary.copy(alpha = 0.4f)
                                                }
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = driveStatus,
                                                fontSize = 12.sp,
                                                color = if (driveStatus.contains("fail", ignoreCase = true) || driveStatus.contains("error", ignoreCase = true) || driveStatus.contains("Could not", ignoreCase = true)) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    if (com.pockethost.app.ui.theme.pocketIsDarkTheme()) Color.White else Color.Black
                                                },
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }

                                    ThemedAccountField(
                                        value = emailInput,
                                        onValueChange = { emailInput = it },
                                        label = "Email",
                                        leadingIcon = Icons.Default.Email,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    ThemedAccountField(
                                        value = passwordInput,
                                        onValueChange = { passwordInput = it },
                                        label = "Password",
                                        leadingIcon = Icons.Default.Lock,
                                        trailingIcon = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        onTrailingIconClick = { passwordVisible = !passwordVisible },
                                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (authTab == 0) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(
                                                enabled = emailInput.isNotBlank() && !authBusy,
                                                onClick = {
                                                    authBusy = true
                                                    AccountManager.sendPasswordReset(emailInput) { result ->
                                                        driveStatus = result.fold(
                                                            onSuccess = { "Password reset email sent to ${emailInput.trim()}. If you didn't receive it, please check your spam folder." },
                                                            onFailure = { it.message ?: "Could not send password reset email." }
                                                        )
                                                        authBusy = false
                                                    }
                                                }
                                            ) {
                                                Text("Forgot password?", fontSize = 11.5.sp)
                                            }
                                        }

                                        DuoButton(
                                            text = if (authBusy) "SIGNING IN..." else "SIGN IN WITH EMAIL",
                                            enabled = !authBusy && emailInput.isNotBlank() && passwordInput.isNotBlank(),
                                            onClick = {
                                                authBusy = true
                                                try {
                                                    AccountManager.signInWithEmail(context, emailInput.trim(), passwordInput) { result ->
                                                        result
                                                            .onSuccess { user ->
                                                                firebaseUser = user
                                                                passwordInput = ""
                                                                // Send verification email, then show verify dialog
                                                                AccountManager.sendEmailVerification { verifyResult ->
                                                                    verifyResult
                                                                        .onSuccess {
                                                                            driveStatus = "Verification email sent to ${user.email ?: emailInput.trim()}. Please verify before continuing. If you don't see it, check your spam folder too."
                                                                            showEmailVerifyDialog = true
                                                                        }
                                                                        .onFailure {
                                                                            driveStatus = "Account created. Verification email could not be sent: ${it.message}"
                                                                        }
                                                                    authBusy = false
                                                                }
                                                                scope.launch {
                                                                    try {
                                                                        driveStatus = "Synchronizing app settings..."
                                                                        val restored = DriveBackupManager.restoreAppSettingsFromFirebase(context, user)
                                                                        if (restored) {
                                                                            driveStatus = "Restored app settings from cloud backup."
                                                                            context.findActivity()?.recreate()
                                                                        } else {
                                                                            DriveBackupManager.uploadAppSettingsToFirebase(context, user)
                                                                            driveStatus = "Signed in as ${user.email ?: user.displayName.orEmpty()}. Settings backed up to cloud."
                                                                        }
                                                                    } catch (e: Exception) {
                                                                        driveStatus = "Signed in, but settings sync failed: ${e.message}"
                                                                    }
                                                                }
                                                            }
                                                            .onFailure { error ->
                                                                driveStatus = error.message ?: "Email sign-in failed."
                                                            }
                                                        authBusy = false
                                                    }
                                                } catch (e: Exception) {
                                                    driveStatus = e.message ?: "Login process error."
                                                    authBusy = false
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            minHeight = 40.dp
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        DuoButton(
                                            text = if (authBusy) "CREATING ACCOUNT..." else "REGISTER ACCOUNT",
                                            enabled = !authBusy && emailInput.isNotBlank() && passwordInput.length >= 6,
                                            onClick = {
                                                authBusy = true
                                                try {
                                                    AccountManager.createAccountWithEmail(context, emailInput.trim(), passwordInput) { result ->
                                                        result
                                                            .onSuccess { user ->
                                                                firebaseUser = user
                                                                passwordInput = ""
                                                                // Send verification email, then prompt user to confirm
                                                                AccountManager.sendEmailVerification { verifyResult ->
                                                                    verifyResult
                                                                        .onSuccess {
                                                                            driveStatus = "Verification email sent to ${user.email ?: emailInput.trim()}. Please verify to activate your account. If you don't see it, check your spam folder too."
                                                                            showEmailVerifyDialog = true
                                                                        }
                                                                        .onFailure {
                                                                            driveStatus = "Account created. Verification email could not be sent: ${it.message}"
                                                                            scope.launch {
                                                                                try { DriveBackupManager.uploadAppSettingsToFirebase(context, user) } catch (_: Exception) {}
                                                                            }
                                                                        }
                                                                    authBusy = false
                                                                }
                                                            }
                                                            .onFailure { error ->
                                                                driveStatus = error.message ?: "Could not create account."
                                                                authBusy = false
                                                            }
                                                    }
                                                } catch (e: Exception) {
                                                    driveStatus = e.message ?: "Registration process error."
                                                    authBusy = false
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            minHeight = 40.dp
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                                        Text(
                                            "OR CONTINUE WITH",
                                            fontSize = 9.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                                    }

                                    DuoButton(
                                        text = if (authBusy) "CONNECTING..." else "SIGN IN WITH GOOGLE",
                                        enabled = !authBusy,
                                        onClick = {
                                            authBusy = true
                                            googleSignInLauncher.launch(AccountManager.googleSignInIntent(context))
                                        },
                                        iconContent = { GoogleGLogo(Modifier.size(16.dp)) },
                                        variant = DuoButtonVariant.Info,
                                        modifier = Modifier.fillMaxWidth(),
                                        minHeight = 40.dp
                                    )
                                } else {
                                    AccountStatusChip(
                                        label = if (signedInAccount != null) "Google Drive backup ready" else "Signed in with email only",
                                        tone = if (signedInAccount != null) PocketColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (signedInAccount != null) {
                                            "Your Google account is connected and Drive backups are enabled."
                                        } else {
                                            "Drive backups require Google sign-in. You can connect Google below without adding any other providers."
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (signedInAccount == null) {
                                        DuoButton(
                                            text = if (authBusy) "CONNECTING..." else "CONNECT GOOGLE",
                                            enabled = !authBusy,
                                            onClick = {
                                                authBusy = true
                                                googleSignInLauncher.launch(AccountManager.googleSignInIntent(context))
                                            },
                                            iconContent = { GoogleGLogo(Modifier.size(16.dp)) },
                                            variant = DuoButtonVariant.Info,
                                            modifier = Modifier.fillMaxWidth(),
                                            minHeight = 40.dp
                                        )
                                    }

                                    // Show any account action errors (e.g. delete failed)
                                    if (accountActionError.isNotBlank()) {
                                        androidx.compose.material3.Surface(
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = accountActionError,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }

                                    DuoButton(
                                        text = if (authBusy) "SIGNING OUT..." else "SIGN OUT",
                                        onClick = {
                                            android.util.Log.d("POCKETCRAFT_TEST", "SIGN OUT button onClick triggered")
                                            accountActionError = ""
                                            showSignOutConfirm = true
                                        },
                                        enabled = !authBusy && !driveActionBusy,
                                        variant = DuoButtonVariant.Danger,
                                        modifier = Modifier.fillMaxWidth(),
                                        minHeight = 40.dp
                                    )
                                    DuoButton(
                                        text = if (authBusy) "WORKING..." else "DELETE ACCOUNT",
                                        onClick = {
                                            android.util.Log.d("POCKETCRAFT_TEST", "DELETE ACCOUNT button onClick triggered")
                                            deleteReAuthPassword = ""
                                            deleteReAuthError = ""
                                            deleteReAuthPasswordVisible = false
                                            showDeleteReAuthDialog = true
                                        },
                                        enabled = !authBusy && !driveActionBusy,
                                        variant = DuoButtonVariant.Warning,
                                        modifier = Modifier.fillMaxWidth(),
                                        minHeight = 40.dp
                                    )
                                }
                            }
                        }
                    }
                }

                if (entitlement.isPremium && !entitlement.isSupportive) {
                    item {
                        AnimatedEntranceContainer(index = 4) {
                            ProDiscordCard(
                                entitlement = entitlement,
                                onMessage = onMessage
                            )
                        }
                    }
                }

                if (entitlement.isSupportive) {
                    item {
                        AnimatedEntranceContainer(index = 4) {
                            SupportiveToolsCard(
                                entitlement = entitlement,
                                onMessage = onMessage
                            )
                        }
                    }
                }
            }

            // --- TAB 3: ABOUT ---
            if (activeTab == 3) {
                item {
                    AnimatedEntranceContainer(index = 0) {
                        SettingsSection("FIND US ONLINE", Icons.Default.Share, isFirstSection = true)
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Discord
                            GameCard(modifier = Modifier.weight(1f).clickable {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.discord_invite_url)))) }
                            }) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(44.dp)
                                            .background(Color(0xFF5865F2).copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Forum, contentDescription = "Discord", tint = Color(0xFF5865F2), modifier = Modifier.size(24.dp))
                                    }
                                    Text("Discord", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Join community", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            // Instagram
                            GameCard(modifier = Modifier.weight(1f).clickable {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/pocketcraftmc/?hl=en"))) }
                            }) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(44.dp)
                                            .background(Color(0xFFE4405F).copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = "Instagram", tint = Color(0xFFE4405F), modifier = Modifier.size(24.dp))
                                    }
                                    Text("Instagram", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Follow us", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                item {
                    AnimatedEntranceContainer(index = 2) {
                        SettingsSection("FEEDBACK & COMMUNITY", Icons.Default.Forum)
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 3) {
                        GameCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Send Feedback", fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = feedbackText,
                                    onValueChange = { feedbackText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("How can we improve?") },
                                    shape = duoTextFieldShape(),
                                    colors = duoOutlinedTextFieldColors()
                                )
                                DuoButton(
                                    text = if (submittingFeedback) "SENDING..." else "SEND",
                                    enabled = feedbackText.isNotBlank() && !submittingFeedback,
                                    onClick = {
                                        scope.launch {
                                            submittingFeedback = true
                                            val result = FeedbackService.submitFeedback(
                                                context = context,
                                                message = feedbackText,
                                                serverVersion = stateHolder.config.gameVersion.ifBlank { "unknown" }
                                            )
                                            submittingFeedback = false
                                            result
                                                .onSuccess {
                                                    feedbackText = ""
                                                    onMessage("Feedback sent with app logs. Thank you.")
                                                }
                                                .onFailure { error ->
                                                    onMessage(error.message ?: "Could not send feedback right now.")
                                                }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                item {
                    AnimatedEntranceContainer(index = 4) {
                        SettingsSection("HELP & LINKS", Icons.Default.Help)
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 5) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsLinkRow(icon = Icons.Default.Policy, label = activeS.legalCenter, description = activeS.legalCenterDesc, onClick = onOpenLegalPage)
                            SettingsLinkRow(icon = Icons.Default.BugReport, label = "Report a Bug", description = "Report on our Discord server", onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.discord_invite_url)))) }
                                    .onFailure { onMessage("Could not open Discord link.") }
                            })
                            SettingsLinkRow(icon = Icons.Default.Description, label = "Open Source Licenses", description = "Third-party software licenses", onClick = {
                                runCatching {
                                    com.google.android.gms.oss.licenses.OssLicensesMenuActivity.setActivityTitle("Open Source Licenses")
                                    context.startActivity(Intent(context, com.google.android.gms.oss.licenses.OssLicensesMenuActivity::class.java))
                                }.onFailure { onMessage("Could not open licenses.") }
                            })
                        }
                    }
                }

                item {
                    AnimatedEntranceContainer(index = 6) {
                        SettingsSection(activeS.sectionAbout, Icons.Default.Info)
                    }
                }
                item {
                    AnimatedEntranceContainer(index = 7) {
                        GameCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_launcher_foreground),
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                    )
                                    Column {
                                        Text("PocketHost", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, fontFamily = Monocraft)
                                        Text("Version ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Build ${BuildConfig.VERSION_CODE}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
                                        firebaseUser?.let {
                                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))
                                            Text("UID: ${it.uid}", fontSize = 9.sp, fontFamily = Monocraft, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                        }
                                    }
                                }
                                Text("Run full Java Edition multiplayer servers directly on your Android device.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
                                Text("NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.\nPocketHost is an independent software application and is not affiliated with, authorized, maintained, sponsored, or endorsed by Mojang AB, Microsoft Corporation, or any of their affiliates.", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), lineHeight = 12.sp)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                DuoButton(
                                    text = if (checkingForUpdate) "CHECKING..." else "CHECK FOR UPDATE",
                                    enabled = !checkingForUpdate,
                                    onClick = {
                                        playHaptic()
                                        scope.launch {
                                            checkingForUpdate = true
                                            val config = UpdateManager.fetchUpdateConfig(context)
                                            checkingForUpdate = false
                                            if (config?.showUpdatePopup == true) {
                                                manualUpdateConfig = config
                                            } else {
                                                onMessage("Your app is up to date!")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("Made with ❤️ by the PocketHost Team", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PocketColors.PrimaryDark)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }

        manualUpdateConfig?.let { config ->
            UpdatePopup(
                config = config,
                onUpdateNow = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(config.playStoreUrl)))
                    }.onFailure {
                        onMessage("Could not open Play Store link.")
                    }
                },
                onDismiss = {
                    manualUpdateConfig = null
                }
            )
        }

        AnimatedVisibility(
            visible = saveStatus != SaveStatus.IDLE,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .card3d(elevation = 4.dp, cornerRadius = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (saveStatus) {
                            SaveStatus.DIRTY -> {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = PocketColors.PrimaryDark,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Unsaved Changes",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            SaveStatus.SAVING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = PocketColors.Primary
                                )
                                Text(
                                    text = LocalAppStrings.current.saving,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            SaveStatus.SAVED -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2ED573),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = LocalAppStrings.current.saved,
                                    color = Color(0xFF2ED573),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            SaveStatus.FAILED -> {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = Color(0xFFFF4757),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Failed to Save",
                                    color = Color(0xFFFF4757),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            else -> {}
                        }
                    }
                    
                    if (saveStatus == SaveStatus.DIRTY || saveStatus == SaveStatus.SAVING || saveStatus == SaveStatus.FAILED) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (saveStatus == SaveStatus.DIRTY || saveStatus == SaveStatus.FAILED) {
                                Button(
                                    onClick = {
                                        playHaptic()
                                        currentState = savedState
                                        saveStatus = SaveStatus.IDLE
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF4757),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Undo")
                                }
                            }
                            Button(
                                onClick = {
                                    playHaptic(doublePulse = true)
                                    scope.launch {
                                        saveStatus = SaveStatus.SAVING
                                        val normalizedLevelType = normalizeWorldType(currentState.levelType)
                                        val nextConfig = currentState.config.copy(
                                            viewDistance = currentState.config.viewDistance.coerceIn(3, 32),
                                            simulationDistance = currentState.config.simulationDistance.coerceIn(3, 32),
                                            levelType = normalizedLevelType
                                        )
                                        val result = runCatching {
                                            withContext(Dispatchers.IO) {
                                                // Batch all server.properties writes in a single read+write pass
                                                stateHolder.writeBulkServerProperties(mapOf(
                                                    "force-gamemode" to currentState.forceGamemode.toString(),
                                                    "broadcast-console-to-ops" to currentState.broadcastConsoleToOps.toString(),
                                                    "hide-online-players" to currentState.hideOnlinePlayers.toString(),
                                                    "require-resource-pack" to currentState.requireResourcePack.toString()
                                                ))
                                            }
                                            stateHolder.saveSettings(nextConfig)
                                            stateHolder.applyOptimizationPresetBlocking(currentState.optimizationPreset)
                                            val preferencesSaved = preferences.saveRuntimeSettings(
                                                autoRestart = currentState.autoRestartEnabled,
                                                maxPowerMode = currentState.maxPowerEnabled,
                                                forceExternalJvm = currentState.forceExternalJvm
                                            )
                                            if (!preferencesSaved) {
                                                throw IllegalStateException("Failed to persist app settings")
                                            }
                                        }
                                        if (result.isSuccess) {
                                            
                                            val savedStateSnapshot = currentState.copy(
                                                config = nextConfig,
                                                levelType = normalizedLevelType,
                                                autoRestartEnabled = currentState.autoRestartEnabled,
                                                maxPowerEnabled = currentState.maxPowerEnabled,
                                                forceExternalJvm = currentState.forceExternalJvm
                                            )
                                            currentState = savedStateSnapshot
                                            savedState = savedStateSnapshot
                                            saveStatus = SaveStatus.SAVED
                                            delay(300)
                                            if (saveStatus == SaveStatus.SAVED) {
                                                saveStatus = SaveStatus.IDLE
                                            }
                                        } else {
                                            saveStatus = SaveStatus.FAILED
                                            onMessage(result.exceptionOrNull()?.message ?: "Failed to save settings")
                                        }
                                    }
                                },
                                enabled = saveStatus != SaveStatus.SAVING,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (saveStatus == SaveStatus.FAILED) Color(0xFFFF4757) else PocketColors.Primary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = when (saveStatus) {
                                        SaveStatus.SAVING -> LocalAppStrings.current.saving
                                        SaveStatus.FAILED -> "Retry"
                                        else -> LocalAppStrings.current.save
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    android.util.Log.d("POCKETCRAFT_TEST", "SettingsScreen after Box block is composed! showSignOutConfirm=$showSignOutConfirm")

    // Storage Manager Bottom Sheet
    if (showStorageManager) {
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showStorageManager = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Manage Versions", fontWeight = FontWeight.ExtraBold, fontFamily = Monocraft, fontSize = 20.sp)
                
                if (installedVersions.isEmpty()) {
                    Text("No downloaded versions found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Select versions to remove:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(installedVersions) { version ->
                            val isCurrentVersion = version.id == stateHolder.config.gameVersion ||
                                version.displayName.contains(stateHolder.config.gameVersion, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (version.id in selectedForDeletion) Color(0xFFFF4757).copy(alpha = 0.1f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                    .clickable(enabled = !isCurrentVersion) {
                                        selectedForDeletion = if (version.id in selectedForDeletion)
                                            selectedForDeletion - version.id
                                        else
                                            selectedForDeletion + version.id
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = version.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = if (isCurrentVersion) "Active — cannot delete" else version.sizeLabel,
                                        fontSize = 11.sp,
                                        color = if (isCurrentVersion) PocketColors.PrimaryDark else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!isCurrentVersion) {
                                    Checkbox(
                                        checked = version.id in selectedForDeletion,
                                        onCheckedChange = { checked ->
                                            selectedForDeletion = if (checked)
                                                selectedForDeletion + version.id
                                            else
                                                selectedForDeletion - version.id
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (installedVersions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showStorageManager = false }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        DuoButton(
                            text = if (isDeletingVersions) "DELETING..." else "DELETE SELECTED",
                            enabled = selectedForDeletion.isNotEmpty() && !isDeletingVersions,
                            onClick = {
                                isDeletingVersions = true
                                scope.launch {
                                    val count = deleteInstalledVersions(
                                        context,
                                        stateHolder.config.gameVersion,
                                        selectedForDeletion
                                    )
                                    installedVersions = scanInstalledVersions(context)
                                    selectedForDeletion = emptySet()
                                    isDeletingVersions = false
                                    showStorageManager = false
                                    onMessage("Removed $count version(s).")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showClearStorageManager) {
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showClearStorageManager = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Clear App Storage", fontWeight = FontWeight.ExtraBold, fontFamily = Monocraft, fontSize = 20.sp)
                
                if (deletableItems.isEmpty()) {
                    Text("No clearable storage files found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Select items to clear:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(deletableItems) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (item.id in selectedItemsForDeletion) Color(0xFFFF4757).copy(alpha = 0.1f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                    .clickable {
                                        selectedItemsForDeletion = if (item.id in selectedItemsForDeletion)
                                            selectedItemsForDeletion - item.id
                                        else
                                            selectedItemsForDeletion + item.id
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = item.description,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.sizeLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PocketColors.Primary
                                    )
                                }
                                Checkbox(
                                    checked = item.id in selectedItemsForDeletion,
                                    onCheckedChange = { checked ->
                                        selectedItemsForDeletion = if (checked)
                                            selectedItemsForDeletion + item.id
                                        else
                                            selectedItemsForDeletion - item.id
                                    }
                                )
                            }
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showClearStorageManager = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (deletableItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        DuoButton(
                            text = if (isClearingStorage) "CLEARING..." else "CLEAR SELECTED",
                            enabled = selectedItemsForDeletion.isNotEmpty() && !isClearingStorage,
                            onClick = {
                                isClearingStorage = true
                                scope.launch {
                                    var freedBytes = 0L
                                    deletableItems.filter { it.id in selectedItemsForDeletion }.forEach { item ->
                                        freedBytes += item.sizeBytes
                                        deleteDeletableItem(item)
                                    }
                                    deletableItems = scanDeletableItems(context)
                                    selectedItemsForDeletion = emptySet()
                                    isClearingStorage = false
                                    showClearStorageManager = false
                                    onMessage("Cleared storage, freed ${formatSize(freedBytes)}.")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSignOutConfirm) {
        android.util.Log.d("POCKETCRAFT_TEST", "Sign Out Dialog block is composed!")
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { 
                    android.util.Log.d("POCKETCRAFT_TEST", "Sign Out Dialog onDismissRequest called")
                    showSignOutConfirm = false 
                },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { 
                            android.util.Log.d("POCKETCRAFT_TEST", "Sign Out Dialog Box scrim clicked")
                            showSignOutConfirm = false 
                        },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showSignOutConfirm,
                        enter = androidx.compose.animation.fadeIn(tween(300)) + androidx.compose.animation.scaleIn(
                            animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            initialScale = 0.9f
                        ),
                        exit = androidx.compose.animation.fadeOut(tween(200)) + androidx.compose.animation.scaleOut(
                            targetScale = 0.9f
                        )
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(280.dp)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {}
                                .border(1.dp, PocketColors.Primary.copy(alpha = 0.15f), RoundedCornerShape(20.dp)),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    "Sign Out",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    fontFamily = Monocraft,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Are you sure you want to sign out? You will need to log back in to access cloud backups.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    DuoButton(
                                        text = "CANCEL",
                                        onClick = { showSignOutConfirm = false },
                                        variant = DuoButtonVariant.Secondary,
                                        modifier = Modifier.weight(1f),
                                        minHeight = 36.dp
                                    )
                                    DuoButton(
                                        text = "SIGN OUT",
                                        onClick = {
                                            showSignOutConfirm = false
                                            // signOut calls onComplete() synchronously after
                                            // Firebase.signOut(), so state is guaranteed to update.
                                            AccountManager.signOut(context) {
                                                firebaseUser = null
                                                signedInAccount = null
                                                passwordInput = ""
                                                accountActionError = ""
                                                driveStatus = "Signed out successfully."
                                            }
                                        },
                                        variant = DuoButtonVariant.Danger,
                                        modifier = Modifier.weight(1f),
                                        minHeight = 36.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // ── Email Verification Dialog (shown after account creation) ─────────
        if (showEmailVerifyDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { if (!emailVerifyBusy) showEmailVerifyDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { if (!emailVerifyBusy) showEmailVerifyDialog = false },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .width(310.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {}
                            .border(1.dp, PocketColors.Primary.copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(PocketColors.Primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MarkEmailRead,
                                    contentDescription = null,
                                    tint = PocketColors.Primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Text(
                                "Verify your email",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                fontFamily = Monocraft
                            )
                            Text(
                                "We sent a verification link to ${firebaseUser?.email.orEmpty()}. Open the link in your email app, then tap \"I've Verified\" below. If you don't see it, check your spam folder too.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            if (emailVerifyError.isNotBlank()) {
                                Text(
                                    emailVerifyError,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            DuoButton(
                                text = if (emailVerifyBusy) "CHECKING..." else "I'VE VERIFIED",
                                enabled = !emailVerifyBusy,
                                onClick = {
                                    emailVerifyBusy = true
                                    emailVerifyError = ""
                                    AccountManager.reloadAndCheckVerified { result ->
                                        result
                                            .onSuccess { verified ->
                                                if (verified) {
                                                    showEmailVerifyDialog = false
                                                    driveStatus = "Email verified! Account ready."
                                                    scope.launch {
                                                        try {
                                                            firebaseUser?.let {
                                                                DriveBackupManager.uploadAppSettingsToFirebase(context, it)
                                                            }
                                                        } catch (_: Exception) {}
                                                    }
                                                } else {
                                                    emailVerifyError = "Email not verified yet. Please click the link in your inbox first."
                                                }
                                            }
                                            .onFailure { emailVerifyError = it.message ?: "Could not check verification status." }
                                        emailVerifyBusy = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minHeight = 44.dp
                            )
                            DuoButton(
                                text = "RESEND EMAIL",
                                variant = DuoButtonVariant.Secondary,
                                enabled = !emailVerifyBusy,
                                onClick = {
                                    emailVerifyBusy = true
                                    AccountManager.sendEmailVerification { result ->
                                        emailVerifyError = result.fold(
                                            onSuccess = { "Verification email resent to ${firebaseUser?.email.orEmpty()}." },
                                            onFailure = { it.message ?: "Could not resend email." }
                                        )
                                        emailVerifyBusy = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minHeight = 40.dp
                            )
                        }
                    }
                }
            }
        }

        // ── Delete Account: Password Re-Auth Dialog ────────────────────────────
        if (showDeleteReAuthDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { if (!authBusy) showDeleteReAuthDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { if (!authBusy) showDeleteReAuthDialog = false },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .width(310.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {}
                            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.22f), RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Text(
                                "Confirm Deletion",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                fontFamily = Monocraft,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "This permanently deletes your account, synced settings, and cloud backups. Enter your password to confirm.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            ThemedAccountField(
                                value = deleteReAuthPassword,
                                onValueChange = { deleteReAuthPassword = it },
                                label = "Password",
                                leadingIcon = Icons.Default.Lock,
                                trailingIcon = if (deleteReAuthPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                onTrailingIconClick = { deleteReAuthPasswordVisible = !deleteReAuthPasswordVisible },
                                visualTransformation = if (deleteReAuthPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (deleteReAuthError.isNotBlank()) {
                                Text(
                                    deleteReAuthError,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DuoButton(
                                    text = "CANCEL",
                                    onClick = { showDeleteReAuthDialog = false },
                                    variant = DuoButtonVariant.Primary,
                                    modifier = Modifier.weight(1f),
                                    minHeight = 40.dp
                                )
                                DuoButton(
                                    text = if (authBusy) "DELETING..." else "DELETE",
                                    enabled = !authBusy && deleteReAuthPassword.isNotBlank(),
                                    onClick = {
                                        authBusy = true
                                        deleteReAuthError = ""
                                        accountActionError = ""
                                        scope.launch {
                                            val result = AccountManager.reauthenticateAndDeleteAccount(context, deleteReAuthPassword)
                                            result.fold(
                                                onSuccess = { message ->
                                                    showDeleteReAuthDialog = false
                                                    firebaseUser = null
                                                    signedInAccount = null
                                                    passwordInput = ""
                                                    emailInput = ""
                                                    deleteReAuthPassword = ""
                                                    accountActionError = ""
                                                    driveStatus = message
                                                    onMessage(message)
                                                },
                                                onFailure = { error ->
                                                    deleteReAuthError = error.message ?: "Could not delete account. Please try again."
                                                }
                                            )
                                            authBusy = false
                                        }
                                    },
                                    variant = DuoButtonVariant.Warning,
                                    modifier = Modifier.weight(1f),
                                    minHeight = 40.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Old simple delete confirm (kept for Google-only accounts) ──────────
        if (showDeleteAccountConfirm) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showDeleteAccountConfirm = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { showDeleteAccountConfirm = false },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showDeleteAccountConfirm,
                        enter = androidx.compose.animation.fadeIn(tween(300)) + androidx.compose.animation.scaleIn(
                            animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            initialScale = 0.9f
                        ),
                        exit = androidx.compose.animation.fadeOut(tween(200)) + androidx.compose.animation.scaleOut(
                            targetScale = 0.9f
                        )
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(300.dp)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {}
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.18f), RoundedCornerShape(20.dp)),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    "Delete Account",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    fontFamily = Monocraft,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "This permanently deletes your PocketCraft account access on this device and removes synced settings. This cannot be undone.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 18.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    DuoButton(
                                        text = "CANCEL",
                                        onClick = { showDeleteAccountConfirm = false },
                                        variant = DuoButtonVariant.Primary,
                                        modifier = Modifier.weight(1f),
                                        minHeight = 36.dp
                                    )
                                    DuoButton(
                                        text = "DELETE",
                                        onClick = {
                                            showDeleteAccountConfirm = false
                                            authBusy = true
                                            accountActionError = ""
                                            scope.launch {
                                                val result = AccountManager.deleteAccount(context)
                                                result.fold(
                                                    onSuccess = { message ->
                                                        firebaseUser = null
                                                        signedInAccount = null
                                                        passwordInput = ""
                                                        emailInput = ""
                                                        accountActionError = ""
                                                        driveStatus = message
                                                        onMessage(message)
                                                    },
                                                    onFailure = { error ->
                                                        val msg = when (error) {
                                                            is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException ->
                                                                "For security, sign out and sign back in before deleting your account."
                                                            else -> error.message ?: "Could not delete account. Please try again."
                                                        }
                                                        accountActionError = msg
                                                        onMessage(msg)
                                                    }
                                                )
                                                authBusy = false
                                            }
                                        },
                                        variant = DuoButtonVariant.Warning,
                                        modifier = Modifier.weight(1f),
                                        minHeight = 36.dp
                                    )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sanitizeLevelType(raw: String): String {
    return raw
        .replace("\\\\", ":")
        .replace("\\", ":")
        .removePrefix("minecraft:")
        .let { "minecraft:$it" }
        .replace("minecraft:minecraft:", "minecraft:")
        .trim()
}

@Composable
fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isFirstSection: Boolean = false
) {
    Row(
        modifier = Modifier.padding(
            top = if (isFirstSection) 0.dp else 8.dp,
            bottom = 6.dp
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = title,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            letterSpacing = 0.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun ThemedAccountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 44.dp),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = if (trailingIcon != null && onTrailingIconClick != null) {
            {
                IconButton(onClick = onTrailingIconClick) {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else null,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = PocketColors.PrimaryMuted.copy(alpha = 0.28f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
            focusedBorderColor = PocketColors.Primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
        )
    )
}

@Composable
private fun AccountStatusChip(
    label: String,
    tone: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.copy(alpha = 0.12f))
            .border(1.dp, tone.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = tone,
            fontWeight = FontWeight.Bold,
            fontSize = 10.5.sp
        )
    }
}

@Composable
private fun SettingsLinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String? = null,
    onClick: () -> Unit
) {
    GameCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(PocketColors.PrimaryMuted, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = PocketColors.PrimaryDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(text = label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    description?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: String = "",
    vectorIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    label: String,
    description: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    val rowAlpha = if (enabled) 1f else 0.4f
    GameCard(modifier = Modifier
        .fillMaxWidth()
        .graphicsLayer(alpha = rowAlpha)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (vectorIcon != null) {
                    Icon(vectorIcon, contentDescription = null, tint = PocketColors.PrimaryDark, modifier = Modifier.size(24.dp))
                } else if (icon.isNotBlank()) {
                    FlatEmojiIcon(icon, modifier = Modifier.size(24.dp), tint = PocketColors.PrimaryDark)
                }
                Column {
                    Text(text = label, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    description?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 2
                        )
                    }
                }
            }
            DuoToggle(
                checked = checked && enabled,
                onCheckedChange = { if (enabled) onToggle(it) }
            )
        }
    }
}

@Composable
private fun SettingsDropdownRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    label: String,
    description: String? = null,
    options: List<String>,
    optionLabels: Map<String, String> = emptyMap(),
    selected: String,
    enabled: Boolean = true,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = optionLabels[selected] ?: selected.replaceFirstChar { it.uppercase() }

    GameCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = PocketColors.PrimaryDark, modifier = Modifier.size(24.dp))
                }
                Text(
                    text = label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1
                )
            }
            description?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                    maxLines = 2
                )
            }
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    PocketColors.PrimaryMuted.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                                )
                            ),
                            RoundedCornerShape(16.dp)
                        )
                        .border(
                            1.2.dp,
                            if (enabled) PocketColors.Primary.copy(alpha = 0.26f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable(enabled = enabled) { expanded = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Selected",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = displayValue,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = PocketColors.PrimaryDark
                    )
                }
                PocketDropdownMenu(
                    expanded = expanded && enabled,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        val optionSelected = option == selected
                        PocketDropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (optionSelected) PocketColors.PrimaryMuted.copy(alpha = 0.34f) else Color.Transparent
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = optionLabels[option] ?: option,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (optionSelected) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                    if (optionSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = PocketColors.Primary
                                        )
                                    }
                                }
                            },
                            onClick = {
                                expanded = false
                                onSelected(option)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSliderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    label: String,
    description: String? = null,
    hint: String? = null,
    min: Int,
    max: Int,
    value: Int,
    step: Int = 1,
    enabled: Boolean = true,
    warning: String? = null,
    onValueChange: (Int) -> Unit
) {
    var internalValue by remember(value) { mutableIntStateOf(value) }
    GameCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = PocketColors.PrimaryDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                        description?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 2
                            )
                        }
                    }
                }
                Text(
                    text = internalValue.toString(),
                    color = PocketColors.PrimaryDark,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            }
            Slider(
                value = internalValue.toFloat(),
                onValueChange = { newValue ->
                    val steps = kotlin.math.round((newValue - min) / step).toInt()
                    val nextVal = (min + steps * step).coerceIn(min, max)
                    if (nextVal != internalValue) {
                        internalValue = nextVal
                        onValueChange(nextVal)
                    }
                },
                valueRange = min.toFloat()..max.toFloat(),
                enabled = enabled
            )
            warning?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF4757),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }
            hint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }
        }
    }
}

private fun normalizeWorldType(value: String?): String {
    val sanitized = value.orEmpty().trim().lowercase()
    return when {
        sanitized.contains("flat") -> "minecraft:flat"
        sanitized.contains("large") -> "minecraft:large_biomes"
        sanitized.contains("amplified") -> "minecraft:amplified"
        sanitized.contains("single") -> "minecraft:single_biome_surface"
        else -> "minecraft:normal"
    }
}

private data class InstalledVersionInfo(
    val id: String,
    val displayName: String,
    val sizeLabel: String
)

private fun scanInstalledVersions(context: android.content.Context): List<InstalledVersionInfo> {
    val serversRoot = File(context.filesDir, "servers")
    val binariesRoot = File(serversRoot, "binaries")
    
    val jars = mutableListOf<File>()
    
    // 1. Look in binaries/<version>/*.jar
    if (binariesRoot.exists() && binariesRoot.isDirectory) {
        binariesRoot.listFiles()?.filter { it.isDirectory }?.forEach { versionDir ->
            versionDir.listFiles()?.forEach { file ->
                // Filter for server jars: must be .jar and usually > 5MB
                if (file.isFile && file.extension.equals("jar", ignoreCase = true) && file.length() > 5_000_000L) {
                    jars.add(file)
                }
            }
        }
    }
    
    // 2. Look in servers/<version>/*.jar (legacy or custom)
    if (serversRoot.exists() && serversRoot.isDirectory) {
        serversRoot.listFiles()?.filter { it.isDirectory && it.name != "worlds" && it.name != "binaries" }?.forEach { versionDir ->
            versionDir.listFiles()?.forEach { file ->
                if (file.isFile && file.extension.equals("jar", ignoreCase = true) && file.length() > 5_000_000L) {
                    jars.add(file)
                }
            }
        }
    }

    return jars.mapNotNull { jar ->
        val versionDirName = jar.parentFile?.name ?: return@mapNotNull null
        val id = versionDirName
        
        // Try to parse display name from jar name (e.g. paper-1.20.1.jar -> Paper 1.20.1)
        val split = jar.nameWithoutExtension.split("-", limit = 2)
        val typeLabel: String
        val version: String
        if (split.size == 2) {
            typeLabel = split[0].lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            version = split[1]
        } else {
            typeLabel = "Custom"
            version = jar.nameWithoutExtension
        }
        val sizeMb = (jar.length() / (1024L * 1024L)).coerceAtLeast(1L)
        InstalledVersionInfo(
            id = id,
            displayName = "$typeLabel $version",
            sizeLabel = "$sizeMb MB"
        )
    }.distinctBy { it.id }.sortedByDescending { it.displayName }
}

private fun deleteInstalledVersions(
    context: android.content.Context,
    currentVersionKey: String,
    versions: Set<String>
): Int {
    val serversRoot = File(context.filesDir, "servers")
    val binariesRoot = File(serversRoot, "binaries")
    var deleted = 0

    versions.forEach { versionKey ->
        if (versionKey == currentVersionKey) return@forEach
        
        val binVersionDir = File(binariesRoot, versionKey)
        if (binVersionDir.exists() && binVersionDir.isDirectory) {
            if (binVersionDir.deleteRecursively()) deleted++
        }
        
        val legacyVersionDir = File(serversRoot, versionKey)
        if (legacyVersionDir.exists() && legacyVersionDir.isDirectory && legacyVersionDir.name != "worlds" && legacyVersionDir.name != "binaries") {
            if (legacyVersionDir.deleteRecursively()) deleted++
        }
    }
    return deleted
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun GoogleGLogo(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val sizePx = size.minDimension
        val halfSize = sizePx / 2f
        val strokeWidth = sizePx * 0.22f
        val rect = androidx.compose.ui.geometry.Rect(strokeWidth / 2f, strokeWidth / 2f, sizePx - strokeWidth / 2f, sizePx - strokeWidth / 2f)

        // 1. Red (top segment)
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 190f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = rect.topLeft,
            size = rect.size,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
        // 2. Blue (right segment)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = 340f,
            sweepAngle = 65f,
            useCenter = false,
            topLeft = rect.topLeft,
            size = rect.size,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
        // 3. Green (bottom segment)
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 95f,
            useCenter = false,
            topLeft = rect.topLeft,
            size = rect.size,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
        // 4. Yellow (left segment)
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 140f,
            sweepAngle = 50f,
            useCenter = false,
            topLeft = rect.topLeft,
            size = rect.size,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
        // 5. Horizontal bar of G (Blue)
        drawLine(
            color = Color(0xFF4285F4),
            start = androidx.compose.ui.geometry.Offset(halfSize, halfSize),
            end = androidx.compose.ui.geometry.Offset(sizePx - strokeWidth / 2f, halfSize),
            strokeWidth = strokeWidth
        )
    }
}

data class DeletableItem(
    val id: String,
    val name: String,
    val description: String,
    val sizeBytes: Long,
    val sizeLabel: String,
    val files: List<File>
)

private fun scanDeletableItems(context: Context): List<DeletableItem> {
    val items = mutableListOf<DeletableItem>()
    val filesDir = context.filesDir

    // 1. App Cache
    val cacheDir = context.cacheDir
    val cacheSize = getDirectorySize(cacheDir)
    if (cacheSize > 0) {
        items.add(DeletableItem(
            id = "cache",
            name = "App Cache",
            description = "Temporary cache files created during app use",
            sizeBytes = cacheSize,
            sizeLabel = formatSize(cacheSize),
            files = listOf(cacheDir)
        ))
    }

    // 2. Server Temporary Runtime Files
    val runtimeTmpDir = File(filesDir, "runtime-tmp")
    val runtimeTmpSize = getDirectorySize(runtimeTmpDir)
    if (runtimeTmpSize > 0) {
        items.add(DeletableItem(
            id = "runtime-tmp",
            name = "JVM Temporary Files",
            description = "Temporary JVM launch and runtime cache files",
            sizeBytes = runtimeTmpSize,
            sizeLabel = formatSize(runtimeTmpSize),
            files = listOf(runtimeTmpDir)
        ))
    }

    // 3. Server Logs
    val logFiles = mutableListOf<File>()
    findLogFiles(filesDir, logFiles)
    val logsSize = logFiles.sumOf { it.length() }
    if (logsSize > 0) {
        items.add(DeletableItem(
            id = "logs",
            name = "Server Log Files",
            description = "Server console log history files",
            sizeBytes = logsSize,
            sizeLabel = formatSize(logsSize),
            files = logFiles
        ))
    }

    // 4. Crash Reports
    val crashFiles = mutableListOf<File>()
    findCrashFiles(filesDir, crashFiles)
    val crashSize = crashFiles.sumOf { it.length() }
    if (crashSize > 0) {
        items.add(DeletableItem(
            id = "crashes",
            name = "JVM Crash Reports",
            description = "Hotspot JVM error reports from previous crashes",
            sizeBytes = crashSize,
            sizeLabel = formatSize(crashSize),
            files = crashFiles
        ))
    }

    // 5. Reset Recovery Archives
    // Every "Reset World" saves the old world into reset_recovery/<worldName>/<timestamp>/.
    // These accumulate permanently and are the largest hidden storage consumer.
    val resetRecoveryRoot = File(filesDir, "reset_recovery")
    val resetRecoverySize = getDirectorySize(resetRecoveryRoot)
    if (resetRecoverySize > 0) {
        items.add(DeletableItem(
            id = "reset-recovery",
            name = "World Reset Archives",
            description = "Old world backups saved when you reset a world. Safe to delete if you no longer need them.",
            sizeBytes = resetRecoverySize,
            sizeLabel = formatSize(resetRecoverySize),
            files = listOf(resetRecoveryRoot)
        ))
    }

    // 6. Local World Backups
    // Auto-backups and manual backups inside each world's /backups/ directory.
    val worldsRoot = File(filesDir, "servers/worlds")
    val backupDirs = mutableListOf<File>()
    worldsRoot.listFiles()?.filter { it.isDirectory }?.forEach { worldDir ->
        val backupsDir = File(worldDir, "backups")
        if (backupsDir.exists() && backupsDir.isDirectory) {
            backupDirs.add(backupsDir)
        }
    }
    val backupsSize = backupDirs.sumOf { getDirectorySize(it) }
    if (backupsSize > 0) {
        items.add(DeletableItem(
            id = "local-backups",
            name = "Local World Backups",
            description = "Auto-backup and manual backup archives stored on this device. Drive backups are unaffected.",
            sizeBytes = backupsSize,
            sizeLabel = formatSize(backupsSize),
            files = backupDirs
        ))
    }

    // 7. Lib Shims (native library compatibility layer, re-extracted on next server launch)
    val libShimsDir = File(filesDir, "lib-shims")
    val libShimsSize = getDirectorySize(libShimsDir)
    if (libShimsSize > 0) {
        items.add(DeletableItem(
            id = "lib-shims",
            name = "Native Library Cache",
            description = "Extracted native compatibility libraries. Automatically re-created on next server launch.",
            sizeBytes = libShimsSize,
            sizeLabel = formatSize(libShimsSize),
            files = listOf(libShimsDir)
        ))
    }

    // Sort by size descending so the biggest offenders appear first
    return items.sortedByDescending { it.sizeBytes }
}

private fun deleteDeletableItem(item: DeletableItem) {
    when (item.id) {
        "cache", "runtime-tmp" -> {
            // Clear contents but keep the directory itself
            item.files.forEach { parentDir ->
                parentDir.listFiles()?.forEach { file -> file.deleteRecursively() }
            }
        }
        "reset-recovery", "local-backups", "lib-shims" -> {
            // Delete entire subdirectories recursively
            item.files.forEach { dir -> dir.deleteRecursively() }
        }
        else -> {
            // Individual files (logs, crash reports)
            item.files.forEach { file -> file.delete() }
        }
    }
}

private fun getDirectorySize(dir: File): Long {
    if (!dir.exists()) return 0L
    if (dir.isFile) return dir.length()
    var size = 0L
    val files = dir.listFiles() ?: return 0L
    for (file in files) {
        size += if (file.isDirectory) getDirectorySize(file) else file.length()
    }
    return size
}

private fun findLogFiles(dir: File, result: MutableList<File>) {
    val files = dir.listFiles() ?: return
    for (file in files) {
        if (file.isDirectory) {
            if (file.name == "logs") {
                addAllFiles(file, result)
            } else {
                findLogFiles(file, result)
            }
        } else if (file.name.endsWith(".log") && !file.name.startsWith("hs_err_pid")) {
            result.add(file)
        }
    }
}

private fun findCrashFiles(dir: File, result: MutableList<File>) {
    val files = dir.listFiles() ?: return
    for (file in files) {
        if (file.isDirectory) {
            findCrashFiles(file, result)
        } else if (file.name.startsWith("hs_err_pid") && file.name.endsWith(".log")) {
            result.add(file)
        }
    }
}

private fun addAllFiles(dir: File, result: MutableList<File>) {
    val files = dir.listFiles() ?: return
    for (file in files) {
        if (file.isDirectory) {
            addAllFiles(file, result)
        } else {
            result.add(file)
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
