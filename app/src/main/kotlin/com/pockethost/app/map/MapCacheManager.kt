package com.pockethost.app.map

import android.content.Context
import android.util.Log
import java.io.File
import java.text.DecimalFormat

/**
 * Manages storage and caching of rendered map tiles.
 * Strict safety rule: NEVER touches or deletes Minecraft world files!
 */
object MapCacheManager {
    private const val TAG = "MapCacheManager"

    /**
     * Dedicated storage directory for rendered map tiles and static assets.
     */
    fun getMapDir(context: Context, worldName: String): File {
        val safeName = worldName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(context.filesDir, "maps/$safeName").also {
            it.mkdirs()
        }
    }

    /**
     * Calculates the total size in bytes of the cached map for a world.
     */
    fun getCacheSizeBytes(context: Context, worldName: String): Long {
        val mapDir = getMapDir(context, worldName)
        if (!mapDir.exists() || !mapDir.isDirectory) return 0L

        return runCatching {
            mapDir.walkTopDown()
                .filter { it.isFile }
                .map { it.length() }
                .sum()
        }.getOrDefault(0L)
    }

    /**
     * Formats bytes into human-readable MB or GB string.
     */
    fun formatSizeBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            val gb = mb / 1024.0
            DecimalFormat("#.## GB").format(gb)
        } else {
            DecimalFormat("#.# MB").format(mb)
        }
    }

    /**
     * Safely purges cached map tiles for a world.
     *
     * IMPORTANT: Contains strict safety checks ensuring the target path is under `files/maps/`
     * and NEVER under `servers/worlds/`!
     */
    fun purgeCache(context: Context, worldName: String): Boolean {
        val mapDir = getMapDir(context, worldName)
        val expectedMapsRoot = File(context.filesDir, "maps").canonicalPath

        val targetCanonical = mapDir.canonicalPath
        if (!targetCanonical.startsWith(expectedMapsRoot)) {
            Log.e(TAG, "Safety check failed! Target path '$targetCanonical' is not inside '$expectedMapsRoot'")
            return false
        }

        return try {
            val success = mapDir.deleteRecursively()
            mapDir.mkdirs() // Recreate clean empty directory
            Log.i(TAG, "Successfully purged map cache for world '$worldName'")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to purge map cache: ${e.message}", e)
            false
        }
    }
}
