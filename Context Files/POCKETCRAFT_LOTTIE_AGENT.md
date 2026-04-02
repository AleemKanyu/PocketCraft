# PocketCraft — Lottie Animations Agent

## Your Role
You are a feature implementation agent. Your job is to add Lottie animations to specific moments in the PocketCraft Android app. Read the existing code carefully before making any changes. Do not ask questions — figure out the structure from the code and implement everything completely.

**Package name:** `com.pocketcraft.server`
**UI toolkit:** Jetpack Compose
**Animation style:** Minecraft themed — creepers, blocks, pickaxes, grass blocks, TNT etc.
**Design:** Match existing pitch black dark theme. Animations should feel native to the app, not out of place.

---

## Dependency Setup

### app/build.gradle
Add Lottie Compose dependency:
```gradle
dependencies {
    implementation "com.airbnb.android:lottie-compose:6.3.0"
}
```

---

## Animation Files Setup

### Where to place files
All Lottie JSON animation files go in:
```
app/src/main/assets/animations/
```
Create this directory if it doesn't exist.

### Animation files needed
Download these free Minecraft-themed Lottie animations from **LottieFiles.com** (lottiefiles.com) — search for these terms and download the JSON file for each:

| File name | Search term on LottieFiles | Used for |
|---|---|---|
| `server_starting.json` | "loading blocks" or "minecraft loading" | Server starting screen |
| `server_success.json` | "success checkmark" or "celebration" | Server online success |
| `server_stopped.json` | "stopped" or "power off" | Server stopped state |
| `empty_plugins.json` | "empty box" or "no data" | No plugins installed |
| `empty_players.json` | "person walking" or "waiting" | No players online |
| `world_importing.json` | "loading" or "downloading" | World import progress |

**Important instruction for agent:** Since you cannot download files from the internet, create placeholder instructions in a file called `ANIMATIONS_NEEDED.md` at the project root listing exactly which files need to be downloaded and where to place them. Then implement all the Lottie composables to reference these file names — the app will show nothing until the JSON files are added, which is acceptable.

Alternatively, if the project already has any Lottie JSON files anywhere in the assets folder, use those instead and adapt the implementation to match whatever is available.

---

## Create LottieAnimations.kt

Create `app/src/main/java/com/pocketcraft/server/ui/LottieAnimations.kt`:

```kotlin
package com.pocketcraft.server.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.airbnb.lottie.compose.*

/**
 * Looping animation — plays continuously until composable leaves composition.
 * Use for: server starting, world importing (ongoing states)
 */
@Composable
fun LoopingLottieAnimation(
    assetName: String,
    modifier: Modifier = Modifier,
    speed: Float = 1f
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("animations/$assetName")
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        speed = speed
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

/**
 * One-shot animation — plays once then stays on last frame.
 * Use for: server success, server stopped (completion states)
 */
@Composable
fun OneShotLottieAnimation(
    assetName: String,
    modifier: Modifier = Modifier,
    speed: Float = 1f,
    onFinished: () -> Unit = {}
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("animations/$assetName")
    )
    val animatable = rememberLottieAnimatable()
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        speed = speed,
        restartOnPlay = false
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

/**
 * Empty state animation — loops gently.
 * Use for: no plugins, no players
 */
@Composable
fun EmptyStateLottieAnimation(
    assetName: String,
    modifier: Modifier = Modifier
) {
    LoopingLottieAnimation(
        assetName = assetName,
        modifier = modifier,
        speed = 0.7f  // slightly slower for idle empty states
    )
}
```

---

## Animation 1 — Server Starting/Loading

### Where to add it
Find the screen or state in the home Activity/composable that shows while the server is starting — between when the user taps "Start" and when `EVENT_TUNNEL_CONNECTED` is received.

### Implementation
Replace any existing loading spinner or progress indicator with:

```kotlin
@Composable
fun ServerStartingScreen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LoopingLottieAnimation(
            assetName = "server_starting.json",
            modifier = Modifier.size(180.dp),
            speed = 1.2f
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Starting server...",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(Modifier.height(8.dp))

        // Show current startup step from server logs
        // Wire this to the existing log output stream
        Text(
            text = startupStatusText, // e.g. "Loading world...", "Preparing chunks..."
            fontSize = 13.sp,
            color = Color(0xFFB3B3B3)
        )

        Spacer(Modifier.height(24.dp))

        // Animated progress steps
        StartupProgressSteps(currentStep = currentStartupStep)
    }
}

@Composable
fun StartupProgressSteps(currentStep: Int) {
    val steps = listOf("Launching JVM", "Loading world", "Starting network", "Connecting relay")
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, label ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = when {
                                index < currentStep -> Color(0xFF57F287)
                                index == currentStep -> Color(0xFFFEE75C)
                                else -> Color(0xFF3A3A3A)
                            },
                            shape = CircleShape
                        )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    fontSize = 9.sp,
                    color = Color(0xFF6B6B6B)
                )
            }
            if (index < steps.size - 1) {
                Divider(
                    modifier = Modifier.width(20.dp),
                    color = if (index < currentStep) Color(0xFF57F287) else Color(0xFF3A3A3A),
                    thickness = 1.dp
                )
            }
        }
    }
}
```

Determine `currentStartupStep` by parsing existing log output:
- Step 0: JVM launched (detect `libjvm.so loaded` in logs)
- Step 1: World loading (detect `Preparing level` in logs)
- Step 2: Network starting (detect `Starting Minecraft server on` in logs)
- Step 3: Relay connecting (detect `onServerReady` or relay registration logs)

---

## Animation 2 — Server Online Success

### Where to add it
When `EVENT_TUNNEL_CONNECTED` is received and the server transitions to online state, briefly show a success animation (plays once, then transitions to the normal online dashboard).

### Implementation
```kotlin
@Composable
fun ServerOnlineSuccessAnimation(
    onAnimationComplete: () -> Unit
) {
    var showAnimation by remember { mutableStateOf(true) }

    if (showAnimation) {
        LaunchedEffect(Unit) {
            delay(2500) // let animation play for 2.5 seconds
            showAnimation = false
            onAnimationComplete()
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OneShotLottieAnimation(
                assetName = "server_success.json",
                modifier = Modifier.size(140.dp),
                speed = 1.0f
            )
            Text(
                text = "Server Online!",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF57F287)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = serverAddress, // e.g. "mine.pocketcraft.online:25501"
                fontSize = 14.sp,
                color = Color(0xFFB3B3B3)
            )
        }
    }
}
```

Wire this so:
1. Server transitions to online → show `ServerOnlineSuccessAnimation`
2. After 2.5 seconds → transition to normal online dashboard automatically

---

## Animation 3 — Server Stopped

### Where to add it
When the server stops (user taps Stop, or `EVENT_STOPPED` received), show a brief stopped animation before returning to the idle dashboard state.

### Implementation
```kotlin
@Composable
fun ServerStoppedAnimation(
    onAnimationComplete: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(2000)
        onAnimationComplete()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OneShotLottieAnimation(
            assetName = "server_stopped.json",
            modifier = Modifier.size(120.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Server Stopped",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFB3B3B3)
        )
    }
}
```

---

## Animation 4 — Empty State: No Plugins

### Where to add it
In the Plugin Manager screen, Tab 1 (Installed Plugins), when `PluginManager.getInstalledPlugins()` returns an empty list.

### Implementation
Replace the existing empty state text with:

```kotlin
@Composable
fun NoPluginsEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EmptyStateLottieAnimation(
            assetName = "empty_plugins.json",
            modifier = Modifier.size(160.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No plugins installed",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Add plugins from the Add Plugin tab\nto enhance your server",
            fontSize = 14.sp,
            color = Color(0xFFB3B3B3),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { /* switch to Add Plugin tab */ },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF57F287))
        ) {
            Text("Add Your First Plugin", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}
```

---

## Animation 5 — Empty State: No Players Online

### Where to add it
On the home screen, in the Players card section, when `playerList.isEmpty()` while server is running.

### Implementation
```kotlin
@Composable
fun NoPlayersEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmptyStateLottieAnimation(
            assetName = "empty_players.json",
            modifier = Modifier.size(120.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No players online",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFB3B3B3)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Share your server address\nto invite friends",
            fontSize = 13.sp,
            color = Color(0xFF6B6B6B),
            textAlign = TextAlign.Center
        )
    }
}
```

---

## Animation 6 — World Importing

### Where to add it
In the world upload flow, replace the existing progress indicator while the zip is being extracted.

### Implementation
```kotlin
@Composable
fun WorldImportingAnimation(
    progress: Int // 0-100
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LoopingLottieAnimation(
            assetName = "world_importing.json",
            modifier = Modifier.size(160.dp),
            speed = 1.5f
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Importing world...",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = progress / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = Color(0xFF57F287),
            trackColor = Color(0xFF2A2A2A)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$progress%",
            fontSize = 13.sp,
            color = Color(0xFFB3B3B3)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Do not close the app",
            fontSize = 12.sp,
            color = Color(0xFF6B6B6B)
        )
    }
}
```

---

## Create ANIMATIONS_NEEDED.md

Create this file at the project root to guide the developer on what to download:

```markdown
# Animations Needed

Download these Lottie JSON files from **lottiefiles.com** and place them in:
`app/src/main/assets/animations/`

| File name | Search term | Notes |
|---|---|---|
| server_starting.json | "minecraft loading" or "blocks loading" | Should loop, Minecraft block theme |
| server_success.json | "success celebration" or "checkmark" | Plays once, bright/celebratory |
| server_stopped.json | "power off" or "stopped" | Plays once, calm/neutral |
| empty_plugins.json | "empty box" or "no items" | Should loop gently |
| empty_players.json | "person waiting" or "no users" | Should loop gently |
| world_importing.json | "downloading" or "loading world" | Should loop |

Tips:
- Filter by "Free" on LottieFiles
- Choose dark-background compatible animations (white/green colors work best)
- Keep file sizes under 100KB each for performance
- Preview the animation before downloading to make sure it loops smoothly
```

---

## Quality Checklist

Before finishing, verify:

- [ ] `lottie-compose:6.3.0` dependency added to app/build.gradle
- [ ] `app/src/main/assets/animations/` directory created
- [ ] `LottieAnimations.kt` exists at correct package path
- [ ] `LoopingLottieAnimation` composable implemented
- [ ] `OneShotLottieAnimation` composable implemented
- [ ] `EmptyStateLottieAnimation` composable implemented
- [ ] `ServerStartingScreen` shows during server boot with progress steps
- [ ] Startup progress steps update based on actual log output parsing
- [ ] `ServerOnlineSuccessAnimation` plays once when tunnel connects then auto-dismisses after 2.5s
- [ ] `ServerStoppedAnimation` plays once when server stops then auto-dismisses after 2s
- [ ] `NoPluginsEmptyState` shows in plugin manager when no plugins installed
- [ ] `NoPluginsEmptyState` has a button that switches to Add Plugin tab
- [ ] `NoPlayersEmptyState` shows in players card when server running but no players
- [ ] `WorldImportingAnimation` shows during world zip extraction with real progress %
- [ ] `ANIMATIONS_NEEDED.md` created at project root with download instructions
- [ ] All animation composables handle missing JSON files gracefully (no crash if file missing)
- [ ] All animations use pitch black / dark background compatible colors
- [ ] App builds without errors even if animation JSON files are not yet present

---

## What This Does NOT Change

- Relay system — unchanged
- Server launching logic — unchanged
- Plugin install logic — unchanged
- World import logic beyond showing the animation — unchanged
- Theme, colors, existing UI — unchanged
- Firebase analytics — unchanged
