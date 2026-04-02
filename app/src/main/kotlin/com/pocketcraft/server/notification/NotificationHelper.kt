package com.pocketcraft.server.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pocketcraft.server.R

object NotificationHelper {
    const val CHANNEL_ID = "pocketcraft_server"
    private const val CHANNEL_NAME = "Server Status"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when server starts or stops"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun notifyServerOnline(context: Context, version: String) {
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)  // Fallback - you may want a custom icon
                .setContentTitle("✅ Server is Online!")
                .setContentText("Minecraft $version is ready. Players can now connect.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(1001, notification)
        } catch (e: Exception) {
            // Silent failure - notification not critical
        }
    }

    fun notifyServerOffline(context: Context) {
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)  // Fallback - you may want a custom icon
                .setContentTitle("Server Stopped")
                .setContentText("Your PocketCraft server has stopped.")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(1002, notification)
        } catch (e: Exception) {
            // Silent failure - notification not critical
        }
    }

    fun dismissServerNotifications(context: Context) {
        try {
            val manager = NotificationManagerCompat.from(context)
            manager.cancel(1001)
            manager.cancel(1002)
        } catch (e: Exception) {
            // Silent failure
        }
    }
}
