package com.pocketcraft.server.service

import android.content.Context
import android.net.Uri
import com.pocketcraft.server.BuildConfig
import com.pocketcraft.server.data.model.Plugin
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import android.util.Log

object PluginManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private const val MODRINTH_BASE_URL = "https://api.modrinth.com/v2"
    private const val HANGAR_BASE_URL = "https://hangar.papermc.io/api/v1"
    private const val GEYSERMC_DOWNLOAD_BASE_URL = "https://download.geysermc.org/v2/projects"
    private const val MEMORY_CACHE_TTL_MS = 3 * 60 * 1000L
    private const val HTTP_CACHE_BYTES = 12L * 1024L * 1024L
    private const val MODRINTH_PROVIDER = "modrinth"
    private const val HANGAR_PROVIDER = "hangar"
    private val builtInBridgeProjectIds = setOf("geyser", "viaversion", "chunky")
    private val builtInBridgeKeywords = setOf("geyser", "viaversion", "chunky")
    private val incompatiblePluginTokens = listOf("fastleafdecay", "inventoryprofiles")
    private val BLOCKED_PLUGINS = setOf("spark", "spark-bukkit")
    private val paperCompatibleLoaders = setOf("paper", "spigot", "purpur", "bukkit", "folia")
    private val modLoaderLabels = linkedMapOf(
        "fabric" to "Fabric",
        "forge" to "Forge",
        "neoforge" to "NeoForge",
        "quilt" to "Quilt"
    )

    private var httpClient: OkHttpClient? = null
    private var cacheDir: File? = null

    private val catalogCache = LinkedHashMap<String, CachedCatalogResult>(24, 0.75f, true)
    private val throttleLock = Any()
    private val providerNextRequestAtMs = mutableMapOf<String, Long>()

    data class RemoteCatalogItem(
        val source: String,
        val projectId: String,
        val title: String,
        val slug: String,
        val iconUrl: String?,
        val description: String,
        val downloads: Long,
        val author: String? = null,
        val canInstall: Boolean = true,
        val supportMessage: String? = null,
        val isSupported: Boolean = true,
        val owner: String? = null
    ) {
        val catalogKey: String = "$source:$projectId"
    }

    enum class ContentType {
        PLUGINS,
        MODS,
        RESOURCE_PACKS
    }

    private enum class ArchiveKind(val label: String) {
        PLUGIN("Plugin"),
        FABRIC_MOD("Fabric"),
        FORGE_MOD("Forge"),
        NEOFORGE_MOD("NeoForge"),
        UNKNOWN("")
    }

    private data class ArchiveMetadata(
        val kind: ArchiveKind = ArchiveKind.UNKNOWN,
        val name: String? = null,
        val version: String? = null
    )

    private data class CachedCatalogResult(
        val items: List<RemoteCatalogItem>,
        val createdAtMs: Long = System.currentTimeMillis()
    ) {
        fun isFresh(): Boolean = System.currentTimeMillis() - createdAtMs < MEMORY_CACHE_TTL_MS
    }

    private data class DownloadCandidate(
        val downloadUrl: String,
        val fileName: String
    )

    private data class BundledPluginUpdate(
        val projectId: String,
        val title: String,
        val downloadUrl: String? = null,
        val fileNameHint: String? = null,
        val catalogItem: RemoteCatalogItem? = null
    )

    private fun getHttpClient(context: Context): OkHttpClient {
        if (httpClient == null) {
            if (cacheDir == null) {
                cacheDir = File(context.cacheDir, "content_catalog_cache")
            }
            val cache = Cache(cacheDir!!, HTTP_CACHE_BYTES)
            httpClient = OkHttpClient.Builder()
                .cache(cache)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
        return httpClient!!
    }

    fun getPluginsDir(context: Context, worldName: String): File =
        File(context.filesDir, "servers/worlds/$worldName/plugins").also { it.mkdirs() }

    private fun isIncompatiblePluginName(name: String): Boolean {
        val normalized = name.lowercase(Locale.US)
            .replace("-", "")
            .replace("_", "")
            .replace(" ", "")
        
        if (incompatiblePluginTokens.any { normalized.contains(it) }) return true
        
        // Fix 3: Block Spark from bundled plugins
        return BLOCKED_PLUGINS.any { normalized.contains(it) }
    }

    fun removeIncompatiblePlugins(context: Context, worldName: String) {
        val pluginsDir = getPluginsDir(context, worldName)
        val removed = pluginsDir.listFiles()
            ?.filter { file ->
                val nameWithoutDisabled = file.name.removeSuffix(".disabled")
                isIncompatiblePluginName(nameWithoutDisabled)
            }
            ?.onEach { it.deleteRecursively() }
            .orEmpty()
        if (removed.isNotEmpty()) {
            Log.w("PluginManager", "Removed incompatible/blocked plugins: ${removed.joinToString { it.name }}")
        }
    }

    fun getGeyserConfigFile(context: Context, worldName: String): File {
        val pluginsDir = getPluginsDir(context, worldName)
        return listOf(
            File(pluginsDir, "Geyser/config.yml"),
            File(pluginsDir, "Geyser-Spigot/config.yml"),
            File(pluginsDir, "Geyser-Spigot/geyser.yml"),
            File(pluginsDir, "geyser/config.yml"),
            File(pluginsDir, "geyser.yml")
        ).firstOrNull { it.exists() } ?: File(pluginsDir, "Geyser-Spigot/config.yml")
    }

    fun getFloodgateConfigFile(context: Context, worldName: String): File {
        val pluginsDir = getPluginsDir(context, worldName)
        return listOf(
            File(pluginsDir, "floodgate/config.yml"),
            File(pluginsDir, "Floodgate/config.yml"),
            File(pluginsDir, "Floodgate/floodgate.yml"),
            File(pluginsDir, "floodgate/floodgate.yml")
        ).firstOrNull { it.exists() } ?: File(pluginsDir, "Floodgate/config.yml")
    }

    fun getModsDir(context: Context, worldName: String): File =
        File(context.filesDir, "servers/$worldName/mods").also { it.mkdirs() }

    fun getResourcePacksDir(context: Context, worldName: String): File =
        File(context.filesDir, "servers/$worldName/resourcepacks").also { it.mkdirs() }

    fun ensureContentDirs(context: Context, worldName: String) {
        getPluginsDir(context, worldName)
        getModsDir(context, worldName)
        getResourcePacksDir(context, worldName)
    }

    fun getContentDir(context: Context, worldName: String, type: ContentType): File {
        return when (type) {
            ContentType.PLUGINS -> getPluginsDir(context, worldName)
            ContentType.MODS -> getModsDir(context, worldName)
            ContentType.RESOURCE_PACKS -> getResourcePacksDir(context, worldName)
        }
    }

    fun listPlugins(context: Context, worldName: String): List<Plugin> {
        return getPluginsDir(context, worldName)
            .listFiles { file -> file.extension == "jar" || file.name.endsWith(".jar.disabled") }
            ?.map { file ->
                val metadata = readArchiveMetadata(file)
                Plugin(
                    name = metadata.name ?: file.name.removeSuffix(".jar").removeSuffix(".disabled"),
                    fileName = file.name,
                    sizeMb = file.length() / (1024f * 1024f),
                    enabled = !file.name.endsWith(".disabled"),
                    version = metadata.version.orEmpty()
                )
            }
            ?.filterNot(::isManagedBridgePlugin)
            ?.sortedByDescending { it.sizeMb }
            ?: emptyList()
    }

    suspend fun ensureBedrockBridgePlugins(
        context: Context,
        worldName: String,
        onProgress: (String) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        removeIncompatiblePlugins(context, worldName)
        val geyser = RemoteCatalogItem(
            source = MODRINTH_PROVIDER,
            projectId = "geyser",
            title = "Geyser",
            slug = "geyser",
            iconUrl = null,
            description = "Built-in Bedrock bridge",
            downloads = 0L
        )
        val viaVersion = RemoteCatalogItem(
            source = MODRINTH_PROVIDER,
            projectId = "viaversion",
            title = "ViaVersion",
            slug = "viaversion",
            iconUrl = null,
            description = "Built-in Java protocol compatibility for latest Bedrock via Geyser",
            downloads = 0L
        )

        installManagedPluginIfMissing(
            context = context,
            worldName = worldName,
            item = geyser,
            onProgress = onProgress
        ).onFailure { error ->
            android.util.Log.w("PluginManager", "Modrinth/Hangar Geyser resolution failed: ${error.message}. Attempting direct download fallback...")
            installManagedPluginFromDirectUrlIfMissing(
                context = context,
                worldName = worldName,
                projectId = "geyser",
                title = "Geyser",
                downloadUrl = "$GEYSERMC_DOWNLOAD_BASE_URL/geyser/versions/latest/builds/latest/downloads/spigot",
                fileNameHint = "Geyser-Spigot.jar",
                onProgress = onProgress
            ).fold(
                onSuccess = {},
                onFailure = { return@withContext Result.failure(it) }
            )
        }

        installManagedPluginFromDirectUrlIfMissing(
            context = context,
            worldName = worldName,
            projectId = "floodgate",
            title = "Floodgate",
            downloadUrl = "$GEYSERMC_DOWNLOAD_BASE_URL/floodgate/versions/latest/builds/latest/downloads/spigot",
            fileNameHint = "Floodgate-Spigot.jar",
            onProgress = onProgress
        ).onFailure { error ->
            android.util.Log.w("PluginManager", "Floodgate direct download failed: ${error.message}")
        }

        installManagedPluginIfMissing(
            context = context,
            worldName = worldName,
            item = viaVersion,
            onProgress = onProgress
        ).onFailure { error ->
            android.util.Log.w("PluginManager", "ViaVersion auto-install skipped: ${error.message}")
        }

        ensureManagedPluginEnabled(context, worldName, "geyser")
        ensureManagedPluginEnabled(context, worldName, "floodgate")

        enforceBedrockBridgeLocalConfig(context, worldName)

        Result.success(Unit)
    }

    suspend fun autoUpdateBundledPlugins(
        context: Context,
        worldName: String,
        onProgress: (String) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val bundledPlugins = listOf(
            BundledPluginUpdate(
                projectId = "geyser",
                title = "Geyser",
                downloadUrl = "$GEYSERMC_DOWNLOAD_BASE_URL/geyser/versions/latest/builds/latest/downloads/spigot",
                fileNameHint = "Geyser-Spigot.jar"
            ),
            BundledPluginUpdate(
                projectId = "floodgate",
                title = "Floodgate",
                downloadUrl = "$GEYSERMC_DOWNLOAD_BASE_URL/floodgate/versions/latest/builds/latest/downloads/spigot",
                fileNameHint = "Floodgate-Spigot.jar"
            ),
            BundledPluginUpdate(
                projectId = "viaversion",
                title = "ViaVersion",
                catalogItem = RemoteCatalogItem(
                    source = MODRINTH_PROVIDER,
                    projectId = "viaversion",
                    title = "ViaVersion",
                    slug = "viaversion",
                    iconUrl = null,
                    description = "Built-in Java protocol compatibility for latest Bedrock via Geyser",
                    downloads = 0L
                )
            )
        )

        for (plugin in bundledPlugins) {
            try {
                onProgress("Updating ${plugin.title} bridge plugin...")
                withTimeoutOrNull(15000L) {
                    replaceManagedPluginFromUrl(
                        context = context,
                        worldName = worldName,
                        projectId = plugin.projectId,
                        title = plugin.title,
                        downloadUrl = plugin.downloadUrl,
                        fileNameHint = plugin.fileNameHint,
                        catalogItem = plugin.catalogItem
                    )
                } ?: Log.w("PluginManager", "Timeout auto-updating ${plugin.title}")
            } catch (e: Exception) {
                Log.w("PluginManager", "Failed to auto-update ${plugin.title}: ${e.message}")
            }
        }

        ensureManagedPluginEnabled(context, worldName, "geyser")
        ensureManagedPluginEnabled(context, worldName, "floodgate")
        ensureManagedPluginEnabled(context, worldName, "viaversion")
        
        enforceBedrockBridgeLocalConfig(context, worldName)
        
        Result.success(Unit)
    }

    suspend fun ensureChunkyPlugin(
        context: Context,
        worldName: String,
        onProgress: (String) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val chunky = RemoteCatalogItem(
            source = MODRINTH_PROVIDER,
            projectId = "chunky",
            title = "Chunky",
            slug = "chunky",
            iconUrl = null,
            description = "Chunk pre-generator",
            downloads = 0L
        )
        installManagedPluginIfMissing(context, worldName, chunky, onProgress)
    }

    fun enforceBedrockBridgeLocalConfig(context: Context, worldName: String) {
        val pluginsDir = getPluginsDir(context, worldName)
        val floodgateKeyPath = when {
            File(pluginsDir, "Floodgate/key.pem").exists() -> "../Floodgate/key.pem"
            File(pluginsDir, "floodgate/key.pem").exists() -> "../floodgate/key.pem"
            else -> "../floodgate/key.pem"
        }
        val geyserConfigFile = getGeyserConfigFile(context, worldName)
        geyserConfigFile.parentFile?.mkdirs()
        val original = if (geyserConfigFile.exists()) {
            runCatching { geyserConfigFile.readText() }.getOrDefault("")
        } else {
            ""
        }
        var updated = original
        // Fix 2: Patch Geyser config to prevent ioctl (SELinux denials)
        updated = ensureYamlSectionValue(updated, "bedrock", "address", "0.0.0.0")
        updated = ensureYamlSectionValue(updated, "bedrock", "port", "19132")
        updated = ensureYamlSectionValue(updated, "bedrock", "clone-remote-port", "false")
        updated = ensureYamlSectionValue(updated, "bedrock", "broadcast-port", "19132")
        updated = ensureYamlSectionValue(updated, "bedrock", "enable-proxy-protocol", "false")
        updated = ensureYamlSectionValue(updated, "bedrock", "motd1", "PocketCraft Server")
        updated = ensureYamlSectionValue(updated, "bedrock", "motd2", "Tap to join")
        updated = ensureTopLevelYamlValue(updated, "ping-passthrough-interval", "1")
        updated = ensureTopLevelYamlValue(updated, "async-motd", "false")
        updated = ensureTopLevelYamlValue(updated, "cache-chunks", "true")
        updated = ensureTopLevelYamlValue(updated, "max-auto-connect-attempts", "5")
        updated = ensureTopLevelYamlValue(updated, "show-cooldown", "disabled")
        updated = ensureTopLevelYamlValue(updated, "forward-hostname", "false")
        updated = ensureTopLevelYamlValue(updated, "floodgate-key-file", floodgateKeyPath)
        updated = ensureYamlSectionValue(updated, "remote", "address", "127.0.0.1")
        updated = ensureYamlSectionValue(updated, "remote", "port", "25565")
        // Use Floodgate auth so Bedrock players are not asked for a Java account.
        updated = ensureYamlSectionValue(updated, "remote", "auth-type", "floodgate")

        // Handle newer Geyser config formats (ensure auth-type is floodgate)
        updated = ensureYamlSectionValue(updated, "server", "auth-type", "floodgate")
        updated = ensureYamlSectionValue(updated, "java", "auth-type", "floodgate")

        updated = ensureYamlSectionValue(updated, "motd", "passthrough-motd", "false")
        updated = ensureYamlSectionValue(updated, "motd", "passthrough-player-counts", "false")

        if (updated != original) {
            geyserConfigFile.writeText(updated)
        }

        // Disable require-link in Floodgate to ensure Bedrock players do NOT need a Java account
        setFloodgateSectionValue(context, worldName, "player-link", "require-link", "false")
    }

    /**
     * Preserves the Floodgate encryption key (key.pem) across server resets.
     * If the key file is deleted or regenerated, all existing player links are
     * invalidated — causing Bedrock player data resets even with player-link enabled.
     * The backup is stored outside the server world folder so it survives world resets.
     */
    fun preserveFloodgateKey(context: Context, worldName: String) {
        val serverDir = File(context.filesDir, "servers/$worldName")
        val floodgateDirs = listOf(
            File(serverDir, "plugins/Floodgate"),
            File(serverDir, "plugins/floodgate")
        )
        val keyFile = floodgateDirs.map { File(it, "key.pem") }.firstOrNull { it.exists() }
        val defaultKeyFile = File(floodgateDirs.first(), "key.pem")
        val backupFile = File(serverDir, "floodgate_key_backup.pem")

        if (keyFile != null && !backupFile.exists()) {
            // First time — back it up
            runCatching {
                keyFile.copyTo(backupFile, overwrite = false)
                android.util.Log.d("Floodgate", "Backed up Floodgate key to ${backupFile.path}")
            }
        } else if (keyFile == null && backupFile.exists()) {
            // Key was deleted — restore it
            runCatching {
                defaultKeyFile.parentFile?.mkdirs()
                backupFile.copyTo(defaultKeyFile, overwrite = true)
                android.util.Log.d("Floodgate", "Restored Floodgate key from backup")
            }
        } else if (keyFile != null && backupFile.exists()) {
            // Both exist — keep backup in sync with current key
            runCatching {
                keyFile.copyTo(backupFile, overwrite = true)
            }
        }
    }

    fun readFloodgateConfigValue(context: Context, worldName: String, key: String): String? {
        val file = getFloodgateConfigFile(context, worldName)
        if (!file.exists()) return null
        val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
        val line = lines.firstOrNull { it.trimStart().startsWith("$key:") }
            ?: return null
        return line.substringAfter(':').trim().stripYamlQuotes().ifBlank { null }
    }

    fun readFloodgateSectionValue(
        context: Context,
        worldName: String,
        section: String,
        key: String
    ): String? {
        val file = getFloodgateConfigFile(context, worldName)
        if (!file.exists()) return null
        val lines = runCatching { file.readLines() }.getOrDefault(emptyList())

        val sectionStart = lines.indexOfFirst { it.trim() == "$section:" }
        if (sectionStart == -1) return null

        var sectionEnd = lines.size
        for (index in (sectionStart + 1) until lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) continue
            if (!line.startsWith(" ") && !line.startsWith("\t")) {
                sectionEnd = index
                break
            }
        }

        val match = ((sectionStart + 1) until sectionEnd).firstOrNull { index ->
            lines[index].trimStart().startsWith("$key:")
        } ?: return null
        return lines[match].substringAfter(':').trim().stripYamlQuotes().ifBlank { null }
    }

    fun setFloodgateConfigValue(context: Context, worldName: String, key: String, value: String) {
        val file = getFloodgateConfigFile(context, worldName)
        file.parentFile?.mkdirs()
        if (!file.exists()) return
        val lines = file.readLines().toMutableList()
        val idx = lines.indexOfFirst { it.trimStart().startsWith("$key:") }
        val formatted = formatYamlScalar(value)
        if (idx != -1) {
            lines[idx] = "$key: $formatted"
        } else {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines += ""
            lines += "$key: $formatted"
        }
        file.writeText(lines.joinToString("\n").trimEnd() + "\n")
    }

    fun setFloodgateSectionValue(
        context: Context,
        worldName: String,
        section: String,
        key: String,
        value: String
    ) {
        val file = getFloodgateConfigFile(context, worldName)
        file.parentFile?.mkdirs()
        if (!file.exists()) return
        val original = file.readText()
        val updated = ensureYamlSectionValue(original, section, key, value)
        if (updated != original) {
            file.writeText(updated)
        }
    }

    fun isFloodgateUsernamePrefixShown(context: Context, worldName: String): Boolean {
        return readFloodgateConfigValue(context, worldName, "username-prefix")
            ?.let { it != "\"\"" && it != "" }
            ?: false
    }

    fun isFloodgatePlayerLinkEnabled(context: Context, worldName: String): Boolean {
        return readFloodgateSectionValue(context, worldName, "player-link", "enabled")
            ?.let { it.equals("true", ignoreCase = true) }
            ?: true
    }

    private fun ensureTopLevelYamlValue(
        original: String,
        key: String,
        value: String
    ): String {
        val lines = original
            .ifBlank { "" }
            .split('\n')
            .toMutableList()
        val keyIndex = lines.indexOfFirst { line ->
            line.trimStart() == "$key: $value" || (!line.startsWith(" ") && !line.startsWith("\t") && line.trimStart().startsWith("$key:"))
        }
        if (keyIndex != -1) {
            lines[keyIndex] = "$key: $value"
        } else {
            if (lines.size == 1 && lines[0].isBlank()) lines.clear()
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines += ""
            lines += "$key: $value"
        }
        return lines.joinToString("\n").trimEnd() + "\n"
    }

    private fun formatYamlScalar(value: String): String {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> "\"\""
            trimmed.any { it.isWhitespace() } -> "\"$trimmed\""
            trimmed == "true" || trimmed == "false" -> trimmed
            trimmed.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' } -> trimmed
            else -> "\"$trimmed\""
        }
    }

    private fun ensureYamlSectionValue(
        original: String,
        section: String,
        key: String,
        value: String
    ): String {
        val lines = original
            .ifBlank { "" }
            .split('\n')
            .toMutableList()

        var sectionStart = lines.indexOfFirst { it.trim() == "$section:" }
        if (sectionStart == -1) {
            if (lines.size == 1 && lines[0].isBlank()) {
                lines.clear()
            }
            if (lines.isNotEmpty() && lines.last().isNotBlank()) {
                lines += ""
            }
            lines += "$section:"
            lines += "  $key: $value"
            return lines.joinToString("\n").trimEnd() + "\n"
        }

        var sectionEnd = lines.size
        for (index in (sectionStart + 1) until lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) continue
            if (!line.startsWith(" ") && !line.startsWith("\t")) {
                sectionEnd = index
                break
            }
        }

        val keyIndex = ((sectionStart + 1) until sectionEnd).firstOrNull { index ->
            val line = lines[index]
            (line.startsWith(" ") || line.startsWith("\t")) && line.trimStart().startsWith("$key:")
        }

        if (keyIndex != null) {
            lines[keyIndex] = "  $key: $value"
        } else {
            lines.add(sectionEnd, "  $key: $value")
        }

        return lines.joinToString("\n").trimEnd() + "\n"
    }

    private fun String.stripYamlQuotes(): String {
        return trim().removePrefix("\"").removeSuffix("\"").removePrefix("'").removeSuffix("'")
    }

    fun isBedrockBridgeEnabled(context: Context, worldName: String): Boolean {
        val hasGeyser = isManagedPluginEnabled(context, worldName, "geyser")
        val hasFloodgate = isManagedPluginEnabled(context, worldName, "floodgate")
        return hasGeyser && hasFloodgate
    }

    fun supportsMods(worldName: String): Boolean {
        // Fallback to worldName if game version not available, or check config repo
        // For simplicity in list functions, we'll keep the signature but might need logic update
        return true 
    }

    fun supportsFabricMods(worldName: String): Boolean {
        return true
    }

    fun listMods(context: Context, worldName: String): List<Plugin> {
        return getModsDir(context, worldName)
            .listFiles { file -> file.extension == "jar" || file.name.endsWith(".jar.disabled") }
            ?.map { file ->
                val metadata = readArchiveMetadata(file)
                Plugin(
                    name = metadata.name ?: file.name.removeSuffix(".jar").removeSuffix(".disabled"),
                    fileName = file.name,
                    sizeMb = file.length() / (1024f * 1024f),
                    enabled = !file.name.endsWith(".disabled"),
                    version = buildVersionLabel(metadata)
                )
            }
            ?.filterNot(::isManagedBridgePlugin)
            ?.sortedByDescending { it.sizeMb }
            ?: emptyList()
    }

    fun listResourcePacks(context: Context, worldName: String): List<Plugin> {
        return getResourcePacksDir(context, worldName)
            .listFiles { file -> file.isDirectory || file.extension == "zip" || file.name.endsWith(".zip.disabled") }
            ?.map { file ->
                Plugin(
                    name = file.name.removeSuffix(".zip").removeSuffix(".disabled"),
                    fileName = file.name,
                    sizeMb = file.length() / (1024f * 1024f),
                    enabled = !file.name.endsWith(".disabled")
                )
            }
            ?.sortedByDescending { it.sizeMb }
            ?: emptyList()
    }

    fun deletePlugin(context: Context, worldName: String, plugin: Plugin): Boolean {
        return try {
            File(getPluginsDir(context, worldName), plugin.fileName).delete()
        } catch (_: Exception) {
            false
        }
    }

    fun deleteContent(context: Context, worldName: String, type: ContentType, plugin: Plugin): Boolean {
        return try {
            File(getContentDir(context, worldName, type), plugin.fileName).deleteRecursively()
        } catch (_: Exception) {
            false
        }
    }

    fun disablePlugin(context: Context, worldName: String, plugin: Plugin): Boolean {
        return try {
            val dir = getPluginsDir(context, worldName)
            File(dir, plugin.fileName).renameTo(File(dir, "${plugin.fileName}.disabled"))
        } catch (_: Exception) {
            false
        }
    }

    fun enablePlugin(context: Context, worldName: String, plugin: Plugin): Boolean {
        return try {
            val dir = getPluginsDir(context, worldName)
            if (plugin.fileName.endsWith(".disabled")) {
                File(dir, plugin.fileName).renameTo(File(dir, plugin.fileName.removeSuffix(".disabled")))
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun toggleContent(context: Context, worldName: String, type: ContentType, plugin: Plugin): Boolean {
        return try {
            val dir = getContentDir(context, worldName, type)
            val source = File(dir, plugin.fileName)
            val target = if (plugin.fileName.endsWith(".disabled")) {
                File(dir, plugin.fileName.removeSuffix(".disabled"))
            } else {
                File(dir, "${plugin.fileName}.disabled")
            }
            source.renameTo(target)
        } catch (_: Exception) {
            false
        }
    }

    fun copyPluginFile(sourceFile: File, context: Context, worldName: String): Boolean {
        return try {
            val destDir = getPluginsDir(context, worldName)
            val destFile = File(destDir, sourceFile.name)
            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun installFromUri(
        context: Context,
        uri: Uri,
        worldName: String,
        type: ContentType,
        runtimeKey: String = worldName,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = getContentDir(context, worldName, type)
            val fileName = getFileNameFromUri(context, uri)
                ?.takeIf { it.isNotBlank() }
                ?: "item_${System.currentTimeMillis()}${defaultExtension(type)}"

            validateFileName(fileName, type)?.let { error ->
                return@withContext Result.failure(Exception(error))
            }

            val destFile = File(dir, sanitizeFileName(fileName))
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Could not open selected file")
            val totalBytes = context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: -1L

            inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var copied = 0L
                    var bytes = input.read(buffer)
                    while (bytes != -1) {
                        output.write(buffer, 0, bytes)
                        copied += bytes
                        if (totalBytes > 0) {
                            withContext(Dispatchers.Main) {
                                onProgress((copied * 100 / totalBytes).toInt().coerceIn(0, 100))
                            }
                        }
                        bytes = input.read(buffer)
                    }
                }
            }

            validateInstalledFile(destFile, type, runtimeKey)?.let { error ->
                destFile.delete()
                return@withContext Result.failure(Exception(error))
            }

            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun installFromUrl(
        context: Context,
        sourceUrl: String,
        worldName: String,
        type: ContentType,
        fileNameHint: String? = null,
        runtimeKey: String = worldName,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = getContentDir(context, worldName, type)
            val client = getHttpClient(context)
            val request = Request.Builder()
                .url(sourceUrl.trim())
                .header("User-Agent", userAgent())
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }

                val resolvedName = resolveFileName(
                    fileNameHint = fileNameHint,
                    contentDisposition = response.header("Content-Disposition"),
                    sourceUrl = sourceUrl,
                    type = type
                )

                validateFileName(resolvedName, type)?.let { error ->
                    return@withContext Result.failure(Exception(error))
                }

                val destFile = File(dir, sanitizeFileName(resolvedName))
                val body = response.body ?: return@withContext Result.failure(Exception("Empty download"))
                val totalBytes = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var bytes = input.read(buffer)
                        while (bytes != -1) {
                            output.write(buffer, 0, bytes)
                            downloaded += bytes
                            if (totalBytes > 0) {
                                withContext(Dispatchers.Main) {
                                    onProgress(((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100))
                                }
                            }
                            bytes = input.read(buffer)
                        }
                    }
                }

                validateInstalledFile(destFile, type, runtimeKey)?.let { error ->
                    destFile.delete()
                    return@withContext Result.failure(Exception(error))
                }

                Result.success(destFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun installRemoteItem(
        context: Context,
        item: RemoteCatalogItem,
        worldName: String,
        type: ContentType,
        runtimeKey: String = worldName,
        minecraftVersion: String? = null,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!item.canInstall) {
            return@withContext Result.failure(Exception(item.supportMessage ?: "This item is not compatible with the current server runtime."))
        }

        val resolvedVersion = minecraftVersion 
            ?: com.pocketcraft.server.data.repository.ServerConfigRepository(context).loadConfig().gameVersion.ifBlank { "1.20.4" }

        val candidate = when (item.source) {
            MODRINTH_PROVIDER -> resolveModrinthDownload(context, item, type, resolvedVersion, runtimeKey)
            HANGAR_PROVIDER -> resolveHangarDownload(context, item, worldName)
            else -> null
        } ?: return@withContext Result.failure(Exception("Could not find a compatible download for ${item.title}."))

        installFromUrl(
            context = context,
            sourceUrl = candidate.downloadUrl,
            worldName = worldName,
            type = type,
            fileNameHint = candidate.fileName,
            runtimeKey = runtimeKey,
            onProgress = onProgress
        )
    }

    suspend fun fetchRemoteCatalog(
        context: Context,
        type: ContentType,
        query: String,
        minecraftVersion: String,
        runtimeKey: String,
        limit: Int = 16
    ): Result<List<RemoteCatalogItem>> = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim()
        val cacheKey = listOf(type.name, minecraftVersion, normalizedQuery.lowercase(Locale.US), limit).joinToString("|")
        val cached = getCachedCatalog(context, cacheKey)
        
        if (cached != null) {
            // Background refresh
            scope.launch {
                runCatching {
                    val freshResults = performCatalogSearch(context, type, normalizedQuery, minecraftVersion, runtimeKey, limit)
                    putCachedCatalog(context, cacheKey, freshResults)
                }
            }
            return@withContext Result.success(cached)
        }

        runCatching {
            performCatalogSearch(context, type, normalizedQuery, minecraftVersion, runtimeKey, limit)
        }.map { results ->
            putCachedCatalog(context, cacheKey, results)
            results
        }
    }

    private suspend fun performCatalogSearch(
        context: Context,
        type: ContentType,
        normalizedQuery: String,
        minecraftVersion: String,
        runtimeKey: String,
        limit: Int
    ): List<RemoteCatalogItem> = coroutineScope {
        val results = when (type) {
            ContentType.PLUGINS -> {
                val perProviderLimit = (limit / 2).coerceAtLeast(6)
                val modrinth = async {
                    runCatching {
                        searchModrinthCatalog(
                            context = context,
                            type = type,
                            query = normalizedQuery,
                            minecraftVersion = minecraftVersion,
                            runtimeKey = runtimeKey,
                            limit = perProviderLimit
                        )
                    }.getOrDefault(emptyList())
                }
                val hangar = async {
                    runCatching {
                        searchHangarPlugins(
                            context = context,
                            query = normalizedQuery,
                            minecraftVersion = minecraftVersion,
                            limit = perProviderLimit
                        )
                    }.getOrDefault(emptyList())
                }
                mergeCatalogResults(
                    items = modrinth.await() + hangar.await(),
                    limit = limit,
                    blankQuery = normalizedQuery.isBlank()
                )
            }
            ContentType.MODS,
            ContentType.RESOURCE_PACKS -> mergeCatalogResults(
                items = searchModrinthCatalog(
                    context = context,
                    type = type,
                    query = normalizedQuery,
                    minecraftVersion = minecraftVersion,
                    runtimeKey = runtimeKey,
                    limit = limit
                ),
                limit = limit,
                blankQuery = normalizedQuery.isBlank()
            )
        }
        
        if (type == ContentType.PLUGINS) {
            results.filterNot(::isManagedBridgeCatalogItem)
        } else {
            results
        }
    }

    fun getResourcePackIcon(packDir: File): File? {
        if (!packDir.exists()) return null
        if (packDir.isDirectory) {
            return File(packDir, "pack.png").takeIf { it.exists() }
        }
        if (!packDir.isFile) return null
        if (!packDir.name.endsWith(".zip", ignoreCase = true) &&
            !packDir.name.endsWith(".zip.disabled", ignoreCase = true)
        ) {
            return null
        }

        val cacheRoot = File(packDir.parentFile, ".pack_icons").also { it.mkdirs() }
        val cachedName = packDir.name.removeSuffix(".disabled").removeSuffix(".zip") + ".png"
        val cachedIcon = File(cacheRoot, cachedName)
        if (cachedIcon.exists() && cachedIcon.lastModified() >= packDir.lastModified()) {
            return cachedIcon
        }

        return runCatching {
            java.util.zip.ZipFile(packDir).use { zip ->
                val iconEntry = zip.getEntry("pack.png") ?: return null
                zip.getInputStream(iconEntry).use { input ->
                    cachedIcon.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            cachedIcon.takeIf { it.exists() }
        }.getOrNull()
    }

    private suspend fun searchModrinthCatalog(
        context: Context,
        type: ContentType,
        query: String,
        minecraftVersion: String,
        runtimeKey: String,
        limit: Int
    ): List<RemoteCatalogItem> {
        throttleProvider(MODRINTH_PROVIDER, minimumGapMs = 250L)

        val facets = buildModrinthFacets(type)
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val encodedFacets = URLEncoder.encode(facets, "UTF-8")
        val index = if (query.isBlank()) "downloads" else "relevance"
        val url = buildString {
            append("$MODRINTH_BASE_URL/search")
            append("?query=$encodedQuery")
            append("&limit=$limit")
            append("&index=$index")
            append("&facets=$encodedFacets")
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent())
            .cacheControl(CacheControl.Builder().maxAge(10, TimeUnit.MINUTES).build())
            .build()

        return getHttpClient(context).newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Could not load the online catalog right now (HTTP ${response.code}).")
            }

            val payload = response.body?.string().orEmpty()
            if (payload.isBlank()) return emptyList()

            val root = JSONObject(payload)
            val hits = root.optJSONArray("hits") ?: return emptyList()
            return buildList {
                for (indexValue in 0 until hits.length()) {
                    val item = hits.optJSONObject(indexValue) ?: continue
                    val projectId = item.optString("project_id").ifBlank { item.optString("projectId") }
                    val slug = item.optString("slug").ifBlank { projectId }
                    val title = item.optString("title").ifBlank { slug }
                    if (projectId.isBlank() || title.isBlank()) continue

                    val versions = jsonArrayStrings(item.optJSONArray("versions"))
                    if (versions.isNotEmpty() && !supportsRequestedVersion(versions, minecraftVersion)) {
                        continue
                    }

                    val categories = (
                        jsonArrayStrings(item.optJSONArray("categories")) +
                            jsonArrayStrings(item.optJSONArray("display_categories"))
                        ).map { it.lowercase(Locale.US) }
                    val compatibleLoaders = compatibleModLoadersForRuntime(runtimeKey)
                    val matchedLoaders = categories.filter { it in modLoaderLabels.keys }

                    val serverSide = item.optString("server_side").lowercase(Locale.US)
                    val isSupportedMod = serverSide != "unsupported"
                    val modSupportMessage = when (type) {
                        ContentType.MODS -> {
                            if (!supportsMods(runtimeKey)) {
                                "Switch this server to Fabric, Quilt, Forge, or NeoForge to install mods."
                            } else {
                                val loaderLabel = matchedLoaders.firstNotNullOfOrNull { modLoaderLabels[it] }
                                if (loaderLabel != null && compatibleLoaders.isNotEmpty() && matchedLoaders.none { it in compatibleLoaders }) {
                                    val requiredLoaders = matchedLoaders.mapNotNull { modLoaderLabels[it] }.distinct().joinToString(" / ")
                                    "Requires $requiredLoaders. Current runtime is ${runtimeLabel(minecraftVersion)}."
                                } else if (loaderLabel != null) {
                                    "$loaderLabel server-side mod."
                                } else {
                                    "Server-side mod."
                                }
                            }
                        }
                        else -> null
                    }

                    if (type == ContentType.MODS && !isSupportedMod) {
                        continue
                    }

                    add(
                        RemoteCatalogItem(
                            source = MODRINTH_PROVIDER,
                            projectId = projectId,
                            title = title,
                            slug = slug,
                            iconUrl = item.optString("icon_url").takeIf { it.isNotBlank() },
                            description = item.optString("description").ifBlank { "No description provided." },
                            downloads = item.optLong("downloads"),
                            author = item.optString("author").takeIf { it.isNotBlank() },
                            canInstall = when (type) {
                                ContentType.MODS -> {
                                    isSupportedMod &&
                                        supportsMods(runtimeKey) &&
                                        (matchedLoaders.isEmpty() || matchedLoaders.any { it in compatibleLoaders })
                                }
                                else -> true
                            },
                            supportMessage = modSupportMessage,
                            isSupported = if (type == ContentType.MODS) isSupportedMod else true
                        )
                    )
                }
            }
        }
    }

    private suspend fun searchHangarPlugins(
        context: Context,
        query: String,
        minecraftVersion: String,
        limit: Int
    ): List<RemoteCatalogItem> {
        throttleProvider(HANGAR_PROVIDER, minimumGapMs = 300L)

        val url = buildString {
            append("$HANGAR_BASE_URL/projects?platform=PAPER")
            append("&version=${URLEncoder.encode(minecraftVersion, "UTF-8")}")
            append("&limit=$limit")
            append("&sort=${if (query.isBlank()) "downloads" else "updated"}")
            if (query.isNotBlank()) {
                append("&query=${URLEncoder.encode(query, "UTF-8")}")
            }
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent())
            .cacheControl(CacheControl.Builder().maxAge(10, TimeUnit.MINUTES).build())
            .build()

        return getHttpClient(context).newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Could not load the online catalog right now (HTTP ${response.code}).")
            }

            val payload = response.body?.string().orEmpty()
            if (payload.isBlank()) return emptyList()

            val root = JSONObject(payload)
            val result = root.optJSONArray("result") ?: return emptyList()
            return buildList {
                for (indexValue in 0 until result.length()) {
                    val item = result.optJSONObject(indexValue) ?: continue
                    val namespace = item.optJSONObject("namespace") ?: continue
                    val slug = namespace.optString("slug")
                    val owner = namespace.optString("owner")
                    val title = item.optString("name").ifBlank { slug }
                    if (slug.isBlank() || owner.isBlank() || title.isBlank()) continue

                    add(
                        RemoteCatalogItem(
                            source = HANGAR_PROVIDER,
                            projectId = slug,
                            title = title,
                            slug = slug,
                            iconUrl = item.optString("avatarUrl").takeIf { it.isNotBlank() },
                            description = item.optString("description").ifBlank { "No description provided." },
                            downloads = item.optJSONObject("stats")?.optLong("downloads") ?: 0L,
                            author = owner,
                            owner = owner
                        )
                    )
                }
            }
        }
    }

    private suspend fun resolveModrinthDownload(
        context: Context,
        item: RemoteCatalogItem,
        type: ContentType,
        minecraftVersion: String,
        runtimeKey: String
    ): DownloadCandidate? {
        throttleProvider(MODRINTH_PROVIDER, minimumGapMs = 250L)

        val loaders = when (type) {
            ContentType.PLUGINS -> JSONArray(paperCompatibleLoaders.toList()).toString()
            ContentType.RESOURCE_PACKS -> "[\"minecraft\"]"
            ContentType.MODS -> JSONArray(compatibleModLoadersForRuntime(runtimeKey)).toString()
        }
        if (type == ContentType.MODS && !supportsMods(runtimeKey)) return null
        val gameVersions = JSONArray(listOf(minecraftVersion)).toString()
        val url = buildString {
            append("$MODRINTH_BASE_URL/project/${item.projectId}/version")
            append("?game_versions=${URLEncoder.encode(gameVersions, "UTF-8")}")
            append("&include_changelog=false")
            if (loaders != "[]") {
                append("&loaders=${URLEncoder.encode(loaders, "UTF-8")}")
            }
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent())
            .cacheControl(CacheControl.Builder().maxAge(10, TimeUnit.MINUTES).build())
            .build()

        return getHttpClient(context).newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null

            val versions = JSONArray(body)
            var familyMatch: DownloadCandidate? = null
            var fallback: DownloadCandidate? = null

            for (indexValue in 0 until versions.length()) {
                val version = versions.optJSONObject(indexValue) ?: continue
                val candidate = extractPrimaryFile(version, defaultExtension(type)) ?: continue
                val declaredVersions = jsonArrayStrings(version.optJSONArray("game_versions"))

                if (declaredVersions.any { it == minecraftVersion }) {
                    return candidate
                }

                if (familyMatch == null && supportsRequestedVersion(declaredVersions, minecraftVersion)) {
                    familyMatch = candidate
                    continue
                }

                if (fallback == null) {
                    fallback = candidate
                }
            }

            return familyMatch ?: fallback
        }
    }

    private suspend fun resolveHangarDownload(
        context: Context,
        item: RemoteCatalogItem,
        minecraftVersion: String
    ): DownloadCandidate? {
        val owner = item.owner ?: item.author ?: return null

        throttleProvider(HANGAR_PROVIDER, minimumGapMs = 300L)

        val url = buildString {
            append("$HANGAR_BASE_URL/projects/")
            append(URLEncoder.encode(owner, "UTF-8"))
            append("/")
            append(URLEncoder.encode(item.slug, "UTF-8"))
            append("/versions?platform=PAPER")
            append("&platformVersion=${URLEncoder.encode(minecraftVersion, "UTF-8")}")
            append("&limit=12")
            append("&offset=0")
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent())
            .cacheControl(CacheControl.Builder().maxAge(10, TimeUnit.MINUTES).build())
            .build()

        return getHttpClient(context).newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null

            val root = JSONObject(body)
            val versions = root.optJSONArray("result") ?: return null

            var fallback: DownloadCandidate? = null
            for (indexValue in 0 until versions.length()) {
                val version = versions.optJSONObject(indexValue) ?: continue
                val channel = version.optJSONObject("channel")
                val flags = channel?.optJSONArray("flags")
                val unstable = jsonArrayStrings(flags).any { it.equals("UNSTABLE", ignoreCase = true) }

                val paperDownload = version.optJSONObject("downloads")
                    ?.optJSONObject("PAPER")
                    ?: continue
                val downloadUrl = paperDownload.optString("downloadUrl")
                val fileName = paperDownload.optJSONObject("fileInfo")?.optString("name")
                if (downloadUrl.isBlank() || fileName.isNullOrBlank()) continue

                val candidate = DownloadCandidate(downloadUrl = downloadUrl, fileName = fileName)
                if (!unstable) {
                    return candidate
                }
                if (fallback == null) {
                    fallback = candidate
                }
            }

            fallback
        }
    }

    private fun readArchiveMetadata(file: File): ArchiveMetadata {
        if (!file.isFile) return ArchiveMetadata()
        return runCatching {
            java.util.zip.ZipFile(file).use { zip ->
                zip.getEntry("paper-plugin.yml")
                    ?.let { entry ->
                        val content = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        return@use ArchiveMetadata(
                            kind = ArchiveKind.PLUGIN,
                            name = readYamlValue(content, "name"),
                            version = readYamlValue(content, "version")
                        )
                    }

                zip.getEntry("plugin.yml")
                    ?.let { entry ->
                        val content = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        return@use ArchiveMetadata(
                            kind = ArchiveKind.PLUGIN,
                            name = readYamlValue(content, "name"),
                            version = readYamlValue(content, "version")
                        )
                    }

                zip.getEntry("bungee.yml")
                    ?.let { entry ->
                        val content = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        return@use ArchiveMetadata(
                            kind = ArchiveKind.PLUGIN,
                            name = readYamlValue(content, "name"),
                            version = readYamlValue(content, "version")
                        )
                    }

                zip.getEntry("fabric.mod.json")
                    ?.let { entry ->
                        val content = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        val json = JSONObject(content)
                        return@use ArchiveMetadata(
                            kind = ArchiveKind.FABRIC_MOD,
                            name = json.optString("name").takeIf { it.isNotBlank() } ?: json.optString("id"),
                            version = json.optString("version").takeIf { it.isNotBlank() }
                        )
                    }

                zip.getEntry("META-INF/neoforge.mods.toml")
                    ?.let { entry ->
                        val content = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        return@use ArchiveMetadata(
                            kind = ArchiveKind.NEOFORGE_MOD,
                            name = readTomlValue(content, "displayName") ?: readTomlValue(content, "modId"),
                            version = readTomlValue(content, "version")
                        )
                    }

                zip.getEntry("META-INF/mods.toml")
                    ?.let { entry ->
                        val content = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        return@use ArchiveMetadata(
                            kind = ArchiveKind.FORGE_MOD,
                            name = readTomlValue(content, "displayName") ?: readTomlValue(content, "modId"),
                            version = readTomlValue(content, "version")
                        )
                    }

                ArchiveMetadata()
            }
        }.getOrDefault(ArchiveMetadata())
    }

    private fun readYamlValue(content: String, key: String): String? {
        return content.lineSequence()
            .map { it.substringBefore('#').trim() }
            .firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(':')
            ?.trim()
            ?.trim('"', '\'')
            ?.takeIf { it.isNotBlank() }
    }

    private fun readTomlValue(content: String, key: String): String? {
        return Regex("""(?m)^\s*$key\s*=\s*["']([^"']+)["']\s*$""")
            .find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
    }

    private fun buildVersionLabel(metadata: ArchiveMetadata): String {
        if (!metadata.version.isNullOrBlank() && metadata.kind.label.isNotBlank()) {
            return "${metadata.kind.label} ${metadata.version}"
        }
        if (!metadata.version.isNullOrBlank()) {
            return metadata.version
        }
        return metadata.kind.label
    }

    private fun validateInstalledFile(file: File, type: ContentType, runtimeKey: String = ""): String? {
        return when (type) {
            ContentType.PLUGINS -> {
                val metadata = readArchiveMetadata(file)
                when (metadata.kind) {
                    ArchiveKind.FABRIC_MOD,
                    ArchiveKind.FORGE_MOD,
                    ArchiveKind.NEOFORGE_MOD -> "This file is a mod jar. The current server runtime cannot load it as a plugin."
                    ArchiveKind.UNKNOWN -> "This .jar is not a valid Paper plugin archive (missing plugin metadata or corrupted file)."
                    else -> null
                }
            }
            ContentType.MODS -> {
                if (!supportsMods(runtimeKey)) {
                    return "This server does not support mods. Switch to Fabric, Quilt, Forge, or NeoForge first."
                }
                val metadata = readArchiveMetadata(file)
                val compatibleLoaders = compatibleModLoadersForRuntime(runtimeKey)
                when (metadata.kind) {
                    ArchiveKind.PLUGIN -> "This file is a plugin. Install it from the Plugins tab instead."
                    ArchiveKind.FABRIC_MOD -> {
                        if ("fabric" in compatibleLoaders) null
                        else "This is a Fabric mod but the current server runtime is ${runtimeLabel(runtimeKey)}."
                    }
                    ArchiveKind.FORGE_MOD -> {
                        if ("forge" in compatibleLoaders) null
                        else "This is a Forge mod but the current server runtime is ${runtimeLabel(runtimeKey)}."
                    }
                    ArchiveKind.NEOFORGE_MOD -> {
                        if ("neoforge" in compatibleLoaders) null
                        else "This is a NeoForge mod but the current server runtime is ${runtimeLabel(runtimeKey)}."
                    }
                    ArchiveKind.UNKNOWN -> null // Allow unknown jars in mods folder for mod loaders
                }
            }
            ContentType.RESOURCE_PACKS -> null
        }
    }

    private fun validateFileName(fileName: String, type: ContentType): String? {
        val lower = fileName.lowercase(Locale.US)
        return when (type) {
            ContentType.PLUGINS,
            ContentType.MODS -> if (!lower.endsWith(".jar")) "Only .jar files are supported here." else null
            ContentType.RESOURCE_PACKS -> if (!lower.endsWith(".zip")) "Only .zip files are supported here." else null
        }
    }

    private fun resolveFileName(
        fileNameHint: String?,
        contentDisposition: String?,
        sourceUrl: String,
        type: ContentType
    ): String {
        val hinted = fileNameHint?.trim()?.takeIf { it.isNotBlank() }
        if (hinted != null) return hinted

        val fromHeader = contentDisposition
            ?.substringAfter("filename=", "")
            ?.trim()
            ?.trim('"', '\'')
            ?.takeIf { it.isNotBlank() }
        if (fromHeader != null) return fromHeader

        val fromUrl = sourceUrl.substringAfterLast('/').substringBefore('?').trim()
        if (fromUrl.isNotBlank()) return fromUrl

        return "item_${System.currentTimeMillis()}${defaultExtension(type)}"
    }

    private fun extractPrimaryFile(version: JSONObject, extension: String): DownloadCandidate? {
        val files = version.optJSONArray("files") ?: return null
        var fallback: DownloadCandidate? = null
        for (indexValue in 0 until files.length()) {
            val file = files.optJSONObject(indexValue) ?: continue
            val fileName = file.optString("filename")
            val fileUrl = file.optString("url")
            if (fileUrl.isBlank() || !fileName.lowercase(Locale.US).endsWith(extension)) continue

            val candidate = DownloadCandidate(downloadUrl = fileUrl, fileName = fileName)
            if (file.optBoolean("primary")) {
                return candidate
            }
            if (fallback == null) {
                fallback = candidate
            }
        }
        return fallback
    }

    private fun defaultExtension(type: ContentType): String {
        return when (type) {
            ContentType.PLUGINS,
            ContentType.MODS -> ".jar"
            ContentType.RESOURCE_PACKS -> ".zip"
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun buildModrinthFacets(type: ContentType): String {
        return when (type) {
            ContentType.PLUGINS -> """[["project_type:plugin"]]"""
            ContentType.MODS -> """[["project_type:mod"]]"""
            ContentType.RESOURCE_PACKS -> """[["project_type:resourcepack"]]"""
        }
    }

    private fun supportsRequestedVersion(versions: List<String>, minecraftVersion: String): Boolean {
        if (versions.isEmpty()) return true
        return versions.any { isCompatibleVersionFamily(it, minecraftVersion) }
    }

    private fun compatibleModLoadersForRuntime(versionId: String): List<String> {
        val normalizedVersion = versionId.lowercase(Locale.US)
        return when {
            normalizedVersion.contains("neoforge") -> listOf("neoforge")
            normalizedVersion.contains("forge") -> listOf("forge")
            normalizedVersion.contains("quilt") -> listOf("quilt", "fabric")
            normalizedVersion.contains("fabric") -> listOf("fabric")
            else -> emptyList()
        }
    }

    private fun runtimeLabel(versionId: String): String {
        val normalizedVersion = versionId.lowercase(Locale.US)
        return when {
            normalizedVersion.contains("neoforge") -> "NeoForge"
            normalizedVersion.contains("forge") -> "Forge"
            normalizedVersion.contains("quilt") -> "Quilt"
            normalizedVersion.contains("fabric") -> "Fabric"
            else -> "Vanilla/Paper"
        }
    }

    private fun isCompatibleVersionFamily(candidateVersion: String, requestedVersion: String): Boolean {
        if (candidateVersion == requestedVersion) return true
        return versionFamily(candidateVersion) == versionFamily(requestedVersion)
    }

    private fun versionFamily(version: String): String? {
        val match = Regex("""^(\d+)\.(\d+)""").find(version) ?: return null
        return "${match.groupValues[1]}.${match.groupValues[2]}"
    }

    private fun mergeCatalogResults(
        items: List<RemoteCatalogItem>,
        limit: Int,
        blankQuery: Boolean
    ): List<RemoteCatalogItem> {
        val seen = linkedSetOf<String>()
        val unique = buildList {
            val ordered = if (blankQuery) {
                items.sortedByDescending { it.downloads }
            } else {
                items
            }

            for (item in ordered) {
                val dedupeKey = normalizeCatalogKey(item.title.ifBlank { item.slug })
                if (!seen.add(dedupeKey)) continue
                add(item)
                if (size >= limit) break
            }
        }
        return unique
    }

    private fun normalizeCatalogKey(value: String): String {
        return value.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "")
    }

    private suspend fun installManagedPluginIfMissing(
        context: Context,
        worldName: String,
        item: RemoteCatalogItem,
        onProgress: (String) -> Unit
    ): Result<Unit> {
        if (isManagedPluginInstalled(context, worldName, item.projectId)) {
            ensureManagedPluginEnabled(context, worldName, item.projectId)
            return Result.success(Unit)
        }
        onProgress("Installing ${item.title} bridge plugin…")
        Log.d("PluginManager", "Installing managed plugin: ${item.title} (${item.projectId})")
        return withTimeoutOrNull(20000L) {
            installRemoteItem(
                context = context,
                item = item,
                worldName = worldName,
                type = ContentType.PLUGINS,
                onProgress = {}
            ).map { Unit }
        } ?: Result.failure(Exception("Timeout installing ${item.title}"))
    }

    private suspend fun installManagedPluginFromCandidatesIfMissing(
        context: Context,
        worldName: String,
        candidates: List<RemoteCatalogItem>,
        onProgress: (String) -> Unit
    ): Result<Unit> {
        val alreadyInstalled = candidates.any { isManagedPluginInstalled(context, worldName, it.projectId) }
        if (alreadyInstalled) {
            candidates.forEach { ensureManagedPluginEnabled(context, worldName, it.projectId) }
            return Result.success(Unit)
        }

        var lastError: Throwable? = null
        for (item in candidates) {
            val res = installManagedPluginIfMissing(context, worldName, item, onProgress)
            if (res.isSuccess) return Result.success(Unit)
            lastError = res.exceptionOrNull()
        }
        return Result.failure(lastError ?: Exception("Failed to install any candidate for ${candidates.firstOrNull()?.title ?: "managed plugin"}"))
    }

    private suspend fun installManagedPluginFromDirectUrlIfMissing(
        context: Context,
        worldName: String,
        projectId: String,
        title: String,
        downloadUrl: String,
        fileNameHint: String,
        onProgress: (String) -> Unit
    ): Result<Unit> {
        if (isManagedPluginInstalled(context, worldName, projectId)) {
            ensureManagedPluginEnabled(context, worldName, projectId)
            return Result.success(Unit)
        }
        onProgress("Installing $title bridge plugin…")
        return installFromUrl(
            context = context,
            sourceUrl = downloadUrl,
            worldName = worldName,
            type = ContentType.PLUGINS,
            fileNameHint = fileNameHint,
            onProgress = {}
        ).map { Unit }
    }

    private suspend fun replaceManagedPluginFromUrl(
        context: Context,
        worldName: String,
        projectId: String,
        title: String,
        downloadUrl: String? = null,
        fileNameHint: String? = null,
        catalogItem: RemoteCatalogItem? = null
    ): Result<Unit> {
        val pluginsDir = getPluginsDir(context, worldName)
        val existing = pluginsDir.listFiles { f ->
            val n = f.name.lowercase()
            n.contains(projectId.lowercase()) && (n.endsWith(".jar") || n.endsWith(".jar.disabled"))
        }
        existing?.forEach { it.delete() }

        return if (downloadUrl != null) {
            installFromUrl(
                context = context,
                sourceUrl = downloadUrl,
                worldName = worldName,
                type = ContentType.PLUGINS,
                fileNameHint = fileNameHint,
                onProgress = {}
            ).map { Unit }
        } else if (catalogItem != null) {
            installRemoteItem(
                context = context,
                item = catalogItem,
                worldName = worldName,
                type = ContentType.PLUGINS,
                onProgress = {}
            ).map { Unit }
        } else {
            Result.failure(Exception("No download source for $title"))
        }
    }

    private fun ensureManagedPluginEnabled(context: Context, worldName: String, projectId: String) {
        val pluginsDir = getPluginsDir(context, worldName)
        pluginsDir.listFiles { file -> file.name.endsWith(".jar.disabled") }
            ?.forEach { file ->
                if (file.name.lowercase().contains(projectId.lowercase())) {
                    val enabledFile = java.io.File(pluginsDir, file.name.removeSuffix(".disabled"))
                    runCatching { file.renameTo(enabledFile) }
                }
            }
    }

    private fun isManagedPluginInstalled(context: Context, worldName: String, projectId: String): Boolean {
        val pluginsDir = getPluginsDir(context, worldName)
        return pluginsDir.listFiles()?.any {
            val n = it.name.lowercase()
            n.contains(projectId.lowercase()) && (n.endsWith(".jar") || n.endsWith(".jar.disabled"))
        } ?: false
    }

    private fun isManagedPluginEnabled(context: Context, worldName: String, projectId: String): Boolean {
        val pluginsDir = getPluginsDir(context, worldName)
        return pluginsDir.listFiles()?.any {
            val n = it.name.lowercase()
            n.contains(projectId.lowercase()) && n.endsWith(".jar")
        } ?: false
    }

    private fun isManagedBridgePlugin(plugin: Plugin): Boolean {
        val candidates = listOf(plugin.name, plugin.fileName)
            .map(::normalizeCatalogKey)
        return candidates.any { normalized ->
            builtInBridgeKeywords.any { keyword -> normalized.contains(keyword) }
        }
    }

    private fun isManagedBridgeCatalogItem(item: RemoteCatalogItem): Boolean {
        val candidates = listOf(item.projectId, item.slug, item.title)
            .map(::normalizeCatalogKey)
        return candidates.any { normalized ->
            builtInBridgeProjectIds.any { projectId -> normalized == projectId || normalized.contains(projectId) }
        }
    }

    private fun sanitizeFileName(fileName: String): String {
        return fileName.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }

    private fun jsonArrayStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (indexValue in 0 until array.length()) {
                val value = array.optString(indexValue)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun getCachedCatalog(context: Context, cacheKey: String): List<RemoteCatalogItem>? {
        val typeToken = object : com.google.gson.reflect.TypeToken<VersionCacheManager.CacheEntry<List<RemoteCatalogItem>>>() {}
        return VersionCacheManager.get(context, "catalog_$cacheKey", typeToken)
    }

    private fun putCachedCatalog(context: Context, cacheKey: String, items: List<RemoteCatalogItem>) {
        VersionCacheManager.put(context, "catalog_$cacheKey", items, TimeUnit.HOURS.toMillis(6))
    }

    private suspend fun throttleProvider(provider: String, minimumGapMs: Long) {
        val delayMs = synchronized(throttleLock) {
            val now = System.currentTimeMillis()
            val reservedAt = providerNextRequestAtMs[provider] ?: now
            val actualDelay = (reservedAt - now).coerceAtLeast(0L)
            providerNextRequestAtMs[provider] = now + actualDelay + minimumGapMs
            actualDelay
        }
        if (delayMs > 0) {
            delay(delayMs)
        }
    }

    private fun userAgent(): String {
        val versionName = BuildConfig.VERSION_NAME.takeIf { it.isNotBlank() } ?: "dev"
        return "${BuildConfig.APPLICATION_ID}/$versionName (Android)"
    }
}
