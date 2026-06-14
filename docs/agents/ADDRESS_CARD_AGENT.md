# PocketCraft — Server Address Card Fix Agent

## Overview
After the server finishes booting (spawn chunks loaded, world done loading), the server address card on the Home screen intermittently fails to appear. The card must **always** show reliably once the server is fully ready.

---

## Root Cause Analysis

The address card visibility is almost certainly driven by a state flag (e.g. `isServerRunning`, `serverReady`, `relayConnected`) that gets set somewhere in the server boot flow. The failure modes are:

1. **Log parsing race** — The UI watches for a specific log line (e.g. `"Done ("` or `"For help, type"`) to trigger the ready state, but the log line arrives before the observer is registered, so it's missed entirely.
2. **StateFlow/LiveData not re-emitting** — The flag is set correctly but the UI collector misses the emission because it was collecting from a cold flow or the lifecycle was in the background during boot.
3. **Relay connection timing** — The address card waits for BOTH `serverReady` AND `relayConnected`, but the relay port assignment arrives slightly after the ready signal, and if the UI checks both simultaneously it can silently fail to show.
4. **Recomposition not triggered** — The state is updated on a background thread without `withContext(Dispatchers.Main)`, so Compose never recomposes.

---

## Fix Instructions

### Step 1 — Identify the ready signal in ServerHostService

Find where the server "done booting" state is set. It will look something like this in `ServerHostService.kt` or `ServerConsole.kt`:

```kotlin
// Likely existing code — find it
if (line.contains("Done (") || line.contains("For help, type")) {
    // something sets server ready here
}
```

Make sure this detection is **exhaustive** — Paper 1.21+ logs the done line as:
```
[XX:XX:XX INFO]: Done (X.XXXs)! For help, type "help"
```

Use a robust check:
```kotlin
val isServerReady = line.contains("Done (") && 
                    (line.contains("For help") || line.contains("type \"help\""))
```

### Step 2 — Use a reliable StateFlow for server ready state

In `ServerHostService.kt`, use a `MutableStateFlow` exposed as immutable to the ViewModel:

```kotlin
companion object {
    private val _serverState = MutableStateFlow(ServerState.IDLE)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()
    
    var isRunning: Boolean = false
        private set
}

enum class ServerState {
    IDLE, BOOTING, READY, STOPPED, ERROR
}
```

When the done log line is detected:
```kotlin
// On the log reading coroutine — switch to Main before emitting
withContext(Dispatchers.Main) {
    _serverState.value = ServerState.READY
}
```

### Step 3 — Fix the ViewModel to never miss the emission

In your Home screen ViewModel, collect `serverState` using `stateIn` so it's always hot and never misses emissions:

```kotlin
val serverState: StateFlow<ServerState> = ServerHostService.serverState
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ServerState.IDLE
    )

// Derive address card visibility from state — never from a separate flag
val showAddressCard: StateFlow<Boolean> = combine(
    serverState,
    relayPort  // your relay port StateFlow
) { state, port ->
    state == ServerState.READY && port != null && port > 0
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = false
)
```

### Step 4 — Fix the Compose UI collection

In your Home screen composable, collect with `collectAsStateWithLifecycle` — NOT `collectAsState()`. The latter can miss emissions when the app is backgrounded during boot:

```kotlin
// Add dependency if not present:
// implementation "androidx.lifecycle:lifecycle-runtime-compose:2.7.0"

val showAddressCard by viewModel.showAddressCard.collectAsStateWithLifecycle()
val serverState by viewModel.serverState.collectAsStateWithLifecycle()
```

Then make the address card always render when ready:

```kotlin
// Address card — always visible when server is READY
AnimatedVisibility(
    visible = showAddressCard,
    enter = fadeIn() + slideInVertically(),
    exit = fadeOut()
) {
    ServerAddressCard(
        address = relayAddress,
        port = relayPort
    )
}
```

### Step 5 — Add a fallback poll for missed ready signal

As a safety net, if the server process is alive but `ServerState` is still `BOOTING` for more than 90 seconds, force-check the process and promote to `READY`:

```kotlin
// In ServerHostService, after launching the server process
viewModelScope.launch {
    delay(90_000)
    if (_serverState.value == ServerState.BOOTING && serverProcess?.isAlive == true) {
        // Server is alive but we missed the done line — force ready
        _serverState.value = ServerState.READY
        Log.w("PocketCraft", "Server ready signal missed — promoted via fallback")
    }
}
```

### Step 6 — Persist ready state across recompositions

If the user navigates away from Home and back while the server is running, the address card must still show. Ensure `serverState` is in the ViewModel (not the composable) and survives navigation:

```kotlin
// In NavHost — use the SAME ViewModel instance across Home re-entry
val homeViewModel: HomeViewModel = hiltViewModel() // or viewModel() with correct scope
```

If not using Hilt, scope the ViewModel to the NavBackStackEntry of the home destination, not the screen composable directly.

---

## Exact Log Lines to Watch For (Paper 1.21.x)

```
Done (X.XXXs)! For help, type "help"         ← primary signal
Timings Reset                                  ← secondary confirm (Paper only)
[Pufferfish] Loaded configuration              ← early signal (if using Pufferfish fork)
```

Trigger `ServerState.READY` on the **first** of these that appears.

---

## Files Likely to Modify
- `ServerHostService.kt` — add `ServerState` enum + `StateFlow`, fix ready detection, ensure Main thread emission
- `ServerConsole.kt` — if log parsing lives here, apply the robust done-line check
- `HomeViewModel.kt` — replace boolean flag with derived `showAddressCard` StateFlow using `combine`
- `HomeScreen.kt` — switch to `collectAsStateWithLifecycle`, wrap card in `AnimatedVisibility`

---

## Testing Checklist
- [ ] Cold boot → address card appears every time after `Done (` log line
- [ ] Boot with slow relay connection → card still appears once relay port is assigned
- [ ] Navigate away from Home during boot, navigate back → card shows correctly
- [ ] Boot → background app → return → card still visible
- [ ] Stop server → card disappears
- [ ] Restart server → card reappears reliably
- [ ] Delete account guard still works (from previous BUGFIX_AGENT)
