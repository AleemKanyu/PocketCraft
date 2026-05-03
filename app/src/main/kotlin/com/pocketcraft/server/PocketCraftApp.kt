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
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
            Firebase.crashlytics.setCrashlyticsCollectionEnabled(true)
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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

class PocketCraftApplication : PocketCraftApp()
