# SETTINGS_FIXES_AGENT

## Overview
Fixes for 6 issues in the Settings screen and broadcast system, identified from screenshots.

---

## Fix 1 — Remove "Edit Config" Button Entirely

In the Settings screen composable (likely `SettingsScreen.kt` or wherever server config options are rendered), find and **delete** the `EDIT CONFIG` button completely. It should not appear anywhere in the UI — not hidden, not disabled, removed.

```kotlin
// DELETE this entire block wherever it appears:
Button(onClick = { openConfigEditor() }) {
    Text("EDIT CONFIG")
}
// Also remove any ConfigEditorScreen navigation, route, or dialog tied to this button.
```

Also remove the `"More relay locations will be available soon!"` text label that sits above the Edit Config button if it's part of the same card/section — or keep it only if it belongs to the relay selector UI independently.

---

## Fix 2 — World Type Dropdown Shows Garbled Text (dots/extra characters)

The `level-type` dropdown is rendering raw `server.properties` values with dot-padding artifacts. Fix both the data source and the display label.

**Step 1 — Replace the options list with clean display values:**

```kotlin
data class LevelTypeOption(val displayName: String, val propertyValue: String)

val levelTypeOptions = listOf(
    LevelTypeOption("Default",       "minecraft:normal"),
    LevelTypeOption("Flat",          "minecraft:flat"),
    LevelTypeOption("Large Biomes",  "minecraft:large_biomes"),
    LevelTypeOption("Amplified",     "minecraft:amplified"),
    LevelTypeOption("Single Biome",  "minecraft:single_biome_surface")
)
```

**Step 2 — In the dropdown, show only `displayName`, write only `propertyValue` to `server.properties`:**

```kotlin
DropdownMenu {
    levelTypeOptions.forEach { option ->
        DropdownMenuItem(
            text = { Text(option.displayName) }, // only show clean name
            onClick = {
                updateProperty("level-type", option.propertyValue)
                expanded = false
            }
        )
    }
}
```

**Step 3 — Sanitize existing value read from disk:**

When reading `level-type` from `server.properties` to pre-select the dropdown, sanitize the raw value:

```kotlin
fun sanitizeLevelType(raw: String): String {
    return raw
        .replace("\\\\", ":")
        .replace("\\", ":")
        .removePrefix("minecraft:")
        .let { "minecraft:$it" }
        .replace("minecraft:minecraft:", "minecraft:")
        .trim()
}

val currentLevelType = sanitizeLevelType(readProperty("level-type") ?: "minecraft:normal")
val selectedOption = levelTypeOptions.find { it.propertyValue == currentLevelType }
    ?: levelTypeOptions[0]
```

---

## Fix 3 — Max Power Mode Toggle Should Be Last in Performance Section

In the Performance & Memory section composable, reorder items so `Max Power Mode` toggle is the **last** item in the section, after all other performance sliders/options (RAM allocation, render distance, simulation distance, etc.).

```kotlin
// Order should be:
// 1. RAM allocation slider
// 2. Render distance slider
// 3. Simulation distance slider
// 4. [any other perf options]
// 5. Max Power Mode toggle   <-- LAST
// 6. Section ends
```

The warning text `"May cause overheating on prolonged sessions."` and the lock hint `"Full RAM and 32-chunk render locked – enable Max Power Mode"` should remain directly below the toggle, still at the bottom.

---

## Fix 4 — Sticky "Save Settings" Bar + Unsaved Changes Warning

### 4a — Make the save bar sticky (scrolls with user, always visible)

The save bar should be a `Box` overlaid at the bottom of the settings `Scaffold`, not inside the scrollable content. Use `Scaffold`'s `bottomBar` slot:

```kotlin
Scaffold(
    bottomBar = {
        AnimatedVisibility(visible = hasUnsavedChanges) {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Unsaved changes", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { saveSettings() }) {
                        Text("Save")
                    }
                }
            }
        }
    }
) { paddingValues ->
    // settings scroll content with paddingValues applied
}
```

This makes the bar always visible at the bottom regardless of scroll position — it is NOT fixed to the screen top, it stays anchored at the bottom of the screen and the user always sees it.

### 4b — Always trigger `hasUnsavedChanges = true` on any setting change

Audit every setting input (sliders, toggles, dropdowns, text fields) in the settings screen. Each `onChange` must set:

```kotlin
hasUnsavedChanges = true
```

If any setting is currently writing directly to `AppPreferences` on change without going through a pending state, fix it to write to a local draft state first, only persisting on Save.

### 4c — Block navigation if unsaved changes exist

Use `BackHandler` and intercept the back navigation:

```kotlin
BackHandler(enabled = hasUnsavedChanges) {
    showUnsavedDialog = true
}

if (showUnsavedDialog) {
    AlertDialog(
        onDismissRequest = { showUnsavedDialog = false },
        title = { Text("Unsaved Changes") },
        text = { Text("You have unsaved settings. Leave without saving?") },
        confirmButton = {
            TextButton(onClick = {
                showUnsavedDialog = false
                hasUnsavedChanges = false
                navController.popBackStack()
            }) { Text("Leave") }
        },
        dismissButton = {
            TextButton(onClick = { showUnsavedDialog = false }) {
                Text("Stay")
            }
        }
    )
}
```

Also intercept the bottom nav bar if the user taps another tab while in Settings:

```kotlin
// In bottom nav click handler:
if (currentScreen == Screen.Settings && hasUnsavedChanges) {
    pendingNavDestination = destination
    showUnsavedDialog = true
    return@onClick
}
```

---

## Fix 5 — Render Distance: Remove Warning, Add Inline Hint

Remove the existing warning/alert about render distance entirely (the red or orange warning text/card).

Replace it with a small inline helper text directly below the render distance slider:

```kotlin
Slider(
    value = renderDistance.toFloat(),
    onValueChange = { renderDistance = it.toInt(); hasUnsavedChanges = true },
    valueRange = 4f..maxRenderDistance.toFloat(), // maxRenderDistance capped by Max Power Mode
)
Text(
    text = "Lower values recommended for low-end devices",
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
)
```

No dialog, no modal, no color-coded alert — just quiet hint text.

---

## Fix 6 — Broadcast Banner Follows Firestore Schema Exactly

The Firestore `broadcasts` collection document has these fields (confirmed from screenshot):
- `active: Boolean`
- `title: String`
- `body: String`
- `type: String` (`"warning"`, `"info"`, `"error"`, etc.)
- `dismissible: Boolean`
- `createdAt: Timestamp`
- `targetMinVersion: Number`

### 6a — Update the broadcast data model:

```kotlin
data class BroadcastMessage(
    val id: String = "",
    val active: Boolean = false,
    val title: String = "",
    val body: String = "",
    val type: String = "info",
    val dismissible: Boolean = true,
    val createdAt: Timestamp? = null,
    val targetMinVersion: Int = 0
)
```

### 6b — Update Firestore listener to use all fields:

```kotlin
firestore.collection("broadcasts")
    .addSnapshotListener { snapshot, error ->
        if (error != null || snapshot == null) return@addSnapshotListener

        val appVersionCode = BuildConfig.VERSION_CODE

        val activeBroadcast = snapshot.documents
            .mapNotNull { doc ->
                doc.toObject(BroadcastMessage::class.java)?.copy(id = doc.id)
            }
            .filter { it.active && appVersionCode >= it.targetMinVersion }
            .maxByOrNull { it.createdAt?.seconds ?: 0 } // show most recent

        _broadcastState.value = activeBroadcast
    }
```

### 6c — Update banner UI to use `title`, `body`, and `type`:

```kotlin
activeBroadcast?.let { broadcast ->
    val (bgColor, icon) = when (broadcast.type) {
        "warning" -> Color(0xFFFFF3CD) to "⚠️"
        "error"   -> Color(0xFFFFE0E0) to "🔴"
        "info"    -> Color(0xFFE0F0FF) to "ℹ️"
        else      -> Color(0xFFF5F5F5) to "📢"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Text(icon, modifier = Modifier.padding(end = 8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(broadcast.title, fontWeight = FontWeight.Bold)
                Text(broadcast.body, style = MaterialTheme.typography.bodySmall)
            }
            if (broadcast.dismissible) {
                IconButton(onClick = { dismissBroadcast(broadcast.id) }) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss")
                }
            }
        }
    }
}
```

### 6d — When `active = false`, hide banner immediately:

The snapshot listener already handles this because `filter { it.active }` will return nothing, setting `_broadcastState.value = null`. Make sure the UI reacts to `null` by showing nothing — no cached/stale banner.

### 6e — Fix the title typo in Firestore display (cosmetic, no code change needed):
The Firestore document has `"Mantainance Break !"` — this is a data issue, not a code issue. Update the document title in Firestore console to `"Maintenance Break !"`. The code should render whatever title is in Firestore, not hardcode it.

---

## Files to modify
- Settings screen composable (`SettingsScreen.kt` or equivalent) — Fixes 1, 2, 3, 4, 5
- `AppPreferences.kt` — Fix 4b (draft state vs immediate write)
- Bottom nav composable — Fix 4c (intercept tab switch with unsaved changes)
- Broadcast ViewModel / `ServerStateHolder.kt` — Fix 6b (Firestore listener)
- Broadcast data model — Fix 6a
- Home screen composable — Fix 6c, 6d (banner UI)

## Do NOT touch
- `RelayManager.kt`, `ServerLauncher.kt`, `ServerHostService.kt` — not related
- Any plugin or server config files — Edit Config is being removed, not replaced
