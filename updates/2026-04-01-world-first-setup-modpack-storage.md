# 2026-04-01 World First Setup + Modpack/Storage Update Plan

## User Request Summary
- Replace "PocketCraft Server" title with the active world name on the main world card.
- For newly created worlds: auto-open/select it and guide first-time setup (version selection/download prompt).
- Replace duplicate/awkward dropdown arrows with a cleaner single indicator.
- Ensure newly created worlds prompt version selection as first-time setup.
- Keep separate folders/backup isolation so backups from different worlds never collide.
- Add settings option to remove multiple installed versions for storage cleanup.
- Keep UI style consistent with existing world/version card design.
- Add modpack support path under version selector/plugins if feasible.
- Split version selector into two paths: normal Minecraft versions and modpacks.
- Avoid API-key/rate-limit regressions.

## Implementation Plan
1. Home card UX:
   - show active world name as card title.
   - remove duplicate arrows and use one clear chevron affordance.
2. First-time world setup flow:
   - extend create-world result so UI can detect first-time world creation.
   - auto-switch to the new world on create.
   - surface setup CTA/banner to open version selector immediately.
3. World-specific backup isolation:
   - store backups under world-specific subfolders in app + Downloads targets.
4. Storage cleanup in settings:
   - add card-style section with multi-select list of downloaded versions and delete selected action.
5. Version selector split:
   - add tabs/segmented toggle: Vanilla vs Modpacks.
   - Vanilla keeps current behavior.
   - Modpacks initial integration via curated provider API (if stable endpoint available) or placeholder-disabled UI with clear messaging if API is not ready.
6. Plugin/modpack relation:
   - expose modpack capability in plugin/version flow where supported.
7. Validation:
   - run build + install on device and report result.

## Notes
- I will keep existing style and avoid broad refactors.
- If external modpack API constraints block full integration safely, I will ship an explicit guarded implementation with clear UX messaging and no runtime breakage.

## Implementation Status
- Completed: Home world card now uses active world name as title and duplicate down-arrow affordance was replaced with single chevron-based cues.
- Completed: New world creation now auto-selects the created world and routes users to version selection for first-time setup.
- Completed: Added first-time world setup state (`activeWorldNeedsSetup`) and setup CTA card.
- Completed: Backups are now world-isolated under per-world folders in app storage and Downloads (`PocketCraftWorldBackups/<world>`).
- Completed: Settings now includes card-style multi-select cleanup for deleting downloaded versions (except current version).
- Completed: Version selector now has two explicit modes: `Minecraft` and `Modpacks`.
- Completed: Modpack mode fetches list data from Modrinth with in-memory cache to reduce repeated calls and rate-limit pressure; install remains intentionally blocked for Paper-host mode with explicit UX guidance.
- Completed: Plugins hub now exposes a Mods tab.

## Validation Status
- Editor diagnostics (`get_errors`) show no Kotlin/Compose errors in modified files.
- Build/install commands were repeatedly interrupted by terminal-session instability in this run; deterministic success output was not produced for this patch set inside the current tool session.
