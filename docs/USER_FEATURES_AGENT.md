# USER_FEATURES_AGENT

## Overview
Implements user-requested features and fixes RAM allocation bug. Work through each section independently.

---

## Feature 1 — Delete Current World (with checkbox selection)

### Goal
Allow the user to reset their world while preserving selected data (plugins, mods, datapacks, configs, player data).

### UI
Add a "Reset World" button in the Storage screen or Settings screen. On tap, show a bottom sheet dialog with:

```
⚠️ Reset World

Choose what to DELETE:
☑ World folders (world, world_nether, world_the_end)
☐ Player data (playerdata, stats, advancements)
☐ Datapacks
☐ Logs

The following are always preserved:
• plugins/ (Paper) or mods/ (Fabric)
• server.properties
• All .yml config files
```

Confirm button is RED and labelled "Delete Selected". Require server to be stopped before allowing this action — show a snackbar error if server is running.

### Implementation in `StorageScreen.kt` or `SettingsScreen.kt`

```kotlin
fun resetWorld(
    serverDir: File,
    deletePlayerData: Boolean,
    deleteDatapacks: Boolean,
    deleteLogs: Boolean
) {
    require(!serverManager.isRunning()) { "Stop the server before resetting the world" }

    // Always delete world dimensions
    listOf("world", "world_nether", "world_the_end").forEach { name ->
        File(serverDir, name).deleteRecursively()
    }

    if (deletePlayerData) {
        File(serverDir, "world/playerdata").deleteRecursively()
        File(serverDir, "world/stats").deleteRecursively()
        File(serverDir, "world/advancements").deleteRecursively()
    }

    if (deleteDatapacks) {
        File(serverDir, "world/datapacks").deleteRecursively()
    }

    if (deleteLogs) {
        File(serverDir, "logs").deleteRecursively()
    }

    // Never touch these — always preserved:
    // plugins/, mods/, server.properties, *.yml, *.json config files
}
```

### Always preserve (hardcoded, never deletable)
- `plugins/`
- `mods/`
- `config/`
- `server.properties`
- All `.yml` files in root server dir
- `ops.json`, `whitelist.json`, `banned-players.json`, `banned-ips.json`

---

## Feature 2 — Fix RAM Allocation (3GB Hard Cap Bug)

### Symptom
Log shows:
```
[PocketCraft] RAM profile: mode=full, heap=1536MB..3072MB, total=7221MB
argv[1] = -Xmx3072m
argv[2] = -Xms1536m
```
Device has 7221MB total. On "full" mode at 90%, `-Xmx` should be `~6498m`. Instead it's capped at `3072m`.

### Root Cause
In `ServerLauncher.kt`, there is almost certainly a hardcoded cap like:
```kotlin
val maxHeap = min(calculatedHeap, 3072) // BUG: this cap must be removed
```
or the Xms/Xmx values are stored as hardcoded constants rather than computed from device RAM.

### Fix in `ServerLauncher.kt`

```kotlin
fun buildRamArgs(mode: String, context: Context): Pair<String, String> {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)

    // Use totalMem not availMem — we want total device RAM
    val totalMb = (memInfo.totalMem / 1024 / 1024).toInt()

    return when (mode) {
        "low" -> Pair("-Xms256m", "-Xmx512m")
        "full" -> {
            val maxMb = (totalMb * 0.80).toInt()  // 80% of total, leave headroom for OS
            val minMb = (maxMb * 0.5).toInt()      // Xms = 50% of Xmx
            Pair("-Xms${minMb}m", "-Xmx${maxMb}m")
        }
        "manual" -> {
            val manualMb = AppPreferences.manualRamMb
            val minMb = (manualMb * 0.5).toInt()
            Pair("-Xms${minMb}m", "-Xmx${manualMb}m")
        }
        else -> Pair("-Xms256m", "-Xmx512m")
    }
}
```

**Why 80% not 90%:** Android 16 isolated bootstrap needs more OS headroom than previous versions. 90% on 7GB causes the OS to kill the process. 80% (~5700MB on this device) is safe.

**Remove any hardcoded cap** like `min(x, 3072)` — search for `3072` in `ServerLauncher.kt` and `launcher.c` and remove it.

---

## Feature 3 — Settings Persistence Fix

### Symptom
Difficulty, auto-restart, max players, entity settings, whitelist, view distance all reset on app restart.

### Root Cause
These settings are written to `AppPreferences` (SharedPreferences) but when the server starts, `server.properties` is either regenerated from defaults or overwritten before the saved values are applied.

### Fix — Two parts:

**Part A: Ensure AppPreferences is a true singleton initialized in Application class**

In your `Application` subclass:
```kotlin
class PocketCraftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPreferences.init(this) // Must be here, not in Activity or Service
    }
}
```

**Part B: Apply saved preferences to server.properties on every server start, AFTER the file is written but BEFORE the JVM launches**

In `ServerLauncher.kt`, add a `applyPreferencesToServerProperties()` call:

```kotlin
fun applyPreferencesToServerProperties(serverDir: File) {
    val propsFile = File(serverDir, "server.properties")
    if (!propsFile.exists()) return

    val props = Properties()
    propsFile.inputStream().use { props.load(it) }

    // Apply all saved settings
    props.setProperty("difficulty", AppPreferences.difficulty)
    props.setProperty("max-players", AppPreferences.maxPlayers.toString())
    props.setProperty("view-distance", AppPreferences.viewDistance.toString())
    props.setProperty("simulation-distance", AppPreferences.simulationDistance.toString())
    props.setProperty("white-list", AppPreferences.whitelistEnabled.toString())
    props.setProperty("spawn-animals", AppPreferences.spawnAnimals.toString())
    props.setProperty("spawn-monsters", AppPreferences.spawnMonsters.toString())
    props.setProperty("spawn-npcs", AppPreferences.spawnNpcs.toString())
    props.setProperty("max-world-size", AppPreferences.maxWorldSize.toString())
    props.setProperty("entity-broadcast-range-percentage", AppPreferences.entityRange.toString())
    // Add any other settings that were resetting

    propsFile.outputStream().use {
        props.store(it, "Managed by PocketCraft")
    }
}
```

Call this immediately before `JLI_Launch` / server start in `ServerLauncher.kt`.

**Part C: Persist auto-restart separately** — this is an app-level setting, not a server.properties key. Ensure it is saved in AppPreferences and read back in `ServerHostService.onCreate()`:

```kotlin
var autoRestart: Boolean
    get() = prefs.getBoolean("auto_restart", false)
    set(value) = prefs.edit().putBoolean("auto_restart", value).apply()
```

---

## Feature 4 — server.properties Config File Editor

### Goal
Let users view and edit `server.properties` directly from the app with a simple key-value UI.

### UI — Add "Edit Config" button in Settings screen

On tap, open a new screen `ConfigEditorScreen.kt` that:
1. Reads `server.properties` from the active server directory
2. Displays each key-value pair as an editable row
3. Groups keys into sections: General, World, Performance, Players, Network
4. Shows a Save button that writes changes back to the file
5. Warns user that changes take effect on next server restart

```kotlin
@Composable
fun ConfigEditorScreen(serverDir: File) {
    val props = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(Unit) {
        val p = Properties()
        File(serverDir, "server.properties").inputStream().use { p.load(it) }
        p.forEach { k, v -> props[k.toString()] = v.toString() }
    }

    // Render each entry as a TextField row
    // On save:
    LazyColumn {
        items(props.entries.toList()) { (key, value) ->
            ConfigRow(
                key = key,
                value = value,
                onValueChange = { props[key] = it }
            )
        }
    }

    Button(onClick = {
        val p = Properties()
        props.forEach { (k, v) -> p.setProperty(k, v) }
        File(serverDir, "server.properties").outputStream().use {
            p.store(it, "Edited via PocketCraft")
        }
    }) {
        Text("Save — Restart server to apply")
    }
}
```

**Only allow editing when server is stopped.** If server is running, show the file read-only with a banner: "Stop the server to edit config."

---

## Feature 5 — Fix Share Feature (Share File, Not Path Text)

### Symptom
Share button sends the file path as plain text instead of sharing the actual file.

### Fix — wherever the share intent is built (search for `ACTION_SEND` or `shareFile`):

```kotlin
fun shareFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = getMimeType(file)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
}

fun getMimeType(file: File): String = when (file.extension.lowercase()) {
    "zip" -> "application/zip"
    "jar" -> "application/java-archive"
    "json" -> "application/json"
    "yml", "yaml" -> "text/plain"
    "properties" -> "text/plain"
    "log" -> "text/plain"
    else -> "application/octet-stream"
}
```

**Also ensure `fileprovider` is declared in `AndroidManifest.xml`:**
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

**And `res/xml/file_paths.xml`:**
```xml
<paths>
    <files-path name="server_files" path="servers/" />
    <files-path name="backups" path="backups/" />
    <external-files-path name="external" path="." />
</paths>
```

---

## Feature 6 — Server Icon Crop (Square + Auto-resize to 64x64)

### Goal
When user picks a server icon image, show a square crop UI, then save the result as exactly 64x64 PNG to `server-icon.png` in the server folder.

### Implementation

Use the `uCrop` library for cropping:

**In `build.gradle.kts`:**
```kotlin
implementation("com.github.yalantis:ucrop:2.2.8")
```

**Launch crop after image pick:**
```kotlin
fun launchIconCrop(context: Context, sourceUri: Uri, serverDir: File) {
    val destUri = Uri.fromFile(File(context.cacheDir, "server-icon-crop.png"))

    UCrop.of(sourceUri, destUri)
        .withAspectRatio(1f, 1f)           // Force square
        .withMaxResultSize(64, 64)          // Output exactly 64x64
        .withOptions(UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.PNG)
            setCompressionQuality(100)
            setHideBottomControls(false)
            setFreeStyleCropEnabled(false)  // Lock to square
            setToolbarTitle("Crop Server Icon")
        })
        .start(context as Activity)
}
```

**In `onActivityResult` / result launcher:**
```kotlin
val croppedUri = UCrop.getOutput(resultData!!)
croppedUri?.let { uri ->
    val bitmap = BitmapFactory.decodeStream(
        context.contentResolver.openInputStream(uri)
    )
    // Scale to exactly 64x64 as final safety
    val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
    val iconFile = File(serverDir, "server-icon.png")
    FileOutputStream(iconFile).use { out ->
        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
}
```

---

## Feature 7 — Auto-detect Phone Language for Server Description

### Goal
The server MOTD (description shown in server list) should be in the user's phone language automatically.

### Implementation in `ServerLauncher.kt` or wherever `server.properties` is written:

```kotlin
fun getLocalizedMotd(): String {
    val locale = Locale.getDefault().language  // "en", "ru", "de", "hi", "ar" etc.

    return when (locale) {
        "ru" -> "Сервер Minecraft · Работает на PocketCraft"
        "hi" -> "Minecraft सर्वर · PocketCraft द्वारा संचालित"
        "ar" -> "خادم Minecraft · يعمل بواسطة PocketCraft"
        "de" -> "Minecraft Server · Betrieben von PocketCraft"
        "fr" -> "Serveur Minecraft · Propulsé par PocketCraft"
        "es" -> "Servidor Minecraft · Desarrollado por PocketCraft"
        "pt" -> "Servidor Minecraft · Desenvolvido pela PocketCraft"
        "tr" -> "Minecraft Sunucusu · PocketCraft ile çalışıyor"
        "id" -> "Server Minecraft · Didukung oleh PocketCraft"
        "ja" -> "Minecraftサーバー · PocketCraft製"
        "zh" -> "Minecraft服务器 · 由PocketCraft提供支持"
        else -> "Minecraft Server · Hosted on PocketCraft"  // default English
    }
}
```

Apply it when writing `server.properties`:
```kotlin
props.setProperty("motd", getLocalizedMotd())
```

**Note:** Only set this on first-time server creation. If user has manually edited the MOTD in config editor (Feature 4), do not overwrite it. Gate with:
```kotlin
if (AppPreferences.isFirstBoot) {
    props.setProperty("motd", getLocalizedMotd())
}
```

---

## Feature 8 — Show 'Mods' Tab Instead of 'Plugins' on Fabric Servers

### Goal
Detect server type at runtime and swap the Plugins tab to a Mods tab with appropriate behavior.

### Detection in `AppPreferences.kt` or `ServerStateHolder.kt`:

```kotlin
enum class ServerType { PAPER, FABRIC, PURPUR, UNKNOWN }

fun detectServerType(serverDir: File): ServerType {
    // Check the JAR name used at launch
    val jarName = AppPreferences.selectedJarName  // e.g. "fabric-1.21.11.jar"
    return when {
        jarName.contains("fabric", ignoreCase = true) -> ServerType.FABRIC
        jarName.contains("purpur", ignoreCase = true) -> ServerType.PURPUR
        jarName.contains("paper", ignoreCase = true) -> ServerType.PAPER
        // Fallback: check for mods/ folder existence
        File(serverDir, "mods").exists() -> ServerType.FABRIC
        else -> ServerType.PAPER
    }
}
```

### UI change in bottom navigation / tab bar:

```kotlin
val serverType by serverStateHolder.serverType.collectAsState()

// In your nav items list:
val pluginsNavItem = if (serverType == ServerType.FABRIC) {
    NavItem(
        label = "Mods",
        icon = Icons.Default.Extension,
        route = "mods"
    )
} else {
    NavItem(
        label = "Plugins",
        icon = Icons.Default.Extension,
        route = "plugins"
    )
}
```

### Behavior difference on Mods screen vs Plugins screen:
- **Plugins (Paper):** URL install + upload `.jar` → `plugins/` folder
- **Mods (Fabric):** Upload `.jar` only (no URL install for mods) → `mods/` folder
- Hide "Install from URL" option on Fabric since Modrinth URLs require specific handling
- Show a note: "Fabric mods require compatible Fabric API version"

---

## Acceptance Criteria
- [ ] "Reset World" shows checkbox dialog, never deletes plugins/mods/configs
- [ ] RAM on "full" mode uses 80% of actual device RAM, no 3072MB cap
- [ ] All settings persist across app restarts (difficulty, whitelist, entity settings, auto-restart)
- [ ] Config editor opens server.properties, edits save correctly, read-only when server running
- [ ] Share button shares the actual file, not path text
- [ ] Server icon picker shows square crop UI, saves as 64x64 PNG
- [ ] MOTD auto-set to phone language on first server creation only
- [ ] Fabric servers show "Mods" tab; Paper servers show "Plugins" tab
