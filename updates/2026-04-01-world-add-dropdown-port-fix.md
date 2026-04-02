# PocketCraft Update - 2026-04-01 - World Add, Dropdown UI, Single Port

## User Request
- Adding a new world should increase total worlds and never delete/replace existing worlds.
- World dropdown should visually match the card style above it.
- World cards should share same design/size and be scrollable.
- Worlds should keep separate plugin feature behavior.
- Enforce one port per phone, even with multiple worlds.

## Plan
1. Audit world-create/switch/storage logic.
2. Fix world-add behavior so it only appends and preserves existing worlds.
3. Refactor selector UI to card-based, same-size, scrollable list.
4. Enforce single-port behavior independent of world count.
5. Build + install + sanity verification.

## Implemented
- Added append-only world creation path in state holder:
	- New `createWorld(worldName)` validates, creates world folder, registers world, and refreshes UI.
	- `setActiveWorld(worldName)` no longer implicitly acts like world creation-only logic; it now switches active world safely.
- Added persistent world registry in `server.properties` via `pocketcraft-world-list` so world count grows and remains visible.
- Added per-world plugin profile switching:
	- Active `plugins/` folder is saved/restored per world on world switch.
	- Profiles stored under `world_plugin_profiles/<world>/`.
- Reworked home world selector UI:
	- Replaced tiny dropdown with card-style `ModalBottomSheet`.
	- Uniform card size/design for each world row and Add World row.
	- Scrollable world list.
- Updated both Home and Worlds add-world dialogs to call `createWorld(...)` (append behavior), not switch-only behavior.
- Enforced one fixed server port per phone:
	- Locked save/load path to `25565` in state holder and repository config writer.
	- Settings UI server port field set to read-only with fixed-port note.

## Verification
- Kotlin/KSP compiles successfully.
- Build + install successful on device.
- Install log: `artifacts/debug/world_ui_port_fix_install.log`
	- `BUILD SUCCESSFUL`
	- `Installed on 1 device.`
	- `lastUpdateTime=2026-04-01 17:38:20`
- Launch smoke test:
	- `adb shell am start -W -n com.pocketcraft.server/.MainActivity` returned `Status: ok`.
	- No startup fatal crash lines found in filtered logcat output for PocketCraft.
