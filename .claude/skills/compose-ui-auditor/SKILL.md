---
name: compose-ui-auditor
description: "Inspects, audits, and refactors Jetpack Compose UI in PocketCraft for tactile 3D consistency, 48dp touch targets, dynamic color contrast, and recomposition hygiene."
---

# /compose-ui-auditor - Jetpack Compose UI Quality & Design Auditor

This skill provides a rigorous auditing protocol for Jetpack Compose UI in the PocketCraft codebase (`app/src/main/kotlin/com/pockethost/app/ui/`).

---

## 1. PocketCraft UI Quality Checklist

When auditing or reviewing any Compose screen or component, verify against these 6 pillars:

### Pillar 1: Tactile 3D Consistency
- [ ] Are all primary and secondary action buttons using `DuoButton`?
- [ ] Are buttons using the semantically appropriate `DuoButtonVariant` (`StartServer`, `Primary`, `Secondary`, `Danger`, `Warning`, `Info`, `Discord`, `Pro`)?
- [ ] If a custom card or surface has a 3D shadow, is it using `Modifier.bottomShadow(...)` with a matching `cornerRadius`?
  ```kotlin
  // Good: cornerRadius in clip matches cornerRadius in bottomShadow
  Box(
      modifier = Modifier
          .clip(RoundedCornerShape(16.dp))
          .bottomShadow(shadowHeight = 4.dp, cornerRadius = 16.dp)
  )
  ```
- [ ] Are flat standard Material buttons (`Button`, `ElevatedButton`, `OutlinedButton`) avoided in favor of the app's tactile game aesthetic?

### Pillar 2: Touch Targets & Accessibility
- [ ] Do all interactive elements meet the **minimum 48x48 dp** touch target?
  - For icon buttons, use `Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)`.
  - Check that small chips, switches, or close buttons have sufficient clickable padding.
- [ ] Do all `Icon` composables have descriptive `contentDescription` or `null` if purely decorative?

### Pillar 3: Theme Adaptability & Contrast
- [ ] Does the UI render legibly in both Dark and Light themes?
  - Test with `pocketIsDarkTheme()`:
  ```kotlin
  val isDark = pocketIsDarkTheme()
  val contentColor = if (isDark) Color.White else Color(0xFF1E293B)
  ```
- [ ] Do status badges and indicators have high-contrast text against their background?
- [ ] Are system bar icons (status bar and navigation bar) dynamically tinted using `systemBarsColor` to match the background?

### Pillar 4: Android 15 Edge-to-Edge Compliance
- [ ] Does the screen handle `WindowInsets` cleanly without content being obscured by the status bar or gesture bar?
- [ ] Does `Scaffold` or root container use `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` or `systemBars`?
- [ ] Do scrollable lists have bottom content padding (`contentPadding = PaddingValues(bottom = 80.dp)`) so the last item is not clipped behind floating action buttons or navigation bars?

### Pillar 5: Recomposition & Performance Hygiene
- [ ] In `LazyColumn` and `LazyRow`, are `items` configured with a unique, stable `key`?
  ```kotlin
  items(items = players, key = { it.uuid }) { player ->
      PlayerCard(player = player)
  }
  ```
- [ ] Are expensive calculations or formatters wrapped in `remember(key)`?
- [ ] Is `derivedStateOf` used when deriving state from rapidly changing values (like scroll position)?
- [ ] Are callbacks passed as stable lambdas without unnecessary object allocations during recomposition?

### Pillar 6: State Hoisting & God Node Separation
- [ ] Does the composable receive state as plain parameters and emit events via lambdas (`onClick`, `onToggle`) rather than directly modifying `ServerStateHolder` internals?
- [ ] Is screen-level business logic isolated in ViewModel or StateHolder, keeping composables pure and preview-friendly?

---

## 2. Fast Verification Commands

When testing UI changes on a connected device or emulator:

```bash
# Dump the active UI layout tree in JSON to verify hierarchy and dimensions
android layout --pretty

# Capture screen to inspect visual fidelity
android screen capture /tmp/screen_audit.png

# Recompile and launch app
./gradlew assembleDebug && android run --apks=app/build/outputs/apk/debug/app-debug.apk
```

---

## 3. Common Fixes Reference

### Fixing Clipped Bottom Shadows
If a `bottomShadow` gets clipped by its parent:
```kotlin
// Ensure parent does not clip children before the shadow renders
Column(modifier = Modifier.padding(bottom = 4.dp)) {
    Box(modifier = Modifier.bottomShadow(shadowHeight = 4.dp, cornerRadius = 16.dp)) { ... }
}
```

### Perfect Dark/Light Card Borders
```kotlin
val borderColor = if (pocketIsDarkTheme()) {
    Color.White.copy(alpha = 0.08f)
} else {
    Color.Black.copy(alpha = 0.06f)
}
Modifier.border(1.dp, borderColor, RoundedCornerShape(16.dp))
```
