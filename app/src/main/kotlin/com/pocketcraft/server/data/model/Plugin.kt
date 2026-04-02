package com.pocketcraft.server.data.model

data class Plugin(
    val name: String,
    val fileName: String,
    val sizeMb: Float,
    val enabled: Boolean = true,
    val version: String = ""
)
