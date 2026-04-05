package com.pocketcraft.server.broadcast

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PocketCraftMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        subscribeToAllUsers()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received: ${message.notification?.title.orEmpty()}")
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
