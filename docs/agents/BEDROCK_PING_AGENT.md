# BEDROCK_PING_AGENT — Fix Bedrock "Pinging" Status on Relay

## Context

PocketCraft uses two AWS EC2 relay servers (`mine.pocketcraft.online`, `play.pocketcraft.online`) running a custom Node.js TCP relay via PM2. Bedrock players connect through `BedrockUdpBridge.kt` on Android, which tunnels UDP over TCP to the relay. The join works, but Bedrock clients always show "Pinging..." in the server list because they send a **UDP RakNet ping on port 19132** to check server status — and the relay has no UDP listener to respond to it.

UDP port 19132 is now open in the EC2 security groups on both instances.

---

## Goal

1. Add a UDP RakNet pong responder to the Node.js relay so Bedrock clients see the server as **Online** in their server list.
2. Add a simple HTTP status endpoint on the relay so the Android app can push live server info (MOTD, player count).
3. Update `RelayManager.kt` (or `BedrockUdpBridge.kt`) to POST server status to the relay after the server starts, and update it periodically.

---

## Task 1 — Node.js Relay: `bedrock-ping.js`

Create a new file `bedrock-ping.js` in the relay project root.

### Requirements

- Listen on **UDP port 19132** using Node's `dgram` module.
- Respond to **RakNet Unconnected Ping** packets (first byte `0x01`) with a valid **Unconnected Pong** (`0x1c`).
- The RakNet offline message magic bytes must be included in the pong:  
  `00 ff ff 00 fe fe fe fe fd fd fd fd 12 34 56 78`
- Use a hardcoded server GUID (any stable 64-bit value, e.g. `0xDEADBEEFCAFE1234`).
- Maintain an in-memory **status map**: `Map<port, { motd, players, maxPlayers, version }>`.
- Default fallback MOTD when no status is registered for a port:  
  `MCPE;PocketCraft;800;1.21.0;0;20;12345;Survival;1;`
- Export two functions:
  - `startBedrockPing(udpPort)` — starts the UDP listener.
  - `updateServerStatus(tcpPort, { motd, players, maxPlayers, version })` — updates the status map.

### RakNet Pong Packet Structure

```
Byte 0:      0x1c  (Unconnected Pong ID)
Bytes 1–8:   ping time (Int64BE, copied from ping packet bytes 1–8)
Bytes 9–16:  server GUID (Int64BE)
Bytes 17–32: offline message magic (16 bytes)
Bytes 33–34: MOTD string length (UInt16BE)
Bytes 35+:   MOTD string (UTF-8)
```

### MOTD String Format (Bedrock)

```
MCPE;<display_name>;<protocol>;<version>;<current_players>;<max_players>;<server_id>;<world_name>;<gamemode_id>;
```

Example:
```
MCPE;PocketCraft Server;800;1.21.0;3;20;12345;Survival;1;
```

---

## Task 2 — Node.js Relay: HTTP Status Endpoint

In the main relay entry file (e.g. `index.js` or `server.js`), add an HTTP endpoint using Express (already installed) or Node's built-in `http` module:

```
POST /status
Body (JSON): { "port": 25501, "motd": "My Server", "players": 3, "maxPlayers": 20, "version": "1.20.4" }
```

- On receiving this, call `updateServerStatus(port, { motd, players, maxPlayers, version })`.
- Respond with `200 OK` and `{ "ok": true }`.
- Protect with a simple shared secret header: `X-PocketCraft-Secret: <SECRET>`.  
  The secret value should be read from an environment variable `RELAY_SECRET` (add to `.env`).
- If the secret is missing or wrong, respond `401`.

---

## Task 3 — Android: Post Status After Server Start

In `RelayManager.kt` (or wherever the relay connection is confirmed), after the server successfully starts and a relay port is assigned:

### Add a `postServerStatus()` suspend function

```kotlin
private suspend fun postServerStatus(
    relayHost: String,
    relayPort: Int,
    motd: String,
    players: Int,
    maxPlayers: Int,
    version: String
)
```

- Makes an HTTP POST to `https://<relayHost>/status` (use port 443 if HTTPS, or the relay's HTTP port).
- Body: JSON with `port`, `motd`, `players`, `maxPlayers`, `version`.
- Header: `X-PocketCraft-Secret: <secret>` — store the secret in `AppPreferences` or `BuildConfig`.
- Use `OkHttp` (already a dependency) for the request.
- Fire-and-forget in a coroutine; log but do not crash on failure.

### Call it after relay is confirmed

In the server-start flow, after `RelayManager` confirms the relay port is assigned, call:

```kotlin
scope.launch {
    postServerStatus(
        relayHost = currentRelayHost,
        relayPort = assignedPort,
        motd = serverProperties.motd,
        players = 0,
        maxPlayers = serverProperties.maxPlayers,
        version = selectedServerVersion
    )
}
```

### Periodic Update (Optional but recommended)

Set up a `TickingJob` or coroutine that calls `postServerStatus()` every **30 seconds** with the latest online player count from `PlayerDataManager`. Cancel this job when the server stops.

---

## Task 4 — Wire Up in Relay Entry Point

In `index.js` (or `server.js`), import and start the ping listener:

```js
const { startBedrockPing, updateServerStatus } = require('./bedrock-ping');

// Start UDP pong listener
startBedrockPing(19132);

// Export updateServerStatus so the HTTP handler can call it
```

Make sure PM2 restarts pick up the new file. After deploying, run:
```bash
pm2 restart all
```

---

## Files to Create / Modify

| File | Action |
|------|--------|
| `relay/bedrock-ping.js` | **Create** — UDP RakNet pong responder |
| `relay/index.js` (or main entry) | **Modify** — import & start bedrock-ping, add POST /status route |
| `relay/.env` | **Modify** — add `RELAY_SECRET=<generate a random string>` |
| `app/.../RelayManager.kt` | **Modify** — add `postServerStatus()` and call after relay confirmed |
| `app/.../AppPreferences.kt` | **Modify** — add `relaySecret` string constant or BuildConfig field |

---

## Notes

- Do **not** change the existing TCP relay logic.
- The UDP listener is completely independent of TCP — it only handles status pings.
- Both EC2 instances (Mumbai + Singapore) need the same relay code deployed. The PM2 ecosystem file should handle both.
- The `BigInt` API is available in Node.js 10.3+. Use `buf.writeBigInt64BE()` for the GUID and ping time fields.
- If `writeBigInt64BE` is unavailable (old Node), split into two `writeUInt32BE` calls.
