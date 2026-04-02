package com.pocketcraft.server

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pocketcraft.server.analytics.FirebaseAnalyticsManager
import com.pocketcraft.server.setup.JreExtractor
import com.pocketcraft.server.ui.screens.ErrorScreen
import com.pocketcraft.server.ui.screens.PocketCraftApp
import com.pocketcraft.server.ui.screens.SplashScreen
import com.pocketcraft.server.ui.theme.PocketCraftTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Initialize Firebase Analytics
        runCatching {
            FirebaseAnalyticsManager.initialize(applicationContext)
        }

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }

        setContent {
            PocketCraftTheme {
                var jreReady by remember { mutableStateOf(false) }
                var jreError by remember { mutableStateOf<String?>(null) }
                var jreProgress by remember { mutableStateOf(0) }
                var jreStatus by remember { mutableStateOf("Preparing Minecraft Runtime...") }

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
                    else -> PocketCraftApp()
                }

                var showBatteryDialog by remember { mutableStateOf(false) }
                LaunchedEffect(jreReady) {
                    if (!jreReady) return@LaunchedEffect
                    val prefs = applicationContext.getSharedPreferences("app_relay_prefs", MODE_PRIVATE)
                    val prompted = prefs.getBoolean("battery_opt_prompted", false)
                    val pm = getSystemService(POWER_SERVICE) as PowerManager
                    if (!prompted && !pm.isIgnoringBatteryOptimizations(packageName)) {
                        showBatteryDialog = true
                    }
                }

                if (showBatteryDialog) {
                    AlertDialog(
                        onDismissRequest = { showBatteryDialog = false },
                        title = { Text("Allow background performance") },
                        text = {
                            Text(
                                "For best performance, allow PocketCraft to run in the background without battery restrictions.\n" +
                                    "This prevents Android from closing your server unexpectedly."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                requestIgnoreBatteryOptimization()
                                applicationContext.getSharedPreferences("app_relay_prefs", MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("battery_opt_prompted", true)
                                    .apply()
                                showBatteryDialog = false
                            }) { Text("Allow") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                applicationContext.getSharedPreferences("app_relay_prefs", MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("battery_opt_prompted", true)
                                    .apply()
                                showBatteryDialog = false
                            }) { Text("Not now") }
                        }
                    )
                }
            }
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            runCatching { startActivity(intent) }
        }
    }
}
