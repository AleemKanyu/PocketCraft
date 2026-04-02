# PocketCraft — Network Awareness & Server Lifecycle Agent

## Your Role
You are a bug fix and feature implementation agent. Your job is to implement network-aware server behavior and fix the server not closing when the app is closed. Read the existing code carefully before making any changes. Do not ask questions — figure out the structure from the code and implement everything completely.

**Package name:** `com.pocketcraft.server`
**UI toolkit:** Jetpack Compose

---

## Overview of Changes

1. **Block server start if no internet AND no WiFi** — no connection at all = don't start
2. **WiFi only (no internet)** — allow server to start, show LAN address only, hide public relay address, warn user online players cannot connect
3. **Mobile data** — allow server to start with relay, show public address normally
4. **Connection lost while server running on mobile data** — stop the server immediately, notify user
5. **Connection lost while server running on WiFi** — keep server running on LAN, hide public address, warn user online players disconnected
6. **Fix server not stopping when app is closed**

---

## Create NetworkMonitor.kt

Create `app/src/main/java/com/pocketcraft/server/NetworkMonitor.kt`:

```kotlin
package com.pocketcraft.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkState {
    NO_CONNECTION,        // No WiFi, no mobile data — block server start
    WIFI_NO_INTERNET,     // WiFi connected but no internet — LAN only mode
    WIFI_WITH_INTERNET,   // WiFi with internet — full relay mode (treated as LAN only per spec)
    MOBILE_DATA           // Mobile data — full relay mode
}

data class NetworkStatus(
    val state: NetworkState,
    val isWifi: Boolean,
    val hasMobileData: Boolean,
    val hasInternet: Boolean
) {
    val canStartServer: Boolean
        get() = state != NetworkState.NO_CONNECTION

    val shouldUseRelay: Boolean
        get() = state == NetworkState.MOBILE_DATA

    val isLanOnly: Boolean
        get() = state == NetworkState.WIFI_NO_INTERNET || state == NetworkState.WIFI_WITH_INTERNET

    val warningMessage: String?
        get() = when (state) {
            NetworkState.WIFI_NO_INTERNET -> "Connected to WiFi without internet. Only players on your local network can join."
            NetworkState.WIFI_WITH_INTERNET -> "Connected to WiFi. Only players on your local network can join. Online players cannot connect."
            NetworkState.NO_CONNECTION -> "No internet connection. Cannot start server."
            NetworkState.MOBILE_DATA -> null
        }
}

object NetworkMonitor {

    fun getNetworkStatus(context: Context): NetworkStatus {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(activeNetwork)

        if (activeNetwork == null || capabilities == null) {
            return NetworkStatus(
                state = NetworkState.NO_CONNECTION,
                isWifi = false,
                hasMobileData = false,
                hasInternet = false
            )
        }

        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val hasMobileData = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val state = when {
            isWifi && !hasInternet -> NetworkState.WIFI_NO_INTERNET
            isWifi && hasInternet -> NetworkState.WIFI_WITH_INTERNET
            hasMobileData -> NetworkState.MOBILE_DATA
            else -> NetworkState.NO_CONNECTION
        }

        return NetworkStatus(
            state = state,
            isWifi = isWifi,
            hasMobileData = hasMobileData,
            hasInternet = hasInternet
        )
    }

    fun observeNetworkStatus(context: Context): Flow<NetworkStatus> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getNetworkStatus(context))
            }

            override fun onLost(network: Network) {
                trySend(getNetworkStatus(context))
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(getNetworkStatus(context))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        // Emit current state immediately
        trySend(getNetworkStatus(context))

        cm.registerNetworkCallback(request, callback)

        awaitClose {
            cm.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
}
```

---

## Modify ServerHostService.kt

### 1. Add network monitoring

At the top of the `ServerHostService` class, add:

```kotlin
private var networkJob: Job? = null
private var serverStartTime: Long = 0L
```

### 2. Add network monitoring after server starts

Find where the server starts successfully (after tunnel connected or after port probe). Add network monitoring:

```kotlin
private fun startNetworkMonitoring() {
    networkJob = CoroutineScope(Dispatchers.IO).launch {
        NetworkMonitor.observeNetworkStatus(this@ServerHostService)
            .collect { status ->
                handleNetworkChange(status)
            }
    }
}

private fun handleNetworkChange(status: NetworkStatus) {
    when (status.state) {
        NetworkState.NO_CONNECTION -> {
            // No connection at all while server running — stop server
            sendBroadcast(Intent(EVENT_NETWORK_LOST).apply {
                putExtra("reason", "no_connection")
                putExtra("message", "Internet connection lost. Server stopped.")
            })
            stopServer()
        }

        NetworkState.MOBILE_DATA -> {
            // Mobile data still connected — normal operation
            sendBroadcast(Intent(EVENT_NETWORK_CHANGED).apply {
                putExtra("state", "mobile_data")
            })
        }

        NetworkState.WIFI_NO_INTERNET, NetworkState.WIFI_WITH_INTERNET -> {
            // Switched to WiFi or lost internet on mobile — switch to LAN only
            // Disconnect relay but keep Minecraft server running
            relayManager.disconnect()
            sendBroadcast(Intent(EVENT_NETWORK_CHANGED).apply {
                putExtra("state", "lan_only")
                putExtra("message", "Switched to LAN only. Online players cannot connect.")
            })
        }
    }
}
```

### 3. Call startNetworkMonitoring after server is ready

Find where `EVENT_TUNNEL_CONNECTED` is broadcast. After that line add:

```kotlin
startNetworkMonitoring()
```

### 4. Stop network monitoring in onDestroy

```kotlin
override fun onDestroy() {
    super.onDestroy()
    networkJob?.cancel()
    relayManager.disconnect()
    stopServer() // ensure server process is killed
}
```

### 5. Add broadcast event constants

Find where existing event constants are defined (EVENT_OUTPUT, EVENT_STOPPED etc.) and add:

```kotlin
const val EVENT_NETWORK_LOST = "com.pocketcraft.server.NETWORK_LOST"
const val EVENT_NETWORK_CHANGED = "com.pocketcraft.server.NETWORK_CHANGED"
```

---

## Fix: Server Not Stopping When App Closes

### Problem
The Minecraft server JVM process keeps running after the app is closed because it's not being explicitly killed.

### Fix in ServerHostService.kt

Find where the Minecraft server process is launched (the `Process` object from `ProcessBuilder` or `Runtime.exec()`). Make sure it's stored as a field:

```kotlin
private var serverProcess: Process? = null
```

Find where the process is created and assign it:
```kotlin
serverProcess = processBuilder.start() // or however it's currently launched
```

Create a proper stop function:

```kotlin
fun stopServer() {
    try {
        // Send stop command to server stdin first (graceful shutdown)
        serverProcess?.outputStream?.let { stdin ->
            stdin.write("stop\n".toByteArray())
            stdin.flush()
        }

        // Wait up to 10 seconds for graceful shutdown
        val stopped = serverProcess?.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) ?: true

        // Force kill if it didn't stop gracefully
        if (!stopped) {
            serverProcess?.destroyForcibly()
        }

        serverProcess = null
    } catch (e: Exception) {
        serverProcess?.destroyForcibly()
        serverProcess = null
    }

    // Cancel network monitoring
    networkJob?.cancel()

    // Disconnect relay
    relayManager.disconnect()

    // Broadcast stopped event
    sendBroadcast(Intent(EVENT_STOPPED))
}
```

### Fix: Ensure service stops when app is closed

In `ServerHostService`, override `onTaskRemoved` — this fires when the user swipes the app away from recents:

```kotlin
override fun onTaskRemoved(rootIntent: Intent?) {
    super.onTaskRemoved(rootIntent)
    stopServer()
    stopSelf()
}
```

Also in `AndroidManifest.xml`, find the `ServerHostService` declaration and add:

```xml
<service
    android:name=".server.ServerHostService"
    android:stopWithTask="true"
    android:exported="false" />
```

`stopWithTask="true"` tells Android to stop the service when the app task is removed.

### Fix: Register shutdown hook for JVM process

In `ServerLauncher.kt` or wherever the JVM is launched, add a shutdown hook:

```kotlin
Runtime.getRuntime().addShutdownHook(Thread {
    serverProcess?.destroyForcibly()
})
```

---

## Modify Home Screen UI

### 1. Check network before starting server

Find the Start button's onClick handler. Replace with:

```kotlin
Button(
    onClick = {
        val networkStatus = NetworkMonitor.getNetworkStatus(context)
        when {
            !networkStatus.canStartServer -> {
                showNoConnectionDialog = true
            }
            networkStatus.isLanOnly -> {
                showLanOnlyWarningDialog = true
                // After user acknowledges, start in LAN only mode
            }
            else -> {
                startServer()
            }
        }
    }
)
```

### 2. Add state variables to home screen

```kotlin
var showNoConnectionDialog by remember { mutableStateOf(false) }
var showLanOnlyWarningDialog by remember { mutableStateOf(false) }
var networkStatus by remember { mutableStateOf(NetworkMonitor.getNetworkStatus(context)) }
var isLanOnlyMode by remember { mutableStateOf(false) }
var networkWarningBanner by remember { mutableStateOf<String?>(null) }

// Observe network changes
LaunchedEffect(Unit) {
    NetworkMonitor.observeNetworkStatus(context).collect { status ->
        networkStatus = status
    }
}
```

### 3. No Connection Dialog

```kotlin
if (showNoConnectionDialog) {
    AlertDialog(
        onDismissRequest = { showNoConnectionDialog = false },
        containerColor = Color(0xFF1A1A1A),
        title = {
            Text("No Internet Connection", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "PocketCraft requires an internet connection or WiFi to start a server. " +
                "Please connect to the internet or WiFi and try again.",
                color = Color(0xFFB3B3B3)
            )
        },
        confirmButton = {
            TextButton(onClick = { showNoConnectionDialog = false }) {
                Text("OK", color = Color(0xFF57F287))
            }
        }
    )
}
```

### 4. LAN Only Warning Dialog

```kotlin
if (showLanOnlyWarningDialog) {
    AlertDialog(
        onDismissRequest = { showLanOnlyWarningDialog = false },
        containerColor = Color(0xFF1A1A1A),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    tint = Color(0xFFFEE75C),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("WiFi Only — LAN Mode", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Text(
                "You are connected to WiFi without internet access. " +
                "Your server will start in LAN mode — only players on the same WiFi network can join. " +
                "Online players cannot connect.",
                color = Color(0xFFB3B3B3)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                showLanOnlyWarningDialog = false
                startServer(lanOnly = true)
            }) {
                Text("Start LAN Server", color = Color(0xFF57F287), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { showLanOnlyWarningDialog = false }) {
                Text("Cancel", color = Color(0xFFB3B3B3))
            }
        }
    )
}
```

### 5. Network warning banner (shows while server is running)

Show this banner below the server status card when network state changes:

```kotlin
// Banner shown when switched to LAN only mid-session
networkWarningBanner?.let { message ->
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1F00)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFFEE75C)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.WifiOff,
                contentDescription = null,
                tint = Color(0xFFFEE75C),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                fontSize = 13.sp,
                color = Color(0xFFFEE75C),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { networkWarningBanner = null },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color(0xFFFEE75C),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
```

### 6. Hide relay address when in LAN only mode

Find where the public address is displayed (e.g. `mine.pocketcraft.online:25501`). Wrap it:

```kotlin
if (!isLanOnly) {
    // Public relay address row
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Public, contentDescription = null,
             tint = Color(0xFF57F287), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = publicAddress,
            fontSize = 14.sp,
            color = Color(0xFFB3B3B3),
            fontFamily = FontFamily.Monospace
        )
        // copy button
    }
} else {
    // LAN only notice
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Wifi, contentDescription = null,
             tint = Color(0xFFFEE75C), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = "LAN only — online players cannot connect",
            fontSize = 12.sp,
            color = Color(0xFFFEE75C)
        )
    }
}
```

### 7. Listen for network broadcasts from service

In the home screen, add a broadcast receiver for network events:

```kotlin
DisposableEffect(Unit) {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ServerHostService.EVENT_NETWORK_LOST -> {
                    val message = intent.getStringExtra("message") ?: "Connection lost"
                    // Server was stopped by service — update UI
                    isServerRunning = false
                    networkWarningBanner = message
                }
                ServerHostService.EVENT_NETWORK_CHANGED -> {
                    val state = intent.getStringExtra("state")
                    if (state == "lan_only") {
                        isLanOnly = true
                        networkWarningBanner = "Connection lost — switched to LAN only. Online players disconnected."
                    } else if (state == "mobile_data") {
                        isLanOnly = false
                        networkWarningBanner = null
                    }
                }
            }
        }
    }

    val filter = IntentFilter().apply {
        addAction(ServerHostService.EVENT_NETWORK_LOST)
        addAction(ServerHostService.EVENT_NETWORK_CHANGED)
    }

    context.registerReceiver(receiver, filter)
    onDispose { context.unregisterReceiver(receiver) }
}
```

---

## Manifest Permissions

Verify these are in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
```

---

## Behavior Summary Table

| Network State | Before start | While running |
|---|---|---|
| No connection | Block + show dialog | Stop server immediately |
| WiFi no internet | Warn + offer LAN mode | Keep running LAN only, hide relay address, show banner |
| WiFi with internet | Warn + offer LAN mode | Keep running LAN only, hide relay address, show banner |
| Mobile data | Start normally | If lost → stop server. If switches to WiFi → LAN only mode |
| Mobile data → WiFi | — | Switch to LAN only, disconnect relay, show banner |
| Mobile data → no connection | — | Stop server, notify user |

---

## Quality Checklist

Before finishing, verify:

- [ ] `NetworkMonitor.kt` exists with correct package name
- [ ] `NetworkState` enum has all 4 states
- [ ] `NetworkStatus.canStartServer` returns false only for NO_CONNECTION
- [ ] `NetworkStatus.isLanOnly` returns true for both WiFi states
- [ ] Start button checks network before starting
- [ ] No connection dialog shows when no network at all
- [ ] LAN only warning dialog shows when on WiFi
- [ ] LAN only dialog has "Start LAN Server" and "Cancel" options
- [ ] Public relay address hidden when in LAN only mode
- [ ] LAN only yellow banner shown instead of relay address
- [ ] Network monitoring starts after server is ready
- [ ] Mobile data lost while running → server stops immediately
- [ ] WiFi (any) while running → relay disconnected, LAN only mode
- [ ] Network warning banner appears on home screen when state changes
- [ ] Banner is dismissible with X button
- [ ] `EVENT_NETWORK_LOST` and `EVENT_NETWORK_CHANGED` broadcasts defined
- [ ] Home screen listens for network broadcast events
- [ ] `serverProcess` stored as field in ServerHostService
- [ ] `stopServer()` sends `stop` command to stdin first
- [ ] `stopServer()` force kills after 10 second timeout
- [ ] `onTaskRemoved()` calls `stopServer()` and `stopSelf()`
- [ ] `stopWithTask="true"` added to service manifest declaration
- [ ] Shutdown hook added to kill process on JVM exit
- [ ] `networkJob` cancelled in `onDestroy()`
- [ ] `relayManager.disconnect()` called in `onDestroy()`
- [ ] App builds without errors

---

## What This Does NOT Change

- Relay system logic beyond disconnecting on WiFi — unchanged
- Firebase analytics — unchanged
- Plugin manager — unchanged
- World import — unchanged
- Google Drive backup — unchanged
- Theme and colors — unchanged
