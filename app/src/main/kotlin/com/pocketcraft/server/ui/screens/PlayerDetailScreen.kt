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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.pocketcraft.server.ui.components.PlayerActionButton
import com.pocketcraft.server.ui.components.PlayerActionType
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.pocketcraft.server.service.NBTParser
import com.pocketcraft.server.service.PlayerDataManager
import com.pocketcraft.server.service.PlayerLocation
import com.pocketcraft.server.ui.components.DuoToggle
import com.pocketcraft.server.ui.components.HealthBar
import com.pocketcraft.server.ui.theme.PocketColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PlayerLiveSnapshot(
    val currentPos: PlayerLocation? = null,
    val respawnPos: PlayerLocation? = null,
    val lastDeathPos: PlayerLocation? = null,
    val health: Float? = null,
    val hunger: Int? = null
)

@Composable
fun PlayerDetailScreen(
    stateHolder: ServerStateHolder,
    player: PlayerInfo,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var gamemode by remember { mutableStateOf("survival") }
    var gamemodeExpanded by remember { mutableStateOf(false) }
    var whitelisted by remember { mutableStateOf(false) }
    var banned by remember { mutableStateOf(false) }
    var op by remember { mutableStateOf(false) }
    var currentPos by remember { mutableStateOf<PlayerLocation?>(null) }
    var respawnPos by remember { mutableStateOf<PlayerLocation?>(null) }
    var lastDeathPos by remember { mutableStateOf<PlayerLocation?>(null) }
    var teleportConfirm by remember { mutableStateOf<PlayerLocation?>(null) }
    var stats by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var health by remember { mutableStateOf(20f) }
    var hunger by remember { mutableStateOf(20) }

    var delXp by remember { mutableStateOf(false) }
    var delEnder by remember { mutableStateOf(false) }
    var delPlayer by remember { mutableStateOf(false) }
    var delStats by remember { mutableStateOf(false) }
    var delAdv by remember { mutableStateOf(false) }
    var confirmDeleteData by remember { mutableStateOf(false) }
    val latestLogLine = stateHolder.logs.lastOrNull()
    val isPlayerOnline = stateHolder.onlinePlayers.any { it.name.equals(player.name, ignoreCase = true) }

    fun syncModerationFlags() {
        scope.launch {
            delay(700)
            val statusSnapshot = withContext(Dispatchers.IO) {
                Triple(
                    PlayerDataManager.isWhitelisted(context, stateHolder.versionLabel, player.name),
                    PlayerDataManager.isBanned(context, stateHolder.versionLabel, player.name),
                    PlayerDataManager.isOp(context, stateHolder.versionLabel, player.name)
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
                PlayerDataManager.isWhitelisted(context, stateHolder.versionLabel, player.name),
                PlayerDataManager.isBanned(context, stateHolder.versionLabel, player.name),
                PlayerDataManager.isOp(context, stateHolder.versionLabel, player.name)
            )
        }
        whitelisted = statusSnapshot.first
        banned = statusSnapshot.second
        op = statusSnapshot.third
    }

    LaunchedEffect(player.uuid, stateHolder.versionLabel) {
        stats = if (player.uuid.isNotBlank()) {
            withContext(Dispatchers.IO) {
                PlayerDataManager.parseStats(
                    PlayerDataManager.getStatsFile(context, stateHolder.versionLabel, player.uuid)
                )
            }
        } else {
            emptyMap()
        }
    }

    LaunchedEffect(player.name, latestLogLine) {
        val snapshot = withContext(Dispatchers.Default) {
            extractPlayerSnapshot(
                logs = stateHolder.logs.toList(),
                playerName = player.name,
                currentDimension = currentPos?.dimension ?: "minecraft:overworld",
                respawnDimension = respawnPos?.dimension ?: "minecraft:overworld"
            )
        }

        snapshot.currentPos?.let { currentPos = it }
        snapshot.respawnPos?.let { respawnPos = it }
        snapshot.lastDeathPos?.let { lastDeathPos = it }
        snapshot.health?.let { health = it.coerceIn(0f, 20f) }
        snapshot.hunger?.let { hunger = it.coerceIn(0, 20) }
    }

    LaunchedEffect(player.name, isPlayerOnline, stateHolder.status) {
        if (!isPlayerOnline || stateHolder.status != ServerStatus.ONLINE) return@LaunchedEffect
        while (true) {
            stateHolder.sendCommand("data get entity ${player.name} Pos")
            stateHolder.sendCommand("data get entity ${player.name} Dimension")
            stateHolder.sendCommand("data get entity ${player.name} Health")
            stateHolder.sendCommand("data get entity ${player.name} foodLevel")
            stateHolder.sendCommand("data get entity ${player.name} SpawnX")
            stateHolder.sendCommand("data get entity ${player.name} SpawnY")
            stateHolder.sendCommand("data get entity ${player.name} SpawnZ")
            stateHolder.sendCommand("data get entity ${player.name} SpawnDimension")
            stateHolder.sendCommand("data get entity ${player.name} LastDeathLocation")
            delay(8_000)
        }
    }

    val healthPercent = (health / 20f).coerceIn(0f, 1f)
    val hungerPercent = (hunger / 20f).coerceIn(0f, 1f)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Text("Player Details", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
                        Text(player.uuid.ifBlank { "Unknown UUID" }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    stateHolder.sendCommand("gamemode $mode ${player.name}")
                                })
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Health and Hunger", fontWeight = FontWeight.Bold)
                    HealthBar(
                        label = "Health",
                        icon = "❤",
                        value = healthPercent,
                        valueLabel = "${"%.1f".format(health)}/20",
                        barColor = PocketColors.Danger
                    )
                    HealthBar(
                        label = "Hunger",
                        icon = "🍖",
                        value = hungerPercent,
                        valueLabel = "$hunger/20",
                        barColor = Color(0xFFE09F3E)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerActionButton(
                            label = "Kill",
                            actionType = PlayerActionType.DAMAGE,
                            onClick = { stateHolder.sendCommand("kill ${player.name}") },
                            modifier = Modifier.weight(1f)
                        )
                        PlayerActionButton(
                            label = "Heal",
                            actionType = PlayerActionType.HEAL,
                            onClick = {
                                stateHolder.sendCommand("effect clear ${player.name} instant_health")
                                stateHolder.sendCommand("data merge entity ${player.name} {Health:20.0f}")
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerActionButton(
                            label = "Starve",
                            actionType = PlayerActionType.STARVE,
                            onClick = {
                                stateHolder.sendCommand("data merge entity ${player.name} {foodLevel:0,foodSaturationLevel:0.0f}")
                            },
                            modifier = Modifier.weight(1f)
                        )
                        PlayerActionButton(
                            label = "Feed",
                            actionType = PlayerActionType.FEED,
                            onClick = {
                                stateHolder.sendCommand("effect clear ${player.name} hunger")
                                stateHolder.sendCommand("data merge entity ${player.name} {foodLevel:20,foodSaturationLevel:20.0f}")
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
                stateHolder.sendCommand(if (it) "op ${player.name}" else "deop ${player.name}")
                syncModerationFlags()
            }
        }

        item {
            PlayerInformationSection(
                currentPos = currentPos,
                respawnPos = respawnPos,
                lastDeathPos = lastDeathPos,
                onTeleport = { teleportConfirm = it }
            )
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Statistics", fontWeight = FontWeight.Bold)
                    Text("Playtime: ${formatPlaytime(stats["minecraft:custom:minecraft:play_time"] ?: 0L)}")
                    Text("Kills: ${stats["minecraft:custom:minecraft:player_kills"] ?: 0}")
                    Text("Deaths: ${stats["minecraft:custom:minecraft:deaths"] ?: 0}")
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Delete Player Data", fontWeight = FontWeight.Bold)
                    DeleteCheckbox("Experience points", delXp) { delXp = it }
                    DeleteCheckbox("Ender Chest", delEnder) { delEnder = it }
                    DeleteCheckbox("Player data file", delPlayer) { delPlayer = it }
                    DeleteCheckbox("Statistics file", delStats) { delStats = it }
                    DeleteCheckbox("Advancements file", delAdv) { delAdv = it }
                    val anySelected = delXp || delEnder || delPlayer || delStats || delAdv
                    if (player.uuid.isBlank()) {
                        Text(
                            text = "Player data actions unlock after PocketCraft learns this player's UUID from the server.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { confirmDeleteData = true },
                        enabled = anySelected && player.uuid.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = PocketColors.Danger)
                    ) {
                        Text("Delete player data")
                    }
                }
            }
        }
    }

    if (teleportConfirm != null) {
        val loc = teleportConfirm!!
        AlertDialog(
            onDismissRequest = { teleportConfirm = null },
            title = { Text("Teleport player to this location?") },
            text = { Text("${loc.formatted()}\n${loc.dimension}") },
            confirmButton = {
                TextButton(onClick = {
                    val cmd = if (loc.dimension == "minecraft:overworld") {
                        "tp ${player.name} ${loc.x} ${loc.y} ${loc.z}"
                    } else {
                        "execute in ${loc.dimension} run tp ${player.name} ${loc.x} ${loc.y} ${loc.z}"
                    }
                    stateHolder.sendCommand(cmd)
                    teleportConfirm = null
                }) { Text("Teleport") }
            },
            dismissButton = { TextButton(onClick = { teleportConfirm = null }) { Text("Cancel") } }
        )
    }

    if (confirmDeleteData) {
        AlertDialog(
            onDismissRequest = { confirmDeleteData = false },
            title = { Text("Delete selected data?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        PlayerDataManager.deletePlayerData(
                            context = context,
                            serverVersion = stateHolder.versionLabel,
                            playerUuid = player.uuid,
                            deleteExperience = delXp,
                            deleteInventory = false,
                            deleteEnderChest = delEnder,
                            deletePlayerData = delPlayer,
                            deleteStats = delStats,
                            deleteAdvancements = delAdv
                        )
                        confirmDeleteData = false
                    }
                }) { Text("Delete", color = PocketColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteData = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ControlToggleRow(
    label: String,
    checked: Boolean,
    activeBadge: String? = null,
    onChanged: (Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label)
    }
}

@Composable
private fun PlayerInformationSection(
    currentPos: PlayerLocation?,
    respawnPos: PlayerLocation?,
    lastDeathPos: PlayerLocation?,
    onTeleport: (PlayerLocation) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Information", fontWeight = FontWeight.Bold)
        CollapsibleLocationSection("📍", "Current position", currentPos, onTeleport)
        CollapsibleLocationSection("🛏", "Respawn location", respawnPos, onTeleport)
        CollapsibleLocationSection("💀", "Last death location", lastDeathPos, onTeleport)
    }
}

@Composable
private fun CollapsibleLocationSection(
    icon: String,
    title: String,
    location: PlayerLocation?,
    onTeleport: (PlayerLocation) -> Unit
) {
    var expanded by remember { mutableStateOf(title == "Current position") }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(icon)
                    Text(title)
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = PocketColors.Primary
                )
            }

            if (expanded) {
                HorizontalDivider()
                Column(modifier = Modifier.padding(12.dp)) {
                    if (location != null) {
                        Text(location.formatted(), fontSize = 13.sp)
                        Text("${location.dimensionIcon()} ${location.dimensionDisplay()}", fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { onTeleport(location) }) {
                            Text("Teleport")
                        }
                    } else {
                        Text("Not set", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
    fun matchingLine(keyword: String): String? {
        return logs.lastOrNull { line ->
            line.contains(keyword, ignoreCase = true) && matchesPlayerEntityLine(line, playerName)
        }
    }

    val posLine = matchingLine("Pos")
    val dimLine = matchingLine("Dimension")
    val healthLine = matchingLine("Health")
    val hungerLine = matchingLine("foodLevel")
    val spawnXLine = matchingLine("SpawnX")
    val spawnYLine = matchingLine("SpawnY")
    val spawnZLine = matchingLine("SpawnZ")
    val spawnDimLine = matchingLine("SpawnDimension")
    val lastDeathLine = matchingLine("LastDeathLocation")

    val currentPos = NBTParser.parsePosition(posLine.orEmpty())?.let { (x, y, z) ->
        PlayerLocation(x, y, z, NBTParser.parseDimension(dimLine.orEmpty().ifBlank { currentDimension }))
    }

    val respawnX = NBTParser.parseIntValue(spawnXLine.orEmpty())?.toDouble()
    val respawnY = NBTParser.parseIntValue(spawnYLine.orEmpty())?.toDouble()
    val respawnZ = NBTParser.parseIntValue(spawnZLine.orEmpty())?.toDouble()
    val respawnPos = if (respawnX != null && respawnY != null && respawnZ != null) {
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
        health = NBTParser.parseFloatValue(healthLine.orEmpty()),
        hunger = NBTParser.parseIntValue(hungerLine.orEmpty())
    )
}

private fun matchesPlayerEntityLine(line: String, playerName: String): Boolean {
    return line.contains(playerName, ignoreCase = true) || line.contains("entity data", ignoreCase = true)
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
