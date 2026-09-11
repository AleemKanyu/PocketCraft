package com.pockethost.app.data.model

enum class ServerType(
    val displayName: String,
    val supportsVersionSelect: Boolean,
    val isEnabled: Boolean = true
) {
    VANILLA("Vanilla", true),
    PAPER("Paper", true),
    PURPUR("Purpur", true),
    FABRIC("Fabric", true),
    BEDROCK("Bedrock Edition", true),
    MODPACK("Modpack", false);

    /**
     * True for server types that speak the Bedrock (RakNet over UDP) protocol rather than the
     * Java edition's TCP protocol. Readiness probing, port handling and world layout all differ.
     */
    val isBedrock: Boolean get() = this == BEDROCK

    companion object {
        fun fromString(value: String?): ServerType {
            if (value.isNullOrBlank()) return PAPER
            for (type in entries) {
                if (type.isEnabled && (type.name.equals(value, ignoreCase = true) || type.displayName.equals(value, ignoreCase = true))) {
                    return type
                }
            }
            return PAPER
        }
    }
}
