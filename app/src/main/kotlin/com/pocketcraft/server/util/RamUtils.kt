package com.pocketcraft.server.util

import android.app.ActivityManager
import android.content.Context

object RamUtils {
    fun getTotalRamMb(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.totalMem / 1024 / 1024).toInt()
    }

    fun getUsedRamMb(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalMb = (memInfo.totalMem / 1024 / 1024).toInt()
        val availMb = (memInfo.availMem / 1024 / 1024).toInt()
        return totalMb - availMb
    }

    fun getAvailableRamMb(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.availMem / 1024 / 1024).toInt()
    }

    fun getProcessRamMb(context: Context): Int {
        val pid = com.pocketcraft.server.server.ServerHostService.getExternalJvmPid(context)
        if (pid <= 0) return 0
        try {
            val statusFile = java.io.File("/proc/$pid/status")
            if (statusFile.exists()) {
                statusFile.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (line.startsWith("VmRSS:", ignoreCase = true)) {
                            val parts = line.split(Regex("\\s+"))
                            if (parts.size >= 2) {
                                val kb = parts[1].toLongOrNull() ?: 0L
                                return (kb / 1024).toInt()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("RamUtils", "Failed to read VmRSS from status: ${e.message}")
        }
        
        // Fallback to statm
        try {
            val statmFile = java.io.File("/proc/$pid/statm")
            if (statmFile.exists()) {
                val content = statmFile.readText().trim()
                val parts = content.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val pages = parts[1].toLongOrNull() ?: 0L
                    return (pages * 4096 / 1024 / 1024).toInt()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("RamUtils", "Failed to read resident memory from statm: ${e.message}")
        }
        
        return 0
    }
}
