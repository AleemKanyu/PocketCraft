package com.pockethost.app.server

import android.util.Log
import com.pockethost.app.service.VersionCacheManager
import com.pockethost.app.service.MinecraftVersionPolicy
import com.pockethost.app.data.model.ServerType
import com.pockethost.app.service.VersionCatalog
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
    private const val USER_AGENT = "PocketHost/1.0"
    private val stableVersionRegex = Regex("^\\d+\\.\\d+(\\.\\d+)?$")
    private val versionComparator = Comparator<String> { left, right ->
        val leftParts = left.split('.').map { it.toIntOrNull() ?: -1 }
        val rightParts = right.split('.').map { it.toIntOrNull() ?: -1 }
        val maxSize = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until maxSize) {
            val leftPart = leftParts.getOrElse(index) { -1 }
            val rightPart = rightParts.getOrElse(index) { -1 }
            if (leftPart != rightPart) {
                return@Comparator rightPart.compareTo(leftPart)
            }
        }
        0
    }

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
                return@withContext MinecraftVersionPolicy.filterInstallableVersions(cached)
            }
        }

        val versions = when (serverType) {
            ServerType.VANILLA -> VersionCatalog.fetchStableVersions(limit = 80)
            ServerType.PAPER -> mergePaperAndStableVersions(
                fetchPaperVersions(),
                VersionCatalog.fetchStableVersions(limit = 80)
            )
            ServerType.PURPUR -> fetchPurpurVersions()
            ServerType.FABRIC -> fetchFabricVersions()
            ServerType.BEDROCK -> fetchBedrockVersions()
            ServerType.MODPACK -> emptyList()
        }

        val filtered = MinecraftVersionPolicy.filterInstallableVersions(versions)
        if (filtered.isNotEmpty()) {
            VersionCacheManager.put(context, cacheKey, filtered)
        }
        filtered
    }

    private fun fetchPaperVersions(): List<String> {
        return runCatching {
            val req = Request.Builder()
                .url("https://fill.papermc.io/v3/projects/paper")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("Paper API error ${resp.code}")
                val body = resp.body?.string() ?: throw Exception("Empty response")
                val json = JSONObject(body)
                val versionsObj = json.getJSONObject("versions")
                val list = mutableListOf<String>()
                val keys = versionsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val arr = versionsObj.getJSONArray(key)
                    for (j in 0 until arr.length()) {
                        list.add(arr.getString(j))
                    }
                }
                list
            }
        }.getOrElse { e ->
            Log.e(TAG, "Failed to fetch Paper versions: ${e.message}", e)
            emptyList()
        }
    }

    private fun mergePaperAndStableVersions(
        paperVersions: List<String>,
        stableVersions: List<String>
    ): List<String> {
        return (paperVersions + stableVersions)
            .filter { stableVersionRegex.matches(it) }
            .distinct()
            .sortedWith(versionComparator)
            .let { MinecraftVersionPolicy.filterInstallableVersions(it) }
    }

    private fun fetchPurpurVersions(): List<String> {
        return runCatching {
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
                list
            }
        }.getOrElse { e ->
            Log.e(TAG, "Failed to fetch Purpur versions: ${e.message}", e)
            emptyList()
        }
    }

    private fun fetchFabricVersions(): List<String> {
        return runCatching {
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
                list
            }
        }.getOrElse { e ->
            Log.e(TAG, "Failed to fetch Fabric versions: ${e.message}", e)
            emptyList()
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
            val launchTarget = com.pockethost.app.service.ServerFileManager.readLaunchTarget(dir)
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

        if (!customJarPath.isNullOrBlank()) {
            val customFile = File(customJarPath)
            if (customFile.exists() && customFile.length() > expectedMinSize) {
                onProgress(100)
                emit(customFile)
                return@flow
            }
        }

        throw IllegalStateException(
            "Server JAR not found for ${serverType.displayName} $gameVersion. Please tap \"Import Server JAR\" to download and select your server .jar file."
        )
    }.flowOn(Dispatchers.IO)

    private fun fetchBedrockVersions(): List<String> {
        return listOf("1.21.60", "1.21.50", "1.21.40", "1.21.30", "1.21.20", "1.21.2", "1.21.0", "1.20.80")
    }
}
