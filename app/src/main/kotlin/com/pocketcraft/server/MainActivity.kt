package com.pocketcraft.server

import android.media.MediaPlayer
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pocketcraft.server.analytics.FirebaseAnalyticsManager
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.setup.JreExtractor
import com.pocketcraft.server.ui.onboarding.OnboardingActivity
import com.pocketcraft.server.ui.screens.ErrorScreen
import com.pocketcraft.server.ui.screens.PocketCraftApp
import com.pocketcraft.server.ui.screens.SplashScreen
import com.pocketcraft.server.ui.theme.PocketCraftTheme
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private fun playStartupSound() {
        runCatching {
            val player = MediaPlayer.create(this, R.raw.startup_chime) ?: return
            player.setOnCompletionListener { it.release() }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                true
            }
            player.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = AppPreferences(this)
        val onboardingCompleted = preferences.onboardingCompleted
        preferences.recordAppLaunch()

        window.statusBarColor = Color.parseColor("#F5F7F3")
        window.navigationBarColor = Color.parseColor("#F5F7F3")
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
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
            PocketCraftTheme {
                var jreReady by remember { mutableStateOf(false) }
                var jreError by remember { mutableStateOf<String?>(null) }
                var jreProgress by remember { mutableStateOf(0) }
                var jreStatus by remember { mutableStateOf("Preparing Minecraft Runtime...") }
                var startupSoundPlayed by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    try {
                        jreProgress = 0
                        jreStatus = "Preparing Minecraft Runtime..."
                        withContext(Dispatchers.IO) {
                            JreExtractor.extractIfNeeded(applicationContext) { percent, status ->
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

                LaunchedEffect(jreReady, onboardingCompleted) {
                    if (jreReady && onboardingCompleted && !startupSoundPlayed) {
                        startupSoundPlayed = true
                        playStartupSound()
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
                    else -> PocketCraftApp()
                }
            }
        }
    }
}
