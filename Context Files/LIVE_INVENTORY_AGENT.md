# LIVE_INVENTORY_AGENT

## Objective
Implement a **live player inventory viewer** inside the existing Player Dashboard/Details screen in PocketCraft (`com.pocketcraft.server`). The inventory must display in real-time, styled to match the app's pitch-black dark theme (`#0A0A0F` bg, `#6C63FF` purple accent), and mirror the classic Minecraft inventory grid layout.

---

## Architecture Overview

```
Paper Server (running on device)
    └── InventoryQueryCommand (custom plugin command via stdin)
            └── outputs JSON to stdout
                    └── ServerConsole.kt parses JSON
                            └── PlayerDataManager.kt stores + exposes state
                                    └── PlayerDashboardScreen (Compose) renders grid
```

No external plugin needed — we inject a server-side command via stdin and parse the stdout response.

---

## Part 1 — Server Side: Inventory Data Extraction

### 1.1 — Inject a custom `/pcinventory` command via the existing plugin or stdin bridge

In `ServerLauncher.kt` or wherever the Paper plugin bootstrap code lives, register a custom command that dumps inventory JSON to stdout when called:

```java
// Inside your Paper plugin's onEnable() or command handler
// Command: /pcinventory <playerName>
public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    if (!cmd.getName().equalsIgnoreCase("pcinventory")) return false;
    if (args.length == 0) return false;

    Player player = Bukkit.getPlayerExact(args[0]);
    if (player == null) {
        Bukkit.getLogger().info("[PC_INV] ERROR:player_offline");
        return true;
    }

    PlayerInventory inv = player.getInventory();
    JSONObject result = new JSONObject();
    result.put("player", player.getName());
    result.put("uuid", player.getUniqueId().toString());

    // Main inventory (slots 0–35) + armor (36–39) + offhand (40)
    JSONArray slots = new JSONArray();
    ItemStack[] contents = inv.getStorageContents(); // slots 0-35
    ItemStack[] armor = inv.getArmorContents();      // boots, leggings, chest, helmet
    ItemStack offhand = inv.getItemInOffHand();

    for (int i = 0; i < contents.length; i++) {
        slots.put(serializeItem(contents[i], i));
    }
    // Armor slots: map to indices 36-39
    for (int i = 0; i < armor.length; i++) {
        slots.put(serializeItem(armor[i], 36 + i));
    }
    // Offhand at slot 40
    slots.put(serializeItem(offhand, 40));

    result.put("slots", slots);
    result.put("heldSlot", inv.getHeldItemSlot());

    // Print with a parseable prefix so ServerConsole.kt can detect it
    Bukkit.getLogger().info("[PC_INV] " + result.toString());
    return true;
}

private JSONObject serializeItem(ItemStack item, int slotIndex) {
    JSONObject obj = new JSONObject();
    obj.put("slot", slotIndex);
    if (item == null || item.getType() == Material.AIR) {
        obj.put("empty", true);
        return obj;
    }
    obj.put("empty", false);
    obj.put("id", item.getType().getKey().toString()); // e.g. "minecraft:diamond_sword"
    obj.put("count", item.getAmount());
    obj.put("displayName", item.hasItemMeta() && item.getItemMeta().hasDisplayName()
            ? item.getItemMeta().getDisplayName() : "");

    // Enchantments
    JSONArray enchants = new JSONArray();
    item.getEnchantments().forEach((ench, level) ->
        enchants.put(ench.getKey().getKey() + ":" + level));
    obj.put("enchantments", enchants);

    // Durability
    if (item.getItemMeta() instanceof Damageable) {
        Damageable d = (Damageable) item.getItemMeta();
        obj.put("damage", d.getDamage());
        obj.put("maxDurability", item.getType().getMaxDurability());
    }
    return obj;
}
```

Register `pcinventory` in your `plugin.yml`:
```yaml
commands:
  pcinventory:
    description: PocketCraft internal inventory query
    usage: /pcinventory <player>
    permission: pocketcraft.internal
```

---

## Part 2 — Android Side: Parsing & State

### 2.1 — `ServerConsole.kt` — Detect inventory output lines

In the stdout parsing loop inside `ServerConsole.kt`, add a branch to intercept `[PC_INV]` lines:

```kotlin
// Inside the line-reading loop in ServerConsole.kt
if (line.contains("[PC_INV] ")) {
    val json = line.substringAfter("[PC_INV] ").trim()
    if (json.startsWith("ERROR:")) {
        PlayerDataManager.onInventoryError(json.removePrefix("ERROR:"))
    } else {
        PlayerDataManager.onInventoryReceived(json)
    }
    continue // don't push this line to the console log buffer
}
```

### 2.2 — `PlayerDataManager.kt` — Add inventory state

Add these data classes and state holders to `PlayerDataManager.kt`:

```kotlin
// --- Data Models ---

data class InventorySlot(
    val slot: Int,
    val empty: Boolean,
    val id: String = "",           // e.g. "minecraft:diamond_sword"
    val count: Int = 1,
    val displayName: String = "",
    val enchantments: List<String> = emptyList(),
    val damage: Int = 0,
    val maxDurability: Int = 0
)

data class PlayerInventoryState(
    val playerName: String,
    val slots: List<InventorySlot>,
    val heldSlot: Int,
    val fetchedAt: Long = System.currentTimeMillis()
)

// --- State flows ---

private val _inventoryState = MutableStateFlow<PlayerInventoryState?>(null)
val inventoryState: StateFlow<PlayerInventoryState?> = _inventoryState.asStateFlow()

private val _inventoryLoading = MutableStateFlow(false)
val inventoryLoading: StateFlow<Boolean> = _inventoryLoading.asStateFlow()

private val _inventoryError = MutableStateFlow<String?>(null)
val inventoryError: StateFlow<String?> = _inventoryError.asStateFlow()

// --- Parse incoming JSON ---

fun onInventoryReceived(json: String) {
    try {
        val obj = JSONObject(json)
        val slotsArr = obj.getJSONArray("slots")
        val slots = (0 until slotsArr.length()).map { i ->
            val s = slotsArr.getJSONObject(i)
            InventorySlot(
                slot = s.getInt("slot"),
                empty = s.getBoolean("empty"),
                id = s.optString("id", ""),
                count = s.optInt("count", 1),
                displayName = s.optString("displayName", ""),
                enchantments = (0 until s.optJSONArray("enchantments")?.length()!!)
                    .map { s.getJSONArray("enchantments").getString(it) },
                damage = s.optInt("damage", 0),
                maxDurability = s.optInt("maxDurability", 0)
            )
        }
        _inventoryState.value = PlayerInventoryState(
            playerName = obj.getString("player"),
            slots = slots,
            heldSlot = obj.getInt("heldSlot")
        )
        _inventoryLoading.value = false
        _inventoryError.value = null
    } catch (e: Exception) {
        _inventoryError.value = "Failed to parse inventory"
        _inventoryLoading.value = false
    }
}

fun onInventoryError(reason: String) {
    _inventoryError.value = when (reason) {
        "player_offline" -> "Player is offline"
        else -> "Could not fetch inventory"
    }
    _inventoryLoading.value = false
}

// --- Request inventory (called from UI) ---

fun requestInventory(playerName: String, sendCommand: (String) -> Unit) {
    _inventoryLoading.value = true
    _inventoryError.value = null
    sendCommand("/pcinventory $playerName")
}
```

---

## Part 3 — Item Icon Resolution

### 3.1 — Strategy

We cannot bundle the full Minecraft texture pack. Instead, use the **Minecraft Wiki CDN** pattern for item icons, which serves PNG sprites at:

```
https://minecraft.wiki/images/Invsprite.png  ← full sprite sheet (too complex)
```

Better approach: use **individual item PNGs** from a public CDN. The cleanest free option is:

```
https://mc-heads.net/item/{item_id_without_namespace}
// e.g. https://mc-heads.net/item/diamond_sword
// e.g. https://mc-heads.net/item/netherite_pickaxe
```

Strip `minecraft:` prefix before requesting.

### 3.2 — Compose image loading

Use **Coil** (already likely in your deps since you use Lottie/Compose):

```kotlin
// build.gradle
implementation("io.coil-kt:coil-compose:2.6.0")
```

```kotlin
@Composable
fun ItemIcon(itemId: String, modifier: Modifier = Modifier) {
    val cleanId = itemId.removePrefix("minecraft:")
    val url = "https://mc-heads.net/item/$cleanId"
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build(),
        contentDescription = cleanId,
        modifier = modifier,
        error = painterResource(R.drawable.ic_unknown_item) // fallback gray block icon
    )
}
```

Create `res/drawable/ic_unknown_item.xml` — a simple gray rounded square as fallback.

---

## Part 4 — UI: Inventory Grid in Player Dashboard

### 4.1 — Layout spec (mirror Minecraft inventory)

```
┌─────────────────────────────────────┐
│  [Helmet]  [Chest]  [Legs]  [Boots] │  ← Armor row (slots 36–39)
├─────────────────────────────────────┤
│  Row 1: Hotbar  (slots 0–8)         │  ← highlighted if heldSlot matches
├─────────────────────────────────────┤
│  Row 2: (slots 9–17)                │
│  Row 3: (slots 18–26)               │
│  Row 4: (slots 27–35)               │
├─────────────────────────────────────┤
│  [Offhand]                          │  ← slot 40
└─────────────────────────────────────┘
```

### 4.2 — Composable

Add `InventorySection` inside the Player Dashboard screen:

```kotlin
@Composable
fun InventorySection(
    playerName: String,
    inventoryState: PlayerInventoryState?,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit
) {
    val slotMap = inventoryState?.slots?.associateBy { it.slot } ?: emptyMap()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111118))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Inventory",
                color = Color(0xFF6C63FF),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onRefresh, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color(0xFF6C63FF),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh inventory",
                        tint = Color(0xFF6C63FF)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Error state
        error?.let {
            Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp)
            return@Column
        }

        // Empty state
        if (inventoryState == null && !isLoading) {
            Text(
                "Tap refresh to load inventory",
                color = Color(0xFF888888),
                fontSize = 13.sp
            )
            return@Column
        }

        // Armor row
        Text("Armor", color = Color(0xFF888888), fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(36, 37, 38, 39).forEach { slot ->
                InventorySlotCell(slotMap[slot], isHeld = false)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Hotbar (row 1, slots 0–8) — highlighted
        Text("Hotbar", color = Color(0xFF888888), fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        InventoryRow(
            slots = (0..8).map { slotMap[it] },
            heldSlot = inventoryState?.heldSlot,
            startIndex = 0
        )

        Spacer(Modifier.height(8.dp))

        // Main inventory (slots 9–35)
        Text("Backpack", color = Color(0xFF888888), fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        listOf(9..17, 18..26, 27..35).forEach { range ->
            InventoryRow(
                slots = range.map { slotMap[it] },
                heldSlot = null,
                startIndex = range.first
            )
            Spacer(Modifier.height(6.dp))
        }

        // Offhand
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Offhand  ", color = Color(0xFF888888), fontSize = 11.sp)
            InventorySlotCell(slotMap[40], isHeld = false)
        }
    }
}

@Composable
fun InventoryRow(
    slots: List<InventorySlot?>,
    heldSlot: Int?,
    startIndex: Int
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        slots.forEachIndexed { i, slot ->
            InventorySlotCell(
                slot = slot,
                isHeld = heldSlot != null && (startIndex + i) == heldSlot
            )
        }
    }
}

@Composable
fun InventorySlotCell(slot: InventorySlot?, isHeld: Boolean) {
    val borderColor = if (isHeld) Color(0xFF6C63FF) else Color(0xFF2A2A3A)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1A1A2E))
            .border(1.5.dp, borderColor, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (slot != null && !slot.empty) {
            ItemIcon(
                itemId = slot.id,
                modifier = Modifier.size(28.dp)
            )
            // Count badge
            if (slot.count > 1) {
                Text(
                    text = slot.count.toString(),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-1).dp, y = (-1).dp)
                )
            }
            // Enchantment glow
            if (slot.enchantments.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x226C63FF))
                )
            }
        }
    }
}
```

### 4.3 — Wire into Player Dashboard ViewModel/Screen

In the Player Dashboard ViewModel (or directly in the screen if you use `collectAsState`):

```kotlin
// Collect inventory state
val inventoryState by PlayerDataManager.inventoryState.collectAsState()
val inventoryLoading by PlayerDataManager.inventoryLoading.collectAsState()
val inventoryError by PlayerDataManager.inventoryError.collectAsState()

// Pass sendCommand lambda (however you currently send stdin commands)
val sendCommand: (String) -> Unit = { cmd ->
    ServerHostService.sendCommand(cmd) // adapt to your actual method
}

// In the screen composable, below existing player stats:
InventorySection(
    playerName = player.name,
    inventoryState = inventoryState,
    isLoading = inventoryLoading,
    error = inventoryError,
    onRefresh = {
        PlayerDataManager.requestInventory(player.name, sendCommand)
    }
)
```

---

## Part 5 — Auto-poll (Optional)

Add a periodic refresh that fires every 10 seconds while the Player Dashboard screen is active:

```kotlin
// In the Player Dashboard screen composable
LaunchedEffect(player.name) {
    while (true) {
        PlayerDataManager.requestInventory(player.name, sendCommand)
        delay(10_000L)
    }
}
```

Cancel it automatically when the composable leaves composition (LaunchedEffect handles this).

---

## Part 6 — Slot Tooltip on Long Press

On long-pressing a slot cell, show a bottom sheet or dialog with:
- Item display name (or formatted ID if no custom name)
- Enchantments list
- Durability bar (damage / maxDurability)

```kotlin
var selectedSlot by remember { mutableStateOf<InventorySlot?>(null) }

// In InventorySlotCell, add to Box modifier:
.combinedClickable(
    onClick = {},
    onLongClick = { if (slot != null && !slot.empty) selectedSlot = slot }
)

// After InventorySection:
selectedSlot?.let { s ->
    ItemDetailBottomSheet(slot = s, onDismiss = { selectedSlot = null })
}
```

`ItemDetailBottomSheet` — a `ModalBottomSheet` with item name, enchant list, and a `LinearProgressIndicator` for durability colored `#6C63FF`.

---

## Files to Create / Modify

| File | Action |
|---|---|
| `PlayerDataManager.kt` | Add `InventorySlot`, `PlayerInventoryState`, state flows, `onInventoryReceived`, `onInventoryError`, `requestInventory` |
| `ServerConsole.kt` | Add `[PC_INV]` line detection branch in stdout loop |
| `PlayerDashboardScreen.kt` | Add `InventorySection`, `InventoryRow`, `InventorySlotCell`, `ItemIcon` composables; wire state collection |
| `build.gradle (app)` | Confirm `io.coil-kt:coil-compose:2.6.0` is present |
| Plugin Java file | Register `/pcinventory` command with `serializeItem` helper |
| `plugin.yml` | Register `pcinventory` command |
| `res/drawable/ic_unknown_item.xml` | Gray rounded square fallback icon |

---

## Constraints & Notes

- **Do not** add a new screen — this is an additional section inside the existing Player Dashboard/Details screen, below existing health/stats cards.
- **Do not** bundle any Minecraft texture assets — use mc-heads.net CDN with Coil disk cache.
- **Do not** poll faster than every 10 seconds — Paper can handle it but it's noisy.
- The `[PC_INV]` prefix must be unique and not appear in normal server log lines; keep it exactly as written so `ServerConsole.kt` matching is reliable.
- All new composables must use `#0A0A0F` base background and `#6C63FF` purple accent, matching the existing theme.
- If `PlayerDataManager` is an `object` singleton (likely), adding `StateFlow` properties is straightforward; if it's a ViewModel, use `viewModelScope` for the request.
- The `/pcinventory` command should only be executable by `OP` or have `pocketcraft.internal` permission to prevent players from spying on each other.
