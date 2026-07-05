package com.pocketcraft.server.ui.screens

import android.widget.Toast
import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pocketcraft.server.ui.components.AnimatedEntranceContainer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pocketcraft.server.analytics.FirebaseAnalyticsManager
import com.pocketcraft.server.data.model.PlayerInfo
import com.pocketcraft.server.ui.components.GameCard
import com.pocketcraft.server.ui.components.PlayerCard
import com.pocketcraft.server.ui.components.PlayerCardAction
import com.pocketcraft.server.ui.components.resolvePlayerAvatarUrl
import com.pocketcraft.server.ui.components.duoOutlinedTextFieldColors
import com.pocketcraft.server.ui.components.duoTextFieldShape
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.theme.PocketMotion
import com.pocketcraft.server.util.LocalAppStrings
import kotlinx.coroutines.launch

@Composable
fun PlayersScreen(
    stateHolder: ServerStateHolder,
    onPlayerSelected: (PlayerInfo) -> Unit = {}
) {
    val s = LocalAppStrings.current
    val tabs = listOf(s.tabOnline, s.tabAllPlayers, s.tabWhitelist, s.tabOps, s.tabBanned)
    var selected by remember { mutableIntStateOf(stateHolder.activePlayersTab) }
    val scope = rememberCoroutineScope()

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
            containerColor = MaterialTheme.colorScheme.background,
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

        if (stateHolder.isRunning && !stateHolder.config.whiteList && stateHolder.openServerRiskAcknowledged) {
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

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally(animationSpec = PocketMotion.softIntOffsetTween(durationMillis = 420)) { it / 12 } +
                            fadeIn(PocketMotion.softFloatTween(durationMillis = 340)) togetherWith
                            slideOutHorizontally(animationSpec = PocketMotion.softIntOffsetTween(durationMillis = 360)) { -it / 14 } +
                            fadeOut(PocketMotion.softFloatTween(durationMillis = 220))
                    } else {
                        slideInHorizontally(animationSpec = PocketMotion.softIntOffsetTween(durationMillis = 420)) { -it / 12 } +
                            fadeIn(PocketMotion.softFloatTween(durationMillis = 340)) togetherWith
                            slideOutHorizontally(animationSpec = PocketMotion.softIntOffsetTween(durationMillis = 360)) { it / 14 } +
                            fadeOut(PocketMotion.softFloatTween(durationMillis = 220))
                    }
                },
                label = "players-tab-transition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> PlayersOnlineTab(
                        stateHolder = stateHolder,
                        onPlayerSelected = onPlayerSelected
                    )
                    1 -> PlayersListTab(
                        players = stateHolder.knownPlayers,
                        emptyTitle = "No known players",
                        emptySubtitle = "Players will appear here once they join your server.",
                        onPlayerSelected = onPlayerSelected,
                        actionLists = { _ -> emptyList() }
                    )
                    2 -> WhitelistTab(
                        stateHolder = stateHolder,
                        players = stateHolder.whitelistPlayers,
                        onPlayerSelected = onPlayerSelected
                    )
                    3 -> PlayersListTab(
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
                    4 -> PlayersListTab(
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
                    else -> Box(Modifier.fillMaxSize())
                }
            }
        }
    }
}

private fun canonicalPlayerName(name: String): String =
    name.trim().trimStart('.', '!', '*').lowercase()

@Composable
fun PlayersOnlineTab(
    stateHolder: ServerStateHolder,
    onPlayerSelected: (PlayerInfo) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var visibleCount by remember { mutableIntStateOf(10) }
    var showAddAfkDialog by remember { mutableStateOf(false) }
    var prefilledAfkName by remember { mutableStateOf("") }
    var prefilledAfkX by remember { mutableStateOf("") }
    var prefilledAfkY by remember { mutableStateOf("") }
    var prefilledAfkZ by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val players = stateHolder.sessionPlayers
        .distinctBy { canonicalPlayerName(it.name) }
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
                    LocalAppStrings.current.players,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
                Text(
                    "${stateHolder.serverName} • ${stateHolder.onlinePlayers.size}/${stateHolder.config.maxPlayers} Online",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }
            IconButton(
                onClick = { scope.launch { stateHolder.refreshAll() } },
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
            shape = duoTextFieldShape(),
            singleLine = true,
            colors = duoOutlinedTextFieldColors()
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
                letterSpacing = 0.sp,
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
                    letterSpacing = 0.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Player list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(filtered.take(visibleCount)) { idx, player ->
                val isOnline = stateHolder.onlinePlayers.any { canonicalPlayerName(it.name) == canonicalPlayerName(player.name) }
                AnimatedEntranceContainer(index = minOf(idx, 8)) {
                    PlayerOnlineCard(
                        player = player,
                        isOnline = isOnline,
                        isServerRunning = stateHolder.isRunning,
                        onOpenDetails = { onPlayerSelected(player) },
                        onKick = { stateHolder.kickPlayer(player.name) },
                        onBan = { stateHolder.banPlayer(player.name) },
                        onOp = {
                            if (player.isOp) stateHolder.removeOp(player.name) else stateHolder.opPlayer(player.name)
                        },
                        onAddAfkHelper = {
                            Toast.makeText(context, "Fetching player location...", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                val location = stateHolder.suggestAfkFarmLocation(player.name)
                                prefilledAfkName = "${player.name}'s Farm"
                                if (location != null) {
                                    prefilledAfkX = location.first.toString()
                                    prefilledAfkY = location.second.toString()
                                    prefilledAfkZ = location.third.toString()
                                } else {
                                    prefilledAfkX = ""
                                    prefilledAfkY = ""
                                    prefilledAfkZ = ""
                                    Toast.makeText(context, "Could not fetch location; fill manually.", Toast.LENGTH_SHORT).show()
                                }
                                showAddAfkDialog = true
                            }
                        }
                    )
                }
            }
            if (filtered.size > visibleCount) {
                item {
                    TextButton(
                        onClick = { visibleCount += 10 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
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

            item {
                Spacer(Modifier.height(20.dp))
                AfkHelpersSection(
                    stateHolder = stateHolder,
                    onAddClick = {
                        prefilledAfkName = ""
                        prefilledAfkX = ""
                        prefilledAfkY = ""
                        prefilledAfkZ = ""
                        showAddAfkDialog = true
                    },
                    onMessage = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    if (showAddAfkDialog) {
        AddAfkFarmDialog(
            stateHolder = stateHolder,
            initialName = prefilledAfkName,
            initialX = prefilledAfkX,
            initialY = prefilledAfkY,
            initialZ = prefilledAfkZ,
            onDismiss = {
                showAddAfkDialog = false
                prefilledAfkName = ""
                prefilledAfkX = ""
                prefilledAfkY = ""
                prefilledAfkZ = ""
            },
            onSaved = { message ->
                showAddAfkDialog = false
                prefilledAfkName = ""
                prefilledAfkX = ""
                prefilledAfkY = ""
                prefilledAfkZ = ""
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun AfkHelpersSection(
    stateHolder: ServerStateHolder,
    onAddClick: () -> Unit,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = PocketColors.SurfaceCard.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            PocketColors.CardBorder.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PocketColors.Primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.VideogameAsset,
                            contentDescription = null,
                            tint = PocketColors.Primary
                        )
                    }
                    Column {
                        Text(
                            "AFK Helpers",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = PocketColors.TextPrimary
                        )
                        Text(
                            "Saved to this world and auto-respawned on next boot.",
                            fontSize = 12.sp,
                            color = PocketColors.TextSecondary
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                stateHolder.refreshAfkHelpers()
                                onMessage("AFK helper status refreshed.")
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PocketColors.InactiveBg)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh AFK helpers",
                            tint = PocketColors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Button(
                        onClick = onAddClick,
                        modifier = Modifier.height(38.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PocketColors.Primary,
                            contentColor = PocketColors.PrimaryText
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Farm", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            if (stateHolder.afkFarms.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.82f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("No AFK farms saved yet", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "Add a farm and PocketCraft will keep its dummy data synced for this world. The dummy will spawn automatically as soon as the server has a player online.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                        )
                    }
                }
            } else {
                stateHolder.afkFarms.forEach { farm ->
                    AfkHelperRow(
                        stateHolder = stateHolder,
                        farm = farm,
                        onMessage = onMessage
                    )
                }
            }
        }
    }
}

@Composable
private fun AfkHelperRow(
    stateHolder: ServerStateHolder,
    farm: com.pocketcraft.server.afk.AfkFarmLocation,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val statusColor = when {
        farm.isLive -> PocketColors.Online
        farm.isActive -> PocketColors.Primary
        else -> PocketColors.TextMuted
    }
    val statusText = when {
        farm.isLive -> "LIVE"
        farm.isActive -> "ARMED"
        else -> "IDLE"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PocketColors.SurfaceCard.copy(alpha = 0.86f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            PocketColors.CardBorder.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            farm.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = PocketColors.TextPrimary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            statusText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = statusColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            letterSpacing = 0.sp
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = PocketColors.Primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "X ${farm.x}  Y ${farm.y}  Z ${farm.z}",
                        fontSize = 12.sp,
                        color = PocketColors.TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(6.dp))
                val secondary = when {
                    farm.isLive -> "Dummy is loaded on the current server session."
                    farm.isActive -> "Waiting for a player to come online, then will spawn automatically."
                    else -> "Disabled. It will stay out until you arm it again."
                }
                Text(
                    secondary,
                    fontSize = 11.sp,
                    color = PocketColors.TextMuted
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (farm.isActive) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                onMessage(stateHolder.toggleAfkFarm(farm.id))
                            }
                        },
                        enabled = !stateHolder.isAfkHelperBusy,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            PocketColors.InactiveBorder
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PocketColors.TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(
                            "Disable Helper",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                onMessage(stateHolder.toggleAfkFarm(farm.id))
                            }
                        },
                        enabled = !stateHolder.isAfkHelperBusy,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PocketColors.Primary,
                            contentColor = PocketColors.PrimaryText
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(
                            "Enable Helper",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                IconButton(
                    onClick = {
                        scope.launch {
                            onMessage(stateHolder.deleteAfkFarm(farm.id))
                        }
                    },
                    enabled = !stateHolder.isAfkHelperBusy,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PocketColors.DangerBg.copy(alpha = 0.12f))
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete AFK helper",
                        tint = PocketColors.Danger,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AddAfkFarmDialog(
    stateHolder: ServerStateHolder,
    initialName: String = "",
    initialX: String = "",
    initialY: String = "",
    initialZ: String = "",
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember(initialName) { mutableStateOf(initialName) }
    var x by remember(initialX) { mutableStateOf(initialX) }
    var y by remember(initialY) { mutableStateOf(initialY) }
    var z by remember(initialZ) { mutableStateOf(initialZ) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPlayerSelectionSheet by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = {
            if (!saving) {
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            }
        },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Add AFK Farm", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Live spawn works best when one player is online. Otherwise PocketCraft will queue the helper for the next server boot.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f)
            )
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text("Farm name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = duoTextFieldShape(),
                colors = duoOutlinedTextFieldColors()
            )
            OutlinedTextField(
                value = x,
                onValueChange = {
                    x = it
                    error = null
                },
                label = { Text("X") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = duoTextFieldShape(),
                colors = duoOutlinedTextFieldColors()
            )
            OutlinedTextField(
                value = y,
                onValueChange = {
                    y = it
                    error = null
                },
                label = { Text("Y") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = duoTextFieldShape(),
                colors = duoOutlinedTextFieldColors()
            )
            OutlinedTextField(
                value = z,
                onValueChange = {
                    z = it
                    error = null
                },
                label = { Text("Z") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = duoTextFieldShape(),
                colors = duoOutlinedTextFieldColors()
            )
            OutlinedButton(
                onClick = {
                    val players = stateHolder.onlinePlayers
                    if (players.isEmpty()) {
                        Toast.makeText(
                            context,
                            "No online player is available right now.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        showPlayerSelectionSheet = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Use Current Online Player Location", fontWeight = FontWeight.Bold)
            }
            if (!error.isNullOrBlank()) {
                Text(
                    error!!,
                    color = Color(0xFFFF7A7A),
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val parsedX = x.toIntOrNull()
                    val parsedY = y.toIntOrNull()
                    val parsedZ = z.toIntOrNull()
                    if (name.trim().isBlank() || parsedX == null || parsedY == null || parsedZ == null) {
                        error = "Fill in a name and valid whole-number coordinates."
                        return@Button
                    }

                    saving = true
                    scope.launch {
                        val message = stateHolder.addAfkFarm(
                            name = name.trim(),
                            x = parsedX,
                            y = parsedY,
                            z = parsedZ
                        )
                        saving = false
                        error = null
                        sheetState.hide()
                        onSaved(message)
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PocketColors.Primary,
                    contentColor = Color.Black,
                    disabledContainerColor = PocketColors.Primary.copy(0.5f)
                )
            ) {
                Text(if (saving) "Saving..." else "Save Farm", fontWeight = FontWeight.Bold)
            }
            TextButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }

    if (showPlayerSelectionSheet) {
        OnlinePlayerSelectionSheet(
            stateHolder = stateHolder,
            onDismiss = { showPlayerSelectionSheet = false },
            onPlayerSelected = { player ->
                scope.launch {
                    val location = stateHolder.suggestAfkFarmLocation(player.name)
                    if (location != null) {
                        x = location.first.toString()
                        y = location.second.toString()
                        z = location.third.toString()
                    }
                    
                    // Copy player IP to clipboard
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Player IP", player.ip)
                    clipboard.setPrimaryClip(clip)
                    
                    Toast.makeText(context, "Copied IP for ${player.name} (${player.ip}) and loaded location.", Toast.LENGTH_LONG).show()
                    showPlayerSelectionSheet = false
                }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun OnlinePlayerSelectionSheet(
    stateHolder: ServerStateHolder,
    onDismiss: () -> Unit,
    onPlayerSelected: (PlayerInfo) -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
        },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Select Online Player", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Choose a player to copy their IP address and load their current in-game coordinates.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f)
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
            ) {
                items(stateHolder.onlinePlayers) { player ->
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onPlayerSelected(player)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(player.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                if (player.ip.isNotBlank()) {
                                    Text(player.ip, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("Select", fontWeight = FontWeight.Bold, color = PocketColors.Primary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
fun PlayerOnlineCard(
    player: PlayerInfo,
    isOnline: Boolean,
    isServerRunning: Boolean,
    onOpenDetails: () -> Unit,
    onKick: () -> Unit,
    onBan: () -> Unit,
    onOp: () -> Unit,
    onAddAfkHelper: () -> Unit
) {
    val subtitle = if (isOnline) {
        player.pingText()
    } else {
        if (!isServerRunning) "Last session" else "Offline"
    }

    val actions = mutableListOf<PlayerCardAction>()
    if (isOnline) {
        actions.add(PlayerCardAction(label = "Add as AFK Helper", onClick = onAddAfkHelper))
        actions.add(PlayerCardAction(label = LocalAppStrings.current.kick, onClick = onKick))
        actions.add(PlayerCardAction(label = LocalAppStrings.current.ban, onClick = onBan, tint = PocketColors.Offline))
    }
    if (player.isOp) {
        actions.add(PlayerCardAction(label = LocalAppStrings.current.removeOp, onClick = onOp, tint = PocketColors.Offline))
    } else {
        actions.add(PlayerCardAction(label = LocalAppStrings.current.makeOp, onClick = onOp, tint = PocketColors.Primary))
    }

    PlayerCard(
        username = player.name,
        subtitle = subtitle,
        avatarUrl = resolvePlayerAvatarUrl(player.name, player.uuid, 64),
        badgeText = if (player.isOp) "OPED" else if (isOnline) "ONLINE" else "OFFLINE",
        badgeColor = if (player.isOp) PocketColors.PrimaryDark else if (isOnline) PocketColors.Primary else Color.Gray,
        onClick = onOpenDetails,
        actions = actions
    )
}

private data class WhitelistSearchResult(
    val player: PlayerInfo,
    val isWhitelisted: Boolean
)

@Composable
fun WhitelistTab(
    stateHolder: ServerStateHolder,
    players: List<PlayerInfo>,
    onPlayerSelected: (PlayerInfo) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val addedNames = remember { mutableStateListOf<String>() }
    val searchResults = remember(players, stateHolder.knownPlayers, query) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            players.map { WhitelistSearchResult(it, isWhitelisted = true) }
        } else {
            val whitelistedMatches = players.filter { it.name.contains(trimmedQuery, ignoreCase = true) }
                .map { WhitelistSearchResult(it, isWhitelisted = true) }
            val knownMatches = stateHolder.knownPlayers.filter { kp ->
                kp.name.contains(trimmedQuery, ignoreCase = true) &&
                players.none { wp -> wp.name.equals(kp.name, ignoreCase = true) }
            }.map { WhitelistSearchResult(it, isWhitelisted = false) }
            whitelistedMatches + knownMatches
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = duoTextFieldShape(),
                    colors = duoOutlinedTextFieldColors(),
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
                    Text(LocalAppStrings.current.addPlayer, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (searchResults.isEmpty()) {
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
            itemsIndexed(searchResults, key = { _, result -> result.player.name }) { idx, result ->
                val player = result.player
                AnimatedEntranceContainer(index = minOf(idx, 8)) {
                    if (result.isWhitelisted) {
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
                    } else {
                        val isAdded = remember(addedNames, player.name) {
                            addedNames.contains(player.name.lowercase())
                        }
                        PlayerCard(
                            username = player.name,
                            subtitle = "Already joined player",
                            badgeText = "JOINED",
                            badgeColor = Color.Gray,
                            onClick = { onPlayerSelected(player) },
                            trailingContent = {
                                Button(
                                    onClick = {
                                        stateHolder.addWhitelistPlayer(player.name)
                                        FirebaseAnalyticsManager.logPlayerWhitelistAdded(player.name)
                                        addedNames.add(player.name.lowercase())
                                    },
                                    enabled = !isAdded,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAdded) Color.Gray.copy(alpha = 0.2f) else PocketColors.Primary,
                                        contentColor = if (isAdded) Color.LightGray else Color.Black,
                                        disabledContainerColor = Color.Gray.copy(alpha = 0.2f),
                                        disabledContentColor = Color.LightGray
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isAdded) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = if (isAdded) "Added" else "Add",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isAdded) "Added" else "Add",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AddPlayerDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var playerName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val addPlayerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            scope.launch {
                addPlayerSheetState.hide()
                onDismiss()
            }
        },
        sheetState = addPlayerSheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add Player to Whitelist", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Enter the exact Minecraft username",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
            )
            OutlinedTextField(
                value = playerName,
                onValueChange = { playerName = it },
                placeholder = { Text("e.g., Steve") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = duoTextFieldShape(),
                colors = duoOutlinedTextFieldColors()
            )
            Button(
                onClick = {
                    scope.launch {
                        addPlayerSheetState.hide()
                        onConfirm(playerName)
                    }
                },
                enabled = playerName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PocketColors.Primary,
                    contentColor = Color.Black,
                    disabledContainerColor = PocketColors.Primary.copy(0.5f)
                )
            ) {
                Text(LocalAppStrings.current.addPlayer, fontWeight = FontWeight.Bold)
            }
            TextButton(
                onClick = {
                    scope.launch {
                        addPlayerSheetState.hide()
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(LocalAppStrings.current.cancel)
            }
        }
    }
}

@Composable
fun PlayersListTab(
    players: List<PlayerInfo>,
    emptyTitle: String,
    emptySubtitle: String,
    onPlayerSelected: (PlayerInfo) -> Unit,
    actionLists: (PlayerInfo) -> List<PlayerCardAction>
) {
    val dedupedPlayers = players
        .groupBy { canonicalPlayerName(it.name) }
        .values
        .map { group ->
            group.maxWithOrNull(
                compareBy<PlayerInfo> { it.isOp }
                    .thenBy { it.uuid.isNotBlank() }
                    .thenByDescending { it.name.startsWith(".").not() }
            ) ?: group.first()
        }
    if (dedupedPlayers.isEmpty()) {
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
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(dedupedPlayers) { idx, player ->
                AnimatedEntranceContainer(index = minOf(idx, 8)) {
                    PlayerCard(
                        username = player.name,
                        subtitle = if (player.uuid.isNotEmpty()) player.uuid.take(8) else "Player info",
                        avatarUrl = resolvePlayerAvatarUrl(player.name, player.uuid, 64),
                        badgeText = if (player.isOp) "OPED" else "MANAGED",
                        badgeColor = if (player.isOp) PocketColors.PrimaryDark else PocketColors.Primary,
                        onClick = { onPlayerSelected(player) },
                        actions = actionLists(player)
                    )
                }
            }
        }
    }
}
