# PocketCraft Codebase Memory & Architecture Guide

This file serves as a persistent memory and token-saving reference. Every AI assistant or developer working on this project must read this file first and update it immediately whenever any structural, architectural, or networking changes are made.

---

## 1. Project Overview
PocketCraft is an Android application that hosts a local Minecraft Paper (Java) server on-device and bridges it to the internet using a custom TCP/UDP relay tunnel. It bundles **Geyser** and **ViaVersion** to allow both Minecraft Java and Bedrock clients to connect.

---

## 2. Infrastructure & Environments

### A. Active Relay Servers
- **Asia (Mumbai)**:
  - **Host / Public IP**: `mine.pocketcraft.online` (`13.201.57.41`)
  - **Key File**: `/home/aleemkanyu/Downloads/pocketcraft-key1.pem`
  - **Relay Path**: `/home/ubuntu/pocketcraft-relay/`
- **Europe (Frankfurt)**:
  - **Host / Public IP**: `eu.pocketcraft.online` (`54.93.247.2`)
  - **Key File**: `/home/aleemkanyu/Downloads/europekey.pem`
  - **Relay Path**: `/home/ubuntu/relay/`
- **America**:
  - **Host**: `us.pocketcraft.online`

*Note: The Singapore relay server (`play.pocketcraft.online`) has been permanently deleted.*

### B. Daemon Processes (PM2) on Relays
Both active servers run two separate PM2 services:
1. `pocketcraft-relay`: Manages the primary TCP/UDP tunnels on port 9000 (phone tunnel) and control API on port 8080.
2. `pocketcraft-subdomain-listener`: Manages the Java Custom IP subdomain routing on port 25565.

---

## 3. Core Architecture & Tunnelling Flow

### A. Standard Connection Flow
1. **Registration**: App requests a port assignment from `http://<relay-host>:8080/register`.
2. **Socket Pooling**: `RelayManager.kt` maintains a pool of persistent outbound TCP connections to `http://<relay-host>:9000` (assigned to the user's UUID).
3. **Traffic Bridging**:
   - Java players connect to the relay server on their assigned port.
   - The relay pairs the player's TCP socket with an idle phone socket from the pool.
   - The phone routes it locally to the Paper port (`25565`).

### B. Custom IP / Subdomain Routing (Port 25565)
1. **Subdomain Creation**: Premium users register a subdomain (e.g., `myname`) mapped to their UUID in the Firestore `subdomains` collection.
2. **DNS Routing**: Wildcards `*.as.pocketcraft.online` and `*.eu.pocketcraft.online` resolve to the Mumbai and Europe relay IPs, respectively.
3. **Parsing & Routing**:
   - `subdomain-listener.js` runs on port 25565 of the relay.
   - When a Java client connects, it parses the Minecraft Handshake packet to extract the host (e.g., `myname.as.pocketcraft.online`).
   - It queries Firestore to find the owner's UUID, calls the relay's local `/status` endpoint on port 8080 to get their assigned tunnel port, and pipes the connection directly to `127.0.0.1:<assigned-port>`.

---

## 4. Key Constraints & Warning Directives

> [!WARNING]
> To prevent massive latency spikes (thousands of ms) and connection drops, the following configurations are locked to the stable v1.6.0 release values:

1. **Buffer Sizes**:
   - `SOCKET_BUFFER_SIZE` in `RelayManager.kt` MUST remain at `256 * 1024` (256KB).
   - `PLAYER_BRIDGE_BUFFER_SIZE` in `RelayManager.kt` MUST remain at `64 * 1024` (64KB).
2. **Bedrock Bridge Channel**:
   - `bedrockTxChannel` capacity MUST remain at `256` (do not use unlimited capacity channels).
3. **No Experimental Bindings**:
   - Do not introduce custom Wi-Fi socket bindings or modify Netty thread loops unless explicitly asked.
4. **Dynamic Fallback Region**:
   - Singapore is deleted. In `RelayManager.kt`, fallback selection is calculated dynamically: if the preferred server is Mumbai, the fallback is Europe; otherwise, the fallback is Mumbai.

---

## 5. Operations Cheatsheet

### Deploying Relay Code changes
```bash
# Deploy to Mumbai
scp -i ~/Downloads/pocketcraft-key1.pem relay/index.js ubuntu@13.201.57.41:~/pocketcraft-relay/index.js
ssh -i ~/Downloads/pocketcraft-key1.pem ubuntu@13.201.57.41 "pm2 restart pocketcraft-relay"

# Deploy to Europe
scp -i ~/Downloads/europekey.pem relay/index.js ubuntu@54.93.247.2:~/relay/index.js
ssh -i ~/Downloads/europekey.pem ubuntu@54.93.247.2 "pm2 restart pocketcraft-relay"
```

### Checking Relay PM2 Status
```bash
ssh -i ~/Downloads/pocketcraft-key1.pem ubuntu@13.201.57.41 "pm2 status"
ssh -i ~/Downloads/europekey.pem ubuntu@54.93.247.2 "pm2 status"
```

### Client Packaging & Installation
```bash
# Compile and build Release APK
./gradlew assembleRelease

# Stream Install to connected device
android run --apks=app/build/outputs/apk/release/app-release.apk
```
