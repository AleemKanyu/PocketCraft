## 7. Remove "Player Moving Too Fast" Warning

### Problem
The app shows a user-facing toast/snackbar/warning card when the server logs
"moved too quickly". This is internal server rubber-banding, not actionable
by the user. Remove it entirely.

### Fix in ConsoleParser.kt
Delete any condition that matches log lines containing:
- "moved too quickly"
- "Mismatch in destroy block"
- "Can't keep up"

Do NOT surface these as UI warnings anywhere.

### Fix in ServerStateHolder.kt / any ViewModel
Remove any state fields, flows, or LiveData related to:
- `playerMovingTooFast`
- `serverOverloaded`
- `blockMismatch`
- Any warning triggered by the above log strings

### Fix in UI layer
Remove any Composable, Snackbar, Toast, or warning card that
displays messages about player speed, server lag, or block mismatches.
Search for the string "Player Moving Too Fast" and delete the entire
UI block that renders it.

### Do NOT remove
- Genuine user-actionable warnings (whitelist, storage full, etc.)

## 8. Fix Maintenance Warning Showing When Disabled

### Problem
The app shows a maintenance/break warning even when the Firestore value
is `false` or missing. Likely a default-to-true bug or a null-safety issue.

### Fix in BroadcastManager.kt (note: has existing typo "Mantainance", do NOT fix the typo
as it may be used as a Firestore field key -- just fix the logic)

Find the maintenance check, it likely looks something like:
```kotlin
if (maintenanceData?.get("enabled") != false) {
    showMaintenanceBanner()
}
```

This is wrong -- `!= false` is true when the value is `null` or missing,
causing the banner to show by default.

### Correct logic -- only show if EXPLICITLY true:
```kotlin
val isEnabled = maintenanceData?.get("enabled") as? Boolean == true
if (isEnabled) {
    showMaintenanceBanner()
}
```

### Also check
- If the Firestore listener has a failure/null fallback that defaults to
  showing the warning -- change all fallbacks to `false` (hidden)
- If there's a local cached value from a previous session that persists
  the warning -- clear it on app start before the Firestore fetch completes
- Do NOT show the maintenance banner during the Firestore fetch loading
  state -- default to hidden until explicitly confirmed true from Firestore

### Files to check
- `BroadcastManager.kt`
- Any ViewModel or StateHolder that holds `isMaintenanceActive` state
- The Composable that renders the maintenance banner -- confirm it checks
  `isMaintenanceActive == true` not `isMaintenanceActive != false`

## 9. Fix Server Starting UI State

### Problem
When the user clicks "Start Server", the UI immediately jumps to showing
a new box with "Rendering spawn chunks" + Stop/Restart buttons instead of:
1. Keeping the Start Server button visible but changed to "Starting..."
2. Showing a progress bar/indicator beneath it

### Desired behavior flow:
```
[User clicks Start Server]
        v
Button text changes to "Starting Server..." (disabled, non-clickable)
LinearProgressIndicator appears below the button (infinite/indeterminate)
        v
Server logs "Done (...) For help, type "help""  <- isServerFullyReady = true
        v
Address card animates in (fade + slide up)
Stop / Restart buttons appear
Progress bar and "Starting Server..." button disappear
```

### ServerStateHolder.kt
Ensure there are exactly 3 distinct states exposed to UI:
```kotlin
enum class ServerUiState {
    IDLE,       // not running, show Start Server button
    STARTING,   // process launched, waiting for "Done" log line
    RUNNING     // fully ready, show address card + Stop/Restart
}
```
- Set `STARTING` immediately when start is clicked
- Set `RUNNING` only on `ServerFullyReady` event (from fix #4 above)
- Set `IDLE` on stop/crash

### HomeScreen Composable
Replace any boolean `isServerRunning` checks with `when (serverUiState)`:

```kotlin
when (serverUiState) {
    IDLE -> {
        // Show normal Start Server button
        Button(onClick = onStartServer) { Text("Start Server") }
    }
    STARTING -> {
        // Mutate the same button -- do NOT show a new box
        Button(onClick = {}, enabled = false) {
            Text("Starting Server...")
        }
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            color = Color(0xFF6C63FF)
        )
    }
    RUNNING -> {
        // Address card + Stop + Restart
        AddressCard(...)
        Row {
            StopButton(...)
            RestartButton(...)
        }
    }
}
```

### Remove
- The intermediate "Rendering spawn chunks" box entirely -- it is replaced
  by the progress bar under the Starting... button
- Any state that shows Stop/Restart before `isServerFullyReady = true`

### Do NOT change
- The actual stop/restart button logic
- Address card content or copy

## 10. Fix Premature RUNNING State Transition (Critical)

### Problem
The app transitions to RUNNING state (showing Stop + Restart buttons and
the spawn chunks spinner in a separate box) as soon as the server PROCESS
starts, not when the server is actually ready.

The current flow is wrong:
```
Start clicked → process launches → immediately shows Stop/Restart + spinner box
```

The correct flow (from fix #9) must be enforced:
```
Start clicked → STARTING (button says "Starting Server..." + progress bar only)
→ "Done (...) For help" detected → RUNNING (Stop/Restart + address card)
```

### Root cause to find and fix
In `ServerStateHolder.kt` or `ServerHostService.kt`, there is likely a state
update triggered by the process launching successfully or the service binding,
something like:

```kotlin
// WRONG — triggers too early
_serverState.value = ServerState.RUNNING
// or
isServerRunning = true
```

This must be removed. The ONLY place that sets state to RUNNING/ready must be
inside the `ServerFullyReady` event handler in `ConsoleParser.kt` which fires
on the "Done (...) For help, type" log line.

### Specifically
- The separate box showing "Starting server... preparing spawn chunks" with
  the spinner must be DELETED from the UI entirely
- Stop and Restart buttons must NOT appear until `ServerUiState == RUNNING`
- During STARTING state: only the disabled "Starting Server..." button +
  LinearProgressIndicator should be visible. Nothing else.
- The circular spinner currently shown in the separate box should be removed;
  the LinearProgressIndicator under the button replaces it entirely

### Search and remove
Any composable block that renders a Card/Box containing:
- "Starting server" text AND a CircularProgressIndicator together as a
  separate UI element below the main button area
