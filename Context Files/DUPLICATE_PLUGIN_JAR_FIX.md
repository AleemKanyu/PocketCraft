# PocketCraft Duplicate Plugin JAR Fix

## Problem
Existing installs can have bridge plugin JARs from the old runtime downloader, for example `Floodgate-Spigot.jar`, alongside the new bundled APK asset `floodgate-spigot.jar`.

Paper then logs:

```text
[ERROR]: Ambiguous plugin name 'floodgate' for files 'plugins/floodgate-spigot.jar' and 'plugins/Floodgate-Spigot.jar'
```

This can load two Floodgate builds at once and make Bedrock startup unpredictable.

## Fix
`BundledPluginInstaller` now:

- Keeps canonical bundled filenames:
  - `Geyser-Spigot.jar`
  - `floodgate-spigot.jar`
- Deletes known legacy capitalization variants before copying bundled plugins.
- Performs a case-insensitive scan for bundled plugin filename duplicates.
- Deletes stale canonical bundled plugins when the APK asset size differs.
- Reinstalls the current bundled copies on app update for all world server directories.
- Still runs on every server start through `ServerFileManager.prepareRuntimeArtifacts()`.

## Verification
After starting a server, Paper should list only one Floodgate entry:

```text
[PluginInitializerManager] Bukkit plugins (...):
- Geyser-Spigot (...)
- floodgate (...)
```

The ambiguous plugin name error must be gone.
