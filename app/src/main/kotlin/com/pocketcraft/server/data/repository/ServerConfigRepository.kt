package com.pocketcraft.server.data.repository

import android.content.Context
import android.os.Build
import com.pocketcraft.server.data.model.PlayerInfo
import com.pocketcraft.server.data.model.ServerConfig
import com.pocketcraft.server.data.model.ServerProfileSummary
import com.pocketcraft.server.data.model.WorldDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.pocketcraft.server.setup.JreExtractor
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.service.ServerFileManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes server.properties and spigot.yml.
 * All file I/O is done inside [Context.getFilesDir] — no root required.
 */
@Singleton
class ServerConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val serverDir: File 
        get() {
            val versionId = runBlocking { AppPreferencesStore.getSelectedVersionFlow(context).first() }
            return ServerFileManager.getServerDir(context, versionId)
        }
    private val serversDir: File get() = File(context.filesDir, "servers")
    private val activeServerFile: File get() = File(serverDir, ".active_server")
    private val propertiesFile: File get() = File(serverDir, "server.properties")
    private val spigotFile: File get() = File(serverDir, "spigot.yml")
    private val setupMarkerFile: File get() = File(context.filesDir, ".setup_done")

    suspend fun loadConfig(): ServerConfig = withContext(Dispatchers.IO) {
        val activeServer = getActiveServerName()
        val file = serverFile(activeServer)
        when {
            file.exists() -> parseConfig(file)
            propertiesFile.exists() -> parseConfig(propertiesFile).also {
                serversDir.mkdirs()
                writeConfigFile(file, it)
            }
            else -> ServerConfig()
        }
    }

    suspend fun saveConfig(config: ServerConfig) = withContext(Dispatchers.IO) {
        val activeServer = getActiveServerName()
        serversDir.mkdirs()
        writeConfigFile(propertiesFile, config)
        writeConfigFile(serverFile(activeServer), config)
        writeSpigotYml(config)
    }

    suspend fun loadWorldDetails(): WorldDetails = withContext(Dispatchers.IO) {
        val config = loadConfig()
        val (playtimeTicks, statsPlayers) = readTotalPlaytime(config.worldName)
        WorldDetails(
            worldName = config.worldName,
            worldSeed = config.worldSeed,
            serverVersion = detectServerVersion(),
            totalPlaytimeTicks = playtimeTicks,
            playersWithStats = statsPlayers
        )
    }

    suspend fun listWorldNames(): List<String> = withContext(Dispatchers.IO) {
        val currentWorld = loadConfig().worldName
        val discovered = serverDir.listFiles()
            ?.filter { it.isDirectory }
            ?.filter { dir ->
                dir.name !in setOf("jre", "jre-21", "jre-runtime", "logs", "plugins", "cache", "config", "libraries")
            }
            ?.filter { dir ->
                File(dir, "level.dat").exists() ||
                    File(dir, "region").isDirectory ||
                    File(dir, "stats").isDirectory ||
                    File(dir, "playerdata").isDirectory
            }
            ?.map { it.name }
            .orEmpty()
        (discovered + currentWorld).distinct().sorted()
    }

    suspend fun setWorldName(worldName: String) = withContext(Dispatchers.IO) {
        val clean = worldName.trim()
        if (clean.isEmpty()) return@withContext
        val current = loadConfig()
        saveConfig(current.copy(worldName = clean))
    }

    suspend fun listKnownPlayers(worldName: String): List<PlayerInfo> = withContext(Dispatchers.IO) {
        val worldDir = File(serverDir, worldName)
        val statsDir = File(worldDir, "stats")
        val playerdataDir = File(worldDir, "playerdata")

        val worldUuids = linkedSetOf<String>()
        statsDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.mapTo(worldUuids) { it.nameWithoutExtension }
        playerdataDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("dat", ignoreCase = true) }
            ?.mapTo(worldUuids) { it.nameWithoutExtension }

        val cachedNames = loadUserCache()
        worldUuids.map { uuid ->
            val name = cachedNames[uuid] ?: uuid.take(8)
            PlayerInfo(name = name, uuid = uuid)
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun listServers(): List<String> = withContext(Dispatchers.IO) {
        val active = getActiveServerName()
        ensureServerFileExists(active)
        val discovered = serversDir.listFiles()
            ?.filter { it.isFile && it.extension == "properties" }
            ?.map { it.nameWithoutExtension }
            .orEmpty()
        (discovered + active).distinct().sorted()
    }

    suspend fun getActiveServerName(): String = withContext(Dispatchers.IO) {
        val active = if (activeServerFile.exists()) activeServerFile.readText().trim() else ""
        sanitizeServerName(active).ifEmpty { "default" }
    }

    suspend fun activateServer(serverName: String) = withContext(Dispatchers.IO) {
        val clean = sanitizeServerName(serverName)
        if (clean.isEmpty()) return@withContext
        activeServerFile.writeText(clean)
        ensureServerFileExists(clean)
        val config = parseConfig(serverFile(clean))
        writeConfigFile(propertiesFile, config)
        writeSpigotYml(config)
    }

    suspend fun addServer(serverName: String, worldName: String? = null): Boolean = withContext(Dispatchers.IO) {
        val clean = sanitizeServerName(serverName)
        if (clean.isEmpty()) return@withContext false
        val target = serverFile(clean)
        if (target.exists()) return@withContext false

        val fallbackWorld = if (clean == "default") "world" else "world_${clean.replace('-', '_')}"
        val chosenWorld = worldName?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackWorld
        val base = if (propertiesFile.exists()) parseConfig(propertiesFile) else ServerConfig()
        val config = base.copy(worldName = chosenWorld)
        writeConfigFile(target, config)
        true
    }

    suspend fun listServerProfileSummaries(): List<ServerProfileSummary> = withContext(Dispatchers.IO) {
        val active = getActiveServerName()
        val servers = listServers()
        servers.map { server ->
            val config = parseConfig(serverFile(server))
            ServerProfileSummary(
                name = server,
                worldName = config.worldName,
                port = config.port,
                motd = config.motd,
                isActive = server == active
            )
        }.sortedBy { it.name }
    }

    suspend fun deleteServer(name: String): Boolean = withContext(Dispatchers.IO) {
        val clean = sanitizeServerName(name)
        if (clean.isEmpty()) return@withContext false
        val file = serverFile(clean)
        if (!file.exists()) return@withContext false
        val deleted = file.delete()
        if (!deleted) return@withContext false

        val active = getActiveServerName()
        val remaining = listServers().filter { it != clean }
        if (remaining.isEmpty()) {
            val fallback = "default"
            activeServerFile.writeText(fallback)
            ensureServerFileExists(fallback)
            val fallbackConfig = parseConfig(serverFile(fallback))
            writeConfigFile(propertiesFile, fallbackConfig)
            writeSpigotYml(fallbackConfig)
        } else if (active == clean) {
            val next = remaining.firstOrNull() ?: "default"
            activeServerFile.writeText(next)
            ensureServerFileExists(next)
            val nextConfig = parseConfig(serverFile(next))
            writeConfigFile(propertiesFile, nextConfig)
            writeSpigotYml(nextConfig)
        }
        true
    }

    private fun writeSpigotYml(config: ServerConfig) {
        val content = """
            |settings:
            |  save-user-cache-on-stop-only: false
            |  moved-wrong-area: true
            |  bungeecord: false
            |  sample-count: 12
            |  timeout-time: 60
            |world-settings:
            |  default:
            |    view-distance: ${config.viewDistance}
            |    mob-spawn-range: 4
            |    entity-activation-range:
            |      animals: 16
            |      monsters: 24
            |      raiders: 48
            |      misc: 8
            |      water: 8
            |      villagers: 16
            |      flying-monsters: 32
        """.trimMargin()
        spigotFile.writeText(content)
    }

    private fun parseConfig(file: File): ServerConfig {
        val props = file.readLines()
            .filter { it.contains("=") && !it.startsWith("#") }
            .associate {
                val idx = it.indexOf('=')
                it.substring(0, idx).trim() to it.substring(idx + 1).trim()
            }
        return ServerConfig(
            worldName = props["level-name"] ?: "world",
            worldSeed = props["level-seed"] ?: "",
            maxPlayers = (props["max-players"]?.toIntOrNull() ?: 10).coerceIn(1, 20),
            port = 25565,
            difficulty = props["difficulty"] ?: "normal",
            gameMode = props["gamemode"] ?: "survival",
            onlineMode = props["online-mode"]?.toBoolean() ?: false,
            motd = props["motd"] ?: "A PocketCraft Server",
            pvp = props["pvp"]?.toBoolean() ?: true,
            viewDistance = props["view-distance"]?.toIntOrNull() ?: 6,
            simulationDistance = props["simulation-distance"]?.toIntOrNull() ?: 4,
            spawnProtection = props["spawn-protection"]?.toIntOrNull() ?: 16,
            allowFlight = props["allow-flight"]?.toBoolean() ?: false,
            whiteList = props["white-list"]?.toBoolean() ?: false,
            enforceWhitelist = props["enforce-whitelist"]?.toBoolean() ?: false,
            commandBlocks = props["enable-command-block"]?.toBoolean() ?: true,
            netherEnabled = props["allow-nether"]?.toBoolean() ?: true,
            spawnMonsters = props["spawn-monsters"]?.toBoolean() ?: true,
            spawnAnimals = props["spawn-animals"]?.toBoolean() ?: true,
            spawnNpcs = props["spawn-npcs"]?.toBoolean() ?: true,
            hardcore = props["hardcore"]?.toBoolean() ?: false,
            maxRamMb = props["pocketcraft-max-ram-mb"]?.toIntOrNull() ?: 1024,
            enableRcon = props["enable-rcon"]?.toBoolean() ?: true,
            joinMessageEnabled = props["pocketcraft-join-message-enabled"]?.toBoolean() ?: false,
            joinMessageText = props["pocketcraft-join-message-text"] ?: "Welcome to the server! Have fun!",
            joinMessageUrl = props["pocketcraft-join-message-url"] ?: ""
        )
    }

    private fun writeConfigFile(file: File, config: ServerConfig) {
        val content = buildString {
            appendLine("#Minecraft server properties (PocketCraft)")
            appendLine("level-name=${config.worldName}")
            appendLine("level-seed=${config.worldSeed}")
            appendLine("max-players=${config.maxPlayers.coerceIn(1, 20)}")
            appendLine("server-port=25565")
            appendLine("difficulty=${config.difficulty}")
            appendLine("gamemode=${config.gameMode}")
            appendLine("online-mode=${config.onlineMode}")
            appendLine("motd=${config.motd}")
            appendLine("pvp=${config.pvp}")
            appendLine("view-distance=${config.viewDistance}")
            appendLine("simulation-distance=${config.simulationDistance}")
            appendLine("spawn-protection=${config.spawnProtection}")
            appendLine("enable-command-block=${config.commandBlocks}")
            appendLine("allow-flight=${config.allowFlight}")
            appendLine("white-list=${config.whiteList}")
            appendLine("enforce-whitelist=${config.enforceWhitelist}")
            appendLine("allow-nether=${config.netherEnabled}")
            appendLine("spawn-monsters=${config.spawnMonsters}")
            appendLine("spawn-animals=${config.spawnAnimals}")
            appendLine("spawn-npcs=${config.spawnNpcs}")
            appendLine("hardcore=${config.hardcore}")
            appendLine("pocketcraft-max-ram-mb=${config.maxRamMb}")
            appendLine("enable-rcon=true")
            appendLine("rcon.port=25575")
            appendLine("rcon.password=pocketcraft-internal-rcon")
            appendLine("broadcast-rcon-to-ops=false")
            appendLine("pocketcraft-join-message-enabled=${config.joinMessageEnabled}")
            appendLine("pocketcraft-join-message-text=${config.joinMessageText}")
            appendLine("pocketcraft-join-message-url=${config.joinMessageUrl}")
        }
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    private fun serverFile(serverName: String): File =
        File(serversDir, "${sanitizeServerName(serverName)}.properties")

    private fun ensureServerFileExists(serverName: String) {
        val clean = sanitizeServerName(serverName).ifEmpty { "default" }
        val file = serverFile(clean)
        if (file.exists()) return
        val worldBase = clean.replace('-', '_')
        val config = if (propertiesFile.exists()) {
            parseConfig(propertiesFile)
        } else {
            ServerConfig(worldName = if (worldBase == "default") "world" else "world_$worldBase")
        }
        writeConfigFile(file, config)
    }

    private fun sanitizeServerName(input: String): String =
        input.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')

    // Backward-compatible wrappers for old callers
    suspend fun listServerProfiles(): List<String> = listServers()
    suspend fun getActiveServerProfile(): String = getActiveServerName()
    suspend fun activateServerProfile(profileName: String) = activateServer(profileName)
    suspend fun createServerProfile(profileName: String, worldName: String? = null): Boolean =
        addServer(profileName, worldName)
    suspend fun renameServerProfile(oldName: String, newName: String): Boolean = false
    suspend fun duplicateServerProfile(sourceName: String, newName: String): Boolean = false
    suspend fun deleteServerProfile(name: String): Boolean = deleteServer(name)

    fun isSetupComplete(): Boolean {
        if (!setupMarkerFile.exists()) return false
        if (!JreExtractor.isExtracted(context)) return false
        val hasDownloadedJar = serversDir.walkTopDown()
            .any { it.isFile && it.extension == "jar" && it.length() > 1_000_000L }
        if (!hasDownloadedJar) return false

        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val arch = when {
            abi.contains("arm64") || abi.contains("aarch64") -> "aarch64"
            abi.contains("arm") -> "aarch32"
            abi.contains("x86_64") -> "amd64"
            abi.contains("x86") -> "i386"
            else -> null
        } ?: return false

        val jreDir = JreExtractor.getJreDir(context)
        val jreLibDir = File(jreDir, "lib")
        val jvmCandidates = listOf(
            File(File(jreLibDir, arch), "server/libjvm.so"),
            File(File(jreLibDir, arch), "client/libjvm.so"),
            File(jreLibDir, "server/libjvm.so"),
            File(jreLibDir, "client/libjvm.so")
        )
        val jliCandidates = listOf(
            File(File(jreLibDir, arch), "jli/libjli.so"),
            File(File(jreLibDir, arch), "libjli.so"),
            File(jreLibDir, "jli/libjli.so"),
            File(jreLibDir, "libjli.so")
        )
        val hasJvm = jvmCandidates.any { it.exists() } ||
            jreLibDir.walkTopDown().any { it.isFile && it.name == "libjvm.so" }
        val hasJli = jliCandidates.any { it.exists() } ||
            jreLibDir.walkTopDown().any { it.isFile && it.name == "libjli.so" }
        return hasJvm && hasJli
    }

    fun markSetupComplete() {
        setupMarkerFile.createNewFile()
    }

    fun deleteWorldData(): Boolean {
        val config = runCatching { 
            propertiesFile.readLines()
                .firstOrNull { it.startsWith("level-name=") }
                ?.substringAfter("=")
                ?.trim() ?: "world"
        }.getOrDefault("world")
        val worldDir = File(serverDir, config)
        return worldDir.deleteRecursively()
    }

    private fun detectServerVersion(): String {
        val paperJar = serversDir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("paper-") && it.extension == "jar" }
            ?.maxByOrNull { it.lastModified() }
            ?.name
            ?: "paper-1.20.4.jar"
        val version = Regex("""paper-(.+)\.jar""").find(paperJar)?.groupValues?.getOrNull(1)
            ?: "1.20.4"
        return "Paper $version"
    }

    private fun readTotalPlaytime(worldName: String): Pair<Long, Int> {
        val statsDir = File(File(serverDir, worldName), "stats")
        if (!statsDir.exists() || !statsDir.isDirectory) return 0L to 0

        var totalTicks = 0L
        var players = 0
        statsDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.forEach { statsFile ->
                val ticks = runCatching {
                    val root = JSONObject(statsFile.readText())
                    val customStats = root.optJSONObject("stats")
                        ?.optJSONObject("minecraft:custom")
                    when {
                        customStats == null -> 0L
                        customStats.has("minecraft:play_time") ->
                            customStats.optLong("minecraft:play_time", 0L)
                        customStats.has("minecraft:play_one_minute") ->
                            customStats.optLong("minecraft:play_one_minute", 0L)
                        else -> 0L
                    }
                }.getOrDefault(0L)
                if (ticks > 0) {
                    totalTicks += ticks
                    players++
                }
            }
        return totalTicks to players
    }

    private fun loadUserCache(): Map<String, String> {
        val userCache = File(serverDir, "usercache.json")
        if (!userCache.exists() || !userCache.isFile) return emptyMap()
        return runCatching {
            val arr = org.json.JSONArray(userCache.readText())
            buildMap {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val uuid = obj.optString("uuid").trim()
                    val name = obj.optString("name").trim()
                    if (uuid.isNotEmpty() && name.isNotEmpty()) {
                        put(uuid, name)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }
}
