package com.pockethost.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.pockethost.app.network.InAppDownloader
import java.io.File

/**
 * Handles downloading an APK from a URL and triggering the system package installer.
 *
 * APKs are downloaded to `cacheDir/updates/` which is already registered in
 * `file_provider_paths.xml` as the "updates" cache path.
 */
object ApkInstaller {
    private const val TAG = "ApkInstaller"
    private const val UPDATES_DIR = "updates"
    private const val APK_FILE_NAME = "PocketHost-update.apk"

    /**
     * Downloads the APK from [url] into the app's cache directory with progress reporting.
     *
     * @return the downloaded [File] on success, or `null` on failure.
     */
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, percentage: Float) -> Unit = { _, _, _ -> }
    ): File? {
        val updatesDir = File(context.cacheDir, UPDATES_DIR)
        cleanOldApks(updatesDir)

        val targetFile = File(updatesDir, APK_FILE_NAME)
        Log.d(TAG, "Downloading APK from $url to ${targetFile.absolutePath}")

        val result = InAppDownloader.downloadFile(
            url = url,
            targetFile = targetFile,
            onProgress = onProgress
        )

        return result.getOrElse { error ->
            Log.e(TAG, "APK download failed: ${error.message}", error)
            null
        }
    }

    /**
     * Launches the system package installer for the given APK file.
     *
     * Uses [FileProvider] to securely share the file with the installer.
     * On Android 8+ (API 26+), if the app doesn't have install-from-unknown-sources
     * permission, this will redirect the user to the permission settings first.
     *
     * @return `true` if the install intent was launched, `false` on error.
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
            return false
        }

        // Check unknown-sources permission on Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Log.d(TAG, "Unknown sources permission not granted, opening settings")
                openInstallPermissionSettings(context)
                return false
            }
        }

        return try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            Log.d(TAG, "Install intent launched for ${apkFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch install intent: ${e.message}", e)
            false
        }
    }

    /**
     * Whether the app has permission to install APKs from unknown sources.
     */
    fun canInstallApks(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Opens the system settings page for granting this app the
     * "Install from Unknown Sources" permission.
     */
    fun openInstallPermissionSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open install permission settings: ${e.message}", e)
        }
    }

    /**
     * Returns the cached APK file if it exists (for retrying install after granting permission).
     */
    fun getCachedApk(context: Context): File? {
        val file = File(File(context.cacheDir, UPDATES_DIR), APK_FILE_NAME)
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Removes previously downloaded APK files from the updates cache directory.
     */
    private fun cleanOldApks(updatesDir: File) {
        if (updatesDir.exists()) {
            updatesDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".apk", ignoreCase = true)) {
                    val deleted = file.delete()
                    Log.d(TAG, "Cleaned old APK ${file.name}: deleted=$deleted")
                }
            }
        }
    }
}
