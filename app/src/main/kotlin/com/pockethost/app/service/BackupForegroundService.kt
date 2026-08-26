package com.pockethost.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pockethost.app.data.preferences.AppPreferences
import com.pockethost.app.data.preferences.AppPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.concurrent.CancellationException

object BackupProgressTracker {
    enum class State { IDLE, RUNNING, COMPLETED, FAILED }

    val state = MutableStateFlow(State.IDLE)
    val progress = MutableStateFlow(0)
    val statusText = MutableStateFlow("")
}

/**
 * Foreground service that performs the automatic backup with no time limit.
 * The BroadcastReceiver simply starts this service; all heavy work happens here.
 *
 * Uses DATA_SYNC foreground service type so Android does not kill it.
 */
class BackupForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private var tempFileToDelete: File? = null

    companion object {
        const val CHANNEL_ID = "pocketcraft_auto_backups"
        const val NOTIFICATION_ID = 9977
        const val ACTION_START_BACKUP = "com.pockethost.app.ACTION_START_BACKUP"
        const val ACTION_STOP_BACKUP = "com.pockethost.app.ACTION_STOP_BACKUP"

        @Volatile
        var isCancelled = false
            private set

        /** Convenience helper called from AutoBackupReceiver / ServerStateHolder. */
        fun start(context: Context) {
            isCancelled = false
            BackupProgressTracker.state.value = BackupProgressTracker.State.IDLE
            BackupProgressTracker.progress.value = 0
            BackupProgressTracker.statusText.value = "Starting..."
            val intent = Intent(context, BackupForegroundService::class.java).apply {
                action = ACTION_START_BACKUP
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("BackupFgService", "Failed to start BackupForegroundService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BackupForegroundService::class.java).apply {
                action = ACTION_STOP_BACKUP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e("BackupFgService", "Failed to stop BackupForegroundService: ${e.message}")
            }
        }

        private fun sanitizeWorldName(name: String): String =
            name.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "world" }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_BACKUP) {
            Log.i("BackupFgService", "Stop backup requested by user")
            isCancelled = true
            BackupProgressTracker.state.value = BackupProgressTracker.State.FAILED
            BackupProgressTracker.statusText.value = "Backup stopped by user."
            notificationManager.cancel(NOTIFICATION_ID)
            tempFileToDelete?.delete()
            stopSelf()
            return START_NOT_STICKY
        }

        if (action != ACTION_START_BACKUP) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        isCancelled = false
        // Promote to foreground immediately so Android does not kill us.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, buildNotification("Starting backup…", 0, true), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification("Starting backup…", 0, true))
            }
        } catch (e: Exception) {
            Log.e("BackupFgService", "startForeground failed: ${e.message}")
        }

        scope.launch {
            try {
                BackupProgressTracker.state.value = BackupProgressTracker.State.RUNNING
                BackupProgressTracker.progress.value = 0
                BackupProgressTracker.statusText.value = "Preparing backup..."

                val worldName = runBlocking {
                    AppPreferencesStore.getSelectedWorldFlow(applicationContext).first()
                }.ifBlank { "world" }

                performBackup(worldName)
            } catch (e: CancellationException) {
                Log.i("BackupFgService", "Backup job cancelled")
                BackupProgressTracker.state.value = BackupProgressTracker.State.FAILED
                BackupProgressTracker.statusText.value = "Backup stopped."
                showDoneNotification(success = false, detail = "Backup stopped by user.")
            } catch (e: Exception) {
                Log.e("BackupFgService", "Backup failed", e)
                BackupProgressTracker.state.value = BackupProgressTracker.State.FAILED
                BackupProgressTracker.statusText.value = "Backup failed: ${e.message}"
                showDoneNotification(success = false, detail = e.message ?: e.javaClass.simpleName)
            } finally {
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ── Core backup logic ────────────────────────────────────────────────────

    private fun performBackup(worldName: String) {
        val serverDir = ServerFileManager.getServerDir(applicationContext, worldName)
        if (!serverDir.exists()) {
            Log.w("BackupFgService", "Server dir missing: ${serverDir.absolutePath}")
            BackupProgressTracker.state.value = BackupProgressTracker.State.FAILED
            BackupProgressTracker.statusText.value = "Server directory not found"
            showDoneNotification(success = false, detail = "Server directory not found")
            return
        }

        val backupExcludeDirs = setOf(
            "jre", "jre-21", "jre-runtime", "jre17", "jre21", "jre25",
            "libraries", "bundler", "versions",
            "binaries", "cache", "logs",
            "crash-reports", "world_plugin_profiles"
        )

        // Collect all entries (mirrors ServerStateHolder.collectBackupEntries)
        data class Entry(val file: File, val relativePath: String, val isDir: Boolean)
        val entries = mutableListOf<Entry>()

        fun collect(f: File) {
            if (isCancelled) throw CancellationException()
            val rel = f.relativeTo(serverDir).invariantSeparatorsPath
            if (f.isDirectory) {
                entries += Entry(f, "$rel/", true)
                f.listFiles()?.forEach { collect(it) }
            } else if (f.isFile) {
                entries += Entry(f, rel, false)
            }
        }
        serverDir.listFiles()?.forEach { child ->
            if (isCancelled) throw CancellationException()
            if (child.name !in backupExcludeDirs) collect(child)
        }

        val fileEntries = entries.filter { !it.isDir }
        val total = fileEntries.size.coerceAtLeast(1)
        var processed = 0
        var lastNotifiedPct = -1

        // Write zip to internal cache (no permissions needed, no size limit)
        val tempFile = File(applicationContext.cacheDir,
            "backup-${System.currentTimeMillis()}.zip")
        tempFileToDelete = tempFile

        notify("Backing up server files…", 0)

        ZipOutputStream(BufferedOutputStream(FileOutputStream(tempFile), 128 * 1024)).use { zos ->
            entries.forEach { entry ->
                if (isCancelled) throw CancellationException()
                try {
                    if (entry.isDir) {
                        zos.putNextEntry(ZipEntry(entry.relativePath))
                        zos.closeEntry()
                    } else {
                        if (entry.file.exists() && !entry.file.name.endsWith(".lock")) {
                            zos.putNextEntry(ZipEntry(entry.relativePath))
                            entry.file.inputStream().use { it.copyTo(zos, bufferSize = 64 * 1024) }
                            zos.closeEntry()
                        }
                        processed++
                        val pct = (processed * 95) / total   // leave last 5% for the copy step
                        if (pct != lastNotifiedPct && (pct % 2 == 0 || processed == total)) {
                            lastNotifiedPct = pct
                            notify("Backing up server files… ($pct%)", pct)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w("BackupFgService", "Skipped ${entry.relativePath}: ${e.message}")
                }
            }
        }

        if (isCancelled) throw CancellationException()

        notify("Saving to Downloads…", 96)

        val backupName = "world-auto-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".zip"
        saveToPersistentBackups(tempFile, backupName, worldName)
        tempFile.delete()
        tempFileToDelete = null

        // Clear the pending flag if set
        AppPreferences(applicationContext).pendingAutoBackup = false

        Log.i("BackupFgService", "Auto-backup complete: $backupName (${fileEntries.size} files)")
        BackupProgressTracker.state.value = BackupProgressTracker.State.COMPLETED
        BackupProgressTracker.progress.value = 100
        BackupProgressTracker.statusText.value = "Backup completed successfully!"
        showDoneNotification(success = true, detail = "Saved to Downloads/PocketCraftWorldBackups")
    }

    private fun saveToPersistentBackups(source: File, displayName: String, worldName: String) {
        val safeWorld = sanitizeWorldName(worldName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = applicationContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/PocketCraftWorldBackups/$safeWorld/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore insert failed")

            resolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { input ->
                    val buf = ByteArray(256 * 1024)
                    val total = source.length().coerceAtLeast(1L)
                    var copied = 0L
                    var lastPct = -1
                    var read = input.read(buf)
                    while (read != -1) {
                        if (isCancelled) {
                            resolver.delete(uri, null, null)
                            throw CancellationException()
                        }
                        out.write(buf, 0, read)
                        copied += read
                        val pct = 96 + ((copied * 4) / total).toInt().coerceIn(0, 4)
                        if (pct != lastPct) {
                            lastPct = pct
                            notify("Saving to Downloads… ($pct%)", pct)
                        }
                        read = input.read(buf)
                    }
                }
            } ?: throw IllegalStateException("Could not open MediaStore output stream")

            if (isCancelled) {
                resolver.delete(uri, null, null)
                throw CancellationException()
            }

            resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            return
        }

        // Legacy (< Android 10)
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "PocketCraftWorldBackups/$safeWorld"
        ).also { it.mkdirs() }
        source.copyTo(File(dir, displayName), overwrite = true)
    }

    // ── Notification helpers ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Automatic Backups",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows progress of automatic server backups" }
            notificationManager.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String, progress: Int, indeterminate: Boolean = false): android.app.Notification {
        val stopIntent = Intent(this, BackupForegroundService::class.java).apply {
            action = ACTION_STOP_BACKUP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1001,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.pockethost.app.R.drawable.ic_notification_small)
            .setContentTitle("Automatic Backup")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun notify(text: String, progress: Int) {
        BackupProgressTracker.progress.value = progress
        BackupProgressTracker.statusText.value = text
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text, progress))
    }

    private fun showDoneNotification(success: Boolean, detail: String) {
        val done = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.pockethost.app.R.drawable.ic_notification_small)
            .setContentTitle(if (success) "Backup Complete ✓" else "Backup Failed ✗")
            .setContentText(detail)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(NOTIFICATION_ID, done)
    }
}
