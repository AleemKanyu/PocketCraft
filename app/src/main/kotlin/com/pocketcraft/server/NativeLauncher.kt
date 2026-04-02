package com.pocketcraft.server

object NativeLauncher {
    init {
        System.loadLibrary("launcher")
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
}
