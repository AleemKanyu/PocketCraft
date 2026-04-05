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
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
open class PocketCraftApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
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
