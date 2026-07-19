package com.pocketcraft.server.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages the Fabric Carpet mod (gnembon/fabric-carpet) for Fabric servers.
 *
 * Carpet adds the `/player <name> spawn` command which creates real fake-player bots
 * with correct player skins and hitboxes — identical to a real connected player.
 *
 * This manager:
 *  1. Checks whether a Carpet jar compatible with the exact MC version is installed.
 *  2. Removes outdated/incompatible Carpet jars that would cause Fabric Mixin crashes.
 *  3. Queries the Modrinth API for exact game-version matches and downloads it automatically.
 *  4. Provides helpers for Carpet RCON bot commands.
 */
object CarpetModManager {

    private const val MODRINTH_PROJECT_ID = "carpet"
    private const val USER_AGENT = "PocketCraft/1.0 (android)"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000

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

    private fun resolveModrinthDownloadUrl(projectId: String, cleanMcVersion: String): String? {
        val versionsToTry = listOfNotNull(
            cleanMcVersion,
            cleanMcVersion.substringBeforeLast('.', missingDelimiterValue = cleanMcVersion)
                .takeIf { it != cleanMcVersion }
        )

        for (targetVer in versionsToTry) {
            val url = runCatching {
                val apiUrl = "https://api.modrinth.com/v2/project/$projectId/version" +
                    "?game_versions=%5B%22$targetVer%22%5D&loaders=%5B%22fabric%22%5D"
                val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", USER_AGENT)
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    requestMethod = "GET"
                }
                if (conn.responseCode != 200) return@runCatching null
                val json = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                if (json.length() == 0) return@runCatching null

                for (i in 0 until json.length()) {
                    val versionObj = json.getJSONObject(i)
                    val gameVersions = versionObj.optJSONArray("game_versions") ?: continue
                    var versionMatched = false
                    for (g in 0 until gameVersions.length()) {
                        if (gameVersions.getString(g) == targetVer) {
                            versionMatched = true
                            break
                        }
                    }
                    if (versionMatched) {
                        val files = versionObj.optJSONArray("files") ?: continue
                        for (f in 0 until files.length()) {
                            val file = files.getJSONObject(f)
                            if (file.optBoolean("primary", false)) {
                                return@runCatching file.getString("url")
                            }
                        }
                        if (files.length() > 0) {
                            return@runCatching files.getJSONObject(0).getString("url")
                        }
                    }
                }
                null
            }.getOrNull()

            if (url != null) return url
        }
        return null
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
                onOutput?.invoke("[PocketCraft] Removing incompatible Carpet mod (${file.name}) for MC $targetMcVersion")
                file.delete()
            }
        }
    }

    /**
     * Removes all Carpet mod jars from modsDir to recover from crash.
     */
    fun purgeAllCarpetJars(modsDir: File, onOutput: ((String) -> Unit)? = null) {
        modsDir.listFiles()?.filter { file ->
            file.name.contains("carpet", ignoreCase = true) && (file.extension == "jar" || file.extension == "disabled")
        }?.forEach { file ->
            onOutput?.invoke("[PocketCraft] Purging Carpet mod jar: ${file.name}")
            file.delete()
        }
    }

    /**
     * Ensures a Carpet mod jar compatible with [mcVersion] is present in [serverDir]/mods/.
     * Downloads it if missing. Safe to call on server start — no-op if already present.
     *
     * @return true if compatible Carpet mod is installed, false otherwise.
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

        // Ensure Fabric API is installed alongside Carpet mod (Carpet depends on Fabric API)
        ensureFabricApiInstalled(modsDir, cleanVer, onOutput)

        // Already installed for this clean version?
        if (carpetJarExists(modsDir, cleanVer)) {
            onOutput("[PocketCraft] Carpet mod (MC $cleanVer) is already installed.")
            return@withContext true
        }

        onOutput("[PocketCraft] Checking Modrinth for Carpet mod compatible with MC $cleanVer…")

        val downloadUrl = resolveModrinthDownloadUrl("carpet", cleanVer)
        if (downloadUrl == null) {
            onOutput("[PocketCraft] Note: No exact Carpet mod match found for MC $cleanVer on Modrinth. AFK bots will use fallback zombies.")
            return@withContext false
        }

        val fileName = downloadUrl.substringAfterLast('/')
        val targetFile = File(modsDir, fileName)

        onOutput("[PocketCraft] Downloading Carpet mod for MC $cleanVer: $fileName …")
        val success = downloadFile(downloadUrl, targetFile, onOutput)
        if (success) {
            onOutput("[PocketCraft] ✅ Carpet mod installed: ${targetFile.name}")
        } else {
            onOutput("[PocketCraft] ⚠ Carpet mod download failed. AFK bots will use fallback zombies.")
        }
        success
    }

    /**
     * Downloads Fabric API if missing from [modsDir] for [cleanMcVersion].
     */
    private fun ensureFabricApiInstalled(modsDir: File, cleanMcVersion: String, onOutput: (String) -> Unit) {
        val hasFabricApi = modsDir.listFiles()?.any {
            it.name.contains("fabric-api", ignoreCase = true) && it.extension == "jar"
        } == true
        if (hasFabricApi) return

        onOutput("[PocketCraft] Checking Modrinth for Fabric API for MC $cleanMcVersion…")
        val apiUrlUrl = resolveModrinthDownloadUrl("fabric-api", cleanMcVersion) ?: return
        val fileName = apiUrlUrl.substringAfterLast('/')
        val targetFile = File(modsDir, fileName)
        onOutput("[PocketCraft] Downloading Fabric API for MC $cleanMcVersion: $fileName …")
        if (downloadFile(apiUrlUrl, targetFile, onOutput)) {
            onOutput("[PocketCraft] ✅ Fabric API installed: ${targetFile.name}")
        }
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

    fun buildSpawnCommand(name: String, x: Int, y: Int, z: Int, dimension: String = "minecraft:overworld"): String =
        "player $name spawn at $x $y $z in $dimension survival"

    fun buildKillCommand(name: String): String = "player $name kill"

    fun isCarpetPlayerCommandAvailable(rconResponse: String): Boolean {
        val lower = rconResponse.lowercase()
        return !lower.contains("unknown command") &&
            !lower.contains("incorrect argument") &&
            !lower.contains("no such command") &&
            !lower.contains("invalid command")
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun resolveModrinthDownloadUrl(projectId: String, cleanMcVersion: String): String? {
        return runCatching {
            val apiUrl = "https://api.modrinth.com/v2/project/$projectId/version" +
                "?game_versions=%5B%22$cleanMcVersion%22%5D&loaders=%5B%22fabric%22%5D"
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
            }
            if (conn.responseCode != 200) return@runCatching null
            val json = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            if (json.length() == 0) return@runCatching null

            // Strict version matching: find a release that explicitly lists cleanMcVersion
            for (i in 0 until json.length()) {
                val versionObj = json.getJSONObject(i)
                val gameVersions = versionObj.optJSONArray("game_versions") ?: continue
                var versionMatched = false
                for (g in 0 until gameVersions.length()) {
                    if (gameVersions.getString(g) == cleanMcVersion) {
                        versionMatched = true
                        break
                    }
                }
                if (versionMatched) {
                    val files = versionObj.optJSONArray("files") ?: continue
                    for (f in 0 until files.length()) {
                        val file = files.getJSONObject(f)
                        if (file.optBoolean("primary", false)) {
                            return@runCatching file.getString("url")
                        }
                    }
                    if (files.length() > 0) {
                        return@runCatching files.getJSONObject(0).getString("url")
                    }
                }
            }
            null
        }.getOrNull()
    }

    private fun downloadFile(url: String, dest: File, onOutput: (String) -> Unit): Boolean {
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) {
                onOutput("[PocketCraft] Carpet download HTTP ${conn.responseCode}: $url")
                return@runCatching false
            }
            val tmpFile = File(dest.parent, dest.name + ".tmp")
            conn.inputStream.use { input ->
                tmpFile.outputStream().use { output ->
                    val buf = ByteArray(32 * 1024)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                    }
                }
            }
            tmpFile.renameTo(dest)
            true
        }.getOrElse { e ->
            onOutput("[PocketCraft] Carpet download error: ${e.message}")
            false
        }
    }
}
