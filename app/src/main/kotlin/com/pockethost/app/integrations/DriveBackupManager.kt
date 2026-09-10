package com.pockethost.app.integrations

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import java.io.File
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.first
import com.pockethost.app.data.preferences.AppPreferences
import com.pockethost.app.data.preferences.AppPreferencesStore
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RemoteDriveBackup(
    val id: String,
    val name: String,
    val modifiedTime: String?
)

object DriveBackupManager {
    private const val APPLICATION_NAME = "PocketHost"
    private const val LEGACY_BACKUP_FOLDER = "PocketHost Server Backups"
    private const val EXPORTED_BACKUP_FOLDER = "PocketHostWorldBackups"

    suspend fun uploadLatestWorldBackup(
        context: Context,
        account: GoogleSignInAccount,
        worldName: String,
        onProgress: suspend (Int, String) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        val localFile = prepareLatestBackupForUpload(context, worldName)
            ?: return@withContext "No readable local backup found for cloud upload. Create a backup first."
        try {
            val token = accessToken(context, account)
            onProgress(5, "Preparing cloud upload...")
            val metadataJson = org.json.JSONObject()
                .put("name", localFile.name)
                .put("parents", org.json.JSONArray().put("appDataFolder"))
                .toString()
            val boundary = "----PocketCraftDrive${System.currentTimeMillis()}"
            val connection = openConnection(
                url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name",
                method = "POST",
                token = token,
                contentType = "multipart/related; boundary=$boundary"
            )
            connection.setChunkedStreamingMode(256 * 1024)
            connection.doOutput = true
            connection.outputStream.use { raw ->
                val output = BufferedOutputStream(raw)
                output.write("--$boundary\r\n".toByteArray())
                output.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
                output.write(metadataJson.toByteArray(Charsets.UTF_8))
                output.write("\r\n--$boundary\r\n".toByteArray())
                output.write("Content-Type: application/zip\r\n\r\n".toByteArray())
                val totalBytes = localFile.length().coerceAtLeast(1L)
                BufferedInputStream(localFile.inputStream()).use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var copied = 0L
                    // Only report when the whole-percent value actually moves. Emitting on every
                    // 16 KB buffer meant tens of thousands of UI updates for a large backup.
                    var lastProgress = -1
                    var bytesRead = input.read(buffer)
                    while (bytesRead != -1) {
                        output.write(buffer, 0, bytesRead)
                        copied += bytesRead
                        val progress = 12 + ((copied * 78L) / totalBytes).toInt().coerceIn(0, 78)
                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgress(progress, "Uploading backup to Google Drive...")
                        }
                        bytesRead = input.read(buffer)
                    }
                }
                output.write("\r\n--$boundary--\r\n".toByteArray())
                output.flush()
            }
            val body = readResponseBody(connection)
            ensureSuccess(connection, body, "upload backup to Google Drive")
            onProgress(100, "Cloud backup complete.")
            return@withContext "Uploaded ${localFile.name} to Google Drive."
        } finally {
            if (localFile.parentFile == context.cacheDir) {
                localFile.delete()
            }
        }
    }

    /**
     * Uploads a specific local backup file to Google Drive.
     * Used when the user taps the "Upload to Cloud" button on a specific backup entry.
     */
    suspend fun uploadSpecificBackupFile(
        context: Context,
        account: GoogleSignInAccount,
        file: File,
        onProgress: suspend (Int, String) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead()) {
            return@withContext "Backup file not found or not readable."
        }
        val token = accessToken(context, account)
        onProgress(5, "Preparing cloud upload...")
        val metadataJson = org.json.JSONObject()
            .put("name", file.name)
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .toString()
        val boundary = "----PocketCraftDrive${System.currentTimeMillis()}"
        val connection = openConnection(
            url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name",
            method = "POST",
            token = token,
            contentType = "multipart/related; boundary=$boundary"
        )
        connection.setChunkedStreamingMode(256 * 1024)
        connection.doOutput = true
        connection.outputStream.use { raw ->
            val output = BufferedOutputStream(raw)
            output.write("--$boundary\r\n".toByteArray())
            output.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            output.write(metadataJson.toByteArray(Charsets.UTF_8))
            output.write("\r\n--$boundary\r\n".toByteArray())
            output.write("Content-Type: application/zip\r\n\r\n".toByteArray())
            val totalBytes = file.length().coerceAtLeast(1L)
            BufferedInputStream(file.inputStream()).use { input ->
                val buffer = ByteArray(16 * 1024)
                var copied = 0L
                // See uploadBackup: report only on whole-percent changes, not per buffer.
                var lastProgress = -1
                var bytesRead = input.read(buffer)
                while (bytesRead != -1) {
                    output.write(buffer, 0, bytesRead)
                    copied += bytesRead
                    val progress = 12 + ((copied * 78L) / totalBytes).toInt().coerceIn(0, 78)
                    if (progress != lastProgress) {
                        lastProgress = progress
                        onProgress(progress, "Uploading to Google Drive...")
                    }
                    bytesRead = input.read(buffer)
                }
            }
            output.write("\r\n--$boundary--\r\n".toByteArray())
            output.flush()
        }
        val body = readResponseBody(connection)
        ensureSuccess(connection, body, "upload backup to Google Drive")
        onProgress(100, "Cloud backup complete.")
        return@withContext "Uploaded ${file.name} to Google Drive."
    }

    suspend fun listWorldBackups(
        context: Context,
        account: GoogleSignInAccount,
        worldName: String
    ): List<RemoteDriveBackup> = withContext(Dispatchers.IO) {
        val token = accessToken(context, account)
        val queryWorld = escapeDriveQuery(worldName)
        val query = "trashed = false and name contains '$queryWorld-'"
        val url = buildString {
            append("https://www.googleapis.com/drive/v3/files")
            append("?spaces=")
            append(URLEncoder.encode("appDataFolder", "UTF-8"))
            append("&q=")
            append(URLEncoder.encode(query, "UTF-8"))
            append("&orderBy=")
            append(URLEncoder.encode("modifiedTime desc", "UTF-8"))
            append("&fields=")
            append(URLEncoder.encode("files(id,name,modifiedTime)", "UTF-8"))
        }
        val connection = openConnection(url = url, method = "GET", token = token)
        val body = readResponseBody(connection)
        ensureSuccess(connection, body, "list cloud backups")
        val files = org.json.JSONObject(body).optJSONArray("files") ?: org.json.JSONArray()
        return@withContext List(files.length()) { index ->
            val item = files.getJSONObject(index)
            RemoteDriveBackup(
                id = item.optString("id"),
                name = item.optString("name"),
                modifiedTime = item.optString("modifiedTime").ifBlank { null }
            )
        }
    }

    suspend fun downloadBackup(
        context: Context,
        account: GoogleSignInAccount,
        backup: RemoteDriveBackup,
        outputFile: File
    ) = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val token = accessToken(context, account)
        val url = "https://www.googleapis.com/drive/v3/files/${backup.id}?alt=media"
        val connection = openConnection(url = url, method = "GET", token = token)
        if (connection.responseCode !in 200..299) {
            val body = readResponseBody(connection)
            throw java.io.IOException(buildErrorMessage(connection, body, "download cloud backup"))
        }
        outputFile.outputStream().use { output ->
            connection.inputStream.use { input ->
                input.copyTo(output)
            }
        }
    }

    suspend fun deleteBackup(
        context: Context,
        account: GoogleSignInAccount,
        backupId: String
    ): String = withContext(Dispatchers.IO) {
        val token = accessToken(context, account)
        val connection = openConnection(
            url = "https://www.googleapis.com/drive/v3/files/$backupId",
            method = "DELETE",
            token = token
        )
        val body = readResponseBody(connection)
        ensureSuccess(connection, body, "delete cloud backup")
        return@withContext "Deleted cloud backup from Google Drive."
    }

    suspend fun deleteAllCloudBackups(
        context: Context,
        account: GoogleSignInAccount
    ): Unit = withContext(Dispatchers.IO) {
        val token = accessToken(context, account)
        var nextPageToken: String? = null
        do {
            val url = buildString {
                append("https://www.googleapis.com/drive/v3/files?spaces=")
                append(java.net.URLEncoder.encode("appDataFolder", "UTF-8"))
                append("&fields=")
                append(java.net.URLEncoder.encode("nextPageToken,files(id)", "UTF-8"))
                if (nextPageToken != null) {
                    append("&pageToken=")
                    append(java.net.URLEncoder.encode(nextPageToken!!, "UTF-8"))
                }
            }
            val connection = openConnection(url = url, method = "GET", token = token)
            val body = readResponseBody(connection)
            ensureSuccess(connection, body, "list all cloud files")
            val jsonObj = org.json.JSONObject(body)
            val files = jsonObj.optJSONArray("files") ?: org.json.JSONArray()
            for (i in 0 until files.length()) {
                val fileId = files.getJSONObject(i).optString("id")
                if (fileId.isNotBlank()) {
                    runCatching {
                        val deleteConnection = openConnection(
                            url = "https://www.googleapis.com/drive/v3/files/$fileId",
                            method = "DELETE",
                            token = token
                        )
                        val deleteBody = readResponseBody(deleteConnection)
                        ensureSuccess(deleteConnection, deleteBody, "delete file")
                    }
                }
            }
            nextPageToken = if (jsonObj.has("nextPageToken") && !jsonObj.isNull("nextPageToken")) {
                jsonObj.optString("nextPageToken").takeIf { it.isNotBlank() }
            } else null
        } while (nextPageToken != null)
    }

    fun latestBackupFile(worldName: String): File? {
        val downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val candidates = listOf(
            File(downloads, "$LEGACY_BACKUP_FOLDER/$worldName"),
            File(downloads, "$EXPORTED_BACKUP_FOLDER/$worldName"),
            File(downloads, "PocketCraftWorldBackups/$worldName"),
            File(downloads, "$LEGACY_BACKUP_FOLDER/${sanitizeWorldName(worldName)}"),
            File(downloads, "$EXPORTED_BACKUP_FOLDER/${sanitizeWorldName(worldName)}"),
            File(downloads, "PocketCraftWorldBackups/${sanitizeWorldName(worldName)}")
        )
        return candidates
            .flatMap { dir ->
                dir.listFiles()
                    ?.filter { it.isFile && it.extension.equals("zip", ignoreCase = true) }
                    .orEmpty()
            }
            .maxByOrNull { it.lastModified() }
    }

    private fun prepareLatestBackupForUpload(context: Context, worldName: String): File? {
        latestBackupUri(context, worldName)?.let { uri ->
            val displayName = latestBackupDisplayName(context, uri)
            val tempFile = File(context.cacheDir, displayName)
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: error("Unable to open backup stream for upload.")
            }.onSuccess {
                if (tempFile.length() > 0L) {
                    return tempFile
                }
            }.onFailure {
                android.util.Log.e("DriveBackupManager", "Failed to stage MediaStore backup for upload", it)
                tempFile.delete()
            }
        }
        return latestBackupFile(worldName)?.takeIf { it.exists() && it.canRead() }
    }

    private fun queryBackupUriForPath(context: Context, relativePathPattern: String): android.net.Uri? {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf(
            relativePathPattern,
            "%.zip"
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        return runCatching {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val id = cursor.getLong(idIndex)
                ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            }
        }.getOrNull()
    }

    private fun latestBackupUri(context: Context, worldName: String): android.net.Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val safeWorldName = sanitizeWorldName(worldName)
        return queryBackupUriForPath(context, "Download/$EXPORTED_BACKUP_FOLDER/$safeWorldName%")
            ?: queryBackupUriForPath(context, "Download/PocketCraftWorldBackups/$safeWorldName%")
    }

    private fun latestBackupDisplayName(context: Context, uri: android.net.Uri): String {
        val fallback = "cloud_backup_${System.currentTimeMillis()}.zip"
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                cursor.getString(nameIndex)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun sanitizeWorldName(worldName: String): String {
        val trimmed = worldName.trim()
        val cleaned = trimmed.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return cleaned.replace(Regex("_+"), "_").trim('_').ifBlank { "world" }
    }

    private fun driveService(context: Context, account: GoogleSignInAccount): Drive {
        val selectedAccount = account.account
            ?: if (!account.email.isNullOrBlank()) {
                android.accounts.Account(account.email!!, "com.google")
            } else {
                throw IllegalStateException("Google account is missing account information.")
            }
        val credential = GoogleAccountCredential.usingOAuth2(
            context.applicationContext,
            setOf(DriveScopes.DRIVE_APPDATA)
        ).apply {
            this.selectedAccount = selectedAccount
        }
        return Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName(APPLICATION_NAME).build()
    }

    private fun accessToken(context: Context, account: GoogleSignInAccount): String {
        return driveCredential(context, account).token
    }

    private fun driveCredential(
        context: Context,
        account: GoogleSignInAccount
    ): GoogleAccountCredential {
        val selectedAccount = account.account
            ?: if (!account.email.isNullOrBlank()) {
                android.accounts.Account(account.email!!, "com.google")
            } else {
                throw IllegalStateException("Google account is missing account information.")
            }
        return GoogleAccountCredential.usingOAuth2(
            context.applicationContext,
            setOf(DriveScopes.DRIVE_APPDATA)
        ).apply {
            this.selectedAccount = selectedAccount
        }
    }

    private fun openConnection(
        url: String,
        method: String,
        token: String,
        contentType: String? = null
    ): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            contentType?.let { setRequestProperty("Content-Type", it) }
            connectTimeout = 30_000
            readTimeout = 60_000
            doInput = true
        }
    }

    private fun readResponseBody(connection: HttpURLConnection): String {
        val stream = connection.errorStream ?: runCatching { connection.inputStream }.getOrNull()
            ?: return ""
        return stream.bufferedReader().use { it.readText() }
    }

    private fun ensureSuccess(connection: HttpURLConnection, body: String, action: String) {
        if (connection.responseCode !in 200..299) {
            throw java.io.IOException(buildErrorMessage(connection, body, action))
        }
    }

    private fun buildErrorMessage(
        connection: HttpURLConnection,
        body: String,
        action: String
    ): String {
        val status = connection.responseCode
        val detail = extractDriveErrorMessage(body).ifBlank { connection.responseMessage ?: "Unknown error" }
        return "Could not $action ($status): $detail"
    }

    private fun extractDriveErrorMessage(body: String): String {
        if (body.isBlank()) return ""
        return runCatching {
            val json = org.json.JSONObject(body)
            when {
                json.has("error") && json.opt("error") is org.json.JSONObject ->
                    json.getJSONObject("error").optString("message")
                json.has("error") && json.opt("error") is String ->
                    json.optString("error")
                else -> json.optString("message")
            }
        }.getOrNull().orEmpty().ifBlank { body.take(240) }
    }

    private fun escapeDriveQuery(value: String): String = value.replace("'", "\\'")

    suspend fun uploadAppSettings(context: Context, account: GoogleSignInAccount): Unit = withContext(Dispatchers.IO) {
        val prefs = AppPreferences(context)
        val soundEnabled = AppPreferencesStore.isSoundEnabledFlow(context).first()
        val notificationsEnabled = AppPreferencesStore.isNotificationsEnabledFlow(context).first()
        val darkMode = AppPreferencesStore.getDarkModeOverrideFlow(context).first() ?: "SYSTEM"
        val relayHost = AppPreferencesStore.getRelayHostFlow(context).first()

        val json = org.json.JSONObject().apply {
            put("themeMode", darkMode)
            put("soundEnabled", soundEnabled)
            put("notificationsEnabled", notificationsEnabled)
            put("autoRestart", prefs.autoRestart)
            put("isFloatingChatEnabled", prefs.isFloatingChatEnabled)
            put("ramMode", prefs.ramMode)
            put("manualRamMb", prefs.manualRamMb)
            put("relayHost", relayHost)
            put("appLanguage", prefs.appLanguage)
            put("isMaxPowerMode", prefs.isMaxPowerMode)
        }

        val tempFile = File(context.cacheDir, "settings_backup.json")
        tempFile.writeText(json.toString(2))

        val drive = driveService(context, account)
        val existingFiles = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("trashed = false and name = 'settings_backup.json'")
            .setFields("files(id)")
            .execute()
            .files
            
        val metadata = DriveFile().apply {
            name = "settings_backup.json"
        }
        val content = FileContent("application/json", tempFile)
        
        if (!existingFiles.isNullOrEmpty()) {
            val fileId = existingFiles[0].id
            drive.files().update(fileId, null, content).execute()
        } else {
            metadata.parents = listOf("appDataFolder")
            drive.files().create(metadata, content).execute()
        }
        tempFile.delete()
    }

    suspend fun restoreAppSettings(context: Context, account: GoogleSignInAccount): Boolean = withContext(Dispatchers.IO) {
        val drive = driveService(context, account)
        val existingFiles = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("trashed = false and name = 'settings_backup.json'")
            .setFields("files(id)")
            .execute()
            .files

        if (existingFiles.isNullOrEmpty()) {
            return@withContext false
        }

        val fileId = existingFiles[0].id
        val tempFile = File(context.cacheDir, "settings_restore.json")
        try {
            tempFile.outputStream().use { os ->
                drive.files().get(fileId).executeMediaAndDownloadTo(os)
            }

            val jsonStr = tempFile.readText()
            val json = org.json.JSONObject(jsonStr)
            val prefs = AppPreferences(context)

            if (json.has("autoRestart")) prefs.autoRestart = json.getBoolean("autoRestart")
            if (json.has("isFloatingChatEnabled")) prefs.isFloatingChatEnabled = json.getBoolean("isFloatingChatEnabled")
            if (json.has("ramMode")) prefs.ramMode = json.getString("ramMode")
            if (json.has("manualRamMb")) prefs.manualRamMb = json.getInt("manualRamMb")
            if (json.has("appLanguage")) prefs.appLanguage = json.getString("appLanguage")
            if (json.has("isMaxPowerMode")) prefs.isMaxPowerMode = json.getBoolean("isMaxPowerMode")

            if (json.has("soundEnabled")) {
                AppPreferencesStore.setSoundEnabled(context, json.getBoolean("soundEnabled"))
            }
            if (json.has("notificationsEnabled")) {
                AppPreferencesStore.setNotificationsEnabled(context, json.getBoolean("notificationsEnabled"))
            }
            if (json.has("themeMode")) {
                AppPreferencesStore.setDarkModeOverride(context, json.getString("themeMode"))
            }
            if (json.has("relayHost")) {
                AppPreferencesStore.setRelayHost(context, json.getString("relayHost"))
            }
            if (json.has("isFloatingChatEnabled")) {
                AppPreferencesStore.setFloatingChatEnabled(context, json.getBoolean("isFloatingChatEnabled"))
            }
            true
        } finally {
            tempFile.delete()
        }
    }

    suspend fun uploadAppSettingsToFirebase(context: Context, user: FirebaseUser) {
        val prefs = AppPreferences(context)
        val soundEnabled = AppPreferencesStore.isSoundEnabledFlow(context).first()
        val notificationsEnabled = AppPreferencesStore.isNotificationsEnabledFlow(context).first()
        val darkMode = AppPreferencesStore.getDarkModeOverrideFlow(context).first() ?: "SYSTEM"
        val relayHost = AppPreferencesStore.getRelayHostFlow(context).first()

        val payload = hashMapOf(
            "themeMode" to darkMode,
            "soundEnabled" to soundEnabled,
            "notificationsEnabled" to notificationsEnabled,
            "autoRestart" to prefs.autoRestart,
            "isFloatingChatEnabled" to prefs.isFloatingChatEnabled,
            "ramMode" to prefs.ramMode,
            "manualRamMb" to prefs.manualRamMb,
            "relayHost" to relayHost,
            "appLanguage" to prefs.appLanguage,
            "isMaxPowerMode" to prefs.isMaxPowerMode,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        withTimeoutOrNull(10.seconds) {
            FirebaseFirestore.getInstance()
                .collection("user_settings")
                .document(user.uid)
                .set(payload, com.google.firebase.firestore.SetOptions.merge())
                .awaitTask()
        }
    }

    suspend fun restoreAppSettingsFromFirebase(context: Context, user: FirebaseUser): Boolean {
        return runCatching {
            val snapshot = withTimeoutOrNull(10.seconds) {
                FirebaseFirestore.getInstance()
                    .collection("user_settings")
                    .document(user.uid)
                    .get()
                    .awaitTask()
            } ?: return false

            if (!snapshot.exists()) return false

            val prefs = AppPreferences(context)
            if (snapshot.contains("autoRestart")) prefs.autoRestart = snapshot.getBoolean("autoRestart") ?: prefs.autoRestart
            if (snapshot.contains("isFloatingChatEnabled")) {
                val enabled = snapshot.getBoolean("isFloatingChatEnabled") ?: prefs.isFloatingChatEnabled
                prefs.isFloatingChatEnabled = enabled
                AppPreferencesStore.setFloatingChatEnabled(context, enabled)
            }
            if (snapshot.contains("ramMode")) prefs.ramMode = snapshot.getString("ramMode") ?: prefs.ramMode
            if (snapshot.contains("manualRamMb")) prefs.manualRamMb = snapshot.getLong("manualRamMb")?.toInt() ?: prefs.manualRamMb
            if (snapshot.contains("appLanguage")) prefs.appLanguage = snapshot.getString("appLanguage") ?: prefs.appLanguage
            if (snapshot.contains("isMaxPowerMode")) prefs.isMaxPowerMode = snapshot.getBoolean("isMaxPowerMode") ?: prefs.isMaxPowerMode

            if (snapshot.contains("soundEnabled")) {
                AppPreferencesStore.setSoundEnabled(context, snapshot.getBoolean("soundEnabled") ?: true)
            }
            if (snapshot.contains("notificationsEnabled")) {
                AppPreferencesStore.setNotificationsEnabled(context, snapshot.getBoolean("notificationsEnabled") ?: true)
            }
            if (snapshot.contains("themeMode")) {
                AppPreferencesStore.setDarkModeOverride(context, snapshot.getString("themeMode") ?: "SYSTEM")
            }
            if (snapshot.contains("relayHost")) {
                AppPreferencesStore.setRelayHost(context, snapshot.getString("relayHost") ?: "joinmc.link")
            }
            true
        }.getOrDefault(false)
    }

    suspend fun listAllCloudBackups(
        context: Context,
        account: GoogleSignInAccount
    ): List<RemoteDriveBackup> = withContext(Dispatchers.IO) {
        val token = accessToken(context, account)
        val query = "trashed = false and name contains '.zip'"
        val url = buildString {
            append("https://www.googleapis.com/drive/v3/files")
            append("?spaces=")
            append(URLEncoder.encode("appDataFolder", "UTF-8"))
            append("&q=")
            append(URLEncoder.encode(query, "UTF-8"))
            append("&orderBy=")
            append(URLEncoder.encode("modifiedTime desc", "UTF-8"))
            append("&fields=")
            append(URLEncoder.encode("files(id,name,modifiedTime)", "UTF-8"))
        }
        val connection = openConnection(url = url, method = "GET", token = token)
        val body = readResponseBody(connection)
        ensureSuccess(connection, body, "list all cloud backups")
        val files = org.json.JSONObject(body).optJSONArray("files") ?: org.json.JSONArray()
        return@withContext List(files.length()) { index ->
            val item = files.getJSONObject(index)
            RemoteDriveBackup(
                id = item.optString("id"),
                name = item.optString("name"),
                modifiedTime = item.optString("modifiedTime").ifBlank { null }
            )
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}
