# PocketCraft – Multi-Version Feature Prompt
> Antigravity-ready prompt for adding Minecraft version selection to PocketCraft

---

## 🧾 Context

I have an Android app called **PocketCraft** built in Kotlin that hosts a Minecraft Java server on-device.  
Currently it only supports **Minecraft 1.20.4**.  
I want to add a full version selection feature so users can choose **any Minecraft Java release** before starting the server.

---

## ✅ Feature Requirements

### 1. Version List
- Fetch all available Minecraft Java releases from Mojang's public API:
  ```
  https://launchermeta.mojang.com/mc/game/version_manifest.json
  ```
- Filter to show only `"release"` type versions (no snapshots by default)
- Add a toggle to also show **snapshots** for advanced users
- Cache the version list locally using **Room** so it works offline
- Fallback to cached/hardcoded list if network is unavailable
- Show a **"Latest"** badge on the most recent release

---

### 2. Version Selector UI (Jetpack Compose)
- Replace the hardcoded `1.20.4` with a **version picker dropdown/list**
- Show version ID (e.g. `"1.20.4"`), release date, and type (`release` / `snapshot`)
- Highlight the currently selected version
- Show a **loading shimmer** while fetching versions from API
- Show an **error state** with retry button if fetch fails
- Remember the last selected version using **DataStore Preferences**

---

### 3. Server JAR Management
- Each Minecraft version has a `server.jar` download URL inside its version JSON  
  *(fetch from `version.url`, then read `downloads.server.url`)*
- Download the correct `server.jar` for the selected version into:
  ```
  /data/data/<package>/files/servers/<version_id>/server.jar
  ```
- Show **download progress** with a `ProgressBar` and percentage text
- **Skip download** if `server.jar` already exists for that version (cache check)
- Allow users to **delete downloaded versions** to free storage
- Show the **size** of each downloaded version

---

### 4. Version-Aware Server Launch
- Pass the correct `server.jar` path to the JRE launch logic based on selected version
- Store **per-version server configs** separately so settings don't clash
- Show **which version is currently running** in the Active Server card

---

### 5. Architecture
- **MVVM** with `StateFlow`
- `VersionRepository` handles API + Room + download logic
- `VersionViewModel` exposes:

| Member | Type | Description |
|---|---|---|
| `versions` | `StateFlow<List<MCVersion>>` | All available versions |
| `selectedVersion` | `StateFlow<MCVersion?>` | Currently chosen version |
| `downloadState` | `StateFlow<DownloadState>` | Idle / Downloading / Done / Error |
| `selectVersion()` | `fun` | Select a version |
| `downloadServerJar()` | `fun` | Trigger JAR download |
| `deleteVersion()` | `fun` | Remove cached JAR |

---

### 6. Data Classes

```kotlin
data class MCVersion(
    val id: String,
    val type: String,       // "release" or "snapshot"
    val url: String,        // points to version detail JSON
    val releaseTime: String
)

data class VersionDetail(
    val id: String,
    val downloads: Downloads
)

data class Downloads(
    val server: ServerDownload
)

data class ServerDownload(
    val url: String,
    val size: Long
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    object Done : DownloadState()
    data class Error(val message: String) : DownloadState()
}
```

---

### 7. Dependencies to Use

| Library | Purpose |
|---|---|
| `Retrofit2 + Gson` | API calls |
| `Room` | Local version cache |
| `DataStore Preferences` | Persist last selected version |
| `Kotlin Coroutines + Flow` | Async logic |
| `Jetpack Compose` | UI |
| `OkHttp` | JAR download with progress tracking |

---

### 8. Fix Pointer-Tagging Crash

Add this to `AndroidManifest.xml` inside the `<application>` tag so the JRE doesn't crash on **Android 12+**:

```xml
<application
    android:allowNativeHeapPointerTagging="false"
    ...>
```

---

## 📁 File Structure

```
com.pocketcraft/
├── data/
│   ├── api/
│   │   └── MojangApiService.kt
│   ├── db/
│   │   ├── VersionDao.kt
│   │   └── VersionDatabase.kt
│   ├── model/
│   │   ├── MCVersion.kt
│   │   ├── VersionDetail.kt
│   │   └── DownloadState.kt
│   └── repository/
│       └── VersionRepository.kt
├── ui/
│   ├── versions/
│   │   ├── VersionSelectorScreen.kt
│   │   └── VersionViewModel.kt
│   └── components/
│       ├── VersionCard.kt
│       └── DownloadProgressBar.kt
└── AndroidManifest.xml
```

---

## ☑️ Quality Checklist

- [ ] Version list loads from API on first launch
- [ ] Offline fallback works using Room cache
- [ ] Correct `server.jar` downloads per version
- [ ] No re-download if JAR already exists
- [ ] Download progress shown in real time
- [ ] Selected version persists across app restarts
- [ ] Server launches with correct JAR for chosen version
- [ ] Pointer tagging fix applied in manifest
- [ ] Error states handled with user-friendly messages
- [ ] Snapshot toggle works correctly

---

## 🔁 App Flow

```
App Launch
    ↓
Fetch Mojang version list (or use Room cache if offline)
    ↓
User picks version from dropdown
    ↓
Check if server.jar exists for that version
    ↓ (if not)
Download server.jar with progress indicator
    ↓
Launch JRE with selected version's JAR path
    ↓
Show active version in server card
```
