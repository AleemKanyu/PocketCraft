package com.pockethost.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Switch
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import com.pockethost.app.ui.components.IpBottomSheet
import androidx.compose.ui.text.style.TextAlign
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.ui.components.FlatEmojiIcon
import com.pockethost.app.ui.components.AnimatedEntranceContainer
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.pockethost.app.R
import com.pockethost.app.billing.BillingManager
import com.pockethost.app.data.model.ServerType
import com.pockethost.app.data.model.PlayerInfo
import com.pockethost.app.data.preferences.AppPreferences
import com.pockethost.app.data.preferences.AppPreferencesStore
import com.pockethost.app.data.repository.ServerConfigRepository
import com.pockethost.app.server.ServerHostService
import com.pockethost.app.service.ServerFileManager
import com.pockethost.app.service.NBTParser
import com.pockethost.app.service.PlayerDataManager
import com.pockethost.app.ui.components.DuoButton
import com.pockethost.app.ui.components.DuoButtonVariant
import com.pockethost.app.ui.components.GameCard
import com.pockethost.app.ui.components.PocketWorldIcon
import com.pockethost.app.ui.components.PlayerCard
import com.pockethost.app.ui.components.PlayerCardAction
import com.pockethost.app.ui.components.ReleaseTrain26WarningBanner
import com.pockethost.app.ui.components.VersionUpgradeCard
import com.pockethost.app.ui.components.duoOutlinedTextFieldColors
import com.pockethost.app.ui.components.duoTextFieldShape
import com.pockethost.app.service.VersionCatalog
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.button3d
import com.pockethost.app.ui.theme.card3d
import com.pockethost.app.ui.theme.pill3d
import com.pockethost.app.ui.theme.pocketIsDarkTheme
import com.pockethost.app.ui.theme.ButtonFont
import com.pockethost.app.ui.theme.DMMono
import com.pockethost.app.ui.theme.Monocraft
import com.pockethost.app.ui.theme.pocketCardShadowColor
import com.pockethost.app.ui.theme.pocketHighContrastBorderColor
import com.pockethost.app.ui.theme.pocketPopupAccentContainerColor
import com.pockethost.app.ui.theme.pocketPopupAccentTintColor
import com.pockethost.app.ui.theme.pocketSheetBorderColor
import com.pockethost.app.ui.theme.pocketWarningAccentColor
import com.pockethost.app.ui.theme.pocketWarningBodyColor
import com.pockethost.app.ui.theme.pocketWarningBorderColor
import com.pockethost.app.ui.theme.pocketWarningIconChipColor
import com.pockethost.app.ui.theme.pocketWarningSurfaceColor
import com.pockethost.app.ui.theme.pocketWarningTitleColor
import com.pockethost.app.ui.theme.raisedBorder
import com.pockethost.app.util.RamUtils
import com.pockethost.app.util.LocalAppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.graphicsLayer

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ConsoleScreen(
    stateHolder: ServerStateHolder,
    onViewAllPlayers: () -> Unit,
    onChangeVersion: () -> Unit = {},
    onInstallCurrentVersion: () -> Unit = {},
    onPlayerSelected: (PlayerInfo) -> Unit = {},
    onOpenServerDetails: () -> Unit = {},
    onAddWorld: () -> Unit = {},
    adContentAfterVersion: (@Composable () -> Unit)? = null,
    topContentBelowServerCard: (@Composable () -> Unit)? = null,
    onNavigateToSignUp: () -> Unit = {}
) {
    
    val logListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var worldSeed by remember { mutableStateOf("") }
    var showSeedDialog by remember { mutableStateOf(false) }
    var showBedrockHelpDialog by remember { mutableStateOf(false) }
    var seedSetupShown by remember { mutableStateOf(false) }
    var firstBootWarningSessionDismissed by remember { mutableStateOf(false) }
    var showDownloadRequiredDialog by remember { mutableStateOf(false) }
    val configuredRuntime = stateHolder.config.customJarPath
        ?.takeIf { it.isNotBlank() }
        ?: stateHolder.config.gameVersion
    val activeRuntimeVersion = stateHolder.versionId.takeIf { it.isNotBlank() }.orEmpty()
    val effectiveServerType = if (
        stateHolder.config.serverType == ServerType.MODPACK ||
        (stateHolder.config.serverType.supportsVersionSelect && isLikelyModpackRuntimeId(configuredRuntime))
    ) {
        ServerType.MODPACK
    } else {
        stateHolder.config.serverType
    }
    val displayedRuntimeVersion = if (effectiveServerType == ServerType.MODPACK) {
        configuredRuntime.ifBlank { activeRuntimeVersion }
    } else {
        stateHolder.config.gameVersion.ifBlank { activeRuntimeVersion }
    }
    val hasActiveRuntimeSession = stateHolder.isNavigationLocked && displayedRuntimeVersion.isNotBlank()
    val isVersionDownloaded = if (effectiveServerType == ServerType.MODPACK) {
        ServerFileManager.isModpackReady(context, stateHolder.activeWorld, displayedRuntimeVersion) ||
            hasActiveRuntimeSession
    } else {
        displayedRuntimeVersion.isNotBlank() && (
            ServerFileManager.isServerJarReady(context, displayedRuntimeVersion, effectiveServerType) ||
                hasActiveRuntimeSession
        )
    }
    val animatedStartupProgress by animateFloatAsState(
        targetValue = (stateHolder.startupProgressPercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 150, easing = androidx.compose.animation.core.LinearEasing),
        label = "startup_progress"
    )
    val uiState = stateHolder.serverUiState
    val cardShadowColor = pocketCardShadowColor()
    val firstServerStartWarningDismissed by AppPreferencesStore.isFirstServerStartWarningDismissedFlow(context).collectAsState(initial = false)

    LaunchedEffect(stateHolder.status) {
        if (stateHolder.status == ServerStatus.ONLINE && !firstServerStartWarningDismissed) {
            AppPreferencesStore.setFirstServerStartWarningDismissed(context, true)
        }
    }

    // RAM feature state
    val prefs = remember { AppPreferences(context) }
    val eulaAccepted = remember(stateHolder.status, stateHolder.activeWorld) {
        val acceptedByFile = ServerFileManager.isEulaAccepted(context, stateHolder.activeWorld)
        if (acceptedByFile && !prefs.eulaAccepted) {
            prefs.eulaAccepted = true
        }
        acceptedByFile || prefs.eulaAccepted
    }
    val totalRamMb = remember { RamUtils.getTotalRamMb(context) }
    var ramMode by remember(stateHolder.config.ramMode) { mutableStateOf(stateHolder.config.ramMode) }
    var manualRamMb by remember(stateHolder.config.maxRamMb) { mutableStateOf(stateHolder.config.maxRamMb.coerceIn(512, totalRamMb)) }
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
            com.pockethost.app.data.preferences.AppPreferencesStore.isSeedSetupShownFlow(context).collect { shown ->
                seedSetupShown = shown
            }
        } catch (e: Exception) {
            android.util.Log.e("ConsoleScreen", "Error loading seed setup flag: ${e.message}")
        }
    }

    LaunchedEffect(Unit) {
        try {
            // Load world seed from preferences
            com.pockethost.app.data.preferences.AppPreferencesStore.getWorldSeedFlow(context).collect { seed ->
                worldSeed = seed
            }
        } catch (e: Exception) {
            android.util.Log.e("ConsoleScreen", "Error loading world seed: ${e.message}")
        }
    }

    // F3G tip removed

    // F3G tip removed

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

    // Poll actual Minecraft server JVM process RAM usage every 3 seconds while online.
    // We retrieve the resident set size (RSS) of the server child process.
    // The cap is the configured server RAM allocation (manualRamMb) so the bar
    // fills against what the user gave to the server.
    LaunchedEffect(stateHolder.status == ServerStatus.ONLINE) {
        if (stateHolder.status == ServerStatus.ONLINE) {
            while (stateHolder.status == ServerStatus.ONLINE) {
                usedRamMb = RamUtils.getUsedRamMb(context)
                delay(3000)
            }
        } else {
            usedRamMb = 0
        }
    }

    // Chunk loading snackbar removed as per request

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AnimatedEntranceContainer(index = 0) {
                ServerIdentityCard(
                    stateHolder = stateHolder,
                    onChangeVersion = onChangeVersion,
                    onOpenServerDetails = onOpenServerDetails,
                    onAddWorld = onAddWorld,
                    onOpenBedrockHelp = { showBedrockHelpDialog = true },
                    topContentBetweenServerAndAddress = topContentBelowServerCard,
                    onNavigateToSignUp = onNavigateToSignUp
                )
            }
        }

        if (stateHolder.status == ServerStatus.STARTING && !stateHolder.firstBootComplete && !firstBootWarningSessionDismissed) {
            item(key = "first_boot_warning") {
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = {
                        if (it == SwipeToDismissBoxValue.StartToEnd || it == SwipeToDismissBoxValue.EndToStart) {
                            firstBootWarningSessionDismissed = true
                            scope.launch {
                                AppPreferencesStore.setFirstBootComplete(context, true)
                            }
                            true
                        } else false
                    }
                )

                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = { Box(Modifier.fillMaxSize()) }
                ) {
                    val isDark = pocketIsDarkTheme()
                    val bannerColor = if (isDark) Color(0xFF3B2214) else Color(0xFFFFE8CC)
                    val bannerBorder = if (isDark) Color(0xFFE6853B).copy(alpha = 0.55f) else Color(0xFFEA580C).copy(alpha = 0.4f)
                    val headingColor = if (isDark) Color(0xFFFFD6B0) else Color(0xFF78350F)
                    val bodyColor = if (isDark) Color(0xFFFFE7D6).copy(alpha = 0.88f) else Color(0xFF92400E)
                    val accent = PocketColors.ConsoleWarn
                    
                    AnimatedEntranceContainer(index = 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .card3d(
                                    elevation = 6.dp,
                                    cornerRadius = 16.dp,
                                    borderColor = bannerBorder,
                                    depthColor = bannerBorder.copy(alpha = (bannerBorder.alpha * 1.5f).coerceAtMost(1f))
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .background(bannerColor)
                        ) {
                            Box {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "First Start Takes Longer",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                            color = headingColor,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "The first time to start server takes some time to load as it generates the world and downloads files.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = bodyColor,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(24.dp))
                                }
                                
                                IconButton(
                                    onClick = {
                                        firstBootWarningSessionDismissed = true
                                        scope.launch {
                                            AppPreferencesStore.setFirstBootComplete(context, true)
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = headingColor.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (stateHolder.status == ServerStatus.ONLINE && !stateHolder.config.whiteList && !stateHolder.openServerRiskAcknowledged) {
            item {
                AnimatedEntranceContainer(index = 2) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .card3d(
                                elevation = 6.dp,
                                cornerRadius = 16.dp,
                                borderColor = pocketWarningBorderColor(),
                                depthColor = pocketWarningBorderColor().copy(alpha = (pocketWarningBorderColor().alpha * 1.5f).coerceAtMost(1f)),
                                borderWidth = 1.dp
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .background(pocketWarningSurfaceColor())
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(pocketWarningIconChipColor(), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = pocketWarningAccentColor(),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    "Open server — anyone can join",
                                    color = pocketWarningTitleColor(),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Whitelist is off, so any player who knows the address can join until you lock it down.",
                                color = pocketWarningBodyColor(),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { stateHolder.enableWhitelist() },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, pocketWarningBorderColor()),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = pocketWarningAccentColor())
                                ) {
                                    Text("Enable Whitelist", fontSize = 12.sp)
                                }
                                TextButton(
                                    onClick = { stateHolder.acknowledgeOpenServerRisk() }
                                ) {
                                    Text("I understand the risks", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            AnimatedEntranceContainer(index = 3) {
                when (uiState) {
                    ServerUiState.IDLE -> {
                        PremiumHomeButton(
                            text = run {
                                val s = LocalAppStrings.current
                                if (stateHolder.isRestartingCycle) s.restarting else s.startServer
                            },
                            onClick = {
                                try {
                                    if (isVersionDownloaded) {
                                        stateHolder.startServer()
                                    } else if (effectiveServerType == ServerType.MODPACK) {
                                        onInstallCurrentVersion()
                                    } else {
                                        showDownloadRequiredDialog = true
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("ConsoleScreen", "Start error", e)
                                    Toast.makeText(context, "Error starting server: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !stateHolder.isRestarting && !stateHolder.isStopping,
                            icon = if (stateHolder.isRestarting) Icons.Default.Refresh else Icons.Default.PlayArrow,
                            modifier = Modifier.fillMaxWidth(),
                            style = PremiumHomeButtonStyle.StartServer
                        )
                    }
                    ServerUiState.STARTING -> {
                        val isDone = stateHolder.serverJoinable && (stateHolder.isJavaServerDone || stateHolder.startupStatusMessage == "Server ready!") && !stateHolder.isStopping
                        val displayPercent = if (isDone) 100 else stateHolder.startupProgressPercent.coerceAtMost(99)
                        val displayProgress = if (isDone) 1f else animatedStartupProgress.coerceAtMost(0.99f)
                        StartupProgressCard(
                            progress = displayProgress,
                            progressPercent = displayPercent,
                            status = stateHolder.startupStatusMessage.ifBlank { "Initializing server..." },
                            isStopping = stateHolder.isStopping,
                            onStop = {
                                stateHolder.stopServer()
                            }
                        )
                    }
                    ServerUiState.RUNNING -> {
                        if (stateHolder.isStopping && !stateHolder.isRestarting) {
                            PremiumHomeButton(
                                text = LocalAppStrings.current.stopping,
                                icon = Icons.Default.Stop,
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                                style = PremiumHomeButtonStyle.DangerGhost
                            )
                        } else if (stateHolder.isRestarting) {
                            PremiumHomeButton(
                                text = LocalAppStrings.current.restarting,
                                icon = Icons.Default.Refresh,
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                                style = PremiumHomeButtonStyle.Primary
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PremiumHomeButton(
                                    text = "Stop",
                                    icon = Icons.Default.Stop,
                                    onClick = {
                                        stateHolder.stopServer()
                                    },
                                    enabled = true,
                                    modifier = Modifier.weight(0.42f),
                                    style = PremiumHomeButtonStyle.Danger
                                )

                                PremiumHomeButton(
                                    text = "Restart",
                                    icon = Icons.Default.Refresh,
                                    onClick = {
                                        try {
                                            stateHolder.restartServer()
                                        } catch (e: Exception) {
                                            android.util.Log.e("ConsoleScreen", "Restart error", e)
                                            Toast.makeText(context, "Error during restart: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = true,
                                    modifier = Modifier.weight(0.66f),
                                    style = PremiumHomeButtonStyle.Primary
                                )
                            }
                        }
                    }
                }
            }
        }
        if (uiState == ServerUiState.RUNNING && stateHolder.onlinePlayers.isNotEmpty()) {
            item {
                AnimatedEntranceContainer(index = 4) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${LocalAppStrings.current.players} (${stateHolder.onlinePlayers.size}/${stateHolder.config.maxPlayers})",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = 0.sp
                        )
                        TextButton(onClick = onViewAllPlayers) {
                            Text(
                                text = LocalAppStrings.current.tabAllPlayers,
                                color = PocketColors.PrimaryDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            itemsIndexed(stateHolder.onlinePlayers.take(4)) { idx, player ->
                AnimatedEntranceContainer(index = 5 + idx) {
                    OnlinePlayerCard(
                        player = player,
                        stateHolder = stateHolder,
                        onOpenDetails = { onPlayerSelected(player) }
                    )
                }
            }
        }
        if (com.pockethost.app.service.MinecraftVersionPolicy.shouldShowReleaseTrain26Warning(displayedRuntimeVersion)) {
            item(key = "release_train_26_warning") {
                ReleaseTrain26WarningBanner(
                    activeVersion = displayedRuntimeVersion,
                    onChangeVersion = onChangeVersion,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            AnimatedEntranceContainer(index = 9) {
                VersionUpgradeCard(
                    serverTypeName = effectiveServerType.displayName,
                    currentVersion = displayedRuntimeVersion,
                    availableVersions = availableVersions,
                    onUpgrade = { onChangeVersion() },
                    onInstallCurrent = onInstallCurrentVersion,
                    serverIsRunning = stateHolder.isNavigationLocked,
                    isVersionDownloaded = isVersionDownloaded
                )
            }
        }
        adContentAfterVersion?.let { adContent ->
            item(key = "home_banner_ad") {
                AnimatedEntranceContainer(index = 10) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .card3d(elevation = 6.dp, cornerRadius = 20.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (pocketIsDarkTheme()) PocketColors.SurfaceCardDark else PocketColors.SurfaceCard)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "SPONSORED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            adContent()
                        }
                    }
                }
            }
        }
        // F3G tip removed UI block
        item {
            AnimatedEntranceContainer(index = 11) {
                RamSettingsCard(
                    ramMode = ramMode,
                    manualRamMb = manualRamMb,
                    totalRamMb = totalRamMb,
                    serverIsRunning = stateHolder.isNavigationLocked,
                    onRamModeChange = { mode ->
                        ramMode = mode
                        prefs.ramMode = mode
                        scope.launch(Dispatchers.IO) {
                            val repository = ServerConfigRepository(context)
                            val currentConfig = repository.loadConfig()
                            val updatedConfig = currentConfig.copy(ramMode = mode)
                            repository.saveConfig(updatedConfig)
                            withContext(Dispatchers.Main) {
                                stateHolder.refreshAll()
                            }
                        }
                    },
                    onManualRamChange = { mb ->
                        manualRamMb = mb
                        prefs.manualRamMb = mb
                        scope.launch(Dispatchers.IO) {
                            val repository = ServerConfigRepository(context)
                            val currentConfig = repository.loadConfig()
                            val updatedConfig = currentConfig.copy(maxRamMb = mb)
                            repository.saveConfig(updatedConfig)
                            withContext(Dispatchers.Main) {
                                stateHolder.refreshAll()
                            }
                        }
                    }
                )
            }
        }
        if (stateHolder.status == ServerStatus.ONLINE) {
            item {
                AnimatedEntranceContainer(index = 12) {
                    val totalPhoneRamMb = RamUtils.getTotalRamMb(context)
                    RamUsageCard(usedMb = usedRamMb, maxMb = totalPhoneRamMb)
                }
            }
        }
        item {
            AnimatedEntranceContainer(index = 13) {
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
    }

    // World Seed Dialog
    if (showDownloadRequiredDialog) {
        val downloadRequiredSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    downloadRequiredSheetState.hide()
                    showDownloadRequiredDialog = false
                }
            },
            sheetState = downloadRequiredSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("No version downloaded", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(
                    "Download a compatible game version from the Home screen before starting the server.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            downloadRequiredSheetState.hide()
                            showDownloadRequiredDialog = false
                            onChangeVersion()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Download")
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            downloadRequiredSheetState.hide()
                            showDownloadRequiredDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    // World Seed Dialog
    if (showSeedDialog) {
        val seedSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { },
            sheetState = seedSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 10.dp,
                border = BorderStroke(1.dp, pocketSheetBorderColor())
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
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
                        shape = duoTextFieldShape(),
                        colors = duoOutlinedTextFieldColors()
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
                                scope.launch {
                                    seedSheetState.hide()
                                    showSeedDialog = false
                                    try {
                                        com.pockethost.app.data.preferences.AppPreferencesStore.setSeedSetupShown(context, true)
                                    } catch (e: Exception) {
                                        android.util.Log.e("ConsoleScreen", "Error marking seed setup as shown: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        TextButton(
                            onClick = {
                                scope.launch {
                                    seedSheetState.hide()
                                    showSeedDialog = false
                                }
                            },
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
        val bedrockHelpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val publicAddress = stateHolder.publicAddress?.takeIf { it.isNotBlank() }
        val hasPublicAddress = publicAddress != null
        
        val publicHost = publicAddress?.substringBefore(":") ?: "Offline"
        val publicPort = publicAddress?.substringAfter(":", "19132") ?: "19132"
        
        val wifiHost = stateHolder.localIp
        
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copyToClipboard: (String, String) -> Unit = { text, label ->
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "$label copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    bedrockHelpSheetState.hide()
                    showBedrockHelpDialog = false
                }
            },
            sheetState = bedrockHelpSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 10.dp,
                border = BorderStroke(1.dp, pocketSheetBorderColor())
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "How to Join",
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = Monocraft,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "To let other players join your server, share the connection details below:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    if (hasPublicAddress) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🌐 Internet Connection Details", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PocketColors.Primary)
                            
                            // Host Box
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("SERVER ADDRESS (HOST)", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                        Text(publicHost, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Monocraft, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    IconButton(
                                        onClick = { copyToClipboard(publicHost, "Server Address") },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Address",
                                            tint = PocketColors.Primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            
                            // Port Box
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("PORT", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                        Text(publicPort, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Monocraft, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    IconButton(
                                        onClick = { copyToClipboard(publicPort, "Port") },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Port",
                                            tint = PocketColors.Primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "⚠️ Bedrock players: Enter the Port in your game client options!",
                                    modifier = Modifier.padding(10.dp),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Start the server to generate internet join addresses.",
                                modifier = Modifier.padding(16.dp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    val serverType = stateHolder.config.serverType
                    val isGeyserCompatible = serverType == ServerType.PAPER || serverType == ServerType.PURPUR

                    if (isGeyserCompatible) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFE8F5E9),
                            border = BorderStroke(1.dp, Color(0xFF81C784)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✅", fontSize = 14.sp)
                                Text(
                                    text = "Bedrock compatibility is bundled for this server type (${serverType.displayName}).",
                                    fontSize = 11.sp,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚠️", fontSize = 14.sp)
                                Text(
                                    text = "The bundled Bedrock bridge only works with Paper or Purpur. ${serverType.displayName} worlds should be switched to Paper if Bedrock players need to join.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🎮 How to Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        JoinStepCard(
                            step = "Java",
                            title = "PC/Mac players",
                            body = "Go to Multiplayer > Add Server. Enter the full Server Address as: $publicHost:$publicPort"
                        )
                        JoinStepCard(
                            step = "Bedrock",
                            title = "Mobile/Windows 10/Console",
                            body = "Go to Servers > Add Server. Enter Server Address: $publicHost and Port: $publicPort separately."
                        )
                        JoinStepCard(
                            step = "LAN",
                            title = "Same Wi-Fi players",
                            body = "If playing in the same room, Bedrock players can connect using local IP $wifiHost and default Bedrock port 19132."
                        )
                    }

                    DuoButton(
                        text = "GOT IT",
                        onClick = {
                            scope.launch {
                                bedrockHelpSheetState.hide()
                                showBedrockHelpDialog = false
                            }
                        },
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
                com.pockethost.app.data.preferences.AppPreferencesStore.setWorldSeed(context, worldSeed)
            } catch (e: Exception) {
                android.util.Log.e("ConsoleScreen", "Error saving world seed: ${e.message}")
            }
        }
    }
}

@Composable
private fun StartupProgressCard(
    progress: Float,
    progressPercent: Int,
    status: String,
    isStopping: Boolean,
    onStop: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val resolvedBgColor = if (isDark) PocketColors.SurfaceCardDark else PocketColors.SurfaceCard
    val sideColor = if (isDark) PocketColors.CardBorderDark else PocketColors.CardBorder
    val bottomColor = if (isDark) PocketColors.CardBorderBottomDark else PocketColors.CardBorderBottom
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .card3d(
                elevation = 6.dp,
                cornerRadius = 18.dp,
                borderColor = sideColor,
                depthColor = bottomColor
            )
            .clip(shape)
            .background(resolvedBgColor)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(PocketColors.Primary.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = PocketColors.Primary,
                        strokeWidth = 3.dp
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isStopping) "Stopping startup..." else "Starting your server",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isStopping) "Safely closing server processes" else "You can stop this at any time",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PocketColors.Primary.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "$progressPercent%",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = PocketColors.Primary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.04f, 1f))
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(PocketColors.Primary)
                )
            }

            Text(
                text = status,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            PremiumHomeButton(
                text = if (isStopping) "STOPPING..." else "STOP STARTUP",
                icon = Icons.Default.Stop,
                onClick = onStop,
                enabled = !isStopping,
                modifier = Modifier.fillMaxWidth(),
                style = PremiumHomeButtonStyle.Danger
            )
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
    onOpenBedrockHelp: () -> Unit,
    topContentBetweenServerAndAddress: (@Composable () -> Unit)? = null,
    onNavigateToSignUp: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val billingManager = remember { BillingManager.getInstance(context) }
    val isPremium by billingManager.isPremium.collectAsState()
    val entitlement by billingManager.entitlement.collectAsState()
    val customSubdomainEnabled by com.pockethost.app.config.RemoteConfigManager.customSubdomainEnabled.collectAsState(initial = false)
    val premiumPurchaseEnabled by com.pockethost.app.config.RemoteConfigManager.premiumPurchaseEnabled.collectAsState(initial = false)
    var showPremiumBottomSheet by remember { mutableStateOf(false) }
    var showIpBottomSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val serverRunning = stateHolder.status == ServerStatus.ONLINE
    val serverProcessActive = stateHolder.status == ServerStatus.ONLINE ||
        stateHolder.status == ServerStatus.STARTING ||
        stateHolder.status == ServerStatus.RESTARTING
    val canChangeWorld = stateHolder.status == ServerStatus.OFFLINE
    val isDarkTheme = pocketIsDarkTheme()
    val accentGreen = Color(0xFF3D8B45)
    val accentText = if (isDarkTheme) PocketColors.PrimaryLight else PocketColors.PrimaryDark
    var showWorldSheet by remember { mutableStateOf(false) }
    var showDeleteWorldDialog by remember { mutableStateOf<WorldEntry?>(null) }
    val activeWorld = stateHolder.worlds.firstOrNull { it.isActive } ?: stateHolder.worlds.firstOrNull()
    val worldItems = stateHolder.worlds
    var worldDay by remember(stateHolder.activeWorld) { mutableStateOf<Long?>(null) }

    LaunchedEffect(stateHolder.activeWorld, stateHolder.status) {
        suspend fun loadOfflineDay() {
            worldDay = withContext(Dispatchers.IO) {
                NBTParser.parseLevelDay(
                    PlayerDataManager.getLevelDataFile(context, stateHolder.activeWorld)
                )
            }
        }

        loadOfflineDay()
        while (stateHolder.status == ServerStatus.ONLINE) {
            delay(30_000)
            loadOfflineDay()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            GameCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showWorldSheet = true }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = LocalAppStrings.current.yourServer,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDarkTheme) PocketColors.PrimaryLight.copy(alpha = 0.78f) else PocketColors.TextSection,
                        letterSpacing = 0.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .card3d(
                                    elevation = 4.dp,
                                    cornerRadius = 12.dp,
                                    borderColor = if (isDarkTheme) PocketColors.CardBorderDark else PocketColors.TagBorder,
                                    depthColor = if (isDarkTheme) PocketColors.CardBorderBottomDark else PocketColors.TagBorderBottom
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDarkTheme) PocketColors.SurfaceVarDark else PocketColors.TagBg),
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
                                Image(
                                    painter = painterResource(id = R.drawable.app_logo_light),
                                    contentDescription = "Server logo",
                                    modifier = Modifier.fillMaxSize().padding(5.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stateHolder.serverName.ifBlank { stateHolder.activeWorld.ifBlank { "world" } },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                            }
                            Text(
                                text = if (stateHolder.serverDescription.isBlank() || stateHolder.serverDescription.contains("Pocketcraft", ignoreCase = true) || stateHolder.serverDescription.contains("PocketCraft", ignoreCase = true)) {
                                    "Hosted on PocketHost"
                                } else {
                                    stateHolder.serverDescription
                                },
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .pill3d(
                                            elevation = 4.dp,
                                            borderColor = if (isDarkTheme) PocketColors.CardBorderDark else PocketColors.TagBorder,
                                            depthColor = if (isDarkTheme) PocketColors.CardBorderBottomDark else PocketColors.TagBorderBottom,
                                            borderWidth = 1.5.dp,
                                            depthWidth = 2.dp
                                        )
                                        .clip(RoundedCornerShape(50.dp))
                                        .background(if (isDarkTheme) PocketColors.SurfaceVarDark else PocketColors.TagBg)
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                ) {
                                    val tagTextColor = if (isDarkTheme) PocketColors.TextDark.copy(alpha = 0.86f) else PocketColors.TagText
                                    Icon(
                                        imageVector = Icons.Filled.Dns,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = tagTextColor
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stateHolder.activeWorld.ifBlank { "world" },
                                        fontSize = 11.sp,
                                        color = tagTextColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                worldDay?.let { day ->
                                    val tagTextColor = if (isDarkTheme) PocketColors.TextDark.copy(alpha = 0.86f) else PocketColors.TagText
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .pill3d(
                                                elevation = 4.dp,
                                                borderColor = if (isDarkTheme) PocketColors.CardBorderDark else PocketColors.TagBorder,
                                                depthColor = if (isDarkTheme) PocketColors.CardBorderBottomDark else PocketColors.TagBorderBottom,
                                                borderWidth = 1.5.dp,
                                                depthWidth = 2.dp
                                            )
                                            .clip(RoundedCornerShape(50.dp))
                                            .background(if (isDarkTheme) PocketColors.SurfaceVarDark else PocketColors.TagBg)
                                            .padding(horizontal = 9.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Day $day",
                                            fontSize = 11.sp,
                                            color = tagTextColor,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        color = if (isDarkTheme) PocketColors.CardBorderDark.copy(alpha = 0.5f) else PocketColors.CardBorder.copy(alpha = 0.55f)
                    )
                    if (stateHolder.isNavigationLocked) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(PocketColors.Primary.copy(alpha = 0.10f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = PocketColors.Primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Heads up: keep your phone on a cool surface while the server is running.",
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = if (canChangeWorld) LocalAppStrings.current.tapToSwap else LocalAppStrings.current.stopToSwitch,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                if (stateHolder.serverUiState != ServerUiState.IDLE) {
                    val statusColor = when (stateHolder.status) {
                        ServerStatus.ONLINE -> PocketColors.Online
                        ServerStatus.STARTING, ServerStatus.RESTARTING -> PocketColors.Starting
                        ServerStatus.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        modifier = Modifier
                            .card3d(
                                elevation = 4.dp,
                                borderColor = when (stateHolder.status) {
                                    ServerStatus.ONLINE -> PocketColors.Online.copy(alpha = 0.5f)
                                    ServerStatus.STARTING, ServerStatus.RESTARTING -> PocketColors.Starting.copy(alpha = 0.5f)
                                    ServerStatus.OFFLINE -> PocketColors.Offline.copy(alpha = 0.5f)
                                },
                                depthColor = when (stateHolder.status) {
                                    ServerStatus.ONLINE -> PocketColors.Online.copy(alpha = 0.3f)
                                    ServerStatus.STARTING, ServerStatus.RESTARTING -> PocketColors.Starting.copy(alpha = 0.3f)
                                    ServerStatus.OFFLINE -> PocketColors.Offline.copy(alpha = 0.3f)
                                },
                                cornerRadius = 10.dp,
                                borderWidth = 1.dp
                            )
                            .clip(RoundedCornerShape(10.dp)),
                        color = when (stateHolder.status) {
                            ServerStatus.ONLINE -> PocketColors.PrimaryMuted
                            ServerStatus.STARTING, ServerStatus.RESTARTING -> PocketColors.Starting.copy(alpha = 0.18f)
                            ServerStatus.OFFLINE -> PocketColors.Offline.copy(alpha = 0.14f)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(statusColor),
                                contentAlignment = Alignment.Center
                            ) {
                                if (stateHolder.status == ServerStatus.ONLINE) {
                                    Box(
                                        modifier = Modifier
                                            .size(3.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                }
                            }

                            Text(
                                text = stateHolder.status.name,
                                color = when (stateHolder.status) {
                                    ServerStatus.ONLINE -> PocketColors.PrimaryDark
                                    ServerStatus.STARTING, ServerStatus.RESTARTING -> PocketColors.Starting
                                    ServerStatus.OFFLINE -> PocketColors.Offline
                                },
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }
            }
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
                        if (worldItems.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "No saved worlds yet",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Create a world to see it listed here.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(worldItems) { world ->
                                WorldSelectorCard(
                                    world = world,
                                    canDelete = canChangeWorld || !world.isActive,
                                    enabled = canChangeWorld && !world.isActive,
                                    onClick = {
                                        scope.launch {
                                            val msg = stateHolder.setActiveWorld(world.name)
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            if (msg.startsWith("Active world switched")) {
                                                showWorldSheet = false
                                                showDeleteWorldDialog = null
                                            }
                                        }
                                    },
                                    onRequestDelete = { showDeleteWorldDialog = world }
                                )
                            }
                        }

                        item {
                            WorldSelectorAddCard(
                                enabled = true,
                                isPremiumUnlocked = isPremium,
                                onClick = {
                                    showWorldSheet = false
                                    if (!isPremium) {
                                        if (premiumPurchaseEnabled) {
                                            showPremiumBottomSheet = true
                                        } else {
                                            Toast.makeText(context, "More world slots are still in staged rollout.", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        onAddWorld()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        showDeleteWorldDialog?.let { world ->
            val deleteWorldSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = {
                    scope.launch {
                        deleteWorldSheetState.hide()
                        showDeleteWorldDialog = null
                    }
                },
                sheetState = deleteWorldSheetState,
                dragHandle = null,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Delete World?", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Text(
                        if (world.isActive) {
                            "Delete ${world.name}? PocketCraft will switch to another saved world first."
                        } else {
                            "Delete ${world.name}? This removes the world from storage."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                val msg = stateHolder.deleteWorld(world.name)
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                deleteWorldSheetState.hide()
                                showDeleteWorldDialog = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("DELETE", color = PocketColors.Offline, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                deleteWorldSheetState.hide()
                                showDeleteWorldDialog = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CANCEL")
                    }
                }
            }
        }

        if (showPremiumBottomSheet) {
            com.pockethost.app.ui.components.PremiumUpgradeBottomSheet(
                onDismissRequest = { showPremiumBottomSheet = false },
                onNavigateToSignUp = onNavigateToSignUp
            )
        }

        if (showIpBottomSheet) {
            IpBottomSheet(
                entitlement = entitlement,
                isPremiumUnlocked = isPremium,
                rolloutEnabled = customSubdomainEnabled,
                onDismissRequest = { showIpBottomSheet = false },
                onMessage = { msg ->
                    scope.launch {
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onNavigateToSignUp = onNavigateToSignUp
            )
        }

        topContentBetweenServerAndAddress?.invoke()

        val publicAddress = stateHolder.publicAddress?.takeIf { it.isNotBlank() }
        val relayConnecting = stateHolder.tunnelConnecting
        val relaySwitching = relayConnecting && stateHolder.relaySwitchInProgress
        val isOfflineMode = !com.pockethost.app.util.NetworkUtils.isOnline(context)
        val internetRelayAddress = when {
            isOfflineMode -> "Offline Mode (Local Wi-Fi / LAN Only)"
            relaySwitching -> "Switching relay connection..."
            relayConnecting && stateHolder.isRunning -> "Reconnecting to the servers..."
            relayConnecting -> "Opening internet relay..."
            !publicAddress.isNullOrBlank() -> publicAddress
            else -> "Opening internet relay..."
        }

        val localWifiAddress = stateHolder.localIp
            .takeIf(::isShareableLanIp)
            ?.let { "$it:${stateHolder.config.port}" }
        val relayReady = !publicAddress.isNullOrBlank()
        val canShareAddresses = (stateHolder.status == ServerStatus.ONLINE || stateHolder.isRunning || stateHolder.isJavaServerDone || stateHolder.serverJoinable) && !stateHolder.isStopping




        val joinCardShadowColor = pocketCardShadowColor()
        val joinCardBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

        AnimatedVisibility(
            visible = canShareAddresses,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .card3d(elevation = 6.dp, cornerRadius = 18.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (pocketIsDarkTheme()) PocketColors.SurfaceCardDark else PocketColors.SurfaceCard)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            letterSpacing = 0.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                relayConnecting -> PocketColors.Starting.copy(alpha = 0.15f)
                                relayReady -> PocketColors.Online.copy(alpha = 0.15f)
                                else -> PocketColors.Offline.copy(alpha = 0.15f)
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            when {
                                                relayConnecting -> PocketColors.Starting
                                                relayReady -> PocketColors.Online
                                                else -> PocketColors.Offline
                                            },
                                            CircleShape
                                        )
                                )
                                Text(
                                    when {
                                        relaySwitching -> "Relay Switching"
                                        relayConnecting && stateHolder.isRunning -> "Reconnecting"
                                        relayConnecting -> "Relay Connecting"
                                        relayReady -> "Relay Ready"
                                        stateHolder.tunnelError != null -> "Relay Error"
                                        else -> "Relay Offline"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        relayConnecting -> PocketColors.Starting
                                        relayReady -> PocketColors.Online
                                        else -> PocketColors.Offline
                                    }
                                )
                            }
                        }
                    }

                    AddressValueRow(
                        label = "Internet relay",
                        address = internetRelayAddress,
                        emphasized = true,
                        onEditClick = {
                            showIpBottomSheet = true
                        },
                        onRetryClick = { stateHolder.reconnectRelay() }
                    )

                    AddressValueRow(
                        label = "Wi-Fi (Java)",
                        address = localWifiAddress ?: "Wi-Fi address unavailable",
                        emphasized = false
                    )

                    PremiumHomeButton(
                        text = "How to Join",
                        icon = Icons.Filled.Info,
                        onClick = onOpenBedrockHelp,
                        modifier = Modifier.fillMaxWidth(),
                        style = PremiumHomeButtonStyle.Secondary
                    )

                    PremiumHomeButton(
                        text = "Share Join Addresses",
                        icon = Icons.Default.Share,
                        onClick = {
                            shareServerAddresses(
                                context = context,
                                internetAddress = publicAddress,
                                lanAddress = localWifiAddress.takeIf { stateHolder.isServerFullyReady }
                            )
                        },
                        enabled = canShareAddresses && (!publicAddress.isNullOrBlank() || localWifiAddress != null),
                        modifier = Modifier.fillMaxWidth(),
                        style = PremiumHomeButtonStyle.Secondary
                    )
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
            color = pocketPopupAccentContainerColor()
        ) {
            Text(
                text = step,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                color = pocketPopupAccentTintColor(),
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
        modifier = Modifier.card3d(elevation = 4.dp, cornerRadius = 18.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = pocketPopupAccentContainerColor()
            ) {
                Text(
                    text = step,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    color = pocketPopupAccentTintColor(),
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
    canDelete: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target == SwipeToDismissBoxValue.EndToStart && canDelete) {
                onRequestDelete()
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = canDelete,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(98.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PocketColors.Offline.copy(alpha = 0.14f))
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Delete", color = PocketColors.Offline, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Icon(Icons.Default.Delete, contentDescription = null, tint = PocketColors.Offline)
                }
            }
        }
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
                        PocketWorldIcon(modifier = Modifier.size(36.dp))
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
}

@Composable
private fun WorldSelectorAddCard(
    enabled: Boolean,
    isPremiumUnlocked: Boolean,
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
                    text = if (isPremiumUnlocked) {
                        "Create a new world without replacing others"
                    } else {
                        "Pro required to create extra worlds"
                    },
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
        subtitle = player.pingText(),
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
    emphasized: Boolean,
    onEditClick: (() -> Unit)? = null,
    onRetryClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = address,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                softWrap = true,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-0.2).sp,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onRetryClick != null) {
                    IconButton(
                        onClick = onRetryClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry Connection",
                            tint = PocketColors.Starting,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (onEditClick != null) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit IP",
                            tint = PocketColors.Online,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                val canCopy = address.isNotBlank() &&
                    address != "Wi-Fi address unavailable" &&
                    address != "No relay address" &&
                    address != "No internet" &&
                    address != "Opening internet relay..." &&
                    address != "Reconnecting to the servers..." &&
                    address != "Switching relay connection..." &&
                    address != "Start the server to generate internet join addresses."
                if (canCopy) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Address", address)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Address copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Address",
                            tint = PocketColors.Primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumHomeButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: PremiumHomeButtonStyle = PremiumHomeButtonStyle.Secondary,
    cornerRadius: Dp = 15.dp
) {
    // ── Colors — matched to HTML reference design ────────────────────────
    val bgColor: Color
    val borderColor: Color
    val bottomBorderColor: Color
    val contentColor: Color

    val isDark = pocketIsDarkTheme()

    if (!enabled) {
        bgColor = if (isDark) PocketColors.SurfaceVarDark.copy(alpha = 0.72f) else PocketColors.InactiveBg
        borderColor = if (isDark) PocketColors.CardBorderDark.copy(alpha = 0.52f) else PocketColors.InactiveBorder
        bottomBorderColor = if (isDark) PocketColors.CardBorderBottomDark.copy(alpha = 0.62f) else PocketColors.InactiveBorderBottom
        contentColor = if (isDark) PocketColors.TextDark.copy(alpha = 0.48f) else PocketColors.TextMuted
    } else {
        when (style) {
            // HTML: bg #4a8a4a, border #2a6a2a, depth #1a5a1a
            PremiumHomeButtonStyle.StartServer -> {
                bgColor = PocketColors.Primary          // #4a8a4a
                borderColor = PocketColors.PrimaryBorder    // #2a6a2a
                bottomBorderColor = PocketColors.PrimaryBorderBottom  // #1a5a1a
                contentColor = PocketColors.PrimaryText     // #e8f8e0
            }
            PremiumHomeButtonStyle.Primary -> {
                bgColor = PocketColors.Primary
                borderColor = PocketColors.PrimaryBorder
                bottomBorderColor = PocketColors.PrimaryBorderBottom
                contentColor = PocketColors.PrimaryText
            }
            PremiumHomeButtonStyle.Secondary -> {
                bgColor = if (isDark) PocketColors.SurfaceVarDark else PocketColors.InactiveBg           // #ddeece
                borderColor = if (isDark) PocketColors.CardBorderDark else PocketColors.InactiveBorder    // #b0cc98
                bottomBorderColor = if (isDark) PocketColors.CardBorderBottomDark else PocketColors.InactiveBorderBottom // #90b878
                contentColor = if (isDark) PocketColors.TextDark.copy(alpha = 0.76f) else PocketColors.InactiveText     // #5a7a5a
            }
            PremiumHomeButtonStyle.DangerGhost -> {
                bgColor = if (isDark) Color(0xFF331A1F) else PocketColors.DangerBg.copy(alpha = 0.10f)
                borderColor = if (isDark) PocketColors.Offline.copy(alpha = 0.44f) else PocketColors.DangerBorder.copy(alpha = 0.35f)
                bottomBorderColor = if (isDark) Color(0xFF682431) else PocketColors.DangerBorderBottom.copy(alpha = 0.50f)
                contentColor = if (isDark) Color(0xFFFF8A9A) else PocketColors.Offline
            }
            PremiumHomeButtonStyle.Danger -> {
                bgColor = Color(0xFFE02424)
                borderColor = Color(0xFFB91C1C)
                bottomBorderColor = Color(0xFF7F1D1D)
                contentColor = Color.White
            }
        }
    }

    // ── Press state ──────────────────────────────────────────────────────────
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val isDangerGhost = style == PremiumHomeButtonStyle.DangerGhost
    val isDanger = style == PremiumHomeButtonStyle.Danger
    val restingBorder = when (style) {
        PremiumHomeButtonStyle.DangerGhost -> 2.dp
        PremiumHomeButtonStyle.Danger -> 4.dp
        PremiumHomeButtonStyle.StartServer -> 3.dp
        else -> 3.dp
    }
    val targetBorder = if (pressed && enabled) 1.5.dp else restingBorder
    val targetOffset = if (pressed && enabled && !isDangerGhost) (restingBorder - 1.5.dp) else 0.dp

    val offsetY by animateDpAsState(
        targetValue = targetOffset,  // 0.dp for DangerGhost (targetOffset guards with !isDangerGhost)
        animationSpec = tween(80),
        label = "premium_btn_offset"
    )
    val bottomBorderDp by animateDpAsState(
        targetValue = targetBorder,
        animationSpec = tween(80),
        label = "premium_btn_bottom_border"
    )

    val shape = RoundedCornerShape(cornerRadius)

    val widthModifier = Modifier.fillMaxWidth()

    Box(
        modifier = modifier
            .padding(bottom = if (isDangerGhost) 2.dp else 4.dp) // space for bottom border overhang
            .offset(y = offsetY)
    ) {
        Box(
            modifier = Modifier
                .then(widthModifier)
                .heightIn(min = if (isDanger) 56.dp else 52.dp)
                // Raised border drawn BEFORE clip — bottom edge stays visible
                .card3d(
                    elevation = if (isDangerGhost) 4.dp else if (isDanger) 9.dp else 8.dp,
                    cornerRadius = cornerRadius,
                    borderColor = borderColor,
                    depthColor = bottomBorderColor,
                    borderWidth = if (isDangerGhost) 1.dp else if (isDanger) 2.dp else 1.5.dp,
                    depthWidth = bottomBorderDp
                )
                .clip(shape)
                .background(bgColor)
                .clickable(
                    enabled           = enabled,
                    interactionSource = interactionSource,
                    indication        = null,
                    onClick           = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (isDanger) 18.dp else if (style == PremiumHomeButtonStyle.StartServer || style == PremiumHomeButtonStyle.Primary) 17.dp else 16.dp),
                    tint = contentColor
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily    = ButtonFont,
                        fontWeight    = if (style == PremiumHomeButtonStyle.StartServer || style == PremiumHomeButtonStyle.Primary || style == PremiumHomeButtonStyle.Danger) FontWeight.Bold else FontWeight.Normal,
                        fontSize      = if (isDanger) 14.sp else if (isDangerGhost) 13.sp else 14.sp,
                        letterSpacing = 0.sp
                    ),
                    maxLines = 2,
                    softWrap = true,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

private enum class PremiumHomeButtonStyle {
    StartServer,
    Primary,
    Secondary,
    DangerGhost,
    Danger
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
                appendLine("PocketHost join addresses")
                appendLine()
                appendLine("Internet: ${internetAddress?.takeIf { it.isNotBlank() } ?: "Unavailable right now"}")
                append("Wi-Fi: ${lanAddress?.takeIf { it.isNotBlank() } ?: "Unavailable right now"}")
            }
        )
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share server address"))
}

private fun isShareableLanIp(ip: String): Boolean {
    val trimmed = ip.trim()
    return trimmed.isNotBlank() &&
        trimmed != "0.0.0.0" &&
        trimmed != "127.0.0.1" &&
        !trimmed.startsWith("127.") &&
        !trimmed.startsWith("169.254.") &&
        !trimmed.contains(":")
}

@Composable
private fun ConsoleCard(
    stateHolder: ServerStateHolder,
    command: String,
    onCommandChange: (String) -> Unit,
    onSend: () -> Unit,
    logListState: androidx.compose.foundation.lazy.LazyListState
) {
    val context = LocalContext.current
    var isUploading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Dark-card shadow (agent spec: blur=14, offsetY=5, alpha=0.18)
            .card3d(
                elevation = 6.dp,
                cornerRadius = 16.dp,
                borderColor = PocketColors.ConsoleBorder,
                depthColor = PocketColors.ConsoleBorderBottom
            )
            .clip(RoundedCornerShape(16.dp))
            .background(PocketColors.ConsoleBg)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = LocalAppStrings.current.console,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = DMMono,
                fontSize = 12.sp,
                letterSpacing = 0.sp,
                color = PocketColors.ConsoleDim
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val logsToCopy = stateHolder.logs
                        val clip = android.content.ClipData.newPlainText("PocketHost Logs", logsToCopy.joinToString("\n"))
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Copy logs",
                        modifier = Modifier.size(18.dp),
                        tint = PocketColors.ConsoleBright.copy(alpha = 0.85f)
                    )
                }
                TextButton(onClick = stateHolder::clearLogs) {
                    Text(
                        text = LocalAppStrings.current.clearLog,
                        fontSize = 12.sp,
                        fontFamily = ButtonFont,
                        color = PocketColors.ConsoleBright.copy(alpha = 0.85f)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PocketColors.ConsoleBg.copy(alpha = 0.92f))
                .padding(10.dp)
        ) {
            val displayedLogs = if (stateHolder.status == ServerStatus.OFFLINE) {
                emptyList()
            } else {
                stateHolder.logs
            }

            if (displayedLogs.isEmpty()) {
                Text(
                    text = "Console output will print here",
                    fontFamily = DMMono,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = PocketColors.ConsoleBright
                )
            } else {
                LazyColumn(
                    state = logListState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = displayedLogs,
                        key = { index, _ -> index }
                    ) { _, line ->
                        Text(
                            text = line,
                            fontFamily = DMMono,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            color = PocketColors.ConsoleBright
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
                shape = duoTextFieldShape(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = DMMono
                ),
                colors = duoOutlinedTextFieldColors()
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
            val ramStrings = LocalAppStrings.current
            Text(
                text = ramStrings.ramAllocation,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
            
            val isEnabled = !serverIsRunning

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("low" to ramStrings.ramLow, "manual" to ramStrings.ramManual, "full" to ramStrings.ramFull).forEach { (mode, label) ->
                    val selected = ramMode == mode
                    RamPillButton(
                        text = label,
                        selected = selected,
                        enabled = isEnabled,
                        onClick = { if (isEnabled) onRamModeChange(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val recommendedMb = (totalRamMb * 0.25).toLong().coerceIn(512L, 1024L).toInt()
            val summaryText = when (ramMode) {
                "full" -> ramStrings.ramHighPerf
                "manual" -> "Custom: ${manualRamMb} MB"
                else -> "Balanced preset — safe for most devices"
            }
            Text(
                text = summaryText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (ramMode == "manual") {
                val maxAllowedRamMb = (totalRamMb * 0.90f).toInt().coerceAtLeast(1024)
                val maxManualMb = ((maxAllowedRamMb / 256) * 256).coerceAtLeast(512)
                val stepsCount = ((maxManualMb - 512) / 256).coerceAtLeast(1)
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
                    enabled = isEnabled
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("512 MB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Recommended: ${recommendedMb} MB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${maxManualMb} MB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun RamPillButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val isDark = pocketIsDarkTheme()

    val bgColor = if (selected) PocketColors.Primary else if (isDark) PocketColors.SurfaceVarDark else PocketColors.TagBg
    val borderColor = if (selected) PocketColors.PrimaryBorder else if (isDark) PocketColors.CardBorderDark else PocketColors.TagBorder
    val bottomBorderColor = if (selected) PocketColors.PrimaryBorderBottom else if (isDark) PocketColors.CardBorderBottomDark else PocketColors.TagBorderBottom
    val contentColor = if (selected) PocketColors.PrimaryText else if (isDark) PocketColors.TextDark.copy(alpha = 0.74f) else PocketColors.InactiveText

    val finalBgColor = if (enabled) bgColor else if (isDark) PocketColors.SurfaceVarDark.copy(alpha = 0.68f) else PocketColors.InactiveBg
    val finalBorderColor = if (enabled) borderColor else if (isDark) PocketColors.CardBorderDark.copy(alpha = 0.45f) else PocketColors.InactiveBorder
    val finalBottomBorderColor = if (enabled) bottomBorderColor else if (isDark) PocketColors.CardBorderBottomDark.copy(alpha = 0.55f) else PocketColors.InactiveBorder
    val finalContentColor = if (enabled) contentColor else if (isDark) PocketColors.TextDark.copy(alpha = 0.44f) else PocketColors.TextMuted

    val shape = RoundedCornerShape(50.dp)
    val targetBorder = if (pressed && enabled) 1.dp else 3.dp
    val targetOffset = if (pressed && enabled) 1.5.dp else 0.dp

    val offsetY by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = tween(80),
        label = "ram_pill_offset"
    )
    val bottomBorderDp by animateDpAsState(
        targetValue = targetBorder,
        animationSpec = tween(80),
        label = "ram_pill_bottom_border"
    )

    Box(
        modifier = modifier
            .padding(bottom = 3.dp)
            .offset(y = offsetY)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .pill3d(
                    elevation = 4.dp,
                    borderColor = finalBorderColor,
                    depthColor = finalBottomBorderColor,
                    depthWidth = bottomBorderDp
                )
                .clip(shape)
                .background(finalBgColor)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = finalContentColor,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = ButtonFont,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    letterSpacing = 0.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = PocketColors.Primary
                    )
                    Text(
                        text = "RAM Usage",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
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

private fun isLikelyModpackRuntimeId(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return false
    if (trimmed.matches(Regex("""\d+(?:\.\d+){1,3}(?:[-+][A-Za-z0-9_.-]+)?"""))) return false
    return trimmed.any { it.isLetter() } && trimmed.any { it == '-' || it == '_' }
}
