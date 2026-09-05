package com.pockethost.app.map

import android.util.Log
import java.io.File

/**
 * Standard Minecraft dimension types.
 */
enum class DimensionType(val standardId: String, val displayName: String, val iconEmoji: String) {
    OVERWORLD("minecraft:overworld", "Overworld", "🌍"),
    NETHER("minecraft:the_nether", "The Nether", "🔥"),
    THE_END("minecraft:the_end", "The End", "🌑"),
    CUSTOM("custom:dimension", "Custom", "🌌")
}

/**
 * Representation of a detected dimension on disk.
 */
data class DetectedDimension(
    val id: String,
    val displayName: String,
    val type: DimensionType,
    val regionDir: File,
    val regionFileCount: Int,
    val approxChunks: Int
)

object DimensionManager {
    private const val TAG = "DimensionManager"

    /**
     * Inspects the server storage directory for a specific world and returns only dimensions
     * that physically exist and contain generated chunks.
     *
     * IMPORTANT: This NEVER triggers terrain generation. It is purely a read-only filesystem check.
     */
    fun discoverDimensions(serverDir: File, worldName: String): List<DetectedDimension> {
        val detected = mutableListOf<DetectedDimension>()
        if (!serverDir.exists() || !serverDir.isDirectory) return emptyList()

        // 1. Overworld Detection
        // Standard Paper/Spigot path: serverDir/<worldName>/region or serverDir/world/region
        val overworldDirs = listOf(
            File(serverDir, "$worldName/dimensions/minecraft/overworld/region"),
            File(serverDir, "world/dimensions/minecraft/overworld/region"),
            File(serverDir, "$worldName/region"),
            File(serverDir, "world/region")
        )
        val overworldRegionDir = overworldDirs.firstOrNull { it.exists() && it.isDirectory }
        if (overworldRegionDir != null) {
            val mcaFiles = overworldRegionDir.listFiles { f -> f.isFile && f.extension == "mca" } ?: emptyArray()
            if (mcaFiles.isNotEmpty()) {
                detected.add(
                    DetectedDimension(
                        id = "minecraft:overworld",
                        displayName = "Overworld",
                        type = DimensionType.OVERWORLD,
                        regionDir = overworldRegionDir,
                        regionFileCount = mcaFiles.size,
                        approxChunks = estimateChunksFromRegions(mcaFiles)
                    )
                )
            }
        }

        // 2. Nether Detection
        // Spigot/Paper: serverDir/<worldName>_nether/DIM-1/region
        // Fabric/Vanilla: serverDir/<worldName>/DIM-1/region
        val netherDirs = listOf(
            File(serverDir, "${worldName}_nether/DIM-1/region"),
            File(serverDir, "world_nether/DIM-1/region"),
            File(serverDir, "$worldName/DIM-1/region"),
            File(serverDir, "world/DIM-1/region")
        )
        val netherRegionDir = netherDirs.firstOrNull { it.exists() && it.isDirectory }
        if (netherRegionDir != null) {
            val mcaFiles = netherRegionDir.listFiles { f -> f.isFile && f.extension == "mca" } ?: emptyArray()
            if (mcaFiles.isNotEmpty()) {
                detected.add(
                    DetectedDimension(
                        id = "minecraft:the_nether",
                        displayName = "Nether",
                        type = DimensionType.NETHER,
                        regionDir = netherRegionDir,
                        regionFileCount = mcaFiles.size,
                        approxChunks = estimateChunksFromRegions(mcaFiles)
                    )
                )
            }
        }

        // 3. The End Detection
        // Spigot/Paper: serverDir/<worldName>_the_end/DIM1/region
        // Fabric/Vanilla: serverDir/<worldName>/DIM1/region
        val endDirs = listOf(
            File(serverDir, "${worldName}_the_end/DIM1/region"),
            File(serverDir, "world_the_end/DIM1/region"),
            File(serverDir, "$worldName/DIM1/region"),
            File(serverDir, "world/DIM1/region")
        )
        val endRegionDir = endDirs.firstOrNull { it.exists() && it.isDirectory }
        if (endRegionDir != null) {
            val mcaFiles = endRegionDir.listFiles { f -> f.isFile && f.extension == "mca" } ?: emptyArray()
            if (mcaFiles.isNotEmpty()) {
                detected.add(
                    DetectedDimension(
                        id = "minecraft:the_end",
                        displayName = "The End",
                        type = DimensionType.THE_END,
                        regionDir = endRegionDir,
                        regionFileCount = mcaFiles.size,
                        approxChunks = estimateChunksFromRegions(mcaFiles)
                    )
                )
            }
        }

        // 4. Custom / Modded Dimensions Detection (e.g. dimensions/<namespace>/<name>/region)
        val customDimensionsRoot = File(serverDir, "$worldName/dimensions")
        if (customDimensionsRoot.exists() && customDimensionsRoot.isDirectory) {
            customDimensionsRoot.walkTopDown()
                .maxDepth(3)
                .filter { it.isDirectory && it.name == "region" }
                .forEach { regDir ->
                    val mcaFiles = regDir.listFiles { f -> f.isFile && f.extension == "mca" } ?: emptyArray()
                    if (mcaFiles.isNotEmpty()) {
                        val dimensionName = regDir.parentFile?.name ?: "Custom"
                        val namespace = regDir.parentFile?.parentFile?.name ?: "custom"
                        val customId = "$namespace:$dimensionName"
                        // Avoid duplicates if already registered
                        if (detected.none { it.id.equals(customId, ignoreCase = true) }) {
                            detected.add(
                                DetectedDimension(
                                    id = customId,
                                    displayName = dimensionName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                                    type = DimensionType.CUSTOM,
                                    regionDir = regDir,
                                    regionFileCount = mcaFiles.size,
                                    approxChunks = estimateChunksFromRegions(mcaFiles)
                                )
                            )
                        }
                    }
                }
        }

        Log.d(TAG, "Discovered ${detected.size} dimensions for world $worldName: ${detected.map { it.displayName }}")
        return detected
    }

    /**
     * Estimates chunk count based on region file sizes (each chunk is ~4-16KB within .mca header).
     */
    private fun estimateChunksFromRegions(mcaFiles: Array<File>): Int {
        // Average rough approximation: each .mca file holds up to 1024 chunks (32x32)
        // Typically populated region files contain around 200-600 inhabited chunks.
        val totalSizeKb = mcaFiles.sumOf { it.length() } / 1024
        return (totalSizeKb / 8).coerceAtLeast(mcaFiles.size.toLong()).toInt()
    }
}
