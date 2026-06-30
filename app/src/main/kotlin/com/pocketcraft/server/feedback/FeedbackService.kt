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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds
import java.util.concurrent.TimeoutException

object FeedbackService {

    private const val FEEDBACK_COLLECTION = "beta_feedback"
    private const val DISCORD_WEB_URL = "https://discord.gg/7xw3Rd2vs2"
    private const val DISCORD_APP_URL = "discord://invite/nc7ceYWVfT"
    private const val INSTAGRAM_WEB_URL = "https://www.instagram.com/pocketcraftmc?igsh=NTRnZGI4MHFuYXd3&utm_source=qr"
    private const val INSTAGRAM_APP_URL = "instagram://user?username=pocketcraftmc"
    private const val SOCIAL_PROMPTS_COLLECTION = "social_prompts"
    private const val FIELD_DISCORD_POPUP_SHOWN = "discordPopupShown"

    suspend fun submitFeedback(context: Context, message: String, serverVersion: String): Result<Unit> {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Feedback cannot be empty."))

        return runCatching {
            val prefs = AppPreferences(context)
            val logDump = createFeedbackLogDump(context, serverVersion)
            val payload = hashMapOf(
                "message" to trimmed,
                "userId" to prefs.userId,
                "appVersion" to BuildConfig.VERSION_NAME,
                "appVersionCode" to BuildConfig.VERSION_CODE,
                "serverVersion" to serverVersion,
                "deviceManufacturer" to Build.MANUFACTURER,
                "deviceModel" to Build.MODEL,
                "androidSdk" to Build.VERSION.SDK_INT,
                "androidRelease" to Build.VERSION.RELEASE,
                "deviceBrand" to Build.BRAND,
                "deviceFingerprint" to Build.FINGERPRINT,
                "logFilePath" to logDump.file.absolutePath,
                "logFileName" to logDump.file.name,
                "appLogExcerpt" to logDump.excerpt,
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

    fun openPlayStore(context: Context): Boolean {
        val packageName = context.packageName
        val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))

        return runCatching {
            context.startActivity(playStoreIntent)
            true
        }.getOrDefault(false)
    }

    suspend fun hasDiscordPopupBeenShown(context: Context): Result<Boolean> = runCatching {
        val prefs = AppPreferences(context)
        val snapshot = withTimeoutOrNull(8.seconds) {
            Firebase.firestore
                .collection(SOCIAL_PROMPTS_COLLECTION)
                .document(prefs.userId)
                .get()
                .awaitTask()
        } ?: throw TimeoutException("Checking Discord popup state timed out")

        snapshot.getBoolean(FIELD_DISCORD_POPUP_SHOWN) == true
    }

    suspend fun markDiscordPopupShown(context: Context): Result<Unit> = runCatching {
        val prefs = AppPreferences(context)
        val payload = hashMapOf(
            "userId" to prefs.userId,
            FIELD_DISCORD_POPUP_SHOWN to true,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        withTimeoutOrNull(8.seconds) {
            Firebase.firestore
                .collection(SOCIAL_PROMPTS_COLLECTION)
                .document(prefs.userId)
                .set(payload, com.google.firebase.firestore.SetOptions.merge())
                .awaitTask()
        } ?: throw TimeoutException("Saving Discord popup state timed out")
    }
}

private data class FeedbackLogDump(
    val file: File,
    val excerpt: String
)

private fun createFeedbackLogDump(context: Context, serverVersion: String): FeedbackLogDump {
    val logsRoot = File(context.filesDir, "feedback_logs").also { it.mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val outFile = File(logsRoot, "feedback_${serverVersion}_$stamp.txt")

    val activeWorld = com.pocketcraft.server.server.ServerHostService.getPersistedActiveWorld(context)
        .ifBlank {
            runBlocking {
                runCatching {
                    com.pocketcraft.server.data.preferences.AppPreferencesStore.getSelectedWorldFlow(context).first()
                }.getOrDefault("world")
            }
        }

    val serverLogFile = File(context.filesDir, "servers/worlds/$activeWorld/logs/latest.log")
    val serverLogText = runCatching {
        if (serverLogFile.exists()) serverLogFile.readText() else "No server log file found at ${serverLogFile.absolutePath}"
    }.getOrDefault("Could not read latest.log")

    val report = buildString {
        appendLine("PocketCraft Feedback Log Dump")
        appendLine("timestamp=$stamp")
        appendLine("serverVersion=$serverVersion")
        appendLine("appVersion=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("brand=${Build.BRAND}")
        appendLine("androidRelease=${Build.VERSION.RELEASE}")
        appendLine("androidSdk=${Build.VERSION.SDK_INT}")
        appendLine("fingerprint=${Build.FINGERPRINT}")
        appendLine()
        appendLine("---- latest.log ----")
        appendLine(serverLogText.takeLast(220_000))
    }

    outFile.writeText(report)
    return FeedbackLogDump(
        file = outFile,
        excerpt = report.takeLast(12_000)
    )
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) continuation.resume(result)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
}
