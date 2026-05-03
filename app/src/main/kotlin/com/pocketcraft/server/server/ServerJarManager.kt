package com.pocketcraft.server.server

import android.util.Log
import com.pocketcraft.server.service.VersionCacheManager
import com.pocketcraft.server.data.model.ServerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object ServerJarManager {
    private const val TAG = "ServerJarManager"
    private const val USER_AGENT = "PocketCraft/1.0"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetchAvailableVersions(
        context: android.content.Context, 
        serverType: ServerType,
        forceRefresh: Boolean = false
    ): List<String> = withContext(Dispatchers.IO) {
        val cacheKey = "versions_${serverType.name.lowercase()}"
        val typeToken = object : com.google.gson.reflect.TypeToken<VersionCacheManager.CacheEntry<List<String>>>() {}
        
        // Return cache instantly if available and not forced
        if (!forceRefresh) {
            val cached = VersionCacheManager.get(context, cacheKey, typeToken)
            if (cached != null) {
                return@withContext cached
            }
        }

        val versions = when (serverType) {
            ServerType.PAPER -> fetchPaperVersions()
            ServerType.PURPUR -> fetchPurpurVersions()
            ServerType.FABRIC -> fetchFabricVersions()
            ServerType.MODPACK -> emptyList()
            ServerType.CUSTOM_JAR -> emptyList()
        }

        if (versions.isNotEmpty()) {
            VersionCacheManager.put(context, cacheKey, versions)
        }
        versions
    }

    private fun fetchPaperVersions(): List<String> {
        val req = Request.Builder()
            .url("https://api.papermc.io/v2/projects/paper")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("PaperMC API error ${resp.code}")
            val body = resp.body?.string() ?: throw Exception("Empty response")
            val json = JSONObject(body)
            val versions = json.getJSONArray("versions")
            val list = mutableListOf<String>()
            for (i in (versions.length() - 1) downTo 0) {
                list.add(versions.getString(i))
            }
            return list
        }
    }

    private fun fetchPurpurVersions(): List<String> {
        val req = Request.Builder()
            .url("https://api.purpurmc.org/v2/purpur")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Purpur API error ${resp.code}")
            val body = resp.body?.string() ?: throw Exception("Empty response")
            val json = JSONObject(body)
            val versions = json.getJSONArray("versions")
            val list = mutableListOf<String>()
            for (i in (versions.length() - 1) downTo 0) {
                list.add(versions.getString(i))
            }
            return list
        }
    }

    private fun fetchFabricVersions(): List<String> {
        val req = Request.Builder()
            .url("https://meta.fabricmc.net/v2/versions/game")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Fabric API error ${resp.code}")
            val body = resp.body?.string() ?: throw Exception("Empty response")
            val jsonArray = JSONArray(body)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getBoolean("stable")) {
                    list.add(obj.getString("version"))
                }
            }
            return list
        }
    }

    fun resolveJar(
        serverType: ServerType,
        gameVersion: String,
        customJarPath: String?,
        targetFile: File,
        onProgress: (Int) -> Unit
    ): Flow<File> = flow {
        if (serverType == ServerType.CUSTOM_JAR) {
            val jarFile = File(customJarPath ?: throw Exception("Custom JAR path is missing"))
            if (!jarFile.exists()) {
                throw Exception("Custom JAR file does not exist: ${jarFile.absolutePath}")
            }
            emit(jarFile)
            return@flow
        }

        if (gameVersion.isBlank()) {
            throw IllegalStateException("Server version not set. Please select a version before downloading a JAR.")
        }

        val expectedMinSize = if (serverType == ServerType.FABRIC) 50_000L else 1_000_000L
        if (targetFile.exists() && targetFile.length() > expectedMinSize) {
            onProgress(100)
            emit(targetFile)
            return@flow
        }

        val downloadUrl = when (serverType) {
            ServerType.PAPER -> resolvePaperUrl(gameVersion)
            ServerType.PURPUR -> resolvePurpurUrl(gameVersion)
            ServerType.FABRIC -> resolveFabricUrl(gameVersion)
            ServerType.MODPACK -> throw IllegalStateException("Modpack uses dedicated installer flow")
            ServerType.CUSTOM_JAR -> throw IllegalStateException("Unreachable")
        }

        downloadFile(downloadUrl, targetFile, onProgress)
        
        val minSize = if (serverType == ServerType.FABRIC) 50_000L else 1024 * 1024L
        if (!targetFile.exists() || targetFile.length() < minSize) {
            targetFile.delete()
            throw Exception("Downloaded file is invalid. Please retry.")
        }

        emit(targetFile)
    }.flowOn(Dispatchers.IO)

    private fun resolvePaperUrl(version: String): String {
        if (version.isBlank()) {
            throw IllegalStateException("Server version not set. Please select a version before downloading a JAR.")
        }
        val req = Request.Builder()
            .url("https://api.papermc.io/v2/projects/paper/versions/$version/builds")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("PaperMC API error")
            val json = JSONObject(resp.body?.string() ?: "")
            val builds = json.getJSONArray("builds")
            val latestBuild = builds.getJSONObject(builds.length() - 1)
            val buildNum = latestBuild.getInt("build")
            val fileName = latestBuild.getJSONObject("downloads").getJSONObject("application").getString("name")
            return "https://api.papermc.io/v2/projects/paper/versions/$version/builds/$buildNum/downloads/$fileName"
        }
    }

    private fun resolvePurpurUrl(version: String): String {
        return "https://api.purpurmc.org/v2/purpur/$version/latest/download"
    }

    private fun resolveFabricUrl(version: String): String {
        val loaderReq = Request.Builder()
            .url("https://meta.fabricmc.net/v2/versions/loader")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        val loaderVersion = client.newCall(loaderReq).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Fabric API error")
            val json = JSONArray(resp.body?.string() ?: "")
            json.getJSONObject(0).getString("version")
        }

        val installerReq = Request.Builder()
            .url("https://meta.fabricmc.net/v2/versions/installer")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        val installerVersion = client.newCall(installerReq).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Fabric API error")
            val json = JSONArray(resp.body?.string() ?: "")
            json.getJSONObject(0).getString("version")
        }
        
        // As per https://meta.fabricmc.net/v2/versions/loader/1.20.4/0.15.7/1.0.0/server/jar
        // But the actual installer version from API is something like "1.0.1"
        return "https://meta.fabricmc.net/v2/versions/loader/$version/$loaderVersion/$installerVersion/server/jar"
    }

    private fun downloadFile(url: String, target: File, onProgress: (Int) -> Unit) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Download failed with HTTP ${resp.code}")
            val contentLength = resp.body?.contentLength() ?: -1L
            val input = resp.body?.byteStream() ?: throw Exception("No stream")

            FileOutputStream(target).use { output ->
                val buffer = ByteArray(16384)
                var downloaded = 0L
                var bytes: Int
                var lastProgress = -1

                while (input.read(buffer).also { bytes = it } >= 0) {
                    output.write(buffer, 0, bytes)
                    downloaded += bytes
                    if (contentLength > 0) {
                        val progress = ((downloaded * 100) / contentLength).toInt()
                        if (progress != lastProgress) {
                            onProgress(progress)
                            lastProgress = progress
                        }
                    }
                }
            }
        }
    }
}
