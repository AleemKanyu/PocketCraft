# CODEBASE_SCAN_PROMPT

## Your Task

Scan the entire PocketCraft Android project codebase and produce a single structured markdown report called `CODEBASE_REPORT.md`. This report will be used to give an AI assistant (Claude) full knowledge of the project so it can help with future tasks without guessing at architecture, file names, or implementation details.

Be exhaustive. Do not summarize vaguely. The goal is that someone with zero prior knowledge of this project can read this report and immediately understand exactly how everything works and where everything lives.

---

## Step 1 — Project Structure

Walk the entire project directory tree and output it in full, up to 6 levels deep. Include all:
- Kotlin source files (`.kt`)
- C/C++ native files (`.c`, `.cpp`, `.h`)
- Config/asset files (`assets/`, `res/`)
- Gradle files (`build.gradle`, `settings.gradle`, `gradle.properties`)
- Manifest (`AndroidManifest.xml`)
- Any markdown, JSON, or YAML config files at the root

Format:
```
app/
  src/
    main/
      java/com/pocketcraft/server/
        ui/
          HomeScreen.kt
          SettingsScreen.kt
          ...
        service/
          ServerHostService.kt
          ...
        ...
      jni/
        launcher.c
        ...
      assets/
        ...
      AndroidManifest.xml
```

---

## Step 2 — Core Files Deep Scan

For each file listed below, output:
1. **Full file path**
2. **Purpose** — what this file does in one sentence
3. **Key functions/classes** — list every function, class, and companion object with a one-line description of what it does
4. **Dependencies** — what other project files it imports or calls
5. **Important constants/values** — any hardcoded strings, ports, URLs, keys, or config values found in the file

### Files to scan:

**Native**
- `jni/launcher.c` (or wherever the JVM launch code lives)

**Services**
- `ServerHostService.kt`
- `RelayManager.kt`
- `NetworkMonitor.kt`

**Data/Preferences**
- `AppPreferences.kt`
- `PlayerDataManager.kt`

**UI Screens** — scan ALL screen files found under `ui/` or `screens/`:
- `HomeScreen.kt`
- `ConsoleScreen.kt`
- `StorageScreen.kt`
- `PluginsScreen.kt`
- `SettingsScreen.kt`
- Any other screen files found

**Features**
- `DriveBackupManager.kt`
- `PluginManager.kt`
- `WorldImporter.kt`
- `Analytics.kt`
- `ServerConsole.kt`

**Any other `.kt` files found** — include them all, even utility/helper files.

---

## Step 3 — AndroidManifest.xml Summary

Output:
- Package name
- Min SDK / Target SDK / Compile SDK
- All declared permissions
- All declared services with their `android:foregroundServiceType` values
- All declared activities
- All declared receivers
- Any custom application class name

---

## Step 4 — Gradle / Dependency Summary

From `build.gradle` (app level), output:
- `applicationId`
- `versionCode` and `versionName`
- All `dependencies { }` entries — library name + version
- All `buildFeatures` enabled
- NDK / CMake config if present
- Any `buildTypes` or `productFlavors`

---

## Step 5 — Assets Inventory

List everything inside `assets/` recursively:
- File name
- File size (bytes)
- Purpose (infer from name — e.g. `paper-1.20.4.jar` → Paper server JAR, `default_plugins/Chunky.jar` → bundled plugin)

---

## Step 6 — UI Theme & Design System

Extract from source files (likely `Theme.kt`, `Color.kt`, or wherever colors are defined):
- All named colors with their hex values
- Font families used
- Any shared composable components (cards, toggles, buttons) with their function signatures

---

## Step 7 — Server Lifecycle Flow

Trace the exact sequence of events from "user taps Start Server" to "server is running and accepting connections". For each step output:
- Which file/function handles it
- What it does
- What it calls next

Example format:
```
1. HomeScreen.kt → StartButton.onClick()
   → calls ServerHostService.start()
2. ServerHostService.kt → onStartCommand()
   → reads AppPreferences for RAM mode, view distance
   → writes server.properties
   → calls launchJVM() in launcher.c via JNI
3. launcher.c → launchJVM()
   → dlopen() loads JRE .so files
   → calls JNI_CreateJavaVM()
   → ...
```

---

## Step 8 — Relay Architecture

Document exactly how the relay works:
- How `RelayManager.kt` connects to the EC2 relay
- What protocol is used (TCP framing, packet format if known)
- How ports are assigned per user
- What `mine.pocketcraft.online` and `play.pocketcraft.online` resolve to
- How the relay address is exposed to players (copy button, share sheet, etc.)

---

## Step 9 — Known Issues & TODOs

Search the entire codebase for:
- `// TODO`
- `// FIXME`
- `// HACK`
- `// workaround`
- Any commented-out code blocks longer than 3 lines

List each one with its file, line number, and the full comment text.

---

## Step 10 — Open Questions for Claude

After completing the scan, list any parts of the codebase that are:
- Incomplete or stubbed out
- Unclear in purpose
- Potentially buggy based on what you can see
- Missing error handling
- Hardcoded values that should be configurable

Format each as:
```
FILE: ServerHostService.kt
ISSUE: destroyForcibly() is called with an 8s timeout but save-all is not awaited first — risk of world corruption on force stop
```

---

## Output Format

Save the complete report as `CODEBASE_REPORT.md` in the project root (or outputs directory).

Structure it with these exact top-level headings:
```
# CODEBASE_REPORT — PocketCraft

## 1. Project Structure
## 2. Core Files
## 3. AndroidManifest Summary
## 4. Gradle & Dependencies
## 5. Assets Inventory
## 6. UI Theme & Design System
## 7. Server Lifecycle Flow
## 8. Relay Architecture
## 9. Known Issues & TODOs
## 10. Open Questions
```

Do not skip any section. If a file does not exist, write `NOT FOUND` for that entry.
