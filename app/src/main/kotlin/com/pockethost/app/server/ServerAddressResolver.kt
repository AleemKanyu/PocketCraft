package com.pockethost.app.server

import java.net.Inet4Address
import java.net.NetworkInterface

object ServerAddressResolver {

    fun getConnectAddress(port: Int = 25565): String? =
        getBestLanAddress()?.let { "$it:$port" }

    fun getLocalIpAddress(): String? = getBestLanAddress()

    private fun getBestLanAddress(): String? {
        val siteLocal = mutableListOf<String>()
        val fallback = mutableListOf<String>()

        runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .asSequence()
                .filter { iface ->
                    runCatching {
                        iface.isUp && !iface.isLoopback && !iface.isVirtual
                    }.getOrDefault(false)
                }
                .forEach { iface ->
                    iface.inetAddresses
                        ?.toList()
                        .orEmpty()
                        .filterIsInstance<Inet4Address>()
                        .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
                        .map { it.hostAddress.orEmpty() }
                        .filter { it.isNotBlank() }
                        .forEach { address ->
                            if (isSiteLocal(address)) {
                                siteLocal += address
                            } else {
                                fallback += address
                            }
                        }
                }
        }

        return siteLocal.firstOrNull() ?: fallback.firstOrNull()
    }

    private fun isSiteLocal(address: String): Boolean =
        address.startsWith("10.") ||
            address.startsWith("192.168.") ||
            address.matches(Regex("""172\.(1[6-9]|2\d|3[0-1])\..+"""))
}
