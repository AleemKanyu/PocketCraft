package com.pocketcraft.server.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pocketcraft.server.analytics.FirebaseAnalyticsManager
import com.pocketcraft.server.data.model.PlayerInfo
import com.pocketcraft.server.ui.components.GameCard
import com.pocketcraft.server.ui.components.PlayerCard
import com.pocketcraft.server.ui.components.PlayerCardAction
import com.pocketcraft.server.ui.theme.PocketColors

@Composable
fun PlayersScreen(
    stateHolder: ServerStateHolder,
    onPlayerSelected: (PlayerInfo) -> Unit = {}
) {
    val tabs = listOf("Online", "Whitelist", "Ops", "Banned", "All Players")
    var selected by remember { mutableIntStateOf(stateHolder.activePlayersTab) }

    LaunchedEffect(selected) {
        stateHolder.activePlayersTab = selected
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        androidx.compose.material3.ScrollableTabRow(
            selectedTabIndex = selected,
            containerColor = MaterialTheme.colorScheme.surface,
            divider = {}
        ) {
            tabs.forEachIndexed { index, label ->
                androidx.compose.material3.Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (selected == index) FontWeight.ExtraBold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    },
                    selectedContentColor = PocketColors.Primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!stateHolder.config.whiteList && stateHolder.openServerRiskAcknowledged) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A0800))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = PocketColors.Starting,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    "Open join mode is ON",
                    fontSize = 11.sp,
                    color = PocketColors.Starting
                )
            }
        }

        when (selected) {
            0 -> PlayersOnlineTab(
                stateHolder = stateHolder,
                onPlayerSelected = onPlayerSelected
            )
            1 -> WhitelistTab(
                stateHolder = stateHolder,
                players = stateHolder.whitelistPlayers,
                onPlayerSelected = onPlayerSelected
            )
            2 -> PlayersListTab(
                players = stateHolder.opPlayers,
                emptyTitle = "No operators yet",
                emptySubtitle = "Promote a player from the Online tab to grant OP access.",
                onPlayerSelected = onPlayerSelected,
                actionLists = { player ->
                    listOf(
                        PlayerCardAction(
                            label = "Remove OP",
                            onClick = { stateHolder.removeOp(player.name) },
                            tint = PocketColors.Offline
                        )
                    )
                }
            )
            3 -> PlayersListTab(
                players = stateHolder.bannedPlayers,
                emptyTitle = "Ban list is empty",
                emptySubtitle = "Banned players are stored in `banned-players.json`.",
                onPlayerSelected = onPlayerSelected,
                actionLists = { player ->
                    listOf(
                        PlayerCardAction(
                            label = "Unban",
                            onClick = { stateHolder.unbanPlayer(player.name) },
                            tint = PocketColors.Primary
                        )
                    )
                }
            )
            4 -> PlayersListTab(
                players = stateHolder.knownPlayers,
                emptyTitle = "No known players",
                emptySubtitle = "Players will appear here once they join your server.",
                onPlayerSelected = onPlayerSelected,
                actionLists = { _ -> emptyList() }
            )
        }
    }
}

@Composable
fun PlayersOnlineTab(
    stateHolder: ServerStateHolder,
    onPlayerSelected: (PlayerInfo) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var visibleCount by remember { mutableIntStateOf(10) }

    val players = stateHolder.onlinePlayers
    val filtered = players.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PocketColors.Primary.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Group,
                    null,
                    tint = PocketColors.Primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Players",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
                Text(
                    "${stateHolder.serverName} • ${players.size}/${stateHolder.config.maxPlayers} Online",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }
            IconButton(
                onClick = { stateHolder.refreshAll() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PocketColors.Primary.copy(0.15f))
            ) {
                Icon(Icons.Default.Refresh, null, tint = PocketColors.Primary)
            }
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    "Search players by username",
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                )
            },
            trailingIcon = null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedBorderColor = PocketColors.Primary
            )
        )

        Spacer(Modifier.size(16.dp))

        // "ACTIVE NOW" header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ACTIVE NOW",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = PocketColors.Primary.copy(0.15f)
            ) {
                Text(
                    "LIVE",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    color = PocketColors.Primary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Player list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(filtered.take(visibleCount)) { player ->
                PlayerOnlineCard(
                    player = player,
                    onOpenDetails = { onPlayerSelected(player) },
                    onKick = { stateHolder.kickPlayer(player.name) },
                    onBan = { stateHolder.banPlayer(player.name) },
                    onOp = { stateHolder.opPlayer(player.name) }
                )
            }
        }

        // "View X more" button
        if (filtered.size > visibleCount) {
            TextButton(
                onClick = { visibleCount += 10 },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    "View ${filtered.size - visibleCount} more players online...",
                    color = PocketColors.Primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun PlayerOnlineCard(
    player: PlayerInfo,
    onOpenDetails: () -> Unit,
    onKick: () -> Unit,
    onBan: () -> Unit,
    onOp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpenDetails)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Avatar with online dot
            Box {
                AsyncImage(
                    model = "https://mc-heads.net/avatar/${player.name}/64",
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp)
                        .size(16.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(PocketColors.Primary)
                        .border(width = 3.dp, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(6.dp))
                )
            }
            Column {
                Text(
                    player.name,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                Text(
                    "IP: ${player.ip.ifEmpty { "N/A" }} • Ping: ${player.pingMs}ms",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onKick, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Kick", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onBan, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Ban", fontWeight = FontWeight.Bold)
            }
            if (player.isOp) {
                OutlinedButton(
                    onClick = onOp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PocketColors.Primary)
                ) {
                    Text("OPED", fontWeight = FontWeight.ExtraBold)
                }
            } else {
                Button(
                    onClick = onOp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PocketColors.Primary,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.Shield, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("OP", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
}

@Composable
fun WhitelistTab(
    stateHolder: ServerStateHolder,
    players: List<PlayerInfo>,
    onPlayerSelected: (PlayerInfo) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredPlayers = remember(players, query) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            players
        } else {
            players.filter { it.name.contains(trimmedQuery, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    placeholder = {
                        Text("Search whitelist or type a username to add")
                    }
                )
                Button(
                    onClick = {
                        val playerName = query.trim()
                        if (playerName.isNotBlank()) {
                            stateHolder.addWhitelistPlayer(playerName)
                            FirebaseAnalyticsManager.logPlayerWhitelistAdded(playerName)
                            query = ""
                        }
                    },
                    enabled = query.trim().isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PocketColors.Primary,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Add Player", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (filteredPlayers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (players.isEmpty()) "Whitelist is empty" else "No matching player found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            if (players.isEmpty()) {
                                "Turn on whitelist in Settings, then add players from the search box above."
                            } else {
                                "Try another username or add the player directly from the field above."
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        } else {
            items(filteredPlayers, key = { it.name }) { player ->
                PlayerCard(
                    username = player.name,
                    subtitle = "Whitelisted player",
                    badgeText = "WHITELISTED",
                    badgeColor = PocketColors.Primary,
                    onClick = { onPlayerSelected(player) },
                    actions = listOf(
                        PlayerCardAction(
                            label = "Remove",
                            onClick = {
                                stateHolder.removeWhitelistPlayer(player.name)
                                FirebaseAnalyticsManager.logPlayerWhitelistRemoved(player.name)
                            },
                            tint = PocketColors.Offline
                        )
                    )
                )
            }
        }
    }
}

@Composable
fun AddPlayerDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var playerName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Player to Whitelist", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter the exact Minecraft username",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                )
                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    placeholder = { Text("e.g., Steve") },
                    modifier = Modifier
                        .fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(playerName) },
                enabled = playerName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PocketColors.Primary,
                    contentColor = Color.Black,
                    disabledContainerColor = PocketColors.Primary.copy(0.5f)
                )
            ) {
                Text("Add", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PlayersListTab(
    players: List<PlayerInfo>,
    emptyTitle: String,
    emptySubtitle: String,
    onPlayerSelected: (PlayerInfo) -> Unit,
    actionLists: (PlayerInfo) -> List<PlayerCardAction>
) {
    if (players.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emptyTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    emptySubtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(players) { player ->
                PlayerCard(
                    username = player.name,
                    subtitle = if (player.uuid.isNotEmpty()) player.uuid.take(8) else "Player info",
                    avatarUrl = "https://mc-heads.net/avatar/${player.name}/64",
                    badgeText = "MANAGED",
                    badgeColor = PocketColors.Primary,
                    onClick = { onPlayerSelected(player) },
                    actions = actionLists(player)
                )
            }
        }
    }
}
