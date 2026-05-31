package com.pocketcraft.server

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import android.content.Intent
import android.util.Log
import com.pocketcraft.server.update.UpdateConfig
import com.pocketcraft.server.update.UpdateManager
import com.pocketcraft.server.ui.components.UpdatePopup
import androidx.compose.runtime.CompositionLocalProvider
import com.pocketcraft.server.util.LocalAppStrings
import com.pocketcraft.server.util.appStringsFor

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
    }

    override fun onStart() {
        super.onStart()
        isAppInForeground = true
    }

    override fun onStop() {
        super.onStop()
        isAppInForeground = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = AppPreferences(this)
        val onboardingCompleted = preferences.onboardingCompleted
        preferences.recordAppLaunch()
        val initialThemePreference = ThemePreferenceStore.load(this)
        val initialDarkTheme = initialThemePreference.resolve(systemDark = ThemePreferenceStore.isSystemDark(this))

        window.statusBarColor = if (initialDarkTheme) PocketColors.BgDark.toArgb() else Color.parseColor("#F5F7F3")
        window.navigationBarColor = if (initialDarkTheme) PocketColors.SurfaceDark.toArgb() else PocketColors.Primary.toArgb()
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !initialDarkTheme
            isAppearanceLightNavigationBars = !initialDarkTheme
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
                Firebase.crashlytics.setCrashlyticsCollectionEnabled(crashDiagnosticsConsent)
            }
        }

        setContent {
            var themePreference by remember { mutableStateOf(initialThemePreference) }
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = themePreference.resolve(systemDark = systemDarkTheme)

            var updateConfig by remember { mutableStateOf<UpdateConfig?>(null) }
            var dismissedUpdateVersion by remember { mutableStateOf<Int?>(null) }
            var dismissedUpdateShowFlag by remember { mutableStateOf(false) }

            val updateConfigFlow = remember { UpdateManager.getUpdateConfigFlow(this@MainActivity) }
            LaunchedEffect(updateConfigFlow) {
                updateConfigFlow.collect { config ->
                    Log.d("MainActivity", "Received update config from flow: $config")
                    if (config != null && config.showUpdatePopup) {
                        val alreadyDismissed = !config.isForced && 
                            dismissedUpdateShowFlag && 
                            dismissedUpdateVersion == config.versionCode
                        
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

            val appStrings = appStringsFor(AppPreferences(this@MainActivity).appLanguage)
            CompositionLocalProvider(LocalAppStrings provides appStrings) {
            PocketCraftTheme(darkTheme = darkTheme) {
                var jreReady by remember { mutableStateOf(false) }
                var jreError by remember { mutableStateOf<String?>(null) }
                var jreProgress by remember { mutableStateOf(0) }
                var jreStatus by remember { mutableStateOf("Preparing Minecraft Runtime...") }

                SideEffect {
                    val showingSplash = jreError == null && (!jreReady || !onboardingCompleted)
                    val statusBarColor = when {
                        showingSplash && darkTheme -> PocketColors.BgDark.toArgb()
                        showingSplash -> Color.parseColor("#F5F7F3")
                        darkTheme -> PocketColors.BgDark.toArgb()
                        else -> Color.parseColor("#F5F7F3")
                    }
                    val navigationBarColor = when {
                        showingSplash && darkTheme -> PocketColors.BgDark.toArgb()
                        showingSplash -> Color.parseColor("#E6F1DF")
                        darkTheme -> PocketColors.Primary.toArgb()
                        else -> PocketColors.Primary.toArgb()
                    }
                    window.statusBarColor = statusBarColor
                    window.navigationBarColor = navigationBarColor
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
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
                        onDarkThemeChange = { enabled ->
                            val nextPreference = if (enabled) ThemePreference.DARK else ThemePreference.LIGHT
                            if (themePreference != nextPreference) {
                                themePreference = nextPreference
                                ThemePreferenceStore.save(this@MainActivity, nextPreference)
                                FirebaseAnalyticsManager.logThemeChanged(nextPreference.name.lowercase())
                            }
                        }
                    )
                }

                updateConfig?.let { config ->
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
                            dismissedUpdateShowFlag = true
                            dismissedUpdateVersion = config.versionCode
                            updateConfig = null
                        }
                    )
                }
            }
            }
        }
    }
}
