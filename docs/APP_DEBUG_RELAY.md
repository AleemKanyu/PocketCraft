# App-Level Relay Debugging (Deep Dive)

## Problem Statement
- ✅ Relay server works (you confirmed)
- ❌ App shows "Pinging" when trying relay address, then times out
- ✅ LAN address (192.168.x.x:25565) works fine

## Critical Question: Does Relay Server Have `/phone-ready` Endpoint?

The app architecture now requires:
1. `POST /register` → Returns `{"ip":"play.pocketcraft.online","port":25500}`
2. `POST /phone-ready` → App tells relay "my local IP is X and port is 25565"
3. `Port 9000` → Relay forwards player traffic through persistent sockets to this port

**IF YOUR RELAY SERVER DOESN'T HAVE `/phone-ready` ENDPOINT:**
- Registration succeeds ✅
- Relay returns the port ✅
- But relay never learns WHERE the local server is ❌
- Relay can't forward traffic to phone ❌
- Result: Players get "Pinging" then disconnect ❌

---

## Quick Test #1: Check What Endpoints Your Relay Actually Implements

SSH to AWS relay and:

```bash
# Method 1: Check what Node.js files define handlers
grep -r "register\|phone-ready\|unregister" ~/relay --include="*.js"

# Method 2: Look at the main server file
cat ~/relay/index.js | grep -A5 "app.post\|router.post"

# Method 3: Check if app is running AND which version
pm2 show relay

# Method 4: Test endpoints directly from relay server
curl -X POST http://localhost:8080/register \
  -H "Content-Type: application/json" \
  -d '{"userId":"test-uuid"}'

curl -X POST http://localhost:8080/phone-ready \
  -H "Content-Type: application/json" \
  -d '{"userId":"test-uuid","host":"127.0.0.1","port":25565}'
```

**What to look for:**
- `/register` endpoint: MUST exist and return port assignment
- `/phone-ready` endpoint: NEW endpoint - may NOT exist yet!
- Both should return HTTP 200 on success

---

## Quick Test #2: Capture App Logs (Real-time)

On your development machine:

```bash
# Terminal 1: Watch logs
adb logcat -c
adb logcat | grep -E "RelayManager|ServerHost|Relay|Register|phone-ready"

# Terminal 2: Start server
# Start the PocketCraft app and launch server

# Terminal 3: Try to connect from Minecraft
# Wait 10 seconds then try connecting from Minecraft to: play.pocketcraft.online:25500
```

**SUCCESS logs should look like:**
```
D/RelayManager: Registering user: f47ac10b-58cc-4a36-0000-000000000000
D/RelayManager: Register request: {"userId":"f47ac10b-58cc-4a36-0000-000000000000"}
D/RelayManager: Register response code: 200
D/RelayManager: Register response: {"ip":"play.pocketcraft.online","port":25500}
D/RelayManager: Register success: play.pocketcraft.online:25500
D/RelayManager: Notifying relay phone-ready (userId=..., relay=play.pocketcraft.online, local=192.168.x.x:25565)
D/RelayManager: phone-ready notification result: HTTP 200
I/RelayManager: Starting pool of 25 sockets...
V/RelayManager: Socket added to pool. Size: 1
V/RelayManager: Socket added to pool. Size: 2
... (more sockets)
```

**PROBLEM logs might look like:**
```
D/RelayManager: Register response code: 500  ← Server error
W/RelayManager: Failed to notify phone-ready: Connection refused  ← /phone-ready doesn't exist!
E/RelayManager: Pool socket error: Connection refused ← Port 9000 not listening
```

---

## Most Likely App Issues

### Issue A: `/phone-ready` Endpoint Missing on Relay (Most Likely!)

**Symptom:**
```
W/RelayManager: Failed to notify phone-ready: Connection refused (or timeout)
```

**Why it breaks:**
- App registers successfully and gets port assignment
- But relay doesn't know the phone's local IP/port
- Relay can't forward incoming player traffic correctly
- Players timeout

**Solution:**
- Relay server MUST implement `/phone-ready` endpoint
- It should store the mapping: userId → {host, port}
- When relay forwards, it uses this info

**Fix:** Check if your relay server has this endpoint. If not, you need to add it.

---

### Issue B: Socket Pool Not Connecting (Likely!)

**Symptom:**
```
I/RelayManager: Starting pool of 25 sockets...
(No "Socket added to pool" messages follow)
E/RelayManager: Pool socket error: Connection refused
```

**Why it breaks:**
- App tries to connect to relay:9000
- Connection fails or hangs
- No sockets in pool to forward player traffic
- Players timeout

**Solution:**
- Check relay server is listening on port 9000
- `sudo ss -tuln | grep 9000` should show listening
- Verify no firewall blocking

---

### Issue C: DNS Resolution Failing

**Symptom:**
```
D/RelayManager: Registering user: ...
E/RelayManager: Pool socket error: UnknownHostException: play.pocketcraft.online
```

**Why it breaks:**
- Phone can't resolve domain name to IP
- All connections fail to "play.pocketcraft.online"

**Solution:**
- Test DNS on phone: `adb shell nslookup play.pocketcraft.online`
- Should return: 16.171.154.34 (your relay IP)
- If fails, check Route53 DNS configuration

---

### Issue D: Port Assignment Not Persisting

**Symptom:**
- Different port each time app registers
- Or port assignment is 0 in response

**Why it breaks:**
- Relay returns different port each time
- Player connects to port 25500, but server is on 25501
- Connection fails

**Solution:**
- Relay must return same port for same userId
- Store mapping: userId → port
- Return same port on subsequent registrations

---

## What to Collect and Send

Once you run the logs above, collect:

1. **Full logcat output** (save all output)
   ```bash
   adb logcat > logcat_full.txt
   ```

2. **Relay server logs**
   ```bash
   ssh ubuntu@16.171.154.34
   pm2 logs relay --lines 100 > relay_logs.txt
   ```

3. **Relay endpoint test results**
   ```bash
   # From relay server:
   curl -X POST http://localhost:8080/register ...
   curl -X POST http://localhost:8080/phone-ready ...
   ```

4. **Your relay server code**
   ```bash
   cat ~/relay/index.js
   ```

---

## The Most Likely Root Cause

Looking at this architecture:
- App calls `/register` → works (you'd see timeout if not)
- App calls `/phone-ready` → SILENT FAILURE (caught and logged as warning)
- Relay forwards traffic → doesn't work because relay doesn't know where phone server is

**Diagnosis:**
1. Check RelayManager logs for: `Failed to notify phone-ready`
2. If found, relay server missing `/phone-ready` endpoint
3. If not found, check socket pool creation logs

---

## Next Steps

Please run these commands and collect output:

```bash
# On your dev machine - watch logs LIVE
adb logcat -c
adb logcat | tee app_logs_$(date +%s).txt | grep -E "Relay|Register|phone-ready"

# In parallel terminal - start server in app

# In another terminal - when ready, try Minecraft connection
```

Then share:
1. The app logs (full relay section)
2. Output of: `curl -X POST http://localhost:8080/phone-ready ...` from relay
3. Relay server's main code file
