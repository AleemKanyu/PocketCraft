package com.pocketcraft.server.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import com.pocketcraft.server.ui.components.PlayerActionButton
import com.pocketcraft.server.ui.components.PlayerActionType
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pocketcraft.server.data.model.PlayerInfo
import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject
import com.pocketcraft.server.service.NBTParser
import com.pocketcraft.server.service.PlayerDataManager
import com.pocketcraft.server.service.PlayerLocation
import com.pocketcraft.server.service.PlayerLiveSnapshot
import com.pocketcraft.server.service.InventoryItem
import com.pocketcraft.server.ui.components.DuoToggle
import com.pocketcraft.server.ui.components.HealthBar
import com.pocketcraft.server.ui.components.FlatEmojiIcon
import com.pocketcraft.server.ui.components.InventoryPreview
import com.pocketcraft.server.ui.theme.PocketColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pocketcraft.server.ui.components.PocketCraftCard

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState

// Sentinel removed — respawn is null until the server confirms real SpawnX/Y/Z values.

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlayerDetailScreen(
    stateHolder: ServerStateHolder,
    player: PlayerInfo,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var refreshTrigger by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var gamemode by remember { mutableStateOf("survival") }
    var gamemodeExpanded by remember { mutableStateOf(false) }
    var whitelisted by remember { mutableStateOf(false) }
    var banned by remember { mutableStateOf(false) }
    var op by remember { mutableStateOf(false) }
    var currentPos by remember { mutableStateOf<PlayerLocation?>(null) }
    var respawnPos by remember { mutableStateOf<PlayerLocation?>(null) }
    var lastDeathPos by remember { mutableStateOf<PlayerLocation?>(null) }
    var stats by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var health by remember { mutableStateOf<Float?>(null) }
    var hunger by remember { mutableStateOf<Int?>(null) }
    var worldSpawnPos by remember { mutableStateOf<PlayerLocation?>(null) }
    var resolvedOfflineUuid by remember { mutableStateOf(player.uuid) }
    val offlineUuid = if (resolvedOfflineUuid.isNotBlank()) resolvedOfflineUuid else player.uuid

    var delXp by remember { mutableStateOf(false) }
    var delEnder by remember { mutableStateOf(false) }
    var delPlayer by remember { mutableStateOf(false) }
    var delStats by remember { mutableStateOf(false) }
    var delAdv by remember { mutableStateOf(false) }
    var confirmDeleteData by remember { mutableStateOf(false) }

    val commandTarget = remember(player.name) { playerCommandTarget(player.name) }
    val latestLogLine = stateHolder.logs.lastOrNull()
    val isPlayerOnline = stateHolder.onlinePlayers.any { it.name.equals(player.name, ignoreCase = true) }

    fun syncModerationFlags() {
        scope.launch {
            delay(700)
            val statusSnapshot = withContext(Dispatchers.IO) {
                Triple(
                    PlayerDataManager.isWhitelisted(context, stateHolder.activeWorld, player.name),
                    PlayerDataManager.isBanned(context, stateHolder.activeWorld, player.name),
                    PlayerDataManager.isOp(context, stateHolder.activeWorld, player.name)
                )
            }
            whitelisted = statusSnapshot.first
            banned = statusSnapshot.second
            op = statusSnapshot.third
            stateHolder.refreshAll()
        }
    }

    LaunchedEffect(player.name, stateHolder.versionLabel) {
        val statusSnapshot = withContext(Dispatchers.IO) {
            Triple(
                PlayerDataManager.isWhitelisted(context, stateHolder.activeWorld, player.name),
                PlayerDataManager.isBanned(context, stateHolder.activeWorld, player.name),
                PlayerDataManager.isOp(context, stateHolder.activeWorld, player.name)
            )
        }
        whitelisted = statusSnapshot.first
        banned = statusSnapshot.second
        op = statusSnapshot.third
    }

    LaunchedEffect(player.name, player.uuid, stateHolder.versionLabel, stateHolder.activeWorld) {
        val resolvedUuid = withContext(Dispatchers.IO) {
            PlayerDataManager.resolveOfflineDataUuid(
                context = context,
                worldName = stateHolder.activeWorld,
                playerName = player.name,
                playerUuid = player.uuid
            )
        }
        resolvedOfflineUuid = resolvedUuid

        if (resolvedUuid.isNotBlank()) {
            val offlineSnapshot = withContext(Dispatchers.IO) {
                val datFile = PlayerDataManager.getPlayerDataFile(context, stateHolder.activeWorld, resolvedUuid)
                NBTParser.parsePlayerData(datFile)
            }

            val worldSpawn = withContext(Dispatchers.IO) {
                val levelFile = PlayerDataManager.getLevelDataFile(context, stateHolder.activeWorld)
                NBTParser.parseLevelData(levelFile)
            }

            offlineSnapshot?.let {
                currentPos = it.currentPos
                respawnPos = it.respawnPos ?: worldSpawn
                health = it.health ?: 20f
                hunger = it.hunger ?: 20
            } ?: run {
                respawnPos = worldSpawn
            }
            worldSpawnPos = worldSpawn

            stats = withContext(Dispatchers.IO) {
                PlayerDataManager.parseStats(
                    PlayerDataManager.getStatsFile(context, stateHolder.activeWorld, resolvedUuid)
                )
            }
        } else {
            stats = emptyMap()
        }
    }

    // Polling loop for live data
    LaunchedEffect(player.name, isPlayerOnline, stateHolder.status, refreshTrigger) {
        if (!isPlayerOnline || stateHolder.status != ServerStatus.ONLINE) return@LaunchedEffect
        while (isActive) {
            withContext(Dispatchers.IO) {
                try {
                    val rconCommands = listOf(
                        "data get entity $commandTarget Health",
                        "data get entity $commandTarget foodLevel",
                        "data get entity $commandTarget Pos",
                        "data get entity $commandTarget Dimension",
                        "data get entity $commandTarget SpawnSet",
                        "data get entity $commandTarget SpawnX",
                        "data get entity $commandTarget SpawnY",
                        "data get entity $commandTarget SpawnZ",
                        "data get entity $commandTarget SpawnDimension",
                        "data get entity $commandTarget LastDeathLocation"
                    )
                    val rconResponses = stateHolder.sendRconCommands(rconCommands)

                    val healthLine = rconResponses[0]
                    val foodLine = rconResponses[1]
                    val posLine = rconResponses[2]
                    val dimLine = rconResponses[3]
                    val spawnSetLine = rconResponses[4]
                    val spawnXLine = rconResponses[5]
                    val spawnYLine = rconResponses[6]
                    val spawnZLine = rconResponses[7]
                    val spawnDimLine = rconResponses[8]
                    val lastDeathLine = rconResponses[9]

                    val newHealth = NBTParser.parseDataFloatValue(healthLine) ?: NBTParser.parseFloatValue(healthLine)
                    val newHunger = NBTParser.parseDataIntValue(foodLine) ?: NBTParser.parseIntValue(foodLine)

                    val parsedPos = NBTParser.parsePosition(posLine)
                    val newCurrentPos = if (parsedPos != null) {
                        PlayerLocation(parsedPos.first, parsedPos.second, parsedPos.third, NBTParser.parseDimension(dimLine))
                    } else null

                    val spawnSet = NBTParser.parseDataIntValue(spawnSetLine) ?: 0
                    val respawnX = NBTParser.parseDataIntValue(spawnXLine)?.toDouble()
                    val respawnY = NBTParser.parseDataIntValue(spawnYLine)?.toDouble()
                    val respawnZ = NBTParser.parseDataIntValue(spawnZLine)?.toDouble()

                    val newRespawnPos = if (respawnX != null && respawnY != null && respawnZ != null) {
                        PlayerLocation(respawnX, respawnY, respawnZ, NBTParser.parseDimension(spawnDimLine))
                    } else null

                    val newDeathPos = NBTParser.parseLastDeathLocation(lastDeathLine)

                    withContext(Dispatchers.Main) {
                        newHealth?.let { health = it.coerceIn(0f, 20f) }
                        newHunger?.let { hunger = it.coerceIn(0, 20) }
                        newCurrentPos?.let { currentPos = it }
                        respawnPos = newRespawnPos ?: worldSpawnPos
                        newDeathPos?.let { lastDeathPos = it }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("INV", "RCON Error: ${e.message}")
                }
            }
            delay(8_000)
        }
    }

    LaunchedEffect(player.name, latestLogLine, isPlayerOnline, stateHolder.status) {
        if (!isPlayerOnline || stateHolder.status != ServerStatus.ONLINE) return@LaunchedEffect
        val latest = latestLogLine.orEmpty().lowercase()
        val normalizedName = canonicalPlayerName(player.name)
        val mentionsPlayer = latest.contains(player.name.lowercase()) || latest.contains(normalizedName)
        val spawnChanged =
            latest.contains("sleeping in bed") ||
                latest.contains("set own spawnpoint") ||
                latest.contains("spawn point set") ||
                latest.contains("respawn point set")
        
        if (!mentionsPlayer || (!spawnChanged && !isPlayerDeathLogFor(latest, player.name))) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            try {
                if (spawnChanged) {
                    val spawnSetLine = stateHolder.sendRconCommand("data get entity $commandTarget SpawnSet")
                    val spawnXLine = stateHolder.sendRconCommand("data get entity $commandTarget SpawnX")
                    val spawnYLine = stateHolder.sendRconCommand("data get entity $commandTarget SpawnY")
                    val spawnZLine = stateHolder.sendRconCommand("data get entity $commandTarget SpawnZ")
                    val spawnDimLine = stateHolder.sendRconCommand("data get entity $commandTarget SpawnDimension")
                    
                    val spawnSet = NBTParser.parseDataIntValue(spawnSetLine) ?: 0
                    val respawnX = NBTParser.parseDataIntValue(spawnXLine)?.toDouble()
                    val respawnY = NBTParser.parseDataIntValue(spawnYLine)?.toDouble()
                    val respawnZ = NBTParser.parseDataIntValue(spawnZLine)?.toDouble()
                    
                    val newRespawnPos = if (respawnX != null && respawnY != null && respawnZ != null) {
                        PlayerLocation(respawnX, respawnY, respawnZ, NBTParser.parseDimension(spawnDimLine))
                    } else null
                    
                    withContext(Dispatchers.Main) { respawnPos = newRespawnPos ?: worldSpawnPos }
                }
                
                if (isPlayerDeathLogFor(latest, player.name)) {
                    val lastDeathLine = stateHolder.sendRconCommand("data get entity $commandTarget LastDeathLocation")
                    val newDeathPos = NBTParser.parseLastDeathLocation(lastDeathLine)
                    withContext(Dispatchers.Main) { newDeathPos?.let { lastDeathPos = it } }
                }
            } catch (e: Exception) {}
        }
    }

    val healthPercent = ((health ?: 0f) / 20f).coerceIn(0f, 1f)
    val hungerPercent = ((hunger ?: 0) / 20f).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                        Text("Player Details", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }

        item {
            PocketCraftCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(
                        model = "https://mc-heads.net/head/${player.name}/64",
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(player.name, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text(
                            if (isPlayerOnline) "Online" else "Offline",
                            color = if (isPlayerOnline) PocketColors.Online else PocketColors.Offline,
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (player.pingMs > 0) "Ping ${player.pingMs}ms" else "Ping unavailable",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Server TPS ${"%.1f".format(stateHolder.tps)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        OutlinedButton(onClick = { gamemodeExpanded = true }) {
                            Text(gamemode)
                        }
                        DropdownMenu(expanded = gamemodeExpanded, onDismissRequest = { gamemodeExpanded = false }) {
                            listOf("survival", "creative", "adventure", "spectator").forEach { mode ->
                                DropdownMenuItem(text = { Text(mode) }, onClick = {
                                    gamemode = mode
                                    gamemodeExpanded = false
                                    stateHolder.changePlayerGamemode(player, mode)
                                })
                            }
                        }
                    }
                }
            }
        }

        item {
            PocketCraftCard {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Health and Hunger", fontWeight = FontWeight.Bold)
                    HealthBar(
                        label = "Health",
                        icon = "❤",
                        value = healthPercent,
                        valueLabel = if (health != null) "${"%.1f".format(health)}/20" else "—",
                        barColor = PocketColors.Danger
                    )
                    HealthBar(
                        label = "Hunger",
                        icon = "🍖",
                        value = hungerPercent,
                        valueLabel = if (hunger != null) "$hunger/20" else "—",
                        barColor = Color(0xFFE09F3E)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerActionButton(
                            label = "Kill",
                            actionType = PlayerActionType.DAMAGE,
                            onClick = {
                                if (isPlayerOnline) {
                                    stateHolder.sendCommand("kill $commandTarget")
                                } else {
                                    scope.launch(Dispatchers.IO) {
                                        val datFile = PlayerDataManager.getPlayerDataFile(context, stateHolder.activeWorld, offlineUuid)
                                        val success = NBTParser.updatePlayerData(datFile, mapOf("Health" to 0f))
                                        if (success) {
                                            health = 0f
                                        }
                                        refreshTrigger++
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        PlayerActionButton(
                            label = "Heal",
                            actionType = PlayerActionType.HEAL,
                            onClick = {
                                if (isPlayerOnline) {
                                    stateHolder.sendCommand("effect clear $commandTarget minecraft:instant_health")
                                    stateHolder.sendCommand("effect give $commandTarget minecraft:instant_health 1 255 true")
                                    health = 20f
                                } else {
                                    scope.launch(Dispatchers.IO) {
                                        val datFile = PlayerDataManager.getPlayerDataFile(context, stateHolder.activeWorld, offlineUuid)
                                        val success = NBTParser.updatePlayerData(datFile, mapOf("Health" to 20f))
                                        if (success) {
                                            health = 20f
                                        }
                                        refreshTrigger++
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerActionButton(
                            label = "Starve",
                            actionType = PlayerActionType.STARVE,
                            onClick = {
                                if (isPlayerOnline) {
                                    stateHolder.sendCommand("effect clear $commandTarget minecraft:saturation")
                                    stateHolder.sendCommand("effect give $commandTarget minecraft:hunger 8 255 true")
                                    hunger = 0
                                } else {
                                    scope.launch(Dispatchers.IO) {
                                        val datFile = PlayerDataManager.getPlayerDataFile(context, stateHolder.activeWorld, offlineUuid)
                                        val success = NBTParser.updatePlayerData(datFile, mapOf("foodLevel" to 0))
                                        if (success) {
                                            hunger = 0
                                        }
                                        refreshTrigger++
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        PlayerActionButton(
                            label = "Feed",
                            actionType = PlayerActionType.FEED,
                            onClick = {
                                if (isPlayerOnline) {
                                    stateHolder.sendCommand("effect clear $commandTarget minecraft:hunger")
                                    stateHolder.sendCommand("effect give $commandTarget minecraft:saturation 2 255 true")
                                    hunger = 20
                                } else {
                                    scope.launch(Dispatchers.IO) {
                                        val datFile = PlayerDataManager.getPlayerDataFile(context, stateHolder.activeWorld, offlineUuid)
                                        val success = NBTParser.updatePlayerData(datFile, mapOf("foodLevel" to 20))
                                        if (success) {
                                            hunger = 20
                                        }
                                        refreshTrigger++
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            ControlToggleRow("Whitelisted", whitelisted) {
                whitelisted = it
                stateHolder.sendCommand(if (it) "whitelist add ${player.name}" else "whitelist remove ${player.name}")
                syncModerationFlags()
            }
        }
        item {
            ControlToggleRow("Banned", banned) {
                banned = it
                stateHolder.sendCommand(if (it) "ban ${player.name}" else "pardon ${player.name}")
                syncModerationFlags()
            }
        }
        item {
            ControlToggleRow(
                label = "Operator",
                checked = op,
                activeBadge = "OPED"
            ) {
                op = it
                if (it) {
                    stateHolder.opPlayer(player.name)
                } else {
                    stateHolder.removeOp(player.name)
                }
                syncModerationFlags()
            }
        }

        item {
            PlayerInformationSection(
                currentPos = currentPos,
                respawnPos = respawnPos,
                lastDeathPos = lastDeathPos,
                onTeleport = { loc, title ->
                    val cmd = if (loc.dimension == "minecraft:overworld") {
                        "tp $commandTarget ${loc.x} ${loc.y} ${loc.z}"
                    } else {
                        "execute in ${loc.dimension} run tp $commandTarget ${loc.x} ${loc.y} ${loc.z}"
                    }
                    stateHolder.sendCommand(cmd)
                    scope.launch {
                        snackbarHostState.showSnackbar("Teleported to $title")
                    }
                }
            )
        }

        item {
            PlayerStatisticsSection(stats = stats)
        }

        item {
            PlayerDataDeletionSection(
                playerUuid = offlineUuid,
                delXp = delXp,
                delEnder = delEnder,
                delPlayer = delPlayer,
                delStats = delStats,
                delAdv = delAdv,
                onDelXpChange = { delXp = it },
                onDelEnderChange = { delEnder = it },
                onDelPlayerChange = { delPlayer = it },
                onDelStatsChange = { delStats = it },
                onDelAdvChange = { delAdv = it },
                onDelete = { confirmDeleteData = true }
            )
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
    }

    if (confirmDeleteData) {
        val deleteDataSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    deleteDataSheetState.hide()
                    confirmDeleteData = false
                }
            },
            sheetState = deleteDataSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Delete selected data?", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text("This action cannot be undone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(
                    onClick = {
                        scope.launch {
                            PlayerDataManager.deletePlayerData(
                                context = context,
                                worldName = stateHolder.activeWorld,
                                playerUuid = offlineUuid,
                                deleteExperience = delXp,
                                deleteInventory = false,
                                deleteEnderChest = delEnder,
                                deletePlayerData = delPlayer,
                                deleteStats = delStats,
                                deleteAdvancements = delAdv
                            )
                            deleteDataSheetState.hide()
                            confirmDeleteData = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Delete", color = PocketColors.Danger) }
                TextButton(
                    onClick = {
                        scope.launch {
                            deleteDataSheetState.hide()
                            confirmDeleteData = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancel") }
            }
        }
    }
}

private fun playerCommandTarget(playerName: String): String {
    val escaped = playerName
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return """@a[name="$escaped",limit=1]"""
}

@Composable
private fun ControlToggleRow(
    label: String,
    checked: Boolean,
    activeBadge: String? = null,
    onChanged: (Boolean) -> Unit
) {
    PocketCraftCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontWeight = FontWeight.SemiBold)
                if (checked && !activeBadge.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .background(PocketColors.PrimaryMuted, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = activeBadge,
                            color = PocketColors.PrimaryDark,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
            DuoToggle(checked = checked, onCheckedChange = onChanged)
        }
    }
}

@Composable
private fun DeleteCheckbox(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = onChecked)
            Text(label, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PlayerStatisticsSection(stats: Map<String, Long>) {
    val playTime = formatPlaytime(stats["minecraft:custom:minecraft:play_time"] ?: 0L)
    val distance = formatDistance(
        (stats["minecraft:custom:minecraft:walk_one_cm"] ?: 0L) +
        (stats["minecraft:custom:minecraft:sprint_one_cm"] ?: 0L) +
        (stats["minecraft:custom:minecraft:crouch_one_cm"] ?: 0L) +
        (stats["minecraft:custom:minecraft:swim_one_cm"] ?: 0L)
    )
    val playerKills = (stats["minecraft:custom:minecraft:player_kills"] ?: 0L).toString()
    val mobKills = (stats["minecraft:custom:minecraft:mob_kills"] ?: 0L).toString()
    val deaths = (stats["minecraft:custom:minecraft:deaths"] ?: 0L).toString()
    val jumps = (stats["minecraft:custom:minecraft:jump"] ?: 0L).toString()
    val timeSinceDeath = formatPlaytime(stats["minecraft:custom:minecraft:time_since_death"] ?: 0L)
    val damageDealt = (stats["minecraft:custom:minecraft:damage_dealt"] ?: 0L).toString()
    val blocksMined = (stats["minecraft:mined:minecraft:stone"] ?: 0L) +
        (stats["minecraft:mined:minecraft:dirt"] ?: 0L) +
        (stats["minecraft:mined:minecraft:deepslate"] ?: 0L)

    PocketCraftCard {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Statistics", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    title = "Play time",
                    value = playTime,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    title = "Distance",
                    value = distance,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    title = "Player Kills",
                    value = playerKills,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    title = "Mob Kills",
                    value = mobKills,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    title = "Deaths",
                    value = deaths,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    title = "Jumps",
                    value = jumps,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    title = "Time since Death",
                    value = timeSinceDeath,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    title = "Damage Dealt",
                    value = damageDealt,
                    modifier = Modifier.weight(1f)
                )
            }
            StatTile(
                title = "Blocks mined",
                value = blocksMined.toString(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = PocketColors.PrimaryMuted.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, PocketColors.BorderDark.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = PocketColors.PrimaryDark
            )
        }
    }
}

private fun formatDistance(cm: Long): String {
    val m = cm / 100.0
    return when {
        m >= 1000.0 -> String.format("%.2f km", m / 1000.0)
        else -> String.format("%.1f m", m)
    }
}

@Composable
private fun PlayerDataDeletionSection(
    playerUuid: String,
    delXp: Boolean,
    delEnder: Boolean,
    delPlayer: Boolean,
    delStats: Boolean,
    delAdv: Boolean,
    onDelXpChange: (Boolean) -> Unit,
    onDelEnderChange: (Boolean) -> Unit,
    onDelPlayerChange: (Boolean) -> Unit,
    onDelStatsChange: (Boolean) -> Unit,
    onDelAdvChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val anySelected = delXp || delEnder || delPlayer || delStats || delAdv
    PocketCraftCard {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Delete Player Data", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                text = "Pick exactly what you want to clear for this player.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DeleteCheckbox("Experience points", delXp, onDelXpChange)
            DeleteCheckbox("Ender Chest", delEnder, onDelEnderChange)
            DeleteCheckbox("Player data file", delPlayer, onDelPlayerChange)
            DeleteCheckbox("Statistics file", delStats, onDelStatsChange)
            DeleteCheckbox("Advancements file", delAdv, onDelAdvChange)
            if (playerUuid.isBlank()) {
                Text(
                    text = "Player data actions unlock after PocketCraft learns this player's UUID from the server.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onDelete,
                enabled = anySelected && playerUuid.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PocketColors.Danger),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete selected player data")
            }
        }
    }
}

@Composable
private fun PlayerInformationSection(
    currentPos: PlayerLocation?,
    respawnPos: PlayerLocation?,
    lastDeathPos: PlayerLocation?,
    onTeleport: (PlayerLocation, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Information", fontWeight = FontWeight.Bold)
        LocationSectionCard("📍", "Current position", currentPos, onTeleport)
        LocationSectionCard("🛏", "Respawn location", respawnPos, onTeleport)
        LocationSectionCard("💀", "Last death location", lastDeathPos, onTeleport)
    }
}

@Composable
private fun LocationSectionCard(
    icon: String,
    title: String,
    location: PlayerLocation?,
    onTeleport: (PlayerLocation, String) -> Unit
) {
    PocketCraftCard(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FlatEmojiIcon(icon, modifier = Modifier.size(18.dp), tint = PocketColors.PrimaryDark)
                    Text(title, fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalDivider()
            Column(modifier = Modifier.padding(12.dp)) {
                if (location != null) {
                    Text(location.formatted(), fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        FlatEmojiIcon(location.dimensionIcon(), modifier = Modifier.size(14.dp), tint = PocketColors.PrimaryDark)
                        Text(location.dimensionDisplay(), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onTeleport(location, title) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Teleport")
                    }
                } else {
                    Text("Not set / Location not available yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun extractPlayerSnapshot(
    logs: List<String>,
    playerName: String,
    currentDimension: String,
    respawnDimension: String
): PlayerLiveSnapshot {
    val normalizedPlayer = canonicalPlayerName(playerName)
    fun matchingLine(keyword: String): String? {
        return logs.lastOrNull { line ->
            line.contains(keyword, ignoreCase = true) && matchesPlayerEntityLine(line, playerName, normalizedPlayer)
        }
    }

    fun latestResponseFor(path: String): String? {
        val commandPrefix = "> data get entity "
        val commandSuffix = " $path"
        val commandIndex = logs.indexOfLast { line ->
            line.startsWith(commandPrefix) &&
                line.endsWith(commandSuffix) &&
                matchesPlayerCommandLine(line, playerName, normalizedPlayer)
        }
        if (commandIndex < 0) return null

        for (index in (commandIndex + 1) until logs.size) {
            val line = logs[index]
            if (line.startsWith("> ")) break
            if (line.isBlank()) continue
            if (line.startsWith("[RCON]")) return line
            return line
        }
        return null
    }

    val posLine = latestResponseFor("Pos") ?: matchingLine("Pos")
    val dimLine = latestResponseFor("Dimension") ?: matchingLine("Dimension")
    val healthLine = latestResponseFor("Health") ?: matchingLine("Health")
    val hungerLine = latestResponseFor("foodLevel") ?: matchingLine("foodLevel")
    val spawnXLine = latestResponseFor("SpawnX") ?: matchingLine("SpawnX")
    val spawnYLine = latestResponseFor("SpawnY") ?: matchingLine("SpawnY")
    val spawnZLine = latestResponseFor("SpawnZ") ?: matchingLine("SpawnZ")
    val spawnDimLine = latestResponseFor("SpawnDimension") ?: matchingLine("SpawnDimension")
    val spawnSetLine = latestResponseFor("SpawnSet") ?: matchingLine("SpawnSet")
    val lastDeathLine = latestResponseFor("LastDeathLocation") ?: matchingLine("LastDeathLocation")

    val currentPos = NBTParser.parsePosition(posLine.orEmpty())?.let { (x, y, z) ->
        PlayerLocation(x, y, z, NBTParser.parseDimension(dimLine.orEmpty().ifBlank { currentDimension }))
    }

    val spawnSet = NBTParser.parseDataIntValue(spawnSetLine.orEmpty()) ?: 0
    val respawnX = NBTParser.parseDataIntValue(spawnXLine.orEmpty())?.toDouble()
    val respawnY = NBTParser.parseDataIntValue(spawnYLine.orEmpty())?.toDouble()
    val respawnZ = NBTParser.parseDataIntValue(spawnZLine.orEmpty())?.toDouble()
    // Only populate if all three axes were confirmed by the server AND SpawnSet is 1 (custom spawn) — otherwise it's world spawn
    val respawnPos = if (spawnSet == 1 && respawnX != null && respawnY != null && respawnZ != null) {
        PlayerLocation(
            x = respawnX,
            y = respawnY,
            z = respawnZ,
            dimension = NBTParser.parseDimension(spawnDimLine.orEmpty().ifBlank { respawnDimension })
        )
    } else {
        null
    }

    return PlayerLiveSnapshot(
        currentPos = currentPos,
        respawnPos = respawnPos,
        lastDeathPos = lastDeathLine?.let(NBTParser::parseLastDeathLocation),
        health = NBTParser.parseDataFloatValue(healthLine.orEmpty()) ?: NBTParser.parseFloatValue(healthLine.orEmpty()),
        hunger = NBTParser.parseDataIntValue(hungerLine.orEmpty()) ?: NBTParser.parseIntValue(hungerLine.orEmpty())
    )
}

private fun matchesPlayerCommandLine(line: String, playerName: String, normalizedPlayer: String): Boolean {
    val lower = line.lowercase()
    return lower.contains("name=\"${playerName.lowercase()}\"") ||
        lower.contains("name=\"${normalizedPlayer}\"") ||
        matchesPlayerEntityLine(line, playerName, normalizedPlayer)
}

private fun matchesPlayerEntityLine(line: String, playerName: String, normalizedPlayer: String): Boolean {
    val lower = line.lowercase()
    val rawName = playerName.lowercase()
    return lower.contains(rawName) || lower.contains(normalizedPlayer)
}

private fun canonicalPlayerName(name: String): String =
    name.trim().trimStart('.', '!', '*').lowercase()

private fun isPlayerDeathLogFor(line: String, playerName: String): Boolean {
    val lower = line.lowercase()
    val normalizedName = canonicalPlayerName(playerName)
    val mentionsPlayer = lower.contains(playerName.lowercase()) || lower.contains(normalizedName)
    if (!mentionsPlayer) return false

    val deathHints = listOf(
        " was slain",
        " was shot",
        " was pummeled",
        " was squashed",
        " was killed",
        " fell ",
        " drowned",
        " burned",
        " blew up",
        " hit the ground too hard",
        " starved to death",
        " suffocated",
        " froze to death",
        " walked into danger",
        " died"
    )
    return deathHints.any { it in lower }
}

private fun formatPlaytime(ticks: Long): String {
    val totalMinutes = (ticks / 20L) / 60L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}


