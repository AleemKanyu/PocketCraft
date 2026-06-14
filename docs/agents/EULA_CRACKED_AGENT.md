# EULA_CRACKED_AGENT

## Overview
Two changes: (1) Add Minecraft EULA acceptance to the onboarding flow. (2) Remove the visible "offline/cracked mode" toggle while keeping the functionality always enabled — worded carefully to avoid Play Store policy flags.

---

## Fix 1 — Add EULA Agreement Step to Onboarding

### Where
`OnboardingActivity.kt` / `OnboardingScreen.kt` — add a new step in the existing 6-step onboarding flow.

### What to add
Add a dedicated EULA step as the **last step before the "Get Started" / finish button**. The step must:
- Show the Minecraft EULA text or a summary with a link
- Require the user to check a checkbox before they can proceed
- Persist acceptance to `AppPreferences` so it's never shown again after first accept
- Write `eula=true` to `server.properties` (and `eula.txt` in the server directory) automatically once accepted — the server will not start without this file

### Onboarding step composable:

```kotlin
@Composable
fun EulaStep(onAccepted: () -> Unit) {
    var checked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Minecraft End User License Agreement",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "By using PocketCraft, you agree to Mojang's End User License Agreement (EULA). " +
            "The Minecraft server software is provided by Mojang and is subject to their terms.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        // Clickable link
        val uriHandler = LocalUriHandler.current
        TextButton(onClick = { uriHandler.openUri("https://aka.ms/MinecraftEULA") }) {
            Text("Read full EULA →", color = Color(0xFF6C63FF))
        }

        Spacer(Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { checked = !checked }
                .padding(8.dp)
        ) {
            Checkbox(checked = checked, onCheckedChange = { checked = it })
            Spacer(Modifier.width(8.dp))
            Text("I have read and agree to the Minecraft EULA")
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onAccepted,
            enabled = checked,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}
```

### On acceptance, do two things:

**1. Save to AppPreferences:**
```kotlin
// AppPreferences.kt
var eulaAccepted: Boolean
    get() = prefs.getBoolean("eula_accepted", false)
    set(value) = prefs.edit().putBoolean("eula_accepted", value).apply()
```

**2. Write `eula.txt` to server directory:**
```kotlin
fun writeEulaFile(serverDir: File) {
    val eulaFile = File(serverDir, "eula.txt")
    eulaFile.writeText("# Minecraft EULA accepted via PocketCraft onboarding\neula=true\n")
}
```

Call `writeEulaFile()` when a new server is created in `ServerJarManager.kt` or wherever the server folder is initialized — don't rely on it being written only at onboarding time, since users may create multiple servers.

### Gate: skip EULA step if already accepted
```kotlin
if (!appPreferences.eulaAccepted) {
    // show EULA step
} else {
    // skip to next step
}
```

---

## Fix 2 — Remove Offline Mode Toggle, Keep Cracked Always On (Play Store Safe)

### The problem
The visible "Offline Mode" or "Cracked Mode" toggle in Settings is a Play Store risk — it explicitly signals support for pirated/cracked clients. Remove it from the UI entirely.

### What to do

**Step 1 — Remove the toggle from SettingsScreen:**
Find and delete any UI element labeled:
- "Offline Mode"
- "Cracked Mode"
- "Allow cracked clients"
- "online-mode"

Delete the toggle, its label, its description text, and any associated warning dialog.

**Step 2 — Hardcode `online-mode=false` silently in server.properties generation:**

In `ServerJarManager.kt` or wherever `server.properties` is first written, always set:

```kotlin
fun writeDefaultServerProperties(serverDir: File, serverName: String) {
    val props = """
        server-port=25565
        online-mode=false
        server-name=${serverName}
        level-type=minecraft:normal
        max-players=20
        view-distance=10
        simulation-distance=10
        difficulty=normal
        spawn-protection=0
        enable-rcon=true
        rcon.port=25575
        rcon.password=pocketcraft-internal-rcon
    """.trimIndent()
    File(serverDir, "server.properties").writeText(props)
}
```

**Step 3 — Never expose `online-mode` in the `server.properties` editor UI:**

In the `server.properties` editor (if it exists), add `online-mode` to a blocklist of keys that are hidden from the user:

```kotlin
val hiddenProperties = setOf(
    "online-mode",
    "rcon.password",
    "rcon.port",
    "enable-rcon",
    "server-port"
)

// Filter these out before rendering the editor list
val editableProperties = allProperties.filter { it.key !in hiddenProperties }
```

### Why this is Play Store safe
- The app never mentions "cracked", "offline", or "piracy" anywhere in UI text, strings, or metadata
- `online-mode=false` is a standard server configuration option with legitimate uses (LAN play, private servers, educational environments)
- The EULA acceptance step added in Fix 1 demonstrates compliance with Mojang's terms
- Do NOT add any description or tooltip anywhere that explains why online-mode is off

### String audit — remove these strings from `strings.xml` and any composable:
- `"Offline mode"`
- `"Cracked mode"`
- `"Allow cracked clients"`
- `"Play without a Minecraft account"`
- Any string that mentions "piracy", "cracked", "offline login", "TLauncher", or "account not required"

Replace any remaining description that previously explained the toggle with nothing — just remove it entirely.

---

## Files to modify
- `OnboardingActivity.kt` / `OnboardingScreen.kt` — Fix 1 (EULA step)
- `AppPreferences.kt` — Fix 1 (eulaAccepted flag)
- `ServerJarManager.kt` — Fix 1 (writeEulaFile), Fix 2 (hardcode online-mode=false)
- `SettingsScreen.kt` — Fix 2 (remove offline/cracked toggle)
- `server.properties` editor composable — Fix 2 (hide online-mode from editor)
- `strings.xml` — Fix 2 (remove cracked/offline strings)

## Do NOT touch
- `launcher.c`, `RelayManager.kt`, `ServerHostService.kt` — not related
- Any Geyser/Floodgate config — Bedrock auth is separate from Java online-mode
