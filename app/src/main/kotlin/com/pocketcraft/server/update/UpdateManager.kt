package com.pocketcraft.server.update

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class UpdateConfig(
    val showUpdatePopup: Boolean = false,
    val playStoreUrl: String = "",
    val versionCode: Int? = null,
    val isForced: Boolean = false,
    val enablePlayStoreRatingPrompt: Boolean = true
)

object UpdateManager {
    private const val TAG = "UpdateManager"
    private val db by lazy { FirebaseFirestore.getInstance() }

    fun getUpdateConfigFlow(context: Context): Flow<UpdateConfig?> = callbackFlow {
        val listener = db.collection("app_config").document("update")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Update config snapshot listener error: ${error.message}", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val config = parseUpdateConfig(context, snapshot)
                    trySend(config)
                } else {
                    Log.d(TAG, "Update config document does not exist.")
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun fetchUpdateConfig(context: Context): UpdateConfig? {
        return try {
            val document = db.collection("app_config").document("update").get().await()
            if (document.exists()) {
                parseUpdateConfig(context, document)
            } else {
                Log.d(TAG, "Update config document does not exist (fetched).")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch update config: ${e.message}", e)
            null
        }
    }

    private fun parseUpdateConfig(context: Context, document: DocumentSnapshot): UpdateConfig? {
        val showUpdatePopupRaw = document.get("showUpdatePopup")
        val isForcedRaw = document.get("isForced")
        val playStoreUrlRaw = document.get("playStoreUrl")
        val versionCodeRaw = document.get("versionCode")
        val enablePlayStoreRatingPromptRaw = document.get("enablePlayStoreRatingPrompt")

        val showUpdatePopup = showUpdatePopupRaw.toBooleanOrNull() ?: false
        val isForced = isForcedRaw.toBooleanOrNull() ?: false
        val playStoreUrl = playStoreUrlRaw?.toString()?.trim() ?: ""
        val versionCode = versionCodeRaw.toIntOrNull()
        val enablePlayStoreRatingPrompt = enablePlayStoreRatingPromptRaw.toBooleanOrNull() ?: true

        Log.d(TAG, "Fetched app_config/update values: showUpdatePopupRaw=$showUpdatePopupRaw, isForcedRaw=$isForcedRaw, playStoreUrlRaw=$playStoreUrlRaw, versionCodeRaw=$versionCodeRaw, enablePlayStoreRatingPromptRaw=$enablePlayStoreRatingPromptRaw")

        if (!showUpdatePopup) {
            Log.d(TAG, "Update config parsed: showUpdatePopup is false/null. Skipping update popup.")
            return UpdateConfig(enablePlayStoreRatingPrompt = enablePlayStoreRatingPrompt)
        }

        // Check version condition
        val currentVersionCode = runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.longVersionCode.toInt()
        }.getOrDefault(0)

        if (versionCode != null && currentVersionCode >= versionCode) {
            Log.d(TAG, "Update config parsed: currentVersionCode ($currentVersionCode) >= target versionCode ($versionCode). Skipping update popup.")
            return UpdateConfig(enablePlayStoreRatingPrompt = enablePlayStoreRatingPrompt)
        }

        if (playStoreUrl.isBlank()) {
            // Treat blank URL as invalid data and skip, or use a default
            Log.w(TAG, "Update config parsed: playStoreUrl is empty/blank (invalid data). Skipping update popup.")
            return UpdateConfig(enablePlayStoreRatingPrompt = enablePlayStoreRatingPrompt)
        }

        Log.d(TAG, "Update config decision: SHOW popup. isForced=$isForced, playStoreUrl='$playStoreUrl', targetVersionCode=$versionCode")
        return UpdateConfig(
            showUpdatePopup = showUpdatePopup,
            playStoreUrl = playStoreUrl,
            versionCode = versionCode,
            isForced = isForced,
            enablePlayStoreRatingPrompt = enablePlayStoreRatingPrompt
        )
    }

    private fun Any?.toBooleanOrNull(): Boolean? {
        if (this == null) return null
        if (this is Boolean) return this
        if (this is Number) return this.toInt() != 0
        if (this is String) {
            return when (this.trim().lowercase()) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> null
            }
        }
        return null
    }

    private fun Any?.toIntOrNull(): Int? {
        if (this == null) return null
        if (this is Number) return this.toInt()
        if (this is String) return this.trim().toIntOrNull()
        return null
    }
}
