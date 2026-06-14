# Modpack Support Notes

## Goal

Add support for user-selectable Minecraft modpacks, including packs from Modrinth and other supported sources.

## Initial Questions

- Confirm whether modded Java clients are expected, or whether all users still join through Bedrock/Geyser. Bedrock/Geyser cannot fully play arbitrary Java modpacks with custom blocks/items.
- Confirm whether CurseForge direct install should be supported with an official API key. Current implementation can show CurseForge discovery results through the existing public endpoint, but direct installs are limited to Modrinth `.mrpack` packs.
- Confirm whether PocketCraft should hide heavy packs on low-RAM devices or simply fail during launch with a clear error.

## Initial Implementation Direction

- Prefer official APIs where available, starting with Modrinth's public API for pack metadata and version files.
- Validate loader, game version, dependencies, and file hashes before installing or launching a pack.
- Keep support source-specific behind a provider interface so additional websites can be added without rewriting install logic.
- Add clear failure states for unsupported loaders, missing runtime support, failed downloads, and hash mismatches.

## Implemented In This Pass

- Enabled the Modpack server type in the server configuration bottom sheet.
- Default empty modpack search now queries Pokemon/Cobblemon, SkyBlock, and OneBlock-style packs.
- Modrinth modpacks install into the currently active world so startup can find the installed files.
- Modrinth pack file downloads validate `sha512` or `sha1` hashes from `modrinth.index.json`.
- Installed modpacks persist their Minecraft version, loader, loader version, pack id, and launch target.
- Server startup can now resolve persisted modpack launch targets.
- Forge and NeoForge modpacks use the generated `unix_args.txt` launch target through the external JVM path.
