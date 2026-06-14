# SERVER_TYPE_AGENT.md
# PocketCraft — Server Type & Version Selection Feature

## Overview

Extend the existing game version popup (shown when a user picks a version) to also let users choose a **server type** before confirming. Supported types: **Paper**, **Purpur**, **Fabric**, and **Custom JAR** (file upload, including modpacks). The selected type is persisted in server metadata and used at launch time to either download the correct JAR from an API or load the user-supplied JAR.

---

## 1. Data Model Changes

### 1.1 New Enum — `ServerType.kt`

Create `com.pocketcraft.server.model.ServerType.kt`:

```kotlin
enum class ServerType(val displayName: String, val supportsVersionSelect: Boolean) {
    PAPER("Paper", true),
    PURPUR("Purpur", true),
    FABRIC("Fabric", true),
    CUSTOM_JAR("Custom JAR", false)   // version irrelevant; user supplies the JAR
}
```

### 1.2 Update `ServerConfig` / metadata model

Add two new nullable fields to whichever data class holds server metadata (e.g. `ServerConfig.kt` or equivalent):

```kotlin
var serverType: ServerType = ServerType.PAPER   // default
var customJarPath: String? = null               // non-null only when type == CUSTOM_JAR
```

Persist both fields to SharedPreferences / JSON alongside existing metadata (version string, RAM allocation, etc.).

---

## 2. UI — Version + Server Type Popup

The existing version-picker dialog (wherever `showVersionPickerDialog()` or equivalent is called) must be replaced with a two-step bottom sheet that is **one cohesive dialog**, not two separate ones.

### 2.1 Layout: `dialog_version_server_type.xml`

```
┌─────────────────────────────────────┐
│  Create Server                      │  ← title
│                                     │
│  SERVER TYPE                        │  ← section label
│  ┌──────┐ ┌───────┐ ┌──────┐ ┌───┐ │
│  │Paper │ │Purpur │ │Fabric│ │JAR│ │  ← chip/card row
│  └──────┘ └───────┘ └──────┘ └───┘ │
│                                     │
│  GAME VERSION          (hidden for  │
│  ┌──────────────────────────────┐   │  CUSTOM_JAR)
│  │  1.20.4  ▼                   │   │  ← spinner / RecyclerView list
│  └──────────────────────────────┘   │
│                                     │
│  CUSTOM JAR PATH       (shown only  │
│  ┌──────────────────────────────┐   │  for CUSTOM_JAR)
│  │  tap to pick file …          │   │
│  └──────────────────────────────┘   │
│                                     │
│            [ CONFIRM ]              │
└─────────────────────────────────────┘
```

Key behaviours:
- **Server type row**: four toggle chips (MaterialChip / custom card). Selecting a chip immediately shows/hides the sections below with a smooth `animateLayoutChanges` transition.
- **Game Version section**: visible for PAPER, PURPUR, FABRIC. Hidden (gone) for CUSTOM_JAR.
- **Custom JAR section**: visible only for CUSTOM_JAR. Contains a `TextView` acting as a file path display + a "Browse" button that launches `ActivityResultContracts.GetContent()` filtered to `application/*`.
- **CONFIRM** button is disabled until: a type is selected AND (version chosen OR jar path set).

### 2.2 Chip / Card styling

Match the existing PocketCraft dark theme:
- Background: `#0A0A0F`
- Selected chip: `#6C63FF` fill, white text
- Unselected chip: `#1A1A2E` fill, `#888` text
- Corner radius: 12dp
- Each chip shows: icon (Paper 📄, Purpur 💜, Fabric 🧵, JAR 📦) + label

### 2.3 Version list

Reuse the existing version fetch logic. Populate the same list you already show — just embed it inside this new combined dialog rather than a standalone one.

---

## 3. Version + JAR Download Logic

### 3.1 `ServerJarManager.kt` (new file)

Responsible for resolving the correct JAR path, downloading if needed, and returning a `File` ready for launch.

```kotlin
object ServerJarManager {

    /**
     * Returns the local File for the server JAR, downloading it if absent.
     * Calls [onProgress] with 0..100 during download.
     * Throws IOException on network failure.
     */
    suspend fun resolveJar(
        serverType: ServerType,
        gameVersion: String?,         // null for CUSTOM_JAR
        customJarPath: String?,       // non-null for CUSTOM_JAR
        serverDir: File,
        onProgress: (Int) -> Unit
    ): File {
        return when (serverType) {
            ServerType.PAPER       -> downloadPaper(gameVersion!!, serverDir, onProgress)
            ServerType.PURPUR      -> downloadPurpur(gameVersion!!, serverDir, onProgress)
            ServerType.FABRIC      -> downloadFabric(gameVersion!!, serverDir, onProgress)
            ServerType.CUSTOM_JAR  -> File(customJarPath!!)   // already on device
        }
    }

    // --- Paper ---
    private suspend fun downloadPaper(version: String, dir: File, onProgress: (Int) -> Unit): File {
        // 1. GET https://api.papermc.io/v2/projects/paper/versions/{version}
        //    → extract latest build number from response["builds"].last()
        // 2. GET https://api.papermc.io/v2/projects/paper/versions/{version}/builds/{build}/downloads/paper-{version}-{build}.jar
        //    → stream to dir/paper-{version}.jar
        val dest = File(dir, "paper-$version.jar")
        if (dest.exists()) return dest
        val latestBuild = fetchLatestPaperBuild(version)
        val url = "https://api.papermc.io/v2/projects/paper/versions/$version/builds/$latestBuild/downloads/paper-$version-$latestBuild.jar"
        streamDownload(url, dest, onProgress)
        return dest
    }

    // --- Purpur ---
    private suspend fun downloadPurpur(version: String, dir: File, onProgress: (Int) -> Unit): File {
        // GET https://api.purpurmc.org/v2/purpur/{version}/latest/download
        val dest = File(dir, "purpur-$version.jar")
        if (dest.exists()) return dest
        val url = "https://api.purpurmc.org/v2/purpur/$version/latest/download"
        streamDownload(url, dest, onProgress)
        return dest
    }

    // --- Fabric ---
    private suspend fun downloadFabric(version: String, dir: File, onProgress: (Int) -> Unit): File {
        // 1. GET https://meta.fabricmc.net/v2/versions/loader/{version}
        //    → extract loaderVersion from response[0]["loader"]["version"]
        // 2. GET https://meta.fabricmc.net/v2/versions/installer
        //    → extract latest installerVersion from response[0]["version"]
        // 3. Download server jar:
        //    GET https://meta.fabricmc.net/v2/versions/loader/{version}/{loaderVersion}/{installerVersion}/server/jar
        val dest = File(dir, "fabric-$version.jar")
        if (dest.exists()) return dest
        val (loaderVer, installerVer) = fetchFabricVersions(version)
        val url = "https://meta.fabricmc.net/v2/versions/loader/$version/$loaderVer/$installerVer/server/jar"
        streamDownload(url, dest, onProgress)
        return dest
    }

    // --- Helpers ---

    private suspend fun fetchLatestPaperBuild(version: String): Int {
        // HTTP GET → parse JSON builds array → return last element
        TODO("implement with OkHttp or HttpURLConnection + kotlinx.serialization")
    }

    private suspend fun fetchFabricVersions(version: String): Pair<String, String> {
        // Fetch loader and installer versions from FabricMC meta API
        TODO("implement")
    }

    private suspend fun streamDownload(url: String, dest: File, onProgress: (Int) -> Unit) {
        // Stream bytes from url → dest, reporting progress via onProgress(percent)
        // Use OkHttp or HttpURLConnection. Update onProgress every ~512KB chunk.
        TODO("implement")
    }
}
```

### 3.2 Version list fetch per server type

Add a method to fetch the **available game versions** for each type so the version dropdown is correct:

```kotlin
suspend fun fetchAvailableVersions(type: ServerType): List<String> {
    return when (type) {
        ServerType.PAPER      -> fetchFromUrl("https://api.papermc.io/v2/projects/paper") { /* parse versions array */ }
        ServerType.PURPUR     -> fetchFromUrl("https://api.purpurmc.org/v2/purpur")        { /* parse versions array */ }
        ServerType.FABRIC     -> fetchFromUrl("https://meta.fabricmc.net/v2/versions/game"){ /* parse stable versions */ }
        ServerType.CUSTOM_JAR -> emptyList()   // no version list needed
    }
}
```

Call this in the dialog's `ViewModel` whenever the selected chip changes, and repopulate the version spinner/list with the result.

---

## 4. Launch Integration

### 4.1 `ServerHostService.kt` / `VMLauncher` — update launch flow

Before building the ProcessBuilder command, call `ServerJarManager.resolveJar(...)` to get the `File`:

```kotlin
// Inside the coroutine that starts the server
val jarFile = ServerJarManager.resolveJar(
    serverType    = config.serverType,
    gameVersion   = config.gameVersion,   // e.g. "1.20.4"
    customJarPath = config.customJarPath,
    serverDir     = serverDirectory,
    onProgress    = { pct -> updateNotificationProgress(pct) }
)

// Then pass jarFile.absolutePath into VMLauncher as before
val command = buildLaunchCommand(jarFile.absolutePath, config.ramMb)
```

The existing JVM flags remain unchanged:
```
-XX:+UseG1GC -XX:+UseCompactObjectHeaders -Xms512M -Xmx{ram}M
-Duser.dir={serverDir} -jar {jarPath} nogui
```

### 4.2 First-run EULA acceptance

After downloading any JAR for the first time, write `eula=true` to `{serverDir}/eula.txt` before launch (required for all server types).

### 4.3 Modpack / Custom JAR specifics

- No version API calls are made.
- No JAR is downloaded.
- The file at `customJarPath` is used directly — it may be a Forge/NeoForge/Fabric modpack server JAR.
- Mods folder contents are the user's responsibility (they manage via the existing file manager / PluginManager UI).
- Display a one-time toast: *"Custom JAR selected. Ensure all required mods are in the mods/ folder."*

---

## 5. UI State & ViewModel

### 5.1 `ServerTypeVersionViewModel.kt` (new)

```kotlin
class ServerTypeVersionViewModel : ViewModel() {
    val selectedType = MutableStateFlow<ServerType>(ServerType.PAPER)
    val availableVersions = MutableStateFlow<List<String>>(emptyList())
    val selectedVersion = MutableStateFlow<String?>(null)
    val customJarPath = MutableStateFlow<String?>(null)
    val isDownloading = MutableStateFlow(false)
    val downloadProgress = MutableStateFlow(0)

    val isConfirmEnabled: StateFlow<Boolean> = combine(
        selectedType, selectedVersion, customJarPath
    ) { type, version, jarPath ->
        when (type) {
            ServerType.CUSTOM_JAR -> jarPath != null
            else -> version != null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onTypeSelected(type: ServerType) {
        selectedType.value = type
        selectedVersion.value = null
        viewModelScope.launch {
            availableVersions.value = ServerJarManager.fetchAvailableVersions(type)
            selectedVersion.value = availableVersions.value.firstOrNull()
        }
    }

    fun onVersionSelected(version: String) { selectedVersion.value = version }
    fun onCustomJarPicked(path: String) { customJarPath.value = path }
}
```

---

## 6. Integration Points — Where to Wire This

| Location | Change |
|---|---|
| Version picker dialog call site | Replace with new `ServerTypeVersionBottomSheet` |
| `ServerConfig` / metadata model | Add `serverType`, `customJarPath` fields + serialization |
| `ServerHostService` / launch path | Call `ServerJarManager.resolveJar()` before building command |
| `AppPreferences.kt` | Add getters/setters for `serverType` and `customJarPath` per server ID |
| Home screen server card | Show server type badge (e.g. "Fabric 1.20.4", "Custom JAR") |

---

## 7. Error Handling

| Scenario | Behaviour |
|---|---|
| Network unavailable during download | Show error snackbar: *"Download failed. Check your connection."* Cancel launch. |
| JAR download 404 (version not found) | Show: *"Version not available for {type}. Choose another."* |
| Custom JAR file deleted/moved | Show: *"JAR file missing. Please re-select."* Block launch. |
| Fabric loader fetch fails | Fall back to latest known stable loader or show error |

---

## 8. Files to Create / Modify

**Create:**
- `model/ServerType.kt`
- `manager/ServerJarManager.kt`
- `ui/dialog/ServerTypeVersionBottomSheet.kt`
- `ui/dialog/ServerTypeVersionViewModel.kt`
- `res/layout/dialog_version_server_type.xml`
- `res/layout/item_server_type_chip.xml`

**Modify:**
- `model/ServerConfig.kt` — add `serverType`, `customJarPath`
- `AppPreferences.kt` — persist new fields
- `ServerHostService.kt` — call `ServerJarManager.resolveJar()`
- Wherever the old version picker dialog was shown — replace with `ServerTypeVersionBottomSheet`
- Home screen server card — add type+version badge

---

## 9. API Reference

| Server Type | Version List API | JAR Download |
|---|---|---|
| Paper | `GET https://api.papermc.io/v2/projects/paper` → `versions[]` | `GET /v2/projects/paper/versions/{v}/builds/{b}/downloads/paper-{v}-{b}.jar` |
| Purpur | `GET https://api.purpurmc.org/v2/purpur` → `versions[]` | `GET https://api.purpurmc.org/v2/purpur/{v}/latest/download` |
| Fabric | `GET https://meta.fabricmc.net/v2/versions/game` → stable entries | `GET https://meta.fabricmc.net/v2/versions/loader/{v}/{loader}/{installer}/server/jar` |
| Custom JAR | N/A | N/A — user supplies path |
