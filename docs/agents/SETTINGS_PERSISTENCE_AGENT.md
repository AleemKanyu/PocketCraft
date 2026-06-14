# SETTINGS_PERSISTENCE_AGENT

## Context
PocketCraft — `com.pocketcraft.server`. Settings (view distance, simulation distance, max players,
difficulty, game mode, etc.) are stored in `AppPreferences.kt` using DataStore. The Paper server
reads its config from `server.properties` on disk. The bug is that changes saved in the UI never
make it into `server.properties`, so every server launch/restart ignores them and uses whatever
was written to disk the first time Paper generated the file.

---

## Root cause

There are two sources of truth that are never synced:

1. **`AppPreferences` (DataStore)** — what the user changed in the Settings screen.
2. **`server.properties` on disk** — what Paper actually reads on startup.

The app saves settings into DataStore fine, but nothing ever reads those values back and
writes them into `server.properties` before launching the server. Paper just reads its own
file every time and ignores DataStore entirely.

---

## Fix — Write settings to `server.properties` before every server launch and restart

### Step 1 — Create `ServerPropertiesWriter.kt`

Create this file alongside `ServerLauncher.kt`.

```kotlin
package com.pocketcraft.server.server

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Properties

object ServerPropertiesWriter {

    private const val TAG = "ServerPropertiesWriter"

    /**
     * Reads the existing server.properties from disk, overlays the user's
     * preferences on top of it, and writes it back. Safe to call on every
     * launch — properties not managed by the app are left untouched.
     *
     * @param serverDir  the directory containing server.properties
     *                   e.g. File(context.filesDir, "servers/1.21.11")
     * @param prefs      snapshot of user preferences — pass in the values
     *                   already read from AppPreferences (not the Flow, the
     *                   actual resolved values)
     */
    fun apply(serverDir: File, prefs: ServerPrefsSnapshot) {
        val file = File(serverDir, "server.properties")

        // Load existing file so we don't wipe properties Paper manages itself
        val props = Properties()
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
        }

        // --- Overlay user-controlled settings ---
        props["view-distance"]            = prefs.viewDistance.toString()
        props["simulation-distance"]      = prefs.simulationDistance.toString()
        props["max-players"]              = prefs.maxPlayers.toString()
        props["difficulty"]               = prefs.difficulty.lowercase()
        props["gamemode"]                 = prefs.gameMode.lowercase()
        props["pvp"]                      = prefs.pvp.toString()
        props["allow-flight"]             = prefs.allowFlight.toString()
        props["spawn-monsters"]           = prefs.spawnMonsters.toString()
        props["spawn-animals"]            = prefs.spawnAnimals.toString()
        props["spawn-npcs"]               = prefs.spawnNpcs.toString()
        props["level-seed"]               = prefs.levelSeed   // empty string is fine
        props["motd"]                     = prefs.motd

        // Add / remove any other keys your Settings screen exposes here.
        // Keys not listed above are intentionally left as Paper wrote them.

        file.outputStream().use {
            props.store(it, "Managed by PocketCraft — do not edit while server is running")
        }

        Log.d(TAG, "server.properties updated: view-distance=${prefs.viewDistance} " +
                "sim-distance=${prefs.simulationDistance} max-players=${prefs.maxPlayers}")
    }
}

/**
 * Plain data holder — collect all AppPreferences flows into this before
 * calling ServerPropertiesWriter.apply(). Using a snapshot avoids passing
 * the whole DataStore into a utility object.
 */
data class ServerPrefsSnapshot(
    val viewDistance: Int,
    val simulationDistance: Int,
    val maxPlayers: Int,
    val difficulty: String,    // "peaceful" | "easy" | "normal" | "hard"
    val gameMode: String,      // "survival" | "creative" | "adventure" | "spectator"
    val pvp: Boolean,
    val allowFlight: Boolean,
    val spawnMonsters: Boolean,
    val spawnAnimals: Boolean,
    val spawnNpcs: Boolean,
    val levelSeed: String,
    val motd: String,
)
```

---

### Step 2 — Call `ServerPropertiesWriter.apply()` in `ServerLauncher.kt` before the JVM is launched

Find the function in `ServerLauncher.kt` that starts the Paper process (the one that calls
`dlopen` / runs the native launcher). Just before that call, add:

```kotlin
// Collect current preferences (one-shot read, not a Flow collector)
val prefs = ServerPrefsSnapshot(
    viewDistance       = appPreferences.viewDistance.first(),
    simulationDistance = appPreferences.simulationDistance.first(),
    maxPlayers         = appPreferences.maxPlayers.first(),
    difficulty         = appPreferences.difficulty.first(),
    gameMode           = appPreferences.gameMode.first(),
    pvp                = appPreferences.pvp.first(),
    allowFlight        = appPreferences.allowFlight.first(),
    spawnMonsters      = appPreferences.spawnMonsters.first(),
    spawnAnimals       = appPreferences.spawnAnimals.first(),
    spawnNpcs          = appPreferences.spawnNpcs.first(),
    levelSeed          = appPreferences.levelSeed.first(),
    motd               = appPreferences.motd.first(),
)

// Write to disk BEFORE the JVM reads server.properties
ServerPropertiesWriter.apply(serverDir, prefs)

// ... existing JVM launch code below, unchanged
```

`appPreferences.someField.first()` is a suspend call so this must be inside a coroutine
(it already will be — `ServerLauncher` starts the server from a coroutine scope in
`ServerHostService`). If any field doesn't have a Flow in AppPreferences yet, add it
following the existing DataStore pattern in that file.

---

### Step 3 — Also apply on server restart (not just first launch)

In `ServerHostService.kt`, find the restart path — the place that re-calls the server
launch logic after a stop. Make sure `ServerPropertiesWriter.apply()` is called there too.

The safest way: wrap the write call in a helper so there's only one call site:

```kotlin
// In ServerHostService.kt or ServerLauncher.kt
private suspend fun prepareAndLaunch() {
    val prefs = collectPrefsSnapshot()        // the block from Step 2
    ServerPropertiesWriter.apply(serverDir, prefs)
    launchServerProcess()                     // existing launch logic
}
```

Call `prepareAndLaunch()` for both first start and every restart. Do NOT call
`launchServerProcess()` directly anywhere else.

---

### Step 4 — Handle the case where `server.properties` doesn't exist yet (first run)

`ServerPropertiesWriter.apply()` already handles this — it checks `file.exists()` and
skips loading if the file isn't there yet, then writes a fresh one with only the
user-controlled keys. Paper fills in the rest when it starts. This is fine — Paper
merges missing keys with defaults on first boot.

---

### Step 5 — Default values in `AppPreferences.kt`

Make sure every key that `ServerPropertiesWriter` writes has a sensible default in
DataStore so a fresh install doesn't write empty/null values:

```kotlin
// In AppPreferences.kt — add these if missing, adjust names to match your existing keys
val viewDistance       = dataStore.data.map { it[VIEW_DISTANCE_KEY]        ?: 6    }
val simulationDistance = dataStore.data.map { it[SIM_DISTANCE_KEY]         ?: 4    }
val maxPlayers         = dataStore.data.map { it[MAX_PLAYERS_KEY]          ?: 10   }
val difficulty         = dataStore.data.map { it[DIFFICULTY_KEY]           ?: "normal"   }
val gameMode           = dataStore.data.map { it[GAME_MODE_KEY]            ?: "survival" }
val pvp                = dataStore.data.map { it[PVP_KEY]                  ?: true  }
val allowFlight        = dataStore.data.map { it[ALLOW_FLIGHT_KEY]         ?: false }
val spawnMonsters      = dataStore.data.map { it[SPAWN_MONSTERS_KEY]       ?: true  }
val spawnAnimals       = dataStore.data.map { it[SPAWN_ANIMALS_KEY]        ?: true  }
val spawnNpcs          = dataStore.data.map { it[SPAWN_NPCS_KEY]           ?: true  }
val levelSeed          = dataStore.data.map { it[LEVEL_SEED_KEY]           ?: ""    }
val motd               = dataStore.data.map { it[MOTD_KEY]                 ?: "A PocketCraft Server" }
```

Match the key names and types to whatever already exists in `AppPreferences.kt`. Only
add the ones that are missing.

---

## What NOT to do

- Do NOT apply settings by sending RCON commands after the server starts
  (e.g. `/difficulty hard`). RCON changes are not persisted by Paper across restarts —
  same problem all over again.
- Do NOT write `server.properties` from the Settings screen UI at save time. Write it
  only at launch time so there is one controlled moment when the file is written, and
  the server process never races with the writer.
- Do NOT use `Properties.store()` with a sorted output if you care about diff readability —
  the default `Properties.store()` writes keys in hashtable order, which is fine for Paper.

---

## Summary of files to change

| File | Change |
|------|--------|
| `ServerPropertiesWriter.kt` | **Create new** — reads existing `server.properties`, overlays DataStore values, writes back |
| `ServerPrefsSnapshot.kt` (or inline in above) | **Create new** — plain data class holding all user-controlled settings |
| `ServerLauncher.kt` | Call `ServerPropertiesWriter.apply(serverDir, prefs)` just before JVM launch |
| `ServerHostService.kt` | Ensure restart path also goes through `ServerPropertiesWriter.apply()` |
| `AppPreferences.kt` | Add defaults for any keys that are missing (`?: defaultValue`) |
