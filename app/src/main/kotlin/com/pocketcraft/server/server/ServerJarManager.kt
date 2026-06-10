package com.pocketcraft.server.server

import android.util.Log
import com.pocketcraft.server.service.VersionCacheManager
import com.pocketcraft.server.data.model.ServerType
import com.pocketcraft.server.service.VersionCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
            ServerType.PAPER -> VersionCatalog.fetchStableVersions(limit = 80)
            ServerType.PURPUR -> fetchPurpurVersions()
            ServerType.FABRIC -> fetchFabricVersions()
            ServerType.MODPACK -> emptyList()
        }

        if (versions.isNotEmpty()) {
            VersionCacheManager.put(context, cacheKey, versions)
        }
        versions
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
        serverDir: File? = null,
        onProgress: (Int) -> Unit
    ): Flow<File> = flow {

        if (serverType == ServerType.MODPACK) {
            val dir = serverDir ?: throw IllegalStateException("Modpack server directory is missing")
            val launchTarget = com.pocketcraft.server.service.ServerFileManager.readLaunchTarget(dir)
            if (launchTarget == null) {
                // No launch target means modpack was never installed on this world.
                // This typically happens when the user switches worlds before installing the modpack.
                throw IllegalStateException(
                    "Modpack not installed on this world. Tap \"Install Modpack\" on the Home screen to set it up before starting."
                )
            }
            if (!launchTarget.file.exists() || launchTarget.file.isDirectory || launchTarget.file.length() <= 0L) {
                throw IllegalStateException(
                    "Modpack files are missing or incomplete. Tap \"Install Modpack\" on the Home screen to re-install."
                )
            }
            onProgress(100)
            emit(launchTarget.file)
            return@flow
        }

        if (gameVersion.isBlank()) {
            throw IllegalStateException("Server version not set. Please select a version and import its server JAR.")
        }

        val expectedMinSize = if (serverType == ServerType.FABRIC) 10_000L else 1_000_000L
        android.util.Log.d("ServerJarManager", "resolveJar: serverType=$serverType gameVersion=$gameVersion targetFile=${targetFile.absolutePath} exists=${targetFile.exists()} isDir=${targetFile.isDirectory} size=${targetFile.length()}")
        if (targetFile.exists() && targetFile.length() > expectedMinSize) {
            android.util.Log.d("ServerJarManager", "resolveJar: cache hit, emitting ${targetFile.absolutePath}")
            onProgress(100)
            emit(targetFile)
            return@flow
        }

        if (targetFile.exists() && targetFile.isDirectory) {
            android.util.Log.e("ServerJarManager", "resolveJar: targetFile is a DIRECTORY — deleting it: ${targetFile.absolutePath}")
            targetFile.deleteRecursively()
        }

        throw IllegalStateException(
            "Server JAR is not installed. Open the version picker, download ${serverType.displayName} $gameVersion in your browser, then select the downloaded JAR."
        )
    }.flowOn(Dispatchers.IO)
}
