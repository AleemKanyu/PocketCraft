package com.pocketcraft.server.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pocketcraft.server.R
import com.pocketcraft.server.data.model.PlayerInfo
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.service.ServerFileManager
import com.pocketcraft.server.ui.components.ChunkyProgressBanner
import com.pocketcraft.server.ui.navigation.PocketBottomNav
import com.pocketcraft.server.ui.navigation.ReverseCurvedFooterShape
import com.pocketcraft.server.ui.navigation.PocketTab
import com.pocketcraft.server.ui.navigation.PocketTopBar
import com.pocketcraft.server.ui.theme.Monocraft
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.theme.PocketMotion
import com.pocketcraft.server.ui.theme.pocketCardBorderColor
import com.pocketcraft.server.ui.theme.pocketDecoratedBackground
import com.pocketcraft.server.ui.theme.pocketGlassCardBrush
import com.pocketcraft.server.ui.theme.pocketGlassControlBrush
import com.pocketcraft.server.ui.theme.card3d
import com.pocketcraft.server.ui.theme.pocketPremiumActionBrush
import com.pocketcraft.server.ui.theme.pocketIsDarkTheme
import com.pocketcraft.server.ui.theme.button3d
import com.pocketcraft.server.ui.theme.buttonDropShadow
import com.pocketcraft.server.ui.util.MobTheme
import com.pocketcraft.server.ui.util.playAppHaptic
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.pocketcraft.server.service.BackupProgressTracker
import com.pocketcraft.server.ui.components.BackupProgressBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    stateHolder: ServerStateHolder,
    onChangeVersion: () -> Unit,
    onInstallCurrentVersion: () -> Unit = onChangeVersion,
    onVersionSelected: (String) -> Unit,
    onRequestExit: () -> Unit,
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    currentMobTheme: MobTheme,
    onMobThemeChange: (MobTheme) -> Unit,
    homeTopContent: (@Composable () -> Unit)? = null
) {
    val stateTrigger by stateHolder.stateUpdateTrigger.collectAsState(initial = 0)
    remember(stateTrigger) { stateTrigger }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isFloatingChatEnabled by AppPreferencesStore.isFloatingChatEnabledFlow(context).collectAsState(initial = false)
    val isFloatingChatFirstTimeShown by AppPreferencesStore.isFloatingChatFirstTimeShownFlow(context).collectAsState(initial = false)
    val billingManager = remember { com.pocketcraft.server.billing.BillingManager.getInstance(context) }
    val isPremium by billingManager.isPremium.collectAsState()
    val entitlement by billingManager.entitlement.collectAsState()
    var showPremiumBottomSheet by remember { mutableStateOf(false) }
    var showUpgradeCelebrationAnimation by remember { mutableStateOf(false) }
    var showDiscordBadgePopup by remember { mutableStateOf(false) }
    var hasInitiallyCheckedPremium by remember { mutableStateOf(false) }

    LaunchedEffect(isPremium) {
        if (!hasInitiallyCheckedPremium) {
            hasInitiallyCheckedPremium = true
            return@LaunchedEffect
        }
        if (isPremium) {
            showUpgradeCelebrationAnimation = true
        } else {
            if (currentMobTheme == MobTheme.CUSTOM) {
                onMobThemeChange(MobTheme.CREEPER)
            }
        }
    }
    val appFeedbackEnabled by AppPreferencesStore.isSoundEnabledFlow(context).collectAsState(initial = true)
    var currentTab by remember { mutableStateOf(PocketTab.HOME) }
    var selectedPlayer by remember { mutableStateOf<PlayerInfo?>(null) }
    var showWorldSetupPage by remember { mutableStateOf(false) }
    var showLegalPage by remember { mutableStateOf(false) }
    var showConfigEditor by remember { mutableStateOf(false) }
    var showFileEditor by remember { mutableStateOf(false) }
    var showServerDetailsPage by remember { mutableStateOf(false) }
    var worldSetupCreateMode by remember { mutableStateOf(false) }
    var showSetupLoading by remember { mutableStateOf(false) }
    var setupLoadingProgress by remember { mutableStateOf(0f) }
    var settingsInitialActiveTab by remember { mutableStateOf<Int?>(null) }
    var settingsInitialAuthTab by remember { mutableStateOf<Int?>(null) }
    val backupState by BackupProgressTracker.state.collectAsState()
    val backupProgress by BackupProgressTracker.progress.collectAsState()
    var showBackupSheet by remember { mutableStateOf(false) }

    fun navigateToTab(tab: PocketTab) {
        currentTab = tab
        selectedPlayer = null
        settingsInitialActiveTab = null
        settingsInitialAuthTab = null
    }

    fun openWorldSetup(createMode: Boolean) {
        worldSetupCreateMode = createMode
        showWorldSetupPage = false
        showSetupLoading = true
    }

    LaunchedEffect(Unit) {
        val prefs = AppPreferences(context)
        if (prefs.openWorldSetupNextLaunch) {
            openWorldSetup(createMode = false)
            prefs.openWorldSetupNextLaunch = false
        }
    }

    LaunchedEffect(stateHolder.activeWorldNeedsSetup) {
        if (!stateHolder.activeWorldNeedsSetup) return@LaunchedEffect
        val alreadyShown = AppPreferencesStore.isInitialWorldSetupShownFlow(context).first()
        if (!alreadyShown) {
            openWorldSetup(createMode = false)
            AppPreferencesStore.setInitialWorldSetupShown(context, true)
        }
    }

    LaunchedEffect(showSetupLoading) {
        if (!showSetupLoading) return@LaunchedEffect
        setupLoadingProgress = 0.12f
        delay(120)
        setupLoadingProgress = 0.38f
        delay(180)
        setupLoadingProgress = 0.74f
        delay(220)
        setupLoadingProgress = 1f
        delay(120)
        showSetupLoading = false
        showWorldSetupPage = true
    }

    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    BackHandler {
        when {
            showSetupLoading -> {
                showSetupLoading = false
                setupLoadingProgress = 0f
            }
            showWorldSetupPage -> {
                showWorldSetupPage = false
                worldSetupCreateMode = false
            }
            showServerDetailsPage -> {
                showServerDetailsPage = false
            }
            showLegalPage -> {
                showLegalPage = false
                currentTab = PocketTab.SETTINGS
            }
            showConfigEditor -> {
                showConfigEditor = false
            }
            showFileEditor -> {
                showFileEditor = false
            }
            selectedPlayer != null -> {
                selectedPlayer = null
            }
            currentTab != PocketTab.HOME -> {
                currentTab = PocketTab.HOME
            }
            else -> {
                onRequestExit()
            }
        }
    }

    Scaffold(
        topBar = {
            PocketTopBar(
                relayHost = stateHolder.relayHost,
                currentMobTheme = currentMobTheme,
                onMobThemeChange = onMobThemeChange,
                relayLocked = stateHolder.isNavigationLocked,
                onRelayHostChange = { host ->
                    scope.launch {
                        showMessage(stateHolder.updateRelayHost(host))
                    }
                },
                onPremiumUpgradeClick = {
                    showPremiumBottomSheet = true
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val topPadding = padding.calculateTopPadding()
        val bottomPadding = padding.calculateBottomPadding()

        // Full-screen box — nav floats as an overlay at the bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding)
                    .pocketDecoratedBackground(),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    when {
                        showSetupLoading -> SplashScreen(
                            progress = setupLoadingProgress,
                            status = "Preparing setup..."
                        )

                        showWorldSetupPage -> WorldSetupScreen(
                            stateHolder = stateHolder,
                            createMode = worldSetupCreateMode,
                            onVersionSelected = onVersionSelected,
                            onBack = { showWorldSetupPage = false },
                            onMessage = showMessage,
                            onComplete = { showWorldSetupPage = false },
                            onNavigateToSignUp = {
                                showWorldSetupPage = false
                                settingsInitialActiveTab = 2
                                settingsInitialAuthTab = 1
                                navigateToTab(PocketTab.SETTINGS)
                            }
                        )

                        showServerDetailsPage -> ServerDetailsScreen(
                            stateHolder = stateHolder,
                            onBack = { showServerDetailsPage = false },
                            onMessage = showMessage,
                            onNavigateToSignUp = {
                                showServerDetailsPage = false
                                settingsInitialActiveTab = 2
                                settingsInitialAuthTab = 1
                                navigateToTab(PocketTab.SETTINGS)
                            }
                        )

                        selectedPlayer != null -> PlayerDetailScreen(
                            stateHolder = stateHolder,
                            player = selectedPlayer!!,
                            onBack = { selectedPlayer = null }
                        )

                        showLegalPage -> LegalCenterScreen(
                            onBack = {
                                showLegalPage = false
                                currentTab = PocketTab.SETTINGS
                            },
                            onMessage = showMessage
                        )

                        else -> {
                            AnimatedContent(
                                targetState = currentTab,
                                transitionSpec = {
                                    if (targetState.ordinal > initialState.ordinal) {
                                        slideInHorizontally(animationSpec = PocketMotion.softIntOffsetTween(durationMillis = 480)) { it / 12 } +
                                            fadeIn(PocketMotion.softFloatTween(durationMillis = 380)) togetherWith
                                            slideOutHorizontally(animationSpec = PocketMotion.softIntOffsetTween(durationMillis = 420)) { -it / 14 } +
                                            fadeOut(PocketMotion.softFloatTween(durationMillis = 240))
                                    } else {
                                        slideInHorizontally(animationSpec = PocketMotion.softIntOffsetTween(durationMillis = 480)) { -it / 12 } +
                                            fadeIn(PocketMotion.softFloatTween(durationMillis = 380)) togetherWith
                                            slideOutHorizontally(animationSpec = PocketMotion.softIntOffsetTween(durationMillis = 420)) { it / 14 } +
                                            fadeOut(PocketMotion.softFloatTween(durationMillis = 240))
                                    }
                                },
                                label = "tab-navigation"
                            ) { targetTab ->
                                when (targetTab) {
                                    PocketTab.HOME,
                                    PocketTab.CONSOLE -> ConsoleScreen(
                                        stateHolder = stateHolder,
                                        onViewAllPlayers = { currentTab = PocketTab.PLAYERS },
                                        onChangeVersion = {
                                            if (stateHolder.isNavigationLocked) {
                                                showMessage("Stop the server before changing versions.")
                                            } else {
                                                onChangeVersion()
                                            }
                                        },
                                        onInstallCurrentVersion = {
                                            if (stateHolder.isNavigationLocked) {
                                                showMessage("Stop the server before installing.")
                                            } else {
                                                onInstallCurrentVersion()
                                            }
                                        },
                                        onPlayerSelected = { player ->
                                            selectedPlayer = player
                                        },
                                        onOpenServerDetails = {
                                            showServerDetailsPage = true
                                            currentTab = PocketTab.HOME
                                        },
                                        onAddWorld = {
                                            openWorldSetup(createMode = true)
                                            currentTab = PocketTab.HOME
                                        },
                                        onNavigateToSignUp = {
                                            settingsInitialActiveTab = 2
                                            settingsInitialAuthTab = 1
                                            navigateToTab(PocketTab.SETTINGS)
                                        },
                                        topContentBelowServerCard = homeTopContent
                                    )

                                    PocketTab.PLAYERS -> PlayersScreen(
                                        stateHolder = stateHolder,
                                        onPlayerSelected = { player -> selectedPlayer = player }
                                    )

                                    PocketTab.STORAGE -> StorageScreen(
                                        stateHolder = stateHolder,
                                        onOpenWorldSetup = { createMode ->
                                            openWorldSetup(createMode)
                                            selectedPlayer = null
                                            currentTab = PocketTab.HOME
                                        },
                                        onMessage = showMessage,
                                        onChangeVersion = {
                                            if (stateHolder.isNavigationLocked) {
                                                showMessage("Stop the server before changing versions.")
                                            } else {
                                                onChangeVersion()
                                            }
                                        },
                                        onNavigateToSettings = { initialActiveTab ->
                                            settingsInitialActiveTab = initialActiveTab
                                            navigateToTab(PocketTab.SETTINGS)
                                        }
                                    )

                                    PocketTab.MODS -> PluginsHubScreen(
                                        stateHolder = stateHolder,
                                        onMessage = showMessage
                                    )

                                    PocketTab.SETTINGS -> SettingsScreen(
                                        stateHolder = stateHolder,
                                        onMessage = showMessage,
                                        onOpenConfigEditor = { showConfigEditor = true },
                                        onOpenLegalPage = {
                                            showLegalPage = true
                                            selectedPlayer = null
                                        },
                                        onDarkThemeChange = onDarkThemeChange,
                                        currentMobTheme = currentMobTheme,
                                        onMobThemeChange = onMobThemeChange,
                                        initialActiveTab = settingsInitialActiveTab,
                                        initialAuthTab = settingsInitialAuthTab
                                    )
                                }
                            }
                        }
                    }

                    // Config editor overlay
                    if (showConfigEditor) {
                        ConfigEditorScreen(
                            serverDir = ServerFileManager.getServerDir(context, stateHolder.activeWorld),
                            isReadOnly = stateHolder.status != ServerStatus.OFFLINE,
                            onClose = {
                                showConfigEditor = false
                                stateHolder.refreshAll()
                            },
                            onOpenFileEditor = {
                                showConfigEditor = false
                                showFileEditor = true
                            }
                        )
                    }

                    // File editor overlay
                    if (showFileEditor) {
                        FileEditorScreen(
                            rootDir = ServerFileManager.getServerDir(context, stateHolder.activeWorld),
                            isServerRunning = stateHolder.status != ServerStatus.OFFLINE,
                            onFileSaved = { savedFile ->
                                if (savedFile.name == "server.properties") {
                                    stateHolder.refreshAll()
                                }
                            },
                            onClose = { showFileEditor = false }
                        )
                    }

                    ChunkyProgressBanner(
                        progress = stateHolder.chunkyProgressPercent,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
            val isServerActive = stateHolder.status == ServerStatus.ONLINE
            val showFloatingBubble = (currentTab == PocketTab.PLAYERS || 
                                      isFloatingChatEnabled || 
                                      (currentTab == PocketTab.HOME && isServerActive))
            
            if (showFloatingBubble) {
                var showFloatingChatSheet by remember { mutableStateOf(false) }
                // First-time intro dialog — shown when user taps the FAB for the first time
                var showFirstTimeChatIntro by remember { mutableStateOf(false) }
                
                if (showFirstTimeChatIntro) {
                    AlertDialog(
                        onDismissRequest = {
                            showFirstTimeChatIntro = false
                            scope.launch { AppPreferencesStore.setFloatingChatFirstTimeShown(context, true) }
                            showFloatingChatSheet = true
                        },
                        icon = { Icon(Icons.Default.Forum, contentDescription = null, tint = PocketColors.Primary) },
                        title = { Text("Chat with Your Players", fontFamily = Monocraft, fontWeight = FontWeight.Bold) },
                        text = {
                            Text(
                                "This chat bubble lets you talk directly with anyone on the server as the Server Operator — send messages, whisper to players, and broadcast announcements, all without leaving the app."
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showFirstTimeChatIntro = false
                                    scope.launch { AppPreferencesStore.setFloatingChatFirstTimeShown(context, true) }
                                    showFloatingChatSheet = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PocketColors.Primary)
                            ) {
                                Text("Open Chat 💬", color = Color.Black, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    )
                }

                // Chat Notification & Animation Logic
                var chatNotificationCount by remember { mutableIntStateOf(0) }
                var lastLogSize by remember { mutableIntStateOf(stateHolder.logs.size) }
                val scale = remember { Animatable(1f) }

                // Reset notification count when overlay is opened
                LaunchedEffect(showFloatingChatSheet) {
                    if (showFloatingChatSheet) {
                        chatNotificationCount = 0
                    }
                }

                // Log observer for incoming chat messages
                LaunchedEffect(stateHolder.logs.size) {
                    val currentSize = stateHolder.logs.size
                    if (currentSize > lastLogSize) {
                        if (!showFloatingChatSheet) {
                            val chatRegex = Regex("""^(?:\[Not Secure\]\s*)?<([^>]+)>\s+(.*)""")
                            val broadcastRegex = Regex("""^(?:\[Not Secure\]\s*)?\[(?:Server|Rcon)\]\s+(.*)""")
                            val consoleWhisperRegex = Regex("""^(?:\[Not Secure\]\s*)?\[(?:[Ss]erver|[Cc]onsole|[Rr][Cc][Oo][Nn]):\s+Whispered\s+(.*)\s+to\s+(\S+)\]""")
                            val consoleWhisperNoBracketsRegex = Regex("""^(?:\[Not Secure\]\s*)?Whispered\s+(.*)\s+to\s+(\S+)""")
                            val youWhisperRegex = Regex("""^(?:\[Not Secure\]\s*)?You\s+whisper(?:ed)?\s+to\s+(\S+):\s+(.*)""")
                            val playerWhisperRegex = Regex("""^(?:\[Not Secure\]\s*)?(\S+)\s+whisper(?:s|ed)?\s+to\s+(?:you|[Ss]erver|[Cc]onsole):\s+(.*)""")
                            val playerWhisperArrowRegex = Regex("""^(?:\[Not Secure\]\s*)?\[(\S+)\s+->\s+(?:[Yy]ou|[Ss]erver|[Cc]onsole)\]\s+(.*)""")

                            var newChatsCount = 0
                            for (i in lastLogSize until currentSize) {
                                val line = stateHolder.logs.getOrNull(i) ?: continue
                                val cleanLine = com.pocketcraft.server.service.ConsoleParser.parse(line).text.trim()
                                val isChat = chatRegex.containsMatchIn(cleanLine) ||
                                             broadcastRegex.containsMatchIn(cleanLine) ||
                                             consoleWhisperRegex.containsMatchIn(cleanLine) ||
                                             consoleWhisperNoBracketsRegex.containsMatchIn(cleanLine) ||
                                             youWhisperRegex.containsMatchIn(cleanLine) ||
                                             playerWhisperRegex.containsMatchIn(cleanLine) ||
                                             playerWhisperArrowRegex.containsMatchIn(cleanLine)
                                if (isChat) {
                                    newChatsCount++
                                }
                            }
                            if (newChatsCount > 0) {
                                chatNotificationCount += newChatsCount
                                
                                // Pop/bounce animation
                                scale.animateTo(1.2f, tween(100, easing = LinearEasing))
                                scale.animateTo(0.9f, tween(100, easing = LinearEasing))
                                scale.animateTo(1.05f, tween(80, easing = LinearEasing))
                                scale.animateTo(1f, tween(80, easing = LinearEasing))
                            }
                        }
                        lastLogSize = currentSize
                    } else if (currentSize < lastLogSize) {
                        lastLogSize = currentSize
                    }
                }

                val hapticFeedback = LocalHapticFeedback.current
                val isDark = pocketIsDarkTheme()
                val interactionSource = remember { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                
                val restingBorder = 3.dp
                val targetBorder = if (pressed) 1.5.dp else restingBorder
                val targetOffsetY = if (pressed) (restingBorder - 1.5.dp) else 0.dp
                
                val fabOffsetY by animateDpAsState(
                    targetValue = targetOffsetY,
                    animationSpec = PocketMotion.softDpTween(durationMillis = 150),
                    label = "fab_press_offset"
                )
                val fabBottomBorder by animateDpAsState(
                    targetValue = targetBorder,
                    animationSpec = PocketMotion.softDpTween(durationMillis = 150),
                    label = "fab_bottom_border"
                )
                // Pulse only on first server start (discovery hint) — stops permanently after first tap
                val showAttentionPulse = isServerActive && !isFloatingChatFirstTimeShown && chatNotificationCount == 0 && !showFloatingChatSheet
                val infiniteTransition = rememberInfiniteTransition(label = "chat_fab_pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.45f,
                    animationSpec = InfiniteRepeatableSpec(
                        animation = tween(900, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "pulse_scale"
                )
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.55f,
                    targetValue = 0f,
                    animationSpec = InfiniteRepeatableSpec(
                        animation = tween(900, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "pulse_alpha"
                )

                
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = bottomPadding + 88.dp + fabOffsetY)
                ) {
                    // Attention pulse ring
                    if (showAttentionPulse) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .align(Alignment.Center)
                                .graphicsLayer(
                                    scaleX = pulseScale,
                                    scaleY = pulseScale,
                                    alpha = pulseAlpha
                                )
                                .background(PocketColors.Primary.copy(alpha = 0.5f), CircleShape)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .buttonDropShadow(isDark = isDark, shadowColor = PocketColors.Primary.copy(alpha = 0.25f), cornerRadius = 28.dp)
                            .button3d(
                                elevation = 8.dp,
                                borderColor = PocketColors.PrimaryBorder,
                                depthColor = PocketColors.PrimaryBorderBottom,
                                depthWidth = fabBottomBorder
                            )
                            .graphicsLayer(
                                scaleX = scale.value,
                                scaleY = scale.value
                            )
                            .clip(CircleShape)
                            .background(PocketColors.Primary)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    if (appFeedbackEnabled) {
                                        scope.launch { playAppHaptic(context, hapticFeedback, false) }
                                    }
                                    if (!isFloatingChatFirstTimeShown && isPremium) {
                                        // First time: show the feature intro dialog, which will open chat on dismiss
                                        showFirstTimeChatIntro = true
                                    } else {
                                        showFloatingChatSheet = true
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = "Open Chat",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Notification badge or PRO badge overlay
                    if (chatNotificationCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .background(Color.Red, CircleShape)
                                .border(1.5.dp, Color.White, CircleShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (chatNotificationCount > 99) "99+" else chatNotificationCount.toString(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Monocraft
                            )
                        }
                    }
                }

                
                if (showFloatingChatSheet) {
                    FloatingChatBottomSheet(
                        stateHolder = stateHolder,
                        onDismissRequest = { showFloatingChatSheet = false },
                        onNavigateToSignUp = {
                            showFloatingChatSheet = false
                            settingsInitialActiveTab = 2
                            settingsInitialAuthTab = 1
                            navigateToTab(PocketTab.SETTINGS)
                        }
                    )
                }
            }

            // Backup progress circle — visible on all screens when a backup is running
            val isManualRunning = stateHolder.isBackingUp
            val isRunning = backupState == BackupProgressTracker.State.RUNNING || isManualRunning
            if (isRunning) {
                val progressPercent = if (isManualRunning) stateHolder.backupProgressPercent else backupProgress
                val trackerInteractionSource = remember { MutableInteractionSource() }
                val trackerPressed by trackerInteractionSource.collectIsPressedAsState()
                val trackerOffsetY = if (trackerPressed) 1.5.dp else 0.dp
                val trackerBorder = if (trackerPressed) 1.5.dp else 3.dp

                val widgetOffsetY by animateDpAsState(
                    targetValue = trackerOffsetY,
                    animationSpec = PocketMotion.softDpTween(durationMillis = 150),
                    label = "backup_widget_press_offset"
                )
                val widgetBorder by animateDpAsState(
                    targetValue = trackerBorder,
                    animationSpec = PocketMotion.softDpTween(durationMillis = 150),
                    label = "backup_widget_border"
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 16.dp, top = topPadding + 16.dp + widgetOffsetY)
                        .size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { progressPercent / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = PocketColors.Primary,
                        trackColor = PocketColors.PrimaryBorder.copy(alpha = 0.25f),
                        strokeWidth = 3.dp
                    )

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .buttonDropShadow(isDark = pocketIsDarkTheme(), shadowColor = PocketColors.Primary.copy(alpha = 0.25f), cornerRadius = 22.dp)
                            .button3d(
                                elevation = 6.dp,
                                borderColor = PocketColors.PrimaryBorder,
                                depthColor = PocketColors.PrimaryBorderBottom,
                                depthWidth = widgetBorder
                            )
                            .clip(CircleShape)
                            .background(PocketColors.PrimaryDark)
                            .clickable(
                                interactionSource = trackerInteractionSource,
                                indication = null,
                                onClick = {
                                    showBackupSheet = true
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Backup in Progress",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (showBackupSheet) {
                BackupProgressBottomSheet(
                    stateHolder = stateHolder,
                    onDismissRequest = { showBackupSheet = false }
                )
            }

            if (showPremiumBottomSheet) {
                com.pocketcraft.server.ui.components.PremiumUpgradeBottomSheet(
                    onDismissRequest = { showPremiumBottomSheet = false },
                    onNavigateToSignUp = {
                        showPremiumBottomSheet = false
                        settingsInitialActiveTab = 2
                        settingsInitialAuthTab = 1
                        navigateToTab(PocketTab.SETTINGS)
                    }
                )
            }

            if (showUpgradeCelebrationAnimation) {
                com.pocketcraft.server.ui.components.UpgradeCelebrationDialog(
                    entitlement = entitlement,
                    onDismissRequest = {
                        showUpgradeCelebrationAnimation = false
                        if (entitlement.isPremium && !entitlement.isSupportive && entitlement.discordId.isBlank()) {
                            showDiscordBadgePopup = true
                        }
                    }
                )
            }

            if (showDiscordBadgePopup) {
                com.pocketcraft.server.ui.components.DiscordBadgePopup(
                    entitlement = entitlement,
                    onDismissRequest = { showDiscordBadgePopup = false },
                    onMessage = showMessage
                )
            }

            // ── Floating nav — footer bg extends through system nav bar inset ──
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = bottomPadding)
                    .background(color = PocketColors.FooterBg, shape = ReverseCurvedFooterShape())
            ) {
                PocketBottomNav(
                    currentTab = currentTab,
                    onTabSelected = { tab ->
                        showConfigEditor = false
                        showFileEditor = false
                        showServerDetailsPage = false
                        if (showSetupLoading) {
                            showSetupLoading = false
                            setupLoadingProgress = 0f
                        }
                        if (showWorldSetupPage) {
                            showWorldSetupPage = false
                            worldSetupCreateMode = false
                        }
                        if (showLegalPage) {
                            showLegalPage = false
                        }
                        navigateToTab(tab)
                    }
                )
            }
        }
    }

    if (stateHolder.showEulaDialog) {
        EulaDialog(
            onAccept = {
                stateHolder.acceptEula()
            },
            onDismiss = { stateHolder.dismissEulaDialog() }
        )
    }

    // Server crash dialog
    if (stateHolder.showCrashDialog) {
        ServerFailureDialog(
            reason = stateHolder.crashReason,
            details = stateHolder.crashDetails,
            duringStartup = stateHolder.crashWasDuringStartup,
            logs = stateHolder.logs,
            onDismiss = { stateHolder.dismissCrashDialog() },
            onRetryWithInProcess = { stateHolder.startServer() }
        )
    }

    if (stateHolder.showBatteryOptimizationDialog) {
        BatteryOptimizationDialog(
            onAccept = {
                stateHolder.requestBatteryOptimization()
            },
            onDismiss = { stateHolder.dismissBatteryOptimizationDialog() }
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Premium EULA Dialog
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EulaDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { /* mandatory — cannot be dismissed */ },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(top = 28.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Icon + title
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Minecraft creeper logo badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(PocketColors.PrimaryMuted, Color.Transparent)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚖️", fontSize = 32.sp)
                }
                Text(
                    text = "Minecraft EULA",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "You must accept the Minecraft End User License Agreement to run a server.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Agreement box
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("📜", fontSize = 18.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "By tapping Accept, you agree to:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "minecraft.net/eula",
                                fontSize = 12.sp,
                                color = PocketColors.Primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.minecraft.net/eula"))
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Accept button (gradient)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(PocketColors.Primary, Color(0xFF4CAF50))
                        )
                    )
                    .clickable(onClick = onAccept)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓  Accept & Continue",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }

            // Decline link
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Decline",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Server failure dialog
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun ServerFailureDialog(
    reason: String,
    details: String,
    duringStartup: Boolean,
    logs: List<String>,
    onDismiss: () -> Unit,
    onRetryWithInProcess: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSending by remember { androidx.compose.runtime.mutableStateOf(false) }
    var ticketNumber by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var submitError by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val failureSummary = remember(reason, details, duringStartup) {
        serverFailureSummary(reason = reason, details = details, duringStartup = duringStartup)
    }
    val glassBrush = pocketGlassCardBrush()
    val controlBrush = pocketGlassControlBrush()
    val actionBrush = pocketPremiumActionBrush()
    val glassBorder = pocketCardBorderColor()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accentColor = if (isDarkTheme) Color(0xFFFF8A98) else Color(0xFFE24E69)
    val mutedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDarkTheme) 0.84f else 0.78f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .heightIn(max = 560.dp)
                .card3d(
                    elevation = 8.dp,
                    cornerRadius = 28.dp,
                    borderColor = glassBorder.copy(alpha = if (isDarkTheme) 0.72f else 0.92f),
                    depthColor = glassBorder.copy(alpha = if (isDarkTheme) 0.92f else 1f)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(glassBrush),
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(controlBrush, CircleShape)
                            .border(1.dp, accentColor.copy(alpha = 0.34f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = if (duringStartup) "Server failed to start" else "Server stopped unexpectedly",
                            fontSize = 21.sp,
                            lineHeight = 25.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "PocketCraft paused retries so you can fix the cause first.",
                            fontSize = 12.5.sp,
                            color = mutedTextColor,
                            lineHeight = 18.sp
                        )
                    }
                }

                HorizontalDivider(color = glassBorder.copy(alpha = 0.42f))

                FailureSummaryCard(
                    title = "REASON",
                    body = failureSummary.reason,
                    iconTint = accentColor,
                    icon = Icons.Default.ErrorOutline
                )

                FailureSummaryCard(
                    title = "HOW TO FIX",
                    body = failureSummary.fix,
                    iconTint = PocketColors.Primary,
                    icon = Icons.Default.Lightbulb
                )

                if (ticketNumber != null) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = PocketColors.Online.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PocketColors.Online.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "LOCKED IN SUPPORT TICKET",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PocketColors.Online,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = ticketNumber!!,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PocketColors.Online,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "Logs submitted! Join our Discord server and drop this ticket number in the support channel.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                } else if (!submitError.isNullOrBlank()) {
                    Text(
                        text = "Error: $submitError",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                val prefs = remember { AppPreferences(context) }
                val deviceProfile = remember {
                    val totalRam = com.pocketcraft.server.util.RamUtils.getTotalRamMb(context)
                    val availRam = com.pocketcraft.server.util.RamUtils.getAvailableRamMb(context)
                    com.pocketcraft.server.util.RamUtils.buildDeviceStabilityProfile(totalRam, availRam)
                }
                val showInProcessFallbackOption = duringStartup && prefs.forceExternalJvm && !deviceProfile.forceExternalJvm && onRetryWithInProcess != null

                if (showInProcessFallbackOption) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "STARTUP FAILURE DETECTED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.error,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Try disabling the external JVM. Some devices run more reliably using the built-in, in-process execution mode.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            androidx.compose.material3.Button(
                                onClick = {
                                    prefs.forceExternalJvm = false
                                    onDismiss()
                                    onRetryWithInProcess?.invoke()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Disable External JVM & Retry", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(actionBrush)
                        .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Close",
                        color = Color(0xFF102016),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            val logContent = buildString {
                                appendLine("=== PocketCraft Server Failure Log ===")
                                appendLine("Reason: $reason")
                                if (details.isNotBlank()) appendLine("Details: $details")
                                appendLine("Stage: ${if (duringStartup) "Startup" else "Runtime"}")
                                appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})")
                                appendLine("\n--- Console Output (${logs.size} lines) ---")
                                logs.takeLast(100).forEach { appendLine(it) }
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("PocketCraft Server Failure Log", logContent)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Server logs copied to clipboard!", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(15.dp),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, PocketColors.Primary.copy(alpha = if (isDarkTheme) 0.58f else 0.42f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PocketColors.Primary
                        )
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Copy Logs",
                            modifier = Modifier.padding(vertical = 2.dp),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.discord_invite_url)))
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(15.dp),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF5865F2).copy(alpha = if (isDarkTheme) 0.58f else 0.42f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDarkTheme) Color(0xFFAEBBFF) else Color(0xFF5865F2)
                        )
                    ) {
                        Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Discord",
                            modifier = Modifier.padding(vertical = 2.dp),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    if (ticketNumber == null) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                isSending = true
                                submitError = null
                                val ticket = "PC-" + String.format("%06d", java.util.Random().nextInt(1000000))
                                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                val data = hashMapOf(
                                    "ticket" to ticket,
                                    "reason" to reason,
                                    "details" to details,
                                    "consoleLines" to logs,
                                    "device" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})",
                                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                )
                                val timeoutJob = scope.launch {
                                    kotlinx.coroutines.delay(8000)
                                    if (isSending && ticketNumber == null) {
                                        submitError = "Upload timed out. Check your connection or try again."
                                        isSending = false
                                    }
                                }
                                db.collection("tickets").document(ticket).set(data)
                                    .addOnSuccessListener {
                                        timeoutJob.cancel()
                                        if (ticketNumber == null) {
                                            ticketNumber = ticket
                                            isSending = false
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("PocketCraft Ticket", ticket)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Ticket created & copied to clipboard!", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        timeoutJob.cancel()
                                        if (isSending) {
                                            submitError = e.message ?: "Failed to upload logs"
                                            isSending = false
                                        }
                                    }
                            },
                            enabled = !isSending,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(15.dp),
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, PocketColors.Primary.copy(alpha = if (isDarkTheme) 0.58f else 0.42f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = PocketColors.Primary
                            )
                        ) {
                            if (isSending) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    color = PocketColors.Primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Send Logs",
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    } else {
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("PocketCraft Ticket", ticketNumber!!)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Ticket copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(15.dp),
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, PocketColors.Online.copy(alpha = if (isDarkTheme) 0.58f else 0.42f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = PocketColors.Online
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Copy Ticket",
                                modifier = Modifier.padding(vertical = 2.dp),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FailureSummaryCard(
    title: String,
    body: String,
    iconTint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, pocketCardBorderColor().copy(alpha = 0.66f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(pocketGlassControlBrush())
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(iconTint.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = body,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private data class ServerFailureSummary(
    val reason: String,
    val fix: String
)

private fun serverFailureSummary(
    reason: String,
    details: String,
    duringStartup: Boolean
): ServerFailureSummary {
    val rawReason = reason.trim()
    val combined = "$rawReason\n$details".lowercase()
    val mentionsModpack = "modpack" in combined
    val mentionsLaunchTarget = "launch target" in combined
    val commandLineOnly = rawReason.startsWith("Command Line", ignoreCase = true) ||
        rawReason.contains("-Xmx", ignoreCase = true) ||
        rawReason.contains("-Djava.home", ignoreCase = true)

    return when {
        "startup timed out" in combined || "startup timeout" in combined -> ServerFailureSummary(
            reason = "The server took too long to start up (exceeded 7 minutes).",
            fix = "Please verify your JRE/Java settings, check the console log for any plugin/mod errors, or try starting again."
        )
        "modpack not installed" in combined || "install modpack" in combined -> ServerFailureSummary(
            reason = "The selected world is configured for a modpack, but the modpack has not been installed yet.",
            fix = "Tap Install Modpack on the Home screen, wait for it to finish, then start the server again."
        )
        "modpack files are missing" in combined || (mentionsModpack && mentionsLaunchTarget) -> ServerFailureSummary(
            reason = "PocketCraft could not find the modpack files needed to launch this world.",
            fix = "Re-install the modpack from the Home screen so the missing launch files are restored."
        )
        "launch target not found" in combined || "server jar could not be resolved" in combined || mentionsLaunchTarget -> ServerFailureSummary(
            reason = "PocketCraft could not find the server launch files needed for this world.",
            fix = "Re-select or re-install the server version from the version card, then start the server again."
        )
        "outofmemory" in combined || "heap" in combined || "cannot allocate" in combined -> ServerFailureSummary(
            reason = "The server ran out of available memory while starting or loading the world.",
            fix = "Lower the RAM allocation, close other apps, then start the server again."
        )
        "address already in use" in combined || "failed to bind" in combined || "port already in use" in combined || "port in use" in combined -> ServerFailureSummary(
            reason = "Another process is already using the server port.",
            fix = "Stop other server apps or restart the device, then try starting PocketCraft again."
        )
        "no space" in combined || "disk full" in combined -> ServerFailureSummary(
            reason = "The device does not have enough free storage for the server to keep running.",
            fix = "Free at least 1-2 GB of storage for worlds, logs, and temporary server files."
        )
        "unable to access jarfile" in combined || "invalid or corrupt jar" in combined || ("jar" in combined && "not found" in combined) -> ServerFailureSummary(
            reason = "The server jar is missing, corrupted, or no longer matches this world.",
            fix = "Re-import the server version or modpack from the version card, then start again."
        )
        "unsupportedclassversion" in combined || "java version" in combined -> ServerFailureSummary(
            reason = "This server build needs a different Java runtime or Minecraft version.",
            fix = "Select a compatible Minecraft version, or re-import a server build made for this version."
        )
        "unknownhost" in combined || "network" in combined || "timed out" in combined || "download" in combined -> ServerFailureSummary(
            reason = "The server needed network access and the request failed.",
            fix = "Check internet, turn off VPN or Private DNS temporarily, then try again."
        )
        "permission denied" in combined || "access denied" in combined -> ServerFailureSummary(
            reason = "PocketCraft could not access a file required by the server.",
            fix = "Re-import the server files and verify the app still has storage access."
        )
        "mod" in combined || "plugin" in combined || "mixin" in combined -> ServerFailureSummary(
            reason = "A mod or plugin is likely incompatible with this server version.",
            fix = "Remove the most recently added mod or plugin, or use the version it was built for."
        )
        "eula" in combined -> ServerFailureSummary(
            reason = "Minecraft's EULA has not been accepted for this world yet.",
            fix = "Accept the EULA when prompted, then start the server again."
        )
        commandLineOnly -> ServerFailureSummary(
            reason = "Java exited during startup before PocketCraft received a clear server error. The command-line dump is internal startup info, not the root cause.",
            fix = "Try starting once more. If it repeats, copy the full issue and send it in Discord so the recent console output can be inspected."
        )
        else -> ServerFailureSummary(
            reason = rawReason.ifBlank {
                if (duringStartup) "The server stopped during startup before reporting a clear error." else "The server process exited without a clear error."
            }.take(220),
            fix = "Try again after checking RAM, storage, and recent mod or plugin changes. If it repeats, copy the full issue for support."
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Premium Battery Optimization Dialog
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryOptimizationDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(top = 28.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(PocketColors.PrimaryMuted, Color.Transparent)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔋", fontSize = 32.sp)
                }
                Text(
                    text = "Background Server Hosting",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "To keep your Minecraft server running reliably in the background when players are connected, Android requires disabling battery optimization for PocketCraft.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(PocketColors.Primary, Color(0xFF4CAF50))
                        )
                    )
                    .clickable(onClick = onAccept)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓  Allow Background Access",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Not Now",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
