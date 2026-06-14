# BEDROCK_STABILITY_AGENT — Fix Player Data Reset & Gameplay Delay

## Context

PocketCraft hosts Minecraft Java Edition servers on-device with Geyser + Floodgate for Bedrock crossplay. The server runs in **offline mode**. Two issues exist:

1. **Bedrock player data resets on every server restart** — Floodgate is not generating stable UUIDs in offline mode, so each restart assigns a new UUID to the same Bedrock player, wiping their inventory, position, and stats.
2. **Gameplay feels delayed for Bedrock players** — despite acceptable ping, actions like breaking blocks, placing blocks, and combat feel sluggish. This is caused by Geyser's translation layer not being optimally configured for low-resource mobile hosting.

---

## Task 1 — Fix Floodgate Configuration for Stable UUIDs

### Problem
In offline mode, Floodgate generates UUIDs based on the player's Xbox username + a random salt. If the salt is not persisted correctly, or `player-link` is disabled, a new UUID is generated each session — wiping all playerdata files.

### Fix
Locate `floodgate.yml` (or `config.yml`) inside the Floodgate plugin folder. The path is relative to the active server directory managed by PocketCraft, typically:
`plugins/floodgate/config.yml`

Apply these changes:

```yaml
# Remove dot prefix from Bedrock usernames (optional but recommended)
username-prefix: ""

# Critical: enable stable UUID linking
player-link:
  enabled: true
  use-global-linking: false
  link-code-timeout: 60

# Ensure Floodgate data is passed to the server
send-floodgate-data: true
```

### Why `use-global-linking: false`
Global linking requires Geyser's online linking service which needs internet auth. In offline mode this fails silently and falls back to random UUIDs. Setting it to `false` forces local linking which works correctly in offline mode.

### After this fix
- Each Bedrock player gets a stable UUID derived from their Xbox account ID
- Their `world/playerdata/<uuid>.dat` file persists across server restarts
- Inventory, XP, position, and stats are all preserved

---

## Task 2 — Fix Geyser Configuration for Reduced Gameplay Delay

### Problem
Geyser translates between Bedrock and Java protocols in real time. Several default settings cause unnecessary processing delays on mobile hardware.

### Fix
Locate `geyser.yml` (or `config.yml`) inside the Geyser plugin folder:
`plugins/Geyser-Spigot/config.yml`

Apply these changes:

```yaml
# Reduce ping passthrough polling — less overhead
ping-passthrough-interval: 1

# Disable async MOTD — reduces thread contention
async-motd: false

# Cache chunks aggressively to reduce re-translation
cache-chunks: true

# Allow longer keep-alive timeout — prevents false disconnects under load
max-auto-connect-attempts: 5

# Reduce Bedrock form delay
show-cooldown: disabled

# Forward hostname — not needed in offline mode
forward-hostname: false
```

### Additional JVM Flag
In `ServerHostService.kt`, where JVM args are assembled, add:

```kotlin
"-Dio.netty.eventLoopThreads=4"
```

This increases Netty's network thread count so Java↔Bedrock packet translation doesn't queue up behind other server tasks. On phones with 4 or fewer CPU cores, use `2` instead of `4`.

---

## Task 3 — Persist Floodgate Key Across Server Restarts

### Problem
Floodgate generates an encryption key (`key.pem`) on first run. If this file is deleted or regenerated, all existing player links are invalidated — causing data reset even with `player-link` enabled.

### Fix
In `ServerHostService.kt` or wherever the server directory is set up before launch, add logic to **preserve `plugins/floodgate/key.pem`** across server resets:

```kotlin
fun preserveFloodgateKey(serverDir: File) {
    val keyFile = File(serverDir, "plugins/floodgate/key.pem")
    val backupFile = File(serverDir.parentFile, "floodgate_key_backup.pem")

    if (keyFile.exists() && !backupFile.exists()) {
        // First time — back it up
        keyFile.copyTo(backupFile, overwrite = false)
        Log.d("Floodgate", "Backed up Floodgate key to ${backupFile.path}")
    } else if (!keyFile.exists() && backupFile.exists()) {
        // Key was deleted — restore it
        backupFile.copyTo(keyFile, overwrite = true)
        Log.d("Floodgate", "Restored Floodgate key from backup")
    }
}
```

Call this function **before** the server process starts, in the server launch sequence.

---

## Task 4 — PlayerDataManager: Warn on UUID Change

In `PlayerDataManager.kt`, when loading a Bedrock player's data, add a check that logs a warning if the same Xbox username is found under a different UUID than expected. This helps diagnose any future UUID instability:

```kotlin
fun checkForOrphanedData(playerName: String, currentUuid: String, worldDir: File) {
    val playerdataDir = File(worldDir, "playerdata")
    if (!playerdataDir.exists()) return
    // Log warning if multiple UUID files exist for similar usernames
    Log.w("PlayerData", "Checking playerdata integrity for $playerName ($currentUuid)")
}
```

---

## Files to Create / Modify

| File | Action |
|------|--------|
| `plugins/floodgate/config.yml` | **Modify** — enable player-link, stable UUID, remove prefix |
| `plugins/Geyser-Spigot/config.yml` | **Modify** — cache chunks, disable async motd, reduce delay |
| `ServerHostService.kt` | **Modify** — add Netty thread flag, call `preserveFloodgateKey()` |
| `PlayerDataManager.kt` | **Modify** — add orphaned data warning |

---

## Notes

- After these changes the server must be **fully restarted** (not reloaded) for Floodgate and Geyser changes to take effect.
- Bedrock players who joined before this fix will rejoin under their new stable UUID. Their old playerdata files remain in `world/playerdata/` but won't be loaded. To migrate old data, the old UUID file would need to be renamed to the new UUID — this is optional and manual.
- `cache-chunks: true` uses slightly more RAM but significantly reduces the translation work Geyser has to do per packet, which is the main source of gameplay delay on mobile.
- The `key.pem` backup should be stored outside the server world folder so it survives world resets.
- Do not delete existing `key.pem` files — this will invalidate all existing player links.
