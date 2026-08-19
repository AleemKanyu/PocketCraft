package com.pockethost.app.network

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object InAppDownloader {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Downloads a file from [url] directly into [targetFile] with live progress updates.
     */
    suspend fun downloadFile(
        url: String,
        targetFile: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, percentage: Float) -> Unit = { _, _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            targetFile.parentFile?.mkdirs()
            if (targetFile.exists()) {
                targetFile.delete()
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PocketCraft/1.8.5 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed with HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw IllegalStateException("Download response body was empty")
                val totalBytes = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesDownloaded = 0L
                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesDownloaded += read

                            val pct = if (totalBytes > 0) {
                                (bytesDownloaded.toFloat() / totalBytes.toFloat()) * 100f
                            } else -1f

                            onProgress(bytesDownloaded, totalBytes, pct)
                        }
                        output.flush()
                    }
                }
            }

            if (!targetFile.exists() || targetFile.length() == 0L) {
                throw IllegalStateException("Downloaded file is empty or missing")
            }

            targetFile
        }.onFailure {
            if (targetFile.exists()) {
                targetFile.delete()
            }
        }
    }

    /**
     * Downloads a file into the user's public Downloads directory and notifies Android media scanner.
     */
    suspend fun downloadToPublicDownloads(
        context: Context,
        url: String,
        fileName: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, percentage: Float) -> Unit = { _, _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val destination = File(downloadsDir, fileName)

        downloadFile(url, destination, onProgress).onSuccess { downloadedFile ->
            runCatching {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(downloadedFile.absolutePath),
                    null,
                    null
                )
            }
        }
    }
}
