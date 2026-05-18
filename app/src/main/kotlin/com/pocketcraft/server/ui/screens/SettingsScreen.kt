package com.pocketcraft.server.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pocketcraft.server.BuildConfig
import com.pocketcraft.server.R
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.ui.components.*
import com.pocketcraft.server.ui.theme.Monocraft
import com.pocketcraft.server.ui.util.ThemePreference
import com.pocketcraft.server.ui.util.ThemePreferenceStore
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.util.playAppHaptic
import com.pocketcraft.server.util.RamUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class LevelTypeOption(val displayName: String, val propertyValue: String)

private val levelTypeOptions = listOf(
    LevelTypeOption("Default",       "minecraft:normal"),
    LevelTypeOption("Flat",          "minecraft:flat"),
    LevelTypeOption("Large Biomes",  "minecraft:large_biomes"),
    LevelTypeOption("Amplified",     "minecraft:amplified"),
    LevelTypeOption("Single Biome",  "minecraft:single_biome_surface")
)

@Composable
fun SettingsScreen(
    stateHolder: ServerStateHolder,
    onMessage: (String) -> Unit,
    onOpenConfigEditor: () -> Unit = {},
    onOpenLegalPage: () -> Unit = {},
    onDarkThemeChange: (Boolean) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val preferences = remember { AppPreferences(context) }
    val hapticFeedback = LocalHapticFeedback.current
    val appFeedbackEnabled by AppPreferencesStore.isSoundEnabledFlow(context).collectAsState(initial = true)
    
    var activeTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Server", "App", "About")

    var autoRestartEnabled by remember { mutableStateOf(preferences.autoRestart) }
    var maxPowerEnabled by remember { mutableStateOf(preferences.isMaxPowerMode) }
    var savedAutoRestart by remember { mutableStateOf(preferences.autoRestart) }
    var savedMaxPower by remember { mutableStateOf(preferences.isMaxPowerMode) }

    var config by remember(stateHolder.config) {
        mutableStateOf(stateHolder.config.copy(maxPlayers = stateHolder.config.maxPlayers.coerceIn(1, 20)))
    }
    var savedConfig by remember(stateHolder.config) {
        mutableStateOf(stateHolder.config.copy(maxPlayers = stateHolder.config.maxPlayers.coerceIn(1, 20)))
    }
    
    var forceGamemode by remember { mutableStateOf(false) }
    var broadcastConsoleToOps by remember { mutableStateOf(false) }
    var hideOnlinePlayers by remember { mutableStateOf(false) }
    var levelType by remember { mutableStateOf("minecraft:normal") }
    
    var savedForceGamemode by remember { mutableStateOf(false) }
    var savedBroadcastConsoleToOps by remember { mutableStateOf(false) }
    var savedHideOnlinePlayers by remember { mutableStateOf(false) }
    var savedLevelType by remember { mutableStateOf("minecraft:normal") }
    
    var optimizationPreset by remember { mutableStateOf("none") }
    var savedOptimizationPreset by remember { mutableStateOf("none") }
    
    var savingSettings by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf("") }
    var submittingFeedback by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showStorageManager by remember { mutableStateOf(false) }
    var showMaxPowerWarning by remember { mutableStateOf(false) }
    var installedVersions by remember { mutableStateOf(scanInstalledVersions(context)) }
    var selectedForDeletion by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isDeletingVersions by remember { mutableStateOf(false) }

    val hasUnsavedChanges by remember {
        derivedStateOf {
            config != savedConfig ||
                forceGamemode != savedForceGamemode ||
                broadcastConsoleToOps != savedBroadcastConsoleToOps ||
                hideOnlinePlayers != savedHideOnlinePlayers ||
                normalizeWorldType(levelType) != normalizeWorldType(savedLevelType) ||
                autoRestartEnabled != savedAutoRestart ||
                maxPowerEnabled != savedMaxPower ||
                optimizationPreset != savedOptimizationPreset
        }
    }

    fun playHaptic(doublePulse: Boolean = false) {
        if (!appFeedbackEnabled) return
        scope.launch {
            playAppHaptic(context, hapticFeedback, doublePulse)
        }
    }

    LaunchedEffect(Unit) {
        stateHolder.refreshAll()
    }

    LaunchedEffect(stateHolder.config) {
        config = stateHolder.config.copy(maxPlayers = stateHolder.config.maxPlayers.coerceIn(1, 20))
        forceGamemode = stateHolder.readServerProperty("force-gamemode")?.toBoolean() ?: false
        broadcastConsoleToOps = stateHolder.readServerProperty("broadcast-console-to-ops")?.toBoolean() ?: false
        hideOnlinePlayers = stateHolder.readServerProperty("hide-online-players")?.toBoolean() ?: false
        levelType = normalizeWorldType(stateHolder.readServerProperty("level-type"))
        optimizationPreset = stateHolder.readOptimizationPreset()
        
        savedConfig = config
        savedForceGamemode = forceGamemode
        savedBroadcastConsoleToOps = broadcastConsoleToOps
        savedHideOnlinePlayers = hideOnlinePlayers
        savedLevelType = levelType
        savedOptimizationPreset = optimizationPreset
    }

    BackHandler(enabled = hasUnsavedChanges) {
        showUnsavedDialog = true
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved settings. Leave without saving?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    // navController.popBackStack() would go here
                }) { Text("Leave") }
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
                    maxPowerEnabled = true
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { showMaxPowerWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = hasUnsavedChanges,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Unsaved changes", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = {
                                playHaptic(doublePulse = true)
                                scope.launch {
                                    savingSettings = true
                                    val normalizedLevelType = normalizeWorldType(levelType)
                                    val nextConfig = config.copy(
                                        viewDistance = config.viewDistance.coerceAtMost(if (maxPowerEnabled) 32 else 16),
                                        simulationDistance = config.simulationDistance.coerceAtMost(if (maxPowerEnabled) 16 else 10),
                                        levelType = normalizedLevelType
                                    )
                                    val result = runCatching {
                                        stateHolder.saveSettings(nextConfig)
                                    }
                                    if (result.isSuccess) {
                                        withContext(Dispatchers.IO) {
                                            stateHolder.writeServerProperty("force-gamemode", forceGamemode.toString())
                                            stateHolder.writeServerProperty("broadcast-console-to-ops", broadcastConsoleToOps.toString())
                                            stateHolder.writeServerProperty("hide-online-players", hideOnlinePlayers.toString())
                                            stateHolder.applyOptimizationPreset(optimizationPreset)
                                        }
                                        preferences.autoRestart = autoRestartEnabled
                                        preferences.isMaxPowerMode = maxPowerEnabled
                                        
                                        config = nextConfig
                                        savedConfig = nextConfig
                                        savedForceGamemode = forceGamemode
                                        savedBroadcastConsoleToOps = broadcastConsoleToOps
                                        savedHideOnlinePlayers = hideOnlinePlayers
                                        savedLevelType = normalizedLevelType
                                        savedAutoRestart = autoRestartEnabled
                                        savedMaxPower = maxPowerEnabled
                                        savedOptimizationPreset = optimizationPreset
                                    }
                                    savingSettings = false
                                    onMessage(result.getOrElse { "Failed to save: ${it.message}" })
                                }
                            },
                            enabled = !savingSettings,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (savingSettings) "Saving..." else "Save")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
            stickyHeader {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 2.dp
                ) {
                    Column {
                        TabRow(
                            selectedTabIndex = activeTab,
                            containerColor = Color.Transparent,
                            contentColor = PocketColors.Primary,
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
                                    onClick = { playHaptic(); activeTab = index },
                                    text = { Text(title, fontWeight = if (activeTab == index) FontWeight.ExtraBold else FontWeight.Bold, fontSize = 13.sp) }
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                }
            }

            // --- TAB 0: SERVER ---
            if (activeTab == 0) {
                item { SettingsSection("PERFORMANCE & OPTIMIZATION", Icons.Default.Memory) }
                item {
                    SettingsSliderRow(
                        icon = Icons.Default.Visibility,
                        label = "View Distance",
                        description = "How far chunks are loaded",
                        hint = "Lower values recommended for low-end devices",
                        min = 3,
                        max = if (maxPowerEnabled) 32 else 16,
                        value = config.viewDistance,
                        onValueChange = { config = config.copy(viewDistance = it) }
                    )
                }
                item {
                    SettingsSliderRow(
                        icon = Icons.Default.Speed,
                        label = "Simulation Distance",
                        description = "Tick distance for mobs/crops",
                        min = 3,
                        max = if (maxPowerEnabled) 16 else 10,
                        value = config.simulationDistance,
                        onValueChange = { config = config.copy(simulationDistance = it) }
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = "⚡",
                        label = "Max Power Mode",
                        description = "Uses full RAM and max render distance. May cause overheating.",
                        checked = maxPowerEnabled,
                        onToggle = { 
                            if (it) {
                                showMaxPowerWarning = true
                            } else {
                                maxPowerEnabled = false
                            }
                            playHaptic() 
                        }
                    )
                }
                item {
                    SettingsDropdownRow(
                        icon = Icons.Default.AutoGraph,
                        label = "Mob Spawning Optimization",
                        description = "Reduces entity count for better performance",
                        options = listOf("none", "lite", "balanced", "performance"),
                        optionLabels = mapOf(
                            "none" to "Standard (Vanilla)",
                            "lite" to "Lite (Recommended)",
                            "balanced" to "Balanced",
                            "performance" to "Aggressive"
                        ),
                        selected = optimizationPreset,
                        onSelected = { optimizationPreset = it }
                    )
                }

                item { SettingsSection("WORLD SETTINGS", Icons.Default.Public) }
                item {
                    val currentLevelType = sanitizeLevelType(levelType)
                    SettingsDropdownRow(
                        icon = Icons.Default.Terrain,
                        label = "World Type",
                        options = levelTypeOptions.map { it.propertyValue },
                        optionLabels = levelTypeOptions.associate { it.propertyValue to it.displayName },
                        selected = currentLevelType,
                        onSelected = { levelType = it }
                    )
                }
                item {
                    SettingsDropdownRow(
                        icon = Icons.Default.SignalCellularAlt,
                        label = "Difficulty",
                        options = listOf("peaceful", "easy", "normal", "hard"),
                        selected = config.difficulty,
                        onSelected = { config = config.copy(difficulty = it) }
                    )
                }
                item {
                    SettingsDropdownRow(
                        icon = Icons.Default.VideogameAsset,
                        label = "Game Mode",
                        options = listOf("survival", "creative", "adventure", "spectator"),
                        selected = config.gameMode,
                        onSelected = { config = config.copy(gameMode = it) }
                    )
                }
                item {
                    SettingsSliderRow(
                        icon = Icons.Default.Height,
                        label = "Max Build Height",
                        min = 64, max = 320, step = 16,
                        value = config.maxBuildHeight,
                        onValueChange = { config = config.copy(maxBuildHeight = it) }
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = "💀",
                        label = "Hardcore Mode",
                        description = "Players are banned upon death",
                        checked = config.hardcore,
                        onToggle = { config = config.copy(hardcore = it) }
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = "🔥",
                        label = "Nether Enabled",
                        checked = config.netherEnabled,
                        onToggle = { config = config.copy(netherEnabled = it) }
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = "👻",
                        label = "Spawn Monsters",
                        checked = config.spawnMonsters,
                        onToggle = { config = config.copy(spawnMonsters = it) }
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = "🐷",
                        label = "Spawn Animals",
                        checked = config.spawnAnimals,
                        onToggle = { config = config.copy(spawnAnimals = it) }
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = "🧑‍🌾",
                        label = "Spawn NPCs (Villagers)",
                        checked = config.spawnNpcs,
                        onToggle = { config = config.copy(spawnNpcs = it) }
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = "📜",
                        label = "Whitelist",
                        description = "Only allowed players can join",
                        checked = config.whiteList,
                        onToggle = { config = config.copy(whiteList = it) }
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = "🚫",
                        label = "Enforce Whitelist",
                        description = "Kick players not on whitelist upon reload",
                        checked = config.enforceWhitelist,
                        onToggle = { config = config.copy(enforceWhitelist = it) }
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = "🏠",
                        label = "Generate Structures",
                        checked = config.generateStructures,
                        onToggle = { config = config.copy(generateStructures = it) }
                    )
                }

                item { SettingsSection("NETWORKING", Icons.Default.VpnLock) }
                item {
                    val relayOptions = mapOf(
                        "play.pocketcraft.online" to "Global (Standard)",
                        "mine.pocketcraft.online" to "Asia (India/Mumbai)"
                    )
                    SettingsDropdownRow(
                        icon = Icons.Default.Router,
                        label = "Relay Server",
                        description = "Closest location for best ping",
                        options = relayOptions.keys.toList(),
                        optionLabels = relayOptions,
                        selected = stateHolder.relayHost,
                        onSelected = { host -> scope.launch { onMessage(stateHolder.updateRelayHost(host)) } }
                    )
                }

                item { SettingsSection("EXTRA SERVER OPTIONS", Icons.Default.Settings) }
                item {
                    SettingsSliderRow(
                        icon = Icons.Default.Groups,
                        label = "Max Players",
                        min = 1, max = 20,
                        value = config.maxPlayers,
                        onValueChange = { config = config.copy(maxPlayers = it) }
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = "⚔️",
                        label = "Player vs Player (PVP)",
                        checked = config.pvp,
                        onToggle = { config = config.copy(pvp = it) }
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = "✈️",
                        label = "Allow Flight",
                        checked = config.allowFlight,
                        onToggle = { config = config.copy(allowFlight = it) }
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = "⚙️",
                        label = "Command Blocks",
                        checked = config.commandBlocks,
                        onToggle = { config = config.copy(commandBlocks = it) }
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = "📣",
                        label = "Broadcast Console To Ops",
                        checked = broadcastConsoleToOps,
                        onToggle = { broadcastConsoleToOps = it }
                    )
                }

                item { SettingsSection("ADVANCED SETTINGS", Icons.Default.Tune) }
                item {
                    SettingsSliderRow(
                        icon = Icons.Default.Shield,
                        label = "Spawn Protection",
                        description = "Radius of protected blocks at spawn",
                        min = 0, max = 100,
                        value = config.spawnProtection,
                        onValueChange = { config = config.copy(spawnProtection = it) }
                    )
                }
                item {
                    SettingsSliderRow(
                        icon = Icons.Default.Timer,
                        label = "Player Idle Timeout",
                        description = "Minutes before kicking idle players",
                        min = 0, max = 120,
                        value = config.playerIdleTimeout,
                        onValueChange = { config = config.copy(playerIdleTimeout = it) }
                    )
                }
                item {
                    SettingsSliderRow(
                        icon = Icons.Default.Person,
                        label = "Entity Broadcast Range",
                        description = "How far entities are visible (%)",
                        min = 10, max = 100, step = 10,
                        value = config.entityBroadcastRangePercentage,
                        onValueChange = { config = config.copy(entityBroadcastRangePercentage = it) }
                    )
                }
                item {
                    SettingsDropdownRow(
                        icon = Icons.Default.AdminPanelSettings,
                        label = "Op Permission Level",
                        options = listOf("1", "2", "3", "4"),
                        optionLabels = mapOf("1" to "Level 1 (Bypass)", "2" to "Level 2 (Commands)", "3" to "Level 3 (Management)", "4" to "Level 4 (Owner)"),
                        selected = config.opPermissionLevel.toString(),
                        onSelected = { config = config.copy(opPermissionLevel = it.toInt()) }
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = "🔒",
                        label = "Online Mode",
                        description = "Verify players with Mojang (Auth)",
                        checked = config.onlineMode,
                        onToggle = { config = config.copy(onlineMode = it) }
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = "🌐",
                        label = "Native Transport",
                        description = "Optimized Linux networking",
                        checked = config.useNativeTransport,
                        onToggle = { config = config.copy(useNativeTransport = it) }
                    )
                }
            }

            // --- TAB 1: APP ---
            if (activeTab == 1) {
                item { SettingsSection("APP PREFERENCES", Icons.Default.Tune) }
                item {
                    var isDark by remember { mutableStateOf(ThemePreferenceStore.load(context) == ThemePreference.DARK) }
                    SettingsToggleRow(
                        icon = "🌚",
                        label = "Dark Theme",
                        description = "Force dark mode for the app",
                        checked = isDark,
                        onToggle = { enabled ->
                            isDark = enabled
                            onDarkThemeChange(enabled)
                            playHaptic()
                        }
                    )
                }
                item {
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
                item {
                    SettingsToggleRow(
                        icon = "🔄",
                        label = "Auto-Restart",
                        description = "Restart server automatically if it crashes",
                        checked = autoRestartEnabled,
                        onToggle = { autoRestartEnabled = it; playHaptic() }
                    )
                }

                item { SettingsSection("DEVICE STORAGE", Icons.Default.Storage) }
                item {
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

            // --- TAB 2: ABOUT ---
            if (activeTab == 2) {
                item { SettingsSection("FIND US ONLINE", Icons.Default.Share) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Discord
                        GameCard(modifier = Modifier.weight(1f).clickable {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/nc7ceYWVfT"))) }
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

                item { SettingsSection("FEEDBACK & COMMUNITY", Icons.Default.Forum) }
                item {
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
                                        delay(1000)
                                        feedbackText = ""
                                        submittingFeedback = false
                                        onMessage("Feedback sent! Thank you.")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item { SettingsSection("HELP & LINKS", Icons.Default.Help) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsLinkRow(icon = Icons.Default.Translate, label = "App Language", description = "Current: English (US)", onClick = { onMessage("More languages coming soon!") })
                        SettingsLinkRow(icon = Icons.Default.Policy, label = "Legal Center", description = "Privacy Policy & Licenses", onClick = onOpenLegalPage)
                        SettingsLinkRow(icon = Icons.Default.BugReport, label = "Report a Bug", description = "Report on our Discord server", onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/nc7ceYWVfT"))) }
                                .onFailure { onMessage("Could not open Discord link.") }
                        })
                    }
                }

                item { SettingsSection("ABOUT", Icons.Default.Info) }
                item {
                    GameCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                )
                                Column {
                                    Text("PocketCraft", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, fontFamily = Monocraft)
                                    Text("Version ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("Run full Minecraft Java Edition servers directly on your Android device.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            Text("Made with ❤️ by the PocketCraft Team", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PocketColors.PrimaryDark)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

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
fun SettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        modifier = Modifier.padding(top = 20.dp, start = 16.dp, bottom = 6.dp),
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
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
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
    icon: String,
    label: String,
    description: String? = null,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    GameCard(modifier = Modifier.fillMaxWidth()) {
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
                FlatEmojiIcon(icon, modifier = Modifier.size(24.dp), tint = PocketColors.PrimaryDark)
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
            DuoToggle(checked = checked, onCheckedChange = onToggle)
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
                val displayValue = optionLabels[selected] ?: selected.replaceFirstChar { it.uppercase() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .clickable(enabled = enabled) { expanded = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayValue,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = expanded && enabled,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    text = optionLabels[option] ?: option,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                ) 
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
                onValueChange = { internalValue = it.toInt().coerceIn(min, max) },
                onValueChangeFinished = { onValueChange(internalValue) },
                valueRange = min.toFloat()..max.toFloat(),
                enabled = enabled
            )
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
