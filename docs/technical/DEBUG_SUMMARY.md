# App Relay Debug Summary (March 26, 2026)

## Issue: App Can't Connect Through Relay (Shows "Pinging" Then Timeout)

### Root Cause Identified
**Critical Issue in `ServerAddressResolver.kt`:**

When the app tries to notify the relay about the local server address, it uses:
```kotlin
val localIp = ServerAddressResolver.getLocalIpAddress() ?: "127.0.0.1"
```

**If address resolution FAILS and returns NULL:**
- App tells relay: `{"host":"127.0.0.1","port":25565}`
- Relay tries to forward to localhost (127.0.0.1)
- But the Minecraft server is on the PHONE, not the relay server
- ❌ Connection fails

### What I've Done

1. ✅ Added comprehensive logging to `ServerAddressResolver.kt`
   - Now logs every network interface found
   - Shows which addresses are site-local vs fallback
   - Shows if address resolution fails

2. ✅ Added critical error logging to `RelayManager.kt`
   - Shows when getLocalIpAddress() returns NULL
   - Shows the IP that will be sent to relay
   - Shows socket connection progress

3. ✅ Building app with new debug logs
   - Current progress: Building...

---

## What To Do Next (After Build Completes)

### Step 1: Clear App Cache
```bash
adb shell pm clear com.pocketcraft.server
```

### Step 2: Reinstall App
Wait for build to complete, then:
```bash
./gradlew installDebug
```

### Step 3: Run Debug Test Script
```bash
chmod +x test_relay.sh
./test_relay.sh
```

This script will:
- Connect to your phone
- Filter logcat for relay-related messages
- Show you EXACTLY what's happening

### Step 4: Start Server in App
In the app, tap to start the Minecraft server. Watch the script output for:

**✅ GOOD logs:**
```
D/ServerAddressResolver: Final: 192.168.1.100 (site-local=1)
D/RelayManager: ✓ Resolved local IP: 192.168.1.100
D/RelayManager: phone-ready notification result: HTTP 200
D/RelayManager: ✓ Socket connected to play.pocketcraft.online:9000
D/RelayManager: ✓ Socket added to pool. Pool size: 1/25
D/RelayManager: ✓ Socket added to pool. Pool size: 2/25
... (more sockets being added)
```

**❌ BAD logs (these indicate the problem):**
```
D/ServerAddressResolver: Final: null (site-local=0, fallback=0)
E/RelayManager: ⚠️ CRITICAL: getLocalIpAddress() returned NULL! Using fallback 127.0.0.1
```

---

### Step 5: Try Minecraft Connection
Once server is running with pool sockets being added:
- Open Minecraft
- Add server: play.pocketcraft.online:25500
- Ping it
- Try to join

**Watch logs for:**
```
D/RelayManager: Player incoming! Bridging to localhost:25565
I/RelayManager: Bridge ACTIVE: Relay <-> Local:25565
```

---

## Expected Outcomes

### If Address Resolution Works (Most Likely)
```
✓ Local IP resolved correctly: 192.168.1.x
✓ Socket pool fills to 25 sockets
✓ Player can ping and join successfully!
```

### If Address Resolution Fails (Less Likely)
```
⚠️ CRITICAL: getLocalIpAddress() returned NULL!
→ Problem: Phone network interfaces not being enumerated correctly
→ Fix: May need to investigate Android network permission or device-specific issue
```

---

## Build Status

Current build progress: **IN PROGRESS**

Location: `/tmp/claude-1000/-home-aleemkanyu--gemini-antigravity-scratch-PocketCraft/tasks/bv90t1cih.output`

Check progress with:
```bash
tail -f /tmp/claude-1000/-home-aleemkanyu--gemini-antigravity-scratch-PocketCraft/tasks/bv90t1cih.output | tail -20
```

Or:
```bash
# Simple output
cat /tmp/claude-1000/-home-aleemkanyu--gemini-antigravity-scratch-PocketCraft/tasks/bv90t1cih.output
```

Once build finishes, you'll see:
```
BUILD SUCCESSFUL in XXs
```

---

## Files Changed

1. **ServerAddressResolver.kt**
   - Added detailed logging for network interface enumeration
   - Shows why address resolution might fail
   - Shows final selected IP

2. **RelayManager.kt**
   - Added critical error logging for NULL address
   - Added socket connection progress logging
   - Shows pool size as sockets are added

3. **test_relay.sh** (NEW)
   - Executable script to capture and filter logs
   - Makes it easy to see relay-related messages
   - Color-coded for easy reading

---

## Timeline

- ⏱️ Build: ~2-5 minutes
- 🔄 Install: ~30 seconds
- 🧪 Test: ~2 minutes (start server + try connection)
- 💾 Logging: Automatic from app

---

## Questions We're Answering

1. **Is the phone able to resolve its own LAN IP?**
   → Check `ServerAddressResolver` logs

2. **Is that IP being sent to the relay correctly?**
   → Check `RelayManager` logs

3. **Can the socket pool connect to relay:9000?**
   → Check socket connection logs

4. **Does the relay acknowledge phone-ready?**
   → Check HTTP response code

5. **Can players bridge through the relay?**
   → Check "Player incoming" and "Bridge ACTIVE" logs

---

## Timeline: When Will Build Finish?

Expected: **5-10 minutes total**
- Gradle overhead: 1 min
- Compilation: 3-5 min
- Packaging APK: 1 min
- Installing: 1 min

Check back here after ~5 minutes!
