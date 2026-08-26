package com.pockethost.app.util

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

    /**
     * Returns the resident set size (RSS) in MB for a given PID by reading /proc/<pid>/status.
     * Returns 0 if the PID is invalid or the file is unavailable.
     */
    private fun readProcRssMb(pid: Long): Int {
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
            android.util.Log.w("RamUtils", "Failed to read VmRSS for pid=$pid: ${e.message}")
        }

        // Fallback: use statm resident pages
        try {
            val statmFile = java.io.File("/proc/$pid/statm")
            if (statmFile.exists()) {
                val content = statmFile.readText().trim()
                val parts = content.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    // Field 1 in statm is resident pages (shared + private)
                    val pages = parts[1].toLongOrNull() ?: 0L
                    return (pages * 4096L / 1024 / 1024).toInt()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("RamUtils", "Failed to read statm for pid=$pid: ${e.message}")
        }
        return 0
    }

    /**
     * Returns the memory (RSS) currently used by the Minecraft server in MB.
     *
     * Strategy:
     *  1. If an external JVM PID is stored (out-of-process launch via ServerLauncher), read that
     *     process's RSS from /proc/<pid>/status — the most accurate measure for
     *     a child JVM we cannot introspect via Runtime.
     *  2. If no external JVM exists (in-process launch inside the :server service process),
     *     find the :server process PID via ActivityManager.getRunningAppProcesses()
     *     and read its VmRSS from /proc. This correctly shows the full server JVM
     *     memory rather than the UI process's own tiny heap.
     *  3. Final fallback: read own process RSS so the bar is never stuck at 0.
     */
    fun getProcessRamMb(context: Context): Int {
        val extPid = com.pockethost.app.server.ServerHostService.getExternalJvmPid(context)
        if (extPid > 0) {
            val serverRss = readProcRssMb(extPid)
            android.util.Log.d("RamUtils", "RAM (external JVM pid=$extPid): ${serverRss}MB")
            return serverRss
        }

        // In-process launch: the server JVM runs inside the :server service process.
        // Runtime.getRuntime() here only reflects the UI process heap, so we must
        // look up the :server process PID and read its /proc/<pid>/status VmRSS.
        val serverPid = findServerProcessPid(context)
        if (serverPid > 0) {
            val serverRss = readProcRssMb(serverPid.toLong())
            android.util.Log.d("RamUtils", "RAM (:server process pid=$serverPid): ${serverRss}MB")
            if (serverRss > 0) return serverRss
        }

        // Last resort: read own process RSS (at least non-zero)
        val selfRss = readProcRssMb(android.os.Process.myPid().toLong())
        android.util.Log.d("RamUtils", "RAM (self process fallback): ${selfRss}MB")
        return selfRss
    }

    /**
     * Returns the PID of the app's :server process, or -1 if not found/running.
     */
    private fun findServerProcessPid(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return -1
        val packageName = context.packageName
        val serverProcessName = "$packageName:server"
        try {
            val processes = am.runningAppProcesses ?: return -1
            for (proc in processes) {
                if (proc.processName == serverProcessName) {
                    return proc.pid
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("RamUtils", "findServerProcessPid failed: ${e.message}")
        }
        return -1
    }

    class DeviceStabilityProfile(
        val forceExternalJvm: Boolean,
        val constrainedHeap: Boolean,
        val maxHeapCapMb: Int,
        val minHeapFloorMb: Int,
        val reason: String?
    )

    fun buildDeviceStabilityProfile(totalRamMb: Int, availableRamMb: Int): DeviceStabilityProfile {
        val manufacturer = android.os.Build.MANUFACTURER.orEmpty().lowercase(java.util.Locale.US)
        val brand = android.os.Build.BRAND.orEmpty().lowercase(java.util.Locale.US)
        val model = android.os.Build.MODEL.orEmpty().lowercase(java.util.Locale.US)
        val device = android.os.Build.DEVICE.orEmpty().lowercase(java.util.Locale.US)
        val product = android.os.Build.PRODUCT.orEmpty().lowercase(java.util.Locale.US)
        val fingerprint = android.os.Build.FINGERPRINT.orEmpty().lowercase(java.util.Locale.US)
        val xiaomiMarkers = listOf(manufacturer, brand, model, device, product, fingerprint)
        val isXiaomiFamily = xiaomiMarkers.any { value ->
            value.contains("xiaomi") || value.contains("redmi") || value.contains("poco")
        }
        val isGalaxyA12Family = manufacturer.contains("samsung") && listOf(model, device, product).any { value ->
            value.contains("a12") || value.contains("sm-a125") || value.contains("sm-a127")
        }
        val isGalaxyM13Family = manufacturer.contains("samsung") && listOf(model, device, product).any { value ->
            value.contains("m13") || value.contains("sm-m135") ||
            value.contains("sm-m136") || value.contains("sm-m137")
        }
        val isXiaomiAndroid14PlusFamily = isXiaomiFamily && android.os.Build.VERSION.SDK_INT >= 34
        val constrainedHeap = isGalaxyA12Family || totalRamMb <= 4096
        val targetHeapCap = when {
            isGalaxyA12Family -> minOf((availableRamMb * 0.52f).toInt(), 896)
            totalRamMb <= 3072 -> minOf((availableRamMb * 0.58f).toInt(), 768)
            totalRamMb <= 4096 -> minOf((availableRamMb * 0.62f).toInt(), 1024)
            totalRamMb <= 6144 -> minOf((availableRamMb * 0.80f).toInt(), 3072)
            else -> minOf((availableRamMb * 0.85f).toInt(), (totalRamMb * 0.75f).toInt(), 4096)
        }
        val minHeapFloor = if (isGalaxyA12Family || totalRamMb <= 3072) 384 else 512
        val reason = when {
            isXiaomiAndroid14PlusFamily -> "Xiaomi/Redmi/POCO Android 14+ device detected. Using external JVM to avoid in-process startup stalls during world preparation."
            isGalaxyM13Family -> "Samsung Galaxy M13 detected. Using external JVM to prevent startup stall on Android 14."
            isGalaxyA12Family -> "Samsung Galaxy A12 low-memory profile active. Using safer heap limits to reduce short crash loops."
            constrainedHeap -> "Low-memory device profile active. Heap is capped to reduce background crash risk."
            else -> null
        }
        return DeviceStabilityProfile(
            forceExternalJvm = isGalaxyM13Family || isXiaomiAndroid14PlusFamily,
            constrainedHeap = constrainedHeap,
            maxHeapCapMb = targetHeapCap.coerceAtLeast(minHeapFloor),
            minHeapFloorMb = minHeapFloor,
            reason = reason
        )
    }

    fun getMaxRamAllocationMb(context: Context, ramMode: String, manualRamMb: Int): Int {
        val totalRam = getTotalRamMb(context)
        val availRam = getAvailableRamMb(context)
        val deviceProfile = buildDeviceStabilityProfile(totalRam, availRam)
        val maxAllowedRam = minOf((totalRam * 0.90).toInt(), deviceProfile.maxHeapCapMb)
            .coerceAtLeast(deviceProfile.minHeapFloorMb)
        val requestedMaxRamMb = when (ramMode) {
            "low" -> when {
                deviceProfile.constrainedHeap && totalRam <= 3500 -> 768
                deviceProfile.constrainedHeap -> 896
                totalRam >= 6000 -> 2048
                totalRam >= 4000 -> 1536
                else -> 1024
            }
            "full" -> maxAllowedRam
            "manual" -> manualRamMb.coerceIn(deviceProfile.minHeapFloorMb, maxAllowedRam)
            else -> 1024
        }
        return requestedMaxRamMb.coerceIn(deviceProfile.minHeapFloorMb, maxAllowedRam)
    }
}
