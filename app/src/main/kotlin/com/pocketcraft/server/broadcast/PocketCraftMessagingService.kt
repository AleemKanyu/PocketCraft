package com.pocketcraft.server.broadcast

import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.util.Log
import com.pocketcraft.server.R
import com.pocketcraft.server.data.preferences.AppPreferences
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PocketCraftMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        AppPreferences(this).fcmToken = token
        subscribeToAllUsers()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received: ${message.notification?.title.orEmpty()}")
        if (handleFeedbackPrompt(message)) {
            return
        }
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "PocketCraft"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return
        showBroadcastNotification(title, body)
    }

    private fun showBroadcastNotification(title: String, body: String) {
        val notification = NotificationCompat.Builder(this, "pocketcraft_broadcast")
            .setSmallIcon(R.drawable.ic_launcher_foreground_circle)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(this)
                .notify(System.currentTimeMillis().toInt(), notification)
        }.onFailure {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(System.currentTimeMillis().toInt(), notification)
        }
    }

    private fun handleFeedbackPrompt(message: RemoteMessage): Boolean {
        val popupType = message.data["popup_type"]?.trim()?.lowercase() ?: return false
        if (popupType != "feedback_prompt") return false

        val title = message.data["title"]?.trim().orEmpty().ifBlank { "Help improve PocketCraft" }
        val body = message.data["body"]?.trim().orEmpty().ifBlank {
            "Tell us what is working well and what we should fix next."
        }
        val cta = message.data["cta_label"]?.trim().orEmpty().ifBlank { "Send feedback" }
        val prompt = FeedbackPromptPayload(title = title, body = body, ctaLabel = cta)
        val prefs = AppPreferences(this)
        prefs.pendingFeedbackPromptTitle = prompt.title
        prefs.pendingFeedbackPromptBody = prompt.body
        prefs.pendingFeedbackPromptCta = prompt.ctaLabel
        FeedbackPromptCenter.show(prompt)
        Log.d(TAG, "Queued feedback prompt bottom sheet")
        return true
    }

    companion object {
        private const val TAG = "PocketCraftFCM"

        fun subscribeToAllUsers() {
            FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                .addOnCompleteListener { task ->
                    Log.d(TAG, if (task.isSuccessful) "Subscribed to all_users" else "all_users subscribe failed")
                }
        }
    }
}
