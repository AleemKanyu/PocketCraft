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
 *  1. Checks whether a Carpet jar is already present in the server's `mods/` folder.
 *  2. If not, queries the Modrinth API to resolve the correct jar for the running
 *     Minecraft version and downloads it automatically.
 *  3. Provides helpers to detect whether Carpet player commands are available via RCON.
 */
object CarpetModManager {

    private const val MODRINTH_PROJECT_ID = "carpet"
    private const val CARPET_TAG = "pocketcraft_afk_carpet"
    private const val USER_AGENT = "PocketCraft/1.0 (android)"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Ensures the Carpet mod jar is present in [serverDir]/mods/ for the given
     * [mcVersion].  Downloads it if missing.  Safe to call on every server start —
     * it's a no-op when the jar is already there.
     *
     * @return true if Carpet is (or was just) installed, false if download failed.
     */
    suspend fun ensureCarpetInstalled(
        serverDir: File,
        mcVersion: String,
        onOutput: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val modsDir = File(serverDir, "mods").also { it.mkdirs() }

        // Already installed?
        if (carpetJarExists(modsDir)) {
            onOutput("[PocketCraft] Carpet mod already installed — skipping download.")
            return@withContext true
        }

        onOutput("[PocketCraft] Carpet mod not found. Fetching compatible version for MC $mcVersion from Modrinth…")

        val downloadUrl = resolveCarpetDownloadUrl(mcVersion)
        if (downloadUrl == null) {
            onOutput("[PocketCraft] ⚠ Could not resolve Carpet download URL for MC $mcVersion. AFK bots will fall back to zombie placeholders.")
            return@withContext false
        }

        val fileName = downloadUrl.substringAfterLast('/')
        val targetFile = File(modsDir, fileName)

        onOutput("[PocketCraft] Downloading Carpet mod: $fileName …")
        val success = downloadFile(downloadUrl, targetFile, onOutput)
        if (success) {
            onOutput("[PocketCraft] ✅ Carpet mod installed: ${targetFile.name}")
        } else {
            onOutput("[PocketCraft] ⚠ Carpet mod download failed. AFK bots will use zombie placeholders.")
        }
        success
    }

    /**
     * Returns true if a Carpet mod jar is present in the mods directory.
     */
    fun carpetJarExists(modsDir: File): Boolean =
        modsDir.listFiles()?.any { it.name.contains("carpet", ignoreCase = true) && it.extension == "jar" } == true

    /**
     * Returns true if a Carpet mod jar is installed for the given server directory.
     */
    fun isCarpetInstalled(serverDir: File): Boolean =
        carpetJarExists(File(serverDir, "mods"))

    // -------------------------------------------------------------------------
    // Carpet RCON command helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the RCON command to spawn a Carpet fake-player bot at specific coordinates.
     *
     * Carpet syntax (1.17+):
     *   /player <name> spawn [at <x> <y> <z>] [in <dimension>] [survival]
     */
    fun buildSpawnCommand(name: String, x: Int, y: Int, z: Int, dimension: String = "minecraft:overworld"): String =
        "player $name spawn at $x $y $z in $dimension survival"

    /**
     * Builds the RCON command to despawn a Carpet fake-player bot.
     */
    fun buildKillCommand(name: String): String = "player $name kill"

    /**
     * Returns true if [rconResponse] indicates the `/player` command is available
     * (i.e. Carpet is loaded and running).
     */
    fun isCarpetPlayerCommandAvailable(rconResponse: String): Boolean {
        val lower = rconResponse.lowercase()
        // Carpet returns a specific error when bot doesn't exist, which still means it's loaded
        return !lower.contains("unknown command") &&
            !lower.contains("incorrect argument") &&
            !lower.contains("no such command") &&
            !lower.contains("invalid command")
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Queries Modrinth's API to find the latest Carpet release compatible with
     * [mcVersion] and returns its primary download URL, or null on failure.
     */
    private fun resolveCarpetDownloadUrl(mcVersion: String): String? {
        return runCatching {
            val apiUrl = "https://api.modrinth.com/v2/project/$MODRINTH_PROJECT_ID/version" +
                "?game_versions=%5B%22$mcVersion%22%5D&loaders=%5B%22fabric%22%5D"
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
            }
            if (conn.responseCode != 200) return@runCatching null
            val json = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            if (json.length() == 0) return@runCatching null

            // Take the first (latest) release
            val latest = json.getJSONObject(0)
            val files = latest.getJSONArray("files")
            // Prefer the primary file
            for (i in 0 until files.length()) {
                val file = files.getJSONObject(i)
                if (file.optBoolean("primary", false)) {
                    return@runCatching file.getString("url")
                }
            }
            // Fallback: first file
            files.getJSONObject(0).getString("url")
        }.getOrNull()
    }

    /**
     * Downloads [url] to [dest], streaming in 32KB chunks.
     * Returns true on success.
     */
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
