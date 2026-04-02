# PocketCraft Execution Note

Date: 2026-04-01

## Requested Changes

- Make the main server card and server details flow feel closer to Duolingo's playful, chunky visual style.
- In server details, use a Duolingo-themed upload button that picks photos from the phone instead of treating the image as a URL-style detail.
- Keep a server description field that appears under the server name on the main card.
- Remove repeated server-detail options from Settings so customization lives in one place.
- Make new servers load chunks less slowly while keeping restored-backup performance strong.
- Make the top-left app icon a bit bigger with rounder corners.

## Implementation Plan

1. Refresh the server identity card and details form styling.
2. Persist uploaded server photos in app storage so they survive process/app restarts.
3. Keep server descriptions stored per world and surface them under the server title.
4. Trim repeated messaging/options from Settings.
5. Relax overly aggressive new-world chunk throttling and stop forcing render distance down too much.
6. Update top bar icon sizing and corner radius.

## Verification

- Build the app and confirm the touched files compile cleanly.
- Check that image upload opens the phone picker and still shows after reloading state.
- Check that the server description appears on the main server card.
- Check that Settings no longer repeats server-detail controls.
- Check that new worlds are not clamped to very low view/simulation values.
