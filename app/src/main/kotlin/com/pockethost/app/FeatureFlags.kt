package com.pockethost.app

/**
 * Compile-time switches for features that are built but not yet shipped.
 */
object FeatureFlags {

    /**
     * The in-app world map.
     *
     * The renderer, tiler and viewer all work (see `com.pockethost.app.map`), but the
     * feature is parked until it can be finished on its own branch. Flipping this back to
     * true restores the home-screen entry point and the map page; nothing else needs to
     * change.
     */
    const val WORLD_MAP_ENABLED = false
}
