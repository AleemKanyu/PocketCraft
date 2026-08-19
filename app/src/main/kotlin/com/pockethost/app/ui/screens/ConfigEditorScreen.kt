package com.pockethost.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.data.model.ServerConfig
import com.pockethost.app.data.repository.ServerConfigRepository
import com.pockethost.app.service.ServerPropertiesHelper
import java.io.File
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConfigEditorScreen(
    serverDir: File,
    isReadOnly: Boolean,
    onClose: () -> Unit,
    onOpenFileEditor: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val props = remember(serverDir.absolutePath) { mutableStateMapOf<String, String>() }
    var isLoaded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(serverDir.absolutePath) {
        runCatching {
            val loaded = ServerPropertiesHelper.readProperties(serverDir)
            props.clear()
            loaded.forEach { key, value ->
                val keyText = key.toString()
                props[keyText] = when (keyText) {
                    "level-type" -> sanitizeLevelType(value.toString())
                    "motd" -> value.toString().removeSuffix(" - Hosted on Pocketcraft").trim()
                    else -> value.toString()
                }
            }
            isLoaded = true
            loadError = null
        }.onFailure { error ->
            loadError = error.message ?: "Unable to load server.properties"
            isLoaded = true
        }
    }

    val orderedKeys = remember(props.keys.toList()) {
        val sections = listOf(
            sectionKeysGeneral,
            sectionKeysWorld,
            sectionKeysPerformance,
            sectionKeysPlayers,
            sectionKeysNetwork
        )
        val known = sections.flatten().toSet()
        sections.flatten().filter { props.containsKey(it) } + props.keys.filterNot { it in known }.sorted()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("server.properties", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Text(
                        text = if (isReadOnly) {
                            "Stop the server to edit config."
                        } else {
                            "Changes take effect on the next server restart."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (loadError != null) {
                AlertDialog(
                    onDismissRequest = onClose,
                    confirmButton = {
                        Button(onClick = onClose) {
                            Text("Close")
                        }
                    },
                    title = { Text("Config load failed") },
                    text = { Text(loadError.orEmpty()) }
                )
            }

            if (!isLoaded) {
                Text("Loading config...")
            } else {
                if (isReadOnly) {
                    Text(
                        text = "Read-only mode: stop the server to make changes.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(sectionedRows(orderedKeys)) { section ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = section.title,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                letterSpacing = 0.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            section.keys.forEach { key ->
                                ConfigRow(
                                    key = key,
                                    value = props[key].orEmpty(),
                                    enabled = !isReadOnly,
                                    onValueChange = { props[key] = it }
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.navigationBarsPadding().height(80.dp))
                    }
                }

                Button(
                    onClick = {
                        if (isReadOnly) return@Button
                        isSaving = true
                        scope.launch {
                            val saved = withContext(Dispatchers.IO) {
                                val toSave = Properties().apply {
                                    props.forEach { (key, value) ->
                                        setProperty(key, if (key == "level-type") sanitizeLevelType(value) else value)
                                    }
                                }
                                serverDir.mkdirs()
                                serverDir.resolve("server.properties").outputStream().use { out ->
                                    toSave.store(out, "Edited via PocketCraft")
                                }

                                val currentConfig = runCatching {
                                    ServerConfigRepository(context).loadConfig()
                                }.getOrNull() ?: ServerConfig()
                                val mergedConfig = currentConfig.copy(
                                    worldName = toSave.getProperty("level-name", currentConfig.worldName),
                                    worldSeed = toSave.getProperty("level-seed", currentConfig.worldSeed),
                                    maxPlayers = toSave.getProperty("max-players", currentConfig.maxPlayers.toString()).toIntOrNull()
                                        ?: currentConfig.maxPlayers,
                                    difficulty = toSave.getProperty("difficulty", currentConfig.difficulty),
                                    gameMode = toSave.getProperty("gamemode", currentConfig.gameMode),
                                    onlineMode = toSave.getProperty("online-mode", currentConfig.onlineMode.toString()).toBoolean(),
                                    motd = toSave.getProperty("motd", currentConfig.motd),
                                    pvp = toSave.getProperty("pvp", currentConfig.pvp.toString()).toBoolean(),
                                    viewDistance = toSave.getProperty("view-distance", currentConfig.viewDistance.toString()).toIntOrNull()
                                        ?: currentConfig.viewDistance,
                                    simulationDistance = toSave.getProperty("simulation-distance", currentConfig.simulationDistance.toString()).toIntOrNull()
                                        ?: currentConfig.simulationDistance,
                                    spawnProtection = toSave.getProperty("spawn-protection", currentConfig.spawnProtection.toString()).toIntOrNull()
                                        ?: currentConfig.spawnProtection,
                                    allowFlight = toSave.getProperty("allow-flight", currentConfig.allowFlight.toString()).toBoolean(),
                                    whiteList = toSave.getProperty("white-list", currentConfig.whiteList.toString()).toBoolean(),
                                    enforceWhitelist = toSave.getProperty("enforce-whitelist", currentConfig.enforceWhitelist.toString()).toBoolean(),
                                    commandBlocks = toSave.getProperty("enable-command-block", currentConfig.commandBlocks.toString()).toBoolean(),
                                    netherEnabled = toSave.getProperty("allow-nether", currentConfig.netherEnabled.toString()).toBoolean(),
                                    spawnMonsters = toSave.getProperty("spawn-monsters", currentConfig.spawnMonsters.toString()).toBoolean(),
                                    spawnAnimals = toSave.getProperty("spawn-animals", currentConfig.spawnAnimals.toString()).toBoolean(),
                                    spawnNpcs = toSave.getProperty("spawn-npcs", currentConfig.spawnNpcs.toString()).toBoolean(),
                                    hardcore = toSave.getProperty("hardcore", currentConfig.hardcore.toString()).toBoolean(),
                                    maxRamMb = toSave.getProperty("pocketcraft-max-ram-mb", currentConfig.maxRamMb.toString()).toIntOrNull()
                                        ?: currentConfig.maxRamMb,
                                    entityBroadcastRangePercentage = toSave.getProperty(
                                        "entity-broadcast-range-percentage",
                                        currentConfig.entityBroadcastRangePercentage.toString()
                                    ).toIntOrNull() ?: currentConfig.entityBroadcastRangePercentage,
                                    maxWorldSize = toSave.getProperty("max-world-size", currentConfig.maxWorldSize.toString()).toIntOrNull()
                                        ?: currentConfig.maxWorldSize,
                                    useNativeTransport = toSave.getProperty("use-native-transport", currentConfig.useNativeTransport.toString()).toBoolean(),
                                    maxBuildHeight = toSave.getProperty("max-build-height", currentConfig.maxBuildHeight.toString()).toIntOrNull()
                                        ?: currentConfig.maxBuildHeight,
                                    serverType = currentConfig.serverType,
                                    gameVersion = currentConfig.gameVersion,
                                    customJarPath = currentConfig.customJarPath
                                )
                                runCatching {
                                    ServerConfigRepository(context).saveConfig(mergedConfig)
                                }
                                true
                            }
                            isSaving = false
                            if (saved) onClose()
                        }
                    },
                    enabled = !isReadOnly && !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save - Restart server to apply", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("Close")
                }

                Button(
                    onClick = onOpenFileEditor,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("Advanced: Open Full File Editor")
                }

                Spacer(modifier = Modifier.navigationBarsPadding().height(80.dp))
            }
        }
    }
}

private fun sanitizeLevelType(raw: String): String {
    return raw.trim()
        .replace("\\\\", ":")
        .replace("\\", ":")
        .replace("minecraft:minecraft:", "minecraft:")
}

@Composable
private fun ConfigRow(
    key: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = key,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )
    }
}

private data class ConfigSection(val title: String, val keys: List<String>)

private fun sectionedRows(keys: List<String>): List<ConfigSection> {
    val lookup = keys.toSet()
    return listOf(
        ConfigSection("GENERAL", sectionKeysGeneral.filter { it in lookup }),
        ConfigSection("WORLD", sectionKeysWorld.filter { it in lookup }),
        ConfigSection("PERFORMANCE", sectionKeysPerformance.filter { it in lookup }),
        ConfigSection("PLAYERS", sectionKeysPlayers.filter { it in lookup }),
        ConfigSection("NETWORK", sectionKeysNetwork.filter { it in lookup }),
        ConfigSection("OTHER", keys.filterNot {
            it in sectionKeysGeneral ||
                it in sectionKeysWorld ||
                it in sectionKeysPerformance ||
                it in sectionKeysPlayers ||
                it in sectionKeysNetwork
        })
    ).filter { it.keys.isNotEmpty() }
}

private val sectionKeysGeneral = listOf(
    "server-ip",
    "server-port",
    "motd",
    "difficulty",
    "gamemode",
    "online-mode",
    "pvp",
    "allow-flight",
    "enable-command-block",
    "white-list",
    "enforce-whitelist",
    "hardcore"
)

private val sectionKeysWorld = listOf(
    "level-name",
    "level-seed",
    "level-type",
    "generate-structures",
    "allow-nether",
    "spawn-protection",
    "max-world-size"
)

private val sectionKeysPerformance = listOf(
    "view-distance",
    "simulation-distance",
    "entity-broadcast-range-percentage",
    "network-compression-threshold",
    "max-tick-time",
    "sync-chunk-writes",
    "use-native-transport"
)

private val sectionKeysPlayers = listOf(
    "max-players",
    "spawn-monsters",
    "spawn-animals",
    "spawn-npcs"
)

private val sectionKeysNetwork = listOf(
    "resource-pack",
    "require-resource-pack",
    "resource-pack-sha1",
    "resource-pack-prompt",
    "enable-rcon",
    "rcon.port",
    "rcon.password",
    "broadcast-rcon-to-ops",
    "query.port",
    "prevent-proxy-connections"
)
