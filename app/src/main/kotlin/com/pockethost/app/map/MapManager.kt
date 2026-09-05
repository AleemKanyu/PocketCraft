package com.pockethost.app.map

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Default render radius in blocks: 1000 covers a 2000x2000 area around spawn.
 */
const val DEFAULT_RENDER_RADIUS_BLOCKS = 1000

/**
 * Central orchestrator for the PocketHost World Map.
 *
 * The map is rendered on the device by [PocketMapTiler]. An earlier build drove the BlueMap
 * server plugin instead; measured on a Pixel 5 that managed ~20 tiles/min, which works out to
 * roughly 3.3 hours and ~340 MB for a 2000x2000 area, because it resolves block models and
 * builds real geometry. The renderer here collapses every column to one shaded colour, which
 * covers the same area in seconds and a couple of MB — at the cost of being a shaded
 * top-down view rather than a true 3D scene.
 */
class MapManager(
    private val context: Context,
    private val onSendCommand: (String) -> Unit
) {
    companion object {
        private const val TAG = "MapManager"

        /** Bundled canvas viewer; it reads tiles from the loopback server. */
        const val VIEWER_URL = "file:///android_asset/map/pocketmap.html"
    }

    private val localServer = MapLocalServer(context)

    private val _availableDimensions = MutableStateFlow<List<DetectedDimension>>(emptyList())
    val availableDimensions: StateFlow<List<DetectedDimension>> = _availableDimensions.asStateFlow()

    private val _activeDimension = MutableStateFlow<DetectedDimension?>(null)
    val activeDimension: StateFlow<DetectedDimension?> = _activeDimension.asStateFlow()

    private val _is3dMode = MutableStateFlow(true)
    val is3dMode: StateFlow<Boolean> = _is3dMode.asStateFlow()

    private val _currentCoordinates = MutableStateFlow(Triple(0.0, 64.0, 0.0))
    val currentCoordinates: StateFlow<Triple<Double, Double, Double>> = _currentCoordinates.asStateFlow()

    private val _renderRadius = MutableStateFlow(DEFAULT_RENDER_RADIUS_BLOCKS)
    val renderRadius: StateFlow<Int> = _renderRadius.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _renderStatus = MutableStateFlow(MapRenderStatus())
    val renderStatus: StateFlow<MapRenderStatus> = _renderStatus.asStateFlow()

    /** True once the loopback file server is accepting connections. */
    private val _localServerReady = MutableStateFlow(false)
    val localServerReady: StateFlow<Boolean> = _localServerReady.asStateFlow()

    private var tiler: PocketMapTiler? = null
    private var activeWorldName: String = "world"
    private var renderScope: CoroutineScope? = null

    fun initialize(
        scope: CoroutineScope,
        worldName: String,
        serverDir: File,
        isServerActive: () -> Boolean,
        currentTps: () -> Double,
        getOnlinePlayers: () -> List<Map<String, Any>>
    ) {
        activeWorldName = worldName
        renderScope = scope

        val mapDir = MapCacheManager.getMapDir(context, worldName)
        val tilesRoot = File(mapDir, "web/tiles").also { it.mkdirs() }
        val activeTiler = PocketMapTiler(tilesRoot)
        tiler = activeTiler

        // Mirror render progress into the status the UI already renders.
        scope.launch {
            activeTiler.progress.collect { p ->
                _renderStatus.value = when {
                    p.running -> MapRenderStatus(
                        state = MapState.RENDERING,
                        message = "Building map ${p.done}/${p.total}",
                        progressPercent = p.fraction * 100f,
                        queuedTasks = (p.total - p.done).coerceAtLeast(0)
                    )
                    p.message != null -> MapRenderStatus(state = MapState.OFFLINE, message = p.message)
                    else -> MapRenderStatus(state = MapState.READY, message = "Map Ready")
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            // Serve tiles before anything points a WebView at the port.
            localServer.start(worldName, getOnlinePlayers)
            _localServerReady.value = true

            val dims = DimensionManager.discoverDimensions(serverDir, worldName)
            _availableDimensions.value = dims
            val initial = dims.firstOrNull { it.type == DimensionType.OVERWORLD } ?: dims.firstOrNull()
            _activeDimension.value = initial

            _cacheSizeBytes.value = MapCacheManager.getCacheSizeBytes(context, worldName)

            initial?.let { renderDimension(scope, it) }

            Log.i(TAG, "MapManager initialized for '$worldName' with ${dims.size} dimensions.")
        }
    }

    /** Renders (or refreshes) the tiles for one dimension. */
    private fun renderDimension(scope: CoroutineScope, dimension: DetectedDimension) {
        val activeTiler = tiler ?: return
        activeTiler.render(
            scope = scope,
            dimensionId = dimension.id,
            regionDir = dimension.regionDir,
            radiusBlocks = _renderRadius.value,
            onTileWritten = {
                _cacheSizeBytes.value = MapCacheManager.getCacheSizeBytes(context, activeWorldName)
            }
        )
    }

    /** URL for the map WebView. Always the bundled viewer; tiles arrive over loopback. */
    fun resolveMapUrl(isServerOnline: Boolean): String? =
        if (_localServerReady.value) VIEWER_URL else null

    fun switchDimension(dimension: DetectedDimension) {
        _activeDimension.value = dimension
        renderScope?.let { renderDimension(it, dimension) }
        Log.d(TAG, "Switched active map dimension to: ${dimension.displayName}")
    }

    /** Re-renders the active dimension, picking up world changes since the last pass. */
    fun refresh() {
        val scope = renderScope ?: return
        val dimension = _activeDimension.value ?: return
        renderDimension(scope, dimension)
    }

    fun set3dMode(enabled: Boolean) {
        _is3dMode.value = enabled
    }

    fun updateCoordinates(x: Double, y: Double, z: Double) {
        _currentCoordinates.value = Triple(x, y, z)
    }

    fun updateRenderRadius(scope: CoroutineScope, newRadius: Int) {
        val clamped = newRadius.coerceIn(500, 10000)
        if (clamped == _renderRadius.value) return
        _renderRadius.value = clamped
        _activeDimension.value?.let { renderDimension(scope, it) }
    }

    fun purgeCache(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            tiler?.cancel()
            MapCacheManager.purgeCache(context, activeWorldName)
            _cacheSizeBytes.value = 0L
            _activeDimension.value?.let { renderDimension(scope, it) }
        }
    }

    fun onConsoleLine(line: String) {
        // The on-device renderer has no server-side counterpart to listen to.
    }

    fun pauseRendering() {
        tiler?.cancel()
    }

    fun resumeRendering() {
        refresh()
    }

    fun shutdown() {
        tiler?.cancel()
        localServer.stop()
        _localServerReady.value = false
        renderScope = null
        Log.i(TAG, "MapManager shut down.")
    }
}
