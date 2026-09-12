package com.pockethost.app.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages the Fabric Carpet mod (gnembon/fabric-carpet) for Fabric servers.
 *
 * Carpet adds the `/player <name> spawn` command which creates real fake-player bots
 * with correct player skins and hitboxes — identical to a real connected player.
 *
 * This manager:
 *  1. Checks whether a Carpet jar compatible with the exact MC version is installed.
 *  2. Removes outdated/incompatible Carpet jars that would cause Fabric Mixin crashes.
 *  3. Provides helpers for Carpet RCON bot commands.
 */
object CarpetModManager {

    /**
     * Sanitizes raw version strings (e.g. "1.21.11" -> "1.21.1", "1.20.1-fabric" -> "1.20.1").
     */
    fun cleanMcVersion(rawVersion: String): String {
        val extracted = Regex("""\b(\d+)\.(\d+)(?:\.(\d+))?\b""").find(rawVersion)
        if (extracted != null) {
            val major = extracted.groupValues[1]
            val minor = extracted.groupValues[2]
            var patch = extracted.groupValues[3]
            if (patch.length >= 2 && patch.all { it == patch[0] }) {
                patch = patch.substring(0, 1)
            }
            return if (patch.isNotBlank()) "$major.$minor.$patch" else "$major.$minor"
        }
        return rawVersion.trim()
    }

    /**
     * Removes any existing Carpet mod jar if it doesn't match [cleanVersion] or if corrupted.
     */
    fun removeIncompatibleCarpetJars(modsDir: File, targetMcVersion: String, onOutput: ((String) -> Unit)? = null) {
        val cleanVer = cleanMcVersion(targetMcVersion)
        modsDir.listFiles()?.filter { file ->
            file.name.contains("carpet", ignoreCase = true) && file.extension == "jar"
        }?.forEach { file ->
            // If the jar name doesn't contain the target MC version substring (e.g. fabric-carpet-1.21.1...), delete it
            if (!file.name.contains(cleanVer)) {
                onOutput?.invoke("[PocketHost] Removing incompatible Carpet mod (${file.name}) for MC $targetMcVersion")
                file.delete()
            }
        }
    }

    /**
     * Removes Carpet and Fabric API jars that do not match [mcVersion].
     *
     * This used to delete every jar whose name contained "carpet" or "fabric-api" regardless of
     * version, which made the problem it was meant to solve unfixable: a player whose mods needed
     * Fabric API could install the correct build and have it silently deleted on the next launch,
     * leaving Fabric Loader to abort with "requires ... fabric-api, which is missing". Only
     * mismatched builds — the ones that actually cause the version-mismatch crashes — are removed.
     *
     * Passing a blank [mcVersion] keeps the old behaviour of removing everything, for callers
     * reacting to a crash where no version is known to be good.
     */
    fun purgeAllCarpetJars(modsDir: File, mcVersion: String = "", onOutput: ((String) -> Unit)? = null) {
        val cleanVer = if (mcVersion.isNotBlank()) cleanMcVersion(mcVersion) else ""
        modsDir.listFiles()?.filter { file ->
            (file.name.contains("carpet", ignoreCase = true) || file.name.contains("fabric-api", ignoreCase = true)) &&
                (file.extension == "jar" || file.extension == "disabled")
        }?.forEach { file ->
            if (cleanVer.isNotBlank() && file.name.contains(cleanVer)) {
                return@forEach
            }
            onOutput?.invoke("[PocketHost] Purging mod jar built for a different game version: ${file.name}")
            file.delete()
        }
    }

    /**
     * Ensures a Carpet mod jar compatible with [mcVersion] is present in [serverDir]/mods/.
     * Downloads it if missing. Safe to call on server start — no-op if already present.
     *
     * @return true if compatible Carpet mod is installed, false otherwise.
     */
    /**
     * Checks if a Carpet mod matching [mcVersion] is present in [serverDir]/mods.
     */
    suspend fun ensureCarpetInstalled(
        serverDir: File,
        mcVersion: String,
        onOutput: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val modsDir = File(serverDir, "mods").also { it.mkdirs() }
        val cleanVer = cleanMcVersion(mcVersion)

        // Remove old/incompatible Carpet jars first
        removeIncompatibleCarpetJars(modsDir, cleanVer, onOutput)

        // Already installed for this clean version?
        if (carpetJarExists(modsDir, cleanVer)) {
            onOutput("[PocketHost] Carpet mod (MC $cleanVer) is active.")
            return@withContext true
        }

        return@withContext false
    }

    /**
     * Returns true if a Carpet mod jar for [mcVersion] is present in [modsDir].
     */
    fun carpetJarExists(modsDir: File, mcVersion: String = ""): Boolean {
        val cleanVer = if (mcVersion.isNotBlank()) cleanMcVersion(mcVersion) else ""
        return modsDir.listFiles()?.any { file ->
            file.name.contains("carpet", ignoreCase = true) &&
                file.extension == "jar" &&
                (cleanVer.isBlank() || file.name.contains(cleanVer))
        } == true
    }

    // -------------------------------------------------------------------------
    // Carpet RCON command helpers
    // -------------------------------------------------------------------------

    fun buildSpawnCommand(name: String, x: Int, y: Int, z: Int, dimension: String = ""): String {
        return if (dimension.isNotBlank()) {
            "player $name spawn at $x $y $z in $dimension"
        } else {
            "player $name spawn at $x $y $z"
        }
    }

    fun buildKillCommand(name: String): String = "player $name kill"

    fun isCarpetPlayerCommandAvailable(rconResponse: String): Boolean {
        val lower = rconResponse.lowercase()
        return !lower.contains("unknown command") &&
            !lower.contains("incorrect argument") &&
            !lower.contains("no such command") &&
            !lower.contains("invalid command")
    }
}
