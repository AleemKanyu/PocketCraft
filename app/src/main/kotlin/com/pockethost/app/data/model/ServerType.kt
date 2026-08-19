package com.pockethost.app.data.model

enum class ServerType(val displayName: String, val supportsVersionSelect: Boolean) {
    VANILLA("Vanilla", true),
    PAPER("Paper", true),
    PURPUR("Purpur", true),
    FABRIC("Fabric", true),
    MODPACK("Modpack", false);

    companion object {
        fun fromString(value: String?): ServerType {
            return values().find { it.name == value } ?: PAPER
        }
    }
}
