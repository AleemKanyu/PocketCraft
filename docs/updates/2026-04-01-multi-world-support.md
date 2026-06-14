# Chat Update - 2026-04-01 - Multi World Support

## Request
Enable multiple world support so users can host multiple worlds on phone, but only one world can be active at a time.

## Progress Log
- Created this update file before code changes.
- Added multi-world state support in server state holder.
- Added world discovery from server directory and active-world metadata (name, size, active flag).
- Added offline-only world switch API that updates `level-name` and refreshes UI state.
- Updated Worlds screen to show all discovered worlds and allow switching active world only while server is offline.
- Added Add World dialog: entering a new world name switches to it; Minecraft generates it on next startup if it does not exist yet.
- Updated dimension import targets to use the currently active world base (`<world>`, `<world>_nether`, `<world>_the_end`).
- Build and install verification executed via `bash ./build_and_install.sh`.
- Device install check: `versionName=1.0.0`, `lastUpdateTime=2026-04-01 16:14:25`.
- Added updates folder convention file at `updates/README.md` for per-chat markdown tracking.
