package com.pockethost.app.data.model

enum class ServerType(val displayName: String, val supportsVersionSelect: Boolean) {
    VANILLA("Vanilla", true),
    PAPER("Paper", true),
    PURPUR("Purpur", true),
    FABRIC("Fabric", true),
    BEDROCK("Bedrock Edition", true),
    MODPACK("Modpack", false);

    companion object {
        fun fromString(value: String?): ServerType {
            if (value.isNullOrBlank()) return PAPER
            for (type in entries) {
                if (type.name.equals(value, ignoreCase = true) || type.displayName.equals(value, ignoreCase = true)) {
                    return type
                }
            }
            return PAPER
        }
    }
}
