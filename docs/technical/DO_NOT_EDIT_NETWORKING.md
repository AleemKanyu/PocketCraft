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
