# PocketHost

<div align="center">

**Host dedicated Minecraft Java & Bedrock servers directly on your Android device.**  
*Zero port forwarding required. Native ARM64 Java performance. Worldwide cross-play.*

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-green.svg)](https://developer.android.com)
[![Java Runtime](https://img.shields.io/badge/Java-17_%7C_21_%7C_25-orange.svg)](https://adoptium.net)

</div>

---

## Overview

**PocketHost** (formerly PocketCraft) turns an Android smartphone or tablet into a fully functional, high-performance dedicated Minecraft server. By leveraging bundled ARM64 OpenJDK runtimes, a native C JNI invoker, and an intelligent global relay tunnel network, PocketHost lets you play multiplayer with friends anywhere without complex networking or desktop hardware.

### Key Capabilities

- **Native Server Engines**: Run official Paper, Purpur, Fabric, Vanilla, or Bedrock PowerNukkitX servers directly on-device.
- **Bundled OpenJDK Runtimes**: Ships with pre-optimized Java 17, Java 21, and bleeding-edge Java 25 ARM64 binaries—no separate JRE installation needed.
- **Zero-Config Global Relays**: Built-in multi-region TCP and UDP relay tunneling allows friends to join from anywhere over cellular or Wi-Fi without port forwarding or UPnP.
- **Seamless Cross-Play**: Pre-configured Geyser and Floodgate integration lets Minecraft Bedrock (mobile, console, Windows) players connect to your Java server with offline-safe UUID resolution.
- **Tactile Modern UI**: Built with Jetpack Compose featuring 120Hz smooth scrolling, real-time streaming console logs, RCON execution, player management, and world import/export.
- **Companion Plugin**: Bundled Bukkit/Paper security plugin prevents unauthorized server shutdowns and guards against packet decode crashes.

---

## Repository Architecture

```
PocketHost/
├── app/               # Android client application (Kotlin + Jetpack Compose)
│   ├── src/main/cpp/  # Native C JNI JVM launcher (launcher.c)
│   ├── src/main/jniLibs/ # ARM64/x86_64 JNI binaries (libjvm.so, libjnidispatch.so)
│   └── src/main/kotlin/com/pockethost/app/
│       ├── billing/   # Play Billing integration & license verification
│       ├── broadcast/ # Remote command dispatching & notification service
│       ├── data/      # Room database, repositories & data models
│       ├── network/   # RCON TCP client & API communication
│       ├── relay/     # Low-latency Bedrock UDP datagram bridge
│       ├── server/    # Foreground service supervisor & JVM launch args
│       ├── service/   # Console stream parsing & world/plugin managers
│       └── ui/        # Compose screens, 3D design system & theme tokens
├── companion-plugin/  # Bukkit/Paper Java plugin bundled with server boot
├── relay/             # Node.js TCP/UDP relay coordinator & subdomain router
├── dashboard/         # Optional React + TypeScript web management console
├── functions/         # Firebase Cloud Functions (feedback & purchase verification)
└── docs/              # Architecture guides, legal notices, and compliance sheets
```

---

## Getting Started

### Prerequisites

- **Java Development Kit**: JDK 21 or newer installed and configured in your environment (`JAVA_HOME`).
- **Android SDK**: Compile SDK 36 (Android 16), NDK, and CMake 3.22+.
- **Node.js**: v20+ (only required if developing the web dashboard or relay coordinator).

### Building the Android App

1. Clone the repository:
   ```bash
   git clone https://github.com/AleemKanyu/PocketCraft_.git
   cd PocketCraft_
   ```

2. Configure keystore properties (optional for debug builds):
   ```bash
   cp keystore.properties.example keystore.properties
   ```

3. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```

4. Install directly to a connected Android device:
   ```bash
   ./gradlew installDebug
   ```

---

## Running the Relay Server

PocketHost can connect to custom self-hosted relays for private networks or community deployments.

1. Navigate to the relay directory:
   ```bash
   cd relay
   npm install
   ```

2. Start the relay coordinator:
   ```bash
   RELAY_SECRET="your_shared_secret" node index.js
   ```

3. (Optional) Start the custom subdomain router on port 25565:
   ```bash
   node subdomain-listener.js
   ```

---

## Technical Highlights

### JVM Process Execution & Memory Management
The Android client uses a lightweight JNI wrapper (`launcher.c`) to spawn OpenJDK directly within a supervised foreground service process. Standard output and error streams are tailed into a ring buffer with millisecond-accurate boot milestone tracking.

### Network Latency Tuning
To avoid buffer bloat across cellular radios, TCP relay socket buffers are strictly tuned to 64KB with an 8KB bridge transfer window. Time-sensitive keep-alive packets bypass bulk chunk transfers, maintaining consistent sub-100ms ping under load.

---

## Contributing

Contributions, bug reports, and feature proposals are welcome! Please check out [CONTRIBUTING.md](CONTRIBUTING.md) for details on code style, branch guidelines, and testing procedures.

---

## License

This project is licensed under the [Apache License 2.0](LICENSE).
Minecraft is a trademark of Mojang Synergies AB. PocketHost is an independent open-source tool and is not affiliated with or endorsed by Mojang or Microsoft.
