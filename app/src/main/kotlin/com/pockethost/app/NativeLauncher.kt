package com.pockethost.app

object NativeLauncher {
    private var libraryLoaded = false
    private var loadError: Throwable? = null

    @Volatile
    var hasInProcessJvmRunInThisProcess: Boolean = false

    fun loadLibrary(): Boolean {
        if (libraryLoaded) return true

        return try {
            System.loadLibrary("launcher")
            libraryLoaded = true
            true
        } catch (e: Throwable) {
            loadError = e
            false
        }
    }

    external fun notifyShutdownStarted()

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
        maxRamMb : Int,
        serverType: String,
        port: Int
    ): Int

    init {
        loadLibrary()
    }
}
