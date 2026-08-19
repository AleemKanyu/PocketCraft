package com.pockethost.app.data.model

/**
 * Snapshot of world metadata shown on the home screen.
 */
data class WorldDetails(
    val worldName: String = "world",
    val worldSeed: String = "",
    val serverVersion: String = "Paper 1.20.4",
    val totalPlaytimeTicks: Long = 0L,
    val playersWithStats: Int = 0
)
