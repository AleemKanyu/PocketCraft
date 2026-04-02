# PocketCraft Update - 2026-04-01 - Startup Crash Fix

## Summary
- Investigated app crash on opening.
- Identified fatal exception in logcat:
  - `java.lang.IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported ex. PNG, JPG, WEBP`
  - Triggered from `painterResource(...)` inside `PocketTopBar` in `PocketNavigation.kt`.

## Root Cause
- The top-bar logo load path used a drawable resource through `painterResource`, and runtime resolved it to a non-supported type for this Compose path.

## Planned Fix
- Replace top-bar logo load with a guaranteed Compose-safe vector icon (`Icon` + Material icon) to avoid unsupported resource decoding.
- Rebuild and install on connected device.

## Verification
- Additional startup crash found and fixed:
  - `Unable to instantiate application ... ClassNotFoundException: com.pocketcraft.server.PocketCraftApp`
  - Added compatibility app class mapping in `PocketCraftApp.kt` so both names resolve:
    - `PocketCraftApp` (Hilt app class)
    - `PocketCraftApplication` (alias subclass)
  - Manifest now points to `.PocketCraftApplication`.
- Build/install validated on device.
- Fresh launch validation:
  - `adb shell am start -W -n com.pocketcraft.server/.MainActivity` returned `Status: ok`.
  - No `FATAL EXCEPTION` / `AndroidRuntime` startup crash entries in filtered logcat for PocketCraft after launch.
