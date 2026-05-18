package com.pocketcraft.server.ui.onboarding

import com.pocketcraft.server.BuildConfig

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pocketcraft.server.MainActivity
import com.pocketcraft.server.config.RelayServers
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.data.model.ServerType
import com.pocketcraft.server.data.repository.ServerConfigRepository
import com.pocketcraft.server.R
import com.pocketcraft.server.ui.screens.ServerTypeVersionBottomSheet
import com.pocketcraft.server.ui.components.duoOutlinedTextFieldColors
import com.pocketcraft.server.ui.components.duoTextFieldShape
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.theme.Monocraft
import com.pocketcraft.server.ui.theme.PocketCraftTheme
import com.pocketcraft.server.ui.theme.pocketIsDarkTheme
import com.pocketcraft.server.ui.util.playAppHaptic
import com.pocketcraft.server.ui.util.ThemePreferenceStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val OnboardingGreenLight = Color(0xFF3DDC84)
private val OnboardingGoldLight = Color(0xFFFFB142)

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
            Color(0xFF10211C),
            Color(0xFF0B1714)
        )
    )
} else {
    Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFFFFFF),
            Color(0xFFEAF8E7),
            Color(0xFFDFF3D8)
        )
    )
}

@Composable
private fun onboardingPhoneOuterBrush(): Brush = if (pocketIsDarkTheme()) {
    Brush.verticalGradient(
        listOf(
            Color(0xFF1E352D),
            Color(0xFF182A24)
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
            Color(0xFF243E35)
        )
    )
} else {
    Brush.verticalGradient(
        listOf(
            PocketColors.SurfaceVarLight,
            Color(0xFFEDF6E1)
        )
    )
}

@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {
    private val preferences by lazy { AppPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val initialThemePreference = ThemePreferenceStore.load(this)

        setContent {
            val darkTheme = initialThemePreference.resolve(systemDark = isSystemInDarkTheme())
            PocketCraftTheme(darkTheme = darkTheme) {
                SideEffect {
                    window.statusBarColor = if (darkTheme) PocketColors.BgDark.toArgb() else AndroidColor.parseColor("#F5FAF1")
                    window.navigationBarColor = if (darkTheme) PocketColors.SurfaceDark.toArgb() else AndroidColor.parseColor("#DFF3D8")
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                OnboardingScreen(onComplete = { completeOnboarding() })
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
private fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { AppPreferences(context) }
    val hapticFeedback = LocalHapticFeedback.current
    val appFeedbackEnabled by AppPreferencesStore.isSoundEnabledFlow(context).collectAsState(initial = true)
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    val steps = remember { onboardingSteps() }
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
    var setupShowVersionDialog by remember { mutableStateOf(false) }
    var setupFormError by rememberSaveable { mutableStateOf("") }
    var versionSelectionError by rememberSaveable { mutableStateOf(false) }
    var versionShakeTick by rememberSaveable { mutableIntStateOf(0) }
    var backgroundPermissionGranted by remember { mutableStateOf(isBackgroundPermissionGranted(context)) }
    var notificationsPermissionGranted by remember { mutableStateOf(isNotificationPermissionGranted(context)) }
    var permissionStepError by rememberSaveable { mutableStateOf("") }
    var permissionWarningTick by rememberSaveable { mutableIntStateOf(0) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsPermissionGranted = granted || isNotificationPermissionGranted(context)
        if (permissionStepError.isNotBlank() && backgroundPermissionGranted && notificationsPermissionGranted) {
            permissionStepError = ""
        }
    }

    val batteryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        backgroundPermissionGranted = isBackgroundPermissionGranted(context)
        if (permissionStepError.isNotBlank() && backgroundPermissionGranted && notificationsPermissionGranted) {
            permissionStepError = ""
        }
    }

    LaunchedEffect(Unit) {
        setupVersion = ""
        setupSeed = AppPreferencesStore.getWorldSeedFlow(context).first()
        backgroundPermissionGranted = isBackgroundPermissionGranted(context)
        notificationsPermissionGranted = isNotificationPermissionGranted(context)
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
        animationSpec = tween(durationMillis = 280)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = onboardingBackgroundBrush())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TopHeader(
                    progress = progress,
                    step = currentStep + 1,
                    total = steps.size
                )

                OnboardingPhoneFrame(
                    step = currentStep,
                    totalSteps = steps.size,
                ) {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            if (targetState > initialState) {
                                slideInHorizontally(animationSpec = tween(420, easing = FastOutSlowInEasing)) { it / 5 } +
                                    fadeIn(tween(380, easing = FastOutSlowInEasing)) +
                                    scaleIn(initialScale = 0.95f, animationSpec = tween(380, easing = FastOutSlowInEasing)) togetherWith
                                    slideOutHorizontally(animationSpec = tween(340, easing = FastOutSlowInEasing)) { -it / 7 } +
                                    fadeOut(tween(320, easing = FastOutSlowInEasing)) +
                                    scaleOut(targetScale = 1.015f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                            } else {
                                slideInHorizontally(animationSpec = tween(420, easing = FastOutSlowInEasing)) { -it / 5 } +
                                    fadeIn(tween(380, easing = FastOutSlowInEasing)) +
                                    scaleIn(initialScale = 0.95f, animationSpec = tween(380, easing = FastOutSlowInEasing)) togetherWith
                                    slideOutHorizontally(animationSpec = tween(340, easing = FastOutSlowInEasing)) { it / 7 } +
                                    fadeOut(tween(320, easing = FastOutSlowInEasing)) +
                                    scaleOut(targetScale = 1.015f, animationSpec = tween(320, easing = FastOutSlowInEasing))
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
                                }
                            )
                            1 -> HowItWorksScreen()
                            2 -> ImportScreen()
                            3 -> FeaturesScreen()
                            4 -> CrossPlayScreen()
                            5 -> RelayRegionOnboardingScreen(
                                selectedHost = setupRelayHost,
                                onSelectHost = { setupRelayHost = it }
                            )
                            6 -> PermissionsScreen(
                                backgroundPermissionGranted = backgroundPermissionGranted,
                                notificationsPermissionGranted = notificationsPermissionGranted,
                                onAllowBackground = {
                                    playHaptic()
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    batteryPermissionLauncher.launch(intent)
                                },
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
                            text = "Back",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                playHaptic()
                                currentStep--
                            }
                        )
                    }

                    val missingPermissionStep = currentStep == 6 && (!backgroundPermissionGranted || !notificationsPermissionGranted)

                    PrimaryButton(
                        modifier = Modifier.weight(1.25f),
                        text = if (currentStep == steps.lastIndex) "Finish setup" else "Next",
                        enabled = if (currentStep == 0) privacyAccepted else true,
                        onClick = {
                            if (missingPermissionStep) {
                                permissionStepError = "Allow both background and notification permissions to continue."
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
                                        bedrockRelayRegion = if (setupRelayHost.contains("mine")) "MUMBAI" else "SINGAPORE"
                                        relayHost = setupRelayHost
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

private fun onboardingSteps(): List<OnboardingStep> {
    return listOf(
        OnboardingStep("WELCOME"),
        OnboardingStep("HOW IT WORKS"),
        OnboardingStep("BRING YOUR WORLD"),
        OnboardingStep("FULL CONTROL"),
        OnboardingStep("CROSS-PLAY READY"),
        OnboardingStep("PICK REGION"),
        OnboardingStep("PERMISSIONS"),
        OnboardingStep("SETUP")
    )
}

@Composable
private fun RelayRegionOnboardingScreen(
    selectedHost: String,
    onSelectHost: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Choose your relay region",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = onboardingTextPrimary(),
            fontFamily = Monocraft
        )
        Text(
            text = "Pick the relay server closest to your players. You can change this later from the dashboard too.",
            fontSize = 13.sp,
            color = onboardingTextSecondary(),
            lineHeight = 18.sp
        )
        RelayServers.ALL.forEach { server ->
            val selected = server.host == selectedHost
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectHost(server.host) },
                shape = RoundedCornerShape(22.dp),
                color = if (selected) onboardingAccentPurpleMuted() else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, if (selected) onboardingAccentPurple() else onboardingBorderColor())
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
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
private fun TopHeader(progress: Float, step: Int, total: Int) {
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

            Surface(
                color = onboardingAccentPurpleMuted(),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = "Beta",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = onboardingAccentPurpleDark(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(26.dp, RoundedCornerShape(34.dp), clip = false)
            .clip(RoundedCornerShape(34.dp))
            .background(brush = onboardingPhoneOuterBrush())
            .border(1.5.dp, onboardingBorderColor().copy(alpha = 0.8f), RoundedCornerShape(34.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 70.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(0.dp, 0.dp, 12.dp, 12.dp))
                .background(if (pocketIsDarkTheme()) MaterialTheme.colorScheme.surface.copy(alpha = 0.92f) else Color(0xFFF6F8F1))
                .border(1.dp, onboardingBorderColor().copy(alpha = 0.85f), RoundedCornerShape(0.dp, 0.dp, 12.dp, 12.dp))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(474.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(brush = onboardingPhoneInnerBrush())
                .border(1.dp, onboardingBorderColor().copy(alpha = 0.8f), RoundedCornerShape(26.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
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
    onOpenPrivacy: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HaloIconBox(
            accent = onboardingAccentGreen(),
            drawableRes = R.drawable.ic_launcher_foreground_square
        )

        Surface(
            color = onboardingAccentPurple().copy(alpha = 0.12f),
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = "Beta",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = onboardingAccentPurple(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "Your phone is now a Minecraft server",
            fontSize = 21.sp,
            lineHeight = 24.sp,
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Host Java Edition servers for free. No PC required. Share with friends anywhere in the world.",
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center,
            maxLines = 3
        )

        FeaturePillRow(
            items = listOf("Free to host", "No PC", "Invite friends")
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clickable { onPrivacyChange(!privacyAccepted) }
        ) {
            Checkbox(
                checked = privacyAccepted,
                onCheckedChange = onPrivacyChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = PocketColors.Primary,
                    uncheckedColor = onboardingBorderColor()
                )
            )
            Text(
                text = buildAnnotatedString {
                    append("I agree to the ")
                    withStyle(style = SpanStyle(color = PocketColors.Primary, fontWeight = FontWeight.Bold)) {
                        append("Privacy Policy")
                    }
                },
                fontSize = 11.sp,
                color = onboardingTextSecondary(),
                modifier = Modifier.clickable { onOpenPrivacy() }
            )
        }

        ScreenCard(
            accent = onboardingAccentGreen(),
            title = "Fast start",
            subtitle = "PocketCraft keeps setup short, then guides you to the real server tools."
        )
    }
}

@Composable
private fun HowItWorksScreen() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "How it works",
            fontSize = 21.sp,
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft
        )

        FlowDiagram()

        DetailCard(
            accent = onboardingAccentPurple(),
            icon = Icons.Filled.Dns,
            title = "1. Start your server",
            body = "Paper runs locally on your phone and uses the memory you already have."
        )
        DetailCard(
            accent = onboardingAccentGold(),
            icon = Icons.Filled.Public,
            title = "2. Share your address",
            body = "PocketCraft gives you a unique relay address that friends can connect to."
        )
        DetailCard(
            accent = onboardingAccentGreen(),
            icon = Icons.Filled.Group,
            title = "3. Friends join instantly",
            body = "Java Edition players connect straight through the relay with minimal friction."
        )
    }
}

@Composable
private fun ImportScreen() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HaloIconBox(
            accent = onboardingAccentGold(),
            icon = Icons.Filled.Upload
        )

        Text(
            text = "Moving from Aternos or Minehut?",
            fontSize = 20.sp,
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Bring your world, plugins, and config with you. No starting over.",
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center,
            maxLines = 3
        )

        FeatureListCard(
            accent = onboardingAccentGold(),
            entries = listOf(
                "Upload world zip" to "Export from Aternos, drop the archive in PocketCraft, and keep going.",
                "Keep your plugins" to "Reuse the same .jar files. Your plugin stack moves with you.",
                "Config & ops carry over" to "server.properties, whitelist, and operator access remain intact."
            )
        )
    }
}

@Composable
private fun FeaturesScreen() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HaloIconBox(
            accent = onboardingAccentGreen(),
            icon = Icons.Filled.Extension
        )

        Text(
            text = "Full control, right in your pocket",
            fontSize = 20.sp,
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Not a stripped-down app. This is the real server toolkit.",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        Surface(
            color = onboardingAccentGreen().copy(alpha = 0.12f),
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = "Everything you need in one place",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = onboardingAccentPurpleDark(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Monocraft
            )
        }

        FeatureGrid(
            items = listOf(
                FeatureItem("Plugins", "Install compatible plugins", Icons.Filled.Extension),
                FeatureItem("Live console", "Run commands and watch logs", Icons.Filled.Dns),
                FeatureItem("Players", "Manage players", Icons.Filled.Group),
                FeatureItem("properties", "Edit settings directly", Icons.Filled.Settings)
            )
        )
    }
}

@Composable
private fun CrossPlayScreen() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HaloIconBox(
            accent = onboardingAccentGreen(),
            icon = Icons.Filled.Public
        )

        Text(
            text = "Cross-play is supported",
            fontSize = 20.sp,
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = "PocketCraft supports Java and Bedrock cross-play when the bridge is enabled in your server setup.",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center,
            maxLines = 3
        )

        DetailCard(
            accent = onboardingAccentGreen(),
            icon = Icons.Filled.PhoneAndroid,
            title = "Java + Bedrock",
            body = "Friends on PC and mobile can join the same world together through the relay."
        )

        DetailCard(
            accent = onboardingAccentPurple(),
            icon = Icons.Filled.Extension,
            title = "No extra app for players",
            body = "Share your server address and players can connect from their own edition right away."
        )

        Surface(
            color = onboardingAccentGold().copy(alpha = 0.12f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "⚠️ Beta Feature - Play at Your Own Discretion\n\nCross-play is still in beta development. Bugs and stability issues may occur as this feature is not yet officially supported. Use at your own risk.",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                color = onboardingTextPrimary(),
                fontSize = 11.sp,
                lineHeight = 16.sp,
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
    backgroundPermissionGranted: Boolean,
    notificationsPermissionGranted: Boolean,
    onAllowBackground: () -> Unit,
    onAllowNotifications: () -> Unit,
    errorText: String,
    warningTick: Int
) {
    val backgroundShakeOffset = remember { Animatable(0f) }
    val notificationsShakeOffset = remember { Animatable(0f) }

    fun shouldWarnBackground(): Boolean = warningTick > 0 && !backgroundPermissionGranted
    fun shouldWarnNotifications(): Boolean = warningTick > 0 && !notificationsPermissionGranted

    LaunchedEffect(warningTick, backgroundPermissionGranted) {
        if (!shouldWarnBackground()) return@LaunchedEffect
        val keyframes = listOf(0f, -8f, 8f, -6f, 6f, -3f, 3f, 0f)
        keyframes.forEach { x ->
            backgroundShakeOffset.animateTo(x, animationSpec = tween(durationMillis = 32))
        }
    }

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
        Text(
            text = "Two quick things",
            fontSize = 21.sp,
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = "These keep your server running without interruption.",
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        PermissionCard(
            accent = onboardingAccentGreen(),
            icon = Icons.Filled.Shield,
            title = "Background battery access",
            body = "Prevents Android from stopping your server after a few minutes of inactivity."
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = backgroundShakeOffset.value.dp)
        ) {
            Surface(
                onClick = onAllowBackground,
                shape = RoundedCornerShape(14.dp),
                color = when {
                    backgroundPermissionGranted -> onboardingAccentGreen().copy(alpha = 0.16f)
                    shouldWarnBackground() -> Color(0xFFFFECEA)
                    else -> onboardingSurfaceColor()
                },
                border = BorderStroke(
                    1.dp,
                    when {
                        backgroundPermissionGranted -> onboardingAccentGreen().copy(alpha = 0.35f)
                        shouldWarnBackground() -> Color(0xFFE85A5A)
                        else -> onboardingAccentGreen().copy(alpha = 0.35f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (backgroundPermissionGranted) "Background access granted" else "Allow background access",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = if (shouldWarnBackground()) Color(0xFFB42318) else onboardingTextPrimary(),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }

        PermissionCard(
            accent = onboardingAccentPurple(),
            icon = Icons.Filled.Notifications,
            title = "Notifications",
            body = "Shows server status and player count while the server is running."
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = notificationsShakeOffset.value.dp)
        ) {
            Surface(
                onClick = onAllowNotifications,
                shape = RoundedCornerShape(14.dp),
                color = when {
                    notificationsPermissionGranted -> onboardingAccentGreen().copy(alpha = 0.16f)
                    shouldWarnNotifications() -> Color(0xFFFFECEA)
                    else -> onboardingSurfaceColor()
                },
                border = BorderStroke(
                    1.dp,
                    when {
                        notificationsPermissionGranted -> onboardingAccentGreen().copy(alpha = 0.35f)
                        shouldWarnNotifications() -> Color(0xFFE85A5A)
                        else -> onboardingAccentGreen().copy(alpha = 0.35f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (notificationsPermissionGranted) "Notification permission granted" else "Allow notifications",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = if (shouldWarnNotifications()) Color(0xFFB42318) else onboardingTextPrimary(),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
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
            text = "Final setup",
            fontSize = 20.sp,
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft
        )

        OutlinedTextField(
            value = serverName,
            onValueChange = onServerNameChange,
            singleLine = true,
            label = { Text("Server name (required)") },
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
            label = { Text("World description (optional)") },
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
                    if (hasSelectedVersion) "${selectedServerType.displayName} $selectedVersion" else "Select server type + version"
                } else {
                    "${selectedServerType.displayName} (Custom JAR)"
                },
                onValueChange = {},
                singleLine = true,
                readOnly = true,
                enabled = false,
                label = { Text("Game version (required)") },
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
                    text = "Please select a game version",
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
            label = { Text("World seed (optional)") },
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
                    text = "You can restore world backups later in Settings whenever you are ready.",
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
            color = if (pocketIsDarkTheme()) MaterialTheme.colorScheme.surface.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.74f),
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, onboardingBorderColor().copy(alpha = 0.4f))
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
    Surface(
        color = onboardingSurfaceColor(),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(1.5.dp, onboardingAccentGreen().copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, color = onboardingTextPrimary(), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(text = subtitle, color = onboardingTextSecondary(), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3)
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .fillMaxWidth(0.38f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent)
            )
        }
    }
}

@Composable
private fun DetailCard(accent: Color, title: String, body: String, icon: ImageVector? = null) {
    Surface(
        color = onboardingSurfaceColor(),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, onboardingAccentGreen().copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(text = "•", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = title, color = onboardingTextPrimary(), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(text = body, color = onboardingTextSecondary(), fontSize = 11.sp, lineHeight = 16.sp, maxLines = 3)
            }
        }
    }
}

@Composable
private fun FeatureListCard(accent: Color, entries: List<Pair<String, String>>) {
    Surface(
        color = onboardingSurfaceColor(),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, onboardingAccentGreen().copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            entries.forEach { (title, body) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Text(text = title, color = onboardingTextPrimary(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(text = body, color = onboardingTextSecondary(), fontSize = 11.sp, lineHeight = 16.sp)
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
                        shadowElevation = 4.dp,
                        border = BorderStroke(1.5.dp, onboardingAccentGreen().copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
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
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, onboardingAccentGreen().copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth()
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
        modifier = modifier
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
    Box(modifier = modifier.height(56.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (enabled) {
                        if (pocketIsDarkTheme()) Color(0xFF3E8A0A) else Color(0xFF57B900)
                    } else {
                        if (pocketIsDarkTheme()) Color(0xFF4C5E4F) else Color(0xFFB7C6A5)
                    }
                )
        )
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.matchParentSize(),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (enabled) PocketColors.Primary else if (pocketIsDarkTheme()) Color(0xFF5C6E5F) else Color(0xFFC9D5BC),
                contentColor = Color.White,
                disabledContainerColor = if (pocketIsDarkTheme()) Color(0xFF5C6E5F) else Color(0xFFC9D5BC),
                disabledContentColor = Color.White
            ),
            border = BorderStroke(
                2.dp,
                if (enabled) {
                    if (pocketIsDarkTheme()) Color(0xFF3E8A0A) else Color(0xFF57B900)
                } else {
                    if (pocketIsDarkTheme()) Color(0xFF4C5E4F) else Color(0xFFB7C6A5)
                }
            )
        ) {
            Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
    }
}

@Composable
private fun OutlineButton(modifier: Modifier = Modifier, text: String, onClick: () -> Unit) {
    Box(modifier = modifier.height(56.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 5.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (pocketIsDarkTheme()) Color(0xFF1B2B26) else Color(0xFFD8DDD1))
        )
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            color = if (pocketIsDarkTheme()) onboardingSurfaceColor() else Color(0xFFF8FAF5),
            modifier = Modifier.matchParentSize(),
            border = BorderStroke(1.5.dp, onboardingBorderColor())
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = onboardingTextDark())
            }
        }
    }
}

@Composable
private fun SkipButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = onboardingSurfaceColor(),
        modifier = modifier.height(56.dp),
        border = BorderStroke(1.dp, onboardingBorderColor())
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = "Skip", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = onboardingTextMuted())
        }
    }
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
