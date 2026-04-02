# Google Drive Integration - Debug & Re-enable Guide

> **Status**: Disabled for pre-release development. Follow this guide to re-enable after PlayStore publishing.

## Overview

PocketCraft previously supported cloud backups via Google Drive. This feature requires Google Sign-In integration and OAuth2 credentials. This file documents the complete setup for re-enabling this feature.

---

## 📋 Pre-Release Status

### What's Disabled
- Google Sign-in UI in Settings screen (CloudBackupSection)
- GoogleAuthManager initialization in PocketCraftApp.kt
- Auto-backup functionality in ServerHostService
- Google Play Services Drive API integration

### Why It's Disabled
- App not yet published to PlayStore
- OAuth credentials are development-only and tied to specific test users
- API keys contain sensitive information that shouldn't be in source control during pre-release

---

## 🔧 Files Involved

### Core Implementation Files
1. **GoogleAuthManager.kt** - OAuth2 authentication manager
   - Location: `app/src/main/kotlin/com/pocketcraft/server/GoogleAuthManager.kt`
   - Contains: Web Client ID, sign-in logic, account management
   - Current State: Code intact, just not initialized

2. **DriveBackupManager.kt** - Google Drive backup handler
   - Location: `app/src/main/kotlin/com/pocketcraft/server/DriveBackupManager.kt`
   - Contains: Upload/download logic for backups
   - Current State: Code intact, just not called

3. **PocketCraftApp.kt** - Application initialization
   - Location: `app/src/main/kotlin/com/pocketcraft/server/PocketCraftApp.kt`
   - Change: Line 24 `GoogleAuthManager.init(this)` commented out
   - Re-enable: Uncomment line 24

4. **SettingsScreen.kt** - User interface
   - Location: `app/src/main/kotlin/com/pocketcraft/server/ui/screens/SettingsScreen.kt`
   - Changes:
     - Lines 58-59: GoogleAuthManager/DriveBackupManager imports commented
     - Lines 75-76: Google Auth/API imports commented
     - Lines 110-125: SignInLauncher setup commented
     - Lines 405: CloudBackupSection() call commented
     - Lines 792-1000+: CloudBackupSection() function commented
   - Re-enable: Uncomment all lines marked with GOOGLE_DRIVE_SETUP_DEBUG

5. **ServerHostService.kt** - Auto-backup on server shutdown
   - Location: `app/src/main/kotlin/com/pocketcraft/server/server/ServerHostService.kt`
   - Changes: Lines 171-178 (auto-backup check) commented out
   - Re-enable: Uncomment lines marked with GOOGLE_DRIVE_SETUP_DEBUG

### Configuration Files
- **build.gradle.kts** - Google Play Services dependencies already included
- **AndroidManifest.xml** - No changes needed (OAuth permissions not required pre-auth)

---

## 🔐 Required Setup Steps (When Re-enabling)

### Step 1: Google Cloud Project Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing: "PocketCraft"
3. Enable APIs:
   - Google Drive API
   - Google Sign-In API
4. Create OAuth 2.0 credentials:
   - Type: **Web Application**
   - Authorized Redirect URIs: Add redirect URI from your app's package

### Step 2: Get Web Client ID

1. In Google Cloud Console, go to Credentials
2. Find OAuth 2.0 Client IDs section
3. Copy the **Web Client ID** (format: `XXXX.apps.googleusercontent.com`)
4. Update in `GoogleAuthManager.kt`:
   ```kotlin
   private const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID_HERE"
   ```

### Step 3: Get SHA-1 Fingerprint for Release Build

When ready to publish:
```bash
# For debug keystore
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android

# For release keystore (after creating)
keytool -list -v -keystore /path/to/release.keystore -alias release
```

Add both SHA-1 fingerprints to Google Cloud Console under "Android" OAuth credentials.

### Step 4: Create Android OAuth Credentials

In Google Cloud Console:
1. Create Credentials → OAuth 2.0 Client ID → Android
2. Package Name: `com.pocketcraft.server`
3. Signature Certificate Fingerprint: Add SHA-1 from step 3
4. Save and note the **Android Client ID**

### Step 5: Test with Test Users

1. Add test user emails to OAuth 2.0 consent screen
2. Email addresses that can authenticate:
   - During development: Any registered test user
   - After publishing: Any Google account (with proper consent screen)

---

## 📝 Re-enabling Checklist

- [ ] **Step 1**: Comment out all `// GOOGLE_DRIVE_SETUP_DEBUG:` markers to restore functionality
  ```kotlin
  // GOOGLE_DRIVE_SETUP_DEBUG: Commented out for pre-release
  // GoogleAuthManager.init(this)  ← Uncomment this line
  ```

- [ ] **Step 2**: Verify imports are restored in SettingsScreen.kt
  ```kotlin
  // GOOGLE_DRIVE_SETUP_DEBUG: Commented out
  // import com.pocketcraft.server.GoogleAuthManager  ← Uncomment
  // import com.pocketcraft.server.DriveBackupManager  ← Uncomment
  ```

- [ ] **Step 3**: Restore CloudBackupSection() call in SettingsScreen
  ```kotlin
  // GOOGLE_DRIVE_SETUP_DEBUG: Commented out
  // CloudBackupSection(...)  ← Uncomment this
  ```

- [ ] **Step 4**: Restore auto-backup logic in ServerHostService
  ```kotlin
  // GOOGLE_DRIVE_SETUP_DEBUG: Commented out
  // if (prefs.autoBackupEnabled && GoogleAuthManager.isSignedIn(...)) { ... }
  ```

- [ ] **Step 5**: Update `GoogleAuthManager.kt` with real Web Client ID
- [ ] **Step 6**: Create Android OAuth credentials in Google Cloud Console
- [ ] **Step 7**: Add all SHA-1 fingerprints (debug + release)
- [ ] **Step 8**: Configure OAuth consent screen
- [ ] **Step 9**: Add test users if needed
- [ ] **Step 10**: Test sign-in flow on debug device

---

## 🧪 Testing After Re-enable

1. **Sign-In Flow**
   - Open Settings → Cloud Backup
   - Click "Sign in with Google"
   - Verify account details appear

2. **Manual Backup**
   - Click "Backup Now"
   - Verify Firebase Console shows new backup uploaded
   - Check Google Drive inside app: backup should appear

3. **Auto-Backup**
   - Enable "Auto Backup" toggle
   - Stop server normally
   - Verify backup created automatically

4. **Restore**
   - Navigate to Backups section
   - Select a backup and restore
   - Verify server config restored correctly

---

## 🔗 Current Code Locations with Disabled Code

### PocketCraftApp.kt
```kotlin
// Line 24
// GOOGLE_DRIVE_SETUP_DEBUG: Commented out for pre-release
// GoogleAuthManager.init(this)
```

### SettingsScreen.kt
```kotlin
// Lines 58-59
// GOOGLE_DRIVE_SETUP_DEBUG: Commented out for pre-release
// import com.pocketcraft.server.GoogleAuthManager
// import com.pocketcraft.server.DriveBackupManager

// Lines 75-76
// GOOGLE_DRIVE_SETUP_DEBUG: Commented out for pre-release
// import com.google.android.gms.auth.api.signin.GoogleSignIn
// import com.google.android.gms.common.api.ApiException

// Lines 110-125
// GOOGLE_DRIVE_SETUP_DEBUG: Commented out - SignIn launcher setup

// Line 405
// GOOGLE_DRIVE_SETUP_DEBUG: Commented out
// CloudBackupSection(...)

// Lines 792-1000+
// GOOGLE_DRIVE_SETUP_DEBUG: Commented out - entire CloudBackupSection function
```

### ServerHostService.kt
```kotlin
// Lines 22-23
// GOOGLE_DRIVE_SETUP_DEBUG: Commented out for pre-release
// import com.pocketcraft.server.GoogleAuthManager
// import com.pocketcraft.server.DriveBackupManager

// Lines 171-178
// GOOGLE_DRIVE_SETUP_DEBUG: Commented out for pre-release
// if (prefs.autoBackupEnabled && GoogleAuthManager.isSignedIn(...)) {
//     // Auto-backup logic
// }
```

---

## 🚨 Important Notes

### Security
- **NEVER** commit real API keys or Client IDs to repository
- Use environment variables or secure secret management for production
- OAuth credentials are development-specific and tied to test accounts

### API Quotas
- Google Drive API has quotas (1 million requests/day typically)
- Each backup is ~few MB
- Multiple sign-in attempts count toward quota
- Monitor in [Google Cloud Console](https://console.cloud.google.com/apis/dashboard)

### PlayStore Requirements
Before publishing:
1. Create privacy policy mentioning Google Drive access
2. Clear OAuth consent screen
3. Move from test user mode to production
4. Ensure all required scopes are disclosed

### Troubleshooting
- **"Authorization failed"**: User not in test users list or credentials expired
- **"Drive quota exceeded"**: Check quotas in Cloud Console
- **"Backup not saving"**: Verify DriveBackupManager credentials are valid
- **"Sign-in button not showing"**: Check GoogleAuthManager was initialized

---

## 📚 References

- [Google Sign-In for Android](https://developers.google.com/identity/sign-in/android)
- [Google Drive API](https://developers.google.com/drive/api)
- [OAuth 2.0 Scopes](https://developers.google.com/identity/protocols/oauth2/scopes)
- [Android App Signing](https://developer.android.com/studio/publish/app-signing)

---

**Last Updated**: March 30, 2026
**Status**: Pre-release (feature disabled)
**Next Step**: Follow checklist when ready to publish to PlayStore
