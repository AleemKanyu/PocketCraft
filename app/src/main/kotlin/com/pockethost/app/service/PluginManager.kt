package com.pockethost.app.service

import android.content.Context
import android.net.Uri
import com.pockethost.app.BuildConfig
import com.pockethost.app.data.model.Plugin
import com.pockethost.app.data.model.ServerType
import com.pockethost.app.server.BundledPluginInstaller
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
        "inventoryprofiles"
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

    enum class CatalogSort(val modrinthIndex: String, val displayName: String) {
        DOWNLOADS("downloads", "Downloads"),
        RELEVANCE("relevance", "Relevance"),
        UPDATED("updated", "Updated"),
        NEWEST("newest", "Newest")
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
        val fileName: String,
        /** Modrinth project ids this version declares as required. */
        val requiredDependencies: List<String> = emptyList()
    )

    private data class BundledPluginUpdate(
        val projectId: String,
        val title: String,
        val fileNameHint: String? = null,
        val catalogItem: RemoteCatalogItem? = null
    )

    private fun getHttpClient(context: Context): OkHttpClient {
        // Synchronized: httpClient/cacheDir are read/written from many suspend functions
        // dispatched across Dispatchers.IO's thread pool. Without a lock, two coroutines
        // racing in before first init could each build an independent Cache pointed at the
        // same cacheDir, and two DiskLruCache instances writing the same journal files
        // concurrently can corrupt the on-disk HTTP cache.
        synchronized(this) {
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
        val serverDir = ServerFileManager.getServerDir(context, worldName)
        BundledPluginInstaller.installBundledPlugins(context, serverDir)

        if (isBedrockBridgeEnabled(context, worldName)) {
            onProgress("Enabling bundled Bedrock bridge plugins...")
            ensureManagedPluginEnabled(context, worldName, "geyser")
            ensureManagedPluginEnabled(context, worldName, "floodgate")
            ensureManagedPluginEnabled(context, worldName, "viaversion")
            preserveFloodgateKey(context, worldName)
            enforceBedrockBridgeLocalConfig(context, worldName)

            // enforceBedrockBridgeLocalConfig's own plugin/download steps swallow individual
            // failures internally (runCatching + log warning) and always return normally, so
            // this was previously the only place that could notice Bedrock join support isn't
            // actually going to work and tell the caller. Geyser/Floodgate ship bundled in the
            // app (no network needed), so a missing jar here means the bundled install itself
            // failed (disk full, permission error) rather than a network hiccup — worth
            // surfacing rather than reporting success.
            val pluginsDir = getPluginsDir(context, worldName)
            val hasGeyserJar = hasEnabledPluginJar(pluginsDir, "geyser")
            val hasFloodgateJar = hasEnabledPluginJar(pluginsDir, "floodgate")
            if (!hasGeyserJar || !hasFloodgateJar) {
                val missing = listOfNotNull(
                    "Geyser".takeIf { !hasGeyserJar },
                    "Floodgate".takeIf { !hasFloodgateJar }
                ).joinToString(" and ")
                return@withContext Result.failure(
                    IllegalStateException("Bedrock bridge setup incomplete: $missing failed to install.")
                )
            }
        } else {
            onProgress("Bedrock crossplay disabled. Skipping Geyser & Floodgate...")
            disableManagedPlugin(context, worldName, "geyser")
            disableManagedPlugin(context, worldName, "floodgate")
        }

        Result.success(Unit)
    }

    private fun hasEnabledPluginJar(pluginsDir: File, projectId: String): Boolean {
        val files = pluginsDir.listFiles() ?: return false
        return files.any { file ->
            file.name.endsWith(".jar") && file.length() > 0 && file.name.lowercase().contains(projectId.lowercase())
        }
    }

    fun setBedrockBridgeEnabled(context: Context, worldName: String, enabled: Boolean) {
        val crossplayPlugins = listOf("geyser", "floodgate", "viaversion", "viabackwards", "geyserreversion")
        if (enabled) {
            val serverDir = ServerFileManager.getServerDir(context, worldName)
            BundledPluginInstaller.installBundledPlugins(context, serverDir)
            crossplayPlugins.forEach { ensureManagedPluginEnabled(context, worldName, it) }
            preserveFloodgateKey(context, worldName)
            enforceBedrockBridgeLocalConfig(context, worldName)
        } else {
            crossplayPlugins.forEach { disableManagedPlugin(context, worldName, it) }
        }
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
        if (!supportsBundledBedrockBridge(context, worldName)) return
        val crossplayPlugins = listOf("geyser", "floodgate", "viaversion", "viabackwards", "geyserreversion")
        crossplayPlugins.forEach { ensureManagedPluginEnabled(context, worldName, it) }
        ensureLatestGeyserSpigotPlugin(context, worldName)
        ensureCrossVersionPlugins(context, worldName)
        ensureGeyserReversionExtension(context, worldName)
        preserveFloodgateKey(context, worldName)
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
        // Geyser Bedrock network tuning for ultra-low ping (<15ms)
        updated = ensureYamlSectionValue(updated, "bedrock", "address", "0.0.0.0")
        updated = ensureYamlSectionValue(updated, "bedrock", "port", "19132")
        updated = ensureYamlSectionValue(updated, "bedrock", "clone-remote-port", "false")
        updated = ensureYamlSectionValue(updated, "bedrock", "broadcast-port", "19132")
        updated = ensureYamlSectionValue(updated, "bedrock", "enable-proxy-protocol", "false")
        updated = ensureYamlSectionValue(updated, "bedrock", "compression-level", "1") // 1 = ultra fast zlib (was 6)
        updated = ensureYamlSectionValue(updated, "bedrock", "motd1", "PocketHost Server")
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
        updated = ensureTopLevelYamlValue(updated, "wait-for-chunks-on-portals", "true")
        updated = ensureTopLevelYamlValue(updated, "check-supported-versions", "false")
        updated = ensureTopLevelYamlValue(updated, "allow-third-party-capes", "false")
        updated = ensureTopLevelYamlValue(updated, "allow-third-party-ears", "false")
        updated = ensureTopLevelYamlValue(updated, "custom-block-overrides", "false")
        updated = ensureTopLevelYamlValue(updated, "custom-item-overrides", "false")
        updated = ensureYamlSectionValue(updated, "custom-blocks", "enabled", "false")
        updated = ensureYamlSectionValue(updated, "custom-items", "enabled", "false")
        updated = ensureTopLevelYamlValue(updated, "ignore-third-party-patches", "true")
        updated = ensureYamlSectionValue(updated, "bedrock", "validate-bedrock-login", "false")
        updated = ensureYamlPathValue(updated, listOf("advanced", "bedrock"), "validate-bedrock-login", "false")
        // MTU set to 1400 (standard WiFi MTU, eliminates UDP packet fragmentation & packet queues)
        updated = ensureYamlPathValue(updated, listOf("advanced", "bedrock"), "mtu", "1400")
        updated = ensureTopLevelYamlValue(updated, "mtu", "1400")
        updated = ensureTopLevelYamlValue(updated, "floodgate-key-file", floodgateKeyPath)
        updated = ensureYamlSectionValue(updated, "advanced", "floodgate-key-file", floodgateKeyPath)

        val geyserAuthType = "floodgate"

        // Force Geyser to connect to Paper over 127.0.0.1 loopback for 0ms internal network latency
        updated = ensureYamlSectionValue(updated, "java", "address", "127.0.0.1")
        updated = ensureYamlSectionValue(updated, "java", "port", "25565")
        updated = ensureYamlSectionValue(updated, "java", "auth-type", geyserAuthType)
        updated = ensureYamlSectionValue(updated, "java", "forward-hostname", "false")
        updated = ensureYamlSectionValue(updated, "java", "use-direct-netty-drive", "true")

        // Older Geyser configs use a dedicated remote section.
        if (updated.lines().any { it.trim() == "remote:" }) {
            updated = ensureYamlSectionValue(updated, "remote", "address", "127.0.0.1")
            updated = ensureYamlSectionValue(updated, "remote", "port", "25565")
            updated = ensureYamlSectionValue(updated, "remote", "auth-type", geyserAuthType)
        }

        // Handle newer config variants that may expose auth in an additional server section.
        if (updated.lines().any { it.trim() == "server:" }) {
            updated = ensureYamlSectionValue(updated, "server", "auth-type", geyserAuthType)
        }

        updated = ensureTopLevelYamlValue(updated, "passthrough-protocol-name", "true")
        updated = ensureYamlSectionValue(updated, "motd", "passthrough-motd", "true")
        updated = ensureYamlSectionValue(updated, "motd", "passthrough-player-counts", "true")
        updated = ensureYamlSectionValue(updated, "motd", "passthrough-protocol-name", "true")

        if (updated != original) {
            geyserConfigFile.writeText(updated)
        }

        // Ensure Geyser extensions directories exist for cross-version translation plugins (ViaBedrock/ViaBackwards)
        File(pluginsDir, "Geyser-Spigot/extensions").mkdirs()
        File(pluginsDir, "Geyser/extensions").mkdirs()

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

        val viaVersionConfigFile = File(pluginsDir, "ViaVersion/config.yml")
        viaVersionConfigFile.parentFile?.mkdirs()
        val viaOriginal = if (viaVersionConfigFile.exists()) {
            runCatching { viaVersionConfigFile.readText() }.getOrDefault("")
        } else {
            ""
        }
        var viaUpdated = viaOriginal
        viaUpdated = ensureTopLevelYamlValue(viaUpdated, "check-for-updates", "false")
        viaUpdated = ensureTopLevelYamlValue(viaUpdated, "blockconnection-method", "packet")
        viaUpdated = ensureTopLevelYamlValue(viaUpdated, "cache-syntax-errors", "false")
        viaUpdated = ensureTopLevelYamlValue(viaUpdated, "quick-move-action-fix", "false")
        viaUpdated = ensureTopLevelYamlValue(viaUpdated, "change-1_9-hitbox", "false")
        viaUpdated = ensureTopLevelYamlValue(viaUpdated, "change-1_14-hitbox", "false")
        viaUpdated = ensureTopLevelYamlValue(viaUpdated, "max-packets-per-second", "1000")
        viaUpdated = ensureTopLevelYamlValue(viaUpdated, "tracking-warning-pps", "800")
        viaUpdated = ensureTopLevelYamlValue(viaUpdated, "max-warnings", "1000")
        viaUpdated = ensureTopLevelYamlValue(viaUpdated, "tracking-period", "10")
        viaUpdated = ensureTopLevelYamlValue(viaUpdated, "max-warnings-kick", "false")
        viaUpdated = ensureTopLevelYamlValue(viaUpdated, "suppress-conversion-warnings", "true")
        viaUpdated = ensureTopLevelYamlValue(viaUpdated, "check-supported-versions", "false")

        if (viaUpdated != viaOriginal) {
            viaVersionConfigFile.writeText(viaUpdated)
        }

        ensureGeyserReversionExtension(context, worldName)
    }

    fun fetchModrinthPluginDownloadUrl(context: Context, projectId: String): String? {
        return runCatching {
            val url = "https://api.modrinth.com/v2/project/$projectId/version"
            val request = Request.Builder().url(url).header("User-Agent", userAgent()).build()
            getHttpClient(context).newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val payload = response.body?.string().orEmpty()
                if (payload.isBlank()) return@runCatching null
                val array = JSONArray(payload)
                if (array.length() > 0) {
                    val files = array.getJSONObject(0).optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        return@runCatching files.getJSONObject(0).optString("url").takeIf { it.isNotBlank() }
                    }
                }
                null
            }
        }.getOrNull()
    }

    /**
     * Ensures a Fabric API build matching [mcVersion] is present in the world's `mods/` folder.
     *
     * Almost every Fabric mod hard-depends on Fabric API, and Fabric Loader aborts startup when it
     * is missing rather than skipping the mod:
     *
     *     Mod 'GeckoLib 5' (geckolib) 5.5.5 requires version 0.152.1+26.2 or later of
     *     fabric-api, which is missing!
     *
     * The build is resolved from Modrinth filtered by both loader and game version, so the jar can
     * never be the mismatched one that made blanket auto-installation unsafe before.
     *
     * @return the installed jar, or null when nothing suitable could be resolved.
     */
    fun ensureFabricApiInstalled(
        context: Context,
        worldName: String,
        mcVersion: String,
        onOutput: ((String) -> Unit)? = null
    ): File? {
        val cleanVersion = com.pockethost.app.server.CarpetModManager.cleanMcVersion(mcVersion)
        if (cleanVersion.isBlank()) return null

        val modsDir = getModsDir(context, worldName)
        // Only mods the player installed matter here; a world with no mods needs no API.
        val hasOtherMods = modsDir.listFiles()?.any { file ->
            file.isFile &&
                file.extension.equals("jar", ignoreCase = true) &&
                !file.name.contains("fabric-api", ignoreCase = true)
        } == true
        if (!hasOtherMods) return null

        existingFabricApiJar(modsDir, cleanVersion)?.let { existing ->
            onOutput?.invoke("[PocketHost] Fabric API (MC $cleanVersion) is installed.")
            return existing
        }

        val resolved = resolveFabricApiDownload(context, cleanVersion)
        if (resolved == null) {
            onOutput?.invoke("[PocketHost] Could not find a Fabric API build for MC $cleanVersion. Mods that require it will not load.")
            return null
        }

        onOutput?.invoke("[PocketHost] Installing Fabric API ${resolved.versionNumber} for MC $cleanVersion...")
        val target = File(modsDir, resolved.fileName)
        val temp = File(modsDir, resolved.fileName + ".downloading")
        val ok = runCatching {
            val request = Request.Builder().url(resolved.url).header("User-Agent", userAgent()).build()
            getHttpClient(context).newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching false
                val body = response.body ?: return@runCatching false
                body.byteStream().use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
            }
            // A truncated download is worse than none: Fabric Loader fails on a corrupt jar with a
            // far less obvious message than "which is missing".
            if (temp.length() < 100_000L) {
                temp.delete()
                false
            } else if (temp.renameTo(target)) {
                true
            } else {
                temp.copyTo(target, overwrite = true)
                temp.delete()
                true
            }
        }.getOrElse { error ->
            Log.w("PluginManager", "Fabric API download failed: ${error.message}")
            temp.delete()
            false
        }

        if (!ok) {
            onOutput?.invoke("[PocketHost] Fabric API download failed. Install it manually from the Mods tab if your mods need it.")
            return null
        }

        // Drop builds for other game versions only now that a correct one is in place.
        removeMismatchedFabricApiJars(modsDir, cleanVersion, target, onOutput)
        onOutput?.invoke("[PocketHost] Fabric API ${resolved.versionNumber} installed.")
        return target
    }

    private data class FabricApiRelease(val versionNumber: String, val fileName: String, val url: String)

    private fun existingFabricApiJar(modsDir: File, cleanVersion: String): File? =
        modsDir.listFiles()?.firstOrNull { file ->
            file.isFile &&
                file.extension.equals("jar", ignoreCase = true) &&
                file.name.contains("fabric-api", ignoreCase = true) &&
                fabricApiJarMatchesVersion(file.name, cleanVersion) &&
                file.length() > 100_000L
        }

    /**
     * Fabric API jars are named "fabric-api-<apiVersion>+<gameVersion>.jar", so the game version is
     * compared against the token after the last "+". A plain substring test would accept
     * fabric-api-0.102.0+1.21.1.jar as a build for 1.21.
     */
    private fun fabricApiJarMatchesVersion(fileName: String, cleanVersion: String): Boolean {
        val base = fileName.removeSuffix(".jar").removeSuffix(".disabled")
        val plusIndex = base.lastIndexOf('+')
        if (plusIndex >= 0) {
            return base.substring(plusIndex + 1).equals(cleanVersion, ignoreCase = true)
        }
        return base.contains(cleanVersion)
    }

    private fun removeMismatchedFabricApiJars(
        modsDir: File,
        cleanVersion: String,
        keep: File,
        onOutput: ((String) -> Unit)?
    ) {
        modsDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            if (file.absolutePath == keep.absolutePath) return@forEach
            if (!file.name.contains("fabric-api", ignoreCase = true)) return@forEach
            if (fabricApiJarMatchesVersion(file.name, cleanVersion)) return@forEach
            onOutput?.invoke("[PocketHost] Removing Fabric API build for a different game version: ${file.name}")
            file.delete()
        }
    }

    private fun resolveFabricApiDownload(context: Context, cleanVersion: String): FabricApiRelease? {
        return runCatching {
            val url = "$MODRINTH_BASE_URL/project/fabric-api/version" +
                "?loaders=%5B%22fabric%22%5D&game_versions=%5B%22$cleanVersion%22%5D"
            val request = Request.Builder().url(url).header("User-Agent", userAgent()).build()
            getHttpClient(context).newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val payload = response.body?.string().orEmpty()
                if (payload.isBlank()) return@runCatching null
                val versions = JSONArray(payload)
                // Modrinth returns newest first; take the first entry that has a primary jar.
                for (index in 0 until versions.length()) {
                    val version = versions.optJSONObject(index) ?: continue
                    val files = version.optJSONArray("files") ?: continue
                    for (fileIndex in 0 until files.length()) {
                        val file = files.optJSONObject(fileIndex) ?: continue
                        val name = file.optString("filename")
                        val downloadUrl = file.optString("url")
                        if (name.endsWith(".jar", ignoreCase = true) && downloadUrl.isNotBlank()) {
                            return@runCatching FabricApiRelease(
                                versionNumber = version.optString("version_number").ifBlank { "latest" },
                                fileName = name,
                                url = downloadUrl
                            )
                        }
                    }
                }
                null
            }
        }.getOrNull()
    }

    fun ensureCrossVersionPlugins(context: Context, worldName: String) {
        val pluginsDir = getPluginsDir(context, worldName)
        val viaVersionFile = File(pluginsDir, "ViaVersion.jar")
        val viaBackwardsFile = File(pluginsDir, "ViaBackwards.jar")

        if (!viaVersionFile.exists() || viaVersionFile.length() < 500_000L) {
            val dlUrl = fetchModrinthPluginDownloadUrl(context, "viaversion")
                ?: "https://api.spiget.org/v2/resources/19254/download"
            runCatching {
                val req = Request.Builder().url(dlUrl).header("User-Agent", userAgent()).build()
                getHttpClient(context).newCall(req).execute().use { resp ->
                    if (resp.isSuccessful && resp.body != null) {
                        val tmp = File(pluginsDir, "ViaVersion.tmp")
                        resp.body!!.byteStream().use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
                        if (tmp.length() > 500_000L) {
                            tmp.copyTo(viaVersionFile, overwrite = true)
                            Log.i("PluginManager", "Successfully downloaded ViaVersion.jar")
                        }
                        tmp.delete()
                    }
                }
            }.onFailure { e -> Log.w("PluginManager", "Download ViaVersion.jar failed: ${e.message}") }
        }

        if (!viaBackwardsFile.exists() || viaBackwardsFile.length() < 500_000L) {
            val dlUrl = fetchModrinthPluginDownloadUrl(context, "viabackwards")
            if (dlUrl != null) {
                runCatching {
                    val req = Request.Builder().url(dlUrl).header("User-Agent", userAgent()).build()
                    getHttpClient(context).newCall(req).execute().use { resp ->
                        if (resp.isSuccessful && resp.body != null) {
                            val tmp = File(pluginsDir, "ViaBackwards.tmp")
                            resp.body!!.byteStream().use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
                            if (tmp.length() > 500_000L) {
                                tmp.copyTo(viaBackwardsFile, overwrite = true)
                                Log.i("PluginManager", "Successfully downloaded ViaBackwards.jar")
                            }
                            tmp.delete()
                        }
                    }
                }.onFailure { e -> Log.w("PluginManager", "Download ViaBackwards.jar failed: ${e.message}") }
            }
        }
    }

    fun ensureGeyserReversionExtension(context: Context, worldName: String) {
        val pluginsDir = getPluginsDir(context, worldName)
        val extDir1 = File(pluginsDir, "Geyser-Spigot/extensions").also { it.mkdirs() }
        val extDir2 = File(pluginsDir, "Geyser/extensions").also { it.mkdirs() }

        val target1 = File(extDir1, "GeyserReversion.jar")
        val target2 = File(extDir2, "GeyserReversion.jar")

        if (target1.exists() && target1.length() > 500_000L) {
            if (!target2.exists() || target2.length() != target1.length()) {
                runCatching { target1.copyTo(target2, overwrite = true) }
            }
            return
        }

        runCatching {
            val downloadUrl = fetchModrinthPluginDownloadUrl(context, "geyserreversion")
            if (!downloadUrl.isNullOrBlank()) {
                val dlReq = Request.Builder().url(downloadUrl).header("User-Agent", userAgent()).build()
                getHttpClient(context).newCall(dlReq).execute().use { dlResp ->
                    if (dlResp.isSuccessful && dlResp.body != null) {
                        dlResp.body!!.byteStream().use { input ->
                            target1.outputStream().use { output -> input.copyTo(output) }
                        }
                        runCatching { target1.copyTo(target2, overwrite = true) }
                        Log.i("PluginManager", "Successfully downloaded and installed GeyserReversion extension for older Bedrock client support!")
                    }
                }
            }
        }.onFailure { e ->
            Log.w("PluginManager", "Could not auto-download GeyserReversion extension: ${e.message}")
        }
    }

    fun ensureLatestGeyserSpigotPlugin(context: Context, worldName: String) {
        val pluginsDir = getPluginsDir(context, worldName)
        val geyserFile = File(pluginsDir, "Geyser-Spigot.jar")
        val floodgateFile = File(pluginsDir, "floodgate-spigot.jar")

        if (!geyserFile.exists() || geyserFile.length() < 1_000_000L) {
            runCatching {
                val url = "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot"
                val request = Request.Builder().url(url).header("User-Agent", userAgent()).build()
                getHttpClient(context).newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val tempFile = File(pluginsDir, "Geyser-Spigot.tmp")
                        response.body!!.byteStream().use { input ->
                            tempFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (tempFile.length() > 5_000_000L) {
                            tempFile.copyTo(geyserFile, overwrite = true)
                            Log.i("PluginManager", "Successfully downloaded Geyser-Spigot.jar")
                        }
                        tempFile.delete()
                    }
                }
            }.onFailure { e -> Log.w("PluginManager", "Download Geyser-Spigot.jar failed: ${e.message}") }
        }

        if (!floodgateFile.exists() || floodgateFile.length() < 500_000L) {
            runCatching {
                val url = "https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot"
                val request = Request.Builder().url(url).header("User-Agent", userAgent()).build()
                getHttpClient(context).newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val tempFile = File(pluginsDir, "floodgate-spigot.tmp")
                        response.body!!.byteStream().use { input ->
                            tempFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (tempFile.length() > 500_000L) {
                            tempFile.copyTo(floodgateFile, overwrite = true)
                            Log.i("PluginManager", "Successfully downloaded floodgate-spigot.jar")
                        }
                        tempFile.delete()
                    }
                }
            }.onFailure { e -> Log.w("PluginManager", "Download floodgate-spigot.jar failed: ${e.message}") }
        }
    }

    /**
     * Preserves and synchronizes the Floodgate AES encryption key (key.pem) across server resets and plugin directories.
     * Validates that key.pem is a valid 128-bit (16-byte) binary AES key, purges any corrupted or invalid keys,
     * generates a fresh key if missing, and replicates the exact raw bytes to all Floodgate and Geyser directories.
     */
    fun preserveFloodgateKey(context: Context, worldName: String) {
        val serverDir = ServerFileManager.getServerDir(context, worldName)
        val backupDir = File(context.filesDir, "servers/$worldName").also { it.mkdirs() }
        val floodgateDirs = listOf(
            File(serverDir, "plugins/floodgate"),
            File(serverDir, "plugins/Floodgate"),
            File(serverDir, "plugins/Geyser-Spigot")
        ).onEach { it.mkdirs() }

        val backupFile = File(backupDir, "floodgate_key_backup.pem")
        val allKeyFiles = floodgateDirs.map { File(it, "key.pem") }

        fun isValidAesKey(bytes: ByteArray): Boolean {
            // Floodgate 2.0 uses raw AES keys: 16 bytes (128-bit), 24 bytes (192-bit), or 32 bytes (256-bit)
            if (bytes.size != 16 && bytes.size != 24 && bytes.size != 32) return false
            // Reject any ASCII/PEM text like "-----BEGIN..."
            val ascii = String(bytes, Charsets.US_ASCII)
            if (ascii.contains("BEGIN") || ascii.contains("PRIVATE") || ascii.contains("KEY")) return false
            return true
        }

        // Check if any existing key file has valid AES bytes
        var validKeyBytes: ByteArray? = null
        for (file in allKeyFiles + backupFile) {
            if (file.exists() && file.length() > 0L) {
                val bytes = runCatching { file.readBytes() }.getOrNull()
                if (bytes != null && isValidAesKey(bytes)) {
                    validKeyBytes = bytes
                    break
                }
            }
        }

        // If no valid key exists or existing keys are corrupted (e.g. invalid length),
        // purge corrupted files and generate a standard 128-bit (16-byte) AES key
        val keyBytes = validKeyBytes ?: run {
            Log.i("PluginManager", "Generating fresh 16-byte AES key for Floodgate and Geyser")
            allKeyFiles.forEach { runCatching { it.delete() } }
            runCatching { backupFile.delete() }
            val keyGen = javax.crypto.KeyGenerator.getInstance("AES")
            keyGen.init(128, java.security.SecureRandom())
            keyGen.generateKey().encoded
        }

        // Synchronize the raw binary AES key to backup and all plugin directories
        runCatching { backupFile.writeBytes(keyBytes) }
        floodgateDirs.forEach { dir ->
            val target = File(dir, "key.pem")
            runCatching { target.writeBytes(keyBytes) }
        }
        Log.i("PluginManager", "Floodgate AES key synchronized (${keyBytes.size} bytes)")
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
        val pluginsDir = getPluginsDir(context, worldName)
        if (!pluginsDir.exists()) return true
        val hasGeyserDisabled = pluginsDir.listFiles()?.any { it.name.lowercase().contains("geyser") && it.name.endsWith(".disabled") } == true
        val hasFloodgateDisabled = pluginsDir.listFiles()?.any { it.name.lowercase().contains("floodgate") && it.name.endsWith(".disabled") } == true
        return !(hasGeyserDisabled || hasFloodgateDisabled)
    }

    fun getRuntimeKeyForWorld(context: Context, worldName: String): String {
        val serverDir = ServerFileManager.getServerDirNoCreate(context, worldName)
        if (!serverDir.exists()) return "paper"
        val props = ServerPropertiesHelper.readProperties(serverDir, persistDefaults = false)
        val serverType = com.pockethost.app.data.model.ServerType.fromString(props.getProperty("pocketcraft-server-type"))
        val version = props.getProperty("pocketcraft-game-version").orEmpty()
        return when (serverType) {
            com.pockethost.app.data.model.ServerType.VANILLA -> "vanilla-$version"
            com.pockethost.app.data.model.ServerType.FABRIC -> "fabric-$version"
            com.pockethost.app.data.model.ServerType.PAPER -> "paper-$version"
            com.pockethost.app.data.model.ServerType.PURPUR -> "purpur-$version"
            com.pockethost.app.data.model.ServerType.BEDROCK -> "bedrock-$version"
            com.pockethost.app.data.model.ServerType.MODPACK -> {
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
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Could not open selected file")
            val totalBytes = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L

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
            val client = getHttpClient(context)
            val request = Request.Builder()
                .url(sourceUrl)
                .header("User-Agent", userAgent())
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code} download failed"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty download response"))
                val totalBytes = body.contentLength()
                val targetDir = getContentDir(context, worldName, type)

                var name = fileNameHint
                if (name.isNullOrBlank()) {
                    val cd = response.header("Content-Disposition")
                    if (cd != null && cd.contains("filename=")) {
                        name = cd.substringAfter("filename=").trim('"').trim('\'')
                    }
                }
                if (name.isNullOrBlank()) {
                    name = sourceUrl.substringAfterLast('/').substringBefore('?')
                }
                if (name.isBlank()) name = "downloaded_content_${System.currentTimeMillis()}"
                if (!name.endsWith(".jar") && (type == ContentType.PLUGINS || type == ContentType.MODS)) {
                    name = "$name.jar"
                }

                val destFile = File(targetDir, sanitizeFileName(name))

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

    /** Guards against a pathological dependency graph pulling down the whole catalogue. */
    private const val MAX_DEPENDENCY_INSTALLS = 8

    suspend fun installRemoteItem(
        context: Context,
        item: RemoteCatalogItem,
        worldName: String,
        type: ContentType,
        runtimeKey: String = worldName,
        minecraftVersion: String? = null,
        onDependencyInstalled: (String) -> Unit = {},
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!item.canInstall) {
            return@withContext Result.failure(Exception(item.supportMessage ?: "This item is not compatible with the current server runtime."))
        }

        val resolvedVersion = minecraftVersion 
            ?: com.pockethost.app.data.repository.ServerConfigRepository(context).loadConfig().gameVersion.ifBlank { "1.20.4" }

        val candidate = when (item.source) {
            MODRINTH_PROVIDER -> resolveModrinthDownload(context, item, type, resolvedVersion, runtimeKey)
            HANGAR_PROVIDER -> resolveHangarDownload(context, item, worldName)
            else -> null
        } ?: return@withContext Result.failure(Exception("Could not find a compatible download for ${item.title}."))

        val installed = installFromUrl(
            context = context,
            sourceUrl = candidate.downloadUrl,
            worldName = worldName,
            type = type,
            fileNameHint = candidate.fileName,
            runtimeKey = runtimeKey,
            onProgress = onProgress
        )

        if (installed.isSuccess && candidate.requiredDependencies.isNotEmpty()) {
            runCatching {
                installRequiredDependencies(
                    context = context,
                    projectIds = candidate.requiredDependencies,
                    worldName = worldName,
                    type = type,
                    runtimeKey = runtimeKey,
                    minecraftVersion = resolvedVersion,
                    onDependencyInstalled = onDependencyInstalled
                )
            }.onFailure { error ->
                Log.w("PluginManager", "Dependency install for ${item.title} failed: ${error.message}")
            }
        }

        installed
    }

    /**
     * Installs the required dependencies of a just-installed project, breadth first.
     *
     * A mod installed without its hard dependencies is not merely missing a feature: Fabric Loader
     * aborts the entire server launch when one is absent, so the player sees a server that refuses
     * to start rather than a mod that quietly does nothing.
     */
    private suspend fun installRequiredDependencies(
        context: Context,
        projectIds: List<String>,
        worldName: String,
        type: ContentType,
        runtimeKey: String,
        minecraftVersion: String,
        onDependencyInstalled: (String) -> Unit
    ) {
        val targetDir = getContentDir(context, worldName, type)
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque(projectIds)
        var installs = 0

        while (queue.isNotEmpty() && installs < MAX_DEPENDENCY_INSTALLS) {
            val projectId = queue.removeFirst()
            if (!visited.add(projectId)) continue

            // resolveModrinthDownload only reads projectId, so a stub is enough to reuse the same
            // loader/game-version filtering the primary download went through.
            val stub = RemoteCatalogItem(
                source = MODRINTH_PROVIDER,
                projectId = projectId,
                title = projectId,
                slug = projectId,
                iconUrl = null,
                description = "",
                downloads = 0L
            )
            val dependency = runCatching {
                resolveModrinthDownload(context, stub, type, minecraftVersion, runtimeKey)
            }.getOrNull() ?: continue

            if (File(targetDir, dependency.fileName).exists()) {
                // Already satisfied; still follow its own requirements.
                queue.addAll(dependency.requiredDependencies)
                continue
            }

            val result = installFromUrl(
                context = context,
                sourceUrl = dependency.downloadUrl,
                worldName = worldName,
                type = type,
                fileNameHint = dependency.fileName,
                runtimeKey = runtimeKey,
                onProgress = {}
            )
            if (result.isSuccess) {
                installs++
                onDependencyInstalled(dependency.fileName.removeSuffix(".jar").removeSuffix(".zip"))
                queue.addAll(dependency.requiredDependencies)
            }
        }
    }

    suspend fun resolveAndInstallMissingDependency(
        context: Context,
        worldName: String,
        dependencyId: String,
        minecraftVersion: String,
        runtimeKey: String = worldName
    ): Result<File> = withContext(Dispatchers.IO) {
        val type = ContentType.MODS
        val effectiveRuntimeKey = if (supportsMods(runtimeKey)) {
            runtimeKey
        } else {
            val serverType = ServerPropertiesHelper
                .readProperties(ServerFileManager.getServerDir(context, worldName), persistDefaults = false)
                .getProperty("pocketcraft-server-type")
                .orEmpty()
            if (serverType.isNotBlank() && supportsMods(serverType)) {
                serverType.lowercase(Locale.US)
            } else {
                "fabric"
            }
        }
        val stub = RemoteCatalogItem(
            source = MODRINTH_PROVIDER,
            projectId = dependencyId,
            title = dependencyId,
            slug = dependencyId,
            iconUrl = null,
            description = "",
            downloads = 0L
        )
        var candidate = runCatching {
            resolveModrinthDownload(context, stub, type, minecraftVersion, effectiveRuntimeKey)
        }.getOrNull()

        if (candidate == null) {
            val searchResult = runCatching {
                searchModrinthCatalog(
                    context = context,
                    type = type,
                    query = dependencyId,
                    minecraftVersion = minecraftVersion,
                    runtimeKey = effectiveRuntimeKey,
                    offset = 0,
                    limit = 5
                )
            }.getOrNull()

            val bestMatch = searchResult?.items?.firstOrNull { item ->
                item.slug.equals(dependencyId, ignoreCase = true) ||
                    item.projectId.equals(dependencyId, ignoreCase = true) ||
                    item.title.replace(" ", "").equals(dependencyId.replace(" ", "").replace("-", "").replace("_", ""), ignoreCase = true)
            } ?: searchResult?.items?.firstOrNull()

            if (bestMatch != null) {
                candidate = runCatching {
                    resolveModrinthDownload(context, bestMatch, type, minecraftVersion, effectiveRuntimeKey)
                }.getOrNull()
            }
        }

        if (candidate == null) {
            return@withContext Result.failure(Exception("Could not find download candidate for dependency '$dependencyId'."))
        }

        val targetDir = getContentDir(context, worldName, type)
        val targetFile = File(targetDir, candidate.fileName)
        if (targetFile.exists()) {
            return@withContext Result.success(targetFile)
        }

        installFromUrl(
            context = context,
            sourceUrl = candidate.downloadUrl,
            worldName = worldName,
            type = type,
            fileNameHint = candidate.fileName,
            runtimeKey = runtimeKey,
            onProgress = {}
        )
    }

    data class CatalogSearchResult(
        val items: List<RemoteCatalogItem>,
        val totalHits: Int = 0,
        val offset: Int = 0,
        val limit: Int = 20
    )

    suspend fun fetchRemoteCatalog(
        context: Context,
        type: ContentType,
        query: String,
        minecraftVersion: String,
        runtimeKey: String,
        offset: Int = 0,
        limit: Int = 20,
        sort: CatalogSort = CatalogSort.DOWNLOADS
    ): Result<CatalogSearchResult> = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim()
        val cacheKey = listOf(type.name, minecraftVersion, normalizedQuery.lowercase(Locale.US), offset, limit, sort.name).joinToString("|")
        val cached = getCachedCatalog(context, cacheKey)
        
        if (cached != null) {
            // Background refresh
            scope.launch {
                runCatching {
                    val freshResults = performCatalogSearch(context, type, normalizedQuery, minecraftVersion, runtimeKey, offset, limit, sort)
                    putCachedCatalog(context, cacheKey, freshResults)
                }
            }
            return@withContext Result.success(cached)
        }

        runCatching {
            performCatalogSearch(context, type, normalizedQuery, minecraftVersion, runtimeKey, offset, limit, sort)
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
        offset: Int = 0,
        limit: Int = 20,
        sort: CatalogSort = CatalogSort.DOWNLOADS
    ): CatalogSearchResult = coroutineScope {
        val rawResult = searchModrinthCatalog(
            context = context,
            type = type,
            query = normalizedQuery,
            minecraftVersion = minecraftVersion,
            runtimeKey = runtimeKey,
            offset = offset,
            limit = limit,
            sort = sort
        )
        val filteredItems = if (type == ContentType.PLUGINS) {
            mergeCatalogResults(rawResult.items, limit).filterNot(::isManagedBridgeCatalogItem)
        } else {
            mergeCatalogResults(rawResult.items, limit)
        }
        CatalogSearchResult(
            items = filteredItems,
            totalHits = rawResult.totalHits,
            offset = rawResult.offset,
            limit = rawResult.limit
        )
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
        offset: Int = 0,
        limit: Int = 20,
        sort: CatalogSort = CatalogSort.DOWNLOADS
    ): CatalogSearchResult {
        throttleProvider(MODRINTH_PROVIDER, minimumGapMs = 250L)

        val facets = buildModrinthFacets(type, runtimeKey)
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val encodedFacets = URLEncoder.encode(facets, "UTF-8")
        val index = sort.modrinthIndex
        val url = buildString {
            append("$MODRINTH_BASE_URL/search")
            append("?query=$encodedQuery")
            append("&offset=$offset")
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
            if (payload.isBlank()) return CatalogSearchResult(emptyList(), 0, offset, limit)

            val root = JSONObject(payload)
            val totalHits = root.optInt("total_hits", 0)
            val hits = root.optJSONArray("hits") ?: return CatalogSearchResult(emptyList(), totalHits, offset, limit)
            val itemsList = buildList {
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

                    val canInstall = when (type) {
                        ContentType.MODS -> isSupportedMod && supportsMods(runtimeKey)
                        ContentType.PLUGINS -> true
                        ContentType.RESOURCE_PACKS -> true
                    }

                    add(
                        RemoteCatalogItem(
                            source = MODRINTH_PROVIDER,
                            projectId = projectId,
                            slug = slug,
                            title = title,
                            description = item.optString("description").ifBlank { "No description provided." },
                            author = item.optString("author").takeIf { it.isNotBlank() },
                            iconUrl = item.optString("icon_url").takeIf { it.isNotBlank() },
                            downloads = item.optLong("downloads"),
                            canInstall = canInstall,
                            supportMessage = modSupportMessage,
                            isSupported = if (type == ContentType.MODS) isSupportedMod else true
                        )
                    )
                }
            }
            CatalogSearchResult(itemsList, totalHits, offset, limit)
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
                val candidate = extractPrimaryFile(version, defaultExtension(type))
                    ?.copy(requiredDependencies = requiredDependencyProjectIds(version))
                    ?: continue
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

    /**
     * Project ids a Modrinth version declares as hard requirements.
     *
     * Installing a mod without these leaves the server unable to start: a Fabric mod whose
     * required dependency is missing makes Fabric Loader abort the whole launch rather than skip
     * the mod, which reads to the player as "the app installed a broken mod".
     */
    private fun requiredDependencyProjectIds(version: JSONObject): List<String> {
        val dependencies = version.optJSONArray("dependencies") ?: return emptyList()
        val ids = mutableListOf<String>()
        for (index in 0 until dependencies.length()) {
            val dependency = dependencies.optJSONObject(index) ?: continue
            if (!dependency.optString("dependency_type").equals("required", ignoreCase = true)) continue
            val projectId = dependency.optString("project_id").trim()
            if (projectId.isNotBlank()) ids.add(projectId)
        }
        return ids.distinct()
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

    private fun buildModrinthFacets(type: ContentType, runtimeKey: String = ""): String {
        return when (type) {
            ContentType.PLUGINS -> """[["project_type:plugin"]]"""
            ContentType.MODS -> {
                val loaders = compatibleModLoadersForRuntime(runtimeKey)
                if (loaders.isNotEmpty()) {
                    val loaderFacets = loaders.joinToString(",") { """"categories:$it"""" }
                    """[["project_type:mod"],[$loaderFacets]]"""
                } else {
                    """[["project_type:mod"]]"""
                }
            }
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
        limit: Int
    ): List<RemoteCatalogItem> {
        val seen = linkedSetOf<String>()
        val unique = buildList {
            for (item in items) {
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
        var unDisabledAny = false
        val files = pluginsDir.listFiles() ?: emptyArray()
        for (file in files) {
            if (file.name.endsWith(".jar.disabled") && file.name.lowercase().contains(projectId.lowercase())) {
                val enabledFile = java.io.File(pluginsDir, file.name.removeSuffix(".disabled"))
                runCatching { file.renameTo(enabledFile) }
                unDisabledAny = true
            }
        }
        var hasEnabledJar = false
        for (file in files) {
            if (file.name.endsWith(".jar") && file.name.lowercase().contains(projectId.lowercase())) {
                hasEnabledJar = true
                break
            }
        }
        if (!unDisabledAny && !hasEnabledJar) {
            val serverDir = ServerFileManager.getServerDir(context, worldName)
            BundledPluginInstaller.installBundledPlugins(context, serverDir)
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
            ServerType.PAPER, ServerType.PURPUR, ServerType.FABRIC -> true
            ServerType.BEDROCK, ServerType.VANILLA, ServerType.MODPACK -> false
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

    private fun getCachedCatalog(context: Context, cacheKey: String): CatalogSearchResult? {
        val typeToken = object : com.google.gson.reflect.TypeToken<VersionCacheManager.CacheEntry<CatalogSearchResult>>() {}
        return VersionCacheManager.get(context, "catalog_$cacheKey", typeToken)
    }

    private fun putCachedCatalog(context: Context, cacheKey: String, result: CatalogSearchResult) {
        VersionCacheManager.put(context, "catalog_$cacheKey", result, TimeUnit.HOURS.toMillis(6))
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
