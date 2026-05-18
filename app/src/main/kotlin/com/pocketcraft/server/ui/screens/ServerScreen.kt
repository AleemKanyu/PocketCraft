package com.pocketcraft.server.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.data.model.PlayerInfo
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.service.ServerFileManager
import com.pocketcraft.server.ui.components.ChunkyProgressBanner
import com.pocketcraft.server.ui.navigation.PocketBottomNav
import com.pocketcraft.server.ui.navigation.PocketTab
import com.pocketcraft.server.ui.navigation.PocketTopBar
import com.pocketcraft.server.ui.theme.PocketColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    stateHolder: ServerStateHolder,
    onChangeVersion: () -> Unit,
    onVersionSelected: (String) -> Unit,
    onRequestExit: () -> Unit,
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    homeTopContent: (@Composable () -> Unit)? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(PocketTab.HOME) }
    var selectedPlayer by remember { mutableStateOf<PlayerInfo?>(null) }
    var showWorldSetupPage by remember { mutableStateOf(false) }
    var showLegalPage by remember { mutableStateOf(false) }
    var showConfigEditor by remember { mutableStateOf(false) }
    var worldSetupCreateMode by remember { mutableStateOf(false) }
    var showSetupLoading by remember { mutableStateOf(false) }
    var setupLoadingProgress by remember { mutableStateOf(0f) }
    val navigationHistory = remember { mutableStateListOf<PocketTab>() }
    val chromeColor = PocketColors.Primary

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

            showLegalPage -> {
                showLegalPage = false
                currentTab = PocketTab.SETTINGS
            }

            showConfigEditor -> {
                showConfigEditor = false
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
                isDarkTheme = isDarkTheme,
                onDarkThemeChange = onDarkThemeChange,
                relayLocked = stateHolder.isNavigationLocked,
                onRelayHostChange = { host ->
                    scope.launch {
                        showMessage(stateHolder.updateRelayHost(host))
                    }
                }
            )
        },
        bottomBar = {
            PocketBottomNav(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    showConfigEditor = false
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = chromeColor
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(chromeColor)
                .padding(padding)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = 0.dp,
                    bottomEnd = 34.dp,
                    bottomStart = 34.dp
                )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
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
                                onPlayerSelected = { player ->
                                    selectedPlayer = player
                                },
                                onOpenServerDetails = {
                                    openWorldSetup(createMode = false)
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
                                onDarkThemeChange = onDarkThemeChange
                            )
                        }
                    }
                    if (showConfigEditor) {
                        ConfigEditorScreen(
                            serverDir = ServerFileManager.getServerDir(context, stateHolder.versionLabel),
                            isReadOnly = stateHolder.status != ServerStatus.OFFLINE,
                            onClose = { showConfigEditor = false }
                        )
                    }
                    ChunkyProgressBanner(
                        progress = stateHolder.chunkyProgressPercent,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
    if (stateHolder.showEulaDialog) {
        EulaDialog(
            onAccept = { stateHolder.acceptEula() },
            onDismiss = { stateHolder.dismissEulaDialog() }
        )
    }
}

@Composable
fun EulaDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Accept Minecraft EULA", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "To start the server, you must agree to the Minecraft End User License Agreement (EULA).",
                    fontSize = 14.sp
                )
                Text(
                    "By clicking 'Accept', you agree to the EULA at https://www.minecraft.net/eula",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.minecraft.net/eula"))
                        context.startActivity(intent)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("ACCEPT", fontWeight = FontWeight.ExtraBold, color = PocketColors.Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}
