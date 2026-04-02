package com.pocketcraft.server.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.pocketcraft.server.data.model.PlayerInfo
import com.pocketcraft.server.ui.navigation.PocketBottomNav
import com.pocketcraft.server.ui.navigation.PocketTab
import com.pocketcraft.server.ui.navigation.PocketTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    stateHolder: ServerStateHolder,
    onChangeVersion: () -> Unit,
    onRequestExit: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(PocketTab.HOME) }
    var selectedPlayer by remember { mutableStateOf<PlayerInfo?>(null) }
    var showServerDetailsPage by remember { mutableStateOf(false) }

    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    BackHandler {
        when {
            showServerDetailsPage -> {
                showServerDetailsPage = false
            }

            selectedPlayer != null -> {
                selectedPlayer = null
                currentTab = PocketTab.HOME
            }

            currentTab != PocketTab.HOME -> {
                currentTab = PocketTab.HOME
            }

            else -> onRequestExit()
        }
    }

    Scaffold(
        topBar = {
            PocketTopBar(
                relayHost = stateHolder.relayHost,
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
                    currentTab = tab
                    selectedPlayer = null
                    showServerDetailsPage = false
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(padding)
        ) {
            when {
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
                            showServerDetailsPage = true
                            currentTab = PocketTab.HOME
                        }
                    )

                    PocketTab.PLAYERS -> PlayersScreen(
                        stateHolder = stateHolder,
                        onPlayerSelected = { player -> selectedPlayer = player }
                    )

                    PocketTab.STORAGE -> StorageScreen(
                        stateHolder = stateHolder,
                        onChangeVersion = {
                            if (stateHolder.isNavigationLocked) {
                                showMessage("Stop the server before changing versions.")
                            } else {
                                onChangeVersion()
                            }
                        }
                    )

                    PocketTab.PLUGINS -> PluginsHubScreen(
                        stateHolder = stateHolder,
                        onMessage = showMessage
                    )

                    PocketTab.SETTINGS -> SettingsScreen(
                        stateHolder = stateHolder,
                        onMessage = showMessage
                    )
                }
            }
        }
    }
}
