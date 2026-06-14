# PocketCraft: How The App Works

This document explains how PocketCraft works in its current source form.
It is based on the actual code in this repository, not just on product intent.

## 1. What PocketCraft Is

PocketCraft is an Android app that turns a phone into a host for a Minecraft Java server.
The app:

- prepares a bundled Java runtime on the device
- lets the user choose a Paper-compatible Minecraft version
- downloads the Paper server JAR for that version
- starts and stops the server from inside the app
- exposes the server on LAN
- optionally connects the phone to a PocketCraft relay so remote players can join
- gives the user in-app tools for console, players, plugins, files, worlds, and settings

At a high level, the app has two runtime halves:

- the normal app/UI process
- a separate foreground-service process called `:server` that actually hosts the server

That split is important: the UI can stay responsive while the service process manages the JVM, logs, relay sockets, and wake lock.

## 2. Core Mental Model

The best way to understand PocketCraft is this pipeline:

```text
MainActivity
  -> Compose app shell
  -> version + relay selection
  -> ServerScreen / ServerStateHolder
  -> start foreground service (:server)
  -> ServerLauncher boots Paper
  -> service watches logs + port readiness
  -> RelayManager registers tunnel
  -> service broadcasts events back to UI
```

The main responsibilities are divided like this:

- UI and navigation:
  - `MainActivity`
  - `ui/screens/*`
  - `ui/navigation/PocketNavigation.kt`
- app startup and analytics:
  - `PocketCraftApp.kt`
  - `Analytics.kt`
- server-facing UI state and commands:
  - `ui/screens/ServerStateHolder.kt`
- server runtime and lifecycle:
  - `server/ServerHostService.kt`
  - `server/ServerLauncher.kt`
  - `NativeLauncher.kt`
  - `app/src/main/cpp/launcher.c`
- relay and network behavior:
  - `RelayManager.kt`
  - `NetworkMonitor.kt`
  - `server/ServerAddressResolver.kt`
- file, world, plugin, and player management:
  - `service/ServerFileManager.kt`
  - `service/ServerPropertiesHelper.kt`
  - `service/PluginManager.kt`
  - `WorldImporter.kt`
  - `PlayerDataManager.kt`
- setup and download:
  - `setup/JreExtractor.kt`
  - `setup/PaperMcDownloader.kt`
  - `server/ServerDownloader.kt`

## 3. Android App Structure

### Manifest and process model

The manifest defines the basic runtime shape:

- `MainActivity` is the launcher activity and is portrait-only.
- `ServerHostService` is a foreground service.
- `ServerHostService` runs in a separate process: `android:process=":server"`.
- `stopWithTask="true"` means removing the task also tears down hosting.

Important permissions and app flags:

- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`
- `WAKE_LOCK`
- `POST_NOTIFICATIONS`
- `RECEIVE_BOOT_COMPLETED`
- `usesCleartextTraffic="true"`
- `allowBackup="false"`

`usesCleartextTraffic="true"` matters because the relay control API is contacted over plain HTTP on port `8080`.

### Application class

`PocketCraftApp.kt` is the `Application` class. It:

- initializes Firebase
- initializes the analytics helper
- records device context in Crashlytics
- logs first launch and app open events
- provides WorkManager configuration through Hilt

Google auth initialization is present in comments, but currently disabled in this startup path.

## 4. Startup Flow

### Activity startup

`MainActivity.kt` does the following:

1. installs the splash screen
2. requests notification permission on Android 13+
3. loads the saved theme preference
4. starts JRE extraction in the background
5. shows `ErrorScreen` if runtime preparation fails
6. otherwise renders the main Compose app

JRE extraction happens before normal hosting usage, so a broken runtime fails early.

### Compose app bootstrap

The main entry composable is `PocketCraftApp(...)` in `ui/screens/PocketCraftAppScreen.kt`.

On launch it:

1. starts on `Screen.LOADING`
2. marks onboarding as complete immediately
3. reads the saved selected version from DataStore
4. normalizes that version against the Paper API and Mojang release metadata
5. auto-selects a relay host from timezone
6. lands on `Screen.SERVER`

The top-level screens are:

- `LOADING`
- `VERSION_PICKER`
- `DOWNLOADING`
- `SERVER`

Current routing behavior:

- normal launch: `LOADING -> SERVER`
- user changes version: `SERVER -> VERSION_PICKER`
- version not downloaded: `VERSION_PICKER -> DOWNLOADING -> SERVER`
- version already present: `VERSION_PICKER -> SERVER`

### Important current behavior

The app contains an onboarding flow (`OnboardingFlow.kt`), but the active startup code does not use it.
Instead, startup forcibly sets:

- `onboarding_complete = true`
- `onboarding_version = "1"`

So onboarding exists in the repo, but is bypassed in the real app path today.

## 5. Version Selection And Download

### How the boot version is chosen

On app launch, `PocketCraftAppScreen.kt` tries to find the best safe version to boot with this order:

1. fetch the list of Paper-supported versions
2. fetch Mojang's latest release version
3. if Mojang latest is also supported by Paper, use it
4. else use the user's saved version if it is still valid
5. else use the newest Paper-supported version
6. else fall back to `"1.21.4"`

This is why the app can silently adjust a stale saved version during startup.

### Version picker

`VersionPickerScreen.kt` fetches versions directly from the Paper API.
If that fails, it falls back to a hardcoded version list.

The screen also:

- tracks which versions are already downloaded locally
- allows downloaded version folders to be deleted
- treats the first returned version as the "latest release" label

### Download pipeline

`AutoDownloadScreen.kt` calls `ServerDownloader.downloadPaperJarOnMain(...)`.

That path does this:

1. ensure the JRE is extracted
2. check whether the selected version's JAR already exists
3. if missing, download the latest Paper build for that version
4. write `eula.txt`
5. return control to the main server screen

### Paper download details

`PaperMcDownloader.kt`:

- talks to `https://api.papermc.io/v2/projects/paper`
- chooses the latest stable or default channel build
- retries failed requests with exponential backoff
- verifies the final file is at least about 1 MB

If Paper DNS fails specifically, it falls back to Mojang's server download URL for that Minecraft version.

## 6. Java Runtime Preparation

`JreExtractor.kt` is responsible for unpacking the bundled runtime.

It supports two asset layouts:

- an already-expanded `assets/jre-runtime/bin` + `assets/jre-runtime/lib` tree
- component archives such as:
  - `universal.tar.xz`
  - `bin-arm64.tar.xz`

Extraction targets:

- Android Q and above: `codeCacheDir/jre-runtime`
- older Android: `filesDir/jre-runtime`

After extraction it:

- flattens a single nested root folder if needed
- normalizes `libjli.so` and `libjvm.so` into predictable locations
- fixes file permissions
- writes a marker file: `files/jre_v3_extracted`

The runtime is considered valid only if these files exist:

- `lib/libjli.so`
- `lib/server/libjvm.so`

## 7. Main In-App Navigation

Once the user is inside `ServerScreen.kt`, the app becomes a single tabbed management interface.

Bottom tabs:

- `Console`
- `Players`
- `Plugins`
- `Files`
- `Settings`

Top bar actions:

- relay region selector
- theme toggle

Back behavior:

- if the user is on a non-console tab, back returns to `Console`
- if the user is already on `Console`, back opens an exit dialog

## 8. Server Storage Layout

PocketCraft keeps each Minecraft version in its own internal directory:

```text
files/
  servers/
    <version>/
      paper-<version>.jar
      server.properties
      eula.txt
      logs/latest.log
      world data
      plugins/
      mods/
      resourcepacks/
      pocketcraft_backups/
      usercache.json
      whitelist.json
      ops.json
      banned-players.json
```

Other important paths:

- `files/runtime-tmp/`
- `files/lib-shims/`
- `files/server_branding/`
- `Downloads/PocketCraftWorldBackups/` for exported local backups

Because versions are isolated by folder, PocketCraft can keep multiple Paper versions downloaded at once.

## 9. The Server Start Flow

### Step 1: UI requests start

When the user taps Start, `ServerStateHolder.startServer()`:

- validates the selected version is downloaded
- saves the current config to `server.properties`
- resets tunnel/log/player UI state
- clears old logs
- marks the UI as starting
- calls `ServerHostService.start(context, versionId)`

### Step 2: foreground service starts

`ServerHostService.kt` then:

- stores the current version id
- starts a Firebase performance trace (`server_startup`)
- enters foreground with a persistent notification
- starts a logcat bridge
- starts tailing `logs/latest.log`
- starts probing the local server port
- acquires a partial wake lock

The service process is where hosting actually lives.

### Step 3: launcher prepares the runtime

`ServerLauncher.startServer(...)` does the server-side prep:

- verifies the Paper JAR exists
- writes `eula.txt`
- rewrites critical server properties for compatibility
- prepares runtime temp files
- checks that the JRE binary and native libraries exist
- creates compatibility symlinks in `files/lib-shims`
- computes a safe heap profile based on total RAM and user-selected RAM mode

RAM mode is read from `SharedPreferences`:

- `low`
- `manual`
- `full`

The actual launcher always caps memory to keep Android stable.
So the visible RAM choice is only an input; final heap size is decided by `ServerLauncher`.

### Step 4: JVM launch path

Launch method depends on Android version:

- Android 11 / API 30 and above:
  - `NativeLauncher.launchJVM(...)`
  - implemented in `app/src/main/cpp/launcher.c`
  - boots HotSpot in-process via `dlopen`
- older Android:
  - launches an external Java process
  - optionally through `libserverwrap.so` if present

The native launcher disables heap tagging and preloads runtime libraries before calling the JVM entry point.

### Step 5: readiness detection

The app considers the server ready when either:

- console output contains the Paper "Done (...)" pattern
- or a TCP probe to `127.0.0.1:<server-port>` succeeds

Once ready:

- UI status changes from starting to online
- queued RCON commands are flushed
- relay setup begins
- analytics records the server as started

## 10. Runtime Compatibility Defaults

PocketCraft intentionally forces some `server.properties` values at launch time.

From `ServerFileManager.prepareServerProperties(...)`, the app enforces:

- `server-port=25565`
- `online-mode=false`
- `server-ip=` (bind all interfaces)
- `enable-rcon=true`
- `rcon.port=25575`
- `rcon.password=pocketcraft-internal-rcon`

`ServerStateHolder.saveConfig(...)` also reinforces compatibility-related values such as:

- `broadcast-rcon-to-ops=false`
- `network-compression-threshold=256`
- `max-tick-time=60000`

Why this matters:

- RCON is how the app sends in-app console commands and player actions.
- Offline mode is required for this LAN/phone-hosting model.
- The app expects a predictable server port and a localhost-only RCON endpoint.

## 11. Service Events, Logs, And UI State

### Broadcast model

`ServerHostService` communicates back to the app mostly through broadcasts.

Main action:

- `ACTION_SERVER_EVENT`

Important event types:

- `output`
- `error`
- `stopped`
- `tunnel_connecting`
- `tunnel_awaiting_claim`
- `tunnel_connected`
- `tunnel_failed`

`ServerStateHolder.kt` listens for these events and turns them into Compose state.

### Log collection

The service emits logs from three places:

- launcher output callbacks
- filtered `logcat`
- tailing `logs/latest.log`

`ServerStateHolder` keeps a 240-line ring buffer for display.

### Live parsing

`ConsoleParser.kt` is used to derive:

- TPS values
- player joins
- player leaves
- server-ready patterns

That is how the UI can update players and health without needing a direct API from Paper.

## 12. Commands And RCON

PocketCraft sends in-app commands through RCON on `127.0.0.1:25575`.

`ServerStateHolder` includes a lightweight RCON client that:

- authenticates using the fixed internal password
- sends command packets directly over a socket
- retries transient connection failures
- queues commands while the server is still starting

This is what powers:

- console command input
- kick / ban / op actions
- player detail actions
- plugin reloads
- stop flow via RCON `stop`

Queued commands are flushed as soon as the server becomes ready.

## 13. Stop, Restart, And Crash Recovery

### User stop

When the user taps Stop:

- `ServerStateHolder.stopServer()` resets the UI immediately
- the service receives `ACTION_STOP`
- the service cancels relay work
- the service disconnects tunnel sockets
- the service sends RCON `stop`

The final stopped event is not emitted until the JVM actually exits.

### Task removal

If Android removes the app task, `ServerHostService.onTaskRemoved(...)` calls a more forceful stop path that:

- tries to write `stop` to the server process stdin
- waits up to 10 seconds
- force-kills the process if needed

### Crash recovery

Unexpected exits can trigger automatic recovery.
The current hardcoded behavior is:

- recovery window: 20 minutes
- maximum attempts: 3
- user-requested stop disables recovery

This logic lives in `ServerHostService.shouldScheduleAutoRecover(...)`.

## 14. Relay And Public Connectivity

### Relay selection

PocketCraft currently knows two relay hosts:

- `play.pocketcraft.online` -> Singapore / global
- `mine.pocketcraft.online` -> India / South Asia

On startup, `RelayServers.getBestForTimeZone(...)` chooses the relay automatically by timezone.

### Registration flow

`RelayManager.kt` implements the app-side tunnel protocol:

1. read `userId` and selected relay host from `SharedPreferences`
2. POST to `http://<relayHost>:8080/register`
3. receive an assigned public port
4. resolve the phone's LAN IP
5. POST `phone-ready` with the phone IP and local server port
6. open a pool of persistent sockets to `<relayHost>:9000`
7. identify each tunnel socket with the user id
8. when player traffic arrives, bridge that relay socket to `127.0.0.1:<localPort>`

### Tunnel pool behavior

The relay pool is intentionally pre-opened.
Current app-side settings:

- pool size: 25 sockets
- large socket buffers
- `TCP_NODELAY`, `keepAlive`, and other latency-oriented socket options

When a socket gets consumed by real player traffic:

- it is removed from the pool
- the pool is topped up
- a bidirectional bridge is created:
  - relay -> local server
  - local server -> relay

### Public address

When registration succeeds, the UI shows:

- `<relay-host>:<assigned-port>`

That address is copied to clipboard, shared, and rendered as a QR code from the console screen.

## 15. Network Behavior

`NetworkMonitor.kt` classifies connectivity into four modes:

- `NO_CONNECTION`
- `WIFI_NO_INTERNET`
- `WIFI_WITH_INTERNET`
- `MOBILE_DATA`

Current service behavior:

- `NO_CONNECTION`
  - stop the server
- `MOBILE_DATA`
  - keep relay mode active
- `WIFI_NO_INTERNET`
  - disconnect relay, remain LAN-only
- `WIFI_WITH_INTERNET`
  - also disconnect relay, remain LAN-only

That last point is important: in the current implementation, Wi-Fi is intentionally treated as LAN-only even if Wi-Fi itself has internet access.

UI handling is split:

- `PocketCraftAppScreen.kt` shows a startup dialog if validated internet disappears
- `ConsoleScreen.kt` listens for service-level network broadcasts and shows LAN-only warnings while hosting

## 16. Console Tab

The console tab is more than a log viewer.

It is the main hosting dashboard and includes:

- start / stop / restart controls
- startup progress UI
- upgrade/version card
- RAM mode controls
- RAM usage monitoring while online
- player previews
- live command input
- public address and LAN address sharing tools
- QR generation for the public address
- relay latency sampling every 20 seconds

RAM usage is polled through `RamUtils.getUsedRamMb(...)`.
If usage crosses warning thresholds, analytics logs RAM warning events.

## 17. Players And Player Details

### Player lists

`ServerStateHolder` keeps several player collections:

- `onlinePlayers`
- `knownPlayers`
- `whitelistPlayers`
- `opPlayers`
- `bannedPlayers`

Those lists are built from:

- live log parsing
- `usercache.json`
- `whitelist.json`
- `ops.json`
- `banned-players.json`
- world stats and playerdata files

### Actions

The players UI can:

- whitelist players
- remove from whitelist
- op / de-op
- ban / unban
- kick online players

If the server is online, PocketCraft also sends live commands so the running server state matches the file edits.

### Player detail screen

`PlayerDetailScreen.kt` uses `PlayerDataManager.kt` to inspect:

- stats JSON
- compressed NBT playerdata
- inventory slots
- XP / health / hunger / saturation
- playtime and kill/death stats

It also uses live commands to query things like:

- position
- dimension
- spawn point

## 18. Files, Worlds, And Backups

### Files tab

The files area shows:

- active world folder
- world size
- current world seed
- version change entry point
- quick backup entry point
- world-management entry point

### World import

`WorldsScreen.kt` allows importing zipped world data.
`WorldImporter.kt`:

1. copies the selected content URI into a temp zip
2. deletes the existing target world folder
3. extracts safely with zip-slip checks
4. looks for nested `level.dat` roots and flattens them

The UI can import separate dimensions into:

- overworld
- nether
- end

### Local backup behavior

`ServerStateHolder.createBackup()` creates a zip that includes:

- `server.properties`
- overworld
- nether, if present
- end, if present
- `plugins/`, if present
- `mods/`, if present

That zip is stored twice:

- inside the version folder under `pocketcraft_backups/`
- exported to `Downloads/PocketCraftWorldBackups`

On Android Q+, export uses `MediaStore`.
On older Android, it writes directly to the Downloads path.

### Automatic backup on stop

When the service emits `EVENT_STOPPED`, `ServerStateHolder` automatically triggers a persistent backup export.

That auto-backup:

- writes a temporary zip
- saves it to Downloads
- keeps only the latest 3 exported backup zips

This local automatic backup is active even though Google Drive backup is not fully wired into the main flow.

### Restore and reset

Restore and reset require the server to be offline.

The app can:

- restore a local backup zip into the server folder
- delete a local backup zip
- delete the current world folder to reset the world

## 19. Plugins, Mods, And Resource Packs

PocketCraft treats add-ons as version-specific files.

Directories:

- `plugins/`
- `mods/`
- `resourcepacks/`

`service/PluginManager.kt` scans those folders and builds the installed list.

Current add-on behavior:

- plugins and mods are `.jar`
- resource packs are `.zip`
- disabling is implemented by renaming to `.disabled`
- enabling removes the `.disabled` suffix

PocketCraft also stores add-on metadata in:

- `.pocketcraft_addon_meta.json`

That metadata includes:

- source type
- icon URL

The plugin screen can do two kinds of installs:

- manual file import from device storage
- remote browsing/install flow through Modrinth search helpers

For a running server, the installed screen also exposes a `reload` command.

## 20. Settings And Branding

`SettingsScreen.kt` exposes several server and app controls:

- view distance
- simulation distance
- difficulty
- gamemode
- whitelist
- server port
- MOTD
- relay region
- monster / animal / NPC spawning
- PVP
- display name
- server photo
- dark mode
- auto-restart toggle

Branding is split this way:

- `serverDisplayName` and `serverPhotoUrl` live in `SharedPreferences`
- if display name is non-blank, PocketCraft also writes it back to the server MOTD

The server photo is copied into:

- `files/server_branding/server_photo.jpg`

## 21. Persistence Model

PocketCraft currently uses multiple persistence systems.

### SharedPreferences (`app_relay_prefs`)

Used for values the service and runtime read directly:

- `user_id`
- `secret_key`
- `ram_mode`
- `manual_ram_mb`
- `relay_host`
- `server_display_name`
- `server_photo_url`
- first-launch and milestone flags
- whitelist warning acknowledgement
- local auto-backup flags and last-backup metadata

### DataStore (`app_prefs`)

Used for app-level preferences:

- `setup_complete`
- `selected_version`
- `world_seed`
- `seed_setup_shown`
- `dark_mode_override`
- `sound_enabled`
- `notifications_enabled`
- `auto_restart`
- `auto_restart_delay_seconds`
- `relay_host`
- `relay_host_selected`
- `onboarding_complete`
- `onboarding_version`

### Room / repository layer

The repo also contains:

- `VersionDatabase`
- `VersionDao`
- `VersionRepository`
- `ServerConfigRepository`

These are part of a broader architecture, but they are not the main code path used by the current Compose hosting flow.
The active UI mostly reads files directly or uses helper objects like `ServerStateHolder`, `ServerDownloader`, and direct Retrofit calls.

## 22. Analytics, Crash Reporting, And Tracing

`Analytics.kt` sends Firebase events for:

- first launch
- app open
- screen views
- version selection
- server start / stop / crash
- tunnel connect / fail / disconnect
- player join / leave / moderation actions
- plugin install / delete / toggle
- world uploads and resets
- RAM warnings
- settings changes
- onboarding events

Crashlytics also stores useful custom keys such as:

- current server version
- RAM mode
- relay host
- relay connection state
- player counts

Firebase performance traces are used at least for:

- server startup
- world import

## 23. Features Present In Code But Not Fully Active

The repo contains several features that exist, but are not part of the main active runtime flow right now.

### Onboarding

- `OnboardingFlow.kt` exists
- startup bypasses it completely

### Google Drive backup

- `GoogleAuthManager.kt`
- `DriveBackupManager.kt`
- `BackupListScreen.kt`

These are present, but the main initialization and visible settings path are commented out or not routed through the current app shell.

### WorkManager-based setup

- `setup/SetupWorker.kt` exists
- the current user-facing setup/download path uses `ServerDownloader` directly instead

## 24. Important Current Caveats

These details matter if you are modifying the app or trying to reason about unexpected behavior.

### Custom server port is not truly supported at runtime

The settings UI lets the user edit `server-port`, but the launch path rewrites critical properties and expects `25565`.
In practice, PocketCraft currently treats port `25565` as the real runtime port.

### Auto-restart setting is not the thing driving crash recovery

The settings screen exposes an auto-restart toggle, but actual crash recovery is hardcoded in `ServerHostService`:

- 3 attempts
- inside 20 minutes

That service logic does not currently consult the UI toggle before recovering.

### Relay fallback UI is ahead of the relay implementation

There are UI states for "fallback relay" behavior, but the current app-side relay code always registers against the selected host.
True app-level fallback routing is not implemented in `RelayManager.kt` today.

### Wi-Fi means LAN-only in the current network policy

Even `WIFI_WITH_INTERNET` is treated as LAN-only by the service.
Only the `MOBILE_DATA` state is currently considered relay-enabled by `NetworkMonitor`.

### Some preferences exist before they are fully wired

DataStore keys for things like:

- sounds
- notifications
- auto-restart delay

exist in the repo, but are not central to the current hosting control path.

## 25. Source Map

If you want to trace behavior quickly, these are the best starting points.

### App entry

- `app/src/main/kotlin/com/pocketcraft/server/MainActivity.kt`
- `app/src/main/kotlin/com/pocketcraft/server/PocketCraftApp.kt`
- `app/src/main/kotlin/com/pocketcraft/server/ui/screens/PocketCraftAppScreen.kt`

### Main server UI

- `app/src/main/kotlin/com/pocketcraft/server/ui/screens/ServerScreen.kt`
- `app/src/main/kotlin/com/pocketcraft/server/ui/screens/ConsoleScreen.kt`
- `app/src/main/kotlin/com/pocketcraft/server/ui/screens/ServerStateHolder.kt`
- `app/src/main/kotlin/com/pocketcraft/server/ui/navigation/PocketNavigation.kt`

### Runtime

- `app/src/main/kotlin/com/pocketcraft/server/server/ServerHostService.kt`
- `app/src/main/kotlin/com/pocketcraft/server/server/ServerLauncher.kt`
- `app/src/main/kotlin/com/pocketcraft/server/NativeLauncher.kt`
- `app/src/main/cpp/launcher.c`

### Relay and network

- `app/src/main/kotlin/com/pocketcraft/server/RelayManager.kt`
- `app/src/main/kotlin/com/pocketcraft/server/NetworkMonitor.kt`
- `app/src/main/kotlin/com/pocketcraft/server/server/ServerAddressResolver.kt`
- `app/src/main/kotlin/com/pocketcraft/server/config/RelayServersConfig.kt`

### Setup and download

- `app/src/main/kotlin/com/pocketcraft/server/setup/JreExtractor.kt`
- `app/src/main/kotlin/com/pocketcraft/server/setup/PaperMcDownloader.kt`
- `app/src/main/kotlin/com/pocketcraft/server/server/ServerDownloader.kt`
- `app/src/main/kotlin/com/pocketcraft/server/ui/screens/VersionPickerScreen.kt`
- `app/src/main/kotlin/com/pocketcraft/server/ui/screens/AutoDownloadScreen.kt`

### Management features

- `app/src/main/kotlin/com/pocketcraft/server/ui/screens/PlayersScreen.kt`
- `app/src/main/kotlin/com/pocketcraft/server/ui/screens/PlayerDetailScreen.kt`
- `app/src/main/kotlin/com/pocketcraft/server/ui/screens/FilesScreen.kt`
- `app/src/main/kotlin/com/pocketcraft/server/ui/screens/WorldsScreen.kt`
- `app/src/main/kotlin/com/pocketcraft/server/ui/screens/PluginManagerScreen.kt`
- `app/src/main/kotlin/com/pocketcraft/server/ui/screens/SettingsScreen.kt`
- `app/src/main/kotlin/com/pocketcraft/server/service/PluginManager.kt`
- `app/src/main/kotlin/com/pocketcraft/server/WorldImporter.kt`
- `app/src/main/kotlin/com/pocketcraft/server/PlayerDataManager.kt`

### Persistence and support code

- `app/src/main/kotlin/com/pocketcraft/server/data/preferences/AppPreferences.kt`
- `app/src/main/kotlin/com/pocketcraft/server/service/ServerFileManager.kt`
- `app/src/main/kotlin/com/pocketcraft/server/service/ServerPropertiesHelper.kt`
- `app/src/main/kotlin/com/pocketcraft/server/Analytics.kt`

## 26. Short Summary

PocketCraft is a Compose Android app with a separate foreground service that hosts a Paper server on-device, monitors it through logs and port probes, and optionally bridges remote traffic through a relay socket pool.

The current codebase is feature-rich, but it also contains some in-progress or partially wired areas.
If you are changing behavior, the most important files to keep in sync are:

- startup routing
- `ServerStateHolder`
- `ServerHostService`
- `ServerLauncher`
- `RelayManager`
- server property enforcement

