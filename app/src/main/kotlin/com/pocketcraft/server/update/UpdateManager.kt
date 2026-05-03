package com.pocketcraft.server.update

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class UpdateConfig(
    val showUpdatePopup: Boolean = false,
    val playStoreUrl: String = "",
    val versionCode: Int? = null,
    val isForced: Boolean = false
)

object UpdateManager {
    private const val TAG = "UpdateManager"
    private val db = FirebaseFirestore.getInstance()

    suspend fun fetchUpdateConfig(): UpdateConfig? {
        return try {
            val document = db.collection("app_config").document("update").get().await()
            if (document.exists()) {
                val showUpdatePopup = document.getBoolean("showUpdatePopup") ?: false
                val playStoreUrl = document.getString("playStoreUrl") ?: ""
                val versionCode = document.getLong("versionCode")?.toInt()
                val isForced = document.getBoolean("isForced") ?: false

                if (playStoreUrl.isBlank()) {
                    Log.w(TAG, "playStoreUrl is empty, skipping update popup.")
                    return null
                }

                UpdateConfig(
                    showUpdatePopup = showUpdatePopup,
                    playStoreUrl = playStoreUrl,
                    versionCode = versionCode,
                    isForced = isForced
                )
            } else {
                Log.d(TAG, "Update config document does not exist.")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch update config: ${e.message}")
            null
        }
    }
}
