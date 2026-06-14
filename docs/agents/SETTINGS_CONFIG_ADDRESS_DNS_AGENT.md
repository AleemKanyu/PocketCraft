# SETTINGS_CONFIG_ADDRESS_DNS_AGENT

## Context
PocketCraft Android app — Kotlin + Jetpack Compose, package `com.pocketcraft.server`.
Key files: `AppPreferences.kt`, `ServerHostService.kt`, `RelayManager.kt`, `ServerStateHolder.kt`, `ServerConsole.kt`.
The app runs a Paper 1.20.4 server locally and exposes it via a TCP relay (Mumbai EC2 `mine.pocketcraft.online`, Singapore `play.pocketcraft.online`).

---

## Bug 1 — Settings "Edit Config" crashes the app

### Root cause pattern
The edit config buttons in Settings are almost certainly calling `Intent(Intent.ACTION_EDIT)` or `Intent(Intent.ACTION_VIEW)` with a raw `file://` URI. On Android 7+ this throws `FileUriExposedException` because raw file URIs cannot be passed between apps. On Android 10+ even FileProvider URIs for arbitrary paths need the correct authority. The crash happens before any chooser appears.

### Fix — Step by step

#### 1. Add FileProvider to `AndroidManifest.xml`
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_provider_paths" />
</provider>
```

#### 2. Create `res/xml/file_provider_paths.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <!-- Covers the Paper server folder inside app files dir -->
    <files-path name="server_files" path="." />
    <!-- Covers external files if configs live there -->
    <external-files-path name="external_files" path="." />
</paths>
```

#### 3. Create `ConfigEditorLauncher.kt` utility
Place in `utils/` or alongside `AppPreferences.kt`.

```kotlin
package com.pocketcraft.server.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ConfigEditorLauncher {

    /**
     * Opens [file] in a text editor.
     * - If only one text editor is installed → opens it directly.
     * - If multiple are installed → shows an app chooser (Intent.createChooser).
     * - If none found → shows Toast and falls back to a plain VIEW intent so
     *   the system can handle it, or shows "no app found" Toast.
     */
    fun openConfig(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "Config file not found: ${file.name}", Toast.LENGTH_LONG).show()
            return
        }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        // Prefer text/plain so editors pick it up; fall back to application/octet-stream
        val mimeType = "text/plain"

        val editIntent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

        val pm: PackageManager = context.packageManager
        val editors = pm.queryIntentActivities(editIntent, PackageManager.MATCH_DEFAULT_ONLY)

        when {
            editors.isEmpty() -> {
                // Fall back to ACTION_VIEW (some editors only register VIEW)
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val viewers = pm.queryIntentActivities(viewIntent, PackageManager.MATCH_DEFAULT_ONLY)
                if (viewers.isEmpty()) {
                    Toast.makeText(
                        context,
                        "No text editor installed. Install one like 'Text Editor' from Play Store.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    context.startActivity(
                        Intent.createChooser(viewIntent, "Open ${file.name} with")
                    )
                }
            }
            editors.size == 1 -> {
                // Only one editor — open directly, no chooser dialog
                editIntent.setPackage(editors[0].activityInfo.packageName)
                context.startActivity(editIntent)
            }
            else -> {
                // Multiple editors — let user choose
                context.startActivity(
                    Intent.createChooser(editIntent, "Edit ${file.name} with")
                )
            }
        }
    }
}
```

#### 4. Update Settings screen — replace all broken edit-config click handlers
Find every place in the Settings composable (likely `SettingsScreen.kt`) that previously triggered an edit config action and replace them with:

```kotlin
// Example for server.properties
val serverPropertiesFile = File(context.filesDir, "server/server.properties")
ConfigEditorLauncher.openConfig(context, serverPropertiesFile)

// Example for bukkit.yml
val bukkitFile = File(context.filesDir, "server/bukkit.yml")
ConfigEditorLauncher.openConfig(context, bukkitFile)

// Example for spigot.yml
val spigotFile = File(context.filesDir, "server/spigot.yml")
ConfigEditorLauncher.openConfig(context, spigotFile)
```

Adjust paths to wherever PocketCraft stores the Paper server folder inside the app's private storage. Check `ServerLauncher.kt` for the exact base dir (`context.filesDir` + some subfolder).

---

## Bug 2 — Address card shown too early (right after relay connects, not after world is joinable)

### Problem
The address card (the UI showing `mine.pocketcraft.online:PORT`) is currently displayed as soon as the relay connection succeeds — i.e., `RelayManager` emits a "connected" event and the composable reacts to it. But the server isn't actually joinable until spawn chunks have been generated/loaded, which Paper signals with a specific log line.

### The correct trigger log line (Paper 1.20.4)
```
Done (X.XXXs)! For help, type "help"
```
This line is emitted by Paper exactly once per startup, after spawn chunks are fully loaded and the server is accepting player connections.

### Fix

#### 1. Add a `serverJoinable: StateFlow<Boolean>` to `ServerStateHolder.kt`
```kotlin
// In ServerStateHolder.kt
private val _serverJoinable = MutableStateFlow(false)
val serverJoinable: StateFlow<Boolean> = _serverJoinable.asStateFlow()

fun markJoinable() {
    _serverJoinable.value = true
}

fun resetJoinable() {
    _serverJoinable.value = false
}
```
Call `resetJoinable()` in the same place you reset other server state on stop/restart.

#### 2. In `ServerConsole.kt` (or wherever log lines are parsed) — detect the "Done" line
```kotlin
// Inside the log-parsing loop (the same loop that updates console output)
if (line.contains("Done (") && line.contains("! For help, type \"help\"")) {
    serverStateHolder.markJoinable()
}
```
This is more reliable than a timer or relay-connection event because it's the server's own signal that it's ready.

#### 3. Update the Home screen composable — gate the address card on `serverJoinable`
```kotlin
val serverJoinable by serverStateHolder.serverJoinable.collectAsState()
val relayConnected by relayManager.isConnected.collectAsState() // already exists

// Show address card ONLY when server is done loading, not just relay-connected
if (relayConnected && serverJoinable) {
    AddressCard(
        host = relayManager.assignedHost,
        port = relayManager.assignedPort
    )
} else if (relayConnected && !serverJoinable) {
    // Optional: show a "Loading world…" placeholder in place of the card
    ServerLoadingIndicator() // a simple Text + CircularProgressIndicator composable
}
```

#### 4. Make sure `resetJoinable()` is called on server stop
In `ServerHostService.kt`, wherever you call the stop sequence:
```kotlin
serverStateHolder.resetJoinable()
```

---

## Bug 3 — Users with different DNS servers get high ping

### Root cause
When the relay server address (`mine.pocketcraft.online`) is resolved by `RelayManager`, the DNS lookup is done at connect time using whatever system DNS the device has. Many ISPs in South Asia return geographically distant or incorrect IP addresses for the hostname, or the TTL is very short and repeated re-resolution is happening mid-session, causing spikes. Additionally, if the app resolves the hostname on each reconnect attempt, users on slow/unreliable DNS will see high initial latency or random lag spikes.

### Fix — Resolve once, cache the IP, reconnect to IP directly

#### In `RelayManager.kt` — add DNS pre-resolution with hardcoded IP fallback

```kotlin
companion object {
    // Hardcode the actual EC2 IPs so DNS is bypassed if resolution fails or is slow.
    // Update these if you ever change EC2 instances.
    private const val PRIMARY_HOST = "mine.pocketcraft.online"
    private const val PRIMARY_IP_FALLBACK = "YOUR_MUMBAI_EC2_IP"   // e.g. "13.235.xx.xx"
    private const val SECONDARY_HOST = "play.pocketcraft.online"
    private const val SECONDARY_IP_FALLBACK = "YOUR_SINGAPORE_EC2_IP"

    private const val RELAY_PORT = 9000  // PHONE_TUNNEL_PORT
}

// Resolved IP cache — filled once per app session
private var resolvedPrimaryIp: String? = null
private var resolvedSecondaryIp: String? = null

/**
 * Resolves hostnames to IPs once and caches them.
 * If DNS is slow/failing, falls back to the hardcoded IPs immediately.
 * Call this once when the user taps "Start Server", before opening the socket.
 */
private suspend fun resolveRelayIps() {
    if (resolvedPrimaryIp != null) return  // already resolved this session

    withContext(Dispatchers.IO) {
        resolvedPrimaryIp = try {
            withTimeoutOrNull(2000L) {
                InetAddress.getAllByName(PRIMARY_HOST)
                    .firstOrNull()?.hostAddress
            } ?: PRIMARY_IP_FALLBACK
        } catch (e: Exception) {
            PRIMARY_IP_FALLBACK
        }

        resolvedSecondaryIp = try {
            withTimeoutOrNull(2000L) {
                InetAddress.getAllByName(SECONDARY_HOST)
                    .firstOrNull()?.hostAddress
            } ?: SECONDARY_IP_FALLBACK
        } catch (e: Exception) {
            SECONDARY_IP_FALLBACK
        }
    }
    Log.d("RelayManager", "Resolved primary=$resolvedPrimaryIp secondary=$resolvedSecondaryIp")
}

// In your connect() function, call resolveRelayIps() first, then use the cached IP:
suspend fun connect() {
    resolveRelayIps()
    val ip = resolvedPrimaryIp ?: PRIMARY_IP_FALLBACK
    // Socket(ip, RELAY_PORT)  ← use ip instead of PRIMARY_HOST
    // On failover to secondary:
    // Socket(resolvedSecondaryIp ?: SECONDARY_IP_FALLBACK, RELAY_PORT)
}
```

**Replace `YOUR_MUMBAI_EC2_IP` and `YOUR_SINGAPORE_EC2_IP` with the actual Elastic IPs from your AWS console.** These don't change unless you reassign them.

#### Also: disable Nagle's algorithm on the relay socket
Nagle's algorithm batches small TCP packets, which adds latency to Minecraft's frequent small packets. Disable it on the relay tunnel socket:

```kotlin
val socket = Socket(ip, RELAY_PORT).apply {
    tcpNoDelay = true          // disable Nagle → reduces per-packet latency
    soTimeout = 30_000         // 30s read timeout so dead connections are detected
    keepAlive = true
}
```

#### Also: set `tcpNoDelay = true` on the Minecraft-side socket too
If `RelayManager` opens a separate socket toward `localhost:25565` (the MC server), set `tcpNoDelay = true` on that one as well.

---

## Summary of files to change

| File | Change |
|------|--------|
| `AndroidManifest.xml` | Add `<provider>` block for FileProvider |
| `res/xml/file_provider_paths.xml` | **Create new** — defines which paths can be shared |
| `utils/ConfigEditorLauncher.kt` | **Create new** — safe file-open logic with chooser |
| `SettingsScreen.kt` | Replace crash-causing edit config click handlers with `ConfigEditorLauncher.openConfig(context, file)` |
| `ServerStateHolder.kt` | Add `serverJoinable: StateFlow<Boolean>`, `markJoinable()`, `resetJoinable()` |
| `ServerConsole.kt` | Detect `"Done ("` log line → call `serverStateHolder.markJoinable()` |
| `HomeScreen.kt` (or wherever AddressCard lives) | Gate address card on `relayConnected && serverJoinable` |
| `ServerHostService.kt` | Call `serverStateHolder.resetJoinable()` in stop sequence |
| `RelayManager.kt` | Add `resolveRelayIps()` with 2s timeout + hardcoded IP fallback; set `tcpNoDelay = true` on both sockets |
