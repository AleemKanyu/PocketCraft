---
name: relay-ops
description: "Operations and diagnostic guide for the AWS EC2 PocketCraft relay servers (Mumbai 13.201.57.41 and Frankfurt 54.93.247.2), PM2 process control, TCP tunneling, and Bedrock RakNet UDP framing."
---

# /relay-ops - AWS Relay Infrastructure Operations & Diagnostics

This skill guides Claude through managing, diagnosing, and deploying the AWS EC2 relay nodes that expose mobile PocketCraft servers to the global internet.

---

## 1. Relay Server Fleet

| Region | Public IP | Hostname | SSH Key | Remote Directory |
| :--- | :--- | :--- | :--- | :--- |
| **Asia (Mumbai)** | `13.201.57.41` | `mine.pocketcraft.online` | `~/Downloads/pocketcraft-key1.pem` | `/home/ubuntu/pocketcraft-relay/` |
| **Europe (Frankfurt)** | `54.93.247.2` | `eu.pocketcraft.online` | `~/Downloads/europekey.pem` | `/home/ubuntu/relay/` |

> [!CAUTION]
> The Singapore server (`play.pocketcraft.online`) has been permanently deleted. Never attempt connections or DNS updates to Singapore.

---

## 2. Process Architecture on Relay Nodes

Each EC2 node runs two separate Node.js processes managed by **PM2**:
1. **`pocketcraft-relay`** (`relay/index.js`):
   - Port `8080`: HTTP REST status & control API (Cleartext HTTP).
   - Port `9000`: Phone socket tunnel listener.
   - Bedrock proxy: Routes UDP RakNet frames to phone tunnel.
2. **`pocketcraft-subdomain-listener`** (`relay/subdomain-listener.js`):
   - Port `25565`: Standard Minecraft Java port.
   - Inspects the Minecraft Java Handshake packet for the hostname (`<subdomain>.as.pocketcraft.online` or `<subdomain>.eu.pocketcraft.online`).
   - Polls `localhost:8080/status` to match the subdomain to the active phone tunnel port, and pipes the connection directly to `127.0.0.1:<port>`.

---

## 3. Standard Operational Commands

### Checking Service Status & Health
```bash
# Check PM2 status on Mumbai
ssh -i ~/Downloads/pocketcraft-key1.pem ubuntu@13.201.57.41 "pm2 status"

# View real-time logs on Frankfurt
ssh -i ~/Downloads/europekey.pem ubuntu@54.93.247.2 "pm2 logs --lines 50"

# Check listening ports on Mumbai
ssh -i ~/Downloads/pocketcraft-key1.pem ubuntu@13.201.57.41 "sudo netstat -tlpn | grep -E '8080|9000|25565'"
```

### Deploying Relay Code Updates
```bash
# Deploy relay update to Frankfurt
scp -i ~/Downloads/europekey.pem relay/index.js ubuntu@54.93.247.2:~/relay/index.js
scp -i ~/Downloads/europekey.pem relay/subdomain-listener.js ubuntu@54.93.247.2:~/relay/subdomain-listener.js
ssh -i ~/Downloads/europekey.pem ubuntu@54.93.247.2 "pm2 restart all"

# Deploy relay update to Mumbai
scp -i ~/Downloads/pocketcraft-key1.pem relay/index.js ubuntu@13.201.57.41:~/pocketcraft-relay/index.js
scp -i ~/Downloads/pocketcraft-key1.pem relay/subdomain-listener.js ubuntu@13.201.57.41:~/pocketcraft-relay/subdomain-listener.js
ssh -i ~/Downloads/pocketcraft-key1.pem ubuntu@13.201.57.41 "pm2 restart all"
```

### Verifying Relay REST API & Phone Tunnel
```bash
# Query active relay status (returns connected phone tunnels)
curl -s http://13.201.57.41:8080/status | jq .
curl -s http://54.93.247.2:8080/status | jq .
```

---

## 4. Diagnostics & Troubleshooting Checklist

- **"Address already in use (EADDRINUSE)"**: Another PM2 instance or zombie node process is holding port 8080 or 25565. Run `sudo fuser -k 8080/tcp` and `pm2 restart all`.
- **Bedrock players cannot join**: Check that UDP port ranges are open in AWS Security Groups and that `bedrock-ping.js` is active.
- **Custom IP Subdomain fails to resolve**: Verify that the phone has registered its subdomain with the relay control API and that `subdomain-listener.js` is polling port 8080 successfully.
