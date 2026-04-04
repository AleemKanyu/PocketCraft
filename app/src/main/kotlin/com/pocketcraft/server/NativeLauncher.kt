package com.pocketcraft.server

object NativeLauncher {
    private var libraryLoaded = false
    private var loadError: Throwable? = null

    fun loadLibrary(): Boolean {
        if (libraryLoaded) return true
        if (loadError != null) throw loadError!!

        return try {
            System.loadLibrary("launcher")
            libraryLoaded = true
            true
        } catch (e: UnsatisfiedLinkError) {
            loadError = e
            false
        }
    }

    /**
     * Boots the JVM in-process via dlopen().
     * Returns 0 on success, negative int on failure.
     * This call BLOCKS until the server stops.
     */
    external fun launchJVM(
        jrePath  : String,
        jarPath  : String,
        serverDir: String,
        tmpDir   : String,
        nativeLibDir: String,
        shimDir  : String,
        minRamMb : Int,
        maxRamMb : Int
    ): Int

    init {
        loadLibrary()
    }
}
