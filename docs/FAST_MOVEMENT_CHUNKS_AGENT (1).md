# FAST_MOVEMENT_CHUNKS_AGENT

## Objective

Fix chunk loading lag specifically during fast player movement — elytra gliding and creative flight. Players outrun the server's chunk generation pipeline and hit blank terrain. This is a separate issue from general chunk loading slowness.

---

## Context

- **App package:** `com.pocketcraft.server`
- **Server type:** Paper (primary)
- **Existing configs to edit:** `assets/paper-world-defaults.yml`, `assets/paper-global.yml`
- **Files to edit:** `ServerHostService.kt`, `AppPreferences.kt`, `SettingsScreen.kt`, `HomeScreen.kt`, `ServerConsole.kt`
- **Theme colors:** `#0A0A0F` bg, `#6C63FF` purple, `#3DDC84` green, `#FF4757` red, `#FFB142` orange

---

## Task 1 — Update `assets/paper-world-defaults.yml`

Find the existing `paper-world-defaults.yml` in `assets/` and update these values:

```yaml
chunk-loading-basic:
  player-max-chunk-load-rate: 200        # raised — gives Paper headroom for fast movers
  player-max-chunk-send-rate: 100        # ceiling only, auto-config manages actual rate
  target-player-chunk-send-rate: -1      # fully automatic per player ping

chunk-loading-advanced:
  auto-config-send-distance: true
  player-loading-priority-override: 10
  player-max-concurrent-loads: 12
```

---

## Task 2 — Update `assets/paper-global.yml`

Find the existing `paper-global.yml` in `assets/` and raise thread counts:

```yaml
chunk-system:
  gen-parallelism: default
  io-threads: 3                          # raised from 2 — handles elytra I/O burst
  worker-threads: 3                      # raised from 2 — more gen workers ahead of player
```

---

## Task 3 — Bundle `PocketCraftChunkLoader` plugin

Create and bundle a minimal Paper plugin JAR at `assets/default_plugins/PocketCraftChunkLoader.jar` that pre-generates chunks ahead of fast-moving players.

Plugin main class logic:

```java
@EventHandler
public void onPlayerMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    double speed = event.getFrom().distance(event.getTo());

    // Only activate for fast movement (elytra ~1.5 blocks/tick, creative ~1.0)
    if (speed < 0.5) return;

    Vector velocity = player.getVelocity().normalize();
    Location loc = player.getLocation();

    // Pre-load chunks 2–6 ahead in movement direction, async non-blocking
    for (int i = 2; i <= 6; i++) {
        Location ahead = loc.clone().add(velocity.clone().multiply(i * 16));
        player.getWorld().getChunkAtAsync(ahead, false);
    }
}
```

In `ServerHostService.kt`, copy `PocketCraftChunkLoader.jar` to `plugins/` on server start alongside the existing Chunky copy logic.

---

## Task 4 — Flight Mode toggle in Settings

### `AppPreferences.kt`

Add:

```kotlin
var flightModeEnabled: Boolean
    get() = prefs.getBoolean("flight_mode_enabled", false)
    set(value) = prefs.edit().putBoolean("flight_mode_enabled", value).apply()
```

### `ServerHostService.kt`

When Flight Mode is enabled, override before server start:

```kotlin
if (appPreferences.flightModeEnabled) {
    serverProperties["view-distance"] = "8"
    serverProperties["simulation-distance"] = "6"
}

// After "Done" detected in logs and isNewWorld:
if (appPreferences.flightModeEnabled) {
    sendCommand("chunky radius 600")
    sendCommand("chunky start")
}
```

### `SettingsScreen.kt`

Add below the existing Fast Start toggle in the Performance section:

```kotlin
SettingsToggleCard(
    title = "Flight Mode",
    subtitle = "Optimizes chunk loading for elytra and creative flight. Uses more RAM.",
    icon = Icons.Default.Air,
    iconTint = Color(0xFF6C63FF),
    checked = appPreferences.flightModeEnabled,
    onCheckedChange = { appPreferences.flightModeEnabled = it }
)

if (appPreferences.flightModeEnabled) {
    Text(
        text = "⚠ Uses more RAM • View: 8 • Sim: 6",
        fontSize = 11.sp,
        color = Color(0xFFFF4757),
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
    )
}
```

---

## Task 5 — "Player moving fast" banner in `HomeScreen.kt`

### `ServerConsole.kt`

Detect this Paper log line:

```
moved too quickly
```

When matched, emit a `ServerEvent.PlayerMovingFast(playerName)` event. Extract player name from the log line prefix.

### `HomeScreen.kt`

Observe the event and show an auto-dismissing banner:

```kotlin
PlayerMovingFastBanner(
    message = "$playerName is flying fast — chunks may lag",
    iconTint = Color(0xFFFFB142),
    autoDismissMs = 4000
)
```

---

## Task 6 — Lock View Distance on First Boot

### Problem

On first server start, the world hasn't been pre-generated yet. If the user raises view distance above 6 before Chunky finishes, the server gets flooded with chunk generation requests it can't handle, causing severe lag or crash. The view distance slider/setting must be locked at 6 until the first boot pre-gen completes.

### `AppPreferences.kt`

Add:

```kotlin
var firstBootComplete: Boolean
    get() = prefs.getBoolean("first_boot_complete", false)
    set(value) = prefs.edit().putBoolean("first_boot_complete", value).apply()
```

Set `firstBootComplete = true` in `ServerHostService.kt` after Chunky pre-gen finishes — detected by parsing this log line from `ServerConsole.kt`:

```
[Chunky] Task finished
```

When matched, emit `ServerEvent.PregenComplete` and set:

```kotlin
appPreferences.firstBootComplete = true
```

### `SettingsScreen.kt`

Find wherever the view distance slider or input field is rendered. Wrap it with a lock state:

```kotlin
val isLocked = !appPreferences.firstBootComplete

// Disable the slider/input
ViewDistanceSlider(
    value = currentViewDistance,
    enabled = !isLocked,
    onValueChange = { if (!isLocked) onViewDistanceChange(it) }
)

// Show lock message directly below when locked
if (isLocked) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = Color(0xFFFFB142),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Locked during first world generation. Will unlock automatically.",
            fontSize = 11.sp,
            color = Color(0xFFFFB142)
        )
    }
}
```

### Guard condition

- Lock only applies when `firstBootComplete == false`
- Once Chunky finishes and `firstBootComplete` is set to `true`, the slider becomes fully interactive immediately — no restart required
- Flight Mode and Fast Start toggles are NOT locked — they apply on the next server start anyway
- If the user has never started a server before (brand new install), `firstBootComplete` defaults to `false`, so lock is active from the start

---

## Task 7 — Lock Modpack Option in Version Picker

### Problem

The version picker (where users select Paper / Purpur / Fabric) likely also shows a "Modpack" option. This feature is not yet implemented. Tapping it must not navigate anywhere — instead show a "Coming Soon" dialog.

### Find the version picker

Locate the screen or composable where server version/type is selected. It is likely in one of:
- `SettingsScreen.kt`
- `ServerSetupScreen.kt`
- `VersionPickerScreen.kt`
- Or a dialog/bottom sheet composable

Find the item or card that represents the **Modpack** option.

### Apply the lock UI

Replace or wrap the Modpack option with a locked variant:

```kotlin
VersionOptionCard(
    title = "Modpack",
    subtitle = "CurseForge, Modrinth & more",
    icon = Icons.Default.Extension,
    isLocked = true,
    onClick = { showComingSoonDialog = true }
)
```

If `VersionOptionCard` doesn't support `isLocked`, add a lock badge overlay manually:

```kotlin
Box {
    VersionOptionCard(
        title = "Modpack",
        subtitle = "CurseForge, Modrinth & more",
        icon = Icons.Default.Extension,
        enabled = false,
        onClick = { showComingSoonDialog = true }
    )
    // Lock badge in top-right corner
    Icon(
        imageVector = Icons.Default.Lock,
        contentDescription = "Coming Soon",
        tint = Color(0xFFFFB142),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(10.dp)
            .size(16.dp)
    )
}
```

### Coming Soon dialog

Add a state variable at the top of the composable:

```kotlin
var showComingSoonDialog by remember { mutableStateOf(false) }
```

Render the dialog:

```kotlin
if (showComingSoonDialog) {
    AlertDialog(
        onDismissRequest = { showComingSoonDialog = false },
        containerColor = Color(0xFF13131A),
        title = {
            Text(
                text = "Coming Soon",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Modpack support is coming in a future update. Stay tuned!",
                color = Color.White.copy(alpha = 0.7f)
            )
        },
        confirmButton = {
            TextButton(onClick = { showComingSoonDialog = false }) {
                Text(
                    text = "Got it",
                    color = Color(0xFF6C63FF)
                )
            }
        }
    )
}
```

### Visual treatment of locked card

The Modpack card must look visually distinct from available options:
- Reduced opacity: `alpha = 0.5f` on the card
- Lock icon badge: top-right corner, orange `#FFB142`
- Card is still tappable (to show the dialog) but does not appear selectable
- Do NOT disable `onClick` — it must still open the dialog

---

## Files Modified

| File | Change |
|------|--------|
| `assets/paper-world-defaults.yml` | Raise load rate to 200, keep send rate auto |
| `assets/paper-global.yml` | IO + worker threads raised to 3 |
| `assets/default_plugins/PocketCraftChunkLoader.jar` | New — velocity-aware chunk preloader plugin |
| `ServerHostService.kt` | Copy new plugin JAR; apply Flight Mode distances; set `firstBootComplete` after pregen |
| `AppPreferences.kt` | Add `flightModeEnabled`, `firstBootComplete` preferences |
| `SettingsScreen.kt` | Flight Mode toggle; view distance lock on first boot |
| `HomeScreen.kt` | Auto-dismiss "moving fast" banner |
| `ServerConsole.kt` | Detect "moved too quickly", "[Chunky] Task finished"; emit events |
| Version picker screen (whichever file) | Lock Modpack card with coming soon dialog |

---

## Task 8 — Remove Render Distance from World Creation Screen

### Problem

The world creation screen currently shows a render distance option. This should be removed entirely — the render distance is always 6 on a new world and the user should not be able to set it there. It only belongs in Settings after the world is running.

### Find the world creation screen

Locate the world creation composable or screen file. It is likely one of:
- `WorldCreationScreen.kt`
- `NewWorldScreen.kt`
- `CreateServerScreen.kt`
- Or a dialog/bottom sheet triggered from `HomeScreen.kt`

### What to remove

Delete or comment out the entire render distance input/slider/dropdown from the world creation UI. This includes:
- The label (e.g. "Render Distance", "View Distance")
- The slider, dropdown, or number input
- Any associated state variable that is only used for this field
- Any logic that passes this value into `server.properties` from the world creation flow

### What to keep

Do NOT remove render distance from `SettingsScreen.kt`. It stays there. Only remove it from the world creation screen.

### Guard condition

If the world creation screen passes a `viewDistance` parameter into `ServerHostService.kt` or `server.properties`, replace that with the hardcoded default of `6` instead of removing the parameter entirely — other code may depend on it.

---

## Task 9 — Render Distance Warning in Settings

### Problem

In `SettingsScreen.kt`, when the user moves the render distance slider above 6, show an inline warning that high render distances only work well with a pre-generated world (i.e. a backup uploaded). New worlds will face chunk rendering issues at high distances.

### Logic

Track the current slider value. Show the warning when value > 6:

```kotlin
var viewDistance by remember { mutableStateOf(appPreferences.viewDistance) }
var showRenderWarning by remember(viewDistance) { mutableStateOf(viewDistance > 6) }
```

### Warning UI

Render directly below the view distance slider:

```kotlin
AnimatedVisibility(visible = viewDistance > 6) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFF4757).copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = Color(0xFFFF4757),
            modifier = Modifier.size(16.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = "High render distance warning",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFF4757)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Render distances above 6 work best with a pre-generated world. " +
                       "On a new world, players may see blank terrain and lag spikes. " +
                       "Upload a world backup first for the best experience.",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 16.sp
            )
        }
    }
}
```

### Behaviour

- Warning appears with `AnimatedVisibility` fade when slider crosses above 6
- Warning disappears automatically when slider drops back to 6 or below
- Warning is purely informational — it does NOT block the user from saving the value
- The warning persists as long as the slider stays above 6, even after the user saves

---

## Files Modified

| File | Change |
|------|--------|
| `assets/paper-world-defaults.yml` | Raise load rate to 200, keep send rate auto |
| `assets/paper-global.yml` | IO + worker threads raised to 3 |
| `assets/default_plugins/PocketCraftChunkLoader.jar` | New — velocity-aware chunk preloader plugin |
| `ServerHostService.kt` | Copy new plugin JAR; apply Flight Mode distances; set `firstBootComplete` after pregen |
| `AppPreferences.kt` | Add `flightModeEnabled`, `firstBootComplete` preferences |
| `SettingsScreen.kt` | Flight Mode toggle; first boot distance lock; render distance warning above 6 |
| `HomeScreen.kt` | Auto-dismiss "moving fast" banner |
| `ServerConsole.kt` | Detect "moved too quickly", "[Chunky] Task finished"; emit events |
| Version picker screen (whichever file) | Lock Modpack card with coming soon dialog |
| World creation screen (whichever file) | Remove render distance option entirely |

---

## Do NOT change

- Existing Fast Start toggle logic
- Existing Chunky pre-gen logic (only add the `chunky radius 600` override when Flight Mode is on)
- Any relay or networking code
- RAM allocation flags in `launcher.c`

---

## Acceptance Criteria

- [ ] `paper-world-defaults.yml` has `player-max-chunk-load-rate: 200` and `target-player-chunk-send-rate: -1`
- [ ] `paper-global.yml` has `io-threads: 3` and `worker-threads: 3`
- [ ] `PocketCraftChunkLoader.jar` copied to `plugins/` on every server start
- [ ] Plugin activates when player speed exceeds 0.5 blocks/tick and pre-loads 6 chunks ahead
- [ ] Flight Mode toggle visible in Settings below Fast Start
- [ ] Flight Mode sets `view-distance=8`, `simulation-distance=6` on server start
- [ ] Flight Mode shows RAM warning chip when enabled
- [ ] "Player moving fast" banner appears on Home screen and auto-dismisses after 4 seconds
- [ ] View distance slider is locked with orange lock message on first boot
- [ ] View distance slider unlocks automatically after `[Chunky] Task finished` is detected in logs
- [ ] `firstBootComplete` preference persists across app restarts — lock never re-appears after first pregen
- [ ] Modpack card in version picker is visible but dimmed (alpha 0.5) with lock badge
- [ ] Tapping Modpack card opens "Coming Soon" dialog with purple "Got it" button
- [ ] Modpack card does NOT navigate or select anything
- [ ] Render distance option is fully removed from the world creation screen
- [ ] World creation still defaults to `view-distance=6` in `server.properties` (hardcoded, not from UI)
- [ ] Render distance warning appears in Settings when slider is moved above 6
- [ ] Warning mentions uploading a world backup and fades in/out with `AnimatedVisibility`
- [ ] Warning does NOT block saving — user can still apply high render distance if they choose
