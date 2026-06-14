# PocketCraft Multi-Page Onboarding/Setup Context

## Project Overview
PocketCraft is an Android app (Minecraft server hosting on mobile) using **Kotlin + Jetpack Compose** for the UI.

**API Level**: Min 26, Target 28, Compile 34
**Language**: Kotlin 1.9+
**UI Framework**: Jetpack Compose (Material3)
**Dependency Injection**: Hilt
**State Management**: Flow, ViewModel, SharedPreferences + DataStore

---

## Current App Architecture

### Entry Point
- **MainActivity.kt**: Sets up the app theme, requests notifications permission, and loads the main Compose content
- Installs Android splash screen
- Uses `PocketCraftTheme` (Material3 dark/light modes)

### Navigation Structure
- **PocketCraftAppScreen.kt**: Main screen router that controls which UI is shown
  - Enum: `Screen { LOADING, VERSION_PICKER, DOWNLOADING, SERVER }`
  - Transitions between screens based on app state
  - Currently always lands on `Screen.SERVER` after init

- **PocketNavigation.kt**: Top/bottom navigation UI components
  - Bottom navigation bar with 5 tabs (CloudSync, Files, Players, Plugins, Settings)
  - Top app bar with Creeper icon + title + relay server selector

### Existing First-Launch Flow
1. App startup → show splash screen during JRE extraction
2. Load saved version preference from DataStore
3. Apply timezone-based relay server selection
4. **ServerLocationDialog.kt**: Dialog for relay server selection (shown conditionally)
5. Jump to main SERVER screen

---

## Key Directories & Files

```
app/src/main/
├── kotlin/com/pocketcraft/server/
│   ├── MainActivity.kt                    (entry point)
│   ├── PocketCraftApp.kt                  (app class with Firebase/Analytics)
│   ├── ui/
│   │   ├── screens/
│   │   │   ├── PocketCraftAppScreen.kt    (main router)
│   │   │   ├── SplashScreen.kt            (loading screen)
│   │   │   ├── ServerScreen.kt            (main home screen)
│   │   │   ├── ServerLocationDialog.kt    (relay server selection)
│   │   │   ├── VersionPickerScreen.kt     (version selection)
│   │   │   ├── AutoDownloadScreen.kt      (download progress)
│   │   │   ├── PlayersScreen.kt
│   │   │   ├── ConsoleScreen.kt
│   │   │   ├── FilesScreen.kt
│   │   │   ├── PluginManagerScreen.kt
│   │   │   ├── BackupsScreen.kt
│   │   │   ├── SettingsScreen.kt
│   │   │   └── ... more screens
│   │   ├── navigation/
│   │   │   └── PocketNavigation.kt        (TopBar + BottomNav)
│   │   ├── components/
│   │   │   ├── GameCard.kt
│   │   │   ├── DuoButton.kt
│   │   │   ├── PlayerCard.kt
│   │   │   └── ... other Composables
│   │   ├── theme/
│   │   │   ├── PocketCraftTheme.kt
│   │   │   ├── Color.kt
│   │   │   └── Type.kt
│   │   └── util/
│   │       └── ThemePreference.kt         (dark/light mode)
│   ├── data/
│   │   ├── preferences/
│   │   │   ├── AppPreferences.kt          (runtime prefs)
│   │   │   └── AppPreferencesStore.kt     (DataStore)
│   │   ├── model/                         (data classes)
│   │   ├── api/                           (Retrofit services)
│   │   ├── db/                            (Room database)
│   │   └── repository/                    (repository pattern)
│   ├── service/
│   │   ├── ServerHostService.kt           (background service)
│   │   ├── ServerFileManager.kt
│   │   └── ... other services
│   ├── setup/
│   │   ├── JreExtractor.kt
│   │   ├── JreDownloader.kt
│   │   └── ... setup utilities
│   ├── Analytics.kt                       (Firebase Analytics)
│   └── ... other core files
└── res/
    ├── drawable/
    │   └── ic_launcher_*.xml              (Creeper icons)
    ├── values/
    │   └── colors.xml                     (theme colors)
    └── ... other resources
```

---

## Current Theme & Colors

**PocketColors.kt** (Material3 theme):
- Primary: Green (Minecraft-inspired)
- Background: Dark/Light based on system preference
- Surface/Card: Elevated backgrounds

**Material3 Components Used**:
- TopAppBar, NavigationBar, NavigationBarItem
- Card, Surface, Button
- TextField, Dialog, LazyColumn
- Icon, Text, Spacer, etc.

---

## State Management Patterns

### Flow-based State
```kotlin
AppPreferencesStore.getSelectedVersionFlow(context).first()
AppPreferencesStore.setSelectedVersion(context, versionId)
AppPreferencesStore.setRelayHostSelected(context, true)
```

### Runtime State (Compose)
```kotlin
var screen by remember { mutableStateOf(Screen.LOADING) }
var selectedHost by remember { mutableStateOf(RelayServers.SINGAPORE.host) }
```

### Shared Preferences (AppPreferences)
```kotlin
val prefs = AppPreferences(context)
prefs.relayHost = bestRelay.host
prefs.userId  // (UUID for relay connection)
```

---

## Example: ServerLocationDialog Pattern

This existing dialog shows the pattern for multi-option selection:

```kotlin
@Composable
fun ServerLocationDialog(
    onLocationSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedHost by remember { mutableStateOf(RelayServers.SINGAPORE.host) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(...)
    ) {
        // Content: LazyColumn with radio buttons
        // Each relay server option clickable
        // Confirm button calls: onLocationSelected(selectedHost)
    }
}
```

---

## Proposed Multi-Page Onboarding System

### Concept
Replace/extend current first-launch flow with a **5-page onboarding wizard**:

1. **Welcome Screen**: App intro, what you can do
2. **Permissions Page**: Request file access, notifications
3. **Server Location Selection**: Where relay server (using existing ServerLocationDialog pattern)
4. **Game Mode Selection**: Vanilla, Paper, etc. (or skip)
5. **Summary/Start**: Confirm settings, start server auto-download

### Implementation Approach

#### Option A: Add to PocketCraftAppScreen.kt
```kotlin
enum class Screen {
    ONBOARDING_1_WELCOME,
    ONBOARDING_2_PERMISSIONS,
    ONBOARDING_3_SERVER_LOCATION,
    ONBOARDING_4_VERSION_SELECTION,
    ONBOARDING_5_SUMMARY,
    LOADING,
    VERSION_PICKER,
    DOWNLOADING,
    SERVER
}

@Composable
fun PocketCraftApp(...) {
    LaunchedEffect(Unit) {
        val hasSeenOnboarding = AppPreferencesStore.getHasSeenOnboarding(context).first()
        screen = if (!hasSeenOnboarding) {
            Screen.ONBOARDING_1_WELCOME
        } else {
            Screen.LOADING
        }
    }
}
```

#### Option B: Separate Onboarding Composable
Create `OnboardingFlow.kt` that handles navigation between 5 pages internally:
```kotlin
@Composable
fun OnboardingFlow(
    onComplete: (OnboardingResult) -> Unit
)
// Contains internal state and page transitions
```

### Data Model
```kotlin
data class OnboardingResult(
    val relayHost: String,          // e.g., "play.pocketcraft.online"
    val versionId: String,          // e.g., "1.20.1"
    val permissionsGranted: Boolean
)

// Store completion state
AppPreferencesStore.setHasSeenOnboarding(context, true)
AppPreferencesStore.setOnboardingVersion(context, 1)  // For future updates
```

### Preferences Storage
Use **AppPreferencesStore.kt** (DataStore-backed):
```kotlin
object AppPreferencesStore {
    suspend fun getHasSeenOnboarding(context: Context): Flow<Boolean>
    suspend fun setHasSeenOnboarding(context: Context, value: Boolean)

    // Add new prefs as needed for onboarding data
}
```

---

## Composable Patterns Used in App

### Page/Screen Pattern
```kotlin
@Composable
fun MyPage(
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        Analytics.logScreenView("my_page")
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Content
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text("Back") }
            Button(onClick = onNext) { Text("Next") }
        }
    }
}
```

### Card-based UI
```kotlin
GameCard(
    modifier = Modifier.fillMaxWidth(),
    // content
)
```

### Dialog Pattern (ServerLocationDialog)
```kotlin
Dialog(
    onDismissRequest = { /* back handler */ },
    properties = DialogProperties(
        dismissOnBackPress = true,
        dismissOnClickOutside = false,
    )
) {
    Card(modifier = Modifier.fillMaxWidth(0.9f)) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Content
        }
    }
}
```

### Back Handler
```kotlin
BackHandler(enabled = true) {
    // Handle back press
}
```

---

## Key Dependencies

From `build.gradle.kts`:
- **Compose**: Latest Material3
- **Hilt**: DI framework
- **DataStore**: Preferences storage
- **Room**: Database (if needed)
- **Retrofit**: HTTP client
- **Firebase**: Analytics, Crashlytics
- **Kotlin Coroutines**: Async/Flow

---

## Important Notes

### 1. Preferences Initialization
Always use `rememberCoroutineScope()` for coroutine operations:
```kotlin
val scope = rememberCoroutineScope()
scope.launch {
    AppPreferencesStore.setHasSeenOnboarding(context, true)
}
```

### 2. Analytics Integration
Log every page view:
```kotlin
LaunchedEffect(Unit) {
    Analytics.logScreenView("onboarding_page_1")
}
```

### 3. Navigation Guard
Don't allow back past first page:
```kotlin
BackHandler(enabled = currentPage > 0) {
    currentPage--
}
```

### 4. Accessibility
- Use proper content descriptions for Icons
- Ensure sufficient color contrast
- Support different text sizes (sp units)

### 5. Screen Orientation
App runs in portrait mode; compose handles rotation automatically.

---

## Design Specifications (from existing UI)

**Colors** (from theme):
- Primary Green: #4A8F50 (Creeper green)
- Background: Dark/Light mode adaptive
- Text: Primary/Secondary variants

**Spacing**:
- Padding: 8.dp, 16.dp, 24.dp (standard)
- Item height: 48.dp minimum for touch targets
- Border radius: 8.dp, 12.dp common

**Typography**:
- Title: titleLarge, titleMedium
- Body: bodyLarge, bodyMedium
- Label: labelLarge

---

## Integration Checklist

- [ ] Decide between Option A (integrated Screen enum) or Option B (separate OnboardingFlow)
- [ ] Create new preference keys in AppPreferencesStore
- [ ] Build 5 onboarding pages
- [ ] Implement state management across pages
- [ ] Add navigation logic to PocketCraftAppScreen.kt
- [ ] Add Analytics logging for each page
- [ ] Test back/next navigation
- [ ] Test permission requests (Android 12+)
- [ ] Add ability to re-show onboarding from Settings
- [ ] Test dark/light theme switching

---

## Questions for Claude/Implementation

1. Should returning users see onboarding again? (Recommended: Add "Reset Onboarding" in Settings)
2. Should version selection be skipped if Paper is already installed?
3. Do we need progress indicator (e.g., "Page 1 of 5")?
4. Should onboarding allow back to Welcome, or prevent navigation backward past confirmation?

---

## Related Existing Code Files to Reference

- `ServerLocationDialog.kt` - Dialog + radio button pattern
- `VersionPickerScreen.kt` - Version selection pattern
- `PocketNavigation.kt` - Theme + icon usage
- `AppPreferences.kt` & `AppPreferencesStore.kt` - Preference patterns
- `PocketCraftAppScreen.kt` - Screen routing pattern
