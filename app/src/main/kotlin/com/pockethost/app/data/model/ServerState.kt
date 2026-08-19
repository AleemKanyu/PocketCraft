package com.pockethost.app.data.model

/**
 * Represents the live state of the Minecraft server.
 */
sealed class ServerState {
    /** Server process is not running. */
    object Stopped : ServerState()

    /** Server is starting up (process launched, waiting for "Done!" line). */
    object Starting : ServerState()

    /** Server is fully running and accepting connections. */
    data class Running(
        val playerCount: Int = 0,
        val maxPlayers: Int = 10,
        val tps: Float = 20f,
        val uptimeSeconds: Long = 0L,
        val localIp: String = "",
        val port: Int = 25565,
        val ramUsedMb: Long = 0L,
        val ramLimitMb: Long = 0L
    ) : ServerState()

    /** Server encountered a fatal error. */
    data class Error(val message: String) : ServerState()
}
