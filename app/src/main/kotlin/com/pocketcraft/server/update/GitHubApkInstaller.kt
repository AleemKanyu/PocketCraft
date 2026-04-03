package com.pocketcraft.server.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.pocketcraft.server.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object GitHubApkInstaller {

    data class DownloadResult(
        val file: File,
        val mimeType: String = "application/vnd.android.package-archive"
    )

    private val client = OkHttpClient()

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Result<DownloadResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cacheDir = File(context.cacheDir, "updates").also { it.mkdirs() }
            val targetFile = File(cacheDir, "pocketcraft-update-${System.currentTimeMillis()}.apk")
            val request = Request.Builder()
                .url(downloadUrl)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "PocketCraft/${BuildConfig.VERSION_NAME}")
                .apply {
                    val token = BuildConfig.GITHUB_RELEASES_TOKEN.trim()
                    if (token.isNotBlank()) {
                        header("Authorization", "Bearer $token")
                    }
                }
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("APK download failed with HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Empty APK download response")
                val totalBytes = body.contentLength().takeIf { it > 0L } ?: -1L

                body.byteStream().use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var copied = 0L
                        var bytes = input.read(buffer)
                        while (bytes != -1) {
                            output.write(buffer, 0, bytes)
                            copied += bytes
                            if (totalBytes > 0L) {
                                withContext(Dispatchers.Main) {
                                    onProgress(((copied * 100L) / totalBytes).toInt().coerceIn(0, 100))
                                }
                            }
                            bytes = input.read(buffer)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    onProgress(100)
                }
                DownloadResult(file = targetFile)
            }
        }
    }

    fun buildInstallIntent(context: Context, downloadResult: DownloadResult): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, downloadResult.file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, downloadResult.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
