package com.pockethost.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import android.util.Log
import com.pockethost.app.data.preferences.AppPreferences
import com.pockethost.app.data.preferences.AppPreferencesStore
import com.pockethost.app.server.BundledPluginInstaller
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
open class PocketHostApp : Application(), Configuration.Provider {

    companion object {
        val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        AppPreferences.init(this)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = Log.getStackTraceString(throwable)
            if (stackTrace.contains("LegacyCursorAnchorInfoController") || 
                stackTrace.contains("updateCursorAnchorInfo") ||
                (throwable is NullPointerException && stackTrace.contains("androidx.compose.foundation.text"))) {
                Log.e("PocketHostApp", "Caught Compose IME cursor framework NullPointerException, preventing crash.", throwable)
                runCatching { Firebase.crashlytics.recordException(throwable) }
            } else {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        val processName = currentProcessName()
        val isMainProcess = processName == packageName
        val isServerProcess = processName == "$packageName:server"

        if (!isMainProcess && !isServerProcess) {
            // Unknown secondary process — skip all initialization.
            return
        }

        // Initialize FirebaseApp once for all valid processes.
        runCatching {
            FirebaseApp.initializeApp(this)
        }

        if (isServerProcess) {
            // Disable Firestore disk persistence in the background process to prevent database locks/crashes
            runCatching {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(false)
                    .build()
                db.firestoreSettings = settings
            }
            // Enable Crashlytics in the background process for release builds to capture JNI/Hotspot crashes
            runCatching {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().apply {
                    setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
                    setCustomKey("app_process", processName)
                    setCustomKey("app_version", BuildConfig.VERSION_NAME)
                }
            }
            return
        }

        runCatching {
            // Configure and enable Crashlytics for the main process in release builds
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().apply {
                setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
                setCustomKey("app_process", processName)
                setCustomKey("app_version", BuildConfig.VERSION_NAME)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "pocketcraft_broadcast",
                "PocketHost Announcements",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Server updates and announcements from PocketCraft"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        migrateRelayHostForRegion()
        reinstallBundledPluginsAfterAppUpdate()
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }

    private fun currentProcessName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        // Fallback for API < 28
        return runCatching {
            File("/proc/self/cmdline").readText().trim('\u0000')
        }.getOrNull()?.trim() ?: packageName
    }

    private fun reinstallBundledPluginsAfterAppUpdate() {
        val prefs = getSharedPreferences("pocketcraft_prefs", MODE_PRIVATE)
        val lastInstalledVersion = prefs.getInt("last_plugin_install_version", 0)
        val currentVersion = BuildConfig.VERSION_CODE
        if (currentVersion <= lastInstalledVersion) return

        runCatching {
            val worldsDir = File(filesDir, "servers/worlds")
            worldsDir.listFiles()
                .orEmpty()
                .filter { it.isDirectory }
                .forEach { worldDir ->
                    BundledPluginInstaller.forceReinstallBundledPlugins(applicationContext, worldDir)
                }
        }.onFailure { error ->
            Firebase.crashlytics.recordException(error)
        }

        prefs.edit().putInt("last_plugin_install_version", currentVersion).apply()
    }

    private fun migrateRelayHostForRegion() {
        runCatching {
            val prefs = AppPreferences(applicationContext)
            prefs.migrateLegacyRelayHostForRegion()
            prefs.migrateSingaporeRelayRemoval()
        }.onFailure { error ->
            Firebase.crashlytics.recordException(error)
        }
        applicationScope.launch {
            runCatching {
                AppPreferencesStore.migrateLegacyRelayHostForRegion(applicationContext)
                AppPreferencesStore.migrateSingaporeRelayRemoval(applicationContext)
            }.onFailure { error ->
                Firebase.crashlytics.recordException(error)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

class PocketHostApplication : PocketHostApp()
