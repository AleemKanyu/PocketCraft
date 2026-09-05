package com.pockethost.app.map

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.InflaterInputStream
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance, read-only Minecraft Anvil (.mca) terrain extractor.
 * Reads real terrain elevation and block distribution from existing .mca region files.
 * Operates strictly offline, never locks world files, and never triggers world generation.
 */
object AnvilTerrainExtractor {
    private const val TAG = "AnvilTerrainExtractor"

    private const val TAG_INT: Byte = 3
    private const val TAG_LONG_ARRAY: Byte = 12
    private val HEIGHTMAP_TAGS = listOf("WORLD_SURFACE", "MOTION_BLOCKING")

    private const val SEA_LEVEL = 62
    private const val STONE_LINE = 105
    private const val SNOW_LINE = 140

    // Block color palette (RGB integers)
    private const val COLOR_GRASS = 0x5b8c3a
    private const val COLOR_WATER = 0x2e5c8a
    private const val COLOR_SAND = 0xd2b55b
    private const val COLOR_STONE = 0x6e6e6e
    private const val COLOR_SNOW = 0xe8eff5
    private const val COLOR_DIRT = 0x866043
    private const val COLOR_WOOD = 0x855f3a
    private const val COLOR_LEAVES = 0x3d6625
    private const val COLOR_NETHERRACK = 0x691f1f
    private const val COLOR_LAVA = 0xd45014
    private const val COLOR_ENDSTONE = 0xdadb9c
    private const val COLOR_VOID = 0x0a0e14

    data class TerrainMesh(
        val dimensionId: String,
        val gridSize: Int,
        val minHeight: Int,
        val maxHeight: Int,
        /** World block X of grid cell (0, 0), so the viewer can place the mesh in world space. */
        val originX: Int,
        /** World block Z of grid cell (0, 0). */
        val originZ: Int,
        val heights: IntArray,
        val colors: IntArray
    ) {
        fun toJsonString(): String {
            val sb = StringBuilder(gridSize * gridSize * 10 + 256)
            sb.append("{\"dimension\":\"").append(dimensionId)
            sb.append("\",\"gridSize\":").append(gridSize)
            sb.append(",\"minHeight\":").append(minHeight)
            sb.append(",\"maxHeight\":").append(maxHeight)
            sb.append(",\"originX\":").append(originX)
            sb.append(",\"originZ\":").append(originZ)
            sb.append(",\"heights\":[")
            for (i in heights.indices) {
                if (i > 0) sb.append(',')
                sb.append(heights[i])
            }
            sb.append("],\"colors\":[")
            for (i in colors.indices) {
                if (i > 0) sb.append(',')
                sb.append(colors[i])
            }
            sb.append("]}")
            return sb.toString()
        }
    }

    /**
     * Extracts a 64x64 block elevation mesh for the given dimension.
     */
    fun extractTerrain(regionDir: File, dimensionId: String, targetGridSize: Int = 64): TerrainMesh {
        if (!regionDir.exists() || !regionDir.isDirectory) {
            return generateProceduralTerrain(dimensionId, targetGridSize)
        }

        val mcaFiles = regionDir.listFiles { f -> f.isFile && f.extension == "mca" } ?: emptyArray()
        if (mcaFiles.isEmpty()) {
            return generateProceduralTerrain(dimensionId, targetGridSize)
        }

        // Prefer the regions around the world origin, but skip stubs that carry no chunk data.
        // Fall back to the largest region file so an off-origin world still renders something.
        val centreNames = listOf("r.0.0.mca", "r.-1.-1.mca", "r.-1.0.mca", "r.0.-1.mca")
        val preferredFile = centreNames
            .firstNotNullOfOrNull { name -> mcaFiles.firstOrNull { it.name == name && it.length() > 8192L } }
            ?: mcaFiles.maxByOrNull { it.length() }
            ?: mcaFiles.first()

        return try {
            parseRegionFile(preferredFile, dimensionId, targetGridSize)
        } catch (e: Exception) {
            Log.w(TAG, "Error reading region file ${preferredFile.name}, falling back to procedural: ${e.message}")
            generateProceduralTerrain(dimensionId, targetGridSize)
        }
    }

    /** Parses "r.<x>.<z>.mca" into its region coordinates, defaulting to (0, 0). */
    private fun regionCoords(mcaFile: File): Pair<Int, Int> {
        val parts = mcaFile.name.split('.')
        val rx = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val rz = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return rx to rz
    }

    private fun parseRegionFile(mcaFile: File, dimensionId: String, gridSize: Int): TerrainMesh {
        val totalCells = gridSize * gridSize
        val isNether = dimensionId.contains("nether", ignoreCase = true)
        val isEnd = dimensionId.contains("end", ignoreCase = true)

        // The Overworld was extended down to -64 in 1.18; the Nether and the End still start at 0.
        val minY = if (isNether || isEnd) 0 else -64
        val defaultColor = when {
            isNether -> COLOR_NETHERRACK
            isEnd -> COLOR_VOID
            else -> COLOR_GRASS
        }
        val defaultHeight = when {
            isNether -> 64
            isEnd -> 0
            else -> 64
        }

        val heights = IntArray(totalCells) { defaultHeight }
        val colors = IntArray(totalCells) { defaultColor }
        val filled = BooleanArray(totalCells)
        var originX = 0
        var originZ = 0

        RandomAccessFile(mcaFile, "r").use { raf ->
            val header = ByteArray(4096)
            raf.readFully(header)

            // gridSize blocks wide == gridSize/16 chunks wide (4 chunks for the default 64).
            val chunksPerSide = min(gridSize / 16, 32)
            val (baseCx, baseCz) = pickDensestChunkWindow(header, chunksPerSide)
            val (regionX, regionZ) = regionCoords(mcaFile)
            originX = (regionX * 32 + baseCx) * 16
            originZ = (regionZ * 32 + baseCz) * 16

            for (dz in 0 until chunksPerSide) {
                for (dx in 0 until chunksPerSide) {
                    val cx = baseCx + dx
                    val cz = baseCz + dz
                    val index = (cx + cz * 32) * 4
                    val offset = ((header[index].toInt() and 0xFF) shl 16) or
                        ((header[index + 1].toInt() and 0xFF) shl 8) or
                        (header[index + 2].toInt() and 0xFF)
                    val sectors = header[index + 3].toInt() and 0xFF

                    if (offset == 0 || sectors == 0) continue

                    try {
                        raf.seek(offset.toLong() * 4096L)
                        val length = raf.readInt()
                        val compression = raf.readByte().toInt()
                        if (length <= 1 || (compression != 1 && compression != 2)) continue

                        val compressedData = ByteArray(length - 1)
                        raf.readFully(compressedData)

                        val chunkHeights = extractChunkHeightmap(compressedData, minY)
                        if (chunkHeights != null) {
                            for (lz in 0 until 16) {
                                for (lx in 0 until 16) {
                                    val gx = dx * 16 + lx
                                    val gz = dz * 16 + lz
                                    if (gx < gridSize && gz < gridSize) {
                                        val gIndex = gz * gridSize + gx
                                        val h = chunkHeights[lz * 16 + lx]
                                        heights[gIndex] = h

                                        filled[gIndex] = true
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Skip individual malformed chunk silently
                    }
                }
            }
        }

        colorize(heights, colors, filled, gridSize, isNether, isEnd, minY, defaultColor)

        var minH = 320
        var maxH = -64
        for (h in heights) {
            if (h < minH) minH = h
            if (h > maxH) maxH = h
        }

        return TerrainMesh(
            dimensionId = dimensionId,
            gridSize = gridSize,
            minHeight = minH,
            maxHeight = maxH,
            originX = originX,
            originZ = originZ,
            heights = heights,
            colors = colors
        )
    }

    /**
     * Assigns a colour per grid cell. Elevation alone cannot identify a block, so the
     * Overworld uses elevation bands plus a neighbour check: sand is painted only where a
     * near-sea-level cell actually touches water, which keeps inland plains green instead
     * of turning every cell at y=63 into beach.
     */
    private fun colorize(
        heights: IntArray,
        colors: IntArray,
        filled: BooleanArray,
        gridSize: Int,
        isNether: Boolean,
        isEnd: Boolean,
        minY: Int,
        defaultColor: Int
    ) {
        for (z in 0 until gridSize) {
            for (x in 0 until gridSize) {
                val i = z * gridSize + x
                if (!filled[i]) {
                    colors[i] = defaultColor
                    continue
                }
                val h = heights[i]

                colors[i] = when {
                    isNether -> if (h <= minY + 32) COLOR_LAVA else COLOR_NETHERRACK
                    isEnd -> if (h <= minY + 10) COLOR_VOID else COLOR_ENDSTONE
                    h <= SEA_LEVEL -> COLOR_WATER
                    h <= SEA_LEVEL + 2 && touchesWater(heights, filled, gridSize, x, z) -> COLOR_SAND
                    h > SNOW_LINE -> COLOR_SNOW
                    h > STONE_LINE -> COLOR_STONE
                    else -> COLOR_GRASS
                }
            }
        }
    }

    /** True when any of the four orthogonal neighbours sits at or below sea level. */
    private fun touchesWater(
        heights: IntArray,
        filled: BooleanArray,
        gridSize: Int,
        x: Int,
        z: Int
    ): Boolean {
        val offsets = arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        for ((dx, dz) in offsets) {
            val nx = x + dx
            val nz = z + dz
            if (nx !in 0 until gridSize || nz !in 0 until gridSize) continue
            val ni = nz * gridSize + nx
            if (filled[ni] && heights[ni] <= SEA_LEVEL) return true
        }
        return false
    }

    /**
     * Region files are 32x32 chunks and the corner at (0,0) is often unexplored. Slides a
     * [windowChunks] square over the region's location header and returns the top-left chunk
     * of the window holding the most generated chunks, so the map opens on real terrain.
     */
    private fun pickDensestChunkWindow(header: ByteArray, windowChunks: Int): Pair<Int, Int> {
        val span = min(windowChunks, 32)
        val populated = Array(32) { cz ->
            BooleanArray(32) { cx ->
                val i = (cx + cz * 32) * 4
                val offset = ((header[i].toInt() and 0xFF) shl 16) or
                    ((header[i + 1].toInt() and 0xFF) shl 8) or
                    (header[i + 2].toInt() and 0xFF)
                offset != 0 && (header[i + 3].toInt() and 0xFF) != 0
            }
        }

        var bestCx = 0
        var bestCz = 0
        var bestCount = -1
        for (cz in 0..(32 - span)) {
            for (cx in 0..(32 - span)) {
                var count = 0
                for (dz in 0 until span) {
                    for (dx in 0 until span) {
                        if (populated[cz + dz][cx + dx]) count++
                    }
                }
                if (count > bestCount) {
                    bestCount = count
                    bestCx = cx
                    bestCz = cz
                }
            }
        }
        return bestCx to bestCz
    }

    /**
     * Locates the WORLD_SURFACE (or MOTION_BLOCKING) heightmap inside a chunk's NBT and
     * decodes it into 256 absolute world Y values, row-major by (z * 16 + x).
     *
     * The tag is found by its full NBT header (type byte + big-endian name length + name)
     * rather than by scanning for a bare name, so the element count that follows is the
     * real one instead of whatever int happens to sit nearby.
     */
    private fun extractChunkHeightmap(compressedData: ByteArray, fallbackMinY: Int): IntArray? {
        val rawNbt = InflaterInputStream(ByteArrayInputStream(compressedData)).use { it.readBytes() }

        // Chunks record their lowest section index; minY = yPos * 16. Reading it keeps
        // custom-height worlds (datapacks, mods) correct instead of assuming vanilla limits.
        val minY = readChunkMinY(rawNbt) ?: fallbackMinY

        for (tag in HEIGHTMAP_TAGS) {
            val nameBytes = tag.toByteArray(Charsets.UTF_8)
            val header = ByteArray(3 + nameBytes.size)
            header[0] = TAG_LONG_ARRAY
            header[1] = ((nameBytes.size shr 8) and 0xFF).toByte()
            header[2] = (nameBytes.size and 0xFF).toByte()
            nameBytes.copyInto(header, 3)

            val at = indexOfBytes(rawNbt, header)
            if (at < 0) continue

            val lengthPos = at + header.size
            if (lengthPos + 4 > rawNbt.size) continue
            val arrayLength = readInt(rawNbt, lengthPos)

            // 256 entries packed without straddling: 37 longs at 9 bits, 32 at 8, 26 at 10.
            if (arrayLength !in 20..64) continue
            val longOffset = lengthPos + 4
            if (longOffset + arrayLength * 8 > rawNbt.size) continue

            val longs = LongArray(arrayLength)
            for (li in 0 until arrayLength) {
                val bytePos = longOffset + li * 8
                var lVal = 0L
                for (b in 0..7) {
                    lVal = (lVal shl 8) or (rawNbt[bytePos + b].toLong() and 0xFFL)
                }
                longs[li] = lVal
            }
            return unpackHeights(longs, minY)
        }

        return null
    }

    /** Reads the chunk's `yPos` TAG_Int and converts it to the world's minimum Y. */
    private fun readChunkMinY(rawNbt: ByteArray): Int? {
        val header = byteArrayOf(TAG_INT, 0x00, 0x04, 'y'.code.toByte(), 'P'.code.toByte(), 'o'.code.toByte(), 's'.code.toByte())
        val at = indexOfBytes(rawNbt, header)
        if (at < 0 || at + header.size + 4 > rawNbt.size) return null
        val yPos = readInt(rawNbt, at + header.size)
        if (yPos < -32 || yPos > 32) return null
        return yPos * 16
    }

    private fun readInt(buf: ByteArray, pos: Int): Int =
        ((buf[pos].toInt() and 0xFF) shl 24) or
            ((buf[pos + 1].toInt() and 0xFF) shl 16) or
            ((buf[pos + 2].toInt() and 0xFF) shl 8) or
            (buf[pos + 3].toInt() and 0xFF)

    /**
     * Unpacks a Minecraft 1.16+ packed long array. Entries never straddle a long boundary:
     * each long carries floor(64 / bitsPerEntry) entries and the leftover high bits are padding.
     * The stored value is (highestNonAirY + 1 - minY), so the surface Y is value + minY - 1.
     */
    private fun unpackHeights(longs: LongArray, minY: Int): IntArray {
        val result = IntArray(256)
        val entriesPerLong = max(1, (256 + longs.size - 1) / longs.size)
        val bitsPerEntry = 64 / entriesPerLong
        val mask = (1L shl bitsPerEntry) - 1L
        val maxY = minY + (1 shl bitsPerEntry) - 1

        for (i in 0 until 256) {
            val longIndex = i / entriesPerLong
            if (longIndex >= longs.size) {
                result[i] = minY
                continue
            }
            val bitOffset = (i % entriesPerLong) * bitsPerEntry
            val rawVal = ((longs[longIndex] ushr bitOffset) and mask).toInt()
            result[i] = (rawVal + minY - 1).coerceIn(minY, maxY)
        }
        return result
    }

    private fun indexOfBytes(source: ByteArray, target: ByteArray): Int {
        if (target.isEmpty() || source.size < target.size) return -1
        outer@ for (i in 0..(source.size - target.size)) {
            for (j in target.indices) {
                if (source[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }

    /**
     * Fallback procedural terrain generator if world has no region files yet.
     */
    fun generateProceduralTerrain(dimensionId: String, size: Int): TerrainMesh {
        val heights = IntArray(size * size)
        val colors = IntArray(size * size)
        val isNether = dimensionId.contains("nether", ignoreCase = true)
        val isEnd = dimensionId.contains("end", ignoreCase = true)

        val half = size / 2.0
        for (z in 0 until size) {
            for (x in 0 until size) {
                val idx = z * size + x
                val dx = (x - half) / half
                val dz = (z - half) / half
                val dist = Math.sqrt(dx * dx + dz * dz)

                val h = when {
                    isNether -> {
                        val n1 = Math.sin(x * 0.15) * Math.cos(z * 0.15) * 15.0
                        val n2 = Math.sin(x * 0.05 + z * 0.05) * 20.0
                        (45 + n1 + n2).toInt().coerceIn(28, 90)
                    }
                    isEnd -> {
                        if (dist > 0.75) 0
                        else {
                            val island = (1.0 - (dist / 0.75)) * 40.0
                            val noise = Math.sin(x * 0.2) * Math.cos(z * 0.2) * 6.0
                            (30 + island + noise).toInt().coerceAtLeast(0)
                        }
                    }
                    else -> {
                        // Overworld island / continent
                        val continent = (1.0 - dist * 0.8).coerceAtLeast(0.0)
                        val hill1 = Math.sin(x * 0.08) * Math.cos(z * 0.08) * 16.0
                        val hill2 = Math.sin(x * 0.03 + z * 0.04) * 24.0
                        (60 + continent * 20 + hill1 + hill2).toInt().coerceIn(40, 160)
                    }
                }

                heights[idx] = h
                colors[idx] = when {
                    isNether -> if (h <= 32) COLOR_LAVA else COLOR_NETHERRACK
                    isEnd -> if (h <= 10) COLOR_VOID else COLOR_ENDSTONE
                    else -> when {
                        h <= 62 -> COLOR_WATER
                        h in 63..64 -> COLOR_SAND
                        h > 120 -> COLOR_SNOW
                        h > 95 -> COLOR_STONE
                        else -> COLOR_GRASS
                    }
                }
            }
        }

        return TerrainMesh(
            dimensionId = dimensionId,
            gridSize = size,
            minHeight = heights.minOrNull() ?: 50,
            maxHeight = heights.maxOrNull() ?: 120,
            originX = -size / 2,
            originZ = -size / 2,
            heights = heights,
            colors = colors
        )
    }
}
