# CODEBASE_REPORT — PocketCraft

## 1. Project Structure

```text
.
├── build.gradle.kts
├── settings.gradle.kts
├── app/
│   ├── build.gradle.kts
│   ├── google-services.json
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   ├── README.md
│       │   ├── adi-registration.properties
│       │   ├── components/jre/
│       │   │   ├── bin-arm.tar.xz
│       │   │   ├── bin-arm64.tar.xz
│       │   │   ├── bin-x86.tar.xz
│       │   │   ├── bin-x86_64.tar.xz
│       │   │   ├── universal.tar.xz
│       │   │   └── version
│       │   ├── connect-spigot.jar
│       │   ├── default_plugins/PocketCraftChunkLoader.jar
│       │   ├── inventory.html
│       │   ├── jre-runtime/
│       │   │   ├── bin-arm.tar.xz
│       │   │   ├── bin-arm64.tar.xz
│       │   │   ├── bin-x86.tar.xz
│       │   │   ├── bin-x86_64.tar.xz
│       │   │   ├── universal.tar.xz
│       │   │   └── version
│       │   └── social/
│       │       ├── discord.png
│       │       └── instagram.png
│       ├── cpp/
│       │   ├── launcher.c
│       │   └── serverwrap.c
│       ├── legal/
│       │   ├── java.base/*
│       │   ├── java.desktop/*
│       │   ├── java.xml/*
│       │   ├── java.xml.crypto/*
│       │   ├── jdk.crypto.cryptoki/*
│       │   ├── jdk.dynalink/*
│       │   └── jdk.localedata/*
│       ├── res/
│       │   ├── drawable*/
│       │   ├── font/
│       │   ├── mipmap-anydpi-v26/
│       │   ├── raw/
│       │   ├── values/
│       │   └── xml/
│       └── kotlin/com/pocketcraft/server/
│           ├── MainActivity.kt
│           ├── NativeLauncher.kt
│           ├── PocketCraftApp.kt
│           ├── RelayManager.kt
│           ├── WorldImporter.kt
│           ├── analytics/
│           ├── broadcast/
│           ├── config/
│           ├── data/
│           ├── di/
│           ├── feedback/
│           ├── network/
│           ├── notification/
│           ├── relay/
│           ├── server/
│           ├── service/
│           ├── setup/
│           ├── sound/
│           ├── ui/
│           ├── update/
│           ├── util/
│           └── viewmodel/
├── docs/
├── updates/
├── Context Files/
├── firebase.json
├── DATA_SAFETY.md
└── code and legal docs
```

### Top-level notes
- `app/src/main/kotlin/com/pocketcraft/server/` contains the runtime, relay, setup, config, data, and UI layers.
- `app/src/main/cpp/` contains the JNI launcher and a wrapper executable for external JVM bootstrap.
- `app/src/main/assets/` contains the bundled JRE archives, bundled plugin JARs, and a few helper assets.
- `app/src/main/res/` contains launcher assets, fonts, raw sound effects, theme XML, and file provider paths.
- `app/src/main/legal/` contains extracted license notices for bundled Java runtime components.

## 2. Core Files

### Native

#### `app/src/main/cpp/launcher.c`
- Purpose: Boots the embedded JVM in-process and wires the native runtime libraries into the Android process.
- Key functions/classes:
  - `Java_com_pocketcraft_server_NativeLauncher_launchJVM(...)`: JNI entry point used by `NativeLauncher.launchJVM(...)`.
  - `disable_heap_tagging()`: disables tagged heap behavior for JRE compatibility on supported devices.
  - `update_ld_library_path(...)`, `preload_runtime_tree(...)`, `detect_runtime_paths(...)`: set up runtime loading before JVM launch.
- Dependencies:
  - Called from `NativeLauncher.kt`.
  - Uses Android log, JNI, `dlopen`, `dlsym`, and filesystem/runtime-path probing.
- Important constants/values:
  - `TAG = "PocketCraft"`
  - `FULL_VERSION = "21-internal"`
  - `DOT_VERSION = "21"`

#### `app/src/main/cpp/serverwrap.c`
- Purpose: Small wrapper executable that launches the Java binary after setting Android heap-tagging compatibility.
- Key functions/classes:
  - `main(...)`: validates arguments, disables heap tagging, then `execv(...)` into the Java process.
  - `disable_heap_tagging()`: same runtime compatibility helper as above.
- Dependencies:
  - Used as a native shim by `ServerLauncher.kt`.
- Important constants/values:
  - `TAG = "PocketCraftWrap"`

### Runtime / Service Layer

#### `app/src/main/kotlin/com/pocketcraft/server/server/ServerHostService.kt`
- Purpose: Foreground service that owns server process lifecycle, notifications, relay coordination, log parsing, and crash recovery.
- Key functions/classes:
  - `ServerHostService : Service`: Android foreground service entry point.
  - `onStartCommand(...)`: handles `ACTION_START`, `ACTION_STOP`, and `ACTION_RECONNECT_RELAY`.
  - `startServer(...)` path inside `onStartCommand`: resolves JAR, prepares runtime, launches the JVM, and wires callbacks.
  - `stopServer()`: shuts down the hosted JVM cleanly, or forces termination if needed.
  - `resumeServer(versionId)`: restores service state if Android recreates the service.
  - `handleObservedOutputLine(...)`: parses console output and dispatches events.
  - `looksLikeServerReady(line)`: recognizes Paper ready logs.
  - `onServerReady()`: marks server ready and triggers relay startup.
  - `scheduleServerReadyFallback(...)`: promotes readiness if the log parser misses the exact ready line.
  - `publishRelayStatus(...)`, `startRelayStatusHeartbeat(...)`: periodically push player status to the relay.
  - `sendRconStop()`: sends RCON stop when running in-process.
  - `companion object`: constants, runtime-state persistence helpers, and static `start/stop/reconnectRelay` entry points.
- Dependencies:
  - `RelayManager`, `ServerLauncher`, `ServerJarManager`, `ServerFileManager`, `ServerVersionMigrator`, `ConsoleParser`, `AppPreferencesStore`.
  - Reads config from `ServerConfigRepository`.
  - Emits events consumed by `ServerStateHolder`.
- Important constants/values:
  - `ACTION_START`, `ACTION_STOP`, `ACTION_RECONNECT_RELAY`, `ACTION_SERVER_EVENT`
  - `EVENT_OUTPUT`, `EVENT_ERROR`, `EVENT_STOPPED`, `EVENT_SERVER_CRASHED`, `EVENT_CHUNKY_PROGRESS`
  - `RUNTIME_STATE_OFFLINE`, `RUNTIME_STATE_STARTING`, `RUNTIME_STATE_RUNNING`
  - `CHANNEL_SERVER_RUNNING`, `CHANNEL_SERVER_ALERTS`, `CHANNEL_SERVER_TIMEOUT`
  - `STOP_GRACE_PERIOD_MS = 12_000L`
  - `AUTO_RECOVER_WINDOW_MS = 20 * 60 * 1000L`
  - `AUTO_RECOVER_MAX_ATTEMPTS = 3`

#### `app/src/main/kotlin/com/pocketcraft/server/RelayManager.kt`
- Purpose: Manages relay registration, persistent tunnel sockets, Bedrock bridging, and player status posting to the relay backend.
- Key functions/classes:
  - `RelayManager(context)`: relay client/controller.
  - `register()`: POSTs the user ID to `/register` and receives a per-user public port.
  - `initPool(localPort)`: retries tunnel initialization with backoff.
  - `connectTunnelPool(localPort)`: opens the persistent socket pool used by the relay.
  - `notifyPhoneReady(localPort)`: signals the relay that the server is ready and the phone port is live.
  - `postServerStatus(...)`: sends live server/player state to the relay.
  - `startBedrockBridge()` / `stopBedrockBridge()`: start/stop UDP bridge for Bedrock traffic.
  - `disconnect()` / `unregister()`: tear down relay connections.
  - `currentRelaySessionId()`: derives a stable relay user identity.
  - `resolveRouteLocalIp(...)`, `resolvePreferIPv4(...)`, `resolveRouteLocalIp(...)`: endpoint and routing helpers.
  - `companion/data classes`: `RelayAddress`, `TunnelPoolResult`.
- Dependencies:
  - `RelayServers`, `AppPreferences`, `BedrockUdpBridge`, OkHttp, JSON, socket I/O.
  - Uses `RELAY_SECRET` from preferences for authenticated relay traffic.
- Important constants/values:
  - `CONTROL_PORT = 8080`
  - `PHONE_TUNNEL_PORT = 9000`
  - `TARGET_POOL_SIZE = 5`
  - `POOL_REFRESH_FLOOR = 2`
  - `IDLE_REPLENISH_DELAY_MS = 1_500L`
  - `SOCKET_OPEN_STAGGER_MS = 250L`
  - `TUNNEL_HEARTBEAT_INTERVAL_MS = 15_000L`
  - `INITIAL_POOL_READY_TIMEOUT_MS = 12_000L`

#### `app/src/main/kotlin/com/pocketcraft/server/server/ServerLauncher.kt`
- Purpose: Prepares server files, RAM settings, runtime artifacts, and launches the JVM either externally or in-process.
- Key functions/classes:
  - `ServerLauncher(context)`: launch coordinator.
  - `startServer(...)`: prepares EULA/properties/runtime, applies bedrock bridge config, picks RAM, and launches Java.
  - `launchExternalJvm(...)`: launches a wrapper process with the correct library paths.
  - `requestForceStop()` / `sendCommand(...)` / `hasActiveExternalProcess()`: manage the external process.
  - `companion object`: holds `activeExternalProcess`.
- Dependencies:
  - `NativeLauncher`, `JreExtractor`, `ServerFileManager`, `ServerPropertiesHelper`, `PluginManager`, `PlayerDataManager`, `DimensionMigrator`, `AppPreferences`.
- Important constants/values:
  - RAM safety logic based on device total RAM.
  - JRE path checks for `bin/java`, `lib/libjli.so`, `lib/server/libjvm.so`.

#### `app/src/main/kotlin/com/pocketcraft/server/server/ServerJarManager.kt`
- Purpose: Resolves and downloads the correct server JAR for Paper, Purpur, or Fabric.
- Key functions/classes:
  - `fetchAvailableVersions(...)`: retrieves version lists with cache.
  - `resolveJar(...)`: downloads or returns the target server JAR as a flow.
  - `resolvePaperUrl(...)`, `resolvePurpurUrl(...)`, `resolveFabricUrl(...)`: resolve concrete download URLs.
  - `downloadFile(...)`: streaming download helper.
- Dependencies:
  - `VersionCacheManager`, OkHttp, `ServerType`.
- Important constants/values:
  - `TAG = "ServerJarManager"`
  - `USER_AGENT = "PocketCraft/1.0"`
  - Paper API base: `https://api.papermc.io/v2/projects/paper`
  - Purpur API base: `https://api.purpurmc.org/v2/purpur`
  - Fabric meta base: `https://meta.fabricmc.net/v2/versions/game`
  - Blank version guard: throws if `gameVersion.isBlank()`

#### `app/src/main/kotlin/com/pocketcraft/server/server/ServerAddressResolver.kt`
- Purpose: Chooses the best LAN IPv4 address for a user-visible `ip:port` connection string.
- Key functions/classes:
  - `getConnectAddress(port)`: returns `ip:port` if a usable LAN address exists.
  - `getLocalIpAddress()`: returns a best-effort LAN IP.
  - `isSiteLocal(...)`: classifies RFC1918 addresses.
- Dependencies:
  - Java `NetworkInterface`, `Inet4Address`.

### Preferences / Data

#### `app/src/main/kotlin/com/pocketcraft/server/data/preferences/AppPreferences.kt`
- Purpose: SharedPreferences + DataStore wrapper for user identity, relay state, UI prefs, and setup flags.
- Key functions/classes:
  - `AppPreferencesKeys`: DataStore keys for setup, version selection, theme, audio, restart, etc.
  - `AppPreferences(context)`: legacy SharedPreferences-backed state holder.
  - `clearUserId()`, `clearFcmToken()`, `clearRelayPort()`, `clearAccountIdentityData()`: surgical account cleanup helpers.
  - `recordAppLaunch()`, `setFirstLaunchAfterOnboarding(...)`, `getLastUpdateCheckTime()`, `setLastUpdateCheckTime(...)`.
  - `AppPreferencesStore`: DataStore-backed flows and setters.
  - `getServerVersionFlow(...)` / `setServerVersion(...)`: aliases for selected version APIs.
- Dependencies:
  - `RelayServers`, DataStore Preferences APIs, coroutines flow.
- Important constants/values:
  - `RELAY_SECRET = "e7f5fbdda85c265419e519454f8d54643930116b89a1b58dcb2b86f91889d3d3"`
  - Default selected version / server type / world / theme / restart values.

#### `app/src/main/kotlin/com/pocketcraft/server/service/PlayerDataManager.kt`
- Purpose: Handles player UUID mapping, offline UUID migration, Floodgate prefix tracking, and playerdata/stat file paths.
- Key functions/classes:
  - `updateActivePlayers(...)`, `updateSessionPlayers(...)`, `updateInventoryJson(...)`.
  - `getOfflineUuid(username)`: computes offline UUIDs.
  - `warnIfFloodgateUsernamePrefixChanged(...)`: warns when Floodgate prefix changes.
  - `checkForOrphanedData(...)`: detects multiple UUIDs for one Bedrock player.
  - `fixOfflineUuids(...)`: migrates online UUID files to offline UUIDs.
  - File helpers: `getStatsFile(...)`, `getPlayerDataFile(...)`, `getAdvancementsFile(...)`, `getOpsFile(...)`, `getLevelDataFile(...)`, `getWhitelistFile(...)`.
- Dependencies:
  - JSON, gzip, filesystem, `ServerFileManager`.

#### `app/src/main/kotlin/com/pocketcraft/server/service/PluginManager.kt`
- Purpose: Downloads, inspects, installs, and manages server plugins and built-in bridge plugins.
- Key functions/classes:
  - `RemoteCatalogItem`, `ContentType`, `ArchiveKind`, `ArchiveMetadata`, `CachedCatalogResult`, `DownloadCandidate`.
  - `getPluginsDir(...)`, `getGeyserConfigFile(...)`, `getFloodgateConfigFile(...)`, `getModsDir(...)`, `getResourcePacksDir(...)`.
  - `listPlugins(...)`, `ensureContentDirs(...)`, `getContentDir(...)`.
  - `ensureBedrockBridgePlugins(...)`: ensures Geyser/Floodgate/ViaVersion are installed.
- Dependencies:
  - OkHttp, JSON, `BuildConfig`, `Plugin` model, content directories.
- Important constants/values:
  - `MODRINTH_BASE_URL = "https://api.modrinth.com/v2"`
  - `HANGAR_BASE_URL = "https://hangar.papermc.io/api/v1"`
  - `GEYSERMC_DOWNLOAD_BASE_URL = "https://download.geysermc.org/v2/projects"`
  - Managed bridge IDs include `geyser`, `floodgate`, `viaversion`, `chunky`.

#### `app/src/main/kotlin/com/pocketcraft/server/service/ServerFileManager.kt`
- Purpose: Owns server directory layout, `server.properties`, EULA, runtime artifact prep, and launch target persistence.
- Key functions/classes:
  - `getServerDir(...)`, `getSharedJarDir(...)`, `getServerJarFile(...)`, `isServerJarReady(...)`.
  - `prepareEula(...)`, `prepareServerProperties(...)`, `prepareRuntimeArtifacts(...)`.
  - `persistLaunchTarget(...)`.
  - `LaunchMode` enum.
- Dependencies:
  - `ServerPropertiesHelper`, `ServerType`.
- Important constants/values:
  - Forced runtime config includes `server-port = 25565`, `enable-rcon = true`, `rcon.port = 25575`, `rcon.password = "pocketcraft-internal-rcon"`.
  - `resource-pack-prompt` is hardcoded in the prepared properties.

#### `app/src/main/kotlin/com/pocketcraft/server/service/ServerPropertiesHelper.kt`
- Purpose: Reads/writes `server.properties` and enforces PocketCraft defaults.
- Key functions/classes:
  - `readProperties(...)`, `saveProperties(...)`, `getProperty(...)`, `setProperty(...)`.
- Dependencies:
  - `File`, `Properties`.
- Important constants/values:
  - `RELAY_READY_COMPRESSION_THRESHOLD = 256`
  - `DEFAULT_VIEW_DISTANCE = 6`
  - `DEFAULT_SIMULATION_DISTANCE = 4`
  - `RELAY_READY_ENTITY_BROADCAST_PERCENT = 75`
  - `POCKETCRAFT_JOIN_MESSAGE_TEXT = "Hosted on PocketCraft! Enjoy and join our Discord using the link already defined in the code."`
  - `POCKETCRAFT_JOIN_MESSAGE_URL = "https://discord.gg/NGPzXFYp"`

#### `app/src/main/kotlin/com/pocketcraft/server/service/ConsoleParser.kt`
- Purpose: Parses server stdout into structured console messages and extracts gameplay events.
- Key functions/classes:
  - `ChunkyProgress(current, total, percent)`
  - `ConsoleParser.parse(...)`, `isDone(...)`, `parseTps(...)`, `parseJoin(...)`, `parseLeave(...)`, `isPreparingStartRegion(...)`, `parseChunkyProgress(...)`, `stripAnsi(...)`.
- Dependencies:
  - `ConsoleMessage`, `LogLevel`.
- Important constants/values:
  - Regexes for `Done (...)!`, `Preparing start region`, `Chunky` progress, joins/leaves, TPS, chat, and log levels.

#### `app/src/main/kotlin/com/pocketcraft/server/service/VersionCatalog.kt`
- Purpose: Fetches a clean list of stable Minecraft versions, preferring Paper and falling back to Mojang manifest.
- Key functions/classes:
  - `fetchStableVersions(limit)`.
- Dependencies:
  - OkHttp, PaperMC API, Mojang version manifest.
- Important constants/values:
  - `versionRegex = ^\\d+\\.\\d+(\\.\\d+)?$`

#### `app/src/main/kotlin/com/pocketcraft/server/service/VersionCacheManager.kt`
- Purpose: Simple in-memory + disk cache for version lists and other serialized values.
- Key functions/classes:
  - `CacheEntry<T>`, `get(...)`, `put(...)`, `clear(...)`.
- Dependencies:
  - Gson, app cache directory.

#### `app/src/main/kotlin/com/pocketcraft/server/service/ServerVersionMigrator.kt`
- Purpose: Migrates world, metadata, whitelist, ops, and usercache data between server versions.
- Key functions/classes:
  - `MigrationResult`.
  - `migrateActiveWorldIfNeeded(...)`.
  - `mergeWorldMetadata(...)`, `migratePhotoIfNeeded(...)`, `migrateWorldPluginProfile(...)`, `mergeNamedPlayerList(...)`, `mergeUserCache(...)`.
- Dependencies:
  - `ServerPropertiesHelper`, filesystem, JSON, URI handling.

#### `app/src/main/kotlin/com/pocketcraft/server/service/DimensionMigrator.kt`
- Purpose: Normalizes dimension folder layout for Bukkit/Paper vs Fabric-style worlds.
- Key functions/classes:
  - `syncDimensionsForServerType(...)`.
  - `copyOrMoveDir(...)`.
- Dependencies:
  - `ServerFileManager`, `ServerPropertiesHelper`, `ServerType`.

#### `app/src/main/kotlin/com/pocketcraft/server/service/ModpackManager.kt`
- Purpose: Searches, resolves, downloads, and sanitizes modpack server installations.
- Key functions/classes:
  - `ModLoader`, `Source`, `ModpackCatalogItem`.
  - `installModpack(...)`, `sanitizeInstalledModsForServer(...)`, `searchModpacks(...)`.
  - `resolveModpack(...)` and loader detection/manifest helpers.
- Dependencies:
  - Modrinth API, CurseForge API, Fabric meta API, `ServerFileManager`, `JreExtractor`.

#### `app/src/main/kotlin/com/pocketcraft/server/setup/JreExtractor.kt`
- Purpose: Extracts and normalizes the bundled JRE into app-private storage.
- Key functions/classes:
  - `getJreDir(...)`, `getJavaBinary(...)`, `isExtracted(...)`, `extractIfNeeded(...)`.
  - Layout helpers: `hasRequiredRuntimeFiles(...)`, `hasExpandedRuntimeLayout(...)`, `hasComponentRuntimeLayout(...)`, `extractComponentRuntime(...)`, `abiArchiveName(...)`, `runtimeArchDirName(...)`, `fixPermissions(...)`, `copyAssetFolder(...)`, `extractTarXzAsset(...)`, `extractTarXzStream(...)`.
- Dependencies:
  - Apache Commons Compress, XZ, assets API.
- Important constants/values:
  - `ASSET_DIR = "jre-runtime"`
  - `VERSION_TAG = "jre_v3_extracted"`

#### `app/src/main/kotlin/com/pocketcraft/server/setup/JreDownloader.kt`
- Purpose: Downloads a known-good external JRE archive for Android device ABIs.
- Key functions/classes:
  - `JreDownloader(client)`
  - `download(targetDir): Flow<Int>`
- Dependencies:
  - OkHttp, coroutine flow, ABI detection.

#### `app/src/main/kotlin/com/pocketcraft/server/setup/SetupWorker.kt`
- Purpose: WorkManager worker that performs initial setup/install tasks in the background.
- Key functions/classes:
  - `doWork()`, `setProgressSync(...)`, `data(...)`.
- Important constants/values:
  - `PROGRESS_KEY`, `PROGRESS_PERCENT`, `STEP_KEY`, `WORLD_SEED_KEY`, `SERVER_VERSION_KEY`, `WORLD_NAME_KEY`.

#### `app/src/main/kotlin/com/pocketcraft/server/WorldImporter.kt`
- Purpose: Imports zipped worlds and normalizes restored backups.
- Key functions/classes:
  - `importWorld(...)`, `normalizeRestoredServerBackup(...)`.
  - `fixWorldStructureAndMigrate(...)`, `migrateDimensionsToTargetStructure(...)`, `mapImportedDimensionName(...)`, `mergeDirectoryContents(...)`, `ensureRestoredServerProperties(...)`, `moveRootLevelWorldContentIntoTarget(...)`.
- Dependencies:
  - `ServerFileManager`, `PlayerDataManager`, `ZipFile`, `ServerType`.

### App / UI Layer

#### `app/src/main/kotlin/com/pocketcraft/server/PocketCraftApp.kt`
- Purpose: Application class that initializes Firebase, notification channels, Hilt/WorkManager, and a process-wide coroutine scope.
- Key functions/classes:
  - `PocketCraftApp : Application(), Configuration.Provider`
  - `applicationScope`: app-wide `CoroutineScope(SupervisorJob() + Dispatchers.IO)`
  - `onCreate()`, `onTerminate()`, `currentProcessName()`
- Dependencies:
  - Firebase, Hilt, WorkManager, `NotificationChannel`.

#### `app/src/main/kotlin/com/pocketcraft/server/MainActivity.kt`
- Purpose: Main Compose host that gates onboarding, initializes runtime, applies theme, and launches `PocketCraftApp`.
- Key functions/classes:
  - `MainActivity : ComponentActivity`
  - `onStart()`, `onStop()`, `onCreate(...)`
  - `companion object { isAppInForeground }`
- Dependencies:
  - `JreExtractor`, `OnboardingActivity`, `PocketCraftApp`, `SplashScreen`, `ErrorScreen`, `FirebaseAnalyticsManager`, `ThemePreferenceStore`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/navigation/PocketNavigation.kt`
- Purpose: Defines the app’s bottom tabs and top bar chrome.
- Key functions/classes:
  - `PocketTab` enum.
  - `PocketTopBar(...)`, `PocketBottomNav(...)`.
- Dependencies:
  - `PocketColors`, Material3, launcher icon drawable.
- Important constants/values:
  - Relay hosts shown in the top bar: `play.pocketcraft.online` and `mine.pocketcraft.online`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/PocketCraftAppScreen.kt`
- Purpose: Main in-app router that switches between home, console, players, storage, mods, and settings.
- Key functions/classes:
  - `Screen` enum.
  - `PocketCraftApp(...)`
  - `requestVersionChange(...)`, `applyVersionChange(...)`, `discardPendingVersionChange(...)`
  - helpers for runtime downloads and version comparison.
- Dependencies:
  - `ServerStateHolder`, `ServerType`, `PocketNavigation`, `AppPreferencesStore`, `Context.findActivity()`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/ServerStateHolder.kt`
- Purpose: Compose-side state holder for a single server instance; tracks runtime state, logs, players, backups, worlds, and relay status.
- Key functions/classes:
  - `ServerStateHolder(context, versionId)`
  - `startServer(isRestart: Boolean = false)`, `restartServer()`, `stopServer()`, `reconnectRelay()`
  - broadcast receiver handling of `ACTION_SERVER_EVENT`
  - helpers for startup progress, player sync, backups, world management, and notifications.
- Dependencies:
  - `ServerHostService`, `ServerFileManager`, `ServerPropertiesHelper`, `PluginManager`, `ConsoleParser`, `SoundManager`, `NotificationHelper`, `PlayerDataManager`.
- Important constants/values:
  - `DEFAULT_SERVER_DESCRIPTION = "Hosted on Pocketcraft !"`
  - `POCKETCRAFT_JOIN_MESSAGE_TEXT` and `POCKETCRAFT_JOIN_MESSAGE_URL`
  - `singleServerPort = 25565`

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/ConsoleScreen.kt`
- Purpose: Server console/home screen showing server status, start controls, runtime cards, and live log console.
- Key functions/classes:
  - `ConsoleScreen(...)`
  - UI helpers for address cards, log view, seed dialog, Bedrock help, etc.
- Dependencies:
  - `ServerStateHolder`, `ServerHostService.serverReadyState`, `ServerFileManager`, `VersionCatalog`, `AppPreferencesStore`, `RamUtils`, `PocketColors`, `collectAsStateWithLifecycle()`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/ServerScreen.kt`
- Purpose: High-level screen shell with tab navigation and modal screens.
- Key functions/classes:
  - `ServerScreen(...)`
  - navigation helpers `navigateToTab(...)`, `goBack()`, `openWorldSetup(...)`.
- Dependencies:
  - `PocketTopBar`, `PocketBottomNav`, `SplashScreen`, `WorldSetupScreen`, `LegalCenterScreen`, `ServerStateHolder`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/SettingsScreen.kt`
- Purpose: Settings UI for gameplay, UI, legal, version management, and local maintenance actions.
- Key functions/classes:
  - `SettingsScreen(...)`
  - `SettingsSection(...)`, `SimpleLegalLink(...)`, `SettingsToggleRow(...)`, `SettingsInputRow(...)`, `SettingsDropdownRow(...)`, `SettingsSliderRow(...)`, `SettingsActionRow(...)`.
  - `deleteInstalledVersions(...)`, `scanInstalledVersions(...)`, `openExternalUrl(...)`.
- Dependencies:
  - `AppPreferences`, `AppPreferencesStore`, `ServerConfigRepository`, `ThemePreferenceStore`, `RelayServers`, `PocketColors`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/ServerTypeVersionBottomSheet.kt`
- Purpose: Bottom sheet for choosing server type and version.
- Key functions/classes:
  - `ServerTypeVersionBottomSheet(...)`.
- Dependencies:
  - `ServerTypeVersionViewModel`, `ServerJarManager`, `NetworkUtils`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/WorldsScreen.kt`
- Purpose: World management UI, including backups, uploads, deletions, and dimension handling.
- Key functions/classes:
  - `WorldsScreen(...)`
  - `SwipeableWorldSlotItem(...)`, `BackupItem(...)`, `DimensionUploadRow(...)`, `SectionLabel(...)`.
- Dependencies:
  - `ServerStateHolder`, `WorldImporter`, `ServerFileManager`, `PlayerDataManager`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/WorldSetupScreen.kt`
- Purpose: Creates/imports worlds and finalizes initial world configuration.
- Key functions/classes:
  - `WorldSetupScreen(...)`
  - `finishSetup()`, `SurfaceInfoText()`, `parseCreatedWorldName(...)`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/StorageScreen.kt`
- Purpose: File browser and storage maintenance screen for server files.
- Key functions/classes:
  - `StorageScreen(...)`
  - `ServerFilesBrowser(...)`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/PluginsHubScreen.kt`
- Purpose: Unified plugins/mods/resource-packs discovery and local install screen.
- Key functions/classes:
  - `PluginsHubScreen(...)`, `SearchBar(...)`, `ContentDetailDialog(...)`, `LocalDetailCardContent(...)`, `RemoteDetailCardContent(...)`.
- Dependencies:
  - `PluginManager`, `ModrinthClient`, `RateLimitedModrinthClient`, `PocketColors`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/DownloadedPluginsScreen.kt`
- Purpose: Shows downloaded plugin content outside the main hub.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/PlayersScreen.kt`
- Purpose: Player list, whitelist, and player actions.
- Key functions/classes:
  - `PlayersScreen(...)`, `PlayersOnlineTab(...)`, `PlayerOnlineCard(...)`, `WhitelistTab(...)`, `AddPlayerDialog(...)`, `PlayersListTab(...)`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/PlayerDetailScreen.kt`
- Purpose: Detailed player profile, stats, inventory, and actions.
- Key functions/classes:
  - `PlayerDetailScreen(...)`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/BackupsScreen.kt`
- Purpose: Backup management UI for world/server backups.
- Key functions/classes:
  - `BackupsScreen(...)`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/AppChangelogScreen.kt`
- Purpose: Shows app changelog/release notes.
- Key functions/classes:
  - `AppChangelogScreen(...)`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/RelayRegionScreen.kt`
- Purpose: Region selection screen for choosing relay host.
- Key functions/classes:
  - `RelayRegionScreen(...)`, `RelayRegionCard(...)`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/LegalCenterScreen.kt`
- Purpose: Central legal links screen for privacy, terms, and notices.
- Key functions/classes:
  - `LegalCenterScreen(...)`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/AppUpdateScreen.kt`
- Purpose: Shows app update availability and install options.
- Key functions/classes:
  - `AppUpdateScreen(...)`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/SplashScreen.kt`
- Purpose: Simple loading splash shown during runtime initialization.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/ErrorScreen.kt`
- Purpose: Full-screen retry/error state for startup failures.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/screens/OnboardingViewModel.kt`
- Purpose: Tracks onboarding completion state and posts it into app-wide state.
- Key functions/classes:
  - `OnboardingViewModel`
  - `checkOnboardingStatus()`, `completeOnboarding()`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/onboarding/OnboardingActivity.kt`
- Purpose: Full-screen onboarding flow with relay selection and setup steps.
- Key functions/classes:
  - `OnboardingActivity : ComponentActivity`
  - `completeOnboarding()`, `start(context)`.
  - `OnboardingScreen(...)`, `RelayRegionOnboardingScreen(...)`, `TopHeader(...)`, `WelcomeScreen(...)`, `HowItWorksScreen(...)`, `ImportScreen(...)`, and other onboarding step composables.
- Dependencies:
  - `AppPreferencesStore`, `RelayServers`, `PocketColors`, `ThemePreference`, haptics, Firebase analytics.

### UI Components

#### `app/src/main/kotlin/com/pocketcraft/server/ui/components/BroadcastBanner.kt`
- Purpose: Banner for broadcast announcements.
- Key functions/classes:
  - `BroadcastBanner(...)`, `normalizeBroadcastBody(...)`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/components/ChunkyProgressBanner.kt`
- Purpose: Shows Chunky pregeneration progress.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/components/DuoButton.kt`
- Purpose: Shared stylized primary/danger button component.
- Key functions/classes:
  - `DuoButtonVariant`, `DuoButton(...)`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/components/DuoTextFieldStyle.kt`
- Purpose: Shared text-field shapes and color styles.
- Key functions/classes:
  - `duoTextFieldShape()`, `duoTextFieldColors()`, `duoOutlinedTextFieldColors()`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/components/DuoToggle.kt`
- Purpose: Shared toggle composable.
- Key functions/classes:
  - `DuoToggle(...)`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/components/FlatEmojiIcon.kt`
- Purpose: Renders emoji icons with consistent sizing/alignment.
- Key functions/classes:
  - `FlatEmojiIcon(...)`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/components/GameCard.kt`
- Purpose: Game/server metadata card composable.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/components/HealthBar.kt`
- Purpose: Player/server health bar composable.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/components/InventoryPreview.kt`
- Purpose: Renders inventory grids and item slots.
- Key functions/classes:
  - `InventorySlotCell(...)`, `InventoryRow(...)`, `InventorySection(...)`, `InventoryPreview(...)`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/components/PlayerActionButton.kt`
- Purpose: Player action buttons for kick/ban/message-like actions.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/components/PlayerCard.kt`
- Purpose: Player card UI.
- Key functions/classes:
  - `PlayerCardAction`, `PlayerCard(...)`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/components/PluginsSection.kt`
- Purpose: Section component for plugin lists and discovery.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/components/PocketCraftCard.kt`
- Purpose: Standard PocketCraft card surface.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/components/PocketIcons.kt`
- Purpose: Custom Minecraft-style icons.
- Key functions/classes:
  - `PocketWorldIcon(...)`, `PocketModsIcon(...)`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/components/ServerDescriptionField.kt`
- Purpose: Server description text field.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/components/ServerPhotoUpload.kt`
- Purpose: Upload and preview server/world photos.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/components/StatusBadge.kt`
- Purpose: Badge component for status labels.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/components/VersionUpgradeCard.kt`
- Purpose: Card shown when an app/server upgrade is available.

### Config / Update / Broadcast / Analytics

#### `app/src/main/kotlin/com/pocketcraft/server/config/RelayServersConfig.kt`
- Purpose: Declares supported relay regions and their hostnames.
- Key functions/classes:
  - `RelayServerConfig`, `RelayServers`.
  - `getBestForTimeZone(...)`, `getByHost(...)`, `getDisplayName(...)`.
- Important constants/values:
  - `play.pocketcraft.online` = Global/Singapore relay
  - `mine.pocketcraft.online` = Asia/Mumbai relay

#### `app/src/main/kotlin/com/pocketcraft/server/config/RemoteConfigManager.kt`
- Purpose: Loads Firebase Remote Config flags used to show/hide social buttons.

#### `app/src/main/kotlin/com/pocketcraft/server/broadcast/BroadcastManager.kt`
- Purpose: Fetches Firestore broadcast banners, caches them locally, and provides offline fallbacks.
- Key functions/classes:
  - `BroadcastMessage`
  - `getBroadcastsFlow(...)`, `initRemoteConfig(...)`, `refreshRemoteConfig(...)`, `defaultOfflineBroadcast()`.
- Important constants/values:
  - `BROADCAST_REMOTE_CONFIG_KEY = "broadcast_banner"`

#### `app/src/main/kotlin/com/pocketcraft/server/broadcast/FeedbackPromptCenter.kt`
- Purpose: App-wide state holder for deferred feedback prompts.

#### `app/src/main/kotlin/com/pocketcraft/server/broadcast/PocketCraftMessagingService.kt`
- Purpose: Firebase Cloud Messaging integration for token capture and push handling.

#### `app/src/main/kotlin/com/pocketcraft/server/analytics/FirebaseAnalyticsManager.kt`
- Purpose: Wraps Firebase Analytics event logging and user properties.
- Key functions/classes:
  - `initialize(...)`, `setCollectionEnabled(...)`, `logEvent(...)`, `setUserProperty(...)`.
  - Convenience events like `logServerStarted(...)`, `logServerStopped(...)`, `logThemeChanged(...)`, etc.

#### `app/src/main/kotlin/com/pocketcraft/server/update/GitHubUpdateChecker.kt`
- Purpose: Checks for app updates from GitHub or hosted manifests and compares version strings.

#### `app/src/main/kotlin/com/pocketcraft/server/update/GitHubApkInstaller.kt`
- Purpose: Downloads and installs APK updates.

#### `app/src/main/kotlin/com/pocketcraft/server/notification/NotificationHelper.kt`
- Purpose: Creates the silent online notification channel and posts the server-online notification.
- Important constants/values:
  - `CHANNEL_ID = "pocketcraft_server_silent"`
  - `NOTIFICATION_ID_ONLINE = 1001`

#### `app/src/main/kotlin/com/pocketcraft/server/sound/SoundManager.kt`
- Purpose: Plays in-app tones for server start/stop events.

#### `app/src/main/kotlin/com/pocketcraft/server/di/AppModule.kt`
- Purpose: Hilt dependency provider for OkHttp, Room, Retrofit, and repositories.

#### `app/src/main/kotlin/com/pocketcraft/server/network/ModrinthClient.kt`
- Purpose: Simple Modrinth API client for search, versions, and download.

#### `app/src/main/kotlin/com/pocketcraft/server/network/RateLimitedModrinthClient.kt`
- Purpose: Cached, rate-limited Modrinth API client with retry/deduplication.

#### `app/src/main/kotlin/com/pocketcraft/server/feedback/FeedbackService.kt`
- Purpose: Feedback collection and submission service.

#### `app/src/main/kotlin/com/pocketcraft/server/relay/BedrockUdpBridge.kt`
- Purpose: UDP bridge used to move Bedrock frames through the relay path.

### Setup / Utility / ViewModels / Data Models

#### `app/src/main/kotlin/com/pocketcraft/server/viewmodel/ServerTypeVersionViewModel.kt`
- Purpose: Owns server type/version selection and downloaded-version state for the version picker UI.

#### `app/src/main/kotlin/com/pocketcraft/server/viewmodel/BroadcastViewModel.kt`
- Purpose: Converts `BroadcastManager` flows into UI state and handles dismissals.

#### `app/src/main/kotlin/com/pocketcraft/server/util/NetworkUtils.kt`
- Purpose: Detects online/offline state via active network capabilities.

#### `app/src/main/kotlin/com/pocketcraft/server/util/RamUtils.kt`
- Purpose: Reads total and used RAM from Android `ActivityManager`.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/util/ThemePreference.kt`
- Purpose: Stores and resolves theme preference.

#### `app/src/main/kotlin/com/pocketcraft/server/ui/util/Haptics.kt`
- Purpose: Shared haptic helper.

#### `app/src/main/kotlin/com/pocketcraft/server/data/api/PaperMcApiService.kt`
- Purpose: Retrofit interface and DTOs for PaperMC version/build data.

#### `app/src/main/kotlin/com/pocketcraft/server/data/api/MojangApiService.kt`
- Purpose: Retrofit interface and DTOs for Mojang version manifest data.

#### `app/src/main/kotlin/com/pocketcraft/server/data/db/VersionDatabase.kt`
- Purpose: Room database for cached Minecraft versions.

#### `app/src/main/kotlin/com/pocketcraft/server/data/db/VersionDao.kt`
- Purpose: Room DAO for version storage and retrieval.

#### `app/src/main/kotlin/com/pocketcraft/server/data/repository/VersionRepository.kt`
- Purpose: Fetches, caches, and downloads Minecraft server versions and JARs.

#### `app/src/main/kotlin/com/pocketcraft/server/data/repository/ServerConfigRepository.kt`
- Purpose: Reads/writes server profiles, `server.properties`, and `spigot.yml`.

#### `app/src/main/kotlin/com/pocketcraft/server/data/model/*`
- Purpose:
  - `ServerType`: server families (`PAPER`, `PURPUR`, `FABRIC`, `MODPACK`, `CUSTOM_JAR`).
  - `ServerState`: UI runtime state sealed class.
  - `ServerConfig`: full configuration mirror of server.properties.
  - `MCVersion`: cached version entity.
  - `VersionDetail`, `Downloads`, `ServerDownload`: PaperMC download DTOs.
  - `PlayerInfo`: live player record.
  - `Plugin`: local plugin record.
  - `ConsoleMessage`: parsed console line model.
  - `DownloadState`: download-state sealed class.
  - `ServerStats`, `ServerProfileSummary`, `WorldDetails`: home-screen summary models.

### Files referenced by prompt but not found in this repo

- `HomeScreen.kt`: NOT FOUND
- `NetworkMonitor.kt`: NOT FOUND
- `DriveBackupManager.kt`: NOT FOUND
- `Analytics.kt`: NOT FOUND
- `ServerConsole.kt`: NOT FOUND
- `PlayerDataManager.kt`: FOUND at `app/src/main/kotlin/com/pocketcraft/server/service/PlayerDataManager.kt`
- `WorldImporter.kt`: FOUND at `app/src/main/kotlin/com/pocketcraft/server/WorldImporter.kt`

## 3. AndroidManifest Summary

- Package/application namespace: `com.pocketcraft.server`
- Application class: `.PocketCraftApplication`
- Minimum SDK: `26` from Gradle
- Target SDK: `35` from Gradle
- Compile SDK: `35` from Gradle

### Permissions
- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.ACCESS_WIFI_STATE`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_DATA_SYNC`
- `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`
- `android.permission.POST_NOTIFICATIONS`
- `android.permission.VIBRATE`
- `android.permission.REQUEST_INSTALL_PACKAGES`
- `android.permission.RECEIVE_BOOT_COMPLETED`
- `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `android.permission.WAKE_LOCK`

### Activities
- `.MainActivity`
- `.ui.onboarding.OnboardingActivity`

### Services
- `.server.ServerHostService`
  - `android:foregroundServiceType="dataSync|specialUse"`
  - `android:process=":server"`
  - `android:stopWithTask="false"`
- `.broadcast.PocketCraftMessagingService`

### Providers
- `androidx.startup.InitializationProvider`
  - WorkManager initializer explicitly removed from startup.
- `androidx.core.content.FileProvider`

### Other manifest notes
- `android:usesCleartextTraffic="true"`
- `android:allowBackup="false"`
- `android:allowNativeHeapPointerTagging="false"`
- `android:memtagMode="off"`

## 4. Gradle & Dependencies

### App config
- `applicationId = "com.pocketcraft.server"`
- `versionName = "0.4.0-Beta"`
- `versionCode = (System.currentTimeMillis() / 60000).toInt()`
- `namespace = "com.pocketcraft.server"`
- `compileSdk = 35`
- `targetSdk = 35`
- `minSdk = 26`
- `buildToolsVersion = "35.0.0"`

### Build features
- `compose = true`
- `buildConfig = true`

### Native build
- `externalNativeBuild.cmake.path = app/src/main/cpp/CMakeLists.txt`
- `externalNativeBuild.cmake.version = "3.22.1"`
- NDK ABIs:
  - `arm64-v8a`
  - `armeabi-v7a`
- `androidResources.noCompress` includes:
  - `jar`
  - `jks`
  - `xz`
  - `gz`

### Build types
- `release`
  - `isMinifyEnabled = true`
  - `isShrinkResources = true`
  - `isDebuggable = false`
  - uses `proguard-android-optimize.txt` + `proguard-rules.pro`

### Dependencies
- `org.apache.commons:commons-compress:1.26.1`
- `org.tukaani:xz:1.9`
- `androidx.core:core-ktx` via version catalog
- `androidx.lifecycle:lifecycle-runtime-ktx`
- `androidx.lifecycle:lifecycle-runtime-compose:2.7.0`
- `androidx.lifecycle:lifecycle-viewmodel-compose`
- `androidx.lifecycle:lifecycle-service`
- `androidx.activity:activity-compose`
- `androidx.splashscreen`
- `androidx.navigation:navigation-compose`
- `androidx.compose` BOM + `ui`, `ui.graphics`, `ui.tooling.preview`, `ui.text.google.fonts`, `material3`, `material.icons.extended`
- `com.google.dagger:hilt-android` / `hilt-compiler`
- `hilt-navigation-compose`
- `hilt-work` / `hilt-work-compiler`
- `androidx.work:work-runtime-ktx`
- `com.squareup.okhttp3:okhttp`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android`
- `androidx.room:room-runtime` / `room-ktx` / `room-compiler`
- `androidx.datastore:datastore-preferences`
- `retrofit2:retrofit`
- `retrofit2:converter-gson`
- `com.google.code.gson:gson`
- `io.coil-kt:coil-compose`
- `com.google.firebase:firebase-bom`
- `com.google.firebase:firebase-analytics`
- `com.google.firebase:firebase-crashlytics`
- `com.google.firebase:firebase-firestore-ktx`
- `com.google.firebase:firebase-messaging-ktx`
- `com.google.firebase:firebase-config-ktx`
- `com.google.firebase:firebase-inappmessaging-display-ktx`
- `com.google.zxing:core:3.5.3`

### Root build
- Uses `com.github.jk1.dependency-license-report` plugin.
- Generates markdown and JSON license inventories for `:app`.

## 5. Assets Inventory

| Path | Size (bytes) | Purpose |
|---|---:|---|
| `app/src/main/assets/README.md` | 2235 | Notes about bundled assets/runtime layout |
| `app/src/main/assets/adi-registration.properties` | 27 | Small registration/config properties file |
| `app/src/main/assets/components/jre/bin-arm.tar.xz` | 3518644 | ARM component JRE archive |
| `app/src/main/assets/components/jre/bin-arm64.tar.xz` | 5394800 | ARM64 component JRE archive |
| `app/src/main/assets/components/jre/bin-x86.tar.xz` | 5613828 | x86 component JRE archive |
| `app/src/main/assets/components/jre/bin-x86_64.tar.xz` | 6384060 | x86_64 component JRE archive |
| `app/src/main/assets/components/jre/universal.tar.xz` | 23503528 | Shared JRE core payload |
| `app/src/main/assets/components/jre/version` | 41 | Version marker for bundled JRE |
| `app/src/main/assets/connect-spigot.jar` | 26440017 | Bundled server JAR / connector payload |
| `app/src/main/assets/default_plugins/PocketCraftChunkLoader.jar` | 2054 | Bundled chunk loader plugin |
| `app/src/main/assets/inventory.html` | 6446 | HTML inventory UI / preview asset |
| `app/src/main/assets/jre-runtime/bin-arm.tar.xz` | 4318124 | Prebuilt ARM runtime archive |
| `app/src/main/assets/jre-runtime/bin-arm64.tar.xz` | 5397948 | Prebuilt ARM64 runtime archive |
| `app/src/main/assets/jre-runtime/bin-x86.tar.xz` | 5625296 | Prebuilt x86 runtime archive |
| `app/src/main/assets/jre-runtime/bin-x86_64.tar.xz` | 6434740 | Prebuilt x86_64 runtime archive |
| `app/src/main/assets/jre-runtime/universal.tar.xz` | 23515864 | Shared runtime core payload |
| `app/src/main/assets/jre-runtime/version` | 41 | Runtime version marker |
| `app/src/main/assets/social/discord.png` | 56452 | Discord social icon |
| `app/src/main/assets/social/instagram.png` | 42247 | Instagram social icon |

### Resource inventory

#### Drawables
- `drawable-nodpi/minecraft_api_diamond_pickaxe.png`
- `drawable-nodpi/minecraft_api_diamond_pickaxe_hd.png`
- `drawable/backup/ic_launcher_foreground_creeper_backup.xml`
- `drawable/backup/ic_launcher_round_creeper_backup.xml`
- `drawable/creeper_walking.gif`
- `drawable/discord_social.png`
- `drawable/ic_bg.xml`
- `drawable/ic_diamond_pickaxe.xml`
- `drawable/ic_discord.xml`
- `drawable/ic_fg.xml`
- `drawable/ic_instagram.xml`
- `drawable/ic_launcher_background.xml`
- `drawable/ic_launcher_creeper.xml`
- `drawable/ic_launcher_foreground.xml`
- `drawable/ic_launcher_foreground_circle.xml`
- `drawable/ic_launcher_foreground_circle_inset.xml`
- `drawable/ic_launcher_foreground_hex.xml`
- `drawable/ic_launcher_foreground_hex_inset.xml`
- `drawable/ic_launcher_foreground_inset.xml`
- `drawable/ic_launcher_foreground_square.xml`
- `drawable/ic_launcher_foreground_square_inset.xml`
- `drawable/ic_mods_pixel.xml`
- `drawable/ic_netherite_chestplate_hd.xml`
- `drawable/ic_pickaxe_pixel.xml`
- `drawable/ic_unknown_item.xml`
- `drawable/ic_world_pixel.xml`

#### Fonts
- `font/monocraft.ttf`
- `font/plus_jakarta_sans_variable.ttf`

#### Raw
- `raw/server_ready.ogg`
- `raw/startup_chime.ogg`

#### Mipmap
- `mipmap-anydpi-v26/ic_launcher.xml`
- `mipmap-anydpi-v26/ic_launcher_circle.xml`
- `mipmap-anydpi-v26/ic_launcher_hex.xml`
- `mipmap-anydpi-v26/ic_launcher_round.xml`
- `mipmap-anydpi-v26/ic_launcher_square.xml`

#### Values
- `values/colors.xml`
- `values/strings.xml`
- `values/themes.xml`

#### XML
- `xml/backup_rules.xml`
- `xml/data_extraction_rules.xml`
- `xml/file_paths.xml`

### Legal runtime inventory
- `app/src/main/legal/java.base/ADDITIONAL_LICENSE_INFO`
- `app/src/main/legal/java.base/ASSEMBLY_EXCEPTION`
- `app/src/main/legal/java.base/LICENSE`
- `app/src/main/legal/java.base/aes.md`
- `app/src/main/legal/java.base/asm.md`
- `app/src/main/legal/java.base/c-libutl.md`
- `app/src/main/legal/java.base/cldr.md`
- `app/src/main/legal/java.base/icu.md`
- `app/src/main/legal/java.base/public_suffix.md`
- `app/src/main/legal/java.base/siphash.md`
- `app/src/main/legal/java.base/unicode.md`
- `app/src/main/legal/java.desktop/colorimaging.md`
- `app/src/main/legal/java.desktop/giflib.md`
- `app/src/main/legal/java.desktop/harfbuzz.md`
- `app/src/main/legal/java.desktop/jpeg.md`
- `app/src/main/legal/java.desktop/lcms.md`
- `app/src/main/legal/java.desktop/libpng.md`
- `app/src/main/legal/java.desktop/mesa3d.md`
- `app/src/main/legal/java.desktop/pipewire.md`
- `app/src/main/legal/java.desktop/xwd.md`
- `app/src/main/legal/java.xml.crypto/santuario.md`
- `app/src/main/legal/java.xml/bcel.md`
- `app/src/main/legal/java.xml/dom.md`
- `app/src/main/legal/java.xml/jcup.md`
- `app/src/main/legal/java.xml/xalan.md`
- `app/src/main/legal/java.xml/xerces.md`
- `app/src/main/legal/jdk.crypto.cryptoki/pkcs11cryptotoken.md`
- `app/src/main/legal/jdk.crypto.cryptoki/pkcs11wrapper.md`
- `app/src/main/legal/jdk.dynalink/dynalink.md`
- `app/src/main/legal/jdk.localedata/thaidict.md`

## 6. UI Theme & Design System

### Colors
- `Primary = #58CC02`
- `PrimaryLight = #7FE620`
- `PrimaryDark = #4F9B7D`
- `PrimaryMuted = #2658CC02`
- `BgLight = #FFFFFCF4`
- `BgDark = #FF132621`
- `SurfaceLight = #FFFFFFFF`
- `SurfaceDark = #FF1A352D`
- `SurfaceVarLight = #FFF4FCE8`
- `SurfaceVarDark = #FF316854`
- `TextLight = #FF203119`
- `TextDark = #FFF5FFFB`
- `TextMuted = #FF596E63`
- `BorderLight = #FFE2E8D3`
- `BorderDark = #FF53917B`
- `Online = #FF35A854`
- `Offline = #FFE85D75`
- `Warning = #FFF59E0B`
- `DownloadBlue = #FF2F80ED`
- `Starting = #FFFFC800`
- `Danger = #FFFF6B6B`
- `ConsoleGreen = #FFF5FFFB`
- `ConsoleWarn = #FFFFB142`
- `ConsoleError = #FFFF4757`
- `ConsoleBg = BgDark`
- `HealthRed = #FFFF4757`
- `XpGreen = Primary`

### Fonts
- `PlusJakartaSans` from `res/font/plus_jakarta_sans_variable.ttf`
- `Monocraft` from `res/font/monocraft.ttf`

### Typography
- `PocketCraftTypography` defines Material 3 text styles using `PlusJakartaSans` with bold/extra-bold emphasis.
- Monospace-style Minecraft branding uses `Monocraft` in the app header.

### Shapes and theme
- `PocketShapes`
  - small: `10.dp`
  - medium: `12.dp`
  - large: `14.dp`
- `PocketCraftTheme(darkTheme, content)` switches between a custom light and dark `ColorScheme`.

### Shared composables and style helpers
- `PocketTopBar(...)`, `PocketBottomNav(...)`
- `PocketCraftCard(...)`
- `DuoButton(...)`, `DuoToggle(...)`
- `duoTextFieldShape()`, `duoTextFieldColors()`, `duoOutlinedTextFieldColors()`
- `StatusBadge(...)`
- `GameCard(...)`
- `PlayerCard(...)`
- `PlayerActionButton(...)`
- `ServerDescriptionField(...)`
- `ServerPhotoUpload(...)`
- `VersionUpgradeCard(...)`
- `BroadcastBanner(...)`
- `ChunkyProgressBanner(...)`
- `InventoryPreview(...)`

## 7. Server Lifecycle Flow

1. User taps Start Server in the server UI.
   - File/function: `ServerScreen.kt` / `ConsoleScreen.kt` / `ServerStateHolder.startServer()`
   - What it does: transitions the Compose state into starting mode and calls `ServerHostService.start(...)`.
   - Next: `ServerHostService.onStartCommand(ACTION_START)`.

2. `ServerStateHolder.startServer()` prepares the UI/runtime state.
   - File/function: `ServerStateHolder.kt -> startServer(isRestart)`
   - What it does: sets `isStarting`, `startupProgressPercent`, clears old state, and requests the foreground service start.
   - Next: service receives `ACTION_START`.

3. `ServerHostService.onStartCommand(...)` initializes service runtime.
   - File/function: `ServerHostService.kt -> onStartCommand`
   - What it does:
     - records the active version
     - sets runtime state to `starting`
     - starts the foreground notification
     - starts logcat and log-tail bridges
     - resolves the server port
     - starts the port probe
     - schedules the server-ready fallback timer
     - acquires a wake lock
   - Next: background coroutine loads config and resolves the server JAR.

4. `ServerHostService` loads server config and resolves the JAR.
   - File/function: `ServerHostService.kt -> serviceScope.launch { ... resolveJar(...) }`
   - What it does:
     - reads config via `ServerConfigRepository`
     - computes the shared JAR path via `ServerFileManager`
     - calls `ServerJarManager.resolveJar(...)`
   - Next: when the JAR is ready, it launches the JVM.

5. `ServerLauncher.startServer(...)` prepares runtime files and launch parameters.
   - File/function: `ServerLauncher.kt -> startServer`
   - What it does:
     - writes `eula.txt`
     - prepares `server.properties`
     - prepares runtime artifacts
     - synchronizes dimensions
     - ensures Bedrock bridge plugins
     - applies Bedrock-ready config defaults
     - computes RAM limits from device memory and `AppPreferences.ramMode`
     - ensures JRE binaries exist
     - creates native library shims
   - Next: launches the JVM either externally or via JNI.

6. External or in-process JVM launch happens.
   - File/function: `ServerLauncher.kt -> launchExternalJvm(...)` or `NativeLauncher.launchJVM(...)`
   - What it does:
     - sets `java.home`, `jna.*`, `user.home`, `user.timezone`, `os.name`, and library paths
     - loads `libserverwrap.so` or directly boots in-process
   - Next: the Minecraft server process starts and writes logs.

7. Logs are parsed for readiness and player events.
   - File/function: `ServerHostService.kt -> handleObservedOutputLine(...)`
   - What it does:
     - parses join/leave/TPS/chunky output
     - detects `Done (...)! For help, type "help"` readiness
     - triggers `onServerReady()`
   - Next: relay startup and UI address exposure.

8. Server-ready state promotes relay startup.
   - File/function: `ServerHostService.kt -> onServerReady()`
   - What it does:
     - marks the service ready
     - cancels fallback timer
     - starts relay registration / tunnel initialization
     - updates runtime state to running
   - Next: `RelayManager.register()` and `RelayManager.connectTunnelPool(...)`.

9. Relay publishes the public address.
   - File/function: `RelayManager.kt -> register()`, `connectTunnelPool(...)`, `postServerStatus(...)`
   - What it does:
     - receives a per-user port assignment
     - opens tunnel sockets
     - keeps the connection alive with heartbeats
     - exposes the public address to UI and share actions
   - Next: UI shows the address card once `serverReadyState` is true.

10. UI reflects the live state.
   - File/function: `ConsoleScreen.kt`
   - What it does:
     - collects `ServerHostService.serverReadyState`
     - shows join/address cards, logs, status, and player info
     - enables sharing only after readiness is established

## 8. Relay Architecture

### Relay host selection
- `RelayServersConfig.kt` defines two public relay hosts:
  - `play.pocketcraft.online` = Global/Singapore relay
  - `mine.pocketcraft.online` = Asia/Mumbai relay
- `RelayServers.getBestForTimeZone(...)` chooses Mumbai for India/South Asia time zones and Singapore otherwise.

### Registration flow
- `RelayManager.register()` sends a JSON POST to:
  - `http://<relayHost>:8080/register`
- Payload includes the user ID:
  - `{"userId":"<uuid>"}`
- The relay returns a port assignment, which is stored in `AppPreferences.relayPort`.

### Socket/tunnel protocol
- The app opens a persistent pool of TCP sockets from phone to relay.
- `RelayManager.PHONE_TUNNEL_PORT = 9000`
- `RelayManager.CONTROL_PORT = 8080`
- The relay forwards incoming player traffic over the pool.
- `notifyPhoneReady(...)` and the heartbeat loop keep the pool aligned with server readiness.

### Bedrock bridge
- `RelayManager.startBedrockBridge()` instantiates `BedrockUdpBridge`.
- Bedrock responses are bridged back through the active socket pool.
- `ServerStateHolder` keeps `bedrockBridgeEnabled` on by default internally.

### Player/public address exposure
- `RelayManager.register()` returns a `RelayAddress(host, port, isFallback)`.
- `ServerHostService` persists the public address and sends it to UI events.
- `ConsoleScreen` and `PocketTopBar` expose the address/share flow to the user.
- LAN fallback is handled separately by `ServerAddressResolver.getConnectAddress(...)`.

## 9. Known Issues & TODOs

- No `TODO`, `FIXME`, `HACK`, or `workaround` comments were found in `app/src/main/kotlin`, `app/src/main/cpp`, or `app/src/main/AndroidManifest.xml` during the source scan.
- The only TODO-like text found in the repo is in planning/docs files, not in runtime source.

## 10. Open Questions

- FILE: `ServerHostService.kt`
  - ISSUE: Readiness is detected by log-string parsing plus a 90-second fallback timer; if Paper log wording changes, readiness may become inaccurate.
- FILE: `ServerJarManager.kt`
  - ISSUE: `resolvePaperUrl(...)` assumes the last build entry is the newest build; that is probably true but is not explicitly validated.
- FILE: `ServerLauncher.kt`
  - ISSUE: Runtime launch depends on a set of hardcoded native path assumptions and shim symlinks; device-specific failures are possible if the JRE layout changes.
- FILE: `ServerFileManager.kt`
  - ISSUE: `rcon.password` is hardcoded to `pocketcraft-internal-rcon`; this is fine for local-only control but should be treated as sensitive.
- FILE: `RelayManager.kt`
  - ISSUE: Control and tunnel ports are hardcoded to `8080` and `9000`; backend changes would require an app update.
- FILE: `RemoteConfigManager.kt`
  - ISSUE: It only manages social-button flags; the `Context` parameter to `initialize(...)` is not used directly.
- FILE: `BroadcastManager.kt`
  - ISSUE: The offline fallback broadcast text is hardcoded and contains a typo (`Mantainance`).
- FILE: `VersionRepository.kt`
  - ISSUE: Fallback Minecraft versions are hardcoded and can drift from reality if the app remains offline for a long period.
- FILE: `OnboardingActivity.kt`
  - ISSUE: The onboarding flow is large and state-heavy; future regressions may be hard to isolate without component extraction.
- FILE: `OnboardingScreen.kt.bak`
  - ISSUE: Backup file is still present in the source tree and may confuse code scans or human readers.
- FILE: `SettingsScreen.kt`
  - ISSUE: The settings screen is still very large, so small regressions in spacing or scroll behavior can be easy to introduce.
- FILE: `ConsoleScreen.kt`
  - ISSUE: It owns a lot of UI and runtime state; lifecycle and recomposition bugs are possible if more responsibilities are added.
- FILE: `ServerStateHolder.kt`
  - ISSUE: State orchestration is broad and mutable; it should be watched for accidental race conditions as features are added.
