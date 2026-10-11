# Contributing to PocketHost

PocketHost is closed source and this repository is private. These guidelines are for team members with access to it. Do not copy code from this repository into public repositories, issues or chats.

---

## Development Guidelines

### Code Style & Architecture
- **Kotlin & Jetpack Compose**: Follow standard Android Kotlin coding conventions. UI components should use the existing theme tokens defined in `PocketThemeTokens.kt` and `PocketColors.kt`.
- **Coroutines**: Prefer structured concurrency with explicit dispatchers (`Dispatchers.IO` for file/network operations, `Dispatchers.Default` for CPU-heavy transformations).
- **Foreground Service**: Any server lifecycle logic must run within or coordinate through `ServerHostService`.

### Critical Networking Guidelines
Before modifying socket configurations or packet buffering in `RelayManager.kt` or `BedrockUdpBridge.kt`, review [NETWORKING_ARCHITECTURE.md](docs/technical/NETWORKING_ARCHITECTURE.md).
- Do not increase TCP socket buffer sizes past 64KB, as larger buffers cause high latency spikes during bulk chunk transfers on mobile networks.
- Bounded coroutine channel capacities must remain bounded to prevent out-of-memory errors.

---

## Pull Request Workflow

1. Create a descriptive feature branch in this repository (do not fork it to a public account):
   ```bash
   git checkout -b feat/your-feature-name
   ```
2. Ensure your changes compile cleanly:
   ```bash
   ./gradlew compileDebugKotlin
   ```
3. Commit your changes using concise Conventional Commits messages:
   - `feat(ui): add new setting for render distance`
   - `fix(server): handle missing world configuration gracefully`
   - `docs: improve build instructions for Linux`
4. Push your branch to this private repository and open a Pull Request.

---

## Reporting Issues

If you encounter a bug or crash:
1. Check existing issues to see if it has already been reported.
2. Provide your Android OS version, device model, and server type/version (e.g., Paper 1.21.4).
3. Include relevant log output from the app's Console screen or `adb logcat`.
