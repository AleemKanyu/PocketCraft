# Networking & Relay Architecture

This document describes the networking pipelines, socket pooling mechanisms, and UDP bridging that route Minecraft Java and Bedrock traffic between the host Android device and public relay nodes.

---

## Core Components

1. **`RelayManager.kt`** (`app/src/main/kotlin/com/pockethost/app/RelayManager.kt`)
   - Manages HTTP/HTTPS registration endpoints with relay nodes.
   - Maintains the persistent TCP socket pool and low-latency tunnel bridges.
   - Handles multi-region fallback resolution (dynamic switching between closest available relay regions).

2. **`BedrockUdpBridge.kt`** (`app/src/main/kotlin/com/pockethost/app/relay/BedrockUdpBridge.kt`)
   - Handles Bedrock UDP datagram framing and loopback translation to the internal Geyser-Spigot/PowerNukkitX port.
   - Batch-drains packets using dedicated coroutine channels to minimize latency.

3. **`ServerHostService.kt` & `ServerLauncher.kt`** (`app/src/main/kotlin/com/pockethost/app/server/`)
   - Manages foreground service lifecycle, wake locks, Wi-Fi high-performance locks, and JVM process execution.
   - Supervises standard I/O streams and RCON interfaces.

4. **Node.js Relay Coordinator** (`relay/index.js` & `relay/subdomain-listener.js`)
   - Acts as the public ingress coordinator matching game clients to mobile hosts without requiring UPnP or port forwarding.
   - Tunnels TCP stream data and proxies UDP RakNet packets.

---

## Latency & Buffer Constraints

The buffer parameters have been empirically benchmarked on cellular and Wi-Fi networks to prevent buffer bloat:

1. **TCP Socket Buffer Sizing**:
   - `RelayManager.kt` socket buffer sizes are explicitly configured: `64 * 1024` (64KB) for send/receive buffers and `8 * 1024` (8KB) for player bridge buffers.
   - *Rationale*: Smaller queues prevent bulk world chunk traffic from queuing ahead of time-critical keep-alive / player movement packets, keeping ping stable under ~100ms.

2. **Bedrock Channel Queue**:
   - `bedrockTxChannel` capacity is bounded to `256`. Unbounded queues can cause memory bloat and packet desynchronization on mobile chipsets.

3. **Custom Subdomain Routing**:
   - Subdomain routing is coordinated by `relay/subdomain-listener.js` on port 25565.
   - It polls the local relay coordinator on port 8080 (`/status`) to resolve mapped subdomains (`<name>.pocketcraft.online`) to their allocated host ports.
