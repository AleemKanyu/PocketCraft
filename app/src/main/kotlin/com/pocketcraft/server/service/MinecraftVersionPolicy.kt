package com.pocketcraft.server.service

import android.content.Context
import com.pocketcraft.server.data.model.ServerType
import java.io.File

/**
 * Minecraft's 26.x release train is not supported yet — the bundled JRE does not include Java 25.
 */
object MinecraftVersionPolicy {

    fun isReleaseTrain26(version: String): Boolean {
        val trimmed = version.trim()
        return trimmed.startsWith("26.") || trimmed == "26"
    }

    fun filterInstallableVersions(versions: Collection<String>): List<String> =
        versions.toList()

    fun listInstalledReleaseTrain26Versions(context: Context): List<String> {
        return emptyList()
    }

    fun shouldShowReleaseTrain26Warning(activeVersion: String?): Boolean = false
}
