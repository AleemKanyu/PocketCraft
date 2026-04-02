package com.pocketcraft.server.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed class ModCategory {
    object Plugins : ModCategory()
    object Mods : ModCategory()
    object ResourcePacks : ModCategory()
}

data class ModItem(
    val id: String,
    val slug: String,
    val name: String,
    val description: String,
    val downloads: Int,
    val followers: Int,
    val imageUrl: String,
    val projectType: String,
    val latestVersion: String? = null
)

class ModrinthClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val baseUrl = "https://api.modrinth.com/v2"

    suspend fun searchMods(
        query: String,
        category: ModCategory,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<ModItem>> = withContext(Dispatchers.IO) {
        try {
            val facets = when (category) {
                ModCategory.Plugins -> "[\"project_type:plugin\"]"
                ModCategory.Mods -> "[\"project_type:mod\"]"
                ModCategory.ResourcePacks -> "[\"project_type:resourcepack\"]"
            }

            val url = "$baseUrl/search?query=$query&facets=$facets&limit=$limit&offset=$offset"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PocketCraft-App")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val jsonObject = gson.fromJson(body, JsonObject::class.java)
            val hits = jsonObject.getAsJsonArray("hits")

            val mods = hits.mapNotNull { hit ->
                try {
                    val obj = hit.asJsonObject
                    ModItem(
                        id = obj.get("id").asString,
                        slug = obj.get("slug").asString,
                        name = obj.get("title").asString,
                        description = obj.get("description").asString,
                        downloads = obj.get("downloads").asInt,
                        followers = obj.get("follows").asInt,
                        imageUrl = obj.takeIf { it.has("icon_url") }?.get("icon_url")?.asString ?: "",
                        projectType = obj.get("project_type").asString
                    )
                } catch (e: Exception) {
                    null
                }
            }

            Result.success(mods)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProjectVersions(projectId: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/project/$projectId/versions"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PocketCraft-App")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val versions = gson.fromJson(body, JsonArray::class.java)

            val versionStrings = versions.mapNotNull { version ->
                try {
                    version.asJsonObject.get("version_number").asString
                } catch (e: Exception) {
                    null
                }
            }

            Result.success(versionStrings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadMod(projectId: String, versionNumber: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val versionsUrl = "$baseUrl/project/$projectId/versions?loaders=[\"paper\",\"spigot\",\"bukkit\"]"
            val versionsRequest = Request.Builder()
                .url(versionsUrl)
                .header("User-Agent", "PocketCraft-App")
                .build()

            val versionsResponse = client.newCall(versionsRequest).execute()
            if (!versionsResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch versions"))
            }

            val versionsBody = versionsResponse.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val versions = gson.fromJson(versionsBody, JsonArray::class.java)

            var downloadUrl: String? = null
            for (version in versions) {
                val versionObj = version.asJsonObject
                if (versionObj.get("version_number").asString == versionNumber) {
                    val files = versionObj.getAsJsonArray("files")
                    if (files.size() > 0) {
                        downloadUrl = files[0].asJsonObject.get("url").asString
                        break
                    }
                }
            }

            if (downloadUrl == null) {
                return@withContext Result.failure(Exception("Version not found"))
            }

            val downloadRequest = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "PocketCraft-App")
                .build()

            val downloadResponse = client.newCall(downloadRequest).execute()
            if (!downloadResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed"))
            }

            val data = downloadResponse.body?.bytes() ?: return@withContext Result.failure(Exception("Empty download"))
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
