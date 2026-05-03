# BEDROCK_FIXES_AGENT — Fix Bedrock Ping & Player Data Saving

## Context

PocketCraft hosts Minecraft Java Edition servers on-device with Geyser + Floodgate for Bedrock crossplay. The server runs in **offline mode**. Bedrock players connect through a UDP relay on EC2. Two known issues exist:

1. **High ping for Bedrock players** — extra latency due to UDP→TCP conversion and Geyser translation layer
2. **Player data not saving** — Bedrock players get a different UUID each session because Floodgate is not generating stable UUIDs in offline mode
3. **Username prefix** — Bedrock player names show with a `.` prefix (e.g. `.Steve`)

---

## Task 1 — Fix Geyser Configuration (`geyser.yml`)

Locate `geyser.yml` inside the Geyser plugin folder. This is bundled inside the server files managed by PocketCraft, likely at:
`/servers/geyser/plugins/Geyser-Spigot/config.yml` or `plugins/Geyser-Spigot/geyser.yml`

Apply the following changes:

```yaml
# Reduces ping display inaccuracy in Bedrock server list
ping-passthrough-interval: 1

# Must be floodgate for offline mode Bedrock auth
auth-type: floodgate

# Disable async MOTD to reduce latency
async-motd: false
```

---

## Task 2 — Fix Floodgate Configuration (`floodgate.yml`)

Locate `floodgate.yml` inside the Floodgate plugin folder:
`plugins/floodgate/config.yml` or `plugins/Floodgate/floodgate.yml`

Apply the following changes:

```yaml
# Change prefix to empty string to remove the dot from Bedrock usernames
# Leave as "." if you want to keep Java/Bedrock name separation
username-prefix: ""

# Enable stable UUID generation so player data persists across sessions
player-link:
  enabled: true
  use-global-linking: false
  link-code-timeout: 60

# Make sure Floodgate data is sent to the server
send-floodgate-data: true
```

> **Note:** Setting `username-prefix` to `""` means a Bedrock player named `Steve` and a Java player named `Steve` would collide. If the server has both Java and Bedrock players with the same name, keep prefix as `"*"` instead of `""`.

---

## Task 3 — Fix JVM Launch Flags for Netty (Reduce Ping)

In the server launch code — wherever the JVM arguments are assembled before starting the server process (likely in `ServerHostService.kt` or `launcher.c`) — add this flag to the JVM args list:

```
-Dio.netty.eventLoopThreads=4
```

This increases Netty's network thread count, reducing the chance of packet queuing delays for Bedrock players.

### Where to add it

In `ServerHostService.kt`, find where JVM args are built (look for a list containing `-Xmx`, `-Xms`, GC flags etc.) and append:

```kotlin
"-Dio.netty.eventLoopThreads=4"
```

---

## Task 4 — Android: Reset Player Data Cache on Username Change

Since changing `username-prefix` will change all existing Bedrock player usernames, their old playerdata files (named after the old UUID) will be orphaned. Add a one-time migration note in `PlayerDataManager.kt`:

Add a log warning when the server starts if Floodgate prefix has changed:

```kotlin
Log.w("PlayerData", "Floodgate username prefix changed — existing Bedrock player data may need to be manually cleared from world/playerdata/")
```

This is just a warning — do not auto-delete player data as it may contain progress the user wants to keep.

---

## Task 5 — Expose Geyser/Floodgate Config in Settings UI (Optional but Recommended)

In the Settings screen, add a new section **"Bedrock Settings"** with two toggle options:

| Setting | Description | Default |
|---------|-------------|---------|
| Show username prefix | Adds `.` before Bedrock player names | Off |
| Link player data | Stable UUID for Bedrock players | On |

These toggles should read/write directly to `floodgate.yml` using a simple YAML line replace (find the line, replace the value). Use the existing file I/O pattern already in the codebase for `server.properties` edits.

```kotlin
// Example pattern for editing a yml value
fun setFloodgateValue(key: String, value: String) {
    val file = File(serverDir, "plugins/Floodgate/config.yml")
    if (!file.exists()) return
    val lines = file.readLines().toMutableList()
    val idx = lines.indexOfFirst { it.trimStart().startsWith(key) }
    if (idx != -1) lines[idx] = "${key}: ${value}"
    file.writeText(lines.joinToString("\n"))
}
```

---

## Files to Create / Modify

| File | Action |
|------|--------|
| `plugins/Geyser-Spigot/geyser.yml` | **Modify** — ping passthrough, auth type, async motd |
| `plugins/Floodgate/floodgate.yml` | **Modify** — username prefix, player link, send-floodgate-data |
| `ServerHostService.kt` | **Modify** — add Netty thread count JVM flag |
| `PlayerDataManager.kt` | **Modify** — add warning log for prefix change |
| `ui/settings/SettingsScreen.kt` | **Modify** — add Bedrock Settings section (optional) |

---

## Notes

- The Netty thread flag only helps if the phone has enough CPU cores. On low-end phones (4 cores), set it to `2` instead of `4`.
- `use-global-linking: false` is correct for offline mode — global linking requires online mode Xbox auth.
- After these changes the server must be fully restarted (not just reloaded) for Floodgate and Geyser changes to take effect.
- Bedrock players who joined before this fix will need to rejoin once — their data will reinitialise under their new stable UUID. Their old data under the old UUID will remain in `world/playerdata/` but will no longer be loaded.
- These config files are inside the server world folder, which is managed by PocketCraft's file system. The agent should locate them relative to the active server directory used by `ServerHostService`.
