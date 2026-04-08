# PocketCraft Server Performance, Relay Stability, and Low-RAM Hardening Plan

Date: 2026-04-05
Status: Research completed, implementation tasks refined
Priority: P0 (startup reliability on low-end devices), P1 (smoother chunk behavior)

## Scope Requested

1. Keep hosting stable on low RAM devices.
2. Make chunk loading/generation smoother and faster on weak phones.
3. Fix crash when RAM mode is set to manual and server starts.
4. Validate Android 14/15 foreground-service crash risk.
5. Confirm whether Claude-suggested fixes are already implemented.

## Verified Codebase Findings

### A) Android 14/15 foreground service compliance

Current state in code:
- Implemented in app/src/main/AndroidManifest.xml:
  - `android:foregroundServiceType="dataSync|specialUse"` on `.server.ServerHostService`
  - `android.permission.FOREGROUND_SERVICE`
  - `android.permission.FOREGROUND_SERVICE_DATA_SYNC`
  - `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`
  - `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" ... />`
- Implemented in app/src/main/kotlin/com/pocketcraft/server/server/ServerHostService.kt:
  - `startForeground(..., ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` on Android Q+
- Defensive launch path exists in app/src/main/kotlin/com/pocketcraft/server/ui/screens/ServerStateHolder.kt:
  - `ServerHostService.start(...)` is wrapped in `runCatching`
  - `SecurityException` is caught and logged to console

Conclusion:
- Claude's suggested Android 14+ manifest/runtime foreground-service fix appears already implemented.
- Remaining action is validation on affected devices/build variants, not redesign.

### B) Relay reconnect resilience

Current state in code (app/src/main/kotlin/com/pocketcraft/server/server/ServerHostService.kt):
- Relay loop is continuous while service/job is active (no permanent 5-attempt stop).
- Exponential backoff with jitter exists via `computeRelayReconnectDelayMs(...)`.
- Periodic health checks exist (`RELAY_HEALTH_CHECK_INTERVAL_MS`) and force reconnect when unhealthy.
- UI events already report reconnecting/tunnel failed/tunnel connected transitions.

Conclusion:
- Core reconnect architecture is already in place.
- This area is no longer primary risk versus low-RAM startup stability.

### C) Low-RAM and manual RAM handling

Current state in code:
- app/src/main/kotlin/com/pocketcraft/server/util/RamUtils.kt:
  - Device RAM classification and safe bounds helpers exist.
  - `computeManualRamBounds(...)` and `clampManualRamMb(...)` are present.
- app/src/main/kotlin/com/pocketcraft/server/data/preferences/AppPreferences.kt:
  - `sanitizeManualRamMb(...)` exists and persists corrected values.
- app/src/main/kotlin/com/pocketcraft/server/ui/screens/ConsoleScreen.kt:
  - Manual RAM state uses sanitized preference value.
  - Slider passes through `RamUtils.clampManualRamMb(...)`.
- app/src/main/kotlin/com/pocketcraft/server/server/ServerLauncher.kt:
  - `resolveHeapConfig(...)` normalizes manual/full/low heap targets.
  - Extra system RAM reservation is applied for low-end devices.

Conclusion:
- Most manual-RAM crash prevention is implemented.
- If crash still occurs at start, likely root causes are:
  1. Foreground-service launch policy timing on specific OEM ROM states.
  2. Native/JVM startup failure on constrained memory after launch begins.
  3. A corner-case preference/config mismatch outside current clamp paths.

### D) Chunk generation smoothness and startup speed

Current state in code (app/src/main/kotlin/com/pocketcraft/server/server/ServerLauncher.kt):
- Adaptive distances are already applied at startup via `applyAdaptiveDistances(...)`.
- New worlds receive lower startup distances than existing worlds.
- Low-RAM devices already get conservative view/simulation values.

Gap:
- Settings screen recommendations can be aggressive for weaker devices.
- No explicit two-phase profile (startup conservative -> runtime relax) after server reaches stable state.

## What Is Already Implemented vs Missing

Already implemented:
1. Android 14/15 special-use foreground-service manifest + runtime type.
2. SecurityException catch in server start UI flow.
3. Low-RAM/manual-RAM clamping in utility, prefs, UI, and launcher paths.
4. Adaptive chunk-distance tuning and relay reconnect health loop.

Still missing or needs hardening:
1. Deterministic reproduction and diagnosis of "manual RAM mode crash at server starting" on low-end devices.
2. Extra startup guardrails around foreground-service start timing and user feedback path.
3. Two-phase chunk profile for low-end devices (startup-safe then optional relaxation).
4. Tighter recommended distance defaults in settings for low-end devices.

## Implementation Tasks (Actionable)

## P0-1: Add startup crash diagnostics for manual RAM mode

Files:
- app/src/main/kotlin/com/pocketcraft/server/server/ServerLauncher.kt
- app/src/main/kotlin/com/pocketcraft/server/ui/screens/ServerStateHolder.kt

Tasks:
1. Emit structured startup diagnostics before JVM launch:
   - total RAM, computed bounds, selected mode, normalized manual value, final Xms/Xmx.
2. Tag failure reason buckets in output:
   - `fgs_security`, `heap_normalization`, `native_launch`, `oom_or_lmk`.
3. Surface explicit user-facing message in UI when startup fails in manual mode.

Acceptance:
- Every startup failure now provides enough detail to classify root cause from logs.

## P0-2: Harden foreground-service start timing and fallback UX

Files:
- app/src/main/kotlin/com/pocketcraft/server/ui/screens/ServerStateHolder.kt
- app/src/main/kotlin/com/pocketcraft/server/server/ServerHostService.kt

Tasks:
1. Keep existing `SecurityException` catch, but add explicit UI state transition and clear retry instruction.
2. Add one guarded immediate retry (short delay) only when error message indicates transient start restriction.
3. Ensure retry does not loop infinitely.

Acceptance:
- No silent failure path when foreground-service start is blocked.

## P1-1: Two-phase low-end chunk profile

Files:
- app/src/main/kotlin/com/pocketcraft/server/server/ServerLauncher.kt
- app/src/main/kotlin/com/pocketcraft/server/server/ServerHostService.kt

Tasks:
1. Keep current startup adaptive distances as phase 1.
2. After confirmed stable runtime (for example after N minutes healthy), optionally relax distances by one tier when RAM headroom is safe.
3. Never auto-relax on low-end devices that remain under pressure.

Acceptance:
- Faster startup on low-end devices remains, with optional smooth quality increase when safe.

## P1-2: Align settings recommendations with adaptive launcher policy

Files:
- app/src/main/kotlin/com/pocketcraft/server/ui/screens/SettingsScreen.kt
- app/src/main/kotlin/com/pocketcraft/server/server/ServerLauncher.kt

Tasks:
1. Recompute recommended `view-distance` and `simulation-distance` using same RAM tiers as launcher profile.
2. Warn when user-selected values exceed low-end-safe thresholds.

Acceptance:
- Users do not get contradictory tuning advice between settings UI and runtime launcher behavior.

## Validation Matrix

1. Foreground service compliance test
- Device: Android 14/15 physical phone.
- Action: Start/stop server 10 cycles from active app screen.
- Pass: No SecurityException crash and clear feedback if blocked.

2. Manual RAM crash test
- Device: low-RAM phone (or equivalent test profile).
- Action: Start in manual mode at min, mid, and max safe values.
- Pass: No app crash; failures are classified with diagnostic bucket.

3. Low-end chunk smoothness test
- Action: Create new world and measure:
  - time to server ready log
  - time to first join
  - player-visible stutter during first 2 minutes
- Pass: Better or equal startup times versus current baseline, no regression on existing worlds.

4. Relay resilience test
- Action: Cut and restore internet at 10s, 30s, 60s while running.
- Pass: Relay reconnects automatically without requiring app restart.

## Execution Order

1. P0-1 startup diagnostics.
2. P0-2 foreground-service UX hardening.
3. P1-2 settings recommendation alignment.
4. P1-1 two-phase chunk profile.
5. Full validation matrix.

## Build/Install Requirement

After each code change batch, run install-debug and verify installation on connected device.
