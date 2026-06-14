package com.pocketcraft.server

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.server.BundledPluginInstaller
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
open class PocketCraftApp : Application(), Configuration.Provider {

    companion object {
        val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        AppPreferences.init(this)
        runCatching {
            FirebaseApp.initializeApp(this)
            Firebase.crashlytics.setCrashlyticsCollectionEnabled(false)
            Firebase.crashlytics.setCustomKey("app_process", currentProcessName())
            Firebase.crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "pocketcraft_broadcast",
                "PocketCraft Announcements",
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            packageName
        }
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
            AppPreferences(applicationContext).migrateLegacyRelayHostForRegion()
        }.onFailure { error ->
            Firebase.crashlytics.recordException(error)
        }
        applicationScope.launch {
            runCatching {
                AppPreferencesStore.migrateLegacyRelayHostForRegion(applicationContext)
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

class PocketCraftApplication : PocketCraftApp()
