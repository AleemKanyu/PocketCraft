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
        versions.filterNot { isReleaseTrain26(it) }

    fun listInstalledReleaseTrain26Versions(context: Context): List<String> {
        val binariesDir = File(context.filesDir, "servers/binaries")
        if (!binariesDir.isDirectory) return emptyList()

        return binariesDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory && isReleaseTrain26(it.name) }
            .filter { versionDir ->
                ServerType.entries.any { type ->
                    if (!type.supportsVersionSelect || type == ServerType.MODPACK) return@any false
                    val jar = File(versionDir, "${type.name.lowercase()}-${versionDir.name}.jar")
                    val minBytes = if (type == ServerType.FABRIC) 10_000L else 1_000_000L
                    jar.isFile && jar.length() > minBytes
                }
            }
            .map { it.name }
            .distinct()
            .sortedDescending()
            .toList()
    }

    fun shouldShowReleaseTrain26Warning(activeVersion: String?): Boolean =
        !activeVersion.isNullOrBlank() && isReleaseTrain26(activeVersion)
}
