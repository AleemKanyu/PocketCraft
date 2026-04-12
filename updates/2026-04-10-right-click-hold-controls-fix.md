# Right-Click Hold Controls Fix

Date: 2026-04-10
Chat: Investigated inaccurate right-click hold actions like eating, drawing bows, and charging tridents.

## What Changed

- Patched the local Pojav upstream source at `.tmp/PojavLauncher-src-2` so right-click tap timing now uses the configured long-press delay instead of a hardcoded `150 ms`.
- Fixed the custom control button move bounds check so held presses use local view coordinates instead of parent-space coordinates.
- Updated the default control layout to add a dedicated toggle-style `USE` button mapped to right click for hold-based item use.

## Files Changed

- `.tmp/PojavLauncher-src-2/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/customcontrols/mouse/RightClickGesture.java`
- `.tmp/PojavLauncher-src-2/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/customcontrols/buttons/ControlButton.java`
- `.tmp/PojavLauncher-src-2/app_pojavlauncher/src/main/assets/default.json`

## Why

- The old timing created a mismatch between the left-click hold gesture and the right-click tap gesture, leaving a dead zone where a press could feel ignored.
- The old move bounds logic could mark a finger as out-of-bounds even while it was still on the same button, which made hold actions feel unreliable.
- A dedicated `USE` toggle gives mobile players a practical control for multiplayer actions that depend on keeping right click held down.

## Verification Notes

- Static review confirms the right-click gesture now follows `LauncherPreferences.PREF_LONGPRESS_TRIGGER`.
- Static review confirms button move bounds now compare against `0..width` and `0..height`, which matches local touch coordinates.
- The default control layout now includes both the original `SEC` button and a new toggle `USE` button.

## Important Scope Note

- The shipped PocketCraft app module in this repo does not currently compile these Pojav client sources into `app/release/PocketCraft.apk`.
- This fix is applied to the local upstream checkout that exists in the workspace for client input work.
