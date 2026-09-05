# CLAUDE.md - PocketCraft Engineering & Architecture Guide

Welcome to the **PocketCraft** (brand: **PocketHost** / internal package: `com.pockethost.app`) codebase. This guide equips Claude with the mental model, strict constraints, CLI workflows, UI conventions, and token-saving Graphify commands required to work effectively in this repository.

---

## 1. Token-Saving Protocol with Graphify

> [!IMPORTANT]
> **DO NOT burn context tokens by reading large files or grepping blindly.**
> PocketCraft has a pre-built knowledge graph with **6,700+ nodes** located at `graphify-out/graph.json`.
> Always use `graphify` commands to inspect architecture and relationships before opening raw source files (especially god nodes like `ServerStateHolder.kt` which is 5,600+ lines).

### Core Graphify Commands
| Task | Command |
| :--- | :--- |
| **Ask architectural questions** | `graphify query "<question>"` (returns a scoped subgraph of 20–40 nodes) |
| **Trace path between components** | `graphify path "<NodeA>" "<NodeB>" --undirected` |
| **Explain a specific class or symbol**| `graphify explain "<SymbolName>"` |
| **Inspect top architectural hubs** | `graphify god-nodes --top 15` |
| **Update graph after code edits** | `graphify update .` *(AST-only, instant, zero API token cost)* |

---

## 2. Core Mental Model & Architecture

PocketCraft turns an Android mobile device into a high-performance, low-latency host for Minecraft Java (Paper, Purpur, Fabric) and Bedrock (PowerNukkitX) servers, bridged over global relay servers.

```
┌────────────────────────────────────────────────────────────────────────┐
│ Android Device                                                         │
│ ┌───────────────────────────┐         ┌──────────────────────────────┐ │
│ │ UI Process (:main)        │         │ Server Process (:server)     │ │
│ │ - MainActivity            │  IPC /  │ - ServerHostService          │ │
│ │ - Jetpack Compose UI      │◄───────►│ - NativeLauncher (launcher.c)│ │
│ │ - ServerStateHolder       │ Intents │ - Bundled JRE 25 (OpenJDK)   │ │
│ │ - DuoButton / 3D Tokens   │         │ - Paper / PowerNukkitX Server│ │
│ └───────────────────────────┘         │ - RelayManager (TCP/UDP)     │ │
│                                       └──────────────┬───────────────┘ │
└──────────────────────────────────────────────────────┼─────────────────┘
                                                       │ TCP Tunnel (9000)
                                                       │ UDP RakNet Loopback
                                                       ▼
┌────────────────────────────────────────────────────────────────────────┐
│ AWS EC2 Relay Infrastructure (Mumbai 13.201.57.41 / Frankfurt 54.93.247.2) │
│ ┌───────────────────────────┐         ┌──────────────────────────────┐ │
│ │ pocketcraft-relay (8080)  │         │ subdomain-listener (25565)   │ │
│ │ - Socket pools (port 9000)│◄───────►│ - Custom IP SNI Handshake    │ │
│ │ - Bedrock MTU & RakNet    │         │ - Subdomain router to phone  │ │
│ └───────────────────────────┘         └──────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

### Process Separation
The Android app is strictly split into two processes in `AndroidManifest.xml`:
1. **`:main` (UI Process)**: Runs Jetpack Compose, handles navigation, renders console logs, and collects user actions via `ServerStateHolder.kt`.
2. **`:server` (Foreground Service Process)**: Runs `ServerHostService.kt`, acquires `WakeLock` and `WifiLock`, unpacks and executes the bundled OpenJDK 25 via `launcher.c` JNI, manages local server files, and establishes tunnels to the AWS relay.
3. **IPC**: Communication happens via Android Intents, Broadcast Receivers (`DashboardCommandListener`), and persistent JSON runtime state snapshots (`readStateFile` / `persistRuntimeState`).

---

## 3. UI Design System: Tactile 3D Game UI

PocketCraft uses a playful, tactile, BrawlStars/Duolingo-inspired 3D Material 3 design system implemented with Jetpack Compose.

### Key Tokens & Components
- **`DuoButton`** (`ui/components/DuoButton.kt`): Tactile push button with physical spring press offset, bottom depth strip, and variants (`StartServer`, `Primary`, `Secondary`, `Danger`, `Warning`, `Info`, `Discord`, `Pro`, `SecondaryGray`).
- **`PocketThemeTokens`** (`ui/theme/PocketThemeTokens.kt`): Provides `Modifier.bottomShadow(shadowColor, shadowHeight, cornerRadius)` with crisp unblurred bottom-only 3D depth, and `Pocket3dShadowTint` (`#1A3A2A`).
- **`GameCard`** & **`PocketHostCard`**: Container cards with consistent corner radii (16.dp to 24.dp) and subtle surface borders.
- **Dynamic Theme Awareness**: Always use `pocketIsDarkTheme()` or `MaterialTheme.colorScheme` rather than hardcoding colors.
- **Touch Targets**: All interactive elements must maintain minimum 48dp touch targets.
- **Recomposition Hygiene**: Never allocate objects inside Compose render loops; use `remember`, `derivedStateOf`, and stable model classes.

---

## 4. Development & Operational Commands

### Android Gradle Builds
```bash
# Build debug APK
./gradlew assembleDebug

# Build production App Bundle (signed via upload-keystore.jks)
./gradlew bundleRelease

# Run unit tests
./gradlew testDebugUnitTest

# Generate dependency license report
./gradlew generateLicenseReport
```

### Device Testing & Inspection
```bash
# Deploy to connected device via Android CLI
android run --apks=app/build/outputs/apk/debug/app-debug.apk

# Inspect active Compose layout tree (faster than screenshots)
android layout --pretty

# Capture device screenshot
android screen capture output.png
```

### Cloud Functions (`/functions`)
```bash
cd functions
npm run build
firebase deploy --only functions
```

### Relay Server Management (SSH / PM2)
```bash
# Deploy relay update to Frankfurt
scp -i ~/Downloads/europekey.pem relay/index.js ubuntu@54.93.247.2:~/relay/index.js
ssh -i ~/Downloads/europekey.pem ubuntu@54.93.247.2 "pm2 restart pocketcraft-relay"

# Check PM2 status on Mumbai
ssh -i ~/Downloads/pocketcraft-key1.pem ubuntu@13.201.57.41 "pm2 status"
```

---

## 5. Key File Map

| Purpose | File Path |
| :--- | :--- |
| **Central UI State God Node** | [`ServerStateHolder.kt`](file:///home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/app/src/main/kotlin/com/pockethost/app/ui/screens/ServerStateHolder.kt) |
| **Foreground Service (`:server`)** | [`ServerHostService.kt`](file:///home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/app/src/main/kotlin/com/pockethost/app/server/ServerHostService.kt) |
| **JVM Process Launcher** | [`ServerLauncher.kt`](file:///home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/app/src/main/kotlin/com/pockethost/app/server/ServerLauncher.kt) |
| **Native C JNI VM Invoker** | [`launcher.c`](file:///home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/app/src/main/cpp/launcher.c) |
| **Bundled JRE Extractor** | [`JreExtractor.kt`](file:///home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/app/src/main/kotlin/com/pockethost/app/setup/JreExtractor.kt) |
| **Relay Socket Client** | [`RelayManager.kt`](file:///home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/app/src/main/kotlin/com/pockethost/app/RelayManager.kt) |
| **Bedrock UDP Translation** | [`BedrockUdpBridge.kt`](file:///home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/app/src/main/kotlin/com/pockethost/app/relay/BedrockUdpBridge.kt) |
| **Companion Spigot Plugin** | [`PocketCraftCompanion.java`](file:///home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/companion-plugin/src/main/java/com/pockethost/companion/PocketCraftCompanion.java) |
| **Node.js Relay Coordinator** | [`relay/index.js`](file:///home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/relay/index.js) |
| **Subdomain Custom IP Router** | [`relay/subdomain-listener.js`](file:///home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/relay/subdomain-listener.js) |
| **Tactile 3D Buttons** | [`DuoButton.kt`](file:///home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/app/src/main/kotlin/com/pockethost/app/ui/components/DuoButton.kt) |
| **Theme & 3D Depth Tokens** | [`PocketThemeTokens.kt`](file:///home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/app/src/main/kotlin/com/pockethost/app/ui/theme/PocketThemeTokens.kt) |
| **Google Play Billing** | [`BillingManager.kt`](file:///home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/app/src/main/kotlin/com/pockethost/app/billing/BillingManager.kt) |

---

## 6. Claude Skills Integration

Specialized skills are installed in `.claude/skills/`:
- **`/stitch`** (`.claude/skills/stitch/SKILL.md`): Convert Google Stitch UI wireframes/HTML into tactile Compose components.
- **`/compose-ui-auditor`** (`.claude/skills/compose-ui-auditor/SKILL.md`): Audit Compose UI against PocketCraft design system, touch targets, and contrast.
- **`/graphify`** (`.claude/skills/graphify/SKILL.md`): Knowledge graph queries for token-efficient codebase exploration.
- **`/playstore-release-notes`** (`.claude/skills/playstore-release-notes/SKILL.md`): Multi-language Play Store release note generation.
- **`/relay-ops`** (`.claude/skills/relay-ops/SKILL.md`): AWS EC2 relay deployment, PM2 management, and UDP ping debugging.
