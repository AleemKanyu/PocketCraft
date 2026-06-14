# Relay Connection Issue - Complete Diagnostic & Fix

## Problem Summary
**Symptom**: Minecraft client shows "Pinging" then times out when trying to connect to the relay address
**Root Cause**: Your relay server on AWS is either:
1. Not listening on port 8080 at all
2. Listening on 127.0.0.1:8080 (localhost only) instead of 0.0.0.0:8080 (all interfaces)
3. Port 8080 blocked by AWS security group or UFW firewall

---

## STEP 1: SSH into Your AWS Relay Server

```bash
ssh -i your-key.pem ubuntu@16.171.154.34
```

Then proceed with the diagnostics below.

---

## STEP 2: Check What's Actually Listening (CRITICAL)

```bash
# Check if anything is listening on port 8080
sudo ss -tuln | grep 8080

# Expected output (GOOD):
# tcp  LISTEN 0  128  0.0.0.0:8080  0.0.0.0:*

# Problem indicators:
# tcp  LISTEN 0  128  127.0.0.1:8080  0.0.0.0:*  <- BINDING TO LOCALHOST ONLY!
# (no output at all)  <- PORT NOT LISTENING!
```

### If you see `127.0.0.1:8080` (localhost only):
**This is 90% likely your issue.** Your Node.js relay app is bound to localhost and cannot accept external connections.

---

## STEP 3: Check Relay Server Status & Logs

```bash
# Check if relay is running
pm2 status

# View recent logs (last 50 lines)
pm2 logs relay --lines 50

# Check if app is crashing or restarting
pm2 show relay
```

**Look for in the logs:**
- `Control API running on 0.0.0.0:8080` ✅ (GOOD)
- `Control API running on 127.0.0.1:8080` ❌ (BAD - fix below)
- `EADDRINUSE: Address already in use` → something else using port 8080
- `EADDRNOTAVAIL: Cannot assign requested address` → binding problem

---

## STEP 4: Fix the Binding Issue (If Localhost-Only)

### Find Your Relay App Source Code
```bash
# Find where your relay app is located
find ~ -name "*.js" -type f 2>/dev/null | grep relay

# Usually it's in one of:
# ~/relay/index.js
# ~/relay/app.js
# ~/relay/server.js
# /opt/relay/index.js
```

### Check the Binding Line
```bash
# Search for the line where the server starts
grep -r "listen\|app.listen\|server.listen" ~/relay/ 2>/dev/null | head -5
```

### The Fix
Find the line that looks like:
```javascript
// WRONG - only accepts localhost connections:
app.listen(8080, 'localhost')
app.listen(8080, '127.0.0.1')

// CORRECT - accepts all external connections:
app.listen(8080, '0.0.0.0')
app.listen(8080)  // Defaults to 0.0.0.0 if no host specified
```

**Edit the file and change it:**
```bash
nano ~/relay/index.js  # or wherever your app file is

# Change the listen line to:
# app.listen(8080, '0.0.0.0')
# or just
# app.listen(8080)

# Save: Ctrl+O, Enter, Ctrl+X
```

### Restart the Relay
```bash
pm2 restart relay
pm2 logs relay --lines 20

# Verify it's now listening on 0.0.0.0:
sudo ss -tuln | grep 8080
# Should now show: tcp  LISTEN 0  128  0.0.0.0:8080
```

---

## STEP 5: Check AWS Security Group (If ports still blocked)

If ports show as listening on 0.0.0.0 but still can't connect from phone:

**Via AWS Console:**
1. Go to EC2 Dashboard
2. Find your relay instance
3. Click on "Security" tab
4. Click on the Security Group name
5. Click "Inbound Rules"
6. Verify you see:
   - **Type**: Custom TCP
   - **Port Range**: 8080
   - **Source**: 0.0.0.0/0 (or your phone's network)

**If missing, add the rule:**
```bash
# Replace sg-xxxxxxxx with your security group ID
aws ec2 authorize-security-group-ingress \
  --group-id sg-xxxxxxxx \
  --protocol tcp \
  --port 8080 \
  --cidr 0.0.0.0/0 \
  --region us-east-1  # Replace with your region
```

---

## STEP 6: Check UFW Firewall (If using)

```bash
# Check UFW status
sudo ufw status

# If enabled, verify ports are open:
sudo ufw status | grep -E '8080|9000'

# If not shown, add rules:
sudo ufw allow 8080/tcp
sudo ufw allow 9000/tcp

# Reload
sudo ufw reload
```

---

## STEP 7: Test Local Connectivity on Server

```bash
# Test local connection (should work immediately)
nc -zv 127.0.0.1 8080

# If this fails, relay app is NOT running:
ps aux | grep relay
pm2 status
```

---

## STEP 8: Test External Connectivity from Phone

Run this on your development machine (after adb devices shows your phone):

```bash
# Test if port 8080 is reachable from phone
adb shell bash -c 'timeout 5 bash -c "echo > /dev/tcp/16.171.154.34/8080" && echo "✓ Port 8080 OPEN" || echo "✗ Port 8080 CLOSED"'

# Test if port 9000 (tunnel) is also open
adb shell bash -c 'timeout 5 bash -c "echo > /dev/tcp/16.171.154.34/9000" && echo "✓ Port 9000 OPEN" || echo "✗ Port 9000 CLOSED"'

# Test DNS resolution
adb shell nslookup play.pocketcraft.online
# Should return: 16.171.154.34
```

---

## STEP 9: Clear App Cache & Reinstall (Final Step)

Once you've fixed the server:

```bash
# On development machine
adb shell pm clear com.pocketcraft.server
./gradlew installDebug
adb logcat -c

# Start server in app and watch logs:
adb logcat | grep -i relay
```

**Success logs should look like:**
```
D/RelayManager: Registering user: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
D/RelayManager: Register response code: 200
D/RelayManager: Register response: {"ip":"play.pocketcraft.online","port":25500}
D/RelayManager: Register success: play.pocketcraft.online:25500
I/RelayManager: Starting pool of 25 sockets...
```

---

## QUICK DIAGNOSTIC CHECKLIST

Run this on AWS server to diagnose all at once:

```bash
#!/bin/bash
echo "=== RELAY DIAGNOSTICS ==="
echo ""
echo "1. Is relay listening?"
sudo ss -tuln | grep -E '8080|9000' || echo "NOT LISTENING!"
echo ""
echo "2. Relay process status:"
pm2 status
echo ""
echo "3. Recent errors:"
pm2 logs relay --lines 10 | grep -i error
echo ""
echo "4. Latest startup message:"
pm2 logs relay --lines 20 | grep -i "listening\|started\|running"
echo ""
echo "5. UFW firewall:"
sudo ufw status | grep -E '8080|9000' || echo "UFW not configured for ports"
echo ""
echo "=== END DIAGNOSTICS ==="
```

---

## Most Likely Fixes (In Order)

1. **80% likely**: Node.js app listening on 127.0.0.1 → Change to 0.0.0.0 in app code
2. **15% likely**: AWS Security Group blocking port 8080 → Add inbound rule
3. **5% likely**: UFW firewall blocking → `sudo ufw allow 8080/tcp`

---

## If Still Stuck

Provide these details:
1. Output of: `sudo ss -tuln | grep 8080`
2. Output of: `pm2 logs relay --lines 20`
3. Your relay app's code (the line where it calls `.listen()`)
4. AWS security group inbound rules (screenshot)
