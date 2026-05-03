package com.pocketcraft.server.data.model

enum class ServerType(val displayName: String, val supportsVersionSelect: Boolean) {
    PAPER("Paper", true),
    PURPUR("Purpur", true),
    FABRIC("Fabric", true),
    MODPACK("Modpack", false),
    CUSTOM_JAR("Custom JAR", false);

    companion object {
        fun fromString(value: String?): ServerType {
            return values().find { it.name == value } ?: PAPER
        }
    }
}
