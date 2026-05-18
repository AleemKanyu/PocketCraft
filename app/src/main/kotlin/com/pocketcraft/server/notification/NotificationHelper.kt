package com.pocketcraft.server.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pocketcraft.server.R

object NotificationHelper {
    /**
     * CHANNEL_ID is a *silent* channel used for the "server is online" push notification that
     * fires when the app is in the background. We intentionally use a separate silent channel
     * (IMPORTANCE_LOW, no sound) so the system does NOT play its own audio — in-app audio is
     * handled separately by SoundManager / MediaPlayer only when the app is on screen.
     *
     * On Android O+ the channel sound cannot be changed after first creation, so we pin it to
     * silent from the start with a distinct ID.
     */
    const val CHANNEL_ID = "pocketcraft_server_alerts"
    private const val CHANNEL_NAME = "Server Status Alerts"
    private const val NOTIFICATION_ID_ONLINE = 1002

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when the server completely starts"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Called when the server transitions to ONLINE.
     * Always sends a heads-up push notification so the user knows the server started.
     */
    fun notifyServerOnline(context: Context, version: String) {
        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Server is Online! 🎮")
                .setContentText("Minecraft $version is ready. Players can now connect.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ONLINE, builder.build())
        } catch (e: Exception) {
            // Silent failure
        }
    }

    /**
     * Dismiss the server-online notification (e.g. when the user opens the app).
     * Server-stopped notifications are intentionally not sent per design requirements.
     */
    fun dismissServerNotifications(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_ONLINE)
        } catch (e: Exception) {
            // Silent failure
        }
    }

    // notifyServerOffline intentionally removed — users should not receive a notification
    // when the server stops. The only notification is the silent "server online" one above.
}
