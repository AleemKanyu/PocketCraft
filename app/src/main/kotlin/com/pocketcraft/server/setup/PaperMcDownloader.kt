package com.pocketcraft.server.setup

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private const val PAPER_API = "https://api.papermc.io/v2/projects/paper"
private const val POCKETCRAFT_USER_AGENT = "PocketCraft/1.0"

/**
 * Downloads the latest recommended PaperMC build from the official API.
 */
class PaperMcDownloader(private val client: OkHttpClient, private val paperVersion: String = "1.20.4") {
    private val tag = "PaperMcDownloader"

    private val versionUrl = "$PAPER_API/versions/$paperVersion/builds"
    private val jarName = "paper-$paperVersion.jar"

    private data class PaperBuild(val number: Int, val fileName: String)

    /**
     * Downloads the PaperMC JAR to [targetDir], emitting progress 0–100.
     */
    fun download(targetDir: File): Flow<Int> = flow {
        emit(0)
        Log.i(tag, "download start version=$paperVersion targetDir=${targetDir.absolutePath}")
        val build = try {
            fetchLatestBuildWithRetry()
        } catch (paperError: Exception) {
            Log.e(tag, "download source resolution failed version=$paperVersion", paperError)
            throw Exception(
                "Paper is required for plugins and Bedrock support. " +
                    "Could not resolve a Paper build for $paperVersion: ${paperError.message ?: "unknown error"}",
                paperError
            )
        }
        val downloadUrl = run {
            Log.i(tag, "using Paper build=${build.number} file=${build.fileName} version=$paperVersion")
            "$PAPER_API/versions/$paperVersion/builds/${build.number}/downloads/${build.fileName}"
        }
        Log.i(tag, "resolved download url source=paper version=$paperVersion")

        emit(5)
        val targetFile = File(targetDir, jarName)

        if (targetFile.exists()) {
            targetFile.delete()
        }

        try {
            downloadFile(downloadUrl, targetFile) { percent ->
                emit(5 + (percent * 0.9).toInt())
            }
        } catch (e: Exception) {
            targetFile.delete()
            Log.e(tag, "download failed version=$paperVersion", e)
            throw Exception("Download interrupted. Please try again.", e)
        }
        
        if (!targetFile.exists() || targetFile.length() < 1024 * 1024) {
            targetFile.delete()
            Log.e(tag, "downloaded file invalid version=$paperVersion")
            throw Exception("Downloaded file is invalid. Please retry.")
        }
        
        emit(100)
        Log.i(tag, "download success version=$paperVersion size=${targetFile.length()}")
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchLatestBuildWithRetry(): PaperBuild {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                return fetchLatestBuild()
            } catch (e: Exception) {
                lastError = e
                if (attempt < 2) {
                    val backoffMs = 500L * (attempt + 1)
                    kotlinx.coroutines.delay(backoffMs)
                }
            }
        }
        throw lastError ?: Exception("PaperMC API unavailable")
    }

    private suspend fun fetchLatestBuild(): PaperBuild = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(versionUrl)
            .header("Accept", "application/json")
            .header("User-Agent", POCKETCRAFT_USER_AGENT)
            .build()
        val response = client.newCall(req).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("PaperMC API returned error ${resp.code}")
            }
            val body = resp.body?.string() ?: throw Exception("Empty API response")
            val json = JSONObject(body)
            val builds = json.getJSONArray("builds")

            var latestPreferred: PaperBuild? = null
            var latestAny: PaperBuild? = null
            for (i in (builds.length() - 1) downTo 0) {
                val buildJson = builds.getJSONObject(i)
                val num = buildJson.getInt("build")
                val fileName = buildJson
                    .getJSONObject("downloads")
                    .getJSONObject("application")
                    .getString("name")
                if (latestAny == null) {
                    latestAny = PaperBuild(num, fileName)
                }
                val channel = buildJson.optString("channel", "default").lowercase()
                if (channel == "default" || channel == "stable") {
                    latestPreferred = PaperBuild(num, fileName)
                    break
                }
            }

            latestPreferred ?: latestAny ?: throw Exception("No Paper builds found for $paperVersion")
        }
    }

    private suspend fun downloadFile(url: String, target: File, onProgress: suspend (Int) -> Unit) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", POCKETCRAFT_USER_AGENT)
            .build()
        val response = client.newCall(req).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("Download failed with HTTP ${resp.code}")
            }

            val contentLength = resp.body?.contentLength() ?: -1L
            val input = resp.body?.byteStream() ?: throw Exception("No download stream")

            FileOutputStream(target).use { output ->
                val buffer = ByteArray(16384)
                var downloaded = 0L
                var bytes: Int
                while (input.read(buffer).also { bytes = it } != -1) {
                    output.write(buffer, 0, bytes)
                    downloaded += bytes
                    if (contentLength > 0) {
                        val percent = ((downloaded * 100) / contentLength).toInt()
                        onProgress(percent.coerceIn(0, 100))
                    }
                }
                output.flush()
            }
        }
    }

    companion object {
        fun buildClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}
