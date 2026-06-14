# RELAY_POOL_PING_FIX_AGENT

## Context
PocketCraft — `com.pocketcraft.server`. The relay system lives in `RelayManager.kt`.
The app maintains a pool of persistent TCP sockets to the EC2 relay (`mine.pocketcraft.online:9000`).
Each socket sits idle until a player connection comes in, at which point it is consumed and a bridge
is established between the relay and `localhost:25565`.

## Problem (confirmed from logs)
```
LocalToRelay write delay: 146368μs for 16384 bytes   ← 146ms
LocalToRelay write delay: 105780μs for 16384 bytes   ← 105ms
```
High ping at session start is caused by three things colliding at player-join time:

1. **Pool promoted 3→5 at first connection** — two new TCP handshakes (each ~50ms RTT to Mumbai)
   happen concurrently with the player's live game traffic.
2. **Idle timeout rotating sockets at join time** — sockets are being killed and rebuilt right
   when the player needs them, so their packets queue behind a new handshake.
3. **Nagle's algorithm on the relay socket** — the server flushes a large buffered chunk (~16KB)
   right after Geyser finishes init; Nagle holds small packets waiting to fill that buffer,
   adding latency to every subsequent write.

---

## Fix 1 — Start pool at 5 from day one, remove the "promote on first connection" logic

### In `RelayManager.kt`

Find the constant or variable that sets initial pool size:
```kotlin
// BEFORE — pool starts small and grows on first player
private const val TARGET_POOL_SIZE = 3  // or 5 after promotion
```

Change it so the pool always starts at the full size:
```kotlin
private const val TARGET_POOL_SIZE = 5  // always, from first registration
```

Find and **delete** the promotion block that looks like this:
```kotlin
// DELETE THIS ENTIRE BLOCK
if (firstConnectionReceived && currentPoolSize < PROMOTED_POOL_SIZE) {
    targetPoolSize = PROMOTED_POOL_SIZE
    // ... replenish logic
}
```
Or wherever you set `Relay pool promoted to 5 sockets after first connection` is logged —
remove that branch entirely. The pool should be at 5 before `phone-ready` is ever called.

---

## Fix 2 — Do not rotate sockets that are currently bridging a player

### In `RelayManager.kt` — socket wrapper / pool entry class

Add a flag to track whether a socket is actively bridging:
```kotlin
data class PooledSocket(
    val socket: Socket,
    var isBridging: Boolean = false,       // ADD THIS
    val createdAt: Long = System.currentTimeMillis()
)
```

In the idle-timeout rotation check, skip bridging sockets:
```kotlin
// BEFORE
if (timeSinceCreated > IDLE_TIMEOUT_MS) {
    rotateSocket(pooledSocket)
}

// AFTER
if (!pooledSocket.isBridging && timeSinceCreated > IDLE_TIMEOUT_MS) {
    rotateSocket(pooledSocket)
}
```

When a socket is consumed by a player connection, mark it before bridging starts:
```kotlin
fun consumeSocket(pooledSocket: PooledSocket) {
    pooledSocket.isBridging = true   // ADD THIS LINE before starting bridge coroutine
    // ... existing bridge setup
}
```

When the bridge ends (either side disconnects), the socket is already being replaced by
`replenishPool()` — no change needed there, just make sure `isBridging` is only set on
the consumed instance, not the replacement.

---

## Fix 3 — Increase idle timeout to stop churning during active sessions

### In `RelayManager.kt`

Find the idle timeout constant (the one that produces `"Socket hit local idle timeout, rotating gracefully..."`):
```kotlin
// BEFORE — too short, causes rotations during active play
private const val SOCKET_IDLE_TIMEOUT_MS = 30_000L  // (or whatever the current value is)
```

Change it:
```kotlin
// AFTER — sockets live long enough to serve an active session without mid-game rotation
private const val SOCKET_IDLE_TIMEOUT_MS = 120_000L  // 2 minutes
```

This stops the pattern seen in logs where sockets rotate every ~30 seconds even while
a player is connected and game traffic is flowing.

---

## Fix 4 — Set tcpNoDelay = true on all relay sockets

### In `RelayManager.kt` — wherever new pool sockets are created

```kotlin
// BEFORE
val socket = Socket(relayHost, relayPort)

// AFTER
val socket = Socket(relayHost, relayPort).apply {
    tcpNoDelay = true      // disables Nagle — no more batching small MC packets
    keepAlive = true       // detects dead connections without waiting for timeout
    soTimeout = 0          // no read timeout on pool sockets (rotation handles lifecycle)
}
```

Also apply `tcpNoDelay = true` to the **local side** socket (the one connecting to `localhost:25565`):
```kotlin
val localSocket = Socket("127.0.0.1", 25565).apply {
    tcpNoDelay = true      // same reason — MC sends many small packets
}
```

This directly eliminates the 146ms / 105ms write delays seen in the log. Nagle was
waiting to accumulate data before flushing; with `tcpNoDelay` every write goes out
immediately.

---

## Fix 5 — Pre-resolve relay hostname once, cache the IP

Prevents DNS lookup latency on reconnects for users with slow ISP DNS (South Asia ISPs
vary wildly in DNS response time).

### In `RelayManager.kt` — add near the top of the class

```kotlin
companion object {
    private const val PRIMARY_HOST = "mine.pocketcraft.online"
    private const val PRIMARY_IP_FALLBACK = "YOUR_MUMBAI_EC2_ELASTIC_IP"   // fill this in
    private const val SECONDARY_HOST = "play.pocketcraft.online"
    private const val SECONDARY_IP_FALLBACK = "YOUR_SINGAPORE_EC2_ELASTIC_IP" // fill this in
}

private var cachedRelayIp: String? = null

private suspend fun resolveRelayIp(): String {
    cachedRelayIp?.let { return it }  // already resolved this session

    return withContext(Dispatchers.IO) {
        try {
            val resolved = withTimeoutOrNull(2_000L) {
                InetAddress.getAllByName(PRIMARY_HOST).firstOrNull()?.hostAddress
            }
            (resolved ?: PRIMARY_IP_FALLBACK).also { cachedRelayIp = it }
        } catch (e: Exception) {
            PRIMARY_IP_FALLBACK.also { cachedRelayIp = it }
        }
    }
}
```

Call `resolveRelayIp()` once before the first `Socket()` call and use the returned IP
for every socket in the pool:
```kotlin
// In your pool initialization / startPool() function
val relayIp = resolveRelayIp()   // fast after first call (cached)
val socket = Socket(relayIp, RELAY_PORT).apply {
    tcpNoDelay = true
    keepAlive = true
}
```

Replace `YOUR_MUMBAI_EC2_ELASTIC_IP` and `YOUR_SINGAPORE_EC2_ELASTIC_IP` with the
actual Elastic IPs from your AWS console. These never change unless you reassign them.

---

## Summary of all changes

| # | What | Where | Effect |
|---|------|--------|--------|
| 1 | Pool starts at 5 always, remove promotion-on-first-player | `RelayManager.kt` | No TCP handshakes during player join |
| 2 | Skip idle rotation for bridging sockets | `RelayManager.kt` — rotation check | Player connections never get their socket pulled mid-game |
| 3 | Idle timeout → 120s | `RelayManager.kt` — `SOCKET_IDLE_TIMEOUT_MS` | Stops the constant churn seen in logs |
| 4 | `tcpNoDelay = true` on relay + local sockets | `RelayManager.kt` — socket constructors | Eliminates 146ms / 105ms Nagle write delays directly |
| 5 | DNS pre-resolve with 2s timeout + IP fallback | `RelayManager.kt` — new `resolveRelayIp()` | Removes DNS RTT from socket creation for slow-DNS users |

## Expected result
Write delays drop from ~100-150ms to <5ms. Socket pool is fully ready before the first
player arrives. No mid-session rotations while a bridge is active. Players experience
normal ping (relay RTT only, typically 40-80ms Mumbai→India) instead of 150-200ms+ spikes.
