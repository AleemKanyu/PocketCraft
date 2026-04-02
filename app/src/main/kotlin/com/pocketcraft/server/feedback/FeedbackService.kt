package com.pocketcraft.server.feedback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import com.pocketcraft.server.BuildConfig
import com.pocketcraft.server.data.preferences.AppPreferences
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import java.util.concurrent.TimeoutException

object FeedbackService {

    private const val FEEDBACK_COLLECTION = "beta_feedback"
    private const val DISCORD_WEB_URL = "https://discord.gg/NGPzXFYp"
    private const val DISCORD_APP_URL = "discord://invite/NGPzXFYp"
    private const val INSTAGRAM_WEB_URL = "https://www.instagram.com/pocketcraftmc?igsh=NTRnZGI4MHFuYXd3&utm_source=qr"
    private const val INSTAGRAM_APP_URL = "instagram://user?username=pocketcraftmc"

    suspend fun submitFeedback(context: Context, message: String, serverVersion: String): Result<Unit> {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Feedback cannot be empty."))

        return runCatching {
            val prefs = AppPreferences(context)
            val payload = hashMapOf(
                "message" to trimmed,
                "userId" to prefs.userId,
                "appVersion" to BuildConfig.VERSION_NAME,
                "appVersionCode" to BuildConfig.VERSION_CODE,
                "serverVersion" to serverVersion,
                "deviceManufacturer" to Build.MANUFACTURER,
                "deviceModel" to Build.MODEL,
                "androidSdk" to Build.VERSION.SDK_INT,
                "createdAt" to FieldValue.serverTimestamp()
            )

            val result = withTimeoutOrNull(10.seconds) {
                Firebase.firestore
                    .collection(FEEDBACK_COLLECTION)
                    .add(payload)
                    .awaitTask()
            }

            if (result == null) {
                throw TimeoutException("Feedback submission timed out after 10 seconds")
            }
        }
    }

    fun openDiscord(context: Context): Boolean {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_APP_URL)).apply {
            setPackage("com.discord")
        }
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_WEB_URL))

        return runCatching {
            when {
                appIntent.resolveActivity(context.packageManager) != null -> {
                    context.startActivity(appIntent)
                    true
                }
                webIntent.resolveActivity(context.packageManager) != null -> {
                    context.startActivity(webIntent)
                    true
                }
                else -> false
            }
        }.getOrDefault(false)
    }

    fun openInstagram(context: Context): Boolean {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(INSTAGRAM_APP_URL)).apply {
            setPackage("com.instagram.android")
        }
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(INSTAGRAM_WEB_URL))

        return runCatching {
            when {
                appIntent.resolveActivity(context.packageManager) != null -> {
                    context.startActivity(appIntent)
                    true
                }
                webIntent.resolveActivity(context.packageManager) != null -> {
                    context.startActivity(webIntent)
                    true
                }
                else -> false
            }
        }.getOrDefault(false)
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) continuation.resume(result)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
}
