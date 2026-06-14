# BUGFIX_AGENT

## Overview
Fix multiple bugs identified from server logs and user reports. Work through each issue in order — they are independent and can be fixed separately.

---

## Bug 1 — App Crashes After Some Time (Android Kills Service)

### Symptom
Server runs fine then suddenly stops. App process is killed by Android OS.

### Root Cause
- Missing WakeLock — CPU throttles during server run
- Battery optimization not exempted
- Service `stopWithTask` may not be set correctly
- `IMPORTANCE_LOW` notification required to keep service alive

### Fix in `ServerHostService.kt`

**1. Acquire WakeLock on service start, release on stop:**
```kotlin
private lateinit var wakeLock: PowerManager.WakeLock

override fun onCreate() {
    super.onCreate()
    val powerManager = getSystemService(POWER_SERVICE) as PowerManager
    wakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "PocketCraft::ServerWakeLock"
    )
    wakeLock.acquire() // no timeout — released manually on stop
}

override fun onDestroy() {
    if (::wakeLock.isInitialized && wakeLock.isHeld) {
        wakeLock.release()
    }
    super.onDestroy()
}
```

**2. In `AndroidManifest.xml`, ensure:**
```xml
<uses-permission android:name="android.permission.WAKE_LOCK" />

<service
    android:name=".ServerHostService"
    android:stopWithTask="true"
    android:foregroundServiceType="dataSync|specialUse" />
```

**3. Request battery optimization exemption at runtime (in onboarding or settings):**
```kotlin
val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
    data = Uri.parse("package:${packageName}")
}
startActivity(intent)
```

**4. Ensure foreground notification uses `IMPORTANCE_LOW`:**
```kotlin
val channel = NotificationChannel(
    CHANNEL_ID,
    "Server Running",
    NotificationManager.IMPORTANCE_LOW
)
```

---

## Bug 2 — Settings Not Persistent After App Restart

### Symptom
RAM allocation, server name, and other settings reset to defaults when app is restarted.

### Root Cause
`AppPreferences` is likely reading defaults correctly but writes are not being committed, or the wrong `SharedPreferences` instance is being used (e.g. a new instance created each time instead of a singleton).

### Fix in `AppPreferences.kt`

**Ensure singleton pattern:**
```kotlin
object AppPreferences {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(
            "pocketcraft_prefs",
            Context.MODE_PRIVATE
        )
    }

    var ramMode: String
        get() = prefs.getString("ram_mode", "low") ?: "low"
        set(value) = prefs.edit().putString("ram_mode", value).apply()

    var customServerName: String
        get() = prefs.getString("server_name", "Minecraft Server") ?: "Minecraft Server"
        set(value) = prefs.edit().putString("server_name", value).apply()

    var viewDistance: Int
        get() = prefs.getInt("view_distance", 6)
        set(value) = prefs.edit().putInt("view_distance", value).apply()

    // Add all other settings following the same pattern
}
```

**Call `AppPreferences.init(this)` in `Application.onCreate()`**, not in Activity or Service.

**Use `.apply()` not `.commit()`** — `.commit()` on the main thread causes ANR; `.apply()` is async and sufficient.

---

## Bug 3 — Relay Connects Too Late (Players Can't Join Immediately)

### Symptom
From logs: relay registration happens at `[06:37:30]` but server `Done` is also at `[06:37:30]` — the relay only starts after full server boot. Total startup: ~206 seconds. Players attempting to connect during boot get nothing.

### Root Cause
Relay initialization is triggered by the server `Done (Xs)! For help, type "help"` log string detection. This is correct behavior, but the relay pool init (5 sockets) adds additional delay on top.

### Fix in `RelayManager.kt`

**Start relay registration earlier — as soon as port 25565 is open, not after `Done`.**

The log shows:
```
[PocketCraft] Server port 25565 is open. Finalizing startup...
```
This fires ~60-90 seconds before `Done`. Trigger relay registration here instead.

```kotlin
// In your log-watching code, add an earlier trigger:
if (line.contains("Server port 25565 is open")) {
    // Start relay registration immediately
    // Pool init can happen in parallel — server will finish booting
    relayManager.startRegistration()
}
```

**Also reduce pool size from 5 to 3 for faster initial readiness**, then replenish to 5 after first player connects:
```kotlin
const val INITIAL_POOL_SIZE = 3   // ready faster
const val TARGET_POOL_SIZE = 5    // after first connection
```

---

## Bug 4 — RAM Not Fully Allocated Even When Set to "Full"

### Symptom
User reports only ~3GB allocated even when "Full" (90%) mode is selected.

### Root Cause
In `launcher.c` around line 343, the RAM args are computed but likely use integer division that truncates, or the `ActivityManager.getMemoryInfo()` call returns total RAM but the JVM cap is hardcoded or capped elsewhere.

### Fix in `ServerLauncher.kt` (where RAM args are built before passing to `launcher.c`)

**Compute available RAM correctly:**
```kotlin
fun getRamArgs(mode: String): Pair<String, String> {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)

    val totalRamMb = (memInfo.totalMem / 1024 / 1024).toInt()

    return when (mode) {
        "low"  -> Pair("-Xms256m", "-Xmx512m")
        "full" -> {
            val allocMb = (totalRamMb * 0.90).toInt()
            Pair("-Xms${allocMb}m", "-Xmx${allocMb}m")
        }
        "manual" -> {
            val manualMb = AppPreferences.manualRamMb
            Pair("-Xms${manualMb}m", "-Xmx${manualMb}m")
        }
        else -> Pair("-Xms256m", "-Xmx512m")
    }
}
```

**Also add G1GC flags to prevent GC-related crashes during long sessions:**
```
-XX:+UseG1GC
-XX:+ParallelRefProcEnabled
-XX:MaxGCPauseMillis=200
-XX:+UnlockExperimentalVMOptions
-XX:+DisableExplicitGC
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=40
-XX:G1HeapRegionSize=8M
-XX:G1ReservePercent=20
-XX:G1HeapWastePercent=5
-XX:G1MixedGCCountTarget=4
-XX:InitiatingHeapOccupancyPercent=15
-XX:G1MixedGCLiveThresholdPercent=90
-XX:G1RSetUpdatingPauseTimePercent=5
-XX:SurvivorRatio=32
-XX:+PerfDisableSharedMem
-XX:MaxTenuringThreshold=1
-Dio.netty.allocator.maxOrder=9
-Dusing.aikars.flags=https://mcflags.emc.gs
```

---

## Bug 5 — Geyser Encryption Timeout Adds ~14s to Every Boot

### Symptom
```
[Geyser-Spigot] Unable to set up encryption!
java.net.SocketTimeoutException: Read timed out
```
Geyser tries to reach `authorization.franchise.minecraft-services.net` on every startup and times out after ~14 seconds. This is unavoidable if the device has no unrestricted internet at that moment, but the timeout blocks the startup thread.

### Fix
In `Geyser-Spigot/config.yml` (inside the server files), set:
```yaml
remote:
  auth-type: floodgate
```
And ensure `floodgate-key.pem` is present. With Floodgate handling auth, Geyser skips the Microsoft auth endpoint entirely.

If Geyser is not needed at all for your current beta (Java-only players), **remove Geyser-Spigot.jar and floodgate.jar from plugins** — this alone will cut ~40 seconds from startup time.

---

## Bug 6 — tellraw JSON Syntax Error on Player Join

### Symptom
```
Expected literal ,
...GPzXFYp"}}{"text":"\n \n","color":"white"}]<--[HERE]
```

### Fix
Find the file that builds the `tellraw` command (search for `NGPzXFYp` or `tellraw`):
```bash
grep -r "NGPzXFYp\|tellraw" --include="*.kt" -l
```

Add the missing comma between the last `clickEvent` object and the trailing newline text component:

**Wrong:**
```
...{"action":"open_url","value":"https://discord.gg/NGPzXFYp"}}{"text":"\n \n"...
```

**Fixed:**
```
...{"action":"open_url","value":"https://discord.gg/NGPzXFYp"}},{"text":"\n \n"...
```

---

## Bug 7 — Paper Config Has Unknown Entity Keys (Spammy Errors)

### Symptom
```
[MapSerializer] Could not deserialize key experience_ball
[MapSerializer] Could not deserialize key thrown_exp_bottle
```
These fire multiple times on every boot.

### Fix
Open `config/paper-world-defaults.yml` (inside the server folder) and find `entity-per-chunk-save-limit`. Remove or rename these two keys:
```yaml
# Remove these two lines:
experience_ball: 8
thrown_exp_bottle: 8

# They are now named correctly in 1.21.x as:
experience_orb: 8
experience_bottle: 8
```

---

## Acceptance Criteria
- [ ] Server runs for 30+ minutes without being killed by Android
- [ ] RAM allocation matches selected mode after restart
- [ ] Settings persist across app restarts
- [ ] Relay address is available within 30 seconds of server port opening
- [ ] No `Expected literal ,` error on player join
- [ ] No `experience_ball` deserialization errors on boot
- [ ] Startup time reduced (target under 120s)
