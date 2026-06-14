# PocketCraft — Master Agent (Full Restoration + New Features)

## Your Role
You are a senior Android development agent. Your job is to implement a large set of features, fixes, and UI changes to the PocketCraft Android app. Work through the TODO list in order. Read ALL existing code carefully before touching anything. Do not ask questions. If you are confused about any feature, refer to the reference MD files listed at the bottom of this document.

**Package name:** `com.pocketcraft.server`
**UI toolkit:** Jetpack Compose
**Design:** Pitch black dark theme — `#0F0F0F` backgrounds, existing layout structure unchanged, only color scheme updated.

---

## MASTER TODO LIST

Work through these in order. Check each off as you complete it.

- [x] TODO 1 — Apply new color theme
- [x] TODO 2 — Fix server crash after extended play
- [x] TODO 3 — Fix server not closing when app closes
- [x] TODO 4 — Whitelist warning system
- [x] TODO 5 — Plugin/Mod/Resource pack screen with images + search
- [x] TODO 6 — Combine World + Files screen
- [x] TODO 7 — Move Plugins to footer navigation
- [x] TODO 8 — Full world backup (all dimensions + plugins)
- [x] TODO 9 — RAM allocation default to Full
- [x] TODO 10 — Hide IP/LAN address until server starts
- [x] TODO 11 — Stop + Restart buttons after server starts
- [x] TODO 12 — Remove QR code from address sharing
- [x] TODO 13 — Extra settings options (Aternos-style)
- [x] TODO 14 — Player details screen
- [x] TODO 15 — New app icon
- [x] TODO 16 — Android killing service fix

---

## TODO 1 — New Color Theme

Keep ALL existing UI layout, structure, and component placement exactly as-is. Only change the color values.

Replace the current color scheme with this throughout the entire app:

```kotlin
// Replace in Theme.kt or Colors.kt — keep all variable names the same, just update hex values

val BackgroundPrimary    = Color(0xFF0A0A0F)   // near-black with slight blue tint
val BackgroundSecondary  = Color(0xFF13131A)   // card surfaces
val BackgroundTertiary   = Color(0xFF1C1C26)   // inputs, elevated

val AccentPrimary        = Color(0xFF6C63FF)   // purple — primary actions (replaces green)
val AccentSuccess        = Color(0xFF3DDC84)   // android green — success/online states
val AccentDanger         = Color(0xFFFF4757)   // red — stop/danger
val AccentWarning        = Color(0xFFFFB142)   // orange — warnings
val AccentInfo           = Color(0xFF2F86EB)   // blue — info

val TextPrimary          = Color(0xFFF1F1F1)
val TextSecondary        = Color(0xFF9E9EA8)
val TextMuted            = Color(0xFF5A5A6A)

val SurfaceBorder        = Color(0xFF2A2A3A)
val SurfaceBorderActive  = Color(0xFF6C63FF)   // purple border on focused/active elements
```

Apply these rules:
- Start button → `AccentSuccess` background
- Stop button → `AccentDanger` background
- Restart button → `AccentWarning` background
- Online status dot → `AccentSuccess`
- Offline status dot → `TextMuted`
- Active nav item → `AccentPrimary`
- Warning banners → `AccentWarning` border + `#1A1200` background
- Error banners → `AccentDanger` border + `#1A0000` background

Update `themes.xml`:
```xml
<style name="Theme.PocketCraft" parent="Theme.MaterialComponents.DayNight.NoActionBar">
    <item name="android:windowBackground">#0A0A0F</item>
    <item name="android:statusBarColor">#0A0A0F</item>
    <item name="android:navigationBarColor">#0A0A0F</item>
    <item name="android:forceDarkAllowed">false</item>
</style>
```

---

## TODO 2 — Fix Server Crash After Extended Play

### Root causes to investigate and fix:

**A. Memory leak in log tailing**
Find `logTailThread` or equivalent that reads `logs/latest.log`. If it reads the entire file repeatedly without truncating the buffer, it will OOM after extended play. Fix:
```kotlin
// Keep only last 1000 lines in memory
private val logBuffer = ArrayDeque<String>(1000)

fun addLogLine(line: String) {
    if (logBuffer.size >= 1000) logBuffer.removeFirst()
    logBuffer.addLast(line)
}
```

**B. JVM memory flags**
In `ServerLauncher.kt` and `launcher.c`, add these JVM args to prevent GC pressure crashes:
```
-XX:+UseG1GC
-XX:+ParallelRefProcEnabled
-XX:MaxGCPauseMillis=200
-XX:+UnlockExperimentalVMOptions
-XX:+DisableExplicitGC
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=40
-XX:G1HeapRegionSize=8M
-XX:G1ReservePercent=20
-XX:G1HeapWastePercent=5
-XX:InitiatingHeapOccupancyPercent=15
```

Add these to the existing argv array in `launcher.c` BEFORE the `-jar` argument. In `ServerLauncher.kt` add them to the args list before `-jar`.

**C. Android Doze killing threads**
Add a WakeLock to prevent Android from throttling the server threads:
```kotlin
private var wakeLock: PowerManager.WakeLock? = null

private fun acquireWakeLock() {
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = pm.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "PocketCraft:ServerWakeLock"
    )
    wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours max
}

private fun releaseWakeLock() {
    wakeLock?.let { if (it.isHeld) it.release() }
    wakeLock = null
}
```

Call `acquireWakeLock()` when server starts, `releaseWakeLock()` in `onDestroy()`.

Add permission to `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.WAKE_LOCK"/>
```

**D. Netty socket exhaustion**
Add to JVM args:
```
-Dio.netty.recycler.maxCapacityPerThread=0
-Dio.netty.recycler.linkCapacity=1024
```

**E. Monitor crash logs**
In `ServerHostService`, watch the server process exit code:
```kotlin
CoroutineScope(Dispatchers.IO).launch {
    val exitCode = serverProcess?.waitFor()
    if (exitCode != 0) {
        // Abnormal exit — log and notify
        sendBroadcast(Intent(EVENT_SERVER_CRASHED).apply {
            putExtra("exit_code", exitCode)
        })
    }
}
```

---

## TODO 3 — Fix Server Not Closing When App Closes

Find `ServerHostService`. Implement all of the following:

```kotlin
// Store process reference
private var serverProcess: Process? = null

// Graceful stop
fun stopServer() {
    try {
        serverProcess?.outputStream?.write("stop\n".toByteArray())
        serverProcess?.outputStream?.flush()
        val stopped = serverProcess?.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) ?: true
        if (!stopped) serverProcess?.destroyForcibly()
    } catch (e: Exception) {
        serverProcess?.destroyForcibly()
    } finally {
        serverProcess = null
        releaseWakeLock()
        networkJob?.cancel()
        relayManager.disconnect()
        sendBroadcast(Intent(EVENT_STOPPED))
    }
}

// Called when user swipes app from recents
override fun onTaskRemoved(rootIntent: Intent?) {
    super.onTaskRemoved(rootIntent)
    stopServer()
    stopSelf()
}

override fun onDestroy() {
    super.onDestroy()
    stopServer()
}
```

In `AndroidManifest.xml` update service declaration:
```xml
<service
    android:name=".server.ServerHostService"
    android:stopWithTask="true"
    android:foregroundServiceType="dataSync"
    android:exported="false"/>
```

Add shutdown hook in launcher:
```kotlin
Runtime.getRuntime().addShutdownHook(Thread {
    serverProcess?.destroyForcibly()
})
```

---

## TODO 4 — Whitelist Warning System

### In ServerHostService / server properties management:

Add a method to check whitelist state:
```kotlin
fun isWhitelistEnabled(context: Context, serverVersion: String): Boolean {
    val propsFile = File(context.filesDir, "servers/$serverVersion/server.properties")
    if (!propsFile.exists()) return false
    return propsFile.readLines().any { it.trim() == "white-list=true" }
}
```

### On home screen — persistent warning banner

Show this banner when server is running AND whitelist is off. Place it directly below the server status card:

```kotlin
if (serverRunning && !isWhitelistEnabled) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0800)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AccentWarning),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null,
                     tint = AccentWarning, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open server — anyone can join",
                     color = AccentWarning, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text("Whitelist is disabled. Any player can connect to your server.",
                 color = Color(0xFFB3B3B3), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { enableWhitelist() },
                    border = BorderStroke(1.dp, AccentSuccess),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentSuccess)
                ) { Text("Enable Whitelist", fontSize = 12.sp) }
                TextButton(
                    onClick = { userAcknowledgedOpenServer = true }
                ) { Text("I understand the risks", fontSize = 12.sp, color = TextMuted) }
            }
        }
    }
}
```

### In Players screen — minimal persistent badge

Show a small badge at the top of the players list when whitelist is off, even after user acknowledged:

```kotlin
if (!isWhitelistEnabled) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A0800))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Default.LockOpen, contentDescription = null,
             tint = AccentWarning, modifier = Modifier.size(12.dp))
        Text("Open join mode is ON",
             fontSize = 11.sp, color = AccentWarning)
    }
}
```

---

## TODO 5 — Plugins / Mods / Resource Packs Screen

### Three tabs: Plugins | Mods | Resource Packs

Each tab has:
- **Search bar** at top — filters list by name in real time
- **Item list** — shows icon + name + size + toggle + delete
- **Add button** — opens sub-screen with URL download + file upload options

### Item icons

For plugins and mods — fetch icon from Modrinth API:
```kotlin
suspend fun fetchModrinthIcon(slug: String): String? {
    return try {
        val url = "https://api.modrinth.com/v2/project/$slug"
        val response = URL(url).readText()
        val json = JSONObject(response)
        json.optString("icon_url").takeIf { it.isNotEmpty() }
    } catch (e: Exception) { null }
}
```

For resource packs — extract `pack.png` from the zip/folder:
```kotlin
fun getResourcePackIcon(packDir: File): File? {
    return File(packDir, "pack.png").takeIf { it.exists() }
}
```

If no icon found — show a placeholder with the first letter of the plugin/mod name in a colored circle.

### Directory paths
```kotlin
// Plugins
context.filesDir/servers/{version}/plugins/

// Mods (for future mod loader support)
context.filesDir/servers/{version}/mods/

// Resource packs
context.filesDir/servers/{version}/resourcepacks/
```

Create these directories if they don't exist.

### Search bar composable
```kotlin
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search..."
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder, color = TextMuted, fontSize = 14.sp) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null,
                 tint = TextMuted, modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = null,
                         tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = BackgroundTertiary,
            unfocusedContainerColor = BackgroundTertiary,
            focusedIndicatorColor = AccentPrimary,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}
```

### Refer to `POCKETCRAFT_PLUGINS_PLAYERS_AGENT.md` for full PluginManager.kt implementation.

---

## TODO 6 — Combine World + Files Screen

Merge into one screen called **"Storage"** with two tabs:

**Tab 1 — Worlds**
- World folder list
- Upload world button
- Each world shows: name, size, last modified
- Long press → rename, delete options

**Tab 2 — Files**
- File browser starting at `servers/{version}/`
- Show folders and files with icons
- Tap folder to navigate in
- Tap file → options: view (text files), share, delete
- Back navigation

Remove the separate World screen and Files screen from navigation. Replace with single Storage screen.

---

## TODO 7 — Move Plugins to Footer Navigation

Find the existing bottom navigation bar. Update nav items to:

```
Home | Console | Storage | Plugins | Settings
```

Remove Players from footer (access via Home screen player card instead).
Remove separate Files and Worlds from footer (now under Storage).
Add Plugins to footer pointing to the new Plugins/Mods/Resource Packs screen.

---

## TODO 8 — Full World Backup (All Dimensions + Plugins)

### What to back up (everything needed to fully restore a server)
```
servers/{version}/
├── world/          ← overworld
├── world_nether/   ← nether (DIM-1)
├── world_the_end/  ← the end (DIM1)
├── plugins/        ← all plugin jars + plugin configs
├── server.properties
├── ops.json
├── whitelist.json
├── banned-players.json
└── banned-ips.json
```

### Update DriveBackupManager.kt

Replace `zipFolder` to include all of the above:

```kotlin
private fun zipServerFolder(context: Context, serverVersion: String, destZip: File) {
    val baseDir = File(context.filesDir, "servers/$serverVersion")
    val foldersToBackup = listOf(
        "world", "world_nether", "world_the_end", "plugins"
    )
    val filesToBackup = listOf(
        "server.properties", "ops.json", "whitelist.json",
        "banned-players.json", "banned-ips.json"
    )

    ZipOutputStream(FileOutputStream(destZip)).use { zip ->
        // Zip folders
        foldersToBackup.forEach { folderName ->
            val folder = File(baseDir, folderName)
            if (folder.exists()) {
                folder.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val entryName = "$folderName/${file.relativeTo(folder).path}"
                        zip.putNextEntry(ZipEntry(entryName))
                        FileInputStream(file).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
        // Zip individual files
        filesToBackup.forEach { fileName ->
            val file = File(baseDir, fileName)
            if (file.exists()) {
                zip.putNextEntry(ZipEntry(fileName))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}
```

### Restore function

When restoring, extract zip back to `servers/{version}/` preserving all folder structure:

```kotlin
suspend fun restore(context: Context, backup: BackupInfo, serverVersion: String, onProgress: (String) -> Unit): BackupResult {
    // Download zip from Drive
    // Extract — each entry in zip maps directly to servers/{version}/{entryName}
    // world/ → servers/{version}/world/
    // world_nether/ → servers/{version}/world_nether/
    // plugins/ → servers/{version}/plugins/
    // server.properties → servers/{version}/server.properties
    // etc.
    // Do NOT delete existing server folder first — overwrite file by file
    // This preserves any files not in the backup
}
```

Show restore confirmation dialog:
```
"This will restore:
✓ Overworld, Nether, End dimensions
✓ All plugins and their configs
✓ Server settings (whitelist, ops, bans)

Your current world will be overwritten.
Make sure the server is stopped before restoring."
```

Only allow restore when server is stopped. If server is running, show error: "Stop the server before restoring a backup."

---

## TODO 9 — RAM Default to Full

In `AppPreferences.kt`, change the default value:

```kotlin
var ramMode: String
    get() = prefs.getString("ram_mode", "full") ?: "full"  // was "low", now "full"
    set(value) = prefs.edit().putString("ram_mode", value).apply()
```

---

## TODO 10 — Hide IP/LAN Address Until Server Starts

Find the server status card on the home screen. The address section should only show when `serverRunning == true`:

```kotlin
// Only show when server is running
if (serverRunning) {
    // Public address row (if relay connected and not LAN only)
    if (!isLanOnly && publicAddress.isNotEmpty()) {
        AddressRow(label = "Public", address = publicAddress)
    }
    // LAN address always shown when server running
    if (lanAddress.isNotEmpty()) {
        AddressRow(label = "LAN", address = lanAddress)
    }
} else {
    // Server offline — show placeholder
    Text(
        text = "Start the server to get your address",
        fontSize = 13.sp,
        color = TextMuted,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
```

---

## TODO 11 — Stop + Restart Buttons After Server Starts

When server is running, replace the single Start button with three buttons in a row:

```kotlin
if (serverRunning) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Stop button
        Button(
            onClick = { stopServer() },
            colors = ButtonDefaults.buttonColors(containerColor = AccentDanger),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Stop, contentDescription = null,
                 modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Stop", fontWeight = FontWeight.Bold)
        }

        // Restart button
        Button(
            onClick = { restartServer() },
            colors = ButtonDefaults.buttonColors(containerColor = AccentWarning),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null,
                 modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Restart", fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
} else {
    // Start button — full width
    Button(
        onClick = { startServer() },
        colors = ButtonDefaults.buttonColors(containerColor = AccentSuccess),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null,
             modifier = Modifier.size(18.dp), tint = Color.Black)
        Spacer(Modifier.width(8.dp))
        Text("Start Server", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 16.sp)
    }
}
```

### Restart function
```kotlin
fun restartServer() {
    CoroutineScope(Dispatchers.IO).launch {
        stopServer()
        delay(3000) // wait for process to fully die
        startServer()
    }
}
```

---

## TODO 12 — Remove QR Code from Address Sharing

Find any QR code generation or display in the address sharing flow. Remove it entirely. Replace the share UI with a simple row:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    // Copy address button
    OutlinedButton(
        onClick = { copyToClipboard(address) },
        modifier = Modifier.weight(1f),
        border = BorderStroke(1.dp, SurfaceBorder)
    ) {
        Icon(Icons.Default.ContentCopy, contentDescription = null,
             modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("Copy Address", fontSize = 13.sp)
    }

    // Share button
    OutlinedButton(
        onClick = { shareAddress(address) },
        modifier = Modifier.weight(1f),
        border = BorderStroke(1.dp, SurfaceBorder)
    ) {
        Icon(Icons.Default.Share, contentDescription = null,
             modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("Share", fontSize = 13.sp)
    }
}
```

---

## TODO 13 — Extra Settings Options (Aternos-style)

Add these settings to the Settings screen, grouped by section. Each setting reads/writes directly to `server.properties`:

### Server section
| Setting | Property key | Type | Default |
|---|---|---|---|
| Max players | `max-players` | Number stepper (1-100) | 20 |
| Gamemode | `gamemode` | Dropdown (Survival/Creative/Adventure/Spectator) | survival |
| Difficulty | `difficulty` | Dropdown (Peaceful/Easy/Normal/Hard) | normal |
| Whitelist | `white-list` | Toggle | false |
| Enforce whitelist | `enforce-whitelist` | Toggle | false |
| PVP | `pvp` | Toggle | true |
| Command blocks | `enable-command-block` | Toggle | false |
| Fly | `allow-flight` | Toggle | false |
| Monsters | `spawn-monsters` | Toggle | true |
| Nether | `allow-nether` | Toggle | true |
| Force gamemode | `force-gamemode` | Toggle | false |
| Hardcore | `hardcore` | Toggle | false |
| View distance | `view-distance` | Number stepper (2-32) | 10 |
| Simulation distance | `simulation-distance` | Number stepper (2-32) | 5 |
| Spawn protection | `spawn-protection` | Number stepper (0-100) | 16 |
| World name | `level-name` | Text input | world |
| Seed | `level-seed` | Text input | (empty) |
| World type | `level-type` | Dropdown | minecraft:normal |
| Generate structures | `generate-structures` | Toggle | true |
| Broadcast console to ops | `broadcast-console-to-ops` | Toggle | false |
| Hide online players | `hide-online-players` | Toggle | false |
| Resource pack URL | `resource-pack` | Text input | (empty) |

### Helper functions for reading/writing server.properties:
```kotlin
fun readServerProperty(context: Context, serverVersion: String, key: String): String? {
    val file = File(context.filesDir, "servers/$serverVersion/server.properties")
    if (!file.exists()) return null
    return file.readLines()
        .firstOrNull { it.startsWith("$key=") }
        ?.removePrefix("$key=")
        ?.trim()
}

fun writeServerProperty(context: Context, serverVersion: String, key: String, value: String) {
    val file = File(context.filesDir, "servers/$serverVersion/server.properties")
    if (!file.exists()) return
    val lines = file.readLines().toMutableList()
    val idx = lines.indexOfFirst { it.startsWith("$key=") }
    if (idx >= 0) {
        lines[idx] = "$key=$value"
    } else {
        lines.add("$key=$value")
    }
    file.writeText(lines.joinToString("\n"))
}
```

Show a note at the top of Settings: "Changes take effect after server restart."

---

## TODO 14 — Player Details Screen

Refer to `POCKETCRAFT_PLUGINS_PLAYERS_AGENT.md` and `POCKETCRAFT_INVENTORY_LOCATION_AGENT.md` for the full implementation.

Summary of what the player detail screen must include:
- Player head (64px from mc-heads.net)
- Username + UUID
- Online badge + gamemode dropdown
- Health bar (hearts) + Kill + Heal buttons
- Hunger bar (drumsticks) + Starve + Feed buttons
- XP level bar
- Inventory grid (armor, main 27 slots, hotbar, offhand)
- Control toggles: Whitelisted, Banned, Operator
- Information: current position + teleport, respawn location, last death
- Statistics: playtime, kills, deaths, KDR, blocks broken, items used, entities killed
- Delete player data section with checkboxes

Access from: tapping a player row on the home screen player list.

---

## TODO 15 — New App Icon

Replace `ic_launcher_foreground.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Background gradient light green to dark green -->
    <path
        android:fillColor="#66BB6A"
        android:pathData="M14,14 Q14,0 28,0 L80,0 Q94,0 94,14 L94,94 Q94,108 80,108 L28,108 Q14,108 14,94 Z" />

    <!-- Subtle highlight top -->
    <path
        android:fillColor="#FFFFFF"
        android:fillAlpha="0.08"
        android:pathData="M14,14 Q14,0 28,0 L80,0 Q94,0 94,14 L94,40 L14,40 Z" />

    <!-- Left eye -->
    <path
        android:fillColor="#1A3E1C"
        android:pathData="M24,30 Q24,27 27,27 L44,27 Q47,27 47,30 L47,47 Q47,50 44,50 L27,50 Q24,50 24,47 Z" />

    <!-- Left eye highlight -->
    <path
        android:fillColor="#FFFFFF"
        android:fillAlpha="0.12"
        android:pathData="M26,29 L31,29 L31,34 L26,34 Z" />

    <!-- Right eye -->
    <path
        android:fillColor="#1A3E1C"
        android:pathData="M61,30 Q61,27 64,27 L81,27 Q84,27 84,30 L84,47 Q84,50 81,50 L64,50 Q61,50 61,47 Z" />

    <!-- Right eye highlight -->
    <path
        android:fillColor="#FFFFFF"
        android:fillAlpha="0.12"
        android:pathData="M63,29 L68,29 L68,34 L63,34 Z" />

    <!-- Mouth top center -->
    <path
        android:fillColor="#1A3E1C"
        android:pathData="M37,58 Q37,55 40,55 L68,55 Q71,55 71,58 L71,66 Q71,69 68,69 L40,69 Q37,69 37,66 Z" />

    <!-- Mouth bottom left -->
    <path
        android:fillColor="#1A3E1C"
        android:pathData="M24,69 Q24,66 27,66 L43,66 Q46,66 46,69 L46,79 Q46,82 43,82 L27,82 Q24,82 24,79 Z" />

    <!-- Mouth bottom right -->
    <path
        android:fillColor="#1A3E1C"
        android:pathData="M62,69 Q62,66 65,66 L81,66 Q84,66 84,69 L84,79 Q84,82 81,82 L65,82 Q62,82 62,79 Z" />
</vector>
```

Update `ic_launcher_background.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
        android:startColor="#66BB6A"
        android:endColor="#2E7D32"
        android:angle="270" />
</shape>
```

---

## TODO 16 — Fix Android Killing the Service

### Foreground service notification

Make sure the server runs as a proper foreground service with a persistent notification. Find the existing notification setup and ensure:

```kotlin
private fun createForegroundNotification(): Notification {
    val channelId = "pocketcraft_server"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Server Running",
            NotificationManager.IMPORTANCE_LOW  // LOW = no sound, stays persistent
        ).apply {
            description = "Shows while your Minecraft server is running"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    return NotificationCompat.Builder(this, channelId)
        .setContentTitle("PocketCraft Server Running")
        .setContentText("Tap to manage your server")
        .setSmallIcon(R.drawable.ic_notification) // use app icon
        .setOngoing(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()
}
```

Call `startForeground(1, createForegroundNotification())` immediately in `onStartCommand`.

### AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>

<service
    android:name=".server.ServerHostService"
    android:stopWithTask="true"
    android:foregroundServiceType="dataSync"
    android:exported="false"/>
```

### Battery optimization prompt

On first launch after server is set up, prompt user to disable battery optimization:

```kotlin
fun requestIgnoreBatteryOptimization(context: Context) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}
```

Show a dialog before launching this intent:
```
"For best performance, allow PocketCraft to run in the background without battery restrictions.
This prevents Android from closing your server unexpectedly."

[Allow]  [Not now]
```

---

## Reference MD Files

If confused about any feature, refer to these existing files:

| Feature | MD File |
|---|---|
| Relay integration | POCKETCRAFT_RELAY_AGENT.md |
| RAM settings, world upload | POCKETCRAFT_FEATURES_AGENT.md |
| Full UI redesign, plugins, sounds | POCKETCRAFT_UI_FEATURES_AGENT.md |
| Plugin manager + player details | POCKETCRAFT_PLUGINS_PLAYERS_AGENT.md |
| Inventory + location | POCKETCRAFT_INVENTORY_LOCATION_AGENT.md |
| Firebase analytics | POCKETCRAFT_FIREBASE_AGENT.md |
| Lottie animations | POCKETCRAFT_LOTTIE_AGENT.md |
| Google Drive backup | POCKETCRAFT_DRIVE_BACKUP_AGENT.md |
| Network awareness | POCKETCRAFT_NETWORK_LIFECYCLE_AGENT.md |

---

## Master Quality Checklist

Before finishing, verify every TODO:

**TODO 1 — Theme**
- [x] New color palette applied globally
- [x] No hardcoded old colors remaining
- [x] Status bar and nav bar updated to `#0A0A0F`
- [x] Start/Stop/Restart buttons use correct accent colors

**TODO 2 — Crash fix**
- [x] G1GC flags added to launcher.c and ServerLauncher.kt
- [x] Log buffer capped at 1000 lines
- [x] WakeLock acquired on server start, released on stop
- [x] WAKE_LOCK permission in manifest

**TODO 3 — Server close fix**
- [x] `serverProcess` stored as field
- [x] `stopServer()` sends stop command, waits 10s, force kills
- [x] `onTaskRemoved` calls `stopServer()` + `stopSelf()`
- [x] `stopWithTask="true"` in manifest
- [x] Shutdown hook added

**TODO 4 — Whitelist warning**
- [x] Warning banner on home screen when whitelist off + server running
- [x] "Enable Whitelist" button works
- [x] "I understand the risks" button dismisses banner
- [x] Minimal badge still shows in Players screen regardless
- [x] Warning reappears on next server start if still disabled

**TODO 5 — Plugins/Mods/Resource Packs**
- [x] Three tabs: Plugins, Mods, Resource Packs
- [x] Search bar filters in real time
- [x] Item icons shown (Modrinth API or pack.png)
- [x] Placeholder icon for missing images
- [x] Upload from storage works
- [x] Download by URL works with progress
- [x] Enable/disable toggle (renames .jar.disabled)
- [x] Delete with confirmation

**TODO 6 — Storage screen**
- [x] World tab and Files tab combined into Storage screen
- [x] World tab shows world folders
- [x] Files tab is a working file browser

**TODO 7 — Footer nav**
- [x] Footer: Home | Console | Storage | Plugins | Settings
- [x] Plugins opens new Plugins/Mods/Resource Packs screen

**TODO 8 — Full backup**
- [x] world/, world_nether/, world_the_end/ all backed up
- [x] plugins/ backed up including configs
- [x] server.properties, ops.json, whitelist.json backed up
- [x] Restore preserves all folder structure
- [x] Restore blocked if server is running
- [x] Restore confirmation lists what will be restored

**TODO 9 — RAM default**
- [x] Default ram_mode is "full" not "low"

**TODO 10 — Hide address**
- [x] Address section hidden when server not running
- [x] Placeholder text shown instead

**TODO 11 — Stop + Restart buttons**
- [x] Start button full width when stopped
- [x] Stop + Restart buttons shown when running
- [x] Correct colors: Stop=red, Restart=orange
- [x] Restart stops then starts after 3s delay

**TODO 12 — No QR code**
- [x] QR code removed from sharing
- [x] Copy + Share buttons shown instead

**TODO 13 — Extra settings**
- [x] All Aternos-style settings implemented
- [x] Settings read from and write to server.properties
- [x] "Changes require restart" note shown

**TODO 14 — Player details**
- [x] Full player detail screen accessible from home player list
- [x] All sections present per reference MDs

**TODO 15 — New icon**
- [x] ic_launcher_foreground.xml updated
- [x] ic_launcher_background.xml updated

**TODO 16 — Android kill fix**
- [x] Foreground notification with IMPORTANCE_LOW
- [x] startForeground called immediately in onStartCommand
- [x] FOREGROUND_SERVICE permissions in manifest
- [x] Battery optimization prompt on first setup

- [ ] App builds without errors
