# Relay Server Diagnostic Guide

## Current Status
- **App Code**: ✅ 100% Complete and Working
- **App-Server Connection**: ❌ Timeout after 10 seconds
- **Relay Process**: Shows "online" in pm2 but not accepting connections

## Error Details
```
failed to connect to play.pocketcraft.online/16.171.154.34 (port 8080)
from /10.178.14.194 (port 44076)
after 10000ms
```

Translation: Phone (10.178.14.194) cannot reach relay server (16.171.154.34:8080)

---

## Step 1: Verify Relay Listening Address

SSH into your Ubuntu EC2 instance and run:

```bash
sudo ss -tuln | grep 8080
```

**Expected output:**
```
tcp  LISTEN 0  128  0.0.0.0:8080  0.0.0.0:*
```

**Problem Indicators:**
- Shows `127.0.0.1:8080` instead of `0.0.0.0:8080` → Node.js app configured to listen locally only
- No output → Node.js app not listening on port 8080
- Different port → Check relay app configuration

If the issue is `127.0.0.1:8080`, the relay Node.js app needs to be configured to bind to `0.0.0.0` or `::`. Check the relay app code for:
```javascript
server.listen(8080, '0.0.0.0')  // Should be this
// NOT this:
server.listen(8080, 'localhost')  // or '127.0.0.1'
```

---

## Step 2: Check AWS Security Group

In AWS Console:
1. Go to EC2 → Security Groups
2. Find the security group attached to your relay server instance
3. Click "Inbound Rules"
4. Verify port 8080 is open to traffic from your phone's IP or from all IPs

**Required rule:**
```
Protocol: TCP
Port: 8080
Source: 0.0.0.0/0 (or your phone's WiFi network range)
```

If missing, add it:
```bash
# Or via AWS CLI:
aws ec2 authorize-security-group-ingress \
  --group-id sg-xxxxxxxx \
  --protocol tcp \
  --port 8080 \
  --cidr 0.0.0.0/0
```

Also verify port 9000 (tunnel socket):
```bash
sudo ss -tuln | grep 9000
```

---

## Step 3: Check Relay App Logs

```bash
pm2 logs relay
```

Look for:
- `Control API running on` → Expected startup message
- `error` / `Error` → Application errors
- `EADDRINUSE` → Port already in use
- `EADDRNOTAVAIL` → Address not available (wrong bind address)

If relaunching frequently (restarts), check the exit code:
```bash
pm2 show relay
```

---

## Step 4: Test Direct Connectivity from Phone

Run on your Android device:

```bash
adb shell <<EOF
# Test port 8080 (control API)
timeout 5 bash -c 'echo > /dev/tcp/16.171.154.34/8080' && echo "✓ Port 8080 OPEN" || echo "✗ Port 8080 CLOSED"

# Test port 9000 (tunnel socket)
timeout 5 bash -c 'echo > /dev/tcp/16.171.154.34/9000' && echo "✓ Port 9000 OPEN" || echo "✗ Port 9000 CLOSED"

# Test DNS resolution
getprop net.resolv1 | head -3
nslookup play.pocketcraft.online
EOF
```

**Expected results:**
- Both ports should show OPEN
- DNS should resolve `play.pocketcraft.online` to `16.171.154.34`

If ports show CLOSED → Security group issue or binding problem (see Step 1-2)
If DNS fails → Issue with domain configuration

---

## Step 5: Test from Ubuntu Server (Local Loopback Test)

```bash
# From the relay server itself
nc -zv 127.0.0.1 8080
telnet 127.0.0.1 8080

# Should succeed immediately if running
# Should timeout/fail if not listening
```

Then test from a different machine:
```bash
# From any external machine
nc -zv 16.171.154.34 8080
```

---

## Step 6: Firewall Check (UFW or iptables)

```bash
# Check UFW status
sudo ufw status

# Should show:
# 8080/tcp    ALLOW

# If not, add it:
sudo ufw allow 8080/tcp
sudo ufw allow 9000/tcp
```

Or with iptables:
```bash
sudo iptables -L -n | grep 8080
```

---

## Step 7: Check if Proxy/Reverse Proxy Issue

If running nginx/Caddy in front of Node.js:

```bash
# Check if reverse proxy is running
sudo systemctl status nginx
sudo systemctl status caddy

# Check proxy config
cat /etc/nginx/sites-enabled/default
cat /etc/caddy/Caddyfile
```

Ensure it's forwarding port 8080 correctly to the Node.js backend.

---

## Most Likely Culprits (In Order)

1. **Node.js app listening on 127.0.0.1 instead of 0.0.0.0**
   - Fix: Verify app code binds to `0.0.0.0`

2. **AWS Security Group not allowing inbound 8080**
   - Fix: Add inbound rule for port 8080

3. **UFW or iptables blocking port 8080**
   - Fix: `sudo ufw allow 8080/tcp`

4. **Node.js app not running or crashed**
   - Fix: Check `pm2 logs relay` and `pm2 show relay`

5. **Reverse proxy misconfiguration**
   - Fix: Verify nginx/Caddy forwarding rules if applicable

---

## Quick Diagnostic Script

Run this on your Ubuntu server:

```bash
#!/bin/bash
echo "=== Relay Diagnostics ==="
echo ""
echo "1. Port Binding:"
sudo ss -tuln | grep -E '8080|9000' || echo "Ports not listening!"
echo ""
echo "2. PM2 Status:"
pm2 status
echo ""
echo "3. Recent Logs:"
pm2 logs relay --lines 20
echo ""
echo "4. Firewall UFW:"
sudo ufw status | grep -E '8080|9000' || echo "UFW may not be configured"
echo ""
echo "5. Test local connection:"
nc -zv 127.0.0.1 8080 2>&1 || echo "Connection failed"
echo ""
echo "Done"
```

---

## After Fixing

Once you've verified ports are open and listening on `0.0.0.0`:

1. Clear app cache:
   ```bash
   adb shell pm clear com.pocketcraft.server
   ```

2. Rebuild and reinstall app:
   ```bash
   ./gradlew installDebug
   ```

3. Run test again with fresh logs:
   ```bash
   adb logcat -c
   adb logcat | grep -i relay
   # Then start server in app
   ```

4. Check app logs for successful connection:
   - "Register response code: 200"
   - "Register success: play.pocketcraft.online:25500" (or similar)
