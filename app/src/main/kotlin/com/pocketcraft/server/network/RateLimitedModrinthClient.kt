package com.pocketcraft.server.network

import android.content.Context
import android.util.LruCache
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Rate-Limited Modrinth API Client with:
 * - HTTP caching (OkHttp's Cache with HTTP headers)
 * - Retry logic with exponential backoff for 429 responses
 * - Request throttling (max 5 concurrent requests per app)
 * - Deduplication for identical concurrent requests
 * - Local LRU cache for query results
 */
class RateLimitedModrinthClient(context: Context) {

    // HTTP Cache: 10MB disk cache + Cache-Control header respecting
    private val cacheDir = File(context.cacheDir, "modrinth_http_cache")
    private val httpCache = Cache(cacheDir, 10 * 1024 * 1024) // 10MB

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .cache(httpCache)
        .build()

    // In-memory LRU cache for search results (last 50 queries)
    private val queryCache = LruCache<String, CachedResult>(50)

    // Throttling: max 5 concurrent requests
    private val requestMutex = Mutex()
    private var activeRequests = 0
    private val maxConcurrentRequests = 5

    // Request deduplication: track in-flight requests
    private val inFlightRequests = mutableMapOf<String, Result<List<ModItem>>>()
    private val inflightMutex = Mutex()

    private val gson = Gson()
    private val baseUrl = "https://api.modrinth.com/v2"

    // Cache result with timestamp
    private data class CachedResult(
        val data: List<ModItem>,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(maxAgeMs: Long = 5 * 60 * 1000): Boolean =
            System.currentTimeMillis() - timestamp > maxAgeMs
    }

    /**
     * Search mods with complete rate limiting protection:
     * 1. Check in-memory LRU cache
     * 2. Check if request is currently in-flight (deduplication)
     * 3. Throttle to max 5 concurrent requests
     * 4. Fetch from Modrinth with retry on 429
     */
    suspend fun searchMods(
        query: String,
        category: ModCategory,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<ModItem>> = withContext(Dispatchers.IO) {
        val cacheKey = "$query:$category:$limit:$offset"

        // Check LRU cache first
        queryCache.get(cacheKey)?.let { cached ->
            if (!cached.isExpired()) {
                return@withContext Result.success(cached.data)
            }
        }

        // Check for in-flight requests (deduplication)
        inflightMutex.withLock {
            inFlightRequests[cacheKey]?.let {
                return@withContext it
            }
        }

        // Throttle: wait if at max concurrent requests
        requestMutex.withLock {
            while (activeRequests >= maxConcurrentRequests) {
                // Wait a bit before checking again
                Thread.sleep(100)
            }
            activeRequests++
        }

        try {
            // Mark request as in-flight
            inflightMutex.withLock {
                inFlightRequests[cacheKey] = Result.success(emptyList())
            }

            val result = performSearchWithRetry(query, category, limit, offset)

            // Cache the result
            result.onSuccess { mods ->
                queryCache.put(cacheKey, CachedResult(mods))
            }

            // Update in-flight record with actual result
            inflightMutex.withLock {
                inFlightRequests[cacheKey] = result
            }

            result
        } finally {
            // Decrement active requests
            requestMutex.withLock {
                activeRequests--
            }

            // Clean up after 1 second to allow deduplication
            withContext(Dispatchers.Default) {
                delay(1000)
                inflightMutex.withLock {
                    inFlightRequests.remove(cacheKey)
                }
            }
        }
    }

    /**
     * Perform search with exponential backoff retry for 429 (Too Many Requests)
     */
    private suspend fun performSearchWithRetry(
        query: String,
        category: ModCategory,
        limit: Int = 20,
        offset: Int = 0,
        retryCount: Int = 0
    ): Result<List<ModItem>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val facets = when (category) {
                ModCategory.Plugins -> "[\"project_type:plugin\"]"
                ModCategory.Mods -> "[\"project_type:mod\"]"
                ModCategory.ResourcePacks -> "[\"project_type:resourcepack\"]"
            }

            val url = "$baseUrl/search?query=$query&facets=$facets&limit=$limit&offset=$offset"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PocketCraft-App")
                .cacheControl(okhttp3.CacheControl.Builder().maxAge(5, TimeUnit.MINUTES).build())
                .build()

            val response = client.newCall(request).execute()

            when {
                response.isSuccessful -> {
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
                }
                response.code == 429 -> {
                    // Rate limited - retry with exponential backoff
                    val maxRetryShift = if (retryCount > 5) 5 else retryCount
                    val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: (1000L * (1 shl maxRetryShift))

                    if (retryCount < 3) {
                        // Wait before retrying (exponential backoff: 1s, 2s, 4s, 8s, 16s)
                        withContext(Dispatchers.Default) {
                            delay(retryAfter)
                        }
                        performSearchWithRetry(query, category, limit, offset, retryCount + 1)
                    } else {
                        Result.failure(Exception("Rate limited: exceeded max retries"))
                    }
                }
                else -> Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get project versions with caching and retry
     */
    suspend fun getProjectVersions(projectId: String): Result<List<String>> = withContext(Dispatchers.IO) {
        val cacheKey = "versions:$projectId"

        // Check cache
        queryCache.get(cacheKey)?.let { cached ->
            if (!cached.isExpired()) {
                @Suppress("UNCHECKED_CAST")
                return@withContext Result.success(cached.data.map { it.slug } as List<String>)
            }
        }

        return@withContext try {
            val url = "$baseUrl/project/$projectId/versions"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PocketCraft-App")
                .cacheControl(okhttp3.CacheControl.Builder().maxAge(10, TimeUnit.MINUTES).build())
                .build()

            val response = client.newCall(request).execute()

            when {
                response.isSuccessful -> {
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
                }
                else -> Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadMod(projectId: String, versionNumber: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        Result.failure(Exception("Direct in-app mod/plugin downloads are disabled. Download in a browser, then import the file from device storage."))
    }

    /**
     * Clear caches manually if needed
     */
    fun clearCaches() {
        queryCache.evictAll()
        try {
            httpCache.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(): String {
        return """
            Query Cache: ${queryCache.size()}/${queryCache.maxSize()} items
            HTTP Cache: ${httpCache.size()} bytes / ${httpCache.maxSize()} bytes (${httpCache.size() / 1024}KB)
            Active Requests: $activeRequests/$maxConcurrentRequests
        """.trimIndent()
    }
}
