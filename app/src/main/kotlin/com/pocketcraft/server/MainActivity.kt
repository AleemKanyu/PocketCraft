package com.pocketcraft.server

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
import com.pocketcraft.server.R
import com.pocketcraft.server.analytics.FirebaseAnalyticsManager
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.setup.JreExtractor
import com.pocketcraft.server.ui.onboarding.OnboardingActivity
import com.pocketcraft.server.ui.screens.ErrorScreen
import com.pocketcraft.server.ui.screens.PocketCraftApp
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.screens.SplashScreen
import com.pocketcraft.server.ui.theme.PocketCraftTheme
import com.pocketcraft.server.ui.util.ThemePreference
import com.pocketcraft.server.ui.util.ThemePreferenceStore
import com.pocketcraft.server.ui.util.MobTheme
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import android.net.Uri
import android.util.Log
import android.content.Intent
import com.pocketcraft.server.update.UpdateConfig
import com.pocketcraft.server.update.UpdateManager
import com.pocketcraft.server.ui.components.UpdatePopup
import androidx.compose.runtime.CompositionLocalProvider
import com.pocketcraft.server.util.LocalAppStrings
import com.pocketcraft.server.util.appStringsFor
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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

    companion object {
        var isAppInForeground = false
        private const val KEY_RATE_LAST_REQUEST_AT = "play_store_rating_last_request_at"
        private const val KEY_RATE_REQUEST_COUNT = "play_store_rating_request_count"
        private const val RATE_MIN_LAUNCHES = 4
        private const val RATE_MAX_REQUESTS = 3
        private const val RATE_PROMPT_DELAY_MS = 1_200L
        private val RATE_REQUEST_COOLDOWN_MS = TimeUnit.DAYS.toMillis(30)
    }

    private var uiCommandListener: com.pocketcraft.server.broadcast.DashboardCommandListener? = null
    private var uiHeartbeatJob: kotlinx.coroutines.Job? = null

    override fun onStart() {
        super.onStart()
        isAppInForeground = true
        com.pocketcraft.server.broadcast.RemoteCommandListener.startListening(this)

        if (uiCommandListener == null) {
            uiCommandListener = com.pocketcraft.server.broadcast.DashboardCommandListener(
                context = this,
                scope = lifecycleScope,
                isMainProcess = true
            )
            uiCommandListener?.start()
        }

        val prefs = AppPreferences(this)
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: prefs.firebaseUserUid
        if (uid != null) {
            uiHeartbeatJob = lifecycleScope.launch(Dispatchers.IO) {
                while (kotlinx.coroutines.isActive) {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val secret = prefs.dashboardSecret ?: ""
                    if (!com.pocketcraft.server.server.ServerHostService.isServiceRunning) {
                        try {
                            val statusDoc = mapOf(
                                "serverRunning" to false,
                                "lastSeen" to com.google.firebase.Timestamp.now(),
                                "secret" to secret
                            )
                            db.collection("users").document(uid)
                                .collection("dashboard_status").document("status")
                                .set(statusDoc, com.google.firebase.firestore.SetOptions.merge())
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                    kotlinx.coroutines.delay(10000L)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        isAppInForeground = false
        com.pocketcraft.server.broadcast.RemoteCommandListener.stopListening()

        uiCommandListener?.stop()
        uiCommandListener = null

        uiHeartbeatJob?.cancel()
        uiHeartbeatJob = null
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
        if (currentUser != null) {
            var secret = preferences.dashboardSecret
            if (secret.isNullOrBlank()) {
                secret = java.util.UUID.randomUUID().toString()
                preferences.dashboardSecret = secret
            }
            try {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUser.uid)
                    .set(mapOf("dashboardSecret" to secret), com.google.firebase.firestore.SetOptions.merge())
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to upload dashboardSecret: ${e.message}")
            }
        }

        val onboardingCompleted = preferences.onboardingCompleted
        preferences.recordAppLaunch()
        ThemePreferenceStore.loadCustomColors(this)
        val initialThemePreference = ThemePreferenceStore.load(this)
        val initialMobTheme = ThemePreferenceStore.loadMobTheme(this)
        val initialDarkTheme = initialThemePreference.resolve(systemDark = ThemePreferenceStore.isSystemDark(this))
        PocketColors.activeMobTheme = initialMobTheme
        PocketColors.isDark = initialDarkTheme

        val splashBackgroundColor = PocketColors.FooterBg.toArgb()
        val initialSystemBarColor = splashBackgroundColor
        val initialNavBarColor = splashBackgroundColor
        val initialLightSystemBars = false
        val initialLightNavBar = false
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
            val billingManager = remember { com.pocketcraft.server.billing.BillingManager.getInstance(this@MainActivity) }
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
                com.pocketcraft.server.config.RemoteConfigManager.initialize(applicationContext)
                remoteConfigInitialized = true
            }

            val currentVersionCode = com.pocketcraft.server.BuildConfig.VERSION_CODE

            LaunchedEffect(remoteConfigInitialized) {
                if (remoteConfigInitialized) {
                    val minSupported = com.pocketcraft.server.config.FeatureGate.getMinSupportedVersionCode()
                    val recommended = com.pocketcraft.server.config.FeatureGate.getRecommendedVersionCode()
                    Log.d("MainActivity", "RemoteConfig versions parsed: minSupported=$minSupported, recommended=$recommended, currentVersionCode=$currentVersionCode")
                    if (currentVersionCode < minSupported) {
                        rcUpdateConfig = UpdateConfig(
                            showUpdatePopup = true,
                            playStoreUrl = "market://details?id=com.pocketcraft.server",
                            versionCode = minSupported,
                            dismissKey = "rc:$minSupported",
                            isForced = true
                        )
                    } else if (currentVersionCode < recommended && dismissedRcUpdateKey != "rc:$recommended") {
                        rcUpdateConfig = UpdateConfig(
                            showUpdatePopup = true,
                            playStoreUrl = "market://details?id=com.pocketcraft.server",
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

            val appStrings = appStringsFor(AppPreferences(this@MainActivity).appLanguage)
            CompositionLocalProvider(LocalAppStrings provides appStrings) {
            PocketCraftTheme(darkTheme = darkTheme, mobTheme = mobTheme) {
                var jreReady by remember { mutableStateOf(initialJreReady) }
                var jreError by remember { mutableStateOf<String?>(null) }
                var jreProgress by remember { mutableStateOf(0) }
                var jreStatus by remember { mutableStateOf("Preparing Minecraft Runtime...") }

                SideEffect {
                    val onSplash = jreError == null && !jreReady
                    val splashBackgroundColor = PocketColors.FooterBg.toArgb()
                    val statusBarColor = if (onSplash) {
                        splashBackgroundColor
                    } else {
                        PocketColors.BgApp.toArgb()
                    }
                    val navBarColor = if (onSplash) {
                        splashBackgroundColor
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
                        isAppearanceLightStatusBars = if (onSplash) false else androidx.compose.ui.graphics.Color(statusBarColor).luminance() >= 0.5f
                        isAppearanceLightNavigationBars = if (onSplash) false else androidx.compose.ui.graphics.Color(navBarColor).luminance() >= 0.5f
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
                    else -> PocketCraftApp(
                        isDarkTheme = darkTheme,
                        isPlayStoreRatingPromptEnabled = playStoreRatingPromptEnabled,
                        currentMobTheme = mobTheme,
                        onDarkThemeChange = { enabled ->
                            val nextPreference = if (enabled) ThemePreference.DARK else ThemePreference.LIGHT
                            if (themePreference != nextPreference) {
                                PocketColors.isDark = enabled
                                themePreference = nextPreference
                                ThemePreferenceStore.save(this@MainActivity, nextPreference)
                                com.pocketcraft.server.server.ServerHostService.pushWidgetUpdate(this@MainActivity)
                                FirebaseAnalyticsManager.logThemeChanged(nextPreference.name.lowercase())
                            }
                        },
                        onMobThemeChange = { nextTheme: MobTheme ->
                            if (mobTheme != nextTheme) {
                                PocketColors.activeMobTheme = nextTheme
                                mobTheme = nextTheme
                                ThemePreferenceStore.saveMobTheme(this@MainActivity, nextTheme)
                                com.pocketcraft.server.server.ServerHostService.pushWidgetUpdate(this@MainActivity)
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
        val currentVersion = com.pocketcraft.server.BuildConfig.VERSION_CODE.toString()

        if (!com.pocketcraft.server.config.FeatureGate.isReviewPromptEnabled()) {
            Log.d("MainActivity", "In-app review prompt is disabled by Remote Config.")
            return
        }

        val starts = preferences.successfulServerStarts
        val minStarts = com.pocketcraft.server.config.FeatureGate.getReviewPromptMinStarts()
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
