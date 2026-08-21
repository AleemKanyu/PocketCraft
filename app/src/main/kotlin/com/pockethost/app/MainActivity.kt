package com.pockethost.app

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pockethost.app.R
import com.pockethost.app.analytics.FirebaseAnalyticsManager
import com.pockethost.app.data.preferences.AppPreferences
import com.pockethost.app.data.preferences.AppPreferencesStore
import com.pockethost.app.service.ServerPropertiesHelper
import com.pockethost.app.service.ServerFileManager
import com.pockethost.app.setup.JreExtractor
import com.pockethost.app.ui.onboarding.OnboardingActivity
import com.pockethost.app.ui.screens.ErrorScreen
import com.pockethost.app.ui.screens.PocketHostApp
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.screens.SplashScreen
import com.pockethost.app.ui.theme.PocketHostTheme
import com.pockethost.app.ui.util.ThemePreference
import com.pockethost.app.ui.util.ThemePreferenceStore
import com.pockethost.app.ui.util.MobTheme
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import android.net.Uri
import android.util.Log
import android.content.Intent
import com.pockethost.app.update.UpdateConfig
import com.pockethost.app.update.UpdateManager
import com.pockethost.app.ui.components.UpdatePopup
import androidx.compose.runtime.CompositionLocalProvider
import com.pockethost.app.util.LocalAppStrings
import com.pockethost.app.util.appStringsFor
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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

    companion object {
        var isAppInForeground = false
        private const val KEY_RATE_LAST_REQUEST_AT = "play_store_rating_last_request_at"
        private const val KEY_RATE_REQUEST_COUNT = "play_store_rating_request_count"
        private const val RATE_MIN_LAUNCHES = 4
        private const val RATE_MAX_REQUESTS = 3
        private const val RATE_PROMPT_DELAY_MS = 1_200L
        private val RATE_REQUEST_COOLDOWN_MS = TimeUnit.DAYS.toMillis(30)
    }

    private var uiCommandListener: com.pockethost.app.broadcast.DashboardCommandListener? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.i("MainActivity", "POST_NOTIFICATIONS granted: $isGranted")
    }

    override fun onStart() {
        super.onStart()
        isAppInForeground = true
        com.pockethost.app.broadcast.RemoteCommandListener.startListening(this)




    }

    override fun onStop() {
        super.onStop()
        isAppInForeground = false
        com.pockethost.app.broadcast.RemoteCommandListener.stopListening()

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var initialJreReady = false
        runBlocking {
            try {
                withTimeout(150) {
                    val selectedVersion = AppPreferencesStore
                        .getSelectedVersionFlow(applicationContext)
                        .first()
                        .orEmpty()
                    val runtime = JreExtractor.runtimeForVersion(selectedVersion)
                    initialJreReady = JreExtractor.isExtracted(applicationContext, runtime)
                }
            } catch (e: Exception) {
                // Ignore timeout or other errors, fallback to false
            }
        }

        val preferences = AppPreferences(this)
        // Sync Firebase auth state to preferences for multi-process safety
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        preferences.firebaseUserUid = currentUser?.uid


        val onboardingCompleted = preferences.onboardingCompleted
        preferences.recordAppLaunch()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (onboardingCompleted && preferences.alwaysAliveBackground && !com.pockethost.app.server.ServerHostService.isServiceRunning(this)) {
            val listenerIntent = Intent(this, com.pockethost.app.server.ServerHostService::class.java).apply {
                action = com.pockethost.app.server.ServerHostService.ACTION_START_LISTENER
            }
            try {
                ContextCompat.startForegroundService(this, listenerIntent)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to auto-start background listener: ${e.message}")
            }
        }
        ThemePreferenceStore.loadCustomColors(this)
        val initialThemePreference = ThemePreferenceStore.load(this)
        val initialMobTheme = ThemePreferenceStore.loadMobTheme(this)
        val initialDarkTheme = initialThemePreference.resolve(systemDark = ThemePreferenceStore.isSystemDark(this))
        PocketColors.activeMobTheme = initialMobTheme
        PocketColors.isDark = initialDarkTheme

        val splashBackgroundColor = PocketColors.BgApp.toArgb()
        val initialSystemBarColor = splashBackgroundColor
        val initialNavBarColor = splashBackgroundColor
        val initialLightSystemBars = PocketColors.BgApp.luminance() >= 0.5f
        val initialLightNavBar = PocketColors.BgApp.luminance() >= 0.5f
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.decorView.isForceDarkAllowed = false
            window.isNavigationBarContrastEnforced = false
        }
        window.statusBarColor = initialSystemBarColor
        window.navigationBarColor = initialNavBarColor
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = initialLightSystemBars
            isAppearanceLightNavigationBars = initialLightNavBar
        }

        // Never block startup on DataStore reads; OEM builds may ANR the activity.
        lifecycleScope.launch(Dispatchers.IO) {
            val analyticsConsent = runCatching {
                AppPreferencesStore.isAnalyticsConsentFlow(applicationContext).first()
            }.getOrDefault(false)

            val crashDiagnosticsConsent = runCatching {
                AppPreferencesStore.isCrashDiagnosticsConsentFlow(applicationContext).first()
            }.getOrDefault(true)

            runCatching {
                FirebaseAnalyticsManager.initialize(applicationContext, collectionEnabled = analyticsConsent)
                if (analyticsConsent) {
                    FirebaseAnalyticsManager.logEvent("app_open")
                }
            }

            runCatching {
                Firebase.crashlytics.setCrashlyticsCollectionEnabled(analyticsConsent)
            }
        }

        setContent {
            val billingManager = remember { com.pockethost.app.billing.BillingManager.getInstance(this@MainActivity) }
            val isPremium by billingManager.isPremium.collectAsState()
            var themePreference by remember { mutableStateOf(initialThemePreference) }
            var mobTheme by remember { mutableStateOf(initialMobTheme) }

            LaunchedEffect(isPremium) {
                if (!isPremium && mobTheme == MobTheme.CUSTOM) {
                    mobTheme = MobTheme.CREEPER
                    PocketColors.activeMobTheme = MobTheme.CREEPER
                    ThemePreferenceStore.saveMobTheme(this@MainActivity, MobTheme.CREEPER)
                }
            }
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = themePreference.resolve(systemDark = systemDarkTheme)

            var updateConfig by remember { mutableStateOf<UpdateConfig?>(null) }
            var playStoreRatingPromptEnabled by remember { mutableStateOf(true) }
            var dismissedUpdateKey by remember { mutableStateOf<String?>(null) }
            var dismissedUpdateShowFlag by remember { mutableStateOf(false) }

            // Remote Config update nudges
            var remoteConfigInitialized by remember { mutableStateOf(false) }
            var rcUpdateConfig by remember { mutableStateOf<UpdateConfig?>(null) }
            var dismissedRcUpdateKey by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                com.pockethost.app.config.RemoteConfigManager.initialize(applicationContext)
                remoteConfigInitialized = true
            }

            val currentVersionCode = com.pockethost.app.BuildConfig.VERSION_CODE

            LaunchedEffect(remoteConfigInitialized) {
                if (remoteConfigInitialized) {
                    val minSupported = com.pockethost.app.config.FeatureGate.getMinSupportedVersionCode()
                    val recommended = com.pockethost.app.config.FeatureGate.getRecommendedVersionCode()
                    Log.d("MainActivity", "RemoteConfig versions parsed: minSupported=$minSupported, recommended=$recommended, currentVersionCode=$currentVersionCode")
                    if (currentVersionCode < minSupported) {
                        rcUpdateConfig = UpdateConfig(
                            showUpdatePopup = true,
                            playStoreUrl = "market://details?id=com.pockethost.app",
                            versionCode = minSupported,
                            dismissKey = "rc:$minSupported",
                            isForced = true
                        )
                    } else if (currentVersionCode < recommended && dismissedRcUpdateKey != "rc:$recommended") {
                        rcUpdateConfig = UpdateConfig(
                            showUpdatePopup = true,
                            playStoreUrl = "market://details?id=com.pockethost.app",
                            versionCode = recommended,
                            dismissKey = "rc:$recommended",
                            isForced = false
                        )
                    } else {
                        rcUpdateConfig = null
                    }
                }
            }

            val updateConfigFlow = remember { UpdateManager.getUpdateConfigFlow(this@MainActivity) }
            LaunchedEffect(updateConfigFlow) {
                updateConfigFlow.collect { config ->
                    Log.d("MainActivity", "Received update config from flow: $config")
                    playStoreRatingPromptEnabled = config?.enablePlayStoreRatingPrompt ?: true
                    if (config != null && config.showUpdatePopup) {
                        val alreadyDismissed = !config.isForced && 
                            dismissedUpdateShowFlag && 
                            dismissedUpdateKey == config.dismissKey
                        
                        if (alreadyDismissed) {
                            Log.d("MainActivity", "Update popup skipped: already dismissed this version/flag in this session.")
                        } else {
                            Log.d("MainActivity", "Showing update popup: $config")
                            updateConfig = config
                        }
                    } else {
                        Log.d("MainActivity", "Hiding update popup: config is null or showUpdatePopup is false.")
                        updateConfig = null
                    }
                }
            }

            val finalUpdateConfig = remember(updateConfig, rcUpdateConfig) {
                if (rcUpdateConfig?.isForced == true) {
                    rcUpdateConfig
                } else if (updateConfig?.isForced == true) {
                    updateConfig
                } else {
                    rcUpdateConfig ?: updateConfig
                }
            }

            val appStrings = appStringsFor(this@MainActivity, AppPreferences(this@MainActivity).appLanguage)
            CompositionLocalProvider(LocalAppStrings provides appStrings) {
            PocketHostTheme(darkTheme = darkTheme, mobTheme = mobTheme) {
                var jreReady by remember { mutableStateOf(initialJreReady) }
                var jreError by remember { mutableStateOf<String?>(null) }
                var jreProgress by remember { mutableStateOf(0) }
                var jreStatus by remember { mutableStateOf("Preparing Minecraft Runtime...") }

                SideEffect {
                    val onSplash = jreError == null && !jreReady
                    val statusBarColor = PocketColors.BgApp.toArgb()
                    val navBarColor = if (onSplash) {
                        PocketColors.BgApp.toArgb()
                    } else {
                        PocketColors.FooterBg.toArgb()
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        window.decorView.isForceDarkAllowed = false
                        window.isNavigationBarContrastEnforced = false
                    }
                    window.statusBarColor = statusBarColor
                    window.navigationBarColor = navBarColor
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = androidx.compose.ui.graphics.Color(statusBarColor).luminance() >= 0.5f
                        isAppearanceLightNavigationBars = androidx.compose.ui.graphics.Color(navBarColor).luminance() >= 0.5f
                    }
                }

                LaunchedEffect(Unit) {
                    try {
                        jreProgress = 0
                        jreStatus = "Preparing Minecraft Runtime..."
                        withContext(Dispatchers.IO) {
                            val selectedVersion = AppPreferencesStore
                                .getSelectedVersionFlow(applicationContext)
                                .first()
                                .orEmpty()
                            val runtime = JreExtractor.runtimeForVersion(selectedVersion)
                            JreExtractor.extractIfNeeded(applicationContext, runtime) { percent, status ->
                                runOnUiThread {
                                    jreProgress = percent
                                    jreStatus = status
                                }
                            }
                        }
                        jreProgress = 100
                        jreStatus = "Minecraft Runtime Ready"
                        jreReady = true
                    } catch (e: Throwable) {
                        jreError = e.message ?: "Unknown runtime initialization failure"
                    }
                }

                LaunchedEffect(jreReady, onboardingCompleted, updateConfig, playStoreRatingPromptEnabled) {
                    if (playStoreRatingPromptEnabled && jreReady && onboardingCompleted && updateConfig == null) {
                        maybeRequestPlayStoreRating(preferences)
                    }
                }

                when {
                    jreError != null -> ErrorScreen(
                        message = "Failed to prepare runtime:\n$jreError",
                        onRetry = { recreate() }
                    )
                    !jreReady -> SplashScreen(
                        progress = jreProgress / 100f,
                        status = jreStatus
                    )
                    !onboardingCompleted -> {
                        SplashScreen(
                            progress = 1f,
                            status = "Opening onboarding..."
                        )
                        LaunchedEffect(Unit) {
                            OnboardingActivity.start(this@MainActivity)
                            finish()
                        }
                    }
                    else -> PocketHostApp(
                        isDarkTheme = darkTheme,
                        isPlayStoreRatingPromptEnabled = playStoreRatingPromptEnabled,
                        currentMobTheme = mobTheme,
                        onDarkThemeChange = { enabled ->
                            val nextPreference = if (enabled) ThemePreference.DARK else ThemePreference.LIGHT
                            if (themePreference != nextPreference) {
                                PocketColors.isDark = enabled
                                themePreference = nextPreference
                                ThemePreferenceStore.save(this@MainActivity, nextPreference)
                                com.pockethost.app.server.ServerHostService.pushWidgetUpdate(this@MainActivity)
                                FirebaseAnalyticsManager.logThemeChanged(nextPreference.name.lowercase())
                            }
                        },
                        onMobThemeChange = { nextTheme: MobTheme ->
                            if (mobTheme != nextTheme) {
                                PocketColors.activeMobTheme = nextTheme
                                mobTheme = nextTheme
                                ThemePreferenceStore.saveMobTheme(this@MainActivity, nextTheme)
                                com.pockethost.app.server.ServerHostService.pushWidgetUpdate(this@MainActivity)
                                FirebaseAnalyticsManager.logThemeChanged(nextTheme.id)
                            }
                        }
                    )
                }

                finalUpdateConfig?.let { config ->
                    UpdatePopup(
                        config = config,
                        onUpdateNow = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(config.playStoreUrl))
                                startActivity(intent)
                            }
                        },
                        onDismiss = {
                            Log.d("MainActivity", "Update popup dismissed by user.")
                            if (config == rcUpdateConfig) {
                                dismissedRcUpdateKey = config.dismissKey
                                rcUpdateConfig = null
                            } else {
                                dismissedUpdateShowFlag = true
                                dismissedUpdateKey = config.dismissKey
                                updateConfig = null
                            }
                        }
                    )
            }
        }
    }
}
    }

    private fun maybeRequestPlayStoreRating(preferences: AppPreferences) {
        val currentVersion = com.pockethost.app.BuildConfig.VERSION_CODE.toString()

        if (!com.pockethost.app.config.FeatureGate.isReviewPromptEnabled()) {
            Log.d("MainActivity", "In-app review prompt is disabled by Remote Config.")
            return
        }

        val starts = preferences.successfulServerStarts
        val minStarts = com.pockethost.app.config.FeatureGate.getReviewPromptMinStarts()
        if (starts < minStarts) {
            Log.d("MainActivity", "In-app review prompt skipped: starts ($starts) < minStarts ($minStarts).")
            return
        }

        if (preferences.reviewRequestedForVersion == currentVersion) {
            Log.d("MainActivity", "In-app review prompt skipped: already requested for this version ($currentVersion).")
            return
        }

        window.decorView.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed

            // Mark review requested for this version
            preferences.reviewRequestedForVersion = currentVersion

            val reviewManager = ReviewManagerFactory.create(this)
            reviewManager.requestReviewFlow().addOnCompleteListener { requestTask ->
                if (!requestTask.isSuccessful || isFinishing || isDestroyed) return@addOnCompleteListener

                reviewManager.launchReviewFlow(this, requestTask.result)
            }
        }, RATE_PROMPT_DELAY_MS)
    }
}
