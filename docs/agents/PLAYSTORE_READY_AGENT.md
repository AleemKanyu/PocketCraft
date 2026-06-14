# PLAYSTORE_READY_AGENT

## Objective

## 🔴 CRITICAL — REMOVE ALL ADS

Completely remove all advertisement-related functionality for now.

- Remove Unity Ads SDK and all ad dependencies
- Remove all ad logic (interstitial, rewarded, banners, etc.)
- Remove any code in Analytics.kt or similar files related to ads
- Remove UnityAds.initialize() and all ad triggers
- Do not leave commented or disabled ad code — delete everything

App must build and run with zero ad-related code present.

Ads will be reimplemented later in a separate phase.

Fix all technical, compliance, and UX issues that would block or flag PocketCraft during Google Play internal testing submission. This MD covers everything the agent must change before the AAB is uploaded to Play Console. Do not skip any task — each one is either a hard Play Store requirement or a known rejection/flag trigger.

---

## Context

- **Package:** `com.pocketcraft.server`
- **App type:** Android foreground service app (server host)
- **Current state:** APK distributed via Instagram DMs, no Play Store presence yet
- **Target:** Internal testing track upload (not production)
- **Key files:** `AndroidManifest.xml`, `build.gradle` (app), `ServerHostService.kt`, `AppPreferences.kt`, `SettingsScreen.kt`, any account/settings screen

---

## Task 1 — Target SDK & Compile SDK

### Requirement

New apps and app updates must target Android 15 (API level 35) or higher to be submitted to Google Play. This is mandatory — Play Console will reject the upload if `targetSdk` is below 35.

### `build.gradle` (app level)

```groovy
android {
    compileSdk 35
    defaultConfig {
        targetSdk 35
        minSdk 26                    // keep existing minSdk — do NOT lower it
    }
}
```

### Also update Gradle tooling

```groovy
// gradle/wrapper/gradle-wrapper.properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
```

```groovy
// build.gradle (project level)
classpath 'com.android.tools.build:gradle:8.5.0'
```

### Behavior changes to handle after bumping to 35

Foreground services now face a six-hour limit for `dataSync` operations within a 24-hour period. After reaching this limit, the system calls `onTimeout()`, requiring services to stop within seconds or face termination.

In `ServerHostService.kt`, implement `onTimeout()` to handle this gracefully:

```kotlin
override fun onTimeout(startId: Int) {
    // Server has been running for 6h on dataSync type
    // Notify user and attempt graceful stop
    showTimeoutNotification()
    sendCommand("save-all")
    Handler(Looper.getMainLooper()).postDelayed({
        stopSelf()
    }, 5000)
}
```

Add a notification channel for the timeout notification if not already present.

---

## Task 2 — Foreground Service Type Declarations

### Requirement

Apps that target API level 34 or higher must declare all foreground services with their service types. Missing `foregroundServiceType` causes a `SecurityException` crash on Android 14+ devices — an instant rejection.

### `AndroidManifest.xml`

Find the `<service>` declaration for `ServerHostService` and ensure it has the correct types:

```xml
<service
    android:name=".service.ServerHostService"
    android:foregroundServiceType="dataSync|specialUse"
    android:stopWithTask="true"
    android:exported="false" />
```

**Why `dataSync|specialUse`:**
- `dataSync` — covers the ongoing server I/O (world saves, chunk writes, player data)
- `specialUse` — required because running a Java server process is not a standard Android foreground service use case; Play Console requires a justification for `specialUse` (see Task 3)

### Also add required permissions in `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

Remove any permissions that are declared but not actually used — Play Console flags unused dangerous permissions.

---

## Task 3 — POST_NOTIFICATIONS Runtime Permission Request

### Requirement

On Android 13+ (API 33+), `POST_NOTIFICATIONS` is a runtime permission — it must be explicitly requested from the user. Without it, the foreground service notification is silently suppressed, which breaks the server running indicator.

### In `ServerHostService.kt` or the screen that starts the server

Before starting the service, check and request notification permission:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    if (ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATION_PERMISSION
        )
        return // don't start server until permission is handled
    }
}
```

Show a rationale dialog before requesting if the user has previously denied:

```kotlin
AlertDialog(
    title = { Text("Notification Permission Required") },
    text = { Text("PocketCraft needs notification permission to show the server status while it's running in the background.") },
    confirmButton = { TextButton(onClick = { requestPermission() }) { Text("Allow") } },
    dismissButton = { TextButton(onClick = { /* proceed anyway, service will be backgrounded */ }) { Text("Skip") } }
)
```

---

## Task 4 — Account Deletion Flow

### Requirement

Google Play requires any app with account creation to also provide an in-app account deletion option. PocketCraft uses Google Sign-In — this qualifies as account creation and mandates a deletion flow.

### What to implement

Add an "Delete Account" option in `SettingsScreen.kt` under the Account section:

```kotlin
SettingsDestructiveCard(
    title = "Delete Account",
    subtitle = "Permanently delete your PocketCraft account and all associated data.",
    icon = Icons.Default.DeleteForever,
    iconTint = Color(0xFFFF4757),
    onClick = { showDeleteAccountDialog = true }
)
```

Confirmation dialog:

```kotlin
if (showDeleteAccountDialog) {
    AlertDialog(
        containerColor = Color(0xFF13131A),
        title = { Text("Delete Account?", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "This will permanently delete your account, server data, and all backups. This cannot be undone.",
                color = Color.White.copy(alpha = 0.7f)
            )
        },
        confirmButton = {
            TextButton(onClick = { performAccountDeletion() }) {
                Text("Delete", color = Color(0xFFFF4757))
            }
        },
        dismissButton = {
            TextButton(onClick = { showDeleteAccountDialog = false }) {
                Text("Cancel", color = Color(0xFF6C63FF))
            }
        }
    )
}
```

`performAccountDeletion()` must:
1. Call Firebase Auth `currentUser?.delete()`
2. Delete Firestore documents for this user
3. Revoke Google Sign-In token
4. Clear all `SharedPreferences`
5. Delete local server files if user consents
6. Sign out and navigate to onboarding screen

---

## Task 5 — Privacy Policy Compliance

### Requirement

Google Play requires a valid privacy policy URL for any app that collects user data. PocketCraft collects Google account info, Firebase Analytics data, and Crashlytics data — all of which require disclosure.

### What to do

**In `AndroidManifest.xml`**, add the privacy policy metadata:

```xml
<meta-data
    android:name="android.webkit.WebView.MetaData"
    android:value="true" />
```

**In the app**, add a "Privacy Policy" link in `SettingsScreen.kt`:

```kotlin
SettingsLinkCard(
    title = "Privacy Policy",
    subtitle = "How we collect and use your data",
    icon = Icons.Default.PrivacyTip,
    iconTint = Color(0xFF6C63FF),
    url = "https://pocketcraft.online/privacy"
)
```

**In onboarding**, show a consent checkbox before Google Sign-In:

```kotlin
Row(verticalAlignment = Alignment.CenterVertically) {
    Checkbox(
        checked = privacyAccepted,
        onCheckedChange = { privacyAccepted = it },
        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF6C63FF))
    )
    Text(
        buildAnnotatedString {
            append("I agree to the ")
            withStyle(SpanStyle(color = Color(0xFF6C63FF))) {
                append("Privacy Policy")
            }
            append(" and ")
            withStyle(SpanStyle(color = Color(0xFF6C63FF))) {
                append("Terms of Service")
            }
        },
        fontSize = 13.sp,
        color = Color.White.copy(alpha = 0.8f),
        modifier = Modifier.clickable { openUrl("https://pocketcraft.online/privacy") }
    )
}
```

Disable the "Continue" / Sign-In button until `privacyAccepted == true`.

---

## Task 6 — App Bundle (AAB) Build Configuration

### Requirement

Play Store only accepts AAB format — not APK. The current distribution is APK via direct link.

### `build.gradle` (app level)

Ensure the release build is configured for AAB:

```groovy
android {
    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }     // critical for native .so files in launcher.c
    }

    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            signingConfig signingConfigs.release
        }
    }
}
```

### ProGuard rules (`proguard-rules.pro`)

Add rules to prevent stripping of JNI-called classes and Paper-related reflection:

```proguard
# Keep JNI bridge
-keep class com.pocketcraft.server.jni.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }

# Keep server-related reflection
-keep class org.bukkit.** { *; }
-keep class io.papermc.** { *; }

# Keep Gson if used
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
```

---

## Task 7 — 16KB Page Alignment for Native Libraries

### Requirement

API 35 introduces strict service rules and mandatory 16 KB memory page support. The JRE `.so` files loaded via `dlopen()` in `launcher.c` must be 16KB page-aligned or the app will crash on newer Android devices (Pixel 8+, Samsung S24+).

### `build.gradle` (app level)

```groovy
android {
    defaultConfig {
        ndk {
            abiFilters "arm64-v8a"    // primary target
        }
    }
    
    packagingOptions {
        jniLibs {
            useLegacyPackaging = false  // required for 16KB alignment
        }
    }
}
```

### In `CMakeLists.txt` (if present)

```cmake
target_link_options(pocketcraft_launcher PRIVATE
    "-Wl,-z,max-page-size=16384"
)
```

### Verify alignment

After building, run:

```bash
python3 check_elf_alignment.py app/build/outputs/bundle/release/app-release.aab
```

If any `.so` file is flagged as misaligned, it needs to be recompiled with the 16KB flag. The bundled JRE `.so` files from PojavLauncher should already be 16KB-aligned — verify this.

---

## Task 8 — Notification Channel Setup

### Requirement

Every notification must belong to a named channel with proper importance level. Play Store review will test this on Android 8+ devices.

### In `ServerHostService.kt` or `Application` class

Ensure all channels are created on app start:

```kotlin
fun createNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // Server running notification — persistent, low importance (no sound)
        NotificationChannel(
            "server_running",
            "Server Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while your Minecraft server is running"
            setShowBadge(false)
        }.also { channel ->
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        // Alerts — high importance (sound, banner)
        NotificationChannel(
            "server_alerts",
            "Server Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Important alerts like crashes or player joins"
        }.also { channel ->
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        // Timeout warning channel
        NotificationChannel(
            "server_timeout",
            "Server Timeout Warning",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifies when the server is approaching the background time limit"
        }.also { channel ->
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
```

---

## Task 9 — Remove Debug/Cracked Mode References

### Requirement

Play Store policy prohibits apps that enable piracy or cracked software. PocketCraft runs Paper in offline mode (no Mojang auth) — this must not be described as "cracked" or "piracy" anywhere in the codebase, UI strings, or logs.

### What to audit and fix

Search the entire codebase for these strings and replace/remove:

| Find | Replace with |
|------|-------------|
| `"cracked"` | `"offline mode"` |
| `"piracy"` | remove entirely |
| `"no auth"` in UI strings | `"offline"` |
| `online-mode=false` comments mentioning piracy | neutral comment only |

In `server.properties` template:

```properties
# Offline mode — players connect without Mojang authentication
online-mode=false
```

No comment should explain this as bypassing auth for piracy — frame it as LAN/offline play.

---

## Task 10 — Ads Compliance (Unity Ads)

### Requirement

Unity Ads interstitials must not show:
- During gameplay or active server sessions
- Without user interaction trigger
- More than once per natural break point

### Audit `Analytics.kt` or wherever Unity Ads is initialized

Ensure:

```kotlin
// Only show interstitial AFTER server start completes, not during
// Add minimum interval guard — no more than 1 interstitial per 3 minutes
private var lastAdShownAt = 0L
private val AD_COOLDOWN_MS = 3 * 60 * 1000L

fun showInterstitialIfReady() {
    val now = System.currentTimeMillis()
    if (now - lastAdShownAt < AD_COOLDOWN_MS) return
    // show ad
    lastAdShownAt = now
}
```

Also ensure the GDPR/consent flow is shown before any ad is displayed for EU users — Unity Ads SDK handles this if configured correctly. Verify `UnityAds.initialize()` is called with `testMode = false` in release builds.

---

## Task 11 — Data Safety Form Preparation

This is not a code task — it is a checklist of what to declare in Play Console's Data Safety form. The agent should add a `DATA_SAFETY.md` file to the project root with this content so Aleem can fill in the form accurately:

```markdown
# PocketCraft Data Safety Declaration

## Data Collected

| Data Type | Collected | Shared | Purpose | Required |
|-----------|-----------|--------|---------|----------|
| Google Account name | Yes | No | User identification, Drive backup | Yes |
| Google Account email | Yes | No | User identification | Yes |
| Firebase Analytics events | Yes | No (aggregated only) | Crash reporting, usage analytics | No |
| Crashlytics crash logs | Yes | No | Bug fixing | No |
| Server world files | Yes (Drive backup) | No | User backup | No |
| IP address | Yes (relay connection) | No | Server hosting relay | Yes |
| Device info | Yes (Crashlytics) | No | Crash context | No |

## Encryption
- All data in transit: Yes (TLS)
- All data at rest: Yes (Firebase encryption)

## Deletion
- User can delete all data via in-app account deletion (Settings → Delete Account)
- Firebase data deleted within 30 days of account deletion

## Children
- App is not directed at children under 13
- COPPA: Not applicable
```

---

## Task 12 — App Version & Build Config

### `build.gradle` (app level)

```groovy
defaultConfig {
    applicationId "com.pocketcraft.server"
    versionCode 2          // increment from current
    versionName "1.0.0-beta"
    
    // Required for Play Console
    multiDexEnabled true
}
```

Ensure `versionCode` is higher than any previously distributed APK. If the Instagram APK was `versionCode 1`, set this to `2`.

---

## Files Modified Summary

| File | Change |
|------|--------|
| `build.gradle` (app) | targetSdk 35, compileSdk 35, bundle config, ProGuard, 16KB alignment |
| `gradle-wrapper.properties` | Gradle 8.9 |
| `AndroidManifest.xml` | foregroundServiceType declarations, permissions cleanup |
| `ServerHostService.kt` | `onTimeout()` handler, notification channels, POST_NOTIFICATIONS check |
| `SettingsScreen.kt` | Account deletion card, Privacy Policy link |
| `OnboardingScreen.kt` | Privacy Policy consent checkbox before sign-in |
| `proguard-rules.pro` | JNI, Firebase, Paper reflection keep rules |
| `CMakeLists.txt` | 16KB page alignment linker flag |
| `DATA_SAFETY.md` (new) | Data safety form reference doc |
| All `.kt` files | Replace "cracked"/"piracy" strings with "offline mode" |
| Ads logic file | Cooldown guard, testMode=false in release |

---

## Do NOT change

- Existing relay logic (`RelayManager.kt`, `NetworkMonitor.kt`)
- Server config files (`server.properties`, `paper-world-defaults.yml`)
- JVM launch flags in `launcher.c` (except 16KB alignment in CMakeLists.txt)
- Chunky / chunk loading logic from previous agent MDs
- Firebase Analytics event names — changing these breaks historical data

---

## Acceptance Criteria

- [ ] `targetSdk = 35` and `compileSdk = 35` in `build.gradle`
- [ ] Gradle wrapper updated to 8.9
- [ ] `ServerHostService` manifest entry has `foregroundServiceType="dataSync|specialUse"`
- [ ] `FOREGROUND_SERVICE_DATA_SYNC` and `FOREGROUND_SERVICE_SPECIAL_USE` permissions declared
- [ ] `POST_NOTIFICATIONS` runtime permission requested before server start on API 33+
- [ ] `onTimeout()` implemented in `ServerHostService.kt`
- [ ] Account deletion option in Settings with double-confirmation dialog
- [ ] Account deletion clears Firebase Auth, Firestore, SharedPreferences, and Google Sign-In token
- [ ] Privacy Policy link in Settings
- [ ] Privacy Policy consent checkbox in onboarding before Sign-In button activates
- [ ] ProGuard rules protect JNI, Firebase, and Paper reflection
- [ ] `useLegacyPackaging = false` set for 16KB alignment
- [ ] All 3 notification channels created on app init
- [ ] No "cracked" or "piracy" strings anywhere in codebase or UI
- [ ] Unity Ads has 3-minute cooldown guard and `testMode = false` in release
- [ ] `DATA_SAFETY.md` created at project root
- [ ] `versionCode` incremented above previously distributed APK value
- [ ] Release AAB builds successfully without errors
