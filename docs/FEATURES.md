# PocketCraft Features Documentation

This document describes the key features implemented in PocketCraft for player management, world settings, and notifications.

## 1. Player Management & Teleportation

### Player Location Tracking
- **Online Polling**: When the server is online, the app automatically polls the location (Pos and Dimension) of all online players every minute.
- **Detailed View**: When viewing a specific player's details, polling increases to every 4 seconds to provide a real-time experience.
- **Data Source**: Location data is fetched via RCON using the `data get entity` command.

### Instant Death Storage
- **Detection**: The app monitors server logs for player death messages (e.g., "was slain", "fell from a high place").
- **Instant Fetch**: As soon as a death is detected, the app immediately sends an RCON command to fetch the `LastDeathLocation` NBT tag for that player.
- **Visualization**: The last death location is stored and displayed in the Player Details screen, allowing administrators to see exactly where a player died.

### Teleportation
- **Command**: Teleportation works by sending the `tp` command via RCON.
- **Dimension Awareness**: If a player is in a different dimension, the app uses `execute in <dimension> run tp ...` to ensure the teleportation works across dimensions.

## 2. Gamemode Management

### Online Players
- Changing a gamemode for an online player sends the standard `/gamemode <mode> <player>` command via RCON.

### Offline Players
- **Direct NBT Editing**: For players who are offline, the app directly modifies their `.dat` file in the `world/playerdata/` directory.
- **Mechanism**: The app reads the GZIP-compressed NBT file, searches for the `playerGameType` tag, and updates its value (0-3).
- **Consistency**: This ensures that when the player next logs in, they will be in the selected gamemode even if it was changed while they were away.

## 3. World Seed Visibility

- **Automatic Detection**: The app reads the actual world seed from the `level.dat` file after the world has been generated.
- **Visibility**: The seed is prominently displayed in the Settings screen with a monospace font for clarity.
- **Sharing**: Users can easily copy the seed to their clipboard or share it using the system share sheet.

## 4. Notification System

### Smart Notifications
- **Foreground (App Open)**: Only an in-app sound (using `SoundManager`) is played when the server becomes online. No push notification is shown to avoid clutter.
- **Background (App Closed)**: A single, silent push notification is sent to the Android notification shade using a dedicated low-importance channel (`pocketcraft_server_silent`).
- **Deduplication**: The app ensures only one "Server Online" notification is sent per session.
- **No Stop Notifications**: By design, no notifications are sent when the server stops, following user feedback to minimize interruptions.

## 5. Developer Guide

### Extending RCON Commands
To add new RCON-based features, use `stateHolder.sendCommand(command)` or the internal `stateHolder.sendRconCommand(command)` for direct responses.

### NBT Operations
For direct world or player data manipulation, refer to `PlayerDataManager.kt` and `NBTParser.kt`. For complex NBT structures, consider adding a full NBT library to the project dependencies.
