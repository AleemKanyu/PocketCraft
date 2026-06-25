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
    val isRelayBridge: Boolean get() = !isBedrock && isLoopbackIp(ip)
    val isBedrockBridge: Boolean get() = isBedrock && isLoopbackIp(ip)

    fun pingText(unavailable: String = "Ping unavailable"): String {
        val ping = pingMs.takeIf { it >= 0 } ?: return unavailable
        val label = when {
            isRelayBridge -> "Relay ping"
            isBedrockBridge -> "Bedrock bridge ping"
            ip.isNotBlank() -> "Wi-Fi ping"
            else -> "Ping"
        }
        return "$label: ${ping}ms"
    }

    companion object {
        private fun isLoopbackIp(value: String): Boolean {
            val host = value.trim().lowercase()
            return host == "localhost" ||
                host == "::1" ||
                host == "0:0:0:0:0:0:0:1" ||
                host == "127.0.0.1" ||
                host.startsWith("127.")
        }
    }
}
