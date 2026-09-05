package com.pockethost.app.map

import java.io.File
import kotlin.math.sqrt

/**
 * Validates chunk and coordinate eligibility against safety boundaries and physical region existence.
 */
object ChunkEligibilityChecker {

    const val DEFAULT_MAX_RADIUS_BLOCKS = 3000
    const val DEFAULT_MAX_RADIUS_CHUNKS = DEFAULT_MAX_RADIUS_BLOCKS / 16 // ~187 chunks

    /**
     * Checks if given block coordinates fall within the maximum rendering radius from origin (0,0).
     */
    fun isBlockWithinBoundary(
        blockX: Double,
        blockZ: Double,
        maxRadiusBlocks: Int = DEFAULT_MAX_RADIUS_BLOCKS
    ): Boolean {
        val distSq = (blockX * blockX) + (blockZ * blockZ)
        return distSq <= (maxRadiusBlocks.toDouble() * maxRadiusBlocks.toDouble())
    }

    /**
     * Checks if given chunk coordinates fall within the maximum chunk radius from origin (0,0).
     */
    fun isChunkWithinBoundary(
        chunkX: Int,
        chunkZ: Int,
        maxRadiusChunks: Int = DEFAULT_MAX_RADIUS_CHUNKS
    ): Boolean {
        val distSq = (chunkX * chunkX) + (chunkZ * chunkZ)
        return distSq <= (maxRadiusChunks * maxRadiusChunks)
    }

    /**
     * Calculates the region file coordinates for a given chunk.
     * Minecraft region files represent 32x32 chunks (r.rx.rz.mca).
     */
    fun chunkToRegion(chunkX: Int, chunkZ: Int): Pair<Int, Int> {
        val rx = chunkX shr 5 // chunkX / 32
        val rz = chunkZ shr 5 // chunkZ / 32
        return Pair(rx, rz)
    }

    /**
     * Checks if the physical region file for a given chunk exists on disk.
     */
    fun doesRegionExistForChunk(regionDir: File, chunkX: Int, chunkZ: Int): Boolean {
        val (rx, rz) = chunkToRegion(chunkX, chunkZ)
        val regionFile = File(regionDir, "r.$rx.$rz.mca")
        return regionFile.exists() && regionFile.isFile && regionFile.length() > 0L
    }

    /**
     * Calculates block distance from (0,0).
     */
    fun calculateDistance(x: Double, z: Double): Double {
        return sqrt((x * x) + (z * z))
    }
}
