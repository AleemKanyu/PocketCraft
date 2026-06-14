# PocketCraft — Inventory Preview & Player Location Agent

## Your Role
You are a feature implementation agent. Your job is to add inventory preview and player location/teleport features to the player detail screen in the PocketCraft Android app. Read the existing code carefully before making any changes. Do not ask questions — figure out the structure from the code and implement everything completely.

**Package name:** `com.pocketcraft.server`
**UI toolkit:** Jetpack Compose
**Design:** Match existing app style exactly — pitch black background, existing color palette. Do not change any other screens.

---

## Context

The app already has a player detail screen. This agent adds two new sections to that screen:
1. **Inventory Preview** — visual grid showing the player's current inventory with item icons
2. **Information Section** — current position, respawn location, last death location, all with teleport buttons

---

## Feature 1 — Inventory Preview

### Layout
Match the Aternos inventory layout exactly:

```
[ Armor  ] [ ← 9 columns of main inventory → ] [ Off-hand ]
[ Slot 1 ] [ 0  1  2  3  4  5  6  7  8       ] [          ]
[ Slot 2 ] [ 9  10 11 12 13 14 15 16 17       ]
[ Slot 3 ] [ 18 19 20 21 22 23 24 25 26       ]
[ Slot 4 ] [ Hotbar: 0  1  2  3  4  5  6  7  8 ]
```

Specifically:
- **Left column (4 slots):** Armor — helmet (top), chestplate, leggings, boots (bottom)
- **Main grid (3 rows × 9):** Inventory slots 9-35
- **Bottom row (9 slots):** Hotbar slots 0-8
- **Right column (1 slot):** Offhand slot

### How to get inventory data

Send this command to the server console:
```
data get entity {playerName} Inventory
```

Parse the output. The server returns NBT data like:
```
{Inventory: [{Slot: 0b, id: "minecraft:diamond_sword", Count: 1b}, ...]}
```

Write a parser that extracts:
- `Slot` — byte value, slot number
- `id` — item identifier e.g. `minecraft:diamond_sword`
- `Count` — item count
- `tag` — optional NBT tag (for enchantments etc, just ignore for display)

Slot mapping:
- Slots 0-8: Hotbar
- Slots 9-35: Main inventory
- Slot 100: Boots
- Slot 101: Leggings
- Slot 102: Chestplate
- Slot 103: Helmet
- Slot -106: Offhand

### Item icons

Use this URL pattern to get item textures:
```
https://mc-heads.net/item/{item_id_without_namespace}
```

Example:
```kotlin
// "minecraft:diamond_sword" → "diamond_sword"
val itemId = "minecraft:diamond_sword"
val iconUrl = "https://mc-heads.net/item/${itemId.removePrefix("minecraft:")}"
```

Load images using Coil (`AsyncImage` composable). If Coil is not already a dependency add:
```gradle
implementation "io.coil-kt:coil-compose:2.5.0"
```

### Inventory slot composable

Each inventory slot is a fixed-size box:

```kotlin
@Composable
fun InventorySlot(
    item: InventoryItem?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .background(
                color = Color(0xFF2A2A2A),
                shape = RoundedCornerShape(4.dp)
            )
            .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (item != null) {
            val iconUrl = "https://mc-heads.net/item/${item.id.removePrefix("minecraft:")}"
            AsyncImage(
                model = iconUrl,
                contentDescription = item.id,
                modifier = Modifier.size(36.dp),
                contentScale = ContentScale.Fit
            )
            // Show count badge if > 1
            if (item.count > 1) {
                Text(
                    text = item.count.toString(),
                    fontSize = 10.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                )
            }
        }
    }
}
```

### Data class

```kotlin
data class InventoryItem(
    val slot: Int,
    val id: String,
    val count: Int
)
```

### Full inventory composable

```kotlin
@Composable
fun InventoryPreview(items: List<InventoryItem>) {
    val slotMap = items.associateBy { it.slot }

    // Helper to get item at slot
    fun getItem(slot: Int) = slotMap[slot]

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Inventory",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Armor column (left)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InventorySlot(getItem(103)) // Helmet
                    InventorySlot(getItem(102)) // Chestplate
                    InventorySlot(getItem(101)) // Leggings
                    InventorySlot(getItem(100)) // Boots
                }

                Spacer(Modifier.width(4.dp))

                // Main inventory + hotbar
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Main inventory (3 rows × 9)
                    for (row in 0..2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (col in 0..8) {
                                val slot = 9 + (row * 9) + col
                                InventorySlot(getItem(slot))
                            }
                        }
                    }
                    // Hotbar (1 row × 9)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (slot in 0..8) {
                            InventorySlot(getItem(slot))
                        }
                    }
                }

                Spacer(Modifier.width(4.dp))

                // Offhand (right)
                Column {
                    InventorySlot(getItem(-106)) // Offhand
                }
            }
        }
    }
}
```

### NBT Parser

Write a simple string parser for the `data get entity` output:

```kotlin
object NBTParser {

    fun parseInventory(output: String): List<InventoryItem> {
        val items = mutableListOf<InventoryItem>()

        // Find the Inventory array in the output
        val inventoryMatch = Regex("Inventory: \\[(.*)\\]").find(output)
            ?: return items

        val inventoryContent = inventoryMatch.groupValues[1]

        // Split into individual item entries
        val itemPattern = Regex("\\{([^}]+)\\}")
        itemPattern.findAll(inventoryContent).forEach { match ->
            try {
                val entry = match.value
                val slot = Regex("Slot: (-?\\d+)b").find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
                val id = Regex("id: \"([^\"]+)\"").find(entry)?.groupValues?.get(1) ?: return@forEach
                val count = Regex("Count: (\\d+)b").find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                items.add(InventoryItem(slot, id, count))
            } catch (e: Exception) {
                // Skip malformed entries
            }
        }

        return items
    }

    fun parsePosition(output: String): Triple<Double, Double, Double>? {
        // Parses output of "data get entity {player} Pos"
        // Format: [X, Y, Z] or Pos: [Xd, Yd, Zd]
        val posMatch = Regex("\\[(-?[\\d.]+)d?, (-?[\\d.]+)d?, (-?[\\d.]+)d?\\]").find(output)
            ?: return null
        return Triple(
            posMatch.groupValues[1].toDouble(),
            posMatch.groupValues[2].toDouble(),
            posMatch.groupValues[3].toDouble()
        )
    }

    fun parseDimension(output: String): String {
        return when {
            output.contains("the_nether") -> "minecraft:the_nether"
            output.contains("the_end") -> "minecraft:the_end"
            else -> "minecraft:overworld"
        }
    }
}
```

### Refresh inventory

When the player detail screen is open, refresh inventory every 10 seconds:

```kotlin
LaunchedEffect(playerName) {
    while (true) {
        ServerConsole.sendCommand("data get entity $playerName Inventory")
        delay(10_000)
    }
}
```

Listen to console output and parse lines containing "Inventory:" to update the displayed items.

---

## Feature 2 — Information Section (Position, Respawn, Death Location)

### Layout
Three collapsible rows exactly like Aternos:

```
📍 Current position          [▲ expanded]
   X -616.50  Y 64.00  Z -135.50
   🌍 minecraft:overworld
                              [Teleport →]

🛏 Respawn location          [▼ collapsed]

💀 Last death location       [▼ collapsed]
```

### Data class

```kotlin
data class PlayerLocation(
    val x: Double,
    val y: Double,
    val z: Double,
    val dimension: String
) {
    fun formatted() = "X %.2f  Y %.2f  Z %.2f".format(x, y, z)
    fun dimensionDisplay() = when (dimension) {
        "minecraft:the_nether" -> "The Nether"
        "minecraft:the_end" -> "The End"
        else -> "Overworld"
    }
    fun dimensionIcon() = when (dimension) {
        "minecraft:the_nether" -> "🔥"
        "minecraft:the_end" -> "🌑"
        else -> "🌍"
    }
}
```

### How to get location data

**Current position:**
```
data get entity {playerName} Pos
data get entity {playerName} Dimension
```

**Respawn location:**
```
data get entity {playerName} SpawnX
data get entity {playerName} SpawnY
data get entity {playerName} SpawnZ
data get entity {playerName} SpawnDimension
```

If SpawnX/Y/Z returns no data or error, show "Not set".

**Last death location:**
Parse from player stats file at:
```
servers/{version}/world/stats/{playerUuid}.json
```
Look for `minecraft:custom` → `minecraft:deaths` for death count.
For location, check `servers/{version}/world/playerdata/{playerUuid}.dat` — this is binary NBT so just show "Unavailable" if parsing is too complex.

### Collapsible section composable

```kotlin
@Composable
fun CollapsibleLocationSection(
    icon: String,
    title: String,
    location: PlayerLocation?,
    playerName: String,
    onTeleport: (PlayerLocation) -> Unit
) {
    var expanded by remember { mutableStateOf(title == "Current position") }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header row — always visible, tap to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(icon, fontSize = 16.sp)
                    Text(
                        text = title,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF57F287)
                )
            }

            // Expanded content
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    if (location != null) {
                        Text(
                            text = location.formatted(),
                            fontSize = 14.sp,
                            color = Color(0xFFB3B3B3)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${location.dimensionIcon()} ${location.dimensionDisplay()}",
                            fontSize = 13.sp,
                            color = Color(0xFF6B6B6B)
                        )
                        Spacer(Modifier.height(12.dp))
                        // Teleport button
                        OutlinedButton(
                            onClick = { onTeleport(location) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF57F287)
                            ),
                            border = BorderStroke(1.dp, Color(0xFF57F287)),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Teleport, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Teleport")
                        }
                    } else {
                        Text(
                            text = "Not set",
                            fontSize = 14.sp,
                            color = Color(0xFF6B6B6B)
                        )
                    }
                }
            }
        }
    }
}
```

### Teleport action

When Teleport is tapped, show a dialog:

```
Teleport player to this location?

X -616.50  Y 64.00  Z -135.50
minecraft:overworld

[ Cancel ]  [ Teleport ]
```

On confirm, send:
```kotlin
ServerConsole.sendCommand("tp $playerName ${location.x} ${location.y} ${location.z}")
```

For cross-dimension teleport (nether/end), use:
```kotlin
ServerConsole.sendCommand("execute in ${location.dimension} run tp $playerName ${location.x} ${location.y} ${location.z}")
```

### Full Information section

```kotlin
@Composable
fun PlayerInformationSection(playerName: String) {
    var currentPos by remember { mutableStateOf<PlayerLocation?>(null) }
    var respawnPos by remember { mutableStateOf<PlayerLocation?>(null) }
    var showTeleportDialog by remember { mutableStateOf<PlayerLocation?>(null) }

    // Refresh position every 5 seconds
    LaunchedEffect(playerName) {
        while (true) {
            ServerConsole.sendCommand("data get entity $playerName Pos")
            ServerConsole.sendCommand("data get entity $playerName Dimension")
            delay(5_000)
        }
    }

    // Listen to console output for position updates
    // Wire this to your existing console output stream/flow

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Information",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White
        )

        CollapsibleLocationSection(
            icon = "📍",
            title = "Current position",
            location = currentPos,
            playerName = playerName,
            onTeleport = { showTeleportDialog = it }
        )

        CollapsibleLocationSection(
            icon = "🛏",
            title = "Respawn location",
            location = respawnPos,
            playerName = playerName,
            onTeleport = { showTeleportDialog = it }
        )

        CollapsibleLocationSection(
            icon = "💀",
            title = "Last death location",
            location = null, // Parse from NBT if possible, otherwise null
            playerName = playerName,
            onTeleport = { showTeleportDialog = it }
        )
    }

    // Teleport confirmation dialog
    showTeleportDialog?.let { loc ->
        AlertDialog(
            onDismissRequest = { showTeleportDialog = null },
            containerColor = Color(0xFF1A1A1A),
            title = { Text("Teleport player?", color = Color.White) },
            text = {
                Column {
                    Text(loc.formatted(), color = Color(0xFFB3B3B3))
                    Text("${loc.dimensionIcon()} ${loc.dimensionDisplay()}", color = Color(0xFF6B6B6B))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cmd = if (loc.dimension == "minecraft:overworld") {
                        "tp $playerName ${loc.x} ${loc.y} ${loc.z}"
                    } else {
                        "execute in ${loc.dimension} run tp $playerName ${loc.x} ${loc.y} ${loc.z}"
                    }
                    ServerConsole.sendCommand(cmd)
                    showTeleportDialog = null
                }) {
                    Text("Teleport", color = Color(0xFF57F287))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTeleportDialog = null }) {
                    Text("Cancel", color = Color(0xFFB3B3B3))
                }
            }
        )
    }
}
```

---

## Integration — Add to Player Detail Screen

Find the existing player detail screen. Add these two sections in this order:

1. After the health/hunger section → add `InventoryPreview(items = inventoryItems)`
2. After the inventory → add `PlayerInformationSection(playerName = player.name)`

Wire the console output parsing into `NBTParser.parseInventory()` and `NBTParser.parsePosition()` wherever console output is currently collected (search for log listener, console flow, or broadcast receiver that collects server output).

---

## Quality Checklist

Before finishing, verify:

- [ ] `InventoryItem` data class exists
- [ ] `NBTParser.kt` exists with `parseInventory()`, `parsePosition()`, `parseDimension()`
- [ ] `InventorySlot` composable renders correct item icon from mc-heads.net
- [ ] Item count badge shows for stacks > 1
- [ ] Armor slots (helmet/chest/legs/boots) show in correct order left column
- [ ] Main inventory shows 3 rows × 9 columns (slots 9-35)
- [ ] Hotbar shows as bottom row (slots 0-8)
- [ ] Offhand slot shows on right
- [ ] Empty slots show as dark grey boxes
- [ ] Inventory refreshes every 10 seconds while screen is open
- [ ] `PlayerLocation` data class exists
- [ ] Current position section is expanded by default
- [ ] Respawn and death location sections are collapsed by default
- [ ] Tap header to expand/collapse each section
- [ ] Position shows X Y Z formatted to 2 decimal places
- [ ] Dimension shows correct icon and display name
- [ ] Teleport button shows confirmation dialog before teleporting
- [ ] Teleport sends correct command (with `execute in` for non-overworld dimensions)
- [ ] Position refreshes every 5 seconds while screen is open
- [ ] All dialogs use dark background (`#1A1A1A`)
- [ ] Coil dependency is added if not already present
- [ ] App builds without errors

---

## What This Does NOT Change

- Any existing screens beyond the player detail screen
- Relay system, RAM settings, world import — all unchanged
- Existing player detail screen sections (header, health, control, statistics, delete data)
- Theme and colors of the rest of the app
