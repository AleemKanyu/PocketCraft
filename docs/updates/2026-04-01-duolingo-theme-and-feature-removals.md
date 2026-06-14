# 2026-04-01 Duolingo Theme + Feature Simplification Update

## User Request Scope
- Remove modpack support completely, including selectors and related UI paths.
- Keep PaperMC flow only (no modpack-specific options).
- Make buttons match Duolingo-like style across the app.
- Make app shapes square-ish with rounded corners (no fully circular pills where avoidable).
- Remove inventory feature from player details because inventory fetch is unreliable.
- Ensure player heads load in player list without requiring player detail screen opening first.
- Auto-set render distance based on RAM:
  - 8 GB and above: 32
  - below 8 GB: lower value
- Ensure UI accurately shows the applied render distance value.
- Remove resource-pack URL setting from settings screen.
- Ensure version deletion in settings removes only version binaries, not world data.
- Remove "better ping" text for India relay location.
- Add separate server-details editing page/flow after server creation and from world-card dropdown.
- Allow editing only server name and server photo in that details flow.
- Keep Duolingo-style card/button visual consistency in those new/updated screens.

## Implementation Plan
1. Remove modpack code paths and UI toggles, restore single-version flow.
2. Remove player inventory UI/data fetch and retain stable player actions.
3. Fix player list head loading to be self-contained in list rows.
4. Render distance policy + UI sync improvements in state/settings.
5. Remove resource-pack URL settings input and write path.
6. Ensure delete-version action targets only downloaded version binaries.
7. Update relay config copy (remove "better ping" wording).
8. Add server details edit screen (name + photo only), accessible from creation flow and card dropdown.
9. Apply rounded-square shape consistency and Duolingo-style buttons in touched screens/components.
10. Validate with build + install and summarize outcomes.

## Notes
- This update intentionally prioritizes behavior stability and simpler UX.
- Any legacy modpack data that exists on disk will be ignored by UI after this update.
