# PocketCraft Project Memory & Architectural Guide

This file serves as a persistent context guide and token-saving reference for the entire PocketCraft codebase. It describes how all components (Android client, Node.js relay nodes, companion Spigot/Paper plugin, and Cloud Functions backend) fit together.

---

## 1. Project Directory Structure

```
PocketCraft/
├── app/               # Android Client (Kotlin / Jetpack Compose UI)
├── companion-plugin/  # Bukkit/Spigot Java plugin bundled in Minecraft server
├── relay/             # Node.js TCP/UDP Relay Server & Custom IP Subdomain listener
├── functions/         # Firebase Cloud Functions (TypeScript)
├── docs/              # Developer guides, technical specs, agents instructions
└── assets/            # Bundled JRE binaries (Java 25) & licensing
```

---

## 2. Component Architectures

### A. Android Client App (`/app`)
The client app acts as the user-facing controller and JVM supervisor that runs the Minecraft server on the phone.

1. **User Interface (`com.pocketcraft.server.ui`)**:
   - Built using **Jetpack Compose** and vanilla Material Design.
   - Screen layouts (`ConsoleScreen.kt`, `ServerDetailsScreen.kt`, `SettingsScreen.kt`, `WorldsScreen.kt`) organize server options, modpacks, and plugins.
   - `SubdomainManager.kt` provides premium users the UI to reserve/configure Custom IPs.

2. **JVM Launching & Execution (`com.pocketcraft.server.server`)**:
   - Extracts a bundled **JRE 25** (`assets/java/jre25`) to local app storage on first startup (`JreExtractor.kt`).
   - Uses a custom native C launcher (`launcher.c` / `libjnidispatch.so` loaded via JNI) to invoke the Java VM in-process/out-of-process.
   - **`ServerLauncher.kt`** sets memory configurations, garbage collection rules, and loads the Paper server jar.
   - **`ConsoleParser.kt`** captures output streams from the server process, parsing logs, events, and player joins to update the app state.

3. **Backup & World Importer (`com.pocketcraft.server.integrations`)**:
   - `DriveBackupManager.kt` integrates with Google Drive APIs to upload/download compressed backups of world files.
   - `WorldImporter.kt` handles extracting, validating, and mounting external Minecraft world folders into local server storage.

4. **Billing & Entitlement (`com.pocketcraft.server.billing`)**:
   - `BillingManager.kt` integrates with Google Play Billing Library to check and track Pro/Member subscription statuses.

---

### B. Companion Spigot/Paper Plugin (`/companion-plugin`)
A custom Bukkit plugin (`PocketCraftCompanion.jar`) is bundled and automatically loaded by the local server.

1. **Security Manager Bypassing**:
   - Installs an anti-exit `SecurityManager`. This intercepts and blocks `System.exit()` requests from third-party plugins, preventing them from taking down the host Android app's JVM process.
2. **Graceful Shutdown**:
   - Periodically polls for a `graceful_stop.signal` file. When found, it reflects into `MinecraftServer.stopServer()` to flush world files and shutdown cleanly instead of force-killing the process.
3. **Debug Subscription Decoders**:
   - Injects a netty channel inbound adapter (`DebugSubscriptionShield.java`) that suppresses decoding errors related to `debug_subscription_request` packets, preventing client connections from getting reset on modern Paper builds.
4. **Ping Reporting**:
   - Outputs console status lines like `[PocketCraftPing] player:ping@address` which the client app's `ConsoleParser` reads to dynamically display player ping/telemetry in the UI.

---

### C. Cloud Functions Backend (`/functions`)
Written in TypeScript and deployed as Firebase v2 Cloud Functions to handle authentication-sensitive operations.

1. **Feedback Forwarder (`forwardFeedbackEmail`)**:
   - Firestore trigger on the `beta_feedback` collection. Automatically builds feedback emails and sends them to `support@pocketcraft.online` via the **Resend API**.
2. **Google Play Purchase Verification (`verifyPurchase`)**:
   - HTTPS Callable function. Receives Google Play Purchase Tokens, verifies them with the Google Play Developer API, and updates user entitlement records in the Firestore `users` collection.
3. **Real-Time Developer Notifications (`syncPlaySubscriptionRtdn`)**:
   - Pub/Sub subscription mapping Google Play RTDN events. Listens for subscription upgrades, cancellations, or expirations, keeping Firestore entitlement states synchronized with Google Play.

---

### D. Relay Infrastructure (`/relay`)
Hosted on AWS EC2 instances to expose local mobile servers to the public internet.

1. **Relay Server (`index.js`)**:
   - Listens on port 8080 (Control API) and port 9000 (Phone tunnel).
   - Maps player TCP sockets to persistent client socket pools registered from phones.
   - Runs a Bedrock MTU proxy and routes UDP RakNet frames to local Geyser bindings.
2. **Subdomain Listener (`subdomain-listener.js` & `hostname-router.js`)**:
   - Listens on standard port 25565.
   - Extracts hostname details from Java Handshake packets.
   - Maps `<subdomain>.as.pocketcraft.online` / `<subdomain>.eu.pocketcraft.online` to target user IDs in Firestore, fetches their active tunnel port from the relay status API, and proxies the Minecraft connection.

---

## 3. Server Node Configurations & Operations

### A. Environments
- **Asia (Mumbai)**:
  - **Public Host**: `mine.pocketcraft.online` (`13.201.57.41`)
  - **Key File**: `/home/aleemkanyu/Downloads/pocketcraft-key1.pem`
  - **Relay Path**: `/home/ubuntu/pocketcraft-relay/`
- **Europe (Frankfurt)**:
  - **Public Host**: `eu.pocketcraft.online` (`54.93.247.2`)
  - **Key File**: `/home/aleemkanyu/Downloads/europekey.pem`
  - **Relay Path**: `/home/ubuntu/relay/`

*Note: The Singapore relay server (`play.pocketcraft.online`) has been permanently deleted.*

### B. Deployment & Process Commands
Both Mumbai and Europe servers run two PM2 processes: `pocketcraft-relay` and `pocketcraft-subdomain-listener`.

```bash
# Redeploy relay code to Europe
scp -i ~/Downloads/europekey.pem relay/index.js ubuntu@54.93.247.2:~/relay/index.js
ssh -i ~/Downloads/europekey.pem ubuntu@54.93.247.2 "pm2 restart pocketcraft-relay"

# Check PM2 status on Mumbai
ssh -i ~/Downloads/pocketcraft-key1.pem ubuntu@13.201.57.41 "pm2 status"
```

---

## 4. Key Constraints & Rules for AI Agents

> [!WARNING]
> To prevent massive latency spikes, compile errors, or connection issues, the following configurations are locked to the stable v1.6.0 release values:

1. **Relay Manager Socket Buffers**:
   - `SOCKET_BUFFER_SIZE` in `RelayManager.kt` MUST remain at `128 * 1024` (128KB).
   - `PLAYER_BRIDGE_BUFFER_SIZE` in `RelayManager.kt` MUST remain at `64 * 1024` (64KB) downstream.
   - `PLAYER_BRIDGE_UPSTREAM_BUFFER_SIZE` in `RelayManager.kt` MUST remain at `64 * 1024` (64KB) upstream.
2. **Bedrock Bridge Channels & Queues**:
   - `BEDROCK_PING_CHANNEL_CAPACITY = 512`
   - `BEDROCK_CHUNK_CHANNEL_CAPACITY = 1024`
   - `BEDROCK_LARGE_FRAME_BATCH_MAX = 4` (Smaller bursts prevent TCP queue delay)
   - `BEDROCK_MAX_BYTES_PER_CYCLE = 16 * 1024` (16KB)
   - Routing: Must route by **RakNet packet ID (byte 9)** instead of size. Route `0xC0` (ACK), `0xA0` (NACK), `0x00` (Ping), `0x03` (Pong) to the high-priority channel instantly to prevent retransmission loops and latency spikes.
   - Event loop: Use Kotlin's `select` expression for event-driven, 0ms latency packet wakeups.
3. **Paper/Java Ping Optimization**:
   - Set `timings.enabled = false` and `timings.really-enabled = false` in `paper-global.yml` to prevent tick loop disk writes on Android.
   - Extend `keep-alive-timeout = 60` to stabilize Java keep-alive ping and prevent kicks.
4. **Chunk Send Budgets**:
   - WiFi: `160` (flight), `100` (walking) chunks/sec. Concurrency: `Triple(10, 18, 12)` (flight).
   - Cellular: `60` (flight), `40` (walking) chunks/sec. Concurrency: `Triple(6, 10, 6)` (flight).
   - Coerce pipeline generates/loads to 300/400 to match.

### Locked Stable Reference Commit
- Stable networking + chunk baseline commit: `6261580` (`Optimize chunk loading speed, cellular budgets, RakNet frame classification, and Java keep-alive ping stability`)
- Baseline snapshot before widget/server-sync UI fixes: `46214a8` (`chore: snapshot current state before widget sync fixes`)
- Do not regress the following when touching relay/server lifecycle code:
  - Keep `RelayManager.kt` socket buffers and Bedrock channel capacities exactly as listed above.
  - Keep RakNet high-priority routing on packet ID, not frame size.
  - Keep the WiFi/cellular chunk budgets and concurrency values above unchanged unless a user explicitly requests a networking retune.

---

## 5. Play Store Release Notes Localization Skill

When the user asks to translate Play Store release notes / update text (e.g. "Fixed X, Fixed Y write this for playstore and only in supported language tags present in the app"):

1. **Supported Locales/Languages**:
   - `en-US` (English - United States)
   - `de-DE` (German)
   - `es-ES` (Spanish)
   - `ru-RU` (Russian)
   - `zh-CN` (Chinese - Simplified)

2. **Required Format**:
   Return the localized release notes in a single code block wrapped in XML-like tags, e.g.:
   ```xml
   <en-US>
   - Fixed player count resetting after app restart
   - Fixed account sign-up failure
   </en-US>
   ...
   ```

3. **Execution Steps**:
   - Directly output the translation in the XML format.
   - Save/overwrite it to [playstore_release_notes.txt](file:///home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/playstore_release_notes.txt) so the user can easily copy/view it.
