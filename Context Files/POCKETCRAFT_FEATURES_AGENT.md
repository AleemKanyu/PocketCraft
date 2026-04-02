# PocketCraft Feature Agent — RAM Settings, RAM Usage Display, World Upload

## Your Role
You are a feature implementation agent. Your job is to add three features to the PocketCraft Android app. Read the existing code carefully before making any changes. Do not ask questions — figure out the structure from the code and implement each feature completely.

**Package name:** `com.pocketcraft.server`
**UI toolkit:** Jetpack Compose
**Home screen:** Activity
**RAM launch args location:** `ServerLauncher.kt` (Kotlin side, ~line 153) and `launcher.c` (native side, ~line 343)

---

## Feature 1 — RAM Usage Options (3 Presets + Manual Slider)

### What to build
A settings screen or dialog (accessible before starting the server) that lets the user choose how much RAM to allocate to the Minecraft server. There are 3 modes:

- **Low** — uses 512MB (`-Xms256m -Xmx512m`). Safe for low-end devices.
- **Manual** — shows a slider letting the user pick any value between 512MB and the device's total RAM in increments of 256MB. Sets `-Xms` to half the chosen value and `-Xmx` to the full chosen value.
- **Full** — uses 90% of total device RAM. Sets `-Xms` to 50% of total RAM and `-Xmx` to 90% of total RAM. Never exceed 90% to avoid OOM kills.

### How to detect total device RAM

Add this utility function wherever appropriate:

```kotlin
fun getTotalRamMb(context: Context): Int {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)
    return (memInfo.totalMem / 1024 / 1024).toInt()
}
```

### How to store the selection

Add these fields to `AppPreferences.kt`:

```kotlin
var ramMode: String
    get() = prefs.getString("ram_mode", "low") ?: "low"  // "low", "manual", "full"
    set(value) = prefs.edit().putString("ram_mode", value).apply()

var manualRamMb: Int
    get() = prefs.getInt("manual_ram_mb", 1024)
    set(value) = prefs.edit().putInt("manual_ram_mb", value).apply()
```

### How to apply it in ServerLauncher.kt

Find the section around line 153 where `maxRamMb` is calculated. Replace that calculation with:

```kotlin
val totalRam = getTotalRamMb(context)
val prefs = AppPreferences(context)

val (minRamMb, maxRamMb) = when (prefs.ramMode) {
    "full" -> {
        val max = (totalRam * 0.9).toInt()
        val min = (totalRam * 0.5).toInt()
        Pair(min, max)
    }
    "manual" -> {
        val max = prefs.manualRamMb.coerceIn(512, totalRam)
        val min = (max * 0.5).toInt()
        Pair(min, max)
    }
    else -> Pair(256, 512) // "low" default
}
```

Make sure both `minRamMb` and `maxRamMb` are passed into the JVM args list as `-Xms${minRamMb}m` and `-Xmx${maxRamMb}m` respectively. Check `launcher.c` around line 343 — if it receives separate min and max values from Kotlin via JNI, update both. If it only receives one value, pass `maxRamMb` for the max and add a new parameter for min, updating the JNI bridge accordingly.

### UI — Compose settings card

Add a RAM settings card to the home screen Activity (before the server start button). It should show:

```
RAM Allocation
[ Low ]  [ Manual ]  [ Full ]

// If Manual is selected, show:
Slider: 512MB ←————●————→ [total RAM]MB
Selected: 2048 MB
```

Use a `SegmentedButton` style row or three `FilterChip` composables for the mode selector. Use a `Slider` composable for the manual mode. Save changes to `AppPreferences` immediately on interaction. Disable the controls while the server is running.

---

## Feature 2 — Live RAM Usage on Home Screen

### What to build
After the server starts, show a live RAM usage indicator on the home screen that updates every 2 seconds. It should show:

```
RAM: 743 MB / 1024 MB  [=========>  ] 72%
```

### How to get RAM usage

Use `ActivityManager.MemoryInfo` to get available RAM, then derive used RAM:

```kotlin
fun getUsedRamMb(context: Context): Int {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)
    val totalMb = (memInfo.totalMem / 1024 / 1024).toInt()
    val availMb = (memInfo.availMem / 1024 / 1024).toInt()
    return totalMb - availMb
}
```

### How to display it

In the home screen Activity, add a `LaunchedEffect` or `ViewModel` that polls RAM every 2 seconds while the server is running:

```kotlin
var usedRamMb by remember { mutableStateOf(0) }
var maxRamMb by remember { mutableStateOf(512) }

LaunchedEffect(serverRunning) {
    if (serverRunning) {
        while (true) {
            usedRamMb = getUsedRamMb(context)
            maxRamMb = getTotalRamMb(context)
            delay(2000)
        }
    }
}
```

Show this as a Compose card below the server status indicator:

```kotlin
if (serverRunning) {
    RamUsageCard(usedMb = usedRamMb, maxMb = maxRamMb)
}
```

Implement `RamUsageCard` as a composable showing:
- Text: `"RAM: ${usedRamMb} MB / ${maxRamMb} MB"`
- A `LinearProgressIndicator` showing `usedRamMb.toFloat() / maxRamMb`
- Color the indicator green below 70%, yellow between 70-85%, red above 85%

---

## Feature 3 — Upload Custom World

### What to build
A button on the home screen (visible only when the server is NOT running) labeled **"Upload World"**. When tapped, it opens the device file picker to select a `.zip` file. The zip is then extracted into the server's world directory, replacing the existing world.

### World directory path

Find where the server files are stored. Based on the existing code it will be something like:

```
/data/user/0/com.pocketcraft.server/files/servers/1.20.4/world/
```

Search the codebase for `files/servers` or `world` directory references to find the exact path. Store it as a constant or derive it from `context.filesDir`.

### Implementation

**Step 1 — Add to AppPreferences.kt**

```kotlin
var selectedWorldPath: String?
    get() = prefs.getString("selected_world_path", null)
    set(value) = prefs.edit().putString("selected_world_path", value).apply()
```

**Step 2 — Create WorldImporter.kt**

Create a new file `WorldImporter.kt` in the correct package:

```kotlin
package com.pocketcraft.server

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object WorldImporter {

    suspend fun importWorld(context: Context, zipUri: Uri, serverVersion: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val worldDir = File(context.filesDir, "servers/$serverVersion/world")

                // Delete existing world
                if (worldDir.exists()) worldDir.deleteRecursively()
                worldDir.mkdirs()

                // Extract zip
                context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val entryFile = File(worldDir, entry.name)

                            // Prevent zip slip attack
                            if (!entryFile.canonicalPath.startsWith(worldDir.canonicalPath)) {
                                throw SecurityException("Zip slip detected: ${entry.name}")
                            }

                            if (entry.isDirectory) {
                                entryFile.mkdirs()
                            } else {
                                entryFile.parentFile?.mkdirs()
                                FileOutputStream(entryFile).use { output ->
                                    zip.copyTo(output)
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                } ?: throw Exception("Could not open zip file")

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
```

**Step 3 — UI in home screen Activity**

Add a file picker launcher and Upload World button:

```kotlin
// File picker launcher — add at the top of the composable or Activity
val worldPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
) { uri ->
    uri?.let {
        scope.launch {
            isImportingWorld = true
            val result = WorldImporter.importWorld(context, it, "1.20.4")
            isImportingWorld = false
            if (result.isSuccess) {
                // Show success toast or snackbar
                Toast.makeText(context, "World imported successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Import failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

// State
var isImportingWorld by remember { mutableStateOf(false) }

// Button — show only when server is NOT running
if (!serverRunning) {
    if (isImportingWorld) {
        CircularProgressIndicator()
        Text("Importing world...")
    } else {
        OutlinedButton(onClick = { worldPickerLauncher.launch("application/zip") }) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Upload World")
        }
    }
}
```

**Step 4 — Handle world folder structure inside zip**

Some world zips have the world folder nested inside (e.g. `my-world/world/region/...`). After extraction, check if the world folder contains only one subdirectory and no `region` folder at the top level. If so, move the contents of that subdirectory up one level:

```kotlin
// After extraction, fix nested structure
val regionDir = File(worldDir, "region")
if (!regionDir.exists()) {
    val subDirs = worldDir.listFiles()?.filter { it.isDirectory }
    if (subDirs?.size == 1) {
        val nested = subDirs[0]
        nested.listFiles()?.forEach { file ->
            file.renameTo(File(worldDir, file.name))
        }
        nested.delete()
    }
}
```

Add this check inside `WorldImporter.importWorld()` after the zip extraction block.

---

## Quality Checklist

Before finishing, verify:

- [ ] `AppPreferences.kt` has `ramMode`, `manualRamMb`, and `selectedWorldPath` fields
- [ ] `getTotalRamMb()` and `getUsedRamMb()` utility functions exist and are accessible
- [ ] `ServerLauncher.kt` uses `ramMode` from prefs to calculate `-Xms` and `-Xmx` values
- [ ] `launcher.c` receives and applies both min and max RAM values correctly via JNI
- [ ] RAM settings card appears on home screen with 3 mode options
- [ ] Manual mode shows a slider bounded by 512MB and total device RAM
- [ ] RAM settings controls are disabled while server is running
- [ ] RAM usage card appears on home screen only while server is running
- [ ] RAM usage updates every 2 seconds
- [ ] Progress bar color changes based on usage percentage (green/yellow/red)
- [ ] `WorldImporter.kt` exists with correct package name
- [ ] Upload World button appears only when server is not running
- [ ] File picker filters for `.zip` files only
- [ ] Existing world is deleted before importing new one
- [ ] Zip slip security check is in place
- [ ] Nested world folder structure is handled automatically
- [ ] Success and error feedback is shown to the user after import
- [ ] App builds without errors

---

## What This Does NOT Change

- Relay system (`RelayManager.kt`, `ServerHostService.kt`) — unchanged
- Server version selection — unchanged
- Any existing navigation or routing — unchanged
- `launcher.c` beyond the RAM parameter handling — unchanged
