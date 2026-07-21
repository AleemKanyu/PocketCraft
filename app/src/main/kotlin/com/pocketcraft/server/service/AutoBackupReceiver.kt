package com.pocketcraft.server.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.server.ServerHostService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * Receives the daily scheduled alarm and starts [BackupForegroundService].
 * No heavy work is done here — all backup logic lives in the service,
 * which has no BroadcastReceiver timeout.
 */
class AutoBackupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = AppPreferences(context)

        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            if (prefs.autoBackupTimeEnabled) {
                AutoBackupScheduler.scheduleDailyBackup(
                    context, prefs.autoBackupTimeHour, prefs.autoBackupTimeMinute
                )
            }
            return
        }

        if (!prefs.autoBackupTimeEnabled) return

        // Re-schedule for tomorrow
        AutoBackupScheduler.scheduleDailyBackup(
            context, prefs.autoBackupTimeHour, prefs.autoBackupTimeMinute
        )

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isServerRunning = ServerHostService.isServiceRunning(context)

                if (isServerRunning) {
                    if (prefs.autoBackupOnStop) {
                        Log.i(TAG, "Server running + autoBackupOnStop enabled → skipping (duplicate avoided).")
                    } else {
                        Log.i(TAG, "Server running → queueing backup for after server stops.")
                        prefs.pendingAutoBackup = true
                    }
                } else {
                    prefs.pendingAutoBackup = false
                    Log.i(TAG, "Server off → launching BackupForegroundService.")
                    BackupForegroundService.start(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in AutoBackupReceiver", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AutoBackupReceiver"

        /**
         * Called from ServerStateHolder when the server stops and a pending
         * daily backup was queued.
         */
        fun startPendingBackup(context: Context) {
            AppPreferences(context).pendingAutoBackup = false
            BackupForegroundService.start(context)
        }
    }
}

// ── Scheduler ────────────────────────────────────────────────────────────────

object AutoBackupScheduler {
    fun scheduleDailyBackup(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AutoBackupReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 9988, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pi)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pi)
            }
            Log.i("AutoBackupScheduler", "Daily backup alarm set for $hour:$minute")
        } catch (e: Exception) {
            Log.e("AutoBackupScheduler", "Failed to schedule daily backup alarm: ${e.message}")
        }
    }

    fun cancelDailyBackup(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AutoBackupReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 9988, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
            Log.i("AutoBackupScheduler", "Daily backup alarm cancelled")
        }
    }
}
