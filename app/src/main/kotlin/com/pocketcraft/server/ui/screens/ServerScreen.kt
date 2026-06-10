package com.pocketcraft.server.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.pocketcraft.server.ui.navigation.PocketTab
import com.pocketcraft.server.ui.navigation.PocketTopBar
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.theme.pocketCardBorderColor
import com.pocketcraft.server.ui.theme.pocketDecoratedBackground
import com.pocketcraft.server.ui.theme.pocketGlassCardBrush
import com.pocketcraft.server.ui.theme.pocketGlassControlBrush
import com.pocketcraft.server.ui.theme.card3d
import com.pocketcraft.server.ui.theme.pocketPremiumActionBrush
import com.pocketcraft.server.ui.util.MobTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val navigationHistory = remember { mutableStateListOf<PocketTab>() }

    fun navigateToTab(tab: PocketTab) {
        if (currentTab != tab) {
            if (tab != PocketTab.SETTINGS) {
                navigationHistory.add(currentTab)
            }
        }
        currentTab = tab
        selectedPlayer = null
    }

    fun goBack(): Boolean {
        return if (navigationHistory.isNotEmpty()) {
            val previousTab = navigationHistory.removeAt(navigationHistory.size - 1)
            currentTab = previousTab
            true
        } else {
            false
        }
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
            else -> if (!goBack()) {
                if (currentTab != PocketTab.HOME) {
                    currentTab = PocketTab.HOME
                } else {
                    onRequestExit()
                }
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
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        // Full-screen box — nav floats as an overlay at the bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
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
                            onComplete = { showWorldSetupPage = false }
                        )

                        showServerDetailsPage -> ServerDetailsScreen(
                            stateHolder = stateHolder,
                            onBack = { showServerDetailsPage = false },
                            onMessage = showMessage
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

                        else -> when (currentTab) {
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
                                onMobThemeChange = onMobThemeChange
                            )
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
            // ── Floating nav — footer bg extends through system nav bar inset ──
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(PocketColors.FooterBg)
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
            onDismiss = { stateHolder.dismissCrashDialog() }
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
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.discord_invite_url)))
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(15.dp),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF5865F2).copy(alpha = if (isDarkTheme) 0.58f else 0.42f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDarkTheme) Color(0xFFAEBBFF) else Color(0xFF5865F2)
                        )
                    ) {
                        Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Discord", modifier = Modifier.padding(vertical = 2.dp), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                    }

                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    "PocketCraft server issue",
                                    details.ifBlank { reason }
                                )
                            )
                            Toast.makeText(context, "Full issue copied. Paste it in Discord.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(15.dp),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, glassBorder.copy(alpha = 0.64f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Copy Issue", modifier = Modifier.padding(vertical = 2.dp), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
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
    val commandLineOnly = rawReason.startsWith("Command Line", ignoreCase = true) ||
        rawReason.contains("-Xmx", ignoreCase = true) ||
        rawReason.contains("-Djava.home", ignoreCase = true)

    return when {
        "modpack not installed" in combined || "install modpack" in combined -> ServerFailureSummary(
            reason = "The selected world is configured for a modpack, but the modpack has not been installed yet.",
            fix = "Tap Install Modpack on the Home screen, wait for it to finish, then start the server again."
        )
        "modpack files are missing" in combined || "launch target" in combined -> ServerFailureSummary(
            reason = "PocketCraft could not find the modpack files needed to launch this world.",
            fix = "Re-install the modpack from the Home screen so the missing launch files are restored."
        )
        "outofmemory" in combined || "heap" in combined || "cannot allocate" in combined -> ServerFailureSummary(
            reason = "The server ran out of available memory while starting or loading the world.",
            fix = "Lower the RAM allocation, close other apps, then start the server again."
        )
        "address already in use" in combined || "failed to bind" in combined || ("port" in combined && "use" in combined) -> ServerFailureSummary(
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
