# PocketCraft TODO

Date: 2026-04-01

## Finish These Tasks

- Restore the Android boot splash to the normal PocketCraft app icon.
- Keep the in-app Java/loading screen on the Minecraft diamond pickaxe artwork.
- Fix server details so name, description, and photo do not reset when moving between pages before first server start.
- Propagate supported server details into the actual Minecraft server metadata:
  - server icon file
  - MOTD / server list description
- Replace raw world-type values with friendlier labeled options and icons.
- Make the top-left app icon box smaller and visually aligned with the relay control on the right.
- Make the creeper image corner radius match the outer box radius.

## Guardrails

- Only claim Minecraft metadata that the server can actually control.
- Re-check the local code paths for:
  - Android splash theme
  - custom loading screen
  - server.properties writes
  - server icon file generation
  - top bar icon container sizing
- Build after changes.
- Install the updated debug app on the connected device.
