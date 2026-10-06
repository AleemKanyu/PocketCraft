package com.pockethost.app.ui.screens

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
import com.pockethost.app.ui.components.AnimatedEntranceContainer
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
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pockethost.app.analytics.FirebaseAnalyticsManager
import com.pockethost.app.data.model.PlayerInfo
import com.pockethost.app.ui.components.GameCard
import com.pockethost.app.ui.components.PlayerCard
import com.pockethost.app.ui.components.PlayerCardAction
import androidx.compose.foundation.horizontalScroll
import com.pockethost.app.ui.components.resolvePlayerAvatarUrl
import com.pockethost.app.ui.components.duoOutlinedTextFieldColors
import com.pockethost.app.ui.components.duoTextFieldShape
import com.pockethost.app.ui.components.DuoButton
import com.pockethost.app.ui.components.DuoButtonVariant
import com.pockethost.app.ui.theme.Monocraft
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.PocketMotion
import com.pockethost.app.ui.theme.button3d
import com.pockethost.app.ui.theme.card3d
import com.pockethost.app.ui.theme.pill3d
import com.pockethost.app.ui.theme.pocketIsDarkTheme
import com.pockethost.app.ui.theme.ButtonFont
import com.pockethost.app.ui.theme.DMMono
import com.pockethost.app.util.LocalAppStrings
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, label ->
                val isSelected = selected == index
                val countText = when (index) {
                    0 -> stateHolder.onlinePlayers.size.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()
                    2 -> stateHolder.whitelistPlayers.size.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()
                    3 -> stateHolder.opPlayers.size.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()
                    4 -> (stateHolder.bannedPlayers.size + stateHolder.bannedIps.size).takeIf { it > 0 }?.let { " ($it)" }.orEmpty()
                    else -> ""
                }
                Box(
                    modifier = Modifier
                        .then(
                            if (isSelected) {
                                Modifier.button3d(
                                    elevation = 4.dp,
                                    borderColor = PocketColors.PrimaryBorder,
                                    depthColor = PocketColors.PrimaryBorderBottom
                                )
                            } else {
                                Modifier.card3d(
                                    elevation = 2.dp,
                                    cornerRadius = 14.dp,
                                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                            }
                        )
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .background(if (isSelected) PocketColors.Primary else MaterialTheme.colorScheme.surface)
                        .clickable { selected = index }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$label$countText",
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isSelected) PocketColors.PrimaryText else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (stateHolder.isRunning && !stateHolder.config.whiteList && stateHolder.openServerRiskAcknowledged) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PocketColors.DangerBg)
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
                    1 -> Column(modifier = Modifier.fillMaxSize()) {
                        com.pockethost.app.ui.components.ImportPlayerDataButton(
                            stateHolder = stateHolder,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)
                        )
                        PlayersListTab(
                            players = stateHolder.knownPlayers,
                            emptyTitle = "No known players",
                            emptySubtitle = "Players will appear here once they join your server, or after you import player data.",
                            onPlayerSelected = onPlayerSelected,
                            actionLists = { _ -> emptyList() }
                        )
                    }
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
                    4 -> BannedTab(
                        stateHolder = stateHolder,
                        onPlayerSelected = onPlayerSelected
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

    val players = (stateHolder.onlinePlayers + stateHolder.sessionPlayers)
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
                    .size(48.dp)
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
            itemsIndexed(filtered.take(visibleCount), key = { _, player -> player.name }) { idx, player ->
                val onlinePlayer = stateHolder.onlinePlayers.firstOrNull { canonicalPlayerName(it.name) == canonicalPlayerName(player.name) }
                val isOnline = onlinePlayer != null
                val displayPlayer = onlinePlayer ?: player
                AnimatedEntranceContainer(index = minOf(idx, 8)) {
                    PlayerOnlineCard(
                        player = displayPlayer,
                        isOnline = isOnline,
                        isServerRunning = stateHolder.isRunning,
                        onOpenDetails = { onPlayerSelected(displayPlayer) },
                        onKick = { stateHolder.kickPlayer(displayPlayer.name) },
                        onBan = { stateHolder.banPlayer(displayPlayer.name) },
                        onOp = {
                            if (displayPlayer.isOp) stateHolder.removeOp(displayPlayer.name) else stateHolder.opPlayer(displayPlayer.name)
                        },
                        onAddAfkHelper = {
                            Toast.makeText(context, "Fetching player location...", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                val location = stateHolder.suggestAfkFarmLocation(displayPlayer.name)
                                prefilledAfkName = "${displayPlayer.name}'s Farm"
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

// ─────────────────────────────────────────────────────────────────────────────
// AFK helpers
//
// One raised card with a header, an optional cooldown notice, the saved farms
// and a full-width add action. The add button used to sit in the header beside
// the refresh icon, where it had no room and wrapped onto two lines.
// ─────────────────────────────────────────────────────────────────────────────

private val AfkCardCorner = 18.dp
private val AfkTileCorner = 12.dp

@Composable
private fun afkSurface(): Color =
    if (pocketIsDarkTheme()) PocketColors.SurfaceCardDark else PocketColors.SurfaceCard

@Composable
private fun afkSurfaceSoft(): Color =
    if (pocketIsDarkTheme()) PocketColors.SurfaceVarDark else PocketColors.SurfaceHover

@Composable
private fun afkBorder(): Color =
    if (pocketIsDarkTheme()) PocketColors.BorderDark else PocketColors.CardBorder

@Composable
private fun afkBorderDepth(): Color =
    if (pocketIsDarkTheme()) PocketColors.CardBorderBottomDark else PocketColors.CardBorderBottom

/** Small square icon tile used by the section header and the empty state. */
@Composable
private fun AfkIconTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    size: Dp = 40.dp,
    corner: Dp = AfkTileCorner,
    iconSize: Dp = 20.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(iconSize)
        )
    }
}

/** Tinted single-line notice used for the cooldown and the unsupported-type warning. */
@Composable
private fun AfkNotice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AfkTileCorner))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(AfkTileCorner))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(15.dp)
        )
        Text(
            text = text,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            color = PocketColors.TextPrimary
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

    val isPaperOrPurpur = stateHolder.config.serverType == com.pockethost.app.data.model.ServerType.PAPER ||
        stateHolder.config.serverType == com.pockethost.app.data.model.ServerType.PURPUR

    if (!isPaperOrPurpur) {
        GameCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                AfkIconTile(icon = Icons.Default.Warning, accent = PocketColors.Warning)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "AFK bots unavailable",
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = Monocraft,
                        fontSize = 14.sp,
                        color = PocketColors.TextPrimary
                    )
                    Text(
                        "AFK helper bots work on Paper and Purpur only. Switch server type in Settings to use them.",
                        fontSize = 12.sp,
                        color = PocketColors.TextSecondary,
                        lineHeight = 17.sp
                    )
                }
            }
        }
        return
    }

    var secondsLeft by remember { mutableIntStateOf(0) }
    LaunchedEffect(stateHolder.lastAfkEnabledTime) {
        while (true) {
            val elapsed = System.currentTimeMillis() - stateHolder.lastAfkEnabledTime
            val left = 10 - (elapsed / 1000).toInt()
            secondsLeft = if (left in 1..10) left else 0
            if (secondsLeft <= 0) break
            kotlinx.coroutines.delay(500)
        }
    }

    val farms = stateHolder.afkFarms.toList()

    GameCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AfkIconTile(icon = Icons.Default.VideogameAsset, accent = PocketColors.Primary)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            "AFK Helpers",
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = Monocraft,
                            fontSize = 15.sp,
                            color = PocketColors.TextPrimary
                        )
                        if (farms.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(PocketColors.Primary.copy(alpha = 0.16f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = farms.size.toString(),
                                    fontSize = 10.sp,
                                    fontFamily = Monocraft,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PocketColors.Primary
                                )
                            }
                        }
                    }
                    Text(
                        "Saved to this world · respawned on next boot",
                        fontSize = 11.5.sp,
                        color = PocketColors.TextSecondary,
                        lineHeight = 15.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(AfkTileCorner))
                        .background(afkSurfaceSoft())
                        .card3d(
                            elevation = 4.dp,
                            cornerRadius = AfkTileCorner,
                            borderColor = afkBorder(),
                            depthColor = afkBorderDepth()
                        )
                        .clickable {
                            scope.launch {
                                stateHolder.refreshAfkHelpers()
                                onMessage("AFK helper status refreshed.")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh AFK helpers",
                        tint = PocketColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (secondsLeft > 0) {
                AfkNotice(
                    icon = Icons.Default.Timer,
                    accent = PocketColors.Warning,
                    text = "Cooldown active — wait $secondsLeft seconds before toggling a helper again."
                )
            }

            if (farms.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AfkCardCorner))
                        .background(afkSurfaceSoft())
                        .border(1.dp, afkBorder(), RoundedCornerShape(AfkCardCorner))
                        .padding(horizontal = 18.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AfkIconTile(
                        icon = Icons.Default.VideogameAsset,
                        accent = PocketColors.Primary,
                        size = 46.dp,
                        corner = 14.dp,
                        iconSize = 22.dp
                    )
                    Text(
                        "No AFK farms yet",
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = Monocraft,
                        fontSize = 14.sp,
                        color = PocketColors.TextPrimary
                    )
                    Text(
                        "Add a farm and PocketHost keeps its dummy synced for this world. It spawns automatically once a player is online.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center,
                        color = PocketColors.TextSecondary
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    farms.forEach { farm ->
                        AfkHelperRow(
                            stateHolder = stateHolder,
                            farm = farm,
                            isCooldownActive = secondsLeft > 0,
                            onMessage = onMessage
                        )
                    }
                }
            }

            DuoButton(
                text = "ADD FARM",
                onClick = onAddClick,
                icon = Icons.Default.Add,
                variant = DuoButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 48.dp
            )
        }
    }
}

@Composable
private fun AfkHelperRow(
    stateHolder: ServerStateHolder,
    farm: com.pockethost.app.afk.AfkFarmLocation,
    isCooldownActive: Boolean,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var showEditDialog by remember { mutableStateOf(false) }
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
    val secondary = when {
        farm.isLive -> "Dummy is loaded on the current server session."
        farm.isActive -> "Waiting for a player to come online, then spawns automatically."
        else -> "Disabled. It stays out until you arm it again."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AfkCardCorner))
            .background(afkSurfaceSoft())
            .card3d(
                elevation = 4.dp,
                cornerRadius = AfkCardCorner,
                borderColor = afkBorder(),
                depthColor = afkBorderDepth()
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                fontSize = 15.sp,
                color = PocketColors.TextPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(statusColor.copy(alpha = 0.14f))
                    .border(1.dp, statusColor.copy(alpha = 0.40f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    statusText,
                    color = statusColor,
                    fontFamily = Monocraft,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(afkSurface())
                .border(1.dp, afkBorder(), RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.MyLocation,
                contentDescription = null,
                tint = PocketColors.TextSecondary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                "X ${farm.x}   Y ${farm.y}   Z ${farm.z}",
                fontSize = 11.sp,
                fontFamily = DMMono,
                color = PocketColors.TextSecondary,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            secondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = PocketColors.TextMuted
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val toggleEnabled = if (farm.isActive) {
                !stateHolder.isAfkHelperBusy
            } else {
                !stateHolder.isAfkHelperBusy && !isCooldownActive
            }
            AfkActionButton(
                text = if (farm.isActive) "Disable" else "Enable",
                enabled = toggleEnabled,
                filled = !farm.isActive,
                modifier = Modifier.weight(1f),
                onClick = {
                    scope.launch {
                        onMessage(stateHolder.toggleAfkFarm(farm.id))
                    }
                }
            )

            AfkIconAction(
                icon = Icons.Default.Edit,
                contentDescription = "Edit AFK bot settings",
                accent = PocketColors.Primary,
                enabled = !stateHolder.isAfkHelperBusy,
                onClick = { showEditDialog = true }
            )

            AfkIconAction(
                icon = Icons.Default.DeleteOutline,
                contentDescription = "Delete AFK helper",
                accent = PocketColors.Danger,
                enabled = !stateHolder.isAfkHelperBusy,
                onClick = {
                    scope.launch {
                        onMessage(stateHolder.deleteAfkFarm(farm.id))
                    }
                }
            )
        }
    }

    if (showEditDialog) {
        EditAfkFarmDialog(
            stateHolder = stateHolder,
            farm = farm,
            onDismiss = { showEditDialog = false },
            onSaved = { msg -> onMessage(msg) }
        )
    }
}

/** Enable/disable toggle, drawn with the app's raised-pill depth. */
@Composable
private fun AfkActionButton(
    text: String,
    enabled: Boolean,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = when {
        !enabled -> PocketColors.InactiveBg
        filled -> PocketColors.Primary
        else -> afkSurface()
    }
    val border = when {
        !enabled -> PocketColors.InactiveBorder
        filled -> PocketColors.PrimaryBorder
        else -> afkBorder()
    }
    val depth = when {
        !enabled -> PocketColors.InactiveBorderBottom
        filled -> PocketColors.PrimaryBorderBottom
        else -> afkBorderDepth()
    }
    val content = when {
        !enabled -> PocketColors.InactiveText.copy(alpha = 0.6f)
        filled -> PocketColors.PrimaryText
        else -> PocketColors.TextPrimary
    }

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .pill3d(elevation = 4.dp, borderColor = border, depthColor = depth)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            fontSize = 12.sp,
            color = content
        )
    }
}

@Composable
private fun AfkIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(AfkTileCorner))
            .background(accent.copy(alpha = 0.12f * alpha))
            .card3d(
                elevation = 4.dp,
                cornerRadius = AfkTileCorner,
                borderColor = accent.copy(alpha = 0.40f * alpha),
                depthColor = accent.copy(alpha = 0.65f * alpha)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = accent.copy(alpha = alpha),
            modifier = Modifier.size(19.dp)
        )
    }
}

// ── Shared pieces for the add / edit AFK farm sheets ─────────────────────────

/** Icon + Monocraft title + one supporting line, used by both AFK sheets. */
@Composable
private fun AfkSheetHeader(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AfkIconTile(icon = icon, accent = PocketColors.Primary, size = 42.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                title,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Monocraft,
                fontSize = 16.sp,
                color = PocketColors.TextPrimary
            )
            Text(
                subtitle,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = PocketColors.TextSecondary
            )
        }
    }
}

/**
 * X / Y / Z on one line. They were three full-width stacked fields, which made
 * a short three-number form scroll and pushed the save button off screen.
 */
@Composable
private fun AfkCoordinateFields(
    x: String,
    onXChange: (String) -> Unit,
    y: String,
    onYChange: (String) -> Unit,
    z: String,
    onZChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            Triple("X", x, onXChange),
            Triple("Y", y, onYChange),
            Triple("Z", z, onZChange)
        ).forEach { (label, value, onChange) ->
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = duoTextFieldShape(),
                colors = duoOutlinedTextFieldColors()
            )
        }
    }
}

@Composable
private fun AfkUseLocationButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(afkSurfaceSoft())
            .pill3d(
                elevation = 4.dp,
                borderColor = afkBorder(),
                depthColor = afkBorderDepth()
            )
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.MyLocation,
            contentDescription = null,
            tint = PocketColors.Primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Use an online player's location",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = PocketColors.TextPrimary
        )
    }
}

/** Inline validation message for the AFK sheets. */
@Composable
private fun AfkSheetError(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AfkTileCorner))
            .background(PocketColors.Danger.copy(alpha = 0.10f))
            .border(1.dp, PocketColors.Danger.copy(alpha = 0.45f), RoundedCornerShape(AfkTileCorner))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = PocketColors.Danger,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(15.dp)
        )
        Text(
            text,
            color = PocketColors.Danger,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AfkSheetActions(
    saveLabel: String,
    saving: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DuoButton(
            text = if (saving) "SAVING..." else saveLabel,
            onClick = onSave,
            enabled = !saving,
            variant = DuoButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth(),
            minHeight = 50.dp
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(999.dp))
                .clickable(enabled = !saving, onClick = onCancel),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Cancel",
                fontWeight = FontWeight.Bold,
                fontFamily = Monocraft,
                fontSize = 12.sp,
                color = PocketColors.TextSecondary
            )
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
            AfkSheetHeader(
                title = "Add AFK farm",
                subtitle = "Live spawn works best with a player online. Otherwise the helper is queued for the next server boot.",
                icon = Icons.Default.Add
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
            AfkCoordinateFields(
                x = x,
                onXChange = { x = it; error = null },
                y = y,
                onYChange = { y = it; error = null },
                z = z,
                onZChange = { z = it; error = null }
            )
            AfkUseLocationButton {
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
            }
            if (!error.isNullOrBlank()) {
                AfkSheetError(error!!)
            }
            AfkSheetActions(
                saveLabel = "SAVE FARM",
                saving = saving,
                onSave = {
                    val parsedX = x.toIntOrNull()
                    val parsedY = y.toIntOrNull()
                    val parsedZ = z.toIntOrNull()
                    if (name.trim().isBlank() || parsedX == null || parsedY == null || parsedZ == null) {
                        error = "Fill in a name and valid whole-number coordinates."
                        return@AfkSheetActions
                    }

                    val isPremium = com.pockethost.app.billing.BillingManager.getInstance(context).isPremium.value
                    if (!isPremium && stateHolder.afkFarms.size >= 1) {
                        error = "Free plan is limited to 1 AFK bot. Upgrade to Pro to unlock unlimited AFK bots!"
                        return@AfkSheetActions
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
                onCancel = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }
            )
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
                        Toast.makeText(context, "Loaded location for ${player.name} (X: ${location.first}, Y: ${location.second}, Z: ${location.third})", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Could not fetch location for ${player.name}.", Toast.LENGTH_SHORT).show()
                    }
                    
                    // Copy player IP to clipboard
                    if (player.ip.isNotBlank()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Player IP", player.ip)
                        clipboard.setPrimaryClip(clip)
                    }
                    showPlayerSelectionSheet = false
                }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EditAfkFarmDialog(
    stateHolder: ServerStateHolder,
    farm: com.pockethost.app.afk.AfkFarmLocation,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(farm.id) { mutableStateOf(farm.name) }
    var x by remember(farm.id) { mutableStateOf(farm.x.toString()) }
    var y by remember(farm.id) { mutableStateOf(farm.y.toString()) }
    var z by remember(farm.id) { mutableStateOf(farm.z.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showPlayerSelectionSheet by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AfkSheetHeader(
                title = "Edit bot settings",
                subtitle = "Update the name or coordinates for ${farm.dummyEntityName}.",
                icon = Icons.Default.Edit
            )
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text("Bot / farm name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = duoTextFieldShape(),
                colors = duoOutlinedTextFieldColors()
            )
            AfkCoordinateFields(
                x = x,
                onXChange = { x = it; error = null },
                y = y,
                onYChange = { y = it; error = null },
                z = z,
                onZChange = { z = it; error = null }
            )
            AfkUseLocationButton {
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
            }
            if (!error.isNullOrBlank()) {
                AfkSheetError(error!!)
            }
            AfkSheetActions(
                saveLabel = "SAVE SETTINGS",
                saving = saving,
                onSave = {
                    val parsedX = x.toIntOrNull()
                    val parsedY = y.toIntOrNull()
                    val parsedZ = z.toIntOrNull()
                    if (name.trim().isBlank() || parsedX == null || parsedY == null || parsedZ == null) {
                        error = "Fill in a name and valid whole-number coordinates."
                        return@AfkSheetActions
                    }

                    saving = true
                    scope.launch {
                        val message = stateHolder.updateAfkFarm(
                            id = farm.id,
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
                onCancel = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }
            )
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
                        Toast.makeText(context, "Loaded location for ${player.name} (X: ${location.first}, Y: ${location.second}, Z: ${location.third})", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Could not fetch location for ${player.name}.", Toast.LENGTH_SHORT).show()
                    }
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
                items(stateHolder.onlinePlayers, key = { it.name }) { player ->
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
    // Computed on every recomposition on purpose: `players` and knownPlayers are the same
    // snapshot-list instances as they change, so remember(players, ...) never saw a new
    // key and kept an empty result after adding someone ("Whitelist (1)" over an empty list).
    // Reading them here subscribes to their changes; the lists are small.
    val searchResults = run {
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
                DuoButton(
                    text = LocalAppStrings.current.addPlayer,
                    onClick = {
                        val playerName = query.trim()
                        if (playerName.isNotBlank()) {
                            stateHolder.addWhitelistPlayer(playerName)
                            FirebaseAnalyticsManager.logPlayerWhitelistAdded(playerName)
                            query = ""
                        }
                    },
                    enabled = query.trim().isNotBlank(),
                    icon = Icons.Default.Add,
                    variant = DuoButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
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
                        // addedNames is a SnapshotStateList — reading .contains() directly here
                        // (not wrapped in remember, whose keys never actually change since
                        // addedNames is the same instance across recompositions) is what makes
                        // this scope correctly subscribe to its mutations, so the button
                        // updates immediately when the user taps Add.
                        val isAdded = addedNames.contains(player.name.lowercase())
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
                "Enter the exact player username",
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
            itemsIndexed(dedupedPlayers, key = { _, player -> player.name }) { idx, player ->
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

@Composable
fun BannedTab(
    stateHolder: ServerStateHolder,
    onPlayerSelected: (PlayerInfo) -> Unit
) {
    var showUnbanIpDialog by remember { mutableStateOf(false) }
    var ipToUnban by remember { mutableStateOf("") }

    if (showUnbanIpDialog) {
        AlertDialog(
            onDismissRequest = { showUnbanIpDialog = false },
            title = { Text("Unban IP Address", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the IP address you wish to unban from the server:", fontSize = 14.sp)
                    OutlinedTextField(
                        value = ipToUnban,
                        onValueChange = { ipToUnban = it },
                        placeholder = { Text("e.g. 192.168.1.50") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = duoTextFieldShape(),
                        colors = duoOutlinedTextFieldColors()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ip = ipToUnban.trim()
                        if (ip.isNotBlank()) {
                            stateHolder.unbanIp(ip)
                        }
                        ipToUnban = ""
                        showUnbanIpDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Unban IP")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnbanIpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GameCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text(
                            text = "IP Ban Management",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Unban specific IP addresses or clear all active IP bans",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DuoButton(
                            text = "Unban IP",
                            onClick = { showUnbanIpDialog = true },
                            icon = Icons.Default.Shield,
                            variant = DuoButtonVariant.Primary,
                            modifier = Modifier.weight(1f),
                            minHeight = 44.dp
                        )

                        if (stateHolder.bannedIps.isNotEmpty()) {
                            DuoButton(
                                text = "Clear All IP Bans",
                                onClick = { stateHolder.unbanAllIps() },
                                variant = DuoButtonVariant.SecondaryGray,
                                modifier = Modifier.weight(1f),
                                minHeight = 44.dp
                            )
                        }
                    }
                }
            }
        }

        if (stateHolder.bannedIps.isNotEmpty()) {
            item {
                Text(
                    text = "Banned IP Addresses (${stateHolder.bannedIps.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(stateHolder.bannedIps, key = { it.ip }) { record ->
                GameCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = record.ip,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                fontFamily = Monocraft
                            )
                            if (record.reason.isNotBlank()) {
                                Text(
                                    text = "Reason: ${record.reason}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(
                            onClick = { stateHolder.unbanIp(record.ip) }
                        ) {
                            Text("Unban", color = PocketColors.Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Banned Players (${stateHolder.bannedPlayers.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (stateHolder.bannedPlayers.isEmpty() && stateHolder.bannedIps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Ban list is empty",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Banned players and IPs are stored in banned-players.json and banned-ips.json.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(stateHolder.bannedPlayers, key = { it.uuid.ifBlank { it.name } }) { player ->
                PlayerCard(
                    username = player.name,
                    subtitle = if (player.uuid.isNotEmpty()) player.uuid.take(8) else "Banned player",
                    avatarUrl = resolvePlayerAvatarUrl(player.name, player.uuid, 64),
                    onClick = { onPlayerSelected(player) },
                    actions = listOf(
                        PlayerCardAction(
                            label = "Unban",
                            onClick = { stateHolder.unbanPlayer(player.name) },
                            tint = PocketColors.Primary
                        )
                    )
                )
            }
        }
    }
}
