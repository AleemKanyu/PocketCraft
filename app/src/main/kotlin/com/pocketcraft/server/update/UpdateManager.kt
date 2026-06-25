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
    val latestVersion: String? = null,
    val dismissKey: String? = null,
    val isForced: Boolean = false,
    val enablePlayStoreRatingPrompt: Boolean = true
)

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val TIMESTAMP_VERSION_CODE_THRESHOLD = 10_000_000
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
        val excludeVersionCodeRaw = document.get("excludeVersionCode")
        val latestVersionRaw = document.get("latestVersion")
        val latestVersionCodeRaw = document.get("latestVersionCode")

        val showUpdatePopup = showUpdatePopupRaw.toBooleanOrNull() ?: false
        val isForced = isForcedRaw.toBooleanOrNull() ?: false
        val playStoreUrl = playStoreUrlRaw?.toString()?.trim() ?: ""
        val versionCode = versionCodeRaw.toFirestoreIntOrNull()
        val enablePlayStoreRatingPrompt = enablePlayStoreRatingPromptRaw.toBooleanOrNull() ?: true
        val excludeVersionCode = excludeVersionCodeRaw.toFirestoreIntOrNull()
        val latestVersion = latestVersionRaw?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val latestVersionCode = latestVersionCodeRaw.toFirestoreIntOrNull()

        Log.d(TAG, "Fetched app_config/update values: showUpdatePopupRaw=$showUpdatePopupRaw, isForcedRaw=$isForcedRaw, playStoreUrlRaw=$playStoreUrlRaw, versionCodeRaw=$versionCodeRaw, enablePlayStoreRatingPromptRaw=$enablePlayStoreRatingPromptRaw, excludeVersionCodeRaw=$excludeVersionCodeRaw, latestVersionRaw=$latestVersionRaw, latestVersionCodeRaw=$latestVersionCodeRaw")

        if (!showUpdatePopup) {
            Log.d(TAG, "Update config parsed: showUpdatePopup is false/null. Skipping update popup.")
            return UpdateConfig(enablePlayStoreRatingPrompt = enablePlayStoreRatingPrompt)
        }

        // Check version condition
        val currentVersionCode = runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.longVersionCode.toInt()
        }.getOrDefault(0)
        val currentVersionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")

        if (excludeVersionCode != null && currentVersionCode == excludeVersionCode) {
            Log.d(TAG, "Update config parsed: currentVersionCode ($currentVersionCode) == excludeVersionCode ($excludeVersionCode). Skipping update popup.")
            return UpdateConfig(enablePlayStoreRatingPrompt = enablePlayStoreRatingPrompt)
        }

        val isUpdateAvailable = isUpdateAvailable(
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode,
            latestVersion = latestVersion,
            latestVersionCode = latestVersionCode,
            minimumVersionCode = versionCode
        )

        if (!isUpdateAvailable) {
            Log.d(TAG, "Update config parsed: No update available (current: $currentVersionName / $currentVersionCode). Skipping update popup.")
            return UpdateConfig(enablePlayStoreRatingPrompt = enablePlayStoreRatingPrompt)
        }

        if (playStoreUrl.isBlank()) {
            // Treat blank URL as invalid data and skip, or use a default
            Log.w(TAG, "Update config parsed: playStoreUrl is empty/blank (invalid data). Skipping update popup.")
            return UpdateConfig(enablePlayStoreRatingPrompt = enablePlayStoreRatingPrompt)
        }

        val shouldForce = isForced && isBelowMinimumSupportedVersion(
            currentVersionCode = currentVersionCode,
            minimumVersionCode = versionCode
        )

        Log.d(TAG, "Update config decision: SHOW popup. isForced=$shouldForce, playStoreUrl='$playStoreUrl', targetVersionCode=$versionCode")
        return UpdateConfig(
            showUpdatePopup = showUpdatePopup,
            playStoreUrl = playStoreUrl,
            versionCode = versionCode,
            latestVersion = latestVersion,
            dismissKey = latestVersion ?: latestVersionCode?.toString() ?: versionCode?.toString(),
            isForced = shouldForce,
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

    private fun compareVersionNames(current: String, target: String): Int {
        val currentParts = normalizeVersionName(current).split('.', '-', '_').filter { it.isNotBlank() }
        val targetParts = normalizeVersionName(target).split('.', '-', '_').filter { it.isNotBlank() }
        val maxParts = maxOf(currentParts.size, targetParts.size)

        for (index in 0 until maxParts) {
            val currentPart = currentParts.getOrNull(index).orEmpty()
            val targetPart = targetParts.getOrNull(index).orEmpty()
            val numericCompare = compareVersionPart(currentPart, targetPart)
            if (numericCompare != 0) return numericCompare
        }
        return 0
    }

    private fun compareVersionPart(currentPart: String, targetPart: String): Int {
        val currentNumber = currentPart.parseVersionNumberOrNull()
        val targetNumber = targetPart.parseVersionNumberOrNull()
        return when {
            currentNumber != null && targetNumber != null -> currentNumber.compareTo(targetNumber)
            else -> currentPart.compareTo(targetPart)
        }
    }

    private fun String.parseVersionNumberOrNull(): Int? = trim().toIntOrNull()

    private fun normalizeVersionName(versionName: String): String {
        return versionName.trim().removePrefix("v").removePrefix("V")
    }

    private fun isUpdateAvailable(
        currentVersionName: String,
        currentVersionCode: Int,
        latestVersion: String?,
        latestVersionCode: Int?,
        minimumVersionCode: Int?
    ): Boolean {
        if (!latestVersion.isNullOrBlank()) {
            when (compareVersionNames(currentVersionName, latestVersion)) {
                -1 -> return true
                1 -> return false
            }
        }

        if (isComparableVersionCode(currentVersionCode, latestVersionCode) && latestVersionCode != null) {
            return currentVersionCode < latestVersionCode
        }

        if (minimumVersionCode == null) {
            return false
        }

        return isBelowMinimumSupportedVersion(
            currentVersionCode = currentVersionCode,
            minimumVersionCode = minimumVersionCode
        )
    }

    private fun isBelowMinimumSupportedVersion(
        currentVersionCode: Int,
        minimumVersionCode: Int?
    ): Boolean {
        if (minimumVersionCode == null) return false
        if (!isComparableVersionCode(currentVersionCode, minimumVersionCode)) return false
        return currentVersionCode < minimumVersionCode
    }

    private fun isComparableVersionCode(currentVersionCode: Int, remoteVersionCode: Int?): Boolean {
        if (remoteVersionCode == null) return false
        return !(isTimestampVersionCode(currentVersionCode) && !isTimestampVersionCode(remoteVersionCode))
    }

    private fun isTimestampVersionCode(code: Int): Boolean = code >= TIMESTAMP_VERSION_CODE_THRESHOLD

    private fun Any?.toFirestoreIntOrNull(): Int? {
        if (this == null) return null
        if (this is Number) return this.toInt()
        if (this is String) return this.trim().toIntOrNull()
        return null
    }
}
