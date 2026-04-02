# PocketCraft Execution Note 2

Date: 2026-04-01

## Requested Changes

- Update the boot screen so the app shows a proper pickaxe and source the pickaxe art from a Minecraft API.
- Make both view distance and simulation distance adjustable again.
- Default render distances based on RAM, with 32/32 on roughly 7 GB+ phones and lower defaults on smaller devices.
- Use the Monocraft font on the Start Server button text.
- Reduce the top-left app icon padding by about half.
- Fix server detail edits so name, photo, and description keep showing after switching screens.
- When a player becomes operator, show that state on the button immediately.
- Restyle the relay selector, dropdowns, and toggles to better match the app’s rounded Duolingo-like theme.
- Remove the “JPG, PNG, WEBP and more” helper text from server details.

## Implementation Plan

1. Inspect splash-screen asset usage and fetch a proper pickaxe asset from a Minecraft API.
2. Update shared controls and top bar spacing for consistent styling.
3. Rework settings state so adjustable distances stay user-editable while still receiving RAM-tier defaults.
4. Fix server-details state refresh behavior and operator button feedback.
5. Build and install the updated debug app on the connected device.
