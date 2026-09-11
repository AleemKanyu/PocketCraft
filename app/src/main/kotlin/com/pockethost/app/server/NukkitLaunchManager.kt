package com.pockethost.app.server

import android.content.Context
import android.util.Log
import com.pockethost.app.service.ServerFileManager
import com.pockethost.app.service.ServerPropertiesHelper
import java.io.File

/**
 * Prepares a world directory for a Bedrock-only PowerNukkitX server.
 *
 * PocketHost keeps `server.properties` as the single source of truth for every world so that the
 * settings screens, relay status publishing and world management keep working unchanged across
 * server types. PowerNukkitX 3.x does not read `server.properties` at all, so the values the app
 * manages are projected into `pnx.yml` here, immediately before each launch.
 */
object NukkitLaunchManager {
    private const val TAG = "NukkitLaunchManager"

    fun configFile(serverDir: File): File = File(serverDir, NukkitVersions.CONFIG_FILE)

    /**
     * Resolves the UDP port a Bedrock world listens on. `pnx.yml` wins when it exists, because
     * PowerNukkitX itself may have rewritten the port; otherwise the app's own record is used.
     */
    fun resolvePort(serverDir: File): Int {
        PnxYaml.readInt(configFile(serverDir), "settings.port")?.takeIf { it in 1..65_535 }
            ?.let { return it }
        val props = ServerPropertiesHelper.readProperties(serverDir, persistDefaults = false)
        return props.getProperty("server-port")?.toIntOrNull()?.takeIf { it in 1..65_535 }
            ?: NukkitVersions.DEFAULT_BEDROCK_PORT
    }

    fun resolveMotd(serverDir: File): String {
        val fromYaml = PnxYaml.readString(configFile(serverDir), "settings.motd")
            ?.trim()
            ?.trim('"', '\'')
        if (!fromYaml.isNullOrBlank()) return fromYaml
        return ServerPropertiesHelper.readProperties(serverDir, persistDefaults = false)
            .getProperty("motd", "A PocketHost Bedrock Server")
    }

    fun resolveMaxPlayers(serverDir: File): Int =
        PnxYaml.readInt(configFile(serverDir), "settings.maxPlayers")?.takeIf { it > 0 }
            ?: ServerPropertiesHelper.readProperties(serverDir, persistDefaults = false)
                .getProperty("max-players")?.toIntOrNull()
            ?: 10

    /**
     * Writes `pnx.yml` for [worldName] and returns its server directory.
     *
     * When the file is absent a minimal document is generated, which both seeds the user's
     * settings and suppresses PowerNukkitX's interactive setup wizard — that wizard reads from
     * stdin and would hang a server launched with a piped stdin. When the file already exists
     * only the keys PocketHost manages are rewritten, so anything the player changed by hand (or
     * PowerNukkitX backfilled) survives.
     */
    fun prepareNukkitServer(
        context: Context,
        worldName: String,
        serverName: String? = null,
        port: Int? = null,
        gamemode: Int? = null,
        difficulty: Int? = null,
        maxPlayers: Int? = null
    ): File {
        val serverDir = ServerFileManager.getServerDir(context, worldName)
        File(serverDir, NukkitVersions.LEVELS_DIR).mkdirs()
        File(serverDir, "plugins").mkdirs()

        val props = ServerPropertiesHelper.readProperties(serverDir, persistDefaults = false)

        val resolvedPort = port
            ?: props.getProperty("server-port")?.toIntOrNull()?.takeIf { it in 1..65_535 }
            ?: NukkitVersions.DEFAULT_BEDROCK_PORT
        val resolvedMotd = serverName?.takeIf { it.isNotBlank() }
            ?: props.getProperty("motd")?.takeIf { it.isNotBlank() }
            ?: "A PocketHost Bedrock Server"
        val resolvedMaxPlayers = maxPlayers
            ?: props.getProperty("max-players")?.toIntOrNull()
            ?: 10
        val resolvedGamemode = gamemode ?: NukkitVersions.gamemodeToInt(props.getProperty("gamemode"))
        val resolvedDifficulty = difficulty ?: NukkitVersions.difficultyToInt(props.getProperty("difficulty"))
        val resolvedViewDistance = props.getProperty("view-distance")?.toIntOrNull()?.coerceIn(3, 16)
            ?: ServerPropertiesHelper.DEFAULT_VIEW_DISTANCE
        val levelName = props.getProperty("level-name")?.takeIf { it.isNotBlank() } ?: "world"

        val configFile = configFile(serverDir)
        if (!configFile.exists()) {
            configFile.writeText(
                NukkitVersions.generateDefaultConfig(
                    motd = resolvedMotd,
                    port = resolvedPort,
                    levelName = levelName,
                    maxPlayers = resolvedMaxPlayers,
                    gamemode = resolvedGamemode,
                    difficulty = resolvedDifficulty,
                    viewDistance = resolvedViewDistance
                )
            )
            Log.i(TAG, "Generated pnx.yml for Bedrock world $worldName on port $resolvedPort")
        } else {
            PnxYaml.apply(
                configFile,
                mapOf(
                    "settings.port" to resolvedPort.toString(),
                    "settings.maxPlayers" to resolvedMaxPlayers.toString(),
                    "settings.motd" to PnxYaml.quote(resolvedMotd),
                    "settings.defaultLevelName" to levelName,
                    "settings.allowList" to (props.getProperty("white-list", "false") == "true").toString(),
                    "gameplay-settings.gamemode" to resolvedGamemode.toString(),
                    "gameplay-settings.difficulty" to resolvedDifficulty.toString(),
                    "gameplay-settings.viewDistance" to resolvedViewDistance.toString(),
                    "gameplay-settings.pvp" to (props.getProperty("pvp", "true") == "true").toString(),
                    "gameplay-settings.hardcore" to (props.getProperty("hardcore", "false") == "true").toString(),
                    "misc-settings.enableMetrics" to "false",
                    "network-settings.zlibProvider" to "1"
                )
            )
            Log.i(TAG, "Synced PocketHost settings into existing pnx.yml for $worldName")
        }

        // Keep the app's own record aligned with what the server will actually bind, and drop the
        // nukkit.yml that PocketHost's pre-3.x integration used to write — PowerNukkitX 3 ignores
        // it, and leaving it behind only confuses anyone inspecting the world directory.
        props.setProperty("pocketcraft-server-type", "BEDROCK")
        props.setProperty("server-port", resolvedPort.toString())
        props.setProperty("motd", resolvedMotd)
        props.setProperty("max-players", resolvedMaxPlayers.toString())
        ServerPropertiesHelper.saveProperties(serverDir, props)
        File(serverDir, "nukkit.yml").takeIf { it.exists() }?.delete()

        Log.i(TAG, "Bedrock server $worldName prepared in ${serverDir.path}")
        return serverDir
    }
}
