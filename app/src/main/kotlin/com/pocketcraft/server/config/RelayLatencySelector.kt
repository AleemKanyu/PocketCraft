package com.pocketcraft.server.config

import com.pocketcraft.server.data.model.RelayRegion
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

object RelayLatencySelector {
    suspend fun pickFastestRelay(regions: List<RelayRegion>): RelayRegion {
        val fallback = regions.firstOrNull() ?: RelayServers.defaultRegions().first()
        return withContext(Dispatchers.IO) {
            coroutineScope {
                regions.ifEmpty { listOf(fallback) }
                    .map { region ->
                        async {
                            region to measureRelayLatency(region.host)
                        }
                    }
                    .awaitAll()
                    .minByOrNull { it.second }
                    ?.first
                    ?: fallback
            }
        }
    }

    fun measureRelayLatency(host: String, port: Int = 8080): Long {
        val targetHost = RelayServers.getByHost(host).fallbackIp ?: host
        return try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(targetHost, port), 2_000)
            }
            System.currentTimeMillis() - start
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }
}
