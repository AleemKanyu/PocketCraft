package com.pockethost.app.ui.onboarding

import com.pockethost.app.BuildConfig

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pockethost.app.MainActivity
import com.pockethost.app.config.RelayLatencySelector
import com.pockethost.app.config.RemoteConfigManager
import com.pockethost.app.config.RelayServers
import com.pockethost.app.data.model.RelayRegion
import com.pockethost.app.data.preferences.AppPreferences
import com.pockethost.app.data.preferences.AppPreferencesStore
import com.pockethost.app.data.model.ServerType
import com.pockethost.app.data.repository.ServerConfigRepository
import com.pockethost.app.integrations.AccountManager
import com.pockethost.app.R
import com.pockethost.app.ui.screens.ServerTypeVersionBottomSheet
import com.pockethost.app.ui.components.DuoButton
import com.pockethost.app.ui.components.DuoButtonVariant
import com.pockethost.app.ui.components.duoOutlinedTextFieldColors
import com.pockethost.app.ui.components.duoTextFieldShape
import com.pockethost.app.ui.theme.Monocraft
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.PocketMotion
import com.pockethost.app.ui.theme.PocketHostTheme
import com.pockethost.app.ui.theme.card3d
import com.pockethost.app.ui.theme.pill3d
import com.pockethost.app.ui.theme.pocketDecoratedBackground
import com.pockethost.app.ui.theme.pocketIsDarkTheme
import com.pockethost.app.ui.util.playAppHaptic
import com.pockethost.app.ui.util.ThemePreferenceStore
import com.pockethost.app.ui.util.MobTheme
import com.pockethost.app.util.AppStrings
import com.pockethost.app.util.LocalAppStrings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Onboarding design tokens
//
// The tour is the first thing a new user sees, so it has to read as the same
// product as the rest of the app: flat theme-coloured surfaces, chunky 3D
// borders with a heavier bottom edge, Monocraft for anything that acts as a
// label. Every colour below resolves through PocketColors / MaterialTheme so
// the tour follows the mob theme and light/dark mode the user picks from the
// header — there are deliberately no screen-local hex literals.
// ─────────────────────────────────────────────────────────────────────────────

/** Corner radii, kept to three steps so the whole flow lines up. */
private val OnboardingCardCorner = 18.dp
private val OnboardingTileCorner = 14.dp
private val OnboardingChipCorner = 12.dp

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

// ── Accents ──────────────────────────────────────────────────────────────────
// Three roles only: the theme's brand colour, a positive/confirmation colour and
// an attention colour. Anything that needs a fourth is reusing one of these.

@Composable
private fun onboardingAccent(): Color = PocketColors.Primary

/** Readable-on-tint variant of the brand colour, for text and small icons. */
@Composable
private fun onboardingAccentStrong(): Color =
    if (pocketIsDarkTheme()) PocketColors.TextDark else PocketColors.PrimaryBorder

@Composable
private fun onboardingSuccess(): Color = PocketColors.Online

@Composable
private fun onboardingWarn(): Color = PocketColors.Warning

// ── Surfaces ─────────────────────────────────────────────────────────────────

@Composable
private fun onboardingSurface(): Color =
    if (pocketIsDarkTheme()) PocketColors.SurfaceCardDark else PocketColors.SurfaceCard

@Composable
private fun onboardingSurfaceSoft(): Color =
    if (pocketIsDarkTheme()) PocketColors.SurfaceVarDark else PocketColors.SurfaceHover

@Composable
private fun onboardingBorder(): Color =
    if (pocketIsDarkTheme()) PocketColors.BorderDark else PocketColors.CardBorder

@Composable
private fun onboardingBorderDepth(): Color =
    if (pocketIsDarkTheme()) PocketColors.CardBorderBottomDark else PocketColors.CardBorderBottom

// ── Error / warning banners ──────────────────────────────────────────────────

@Composable
private fun onboardingErrorSurface(): Color = if (pocketIsDarkTheme()) {
    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
} else {
    MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
}

@Composable
private fun onboardingErrorBorder(): Color = MaterialTheme.colorScheme.error.copy(alpha = 0.55f)

@Composable
private fun onboardingErrorText(): Color = MaterialTheme.colorScheme.error

// ── Text ─────────────────────────────────────────────────────────────────────

@Composable
private fun onboardingTextPrimary(): Color = PocketColors.TextPrimary

@Composable
private fun onboardingTextSecondary(): Color = PocketColors.TextSecondary

@Composable
private fun onboardingTextMuted(): Color = PocketColors.TextMuted

// ── Shared building blocks ───────────────────────────────────────────────────

/**
 * The app's standard raised card: flat surface, 1.5dp border and a thicker
 * bottom edge. Used for every panel in the tour so nothing floats on its own
 * drop shadow the way the old gradient cards did.
 */
@Composable
private fun OnboardingCard(
    modifier: Modifier = Modifier,
    corner: Dp = OnboardingCardCorner,
    color: Color = onboardingSurface(),
    borderColor: Color = Color.Unspecified,
    depthColor: Color = Color.Unspecified,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(color)
            .card3d(
                elevation = 4.dp,
                cornerRadius = corner,
                borderColor = borderColor,
                depthColor = depthColor
            )
    ) {
        content()
    }
}

/**
 * Small Monocraft label on a tinted pill — the tour's section markers
 * ("STEP 3 OF 8", "PERMISSION REQUIRED", …).
 */
@Composable
private fun OnboardingEyebrow(
    text: String,
    accent: Color = onboardingAccent(),
    modifier: Modifier = Modifier
) {
    val scale = onboardingCompactScale()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = onboardingAccentStrong(),
            fontSize = 9.5.sp.scaledSp(scale),
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Square icon tile with the same raised treatment as the cards. Replaces the
 * old concentric-glow halo, which was the only place in the app using soft
 * radial gradients.
 */
@Composable
private fun OnboardingIconTile(
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    corner: Dp = OnboardingTileCorner,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(accent.copy(alpha = 0.16f))
            .card3d(
                elevation = 4.dp,
                cornerRadius = corner,
                borderColor = accent.copy(alpha = 0.55f),
                depthColor = accent.copy(alpha = 0.85f)
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Title block shared by every step: optional eyebrow, headline, supporting line. */
@Composable
private fun OnboardingHeading(
    title: String,
    subtitle: String? = null,
    eyebrow: String? = null,
    eyebrowAccent: Color = onboardingAccent(),
    centered: Boolean = true,
    titleSize: TextUnit = 21.sp
) {
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(7.dp.scaled(scale))
    ) {
        if (eyebrow != null) {
            OnboardingEyebrow(text = eyebrow, accent = eyebrowAccent)
        }
        Text(
            text = title,
            fontSize = titleSize.scaledSp(scale),
            lineHeight = (titleSize.value * 1.22f).sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 13.sp.scaledSp(scale),
                lineHeight = 19.sp.scaledSp(scale),
                color = onboardingTextSecondary(),
                textAlign = if (centered) TextAlign.Center else TextAlign.Start
            )
        }
    }
}

@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.pockethost.app.util.LocaleUtils.wrapContext(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfig: android.content.res.Configuration?) {
        overrideConfig?.let { cfg ->
            com.pockethost.app.util.LocaleUtils.getSavedLocale(baseContext)?.let { locale ->
                com.pockethost.app.util.LocaleUtils.applyToConfig(cfg, locale)
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
            PocketHostTheme(darkTheme = darkTheme, mobTheme = mobTheme) {
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
                            MobTheme.SIMPLE_DARK -> com.pockethost.app.ui.util.ThemePreference.DARK
                            MobTheme.SIMPLE_WHITE -> com.pockethost.app.ui.util.ThemePreference.LIGHT
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
        try {
            preferences.onboardingCompleted = true
            preferences.openWorldSetupNextLaunch = false
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            android.util.Log.e("OnboardingActivity", "Failed to complete onboarding safely", e)
            try {
                startActivity(Intent(this, MainActivity::class.java))
            } catch (_: Exception) {}
            finish()
        }
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
    var setupServerName by rememberSaveable { mutableStateOf("PocketHost Server") }
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
    var versionShakeConsumed by rememberSaveable { mutableStateOf(false) }
    var notificationsPermissionGranted by remember { mutableStateOf(isNotificationPermissionGranted(context)) }
    var permissionStepError by rememberSaveable { mutableStateOf("") }
    var permissionWarningTick by rememberSaveable { mutableIntStateOf(0) }
    var permissionShakeConsumed by rememberSaveable { mutableStateOf(false) }
    var signedInAccountEmail by rememberSaveable { mutableStateOf(AccountManager.currentDriveAccount(context)?.email.orEmpty()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsPermissionGranted = isNotificationPermissionGranted(context)
                if (notificationsPermissionGranted && permissionStepError.isNotBlank()) {
                    permissionStepError = ""
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsPermissionGranted = granted || isNotificationPermissionGranted(context)
        if (permissionStepError.isNotBlank() && notificationsPermissionGranted) {
            permissionStepError = ""
        }
        if (!notificationsPermissionGranted) {
            // Only bounce out to system Settings once the OS itself says it won't show the
            // in-app request again (permanently denied / "Don't ask again"). On an ordinary
            // first "Deny", shouldShowRequestPermissionRationale is still true (or the
            // permission hasn't been asked before at all) — let the existing in-app error
            // banner handle it so the user can just retry the in-app Allow button instead of
            // being yanked out of onboarding.
            val activity = context.findActivity()
            val permanentlyDenied = activity != null &&
                !activity.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)
            if (permanentlyDenied) {
                openAppNotificationSettings(context)
            }
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
            .pocketDecoratedBackground()
    ) {
        val compact = maxHeight < 760.dp || maxWidth < 392.dp
        val outerPadding = if (compact) 14.dp else 18.dp
        val verticalPadding = if (compact) 8.dp else 12.dp
        val contentSpacing = if (compact) 10.dp else 14.dp

        // Fixed header, a stage card that fills whatever is left, and a pinned
        // Back/Next bar. Scrolling happens inside the card, so a short step
        // never leaves a hole between the card and the buttons and the primary
        // action is always reachable.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = outerPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing)
        ) {
            TopHeader(
                progress = progress,
                step = currentStep + 1,
                total = steps.size,
                stepLabel = steps[currentStep].label,
                currentMobTheme = currentMobTheme,
                onMobThemeChange = onMobThemeChange,
                // Skipping the tour must still land on server setup — leaving onboarding
                // entirely drops the user into the app with no server configured.
                onSkipAll = { currentStep = steps.lastIndex },
                // The terms and privacy policy live on the first step, so skipping past them
                // would mean never accepting them.
                skipEnabled = privacyAccepted,
                // Tapping the dimmed button takes the user to the step holding the checkbox
                // rather than doing nothing.
                onSkipBlocked = { currentStep = 0 }
            )

            OnboardingStage(
                modifier = Modifier.weight(1f),
                scrollState = scrollState
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
                                    if (!isNotificationPermissionGranted(context)) {
                                        try {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } catch (_: Exception) {
                                            openAppNotificationSettings(context)
                                        }
                                    } else {
                                        notificationsPermissionGranted = true
                                    }
                                } else {
                                    notificationsPermissionGranted = true
                                }
                            },
                            errorText = permissionStepError,
                            warningTick = permissionWarningTick,
                            shakeConsumed = permissionShakeConsumed,
                            onShakeConsumed = { permissionShakeConsumed = true }
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
                            versionShakeTick = versionShakeTick,
                            shakeConsumed = versionShakeConsumed,
                            onShakeConsumed = { versionShakeConsumed = true }
                        )
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                            if (missingPermissionStep && permissionWarningTick == 0) {
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
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 84.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PocketColors.Primary)
                    .pill3d(
                        elevation = 4.dp,
                        borderColor = PocketColors.PrimaryBorder,
                        depthColor = PocketColors.PrimaryBorderBottom
                    )
                    .clickable {
                        scope.launch {
                            scrollState.animateScrollTo((scrollState.value + 280).coerceAtMost(scrollState.maxValue))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Scroll down",
                    tint = PocketColors.PrimaryText,
                    modifier = Modifier.size(22.dp)
                )
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
    /** Short all-caps name of the step, shown next to the progress bar. */
    val label: String
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
    val connected = signedInAccountEmail.isNotBlank()
    val statusAccent = if (connected) onboardingSuccess() else onboardingAccent()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(13.dp.scaled(scale))
    ) {
        OnboardingIconTile(accent = statusAccent, size = 64.dp.scaled(scale)) {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                tint = statusAccent,
                modifier = Modifier.size(28.dp.scaled(scale))
            )
        }

        OnboardingHeading(
            title = s.onboardingGoogleTitle,
            subtitle = s.onboardingGoogleSubtitle
        )

        OnboardingCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (connected) onboardingSuccess().copy(alpha = 0.55f) else Color.Unspecified,
            depthColor = if (connected) onboardingSuccess().copy(alpha = 0.85f) else Color.Unspecified
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp.scaled(scale)),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(statusAccent.copy(alpha = 0.18f))
                            .border(1.dp, statusAccent.copy(alpha = 0.40f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (connected) Icons.Filled.CheckCircle else Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = statusAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (connected) s.onboardingGoogleConnectedTitle else s.onboardingGoogleStatusTitle,
                            fontSize = 13.sp.scaledSp(scale),
                            fontWeight = FontWeight.Bold,
                            color = onboardingTextPrimary()
                        )
                        Text(
                            text = if (connected) s.onboardingGoogleStatusActive else s.onboardingGoogleStatusInactive,
                            fontSize = 11.sp.scaledSp(scale),
                            color = if (connected) onboardingSuccess() else onboardingTextSecondary(),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = if (connected) {
                        s.onboardingGoogleConnectedDesc.format(signedInAccountEmail)
                    } else {
                        s.onboardingGoogleStatusDesc
                    },
                    fontSize = 11.sp.scaledSp(scale),
                    lineHeight = 16.sp.scaledSp(scale),
                    color = onboardingTextSecondary()
                )
            }
        }

        DuoButton(
            text = if (connected) s.onboardingGoogleSignedInButton else s.onboardingGoogleSignInButton,
            onClick = onSignInClick,
            enabled = !connected,
            iconContent = if (connected) null else ({ GoogleLogoIcon() }),
            variant = DuoButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth(),
            minHeight = 56.dp
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
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled(scale))
    ) {
        OnboardingHeading(
            title = s.onboardingRegionTitle,
            subtitle = s.onboardingRegionSubtitle,
            eyebrow = "CONNECTION",
            centered = false,
            titleSize = 21.sp
        )

        // Latency probe result. Shown as its own strip so the list below never
        // shifts around while the probe is still running.
        if (isFindingBestRelay) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = onboardingAccent()
                )
                Text(
                    text = s.onboardingRegionFinding,
                    fontSize = 12.sp.scaledSp(scale),
                    fontWeight = FontWeight.Bold,
                    color = onboardingAccentStrong()
                )
            }
        } else if (!recommendation.isNullOrBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Wifi,
                    contentDescription = null,
                    tint = onboardingSuccess(),
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = s.onboardingRegionRecommended.format(recommendation),
                    fontSize = 12.sp.scaledSp(scale),
                    color = onboardingTextSecondary()
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp.scaled(scale))) {
            RelayServers.ALL.filter { server -> regions.any { it.host == server.host } }.forEach { server ->
                val selected = server.host == selectedHost
                OnboardingCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectHost(server.host) },
                    corner = OnboardingTileCorner,
                    color = if (selected) onboardingAccent().copy(alpha = 0.12f) else onboardingSurface(),
                    borderColor = if (selected) onboardingAccent() else onboardingBorder(),
                    depthColor = if (selected) onboardingAccent().copy(alpha = 0.9f) else onboardingBorderDepth()
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 14.dp.scaled(scale),
                            vertical = 12.dp.scaled(scale)
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = server.icon, fontSize = 22.sp)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = server.region,
                                fontSize = 13.sp.scaledSp(scale),
                                fontWeight = FontWeight.ExtraBold,
                                color = onboardingTextPrimary()
                            )
                            Text(
                                text = server.bestFor,
                                fontSize = 11.sp.scaledSp(scale),
                                color = onboardingTextSecondary()
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = onboardingAccent(),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


private fun isNotificationPermissionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun openAppNotificationSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}

@Composable
private fun TopHeader(
    progress: Float,
    step: Int,
    total: Int,
    stepLabel: String,
    currentMobTheme: MobTheme,
    onMobThemeChange: (MobTheme) -> Unit,
    onSkipAll: () -> Unit,
    skipEnabled: Boolean = true,
    onSkipBlocked: () -> Unit = {}
) {
    val compact = onboardingIsCompact()
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                OnboardingIconTile(
                    accent = onboardingAccent(),
                    size = if (compact) 38.dp else 42.dp,
                    corner = OnboardingChipCorner
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo_light),
                        contentDescription = null,
                        modifier = Modifier.size(if (compact) 24.dp else 26.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Column {
                    Text(
                        text = "PocketHost",
                        fontSize = if (compact) 17.sp else 19.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Monocraft,
                        color = onboardingTextPrimary(),
                        letterSpacing = 0.2.sp
                    )
                    Text(
                        text = "Your phone becomes the server",
                        fontSize = 10.5.sp,
                        color = onboardingTextMuted(),
                        maxLines = 1
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OnboardingThemeButton(
                    currentMobTheme = currentMobTheme,
                    onMobThemeChange = onMobThemeChange
                )
                OnboardingHeaderChip(
                    text = "Skip",
                    enabled = skipEnabled,
                    onClick = { if (skipEnabled) onSkipAll() else onSkipBlocked() }
                )
            }
        }

        StepProgressBar(progress = progress, step = step, total = total)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STEP $step OF $total",
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
                color = onboardingAccentStrong(),
                fontFamily = Monocraft,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = stepLabel.uppercase(),
                fontSize = 10.sp,
                letterSpacing = 0.6.sp,
                color = onboardingTextMuted(),
                fontFamily = Monocraft,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

/**
 * Segmented progress — one chunk per step, filled chunks carrying the theme's
 * primary colour. A segmented bar reads as "8 short screens" at a glance, where
 * the old continuous gradient bar gave no sense of how much was left.
 */
@Composable
private fun StepProgressBar(progress: Float, step: Int, total: Int) {
    val trackHeight = if (onboardingIsCompact()) 9.dp else 11.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(total) { index ->
            // The current segment fills proportionally so the bar still animates
            // between steps rather than snapping a whole chunk at a time.
            val fill = (progress * total - index).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(999.dp))
                    .background(onboardingSurfaceSoft())
                    .border(1.dp, onboardingBorder(), RoundedCornerShape(999.dp))
            ) {
                if (fill > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fill)
                            .height(trackHeight)
                            .clip(RoundedCornerShape(999.dp))
                            .background(onboardingAccent())
                    )
                }
            }
        }
    }
}

/** Square icon button matching the app's header controls. */
@Composable
private fun OnboardingThemeButton(
    currentMobTheme: MobTheme,
    onMobThemeChange: (MobTheme) -> Unit
) {
    var showThemeMenu by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(OnboardingChipCorner))
                .background(onboardingSurface())
                .card3d(
                    elevation = 4.dp,
                    cornerRadius = OnboardingChipCorner,
                    borderColor = onboardingBorder(),
                    depthColor = onboardingBorderDepth()
                )
                .clickable { showThemeMenu = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = "Change theme",
                modifier = Modifier.size(18.dp),
                tint = onboardingAccentStrong()
            )
        }

        DropdownMenu(
            expanded = showThemeMenu,
            onDismissRequest = { showThemeMenu = false },
            modifier = Modifier.background(onboardingSurface())
        ) {
            MobTheme.entries.filter { it != MobTheme.CUSTOM }.forEach { theme ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = theme.themeName,
                            fontWeight = if (theme == currentMobTheme) FontWeight.ExtraBold else FontWeight.Medium,
                            color = onboardingTextPrimary(),
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

@Composable
private fun OnboardingHeaderChip(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(onboardingSurface())
            .pill3d(
                elevation = 4.dp,
                borderColor = onboardingBorder().copy(alpha = contentAlpha),
                depthColor = onboardingBorderDepth().copy(alpha = contentAlpha)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontFamily = Monocraft,
            fontWeight = FontWeight.Bold,
            color = onboardingTextPrimary().copy(alpha = contentAlpha)
        )
    }
}

/**
 * The panel every step is drawn into. Previously this was a fake phone mockup
 * complete with a notch, which put a device bezel around content the user was
 * already reading on a device. It is now simply the app's own raised card, so
 * the tour looks like the screens it is introducing.
 */
@Composable
private fun OnboardingStage(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    content: @Composable () -> Unit
) {
    val scale = onboardingCompactScale()
    OnboardingCard(
        modifier = modifier.fillMaxWidth(),
        corner = 24.dp.scaled(scale)
    ) {
        // fillMaxSize inside a vertical scroll sets the minimum height to the
        // card, so short steps centre themselves and long ones scroll.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = 18.dp.scaled(scale),
                    vertical = 20.dp.scaled(scale)
                ),
            verticalArrangement = Arrangement.Center
        ) {
            content()
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
    val accent = onboardingAccent()
    val success = onboardingSuccess()
    val warn = onboardingWarn()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp.scaled(scale))
    ) {
        OnboardingIconTile(
            accent = accent,
            size = 72.dp.scaled(scale),
            corner = 20.dp.scaled(scale)
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo_light),
                contentDescription = null,
                modifier = Modifier.size(42.dp.scaled(scale)),
                contentScale = ContentScale.Fit
            )
        }

        OnboardingHeading(
            title = "Your phone is now a\ndedicated game server",
            subtitle = "Host Java Edition servers for free.\nNo PC required. Play with anyone.",
            titleSize = 19.sp
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(
                Triple(Icons.Filled.Star, s.onboardingFreeToHost, warn),
                Triple(Icons.Filled.PhoneAndroid, s.onboardingNoPcNeeded, accent),
                Triple(Icons.Filled.Group, s.onboardingInviteAnyone, success)
            ).forEach { (icon, label, tint) ->
                OnboardingCard(
                    modifier = Modifier.weight(1f),
                    corner = OnboardingTileCorner,
                    color = onboardingSurfaceSoft()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp.scaled(scale), horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(tint.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(17.dp)
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

        // Terms gate. Tapping anywhere on the card toggles it; the two links open
        // the documents without toggling.
        OnboardingCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPrivacyChange(!privacyAccepted) },
            corner = OnboardingTileCorner,
            color = if (privacyAccepted) success.copy(alpha = 0.10f) else onboardingSurfaceSoft(),
            borderColor = if (privacyAccepted) success.copy(alpha = 0.55f) else onboardingBorder(),
            depthColor = if (privacyAccepted) success.copy(alpha = 0.85f) else onboardingBorderDepth()
        ) {
            Row(
                modifier = Modifier.padding(
                    start = 6.dp,
                    end = 14.dp.scaled(scale),
                    top = 8.dp.scaled(scale),
                    bottom = 8.dp.scaled(scale)
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = privacyAccepted,
                    onCheckedChange = onPrivacyChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = success,
                        checkmarkColor = PocketColors.PrimaryText,
                        uncheckedColor = onboardingTextSecondary()
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
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
                            color = onboardingAccentStrong(),
                            fontSize = 11.sp.scaledSp(scale),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onOpenPrivacy() }
                        )
                        Text(text = "•", color = onboardingTextMuted(), fontSize = 10.sp.scaledSp(scale))
                        Text(
                            text = s.onboardingTermsOfUse,
                            color = onboardingAccentStrong(),
                            fontSize = 11.sp.scaledSp(scale),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onOpenTerms() }
                        )
                    }
                }
            }
        }

        // Mojang disclaimer — required, but it is fine print, so it sits last and
        // quietest rather than interrupting the hero copy.
        Text(
            text = "NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT. PocketHost is an independent software application and is not affiliated with, authorized, maintained, sponsored, or endorsed by Mojang AB, Microsoft Corporation, or any of their affiliates.",
            fontSize = 8.5.sp.scaledSp(scale),
            lineHeight = 12.sp.scaledSp(scale),
            color = onboardingTextMuted().copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun HowItWorksScreen() {
    val s = LocalAppStrings.current
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp.scaled(scale))
    ) {
        OnboardingHeading(
            title = s.onboardingHowItWorksTitle,
            eyebrow = "THREE STEPS"
        )

        FlowDiagram()

        Column(verticalArrangement = Arrangement.spacedBy(10.dp.scaled(scale))) {
            DetailCard(
                accent = onboardingAccent(),
                icon = Icons.Filled.Dns,
                title = s.onboardingStep1Title,
                body = s.onboardingStep1Body
            )
            DetailCard(
                accent = onboardingWarn(),
                icon = Icons.Filled.Public,
                title = s.onboardingStep2Title,
                body = s.onboardingStep2Body
            )
            DetailCard(
                accent = onboardingSuccess(),
                icon = Icons.Filled.Group,
                title = s.onboardingStep3Title,
                body = s.onboardingStep3Body
            )
        }
    }
}

@Composable
private fun ImportScreen() {
    val s = LocalAppStrings.current
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp.scaled(scale))
    ) {
        OnboardingIconTile(accent = onboardingWarn(), size = 64.dp.scaled(scale)) {
            Icon(
                imageVector = Icons.Filled.Upload,
                contentDescription = null,
                tint = onboardingWarn(),
                modifier = Modifier.size(28.dp.scaled(scale))
            )
        }

        OnboardingHeading(
            title = s.onboardingImportTitle,
            subtitle = s.onboardingImportSubtitle,
            titleSize = 20.sp
        )

        FeatureListCard(
            accent = onboardingWarn(),
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
        verticalArrangement = Arrangement.spacedBy(13.dp.scaled(scale))
    ) {
        OnboardingIconTile(accent = onboardingAccent(), size = 64.dp.scaled(scale)) {
            Icon(
                imageVector = Icons.Filled.Extension,
                contentDescription = null,
                tint = onboardingAccent(),
                modifier = Modifier.size(28.dp.scaled(scale))
            )
        }

        OnboardingHeading(
            title = s.onboardingFeaturesTitle,
            subtitle = s.onboardingFeaturesSubtitle,
            eyebrow = s.onboardingFeaturesBanner,
            titleSize = 20.sp
        )

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
        verticalArrangement = Arrangement.spacedBy(13.dp.scaled(scale))
    ) {
        OnboardingIconTile(accent = onboardingSuccess(), size = 64.dp.scaled(scale)) {
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = null,
                tint = onboardingSuccess(),
                modifier = Modifier.size(28.dp.scaled(scale))
            )
        }

        OnboardingHeading(
            title = s.onboardingCrossPlayTitle,
            subtitle = s.onboardingCrossPlaySubtitle,
            titleSize = 20.sp
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp.scaled(scale))) {
            DetailCard(
                accent = onboardingSuccess(),
                icon = Icons.Filled.PhoneAndroid,
                title = s.onboardingCrossPlayJavaBedrock,
                body = s.onboardingCrossPlayJavaBedrockBody
            )
            DetailCard(
                accent = onboardingAccent(),
                icon = Icons.Filled.Extension,
                title = s.onboardingCrossPlayNoExtraApp,
                body = s.onboardingCrossPlayNoExtraAppBody
            )
        }

        OnboardingNoticeStrip(
            accent = onboardingWarn(),
            icon = Icons.Filled.Shield,
            text = s.onboardingCrossPlayExperimental
        )
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
    warningTick: Int,
    shakeConsumed: Boolean = false,
    onShakeConsumed: () -> Unit = {}
) {
    val scale = onboardingCompactScale()
    val notificationsShakeOffset = remember { Animatable(0f) }

    fun shouldWarnNotifications(): Boolean = warningTick > 0 && !notificationsPermissionGranted

    LaunchedEffect(warningTick, notificationsPermissionGranted) {
        // warningTick lives in the parent (rememberSaveable) and survives this composable
        // being torn down and recreated by the outer AnimatedContent on Back/Next — but that
        // teardown/recreate means THIS LaunchedEffect restarts too, even though warningTick
        // itself didn't change. shakeConsumed is the parent-owned guard against replaying the
        // shake on every re-entry into this step; it's only meaningful once, so it's marked
        // consumed here regardless of whether the animation actually got to play.
        if (shakeConsumed) return@LaunchedEffect
        if (!shouldWarnNotifications()) return@LaunchedEffect
        onShakeConsumed()
        val keyframes = listOf(0f, -8f, 8f, -6f, 6f, -3f, 3f, 0f)
        keyframes.forEach { x ->
            notificationsShakeOffset.animateTo(x, animationSpec = tween(durationMillis = 32))
        }
    }

    val statusAccent = if (notificationsPermissionGranted) onboardingSuccess() else onboardingWarn()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(13.dp.scaled(scale))
    ) {
        OnboardingIconTile(accent = statusAccent, size = 64.dp.scaled(scale)) {
            Icon(
                imageVector = if (notificationsPermissionGranted) Icons.Filled.CheckCircle else Icons.Filled.Notifications,
                contentDescription = null,
                tint = statusAccent,
                modifier = Modifier.size(28.dp.scaled(scale))
            )
        }

        OnboardingHeading(
            title = s.onboardingPermissionsTitle,
            subtitle = s.onboardingPermissionsSubtitle,
            eyebrow = if (notificationsPermissionGranted) s.onboardingPermissionsComplete else s.onboardingPermissionsRequired,
            eyebrowAccent = statusAccent
        )

        PermissionCard(
            accent = onboardingAccent(),
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
                icon = if (notificationsPermissionGranted) Icons.Filled.CheckCircle else null,
                variant = DuoButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 52.dp
            )
        }

        OnboardingNoticeStrip(
            accent = when {
                notificationsPermissionGranted -> onboardingSuccess()
                shouldWarnNotifications() -> MaterialTheme.colorScheme.error
                else -> onboardingAccent()
            },
            icon = when {
                notificationsPermissionGranted -> Icons.Filled.CheckCircle
                else -> Icons.Filled.Shield
            },
            text = when {
                notificationsPermissionGranted -> s.onboardingPermissionsStatusSet
                shouldWarnNotifications() -> s.onboardingPermissionsStatusWarn
                else -> s.onboardingPermissionsStatusTap
            },
            emphasised = shouldWarnNotifications()
        )

        if (errorText.isNotBlank()) {
            OnboardingErrorStrip(text = errorText)
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
    versionShakeTick: Int,
    shakeConsumed: Boolean = false,
    onShakeConsumed: () -> Unit = {}
) {
    val s = LocalAppStrings.current
    val scale = onboardingCompactScale()
    val versionShakeOffset = remember { Animatable(0f) }
    LaunchedEffect(versionShakeTick) {
        // Same re-entry issue as PermissionsScreen's shake: versionShakeTick survives the
        // outer AnimatedContent tearing this composable down and recreating it on Back/Next,
        // but this LaunchedEffect itself restarts on that recreate even with an unchanged
        // tick value — shakeConsumed (owned by the parent) stops it from replaying.
        if (shakeConsumed) return@LaunchedEffect
        if (versionShakeTick == 0) return@LaunchedEffect
        onShakeConsumed()
        val keyframes = listOf(0f, -7f, 7f, -5f, 5f, -3f, 3f, 0f)
        keyframes.forEach {
            versionShakeOffset.animateTo(it, animationSpec = tween(durationMillis = 32))
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled(scale))
    ) {
        OnboardingHeading(
            title = s.onboardingSetupTitle,
            eyebrow = "LAST STEP",
            centered = false,
            titleSize = 20.sp
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
                    tint = onboardingAccentStrong()
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
                    tint = onboardingAccentStrong()
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
                        tint = onboardingAccentStrong()
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = if (hasSelectedVersion) {
                        onboardingTextPrimary()
                    } else {
                        onboardingTextSecondary()
                    },
                    disabledBorderColor = if (showVersionError) MaterialTheme.colorScheme.error else onboardingBorder(),
                    disabledLabelColor = onboardingTextSecondary(),
                    disabledTrailingIconColor = onboardingAccentStrong()
                ),
                shape = duoTextFieldShape(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (showVersionError) {
            OnboardingErrorStrip(text = s.onboardingSetupVersionError)
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
                    tint = onboardingAccentStrong()
                )
            },
            shape = duoTextFieldShape(),
            modifier = Modifier.fillMaxWidth(),
            colors = duoOutlinedTextFieldColors()
        )

        OnboardingNoticeStrip(
            accent = onboardingSuccess(),
            icon = Icons.Filled.CheckCircle,
            text = s.onboardingSetupBackupNotice
        )
    }
}

/**
 * Inline status / notice line: tinted container, leading icon, one short body.
 * Every "heads up" box in the tour uses this so they all read the same.
 */
@Composable
private fun OnboardingNoticeStrip(
    accent: Color,
    icon: ImageVector,
    text: String,
    emphasised: Boolean = false
) {
    val scale = onboardingCompactScale()
    OnboardingCard(
        modifier = Modifier.fillMaxWidth(),
        corner = OnboardingTileCorner,
        color = accent.copy(alpha = if (emphasised) 0.16f else 0.10f),
        borderColor = accent.copy(alpha = if (emphasised) 0.75f else 0.45f),
        depthColor = accent.copy(alpha = if (emphasised) 0.95f else 0.65f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp.scaled(scale), vertical = 11.dp.scaled(scale)),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(17.dp)
            )
            Text(
                text = text,
                color = onboardingTextPrimary(),
                fontSize = 11.sp.scaledSp(scale),
                lineHeight = 16.sp.scaledSp(scale),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OnboardingErrorStrip(text: String) {
    val scale = onboardingCompactScale()
    OnboardingCard(
        modifier = Modifier.fillMaxWidth(),
        corner = OnboardingChipCorner,
        color = onboardingErrorSurface(),
        borderColor = onboardingErrorBorder(),
        depthColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp.scaled(scale)),
            color = onboardingErrorText(),
            fontSize = 11.sp.scaledSp(scale),
            lineHeight = 16.sp.scaledSp(scale),
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Phone → relay → friends. Three raised tiles joined by chevrons, matching the
 * app's node styling rather than the flat outlined boxes it used before.
 */
@Composable
private fun FlowDiagram() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Top
    ) {
        FlowNode(label = "Your\nphone", icon = Icons.Filled.PhoneAndroid)
        FlowArrow()
        FlowNode(label = "PocketHost\nrelay", iconRes = R.drawable.ic_fg)
        FlowArrow()
        FlowNode(label = "Friends\nconnect", icon = Icons.Filled.Group)
    }
}

@Composable
private fun FlowNode(label: String, emoji: String? = null, icon: ImageVector? = null, iconRes: Int? = null) {
    val scale = onboardingCompactScale()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OnboardingIconTile(
            accent = onboardingAccent(),
            size = 46.dp.scaled(scale),
            corner = OnboardingChipCorner
        ) {
            when {
                emoji != null -> Text(text = emoji, fontSize = 18.sp)
                iconRes != null -> Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = onboardingAccentStrong(),
                    modifier = Modifier.size(22.dp.scaled(scale))
                )
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = onboardingAccentStrong(),
                    modifier = Modifier.size(22.dp.scaled(scale))
                )
            }
        }
        Text(
            text = label,
            textAlign = TextAlign.Center,
            color = onboardingTextSecondary(),
            fontSize = 9.sp.scaledSp(scale),
            lineHeight = 12.sp.scaledSp(scale),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FlowArrow() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = null,
        tint = onboardingBorder(),
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 14.dp)
            .size(16.dp)
    )
}

/** Row card carrying one idea: a tinted icon chip, a bold title and a short body. */
@Composable
private fun DetailCard(
    accent: Color,
    title: String,
    body: String,
    icon: ImageVector? = null
) {
    val scale = onboardingCompactScale()
    OnboardingCard(
        modifier = Modifier.fillMaxWidth(),
        corner = OnboardingTileCorner
    ) {
        Row(
            modifier = Modifier.padding(13.dp.scaled(scale)),
            horizontalArrangement = Arrangement.spacedBy(12.dp.scaled(scale)),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp.scaled(scale))
                    .clip(RoundedCornerShape(10.dp.scaled(scale)))
                    .background(accent.copy(alpha = 0.18f))
                    .border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(10.dp.scaled(scale))),
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
                    Text(
                        text = "•",
                        color = accent,
                        fontSize = 20.sp.scaledSp(scale),
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp.scaled(scale))) {
                Text(
                    text = title,
                    color = onboardingTextPrimary(),
                    fontSize = 13.sp.scaledSp(scale),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Text(
                    text = body,
                    color = onboardingTextSecondary(),
                    fontSize = 11.sp.scaledSp(scale),
                    lineHeight = 16.sp.scaledSp(scale),
                    maxLines = 3
                )
            }
        }
    }
}

/** Single card listing several title/body pairs, separated by hairlines. */
@Composable
private fun FeatureListCard(accent: Color, entries: List<Pair<String, String>>) {
    val scale = onboardingCompactScale()
    OnboardingCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp.scaled(scale))) {
            entries.forEachIndexed { index, (title, body) ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 11.dp.scaled(scale))
                            .height(1.dp)
                            .background(onboardingBorder().copy(alpha = 0.6f))
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp.scaled(scale)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp.scaled(scale)),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp.scaled(scale))
                                .clip(RoundedCornerShape(3.dp))
                                .background(accent)
                        )
                        Text(
                            text = title,
                            color = onboardingTextPrimary(),
                            fontSize = 12.sp.scaledSp(scale),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = body,
                        color = onboardingTextSecondary(),
                        fontSize = 11.sp.scaledSp(scale),
                        lineHeight = 16.sp.scaledSp(scale)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureGrid(items: List<FeatureItem>) {
    val scale = onboardingCompactScale()
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { item ->
                    OnboardingCard(
                        modifier = Modifier.weight(1f),
                        corner = OnboardingTileCorner
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp.scaled(scale)),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(onboardingAccent().copy(alpha = 0.18f))
                                    .border(1.dp, onboardingAccent().copy(alpha = 0.40f), RoundedCornerShape(9.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = onboardingAccent(),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Text(
                                text = item.title,
                                color = onboardingTextPrimary(),
                                fontSize = 12.sp.scaledSp(scale),
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = item.body,
                                color = onboardingTextSecondary(),
                                fontSize = 10.sp.scaledSp(scale),
                                lineHeight = 14.sp.scaledSp(scale)
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
    val scale = onboardingCompactScale()
    OnboardingCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp.scaled(scale)),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accent.copy(alpha = 0.18f))
                    .border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    color = onboardingTextPrimary(),
                    fontSize = 13.sp.scaledSp(scale),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Text(
                    text = body,
                    color = onboardingTextSecondary(),
                    fontSize = 11.sp.scaledSp(scale),
                    lineHeight = 16.sp.scaledSp(scale),
                    maxLines = 3
                )
            }
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

/**
 * Quiet counterpart to [PrimaryButton]. DuoButton's Secondary variant is a fixed
 * bright green, which both fights the primary action for attention and ignores
 * the selected mob theme — Back is drawn here as a neutral raised surface
 * instead, with the same press depth as the rest of the app's buttons.
 */
@Composable
private fun OutlineButton(modifier: Modifier = Modifier, text: String, onClick: () -> Unit) {
    Box(modifier = modifier.padding(bottom = 4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(onboardingSurface())
                .pill3d(
                    elevation = 4.dp,
                    borderColor = onboardingBorder(),
                    depthColor = onboardingBorderDepth()
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = onboardingTextPrimary(),
                fontSize = 15.sp,
                fontFamily = Monocraft,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
