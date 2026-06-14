# PocketCraft — Google Account & Drive Backup Agent

## Your Role
You are a feature implementation agent. Your job is to add Google Sign-In and Google Drive world backup support to the PocketCraft Android app. Read the existing code carefully before making any changes. Do not ask questions — figure out the structure from the code and implement everything completely.

**Package name:** `com.pocketcraft.server`
**UI toolkit:** Jetpack Compose
**Design:** Match existing pitch black dark theme exactly.

---

## Overview

### What this feature does
- User signs in with their Google account inside PocketCraft
- App backs up the entire server folder as a zip to their personal Google Drive
- Backups happen automatically when the server stops AND manually when user taps backup
- Keep last 3 backups per user — oldest is deleted when a 4th is created
- User can restore any backup by downloading it back to the device

### What gets backed up
Everything inside the server folder:
```
context.filesDir/servers/{version}/
├── world/
├── plugins/
├── server.properties
├── ops.json
├── whitelist.json
├── banned-players.json
└── all other files
```

Compressed into a zip named:
```
PocketCraft_Backup_{version}_{timestamp}.zip
```
Example: `PocketCraft_Backup_1.20.4_2026-03-29_14-30.zip`

Stored in Google Drive in a folder called `PocketCraft Backups`.

---

## Dependencies

Add to `app/build.gradle`:
```gradle
dependencies {
    // Google Sign-In
    implementation 'com.google.android.gms:play-services-auth:21.0.0'

    // Google Drive API
    implementation 'com.google.api-client:google-api-client-android:2.2.0'
    implementation 'com.google.apis:google-api-services-drive:v3-rev20231128-2.0.0'
    implementation 'com.google.oauth-client:google-oauth-client-jetty:1.34.1'

    // Coroutines support
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3'
}
```

Add to `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.INTERNET" />

<!-- Already present but verify -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## Google Cloud Console Setup Instructions

Add these instructions to a file called `GOOGLE_DRIVE_SETUP.md` at the project root:

```markdown
# Google Drive Setup

1. Go to console.cloud.google.com
2. Create a new project called "PocketCraft"
3. Enable the Google Drive API:
   - APIs & Services → Library → search "Google Drive API" → Enable
4. Create OAuth 2.0 credentials:
   - APIs & Services → Credentials → Create Credentials → OAuth Client ID
   - Application type: Android
   - Package name: com.pocketcraft.server
   - SHA-1 fingerprint: run this command to get it:
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   - Copy the SHA-1 and paste it
5. Download the credentials (not needed as a file — the SHA-1 registration is enough for Android)
6. Also create a Web Application OAuth client (needed for Drive scopes):
   - Create Credentials → OAuth Client ID → Web application
   - Copy the Web Client ID
   - Add it to AppPreferences or a constants file as WEB_CLIENT_ID
```

---

## Create GoogleAuthManager.kt

```kotlin
package com.pocketcraft.server

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

object GoogleAuthManager {

    // Replace with your actual Web Client ID from Google Cloud Console
    private const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID_HERE"

    private var signInClient: GoogleSignInClient? = null

    fun init(context: Context) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE)) // access only files created by this app
            .build()
        signInClient = GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent(): Intent {
        return signInClient!!.signInIntent
    }

    fun getSignedInAccount(context: Context): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun isSignedIn(context: Context): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && account.grantedScopes.contains(Scope(DriveScopes.DRIVE_FILE))
    }

    fun signOut(context: Context, onComplete: () -> Unit) {
        signInClient?.signOut()?.addOnCompleteListener {
            onComplete()
        }
    }

    fun getAccountEmail(context: Context): String? {
        return getSignedInAccount(context)?.email
    }

    fun getAccountName(context: Context): String? {
        return getSignedInAccount(context)?.displayName
    }
}
```

---

## Create DriveBackupManager.kt

```kotlin
package com.pocketcraft.server

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DriveBackupManager {

    private const val BACKUP_FOLDER_NAME = "PocketCraft Backups"
    private const val MAX_BACKUPS = 3
    private const val APP_NAME = "PocketCraft"

    data class BackupInfo(
        val fileId: String,
        val fileName: String,
        val createdTime: Long,
        val sizeMb: Float
    )

    data class BackupResult(
        val success: Boolean,
        val fileName: String = "",
        val error: String = ""
    )

    private fun getDriveService(context: Context): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null

        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE)
        ).apply {
            selectedAccount = account.account
        }

        return Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName(APP_NAME).build()
    }

    /**
     * Creates a zip of the entire server folder and uploads to Google Drive.
     * Deletes oldest backup if more than MAX_BACKUPS exist.
     */
    suspend fun backup(
        context: Context,
        serverVersion: String,
        onProgress: (String) -> Unit
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService(context)
                ?: return@withContext BackupResult(false, error = "Not signed in to Google")

            onProgress("Preparing backup...")

            // Step 1: Zip the server folder
            val serverDir = File(context.filesDir, "servers/$serverVersion")
            if (!serverDir.exists()) {
                return@withContext BackupResult(false, error = "Server folder not found")
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
            val zipName = "PocketCraft_Backup_${serverVersion}_${timestamp}.zip"
            val zipFile = File(context.cacheDir, zipName)

            onProgress("Compressing server files...")
            zipFolder(serverDir, zipFile)

            val sizeMb = zipFile.length() / 1024f / 1024f
            onProgress("Uploading to Google Drive (%.1f MB)...".format(sizeMb))

            // Step 2: Get or create PocketCraft Backups folder in Drive
            val folderId = getOrCreateBackupFolder(drive)

            // Step 3: Upload zip to Drive
            val fileMetadata = DriveFile().apply {
                name = zipName
                parents = listOf(folderId)
            }

            val mediaContent = com.google.api.client.http.FileContent("application/zip", zipFile)

            drive.files().create(fileMetadata, mediaContent)
                .setFields("id, name, createdTime, size")
                .execute()

            onProgress("Cleaning up old backups...")

            // Step 4: Delete oldest backups if over limit
            enforceBackupLimit(drive, folderId)

            // Step 5: Clean up local zip
            zipFile.delete()

            onProgress("Backup complete!")
            BackupResult(success = true, fileName = zipName)

        } catch (e: Exception) {
            BackupResult(false, error = e.message ?: "Unknown error")
        }
    }

    /**
     * Lists all backups in Google Drive for this app.
     */
    suspend fun listBackups(context: Context): List<BackupInfo> = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService(context) ?: return@withContext emptyList()
            val folderId = getOrCreateBackupFolder(drive)

            val result = drive.files().list()
                .setQ("'$folderId' in parents and trashed=false")
                .setOrderBy("createdTime desc")
                .setFields("files(id, name, createdTime, size)")
                .execute()

            result.files.map { file ->
                BackupInfo(
                    fileId = file.id,
                    fileName = file.name,
                    createdTime = file.createdTime?.value ?: 0L,
                    sizeMb = (file.getSize() ?: 0L) / 1024f / 1024f
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Downloads a backup from Drive and extracts it to the server folder.
     * WARNING: This overwrites the existing server folder.
     */
    suspend fun restore(
        context: Context,
        backup: BackupInfo,
        serverVersion: String,
        onProgress: (String) -> Unit
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService(context)
                ?: return@withContext BackupResult(false, error = "Not signed in to Google")

            onProgress("Downloading backup...")

            // Download to cache
            val zipFile = File(context.cacheDir, backup.fileName)
            val outputStream = FileOutputStream(zipFile)
            drive.files().get(backup.fileId).executeMediaAndDownloadTo(outputStream)
            outputStream.close()

            onProgress("Extracting backup...")

            // Delete existing server folder
            val serverDir = File(context.filesDir, "servers/$serverVersion")
            if (serverDir.exists()) serverDir.deleteRecursively()
            serverDir.mkdirs()

            // Extract zip
            java.util.zip.ZipInputStream(FileInputStream(zipFile)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val file = File(serverDir, entry.name)
                    if (!file.canonicalPath.startsWith(serverDir.canonicalPath)) {
                        throw SecurityException("Zip slip detected")
                    }
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { out -> zip.copyTo(out) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            zipFile.delete()
            onProgress("Restore complete!")
            BackupResult(success = true, fileName = backup.fileName)

        } catch (e: Exception) {
            BackupResult(false, error = e.message ?: "Unknown error")
        }
    }

    /**
     * Deletes a specific backup from Drive.
     */
    suspend fun deleteBackup(context: Context, backup: BackupInfo): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val drive = getDriveService(context) ?: return@withContext false
                drive.files().delete(backup.fileId).execute()
                true
            } catch (e: Exception) {
                false
            }
        }

    private fun getOrCreateBackupFolder(drive: Drive): String {
        // Check if folder already exists
        val result = drive.files().list()
            .setQ("name='$BACKUP_FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false")
            .setFields("files(id)")
            .execute()

        if (result.files.isNotEmpty()) {
            return result.files[0].id
        }

        // Create folder
        val folderMetadata = DriveFile().apply {
            name = BACKUP_FOLDER_NAME
            mimeType = "application/vnd.google-apps.folder"
        }
        val folder = drive.files().create(folderMetadata)
            .setFields("id")
            .execute()
        return folder.id
    }

    private fun enforceBackupLimit(drive: Drive, folderId: String) {
        val result = drive.files().list()
            .setQ("'$folderId' in parents and trashed=false")
            .setOrderBy("createdTime asc") // oldest first
            .setFields("files(id, name)")
            .execute()

        // Delete oldest files if over limit
        val files = result.files
        if (files.size > MAX_BACKUPS) {
            val toDelete = files.take(files.size - MAX_BACKUPS)
            toDelete.forEach { file ->
                try {
                    drive.files().delete(file.id).execute()
                } catch (e: Exception) {
                    // Ignore individual delete failures
                }
            }
        }
    }

    private fun zipFolder(sourceDir: File, destZip: File) {
        ZipOutputStream(FileOutputStream(destZip)).use { zip ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entryName = file.relativeTo(sourceDir).path
                    zip.putNextEntry(ZipEntry(entryName))
                    FileInputStream(file).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }
}
```

---

## Add to AppPreferences.kt

```kotlin
var autoBackupEnabled: Boolean
    get() = prefs.getBoolean("auto_backup_enabled", true)
    set(value) = prefs.edit().putBoolean("auto_backup_enabled", value).apply()

var lastBackupTime: Long
    get() = prefs.getLong("last_backup_time", 0L)
    set(value) = prefs.edit().putLong("last_backup_time", value).apply()

var lastBackupFileName: String?
    get() = prefs.getString("last_backup_file", null)
    set(value) = prefs.edit().putString("last_backup_file", value).apply()
```

---

## Wire Auto-Backup into ServerHostService.kt

Find where `EVENT_STOPPED` is broadcast or where the server process is terminated. Add auto-backup trigger:

```kotlin
private fun onServerStopped() {
    val prefs = AppPreferences(this)

    if (prefs.autoBackupEnabled && GoogleAuthManager.isSignedIn(this)) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = DriveBackupManager.backup(
                context = this@ServerHostService,
                serverVersion = currentServerVersion,
                onProgress = { status ->
                    // Broadcast progress to UI if needed
                    sendBroadcast(Intent("ACTION_BACKUP_PROGRESS").apply {
                        putExtra("status", status)
                    })
                }
            )

            if (result.success) {
                prefs.lastBackupTime = System.currentTimeMillis()
                prefs.lastBackupFileName = result.fileName
            }
        }
    }
}
```

Call `onServerStopped()` wherever the server stops — both user-initiated and crash stops.

---

## UI — Google Account Section in Settings Screen

Add a new section to the Settings screen called **"Cloud Backup"**:

```kotlin
@Composable
fun CloudBackupSection(
    context: Context,
    serverVersion: String,
    signInLauncher: ActivityResultLauncher<Intent>
) {
    val isSignedIn = remember { mutableStateOf(GoogleAuthManager.isSignedIn(context)) }
    val accountEmail = remember { mutableStateOf(GoogleAuthManager.getAccountEmail(context)) }
    val accountName = remember { mutableStateOf(GoogleAuthManager.getAccountName(context)) }
    val autoBackup = remember { mutableStateOf(AppPreferences(context).autoBackupEnabled) }
    val backupStatus = remember { mutableStateOf("") }
    val isBackingUp = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Text(
                text = "Cloud Backup",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )

            Spacer(Modifier.height(16.dp))

            if (!isSignedIn.value) {
                // Not signed in — show sign in button
                Text(
                    text = "Sign in with Google to back up your server to Google Drive automatically.",
                    fontSize = 14.sp,
                    color = Color(0xFFB3B3B3)
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { signInLauncher.launch(GoogleAuthManager.getSignInIntent()) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_google), // add google icon drawable
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sign in with Google", color = Color.White, fontWeight = FontWeight.Bold)
                }

            } else {
                // Signed in — show account info and backup controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = accountName.value ?: "Google Account",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = accountEmail.value ?: "",
                            fontSize = 13.sp,
                            color = Color(0xFF6B6B6B)
                        )
                    }
                    TextButton(
                        onClick = {
                            GoogleAuthManager.signOut(context) {
                                isSignedIn.value = false
                                accountEmail.value = null
                            }
                        }
                    ) {
                        Text("Sign out", color = Color(0xFFED4245), fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Auto backup toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Auto-backup on server stop",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Backs up everything when server stops",
                            fontSize = 12.sp,
                            color = Color(0xFF6B6B6B)
                        )
                    }
                    Switch(
                        checked = autoBackup.value,
                        onCheckedChange = { enabled ->
                            autoBackup.value = enabled
                            AppPreferences(context).autoBackupEnabled = enabled
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF57F287)
                        )
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Show last backup time
                val lastBackup = AppPreferences(context).lastBackupTime
                if (lastBackup > 0) {
                    val timeAgo = getTimeAgo(lastBackup)
                    Text(
                        text = "Last backup: $timeAgo",
                        fontSize = 12.sp,
                        color = Color(0xFF6B6B6B)
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Backup status
                if (backupStatus.value.isNotEmpty()) {
                    Text(
                        text = backupStatus.value,
                        fontSize = 13.sp,
                        color = Color(0xFF57F287)
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Manual backup button
                Button(
                    onClick = {
                        scope.launch {
                            isBackingUp.value = true
                            backupStatus.value = "Starting backup..."
                            val result = DriveBackupManager.backup(
                                context = context,
                                serverVersion = serverVersion,
                                onProgress = { status -> backupStatus.value = status }
                            )
                            isBackingUp.value = false
                            if (result.success) {
                                AppPreferences(context).lastBackupTime = System.currentTimeMillis()
                                backupStatus.value = "✓ Backup saved to Google Drive"
                            } else {
                                backupStatus.value = "✗ Backup failed: ${result.error}"
                            }
                        }
                    },
                    enabled = !isBackingUp.value,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF57F287),
                        disabledContainerColor = Color(0xFF2A2A2A)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isBackingUp.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Backing up...", color = Color.Black)
                    } else {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("Backup Now", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // View backups button
                OutlinedButton(
                    onClick = { /* navigate to backup list screen */ },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB3B3B3)),
                    border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("View & Restore Backups")
                }
            }
        }
    }
}

fun getTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000} minutes ago"
        diff < 86_400_000 -> "${diff / 3_600_000} hours ago"
        else -> "${diff / 86_400_000} days ago"
    }
}
```

---

## UI — Backup List Screen

Create a new screen `BackupListScreen.kt`:

```kotlin
@Composable
fun BackupListScreen(
    context: Context,
    serverVersion: String,
    onNavigateBack: () -> Unit
) {
    var backups by remember { mutableStateOf<List<DriveBackupManager.BackupInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var restoreStatus by remember { mutableStateOf("") }
    var isRestoring by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf<DriveBackupManager.BackupInfo?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        backups = DriveBackupManager.listBackups(context)
        isLoading = false
    }

    Scaffold(
        containerColor = Color(0xFF0F0F0F),
        topBar = {
            TopAppBar(
                title = { Text("Cloud Backups", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F0F))
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {

            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFF57F287))
            } else if (backups.isEmpty()) {
                Text(
                    text = "No backups yet.\nCreate your first backup from Settings.",
                    color = Color(0xFFB3B3B3),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp)
                )
            } else {
                Text(
                    text = "Last ${backups.size} backups (max 3 kept)",
                    fontSize = 13.sp,
                    color = Color(0xFF6B6B6B),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                backups.forEach { backup ->
                    BackupItem(
                        backup = backup,
                        onRestore = { showRestoreConfirm = it },
                        onDelete = {
                            scope.launch {
                                DriveBackupManager.deleteBackup(context, it)
                                backups = DriveBackupManager.listBackups(context)
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (restoreStatus.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(text = restoreStatus, color = Color(0xFF57F287), fontSize = 14.sp)
            }
        }
    }

    // Restore confirmation dialog
    showRestoreConfirm?.let { backup ->
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = null },
            containerColor = Color(0xFF1A1A1A),
            title = { Text("Restore Backup?", color = Color.White) },
            text = {
                Text(
                    "This will replace your current server with:\n\n${backup.fileName}\n\nThis cannot be undone.",
                    color = Color(0xFFB3B3B3)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = null
                    scope.launch {
                        isRestoring = true
                        restoreStatus = "Restoring..."
                        val result = DriveBackupManager.restore(
                            context = context,
                            backup = backup,
                            serverVersion = serverVersion,
                            onProgress = { restoreStatus = it }
                        )
                        isRestoring = false
                        restoreStatus = if (result.success) "✓ Restored successfully" else "✗ ${result.error}"
                    }
                }) {
                    Text("Restore", color = Color(0xFFED4245), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = null }) {
                    Text("Cancel", color = Color(0xFFB3B3B3))
                }
            }
        )
    }
}

@Composable
fun BackupItem(
    backup: DriveBackupManager.BackupInfo,
    onRestore: (DriveBackupManager.BackupInfo) -> Unit,
    onDelete: (DriveBackupManager.BackupInfo) -> Unit
) {
    val date = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        .format(Date(backup.createdTime))

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = date,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = "%.1f MB".format(backup.sizeMb),
                    fontSize = 12.sp,
                    color = Color(0xFF6B6B6B)
                )
            }
            Row {
                TextButton(onClick = { onRestore(backup) }) {
                    Text("Restore", color = Color(0xFF57F287), fontSize = 13.sp)
                }
                IconButton(onClick = { onDelete(backup) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFED4245),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
```

---

## Wire Google Sign-In in Activity

In the main Activity (home screen), set up the sign-in launcher:

```kotlin
// In the Activity or composable that hosts Settings
val signInLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            // Sign in successful — refresh UI
            isSignedIn = true
        } catch (e: ApiException) {
            // Sign in failed
            Toast.makeText(context, "Sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

// Initialize GoogleAuthManager on app start
LaunchedEffect(Unit) {
    GoogleAuthManager.init(context)
}
```

---

## Quality Checklist

Before finishing, verify:

- [ ] All Drive dependencies added to app/build.gradle
- [ ] `GoogleAuthManager.kt` exists with correct package name
- [ ] `DriveBackupManager.kt` exists with correct package name
- [ ] `GOOGLE_DRIVE_SETUP.md` created at project root with setup instructions
- [ ] `autoBackupEnabled`, `lastBackupTime`, `lastBackupFileName` added to AppPreferences
- [ ] `GoogleAuthManager.init()` called in Application onCreate
- [ ] Sign in button shows when not signed in
- [ ] Sign out button shows when signed in with account name and email
- [ ] Auto-backup toggle saves to AppPreferences
- [ ] Auto-backup triggers in ServerHostService when server stops
- [ ] Auto-backup only runs if user is signed in AND auto-backup is enabled
- [ ] Manual backup button shows progress status text while running
- [ ] Manual backup button disabled while backup is in progress
- [ ] Last backup time shown as relative time ("2 hours ago")
- [ ] Backup creates zip of entire server folder
- [ ] Zip named `PocketCraft_Backup_{version}_{timestamp}.zip`
- [ ] Zip uploaded to `PocketCraft Backups` folder in user's Google Drive
- [ ] Folder created automatically if it doesn't exist
- [ ] Max 3 backups enforced — oldest deleted when 4th is created
- [ ] BackupListScreen shows all backups with date and size
- [ ] Restore shows confirmation dialog before overwriting
- [ ] Restore downloads zip and extracts to server folder
- [ ] Zip slip security check in restore
- [ ] Delete backup removes from Google Drive
- [ ] Empty state shown when no backups exist
- [ ] All dialogs and screens use pitch black dark theme
- [ ] App builds without errors

---

## What This Does NOT Change

- Relay system — unchanged
- Server launching logic — unchanged
- Firebase Analytics — unchanged
- Plugin manager — unchanged
- World import — unchanged
- Existing UI screens beyond adding the cloud backup section to Settings
