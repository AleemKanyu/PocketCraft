# Teleport Section — Implementation Guide

## Current State

The teleport section already exists in `PlayerDetailScreen.kt` inside `CollapsibleLocationSection`.
It shows three locations and has a `Teleport` button which fires the correct `/tp` command.

**What works:**
- ✅ Current position polling (via `data get entity @a[name="..."] Pos`)
- ✅ Respawn location polling (SpawnX/Y/Z)
- ✅ Last death location polling (LastDeathLocation)
- ✅ Cross-dimension teleport (`execute in <dim> run tp ...`)

**What's missing / broken:**
- ❌ No "Teleport to me" (console → player) direction — only player → location
- ❌ No teleport between two online players
- ❌ No UI to type custom coordinates manually
- ❌ The teleport button is only on location cards, not prominent enough
- ❌ No feedback after teleport (no toast / status line)

---

## Proposed Teleport UI Section

Add a dedicated **"Teleport"** card between Health/Hunger and the location section.

```
┌─────────────────────────────────────────────┐
│  Teleport                                   │
│                                             │
│  [📍 To current position]  [🛏 To spawn]    │
│  [💀 To last death]        [🌍 To world origin] │
│                                             │
│  ── Teleport to player ──                   │
│  [Dropdown: select online player]  [GO]     │
│                                             │
│  ── Custom coordinates ──                   │
│  X [____]  Y [____]  Z [____]               │
│  Dimension [Overworld ▼]   [Teleport]       │
└─────────────────────────────────────────────┘
```

---

## Implementation Steps

### Step 1 — Add `PlayerInfo.health` + `PlayerInfo.hunger` to model (optional improvement)
No action needed for teleport specifically.

### Step 2 — Add Teleport Card Composable

Create `TeleportSection` composable in `PlayerDetailScreen.kt`:

```kotlin
@Composable
private fun TeleportSection(
    commandTarget: String,
    currentPos: PlayerLocation?,
    respawnPos: PlayerLocation?,
    lastDeathPos: PlayerLocation?,
    onlinePlayers: List<PlayerInfo>,
    thisPlayerName: String,
    onCommand: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    var customX by remember { mutableStateOf("") }
    var customY by remember { mutableStateOf("64") }
    var customZ by remember { mutableStateOf("") }
    var customDim by remember { mutableStateOf("minecraft:overworld") }
    var selectedPlayer by remember { mutableStateOf<String?>(null) }

    Card { /* ... */ }
}
```

### Step 3 — Teleport Buttons

| Button | Command |
|--------|---------|
| To current position | `tp <target> <x> <y> <z>` |
| To spawn | `execute in <dim> run tp <target> <sx> <sy> <sz>` |
| To last death | `execute in <dim> run tp <target> <dx> <dy> <dz>` |
| To world origin | `tp <target> 0 64 0` |
| To selected player | `tp <target> <otherPlayer>` |
| Custom coords | `execute in <dim> run tp <target> <x> <y> <z>` |

### Step 4 — "Teleport to player" dropdown

Use the `stateHolder.onlinePlayers` list filtered to exclude the current player.

```kotlin
val otherPlayers = onlinePlayers
    .filter { !it.name.equals(thisPlayerName, ignoreCase = true) }
    .map { it.name }
```

Display as a `DropdownMenu` or a `LazyRow` of chips.

Command:
```
tp @a[name="TargetPlayer"] OtherPlayerName
```

### Step 5 — Custom Coordinates Input

Three `OutlinedTextField` widgets for X, Y, Z + a `DropdownMenu` for dimension.

Validate that X/Y/Z are numbers before enabling the Teleport button.

```kotlin
val isValid = customX.toDoubleOrNull() != null &&
              customY.toDoubleOrNull() != null &&
              customZ.toDoubleOrNull() != null
```

### Step 6 — Feedback

After sending the command, show a short message in the console or a snackbar:

```kotlin
onCommand("execute in $dim run tp $commandTarget $x $y $z")
onMessage("Teleporting ${playerName} to ($x, $y, $z) in $dim")
```

---

## Files to Modify

| File | Change |
|------|--------|
| `PlayerDetailScreen.kt` | Add `TeleportSection` composable, insert it above `PlayerInformationSection` |
| `ServerStateHolder.kt` | Ensure `sendCommand` and `onlinePlayers` are accessible |
| No new files needed | All logic lives in the existing screen |

---

## Known Limitations

- **Offline players** cannot be teleported — `/tp` requires the player to be online
- **Cross-dimension** teleport needs `execute in <dim> run tp` syntax, which already exists in `onTeleport` lambda
- If the **Pos data** hasn't loaded yet (screen just opened), disable the "To current position" button

---

## Quick Win — Immediate Teleport Buttons (no custom UI)

The simplest first step is to add quick-action buttons directly below the location rows in the existing `CollapsibleLocationSection`. This requires zero new composables:

```kotlin
// Already exists — just make this button more prominent:
Button(  // change OutlinedButton → Button
    onClick = { onTeleport(location) },
    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.Primary)
) {
    Icon(Icons.Default.Navigation, contentDescription = null)
    Spacer(Modifier.width(6.dp))
    Text("Teleport Here")
}
```
