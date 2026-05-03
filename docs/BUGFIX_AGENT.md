# PocketCraft Bug Fix Agent

## Bug 1: Onboarding Version Download Fails on First Try

**Root cause:** JAR download coroutine is launched in a `viewModelScope` tied to the onboarding screen. When onboarding navigates to Home, the screen dies and cancels the coroutine mid-download. The user must re-select the version to re-trigger it.

**Fix in `OnboardingViewModel.kt` / completion handler:**

```kotlin
// In PocketCraftApplication.kt — add if not present
val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

// In onboarding completion — replace viewModelScope with applicationScope
applicationScope.launch {
    // 1. Await pref write before download — never fire-and-forget
    appPreferences.setServerVersion(selectedVersion)  // await if suspend
    
    // 2. Pass version directly — don't re-read from prefs
    _uiState.update { it.copy(isDownloadingJar = true) }
    val success = jarDownloader.downloadJar(selectedVersion)
    _uiState.update { it.copy(isDownloadingJar = false) }
    
    // 3. Only navigate after download completes
    withContext(Dispatchers.Main) {
        if (success) {
            navController.navigate("home") { popUpTo("onboarding") { inclusive = true } }
        } else {
            _uiState.update { it.copy(downloadError = "Failed to download server JAR. Try again.") }
        }
    }
}
```

Also add a null guard in the JAR resolver before making any API call:

```kotlin
val version = appPreferences.getServerVersion()
if (version.isNullOrBlank()) {
    Log.e("PocketCraft", "No server version set — aborting JAR resolve")
    _serverState.value = ServerState.ERROR
    // show UI: "No version selected. Go to Settings and select a server version."
    return
}
```

Apply the same `applicationScope` fix to `WorldCreationViewModel` if it also triggers a JAR download.

---

## Bug 2: Remove Delete Account Option from Settings

The delete account UI must be fully removed — there is no auth account system in the app currently so this option serves no purpose and caused server state corruption.

- Remove the delete account button/item from `SettingsScreen.kt`
- Remove `onDeleteAccountConfirmed()` and `proceedWithAccountDeletion()` from `SettingsViewModel.kt`
- Do NOT delete any `AppPreferences` keys — leave them in place for future use

---

## Files to Modify
- `PocketCraftApplication.kt` — add `applicationScope`
- `OnboardingViewModel.kt` — switch to `applicationScope`, await pref write, navigate after download
- `WorldCreationViewModel.kt` — same `applicationScope` fix if JAR download triggered here
- `AppPreferences.kt` — confirm `setServerVersion` is a suspend fun and is awaited
- JAR resolver — add null/blank version guard before API call
- `SettingsScreen.kt` — remove delete account button
- `SettingsViewModel.kt` — remove delete account functions

## Testing
- [ ] Complete onboarding → JAR downloads on first try, no re-selection needed
- [ ] New world creation with version → JAR downloads on first try  
- [ ] Boot after fresh onboarding → no "Failed to resolve JAR" error
- [ ] Settings screen → no delete account option visible
