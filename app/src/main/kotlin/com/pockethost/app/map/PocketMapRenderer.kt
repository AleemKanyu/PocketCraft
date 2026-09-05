package com.pockethost.app.map

import android.graphics.Bitmap
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.InflaterInputStream

/**
 * Lightweight surface-map renderer built for phones.
 *
 * Renders one Anvil region (512x512 blocks) into one PNG. For each column it reads the
 * surface height from the chunk's heightmap, decodes the single block sitting at that
 * height, maps it to a flat colour, and shades it against its northern neighbour so the
 * relief reads as depth. That is a handful of operations per column.
 *
 * The comparison that motivated this: a full block-model renderer produced ~20 tiles/min on
 * a Pixel 5 — about 3.3 hours and 340 MB for a 2000x2000 area. This approach covers the same
 * area in roughly a dozen region passes and a couple of MB, because it never builds geometry
 * or samples a texture.
 */
object PocketMapRenderer {
    private const val TAG = "PocketMapRenderer"

    /** A region is 32x32 chunks of 16 blocks: one pixel per block. */
    const val REGION_BLOCKS = 512

    private const val SECTORS = 4096
    private const val CHUNKS_PER_REGION_SIDE = 32

    /** Height step (in blocks) beyond which the shading uses its stronger contrast. */
    private const val STRONG_STEP = 3

    data class RegionResult(val bitmap: Bitmap, val columnsDrawn: Int)

    /** Parses "r.<x>.<z>.mca" into region coordinates. */
    fun regionCoords(file: File): Pair<Int, Int>? {
        val parts = file.name.split('.')
        if (parts.size < 4 || parts[0] != "r") return null
        val x = parts[1].toIntOrNull() ?: return null
        val z = parts[2].toIntOrNull() ?: return null
        return x to z
    }

    /**
     * Renders one region file. Returns null when the region holds no drawable columns,
     * which happens for region files that exist but were never populated.
     */
    fun renderRegion(mcaFile: File): RegionResult? {
        // Surface height and block name per pixel; kept as flat arrays so the shading pass
        // can look at the northern neighbour without re-decoding anything.
        val heights = IntArray(REGION_BLOCKS * REGION_BLOCKS) { Int.MIN_VALUE }
        val colors = IntArray(REGION_BLOCKS * REGION_BLOCKS)
        var drawn = 0

        try {
            RandomAccessFile(mcaFile, "r").use { raf ->
                val header = ByteArray(SECTORS)
                raf.readFully(header)

                for (index in 0 until CHUNKS_PER_REGION_SIDE * CHUNKS_PER_REGION_SIDE) {
                    val o = index * 4
                    val offset = ((header[o].toInt() and 0xFF) shl 16) or
                        ((header[o + 1].toInt() and 0xFF) shl 8) or
                        (header[o + 2].toInt() and 0xFF)
                    if (offset == 0 || (header[o + 3].toInt() and 0xFF) == 0) continue

                    val chunkX = index % CHUNKS_PER_REGION_SIDE
                    val chunkZ = index / CHUNKS_PER_REGION_SIDE

                    val nbt = readChunk(raf, offset) ?: continue
                    drawn += renderChunk(nbt, chunkX, chunkZ, heights, colors)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read ${mcaFile.name}: ${e.message}")
            return null
        }

        if (drawn == 0) return null
        return RegionResult(shadeToBitmap(heights, colors), drawn)
    }

    private fun readChunk(raf: RandomAccessFile, offset: Int): Map<String, Any>? = runCatching {
        raf.seek(offset.toLong() * SECTORS)
        val length = raf.readInt()
        val compression = raf.readByte().toInt()
        if (length <= 1) return null
        val payload = ByteArray(length - 1)
        raf.readFully(payload)
        // 1 = gzip, 2 = zlib; InflaterInputStream handles zlib, gzip needs the nowrap form.
        val raw = when (compression) {
            2 -> InflaterInputStream(ByteArrayInputStream(payload)).use { it.readBytes() }
            1 -> java.util.zip.GZIPInputStream(ByteArrayInputStream(payload)).use { it.readBytes() }
            else -> return null
        }
        NbtReader.parse(raw)
    }.getOrNull()

    @Suppress("UNCHECKED_CAST")
    private fun renderChunk(
        nbt: Map<String, Any>,
        chunkX: Int,
        chunkZ: Int,
        heights: IntArray,
        colors: IntArray
    ): Int {
        val heightmaps = nbt["Heightmaps"] as? Map<String, Any> ?: return 0
        val packed = (heightmaps["WORLD_SURFACE"] ?: heightmaps["MOTION_BLOCKING"]) as? LongArray ?: return 0
        if (packed.isEmpty()) return 0

        val minY = ((nbt["yPos"] as? Byte)?.toInt() ?: -4) * 16

        // Heightmap entries are sized to the world height; derive the width from the
        // array length rather than assuming 9 bits, so custom-height worlds still decode.
        val entriesPerLong = (256 + packed.size - 1) / packed.size
        val heightBits = 64 / entriesPerLong.coerceAtLeast(1)
        val surface = NbtReader.unpack(packed, 256, heightBits)

        // Index the sections by their Y so each column jumps straight to the right one.
        val sections = HashMap<Int, Map<String, Any>>()
        (nbt["sections"] as? List<Any>)?.forEach { entry ->
            val section = entry as? Map<String, Any> ?: return@forEach
            val y = (section["Y"] as? Byte)?.toInt() ?: return@forEach
            val states = section["block_states"] as? Map<String, Any> ?: return@forEach
            if (states["palette"] != null) sections[y] = states
        }

        var drawn = 0
        for (localZ in 0 until 16) {
            for (localX in 0 until 16) {
                val y = surface[localZ * 16 + localX] + minY - 1
                val states = sections[y shr 4] ?: continue
                val paletteList = states["palette"] as? List<Any> ?: continue
                if (paletteList.isEmpty()) continue

                val name = if (paletteList.size == 1) {
                    ((paletteList[0] as? Map<String, Any>)?.get("Name") as? String)
                } else {
                    val data = states["data"] as? LongArray ?: continue
                    val bits = maxOf(4, 32 - Integer.numberOfLeadingZeros(paletteList.size - 1))
                    val perLong = 64 / bits
                    val i = ((y and 15) * 16 + localZ) * 16 + localX
                    val li = i / perLong
                    if (li >= data.size) continue
                    val mask = (1L shl bits) - 1L
                    val v = ((data[li] ushr ((i % perLong) * bits)) and mask).toInt()
                    if (v >= paletteList.size) continue
                    ((paletteList[v] as? Map<String, Any>)?.get("Name") as? String)
                } ?: continue

                if (BlockPalette.isAir(name)) continue

                val px = chunkX * 16 + localX
                val pz = chunkZ * 16 + localZ
                if (px >= REGION_BLOCKS || pz >= REGION_BLOCKS) continue

                val idx = pz * REGION_BLOCKS + px
                heights[idx] = y
                colors[idx] = BlockPalette.colorOf(name)
                drawn++
            }
        }
        return drawn
    }

    /**
     * Applies relief shading and produces the tile.
     *
     * Comparing each column against the one to its north turns a flat colour field into
     * something that reads as terrain: slopes facing away darken, slopes facing toward
     * lighten. It costs one comparison per pixel and replaces real lighting entirely.
     */
    private fun shadeToBitmap(heights: IntArray, colors: IntArray): Bitmap {
        val pixels = IntArray(REGION_BLOCKS * REGION_BLOCKS)

        for (z in 0 until REGION_BLOCKS) {
            for (x in 0 until REGION_BLOCKS) {
                val idx = z * REGION_BLOCKS + x
                val h = heights[idx]
                if (h == Int.MIN_VALUE) {
                    pixels[idx] = 0 // transparent: nothing generated here
                    continue
                }

                var factor = 1.0f
                if (z > 0) {
                    val north = heights[idx - REGION_BLOCKS]
                    if (north != Int.MIN_VALUE) {
                        val delta = h - north
                        factor = when {
                            delta > STRONG_STEP -> 1.28f
                            delta > 0 -> 1.16f
                            delta < -STRONG_STEP -> 0.72f
                            delta < 0 -> 0.84f
                            else -> 1.0f
                        }
                    }
                }

                val rgb = colors[idx]
                val r = (((rgb shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
                val g = (((rgb shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
                val b = ((rgb and 0xFF) * factor).toInt().coerceIn(0, 255)
                pixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return Bitmap.createBitmap(pixels, REGION_BLOCKS, REGION_BLOCKS, Bitmap.Config.ARGB_8888)
    }
}
