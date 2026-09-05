package com.pockethost.app.map

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

/**
 * Drives [PocketMapRenderer] over a world's region files and maintains the tile pyramid the
 * viewer reads.
 *
 * Two things keep this cheap enough to run on the device that is also hosting the server:
 * work is bounded to regions within the configured radius, and a region is only re-rendered
 * when its `.mca` is newer than the tile produced from it. Re-opening the map after the
 * first pass therefore costs almost nothing.
 */
class PocketMapTiler(private val tilesRoot: File) {

    companion object {
        private const val TAG = "PocketMapTiler"

        /** Downscaled copies for zoomed-out viewing, as fractions of the full tile. */
        private val LOD_SIZES = intArrayOf(256, 128, 64)

        private const val PNG_QUALITY = 100
    }

    data class Progress(
        val done: Int = 0,
        val total: Int = 0,
        val running: Boolean = false,
        val message: String? = null
    ) {
        val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    }

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private var job: Job? = null

    fun dimensionDir(dimensionId: String): File {
        val slug = dimensionId.substringAfterLast(':').ifBlank { "overworld" }
        return File(tilesRoot, slug).also { it.mkdirs() }
    }

    /** Tile file for a region, at full resolution or one of the LOD levels. */
    fun tileFile(dimensionId: String, regionX: Int, regionZ: Int, size: Int = PocketMapRenderer.REGION_BLOCKS): File {
        val dir = if (size == PocketMapRenderer.REGION_BLOCKS) {
            dimensionDir(dimensionId)
        } else {
            File(dimensionDir(dimensionId), "lod$size").also { it.mkdirs() }
        }
        return File(dir, "r.$regionX.$regionZ.png")
    }

    /** Regions inside [radiusBlocks] of the origin, nearest first so the middle appears first. */
    private fun regionsInRange(regionDir: File, radiusBlocks: Int): List<File> {
        val maxRegion = (radiusBlocks + PocketMapRenderer.REGION_BLOCKS - 1) / PocketMapRenderer.REGION_BLOCKS
        return regionDir.listFiles { f -> f.isFile && f.extension == "mca" }
            ?.mapNotNull { file ->
                val coords = PocketMapRenderer.regionCoords(file) ?: return@mapNotNull null
                if (abs(coords.first) > maxRegion || abs(coords.second) > maxRegion) null
                else file to (coords.first * coords.first + coords.second * coords.second)
            }
            ?.sortedBy { it.second }
            ?.map { it.first }
            .orEmpty()
    }

    /**
     * Renders every in-range region whose tile is missing or stale.
     * Safe to call repeatedly; an in-flight pass is cancelled and replaced.
     */
    fun render(
        scope: CoroutineScope,
        dimensionId: String,
        regionDir: File,
        radiusBlocks: Int,
        onTileWritten: (() -> Unit)? = null
    ) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            val candidates = regionsInRange(regionDir, radiusBlocks)
            if (candidates.isEmpty()) {
                _progress.value = Progress(message = "No map data in range")
                return@launch
            }

            val stale = candidates.filter { mca ->
                val coords = PocketMapRenderer.regionCoords(mca) ?: return@filter false
                val tile = tileFile(dimensionId, coords.first, coords.second)
                !tile.isFile || tile.lastModified() < mca.lastModified()
            }

            if (stale.isEmpty()) {
                _progress.value = Progress(done = candidates.size, total = candidates.size, message = null)
                return@launch
            }

            _progress.value = Progress(0, stale.size, running = true, message = "Building map…")
            var done = 0
            var failed = 0

            for (mca in stale) {
                ensureActive()
                val coords = PocketMapRenderer.regionCoords(mca) ?: continue

                val result = PocketMapRenderer.renderRegion(mca)
                if (result == null) {
                    failed++
                } else {
                    writeTiles(dimensionId, coords.first, coords.second, result.bitmap)
                    result.bitmap.recycle()
                    onTileWritten?.invoke()
                }

                done++
                _progress.value = Progress(done, stale.size, running = true, message = "Building map…")
            }

            _progress.value = Progress(done, stale.size, running = false, message = null)
            Log.i(TAG, "Rendered $done region(s) for $dimensionId ($failed empty)")
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _progress.value = _progress.value.copy(running = false)
    }

    /** Writes the full-resolution tile plus its downscaled copies. */
    private fun writeTiles(dimensionId: String, regionX: Int, regionZ: Int, bitmap: Bitmap) {
        writePng(tileFile(dimensionId, regionX, regionZ), bitmap)
        for (size in LOD_SIZES) {
            val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
            writePng(tileFile(dimensionId, regionX, regionZ, size), scaled)
            scaled.recycle()
        }
    }

    private fun writePng(target: File, bitmap: Bitmap) {
        runCatching {
            // Write to a temp sibling first so the viewer never reads a half-written tile.
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }.onFailure { Log.w(TAG, "Failed writing ${target.name}: ${it.message}") }
    }

    /** Bounds of the rendered tiles, in region coordinates, for the viewer to frame on. */
    fun renderedBounds(dimensionId: String): IntArray? {
        val files = dimensionDir(dimensionId).listFiles { f -> f.isFile && f.extension == "png" } ?: return null
        if (files.isEmpty()) return null
        var minX = Int.MAX_VALUE; var maxX = Int.MIN_VALUE
        var minZ = Int.MAX_VALUE; var maxZ = Int.MIN_VALUE
        for (f in files) {
            val parts = f.nameWithoutExtension.split('.')
            val x = parts.getOrNull(1)?.toIntOrNull() ?: continue
            val z = parts.getOrNull(2)?.toIntOrNull() ?: continue
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (z < minZ) minZ = z
            if (z > maxZ) maxZ = z
        }
        if (minX == Int.MAX_VALUE) return null
        return intArrayOf(minX, minZ, maxX, maxZ)
    }
}
