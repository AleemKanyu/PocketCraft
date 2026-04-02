# PocketCraft — Firebase Analytics & Crashlytics Agent

## Your Role
You are a feature implementation agent. Your job is to integrate Firebase Analytics, Crashlytics, and Performance Monitoring into the PocketCraft Android app. Read the existing code carefully before making any changes. Do not ask questions — figure out the structure from the code and implement everything completely.

**Package name:** `com.pocketcraft.server`
**UI toolkit:** Jetpack Compose
**Note:** The app does NOT have ads yet. Do not add any ad-related code. Focus only on analytics, crash reporting, and performance monitoring.

---

## Setup — Dependencies & Configuration

### Step 1 — app/build.gradle

Add these plugins at the top of `app/build.gradle`:
```gradle
plugins {
    id 'com.google.gms.google-services'
    id 'com.google.firebase.crashlytics'
    id 'com.google.firebase.firebase-perf'
}
```

Add these dependencies:
```gradle
dependencies {
    // Firebase BOM — manages all Firebase versions automatically
    implementation platform('com.google.firebase:firebase-bom:32.7.0')

    // Analytics — tracks user behavior and custom events
    implementation 'com.google.firebase:firebase-analytics-ktx'

    // Crashlytics — crash reporting
    implementation 'com.google.firebase:firebase-crashlytics-ktx'

    // Performance monitoring — app startup time, network calls
    implementation 'com.google.firebase:firebase-perf-ktx'
}
```

### Step 2 — project-level build.gradle

Add to `dependencies` block in root `build.gradle`:
```gradle
classpath 'com.google.gms:google-services:4.4.0'
classpath 'com.google.firebase:firebase-crashlytics-gradle:2.9.9'
classpath 'com.google.firebase:firebase-perf-plugin:1.4.2'
```

### Step 3 — google-services.json

The `google-services.json` file must be placed in the `app/` directory. This file is provided by the developer from Firebase Console. If it is not present, add a comment in the README:
```
// TODO: Add google-services.json from Firebase Console to app/ directory
// Package name: com.pocketcraft.server
```
Do NOT generate or fake this file. Just ensure the gradle setup expects it.

---

## Create Analytics.kt

Create a new file `app/src/main/java/com/pocketcraft/server/Analytics.kt`:

```kotlin
package com.pocketcraft.server

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase

object Analytics {

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    fun init(context: Context) {
        firebaseAnalytics = Firebase.analytics
        Firebase.crashlytics.setCrashlyticsCollectionEnabled(true)
    }

    // ─────────────────────────────────────────────
    // APP LIFECYCLE
    // ─────────────────────────────────────────────

    fun logAppOpened() {
        firebaseAnalytics.logEvent("app_opened", null)
    }

    fun logFirstLaunch() {
        firebaseAnalytics.logEvent("first_launch", null)
    }

    // ─────────────────────────────────────────────
    // SERVER EVENTS
    // ─────────────────────────────────────────────

    fun logServerStarted(version: String, ramMode: String, ramMb: Int) {
        val bundle = Bundle().apply {
            putString("server_version", version)
            putString("ram_mode", ramMode)
            putInt("ram_mb", ramMb)
        }
        firebaseAnalytics.logEvent("server_started", bundle)

        // Set Crashlytics context
        Firebase.crashlytics.setCustomKey("server_running", true)
        Firebase.crashlytics.setCustomKey("server_version", version)
        Firebase.crashlytics.setCustomKey("ram_mode", ramMode)
        Firebase.crashlytics.setCustomKey("ram_mb", ramMb)
    }

    fun logServerStopped(uptimeMinutes: Long, peakPlayers: Int, stoppedBy: String) {
        // stoppedBy: "user", "crash", "oom", "unknown"
        val bundle = Bundle().apply {
            putLong("uptime_minutes", uptimeMinutes)
            putInt("peak_players", peakPlayers)
            putString("stopped_by", stoppedBy)
        }
        firebaseAnalytics.logEvent("server_stopped", bundle)

        Firebase.crashlytics.setCustomKey("server_running", false)
    }

    fun logServerCrash(reason: String) {
        val bundle = Bundle().apply {
            putString("crash_reason", reason)
        }
        firebaseAnalytics.logEvent("server_crash", bundle)
        Firebase.crashlytics.log("Server crashed: $reason")
    }

    fun logServerVersionSelected(version: String) {
        val bundle = Bundle().apply {
            putString("version", version)
        }
        firebaseAnalytics.logEvent("version_selected", bundle)
    }

    // ─────────────────────────────────────────────
    // TUNNEL / RELAY EVENTS
    // ─────────────────────────────────────────────

    fun logTunnelConnected(relay: String, timeToConnectMs: Long, port: Int) {
        val bundle = Bundle().apply {
            putString("relay", relay)
            putLong("time_to_connect_ms", timeToConnectMs)
            putInt("assigned_port", port)
        }
        firebaseAnalytics.logEvent("tunnel_connected", bundle)

        Firebase.crashlytics.setCustomKey("relay_connected", true)
        Firebase.crashlytics.setCustomKey("relay", relay)
        Firebase.crashlytics.setCustomKey("relay_port", port)
    }

    fun logTunnelFailed(relay: String, reason: String) {
        val bundle = Bundle().apply {
            putString("relay", relay)
            putString("failure_reason", reason)
        }
        firebaseAnalytics.logEvent("tunnel_failed", bundle)

        Firebase.crashlytics.setCustomKey("relay_connected", false)
        Firebase.crashlytics.log("Tunnel failed: $reason")
    }

    fun logTunnelDisconnected(relay: String, sessionDurationMs: Long) {
        val bundle = Bundle().apply {
            putString("relay", relay)
            putLong("session_duration_ms", sessionDurationMs)
        }
        firebaseAnalytics.logEvent("tunnel_disconnected", bundle)
    }

    // ─────────────────────────────────────────────
    // PLAYER EVENTS
    // ─────────────────────────────────────────────

    fun logPlayerJoined(playerCount: Int, maxPlayers: Int) {
        val bundle = Bundle().apply {
            putInt("player_count", playerCount)
            putInt("max_players", maxPlayers)
        }
        firebaseAnalytics.logEvent("player_joined", bundle)
        Firebase.crashlytics.setCustomKey("current_players", playerCount)
    }

    fun logPlayerLeft(playerCount: Int, sessionMinutes: Long) {
        val bundle = Bundle().apply {
            putInt("player_count", playerCount)
            putLong("player_session_minutes", sessionMinutes)
        }
        firebaseAnalytics.logEvent("player_left", bundle)
        Firebase.crashlytics.setCustomKey("current_players", playerCount)
    }

    fun logPlayerKicked(reason: String) {
        val bundle = Bundle().apply {
            putString("reason", reason)
        }
        firebaseAnalytics.logEvent("player_kicked", bundle)
    }

    fun logPlayerBanned() {
        firebaseAnalytics.logEvent("player_banned", null)
    }

    fun logPlayerOpped() {
        firebaseAnalytics.logEvent("player_opped", null)
    }

    fun logPlayerTeleported() {
        firebaseAnalytics.logEvent("player_teleported", null)
    }

    // ─────────────────────────────────────────────
    // PLUGIN EVENTS
    // ─────────────────────────────────────────────

    fun logPluginInstalled(method: String, pluginName: String) {
        // method: "url", "upload"
        val bundle = Bundle().apply {
            putString("install_method", method)
            putString("plugin_name", pluginName)
        }
        firebaseAnalytics.logEvent("plugin_installed", bundle)
    }

    fun logPluginDeleted() {
        firebaseAnalytics.logEvent("plugin_deleted", null)
    }

    fun logPluginToggled(enabled: Boolean) {
        val bundle = Bundle().apply {
            putBoolean("enabled", enabled)
        }
        firebaseAnalytics.logEvent("plugin_toggled", bundle)
    }

    fun logPluginDownloadFailed(reason: String) {
        val bundle = Bundle().apply {
            putString("reason", reason)
        }
        firebaseAnalytics.logEvent("plugin_download_failed", bundle)
    }

    // ─────────────────────────────────────────────
    // WORLD EVENTS
    // ─────────────────────────────────────────────

    fun logWorldUploaded(fileSizeMb: Float) {
        val bundle = Bundle().apply {
            putFloat("file_size_mb", fileSizeMb)
        }
        firebaseAnalytics.logEvent("world_uploaded", bundle)
    }

    fun logWorldUploadFailed(reason: String) {
        val bundle = Bundle().apply {
            putString("reason", reason)
        }
        firebaseAnalytics.logEvent("world_upload_failed", bundle)
    }

    fun logWorldReset() {
        firebaseAnalytics.logEvent("world_reset", null)
    }

    // ─────────────────────────────────────────────
    // RAM EVENTS
    // ─────────────────────────────────────────────

    fun logRamModeChanged(mode: String, ramMb: Int) {
        // mode: "low", "manual", "full"
        val bundle = Bundle().apply {
            putString("ram_mode", mode)
            putInt("ram_mb", ramMb)
        }
        firebaseAnalytics.logEvent("ram_mode_changed", bundle)
    }

    fun logRamWarning(usedMb: Int, maxMb: Int, percentage: Int) {
        // Fired when RAM usage exceeds 85%
        val bundle = Bundle().apply {
            putInt("used_mb", usedMb)
            putInt("max_mb", maxMb)
            putInt("usage_percentage", percentage)
        }
        firebaseAnalytics.logEvent("ram_warning", bundle)
        Firebase.crashlytics.log("RAM warning: ${usedMb}MB / ${maxMb}MB (${percentage}%)")
    }

    // ─────────────────────────────────────────────
    // SETTINGS EVENTS
    // ─────────────────────────────────────────────

    fun logSettingChanged(settingName: String, newValue: String) {
        val bundle = Bundle().apply {
            putString("setting_name", settingName)
            putString("new_value", newValue)
        }
        firebaseAnalytics.logEvent("setting_changed", bundle)
    }

    fun logWhitelistToggled(enabled: Boolean) {
        val bundle = Bundle().apply {
            putBoolean("enabled", enabled)
        }
        firebaseAnalytics.logEvent("whitelist_toggled", bundle)
    }

    fun logSoundsToggled(enabled: Boolean) {
        val bundle = Bundle().apply {
            putBoolean("enabled", enabled)
        }
        firebaseAnalytics.logEvent("sounds_toggled", bundle)
    }

    // ─────────────────────────────────────────────
    // SCREEN VIEWS
    // ─────────────────────────────────────────────

    fun logScreenView(screenName: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
        }
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }

    // Call these on each screen:
    // Analytics.logScreenView("home")
    // Analytics.logScreenView("console")
    // Analytics.logScreenView("settings")
    // Analytics.logScreenView("plugins")
    // Analytics.logScreenView("player_detail")
    // Analytics.logScreenView("worlds")
    // Analytics.logScreenView("files")

    // ─────────────────────────────────────────────
    // ERROR REPORTING
    // ─────────────────────────────────────────────

    fun logError(tag: String, message: String, exception: Exception? = null) {
        val bundle = Bundle().apply {
            putString("error_tag", tag)
            putString("error_message", message)
        }
        firebaseAnalytics.logEvent("app_error", bundle)

        Firebase.crashlytics.log("[$tag] $message")
        exception?.let { Firebase.crashlytics.recordException(it) }
    }

    fun logNonFatalException(exception: Exception, context: String) {
        Firebase.crashlytics.log("Non-fatal in: $context")
        Firebase.crashlytics.recordException(exception)
    }

    // ─────────────────────────────────────────────
    // DEVICE CONTEXT (set once on app start)
    // ─────────────────────────────────────────────

    fun setDeviceContext(totalRamMb: Int, androidVersion: Int) {
        Firebase.crashlytics.setCustomKey("total_ram_mb", totalRamMb)
        Firebase.crashlytics.setCustomKey("android_version", androidVersion)
        Firebase.crashlytics.setCustomKey("relay_host", "mine.pocketcraft.online")
    }

    // ─────────────────────────────────────────────
    // FEATURE USAGE
    // ─────────────────────────────────────────────

    fun logFeatureUsed(featureName: String) {
        // Track which features are actually used
        // featureName examples: "player_detail", "inventory_preview",
        // "player_teleport", "server_console", "file_manager",
        // "plugin_manager", "world_import", "whitelist_manager"
        val bundle = Bundle().apply {
            putString("feature_name", featureName)
        }
        firebaseAnalytics.logEvent("feature_used", bundle)
    }

    // ─────────────────────────────────────────────
    // ONBOARDING / RETENTION
    // ─────────────────────────────────────────────

    fun logOnboardingStep(step: Int, stepName: String) {
        val bundle = Bundle().apply {
            putInt("step_number", step)
            putString("step_name", stepName)
        }
        firebaseAnalytics.logEvent("onboarding_step", bundle)
    }

    fun logOnboardingCompleted() {
        firebaseAnalytics.logEvent("onboarding_completed", null)
    }

    fun logServerHostedForFirstTime() {
        firebaseAnalytics.logEvent("first_server_hosted", null)
    }

    fun logFriendJoinedForFirstTime() {
        // First time someone other than the host connects
        firebaseAnalytics.logEvent("first_friend_joined", null)
    }
}
```

---

## Create Application Class

Search the codebase for an existing `Application` class. If one exists, add Firebase init to it. If not, create one:

```kotlin
package com.pocketcraft.server

import android.app.Application
import com.google.firebase.FirebaseApp

class PocketCraftApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        Analytics.init(this)

        // Set device context for Crashlytics
        val totalRam = getTotalRamMb(this)
        Analytics.setDeviceContext(
            totalRamMb = totalRam,
            androidVersion = android.os.Build.VERSION.SDK_INT
        )

        // Track first launch
        val prefs = AppPreferences(this)
        if (!prefs.hasLaunchedBefore) {
            Analytics.logFirstLaunch()
            prefs.hasLaunchedBefore = true
        }

        Analytics.logAppOpened()
    }
}
```

Add `hasLaunchedBefore` to `AppPreferences.kt`:
```kotlin
var hasLaunchedBefore: Boolean
    get() = prefs.getBoolean("has_launched_before", false)
    set(value) = prefs.edit().putBoolean("has_launched_before", value).apply()
```

Register in `AndroidManifest.xml`:
```xml
<application
    android:name=".PocketCraftApp"
    ...>
```

---

## Wire Analytics Calls Into Existing Code

### ServerHostService.kt
Find the existing server lifecycle events and add analytics:

```kotlin
// When server starts successfully (after port probe confirms ready)
Analytics.logServerStarted(
    version = selectedVersion,
    ramMode = AppPreferences(this).ramMode,
    ramMb = AppPreferences(this).manualRamMb
)
Analytics.logServerHostedForFirstTime() // only first time — check a flag in prefs

// When tunnel connects
val connectStart = System.currentTimeMillis()
// ... existing tunnel connect code ...
Analytics.logTunnelConnected(
    relay = "mumbai", // or "singapore"
    timeToConnectMs = System.currentTimeMillis() - connectStart,
    port = relayManager.assignedPort ?: 0
)

// When tunnel fails
Analytics.logTunnelFailed(relay = "mumbai", reason = e.message ?: "unknown")

// When server stops
Analytics.logServerStopped(
    uptimeMinutes = uptimeMs / 60000,
    peakPlayers = peakPlayerCount,
    stoppedBy = "user" // or "crash" or "oom"
)

// When server crashes (detect OOM or unexpected stop)
Analytics.logServerCrash(reason = "oom") // or parse crash log
```

### RelayManager.kt
```kotlin
// When tunnel disconnects unexpectedly
Analytics.logTunnelDisconnected(
    relay = "mumbai",
    sessionDurationMs = System.currentTimeMillis() - tunnelStartTime
)
```

### ServerLauncher.kt
```kotlin
// When version is selected
Analytics.logServerVersionSelected(version = selectedVersion)
```

### PluginManager.kt
```kotlin
// After successful install from URI
Analytics.logPluginInstalled(method = "upload", pluginName = fileName)

// After successful install from URL
Analytics.logPluginInstalled(method = "url", pluginName = fileName)

// On download failure
Analytics.logPluginDownloadFailed(reason = e.message ?: "unknown")
```

### WorldImporter.kt
```kotlin
// After successful world import
val fileSizeMb = zipUri.let { /* get file size */ } / 1024f / 1024f
Analytics.logWorldUploaded(fileSizeMb = fileSizeMb)

// On import failure
Analytics.logWorldUploadFailed(reason = e.message ?: "unknown")
```

### Home Screen / Dashboard Activity
```kotlin
// On screen open
LaunchedEffect(Unit) {
    Analytics.logScreenView("home")
}

// When RAM usage hits 85%+
LaunchedEffect(usedRamMb, maxRamMb) {
    val pct = (usedRamMb * 100 / maxRamMb)
    if (pct >= 85) {
        Analytics.logRamWarning(usedRamMb, maxRamMb, pct)
    }
}
```

### All other screens
Add `Analytics.logScreenView("screen_name")` in a `LaunchedEffect(Unit)` at the top of each screen composable:

```kotlin
// Console screen
LaunchedEffect(Unit) { Analytics.logScreenView("console") }

// Settings screen
LaunchedEffect(Unit) { Analytics.logScreenView("settings") }

// Plugin manager screen
LaunchedEffect(Unit) { Analytics.logScreenView("plugins") }

// Player detail screen
LaunchedEffect(Unit) {
    Analytics.logScreenView("player_detail")
    Analytics.logFeatureUsed("player_detail")
}

// Worlds screen
LaunchedEffect(Unit) { Analytics.logScreenView("worlds") }

// Files screen
LaunchedEffect(Unit) { Analytics.logScreenView("files") }
```

### Settings Screen
```kotlin
// When any setting is changed
Analytics.logSettingChanged("render_distance", "10")
Analytics.logSettingChanged("game_mode", "survival")
Analytics.logSettingChanged("difficulty", "hard")
Analytics.logWhitelistToggled(enabled = true)
Analytics.logSoundsToggled(enabled = false)
Analytics.logRamModeChanged(mode = "manual", ramMb = 2048)
```

### Player Management
```kotlin
Analytics.logPlayerJoined(playerCount = currentCount, maxPlayers = maxCount)
Analytics.logPlayerLeft(playerCount = currentCount, sessionMinutes = sessionMs / 60000)
Analytics.logPlayerKicked(reason = "manual")
Analytics.logPlayerBanned()
Analytics.logPlayerOpped()
Analytics.logPlayerTeleported()
Analytics.logFeatureUsed("inventory_preview")
Analytics.logFeatureUsed("player_teleport")
```

---

## Add Crashlytics Error Wrapping

Find the most critical try/catch blocks in the codebase — especially in:
- `ServerLauncher.kt` — JVM launch
- `RelayManager.kt` — socket operations
- `ServerHostService.kt` — service lifecycle
- `WorldImporter.kt` — file operations
- `PluginManager.kt` — download/install

Wrap non-fatal exceptions:

```kotlin
try {
    // existing code
} catch (e: Exception) {
    Analytics.logNonFatalException(e, context = "ServerLauncher.launch")
    // existing error handling
}
```

For fatal crashes, Crashlytics catches them automatically — no code needed.

---

## Performance Monitoring — Custom Traces

Add performance traces around the two slowest operations:

### Server startup time
```kotlin
import com.google.firebase.perf.ktx.performance
import com.google.firebase.perf.ktx.trace

// In ServerLauncher.kt or ServerHostService.kt
Firebase.performance.newTrace("server_startup").trace {
    // existing server launch code
}
```

### World import time
```kotlin
// In WorldImporter.kt
Firebase.performance.newTrace("world_import").trace {
    // existing import code
}
```

---

## Quality Checklist

Before finishing, verify:

- [ ] `google-services` plugin applied in app/build.gradle
- [ ] `firebase-crashlytics` plugin applied in app/build.gradle
- [ ] Firebase BOM dependency added
- [ ] `firebase-analytics-ktx` dependency added
- [ ] `firebase-crashlytics-ktx` dependency added
- [ ] `firebase-perf-ktx` dependency added
- [ ] `Analytics.kt` exists at correct package path `com.pocketcraft.server`
- [ ] `PocketCraftApp` Application class exists and registered in manifest
- [ ] `Analytics.init()` called in Application `onCreate()`
- [ ] `Analytics.setDeviceContext()` called on app start with real RAM and Android version
- [ ] `hasLaunchedBefore` added to `AppPreferences.kt`
- [ ] `Analytics.logFirstLaunch()` fires only on first ever launch
- [ ] `Analytics.logServerStarted()` fires when server becomes ready
- [ ] `Analytics.logServerStopped()` fires when server stops with correct uptime
- [ ] `Analytics.logTunnelConnected()` fires with actual connection time
- [ ] `Analytics.logTunnelFailed()` fires on relay failure
- [ ] `Analytics.logPlayerJoined/Left()` fires on player events
- [ ] `Analytics.logPluginInstalled()` fires for both upload and URL methods
- [ ] `Analytics.logWorldUploaded()` fires after successful import
- [ ] `Analytics.logScreenView()` added to every screen
- [ ] `Analytics.logRamWarning()` fires when RAM exceeds 85%
- [ ] Non-fatal exceptions wrapped in critical try/catch blocks
- [ ] Performance traces added to server startup and world import
- [ ] README note added about needing `google-services.json` from Firebase Console
- [ ] App builds without errors

---

## What This Does NOT Change

- Relay system (`RelayManager.kt`, `ServerHostService.kt` relay logic) — unchanged
- Any UI screens — unchanged
- RAM calculation logic — unchanged
- World import logic — unchanged
- Plugin manager logic — unchanged
- Theme and colors — unchanged
- No ads or monetization code added
