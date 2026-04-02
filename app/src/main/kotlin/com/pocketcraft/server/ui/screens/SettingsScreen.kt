package com.pocketcraft.server.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.data.model.ServerConfig
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.components.DuoToggle
import com.pocketcraft.server.ui.components.GameCard
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.util.RamUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val WorldTypeLabels = linkedMapOf(
    "minecraft:normal" to "🌍 Default",
    "minecraft:flat" to "🟩 Flat",
    "minecraft:large_biomes" to "🌄 Large Biomes",
    "minecraft:amplified" to "⛰ Amplified"
)

@Composable
fun SettingsScreen(
    stateHolder: ServerStateHolder,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var config by remember(stateHolder.config) { mutableStateOf(stateHolder.config) }
    var forceGamemode by remember { mutableStateOf(false) }
    var broadcastConsoleToOps by remember { mutableStateOf(false) }
    var hideOnlinePlayers by remember { mutableStateOf(false) }
    var levelType by remember { mutableStateOf("minecraft:normal") }
    var installedVersions by remember { mutableStateOf<List<InstalledVersionInfo>>(emptyList()) }
    var selectedDeleteVersions by remember { mutableStateOf(setOf<String>()) }
    var deletingVersions by remember { mutableStateOf(false) }
    val totalRamMb = remember { RamUtils.getTotalRamMb(context) }
    val recommendedViewDistance = remember(totalRamMb) {
        if (totalRamMb >= 7168) 32 else if (totalRamMb >= 6144) 20 else if (totalRamMb >= 4096) 12 else 8
    }
    val recommendedSimulationDistance = remember(totalRamMb) {
        if (totalRamMb >= 7168) 32 else if (totalRamMb >= 6144) 14 else if (totalRamMb >= 4096) 10 else 6
    }

    LaunchedEffect(stateHolder.versionLabel) {
        forceGamemode = stateHolder.readServerProperty("force-gamemode")?.toBoolean() ?: false
        broadcastConsoleToOps = stateHolder.readServerProperty("broadcast-console-to-ops")?.toBoolean() ?: false
        hideOnlinePlayers = stateHolder.readServerProperty("hide-online-players")?.toBoolean() ?: false
        levelType = normalizeWorldType(stateHolder.readServerProperty("level-type"))
        installedVersions = withContext(Dispatchers.IO) {
            scanInstalledVersions(context)
        }
        selectedDeleteVersions = emptySet()
    }

    LaunchedEffect(
        config,
        forceGamemode,
        broadcastConsoleToOps,
        hideOnlinePlayers,
        levelType
    ) {
        delay(350)
        stateHolder.writeServerProperty("max-players", config.maxPlayers.toString())
        stateHolder.writeServerProperty("gamemode", config.gameMode)
        stateHolder.writeServerProperty("difficulty", config.difficulty)
        stateHolder.writeServerProperty("white-list", config.whiteList.toString())
        stateHolder.writeServerProperty("enforce-whitelist", config.enforceWhitelist.toString())
        stateHolder.writeServerProperty("pvp", config.pvp.toString())
        stateHolder.writeServerProperty("enable-command-block", config.commandBlocks.toString())
        stateHolder.writeServerProperty("allow-flight", config.allowFlight.toString())
        stateHolder.writeServerProperty("spawn-monsters", config.spawnMonsters.toString())
        stateHolder.writeServerProperty("allow-nether", config.netherEnabled.toString())
        stateHolder.writeServerProperty("force-gamemode", forceGamemode.toString())
        stateHolder.writeServerProperty("hardcore", config.hardcore.toString())
        stateHolder.writeServerProperty("view-distance", config.viewDistance.toString())
        stateHolder.writeServerProperty("simulation-distance", config.simulationDistance.toString())
        stateHolder.writeServerProperty("spawn-protection", config.spawnProtection.toString())
        stateHolder.writeServerProperty("level-name", config.worldName)
        stateHolder.writeServerProperty("level-seed", config.worldSeed)
        stateHolder.writeServerProperty("level-type", normalizeWorldType(levelType))
        stateHolder.writeServerProperty("generate-structures", config.generateStructures.toString())
        stateHolder.writeServerProperty("broadcast-console-to-ops", broadcastConsoleToOps.toString())
        stateHolder.writeServerProperty("hide-online-players", hideOnlinePlayers.toString())
        stateHolder.saveSettings(config) // Save silently without showing toast
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Changes take effect after server restart.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
        item { SettingsSection("PERFORMANCE & MEMORY") }
        item {
            SettingsSliderRow(
                label = "View Distance",
                description = "How far chunks are loaded for players",
                min = 2,
                max = 32,
                value = config.viewDistance,
                enabled = true,
                onValueChange = { config = config.copy(viewDistance = it) }
            )
        }
        item {
            SettingsSliderRow(
                label = "Simulation Distance",
                description = "How far away crops grow and mobs move",
                min = 2,
                max = 32,
                value = config.simulationDistance,
                enabled = true,
                onValueChange = { config = config.copy(simulationDistance = it) }
            )
        }
        item {
            Text(
                text = "Recommended default for this phone (${totalRamMb / 1024} GB): view=$recommendedViewDistance, simulation=$recommendedSimulationDistance.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        item { SettingsSection("WORLD SETTINGS") }
        item {
            SettingsDropdownRow(
                label = "Difficulty",
                description = "Overall game difficulty level",
                options = listOf("peaceful", "easy", "normal", "hard"),
                selected = config.difficulty,
                onSelected = { config = config.copy(difficulty = it) }
            )
        }
        item {
            SettingsDropdownRow(
                label = "Gamemode",
                description = "Default game mode for new players",
                options = listOf("survival", "creative", "adventure", "spectator"),
                selected = config.gameMode,
                onSelected = { config = config.copy(gameMode = it) }
            )
        }
        item {
            SettingsToggleRow(
                icon = "🏠",
                label = "Generate Structures",
                description = "Villages, dungeons, and monuments",
                checked = config.generateStructures,
                onToggle = { config = config.copy(generateStructures = it) }
            )
        }
        item {
            SettingsToggleRow(
                icon = "🌑",
                label = "Allow Nether",
                description = "Enable access to the Nether dimension",
                checked = config.netherEnabled,
                onToggle = { config = config.copy(netherEnabled = it) }
            )
        }
        item {
            SettingsToggleRow(
                icon = "🔥",
                label = "Hardcore Mode",
                description = "Permanent death for all players",
                checked = config.hardcore,
                onToggle = { config = config.copy(hardcore = it) }
            )
        }
        item {
            SettingsToggleRow(
                icon = "📋",
                label = "Whitelist",
                description = "Only allow approved players to join",
                checked = config.whiteList,
                onToggle = { config = config.copy(whiteList = it) }
            )
        }

        item { SettingsSection("NETWORKING") }
        item {
            SettingsInputRow(
                label = "Server Port",
                description = "Fixed per device to prevent multiple-port hosting",
                value = config.port.toString(),
                onValueChange = {},
                readOnly = true
            )
        }
        item {
            val relayOptions = mapOf(
                "play.pocketcraft.online" to "🌐 Global",
                "mine.pocketcraft.online" to "🌏 Asia"
            )
            SettingsDropdownRow(
                label = "Relay Server Location",
                description = "Choose the closest relay for lower ping.",
                options = relayOptions.keys.toList(),
                optionLabels = relayOptions,
                selected = stateHolder.relayHost,
                enabled = !stateHolder.isNavigationLocked,
                onSelected = { host ->
                    scope.launch {
                        onMessage(stateHolder.updateRelayHost(host))
                    }
                }
            )

            if (stateHolder.isNavigationLocked) {
                Text(
                    text = "Stop the server before changing relay location.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (stateHolder.relayFallbackActive && stateHolder.relayHost.contains("mine")) {
                Text(
                    text = "⚠️ India relay is currently offline. Using Global (Singapore) relay as a fallback for connectivity.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
        item {
            Text(
                text = "More relay locations will be available soon!",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        item { SettingsSection("ENTITY SETTINGS") }
        item {
            SettingsToggleRow(
                icon = "👿",
                label = "Spawn Monsters",
                description = "Zombies, skeletons, and creepers",
                checked = config.spawnMonsters,
                onToggle = { config = config.copy(spawnMonsters = it) }
            )
        }
        item {
            SettingsToggleRow(
                icon = "🐄",
                label = "Spawn Animals",
                description = "Cows, pigs, and chickens",
                checked = config.spawnAnimals,
                onToggle = { config = config.copy(spawnAnimals = it) }
            )
        }
        item {
            SettingsToggleRow(
                icon = "🏙️",
                label = "Spawn NPCs",
                description = "Villagers and Golems",
                checked = config.spawnNpcs,
                onToggle = { config = config.copy(spawnNpcs = it) }
            )
        }
        item {
            SettingsToggleRow(
                icon = "⚔️",
                label = "PVP",
                description = "Allow players to damage each other",
                checked = config.pvp,
                onToggle = { config = config.copy(pvp = it) }
            )
        }

        item { SettingsSection("APP PREFERENCES") }
        item {
            SettingsToggleRow(
                icon = "🔄",
                label = "Auto-Restart",
                description = "Restart server automatically if it crashes",
                checked = config.autoRestart,
                onToggle = { config = config.copy(autoRestart = it) }
            )
        }

        item {
            SettingsSection("DEVICE STORAGE")
        }
        item {
            GameCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Delete downloaded versions",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Remove unused server versions to free storage. Current version is locked.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )

                    if (installedVersions.isEmpty()) {
                        Text(
                            text = "No downloaded versions found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    } else {
                        installedVersions.forEach { item ->
                            val checked = selectedDeleteVersions.contains(item.id)
                            val canDelete = item.id != stateHolder.versionLabel
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = canDelete) {
                                        selectedDeleteVersions = if (checked) {
                                            selectedDeleteVersions - item.id
                                        } else {
                                            selectedDeleteVersions + item.id
                                        }
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = {
                                            selectedDeleteVersions = if (it) {
                                                selectedDeleteVersions + item.id
                                            } else {
                                                selectedDeleteVersions - item.id
                                            }
                                        },
                                        enabled = canDelete
                                    )
                                    Column {
                                        Text(
                                            text = "Minecraft ${item.id}",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = if (canDelete) item.sizeLabel else "In use (${item.sizeLabel})",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    DuoButton(
                        text = if (deletingVersions) "DELETING..." else "DELETE SELECTED",
                        enabled = !deletingVersions && selectedDeleteVersions.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                deletingVersions = true
                                val deletedCount = withContext(Dispatchers.IO) {
                                    deleteInstalledVersions(context, stateHolder.versionLabel, selectedDeleteVersions)
                                }
                                installedVersions = withContext(Dispatchers.IO) {
                                    scanInstalledVersions(context)
                                }
                                selectedDeleteVersions = emptySet()
                                deletingVersions = false
                                onMessage("Deleted $deletedCount version(s) from device storage.")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            SettingsSection("EXTRA SERVER OPTIONS")
        }
        item {
            SettingsSliderRow(
                label = "Max Players",
                description = "Maximum simultaneous players",
                min = 1,
                max = 100,
                value = config.maxPlayers,
                onValueChange = { config = config.copy(maxPlayers = it) }
            )
        }
        item {
            if (config.maxPlayers > 50) {
                Text(
                    text = "⚠️ High player count may increase device temperature and battery drain during extended server usage.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF9800),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 10.sp
                )
            }
        }
        item {
            SettingsInputRow(
                label = "Seed",
                description = "server.properties: level-seed",
                value = config.worldSeed,
                onValueChange = { config = config.copy(worldSeed = it) }
            )
        }
        item {
            SettingsDropdownRow(
                label = "World Type",
                description = "server.properties: level-type",
                options = listOf("minecraft:normal", "minecraft:flat", "minecraft:large_biomes", "minecraft:amplified"),
                optionLabels = WorldTypeLabels,
                selected = normalizeWorldType(levelType),
                onSelected = { levelType = normalizeWorldType(it) }
            )
        }
        item {
            SettingsToggleRow(
                icon = "🕹️",
                label = "Force Gamemode",
                description = "Force selected gamemode on join",
                checked = forceGamemode,
                onToggle = { forceGamemode = it }
            )
        }
        item {
            SettingsToggleRow(
                icon = "📣",
                label = "Broadcast Console To Ops",
                description = "Send console output to operators",
                checked = broadcastConsoleToOps,
                onToggle = { broadcastConsoleToOps = it }
            )
        }
        item {
            SettingsToggleRow(
                icon = "🙈",
                label = "Hide Online Players",
                description = "Hide player count in ping response",
                checked = hideOnlinePlayers,
                onToggle = { hideOnlinePlayers = it }
            )
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun SettingsSection(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, start = 4.dp, bottom = 4.dp)
    )
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
                Text(text = icon, fontSize = 24.sp)
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
private fun SettingsInputRow(
    label: String,
    description: String? = null,
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean = false,
    enabled: Boolean = true
) {
    GameCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            description?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                    maxLines = 2
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
        }
    }
}

@Composable
private fun SettingsDropdownRow(
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
        Column {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
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
                val normalizedSelected = selected.trim().replace("\\:", ":")
                val displayValue = optionLabels[normalizedSelected]
                    ?: optionLabels[selected]
                    ?: normalizedSelected.replaceFirstChar { it.uppercase() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
                        .border(
                            2.dp,
                            if (enabled) PocketColors.Primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            RoundedCornerShape(18.dp)
                        )
                        .clickable(enabled = enabled) { expanded = true }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayValue,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(PocketColors.PrimaryMuted, RoundedCornerShape(10.dp))
                            .border(1.5.dp, PocketColors.Primary.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = PocketColors.PrimaryDark
                        )
                    }
                }
                DropdownMenu(
                    expanded = expanded && enabled,
                    onDismissRequest = { expanded = false },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    shadowElevation = 10.dp
                ) {
                    options.forEach { option ->
                        val optionLabel = optionLabels[option] ?: option.replaceFirstChar { it.uppercase() }
                        val isSelected = option == normalizedSelected || option == selected
                        DropdownMenuItem(
                            text = {
                                Text(
                                    optionLabel,
                                    maxLines = 1,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) PocketColors.PrimaryDark else MaterialTheme.colorScheme.onSurface
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

private fun normalizeWorldType(value: String?): String {
    return when (value.orEmpty().trim().replace("\\:", ":").lowercase()) {
        "", "default", "normal", "minecraft:normal" -> "minecraft:normal"
        "flat", "minecraft:flat" -> "minecraft:flat"
        "largebiomes", "large_biomes", "minecraft:large_biomes" -> "minecraft:large_biomes"
        "amplified", "minecraft:amplified" -> "minecraft:amplified"
        else -> value.orEmpty().trim().replace("\\:", ":")
    }
}

@Composable
private fun SettingsSliderRow(
    label: String,
    description: String? = null,
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
                Column(modifier = Modifier.weight(1f)) {
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
                Text(
                    text = internalValue.toString(),
                    color = PocketColors.PrimaryDark,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Slider(
                value = internalValue.toFloat(),
                onValueChange = {
                    val snapped = if (step <= 1) {
                        it.toInt()
                    } else {
                        (it.toInt() / step) * step
                    }.coerceIn(min, max)
                    internalValue = snapped
                },
                onValueChangeFinished = {
                    onValueChange(internalValue)
                },
                valueRange = min.toFloat()..max.toFloat(),
                enabled = enabled,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private data class InstalledVersionInfo(
    val id: String,
    val sizeLabel: String
)

private fun scanInstalledVersions(context: android.content.Context): List<InstalledVersionInfo> {
    val root = File(context.filesDir, "servers")
    if (!root.exists() || !root.isDirectory) return emptyList()

    return root.listFiles().orEmpty()
        .filter { it.isDirectory }
        .mapNotNull { dir ->
            val version = dir.name
            val jar = File(dir, "paper-$version.jar")
            if (!jar.exists() || jar.length() <= 1_000_000L) return@mapNotNull null
            val sizeMb = (dir.walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() } / (1024L * 1024L)).coerceAtLeast(1L)
            InstalledVersionInfo(version, "$sizeMb MB")
        }
        .sortedByDescending { it.id }
}

private fun deleteInstalledVersions(
    context: android.content.Context,
    currentVersion: String,
    versions: Set<String>
): Int {
    val root = File(context.filesDir, "servers")
    if (!root.exists()) return 0

    var deleted = 0
    versions.forEach { version ->
        if (version == currentVersion) return@forEach
        val dir = File(root, version)
        if (!dir.exists()) return@forEach
        val worldName = runCatching {
            File(dir, "server.properties").readLines()
                .firstOrNull { it.startsWith("level-name=") }
                ?.substringAfter("=")
                ?.trim()
                ?.ifBlank { "world" }
                ?: "world"
        }.getOrDefault("world")
        val protectedDirs = setOf(
            worldName,
            "${worldName}_nether",
            "${worldName}_the_end",
            "pocketcraft_backups",
            "world_plugin_profiles"
        )

        var removedAny = false
        dir.listFiles().orEmpty().forEach { child ->
            val keep = child.isDirectory && protectedDirs.contains(child.name)
            if (!keep && child.deleteRecursively()) {
                removedAny = true
            }
        }
        if (removedAny) {
            deleted++
        }
    }
    return deleted
}
