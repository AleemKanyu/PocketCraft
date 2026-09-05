package com.pockethost.app.map

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * State of the map rendering engine.
 */
enum class MapState {
    READY,
    RENDERING,
    PAUSED,
    ERROR,
    OFFLINE,
    DISABLED
}

/**
 * Snapshot of the current map render status.
 */
data class MapRenderStatus(
    val state: MapState = MapState.READY,
    val message: String = "Map Ready",
    val progressPercent: Float = 100f,
    val queuedTasks: Int = 0,
    val isServerBusy: Boolean = false,
    val errorDetail: String? = null
)

/**
 * Abstraction interface for Minecraft map renderers (e.g. BlueMap, OfflineRenderer).
 */
interface MapRenderer {
    /**
     * Initializes renderer configuration for a specific world.
     */
    suspend fun configureForWorld(context: Context, worldName: String, serverDir: File, mapDir: File)

    /**
     * Checks if the renderer is installed and available.
     */
    fun isAvailable(context: Context, serverDir: File): Boolean

    /**
     * Pauses terrain rendering (e.g. during high server load).
     */
    fun pauseRendering(onCommand: (String) -> Unit)

    /**
     * Resumes terrain rendering.
     */
    fun resumeRendering(onCommand: (String) -> Unit)

    /**
     * Cancels any pending render queue items.
     */
    fun cancelRendering(onCommand: (String) -> Unit)

    /**
     * Observable render status.
     */
    val renderStatus: StateFlow<MapRenderStatus>
}
