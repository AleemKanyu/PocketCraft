package com.pocketcraft.server.service

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.TimeUnit

object VersionCacheManager {
    private const val TAG = "VersionCacheManager"
    private const val CACHE_DIR = "version_cache"
    private val gson = Gson()

    private val memoryCache = mutableMapOf<String, CacheEntry<*>>()

    data class CacheEntry<T>(
        val data: T,
        val timestamp: Long,
        val ttlMs: Long
    )

    fun <T> get(context: Context, key: String, typeToken: TypeToken<CacheEntry<T>>): T? {
        // Memory hit
        @Suppress("UNCHECKED_CAST")
        val mem = memoryCache[key] as? CacheEntry<T>
        if (mem != null && !isExpired(mem)) {
            return mem.data
        }

        // Disk hit
        val cacheFile = getCacheFile(context, key)
        if (cacheFile.exists()) {
            try {
                val json = cacheFile.readText()
                val entry: CacheEntry<T> = gson.fromJson(json, typeToken.type)
                if (!isExpired(entry)) {
                    memoryCache[key] = entry
                    return entry.data
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read cache for $key", e)
                cacheFile.delete()
            }
        }
        return null
    }

    fun <T> put(context: Context, key: String, data: T, ttl: Long = TimeUnit.DAYS.toMillis(1)) {
        val entry = CacheEntry(data, System.currentTimeMillis(), ttl)
        memoryCache[key] = entry
        
        try {
            val cacheFile = getCacheFile(context, key)
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(gson.toJson(entry))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write cache for $key", e)
        }
    }

    private fun isExpired(entry: CacheEntry<*>): Boolean {
        return System.currentTimeMillis() - entry.timestamp > entry.ttlMs
    }

    private fun getCacheFile(context: Context, key: String): File {
        val dir = File(context.cacheDir, CACHE_DIR)
        return File(dir, "${key.replace(":", "_")}.json")
    }

    fun clear(context: Context) {
        memoryCache.clear()
        File(context.cacheDir, CACHE_DIR).deleteRecursively()
    }
}
