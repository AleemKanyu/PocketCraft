package com.pocketcraft.server.ui.onboarding

import com.pocketcraft.server.BuildConfig

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pocketcraft.server.MainActivity
import com.pocketcraft.server.config.RelayLatencySelector
import com.pocketcraft.server.config.RemoteConfigManager
import com.pocketcraft.server.config.RelayServers
import com.pocketcraft.server.data.model.RelayRegion
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.data.model.ServerType
import com.pocketcraft.server.data.repository.ServerConfigRepository
import com.pocketcraft.server.integrations.AccountManager
import com.pocketcraft.server.R
import com.pocketcraft.server.ui.screens.ServerTypeVersionBottomSheet
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.components.DuoButtonVariant
import com.pocketcraft.server.ui.components.duoOutlinedTextFieldColors
import com.pocketcraft.server.ui.components.duoTextFieldShape
import com.pocketcraft.server.ui.theme.Monocraft
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.theme.PocketMotion
import com.pocketcraft.server.ui.theme.PocketCraftTheme
import com.pocketcraft.server.ui.theme.card3d
import com.pocketcraft.server.ui.theme.pocketIsDarkTheme
import com.pocketcraft.server.ui.util.playAppHaptic
import com.pocketcraft.server.ui.util.ThemePreferenceStore
import com.pocketcraft.server.ui.util.MobTheme
import com.pocketcraft.server.util.AppStrings
import com.pocketcraft.server.util.LocalAppStrings
import com.pocketcraft.server.util.appStringsFor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val OnboardingGreenLight = Color(0xFF3DDC84)
private val OnboardingGoldLight = Color(0xFFFFB142)

private fun Dp.scaled(factor: Float): Dp = (value * factor).dp
private fun TextUnit.scaledSp(factor: Float): TextUnit = (value * factor).sp

@Composable
private fun onboardingCompactScale(): Float {
    val configuration = LocalConfiguration.current
    return when {
        configuration.screenHeightDp <= 690 || configuration.screenWidthDp <= 360 -> 0.82f
        configuration.screenHeightDp <= 760 || configuration.screenWidthDp <= 392 -> 0.9f
        else -> 1f
    }
}

@Composable
private fun onboardingIsCompact(): Boolean = onboardingCompactScale() < 1f

@Composable
private fun onboardingAccentPurple(): Color = PocketColors.Primary

@Composable
private fun onboardingAccentPurpleDark(): Color = if (pocketIsDarkTheme()) PocketColors.TextDark else PocketColors.PrimaryDark

@Composable
private fun onboardingAccentPurpleMuted(): Color = if (pocketIsDarkTheme()) {
    PocketColors.SurfaceVarDark.copy(alpha = 0.78f)
} else {
    PocketColors.PrimaryMuted
}

@Composable
private fun onboardingAccentGreen(): Color = if (pocketIsDarkTheme()) Color(0xFF54D68C) else OnboardingGreenLight

@Composable
private fun onboardingAccentGold(): Color = if (pocketIsDarkTheme()) Color(0xFFFFC76B) else OnboardingGoldLight

@Composable
private fun onboardingSurfaceColor(): Color = MaterialTheme.colorScheme.surface

@Composable
private fun onboardingSurfaceSoftColor(): Color = if (pocketIsDarkTheme()) {
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
} else {
    PocketColors.SurfaceVarLight
}

@Composable
private fun onboardingBorderColor(): Color = if (pocketIsDarkTheme()) {
    PocketColors.BorderDark.copy(alpha = 0.82f)
} else {
    PocketColors.BorderLight
}

@Composable
private fun onboardingTextPrimary(): Color = MaterialTheme.colorScheme.onSurface

@Composable
private fun onboardingTextSecondary(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun onboardingTextDark(): Color = MaterialTheme.colorScheme.onSurface

@Composable
private fun onboardingTextMuted(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)

@Composable
private fun onboardingBackgroundBrush(): Brush = if (pocketIsDarkTheme()) {
    Brush.verticalGradient(
        colors = listOf(
            PocketColors.BgDark,
            Color(0xFF121620),
            Color(0xFF07090E)
        )
    )
} else {
    Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFFFFFF),
            PocketColors.BgLight,
            Color(0xFFE8EDF5)
        )
    )
}

@Composable
private fun onboardingPhoneOuterBrush(): Brush = if (pocketIsDarkTheme()) {
    Brush.verticalGradient(
        listOf(
            Color(0xFF252B36),
            Color(0xFF161A22)
        )
    )
} else {
    Brush.verticalGradient(
        listOf(
            Color.White,
            Color(0xFFF7FAF2)
        )
    )
}

@Composable
private fun onboardingPhoneInnerBrush(): Brush = if (pocketIsDarkTheme()) {
    Brush.verticalGradient(
        listOf(
            onboardingSurfaceSoftColor(),
            Color(0xFF202633)
        )
    )
} else {
    Brush.verticalGradient(
        listOf(
            PocketColors.SurfaceVarLight,
            Color(0xFFE9EEF6)
        )
    )
}

@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.pocketcraft.server.util.LocaleUtils.wrapContext(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfig: android.content.res.Configuration?) {
        overrideConfig?.let { cfg ->
            com.pocketcraft.server.util.LocaleUtils.getSavedLocale(baseContext)?.let { locale ->
                com.pocketcraft.server.util.LocaleUtils.applyToConfig(cfg, locale)
            }
        }
        super.applyOverrideConfiguration(overrideConfig)
    }

    private val preferences by lazy { AppPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.decorView.isForceDarkAllowed = false
            window.isNavigationBarContrastEnforced = false
        }
        val initialThemePreference = ThemePreferenceStore.load(this)
        val initialMobTheme = ThemePreferenceStore.loadMobTheme(this)

        setContent {
            var themePreference by remember { mutableStateOf(initialThemePreference) }
            var mobTheme by remember { mutableStateOf(initialMobTheme) }
            val darkTheme = themePreference.resolve(systemDark = isSystemInDarkTheme())
            PocketCraftTheme(darkTheme = darkTheme, mobTheme = mobTheme) {
                SideEffect {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        window.decorView.isForceDarkAllowed = false
                        window.isNavigationBarContrastEnforced = false
                    }
                    window.statusBarColor = PocketColors.BgApp.toArgb()
                    window.navigationBarColor = PocketColors.BgApp.toArgb()
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = PocketColors.BgApp.luminance() >= 0.5f
                        isAppearanceLightNavigationBars = PocketColors.BgApp.luminance() >= 0.5f
                    }
                }
                OnboardingScreen(
                    currentMobTheme = mobTheme,
                    onMobThemeChange = { newTheme ->
                        mobTheme = newTheme
                        ThemePreferenceStore.saveMobTheme(this@OnboardingActivity, newTheme)
                        val newPref = when (newTheme) {
                            MobTheme.SIMPLE_DARK -> com.pocketcraft.server.ui.util.ThemePreference.DARK
                            MobTheme.SIMPLE_WHITE -> com.pocketcraft.server.ui.util.ThemePreference.LIGHT
                            else -> themePreference
                        }
                        themePreference = newPref
                        ThemePreferenceStore.save(this@OnboardingActivity, newPref)
                    },
                    onComplete = { completeOnboarding() }
                )
            }
        }
    }

    private fun completeOnboarding() {
        preferences.onboardingCompleted = true
        preferences.openWorldSetupNextLaunch = false
        startActivity(Intent(this, MainActivity::class.java))
        finishAffinity()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, OnboardingActivity::class.java))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingScreen(
    currentMobTheme: MobTheme,
    onMobThemeChange: (MobTheme) -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { AppPreferences(context) }
    val hapticFeedback = LocalHapticFeedback.current
    val appFeedbackEnabled by AppPreferencesStore.isSoundEnabledFlow(context).collectAsState(initial = true)
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    val s = LocalAppStrings.current
    val steps = remember(s) { onboardingSteps(s) }
    var privacyAccepted by rememberSaveable { mutableStateOf(false) }
    var setupServerName by rememberSaveable { mutableStateOf("PocketCraft Server") }
    var setupWorldDescription by rememberSaveable { mutableStateOf("") }
    var setupSeed by rememberSaveable { mutableStateOf("") }
    var setupVersion by rememberSaveable { mutableStateOf("") }
    var setupServerType by rememberSaveable { mutableStateOf(ServerType.PAPER) }
    var setupCustomJarPath by rememberSaveable { mutableStateOf<String?>(null) }
    var setupRelayHost by rememberSaveable {
        mutableStateOf(AppPreferences(context).relayHost)
    }
    var relayAutoSelectionChecked by rememberSaveable { mutableStateOf(false) }
    var relayAutoSelectedByLatency by rememberSaveable { mutableStateOf(false) }
    var relaySelectionChangedManually by rememberSaveable { mutableStateOf(false) }
    var isFindingBestRelay by rememberSaveable { mutableStateOf(false) }
    var relayRecommendation by rememberSaveable { mutableStateOf<String?>(null) }
    var setupShowVersionDialog by remember { mutableStateOf(false) }
    var setupFormError by rememberSaveable { mutableStateOf("") }
    var versionSelectionError by rememberSaveable { mutableStateOf(false) }
    var versionShakeTick by rememberSaveable { mutableIntStateOf(0) }
    var notificationsPermissionGranted by remember { mutableStateOf(isNotificationPermissionGranted(context)) }
    var permissionStepError by rememberSaveable { mutableStateOf("") }
    var permissionWarningTick by rememberSaveable { mutableIntStateOf(0) }
    var signedInAccountEmail by rememberSaveable { mutableStateOf(AccountManager.currentDriveAccount(context)?.email.orEmpty()) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsPermissionGranted = granted || isNotificationPermissionGranted(context)
        if (permissionStepError.isNotBlank() && notificationsPermissionGranted) {
            permissionStepError = ""
        }
    }
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        AccountManager.completeGoogleSignIn(context, result.data) { account, user, _ ->
            signedInAccountEmail = account?.email ?: user?.email.orEmpty()
        }
    }

    val relayRegions by RemoteConfigManager.relayRegions.collectAsState(initial = RelayServers.defaultRegions())

    LaunchedEffect(Unit) {
        setupVersion = ""
        setupSeed = AppPreferencesStore.getWorldSeedFlow(context).first()
        notificationsPermissionGranted = isNotificationPermissionGranted(context)
        RemoteConfigManager.initialize(context)
    }

    LaunchedEffect(currentStep, relayRegions) {
        if (currentStep != 5 || relayAutoSelectionChecked || relayRegions.isEmpty()) return@LaunchedEffect
        relayAutoSelectionChecked = true
        relayRecommendation = RelayServers.getDisplayName(setupRelayHost)
        if (preferences.relayAutoSelectedOnce || preferences.relayHostUserOverridden) return@LaunchedEffect

        isFindingBestRelay = true
        val fastest = RelayLatencySelector.pickFastestRelay(relayRegions)
        setupRelayHost = fastest.host
        relayRecommendation = fastest.label
        relayAutoSelectedByLatency = true
        isFindingBestRelay = false
    }

    fun playHaptic(doublePulse: Boolean = false) {
        if (!appFeedbackEnabled) return
        scope.launch {
            playAppHaptic(
                context = context,
                hapticFeedback = hapticFeedback,
                doublePulse = doublePulse
            )
        }
    }

    val progress by animateFloatAsState(
        targetValue = (currentStep + 1) / steps.size.toFloat(),
        animationSpec = PocketMotion.softFloatTween(durationMillis = 420)
    )
    val scrollState = rememberScrollState()

    LaunchedEffect(currentStep) {
        scrollState.scrollTo(0)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = onboardingBackgroundBrush())
    ) {
        val compact = maxHeight < 760.dp || maxWidth < 392.dp
        val outerPadding = if (compact) 14.dp else 18.dp
        val verticalPadding = if (compact) 8.dp else 12.dp
        val contentSpacing = if (compact) 10.dp else 14.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = outerPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(contentSpacing)) {
                TopHeader(
                    progress = progress,
                    step = currentStep + 1,
                    total = steps.size,
                    currentMobTheme = currentMobTheme,
                    onMobThemeChange = onMobThemeChange
                )

                OnboardingPhoneFrame(
                    step = currentStep,
                    totalSteps = steps.size,
                ) {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            if (targetState > initialState) {
                                slideInHorizontally(animationSpec = PocketMotion.softIntOffsetTween(durationMillis = 520)) { it / 12 } +
                                    fadeIn(PocketMotion.softFloatTween(durationMillis = 440)) +
                                    scaleIn(initialScale = 0.985f, animationSpec = PocketMotion.softFloatTween(durationMillis = 500)) togetherWith
                                    slideOutHorizontally(animationSpec = PocketMotion.softIntOffsetTween(durationMillis = 440)) { -it / 14 } +
                                    fadeOut(PocketMotion.softFloatTween(durationMillis = 320)) +
                                    scaleOut(targetScale = 1.005f, animationSpec = PocketMotion.softFloatTween(durationMillis = 380))
                            } else {
                                slideInHorizontally(animationSpec = PocketMotion.softIntOffsetTween(durationMillis = 520)) { -it / 12 } +
                                    fadeIn(PocketMotion.softFloatTween(durationMillis = 440)) +
                                    scaleIn(initialScale = 0.985f, animationSpec = PocketMotion.softFloatTween(durationMillis = 500)) togetherWith
                                    slideOutHorizontally(animationSpec = PocketMotion.softIntOffsetTween(durationMillis = 440)) { it / 14 } +
                                    fadeOut(PocketMotion.softFloatTween(durationMillis = 320)) +
                                    scaleOut(targetScale = 1.005f, animationSpec = PocketMotion.softFloatTween(durationMillis = 380))
                            }
                        },
                        label = "onboarding-page"
                    ) { animatedPage ->
                        when (animatedPage) {
                            0 -> WelcomeScreen(
                                privacyAccepted = privacyAccepted,
                                onPrivacyChange = { privacyAccepted = it },
                                onOpenPrivacy = {
                                    openExternalUrl(context, BuildConfig.PRIVACY_POLICY_URL)
                                },
                                onOpenTerms = {
                                    openExternalUrl(context, BuildConfig.TERMS_OF_USE_URL)
                                }
                            )
                            1 -> HowItWorksScreen()
                            2 -> ImportScreen()
                            3 -> FeaturesScreen()
                            4 -> CrossPlayScreen()
                            5 -> RelayRegionOnboardingScreen(
                                selectedHost = setupRelayHost,
                                regions = relayRegions,
                                isFindingBestRelay = isFindingBestRelay,
                                recommendation = relayRecommendation,
                                onSelectHost = {
                                    setupRelayHost = it
                                    relayRecommendation = RelayServers.getDisplayName(it)
                                    relaySelectionChangedManually = true
                                    relayAutoSelectedByLatency = false
                                }
                            )
                            6 -> PermissionsScreen(
                                    s = s,
                                notificationsPermissionGranted = notificationsPermissionGranted,
                                onAllowNotifications = {
                                    playHaptic()
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        notificationsPermissionGranted = true
                                    }
                                },
                                errorText = permissionStepError,
                                warningTick = permissionWarningTick
                            )
                            7 -> OnboardingGoogleSignInScreen(
                                signedInAccountEmail = signedInAccountEmail,
                                onSignInClick = {
                                    playHaptic()
                                    googleSignInLauncher.launch(AccountManager.googleSignInIntent(context))
                                }
                            )
                            else -> OnboardingSetupScreen(
                                serverName = setupServerName,
                                onServerNameChange = {
                                    setupServerName = it
                                    if (setupFormError.isNotBlank()) setupFormError = ""
                                },
                                worldDescription = setupWorldDescription,
                                onWorldDescriptionChange = {
                                    setupWorldDescription = it
                                    if (setupFormError.isNotBlank()) setupFormError = ""
                                },
                                selectedServerType = setupServerType,
                                selectedVersion = setupVersion,
                                onVersionClick = {
                                    setupShowVersionDialog = true
                                    versionSelectionError = false
                                    playHaptic()
                                },
                                worldSeed = setupSeed,
                                onWorldSeedChange = { setupSeed = it },
                                showVersionError = versionSelectionError,
                                versionShakeTick = versionShakeTick
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = steps[currentStep].footer,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = onboardingTextMuted(),
                    fontSize = 12.sp,
                    fontFamily = Monocraft,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentStep > 0) {
                        OutlineButton(
                            text = s.onboardingButtonBack,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                playHaptic()
                                currentStep--
                            }
                        )
                    }

                    val missingPermissionStep = currentStep == 6 && !notificationsPermissionGranted

                    PrimaryButton(
                        modifier = Modifier.weight(1.25f),
                        text = if (currentStep == steps.lastIndex) s.onboardingButtonFinish else if (currentStep == 7) s.onboardingButtonSkip else s.onboardingButtonNext,
                        enabled = if (currentStep == 0) privacyAccepted else true,
                        onClick = {
                            if (missingPermissionStep) {
                                permissionStepError = s.onboardingPermissionsError
                                permissionWarningTick++
                                playHaptic(doublePulse = true)
                                return@PrimaryButton
                            }
                            if (currentStep == steps.lastIndex) {
                                if (setupServerName.trim().isBlank()) {
                                    setupFormError = "Server name is required."
                                    playHaptic(doublePulse = true)
                                    return@PrimaryButton
                                }
                                if (setupVersion.trim().isBlank()) {
                                    setupFormError = "Game version is required."
                                    versionSelectionError = true
                                    versionShakeTick++
                                    playHaptic(doublePulse = true)
                                    return@PrimaryButton
                                }
                                scope.launch {
                                    val selectedVersion = setupVersion.trim()
                                    AppPreferences(context).apply {
                                        if (relaySelectionChangedManually) {
                                            setManualRelayHost(setupRelayHost)
                                        } else if (relayAutoSelectedByLatency || !relayAutoSelectedOnce) {
                                            setAutoSelectedRelayHost(setupRelayHost)
                                        } else {
                                            relayHost = setupRelayHost
                                            updateBedrockRelayRegionForHost(setupRelayHost)
                                        }
                                    }
                                    AppPreferencesStore.setRelayHost(context, setupRelayHost)
                                    AppPreferencesStore.setSelectedServerType(context, setupServerType.name)
                                    AppPreferencesStore.setServerVersion(context, selectedVersion)
                                    AppPreferencesStore.setWorldSeed(context, setupSeed.trim())
                                    AppPreferencesStore.setSeedSetupShown(context, true)
                                    AppPreferencesStore.setInitialWorldSetupShown(context, true)
                                    AppPreferencesStore.setPendingAutoDownloadVersion(context, selectedVersion)

                                    runCatching {
                                        val repo = ServerConfigRepository(context.applicationContext)
                                        val current = repo.loadConfig()
                                        repo.saveConfig(
                                            current.copy(
                                                gameVersion = selectedVersion,
                                                serverType = setupServerType,
                                                customJarPath = setupCustomJarPath
                                            )
                                        )
                                    }
                                    preferences.openWorldSetupNextLaunch = false
                                    onComplete()
                                }
                            } else {
                                playHaptic()
                                currentStep++
                            }
                        }
                    )
                }
            }
        }

        if (scrollState.maxValue > 0 && scrollState.canScrollForward) {
            Surface(
                onClick = {
                    scope.launch {
                        scrollState.animateScrollTo((scrollState.value + 280).coerceAtMost(scrollState.maxValue))
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 18.dp)
                    .size(46.dp),
                shape = CircleShape,
                color = PocketColors.Primary,
                border = BorderStroke(1.5.dp, PocketColors.PrimaryBorder),
                shadowElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Scroll down",
                        tint = PocketColors.PrimaryText,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (setupShowVersionDialog) {
            val versionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = {
                    scope.launch {
                        versionSheetState.hide()
                        setupShowVersionDialog = false
                    }
                },
                sheetState = versionSheetState,
                dragHandle = null,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                ServerTypeVersionBottomSheet(
                    onDismissRequest = {
                        scope.launch {
                            versionSheetState.hide()
                            setupShowVersionDialog = false
                        }
                    },
                    onConfirm = { type, version, customJar ->
                        scope.launch {
                            setupServerType = type
                            setupCustomJarPath = customJar
                            setupVersion = if (type.supportsVersionSelect) {
                                version ?: setupVersion
                            } else {
                                setupVersion.ifBlank { "custom" }
                            }
                            if (setupFormError.isNotBlank()) setupFormError = ""
                            versionSelectionError = false
                            versionSheetState.hide()
                            setupShowVersionDialog = false
                        }
                    },
                    currentServerType = setupServerType,
                    currentGameVersion = setupVersion,
                    currentCustomJarPath = setupCustomJarPath
                )
            }
        }
    }
}

private data class OnboardingStep(
    val footer: String
)

private fun onboardingSteps(s: AppStrings): List<OnboardingStep> {
    return listOf(
        OnboardingStep(s.onboardingStepWelcome),
        OnboardingStep(s.onboardingStepHowItWorks),
        OnboardingStep(s.onboardingStepBringYourWorld),
        OnboardingStep(s.onboardingStepFullControl),
        OnboardingStep(s.onboardingStepCrossPlay),
        OnboardingStep(s.onboardingStepPickRegion),
        OnboardingStep(s.onboardingStepPermissions),
        OnboardingStep(s.onboardingStepGoogleSignIn),
        OnboardingStep(s.onboardingStepSetup)
    )
}

@Composable
private fun GoogleLogoIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
        val sizePx = size.width
        val stroke = sizePx * 0.22f
        
        // Draw yellow arc (left)
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 135f,
            sweepAngle = 92f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Butt
            )
        )
        
        // Draw red arc (top)
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 225f,
            sweepAngle = 92f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Butt
            )
        )
        
        // Draw green arc (bottom)
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 92f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Butt
            )
        )
        
        // Draw blue arc (right side and bar)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -45f,
            sweepAngle = 92f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Butt
            )
        )
        
        // Draw blue bar
        drawLine(
            color = Color(0xFF4285F4),
            start = androidx.compose.ui.geometry.Offset(sizePx / 2f, sizePx / 2f),
            end = androidx.compose.ui.geometry.Offset(sizePx - stroke / 2f, sizePx / 2f),
            strokeWidth = stroke
        )
    }
}

@Composable
private fun OnboardingGoogleSignInScreen(
    signedInAccountEmail: String,
    onSignInClick: () -> Unit
) {
    val s = LocalAppStrings.current
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled(scale))
    ) {
        HaloIconBox(
            accent = onboardingAccentPurple(),
            icon = Icons.Filled.AccountCircle
        )

        Text(
            text = s.onboardingGoogleTitle,
            fontSize = 21.sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = s.onboardingGoogleSubtitle,
            fontSize = 13.sp.scaledSp(scale),
            lineHeight = 19.sp.scaledSp(scale),
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            color = onboardingSurfaceColor(),
            border = BorderStroke(
                width = 1.dp,
                color = if (signedInAccountEmail.isBlank()) onboardingBorderColor() else onboardingAccentGreen().copy(alpha = 0.4f)
            ),
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (signedInAccountEmail.isBlank()) {
                                    onboardingAccentPurple().copy(alpha = 0.1f)
                                } else {
                                    onboardingAccentGreen().copy(alpha = 0.1f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (signedInAccountEmail.isBlank()) Icons.Filled.AccountCircle else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (signedInAccountEmail.isBlank()) onboardingAccentPurple() else onboardingAccentGreen(),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (signedInAccountEmail.isBlank()) s.onboardingGoogleStatusTitle else s.onboardingGoogleConnectedTitle,
                            fontSize = 13.sp.scaledSp(scale),
                            fontWeight = FontWeight.Bold,
                            color = onboardingTextPrimary()
                        )
                        Text(
                            text = if (signedInAccountEmail.isBlank()) s.onboardingGoogleStatusInactive else s.onboardingGoogleStatusActive,
                            fontSize = 11.sp.scaledSp(scale),
                            color = if (signedInAccountEmail.isBlank()) onboardingTextSecondary() else onboardingAccentGreen(),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = if (signedInAccountEmail.isBlank()) {
                        s.onboardingGoogleStatusDesc
                    } else {
                        s.onboardingGoogleConnectedDesc.format(signedInAccountEmail)
                    },
                    fontSize = 11.sp.scaledSp(scale),
                    lineHeight = 16.sp.scaledSp(scale),
                    color = onboardingTextSecondary()
                )
            }
        }

        DuoButton(
            text = if (signedInAccountEmail.isBlank()) s.onboardingGoogleSignInButton else s.onboardingGoogleSignedInButton,
            onClick = onSignInClick,
            enabled = signedInAccountEmail.isBlank(),
            iconContent = if (signedInAccountEmail.isBlank()) {
                { GoogleLogoIcon() }
            } else {
                null
            },
            variant = DuoButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth(),
            minHeight = 60.dp
        )

        Text(
            text = s.onboardingGoogleSkipNotice,
            fontSize = 11.sp.scaledSp(scale),
            color = onboardingTextMuted(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RelayRegionOnboardingScreen(
    selectedHost: String,
    regions: List<RelayRegion>,
    isFindingBestRelay: Boolean,
    recommendation: String?,
    onSelectHost: (String) -> Unit
) {
    val s = LocalAppStrings.current
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp.scaled(scale))
    ) {
        Text(
            text = s.onboardingRegionTitle,
            fontSize = 24.sp.scaledSp(scale),
            fontWeight = FontWeight.ExtraBold,
            color = onboardingTextPrimary(),
            fontFamily = Monocraft
        )
        Text(
            text = s.onboardingRegionSubtitle,
            fontSize = 13.sp.scaledSp(scale),
            color = onboardingTextSecondary(),
            lineHeight = 18.sp.scaledSp(scale)
        )
        if (isFindingBestRelay) {
            Text(
                text = s.onboardingRegionFinding,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = onboardingAccentPurpleDark()
            )
        } else if (!recommendation.isNullOrBlank()) {
            Text(
                text = s.onboardingRegionRecommended.format(recommendation),
                fontSize = 12.sp,
                color = onboardingTextSecondary()
            )
        }
        RelayServers.ALL.filter { server -> regions.any { it.host == server.host } }.forEach { server ->
            val selected = server.host == selectedHost
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectHost(server.host) },
                shape = RoundedCornerShape(22.dp.scaled(scale)),
                color = if (selected) onboardingAccentPurpleMuted() else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, if (selected) onboardingAccentPurple() else onboardingBorderColor())
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp.scaled(scale), vertical = 14.dp.scaled(scale)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = server.icon, fontSize = 24.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(server.region, fontWeight = FontWeight.ExtraBold, color = onboardingTextPrimary())
                        Text(server.bestFor, fontSize = 11.sp, color = onboardingTextSecondary())
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = onboardingAccentPurpleDark()
                        )
                    }
                }
            }
        }
    }
}

private fun isBackgroundPermissionGranted(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun isNotificationPermissionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun TopHeader(
    progress: Float,
    step: Int,
    total: Int,
    currentMobTheme: MobTheme,
    onMobThemeChange: (MobTheme) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "PocketCraft",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Monocraft,
                    color = onboardingTextDark(),
                    letterSpacing = 0.1.sp
                )
                Text(
                    text = "Your phone becomes the server",
                    fontSize = 12.sp,
                    color = onboardingTextMuted()
                )
            }

            Box {
                var showThemeMenu by remember { mutableStateOf(false) }
                Surface(
                    onClick = { showThemeMenu = true },
                    shape = RoundedCornerShape(12.dp),
                    color = onboardingSurfaceSoftColor(),
                    border = BorderStroke(1.dp, onboardingBorderColor())
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🎨", fontSize = 14.sp)
                        Text(
                            text = currentMobTheme.themeName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = onboardingTextPrimary()
                        )
                    }
                }

                DropdownMenu(
                    expanded = showThemeMenu,
                    onDismissRequest = { showThemeMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    MobTheme.entries.filter { it != MobTheme.CUSTOM }.forEach { theme ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = theme.themeName,
                                    fontWeight = if (theme == currentMobTheme) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            },
                            onClick = {
                                showThemeMenu = false
                                onMobThemeChange(theme)
                            }
                        )
                    }
                }
            }
        }

        Surface(
            color = if (pocketIsDarkTheme()) MaterialTheme.colorScheme.surface.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.72f),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (pocketIsDarkTheme()) onboardingBorderColor().copy(alpha = 0.9f) else Color.White.copy(alpha = 0.9f),
                    RoundedCornerShape(999.dp)
                )
        ) {
            Box(modifier = Modifier.padding(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.08f, 1f))
                        .height(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(onboardingAccentPurpleDark(), onboardingAccentPurple())
                            )
                        )
                )
            }
        }

        Text(
            text = "$step / $total",
            fontSize = 11.sp,
            color = onboardingTextMuted(),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OnboardingPhoneFrame(
    step: Int,
    totalSteps: Int,
    content: @Composable () -> Unit
) {
    val scale = onboardingCompactScale()
    val compact = onboardingIsCompact()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(34.dp.scaled(scale)))
            .background(brush = onboardingPhoneOuterBrush())
            .border(1.5.dp, onboardingBorderColor().copy(alpha = 0.8f), RoundedCornerShape(34.dp.scaled(scale)))
            .padding(horizontal = 14.dp.scaled(scale), vertical = 12.dp.scaled(scale)),
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled(scale))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 54.dp else 70.dp)
                .height(20.dp.scaled(scale))
                .clip(RoundedCornerShape(0.dp, 0.dp, 12.dp.scaled(scale), 12.dp.scaled(scale)))
                .background(if (pocketIsDarkTheme()) MaterialTheme.colorScheme.surface.copy(alpha = 0.92f) else Color(0xFFF6F8F1))
                .border(1.dp, onboardingBorderColor().copy(alpha = 0.85f), RoundedCornerShape(0.dp, 0.dp, 12.dp.scaled(scale), 12.dp.scaled(scale)))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 390.dp else 474.dp)
                .clip(RoundedCornerShape(26.dp.scaled(scale)))
                .background(brush = onboardingPhoneInnerBrush())
                .border(1.dp, onboardingBorderColor().copy(alpha = 0.8f), RoundedCornerShape(26.dp.scaled(scale)))
                .padding(horizontal = 20.dp.scaled(scale), vertical = 16.dp.scaled(scale)),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                content()
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(1.dp))
            }
        }
    }
}

@Composable
private fun StepDots(currentStep: Int, totalSteps: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(if (index == currentStep) 18.dp else 6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (index == currentStep) onboardingAccentPurple() else onboardingBorderColor())
            )
        }
    }
}

@Composable
private fun WelcomeScreen(
    privacyAccepted: Boolean,
    onPrivacyChange: (Boolean) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit
) {
    val s = LocalAppStrings.current
    val scale = onboardingCompactScale()
    val accentGreen  = onboardingAccentGreen()
    val accentGold   = onboardingAccentGold()
    val accentPurple = onboardingAccentPurple()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp.scaled(scale))
    ) {

        // ── Hero icon: concentric glow rings ──────────────────────────────────
        Box(contentAlignment = Alignment.Center) {
            // Outer soft halo
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(accentGreen.copy(alpha = 0.15f), Color.Transparent)
                        )
                    )
            )
            // Mid ring
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(accentGreen.copy(alpha = 0.10f))
            )
            // Icon square
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(accentGreen.copy(alpha = 0.20f))
                    .border(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(accentGreen.copy(alpha = 0.70f), accentGreen.copy(alpha = 0.25f))
                        ),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground_square),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // ── Headline ──────────────────────────────────────────────────────────
        Text(
            text = "Your phone is now\na Minecraft server",
            fontSize = 25.sp.scaledSp(scale),
            lineHeight = 30.sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        // ── Subtitle ──────────────────────────────────────────────────────────
        Text(
            text = "Host Java Edition servers for free.\nNo PC required. Play with anyone.",
            fontSize = 13.sp.scaledSp(scale),
            lineHeight = 19.sp.scaledSp(scale),
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center,
            maxLines = 3
        )

        // ── Feature cards: icon + label ───────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(
                Triple(Icons.Filled.Star,        s.onboardingFreeToHost, accentGold),
                Triple(Icons.Filled.PhoneAndroid, s.onboardingNoPcNeeded, accentGreen),
                Triple(Icons.Filled.Group,        s.onboardingInviteAnyone, accentPurple)
            ).forEach { (icon, label, accent) ->
                Surface(
                    color = onboardingSurfaceSoftColor(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = label,
                            fontSize = 10.sp.scaledSp(scale),
                            fontWeight = FontWeight.Bold,
                            color = onboardingTextPrimary(),
                            textAlign = TextAlign.Center,
                            lineHeight = 13.sp.scaledSp(scale)
                        )
                    }
                }
            }
        }

        // ── Terms & policy ────────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPrivacyChange(!privacyAccepted) },
            shape = RoundedCornerShape(14.dp.scaled(scale)),
            color = if (privacyAccepted) accentGreen.copy(alpha = 0.08f)
                    else onboardingSurfaceSoftColor().copy(alpha = 0.5f),
            border = BorderStroke(
                1.dp,
                if (privacyAccepted) accentGreen.copy(alpha = 0.4f)
                else onboardingBorderColor().copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp.scaled(scale), vertical = 10.dp.scaled(scale)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = privacyAccepted,
                    onCheckedChange = onPrivacyChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = accentGreen,
                        uncheckedColor = onboardingBorderColor()
                    )
                )
                Spacer(modifier = Modifier.width(10.dp.scaled(scale)))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.onboardingAgreeTermsPolicy,
                        fontSize = 12.sp.scaledSp(scale),
                        color = onboardingTextPrimary(),
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = s.onboardingPrivacyPolicy,
                            color = accentPurple,
                            fontSize = 11.sp.scaledSp(scale),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onOpenPrivacy() }
                        )
                        Text(text = "•", color = onboardingTextMuted(), fontSize = 10.sp.scaledSp(scale))
                        Text(
                            text = s.onboardingTermsOfUse,
                            color = accentPurple,
                            fontSize = 11.sp.scaledSp(scale),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onOpenTerms() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HowItWorksScreen() {
    val s = LocalAppStrings.current
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled(scale))
    ) {
        Text(
            text = s.onboardingHowItWorksTitle,
            fontSize = 21.sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft
        )

        FlowDiagram()

        DetailCard(
            accent = onboardingAccentPurple(),
            icon = Icons.Filled.Dns,
            title = s.onboardingStep1Title,
            body = s.onboardingStep1Body
        )
        DetailCard(
            accent = onboardingAccentGold(),
            icon = Icons.Filled.Public,
            title = s.onboardingStep2Title,
            body = s.onboardingStep2Body
        )
        DetailCard(
            accent = onboardingAccentGreen(),
            icon = Icons.Filled.Group,
            title = s.onboardingStep3Title,
            body = s.onboardingStep3Body
        )
    }
}

@Composable
private fun ImportScreen() {
    val s = LocalAppStrings.current
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled(scale))
    ) {
        HaloIconBox(
            accent = onboardingAccentGold(),
            icon = Icons.Filled.Upload
        )

        Text(
            text = s.onboardingImportTitle,
            fontSize = 20.sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = s.onboardingImportSubtitle,
            fontSize = 13.sp.scaledSp(scale),
            lineHeight = 19.sp.scaledSp(scale),
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center,
            maxLines = 3
        )

        FeatureListCard(
            accent = onboardingAccentGold(),
            entries = listOf(
                s.onboardingImportWorldTitle to s.onboardingImportWorldBody,
                s.onboardingImportPluginsTitle to s.onboardingImportPluginsBody,
                s.onboardingImportConfigTitle to s.onboardingImportConfigBody
            )
        )
    }
}

@Composable
private fun FeaturesScreen() {
    val s = LocalAppStrings.current
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp.scaled(scale))
    ) {
        HaloIconBox(
            accent = onboardingAccentGreen(),
            icon = Icons.Filled.Extension
        )

        Text(
            text = s.onboardingFeaturesTitle,
            fontSize = 20.sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = s.onboardingFeaturesSubtitle,
            fontSize = 13.sp.scaledSp(scale),
            lineHeight = 18.sp.scaledSp(scale),
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        Surface(
            color = onboardingAccentGreen().copy(alpha = 0.12f),
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = s.onboardingFeaturesBanner,
                modifier = Modifier.padding(horizontal = 12.dp.scaled(scale), vertical = 6.dp.scaled(scale)),
                color = onboardingAccentPurpleDark(),
                fontSize = 10.sp.scaledSp(scale),
                fontWeight = FontWeight.Bold,
                fontFamily = Monocraft
            )
        }

        FeatureGrid(
            items = listOf(
                FeatureItem(s.hubTabPlugins, s.onboardingFeaturesPluginsBody, Icons.Filled.Extension),
                FeatureItem(s.onboardingFeaturesConsoleTitle, s.onboardingFeaturesConsoleBody, Icons.Filled.Dns),
                FeatureItem(s.players, s.onboardingFeaturesPlayersBody, Icons.Filled.Group),
                FeatureItem(s.onboardingFeaturesConfigTitle, s.onboardingFeaturesConfigBody, Icons.Filled.Settings)
            )
        )
    }
}

@Composable
private fun CrossPlayScreen() {
    val s = LocalAppStrings.current
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled(scale))
    ) {
        HaloIconBox(
            accent = onboardingAccentGreen(),
            icon = Icons.Filled.Public
        )

        Text(
            text = s.onboardingCrossPlayTitle,
            fontSize = 20.sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = s.onboardingCrossPlaySubtitle,
            fontSize = 13.sp.scaledSp(scale),
            lineHeight = 18.sp.scaledSp(scale),
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center,
            maxLines = 3
        )

        DetailCard(
            accent = onboardingAccentGreen(),
            icon = Icons.Filled.PhoneAndroid,
            title = s.onboardingCrossPlayJavaBedrock,
            body = s.onboardingCrossPlayJavaBedrockBody
        )

        DetailCard(
            accent = onboardingAccentPurple(),
            icon = Icons.Filled.Extension,
            title = s.onboardingCrossPlayNoExtraApp,
            body = s.onboardingCrossPlayNoExtraAppBody
        )

        Surface(
            color = onboardingAccentGold().copy(alpha = 0.12f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = s.onboardingCrossPlayExperimental,
                modifier = Modifier.padding(horizontal = 14.dp.scaled(scale), vertical = 12.dp.scaled(scale)),
                color = onboardingTextPrimary(),
                fontSize = 11.sp.scaledSp(scale),
                lineHeight = 16.sp.scaledSp(scale),
                fontWeight = FontWeight.SemiBold,
                maxLines = 5
            )
        }
    }
}

private data class FeatureItem(
    val title: String,
    val body: String,
    val icon: ImageVector
)

@Composable
private fun PermissionsScreen(
    s: AppStrings = LocalAppStrings.current,
    notificationsPermissionGranted: Boolean,
    onAllowNotifications: () -> Unit,
    errorText: String,
    warningTick: Int
) {
    val notificationsShakeOffset = remember { Animatable(0f) }

    fun shouldWarnNotifications(): Boolean = warningTick > 0 && !notificationsPermissionGranted

    LaunchedEffect(warningTick, notificationsPermissionGranted) {
        if (!shouldWarnNotifications()) return@LaunchedEffect
        val keyframes = listOf(0f, -8f, 8f, -6f, 6f, -3f, 3f, 0f)
        keyframes.forEach { x ->
            notificationsShakeOffset.animateTo(x, animationSpec = tween(durationMillis = 32))
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = if (notificationsPermissionGranted) {
                onboardingAccentGreen().copy(alpha = 0.14f)
            } else {
                onboardingAccentGold().copy(alpha = 0.14f)
            },
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = if (notificationsPermissionGranted) s.onboardingPermissionsComplete else s.onboardingPermissionsRequired,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (notificationsPermissionGranted) onboardingAccentGreen() else onboardingAccentPurpleDark(),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Monocraft
            )
        }

        Text(
            text = s.onboardingPermissionsTitle,
            fontSize = 21.sp,
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = s.onboardingPermissionsSubtitle,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center,
            maxLines = 3
        )

        PermissionCard(
            accent = onboardingAccentPurple(),
            icon = Icons.Filled.Notifications,
            title = s.onboardingPermissionsCardTitle,
            body = s.onboardingPermissionsCardBody
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = notificationsShakeOffset.value.dp)
        ) {
            DuoButton(
                text = if (notificationsPermissionGranted) s.onboardingPermissionsButtonEnabled else s.onboardingPermissionsButtonAllow,
                onClick = onAllowNotifications,
                enabled = !notificationsPermissionGranted,
                variant = if (notificationsPermissionGranted) DuoButtonVariant.Secondary else DuoButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 52.dp
            )
        }

        Surface(
            color = when {
                notificationsPermissionGranted -> onboardingAccentGreen().copy(alpha = 0.1f)
                shouldWarnNotifications() -> Color(0xFFFFF1F0)
                else -> onboardingSurfaceSoftColor()
            },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                1.dp,
                when {
                    notificationsPermissionGranted -> onboardingAccentGreen().copy(alpha = 0.35f)
                    shouldWarnNotifications() -> Color(0xFFFFB3AE)
                    else -> onboardingBorderColor().copy(alpha = 0.8f)
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = when {
                    notificationsPermissionGranted -> s.onboardingPermissionsStatusSet
                    shouldWarnNotifications() -> s.onboardingPermissionsStatusWarn
                    else -> s.onboardingPermissionsStatusTap
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                color = if (shouldWarnNotifications()) Color(0xFF9F2D2D) else onboardingTextPrimary(),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        if (errorText.isNotBlank()) {
            Surface(
                color = Color(0xFFFFF1F0),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFFB3AE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    color = Color(0xFF9F2D2D),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun OnboardingSetupScreen(
    serverName: String,
    onServerNameChange: (String) -> Unit,
    worldDescription: String,
    onWorldDescriptionChange: (String) -> Unit,
    selectedServerType: ServerType,
    selectedVersion: String,
    onVersionClick: () -> Unit,
    worldSeed: String,
    onWorldSeedChange: (String) -> Unit,
    showVersionError: Boolean,
    versionShakeTick: Int
) {
    val s = LocalAppStrings.current
    val versionShakeOffset = remember { Animatable(0f) }
    LaunchedEffect(versionShakeTick) {
        if (versionShakeTick == 0) return@LaunchedEffect
        val keyframes = listOf(0f, -7f, 7f, -5f, 5f, -3f, 3f, 0f)
        keyframes.forEach {
            versionShakeOffset.animateTo(it, animationSpec = tween(durationMillis = 32))
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = s.onboardingSetupTitle,
            fontSize = 20.sp,
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft
        )

        OutlinedTextField(
            value = serverName,
            onValueChange = onServerNameChange,
            singleLine = true,
            label = { Text(s.onboardingSetupServerLabel) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Dns,
                    contentDescription = null,
                    tint = onboardingAccentPurpleDark()
                )
            },
            shape = duoTextFieldShape(),
            modifier = Modifier.fillMaxWidth(),
            colors = duoOutlinedTextFieldColors()
        )

        OutlinedTextField(
            value = worldDescription,
            onValueChange = onWorldDescriptionChange,
            label = { Text(s.onboardingSetupWorldDescLabel) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = null,
                    tint = onboardingAccentPurpleDark()
                )
            },
            shape = duoTextFieldShape(),
            modifier = Modifier.fillMaxWidth(),
            colors = duoOutlinedTextFieldColors()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = versionShakeOffset.value.dp)
                .clickable(onClick = onVersionClick)
        ) {
            val hasSelectedVersion = selectedVersion.isNotBlank()
            OutlinedTextField(
                value = if (selectedServerType.supportsVersionSelect) {
                    if (hasSelectedVersion) "${selectedServerType.displayName} $selectedVersion" else s.onboardingSetupVersionPlaceholder
                } else {
                    s.onboardingSetupCustomJar.format(selectedServerType.displayName)
                },
                onValueChange = {},
                singleLine = true,
                readOnly = true,
                enabled = false,
                label = { Text(s.onboardingSetupVersionLabel) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Dns,
                        contentDescription = null,
                        tint = onboardingAccentPurpleDark()
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = if (hasSelectedVersion) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    disabledBorderColor = if (showVersionError) Color(0xFFDB3A34) else onboardingBorderColor(),
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = onboardingAccentPurpleDark()
                ),
                shape = duoTextFieldShape(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (showVersionError) {
            Surface(
                color = Color(0xFFFFF1F0),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFFB3AE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = s.onboardingSetupVersionError,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color(0xFF9F2D2D),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        OutlinedTextField(
            value = worldSeed,
            onValueChange = onWorldSeedChange,
            singleLine = true,
            label = { Text(s.onboardingSetupSeedLabel) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Forest,
                    contentDescription = null,
                    tint = onboardingAccentPurpleDark()
                )
            },
            shape = duoTextFieldShape(),
            modifier = Modifier.fillMaxWidth(),
            colors = duoOutlinedTextFieldColors()
        )

        Surface(
            color = onboardingAccentGreen().copy(alpha = 0.12f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = onboardingAccentGreen(),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = s.onboardingSetupBackupNotice,
                    color = onboardingTextPrimary(),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Monocraft
                )
            }
        }
    }
}

@Composable
private fun FloatingToolArt(modifier: Modifier, mirrored: Boolean = false, secondary: Boolean = false) {
    val bob by animateFloatAsState(
        targetValue = if (secondary) 1f else 0f,
        animationSpec = tween(durationMillis = 2400)
    )
    val alpha = if (secondary) 0.45f else 0.72f
    val tool = if (mirrored) R.drawable.ic_pickaxe_pixel else R.drawable.ic_diamond_pickaxe

    Box(
        modifier = modifier
            .alpha(alpha)
            .padding(top = (bob * 10).dp)
    ) {
        Surface(
            modifier = Modifier.card3d(elevation = 6.dp, cornerRadius = 24.dp),
            color = if (pocketIsDarkTheme()) MaterialTheme.colorScheme.surface.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.74f),
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 0.dp
        ) {
            Image(
                painter = painterResource(id = tool),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun FlowDiagram() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlowNode(label = "Your\nphone", icon = Icons.Filled.PhoneAndroid)
        FlowArrow()
        FlowNode(label = "PocketCraft\nrelay", iconRes = R.drawable.ic_fg)
        FlowArrow()
        FlowNode(label = "Friends\nconnect", icon = Icons.Filled.Group)
    }
}

@Composable
private fun FlowNode(label: String, emoji: String? = null, icon: ImageVector? = null, iconRes: Int? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Surface(
            color = onboardingSurfaceSoftColor(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .size(46.dp)
                .border(1.dp, onboardingBorderColor(), RoundedCornerShape(14.dp))
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (emoji != null) {
                    Text(text = emoji, fontSize = 18.sp)
                } else if (iconRes != null) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = onboardingAccentPurpleDark(),
                        modifier = Modifier.size(22.dp)
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = onboardingAccentPurpleDark(),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        Text(
            text = label,
            textAlign = TextAlign.Center,
            color = onboardingTextSecondary(),
            fontSize = 9.sp,
            lineHeight = 11.sp
        )
    }
}

@Composable
private fun FlowArrow() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = null,
        tint = onboardingAccentPurpleDark(),
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .size(16.dp)
    )
}

@Composable
private fun HaloIconBox(accent: Color, icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(onboardingSurfaceSoftColor())
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun HaloIconBox(accent: Color, drawableRes: Int) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(onboardingSurfaceSoftColor())
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun FeaturePillRow(items: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { label ->
            Surface(
                color = onboardingSurfaceSoftColor(),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    textAlign = TextAlign.Center,
                    color = onboardingTextPrimary(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ScreenCard(accent: Color, title: String, subtitle: String) {
    val scale = onboardingCompactScale()
    Surface(
        color = onboardingSurfaceColor(),
        shape = RoundedCornerShape(18.dp.scaled(scale)),
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .card3d(elevation = 4.dp.scaled(scale), cornerRadius = 18.dp.scaled(scale))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp.scaled(scale)),
            verticalArrangement = Arrangement.spacedBy(6.dp.scaled(scale))
        ) {
            Text(text = title, color = onboardingTextPrimary(), fontSize = 14.sp.scaledSp(scale), fontWeight = FontWeight.Bold, maxLines = 2)
            Text(text = subtitle, color = onboardingTextSecondary(), fontSize = 12.sp.scaledSp(scale), lineHeight = 17.sp.scaledSp(scale), maxLines = 3)
            Box(
                modifier = Modifier
                    .height(4.dp.scaled(scale))
                    .fillMaxWidth(0.38f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent)
            )
        }
    }
}

@Composable
private fun DetailCard(accent: Color, title: String, body: String, icon: ImageVector? = null) {
    val scale = onboardingCompactScale()
    Surface(
        color = onboardingSurfaceColor(),
        shape = RoundedCornerShape(16.dp.scaled(scale)),
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .card3d(elevation = 4.dp.scaled(scale), cornerRadius = 16.dp.scaled(scale))
    ) {
        Row(
            modifier = Modifier.padding(14.dp.scaled(scale)),
            horizontalArrangement = Arrangement.spacedBy(12.dp.scaled(scale)),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp.scaled(scale))
                    .clip(RoundedCornerShape(10.dp.scaled(scale)))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp.scaled(scale))
                    )
                } else {
                    Text(text = "•", color = accent, fontSize = 20.sp.scaledSp(scale), fontWeight = FontWeight.Black)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp.scaled(scale))) {
                Text(text = title, color = onboardingTextPrimary(), fontSize = 13.sp.scaledSp(scale), fontWeight = FontWeight.Bold, maxLines = 2)
                Text(text = body, color = onboardingTextSecondary(), fontSize = 11.sp.scaledSp(scale), lineHeight = 16.sp.scaledSp(scale), maxLines = 3)
            }
        }
    }
}

@Composable
private fun FeatureListCard(accent: Color, entries: List<Pair<String, String>>) {
    val scale = onboardingCompactScale()
    Surface(
        color = onboardingSurfaceColor(),
        shape = RoundedCornerShape(18.dp.scaled(scale)),
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .card3d(elevation = 4.dp.scaled(scale), cornerRadius = 18.dp.scaled(scale))
    ) {
        Column(
            modifier = Modifier.padding(14.dp.scaled(scale)),
            verticalArrangement = Arrangement.spacedBy(12.dp.scaled(scale))
        ) {
            entries.forEach { (title, body) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp.scaled(scale)), modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp.scaled(scale)), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .size(10.dp.scaled(scale))
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Text(text = title, color = onboardingTextPrimary(), fontSize = 12.sp.scaledSp(scale), fontWeight = FontWeight.Bold)
                    }
                    Text(text = body, color = onboardingTextSecondary(), fontSize = 11.sp.scaledSp(scale), lineHeight = 16.sp.scaledSp(scale))
                }
            }
        }
    }
}

@Composable
private fun FeatureGrid(items: List<FeatureItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { item ->
                    Surface(
                        color = onboardingSurfaceColor(),
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .card3d(elevation = 4.dp, cornerRadius = 18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(onboardingAccentGreen().copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = onboardingAccentGreen(),
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            Text(
                                text = item.title,
                                color = onboardingTextPrimary(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = item.body,
                                color = onboardingTextSecondary(),
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(accent: Color, icon: ImageVector, title: String, body: String) {
    Surface(
        color = onboardingSurfaceColor(),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .card3d(elevation = 4.dp, cornerRadius = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, color = onboardingTextPrimary(), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(text = body, color = onboardingTextSecondary(), fontSize = 11.sp, lineHeight = 16.sp, maxLines = 3)
            }
        }
    }
}

@Composable
private fun StatsRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard(modifier = Modifier.weight(1f), value = "Free", label = "to host", accent = onboardingAccentPurple())
        StatCard(modifier = Modifier.weight(1f), value = "24/7", label = "while app runs", accent = onboardingAccentGreen())
        StatCard(modifier = Modifier.weight(1f), value = "Sync", label = "backup ready", accent = onboardingAccentGold())
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, value: String, label: String, accent: Color) {
    Surface(
        color = onboardingAccentPurpleMuted(),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 0.dp,
        modifier = modifier.card3d(elevation = 4.dp, cornerRadius = 14.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = value, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(text = label, color = onboardingTextSecondary(), fontSize = 10.sp, textAlign = TextAlign.Center, lineHeight = 12.sp, maxLines = 2)
        }
    }
}

@Composable
private fun PrimaryButton(modifier: Modifier = Modifier, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    DuoButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        variant = DuoButtonVariant.Primary,
        minHeight = 56.dp,
        modifier = modifier
    )
}

@Composable
private fun OutlineButton(modifier: Modifier = Modifier, text: String, onClick: () -> Unit) {
    DuoButton(
        text = text,
        onClick = onClick,
        variant = DuoButtonVariant.Secondary,
        minHeight = 56.dp,
        modifier = modifier
    )
}

@Composable
private fun SkipButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    DuoButton(
        text = "Skip",
        onClick = onClick,
        variant = DuoButtonVariant.Secondary,
        minHeight = 56.dp,
        modifier = modifier
    )
}

private fun openExternalUrl(context: android.content.Context, url: String): Boolean {
    if (url.isBlank()) return false
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
