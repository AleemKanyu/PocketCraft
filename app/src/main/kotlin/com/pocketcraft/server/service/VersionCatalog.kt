package com.pocketcraft.server.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object VersionCatalog {

    private val versionRegex = Regex("^\\d+\\.\\d+(\\.\\d+)?$")

    suspend fun fetchStableVersions(limit: Int = 60): List<String> = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder().build()

        val fromPaper = runCatching {
            val request = Request.Builder()
                .url("https://api.papermc.io/v2/projects/paper")
                .header("User-Agent", "PocketCraft/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList<String>()
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val versions = json.optJSONArray("versions") ?: return@use emptyList<String>()
                buildList {
                    for (i in 0 until versions.length()) {
                        val version = versions.optString(i)
                        if (versionRegex.matches(version)) add(version)
                    }
                }.reversed().distinct().take(limit)
            }
        }.getOrDefault(emptyList())

        if (fromPaper.isNotEmpty()) return@withContext fromPaper

        runCatching {
            val request = Request.Builder()
                .url("https://launchermeta.mojang.com/mc/game/version_manifest.json")
                .header("User-Agent", "PocketCraft/1.0")
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
                        if (versionRegex.matches(id)) add(id)
                    }
                }.distinct().take(limit)
            }
        }.getOrDefault(emptyList())
    }
}
