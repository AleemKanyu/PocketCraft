package com.pocketcraft.server.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.ui.components.FlatEmojiIcon
import coil.compose.AsyncImage
import com.pocketcraft.server.data.model.PlayerInfo
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.service.ServerFileManager
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.components.DuoButtonVariant
import com.pocketcraft.server.ui.components.GameCard
import com.pocketcraft.server.ui.components.PocketWorldIcon
import com.pocketcraft.server.ui.components.StatusBadge
import com.pocketcraft.server.ui.components.PlayerCard
import com.pocketcraft.server.ui.components.PlayerCardAction
import com.pocketcraft.server.ui.components.VersionUpgradeCard
import com.pocketcraft.server.service.VersionCatalog
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.theme.Monocraft
import com.pocketcraft.server.util.RamUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConsoleScreen(
    stateHolder: ServerStateHolder,
    onViewAllPlayers: () -> Unit,
    onChangeVersion: () -> Unit = {},
    onPlayerSelected: (PlayerInfo) -> Unit = {},
    onOpenServerDetails: () -> Unit = {},
    onAddWorld: () -> Unit = {}
) {
    val logListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    var worldSeed by remember { mutableStateOf("") }
    var showSeedDialog by remember { mutableStateOf(false) }
    var showBedrockHelpDialog by remember { mutableStateOf(false) }
    var seedSetupShown by remember { mutableStateOf(false) }
    var showDownloadRequiredDialog by remember { mutableStateOf(false) }
    val isVersionDownloaded = remember(stateHolder.versionLabel) {
        ServerFileManager.isServerJarReady(context, stateHolder.versionLabel)
    }
    val animatedStartupProgress by animateFloatAsState(
        targetValue = (stateHolder.startupProgressPercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "startup_progress"
    )

    // RAM feature state
    val prefs = remember { AppPreferences(context) }
    val totalRamMb = remember { RamUtils.getTotalRamMb(context) }
    var ramMode by remember { mutableStateOf(prefs.ramMode) }
    var manualRamMb by remember { mutableStateOf(prefs.manualRamMb.coerceIn(512, totalRamMb)) }
    var usedRamMb by remember { mutableStateOf(0) }

    var availableVersions by remember { mutableStateOf(listOf(
        "1.21.1", "1.21", "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
        "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
        "1.18.2", "1.18.1", "1.18",
        "1.17.1", "1.17",
        "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16",
        "1.15.2", "1.15.1", "1.15",
        "1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14",
        "1.13.2", "1.13.1", "1.13",
        "1.12.2", "1.12.1", "1.12",
        "1.11.2", "1.11.1", "1.11",
        "1.10.2", "1.10.1", "1.10",
        "1.9.4", "1.9.3", "1.9.2", "1.9.1", "1.9",
        "1.8.9", "1.8.8", "1.8.7", "1.8.6", "1.8.5", "1.8.4", "1.8.3"
    )) }

    LaunchedEffect(Unit) {
        try {
            // Load seed setup shown flag
            com.pocketcraft.server.data.preferences.AppPreferencesStore.isSeedSetupShownFlow(context).collect { shown ->
                seedSetupShown = shown
            }
        } catch (e: Exception) {
            android.util.Log.e("ConsoleScreen", "Error loading seed setup flag: ${e.message}")
        }
    }

    LaunchedEffect(Unit) {
        try {
            // Load world seed from preferences
            com.pocketcraft.server.data.preferences.AppPreferencesStore.getWorldSeedFlow(context).collect { seed ->
                worldSeed = seed
            }
        } catch (e: Exception) {
            android.util.Log.e("ConsoleScreen", "Error loading world seed: ${e.message}")
        }
    }

    LaunchedEffect(Unit) {
        try {
            val versions = VersionCatalog.fetchStableVersions()

            if (versions.isNotEmpty()) {
                android.util.Log.d("ConsoleScreen", "Using ${versions.size} versions from API")
                availableVersions = versions
            } else {
                android.util.Log.d("ConsoleScreen", "Using default fallback versions (${availableVersions.size})")
            }
        } catch (e: Exception) {
            android.util.Log.e("ConsoleScreen", "Fatal error: ${e.message}")
            // Keep default versions
        }
    }

    LaunchedEffect(stateHolder.logs.size) {
        if (stateHolder.logs.isNotEmpty()) {
            logListState.animateScrollToItem(stateHolder.logs.lastIndex)
        }
    }

    // Poll RAM usage every 2 seconds while server is running
    LaunchedEffect(stateHolder.status == ServerStatus.ONLINE) {
        if (stateHolder.status == ServerStatus.ONLINE) {
            while (stateHolder.status == ServerStatus.ONLINE) {
                usedRamMb = RamUtils.getUsedRamMb(context)
                delay(5000)
            }
        } else {
            usedRamMb = 0
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ServerIdentityCard(
                stateHolder = stateHolder,
                onChangeVersion = onChangeVersion,
                onOpenServerDetails = onOpenServerDetails,
                onAddWorld = onAddWorld,
                onOpenBedrockHelp = { showBedrockHelpDialog = true }
            )
        }
        if (stateHolder.status == ServerStatus.ONLINE && !stateHolder.config.whiteList && !stateHolder.openServerRiskAcknowledged) {
            item {
                androidx.compose.material3.Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFF1A1200)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PocketColors.Starting),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = PocketColors.Starting,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Open server — anyone can join",
                                color = PocketColors.Starting,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Whitelist is disabled. Any player can connect to your server.",
                            color = Color(0xFFB3B3B3),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { stateHolder.enableWhitelist() },
                                border = androidx.compose.foundation.BorderStroke(1.dp, PocketColors.Online),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PocketColors.Online)
                            ) {
                                Text("Enable Whitelist", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = { stateHolder.acknowledgeOpenServerRisk() }
                            ) {
                                Text("I understand the risks", fontSize = 12.sp, color = PocketColors.TextMuted)
                            }
                        }
                    }
                }
            }
        }
        item {
            if (stateHolder.status == ServerStatus.ONLINE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DuoButton(
                        text = if (stateHolder.isStopping && !stateHolder.isRestarting) "STOPPING..." else "STOP",
                        onClick = { stateHolder.stopServer() },
                        enabled = !stateHolder.isStopping,
                        variant = DuoButtonVariant.Danger,
                        modifier = Modifier.weight(1f)
                    )

                    DuoButton(
                        text = if (stateHolder.isRestarting) "RESTARTING..." else "RESTART",
                        onClick = {
                            try {
                                stateHolder.restartServer()
                            } catch (e: Exception) {
                                android.util.Log.e("ConsoleScreen", "Restart error", e)
                                Toast.makeText(context, "Error during restart: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !stateHolder.isStopping,
                        variant = DuoButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                DuoButton(
                    text = if (stateHolder.status == ServerStatus.STARTING) "STARTING..." else "START SERVER",
                    onClick = {
                        try {
                            if (isVersionDownloaded) {
                                stateHolder.startServer()
                            } else {
                                showDownloadRequiredDialog = true
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ConsoleScreen", "Start error", e)
                            Toast.makeText(context, "Error starting server: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = stateHolder.status != ServerStatus.STARTING && !stateHolder.isStopping,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        // Startup Progress Bar (shown only when starting)
        if (stateHolder.isStarting) {
            item {
                GameCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Starting Server",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        LinearProgressIndicator(
                            progress = { animatedStartupProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stateHolder.startupStatusMessage.ifBlank { "Initializing..." },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${stateHolder.startupProgressPercent}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PocketColors.Primary
                            )
                        }
                    }
                }
            }
        }
        item {
            VersionUpgradeCard(
                currentVersion = stateHolder.versionLabel,
                availableVersions = availableVersions,
                onUpgrade = { onChangeVersion() },
                serverIsRunning = stateHolder.isNavigationLocked,
                isVersionDownloaded = isVersionDownloaded
            )
        }
        // RAM Settings Card
        item {
            RamSettingsCard(
                ramMode = ramMode,
                manualRamMb = manualRamMb,
                totalRamMb = totalRamMb,
                serverIsRunning = stateHolder.isNavigationLocked,
                onRamModeChange = { mode ->
                    ramMode = mode
                    prefs.ramMode = mode
                },
                onManualRamChange = { mb ->
                    manualRamMb = mb
                    prefs.manualRamMb = mb
                }
            )
        }
        // RAM Usage Card (visible only while server is running)
        if (stateHolder.status == ServerStatus.ONLINE) {
            item {
                RamUsageCard(usedMb = usedRamMb, maxMb = totalRamMb)
            }
        }
        if (stateHolder.onlinePlayers.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PLAYERS ONLINE (${stateHolder.onlinePlayers.size}/${stateHolder.config.maxPlayers})",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        letterSpacing = 0.5.sp
                    )
                    TextButton(onClick = onViewAllPlayers) {
                        Text(
                            text = "View All",
                            color = PocketColors.PrimaryDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            items(stateHolder.onlinePlayers.take(4)) { player ->
                OnlinePlayerCard(
                    player = player,
                    stateHolder = stateHolder,
                    onOpenDetails = { onPlayerSelected(player) }
                )
            }
        }
        item {
            ConsoleCard(
                stateHolder = stateHolder,
                command = command,
                onCommandChange = { command = it },
                onSend = {
                    if (command.isNotBlank()) {
                        stateHolder.sendCommand(command)
                        command = ""
                    }
                },
                logListState = logListState
            )
        }
    }

    // World Seed Dialog
    if (showDownloadRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadRequiredDialog = false },
            title = { Text("No version downloaded") },
            text = { Text("Download a compatible Minecraft version from the Home screen before starting the server.") },
            confirmButton = {
                TextButton(onClick = {
                    showDownloadRequiredDialog = false
                    onChangeVersion()
                }) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadRequiredDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // World Seed Dialog
    if (showSeedDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 10.dp,
                border = BorderStroke(1.dp, PocketColors.BorderLight)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Set World Seed",
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = Monocraft,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        "Enter a seed for your world. Leave it blank for a random world.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SeedStepRow(step = "1", text = "Choose a seed or let the game generate one for you.")
                        SeedStepRow(step = "2", text = "Tap Confirm to save it in your server settings.")
                        SeedStepRow(step = "3", text = "Start the world again to use the new generation seed.")
                    }

                    OutlinedTextField(
                        value = worldSeed,
                        onValueChange = { worldSeed = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        label = { Text("World Seed (optional)") },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DuoButton(
                            text = "CONFIRM",
                            onClick = {
                                showSeedDialog = false
                                // Mark that we've shown the dialog to prevent it from showing again
                                scope.launch {
                                    try {
                                        com.pocketcraft.server.data.preferences.AppPreferencesStore.setSeedSetupShown(context, true)
                                    } catch (e: Exception) {
                                        android.util.Log.e("ConsoleScreen", "Error marking seed setup as shown: ${e.message}")
                                    }
                                }
                                // WorldSeed will be saved to preferences and used on next server start
                            },
                            modifier = Modifier
                                .weight(1f)
                        )

                        TextButton(
                            onClick = { showSeedDialog = false },
                            modifier = Modifier
                                .weight(0.9f)
                                .height(52.dp)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showBedrockHelpDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showBedrockHelpDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 10.dp,
                border = BorderStroke(1.dp, PocketColors.BorderLight)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = PocketColors.PrimaryMuted
                    ) {
                        Text(
                            text = "Beta",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            fontWeight = FontWeight.Bold,
                            color = PocketColors.PrimaryDark,
                            fontSize = 11.sp
                        )
                    }

                    Text(
                        text = "Cross-platform play",
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = Monocraft,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Bedrock is supported for local LAN play while PocketCraft is in beta. Online play will come soon.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        JoinStepCard(
                            step = "1",
                            title = "Start the server",
                            body = "Wait until PocketCraft shows the server is running."
                        )
                        JoinStepCard(
                            step = "2",
                            title = "Use full LAN IP",
                            body = "Enter the full LAN IP exactly as shown in PocketCraft, without adding a port."
                        )
                        JoinStepCard(
                            step = "3",
                            title = "Stay on the same Wi-Fi",
                            body = "Bedrock players can join from nearby devices on the same network only for now."
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = PocketColors.PrimaryMuted.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = "LAN only for now. The online version is coming soon in beta updates.",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PocketColors.PrimaryDark
                        )
                    }

                    DuoButton(
                        text = "GOT IT",
                        onClick = { showBedrockHelpDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Save seed to preferences
    LaunchedEffect(worldSeed) {
        if (worldSeed.isNotEmpty() && !showSeedDialog) {
            try {
                com.pocketcraft.server.data.preferences.AppPreferencesStore.setWorldSeed(context, worldSeed)
            } catch (e: Exception) {
                android.util.Log.e("ConsoleScreen", "Error saving world seed: ${e.message}")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ServerIdentityCard(
    stateHolder: ServerStateHolder,
    onChangeVersion: () -> Unit,
    onOpenServerDetails: () -> Unit,
    onAddWorld: () -> Unit,
    onOpenBedrockHelp: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val serverRunning = stateHolder.status == ServerStatus.ONLINE
    val canChangeWorld = stateHolder.status == ServerStatus.OFFLINE
    var showWorldSheet by remember { mutableStateOf(false) }
    val activeWorld = if (stateHolder.worlds.isNotEmpty()) {
        stateHolder.worlds.firstOrNull { it.isActive } ?: stateHolder.worlds.firstOrNull()
    } else {
        null
    }

    val worldItems = if (stateHolder.worlds.isNotEmpty()) {
        stateHolder.worlds
    } else {
        listOf(
            WorldEntry(
                name = stateHolder.config.worldName.ifBlank { "world" },
                sizeMb = stateHolder.worldSizeMb,
                isActive = true,
                photoUrl = stateHolder.serverPhotoUrl
            )
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            GameCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showWorldSheet = true }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(PocketColors.PrimaryMuted, RoundedCornerShape(999.dp))
                                .border(2.dp, PocketColors.Primary.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "YOUR SERVER",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PocketColors.PrimaryDark,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(PocketColors.PrimaryMuted)
                                .border(2.dp, PocketColors.Primary.copy(alpha = 0.26f), RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (stateHolder.serverPhotoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = stateHolder.serverPhotoUrl,
                                    contentDescription = "Server photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else if (!activeWorld?.photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = activeWorld?.photoUrl,
                                    contentDescription = "Active world photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                PocketWorldIcon(modifier = Modifier.size(34.dp))
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stateHolder.serverName.ifBlank { stateHolder.config.worldName.ifBlank { "world" } },
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                lineHeight = 24.sp
                            )
                            if (stateHolder.serverDescription.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = stateHolder.serverDescription,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(999.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Dns,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = PocketColors.PrimaryDark
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stateHolder.config.worldName.ifBlank { "world" },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(PocketColors.Primary.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = PocketColors.PrimaryDark
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (canChangeWorld) "Tap card to switch world or edit details" else "Stop server to switch world",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            StatusBadge(
                status = stateHolder.status,
                bedrockBridgeEnabled = stateHolder.bedrockBridgeEnabled,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (showWorldSheet) {
            ModalBottomSheet(
                onDismissRequest = { showWorldSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Worlds",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = if (canChangeWorld) "Select a world to make it active" else "Stop server to switch or add worlds",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = {
                            showWorldSheet = false
                            onOpenServerDetails()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("EDIT SERVER DETAILS")
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(worldItems) { world ->
                            WorldSelectorCard(
                                world = world,
                                enabled = canChangeWorld && !world.isActive,
                                onClick = {
                                    scope.launch {
                                        val msg = stateHolder.setActiveWorld(world.name)
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        showWorldSheet = false
                                    }
                                }
                            )
                        }

                        item {
                            WorldSelectorAddCard(
                                enabled = canChangeWorld,
                                onClick = {
                                    showWorldSheet = false
                                    onAddWorld()
                                }
                            )
                        }
                    }
                }
            }
        }

        val publicAddress = stateHolder.publicAddress?.takeIf { it.isNotBlank() }

        if (stateHolder.tunnelError != null && publicAddress == null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1A0000)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Warning: Internet relay unavailable",
                        fontWeight = FontWeight.Bold,
                        color = PocketColors.Danger
                    )
                    Text(
                        text = stateHolder.tunnelError.orEmpty(),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "LAN hosting still works on the same Wi-Fi network.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = stateHolder::clearTunnelError) {
                        Text("OK")
                    }
                }
            }
        }

        if (stateHolder.tunnelConnecting) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1A1200)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Opening internet relay...",
                        color = PocketColors.Starting,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        val localLanAddress = "${stateHolder.localIp}:${stateHolder.config.port}"
        val canShareAddresses = publicAddress != null || serverRunning

        if (canShareAddresses) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, PocketColors.BorderDark)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Join Addresses",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PocketColors.Primary,
                            letterSpacing = 0.8.sp
                        )
                        IconButton(onClick = onOpenBedrockHelp) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "How to join",
                                tint = PocketColors.PrimaryDark
                            )
                        }
                    }

                    AddressValueRow(
                        label = "Java / Internet",
                        address = publicAddress ?: "Unavailable right now",
                        emphasized = publicAddress != null
                    )

                    AddressValueRow(
                        label = "LAN",
                        address = localLanAddress,
                        emphasized = false
                    )

                    OutlinedButton(
                        onClick = onOpenBedrockHelp,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, PocketColors.BorderDark)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bedrock Join Guide", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            shareServerAddresses(
                                context = context,
                                internetAddress = publicAddress,
                                lanAddress = localLanAddress.takeIf { serverRunning }
                            )
                        },
                        enabled = canShareAddresses,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, PocketColors.BorderDark)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Join Addresses", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeedStepRow(step: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = PocketColors.PrimaryMuted
        ) {
            Text(
                text = step,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                color = PocketColors.PrimaryDark,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun JoinStepCard(step: String, title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = PocketColors.SurfaceVarLight,
        border = BorderStroke(1.dp, PocketColors.BorderLight)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PocketColors.PrimaryMuted
            ) {
                Text(
                    text = step,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    color = PocketColors.PrimaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = body,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WorldSelectorCard(
    world: WorldEntry,
    enabled: Boolean,
    onClick: () -> Unit
) {
    GameCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(98.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PocketColors.PrimaryMuted)
                    .border(2.dp, PocketColors.Primary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (world.photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = world.photoUrl,
                        contentDescription = "${world.name} icon",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                        PocketWorldIcon(modifier = Modifier.size(24.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(world.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1)
                Text(
                    text = if (world.isActive) "Active world" else "${world.sizeMb} MB",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            if (world.isActive) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = PocketColors.Primary.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "ACTIVE",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PocketColors.Primary
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun WorldSelectorAddCard(
    enabled: Boolean,
    onClick: () -> Unit
) {
    GameCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(98.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PocketColors.PrimaryMuted)
                    .border(2.dp, PocketColors.Primary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = PocketColors.Primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text("Add world", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text(
                    text = "Create a new world without replacing others",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OnlinePlayerCard(
    player: PlayerInfo,
    stateHolder: ServerStateHolder,
    onOpenDetails: () -> Unit
) {
    PlayerCard(
        username = player.name,
        subtitle = player.pingMs.takeIf { it > 0 }?.let { "Ping: ${it}ms" } ?: "Ping unavailable",
        modifier = Modifier.clickable { onOpenDetails() },
        actions = listOf(
            PlayerCardAction(label = "Make OP", onClick = { stateHolder.opPlayer(player.name) }),
            PlayerCardAction(label = "Kick", onClick = { stateHolder.kickPlayer(player.name) }),
            PlayerCardAction(
                label = "Ban",
                onClick = { stateHolder.banPlayer(player.name) },
                tint = PocketColors.Offline
            )
        )
    )
}

@Composable
private fun AddressValueRow(
    label: String,
    address: String,
    emphasized: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = address,
            fontSize = if (emphasized) 16.sp else 14.sp,
            fontWeight = if (emphasized) FontWeight.ExtraBold else FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun shareServerAddresses(
    context: android.content.Context,
    internetAddress: String?,
    lanAddress: String?
) {
    if (internetAddress.isNullOrBlank() && lanAddress.isNullOrBlank()) return
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            buildString {
                appendLine("PocketCraft join addresses")
                appendLine()
                appendLine("Internet: ${internetAddress?.takeIf { it.isNotBlank() } ?: "Unavailable right now"}")
                append("LAN: ${lanAddress?.takeIf { it.isNotBlank() } ?: "Unavailable right now"}")
            }
        )
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share server address"))
}

@Composable
private fun ConsoleCard(
    stateHolder: ServerStateHolder,
    command: String,
    onCommandChange: (String) -> Unit,
    onSend: () -> Unit,
    logListState: androidx.compose.foundation.lazy.LazyListState
) {
    GameCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONSOLE",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = stateHolder::clearLogs) {
                Text(
                    text = "Clear",
                    fontSize = 12.sp,
                    color = PocketColors.PrimaryDark
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PocketColors.ConsoleBg)
                .padding(10.dp)
        ) {
            if (stateHolder.logs.isEmpty()) {
                Text(
                    text = "[PocketCraft] Console output will appear here.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = PocketColors.ConsoleGreen
                )
            } else {
                LazyColumn(
                    state = logListState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = stateHolder.logs,
                        key = { index, _ -> index }
                    ) { _, line ->
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            color = PocketColors.ConsoleGreen
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = command,
                onValueChange = onCommandChange,
                placeholder = { Text(text = "Send command...", fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                )
            )
            Button(
                onClick = onSend,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PocketColors.Primary,
                    contentColor = PocketColors.TextLight
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send command"
                )
            }
        }
    }
}

@Composable
private fun RamSettingsCard(
    ramMode: String,
    manualRamMb: Int,
    totalRamMb: Int,
    serverIsRunning: Boolean,
    onRamModeChange: (String) -> Unit,
    onManualRamChange: (Int) -> Unit
) {
    GameCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "RAM Allocation",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("low" to "Low", "manual" to "Manual", "full" to "Full").forEach { (mode, label) ->
                    FilterChip(
                        selected = ramMode == mode,
                        onClick = { if (!serverIsRunning) onRamModeChange(mode) },
                        label = { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                        enabled = !serverIsRunning,
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PocketColors.PrimaryMuted,
                            selectedLabelColor = PocketColors.PrimaryDark
                        )
                    )
                }
            }

            val summaryText = when (ramMode) {
                "full" -> "High-performance preset"
                "manual" -> "Custom RAM preset"
                else -> "Low-memory preset"
            }
            Text(
                text = summaryText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (ramMode == "manual") {
                val stepsCount = ((totalRamMb - 512) / 256).coerceAtLeast(1)
                val steps = (0..stepsCount).map { 512 + it * 256 }
                val sliderIndex = steps.indexOfFirst { it >= manualRamMb }.takeIf { it >= 0 } ?: steps.lastIndex

                Text(
                    text = "Selected: ${manualRamMb} MB",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PocketColors.PrimaryDark
                )
                Slider(
                    value = sliderIndex.toFloat(),
                    onValueChange = { idx ->
                        val mb = steps.getOrElse(idx.toInt()) { manualRamMb }
                        onManualRamChange(mb)
                    },
                    valueRange = 0f..(stepsCount.toFloat()),
                    steps = (stepsCount - 1).coerceAtLeast(0),
                    enabled = !serverIsRunning
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("512 MB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${totalRamMb} MB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (serverIsRunning) {
                Text(
                    text = "Stop the server to change RAM settings",
                    fontSize = 11.sp,
                    color = PocketColors.Offline.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun RamUsageCard(usedMb: Int, maxMb: Int) {
    val fraction = if (maxMb > 0) (usedMb.toFloat() / maxMb).coerceIn(0f, 1f) else 0f
    val barColor = when {
        fraction > 0.85f -> PocketColors.Offline
        fraction > 0.70f -> PocketColors.Starting
        else -> PocketColors.XpGreen
    }
    GameCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🧠 RAM Usage",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
                Text(
                    text = "${usedMb} MB / ${maxMb} MB  (${(fraction * 100).toInt()}%)",
                    fontSize = 12.sp,
                    color = barColor,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
