# MINOR_FIXES_AGENT

## Overview
Fixes for 7 issues identified from user screenshots and reports. All are self-contained and independently implementable.

---

## Fix 1 — Geyser + ViaVersion Auto-Update on Every Launch

### What to do
In `PluginManager.kt` (or wherever Geyser/Via plugins are downloaded), add an auto-update check that runs every time the server is about to start — before `ServerLauncher` fires.

```kotlin
suspend fun autoUpdateBundledPlugins(context: Context, serverFilesDir: File) {
    val plugins = listOf(
        BundledPlugin(
            name = "Geyser-Spigot",
            fileName = "Geyser-Spigot.jar",
            apiUrl = "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest"
        ),
        BundledPlugin(
            name = "ViaVersion",
            fileName = "ViaVersion.jar",
            apiUrl = "https://hangar.papermc.io/api/v1/projects/ViaVersion/latestrelease"
        )
    )

    val pluginsDir = File(serverFilesDir, "plugins")
    pluginsDir.mkdirs()

    for (plugin in plugins) {
        try {
            val latestVersion = fetchLatestVersion(plugin.apiUrl)
            val installedVersion = getInstalledVersion(pluginsDir, plugin.fileName)

            if (installedVersion == null || latestVersion != installedVersion) {
                Log.d("PluginManager", "Updating ${plugin.name}: $installedVersion → $latestVersion")
                downloadPlugin(plugin, pluginsDir)
            } else {
                Log.d("PluginManager", "${plugin.name} is up to date ($installedVersion)")
            }
        } catch (e: Exception) {
            Log.e("PluginManager", "Failed to auto-update ${plugin.name}: ${e.message}")
            // Fail silently — use existing jar if download fails
        }
    }
}
```

Call `autoUpdateBundledPlugins()` inside `ServerHostService.kt` in the pre-launch sequence, after readiness checks but before `ServerLauncher.launch()`.

### Lock config editing
In the Plugins screen (wherever Geyser/Via config edit UI is exposed), remove or hide the config edit option for these two plugins specifically:

```kotlin
// In your plugin list composable, suppress edit button for bundled plugins
val isBundled = plugin.fileName in listOf("Geyser-Spigot.jar", "ViaVersion.jar")
if (!isBundled) {
    EditConfigButton(plugin)
}
```

---

## Fix 2 — Join Announcement Message

### What to do
Find where the join announcement is sent — likely in `ServerHostService.kt` where log lines are parsed for `UUID joined the game` or similar. Replace the hardcoded `tellraw` command with:

```kotlin
val joinMessage = """
    tellraw @a ["",{"text":"This world was hosted on PocketCraft! Thank you for using it  ","color":"green","bold":true},{"text":"\nJoin our Discord: ","color":"white"},{"text":"https://discord.gg/nc7ceYWVfT","color":"aqua","underlined":true}]
""".trimIndent()

sendRconCommand(joinMessage)
```

Make sure this fires on the `[Server] UUID joined the game` log line parse, not on server ready.

Also fix the known `tellraw` JSON malformation bug noted in `BUGFIX_AGENT.md` — ensure the JSON is valid before sending.

---

## Fix 3 — Notification Shows Full Process (not just "Downloading")

### What to do
In `ServerHostService.kt`, find the foreground notification update logic. Currently it likely only updates during download. Extend it to reflect each stage:

```kotlin
enum class ServerStage {
    DOWNLOADING_PLUGINS,
    EXTRACTING_JRE,
    CHECKING_PLUGINS,
    STARTING_SERVER,
    RUNNING,
    STOPPING
}

fun updateNotification(stage: ServerStage) {
    val (title, text) = when (stage) {
        ServerStage.DOWNLOADING_PLUGINS -> "PocketCraft" to "Updating plugins..."
        ServerStage.EXTRACTING_JRE     -> "PocketCraft" to "Preparing Java runtime..."
        ServerStage.CHECKING_PLUGINS   -> "PocketCraft" to "Checking Bedrock bridge..."
        ServerStage.STARTING_SERVER    -> "PocketCraft" to "Starting server... (30–60s)"
        ServerStage.RUNNING            -> "PocketCraft" to "Server is running ✓"
        ServerStage.STOPPING           -> "PocketCraft" to "Stopping server..."
    }
    val notification = buildNotification(title, text)
    startForeground(NOTIFICATION_ID, notification)
}
```

Call `updateNotification(stage)` at each transition point in the launch sequence. Remove any separate "Downloading..." notification that fires independently — consolidate everything into the single foreground notification.

---

## Fix 4 — RAM and Render Distance Locked Behind Max Power Mode

### What to do
In `AppPreferences.kt`, add a check for max power mode:

```kotlin
val isMaxPowerEnabled: Boolean
    get() = prefs.getBoolean("max_power_mode", false)
```

In `ServerLauncher.kt` (or `launcher.c` RAM args section ~line 343), apply caps when max power is NOT active:

```kotlin
val maxPowerEnabled = AppPreferences(context).isMaxPowerEnabled

val effectiveHeapMB = if (!maxPowerEnabled) {
    val totalRamMB = getTotalRamMB(context)
    minOf(allocatedHeapMB, (totalRamMB * 0.50).toInt()) // cap at 50%
} else {
    allocatedHeapMB // full allocation allowed
}
```

For render distance, in `server.properties` generation or the relay profile writer:

```kotlin
val maxViewDistance = if (!maxPowerEnabled) 16 else 32
val maxSimDistance = if (!maxPowerEnabled) 10 else 16

writeServerProperty("view-distance", maxViewDistance.toString())
writeServerProperty("simulation-distance", maxSimDistance.toString())
```

Also update the UI in the RAM/Settings screen to show locked state visually when max power is off:

```kotlin
if (!isMaxPowerEnabled) {
    Text(
        "🔒 Full RAM & 32-chunk render locked — enable Max Power Mode",
        color = Color(0xFFFFB142),
        fontSize = 12.sp
    )
}
```

---

## Fix 5 — World Type Dropdown Shows `\\\\` Instead of Values (`server.properties` editor)

### What to do
This is a string escaping bug. Find where `level-type` options are defined for the dropdown. The backslash is being double-escaped. Fix:

```kotlin
// WRONG — results in \\\\ displayed
val levelTypes = listOf("minecraft\\\\normal", "minecraft\\\\flat", "minecraft\\\\large_biomes")

// CORRECT
val levelTypes = listOf(
    "minecraft:normal",
    "minecraft:flat",
    "minecraft:large_biomes",
    "minecraft:amplified",
    "minecraft:single_biome_surface"
)
```

When writing to `server.properties`, these values should be written as-is (no escaping needed in `.properties` format for colons). Verify the file writer is not double-escaping backslashes if the old format used `minecraft\normal`.

If the dropdown reads its values from `server.properties` on disk (which may have been written with `\\`), add a sanitizer on read:

```kotlin
fun sanitizeLevelType(raw: String): String {
    return raw.replace("\\\\", ":").replace("\\", ":")
        .replace("minecraft:minecraft:", "minecraft:") // guard against double-replace
}
```

---

## Fix 6 — Broadcast Banner Not Dismissing When Firestore Value is `false`

### What to do
The broadcast listener is likely a one-time fetch or snapshot that doesn't react to value going `false`. Find the Firestore listener in the home/broadcast logic (likely in `ServerStateHolder.kt` or a ViewModel) and ensure the `false` case actively hides the banner:

```kotlin
firestore.collection("broadcasts")
    .document("current")
    .addSnapshotListener { snapshot, error ->
        if (error != null || snapshot == null) return@addSnapshotListener

        val isActive = snapshot.getBoolean("active") ?: false
        val message = snapshot.getString("message") ?: ""

        _broadcastState.value = if (isActive && message.isNotBlank()) {
            BroadcastState.Visible(message)
        } else {
            BroadcastState.Hidden  // ← this case must be handled
        }
    }
```

In the UI composable (HomeScreen or wherever the orange banner is rendered):

```kotlin
when (val broadcast = broadcastState) {
    is BroadcastState.Visible -> BroadcastBanner(broadcast.message)
    is BroadcastState.Hidden  -> { /* render nothing */ }
}
```

Also fix the known typo `'Mantainance'` → `'Maintenance'` in the broadcast label while you're here.

---

## Fix 7 — Player Ping Not Visible (`PlayerDataManager.kt` / `ConsoleScreen.kt`)

### What to do
Ping data comes from Paper's `/list` command output or RCON `getPlayerLatency`. Find where player data is fetched and make sure ping is included.

**Option A — via RCON (recommended):**
```kotlin
// Paper supports this Bukkit API call via RCON plugin bridge or log parsing
// Send: /spark health or parse log for ping values
// Better: use Paper's player list with ping via a plugin or log parser
```

**Option B — parse Paper console output:**
Paper 1.20+ prints ping in some contexts. Add a log parser for:
```
<PlayerName> (ping: Xms)
```

**Option C — use a lightweight RCON command:**
Send `list` via RCON and parse. For per-player ping, use a Paper plugin side command or `PlayerJoinEvent` ping logging.

**Quickest fix** — in `PlayerDataManager.kt`, wherever `PlayerInfo` is built, add:

```kotlin
data class PlayerInfo(
    val name: String,
    val uuid: String,
    val ping: Int = -1  // -1 = unavailable, show "..." in UI
)
```

In the player card composable, replace `"Ping unavailable"` with:

```kotlin
val pingText = if (player.ping >= 0) "${player.ping}ms" else "..."
Text(pingText, color = pingColor(player.ping))

fun pingColor(ping: Int) = when {
    ping < 0   -> Color.Gray
    ping < 80  -> Color(0xFF3DDC84)  // green
    ping < 150 -> Color(0xFFFFB142)  // orange
    else       -> Color(0xFFFF4757)  // red
}
```

For actual ping values, the most reliable path is a small PocketCraft companion plugin that runs `Bukkit.getOnlinePlayers().forEach { sendPingToApp(it.name, it.ping) }` via RCON or a local socket back to the Android app. Add this as a bundled plugin alongside Geyser/Via.

---

## Files to modify
- `PluginManager.kt` — Fix 1 (auto-update)
- `ServerHostService.kt` — Fix 1 (call auto-update), Fix 2 (join message), Fix 3 (notification stages)
- `ServerLauncher.kt` — Fix 4 (RAM/render cap)
- `AppPreferences.kt` — Fix 4 (max power flag)
- `server.properties` editor UI / writer — Fix 5 (level-type escaping)
- `ServerStateHolder.kt` or broadcast ViewModel — Fix 6 (Firestore listener)
- HomeScreen composable — Fix 6 (banner hide on false), Fix 6 (typo fix)
- `PlayerDataManager.kt` — Fix 7 (ping field)
- Player card composable — Fix 7 (ping display + color)

## Do NOT touch
- `RelayManager.kt` — not related to any of these issues
- `launcher.c` — RAM cap should be enforced in Kotlin layer before args are passed
