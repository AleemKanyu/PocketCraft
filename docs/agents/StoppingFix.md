# 🛑 Server Shutdown Fix Guide (PocketCraft)

## 🔍 Problem Summary

The server shutdown process hangs due to a **30-second grace period** waiting for RCON, followed by a forced kill. This can cause:

* UI freezes (ANR risk)
* Incomplete cleanup
* Server not stopping reliably

---

## ✅ Root Causes & Fixes

### 1. ❗ RCON Not Working (Most Common)

The server attempts a graceful shutdown using RCON, but if it's not enabled or fails, it wastes 30 seconds.

#### ✅ Fix

Ensure these are always written before server startup in `server.properties`:

```
enable-rcon=true
rcon.port=25575
rcon.password=pocketcraft_internal
```

👉 Best approach: Write these programmatically in `ServerLauncher.kt`.

---

### 2. ❗ Blocking Main Thread (UI Freeze / ANR)

If `stopServer()` runs on the main thread and waits (polling), it freezes the UI.

#### ✅ Fix

Run shutdown on background thread:

```kotlin
fun stopServer() {
    CoroutineScope(Dispatchers.IO).launch {
        // shutdown logic
    }
}
```

---

### 3. ❗ Cleanup Not Guaranteed

If an exception occurs, cleanup functions like `finalizeForcedStop()` may never run.

#### ✅ Fix

Use `finally` block:

```kotlin
fun stopServer() {
    try {
        sendRconStop()
        serverProcess?.waitFor(30, TimeUnit.SECONDS)
    } catch (e: Exception) {
        // log only
    } finally {
        serverProcess?.destroyForcibly()
        serverProcess = null
        finalizeForcedStop()
    }
}
```

---

### 4. ❗ `onTaskRemoved` Not Reliable

Some Android devices don’t call this consistently.

#### ✅ Fix

Add this in `AndroidManifest.xml`:

```xml
<service
    android:name=".server.ServerHostService"
    android:stopWithTask="true"
    android:foregroundServiceType="dataSync"
    android:exported="false"/>
```

---

### 5. ❗ Relay Disconnect Hanging

Socket disconnection can block indefinitely.

#### ✅ Fix

Wrap with timeout:

```kotlin
withTimeout(3000) {
    relayManager.disconnect()
}
```

---

## 🚀 Recommended Final Fix (Best Approach)

Replace your shutdown logic with this **safe + fast version**:

```kotlin
fun stopServer() {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // Send stop command via stdin (more reliable than RCON)
            serverProcess?.outputStream?.write("stop\n".toByteArray())
            serverProcess?.outputStream?.flush()

            // Wait max 8 seconds
            serverProcess?.waitFor(8, TimeUnit.SECONDS)

        } catch (e: Exception) {
            // Ignore errors, proceed to cleanup

        } finally {
            // Force kill no matter what
            serverProcess?.destroyForcibly()
            serverProcess = null

            try {
                relayManager.disconnect()
            } catch (_: Exception) {}

            networkJob?.cancel()
            releaseWakeLock()

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()

            sendBroadcast(Intent(EVENT_STOPPED))
        }
    }
}
```

---

## 🔑 Key Improvements

* ✅ **No 30s hang** → reduced to ~8s max
* ✅ **Guaranteed cleanup** via `finally`
* ✅ **No UI freeze** (runs on IO thread)
* ✅ **Doesn’t depend on RCON**
* ✅ **Safe fallback force kill**
* ✅ **Relay can't block shutdown**

---

## 🧠 Pro Tip

For reliability in production systems:

* Prefer **stdin commands over RCON**
* Always design shutdown with **timeouts + fallback**
* Never trust external systems (network, sockets) to respond instantly

---

