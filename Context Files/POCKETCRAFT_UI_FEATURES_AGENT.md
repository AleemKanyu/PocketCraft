# PocketCraft UI & Features Agent

## Your Role
You are a feature and UI implementation agent. Your job is to implement multiple features and a full UI redesign in the PocketCraft Android app. Read the existing code carefully before making any changes. Do not ask questions — figure out the structure from the code and implement everything completely.

**Package name:** `com.pocketcraft.server`
**UI toolkit:** Jetpack Compose
**Design target:** Clean modern dark — like Discord/Spotify. NOT Material default. Think deep dark backgrounds, subtle card elevation, clean typography, smooth animations.

---

## Design System — Apply Everywhere

### Color Palette
Define these in a `Theme.kt` or `Colors.kt` file and use them throughout:

```kotlin
// Backgrounds
val BackgroundPrimary = Color(0xFF0F0F0F)      // pitch black — main background
val BackgroundSecondary = Color(0xFF1A1A1A)    // slightly lighter — cards
val BackgroundTertiary = Color(0xFF242424)     // inputs, elevated surfaces

// Accents
val AccentGreen = Color(0xFF57F287)            // primary action (like Discord green)
val AccentRed = Color(0xFFED4245)              // danger/stop
val AccentYellow = Color(0xFFFEE75C)           // warnings
val AccentBlue = Color(0xFF5865F2)             // info/secondary actions

// Text
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB3B3B3)
val TextMuted = Color(0xFF6B6B6B)

// Surface
val SurfaceBorder = Color(0xFF2A2A2A)
```

### Typography
Use `Inter` font family if available, otherwise system default. Apply these weights:
- Headings: `FontWeight.Bold`, 20-24sp
- Body: `FontWeight.Normal`, 14-16sp
- Labels/captions: `FontWeight.Medium`, 12sp, `TextSecondary` color

### Card style
All cards should use:
```kotlin
Card(
    colors = CardDefaults.cardColors(containerColor = BackgroundSecondary),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, SurfaceBorder)
)
```

### Dark mode
Force pitch black dark mode app-wide. In `Theme.kt`, set:
```kotlin
val darkColorScheme = darkColorScheme(
    background = BackgroundPrimary,
    surface = BackgroundSecondary,
    primary = AccentGreen,
    error = AccentRed
)
```
Override system theme — always use dark. Never use light mode.

---

## Feature 1 — Full UI Redesign

### Home/Dashboard Screen
Redesign the home screen with these sections in order:

**1. Header bar**
- App name "PocketCraft" in bold left-aligned
- Settings icon button on the right

**2. Server status card**
Large card showing:
- Server status indicator: animated pulsing green dot when running, grey when stopped
- Status text: "Server Online" / "Server Offline"
- Public address (e.g. `mine.pocketcraft.online:25501`) with a copy-to-clipboard icon button next to it
- LAN address below it in `TextSecondary` color
- Start/Stop button — full width, green when stopped (Start), red when running (Stop)
- Smooth color transition animation on state change

**3. Stats row** (visible only when server is running)
Three equal-width cards in a row:
- Players online (e.g. "3/20")
- RAM usage (e.g. "743 MB")
- Uptime (e.g. "01:23:45") — count up from when server started

**4. RAM settings card** (visible only when server is stopped)
As previously specified — Low / Manual / Full with slider for manual.

**5. Quick actions row**
Icon buttons in a horizontal scrollable row:
- Console, Players, Plugins, Worlds, Files, Settings
- Each is a rounded square card with icon + label below

### Console Screen
- Black background with monospace font for log output
- Color code log levels: INFO=white, WARN=yellow, ERROR=red, DONE/success=green
- Input bar pinned to bottom with send button
- Auto-scroll to bottom on new output
- Copy button on long press of any log line

### Settings Screen
Redesign with grouped sections:
- **Server** — version, game mode, difficulty, max players, render distance
- **RAM** — same RAM allocation card as home screen
- **Network** — show current relay address, relay status
- **App** — dark mode toggle (but always pitch black), sounds toggle, about

### All other existing screens
Apply the same color palette, card style, and typography. No screen should have white/light backgrounds. Every screen must feel consistent.

---

## Feature 2 — Plugin Support

### Plugin Manager Screen
Create a new screen accessible from the quick actions row on home. It has two tabs:

**Tab 1 — Installed Plugins**
- List of all `.jar` files in the server's `plugins/` directory
- Each item shows: plugin name, file size, enable/disable toggle, delete button
- Empty state: "No plugins installed yet"
- Reload plugins button at top (sends `reload` command to server console)

**Tab 2 — Add Plugin**
Two options shown as cards:

**Option A — Upload from storage**
```kotlin
val pluginPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
) { uri ->
    uri?.let {
        scope.launch {
            PluginManager.installFromUri(context, it, serverVersion)
        }
    }
}
// Launch with:
pluginPickerLauncher.launch("application/java-archive")
// Also accept: "*/*" as fallback since some file managers don't support java-archive mime
```

**Option B — Download by URL**
- Text input field: "Paste plugin URL (.jar)"
- Supports direct `.jar` URLs from Spigot, Modrinth, Hangar, CurseForge
- Download button — shows progress indicator while downloading
- Validates that URL ends in `.jar` before downloading

### Create PluginManager.kt

```kotlin
package com.pocketcraft.server

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

object PluginManager {

    fun getPluginsDir(context: Context, serverVersion: String): File {
        return File(context.filesDir, "servers/$serverVersion/plugins").also { it.mkdirs() }
    }

    fun getInstalledPlugins(context: Context, serverVersion: String): List<File> {
        return getPluginsDir(context, serverVersion)
            .listFiles { f -> f.extension == "jar" }
            ?.toList() ?: emptyList()
    }

    suspend fun installFromUri(context: Context, uri: Uri, serverVersion: String): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val pluginsDir = getPluginsDir(context, serverVersion)
                val fileName = getFileNameFromUri(context, uri) ?: "plugin_${System.currentTimeMillis()}.jar"
                val destFile = File(pluginsDir, fileName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw Exception("Could not open file")

                Result.success(destFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun installFromUrl(context: Context, url: String, serverVersion: String, onProgress: (Int) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                if (!url.endsWith(".jar")) throw Exception("URL must point to a .jar file")

                val pluginsDir = getPluginsDir(context, serverVersion)
                val fileName = url.substringAfterLast("/")
                val destFile = File(pluginsDir, fileName)

                val connection = URL(url).openConnection()
                connection.connect()
                val totalSize = connection.contentLength

                connection.getInputStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0
                        var bytes = input.read(buffer)
                        while (bytes != -1) {
                            output.write(buffer, 0, bytes)
                            downloaded += bytes
                            if (totalSize > 0) {
                                onProgress((downloaded * 100 / totalSize))
                            }
                            bytes = input.read(buffer)
                        }
                    }
                }

                Result.success(destFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun deletePlugin(file: File): Boolean = file.delete()

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
```

---

## Feature 3 — Player Details (Aternos-style)

### What to build
When the server is running and players are online, show a players section on the home screen and a full player management screen.

### How to get player data
Use the Paper/Bukkit RCON protocol or parse server log output. The simplest approach without RCON:

**Parse console output** for these patterns:
- Player join: `[Server thread/INFO]: PlayerName joined the game`
- Player leave: `[Server thread/INFO]: PlayerName left the game`
- Player health/hunger: send `data get entity PlayerName` via console, parse output

Maintain an in-memory player list in `ServerHostService` that updates as log lines are parsed.

### Player list card on home screen
When server is running, show a card:
```
Online Players (3/20)
[Avatar] Steve      ● Online    >
[Avatar] Alex       ● Online    >
[Avatar] Notch      ● Online    >
```
- Player avatar: fetch from `https://mc-heads.net/avatar/{username}/32` (free API, no auth needed)
- Tap any player to open player detail bottom sheet

### Player detail bottom sheet
Shows:
- Player head (64px, from mc-heads.net)
- Username + joined time
- **Health bar** — 0-20 hearts, shown as red heart icons (get via `data get entity` command)
- **Hunger bar** — 0-20, shown as drumstick icons
- **Game mode** badge (Survival/Creative/Adventure/Spectator)
- **Location** — X Y Z coordinates
- Action buttons row:
  - `Kick` — sends `kick PlayerName` to console
  - `Ban` — sends `ban PlayerName`
  - `Op` — sends `op PlayerName`
  - `Give` — opens a simple item give dialog

### Player data refresh
Refresh player details every 5 seconds while the bottom sheet is open by sending commands to the server console and parsing output.

---

## Feature 4 — Sounds

### When to play sounds
- **Server started** — play a short success chime
- **Server stopped** — play a short stop sound
- **Player joins** — play a subtle notification ping
- **Player leaves** — play a subtle lower-pitched ping
- **Error/tunnel failed** — play an error sound

### Implementation
Use Android's `SoundPool` for low-latency playback:

```kotlin
package com.pocketcraft.server

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

object AppSounds {
    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<String, Int>()

    fun init(context: Context) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()

        // Load sounds from res/raw/
        // Add these sound files to res/raw/:
        // server_start.mp3, server_stop.mp3, player_join.mp3, player_leave.mp3, error.mp3
        soundIds["server_start"] = soundPool!!.load(context, R.raw.server_start, 1)
        soundIds["server_stop"] = soundPool!!.load(context, R.raw.server_stop, 1)
        soundIds["player_join"] = soundPool!!.load(context, R.raw.player_join, 1)
        soundIds["player_leave"] = soundPool!!.load(context, R.raw.player_leave, 1)
        soundIds["error"] = soundPool!!.load(context, R.raw.error, 1)
    }

    fun play(name: String, volume: Float = 1f) {
        if (AppPreferences.soundsEnabled) {
            soundIds[name]?.let { id ->
                soundPool?.play(id, volume, volume, 1, 0, 1f)
            }
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}
```

Add `soundsEnabled` boolean to `AppPreferences`:
```kotlin
var soundsEnabled: Boolean
    get() = prefs.getBoolean("sounds_enabled", true)
    set(value) = prefs.edit().putBoolean("sounds_enabled", value).apply()
```

**Sound files:** Generate or download 5 short royalty-free sound effects and place them in `app/src/main/res/raw/`. Alternatively, use Android's built-in `RingtoneManager` sounds as fallback if custom files are not available:
```kotlin
// Fallback if no custom sounds
RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)).play()
```

---

## Feature 5 — Uptime Counter

Add a live uptime counter to the stats row on the home screen. It starts counting from 0 when the server starts and displays as `HH:MM:SS`.

```kotlin
var serverStartTime by remember { mutableStateOf(0L) }
var uptime by remember { mutableStateOf("00:00:00") }

LaunchedEffect(serverRunning) {
    if (serverRunning) {
        serverStartTime = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - serverStartTime
            val hours = elapsed / 3600000
            val minutes = (elapsed % 3600000) / 60000
            val seconds = (elapsed % 60000) / 1000
            uptime = "%02d:%02d:%02d".format(hours, minutes, seconds)
            delay(1000)
        }
    } else {
        uptime = "00:00:00"
    }
}
```

---

## Quality Checklist

Before finishing, verify:

- [ ] All screens use pitch black background (`#0F0F0F`)
- [ ] No white or light backgrounds anywhere in the app
- [ ] Color palette is defined in one place and referenced everywhere
- [ ] Server status card has pulsing animation when online
- [ ] Start/Stop button animates color change smoothly
- [ ] Stats row (players/RAM/uptime) shows only when server is running
- [ ] RAM settings card shows only when server is stopped
- [ ] Quick actions row is horizontally scrollable
- [ ] Console log lines are color coded by level
- [ ] Plugin manager has both tabs (Installed + Add)
- [ ] `PluginManager.kt` exists with correct package name
- [ ] Plugin upload from storage works with `.jar` files
- [ ] Plugin download by URL works with progress indicator
- [ ] Player list shows on home screen when players are online
- [ ] Player avatars load from mc-heads.net
- [ ] Player detail bottom sheet shows health, hunger, location, gamemode
- [ ] Kick/Ban/Op actions work from player detail sheet
- [ ] `AppSounds.kt` exists and plays sounds on server start/stop/player join/leave/error
- [ ] Sounds can be toggled in settings
- [ ] Uptime counter increments every second while server is running
- [ ] Dark mode is always pitch black, never system light mode
- [ ] App builds without errors

---

## What This Does NOT Change

- Relay system (`RelayManager.kt`, `ServerHostService.kt` relay logic) — unchanged
- RAM calculation logic in `ServerLauncher.kt` and `launcher.c` — unchanged
- World import logic (`WorldImporter.kt`) — unchanged
- Server version selection logic — unchanged
