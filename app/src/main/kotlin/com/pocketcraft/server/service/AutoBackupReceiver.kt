package com.pocketcraft.server.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.util.Log
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.server.ServerHostService
import com.pocketcraft.server.service.ServerFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AutoBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = AppPreferences(context)
        
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (prefs.autoBackupTimeEnabled) {
                AutoBackupScheduler.scheduleDailyBackup(context, prefs.autoBackupTimeHour, prefs.autoBackupTimeMinute)
            }
            return
        }

        if (!prefs.autoBackupTimeEnabled) return

        // Re-schedule alarm for the next day
        AutoBackupScheduler.scheduleDailyBackup(context, prefs.autoBackupTimeHour, prefs.autoBackupTimeMinute)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isServiceRunning = ServerHostService.isServiceRunning(context)
                if (!isServiceRunning) {
                    val activeWorld = prefs.selectedWorldPath ?: "world"
                    val backupsDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "PocketCraftWorldBackups"
                    )
                    val currentActiveWorld = activeWorld.ifBlank { "world" }
                    val serverDir = ServerFileManager.getServerDir(context, currentActiveWorld)
                    
                    val backupName = "world-auto-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".zip"
                    val destDir = File(backupsDir, currentActiveWorld).also { it.mkdirs() }
                    val backupFile = File(destDir, backupName)

                    ZipOutputStream(FileOutputStream(backupFile)).use { zos ->
                        val pathsToBackup = mutableListOf<File>()
                        val worldFolder = File(serverDir, currentActiveWorld)
                        if (worldFolder.exists()) pathsToBackup.add(worldFolder)

                        val netherFolder = File(serverDir, "${currentActiveWorld}_nether")
                        if (netherFolder.exists()) pathsToBackup.add(netherFolder)

                        val endFolder = File(serverDir, "${currentActiveWorld}_the_end")
                        if (endFolder.exists()) pathsToBackup.add(endFolder)

                        for (baseDir in pathsToBackup) {
                            baseDir.walkTopDown().filter { it.isFile }.forEach { file ->
                                if (!file.name.endsWith(".lock")) {
                                    val relativePath = file.relativeTo(serverDir).path.replace('\\', '/')
                                    try {
                                        zos.putNextEntry(ZipEntry(relativePath))
                                        file.inputStream().use { it.copyTo(zos) }
                                        zos.closeEntry()
                                    } catch (e: Exception) {
                                        Log.w("AutoBackupReceiver", "Skipped file: ${file.name} (${e.message})")
                                    }
                                }
                            }
                        }
                    }
                    Log.i("AutoBackupReceiver", "Daily auto-backup completed: ${backupFile.absolutePath}")
                } else {
                    Log.i("AutoBackupReceiver", "Skipping auto-backup: Server service is currently running.")
                }
            } catch (e: Exception) {
                Log.e("AutoBackupReceiver", "Failed daily auto-backup", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

object AutoBackupScheduler {
    fun scheduleDailyBackup(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AutoBackupReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9988,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
        Log.i("AutoBackupScheduler", "Scheduled daily backup alarm for $hour:$minute")
    }

    fun cancelDailyBackup(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AutoBackupReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9988,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.i("AutoBackupScheduler", "Cancelled daily backup alarm")
        }
    }
}
