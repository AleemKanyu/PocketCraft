package com.pocketcraft.server.data.model

/**
 * Represents a player currently connected to the server.
 */
data class PlayerInfo(
    val name: String,
    val uuid: String = "",
    val pingMs: Int = -1,
    val ip: String = "",
    val isOp: Boolean = false
) {
    val isBedrock: Boolean get() = name.startsWith(".")
}
