package com.pocketcraft.server.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import com.pocketcraft.server.broadcast.FeedbackPromptCenter
import com.pocketcraft.server.broadcast.FeedbackPromptPayload
import com.pocketcraft.server.broadcast.PocketCraftMessagingService
import com.pocketcraft.server.feedback.FeedbackService
import com.pocketcraft.server.analytics.FirebaseAnalyticsManager
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.config.RemoteConfigManager
import com.pocketcraft.server.R
import com.pocketcraft.server.ui.components.BroadcastBanner
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.theme.pocketPopupAccentContainerColor
import com.pocketcraft.server.update.GitHubApkInstaller
import com.pocketcraft.server.update.GitHubUpdateChecker
import com.pocketcraft.server.viewmodel.BroadcastViewModel
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class Screen { LOADING, VERSION_PICKER, DOWNLOADING, SERVER }

private const val TAG_POCKETCRAFT_APP = "PocketCraftApp"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketCraftApp(
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    var screen by remember { mutableStateOf(Screen.SERVER) }
    var transitionTarget by remember { mutableStateOf<Screen?>(null) }
    var versionId by remember { mutableStateOf("1.21.6") }
    var downloadedVersions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showVersionPickerDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showCommunityDialog by remember { mutableStateOf(false) }
    var pendingCommunityDialog by remember { mutableStateOf(false) }
    var discordPopupAlreadyShownRemotely by remember { mutableStateOf<Boolean?>(null) }
    var showInstagramDialog by remember { mutableStateOf(false) }
    var pendingInstagramDialog by remember { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }
    var pendingConsentDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<GitHubUpdateChecker.ReleaseInfo?>(null) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    var changelogInfo by remember { mutableStateOf<GitHubUpdateChecker.ReleaseInfo?>(null) }
    var pendingFeedbackPrompt by remember { mutableStateOf<FeedbackPromptPayload?>(null) }
    var showFeedbackPromptDialog by remember { mutableStateOf(false) }
    var feedbackPromptInput by remember { mutableStateOf("") }
    var sendingFeedbackPrompt by remember { mutableStateOf(false) }
    var feedbackPromptError by remember { mutableStateOf<String?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var updateDownloadProgress by remember { mutableStateOf(0) }
    var updateDownloadStatus by remember { mutableStateOf("Preparing update...") }
    var updateDownloadError by remember { mutableStateOf<String?>(null) }
    var awaitingInstallPermission by remember { mutableStateOf(false) }
    var pendingInstallResult by remember { mutableStateOf<GitHubApkInstaller.DownloadResult?>(null) }
    var homeScreenReady by remember { mutableStateOf(false) }
    var releaseCheckHandled by remember { mutableStateOf(false) }
    var showDiscordButtonFromRemoteConfig by remember { mutableStateOf(true) }
    var showInstagramButtonFromRemoteConfig by remember { mutableStateOf(true) }
    var sessionClosedBroadcastIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences = remember { AppPreferences(context) }
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val stateHolder = remember(versionId) { ServerStateHolder(context.applicationContext, versionId) }
    val broadcastViewModel: BroadcastViewModel = hiltViewModel()
    val configBanner by broadcastViewModel.configBanner.collectAsState()
    val broadcasts by broadcastViewModel.visibleBroadcasts.collectAsState()
    val notificationsEnabled by AppPreferencesStore.isNotificationsEnabledFlow(context).collectAsState(initial = true)
    val analyticsConsentGranted by AppPreferencesStore.isAnalyticsConsentFlow(context).collectAsState(initial = false)
    val adsConsentGranted by AppPreferencesStore.isAdsConsentFlow(context).collectAsState(initial = false)
    val legalVersionAccepted by AppPreferencesStore.getLegalVersionAcceptedFlow(context).collectAsState(initial = null)
    val isFirstLaunchAfterInstallOrUpdate = remember(BuildConfig.VERSION_NAME) {
        preferences.lastLaunchedAppVersion != BuildConfig.VERSION_NAME
    }
    val colorScheme = MaterialTheme.colorScheme
    val popupAccentContainerColor = pocketPopupAccentContainerColor()
    val hasPendingBroadcast by remember(configBanner, broadcasts, sessionClosedBroadcastIds) {
        mutableStateOf(
            (configBanner != null && configBanner!!.id !in sessionClosedBroadcastIds) ||
                broadcasts.any { it.id !in sessionClosedBroadcastIds }
        )
    }
    val hasBlockingSheet = showVersionPickerDialog ||
        showConsentDialog ||
        showCommunityDialog ||
        showInstagramDialog ||
        showFeedbackPromptDialog ||
        showUpdateDialog ||
        showChangelogDialog ||
        isDownloadingUpdate ||
        awaitingInstallPermission ||
        updateDownloadError != null ||
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
        val savedVersionId = AppPreferencesStore.getSelectedVersionFlow(context).first()
        val pendingAutoDownloadVersion = AppPreferencesStore.getPendingAutoDownloadVersionFlow(context).first()

        versionId = savedVersionId

        if (!setupComplete) {
            AppPreferencesStore.setSetupComplete(context, true)
        }
        downloadedVersions = scanDownloadedVersionIds(context.applicationContext)

        if (!pendingAutoDownloadVersion.isNullOrBlank() && !downloadedVersions.contains(pendingAutoDownloadVersion)) {
            versionId = pendingAutoDownloadVersion
            transitionTarget = Screen.DOWNLOADING
            screen = Screen.LOADING
        }
        AppPreferencesStore.setPendingAutoDownloadVersion(context, null)
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
        showUpdateDialog,
        isDownloadingUpdate,
        awaitingInstallPermission,
        updateDownloadError,
        showExitDialog,
        screen,
        homeScreenReady
    ) {
        if (!pendingConsentDialog) return@LaunchedEffect
        if (screen != Screen.SERVER || !homeScreenReady) return@LaunchedEffect
        val hasBlockingPopup = showVersionPickerDialog || showUpdateDialog || isDownloadingUpdate || awaitingInstallPermission || updateDownloadError != null || showExitDialog || showCommunityDialog || showInstagramDialog
        if (hasBlockingPopup) return@LaunchedEffect
        showConsentDialog = true
        pendingConsentDialog = false
    }

    LaunchedEffect(
        pendingCommunityDialog,
        showVersionPickerDialog,
        showConsentDialog,
        showInstagramDialog,
        showUpdateDialog,
        isDownloadingUpdate,
        awaitingInstallPermission,
        updateDownloadError,
        showExitDialog,
        screen,
        homeScreenReady,
        hasPendingBroadcast
    ) {
        if (!pendingCommunityDialog) return@LaunchedEffect
        if (screen != Screen.SERVER || !homeScreenReady) return@LaunchedEffect
        val hasBlockingPopup = showVersionPickerDialog || showConsentDialog || showUpdateDialog || isDownloadingUpdate || awaitingInstallPermission || updateDownloadError != null || showExitDialog || showCommunityDialog || showInstagramDialog || hasPendingBroadcast
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
        showUpdateDialog,
        isDownloadingUpdate,
        awaitingInstallPermission,
        updateDownloadError,
        showExitDialog,
        screen,
        homeScreenReady,
        hasPendingBroadcast
    ) {
        if (!pendingInstagramDialog) return@LaunchedEffect
        if (screen != Screen.SERVER || !homeScreenReady) return@LaunchedEffect
        if (!showInstagramButtonFromRemoteConfig) return@LaunchedEffect
        val hasBlockingPopup = showVersionPickerDialog || showConsentDialog || showCommunityDialog || showUpdateDialog || isDownloadingUpdate || awaitingInstallPermission || updateDownloadError != null || showExitDialog || showInstagramDialog || showFeedbackPromptDialog || hasPendingBroadcast
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
        showUpdateDialog,
        isDownloadingUpdate,
        awaitingInstallPermission,
        updateDownloadError,
        showExitDialog,
        screen,
        homeScreenReady,
        hasPendingBroadcast
    ) {
        if (pendingFeedbackPrompt == null) return@LaunchedEffect
        if (screen != Screen.SERVER || !homeScreenReady) return@LaunchedEffect
        val hasBlockingPopup = showVersionPickerDialog || showConsentDialog || showCommunityDialog || showInstagramDialog || showUpdateDialog || isDownloadingUpdate || awaitingInstallPermission || updateDownloadError != null || showExitDialog || hasPendingBroadcast
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

    DisposableEffect(lifecycleOwner, awaitingInstallPermission, pendingInstallResult) {
        if (!awaitingInstallPermission || pendingInstallResult == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
                if (!GitHubApkInstaller.canRequestPackageInstalls(context.applicationContext)) return@LifecycleEventObserver

                val pending = pendingInstallResult ?: return@LifecycleEventObserver
                runCatching {
                    context.startActivity(
                        GitHubApkInstaller.buildInstallIntent(context.applicationContext, pending)
                    )
                    awaitingInstallPermission = false
                    pendingInstallResult = null
                }.onFailure { error ->
                    updateDownloadError = error.message ?: "Could not open installer after permission was granted."
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    LaunchedEffect(screen, homeScreenReady, hasPendingBroadcast, hasBlockingSheet, releaseCheckHandled) {
        if (releaseCheckHandled) {
            return@LaunchedEffect
        }
        if (screen != Screen.SERVER || !homeScreenReady || hasPendingBroadcast || hasBlockingSheet) {
            return@LaunchedEffect
        }

        releaseCheckHandled = true
        scope.launch {
            val latestRelease = GitHubUpdateChecker.fetchLatestRelease(context)
            if (latestRelease == null) return@launch
            when {
                GitHubUpdateChecker.compareVersions(latestRelease.tagName, BuildConfig.VERSION_NAME) > 0 -> {
                    updateInfo = latestRelease
                    delay(500)
                    showUpdateDialog = true
                }
                GitHubUpdateChecker.compareVersions(latestRelease.tagName, BuildConfig.VERSION_NAME) == 0 &&
                    isFirstLaunchAfterInstallOrUpdate &&
                    latestRelease.body.isNotBlank() &&
                    preferences.lastSeenChangelogVersion != latestRelease.tagName -> {
                    changelogInfo = latestRelease
                    delay(500)
                    showChangelogDialog = true
                }
            }
        }
    }

    BackHandler(enabled = screen == Screen.DOWNLOADING) {
        screen = Screen.SERVER
    }

    fun requestVersionChange(version: String) {
        Log.i(
            TAG_POCKETCRAFT_APP,
            "requestVersionChange version=$version current=$versionId alreadyDownloaded=${downloadedVersions.contains(version)}"
        )
        versionId = version
        scope.launch {
            AppPreferencesStore.setSetupComplete(context, true)
            AppPreferencesStore.setSelectedVersion(context, version)
            stateHolder.markActiveWorldSetupCompleted()
            transitionTarget = if (downloadedVersions.contains(version)) Screen.SERVER else Screen.DOWNLOADING
            Log.i(TAG_POCKETCRAFT_APP, "transition target=${transitionTarget?.name} for version=$version")
            screen = Screen.LOADING
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            Screen.LOADING -> SplashScreen(
                progress = 0.66f,
                status = if (transitionTarget == Screen.DOWNLOADING) "Preparing download..." else "Preparing PocketCraft..."
            )

            Screen.DOWNLOADING -> AutoDownloadScreen(
                versionId = versionId,
                onReady = {
                    Log.i(TAG_POCKETCRAFT_APP, "AutoDownloadScreen onReady version=$versionId")
                    downloadedVersions = downloadedVersions + versionId
                    screen = Screen.SERVER
                }
            )

            Screen.SERVER -> ServerScreen(
                stateHolder = stateHolder,
                onChangeVersion = { showVersionPickerDialog = true },
                onVersionSelected = ::requestVersionChange,
                onRequestExit = { showExitDialog = true },
                isDarkTheme = isDarkTheme,
                onDarkThemeChange = onDarkThemeChange,
                homeTopContent = {
                    if (screen == Screen.SERVER && homeScreenReady) {
                        configBanner?.let { banner ->
                            if (banner.id !in sessionClosedBroadcastIds) {
                                BroadcastBanner(
                                    message = banner,
                                    onDismiss = {
                                        broadcastViewModel.dismissConfigBanner()
                                        sessionClosedBroadcastIds = sessionClosedBroadcastIds + banner.id
                                    },
                                    enableDetailsSheet = false,
                                    outerPadding = PaddingValues(0.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        broadcasts.forEach { message ->
                            if (message.id !in sessionClosedBroadcastIds) {
                                BroadcastBanner(
                                    message = message,
                                    onDismiss = {
                                        broadcastViewModel.dismiss(message.id)
                                        sessionClosedBroadcastIds = sessionClosedBroadcastIds + message.id
                                    },
                                    enableDetailsSheet = false,
                                    outerPadding = PaddingValues(0.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Switch Minecraft version", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Text(
                        "Choose a version below. If it is not downloaded yet, PocketCraft will fetch it automatically.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }

                VersionPickerScreen(
                    selectedVersion = versionId,
                    showWorldSetupHint = stateHolder.activeWorldNeedsSetup,
                    worldName = stateHolder.config.worldName.ifBlank { "world" },
                    embeddedInSheet = true,
                    onVersionSelected = { selected ->
                        scope.launch {
                            versionPickerSheetState.hide()
                            showVersionPickerDialog = false
                            requestVersionChange(selected)
                        }
                    }
                )

                TextButton(
                    onClick = {
                        scope.launch {
                            versionPickerSheetState.hide()
                            showVersionPickerDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
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
                    "Choose whether PocketCraft can use optional diagnostics, analytics, and ads. You can change this later in Settings > Privacy & Legal.",
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
                            AppPreferencesStore.setAdsConsent(context, true)
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
                            AppPreferencesStore.setAdsConsent(context, false)
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
                    onClick = {
                        scope.launch {
                            exitSheetState.hide()
                            showExitDialog = false
                            activity?.finish()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            exitSheetState.hide()
                            showExitDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("No", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
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

    if (showUpdateDialog && updateInfo != null) {
        AppUpdateScreen(
            updateInfo = updateInfo!!,
            isDownloading = isDownloadingUpdate,
            downloadProgress = updateDownloadProgress,
            downloadStatus = updateDownloadStatus,
            isAwaitingInstallPermission = awaitingInstallPermission,
            errorMessage = updateDownloadError,
            onUpdateNow = {
                val selectedUpdate = updateInfo
                if (selectedUpdate == null) {
                    showUpdateDialog = false
                    return@AppUpdateScreen
                }
                val apkUrl = selectedUpdate.downloadUrl
                if (apkUrl.isNullOrBlank()) {
                    updateDownloadError = "No APK asset was attached to this GitHub release."
                    return@AppUpdateScreen
                }

                scope.launch {
                    try {
                        isDownloadingUpdate = true
                        updateDownloadError = null
                        updateDownloadStatus = "Downloading update..."
                        updateDownloadProgress = 0

                        val downloadResult = GitHubApkInstaller.downloadApk(
                            context = context.applicationContext,
                            downloadUrl = apkUrl,
                            onProgress = { progress ->
                                updateDownloadProgress = progress
                                updateDownloadStatus = if (progress < 100) {
                                    "Downloading update..."
                                } else {
                                    "Download complete. Opening installer..."
                                }
                            }
                        ).getOrThrow()

                        if (!GitHubApkInstaller.canRequestPackageInstalls(context.applicationContext)) {
                            isDownloadingUpdate = false
                            awaitingInstallPermission = true
                            pendingInstallResult = downloadResult
                            context.startActivity(GitHubApkInstaller.buildUnknownAppsSettingsIntent(context.applicationContext))
                            return@launch
                        }

                        isDownloadingUpdate = false
                        updateDownloadStatus = "Opening installer..."
                        showUpdateDialog = false
                        context.startActivity(GitHubApkInstaller.buildInstallIntent(context.applicationContext, downloadResult))
                    } catch (error: Throwable) {
                        isDownloadingUpdate = false
                        updateDownloadError = error.message ?: "Could not download update from GitHub."
                    }
                }
            },
            onLater = {
                if (!isDownloadingUpdate && !awaitingInstallPermission) {
                    showUpdateDialog = false
                    updateDownloadError = null
                }
            },
            onOpenInstallSettings = {
                runCatching {
                    context.startActivity(
                        GitHubApkInstaller.buildUnknownAppsSettingsIntent(context.applicationContext)
                    )
                }.onFailure { error ->
                    updateDownloadError = error.message ?: "Could not open settings."
                }
            },
            onInstallPermissionEnabled = {
                if (GitHubApkInstaller.canRequestPackageInstalls(context.applicationContext)) {
                    val pending = pendingInstallResult ?: return@AppUpdateScreen
                    runCatching {
                        context.startActivity(
                            GitHubApkInstaller.buildInstallIntent(context.applicationContext, pending)
                        )
                        awaitingInstallPermission = false
                        pendingInstallResult = null
                        showUpdateDialog = false
                    }.onFailure { error ->
                        updateDownloadError = error.message ?: "Could not open installer."
                    }
                }
            },
            onDismissError = {
                updateDownloadError = null
            }
        )
    }

    if (showChangelogDialog && changelogInfo != null) {
        AppChangelogScreen(
            releaseInfo = changelogInfo!!,
            onDismiss = {
                preferences.lastSeenChangelogVersion = changelogInfo!!.tagName
                showChangelogDialog = false
            }
        )
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


private fun scanDownloadedVersionIds(context: Context): Set<String> {
    val serversRoot = File(context.filesDir, "servers")
    val dirs = serversRoot.listFiles() ?: return emptySet()
    return dirs.asSequence()
        .filter { it.isDirectory }
        .mapNotNull { dir ->
            val version = dir.name
            val jar = File(dir, "paper-$version.jar")
            version.takeIf { jar.exists() && jar.length() > 1_000_000L }
        }
        .toSet()
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
