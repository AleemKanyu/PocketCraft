package com.pocketcraft.server.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object VersionCatalog {

    // Accept stable semantic Minecraft version identifiers including 26.x-style release train.
    private val minecraftVersionRegex = Regex("^(?:\\d+\\.\\d+(?:\\.\\d+)?|26(?:\\.\\d+)*)$")
    private val userAgent = "PocketCraft/1.0"

    suspend fun fetchStableVersions(limit: Int = 60): List<String> = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder().build()

        val fromPurpur = runCatching {
            val request = Request.Builder()
                .url("https://api.purpurmc.org/v2/purpur")
                .header("User-Agent", userAgent)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList<String>()
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val versions = json.optJSONArray("versions") ?: return@use emptyList<String>()
                buildList {
                    for (i in 0 until versions.length()) {
                        val version = versions.optString(i)
                        if (minecraftVersionRegex.matches(version)) add(version)
                    }
                }.distinct()
            }
        }.getOrDefault(emptyList())

        val fromFabric = runCatching {
            val request = Request.Builder()
                .url("https://meta.fabricmc.net/v2/versions/game")
                .header("User-Agent", userAgent)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList<String>()
                val body = response.body?.string().orEmpty()
                val versions = org.json.JSONArray(body)
                buildList {
                    for (i in 0 until versions.length()) {
                        val entry = versions.optJSONObject(i) ?: continue
                        if (!entry.optBoolean("stable")) continue
                        val version = entry.optString("version")
                        if (minecraftVersionRegex.matches(version)) add(version)
                    }
                }.distinct()
            }
        }.getOrDefault(emptyList())

        val combined = (fromPurpur + fromFabric)
            .filter { minecraftVersionRegex.matches(it) }
            .distinct()
            .sortedWith(versionComparator)
            .take(limit)

        val filtered = MinecraftVersionPolicy.filterInstallableVersions(combined)
        if (filtered.isNotEmpty()) return@withContext filtered

        runCatching {
            val request = Request.Builder()
                .url("https://launchermeta.mojang.com/mc/game/version_manifest.json")
                .header("User-Agent", userAgent)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList<String>()
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val versions = json.optJSONArray("versions") ?: return@use emptyList<String>()
                buildList {
                    for (i in 0 until versions.length()) {
                        val entry = versions.optJSONObject(i) ?: continue
                        if (entry.optString("type") != "release") continue
                        val id = entry.optString("id")
                        if (minecraftVersionRegex.matches(id)) add(id)
                    }
                }.distinct().sortedWith(versionComparator).take(limit)
            }
        }.getOrDefault(emptyList()).let { MinecraftVersionPolicy.filterInstallableVersions(it) }
    }

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
}
