# PocketCraft Actionable Fixes (Mar 26)

## Scope
This plan converts all requested issues into implementation tasks with acceptance checks.

## 1) Player vitals rollback after Feed/Boost HP
- Problem: Health/Hunger updates briefly then revert to old values.
- Implement:
  - Keep optimistic UI update.
  - Prevent stale refresh responses from overwriting newer optimistic values.
  - Trigger one authoritative refresh after command execution settles.
- Acceptance:
  - Press Feed: hunger rises and stays at updated value.
  - Press Boost HP: health/hunger rises and stays updated.

## 2) Hide server IP while server is STARTING
- Problem: Local/Public IP appears before server is fully online.
- Implement:
  - In server identity card, show address only when state is RUNNING.
  - Show placeholder text while STARTING/STOPPED.
- Acceptance:
  - STARTING state does not display connectable IP.
  - RUNNING state displays copyable local/public address.

## 3) View distance and simulation distance stale after navigating away/back
- Problem: Saved values revert in UI when switching screens.
- Implement:
  - Refresh values from server properties on resume/return.
  - Update source-of-truth state after successful save.
- Acceptance:
  - Save changed values, navigate away/back, values remain updated.

## 4) Settings "Upload photo from phone" button styling
- Problem: Button is too large/visually heavy.
- Implement:
  - Convert to smaller outlined/hollow style.
  - Keep green border and readable label.
- Acceptance:
  - Control is compact, green outlined, and still clearly tappable.

## 5) Restart flow reliability (stop -> start / restart)
- Problem: Server sometimes fails to boot after stop/restart cycle.
- Implement:
  - Ensure previous process state fully clears before new start command.
  - Reset/startup state machine guards before relaunch.
  - Add robust timeout + fallback reset on failed boot detect.
- Acceptance:
  - Stop then Start works repeatedly.
  - Restart action consistently returns server to RUNNING.

## 6) Grey out unsupported plugins/mods by game version
- Problem: Unsupported entries appear active/selectable.
- Implement:
  - Compare item supported MC versions against selected server version.
  - Mark incompatible items disabled and visually greyed.
  - Add small compatibility label.
- Acceptance:
  - Unsupported items are visibly disabled and cannot be installed.

## 7) "No downloadable version found" fallback
- Problem: Some mods report no downloadable version despite available artifacts.
- Implement:
  - Add fallback selection strategy:
    - exact version match
    - nearest compatible loader/version
    - latest available version with warning tag
- Acceptance:
  - Previously failing mods can be downloaded when any compatible artifact exists.

## 8) Show icons for downloaded mods
- Problem: Downloaded mod rows missing icons.
- Implement:
  - Persist icon URL at install time where available.
  - Resolve and render icon in installed list with placeholder fallback.
  - Cache icon metadata for offline display.
- Acceptance:
  - Installed mod list shows icon (or deterministic placeholder) for each row.

## 9) Validation pass
- Build and install debug APK.
- Manual smoke checks:
  - Feed/Boost persistence
  - IP hidden until RUNNING
  - Distance settings persist across navigation
  - Restart works
  - Unsupported entries greyed and blocked
  - Mod icon display and version fallback behavior
