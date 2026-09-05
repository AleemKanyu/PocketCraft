---
name: stitch
description: "Converts Google Stitch UI wireframes, HTML mockups, and exported screen designs into tactile PocketCraft Jetpack Compose components adhering to DuoButton, PocketThemeTokens, and 3D game UI standards."
---

# /stitch - Google Stitch Wireframe to Jetpack Compose Converter

This skill equips Claude to ingest UI wireframes, screenshots, HTML/CSS exports, and component specs from **Google Stitch** (`stitch.withgoogle.com` or local scratch wireframes) and convert them into production-ready Jetpack Compose code matching PocketCraft's tactile 3D design system.

---

## 1. PocketCraft Design System Rules

When generating or refactoring UI from Stitch:

### A. The 3D Tactile Aesthetic
PocketCraft does NOT use flat, plain Material 3 cards. It uses a tactile, playful, chunky 3D design inspired by Brawl Stars and Duolingo.
- Every interactive button is a **`DuoButton`** (`com.pockethost.app.ui.components.DuoButton`).
- Static cards use **`GameCard`** or **`PocketHostCard`**.
- Elevation is represented by a **crisp bottom-edge depth strip** using `Modifier.bottomShadow(...)`, NOT a blurry drop shadow.

### B. Button Mapping Reference
Map generic buttons from Stitch to `DuoButton` variants:

| Stitch Element | `DuoButton` Variant | Purpose / Semantic |
| :--- | :--- | :--- |
| **"Start Server" / Hero Action** | `DuoButtonVariant.StartServer` | Vibrant green with animated pulse / start icon |
| **Primary Action / Save / Confirm** | `DuoButtonVariant.Primary` | Brand accent filled button with tactile press |
| **Cancel / Secondary Action** | `DuoButtonVariant.Secondary` or `SecondaryGray` | Muted background with contrasting text |
| **Delete / Stop Server / Ban** | `DuoButtonVariant.Danger` | Red alert with dark red 3D bottom strip |
| **Restart / Warning** | `DuoButtonVariant.Warning` | Orange/Amber with dark amber 3D bottom strip |
| **Documentation / Info** | `DuoButtonVariant.Info` | Blue action button |
| **Discord Community** | `DuoButtonVariant.Discord` | Blurple Discord branded button |
| **Pro / Premium Upgrade** | `DuoButtonVariant.Pro` | Shimmer / gold accent badge button |

Example `DuoButton` usage:
```kotlin
DuoButton(
    text = "START SERVER",
    onClick = { serverStateHolder.startServer() },
    variant = DuoButtonVariant.StartServer,
    modifier = Modifier.fillMaxWidth()
)
```

### C. Bottom Shadow Depth Modifier
For custom containers or headers, apply the bottom-only depth strip:
```kotlin
import com.pockethost.app.ui.theme.bottomShadow
import com.pockethost.app.ui.theme.Pocket3dShadowTint

Box(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .bottomShadow(
            shadowColor = Pocket3dShadowTint.copy(alpha = 0.25f),
            shadowHeight = 4.dp,
            cornerRadius = 16.dp
        )
        .padding(16.dp)
) {
    // Card contents
}
```

### D. Dynamic Theme & Contrast
Never hardcode light/dark hex colors directly. Always check theme luminance:
```kotlin
import com.pockethost.app.ui.theme.pocketIsDarkTheme

val isDark = pocketIsDarkTheme()
val iconTint = if (isDark) Color.White else Color(0xFF1E293B)
```

---

## 2. Converting Stitch Wireframes to Code: Step-by-Step

When the user provides a Stitch wireframe (HTML, PNG, or description):

1. **Deconstruct Layout Hierarchy**:
   - Break down into TopBar (with dynamic back button & title), Scrollable Column, and Sticky Bottom Bar (if actions exist).
   - Use `Scaffold` with `contentWindowInsets = WindowInsets.systemBars`.

2. **Extract Data Models & State**:
   - Identify UI state fields: Loading states, error states, form input text, active toggles.
   - Use `rememberSaveable` or delegate to `ServerStateHolder.kt` for server-lifecycle state.

3. **Replace Standard Inputs with PocketCraft Components**:
   - `TextField` -> Use `DuoTextFieldStyle` or themed rounded text input.
   - `Switch` -> Use `DuoToggle`.
   - `Button` -> Use `DuoButton`.
   - Card container -> Use `GameCard` with rounded corners (16.dp).

4. **Verify Tap Targets & Ergonomics**:
   - Ensure interactive areas are at least `48.dp` tall (`Modifier.heightIn(min = 48.dp)`).
   - Apply padding tokens: `16.dp` screen edge margins, `12.dp` inter-item spacing, `8.dp` compact spacing.

---

## 3. Stitch Local Asset Inspection

Wireframes and HTML mocks exported from Stitch are stored in:
- Scratch storage: `/home/aleemkanyu/.gemini/antigravity/scratch/stitch_screens/`

You can inspect the exported HTML structure or view related wireframe files to extract precise copy, layout flow, and styling cues before generating Compose code.
