# CRITICAL DIRECTIVE: DO NOT EDIT OR MODIFY THE SERVER RUNNING AND NETWORKING LOGIC

This codebase contains highly optimized networking pipelines, socket pools, and UDP bridges that route Minecraft Java & Bedrock traffic between the host phone and the internet relay servers. 

**Modifying these files is strictly prohibited** because even minor adjustments to bridge buffer sizes, TCP socket pooling timeouts, keep-alive packets, or thread priorities will corrupt network framing, break UDP packet routing, and inflate server ping, leading to connection drops and severe lag.

## Protected Files & Directories

1. **`app/src/main/kotlin/com/pocketcraft/server/RelayManager.kt`**
   - Controls OkHttp and HttpURLConnection endpoints for registering/notifying phone ready.
   - Manages the socket pool and low-latency TCP bridge warmups.

2. **`app/src/main/kotlin/com/pocketcraft/server/relay/` (e.g., `BedrockUdpBridge.kt`)**
   - Manages Bedrock packet framing, translation, and high-performance direct loopback pipelines.

3. **`app/src/main/kotlin/com/pocketcraft/server/server/` (e.g., `ServerHostService.kt`, `ServerLauncher.kt`)**
   - Manages foreground service, wake locks, Wi-Fi locks, process lifecycles, and in-process/out-of-process JVM execution.

4. **`index.js`**
   - The Node.js relay coordinator server which matches clients, tunnels TCP traffic, and intercepts/negotiates Bedrock connection MTU sizes.

## Strict Directive for AI Assistants
If you are an AI assistant (such as Gemini, Claude, Copilot, etc.) asked to modify the UI, layout, themes, or non-networking features of this application:
- **Do not edit, delete, or refactor any parts of the files listed above.**
- **If you need to display details from these components, read their public states or events, but do not touch their internal logic.**

---

## 5. Stable Networking Constraints & Custom IP Architecture

To prevent regressions (high latency spikes in thousands of milliseconds, connection drops):

1. **Buffer Size Constraints**:
   - `RelayManager.kt` socket buffer sizes MUST remain at the v1.6.0 values: `256 * 1024` (256KB) for socket send/receive sizes and `64 * 1024` (64KB) for player bridge buffer size.
   - `bedrockTxChannel` capacity MUST remain at `256` (do not use `Channel.UNLIMITED` or inject high-volume write loops without batch-draining).
   - Do NOT introduce experimental Wi-Fi socket bindings or other network routing logic.

2. **Singapore Region Deletion**:
   - The Singapore relay server (`play.pocketcraft.online`) has been permanently deleted. Do not reference it or add it back to `RelayServersConfig.kt` or `RelayManager.kt`.
   - Fallback region resolution in `RelayManager.kt` must be resolved **dynamically**:
     - If the user's preferred region is Mumbai (`mine.pocketcraft.online`), the fallback is Europe (`eu.pocketcraft.online`).
     - Otherwise, the fallback is Mumbai (`mine.pocketcraft.online`).

3. **Custom IP (Subdomain Routing)**:
   - Minecraft Java Custom IP routing is managed by `subdomain-listener.js` running on port 25565 on each relay server.
   - It polls the relay's `/status` endpoint on port 8080 to map subdomains to the assigned local tunnel ports and pipes connections directly to `127.0.0.1:<port>`.
   - The relay's `/status` endpoint schema and the TCP connection handshake forwarding logic in `relay/index.js` must be kept fully compatible with `subdomain-listener.js`.

