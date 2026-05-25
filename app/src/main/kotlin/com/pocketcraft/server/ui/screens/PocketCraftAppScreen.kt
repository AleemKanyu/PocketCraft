package com.pocketcraft.server.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.pocketcraft.server.BuildConfig
import com.pocketcraft.server.PocketCraftApp
import com.pocketcraft.server.broadcast.FeedbackPromptCenter
import com.pocketcraft.server.broadcast.FeedbackPromptPayload
import com.pocketcraft.server.broadcast.PocketCraftMessagingService
import com.pocketcraft.server.feedback.FeedbackService
import com.pocketcraft.server.analytics.FirebaseAnalyticsManager
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.config.RemoteConfigManager
import com.pocketcraft.server.R
import com.pocketcraft.server.server.ServerHostService
import com.pocketcraft.server.ui.components.BroadcastBanner
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.theme.pocketPopupAccentContainerColor
import com.pocketcraft.server.service.ModpackManager
import com.pocketcraft.server.viewmodel.BroadcastViewModel
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.pocketcraft.server.data.model.ServerType
import com.pocketcraft.server.server.ServerJarManager
import com.pocketcraft.server.service.ServerFileManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { LOADING, DOWNLOADING, VERSION_PICKER, SERVER }

private const val TAG_POCKETCRAFT_APP = "PocketCraftApp"
private data class PendingVersionChange(
    val type: ServerType,
    val version: String,
    val customJar: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketCraftApp(
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    var screen by remember { mutableStateOf(Screen.LOADING) }
    var transitionTarget by remember { mutableStateOf<Screen?>(null) }
    var loadingStatus by remember { mutableStateOf("Preparing PocketCraft...") }
    var versionDownloadProgress by remember { mutableStateOf(0) }
    var versionId by remember { mutableStateOf("") }
    var bootstrapComplete by remember { mutableStateOf(false) }
    var selectedServerType by remember { mutableStateOf(ServerType.PAPER) }
    var downloadedVersions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingAutoDownloadVersion by remember { mutableStateOf<String?>(null) }
    var showVersionPickerDialog by remember { mutableStateOf(false) }
    var showVersionRiskDialog by remember { mutableStateOf(false) }
    var pendingVersionChange by remember { mutableStateOf<PendingVersionChange?>(null) }
    var pendingVersionRollbackConfig by remember { mutableStateOf<Triple<ServerType, String, String?>?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showCommunityDialog by remember { mutableStateOf(false) }
    var pendingCommunityDialog by remember { mutableStateOf(false) }
    var discordPopupAlreadyShownRemotely by remember { mutableStateOf<Boolean?>(null) }
    var showInstagramDialog by remember { mutableStateOf(false) }
    var pendingInstagramDialog by remember { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }
    var pendingConsentDialog by remember { mutableStateOf(false) }
    var pendingFeedbackPrompt by remember { mutableStateOf<FeedbackPromptPayload?>(null) }
    var showFeedbackPromptDialog by remember { mutableStateOf(false) }
    var feedbackPromptInput by remember { mutableStateOf("") }
    var sendingFeedbackPrompt by remember { mutableStateOf(false) }
    var feedbackPromptError by remember { mutableStateOf<String?>(null) }
    var homeScreenReady by remember { mutableStateOf(false) }
    var showDiscordButtonFromRemoteConfig by remember { mutableStateOf(true) }
    var showInstagramButtonFromRemoteConfig by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences = remember { AppPreferences(context) }
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val selectedWorld by AppPreferencesStore.getSelectedWorldFlow(context).collectAsState(initial = "world")
    val stateHolder = remember(versionId, selectedWorld) {
        ServerStateHolder(context.applicationContext, versionId, selectedServerType, selectedWorld)
    }
    val broadcastViewModel: BroadcastViewModel = hiltViewModel()
    val configBanner by broadcastViewModel.configBanner.collectAsState()
    val broadcasts by broadcastViewModel.visibleBroadcasts.collectAsState()
    val notificationsEnabled by AppPreferencesStore.isNotificationsEnabledFlow(context).collectAsState(initial = true)
    val analyticsConsentGranted by AppPreferencesStore.isAnalyticsConsentFlow(context).collectAsState(initial = false)
    val legalVersionAccepted by AppPreferencesStore.getLegalVersionAcceptedFlow(context).collectAsState(initial = null)
    val isFirstLaunchAfterInstallOrUpdate = remember(BuildConfig.VERSION_NAME) {
        preferences.lastLaunchedAppVersion != BuildConfig.VERSION_NAME
    }
    val colorScheme = MaterialTheme.colorScheme
    val popupAccentContainerColor = pocketPopupAccentContainerColor()
    val hasPendingBroadcast by remember(configBanner, broadcasts) {
        mutableStateOf(configBanner != null || broadcasts.isNotEmpty())
    }
    val hasBlockingSheet = showVersionPickerDialog ||
        showVersionRiskDialog ||
        showConsentDialog ||
        showCommunityDialog ||
        showInstagramDialog ||
        showFeedbackPromptDialog ||
        showExitDialog

    DisposableEffect(stateHolder) {
        onDispose { stateHolder.dispose() }
    }

    LaunchedEffect(BuildConfig.VERSION_NAME) {
        if (preferences.lastLaunchedAppVersion != BuildConfig.VERSION_NAME) {
            preferences.lastLaunchedAppVersion = BuildConfig.VERSION_NAME
        }
    }

    LaunchedEffect(notificationsEnabled) {
        if (notificationsEnabled) {
            PocketCraftMessagingService.subscribeToAllUsers()
        }
    }

    LaunchedEffect(Unit) {
        val setupComplete = AppPreferencesStore.isSetupCompleteFlow(context).first()
        val storedVersionId = AppPreferencesStore.getStoredSelectedVersionFlow(context).first()
        val savedVersionId = AppPreferencesStore.getSelectedVersionFlow(context).first()
        val savedServerType = ServerType.fromString(AppPreferencesStore.getSelectedServerTypeFlow(context).first())
        val pending = AppPreferencesStore.getPendingAutoDownloadVersionFlow(context).first()
        val activeVersionId = ServerHostService.getPersistedActiveVersion(context)

        selectedServerType = savedServerType
        downloadedVersions = scanDownloadedRuntimeKeys(context.applicationContext)

        val bootstrapVersion = when {
            activeVersionId.isNotBlank() -> activeVersionId
            !storedVersionId.isNullOrBlank() -> storedVersionId
            else -> "" // Do NOT auto-pick any version if no selection exists
        }
        versionId = bootstrapVersion

        if (!pending.isNullOrBlank() &&
            !downloadedVersions.contains(runtimeDownloadKey(savedServerType, pending))
        ) {
            Log.i(TAG_POCKETCRAFT_APP, "Need download for version=$bootstrapVersion")
            pendingAutoDownloadVersion = pending
            transitionTarget = Screen.SERVER
        }

        if (!setupComplete) {
            AppPreferencesStore.setSetupComplete(context, true)
        }
        AppPreferencesStore.setSelectedServerType(context, savedServerType.name)
        if (!bootstrapVersion.isNullOrBlank() && storedVersionId != bootstrapVersion) {
            AppPreferencesStore.setSelectedVersion(context, bootstrapVersion)
        }
        bootstrapComplete = true
        transitionTarget = if (bootstrapVersion.isBlank()) Screen.VERSION_PICKER else Screen.SERVER
    }

    LaunchedEffect(stateHolder, versionId, selectedServerType, bootstrapComplete) {
        if (!bootstrapComplete || versionId.isBlank()) return@LaunchedEffect
        stateHolder.refreshAll()
        snapshotFlow { stateHolder.isRefreshing }
            .dropWhile { !it }
            .first { !it }

        val current = stateHolder.config
        val shouldSyncRuntime = current.gameVersion != versionId || current.serverType != selectedServerType
        if (shouldSyncRuntime) {
            stateHolder.saveSettings(
                current.copy(
                    serverType = selectedServerType,
                    gameVersion = versionId
                )
            )
            stateHolder.refreshAll()
        }
    }

    LaunchedEffect(Unit) {
        RemoteConfigManager.initialize(context)
    }

    LaunchedEffect(Unit) {
        val title = preferences.pendingFeedbackPromptTitle
        val body = preferences.pendingFeedbackPromptBody
        if (title.isNotBlank() || body.isNotBlank()) {
            pendingFeedbackPrompt = FeedbackPromptPayload(
                title = title.ifBlank { "Help improve PocketCraft" },
                body = body.ifBlank { "Tell us what is working well and what we should fix next." },
                ctaLabel = preferences.pendingFeedbackPromptCta.ifBlank { "Send feedback" }
            )
        }
    }

    LaunchedEffect(Unit) {
        FeedbackPromptCenter.pendingPrompt.collect { prompt ->
            if (prompt != null) {
                pendingFeedbackPrompt = prompt
            }
        }
    }

    LaunchedEffect(Unit) {
        discordPopupAlreadyShownRemotely = FeedbackService
            .hasDiscordPopupBeenShown(context)
            .getOrDefault(false)
    }

    LaunchedEffect(Unit) {
        RemoteConfigManager.showDiscordButton.collect { value ->
            showDiscordButtonFromRemoteConfig = value
        }
    }

    LaunchedEffect(Unit) {
        RemoteConfigManager.showInstagramButton.collect { value ->
            showInstagramButtonFromRemoteConfig = value
        }
    }

    LaunchedEffect(screen, transitionTarget) {
        if (screen != Screen.LOADING) {
            return@LaunchedEffect
        }
        val nextScreen = transitionTarget ?: Screen.SERVER
        delay(220)
        screen = nextScreen
        transitionTarget = null
    }

    LaunchedEffect(screen) {
        if (screen != Screen.SERVER) {
            homeScreenReady = false
            return@LaunchedEffect
        }
        delay(500)
        homeScreenReady = true
    }

    LaunchedEffect(
        screen,
        homeScreenReady,
        showDiscordButtonFromRemoteConfig,
        showInstagramButtonFromRemoteConfig,
        hasPendingBroadcast,
        discordPopupAlreadyShownRemotely
    ) {
        if (screen != Screen.SERVER || !homeScreenReady) {
            return@LaunchedEffect
        }
        if (hasPendingBroadcast) {
            return@LaunchedEffect
        }
        if (discordPopupAlreadyShownRemotely == null) {
            return@LaunchedEffect
        }
        val needsConsent = legalVersionAccepted != BuildConfig.LEGAL_POLICY_VERSION
        if (needsConsent) {
            pendingConsentDialog = true
        }
        val shouldPromptDiscord =
            preferences.appLaunchCount >= 2 &&
                !preferences.socialLinksJoined &&
                showDiscordButtonFromRemoteConfig &&
                discordPopupAlreadyShownRemotely == false
        val shouldPromptInstagram = !preferences.socialLinksJoined && showInstagramButtonFromRemoteConfig
        if (shouldPromptDiscord) {
            delay(700)
            pendingCommunityDialog = true
        } else if (shouldPromptInstagram) {
            delay(700)
            pendingInstagramDialog = true
        }
    }

    LaunchedEffect(
        pendingConsentDialog,
        showVersionPickerDialog,
        showCommunityDialog,
        showInstagramDialog,
        showExitDialog,
        screen,
        homeScreenReady
    ) {
        if (!pendingConsentDialog) return@LaunchedEffect
        if (screen != Screen.SERVER || !homeScreenReady) return@LaunchedEffect
        val hasBlockingPopup = showVersionPickerDialog || showExitDialog || showCommunityDialog || showInstagramDialog
        if (hasBlockingPopup) return@LaunchedEffect
        showConsentDialog = true
        pendingConsentDialog = false
    }

    LaunchedEffect(
        pendingCommunityDialog,
        showVersionPickerDialog,
        showConsentDialog,
        showInstagramDialog,
        showExitDialog,
        screen,
        homeScreenReady,
        hasPendingBroadcast
    ) {
        if (!pendingCommunityDialog) return@LaunchedEffect
        if (screen != Screen.SERVER || !homeScreenReady) return@LaunchedEffect
        val hasBlockingPopup = showVersionPickerDialog || showConsentDialog || showExitDialog || showCommunityDialog || showInstagramDialog || hasPendingBroadcast
        if (hasBlockingPopup) return@LaunchedEffect
        showCommunityDialog = true
        pendingCommunityDialog = false
        if (discordPopupAlreadyShownRemotely == false) {
            discordPopupAlreadyShownRemotely = true
            FeedbackService.markDiscordPopupShown(context)
        }
    }

    LaunchedEffect(
        pendingInstagramDialog,
        pendingFeedbackPrompt,
        showVersionPickerDialog,
        showConsentDialog,
        showCommunityDialog,
        showExitDialog,
        screen,
        homeScreenReady,
        hasPendingBroadcast
    ) {
        if (!pendingInstagramDialog) return@LaunchedEffect
        if (screen != Screen.SERVER || !homeScreenReady) return@LaunchedEffect
        if (!showInstagramButtonFromRemoteConfig) return@LaunchedEffect
        val hasBlockingPopup = showVersionPickerDialog || showConsentDialog || showCommunityDialog || showExitDialog || showInstagramDialog || showFeedbackPromptDialog || hasPendingBroadcast
        if (hasBlockingPopup) return@LaunchedEffect
        showInstagramDialog = true
        pendingInstagramDialog = false
    }

    LaunchedEffect(
        pendingFeedbackPrompt,
        showVersionPickerDialog,
        showConsentDialog,
        showCommunityDialog,
        showInstagramDialog,
        showExitDialog,
        screen,
        homeScreenReady,
        hasPendingBroadcast
    ) {
        if (pendingFeedbackPrompt == null) return@LaunchedEffect
        if (screen != Screen.SERVER || !homeScreenReady) return@LaunchedEffect
        val hasBlockingPopup = showVersionPickerDialog || showConsentDialog || showCommunityDialog || showInstagramDialog || showExitDialog || hasPendingBroadcast
        if (hasBlockingPopup) return@LaunchedEffect
        showFeedbackPromptDialog = true
    }

    SideEffect {
        val window = activity?.window ?: return@SideEffect
        val statusBarColor = when {
            hasBlockingSheet -> colorScheme.surface.toArgb()
            screen == Screen.SERVER -> colorScheme.surface.toArgb()
            else -> colorScheme.background.toArgb()
        }
        val navBarColor = when {
            hasBlockingSheet -> colorScheme.surface.toArgb()
            screen == Screen.SERVER -> PocketColors.Primary.toArgb()
            else -> colorScheme.background.toArgb()
        }
        window.statusBarColor = statusBarColor
        window.navigationBarColor = navBarColor
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = colorScheme.surface.luminance() > 0.5f
            isAppearanceLightNavigationBars = Color(navBarColor).luminance() > 0.5f
        }
    }

    fun requestVersionChange(type: ServerType, version: String) {
        Log.i(
            TAG_POCKETCRAFT_APP,
            "requestVersionChange version=$version current=$versionId"
        )
        selectedServerType = type
        versionId = version
        scope.launch {
            AppPreferencesStore.setSetupComplete(context, true)
            AppPreferencesStore.setSelectedServerType(context, type.name)
            AppPreferencesStore.setSelectedVersion(context, version)
            stateHolder.markActiveWorldSetupCompleted()
            transitionTarget = Screen.SERVER
            screen = Screen.LOADING
        }
    }

    suspend fun applyVersionChange(
        type: ServerType,
        resolvedVersion: String,
        customJar: String?,
        createNewWorld: Boolean
    ) {
        if (createNewWorld) {
            val worldHint = "${type.name.lowercase()}_${resolvedVersion.replace('.', '_')}"
            val createdMessage = stateHolder.createWorld(worldHint)
            val createdWorld = parseCreatedWorldName(createdMessage)
            if (createdWorld == null) {
                Toast.makeText(context, createdMessage, Toast.LENGTH_LONG).show()
                return
            }
            val switchResult = stateHolder.setActiveWorld(createdWorld, syncPluginProfiles = false)
            if (!switchResult.startsWith("Active world switched")) {
                Toast.makeText(context, switchResult, Toast.LENGTH_LONG).show()
                return
            }
        }

        val currentConfig = stateHolder.config
        val newConfig = currentConfig.copy(
            serverType = type,
            gameVersion = resolvedVersion,
            customJarPath = customJar
        )
        stateHolder.saveSettings(newConfig)

        if (type == ServerType.MODPACK) {
            val modpackId = customJar?.trim().orEmpty()
            if (modpackId.isBlank()) {
                Toast.makeText(context, "Please select a modpack first.", Toast.LENGTH_LONG).show()
                return
            }

            loadingStatus = "Installing modpack..."
            versionDownloadProgress = 0
            screen = Screen.DOWNLOADING

            val installResult = ModpackManager.installModpack(
                context = context.applicationContext,
                modpackId = modpackId,
                onStatus = { status ->
                    scope.launch { loadingStatus = status }
                },
                onProgress = { percent ->
                    scope.launch { versionDownloadProgress = percent.coerceIn(0, 100) }
                }
            )

            if (installResult.isFailure) {
                Toast.makeText(
                    context,
                    "Failed to install modpack: ${installResult.exceptionOrNull()?.message ?: "unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
                loadingStatus = "Preparing PocketCraft..."
                versionDownloadProgress = 0
                transitionTarget = Screen.SERVER
                screen = Screen.SERVER
                return
            }

            AppPreferencesStore.setSelectedServerType(context, type.name)
            AppPreferencesStore.setSelectedVersion(context, modpackId)
            downloadedVersions = scanDownloadedRuntimeKeys(context.applicationContext)
            requestVersionChange(type, modpackId)
            return
        }

        if (type.supportsVersionSelect) {
            loadingStatus = "Downloading $type $resolvedVersion..."
            versionDownloadProgress = 0
            screen = Screen.DOWNLOADING
            val downloadResult = runCatching {
                withContext(Dispatchers.IO) {
                    val targetFile = ServerFileManager.getServerJarFile(
                        context = context.applicationContext,
                        gameVersion = resolvedVersion,
                        serverType = type
                    )
                    ServerJarManager.resolveJar(
                        serverType = type,
                        gameVersion = resolvedVersion,
                        customJarPath = null,
                        targetFile = targetFile,
                        onProgress = { percent ->
                            scope.launch {
                                versionDownloadProgress = percent.coerceIn(0, 100)
                                loadingStatus = "Downloading $type $resolvedVersion... $percent%"
                            }
                        }
                    ).collect { }
                }
            }
            if (downloadResult.isFailure) {
                val error = downloadResult.exceptionOrNull()
                Toast.makeText(
                    context,
                    "Failed to download $type $resolvedVersion: ${error?.message ?: "unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
                loadingStatus = "Preparing PocketCraft..."
                versionDownloadProgress = 0
                transitionTarget = Screen.SERVER
                screen = Screen.SERVER
                return
            }
        }

        if (type.supportsVersionSelect) {
            AppPreferencesStore.setSelectedServerType(context, type.name)
            AppPreferencesStore.setSelectedVersion(context, resolvedVersion)
            downloadedVersions = scanDownloadedRuntimeKeys(context.applicationContext)
            requestVersionChange(type, resolvedVersion)
        } else {
            loadingStatus = "Preparing PocketCraft..."
            transitionTarget = Screen.SERVER
            screen = Screen.SERVER
        }
    }

    suspend fun discardPendingVersionChange() {
        val rollback = pendingVersionRollbackConfig
        pendingVersionChange = null
        pendingVersionRollbackConfig = null
        showVersionRiskDialog = false
        if (rollback != null) {
            val current = stateHolder.config
            val shouldRollback = current.serverType != rollback.first ||
                current.gameVersion != rollback.second ||
                current.customJarPath != rollback.third
            if (shouldRollback) {
                stateHolder.saveSettings(
                    current.copy(
                        serverType = rollback.first,
                        gameVersion = rollback.second,
                        customJarPath = rollback.third
                    )
                )
            }
        }
    }

    LaunchedEffect(pendingAutoDownloadVersion, selectedServerType, downloadedVersions) {
        val pending = pendingAutoDownloadVersion?.trim().orEmpty()
        if (pending.isBlank()) return@LaunchedEffect
        if (downloadedVersions.contains(runtimeDownloadKey(selectedServerType, pending))) {
            pendingAutoDownloadVersion = null
            AppPreferencesStore.setPendingAutoDownloadVersion(context, null)
            return@LaunchedEffect
        }

        PocketCraftApp.applicationScope.launch {
            runCatching {
                withContext(Dispatchers.Main.immediate) {
                    applyVersionChange(
                        type = selectedServerType,
                        resolvedVersion = pending,
                        customJar = null,
                        createNewWorld = false
                    )
                }
            }
            withContext(Dispatchers.Main.immediate) {
                downloadedVersions = scanDownloadedRuntimeKeys(context.applicationContext)
                pendingAutoDownloadVersion = null
                AppPreferencesStore.setPendingAutoDownloadVersion(context, null)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            Screen.LOADING -> SplashScreen(
                progress = 0.66f,
                status = loadingStatus
            )

            Screen.DOWNLOADING -> SplashScreen(
                progress = (versionDownloadProgress / 100f).coerceIn(0f, 1f),
                status = loadingStatus
            )

            Screen.SERVER -> ServerScreen(
                stateHolder = stateHolder,
                onChangeVersion = { showVersionPickerDialog = true },
                onVersionSelected = { version ->
                    requestVersionChange(stateHolder.config.serverType, version)
                },
                onRequestExit = { showExitDialog = true },
                isDarkTheme = isDarkTheme,
                onDarkThemeChange = onDarkThemeChange,
                homeTopContent = {
                    if (screen == Screen.SERVER) {
                        configBanner?.let { banner ->
                            BroadcastBanner(
                                message = banner,
                                onDismiss = { broadcastViewModel.dismissConfigBanner() },
                                enableDetailsSheet = false,
                                outerPadding = PaddingValues(0.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        broadcasts.forEach { message ->
                            BroadcastBanner(
                                message = message,
                                onDismiss = { broadcastViewModel.dismiss(message.id) },
                                enableDetailsSheet = false,
                                outerPadding = PaddingValues(0.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            )

            Screen.VERSION_PICKER -> Unit
        }

        // Broadcasts are rendered inline within the home screen under the server card.
    }

    if (showVersionPickerDialog) {
        val versionPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    versionPickerSheetState.hide()
                    showVersionPickerDialog = false
                }
            },
            sheetState = versionPickerSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            ServerTypeVersionBottomSheet(
                onDismissRequest = {
                    scope.launch {
                        versionPickerSheetState.hide()
                        showVersionPickerDialog = false
                    }
                },
                onConfirm = { type, version, customJar ->
                    scope.launch {
                        versionPickerSheetState.hide()
                        showVersionPickerDialog = false
                        val currentConfig = stateHolder.config
                        val resolvedVersion = when (type) {
                            ServerType.MODPACK -> customJar ?: currentConfig.gameVersion
                            else -> version ?: currentConfig.gameVersion
                        }
                        val shouldWarn = currentConfig.serverType != type || currentConfig.gameVersion != resolvedVersion
                        if (shouldWarn) {
                            pendingVersionRollbackConfig = Triple(
                                currentConfig.serverType,
                                currentConfig.gameVersion,
                                currentConfig.customJarPath
                            )
                            pendingVersionChange = PendingVersionChange(type, resolvedVersion, customJar)
                            showVersionRiskDialog = true
                        } else {
                            applyVersionChange(type, resolvedVersion, customJar, createNewWorld = false)
                        }
                    }
                },
                currentServerType = stateHolder.config.serverType,
                currentGameVersion = stateHolder.config.gameVersion,
                currentCustomJarPath = stateHolder.config.customJarPath
            )
        }
    }

    if (showVersionRiskDialog && pendingVersionChange != null) {
        val riskSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val pending = pendingVersionChange!!
        val current = stateHolder.config
        val changeLabel = describeVersionChange(
            oldType = current.serverType,
            oldVersion = current.gameVersion,
            newType = pending.type,
            newVersion = pending.version
        )
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    riskSheetState.hide()
                    discardPendingVersionChange()
                }
            },
            sheetState = riskSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Confirm server change", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(
                    "You are about to $changeLabel. Continuing can break worlds, plugins, or mods.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                Text(
                    "Safer option: start a new world for this server type/version.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                DuoButton(
                    text = "START NEW WORLD",
                    onClick = {
                        scope.launch {
                            riskSheetState.hide()
                            pendingVersionRollbackConfig = null
                            showVersionRiskDialog = false
                            applyVersionChange(
                                type = pending.type,
                                resolvedVersion = pending.version,
                                customJar = pending.customJar,
                                createNewWorld = true
                            )
                            pendingVersionChange = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                DuoButton(
                    text = "CONTINUE (AT YOUR RISK)",
                    variant = com.pocketcraft.server.ui.components.DuoButtonVariant.Danger,
                    onClick = {
                        scope.launch {
                            riskSheetState.hide()
                            pendingVersionRollbackConfig = null
                            showVersionRiskDialog = false
                            applyVersionChange(
                                type = pending.type,
                                resolvedVersion = pending.version,
                                customJar = pending.customJar,
                                createNewWorld = false
                            )
                            pendingVersionChange = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            riskSheetState.hide()
                            discardPendingVersionChange()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }

    if (showConsentDialog) {
        val consentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {},
            sheetState = consentSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Privacy choices", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(
                    "Choose whether PocketCraft can use optional diagnostics and analytics. You can change this later in Settings > Privacy & Legal.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                Text(
                    "Policy version ${BuildConfig.LEGAL_POLICY_VERSION}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )

                DuoButton(
                    text = "ACCEPT ALL",
                    onClick = {
                        scope.launch {
                            AppPreferencesStore.setCrashDiagnosticsConsent(context, true)
                            AppPreferencesStore.setAnalyticsConsent(context, true)
                            AppPreferencesStore.setLegalVersionAccepted(context, BuildConfig.LEGAL_POLICY_VERSION)
                            runCatching {
                                Firebase.crashlytics.setCrashlyticsCollectionEnabled(true)
                            }
                            FirebaseAnalyticsManager.initialize(context.applicationContext, collectionEnabled = true)
                            FirebaseAnalyticsManager.logEvent("consent_accept_all")
                            showConsentDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                DuoButton(
                    text = "ESSENTIAL ONLY",
                    onClick = {
                        scope.launch {
                            AppPreferencesStore.setCrashDiagnosticsConsent(context, true)
                            AppPreferencesStore.setAnalyticsConsent(context, false)
                            AppPreferencesStore.setLegalVersionAccepted(context, BuildConfig.LEGAL_POLICY_VERSION)
                            runCatching {
                                Firebase.crashlytics.setCrashlyticsCollectionEnabled(true)
                            }
                            FirebaseAnalyticsManager.setCollectionEnabled(false)
                            showConsentDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(
                    onClick = {
                        openExternalUrl(context, BuildConfig.PRIVACY_POLICY_URL)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Read Privacy Policy", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                }
                TextButton(
                    onClick = {
                        openExternalUrl(context, BuildConfig.TERMS_OF_USE_URL)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Read Terms of Use", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                }
            }
        }
    }



    if (showExitDialog) {
        val exitSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    exitSheetState.hide()
                    showExitDialog = false
                }
            },
            sheetState = exitSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Exit PocketCraft", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(
                    "Are you sure you want to exit?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                DuoButton(
                    text = "YES, EXIT",
                    variant = com.pocketcraft.server.ui.components.DuoButtonVariant.Danger,
                    onClick = {
                        scope.launch {
                            exitSheetState.hide()
                            showExitDialog = false
                            activity?.finish()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                DuoButton(
                    text = "NO",
                    onClick = {
                        scope.launch {
                            exitSheetState.hide()
                            showExitDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showCommunityDialog) {
        val communitySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    communitySheetState.hide()
                    showCommunityDialog = false
                }
            },
            sheetState = communitySheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = popupAccentContainerColor,
                    tonalElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.discord_social),
                            contentDescription = "Discord",
                            modifier = Modifier.size(36.dp),
                            tint = Color.Unspecified
                        )
                    }
                }

                androidx.compose.foundation.layout.Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Join our Discord",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Connect with the PocketCraft community, share your worlds, and get tips from other players",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                DuoButton(
                    text = "JOIN DISCORD",
                    icon = null,
                    onClick = {
                        val opened = FeedbackService.openDiscord(context)
                        if (opened) {
                            preferences.socialLinksJoined = true
                        }
                        scope.launch {
                            communitySheetState.hide()
                            showCommunityDialog = false
                            if (showInstagramButtonFromRemoteConfig) {
                                pendingInstagramDialog = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(
                    onClick = {
                        scope.launch {
                            communitySheetState.hide()
                            showCommunityDialog = false
                            if (showInstagramButtonFromRemoteConfig) {
                                pendingInstagramDialog = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Maybe later",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showInstagramDialog) {
        val instagramSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    instagramSheetState.hide()
                    showInstagramDialog = false
                }
            },
            sheetState = instagramSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = popupAccentContainerColor,
                    tonalElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = "file:///android_asset/social/instagram.png",
                            contentDescription = "Instagram",
                            modifier = Modifier.size(36.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                androidx.compose.foundation.layout.Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Follow us on Instagram",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Get updates, feature previews, and community highlights from PocketCraft.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                DuoButton(
                    text = "FOLLOW INSTAGRAM",
                    icon = null,
                    onClick = {
                        val opened = FeedbackService.openInstagram(context)
                        if (opened) {
                            preferences.socialLinksJoined = true
                        }
                        scope.launch {
                            instagramSheetState.hide()
                            showInstagramDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(
                    onClick = {
                        scope.launch {
                            instagramSheetState.hide()
                            showInstagramDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Maybe later",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showFeedbackPromptDialog && pendingFeedbackPrompt != null) {
        val feedbackSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    feedbackSheetState.hide()
                    showFeedbackPromptDialog = false
                    pendingFeedbackPrompt = null
                    feedbackPromptInput = ""
                    feedbackPromptError = null
                    preferences.clearPendingFeedbackPrompt()
                    FeedbackPromptCenter.clear()
                }
            },
            sheetState = feedbackSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = pendingFeedbackPrompt!!.title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
                Text(
                    text = pendingFeedbackPrompt!!.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = feedbackPromptInput,
                    onValueChange = {
                        feedbackPromptInput = it
                        feedbackPromptError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 6,
                    label = { Text("Your feedback") }
                )
                if (!feedbackPromptError.isNullOrBlank()) {
                    Text(
                        text = feedbackPromptError!!,
                        color = PocketColors.Danger,
                        fontSize = 12.sp
                    )
                }
                DuoButton(
                    text = if (sendingFeedbackPrompt) "SENDING..." else pendingFeedbackPrompt!!.ctaLabel.uppercase(),
                    onClick = {
                        scope.launch {
                            val message = feedbackPromptInput.trim()
                            if (message.isBlank()) {
                                feedbackPromptError = "Write a short message first."
                                return@launch
                            }
                            sendingFeedbackPrompt = true
                            feedbackPromptError = null
                            val result = FeedbackService.submitFeedback(context, message, versionId)
                            sendingFeedbackPrompt = false
                            if (result.isSuccess) {
                                feedbackSheetState.hide()
                                showFeedbackPromptDialog = false
                                pendingFeedbackPrompt = null
                                feedbackPromptInput = ""
                                preferences.clearPendingFeedbackPrompt()
                                FeedbackPromptCenter.clear()
                            } else {
                                feedbackPromptError = result.exceptionOrNull()?.message ?: "Could not send feedback right now."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            feedbackSheetState.hide()
                            showFeedbackPromptDialog = false
                            pendingFeedbackPrompt = null
                            feedbackPromptInput = ""
                            feedbackPromptError = null
                            preferences.clearPendingFeedbackPrompt()
                            FeedbackPromptCenter.clear()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Later")
                }
            }
        }
    }
}


private fun scanDownloadedRuntimeKeys(context: Context): Set<String> {
    // JARs now live at files/servers/binaries/<version>/<type>-<version>.jar
    val serversRoot = File(context.filesDir, "servers/binaries")
    if (!serversRoot.isDirectory) return emptySet()
    val keys = mutableSetOf<String>()
    serversRoot.listFiles()?.forEach { versionDir ->
        if (!versionDir.isDirectory) return@forEach
        versionDir.listFiles()?.forEach { jar ->
            if (!jar.isFile) return@forEach
            if (!jar.extension.equals("jar", ignoreCase = true)) return@forEach
            if (jar.length() <= 50_000L) return@forEach
            // jar name is "<type>-<version>.jar" where version matches parent dir
            val nameWithout = jar.nameWithoutExtension
            val typePrefix = nameWithout.substringBefore('-', "").takeIf { it.isNotBlank() } ?: return@forEach
            val version = nameWithout.substringAfter('-', "").takeIf { it.isNotBlank() } ?: return@forEach
            keys.add("${typePrefix.uppercase()}::$version")
        }
    }
    return keys
}

private fun runtimeDownloadKey(type: ServerType, version: String): String {
    return "${type.name}::$version"
}

private fun latestDownloadedVersionForType(
    downloadedRuntimeKeys: Set<String>,
    type: ServerType
): String? {
    return downloadedRuntimeKeys.asSequence()
        .mapNotNull { key ->
            val parts = key.split("::", limit = 2)
            val keyType = parts.getOrNull(0) ?: return@mapNotNull null
            val version = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (keyType != type.name) return@mapNotNull null
            version
        }
        .sortedWith { left, right -> compareVersionIdsDescending(left, right) }
        .firstOrNull()
}

private fun compareVersionIdsDescending(left: String, right: String): Int {
    val leftParts = left.split('.', '-', '_')
    val rightParts = right.split('.', '-', '_')
    val maxLength = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until maxLength) {
        val leftPart = leftParts.getOrNull(index)
        val rightPart = rightParts.getOrNull(index)
        if (leftPart == rightPart) continue

        val leftNumber = leftPart?.toIntOrNull()
        val rightNumber = rightPart?.toIntOrNull()
        val comparison = when {
            leftNumber != null && rightNumber != null -> rightNumber.compareTo(leftNumber)
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> (rightPart ?: "").compareTo(leftPart ?: "")
        }
        if (comparison != 0) return comparison
    }
    return 0
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openExternalUrl(context: Context, url: String): Boolean {
    if (url.isBlank()) return false
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

private fun parseCreatedWorldName(message: String): String? {
    val directPrefix = "World added: "
    val renamedPrefix = "World added as "
    return when {
        message.startsWith(directPrefix) -> message.removePrefix(directPrefix).removeSuffix(".").trim().ifBlank { null }
        message.startsWith(renamedPrefix) -> message.removePrefix(renamedPrefix).substringBefore(" because").trim().ifBlank { null }
        else -> null
    }
}

private fun describeVersionChange(
    oldType: ServerType,
    oldVersion: String,
    newType: ServerType,
    newVersion: String
): String {
    if (oldType != newType) {
        return "switch server type from ${oldType.displayName} to ${newType.displayName}"
    }
    val cmp = compareVersionStrings(newVersion, oldVersion)
    return when {
        cmp > 0 -> "upgrade from $oldVersion to $newVersion"
        cmp < 0 -> "downgrade from $oldVersion to $newVersion"
        else -> "reconfigure the same version ($newVersion)"
    }
}

private fun compareVersionStrings(left: String, right: String): Int {
    val leftParts = left.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
    val rightParts = right.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
    val maxSize = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until maxSize) {
        val l = leftParts.getOrElse(index) { 0 }
        val r = rightParts.getOrElse(index) { 0 }
        if (l != r) return l.compareTo(r)
    }
    return 0
}
