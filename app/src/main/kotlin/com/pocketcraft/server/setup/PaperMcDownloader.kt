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
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

private const val PAPER_API = "https://api.papermc.io/v2/projects/paper"
private const val MOJANG_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json"
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
        var usedMojangFallback = false
        val downloadUrl = try {
            val build = fetchLatestBuildWithRetry()
            Log.i(tag, "using Paper build=${build.number} file=${build.fileName} version=$paperVersion")
            "$PAPER_API/versions/$paperVersion/builds/${build.number}/downloads/${build.fileName}"
        } catch (paperError: Exception) {
            val fallback = runCatching { fetchMojangServerJarUrl() }.getOrNull()
            if (fallback != null) {
                usedMojangFallback = true
                Log.w(tag, "Paper API failed for version=$paperVersion; falling back to Mojang server jar", paperError)
                fallback
            } else {
                Log.e(tag, "download source resolution failed version=$paperVersion", paperError)
                throw Exception("Could not connect to PaperMC API. Check your internet connection.", paperError)
            }
        }
        Log.i(tag, "resolved download url source=${if (usedMojangFallback) "mojang" else "paper"} version=$paperVersion")

        emit(if (usedMojangFallback) 8 else 5)
        val targetFile = File(targetDir, jarName)

        if (targetFile.exists()) {
            targetFile.delete()
        }

        try {
            downloadFile(downloadUrl, targetFile) { percent ->
                val floor = if (usedMojangFallback) 8 else 5
                emit(floor + (percent * 0.9).toInt())
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
            
            var latest: PaperBuild? = null
            for (i in (builds.length() - 1) downTo 0) {
                val buildJson = builds.getJSONObject(i)
                val channel = buildJson.optString("channel", "default")
                if (channel == "default" || channel == "stable") {
                    val num = buildJson.getInt("build")
                    val fileName = buildJson
                        .getJSONObject("downloads")
                        .getJSONObject("application")
                        .getString("name")
                    latest = PaperBuild(num, fileName)
                    break
                }
            }
            
            latest ?: throw Exception("No stable builds found for $paperVersion")
        }
    }

                private suspend fun fetchMojangServerJarUrl(): String = withContext(Dispatchers.IO) {
                    val manifestReq = Request.Builder()
                        .url(MOJANG_MANIFEST_URL)
                        .header("Accept", "application/json")
                        .header("User-Agent", POCKETCRAFT_USER_AGENT)
                        .build()

                    val manifestResponse = client.newCall(manifestReq).execute()
                    val versionDetailUrl = manifestResponse.use { resp ->
                        if (!resp.isSuccessful) {
                            throw Exception("Mojang manifest request failed with HTTP ${resp.code}")
                        }
                        val body = resp.body?.string().orEmpty()
                        if (body.isBlank()) throw Exception("Empty Mojang manifest response")

                        val manifest = JSONObject(body)
                        val versions = manifest.optJSONArray("versions") ?: throw Exception("Missing Mojang versions array")
                        var detailUrl: String? = null
                        for (i in 0 until versions.length()) {
                            val entry = versions.optJSONObject(i) ?: continue
                            if (entry.optString("id") == paperVersion) {
                                detailUrl = entry.optString("url").takeIf { it.isNotBlank() }
                                break
                            }
                        }
                        detailUrl ?: throw Exception("Mojang version $paperVersion not found")
                    }

                    val detailReq = Request.Builder()
                        .url(versionDetailUrl)
                        .header("Accept", "application/json")
                        .header("User-Agent", POCKETCRAFT_USER_AGENT)
                        .build()
                    val detailResponse = client.newCall(detailReq).execute()
                    detailResponse.use { resp ->
                        if (!resp.isSuccessful) {
                            throw Exception("Mojang version detail failed with HTTP ${resp.code}")
                        }
                        val body = resp.body?.string().orEmpty()
                        if (body.isBlank()) throw Exception("Empty Mojang version detail response")

                        val detail = JSONObject(body)
                        val downloads = detail.optJSONObject("downloads") ?: throw Exception("Missing downloads in Mojang version detail")
                        val server = downloads.optJSONObject("server") ?: throw Exception("Server JAR missing for version $paperVersion")
                        server.optString("url").takeIf { it.isNotBlank() }
                            ?: throw Exception("Mojang server JAR URL missing")
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
