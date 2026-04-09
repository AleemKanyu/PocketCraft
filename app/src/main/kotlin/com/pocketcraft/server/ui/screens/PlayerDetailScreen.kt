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
import com.pocketcraft.server.service.NBTParser
import com.pocketcraft.server.service.PlayerDataManager
import com.pocketcraft.server.service.PlayerLocation
import com.pocketcraft.server.ui.components.DuoToggle
import com.pocketcraft.server.ui.components.HealthBar
import com.pocketcraft.server.ui.components.FlatEmojiIcon
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

private val DefaultRespawnLocation = PlayerLocation(0.0, 0.0, 0.0, "minecraft:overworld")

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
    var respawnPos by remember { mutableStateOf<PlayerLocation?>(DefaultRespawnLocation) }
    var lastDeathPos by remember { mutableStateOf<PlayerLocation?>(null) }
    var stats by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var health by remember { mutableStateOf(20f) }
    var hunger by remember { mutableStateOf(20) }

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
        if (!mentionsPlayer || !spawnChanged) return@LaunchedEffect

        delay(500)
        stateHolder.sendCommand("data get entity $commandTarget SpawnX")
        delay(120)
        stateHolder.sendCommand("data get entity $commandTarget SpawnY")
        delay(120)
        stateHolder.sendCommand("data get entity $commandTarget SpawnZ")
        delay(120)
        stateHolder.sendCommand("data get entity $commandTarget SpawnDimension")
    }

    LaunchedEffect(player.name, latestLogLine, isPlayerOnline, stateHolder.status) {
        if (!isPlayerOnline || stateHolder.status != ServerStatus.ONLINE) return@LaunchedEffect
        val latest = latestLogLine.orEmpty()
        if (!isPlayerDeathLogFor(latest, player.name)) return@LaunchedEffect

        delay(500)
        stateHolder.sendCommand("data get entity $commandTarget LastDeathLocation")
    }

    LaunchedEffect(player.name, isPlayerOnline, stateHolder.status) {
        if (!isPlayerOnline || stateHolder.status != ServerStatus.ONLINE) return@LaunchedEffect
        while (true) {
            stateHolder.sendCommand("data get entity $commandTarget Pos")
            delay(140)
            stateHolder.sendCommand("data get entity $commandTarget Dimension")
            delay(140)
            stateHolder.sendCommand("data get entity $commandTarget Health")
            delay(140)
            stateHolder.sendCommand("data get entity $commandTarget foodLevel")
            delay(140)
            stateHolder.sendCommand("data get entity $commandTarget SpawnX")
            delay(140)
            stateHolder.sendCommand("data get entity $commandTarget SpawnY")
            delay(140)
            stateHolder.sendCommand("data get entity $commandTarget SpawnZ")
            delay(140)
            stateHolder.sendCommand("data get entity $commandTarget SpawnDimension")
            delay(140)
            stateHolder.sendCommand("data get entity $commandTarget LastDeathLocation")
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
                                    stateHolder.sendCommand("gamemode $mode $commandTarget")
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
                            onClick = { stateHolder.sendCommand("kill $commandTarget") },
                            modifier = Modifier.weight(1f)
                        )
                        PlayerActionButton(
                            label = "Heal",
                            actionType = PlayerActionType.HEAL,
                            onClick = {
                                stateHolder.sendCommand("effect clear $commandTarget minecraft:instant_health")
                                stateHolder.sendCommand("effect give $commandTarget minecraft:instant_health 1 255 true")
                                health = 20f
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerActionButton(
                            label = "Starve",
                            actionType = PlayerActionType.STARVE,
                            onClick = {
                                stateHolder.sendCommand("effect clear $commandTarget minecraft:saturation")
                                stateHolder.sendCommand("effect give $commandTarget minecraft:hunger 8 255 true")
                                hunger = 0
                            },
                            modifier = Modifier.weight(1f)
                        )
                        PlayerActionButton(
                            label = "Feed",
                            actionType = PlayerActionType.FEED,
                            onClick = {
                                stateHolder.sendCommand("effect clear $commandTarget minecraft:hunger")
                                stateHolder.sendCommand("effect give $commandTarget minecraft:saturation 2 255 true")
                                hunger = 20
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
                onTeleport = { loc ->
                    val cmd = if (loc.dimension == "minecraft:overworld") {
                        "tp $commandTarget ${loc.x} ${loc.y} ${loc.z}"
                    } else {
                        "execute in ${loc.dimension} run tp $commandTarget ${loc.x} ${loc.y} ${loc.z}"
                    }
                    stateHolder.sendCommand(cmd)
                }
            )
        }

        item {
            PlayerStatisticsSection(stats = stats)
        }

        item {
            PlayerDataDeletionSection(
                playerUuid = player.uuid,
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
                                serverVersion = stateHolder.versionLabel,
                                playerUuid = player.uuid,
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
    return """@a[name="$escaped"]"""
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
    val kills = (stats["minecraft:custom:minecraft:player_kills"] ?: 0L).toString()
    val deaths = (stats["minecraft:custom:minecraft:deaths"] ?: 0L).toString()
    val jumps = (stats["minecraft:custom:minecraft:jump"] ?: 0L).toString()
    val blocksMined = (stats["minecraft:mined:minecraft:stone"] ?: 0L) +
        (stats["minecraft:mined:minecraft:dirt"] ?: 0L) +
        (stats["minecraft:mined:minecraft:deepslate"] ?: 0L)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
                    title = "Kills",
                    value = kills,
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
                    FlatEmojiIcon(icon, modifier = Modifier.size(18.dp), tint = PocketColors.PrimaryDark)
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
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            FlatEmojiIcon(location.dimensionIcon(), modifier = Modifier.size(14.dp), tint = PocketColors.PrimaryDark)
                            Text(location.dimensionDisplay(), fontSize = 12.sp)
                        }
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
    val lastDeathLine = latestResponseFor("LastDeathLocation") ?: matchingLine("LastDeathLocation")

    val currentPos = NBTParser.parsePosition(posLine.orEmpty())?.let { (x, y, z) ->
        PlayerLocation(x, y, z, NBTParser.parseDimension(dimLine.orEmpty().ifBlank { currentDimension }))
    }

    val respawnX = NBTParser.parseDataIntValue(spawnXLine.orEmpty())?.toDouble()
    val respawnY = NBTParser.parseDataIntValue(spawnYLine.orEmpty())?.toDouble()
    val respawnZ = NBTParser.parseDataIntValue(spawnZLine.orEmpty())?.toDouble()
    val respawnPos = if (respawnX != null && respawnY != null && respawnZ != null) {
        PlayerLocation(
            x = respawnX,
            y = respawnY,
            z = respawnZ,
            dimension = NBTParser.parseDimension(spawnDimLine.orEmpty().ifBlank { respawnDimension })
        )
    } else {
        DefaultRespawnLocation
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
