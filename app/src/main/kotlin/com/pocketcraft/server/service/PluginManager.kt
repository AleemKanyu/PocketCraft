package com.pocketcraft.server.service

import android.content.Context
import android.net.Uri
import com.pocketcraft.server.BuildConfig
import com.pocketcraft.server.data.model.Plugin
import com.pocketcraft.server.data.model.ServerType
import com.pocketcraft.server.server.BundledPluginInstaller
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
    private const val MEMORY_CACHE_TTL_MS = 3 * 60 * 1000L
    private const val HTTP_CACHE_BYTES = 12L * 1024L * 1024L
    private const val MODRINTH_PROVIDER = "modrinth"
    private const val HANGAR_PROVIDER = "hangar"
    private val builtInBridgeProjectIds = setOf("geyser", "viaversion", "chunky")
    private val builtInBridgeKeywords = setOf("geyser", "viaversion", "chunky")
    // Managed bridge plugins that MUST be real JARs — any file smaller than this threshold
    // is treated as a corrupted/truncated download and will be re-downloaded automatically.
    private val managedBridgePluginIds = setOf("geyser", "floodgate", "viaversion")
    private const val MANAGED_PLUGIN_MIN_VALID_BYTES = 512 * 1024L // 512 KB minimum for real bridge jars
    // Via* hooks the Netty pipeline and breaks same-version Java joins on 1.21.11+
    // (debug_subscription_request decode kicks). Keep them disabled for native hosting.
    private val incompatiblePluginTokens = listOf(
        "fastleafdecay",
        "inventoryprofiles",
        "viabackwards",
        "viarewind"
    )
    private val BLOCKED_PLUGINS = setOf("spark", "spark-bukkit")
    private val paperCompatibleLoaders = setOf("paper", "spigot", "purpur", "bukkit", "folia")
    private val modLoaderLabels = linkedMapOf(
        "fabric" to "Fabric",
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
        ).firstOrNull { it.exists() } ?: File(pluginsDir, "floodgate/config.yml")
    }

    fun getModsDir(context: Context, worldName: String): File =
        File(context.filesDir, "servers/worlds/$worldName/mods").also { it.mkdirs() }

    fun getResourcePacksDir(context: Context, worldName: String): File =
        File(context.filesDir, "servers/worlds/$worldName/resourcepacks").also { it.mkdirs() }

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
            ?.filterNot(::isHiddenPocketCraftPlugin)
            ?.sortedByDescending { it.sizeMb }
            ?: emptyList()
    }

    private fun isHiddenPocketCraftPlugin(plugin: Plugin): Boolean {
        val normalizedName = plugin.name.lowercase(Locale.US)
        val normalizedFile = plugin.fileName.lowercase(Locale.US)
        return normalizedName == "pocketcraftcompanion" ||
            normalizedFile.startsWith("pocketcraftcompanion.jar")
    }

    suspend fun ensureBedrockBridgePlugins(
        context: Context,
        worldName: String,
        onProgress: (String) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!supportsBundledBedrockBridge(context, worldName)) {
            disableManagedPlugin(context, worldName, "viaversion")
            return@withContext Result.failure(
                IllegalStateException("Bedrock bridge is bundled only for Paper and Purpur worlds right now.")
            )
        }

        removeIncompatiblePlugins(context, worldName)
        onProgress("Installing bundled Bedrock bridge plugins...")
        val serverDir = ServerFileManager.getServerDir(context, worldName)
        BundledPluginInstaller.installBundledPlugins(context, serverDir)

        ensureManagedPluginEnabled(context, worldName, "geyser")
        ensureManagedPluginEnabled(context, worldName, "floodgate")
        ensureManagedPluginEnabled(context, worldName, "viaversion")
        preserveFloodgateKey(context, worldName)

        enforceBedrockBridgeLocalConfig(context, worldName)

        Result.success(Unit)
    }

    suspend fun autoUpdateBundledPlugins(
        context: Context,
        worldName: String,
        onProgress: (String) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!supportsBundledBedrockBridge(context, worldName)) {
            disableManagedPlugin(context, worldName, "viaversion")
            return@withContext Result.failure(
                IllegalStateException("Bedrock bridge is bundled only for Paper and Purpur worlds right now.")
            )
        }

        onProgress("Refreshing bundled Bedrock bridge plugins...")
        val serverDir = ServerFileManager.getServerDir(context, worldName)
        BundledPluginInstaller.reinstallBundledPlugins(context, serverDir)

        ensureManagedPluginEnabled(context, worldName, "geyser")
        ensureManagedPluginEnabled(context, worldName, "floodgate")
        ensureManagedPluginEnabled(context, worldName, "viaversion")
        preserveFloodgateKey(context, worldName)
        
        enforceBedrockBridgeLocalConfig(context, worldName)
        
        Result.success(Unit)
    }

    suspend fun removeChunkyPlugin(
        context: Context,
        worldName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val pluginsDir = getPluginsDir(context, worldName)
        pluginsDir.listFiles()?.forEach { file ->
            if (file.name.contains("chunky", ignoreCase = true) && file.name.endsWith(".jar")) {
                file.delete()
            }
        }
        Result.success(Unit)
    }

    fun enforceBedrockBridgeLocalConfig(context: Context, worldName: String) {
        val pluginsDir = getPluginsDir(context, worldName)
        val floodgateConfigFile = getFloodgateConfigFile(context, worldName)
        val floodgateDirName = floodgateConfigFile.parentFile?.name?.takeIf { it.isNotBlank() } ?: "floodgate"
        val floodgateKeyPath = when {
            File(pluginsDir, "floodgate/key.pem").exists() -> "../floodgate/key.pem"
            File(pluginsDir, "Floodgate/key.pem").exists() -> "../Floodgate/key.pem"
            else -> "../$floodgateDirName/key.pem"
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
        // Geyser status passthrough every second is unnecessary for relay hosting and adds
        // extra background status queries while the phone is already bandwidth-constrained.
        updated = ensureTopLevelYamlValue(updated, "ping-passthrough-interval", "3")
        updated = ensureTopLevelYamlValue(updated, "async-motd", "false")
        updated = ensureTopLevelYamlValue(updated, "cache-chunks", "true")
        updated = ensureTopLevelYamlValue(updated, "use-native-transport", "false")
        updated = ensureTopLevelYamlValue(updated, "max-auto-connect-attempts", "5")
        updated = ensureTopLevelYamlValue(updated, "forward-hostname", "false")
        updated = ensureTopLevelYamlValue(updated, "show-cooldown", "disabled")
        updated = ensureTopLevelYamlValue(updated, "pending-authentication-timeout", "30")
        updated = ensureTopLevelYamlValue(updated, "above-bedrock-nether-building", "true")
        updated = ensureYamlPathValue(updated, listOf("advanced", "bedrock"), "validate-bedrock-login", "false")
        updated = ensureYamlPathValue(updated, listOf("advanced", "bedrock"), "mtu", "1200")
        updated = ensureYamlSectionValue(updated, "advanced", "floodgate-key-file", floodgateKeyPath)
        updated = ensureYamlSectionValue(updated, "java", "address", "auto")
        updated = ensureYamlSectionValue(updated, "java", "port", "25565")
        updated = ensureYamlSectionValue(updated, "java", "auth-type", "floodgate")
        updated = ensureYamlSectionValue(updated, "java", "forward-hostname", "false")

        // Older Geyser configs use a dedicated remote section.
        if (updated.lines().any { it.trim() == "remote:" }) {
            // Let Geyser resolve the active Paper bind target instead of forcing loopback.
            updated = ensureYamlSectionValue(updated, "remote", "address", "auto")
            updated = ensureYamlSectionValue(updated, "remote", "port", "25565")
            updated = ensureYamlSectionValue(updated, "remote", "auth-type", "floodgate")
        }

        // Handle newer config variants that may expose auth in an additional server section.
        if (updated.lines().any { it.trim() == "server:" }) {
            updated = ensureYamlSectionValue(updated, "server", "auth-type", "floodgate")
        }

        updated = ensureYamlSectionValue(updated, "motd", "passthrough-motd", "false")
        updated = ensureYamlSectionValue(updated, "motd", "passthrough-player-counts", "false")

        if (updated != original) {
            geyserConfigFile.writeText(updated)
        }

        floodgateConfigFile.parentFile?.mkdirs()
        val floodgateOriginal = if (floodgateConfigFile.exists()) {
            runCatching { floodgateConfigFile.readText() }.getOrDefault("")
        } else {
            ""
        }
        var floodgateUpdated = floodgateOriginal
        floodgateUpdated = ensureTopLevelYamlValue(floodgateUpdated, "send-floodgate-data", "true")
        floodgateUpdated = ensureTopLevelYamlValue(floodgateUpdated, "username-prefix", ".")
        // Android cannot load Floodgate's optional database implementation. Account
        // linking is not required for Floodgate authentication, so leave it disabled.
        floodgateUpdated = ensureYamlSectionValue(floodgateUpdated, "player-link", "enabled", "false")
        floodgateUpdated = ensureYamlSectionValue(floodgateUpdated, "player-link", "require-link", "false")
        // Different Floodgate builds have used both keys; keep both aligned to avoid
        // falling back to global account linking on older config layouts.
        floodgateUpdated = ensureYamlSectionValue(floodgateUpdated, "player-link", "enable-global-linking", "false")
        floodgateUpdated = ensureYamlSectionValue(floodgateUpdated, "player-link", "use-global-linking", "false")
        floodgateUpdated = ensureYamlSectionValue(floodgateUpdated, "player-link", "link-code-timeout", "60")
        if (floodgateUpdated != floodgateOriginal) {
            floodgateConfigFile.writeText(floodgateUpdated)
        }
    }

    /**
     * Preserves the Floodgate encryption key (key.pem) across server resets.
     * If the key file is deleted or regenerated, all existing player links are
     * invalidated — causing Bedrock player data resets even with player-link enabled.
     * The backup is stored outside the server world folder so it survives world resets.
     */
    fun preserveFloodgateKey(context: Context, worldName: String) {
        val serverDir = ServerFileManager.getServerDir(context, worldName)
        val backupDir = File(context.filesDir, "servers/$worldName").also { it.mkdirs() }
        val floodgateDirs = listOf(
            File(serverDir, "plugins/floodgate"),
            File(serverDir, "plugins/Floodgate")
        )
        val keyFile = floodgateDirs.map { File(it, "key.pem") }.firstOrNull { it.exists() }
        val defaultKeyFile = File(floodgateDirs.first(), "key.pem")
        val backupFile = File(backupDir, "floodgate_key_backup.pem")

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
            (!line.startsWith(" ") && !line.startsWith("\t")) && (line.trimStart() == "$key: $value" || line.trimStart().startsWith("$key:"))
        }
        if (keyIndex != -1) {
            lines[keyIndex] = "$key: ${formatYamlScalar(value)}"
        } else {
            if (lines.size == 1 && lines[0].isBlank()) lines.clear()
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines += ""
            lines += "$key: ${formatYamlScalar(value)}"
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
            lines += "  $key: ${formatYamlScalar(value)}"
            return lines.joinToString("\n").trimEnd() + "\n"
        }

        val sectionIndent = lines[sectionStart].takeWhile { it == ' ' || it == '\t' }
        var sectionEnd = lines.size
        for (index in (sectionStart + 1) until lines.size) {
            val line = lines[index]
            if (line.trim().isBlank() || line.trim().startsWith("#")) continue
            val indent = line.takeWhile { it == ' ' || it == '\t' }
            if (indent.length <= sectionIndent.length) {
                sectionEnd = index
                break
            }
        }

        val keyIndex = ((sectionStart + 1) until sectionEnd).firstOrNull { index ->
            val line = lines[index]
            val indent = line.takeWhile { it == ' ' || it == '\t' }
            indent.length > sectionIndent.length && line.trimStart().startsWith("$key:")
        }

        if (keyIndex != null) {
            val existingIndent = lines[keyIndex].takeWhile { it == ' ' || it == '\t' }
            lines[keyIndex] = "$existingIndent$key: ${formatYamlScalar(value)}"
        } else {
            val targetIndent = sectionIndent + "  "
            lines.add(sectionEnd, "$targetIndent$key: ${formatYamlScalar(value)}")
        }

        return lines.joinToString("\n").trimEnd() + "\n"
    }

    private fun ensureYamlPathValue(
        original: String,
        path: List<String>,
        key: String,
        value: String
    ): String {
        val lines = original
            .ifBlank { "" }
            .split('\n')
            .toMutableList()

        if (lines.size == 1 && lines[0].isBlank()) {
            lines.clear()
        }

        var searchStart = 0
        var searchEnd = lines.size

        path.forEachIndexed { depth, section ->
            val indent = "  ".repeat(depth)
            val sectionIndex = (searchStart until searchEnd).firstOrNull { index ->
                val line = lines[index]
                line.trim() == "$section:" && leadingYamlIndent(line) == indent.length
            }

            val actualIndex = if (sectionIndex != null) {
                sectionIndex
            } else {
                val insertionIndex = searchEnd
                lines.add(insertionIndex, "$indent$section:")
                searchEnd += 1
                insertionIndex
            }

            searchStart = actualIndex + 1
            searchEnd = findYamlSectionEnd(lines, actualIndex)
        }

        val keyIndent = "  ".repeat(path.size)
        val keyIndex = (searchStart until searchEnd).firstOrNull { index ->
            val line = lines[index]
            leadingYamlIndent(line) == keyIndent.length && line.trimStart().startsWith("$key:")
        }

        val formatted = formatYamlScalar(value)
        if (keyIndex != null) {
            lines[keyIndex] = "$keyIndent$key: $formatted"
        } else {
            lines.add(searchEnd, "$keyIndent$key: $formatted")
        }

        return lines.joinToString("\n").trimEnd() + "\n"
    }

    private fun findYamlSectionEnd(
        lines: List<String>,
        sectionStart: Int
    ): Int {
        val sectionIndent = leadingYamlIndent(lines[sectionStart])
        for (index in (sectionStart + 1) until lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) continue
            if (leadingYamlIndent(line) <= sectionIndent) {
                return index
            }
        }
        return lines.size
    }

    private fun leadingYamlIndent(line: String): Int {
        return line.takeWhile { it == ' ' || it == '\t' }.length
    }

    private fun String.stripYamlQuotes(): String {
        return trim().removePrefix("\"").removeSuffix("\"").removePrefix("'").removeSuffix("'")
    }

    fun isBedrockBridgeEnabled(context: Context, worldName: String): Boolean {
        if (!supportsBundledBedrockBridge(context, worldName)) return false
        val hasGeyser = isManagedPluginEnabled(context, worldName, "geyser")
        val hasFloodgate = isManagedPluginEnabled(context, worldName, "floodgate")
        return hasGeyser && hasFloodgate
    }

    fun getRuntimeKeyForWorld(context: Context, worldName: String): String {
        val serverDir = ServerFileManager.getServerDirNoCreate(context, worldName)
        if (!serverDir.exists()) return "paper"
        val props = ServerPropertiesHelper.readProperties(serverDir, persistDefaults = false)
        val serverType = com.pocketcraft.server.data.model.ServerType.fromString(props.getProperty("pocketcraft-server-type"))
        val version = props.getProperty("pocketcraft-game-version").orEmpty()
        return when (serverType) {
            com.pocketcraft.server.data.model.ServerType.FABRIC -> "fabric-$version"
            com.pocketcraft.server.data.model.ServerType.PAPER -> "paper-$version"
            com.pocketcraft.server.data.model.ServerType.PURPUR -> "purpur-$version"
            com.pocketcraft.server.data.model.ServerType.MODPACK -> {
                val loader = props.getProperty("pocketcraft-modpack-loader").orEmpty().lowercase()
                when {
                    loader.contains("quilt") -> "quilt-$version"
                    loader.contains("fabric") -> "fabric-$version"
                    loader.contains("neoforge") -> "neoforge-$version"
                    loader.contains("forge") -> "forge-$version"
                    else -> "modpack-$version"
                }
            }
        }
    }

    fun supportsMods(worldNameOrRuntimeKey: String): Boolean {
        val normalized = worldNameOrRuntimeKey.lowercase(Locale.US)
        if (normalized.contains("paper") || normalized.contains("purpur")) {
            return false
        }
        return true
    }

    fun supportsFabricMods(worldNameOrRuntimeKey: String): Boolean {
        val normalized = worldNameOrRuntimeKey.lowercase(Locale.US)
        return normalized.contains("fabric") || normalized.contains("quilt")
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
        Result.failure(Exception("Direct in-app downloads are disabled. Open the official page in your browser, download the file there, then import it from device storage."))
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
        Result.failure(Exception("Direct in-app downloads are disabled. Open ${item.title}'s official page, download the file in your browser, then import it from device storage."))
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
                mergeCatalogResults(
                    items = searchModrinthCatalog(
                        context = context,
                        type = type,
                        query = normalizedQuery,
                        minecraftVersion = minecraftVersion,
                        runtimeKey = runtimeKey,
                        limit = limit
                    ),
                    limit = limit,
                    blankQuery = true
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
        val index = "downloads"
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
                                "Switch this server to Fabric or Quilt to install mods."
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
                // Managed bridge plugins (Geyser, Floodgate, ViaVersion) are known-valid but
                // may use non-standard plugin descriptors or platform-specific loaders.
                // Reject only obvious stubs (< 512 KB) rather than validating their internals.
                val fileName = file.name.lowercase()
                val isBridgePlugin = managedBridgePluginIds.any { fileName.contains(it) }
                if (isBridgePlugin) {
                    return if (file.length() < MANAGED_PLUGIN_MIN_VALID_BYTES) {
                        "Downloaded $fileName is too small (${file.length()} bytes) — download was likely truncated. Will retry."
                    } else {
                        null // Accept without further inspection
                    }
                }

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
                    return "This server does not support mods. Switch to Fabric or Quilt first."
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

    fun getFileNameFromUri(context: Context, uri: Uri): String? {
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
            normalizedVersion.contains("quilt") -> listOf("quilt", "fabric")
            normalizedVersion.contains("fabric") -> listOf("fabric")
            normalizedVersion.contains("neoforge") -> listOf("neoforge")
            normalizedVersion.contains("forge") -> listOf("forge")
            else -> emptyList()
        }
    }

    private fun runtimeLabel(versionId: String): String {
        val normalizedVersion = versionId.lowercase(Locale.US)
        return when {
            normalizedVersion.contains("quilt") -> "Quilt"
            normalizedVersion.contains("fabric") -> "Fabric"
            normalizedVersion.contains("neoforge") -> "NeoForge"
            normalizedVersion.contains("forge") -> "Forge"
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
        onProgress("${item.title} must be imported manually.")
        return Result.failure(Exception("${item.title} is not bundled and cannot be downloaded in-app."))
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
        onProgress("$title must be imported manually.")
        return Result.failure(Exception("$title is not bundled and cannot be downloaded in-app."))
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

        return Result.failure(Exception("$title cannot be updated in-app. Bundle a newer version with an app update."))
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

    private fun disableManagedPlugin(context: Context, worldName: String, projectId: String) {
        val pluginsDir = getPluginsDir(context, worldName)
        pluginsDir.listFiles()
            ?.filter { file ->
                val name = file.name.lowercase()
                name.contains(projectId.lowercase()) && name.endsWith(".jar")
            }
            ?.forEach { file ->
                val disabledFile = File(pluginsDir, "${file.name}.disabled")
                if (disabledFile.exists()) {
                    runCatching { file.delete() }
                } else {
                    runCatching { file.renameTo(disabledFile) }
                }
            }
    }

    private fun isManagedPluginInstalled(context: Context, worldName: String, projectId: String): Boolean {
        val pluginsDir = getPluginsDir(context, worldName)
        val normalizedId = projectId.lowercase()
        val files = pluginsDir.listFiles() ?: return false
        return files.any { file ->
            val n = file.name.lowercase()
            val nameMatches = n.contains(normalizedId) && (n.endsWith(".jar") || n.endsWith(".jar.disabled"))
            if (!nameMatches) return@any false

            // For known managed bridge plugins, a jar smaller than MANAGED_PLUGIN_MIN_VALID_BYTES
            // is a corrupted/truncated download stub. Treat it as "not installed" so the
            // real jar gets re-downloaded automatically on the next server start.
            if (managedBridgePluginIds.contains(normalizedId) && file.length() < MANAGED_PLUGIN_MIN_VALID_BYTES) {
                android.util.Log.w(
                    "PluginManager",
                    "Detected corrupt/truncated $projectId jar (${file.length()} bytes < $MANAGED_PLUGIN_MIN_VALID_BYTES min). " +
                    "Deleting stub and scheduling re-download."
                )
                runCatching { file.delete() }
                return@any false
            }

            true
        }
    }

    fun isDependencyInstalled(context: Context, worldName: String, slug: String, title: String, projectId: String): Boolean {
        val slugLower = slug.lowercase()
        val titleLower = title.lowercase()
        val idLower = projectId.lowercase()

        // Check mods directory first
        val modsDir = getModsDir(context, worldName)
        val modsFiles = modsDir.listFiles()
        if (modsFiles != null) {
            val found = modsFiles.any { file ->
                val n = file.name.lowercase()
                n.contains(slugLower) || n.contains(idLower) || 
                    n.contains(titleLower.replace(" ", "")) || 
                    n.contains(titleLower.replace("-", ""))
            }
            if (found) return true
        }

        // Also check plugins directory
        val pluginsDir = getPluginsDir(context, worldName)
        val pluginsFiles = pluginsDir.listFiles()
        if (pluginsFiles != null) {
            return pluginsFiles.any { file ->
                val n = file.name.lowercase()
                n.contains(slugLower) || n.contains(idLower) || 
                    n.contains(titleLower.replace(" ", "")) || 
                    n.contains(titleLower.replace("-", ""))
            }
        }
        return false
    }

    private fun isManagedPluginEnabled(context: Context, worldName: String, projectId: String): Boolean {
        val pluginsDir = getPluginsDir(context, worldName)
        return pluginsDir.listFiles()?.any {
            val n = it.name.lowercase()
            n.contains(projectId.lowercase()) && n.endsWith(".jar")
        } ?: false
    }

    private fun supportsBundledBedrockBridge(context: Context, worldName: String): Boolean {
        val serverDir = ServerFileManager.getServerDirNoCreate(context, worldName)
        if (!serverDir.exists()) return true
        val props = ServerPropertiesHelper.readProperties(serverDir, persistDefaults = false)
        return when (ServerType.fromString(props.getProperty("pocketcraft-server-type"))) {
            ServerType.PAPER, ServerType.PURPUR -> true
            ServerType.FABRIC, ServerType.MODPACK -> false
        }
    }

    fun isPreinstalledPlugin(plugin: Plugin): Boolean {
        val normalized = plugin.name.lowercase(Locale.US)
        return normalized.contains("geyser") || normalized.contains("viaversion") || normalized.contains("floodgate")
    }

    private val resolvedProjectCache = java.util.concurrent.ConcurrentHashMap<String, RemoteCatalogItem>()

    suspend fun resolveModrinthProjectByName(
        context: Context,
        name: String,
        type: ContentType
    ): RemoteCatalogItem? = withContext(Dispatchers.IO) {
        val cacheKey = "${type.name}|$name"
        resolvedProjectCache[cacheKey]?.let { return@withContext it }

        // Clean up the name (remove file extensions, version numbers, brackets, etc.)
        var cleanName = name.replace(Regex("\\.jar$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[-_]?[0-9\\.]+(-SNAPSHOT)?[-_]?.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[\\[\\(].*?[\\]\\)]"), "")
            .trim()
        if (cleanName.isBlank()) cleanName = name

        try {
            val facets = buildModrinthFacets(type)
            val encodedQuery = java.net.URLEncoder.encode(cleanName, "UTF-8")
            val encodedFacets = java.net.URLEncoder.encode(facets, "UTF-8")
            val url = "$MODRINTH_BASE_URL/search?query=$encodedQuery&limit=1&index=relevance&facets=$encodedFacets"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent())
                .build()

            getHttpClient(context).newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val payload = response.body?.string().orEmpty()
                if (payload.isBlank()) return@withContext null

                val root = org.json.JSONObject(payload)
                val hits = root.optJSONArray("hits") ?: return@withContext null
                if (hits.length() > 0) {
                    val hit = hits.getJSONObject(0)
                    val projectId = hit.optString("project_id").ifBlank { hit.optString("projectId") }
                    val slug = hit.optString("slug").ifBlank { projectId }
                    val title = hit.optString("title").ifBlank { slug }
                    val description = hit.optString("description")
                    val iconUrl = hit.optString("icon_url")
                    
                    if (projectId.isNotBlank()) {
                        val catalogItem = RemoteCatalogItem(
                            source = "modrinth",
                            projectId = projectId,
                            title = title,
                            slug = slug,
                            iconUrl = iconUrl,
                            description = description,
                            downloads = hit.optLong("downloads"),
                            owner = hit.optString("author")
                        )
                        resolvedProjectCache[cacheKey] = catalogItem
                        return@withContext catalogItem
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PluginManager", "Failed to resolve Modrinth project for $name: ${e.message}")
        }
        null
    }

    private val latestVersionsCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun getLatestVersionFromModrinth(context: Context, projectId: String): String? = withContext(Dispatchers.IO) {
        val cached = latestVersionsCache[projectId]
        if (cached != null) return@withContext cached

        try {
            val url = "https://api.modrinth.com/v2/project/$projectId/version"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent())
                .build()

            getHttpClient(context).newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val payload = response.body?.string().orEmpty()
                if (payload.isBlank()) return@withContext null

                val array = JSONArray(payload)
                if (array.length() > 0) {
                    val latestVersion = array.getJSONObject(0).optString("version_number")
                    if (latestVersion.isNotBlank()) {
                        latestVersionsCache[projectId] = latestVersion
                        return@withContext latestVersion
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PluginManager", "Failed to fetch latest version for $projectId: ${e.message}")
        }
        null
    }

    fun isUpdateAvailable(localVersion: String, remoteVersion: String): Boolean {
        if (localVersion.isBlank() || remoteVersion.isBlank()) return false
        if (localVersion == remoteVersion) return false
        
        val localParts = localVersion.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        val remoteParts = remoteVersion.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        
        val minSize = minOf(localParts.size, remoteParts.size)
        for (i in 0 until minSize) {
            if (remoteParts[i] > localParts[i]) return true
            if (remoteParts[i] < localParts[i]) return false
        }
        return remoteParts.size > localParts.size
    }

    private fun isManagedBridgePlugin(plugin: Plugin): Boolean {
        val candidates = listOf(plugin.name, plugin.fileName)
            .map(::normalizeCatalogKey)
        
        // Do not filter out geyser and viaversion so they can be shown in plugin lists
        val isGeyserOrVia = candidates.any { normalized ->
            normalized.contains("geyser") || normalized.contains("viaversion")
        }
        if (isGeyserOrVia) return false

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

    data class ModDependency(
        val projectId: String,
        val title: String,
        val slug: String,
        val pageUrl: String,
        val isRequired: Boolean
    )

    suspend fun fetchModDependencies(context: Context, projectId: String): List<ModDependency> = withContext(Dispatchers.IO) {
        try {
            val url = "$MODRINTH_BASE_URL/project/$projectId/dependencies"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent())
                .build()

            val client = getHttpClient(context)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val payload = response.body?.string().orEmpty()
                if (payload.isBlank()) return@withContext emptyList()

                val obj = JSONObject(payload)
                val projectsArr = obj.optJSONArray("projects") ?: return@withContext emptyList()
                val result = mutableListOf<ModDependency>()
                for (i in 0 until projectsArr.length()) {
                    val p = projectsArr.getJSONObject(i)
                    val id = p.optString("id")
                    val title = p.optString("title")
                    val slug = p.optString("slug")
                    if (id.isNotBlank() && title.isNotBlank()) {
                        val depSlug = slug.ifBlank { id }
                        result.add(
                            ModDependency(
                                projectId = id,
                                title = title,
                                slug = depSlug,
                                pageUrl = "https://modrinth.com/mod/$depSlug",
                                isRequired = true
                            )
                        )
                    }
                }
                return@withContext result
            }
        } catch (e: Exception) {
            android.util.Log.e("PluginManager", "Error fetching mod dependencies: ${e.message}", e)
            emptyList()
        }
    }
}
