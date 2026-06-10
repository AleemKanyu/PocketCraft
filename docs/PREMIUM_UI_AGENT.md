# PREMIUM_UI_AGENT

## Objective
Polish the PocketCraft home screen UI to feel premium and iOS 26 / Liquid Glass inspired. This is a **visual-only** pass — no logic, no new features, no layout restructuring. Every change is purely cosmetic: borders, colors, elevation, spacing, radius, and animation.

---

## Context
The home screen has these sections (top → bottom):
1. App header (title + action icons)
2. "Your Server" card
3. "Join Addresses" card
4. STOP / RESTART buttons
5. Paper version card
6. Bottom navigation bar

Reference screenshots show the app running on Paper 1.21.11. The UI is Jetpack Compose. The primary accent is Minecraft green (`#3D8B45`).

---

## Changes — implement ALL of the following

---

### 1. Join Addresses card — remove thick stroke border

**Problem:** The Join Addresses card uses a heavy dark stroke border (likely `Border(1.dp, Color...)` or `OutlinedCard`) that looks dated and heavy.

**Fix:** Replace with a thin `0.5.dp` subtle border using the same surface-level tint as other cards. It should match the visual weight of the "Your Server" card exactly.

```kotlin
// Before (approx):
OutlinedCard(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline))

// After:
Card(
    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(18.dp)
)
```

---

### 2. Button hierarchy — RESTART = hero, STOP = ghost

**Problem:** STOP and RESTART are the same size pill, implying equal importance. RESTART is the primary action.

**Fix:**
- **RESTART**: Full-width (fills remaining horizontal space), solid green fill (`#3D8B45`), white text, `14sp` bold, `15.dp` corner radius, height `52.dp`
- **STOP**: Compact width (just enough for icon + label), ghost style — transparent background, `1.dp` border in `#FF3B30` at 25% alpha, text color `#FF3B30`, `13sp` semibold, same height `52.dp`
- Row layout: `Arrangement.spacedBy(8.dp)`, STOP has `wrapContentWidth()`, RESTART has `weight(1f)`

```kotlin
Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

    // STOP — ghost/outline
    OutlinedButton(
        onClick = { /* stop */ },
        modifier = Modifier.height(52.dp),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, Color(0xFFFF3B30).copy(alpha = 0.25f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color(0xFFFF3B30),
            containerColor = Color(0xFFFF3B30).copy(alpha = 0.07f)
        )
    ) {
        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text("Stop", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }

    // RESTART — hero primary
    Button(
        onClick = { /* restart */ },
        modifier = Modifier.weight(1f).height(52.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D8B45))
    ) {
        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text("Restart", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
    }
}
```

---

### 3. Floating glass bottom navigation bar

**Problem:** The nav bar is a flat bar welded to the screen bottom edge — standard Material BottomNavigation, no depth or separation from content.

**Fix:** Wrap the `BottomNavigation` / `NavigationBar` in a floating pill container:
- Remove default nav bar background/elevation
- Wrap in a `Box` pinned to screen bottom with `padding(horizontal = 12.dp, vertical = 12.dp)`
- The pill: `background = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)`, `Modifier.blur(20.dp)` (API 31+) or `graphicsLayer { renderEffect = BlurMaskFilter... }`, `clip = RoundedCornerShape(24.dp)`, thin `0.5.dp` border `outlineVariant.copy(alpha = 0.4f)`
- Active tab indicator: small rounded pill background behind the icon (`background = accentGreen.copy(alpha = 0.12f)`, `RoundedCornerShape(12.dp)`)
- Nav label font size: `9.sp`, active label color `#3D8B45`, inactive `onSurface.copy(alpha = 0.35f)`

```kotlin
Box(
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(horizontal = 12.dp, vertical = 12.dp)
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
) {
    NavigationBar(
        containerColor = Color.Transparent,
        contentColor = Color(0xFF3D8B45),
        tonalElevation = 0.dp
    ) {
        // ... nav items
    }
}
```

> **Note:** To make the blur work, the `Scaffold` content must extend behind the nav bar. Set `WindowCompat.setDecorFitsSystemWindows(window, false)` and use `contentWindowInsets = WindowInsets(0)` on Scaffold, then handle insets manually in the content padding.

---

### 4. Header action icons — grouped glass pill

**Problem:** The theme toggle icon and globe icon are separate icon buttons with separate touch targets, looking scattered.

**Fix:** Group them in a single rounded capsule container:
- Container: `background = onSurface.copy(alpha = 0.06f)`, `RoundedCornerShape(20.dp)`, `border = 0.5.dp outlineVariant.copy(0.4f)`
- Inside: two `IconButton`s with no individual background
- A `0.5.dp` vertical `Divider` between them colored `outlineVariant.copy(0.3f)`
- Icon size: `17.dp`, tint: `onSurface.copy(alpha = 0.55f)`

```kotlin
Row(
    modifier = Modifier
        .clip(RoundedCornerShape(20.dp))
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.4f), RoundedCornerShape(20.dp)),
    verticalAlignment = Alignment.CenterVertically
) {
    IconButton(onClick = { toggleTheme() }, modifier = Modifier.size(36.dp)) {
        Icon(if (isDark) Icons.Rounded.WbSunny else Icons.Rounded.DarkMode, contentDescription = "Theme", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.55f))
    }
    Divider(modifier = Modifier.width(0.5.dp).height(18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.35f))
    IconButton(onClick = { openWeb() }, modifier = Modifier.size(36.dp)) {
        Icon(Icons.Rounded.Language, contentDescription = "Web", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.55f))
    }
}
```

---

### 5. ONLINE status badge — pulsing dot animation

**Problem:** The green dot in the ONLINE badge is static. A live server status deserves a living indicator.

**Fix:** Add an infinite `infiniteTransition` scale + alpha pulse on the dot:

```kotlin
val pulse = rememberInfiniteTransition(label = "pulse")
val ringAlpha by pulse.animateFloat(
    initialValue = 0.5f, targetValue = 0f, label = "alpha",
    animationSpec = infiniteRepeatable(tween(2000, easing = EaseOut), RepeatMode.Restart)
)
val ringScale by pulse.animateFloat(
    initialValue = 1f, targetValue = 2.2f, label = "scale",
    animationSpec = infiniteRepeatable(tween(2000, easing = EaseOut), RepeatMode.Restart)
)

Box(contentAlignment = Alignment.Center) {
    // Pulse ring
    Box(modifier = Modifier
        .size(10.dp)
        .scale(ringScale)
        .background(Color(0xFF34C759).copy(alpha = ringAlpha), CircleShape))
    // Solid dot
    Box(modifier = Modifier.size(6.dp).background(Color(0xFF34C759), CircleShape))
}
```

Only show the pulse when the server is actually ONLINE. Stop the transition when offline.

---

### 6. Server icon — frosted/tinted background

**Problem:** The server icon sits on a solid opaque green square. Looks heavy and flat.

**Fix:** Replace with a semi-transparent tinted container:
- Light: `background = Color(0xFF3D8B45).copy(alpha = 0.10f)`, border `Color(0xFF3D8B45).copy(alpha = 0.18f)`
- Dark: `background = Color(0xFF3D8B45).copy(alpha = 0.22f)`, border `Color(0xFF3D8B45).copy(alpha = 0.30f)`
- Shape: `RoundedCornerShape(13.dp)`
- Icon: keep existing creeper/server icon, tint it `Color(0xFF3D8B45)` (not white)
- Size: `44.dp × 44.dp`

```kotlin
Box(
    modifier = Modifier
        .size(44.dp)
        .clip(RoundedCornerShape(13.dp))
        .background(Color(0xFF3D8B45).copy(alpha = if (isDark) 0.22f else 0.10f))
        .border(0.5.dp, Color(0xFF3D8B45).copy(alpha = if (isDark) 0.30f else 0.18f), RoundedCornerShape(13.dp)),
    contentAlignment = Alignment.Center
) {
    Icon(serverIcon, contentDescription = null, tint = Color(0xFF3D8B45), modifier = Modifier.size(24.dp))
}
```

---

### 7. "Switch worlds" — info chip instead of plain text

**Problem:** "Stop the server to switch worlds" is a plain text anchor/button that looks like an afterthought.

**Fix:** Wrap in an info chip:
- Background: `onSurface.copy(alpha = 0.04f)`, `RoundedCornerShape(10.dp)`, no border
- Leading icon: `Icons.Rounded.Info`, `13.dp`, tint `onSurface.copy(0.42f)`
- Text: `11.sp`, `onSurface.copy(0.42f)`
- Padding: `7.dp vertical, 10.dp horizontal`

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
        .padding(horizontal = 10.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
) {
    Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.42f))
    Text("Stop the server to switch worlds", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.42f))
}
```

---

### 8. Dark mode surface layering

**Problem:** All dark mode cards sit at the same `#1C1C1E` elevation. No depth.

**Fix:** Use 3 distinct surface levels:
| Layer | Usage | Color |
|---|---|---|
| `surfaceBg` | Screen background | `#111113` |
| `surfaceCard` | Cards | `#1C1C1E` |
| `surfaceInset` | Chips inside cards | `#2A2A2C` |

In `Theme.kt` or `Color.kt`, define these as dark-mode tokens and reference them consistently. The inset level (`surfaceInset`) should be used for: the "Switch worlds" chip, the address label chips inside Join Addresses, the Upgrade button background, and the Bedrock Guide / Share buttons.

---

### 9. Card corner radius — make it consistent

All cards should use `18.dp` corner radius. Audit every `Card`, `Surface`, and clipped `Box` on the home screen and normalize to `18.dp`. The version/upgrade card at the bottom currently appears to have a smaller radius than the others.

---

### 10. Typography — tighten the address line

The join address `mine.pocketcraft.online:29846` should use:
- `fontFamily = FontFamily.Monospace` (or your existing monospaced typeface)
- `letterSpacing = (-0.2).sp`
- `fontWeight = FontWeight.SemiBold`
- `fontSize = 13.sp`

This makes it feel like a technical, precise value rather than body text.

---

## What NOT to change
- App logic, server start/stop/restart behavior
- Navigation destinations
- Firebase, AdMob, relay connection code
- Any string values / user-facing copy
- Feature flags or settings

---

## Testing checklist
- [ ] Light mode — all cards visible, buttons clearly hierarchical
- [ ] Dark mode — 3 distinct surface levels visible, no card blending into background  
- [ ] ONLINE badge pulse animation plays when server is running, stops when offline
- [ ] Floating nav bar clips correctly on all screen sizes (especially smaller phones)
- [ ] Floating nav bar doesn't overlap scrollable content (check padding bottom = nav height + 12dp)
- [ ] Backdrop blur on nav degrades gracefully on API < 31 (fall back to `alpha = 0.95f` solid)
- [ ] No visual regressions on Players / Storage / Mods / Settings screens