# Relay Server - Quick Start Fix

## Your Current Situation

✅ **App fully integrated and working perfectly**
- UUID generation: Working
- Relay registration code: Perfect
- Error logging: Comprehensive
- Broadcast to UI: Fixed

❌ **Network connection blocking progress**
- Phone timeouts when trying to reach relay on port 8080
- Relay shows as running but not accepting external connections

---

## Most Likely Issue: Binding to Wrong Address

**Symptom**: Node.js app listening on `127.0.0.1:8080` instead of `0.0.0.0:8080`

### Test It (30 seconds)

SSH into your Ubuntu relay server:

```bash
# Check what address relay is listening on
sudo ss -tuln | grep 8080

# Expected: tcp  LISTEN 0  128  0.0.0.0:8080
# Problem: tcp  LISTEN 0  128  127.0.0.1:8080
```

If you see `127.0.0.1:8080`, that's your problem - relay is only accepting local connections.

### Fix It

Your relay app (Node.js) needs to bind to `0.0.0.0` instead of `localhost` or `127.0.0.1`.

Find the line in your relay app where the server starts:

```javascript
// WRONG:
app.listen(8080, 'localhost')
app.listen(8080, '127.0.0.1')

// CORRECT:
app.listen(8080, '0.0.0.0')
app.listen(8080)  // Defaults to 0.0.0.0
```

After fixing:
```bash
pm2 restart relay
pm2 logs relay

# Verify port is now listening on 0.0.0.0:
sudo ss -tuln | grep 8080
```

---

## Second Most Likely: AWS Security Group

Test from phone:
```bash
adb shell
timeout 5 bash -c 'echo > /dev/tcp/16.171.154.34/8080' && echo "OPEN" || echo "BLOCKED"
```

If it shows `BLOCKED`:

1. AWS Console → EC2 → Security Groups
2. Find your relay server's security group
3. Click "Inbound Rules"
4. Add rule: Protocol TCP, Port 8080, Source 0.0.0.0/0

Or via AWS CLI:
```bash
aws ec2 authorize-security-group-ingress \
  --group-id sg-xxxxxxxx \
  --protocol tcp \
  --port 8080 \
  --cidr 0.0.0.0/0
```

---

## Verification After Fix

Once ports are open:

```bash
# 1. Verify listening on 0.0.0.0
sudo ss -tuln | grep -E '8080|9000'

# 2. Restart relay
pm2 restart relay

# 3. Check logs (should show no errors)
pm2 logs relay --lines 50

# 4. Test local connection
nc -zv 127.0.0.1 8080

# 5. From phone, run app test:
#    - Clear app cache: adb shell pm clear com.pocketcraft.server
#    - Rebuild app: ./gradlew installDebug
#    - Start server in app
#    - Check logs: adb logcat | grep -i relay
```

---

## What App Logs to Look For (Success Case)

```
D/RelayManager: Registering user: 1709bead-4036-481d-9b7c-ca0cf88a6cf0
D/RelayManager: Register request: {"userId":"1709bead-4036-481d-9b7c-ca0cf88a6cf0"}
D/RelayManager: Register response code: 200
D/RelayManager: Register response: {"ip":"play.pocketcraft.online","port":25500}
D/RelayManager: Register success: play.pocketcraft.online:25500
D/RelayManager: Connecting tunnel for user: 1709bead-4036-481d-9b7c-ca0cf88a6cf0
D/RelayManager: Tunnel connected successfully
```

---

## The Complete Diagnostic Sequence

```bash
# ON SERVER:
sudo ss -tuln | grep '8080|9000'
pm2 status
pm2 logs relay | tail -50

# ON PHONE:
adb devices
adb shell nc -zv 16.171.154.34 8080
adb logcat -c
adb logcat | grep RelayManager

# THEN:
# Start Minecraft server in PocketCraft app
# Watch logs stream in both terminal windows
```

Need more details? See **RELAY_DIAGNOSTICS.md** in the repo root for the full troubleshooting guide.
