package com.pocketcraft.server.server

enum class ServerTypeConfig(
    val id: String,
    val label: String,
    val description: String
) {
    PAPER(
        id = "paper",
        label = "Paper",
        description = "High-performance fork of Spigot. Best for plugins. Recommended."
    ),
    PURPUR(
        id = "purpur",
        label = "Purpur",
        description = "Fork of Paper with extra gameplay tweaks and configuration options."
    ),
    FABRIC(
        id = "fabric",
        label = "Fabric",
        description = "Lightweight modding server with Fabric loaders and vanilla versions."
    ),
    MODPACK(
        id = "modpack",
        label = "Modpack",
        description = "One-click install for popular modpacks like OneBlock Horror."
    );

    fun jarFileName(versionId: String): String = "$id-$versionId.jar"

    companion object {
        fun fromId(raw: String?): ServerTypeConfig =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: PAPER
    }
}
