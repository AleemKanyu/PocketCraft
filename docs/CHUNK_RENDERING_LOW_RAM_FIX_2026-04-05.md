# Chunk Rendering and Low-RAM Stability Research + Fixes


Date: 2026-04-05
Owner: PocketCraft Android app
Status: Implemented initial high-impact fixes in app code

## User-Observed Symptoms

1. Paper watchdog reports repeated server stalls.
2. Players disconnect automatically while hosting.
3. Chunk loading/rendering feels very slow on low-end devices.
4. Changing RAM mode sometimes appears to stay on Full.

## Root Cause Research (from current logs and code)

### 1) Watchdog stack points to chunk save flush stalls

Observed watchdog stack repeatedly blocks in:
- `MoonriseRegionFileIO.partialFlush`
- `ChunkHolderManager.saveAllChunks`
- `ServerChunkCache.save`
- `SaveAllCommand.saveAll`

This strongly indicates the server thread is spending too long in forced chunk flush work.

### 2) PocketCraft was forcing heavy periodic saves

In `ServerStateHolder.startPeriodicWorldSave()` the app periodically executed:
- `save-all flush`

`save-all flush` is much heavier than `save-all` and can block long on slow phone storage, especially with active chunk writes.

### 3) RAM mode persistence edge case

`AppPreferences.ramMode` previously returned raw stored string.
Legacy or malformed values (for example mixed casing or old labels) could fail launcher mode matching and effectively fall back to Full behavior.

## Implemented Fixes (This Session)

### A) Reduced chunk-save spikes that trigger watchdog lag

File:
- `app/src/main/kotlin/com/pocketcraft/server/ui/screens/ServerStateHolder.kt`

Changes:
1. Periodic autosave command changed from `save-all flush` to `save-all`.
2. Periodic autosave interval now scales by device RAM:
   - <=3 GB: every 8 minutes
   - <=4 GB: every 6 minutes
   - >4 GB: every 4 minutes
3. `save-all flush` is still retained for explicit stop/restart safety via `requestWorldSave(...)`.

Expected impact:
- Lower chance of long server-thread stalls.
- Fewer lag spikes and fewer timeout-style disconnects.

### B) Lowered CPU load for mobile-hosted networking

File:
- `app/src/main/kotlin/com/pocketcraft/server/ui/screens/ServerStateHolder.kt`

Changes:
1. `network-compression-threshold` set to `-1` in saved server properties paths.

Expected impact:
- Less CPU overhead on weak phones.
- Better tick stability under relay/LAN player traffic.

### C) RAM mode now normalizes and persists safely

File:
- `app/src/main/kotlin/com/pocketcraft/server/data/preferences/AppPreferences.kt`

Changes:
1. Added RAM mode normalization helper.
2. Getter now normalizes old/invalid values to one of:
   - `low`, `manual`, `full`
3. Getter also self-heals stored value when legacy/malformed.
4. Setter always persists normalized value.

Expected impact:
- RAM mode changes no longer silently behave like Full due to bad stored tokens.

## Why This Addresses Your Report

1. Your log stack is specifically a chunk flush bottleneck.
2. The app had aggressive periodic `save-all flush`, matching the observed stall profile.
3. Stall spikes can make players appear to disconnect due to timeout/keepalive misses.
4. RAM-mode self-healing removes one major reason settings seem ignored.

## Validation Checklist

1. Start server on low-end device and host for 20+ minutes.
2. Verify watchdog spam frequency is reduced or gone.
3. Verify players remain connected during autosave windows.
4. Switch RAM mode in UI (`Low`, `Manual`, `Full`), restart server each time, and confirm launcher log prints selected mode correctly.
5. In server properties, verify `network-compression-threshold=-1`.

## Next Optimizations (If Needed)

1. Add runtime telemetry around save durations (`save-all` execution time).
2. Skip periodic save while high player activity is detected, then backoff save to low activity windows.
3. Add optional "Low-end performance profile" toggle that caps view/simulation distance while online.
4. Add chunk pregen throttling presets for new worlds to reduce first-join lag.
