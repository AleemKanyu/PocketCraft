# Google Drive Setup for PocketCraft

This guide explains how to set up Google Cloud Console to enable Google Drive backups in PocketCraft.

## Prerequisites
- A Google account
- Access to Google Cloud Console (console.cloud.google.com)

## Step-by-Step Setup

### 1. Create a Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Click on the project selector at the top
3. Click "NEW PROJECT"
4. Name it: `PocketCraft`
5. Click "CREATE"
6. Wait for the project to be created, then select it

### 2. Enable Google Drive API

1. In the Cloud Console, go to **APIs & Services** → **Library**
2. Search for "Google Drive API"
3. Click on **Google Drive API**
4. Click the blue **ENABLE** button
5. Wait for it to enable

### 3. Create Android OAuth 2.0 Credentials

1. Go to **APIs & Services** → **Credentials**
2. Click **+ CREATE CREDENTIALS** → **OAuth Client ID**
3. You'll be prompted to configure the OAuth consent screen first:
   - Select **External** as User Type
   - Click **CREATE**
4. On the OAuth consent screen:
   - **App name**: `PocketCraft`
   - **User support email**: (your email)
   - **Developer contact information**: (your email)
   - Click **SAVE AND CONTINUE** through all steps
5. Return to Credentials and click **+ CREATE CREDENTIALS** → **OAuth Client ID**
6. Select **Android** as Application Type
7. Fill in:
   - **Package name**: `com.pocketcraft.server`
   - **SHA-1 certificate fingerprint**: (see below for how to get this)
8. Get your SHA-1 fingerprint by running:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
   Look for the SHA-1 line (format: `XX:XX:XX:XX:...`)
9. Paste the SHA-1 into the form
10. Click **CREATE**
11. You should see "Android client created successfully"

### 4. Create Web Application OAuth 2.0 Credentials

1. Go to **APIs & Services** → **Credentials**
2. Click **+ CREATE CREDENTIALS** → **OAuth Client ID**
3. Select **Web application**
4. Name it: `PocketCraft Web Client`
5. Click **CREATE**
6. Copy the **Client ID** (it will look like: `abc123...xyz.apps.googleusercontent.com`)
7. Go to `GoogleAuthManager.kt` in your code and replace:
   ```kotlin
   private const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID_HERE"
   ```
   with your copied Client ID

### 5. Verify Setup

1. In Google Cloud Console, go to **APIs & Services** → **Enabled APIs**
2. Verify that **Google Drive API** is listed
3. Go to **APIs & Services** → **Credentials**
4. Verify you have:
   - One Android Client ID (with SHA-1)
   - One Web Application Client ID

## What's Happening

- **Android Client ID**: Lets the PocketCraft app on your phone sign in using Google
- **Web Client ID**: Used internally by the Google API libraries to make Drive API calls
- **Google Drive API**: Allows the app to read, write, and delete files in your Google Drive

## Troubleshooting

**"Package name mismatch"**: Make sure you use exactly `com.pocketcraft.server` in the package name field.

**"SHA-1 fingerprint not valid"**: Make sure you're copying the correct line from keytool output. It should start with a hex digit and contain colons.

**"Sign-in not working"**: If OAuth consent screen shows a warning, you may need to add your Google account to the test users list:
1. Go to **OAuth consent screen** → **Test users**
2. Click **ADD USERS**
3. Enter your Google account email
4. Click **ADD**

**"Drive API not enabled"**: Go back to Step 2 and make sure you clicked the **ENABLE** button.

## One-Time Setup

You only need to set this up once per development machine. The WEB_CLIENT_ID in `GoogleAuthManager.kt` is committed to the repo and will work for all developers.

For production builds with a release keystore, you'll need to:
1. Get the SHA-1 from your release keystore:
   ```bash
   keytool -list -v -keystore /path/to/release.keystore -alias your_alias
   ```
2. Create a NEW Android OAuth Client ID in Google Cloud Console using that SHA-1
3. The release build will use whichever Client ID matches its keystore's SHA-1

## Resetting / Troubleshooting

If you need to reset everything:
1. Delete the project in Google Cloud Console (or just delete the credentials)
2. Create a new project and follow these steps again
3. Update the WEB_CLIENT_ID in `GoogleAuthManager.kt`
