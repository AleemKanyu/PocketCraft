# PocketCraft Relay — Agent Implementation Prompt

Use this prompt with any agentic coding tool (Cursor, Claude Code, Antigravity, Windsurf, etc.) to implement the relay tunnel feature in the PocketCraft Android app.

---

## Prompt

You are a senior Android/backend engineer implementing the **PocketCraft Relay Tunnel** feature — a system that allows an Android phone running a local Minecraft Java server to be reachable by players on the public internet, without port-forwarding or paid hosting.

The full PRD for this feature is embedded at the bottom of this prompt. Read it entirely before writing any code.

---

### What you are building

Two components, implemented in this order:

**1. Relay Server (Node.js, runs on a VPS)**
A lightweight TCP + WebSocket bridge server. Android phones connect to it via WebSocket to register themselves. Players connect to it via TCP on port 25565. The server forwards raw bytes bidirectionally between the player and the correct phone.

**2. Android Tunnel Client (Kotlin)**
A module in the existing PocketCraft Android app. It connects to the relay server via WebSocket (OkHttp), starts a local Minecraft server process, and pipes bytes between the relay connection and the local server socket using Kotlin coroutines.

---

### Relay Server — Implementation Requirements

**File:** `relay/server.js`

**Dependencies:** `npm install ws net`

**Exact behaviour required:**

- Start a WebSocket server on port `8080`. This is where Android phones connect.
- Start a TCP server on port `25565`. This is where Minecraft clients connect.
- When a phone connects via WebSocket:
  - Generate a random 8-character alphanumeric session ID.
  - Store the mapping: `sessionId → wsSocket` in a `Map`.
  - Send the session ID back to the phone as a plain text message: `{"type":"session","id":"abc12345"}`.
  - On disconnect, remove from the map and close any associated TCP sockets.
- When a player connects via TCP:
  - Read the first packet (Minecraft handshake). Parse the `Server Address` field from the Minecraft handshake packet (packet ID `0x00`, field index 2) to extract the subdomain (e.g. `abc12345.pocketcraft.app` → `abc12345`).
  - Look up `abc12345` in the session map. If not found, write `{"error":"no session"}` and close.
  - If found, store the mapping `sessionId → tcpSocket`, and begin bidirectional forwarding.
- Forwarding: all bytes received on the TCP socket must be sent as binary WebSocket messages to the phone. All binary WebSocket messages received from the phone must be written directly to the TCP socket.
- Heartbeat: if no message is received from a phone WS connection for 45 seconds, close it and clean up.
- Session cleanup: when either the TCP socket or the WS socket closes, close the other and remove both from all maps.
- Log all connect/disconnect events with timestamps. Use `console.log` only, no external logging libraries.

**Do not** add HTTP endpoints, auth middleware, TLS handling, or any dependencies beyond `ws` and `net`. Keep it minimal and correct.

---

### Android Tunnel Client — Implementation Requirements

**Location in project:** `app/src/main/java/com/pocketcraft/tunnel/`

**Language:** Kotlin
**Coroutine scope:** Use `lifecycleScope` or a `CoroutineScope(Dispatchers.IO)` passed in — do not hardcode GlobalScope.

**Files to create:**

```
tunnel/
  RelayClient.kt       — WebSocket connection + session management
  TunnelBridge.kt      — Byte-pipe coroutines between relay WS and local MC server
  TunnelState.kt       — Sealed class for UI state
  TunnelService.kt     — Android Foreground Service wrapping the tunnel
```

**RelayClient.kt requirements:**
- Use OkHttp `WebSocket` (already in project dependencies).
- Connect to `ws://<RELAY_HOST>:8080`.
- On first text message, parse `{"type":"session","id":"..."}` to extract session ID.
- Expose the session ID as `StateFlow<String?>` so the UI can observe and display the shareable address.
- Send a binary heartbeat ping every 15 seconds using `ws.send(ByteString.EMPTY)`.
- Implement exponential backoff reconnect (1s, 2s, 4s, max 30s) on any disconnect. On reconnect, emit a new session ID (the relay will assign a new one).
- All public methods must be coroutine-friendly (suspend or return Flow).

**TunnelBridge.kt requirements:**
- Accept a `WebSocket` instance and a `localPort: Int` (the port the Minecraft server is listening on, default 25565).
- Open a `java.net.Socket` to `localhost:localPort`.
- Launch two coroutines:
  - `relayToLocal`: reads binary messages from the WebSocket (via a `Channel<ByteArray>` fed by the WebSocket listener), writes them to the local socket output stream.
  - `localToRelay`: reads from the local socket input stream in a loop, sends each chunk as a binary message on the WebSocket.
- Both loops must terminate cleanly (no silent swallows) when either end closes.
- Buffer size for socket reads: 8192 bytes.

**TunnelState.kt:**
```kotlin
sealed class TunnelState {
    object Idle : TunnelState()
    object Connecting : TunnelState()
    data class Active(val sessionId: String, val shareableAddress: String) : TunnelState()
    data class Reconnecting(val attempt: Int) : TunnelState()
    data class Error(val message: String) : TunnelState()
}
```

**TunnelService.kt requirements:**
- Extend `Service` and start as a Foreground Service.
- Show a persistent notification: "PocketCraft server is running — tap to share address."
- Acquire a `PARTIAL_WAKE_LOCK` on start, release on stop.
- On `startService`, start `RelayClient` and `TunnelBridge`.
- On `stopService`, cancel all coroutines and close sockets cleanly.
- Expose `TunnelState` as a `StateFlow` accessible from the ViewModel via a bound service interface.

---

### Manifest & permissions required

Add to `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>

<service
    android:name=".tunnel.TunnelService"
    android:foregroundServiceType="connectedDevice"
    android:exported="false"/>
```

---

### Gradle dependencies to add

```kotlin
// app/build.gradle.kts
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
```

---

### What NOT to implement

Do not implement any of the following — they are explicitly out of scope for this task:

- Auth, login, or user accounts
- Custom subdomains or persistent session IDs
- A web dashboard or admin interface
- Player count tracking
- iOS / KMP support
- TLS / WSS (Cloudflare will handle this later)
- Any UI beyond the `TunnelState` state flow (the ViewModel and UI already exist)

---

### Quality checklist — do not mark this task complete until all pass

- [ ] Relay server starts without errors: `node relay/server.js`
- [ ] A WebSocket client can connect and receive a session ID JSON message
- [ ] A TCP connection to port 25565 with a valid Minecraft handshake is routed to the correct WebSocket connection
- [ ] Raw bytes flow bidirectionally without modification
- [ ] Stale WebSocket connections are cleaned up after 45s of inactivity
- [ ] Android app connects to relay, receives session ID, and emits `TunnelState.Active`
- [ ] Foreground service notification appears when tunnel is active
- [ ] Heartbeat is sent every 15s (verify in relay logs)
- [ ] Disconnecting the relay from the server side triggers auto-reconnect on Android
- [ ] All coroutines cancel cleanly when `TunnelService.onDestroy()` is called (no leaked threads)
- [ ] No hardcoded IPs — relay host must come from a `BuildConfig` field or a constants file

---

## Embedded PRD

### Problem & Opportunity

Minecraft Java Edition requires players to either pay for a dedicated hosting service (~$5–15/month), set up port-forwarding (impossible on most mobile data and university networks), or use third-party tunnel services that are not purpose-built for mobile and require technical setup.

PocketCraft targets a specific, underserved segment: casual players who want to host a quick server for friends from their Android phone — no PC, no credit card, zero config.

**Core insight:** the phone already has enough CPU to run a Minecraft server for 2–5 players. The only missing piece is a reliable public address. PocketCraft provides exactly that.

**Who this is for:** Students and younger players (16–24) who are comfortable installing an Android app, want to play with friends on-demand, and either can't or won't pay for dedicated hosting.

---

### Goals & Success Metrics

| Metric | Target |
|---|---|
| Waitlist signups before launch | 500 |
| Relay overhead latency (same region) | < 150ms |
| Session uptime per 4hr play session | 99% |
| Time from install to shareable link | < 60 seconds |

**Non-goals for MVP:** iOS support, web dashboard, custom domains, more than 5 concurrent players per session.

---

### System Architecture

```
Android App (Minecraft server :25565)
  ↕  WebSocket (:8080)
Relay Server (VPS — DigitalOcean / Oracle Free)
  ↕  TCP (:25565)
Player Client (Minecraft Java)

Wildcard DNS: *.pocketcraft.app → VPS IP
```

**Relay Server:** Maintains a routing table mapping session IDs to active WebSocket connections. Listens on TCP :25565 for players, parses the Minecraft handshake to extract the subdomain, and bridges to the correct phone.

**Android Client:** Starts local Minecraft server. Opens persistent WebSocket to relay. Dual coroutine loops: relay→local and local→relay. Heartbeat every 15s.

**Session Addressing:** Random slug per session (e.g. `aleem123.pocketcraft.app`). Wildcard DNS to VPS. Relay parses subdomain to route.

---

### Feature Requirements (MVP scope only)

| Feature | Description |
|---|---|
| TCP tunnel | Bidirectional raw-byte forwarding. No packet modification. |
| WebSocket connection | Phone→relay. Persistent, auto-reconnects on drop. |
| Session ID + subdomain | Random slug on connect, used as subdomain and shareable address. |
| Heartbeat / keepalive | Phone pings every 15s. Relay times out stale after 45s. |
| Auto-reconnect | Exponential backoff, resumes within 60s. |
| Share link UI | One-tap copy of server address. Shown immediately. |
| Multi-user routing | N concurrent phone sessions, each routed independently. |

---

### Technical Constraints

- Minecraft Java uses raw TCP — relay must forward unmodified bytes.
- Android side must use Kotlin coroutines for dual I/O loops (not blocking threads).
- **Never modify or buffer packets.** Minecraft's protocol is stateful — partial or reordered bytes cause silent client disconnects.
- Foreground Service + WAKE_LOCK is mandatory — Android Doze will kill background network without it.
- Mobile data kills idle TCP after 30–90s — heartbeat is not optional.

---

### Risks

| Severity | Risk | Mitigation |
|---|---|---|
| High | NAT timeout drops tunnel | Heartbeat every 15s, auto-reconnect with backoff |
| High | Android Doze kills network | Foreground Service + WAKE_LOCK required |
| Medium | Node.js relay bottleneck at scale | Fine for MVP (~100 sessions); migrate to Go for v2 |
| Medium | VPS bandwidth cost | Oracle Free Tier gives 10 TB/month — sufficient for hundreds of sessions |

---

### Open Questions (resolve before Week 3)

1. Which Minecraft server JAR (Fabric/Paper ARM build) runs reliably on Android 12+? This is the blocker for the Android client integration.
2. Auth before tunnel open? MVP: no auth. Evaluate after beta.
3. Supabase session logging on relay? Low cost, decide if needed at MVP.
4. Relay TLS? Use Cloudflare proxy for WSS from day one to avoid migration later.
