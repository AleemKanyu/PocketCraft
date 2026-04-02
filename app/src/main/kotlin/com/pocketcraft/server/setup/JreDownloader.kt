package com.pocketcraft.server.setup

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

/**
 * Downloads a modern JRE 17 build (2024) patched for Android 12-14 pointer tagging.
 */
class JreDownloader(private val client: OkHttpClient) {

    fun download(targetDir: File): Flow<Int> = flow {
        val abi = Build.SUPPORTED_ABIS.firstOrNull()?.lowercase(Locale.US) ?: "arm64-v8a"
        // Using a 2024 build which is proven to work on Android 14
        val url = when {
            abi.contains("arm64") || abi.contains("aarch64") -> 
                "https://github.com/PojavLauncherTeam/PojavLauncher-Jre-Archives/releases/download/jre17-20240112/jre17-aarch64-20240112-release.tar.xz"
            else -> 
                "https://github.com/PojavLauncherTeam/PojavLauncher-Jre-Archives/releases/download/jre17-20240112/jre17-arm-20240112-release.tar.xz"
        }
        
        val targetFile = File(targetDir, "jre17_downloaded.tar.xz")
        if (targetFile.exists()) targetFile.delete()
        
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to download JRE: ${response.code}")
            
            val body = response.body ?: throw IOException("Empty response body")
            val totalBytes = body.contentLength()
            val input = body.byteStream()
            
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(32768)
                var bytesRead: Int
                var totalRead = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) {
                        emit(((totalRead * 100) / totalBytes).toInt())
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
