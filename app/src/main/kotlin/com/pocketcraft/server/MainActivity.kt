package com.pocketcraft.server

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pocketcraft.server.analytics.FirebaseAnalyticsManager
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.setup.JreExtractor
import com.pocketcraft.server.update.GitHubApkInstaller
import com.pocketcraft.server.update.GitHubUpdateChecker
import com.pocketcraft.server.ui.onboarding.OnboardingActivity
import com.pocketcraft.server.ui.screens.ErrorScreen
import com.pocketcraft.server.ui.screens.PocketCraftApp
import com.pocketcraft.server.ui.screens.SplashScreen
import com.pocketcraft.server.ui.theme.PocketCraftTheme
import com.pocketcraft.server.ui.theme.PocketColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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

        // Initialize Firebase Analytics
        runCatching {
            FirebaseAnalyticsManager.initialize(applicationContext)
            FirebaseAnalyticsManager.logEvent("app_open")
        }

        setContent {
            PocketCraftTheme {
                var jreReady by remember { mutableStateOf(false) }
                var jreError by remember { mutableStateOf<String?>(null) }
                var jreProgress by remember { mutableStateOf(0) }
                var jreStatus by remember { mutableStateOf("Preparing Minecraft Runtime...") }
                var availableUpdate by remember { mutableStateOf<GitHubUpdateChecker.ReleaseInfo?>(null) }
                var updatePromptDismissed by remember { mutableStateOf(false) }
                var isDownloadingUpdate by remember { mutableStateOf(false) }
                var updateDownloadProgress by remember { mutableStateOf(0) }
                var updateDownloadStatus by remember { mutableStateOf("Preparing update...") }
                var updateDownloadError by remember { mutableStateOf<String?>(null) }
                val updateScope = rememberCoroutineScope()

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

                LaunchedEffect(jreReady) {
                    if (!jreReady || !onboardingCompleted) return@LaunchedEffect

                    try {
                        // Don't check for updates on first app launch after onboarding completes
                        val isFirstLaunchAfterOnboarding = preferences.isFirstLaunchAfterOnboarding()
                        if (isFirstLaunchAfterOnboarding) {
                            preferences.setFirstLaunchAfterOnboarding(false)
                            return@LaunchedEffect
                        }

                        // Only check for updates twice per day (12 hours apart)
                        val lastCheckTime = preferences.getLastUpdateCheckTime()
                        val now = System.currentTimeMillis()
                        val timeSinceLastCheck = now - lastCheckTime
                        val TWELVE_HOURS = 12L * 60L * 60L * 1000L

                        if (timeSinceLastCheck >= TWELVE_HOURS) {
                            availableUpdate = withContext(Dispatchers.IO) {
                                GitHubUpdateChecker.checkForUpdate(applicationContext)
                            }
                            preferences.setLastUpdateCheckTime(now)
                        }
                    } catch (e: Exception) {
                        // Silently ignore update check failures
                        e.printStackTrace()
                    }
                }

                val update = availableUpdate
                if (update != null && !updatePromptDismissed && !isDownloadingUpdate) {
                    val forceUpdate = update.isForceUpdateDue()
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text(if (forceUpdate) "Update required" else "Update available") },
                        text = {
                            Text(
                                if (forceUpdate) {
                                    "You are on ${BuildConfig.VERSION_NAME}. Release ${update.releaseName} has been out for more than 3 days, so PocketCraft needs to be updated before you can continue."
                                } else {
                                    "You are on ${BuildConfig.VERSION_NAME}. A newer release (${update.releaseName}) is available. You can keep using PocketCraft for now, but updating is recommended."
                                }
                            )
                        },
                        confirmButton = {
                            OutlinedButton(
                                onClick = {
                                    val apkUrl = update.downloadUrl
                                    if (apkUrl.isNullOrBlank()) {
                                        updateDownloadError = "No APK asset was attached to this release."
                                        return@OutlinedButton
                                    }

                                    updateScope.launch {
                                        try {
                                            isDownloadingUpdate = true
                                            updateDownloadError = null
                                            updateDownloadStatus = "Downloading update..."
                                            updateDownloadProgress = 0

                                            val downloadResult = GitHubApkInstaller.downloadApk(
                                                context = applicationContext,
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

                                            if (!GitHubApkInstaller.canRequestPackageInstalls(applicationContext)) {
                                                isDownloadingUpdate = false
                                                updateDownloadError = "Allow 'Install unknown apps' for PocketCraft, then tap Install update again."
                                                startActivity(GitHubApkInstaller.buildUnknownAppsSettingsIntent(applicationContext))
                                                return@launch
                                            }

                                            isDownloadingUpdate = false
                                            startActivity(GitHubApkInstaller.buildInstallIntent(applicationContext, downloadResult))
                                            if (forceUpdate) {
                                                finishAffinity()
                                            }
                                        } catch (error: Throwable) {
                                            isDownloadingUpdate = false
                                            updateDownloadError = error.message ?: "Could not download update."
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(2.dp, PocketColors.Primary),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PocketColors.Primary)
                            ) {
                                Text(if (forceUpdate) "Update now" else "Install update", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            if (!forceUpdate) {
                                TextButton(onClick = { updatePromptDismissed = true }) {
                                    Text("Later", color = PocketColors.Primary)
                                }
                            }
                        }
                    )
                }

                if (isDownloadingUpdate) {
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text("Downloading update") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(updateDownloadStatus)
                                LinearProgressIndicator(
                                    progress = { (updateDownloadProgress / 100f).coerceIn(0f, 1f) },
                                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                                )
                                Text("${updateDownloadProgress}%")
                            }
                        },
                        confirmButton = {}
                    )
                }

                if (updateDownloadError != null) {
                    AlertDialog(
                        onDismissRequest = { updateDownloadError = null },
                        title = { Text("Update error") },
                        text = { Text(updateDownloadError ?: "Unknown error") },
                        confirmButton = {
                            TextButton(onClick = { updateDownloadError = null }) {
                                Text("OK")
                            }
                        }
                    )
                }
            }
        }
    }
}
