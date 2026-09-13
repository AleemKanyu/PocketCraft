package com.pockethost.app.config

import com.pockethost.app.data.model.RelayRegion
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

object RelayLatencySelector {
    private const val CONNECT_TIMEOUT_MS = 1_500
    private const val SAMPLE_COUNT = 2

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
        val targetHost = resolveTargetHost(host)
        val samples = buildList {
            repeat(SAMPLE_COUNT) {
                val sample = measureTcpConnectLatency(targetHost, port)
                if (sample != Long.MAX_VALUE) add(sample)
            }
        }
        if (samples.isEmpty()) return Long.MAX_VALUE
        return samples.minOrNull() ?: Long.MAX_VALUE
    }

    private fun measureTcpConnectLatency(host: String, port: Int): Long {
        return try {
            val startNs = System.nanoTime()
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            }
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    private fun resolveTargetHost(host: String): String {
        val resolved = runCatching {
            InetAddress.getAllByName(host)
                .firstOrNull { it is Inet4Address }
                ?.hostAddress
                ?: InetAddress.getByName(host).hostAddress
        }.getOrNull()

        return resolved?.takeIf { it.isNotBlank() }
            ?: RelayServers.getByHost(host).fallbackIp
            ?: host
    }
}
