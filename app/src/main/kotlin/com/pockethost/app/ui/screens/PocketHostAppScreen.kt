package com.pockethost.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.google.android.play.core.review.ReviewManagerFactory
import com.pockethost.app.ui.components.PocketAppLogo
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlin.math.abs
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.pockethost.app.BuildConfig
import com.pockethost.app.broadcast.FeedbackPromptCenter
import com.pockethost.app.broadcast.FeedbackPromptPayload
import com.pockethost.app.broadcast.PocketHostMessagingService
import com.pockethost.app.feedback.FeedbackService
import com.pockethost.app.analytics.FirebaseAnalyticsManager
import com.pockethost.app.data.preferences.AppPreferencesStore
import com.pockethost.app.data.preferences.AppPreferences
import com.pockethost.app.config.RemoteConfigManager
import com.pockethost.app.R
import com.pockethost.app.ui.components.BroadcastBanner
import com.pockethost.app.ui.components.AnnouncementDialog
import com.pockethost.app.ui.components.BroadcastPopup
import com.pockethost.app.ui.components.NewFeaturesPopup
import com.pockethost.app.ui.components.DuoButton
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.PocketMotion
import com.pockethost.app.ui.theme.card3d
import com.pockethost.app.ui.theme.pocketPopupAccentContainerColor
import com.pockethost.app.ui.util.MobTheme
import com.pockethost.app.service.ModpackManager
import com.pockethost.app.ui.components.ServerModpackPickerBottomSheet
import com.pockethost.app.viewmodel.BroadcastViewModel
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Restore
import com.pockethost.app.billing.BillingManager
import com.pockethost.app.billing.PremiumTier
import com.pockethost.app.ui.components.PromotionBottomSheet
import com.pockethost.app.ui.components.PremiumUpgradeBottomSheet
import com.pockethost.app.data.model.ServerType
import com.pockethost.app.server.ServerHostService
import com.pockethost.app.service.ServerFileManager
import com.pockethost.app.service.ServerPropertiesHelper
import com.pockethost.app.data.repository.ServerConfigRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { LOADING, DOWNLOADING, VERSION_PICKER, SERVER }

private const val TAG_POCKETCRAFT_APP = "PocketHostApp"
private data class PendingVersionChange(
    val type: ServerType,
    val version: String,
    val customJar: String?
)

private data class PromotionData(
    val id: String,
    val title: String,
    val body: String,
    val ctaText: String,
    val iconEmoji: String,
    val targetGroup: String
)

private const val RECURRING_UPSELL_REQUIRED_OPENS = 5
private const val FREE_TO_PRO_UPSELL_TITLE = "Ready for more?"
private const val FREE_TO_PRO_UPSELL_BODY =
    "Upgrade to PocketCraft Pro to unlock more power for your server, including higher player limits, extra customization, and premium tools."
private const val FREE_TO_PRO_UPSELL_CTA = "Upgrade to Pro"
private const val PRO_TO_MEMBER_UPSELL_TITLE = "Enjoying Pro?"
private const val PRO_TO_MEMBER_UPSELL_BODY =
    "If PocketCraft Pro has been valuable for you, becoming a Member is a simple way to support the project and help us keep improving it."
private const val PRO_TO_MEMBER_UPSELL_CTA = "Upgrade to Member"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketHostApp(
    isDarkTheme: Boolean,
    isPlayStoreRatingPromptEnabled: Boolean,
    currentMobTheme: MobTheme,
    onDarkThemeChange: (Boolean) -> Unit,
    onMobThemeChange: (MobTheme) -> Unit
) {
    var screen by remember { mutableStateOf(Screen.LOADING) }
    var transitionTarget by remember { mutableStateOf<Screen?>(null) }
    var loadingStatus by remember { mutableStateOf("Preparing PocketCraft...") }
    var versionDownloadProgress by remember { mutableStateOf(0) }
    var versionId by remember { mutableStateOf("") }
    var lastProcessedWorld by remember { mutableStateOf("") }
    var bootstrapComplete by remember { mutableStateOf(false) }
    var selectedServerType by remember { mutableStateOf(ServerType.PAPER) }
    var downloadedVersions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showVersionPickerDialog by remember { mutableStateOf(false) }
    var showBedrockCreationDialog by remember { mutableStateOf(false) }
    var showVersionRiskDialog by remember { mutableStateOf(false) }
    var pendingVersionChange by remember { mutableStateOf<PendingVersionChange?>(null) }
    var pendingVersionRollbackConfig by remember { mutableStateOf<Triple<ServerType, String, String?>?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showAnnouncementDialog by remember { mutableStateOf(false) }
    var pendingAnnouncementDialog by remember { mutableStateOf(false) }
    var showNewFeaturesDialog by remember { mutableStateOf(false) }
    var pendingNewFeaturesDialog by remember { mutableStateOf(false) }
    var showInstagramDialog by remember { mutableStateOf(false) }
    var pendingInstagramDialog by remember { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }
    var pendingConsentDialog by remember { mutableStateOf(false) }
    var pendingFeedbackPrompt by remember { mutableStateOf<FeedbackPromptPayload?>(null) }
    var showFeedbackPromptDialog by remember { mutableStateOf(false) }
    var feedbackPromptInput by remember { mutableStateOf("") }
    var sendingFeedbackPrompt by remember { mutableStateOf(false) }
    var feedbackPromptError by remember { mutableStateOf<String?>(null) }
    var showRatingPromptDialog by remember { mutableStateOf(false) }
    var homeScreenReady by remember { mutableStateOf(false) }
    var showInstagramButtonFromRemoteConfig by remember { mutableStateOf(true) }
    var showModpackImportDialog by remember { mutableStateOf(false) }
    var pendingModpackImportId by remember { mutableStateOf<String?>(null) }
    var pendingModpackImportPageUrl by remember { mutableStateOf<String?>(null) }
    var modpackImportInProgress by remember { mutableStateOf(false) }
    var modpackImportId by remember { mutableStateOf<String?>(null) }
    var modpackImportStatus by remember { mutableStateOf("Preparing modpack import...") }
    var modpackImportProgress by remember { mutableStateOf(0) }
    var modpackImportError by remember { mutableStateOf<String?>(null) }
    var showForceReconnectPrompt by remember { mutableStateOf(false) }

    var activePromotion by remember { mutableStateOf<PromotionData?>(null) }
    var showPromotionDialog by remember { mutableStateOf(false) }
    var showPremiumUpgradeDialog by remember { mutableStateOf(false) }
    var showDonationReminderDialog by remember { mutableStateOf(false) }
    var pendingDonationReminderDialog by remember { mutableStateOf(false) }
    // Only one non-consent popup shows per app launch to avoid overwhelming the user.
    var popupShownThisLaunch by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences = remember { AppPreferences(context) }
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val selectedWorld by AppPreferencesStore.getSelectedWorldFlow(context).collectAsState(initial = "world")
    val stateHolder = remember(versionId, selectedWorld) {
        ServerStateHolder(context.applicationContext, versionId, selectedWorld)
    }
    val broadcastViewModel: BroadcastViewModel = hiltViewModel()
    val configBanner by broadcastViewModel.configBanner.collectAsState()
    val broadcasts by broadcastViewModel.visibleBroadcasts.collectAsState()
    val notificationsEnabled by AppPreferencesStore.isNotificationsEnabledFlow(context).collectAsState(initial = true)
    val analyticsConsentGranted by AppPreferencesStore.isAnalyticsConsentFlow(context).collectAsState(initial = false)
    val legalVersionAccepted by AppPreferencesStore.getLegalVersionAcceptedFlow(context).collectAsState(initial = null)
    val billingManager = remember { BillingManager.getInstance(context) }
    val entitlement by billingManager.entitlement.collectAsState()
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
        showAnnouncementDialog ||
        showNewFeaturesDialog ||
        showInstagramDialog ||
        showFeedbackPromptDialog ||
        showRatingPromptDialog ||
        showExitDialog ||
        showPromotionDialog ||
        showDonationReminderDialog ||
        showPremiumUpgradeDialog

    DisposableEffect(stateHolder) {
        onDispose { stateHolder.dispose() }
    }

    LaunchedEffect(BuildConfig.VERSION_NAME) {
        if (preferences.lastLaunchedAppVersion != BuildConfig.VERSION_NAME) {
            preferences.lastLaunchedAppVersion = BuildConfig.VERSION_NAME
        }
    }

    LaunchedEffect(versionId, selectedWorld, currentMobTheme, isDarkTheme) {
        ServerHostService.pushWidgetUpdate(context.applicationContext)
    }

    LaunchedEffect(entitlement.tier) {
        val currentTier = entitlement.tier.wireValue
        val previousTier = preferences.lastSeenMembershipTier.ifBlank { PremiumTier.NONE.wireValue }

        when (entitlement.tier) {
            PremiumTier.NONE -> {
                if (previousTier != PremiumTier.NONE.wireValue || !preferences.freeToProUpsellTrackingStarted) {
                    preferences.freeToProUpsellTrackingStarted = true
                    preferences.freeToProUpsellStartLaunchCount = preferences.appLaunchCount
                    preferences.freeToProUpsellLastShownLaunchCount = 0
                }
                preferences.resetProToMemberUpsellTracking(currentTier)
            }
            PremiumTier.PREMIUM -> {
                preferences.resetFreeToProUpsellTracking()
                if (previousTier != PremiumTier.PREMIUM.wireValue || !preferences.proToMemberUpsellTrackingStarted) {
                    preferences.proToMemberUpsellTrackingStarted = true
                    preferences.proToMemberUpsellStartLaunchCount = preferences.appLaunchCount
                    preferences.proToMemberUpsellLastShownLaunchCount = 0
                    preferences.proToMemberUpsellShown = false
                }
            }
            PremiumTier.SUPPORTIVE -> {
                preferences.resetFreeToProUpsellTracking()
                preferences.resetProToMemberUpsellTracking(currentTier)
            }
        }

        preferences.lastSeenMembershipTier = currentTier
    }

    LaunchedEffect(notificationsEnabled) {
        if (notificationsEnabled) {
            PocketHostMessagingService.subscribeToAllUsers()
        }
    }

    LaunchedEffect(Unit) {
        launch {
            com.pockethost.app.broadcast.RemoteCommandListener.forceReconnectTrigger.collect {
                showForceReconnectPrompt = true
            }
        }
        launch {
            com.pockethost.app.broadcast.RemoteCommandListener.triggerRatingPromptFlow.collect {
                showRatingPromptDialog = true
                popupShownThisLaunch = true
            }
        }
    }

    LaunchedEffect(Unit) {
        val setupComplete = AppPreferencesStore.isSetupCompleteFlow(context).first()
        val storedVersionId = AppPreferencesStore.getStoredSelectedVersionFlow(context).first()
        val storedServerType = ServerType.fromString(
            AppPreferencesStore.getSelectedServerTypeFlow(context).first()
        )
        val pending = AppPreferencesStore.getPendingAutoDownloadVersionFlow(context).first()
        val activeWorld = AppPreferencesStore.getSelectedWorldFlow(context).first()

        val worldDir = ServerFileManager.getServerDir(context.applicationContext, activeWorld)
        val worldProps = ServerPropertiesHelper.readProperties(worldDir)
        val worldVersion = worldProps.getProperty("pocketcraft-game-version").orEmpty()
        val worldServerType = worldProps.getProperty("pocketcraft-server-type")
            ?.takeIf { it.isNotBlank() }
            ?.let(ServerType::fromString)
        val bootstrapVersion = worldVersion.ifBlank { storedVersionId.orEmpty() }
        val initialServerType = worldServerType
            ?: if (bootstrapVersion.isNotBlank()) storedServerType else ServerType.PAPER

        selectedServerType = initialServerType
        downloadedVersions = scanDownloadedRuntimeKeys(context.applicationContext)
        lastProcessedWorld = activeWorld

        versionId = bootstrapVersion

        if (bootstrapVersion.isNotBlank() && worldVersion.isBlank()) {
            val repository = ServerConfigRepository(context).apply {
                setWorldNameOverride(activeWorld)
            }
            val loadedConfig = repository.loadConfig()
            stateHolder.saveSettings(
                loadedConfig.copy(
                    serverType = initialServerType,
                    gameVersion = bootstrapVersion,
                    customJarPath = if (initialServerType == ServerType.MODPACK) {
                        loadedConfig.customJarPath?.takeIf { it.isNotBlank() } ?: bootstrapVersion
                    } else {
                        null
                    }
                ),
                targetWorldName = activeWorld
            )
        }

        if (!pending.isNullOrBlank()) {
            AppPreferencesStore.setPendingAutoDownloadVersion(context, null)
        }

        if (!setupComplete) {
            AppPreferencesStore.setSetupComplete(context, true)
        }
        AppPreferencesStore.setSelectedServerType(context, initialServerType.name)
        if (bootstrapVersion.isNotBlank() && storedVersionId != bootstrapVersion) {
            AppPreferencesStore.setSelectedVersion(context, bootstrapVersion)
        }
        bootstrapComplete = true
        transitionTarget = Screen.SERVER
    }

    LaunchedEffect(stateHolder, versionId, selectedServerType, bootstrapComplete) {
        if (!bootstrapComplete) return@LaunchedEffect

        if (stateHolder.activeWorld != lastProcessedWorld) {
            val targetServerDir = ServerFileManager.getServerDir(context.applicationContext, stateHolder.activeWorld)
            val targetProps = ServerPropertiesHelper.readProperties(targetServerDir)
            val rawTargetVersion = targetProps.getProperty("pocketcraft-game-version").orEmpty()
            val targetVersion = rawTargetVersion.ifBlank {
                versionId.ifBlank {
                    AppPreferencesStore.getStoredSelectedVersionFlow(context).first().orEmpty()
                }
            }
            val rawTargetType = targetProps.getProperty("pocketcraft-server-type")
                ?.takeIf { it.isNotBlank() }
                ?.let(ServerType::fromString)
            val targetType = rawTargetType
                ?: if (targetVersion.isNotBlank()) selectedServerType else ServerType.PAPER

            if (targetVersion.isNotBlank() && rawTargetVersion.isBlank()) {
                val repository = ServerConfigRepository(context).apply {
                    setWorldNameOverride(stateHolder.activeWorld)
                }
                val loadedConfig = repository.loadConfig()
                stateHolder.saveSettings(
                    loadedConfig.copy(
                        serverType = targetType,
                        gameVersion = targetVersion,
                        customJarPath = if (targetType == ServerType.MODPACK) {
                            loadedConfig.customJarPath?.takeIf { it.isNotBlank() } ?: targetVersion
                        } else {
                            null
                        }
                    ),
                    targetWorldName = stateHolder.activeWorld
                )
            }

            var updated = false
            if (targetVersion.isNotBlank() && targetVersion != versionId) {
                versionId = targetVersion
                AppPreferencesStore.setSelectedVersion(context, targetVersion)
                updated = true
            }
            if (targetType != selectedServerType) {
                selectedServerType = targetType
                AppPreferencesStore.setSelectedServerType(context, targetType.name)
                updated = true
            }
            lastProcessedWorld = stateHolder.activeWorld

            if (updated) {
                return@LaunchedEffect
            }
        }

        if (versionId.isBlank()) {
            val restoredVersion = stateHolder.config.gameVersion
                .takeIf { it.isNotBlank() }
                ?: AppPreferencesStore.getStoredSelectedVersionFlow(context).first().orEmpty()
            if (restoredVersion.isNotBlank()) {
                versionId = restoredVersion
                AppPreferencesStore.setSelectedVersion(context, restoredVersion)
            }
            return@LaunchedEffect
        }

        stateHolder.refreshAll()
        snapshotFlow { stateHolder.isRefreshing }
            .dropWhile { !it }
            .first { !it }

        val current = stateHolder.config
        if (current.gameVersion.isBlank()) {
            stateHolder.saveSettings(
                current.copy(
                    serverType = selectedServerType,
                    gameVersion = versionId,
                    customJarPath = if (selectedServerType == ServerType.MODPACK) {
                        current.customJarPath?.takeIf { it.isNotBlank() } ?: versionId
                    } else {
                        null
                    }
                )
            )
            AppPreferencesStore.setSelectedServerType(context, selectedServerType.name)
            AppPreferencesStore.setSelectedVersion(context, versionId)
            return@LaunchedEffect
        }
        val shouldSyncRuntime = current.gameVersion != versionId || current.serverType != selectedServerType
        if (shouldSyncRuntime) {
            selectedServerType = current.serverType
            versionId = current.gameVersion
            AppPreferencesStore.setSelectedServerType(context, current.serverType.name)
            AppPreferencesStore.setSelectedVersion(context, current.gameVersion)
            return@LaunchedEffect
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
                title = title.ifBlank { "Help improve PocketHost" },
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
        RemoteConfigManager.showInstagramButton.collect { value ->
            showInstagramButtonFromRemoteConfig = value
        }
    }

    LaunchedEffect(screen, transitionTarget) {
        if (screen != Screen.LOADING) {
            return@LaunchedEffect
        }
        val nextScreen = transitionTarget ?: Screen.SERVER
        delay(40)
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
        showInstagramButtonFromRemoteConfig,
        hasPendingBroadcast,
        showAnnouncementDialog,
        pendingAnnouncementDialog,
        showNewFeaturesDialog,
        pendingNewFeaturesDialog
    ) {
        if (screen != Screen.SERVER || !homeScreenReady) {
            return@LaunchedEffect
        }
        if (hasPendingBroadcast) {
            return@LaunchedEffect
        }
        val needsConsent = legalVersionAccepted != BuildConfig.LEGAL_POLICY_VERSION
        if (needsConsent) {
            pendingConsentDialog = true
        }
        if (
            !showAnnouncementDialog &&
            !pendingAnnouncementDialog &&
            AnnouncementDialog.shouldShow(context)
        ) {
            pendingAnnouncementDialog = true
        }
        if (
            false // Disabled: do not show new features popup
        ) {
            pendingNewFeaturesDialog = true
        }
        val shouldPromptInstagram = !preferences.socialLinksJoined && showInstagramButtonFromRemoteConfig
        if (shouldPromptInstagram) {
            delay(700)
            pendingInstagramDialog = true
        }
    }

    LaunchedEffect(
        pendingConsentDialog,
        showVersionPickerDialog,
        showAnnouncementDialog,
        showNewFeaturesDialog,
        showInstagramDialog,
        showExitDialog,
        screen,
        homeScreenReady
    ) {
        if (!pendingConsentDialog) return@LaunchedEffect
        if (screen != Screen.SERVER || !homeScreenReady) return@LaunchedEffect
        val hasBlockingPopup = showVersionPickerDialog || showExitDialog || showAnnouncementDialog || showNewFeaturesDialog || showInstagramDialog
        if (hasBlockingPopup) return@LaunchedEffect
        showConsentDialog = true
        pendingConsentDialog = false
    }

    LaunchedEffect(
        pendingAnnouncementDialog,
        pendingConsentDialog,
        showVersionPickerDialog,
        showConsentDialog,
        showInstagramDialog,
        showExitDialog,
        screen,
        homeScreenReady,
        hasPendingBroadcast
    ) {
        if (!pendingAnnouncementDialog) return@LaunchedEffect
        if (screen != Screen.SERVER || !homeScreenReady) return@LaunchedEffect
        if (pendingConsentDialog) return@LaunchedEffect
        if (popupShownThisLaunch) return@LaunchedEffect
        val hasBlockingPopup = showVersionPickerDialog || showConsentDialog || showExitDialog || showAnnouncementDialog || showNewFeaturesDialog || showInstagramDialog || hasPendingBroadcast
        if (hasBlockingPopup) return@LaunchedEffect
        showAnnouncementDialog = true
        pendingAnnouncementDialog = false
        popupShownThisLaunch = true
    }

    LaunchedEffect(
        pendingNewFeaturesDialog,
        pendingConsentDialog,
        pendingAnnouncementDialog,
        showVersionPickerDialog,
        showConsentDialog,
        showAnnouncementDialog,
        showInstagramDialog,
        showExitDialog,
        screen,
        homeScreenReady,
        hasPendingBroadcast
    ) {
        if (!pendingNewFeaturesDialog) return@LaunchedEffect
        if (screen != Screen.SERVER || !homeScreenReady) return@LaunchedEffect
        if (pendingConsentDialog || pendingAnnouncementDialog) return@LaunchedEffect
        if (popupShownThisLaunch) return@LaunchedEffect
        val hasBlockingPopup = showVersionPickerDialog || showConsentDialog || showExitDialog || showAnnouncementDialog || showNewFeaturesDialog || showInstagramDialog || hasPendingBroadcast
        if (hasBlockingPopup) return@LaunchedEffect
        showNewFeaturesDialog = true
        pendingNewFeaturesDialog = false
        popupShownThisLaunch = true
    }

    LaunchedEffect(
        pendingInstagramDialog,
        pendingFeedbackPrompt,
        pendingConsentDialog,
        pendingAnnouncementDialog,
        pendingNewFeaturesDialog,
        showVersionPickerDialog,
        showConsentDialog,
        showAnnouncementDialog,
        showNewFeaturesDialog,
        showExitDialog,
        screen,
        homeScreenReady,
        hasPendingBroadcast
    ) {
        if (!pendingInstagramDialog) return@LaunchedEffect
        if (screen != Screen.SERVER || !homeScreenReady) return@LaunchedEffect
        if (!showInstagramButtonFromRemoteConfig) return@LaunchedEffect
        if (pendingConsentDialog || pendingAnnouncementDialog || pendingNewFeaturesDialog) return@LaunchedEffect
        if (popupShownThisLaunch) return@LaunchedEffect
        val hasBlockingPopup = showVersionPickerDialog || showConsentDialog || showAnnouncementDialog || showNewFeaturesDialog || showExitDialog || showInstagramDialog || showFeedbackPromptDialog || hasPendingBroadcast
        if (hasBlockingPopup) return@LaunchedEffect
        showInstagramDialog = true
        pendingInstagramDialog = false
        popupShownThisLaunch = true
    }

    LaunchedEffect(
        pendingFeedbackPrompt,
        pendingConsentDialog,
        pendingAnnouncementDialog,
        pendingNewFeaturesDialog,
        showVersionPickerDialog,
        showConsentDialog,
        showAnnouncementDialog,
        showNewFeaturesDialog,
        showInstagramDialog,
        showExitDialog,
        screen,
        homeScreenReady,
        hasPendingBroadcast
    ) {
        if (pendingFeedbackPrompt == null) return@LaunchedEffect
        if (screen != Screen.SERVER || !homeScreenReady) return@LaunchedEffect
        if (pendingConsentDialog || pendingAnnouncementDialog || pendingNewFeaturesDialog) return@LaunchedEffect
        if (popupShownThisLaunch) return@LaunchedEffect
        val hasBlockingPopup = showVersionPickerDialog || showConsentDialog || showAnnouncementDialog || showNewFeaturesDialog || showInstagramDialog || showExitDialog || hasPendingBroadcast
        if (hasBlockingPopup) return@LaunchedEffect
        showFeedbackPromptDialog = true
        popupShownThisLaunch = true
    }

    LaunchedEffect(
        isPlayStoreRatingPromptEnabled,
        screen,
        homeScreenReady,
        pendingConsentDialog,
        pendingAnnouncementDialog,
        pendingNewFeaturesDialog,
        showVersionPickerDialog,
        showConsentDialog,
        showAnnouncementDialog,
        showNewFeaturesDialog,
        showInstagramDialog,
        showFeedbackPromptDialog,
        showExitDialog,
        hasPendingBroadcast
    ) {
        if (!isPlayStoreRatingPromptEnabled) return@LaunchedEffect
        if (screen != Screen.SERVER || !homeScreenReady) return@LaunchedEffect
        if (pendingConsentDialog || pendingAnnouncementDialog || pendingNewFeaturesDialog) return@LaunchedEffect
        if (preferences.ratingPopupDismissedForever) return@LaunchedEffect
        // Show if a real player joined in a previous session OR if the user has successfully started the server 3+ times
        val hasMetStartsThreshold = preferences.successfulServerStarts >= 3
        if (!preferences.pendingRatingPopup && !hasMetStartsThreshold) return@LaunchedEffect
        if (preferences.ratingPopupShowCount >= 3) return@LaunchedEffect
        val cooldownMs = 14L * 24L * 60L * 60L * 1000L
        if (System.currentTimeMillis() - preferences.ratingPopupLastShownAt < cooldownMs) return@LaunchedEffect
        if (popupShownThisLaunch) return@LaunchedEffect
        val hasBlockingPopup = showVersionPickerDialog ||
            showConsentDialog ||
            showAnnouncementDialog ||
            showNewFeaturesDialog ||
            showInstagramDialog ||
            showFeedbackPromptDialog ||
            showExitDialog ||
            hasPendingBroadcast
        if (hasBlockingPopup) return@LaunchedEffect
        delay(1_000)
        preferences.pendingRatingPopup = false
        preferences.ratingPopupLastShownAt = System.currentTimeMillis()
        preferences.ratingPopupShowCount = preferences.ratingPopupShowCount + 1
        showRatingPromptDialog = true
        popupShownThisLaunch = true
    }

    LaunchedEffect(
        screen,
        homeScreenReady,
        showVersionPickerDialog,
        showConsentDialog,
        showAnnouncementDialog,
        showNewFeaturesDialog,
        showInstagramDialog,
        showFeedbackPromptDialog,
        showRatingPromptDialog,
        showExitDialog,
        hasPendingBroadcast
    ) {
        if (screen != Screen.SERVER || !homeScreenReady) return@LaunchedEffect
        val is15thLaunch = preferences.appLaunchCount > 0 && (preferences.appLaunchCount % 15 == 0)
        if (is15thLaunch && preferences.donationReminderLastShownLaunchCount != preferences.appLaunchCount) {
            pendingDonationReminderDialog = true
        }
    }

    LaunchedEffect(
        pendingDonationReminderDialog,
        pendingConsentDialog,
        pendingAnnouncementDialog,
        pendingNewFeaturesDialog,
        showVersionPickerDialog,
        showConsentDialog,
        showAnnouncementDialog,
        showNewFeaturesDialog,
        showInstagramDialog,
        showFeedbackPromptDialog,
        showRatingPromptDialog,
        showExitDialog,
        showPromotionDialog,
        showPremiumUpgradeDialog,
        screen,
        homeScreenReady,
        hasPendingBroadcast
    ) {
        if (!pendingDonationReminderDialog) return@LaunchedEffect
        if (screen != Screen.SERVER || !homeScreenReady) return@LaunchedEffect
        if (pendingConsentDialog || pendingAnnouncementDialog || pendingNewFeaturesDialog) return@LaunchedEffect
        val hasBlockingPopup = showVersionPickerDialog ||
            showConsentDialog ||
            showAnnouncementDialog ||
            showNewFeaturesDialog ||
            showInstagramDialog ||
            showFeedbackPromptDialog ||
            showRatingPromptDialog ||
            showExitDialog ||
            showPromotionDialog ||
            showPremiumUpgradeDialog ||
            hasPendingBroadcast
        if (hasBlockingPopup) return@LaunchedEffect
        if (popupShownThisLaunch) return@LaunchedEffect
        // Voluntary donation popups disabled
    }

    LaunchedEffect(
        screen,
        homeScreenReady,
        entitlement.tier,
        showVersionPickerDialog,
        showConsentDialog,
        showAnnouncementDialog,
        showNewFeaturesDialog,
        showInstagramDialog,
        showFeedbackPromptDialog,
        showRatingPromptDialog,
        showExitDialog,
        hasPendingBroadcast,
        pendingDonationReminderDialog,
        showDonationReminderDialog
    ) {
        if (pendingDonationReminderDialog || showDonationReminderDialog || showNewFeaturesDialog) return@LaunchedEffect
        if (screen != Screen.SERVER || !homeScreenReady) return@LaunchedEffect
        val targetGroup = when (entitlement.tier) {
            PremiumTier.NONE -> "free"
            PremiumTier.PREMIUM -> "pro"
            else -> null
        }
        if (targetGroup != null) {
            val shownPromotions = preferences.getShownPromotions()
            val db = FirebaseFirestore.getInstance()
            runCatching {
                db.collection("promotions")
                    .whereEqualTo("active", true)
                    .whereEqualTo("targetGroup", targetGroup)
                    .get()
                    .await()
            }.onSuccess { querySnapshot ->
                val promoDoc = querySnapshot.documents.firstOrNull { doc ->
                    val id = doc.id
                    !shownPromotions.contains(id)
                }
                if (promoDoc != null) {
                    val id = promoDoc.id
                    val title = promoDoc.getString("title").orEmpty()
                    val body = promoDoc.getString("body").orEmpty()
                    val ctaText = promoDoc.getString("ctaText").orEmpty().ifBlank { "Upgrade Now" }
                    val iconEmoji = promoDoc.getString("iconEmoji").orEmpty().ifBlank { "🚀" }
                    activePromotion = PromotionData(
                        id = id,
                        title = title,
                        body = body,
                        ctaText = ctaText,
                        iconEmoji = iconEmoji,
                        targetGroup = targetGroup
                    )
                    showPromotionDialog = true
                    popupShownThisLaunch = true
                }
            }.onFailure { e ->
                Log.e(TAG_POCKETCRAFT_APP, "Failed to fetch active promotions: ${e.message}", e)
            }
        }
    }

    SideEffect {
        val window = activity?.window ?: return@SideEffect
        val isOnSplash = screen == Screen.LOADING || screen == Screen.DOWNLOADING
        val statusBarColor = when {
            hasBlockingSheet -> colorScheme.surface.toArgb()
            isOnSplash -> PocketColors.BgApp.toArgb()
            screen == Screen.SERVER -> colorScheme.surface.toArgb()
            else -> colorScheme.background.toArgb()
        }
        val navBarColor = when {
            hasBlockingSheet -> colorScheme.surface.toArgb()
            isOnSplash -> PocketColors.BgApp.toArgb()
            screen == Screen.SERVER -> PocketColors.FooterBg.toArgb()
            else -> PocketColors.FooterBg.toArgb()
        }
        window.statusBarColor = statusBarColor
        window.navigationBarColor = navBarColor
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = Color(statusBarColor).luminance() >= 0.5f
            isAppearanceLightNavigationBars = Color(navBarColor).luminance() >= 0.5f
        }
    }

    fun requestVersionChange(type: ServerType, version: String) {
        val selectedVersion = version.trim()
        if (selectedVersion.isBlank()) {
            Toast.makeText(context, "Please select a server version first.", Toast.LENGTH_LONG).show()
            return
        }
        Log.i(
            TAG_POCKETCRAFT_APP,
            "requestVersionChange version=$selectedVersion current=$versionId"
        )
        selectedServerType = type
        versionId = selectedVersion
        scope.launch {
            stateHolder.saveSettings(
                stateHolder.config.copy(
                    serverType = type,
                    gameVersion = selectedVersion,
                    customJarPath = if (type == ServerType.MODPACK) selectedVersion else null
                )
            )
            AppPreferencesStore.setSetupComplete(context, true)
            AppPreferencesStore.setSelectedServerType(context, type.name)
            AppPreferencesStore.setSelectedVersion(context, selectedVersion)
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
        val effectiveType = if (
            type.supportsVersionSelect &&
            customJar.isNullOrBlank() &&
            isLikelyModpackRuntimeId(resolvedVersion)
        ) {
            ServerType.MODPACK
        } else {
            type
        }
        val effectiveCustomJar = if (effectiveType == ServerType.MODPACK) {
            customJar?.takeIf { it.isNotBlank() } ?: resolvedVersion
        } else {
            customJar
        }
        val modpackIdForConfig = if (effectiveType == ServerType.MODPACK) {
            effectiveCustomJar.orEmpty().substringBefore('|').trim()
        } else {
            null
        }
        val effectiveVersion = if (effectiveType == ServerType.MODPACK) {
            modpackIdForConfig.orEmpty()
        } else {
            resolvedVersion
        }

        if (createNewWorld) {
            val worldHint = "${effectiveType.name.lowercase()}_${effectiveVersion.replace('.', '_')}"
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
            serverType = effectiveType,
            gameVersion = effectiveVersion,
            customJarPath = if (effectiveType == ServerType.MODPACK) modpackIdForConfig else effectiveCustomJar
        )
        stateHolder.saveSettings(newConfig)

        if (effectiveType == ServerType.MODPACK) {
            val rawPayload = effectiveCustomJar?.trim().orEmpty()
            // Payload may be encoded as "modpackId|pageUrl" to carry browser URL
            val modpackId = rawPayload.substringBefore('|').trim()
            val encodedPageUrl = rawPayload.substringAfter('|', missingDelimiterValue = "").trim()
                .takeIf { it.startsWith("http") }

            if (modpackId.isBlank()) {
                Toast.makeText(context, "Please select a modpack first.", Toast.LENGTH_LONG).show()
                return
            }

            val activeWorld = stateHolder.activeWorld.ifBlank { "world" }
            if (ModpackManager.isModpackInstalled(context.applicationContext, activeWorld, modpackId)) {
                AppPreferencesStore.setSelectedServerType(context, effectiveType.name)
                AppPreferencesStore.setSelectedVersion(context, modpackId)
                downloadedVersions = scanDownloadedRuntimeKeys(context.applicationContext)
                requestVersionChange(effectiveType, modpackId)
                return
            }

            pendingModpackImportId = modpackId
            pendingModpackImportPageUrl = encodedPageUrl
            showModpackImportDialog = true
            return
        }

        if (effectiveType.supportsVersionSelect) {
            val jarReady = withContext(Dispatchers.IO) {
                ServerFileManager.isServerJarReady(
                    context = context.applicationContext,
                    gameVersion = effectiveVersion,
                    serverType = effectiveType
                )
            }
            if (!jarReady) {
                requestVersionChange(effectiveType, effectiveVersion)
                return
            }
        }

        if (effectiveType.supportsVersionSelect) {
            AppPreferencesStore.setSelectedServerType(context, effectiveType.name)
            AppPreferencesStore.setSelectedVersion(context, effectiveVersion)
            downloadedVersions = scanDownloadedRuntimeKeys(context.applicationContext)
            requestVersionChange(effectiveType, effectiveVersion)
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

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn(animationSpec = PocketMotion.softFloatTween(durationMillis = 120)) +
                    scaleIn(initialScale = 0.99f, animationSpec = PocketMotion.softFloatTween(durationMillis = 140))) togetherWith
                (fadeOut(animationSpec = PocketMotion.softFloatTween(durationMillis = 100)) +
                    scaleOut(targetScale = 0.995f, animationSpec = PocketMotion.softFloatTween(durationMillis = 120)))
            },
            label = "app_screen_transition"
        ) { targetScreen ->
            when (targetScreen) {
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
                    onInstallCurrentVersion = {
                        scope.launch {
                            val currentConfig = stateHolder.config
                            val configuredRuntime = currentConfig.customJarPath
                                ?.takeIf { it.isNotBlank() }
                                ?: currentConfig.gameVersion.takeIf { it.isNotBlank() }
                            val installType = if (
                                currentConfig.serverType.supportsVersionSelect &&
                                isLikelyModpackRuntimeId(configuredRuntime.orEmpty())
                            ) {
                                ServerType.MODPACK
                            } else {
                                currentConfig.serverType
                            }
                            val modpackId = currentConfig.customJarPath
                                ?.takeIf { it.isNotBlank() }
                                ?: currentConfig.gameVersion.takeIf { it.isNotBlank() }
                            if (installType == ServerType.MODPACK && modpackId.isNullOrBlank()) {
                                Toast.makeText(context, "Please select a modpack first.", Toast.LENGTH_LONG).show()
                                return@launch
                            }
                            val resolvedVersion = if (installType == ServerType.MODPACK) {
                                modpackId.orEmpty()
                            } else {
                                currentConfig.gameVersion
                            }
                            if (installType != ServerType.MODPACK && resolvedVersion.isBlank()) {
                                showVersionPickerDialog = true
                                return@launch
                            }
                            val customJar = if (installType == ServerType.MODPACK) {
                                modpackId
                            } else {
                                currentConfig.customJarPath
                            }
                            applyVersionChange(
                                type = installType,
                                resolvedVersion = resolvedVersion,
                                customJar = customJar,
                                createNewWorld = false
                            )
                        }
                    },
                    onVersionSelected = { version ->
                        requestVersionChange(stateHolder.config.serverType, version)
                    },
                    onRequestExit = { showExitDialog = true },
                    isDarkTheme = isDarkTheme,
                    onDarkThemeChange = onDarkThemeChange,
                    currentMobTheme = currentMobTheme,
                    onMobThemeChange = onMobThemeChange,
                    onOpenBedrockCreation = { showBedrockCreationDialog = true },
                    homeTopContent = {
                        if (modpackImportInProgress || modpackImportError != null) {
                            ModpackImportProgressCard(
                                modpackId = modpackImportId.orEmpty(),
                                status = modpackImportError ?: modpackImportStatus,
                                progress = modpackImportProgress,
                                isError = modpackImportError != null,
                                onDismiss = if (modpackImportError != null) {
                                    {
                                        modpackImportError = null
                                        modpackImportInProgress = false
                                        modpackImportId = null
                                    }
                                } else null
                            )
                        }
                        if (targetScreen == Screen.SERVER) {
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
        }
    }

    if (stateHolder.isRestoringBackup) {
        val isBackingUp = false
        val progress = stateHolder.restoreProgressPercent
        val statusMessage = stateHolder.restoreStatusMessage
        val title = "Restoring World"
        
        val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
            targetValue = (progress / 100f).coerceIn(0f, 1f),
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
            label = "backup_restore_dialog_progress"
        )
        
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(24.dp))
                        .border(
                            1.5.dp, 
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.2f),
                                    Color.White.copy(alpha = 0.05f)
                                )
                            ), 
                            RoundedCornerShape(24.dp)
                        ),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            PocketColors.Primary.copy(alpha = 0.15f),
                                            PocketColors.PrimaryMuted.copy(alpha = 0.35f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBackingUp) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = PocketColors.Primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Restore,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = PocketColors.Primary
                                )
                            }
                        }
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = title,
                                fontFamily = com.pockethost.app.ui.theme.Monocraft,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Do not close the app or switch screens",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = PocketColors.Primary,
                                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = statusMessage,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$progress%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PocketColors.Primary,
                                    fontFamily = com.pockethost.app.ui.theme.Monocraft
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForceReconnectPrompt) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showForceReconnectPrompt = false },
            title = { 
                Text(
                    text = "Restart Required", 
                    fontFamily = com.pockethost.app.ui.theme.Monocraft, 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ) 
            },
            text = { 
                Text(
                    text = "A remote command has requested a server restart to apply updates. Restart your server now?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ) 
            },
            confirmButton = {
                DuoButton(
                    text = "RESTART NOW",
                    onClick = {
                        showForceReconnectPrompt = false
                        stateHolder.restartServer()
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showForceReconnectPrompt = false }) {
                    Text("LATER", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    val interactiveBroadcast = broadcasts.firstOrNull { it.interactionType != "none" }
    interactiveBroadcast?.let { msg ->
        BroadcastPopup(
            broadcast = msg,
            onDismiss = {
                broadcastViewModel.dismiss(msg.id)
            }
        )
    }

    if (showAnnouncementDialog) {
        AnnouncementDialog.Content(
            onFinished = {
                pendingAnnouncementDialog = false
                showAnnouncementDialog = false
            }
        )
    }

    // Disabled: NewFeaturesPopup.Content is removed.

    if (showModpackImportDialog && !pendingModpackImportId.isNullOrBlank()) {
        ServerModpackPickerBottomSheet(
            modpackId = pendingModpackImportId.orEmpty(),
            pageUrl = pendingModpackImportPageUrl,
            onDismiss = { showModpackImportDialog = false },
            onZipSelected = { zipUri ->
                val importId = pendingModpackImportId.orEmpty()
                showModpackImportDialog = false
                scope.launch {
                    modpackImportInProgress = true
                    modpackImportError = null
                    modpackImportId = importId
                    modpackImportStatus = "Extracting modpack '$importId'..."
                    modpackImportProgress = 10

                    val activeWorld = stateHolder.activeWorld.ifBlank { "world" }
                    val result = ModpackManager.importModpackZip(
                        context = context,
                        zipUri = zipUri,
                        worldName = activeWorld,
                        modpackId = importId,
                        onStatus = { status ->
                            modpackImportStatus = status
                        },
                        onProgress = { progress ->
                            modpackImportProgress = progress.coerceIn(10, 95)
                        }
                    )
                    if (result.isSuccess) {
                        Toast.makeText(context, "Modpack imported successfully!", Toast.LENGTH_LONG).show()
                        modpackImportProgress = 100
                        modpackImportStatus = "Import complete. Preparing server..."
                        val currentConfig = stateHolder.config
                        val newConfig = currentConfig.copy(
                            serverType = ServerType.MODPACK,
                            gameVersion = importId,
                            customJarPath = importId
                        )
                        stateHolder.saveSettings(newConfig)
                        AppPreferencesStore.setSelectedServerType(context, ServerType.MODPACK.name)
                        AppPreferencesStore.setSelectedVersion(context, importId)
                        downloadedVersions = scanDownloadedRuntimeKeys(context.applicationContext)
                        requestVersionChange(ServerType.MODPACK, importId)
                        delay(1_500)
                        modpackImportInProgress = false
                        modpackImportId = null
                    } else {
                        val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                        modpackImportError = "Import failed: $errorMsg"
                        modpackImportInProgress = false
                        Toast.makeText(context, "Import failed: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
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
                        applyVersionChange(type, resolvedVersion, customJar, createNewWorld = false)
                    }
                },
                currentServerType = stateHolder.config.serverType,
                currentGameVersion = stateHolder.config.gameVersion,
                currentCustomJarPath = stateHolder.config.customJarPath
            )
        }
    }

    if (showBedrockCreationDialog) {
        com.pockethost.app.ui.components.BedrockServerCreationBottomSheet(
            onDismiss = { showBedrockCreationDialog = false },
            onCreateBedrockServer = { name, port, gamemode, difficulty, maxPlayers ->
                scope.launch {
                    val createdWorldName = "bedrock_${System.currentTimeMillis() / 1000}"
                    com.pockethost.app.server.NukkitLaunchManager.prepareNukkitServer(
                        context = context,
                        worldName = createdWorldName,
                        serverName = name,
                        port = port,
                        gamemode = gamemode,
                        difficulty = difficulty,
                        maxPlayers = maxPlayers
                    )
                    stateHolder.createWorld(createdWorldName)
                    stateHolder.setActiveWorld(createdWorldName, syncPluginProfiles = false)
                    requestVersionChange(ServerType.BEDROCK, "1.21.60")
                    Toast.makeText(context, "Bedrock server '$name' created!", Toast.LENGTH_SHORT).show()
                }
            }
        )
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
                    variant = com.pockethost.app.ui.components.DuoButtonVariant.Danger,
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
                    variant = com.pockethost.app.ui.components.DuoButtonVariant.Danger,
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

    if (showRatingPromptDialog) {
        AlertDialog(
            onDismissRequest = {
                showRatingPromptDialog = false
            },
            icon = {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = popupAccentContainerColor,
                    border = BorderStroke(1.dp, PocketColors.Primary.copy(alpha = 0.28f))
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PocketAppLogo(
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            },
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Enjoying PocketCraft?",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 21.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "Your rating helps the app grow.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            text = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = popupAccentContainerColor.copy(alpha = 0.58f)
                ) {
                    Text(
                        text = "A quick Play Store rating helps more players discover PocketCraft servers.",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DuoButton(
                        text = "RATE ON PLAY STORE",
                        onClick = {
                            showRatingPromptDialog = false
                            preferences.ratingPopupDismissedForever = true
                            requestPlayStoreRating(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                showRatingPromptDialog = false
                            }
                        ) {
                            Text(
                                text = "Maybe later",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                            )
                        }
                        TextButton(
                            onClick = {
                                preferences.ratingPopupDismissedForever = true
                                showRatingPromptDialog = false
                            }
                        ) {
                            Text(
                                text = "No thanks",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                            )
                        }
                    }
                }
            },
            dismissButton = {
                Spacer(modifier = Modifier.size(0.dp))
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(26.dp)
        )
    }

    if (showPromotionDialog && activePromotion != null) {
        val promo = activePromotion!!
        PromotionBottomSheet(
            title = promo.title,
            body = promo.body,
            ctaText = promo.ctaText,
            iconEmoji = promo.iconEmoji,
            onDismissRequest = {
                preferences.markPromotionShown(promo.id)
                showPromotionDialog = false
                activePromotion = null
            },
            onCtaClick = {
                preferences.markPromotionShown(promo.id)
                showPromotionDialog = false
                activePromotion = null
                showPremiumUpgradeDialog = true
            }
        )
    }
}


private fun scanDownloadedRuntimeKeys(context: Context): Set<String> {
    // JARs now live at files/servers/binaries/<version>/<type>-<version>.jar
    val keys = mutableSetOf<String>()

    val binariesRoot = File(context.filesDir, "servers/binaries")
    binariesRoot.listFiles()?.forEach { versionDir ->
        if (versionDir.isDirectory) {
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
    }

    val worldsRoot = File(context.filesDir, "servers/worlds")
    worldsRoot.listFiles()?.forEach { worldDir ->
        if (!worldDir.isDirectory) return@forEach
        val props = com.pockethost.app.service.ServerPropertiesHelper.readProperties(worldDir)
        if (ServerType.fromString(props.getProperty("pocketcraft-server-type")) != ServerType.MODPACK) return@forEach
        val modpackId = props.getProperty("pocketcraft-modpack-id")
            ?: props.getProperty("pocketcraft-custom-jar-path")
            ?: return@forEach
        if (modpackId.isBlank()) return@forEach
        val launchTarget = ServerFileManager.readLaunchTarget(worldDir) ?: return@forEach
        if (launchTarget.file.exists() && launchTarget.file.isFile && launchTarget.file.length() > 0L) {
            keys.add(runtimeDownloadKey(ServerType.MODPACK, modpackId))
        }
    }

    return keys
}

private fun runtimeDownloadKey(type: ServerType, version: String): String {
    return "${type.name}::$version"
}

private fun isLikelyModpackRuntimeId(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return false
    if (trimmed.matches(Regex("""\d+(?:\.\d+){1,3}(?:[-+][A-Za-z0-9_.-]+)?"""))) return false
    return trimmed.any { it.isLetter() } && trimmed.any { it == '-' || it == '_' }
}

@Composable
private fun ModpackImportProgressCard(
    modpackId: String,
    status: String,
    progress: Int,
    isError: Boolean,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        PocketColors.SurfaceCard
    }
    val borderColor = if (isError) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
    } else {
        PocketColors.Primary.copy(alpha = 0.45f)
    }
    val titleColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        PocketColors.TextPrimary
    }
    val detailColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.84f)
    } else {
        PocketColors.TextSecondary
    }

    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by androidx.compose.animation.core.animateFloatAsState(
        targetValue = offsetX,
        label = "modpack_swipe_offset"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
            .then(
                if (onDismiss != null) {
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(offsetX) > 180f) {
                                    onDismiss()
                                } else {
                                    offsetX = 0f
                                }
                            },
                            onDragCancel = { offsetX = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                offsetX += dragAmount
                            }
                        )
                    }
                } else Modifier
            )
            .card3d(
                elevation = 6.dp,
                cornerRadius = 20.dp,
                borderColor = borderColor,
                depthColor = borderColor.copy(alpha = (borderColor.alpha * 1.3f).coerceAtMost(1f))
            ),
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = if (isError) "Modpack import needs attention" else "Importing modpack",
                        color = titleColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = modpackId.ifBlank { "Selected modpack" },
                        color = detailColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                if (!isError) {
                    Text(
                        text = "${progress.coerceIn(0, 100)}%",
                        color = PocketColors.Primary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                } else if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = titleColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (!isError) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(9.dp),
                    color = PocketColors.Primary,
                    trackColor = PocketColors.Primary.copy(alpha = 0.18f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }

            Text(
                text = status,
                color = detailColor,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
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

private fun openPlayStoreListing(context: Context): Boolean {
    val packageName = context.packageName
    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val openedMarket = runCatching {
        context.startActivity(marketIntent)
        true
    }.getOrDefault(false)
    if (openedMarket) return true
    return openExternalUrl(context, "https://play.google.com/store/apps/details?id=$packageName")
}

private fun requestPlayStoreRating(context: Context) {
    val activity = context.findActivity()
    if (activity == null || activity.isFinishing || activity.isDestroyed) {
        openPlayStoreListing(context)
        return
    }

    val reviewManager = ReviewManagerFactory.create(activity)
    reviewManager.requestReviewFlow().addOnCompleteListener { requestTask ->
        if (!requestTask.isSuccessful || activity.isFinishing || activity.isDestroyed) {
            openPlayStoreListing(context)
            return@addOnCompleteListener
        }

        reviewManager.launchReviewFlow(activity, requestTask.result).addOnCompleteListener {
            if (activity.isFinishing || activity.isDestroyed) return@addOnCompleteListener
            openPlayStoreListing(context)
        }
    }
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
