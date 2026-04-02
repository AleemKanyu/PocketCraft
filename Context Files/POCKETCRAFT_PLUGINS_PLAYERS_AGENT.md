# PocketCraft — Plugin Support & Player Details Agent

## Your Role
You are a feature implementation agent. Your job is to add two major features to the PocketCraft Android app. Read the existing code carefully before making any changes. Do not ask questions — figure out the structure from the code and implement everything completely.

**Package name:** `com.pocketcraft.server`
**UI toolkit:** Jetpack Compose
**Design:** Match existing app style exactly — do not change any existing UI, colors, or screens unless explicitly stated below.

---

## Feature 1 — Plugin Support

### Plugin Manager Screen
Create a new screen accessible from the existing navigation. It has two tabs:

---

### Tab 1 — Installed Plugins

- Scan the server's `plugins/` directory for all `.jar` files
- Each plugin item shows:
  - Plugin name (filename without `.jar`)
  - File size in KB/MB
  - Enable/Disable toggle — renames file to `pluginname.jar.disabled` when disabled, renames back when enabled
  - Delete button with confirmation dialog
- Empty state message: `"No plugins installed yet"`
- **Reload button** at top — sends `reload` command to the server console (only enabled when server is running)
- Pull to refresh to rescan the plugins directory

Plugin directory path — search codebase for `files/servers` to find the exact base path, then append `/plugins/`. Example:
```
context.filesDir/servers/1.20.4/plugins/
```

---

### Tab 2 — Add Plugin

Two cards shown side by side or stacked:

**Card A — Upload from device storage**
- Button: "Upload .jar file"
- Opens Android file picker filtered to `.jar` files
- If file manager doesn't support `application/java-archive` MIME type, fall back to `*/*` and validate extension manually
- Shows upload progress bar while copying
- On success: show toast "Plugin installed. Restart server to activate."

**Card B — Download from URL**
- Text input: "Paste direct .jar URL"
- Accepts URLs from: Spigot, Modrinth, Hangar, CurseForge, GitHub releases
- Validate URL ends in `.jar` before downloading
- Shows download progress bar (percentage)
- On success: show toast "Plugin downloaded. Restart server to activate."
- On failure: show specific error (invalid URL, network error, not a jar file)

---

### Create PluginManager.kt

```kotlin
package com.pocketcraft.server

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object PluginManager {

    fun getPluginsDir(context: Context, serverVersion: String): File {
        return File(context.filesDir, "servers/$serverVersion/plugins").also { it.mkdirs() }
    }

    fun getInstalledPlugins(context: Context, serverVersion: String): List<File> {
        return getPluginsDir(context, serverVersion)
            .listFiles { f -> f.extension == "jar" || f.name.endsWith(".jar.disabled") }
            ?.toList() ?: emptyList()
    }

    fun isEnabled(file: File): Boolean = file.extension == "jar"

    fun togglePlugin(file: File): File {
        return if (isEnabled(file)) {
            val disabled = File(file.parent, file.name + ".disabled")
            file.renameTo(disabled)
            disabled
        } else {
            val enabled = File(file.parent, file.name.removeSuffix(".disabled"))
            file.renameTo(enabled)
            enabled
        }
    }

    fun deletePlugin(file: File): Boolean = file.delete()

    suspend fun installFromUri(
        context: Context,
        uri: Uri,
        serverVersion: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val pluginsDir = getPluginsDir(context, serverVersion)
            val fileName = getFileNameFromUri(context, uri) ?: "plugin_${System.currentTimeMillis()}.jar"

            if (!fileName.endsWith(".jar")) {
                return@withContext Result.failure(Exception("File must be a .jar file"))
            }

            val destFile = File(pluginsDir, fileName)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw Exception("Could not open file")

            val totalBytes = context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: -1L

            inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var copied = 0L
                    var bytes = input.read(buffer)
                    while (bytes != -1) {
                        output.write(buffer, 0, bytes)
                        copied += bytes
                        if (totalBytes > 0) onProgress((copied * 100 / totalBytes).toInt())
                        bytes = input.read(buffer)
                    }
                }
            }

            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun installFromUrl(
        context: Context,
        url: String,
        serverVersion: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!url.trim().endsWith(".jar")) {
                return@withContext Result.failure(Exception("URL must point to a .jar file"))
            }

            val pluginsDir = getPluginsDir(context, serverVersion)
            val fileName = url.substringAfterLast("/").substringBefore("?")
            val destFile = File(pluginsDir, fileName)

            val connection = URL(url.trim()).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${connection.responseCode}")
            }

            val totalBytes = connection.contentLength

            connection.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0
                    var bytes = input.read(buffer)
                    while (bytes != -1) {
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        if (totalBytes > 0) onProgress(downloaded * 100 / totalBytes)
                        bytes = input.read(buffer)
                    }
                }
            }

            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

## Feature 2 — Player Details (Full Aternos-style)

### Overview
When the server is running and players are online, each player entry opens a full detail screen with the same level of control as Aternos. Data is obtained by sending commands to the server console and parsing the output.

---

### Player List (on existing home/dashboard screen)
Show a card when server is running:
```
Online Players (2/20)
[Head] Steve    ● Online    →
[Head] Alex     ● Online    →
```
- Player head image from `https://mc-heads.net/avatar/{username}/40`
- Tap row → opens Player Detail Screen

---

### Player Detail Screen

Full screen (not bottom sheet) with these sections:

---

#### Header
- Player head image (64px) from `https://mc-heads.net/head/{username}/64`
- Username in bold
- UUID below username in small muted text
- Online/Offline badge
- Gamemode dropdown top right (Survival / Creative / Adventure / Spectator) — changing it sends `/gamemode {mode} {player}` to console

---

#### Section: Health and Experience
- XP level bar — filled green bar showing progress to next level, label "Level X" centered on bar
- **Kill button** — sends `/kill {player}` — red outlined button
- **Heal button** — sends `/heal {player}` or `/effect give {player} instant_health 1 255` — green button
- Heart row — 10 heart icons, filled red based on current health (20 = full, 0 = empty)
- **Starve button** — sends `/effect give {player} hunger 9999 255` — orange outlined button
- **Feed button** — sends `/feed {player}` or `/effect give {player} saturation 1 255` — green button
- Drumstick row — 10 drumstick icons, filled based on food level

Get health and food data by sending `data get entity {player}` and parsing the output for `Health` and `FoodLevel` fields.

---

#### Section: Inventory
Show a grid matching Minecraft's inventory layout:
- 4 armor slots on the left (helmet, chestplate, leggings, boots)
- 27 main inventory slots (3 rows × 9)
- 9 hotbar slots at the bottom
- Offhand slot

For each slot that has an item:
- Show the Minecraft item texture using `https://mc-heads.net/item/{item_id}` or map common item IDs to drawable resources
- Show item count if > 1 as a badge
- Show item name on long press tooltip

Get inventory data by sending `data get entity {player} Inventory` and parsing the NBT output.

---

#### Section: Control
Three toggle rows matching Aternos layout:

| Setting | Command to toggle ON | Command to toggle OFF |
|---|---|---|
| Whitelisted | `whitelist add {player}` | `whitelist remove {player}` |
| Banned | `ban {player}` | `pardon {player}` |
| Operator | `op {player}` | `deop {player}` |

Each row:
- Label on left
- Red X (inactive) or Green checkmark (active) toggle on right
- Read current state by checking `ops.json`, `whitelist.json`, `banned-players.json` files in the server directory

---

#### Section: Information

**Current position** (collapsible, expanded by default)
- X, Y, Z coordinates
- Dimension (minecraft:overworld / minecraft:the_nether / minecraft:the_end)
- **Teleport button** — opens dialog asking for target coordinates or player name, sends `/tp {player} X Y Z`

**Respawn location** (collapsible)
- Shows spawn point X Y Z if set
- Shows "Not set" if no bed/respawn anchor

**Last death location** (collapsible)
- Shows last death X Y Z and dimension if available

Get position data from `data get entity {player} Pos` and `data get entity {player} Dimension`.

---

#### Section: Statistics
Four stat cards in a 2×2 grid at top:
- **Playtime** — hours and minutes
- **Player Kills** — count
- **Deaths** — count  
- **KDR** — kills/deaths ratio

Below that, four columns of detailed stats:

**Distance travelled (in blocks)**
- Total, By Elytra, Walked, Sprinted, Crouched, Under Water, On Water, Swum, Climbed, By Boat, By Horse

**Blocks broken**
- Total count + top 15 most broken blocks with item icon and count
- "Show all" button to expand full list

**Items used**
- Total count + top 15 most used items with icon and count
- "Show all" button

**Entities killed**
- Total count + top 15 entities with mob icon and count
- "Show all" button

Get stats from the player's stats file at:
```
servers/{version}/world/stats/{player-uuid}.json
```
This is a standard Minecraft stats JSON file — parse it directly, no console commands needed.

---

#### Section: Delete Player Data
Checkboxes at the bottom (unchecked by default):
- Experience points
- Inventory
- Ender Chest
- Player data file
- Statistics file
- Advancements file
- Select all

**Delete player data** button (red, right-aligned) — only enabled when at least one checkbox is checked. Shows confirmation dialog before deleting. Deletes the corresponding files from the server directory.

---

### PlayerDataManager.kt

Create this file to handle all player data operations:

```kotlin
package com.pocketcraft.server

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object PlayerDataManager {

    fun getStatsFile(context: Context, serverVersion: String, playerUuid: String): File {
        return File(context.filesDir, "servers/$serverVersion/world/stats/$playerUuid.json")
    }

    fun getPlayerDataFile(context: Context, serverVersion: String, playerUuid: String): File {
        return File(context.filesDir, "servers/$serverVersion/world/playerdata/$playerUuid.dat")
    }

    fun getAdvancementsFile(context: Context, serverVersion: String, playerUuid: String): File {
        return File(context.filesDir, "servers/$serverVersion/world/advancements/$playerUuid.json")
    }

    fun getOpsFile(context: Context, serverVersion: String): File {
        return File(context.filesDir, "servers/$serverVersion/ops.json")
    }

    fun getWhitelistFile(context: Context, serverVersion: String): File {
        return File(context.filesDir, "servers/$serverVersion/whitelist.json")
    }

    fun getBannedPlayersFile(context: Context, serverVersion: String): File {
        return File(context.filesDir, "servers/$serverVersion/banned-players.json")
    }

    suspend fun parseStats(statsFile: File): Map<String, Long> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, Long>()
        if (!statsFile.exists()) return@withContext result

        try {
            val json = JSONObject(statsFile.readText())
            val stats = json.optJSONObject("stats") ?: return@withContext result

            // Parse all stat categories
            stats.keys().forEach { category ->
                val categoryObj = stats.optJSONObject(category) ?: return@forEach
                categoryObj.keys().forEach { key ->
                    result["$category:$key"] = categoryObj.optLong(key, 0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        result
    }

    fun isOp(context: Context, serverVersion: String, playerName: String): Boolean {
        return try {
            val file = getOpsFile(context, serverVersion)
            if (!file.exists()) return false
            file.readText().contains("\"name\": \"$playerName\"", ignoreCase = true)
        } catch (e: Exception) { false }
    }

    fun isWhitelisted(context: Context, serverVersion: String, playerName: String): Boolean {
        return try {
            val file = getWhitelistFile(context, serverVersion)
            if (!file.exists()) return false
            file.readText().contains("\"name\": \"$playerName\"", ignoreCase = true)
        } catch (e: Exception) { false }
    }

    fun isBanned(context: Context, serverVersion: String, playerName: String): Boolean {
        return try {
            val file = getBannedPlayersFile(context, serverVersion)
            if (!file.exists()) return false
            file.readText().contains("\"name\": \"$playerName\"", ignoreCase = true)
        } catch (e: Exception) { false }
    }

    suspend fun deletePlayerData(
        context: Context,
        serverVersion: String,
        playerUuid: String,
        deleteExperience: Boolean,
        deleteInventory: Boolean,
        deleteEnderChest: Boolean,
        deletePlayerData: Boolean,
        deleteStats: Boolean,
        deleteAdvancements: Boolean
    ) = withContext(Dispatchers.IO) {
        // For experience and inventory, we'd need to modify the .dat file
        // For simplicity, deletePlayerData covers the full .dat file
        if (deletePlayerData || deleteExperience || deleteInventory || deleteEnderChest) {
            getPlayerDataFile(context, serverVersion, playerUuid).delete()
        }
        if (deleteStats) {
            getStatsFile(context, serverVersion, playerUuid).delete()
        }
        if (deleteAdvancements) {
            getAdvancementsFile(context, serverVersion, playerUuid).delete()
        }
    }
}
```

---

### Console Command Bridge

Both features need to send commands to the running server. Find the existing mechanism in the codebase for sending commands to the server's stdin (search for `inputStream`, `outputStream`, `Process`, or existing console send logic in `ServerHostService.kt` or similar).

Create a singleton or use the existing mechanism:

```kotlin
object ServerConsole {
    private var processOutputStream: java.io.OutputStream? = null

    fun attach(outputStream: java.io.OutputStream) {
        processOutputStream = outputStream
    }

    fun sendCommand(command: String) {
        try {
            processOutputStream?.write("$command\n".toByteArray())
            processOutputStream?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun detach() {
        processOutputStream = null
    }
}
```

If an equivalent already exists in the codebase, use it instead of creating a new one.

---

## Quality Checklist

Before finishing, verify:

- [ ] `PluginManager.kt` exists with correct package name `com.pocketcraft.server`
- [ ] Plugin manager screen has two tabs: Installed and Add Plugin
- [ ] Installed plugins list shows name, size, enable/disable toggle, delete button
- [ ] Disabled plugins are renamed to `.jar.disabled` not deleted
- [ ] Delete shows confirmation dialog before deleting
- [ ] Upload from storage works and validates `.jar` extension
- [ ] Download by URL works with progress bar
- [ ] URL validation rejects non-.jar URLs with clear error message
- [ ] Reload button sends `reload` to console and is disabled when server is stopped
- [ ] `PlayerDataManager.kt` exists with correct package name
- [ ] Player list card shows on home screen when server is running
- [ ] Player heads load from mc-heads.net
- [ ] Player detail screen has all 6 sections: Header, Health/XP, Inventory, Control, Information, Statistics
- [ ] Gamemode dropdown changes gamemode via console command
- [ ] Kill/Heal/Starve/Feed buttons send correct console commands
- [ ] Heart and drumstick rows reflect actual health/food values
- [ ] Inventory grid matches Minecraft layout (armor, main, hotbar, offhand)
- [ ] Whitelisted/Banned/Operator toggles read from actual server JSON files
- [ ] Toggles send correct console commands when changed
- [ ] Position shows X Y Z and dimension
- [ ] Teleport button sends `/tp` command
- [ ] Statistics section parses `world/stats/{uuid}.json` directly
- [ ] Stats show playtime, kills, deaths, KDR plus detailed breakdowns
- [ ] "Show all" expands full block/item/entity lists
- [ ] Delete player data section has all 6 checkboxes
- [ ] Delete button is disabled until at least one checkbox is checked
- [ ] Confirmation dialog shown before deleting
- [ ] `ServerConsole.sendCommand()` is used for all console interactions
- [ ] App builds without errors

---

## What This Does NOT Change

- Relay system (`RelayManager.kt`, `ServerHostService.kt` relay logic) — unchanged
- RAM settings and calculation — unchanged
- World import (`WorldImporter.kt`) — unchanged
- Any existing UI screens beyond adding the plugin screen and player detail screen — unchanged
- Server version selection — unchanged
- Theme and colors — unchanged
