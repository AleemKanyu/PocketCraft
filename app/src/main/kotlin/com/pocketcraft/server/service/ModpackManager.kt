package com.pocketcraft.server.service

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.pocketcraft.server.setup.JreExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object ModpackManager {
    private const val TAG = "ModpackManager"
    private const val FORGE_INSTALL_TIMEOUT_MINUTES = 15L
    private const val MODRINTH_BASE_URL = "https://api.modrinth.com/v2"
    private const val CURSE_TOOLS_BASE_URL = "https://api.curse.tools/v1/cf"
    private const val FABRIC_META_BASE_URL = "https://meta.fabricmc.net/v2"
    private val forgeMavenBaseUrl = "https://" + "maven" + ".minecraftforge" + ".net"
    private val neoForgeMavenBaseUrl = "https://" + "maven" + ".neoforged" + ".net"
    private const val MODPACK_SEARCH_CACHE_TTL_MS = 5 * 60_000L
    private val DEFAULT_MODPACK_QUERIES = listOf(
        "skyblock",
        "oneblock",
        "cobblemon",
        "pokemon",
        "fabulously optimized",
        "simply optimized",
        "optimization"
    )
    private val KNOWN_CLIENT_ONLY_MOD_IDS = setOf(
        "better_client",
        "sodium",
        "reeses_sodium_options",
        "continuity",
        "iris",
        "indium",
        "modmenu",
        "dynamic_fps",
        "entityculling",
        "notenoughanimations",
        "lambdynlights",
        "xaerominimap",
        "xaeroworldmap",
        "journeymap"
    )
    private val KNOWN_CLIENT_ONLY_FILE_HINTS = listOf(
        "better_client",
        "sodium",
        "iris",
        "embeddium",
        "optifine",
        "modmenu",
        "dynamic-fps",
        "dynamic_fps",
        "entityculling",
        "not-enough-animations",
        "notenoughanimations",
        "lambdynamiclights",
        "xaeros_minimap",
        "xaeros-world-map",
        "journeymap"
    )
    private val MODPACK_MANAGED_DIRS = setOf(
        "mods",
        "config",
        "defaultconfigs",
        "kubejs",
        "libraries",
        "versions",
        ".fabric"
    )
    private val MODPACK_MANAGED_FILE_PREFIXES = listOf(
        "modpack-",
        "forge-installer-",
        "neoforge-installer-"
    )
    private val MODPACK_MANAGED_FILES = setOf(
        "pack.mrpack",
        "run.sh",
        "run.bat",
        "unix_args.txt",
        "user_jvm_args.txt"
    )
    private data class SearchCacheEntry(
        val items: List<ModpackCatalogItem>,
        val timestampMs: Long
    )

    private val searchCache = mutableMapOf<String, SearchCacheEntry>()

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(2, TimeUnit.MINUTES)
            .callTimeout(10, TimeUnit.MINUTES)
            .build()
    }

    private data class ResolvedModpack(
        val downloadUrl: String,
        val projectId: String,
        val loader: ModLoader,
        val loaderVersion: String
    )

    private data class ModpackManifest(
        val name: String,
        val files: List<ManifestFile>,
        val dependencies: Map<String, String>
    )

    private data class ManifestFile(
        val path: String,
        val downloads: List<String>,
        val hashes: Map<String, String>,
        val serverSupport: String
    )

    private data class LoaderSpec(
        val id: String,
        val minecraftVersion: String,
        val loaderVersion: String
    )

    private data class ImportedLaunchTarget(
        val mode: ServerFileManager.LaunchMode,
        val file: File
    )

    enum class ModLoader(val id: String, val displayName: String) {
        FABRIC("fabric-loader", "Fabric"),
        QUILT("quilt-loader", "Quilt"),
        FORGE("forge", "Forge"),
        NEOFORGE("neoforge", "NeoForge"),
        UNKNOWN("unknown", "Unknown");

        companion object {
            fun fromId(id: String?): ModLoader {
                return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: UNKNOWN
            }

            fun fromString(value: String?): ModLoader {
                val lowered = value?.lowercase() ?: return UNKNOWN
                return when {
                    lowered.contains("fabric") -> FABRIC
                    lowered.contains("quilt") -> QUILT
                    lowered.contains("neoforge") -> NEOFORGE
                    lowered.contains("forge") -> FORGE
                    else -> UNKNOWN
                }
            }
        }
    }

    enum class Source { MODRINTH, CURSEFORGE }

    data class ModpackCatalogItem(
        val id: String,
        val title: String,
        val description: String,
        val source: Source,
        val downloads: Long = 0,
        val iconUrl: String? = null,
        val minecraftVersions: List<String> = emptyList(),
        val loaders: List<ModLoader> = emptyList(),
        val installSupported: Boolean = true,
        val supportMessage: String? = null,
        val pageUrl: String? = null
    )

    suspend fun installModpack(
        context: Context,
        modpackId: String,
        worldName: String = modpackId,
        onStatus: (String) -> Unit,
        onProgress: (Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val serverDir = ServerFileManager.getServerDir(context, worldName)
        val tempPackFile = File(serverDir, "pack_${System.currentTimeMillis()}.mrpack")
        try {
            onStatus("Resolving modpack $modpackId...")
            val resolved = resolveModpack(modpackId)
            if (resolved.downloadUrl.isBlank()) {
                return@withContext Result.failure(Exception("Could not find download URL for $modpackId"))
            }

            onStatus("Downloading modpack package...")
            blockedRuntimeFileFetch(resolved.downloadUrl, tempPackFile) { percent ->
                onProgress((percent * 0.2f).toInt().coerceIn(0, 20))
            }

            if (!isModrinthPackFile(tempPackFile)) {
                throw Exception("Downloaded file is not a valid Modrinth modpack package.")
            }

            onStatus("Installing Modrinth modpack...")
            installModrinthPackFile(
                context = context,
                packFile = tempPackFile,
                serverDir = serverDir,
                worldName = worldName,
                modpackId = modpackId,
                onStatus = onStatus,
                onProgress = onProgress
            )

            onProgress(100)
            onStatus("Modpack installed successfully!")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install modpack $modpackId", e)
            Result.failure(e)
        } finally {
            if (tempPackFile.exists()) {
                tempPackFile.delete()
            }
        }
    }

    suspend fun importModpackZip(
        context: Context,
        zipUri: Uri,
        worldName: String,
        modpackId: String,
        onStatus: (String) -> Unit = {},
        onProgress: (Int) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "modpack_import_${System.currentTimeMillis()}.zip")
        try {
            onStatus("Copying modpack package...")
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Could not open imported file")

            val serverDir = ServerFileManager.getServerDir(context, worldName)
            if (isModrinthPackFile(tempFile)) {
                throw Exception("PocketCraft only supports Server Pack ZIP files. Please download the 'Server Pack' from Modrinth, not the .mrpack file.")
            }

            onStatus("Extracting modpack server files...")
            ZipFile(tempFile).use { zip ->
                val stripPrefix = detectServerPackWrapperPrefix(zip)
                val totalEntries = zip.size().toFloat()
                var processed = 0
                var lastProgressPercent = -1
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val normalizedName = normalizeZipEntryName(entry.name, stripPrefix)
                    if (normalizedName.isBlank()) {
                        processed++
                        val currentProgress = ((processed / totalEntries) * 80).toInt()
                        if (currentProgress != lastProgressPercent) {
                            lastProgressPercent = currentProgress
                            onProgress(currentProgress)
                        }
                        continue
                    }
                    val entryFile = File(serverDir, normalizedName)
                    if (!entryFile.canonicalPath.startsWith(serverDir.canonicalPath)) {
                        processed++
                        val currentProgress = ((processed / totalEntries) * 80).toInt()
                        if (currentProgress != lastProgressPercent) {
                            lastProgressPercent = currentProgress
                            onProgress(currentProgress)
                        }
                        continue
                    }
                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            entryFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    processed++
                    val currentProgress = ((processed / totalEntries) * 80).toInt()
                    if (currentProgress != lastProgressPercent) {
                        lastProgressPercent = currentProgress
                        onProgress(currentProgress)
                    }
                }
            }

            onStatus("Scanning for server launch target...")
            val launchTarget = resolveImportedLaunchTarget(serverDir)
                ?: throw Exception(
                    "Could not find a launchable server target inside the ZIP. " +
                        "Import a Server Pack ZIP containing server.jar, fabric-server-launch.jar, or Forge/NeoForge unix_args.txt."
                )

            val relativeLaunchPath = launchTarget.file.relativeTo(serverDir).path
            ServerFileManager.persistLaunchTarget(context, worldName, launchTarget.mode, relativeLaunchPath)

            val props = ServerPropertiesHelper.readProperties(serverDir)
            props.setProperty("pocketcraft-server-type", com.pocketcraft.server.data.model.ServerType.MODPACK.name)
            props.setProperty("pocketcraft-custom-jar-path", modpackId)
            props.setProperty("pocketcraft-modpack-id", modpackId)
            props.setProperty("pocketcraft-modpack-name", modpackId)
            ServerPropertiesHelper.saveProperties(serverDir, props)

            onStatus("Removing client-only mods for compatibility...")
            sanitizeClientOnlyMods(serverDir)

            onProgress(100)
            onStatus("Modpack installed successfully!")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Modpack import failed", e)
            Result.failure(e)
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    fun sanitizeInstalledModsForServer(serverDir: File) {
        sanitizeClientOnlyMods(serverDir)
    }

    fun isModpackInstalled(context: Context, worldName: String, modpackId: String): Boolean {
        return ServerFileManager.isModpackReady(context, worldName, modpackId)
    }

    suspend fun searchModpacks(
        query: String,
        limit: Int = 20
    ): Result<List<ModpackCatalogItem>> = withContext(Dispatchers.IO) {
        val normalizedLimit = limit.coerceIn(1, 80)
        val normalizedQuery = query.trim()
        val cacheKey = "${normalizedQuery.lowercase()}:$normalizedLimit"
        synchronized(searchCache) {
            searchCache[cacheKey]
                ?.takeIf { System.currentTimeMillis() - it.timestampMs < MODPACK_SEARCH_CACHE_TTL_MS }
                ?.items
                ?.let { return@withContext Result.success(it) }
        }

        val queries = if (normalizedQuery.isBlank()) {
            DEFAULT_MODPACK_QUERIES
        } else {
            listOf(normalizedQuery)
        }
        val perQueryLimit = (normalizedLimit / queries.size).coerceIn(6, 12)

        val modrinthItems = coroutineScope {
            queries.map { term ->
                async(Dispatchers.IO) {
                    runCatching {
                        fetchModrinthModpacks(query = term, limit = perQueryLimit)
                    }.getOrElse {
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }
        val curseforgeItems = coroutineScope {
            queries.map { term ->
                async(Dispatchers.IO) {
                    runCatching {
                        fetchCurseforgeModpacks(query = term, limit = perQueryLimit)
                    }.getOrElse {
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }

        val withCompatibility = (modrinthItems + curseforgeItems)
            .distinctBy { "${it.source}:${it.id}" }
            .filter { isAllowedModpack(it) }
            .map { item ->
                when (item.source) {
                    Source.MODRINTH -> item.copy(
                        installSupported = true,
                        supportMessage = if (item.loaders.isEmpty()) {
                            "Install supported; checked during setup."
                        } else {
                            "Install: ${item.loaders.joinToString { it.displayName }}"
                        }
                    )
                    Source.CURSEFORGE -> item.copy(
                        installSupported = false,
                        supportMessage = "CurseForge listing shown for discovery; direct install is coming soon."
                    )
                }
            }
            .sortedWith(
                compareBy<ModpackCatalogItem> { if (it.source == Source.MODRINTH) 0 else 1 }
                    .thenByDescending { it.downloads }
            )
            .take(normalizedLimit)
        synchronized(searchCache) {
            if (searchCache.size > 24) searchCache.clear()
            searchCache[cacheKey] = SearchCacheEntry(withCompatibility, System.currentTimeMillis())
        }
        Result.success(withCompatibility)
    }

    private fun isAllowedModpack(item: ModpackCatalogItem): Boolean {
        val titleLower = item.title.lowercase()
        val descLower = item.description.lowercase()

        // 1. Skyblock Category
        val isSkyblock = titleLower.contains("skyblock") || titleLower.contains("sky-block") ||
                titleLower.contains("oneblock") || titleLower.contains("one-block") ||
                titleLower.contains("skyfactory") || titleLower.contains("sky factory") ||
                titleLower.contains("project ozone") ||
                descLower.contains("skyblock") || descLower.contains("sky-block") ||
                descLower.contains("oneblock") || descLower.contains("one-block")

        if (isSkyblock) return true

        // 2. Pokemon Category
        val isPokemon = titleLower.contains("pokemon") || titleLower.contains("cobblemon") ||
                titleLower.contains("pixelmon") || titleLower.contains("poké") ||
                descLower.contains("pokemon") || descLower.contains("cobblemon") ||
                descLower.contains("pixelmon")

        if (isPokemon) return true

        // 3. Optimization Category
        val optTitleKeywords = listOf(
            "optimized", "optimization", "performance", "fps boost", "boosted fps",
            "simply optimized", "fabulously optimized", "optifine", "sodium", "lithium", "iris",
            "embeddium", "additive", "adrenaline", "smoothness", "speedy", "fps-boost", "better fps", "fps"
        )
        val hasOptTitle = optTitleKeywords.any { titleLower.contains(it) }

        val optDescPhrases = listOf(
            "optimization modpack", "optimization pack", "performance modpack", "performance pack",
            "performance-focused", "focuses on performance", "focused on performance", "performance-oriented",
            "fabulously optimized", "simply optimized"
        )
        val hasOptDesc = optDescPhrases.any { descLower.contains(it) }

        return hasOptTitle || hasOptDesc
    }

    private suspend fun resolveModpack(id: String): ResolvedModpack = withContext(Dispatchers.IO) {
        val projectId = id

        val url = "$MODRINTH_BASE_URL/project/$projectId/version"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PocketCraft/1.0")
            .build()

        executeWithRetry(request, "resolve modpack versions").use { response ->
            if (!response.isSuccessful) throw Exception("Modrinth API error: ${response.code}")
            val versions = JSONArray(response.body?.string().orEmpty())
            val latest = findBestSupportedVersion(versions) ?: versions.optJSONObject(0)
                ?: throw Exception("No compatible versions found for this modpack.")
            val files = latest.optJSONArray("files") ?: throw Exception("No downloadable files found")
            val primaryFile = (0 until files.length())
                .mapNotNull { files.optJSONObject(it) }
                .firstOrNull { it.optBoolean("primary", false) }
                ?: files.optJSONObject(0)
                ?: throw Exception("No downloadable files found")
            val loader = detectSupportedLoader(latest.optJSONObject("dependencies"))
            val loaderVersion = latest.optJSONObject("dependencies")?.optString(loader.id) ?: ""

            ResolvedModpack(
                downloadUrl = primaryFile.optString("url"),
                projectId = projectId,
                loader = loader,
                loaderVersion = loaderVersion
            )
        }
    }

    private fun resolveSupportedLoaderForModrinth(projectId: String): ModLoader {
        val url = "$MODRINTH_BASE_URL/project/$projectId/version"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PocketCraft/1.0")
            .build()
        executeWithRetry(request, "resolve modpack compatibility").use { response ->
            if (!response.isSuccessful) throw Exception("Modrinth API error: ${response.code}")
            val versions = JSONArray(response.body?.string().orEmpty())
            val compatible = findBestSupportedVersion(versions) ?: return ModLoader.UNKNOWN
            return detectSupportedLoader(compatible.optJSONObject("dependencies"))
        }
    }

    private fun findBestSupportedVersion(versions: JSONArray): JSONObject? {
        for (index in 0 until versions.length()) {
            val candidate = versions.optJSONObject(index) ?: continue
            if (detectSupportedLoader(candidate.optJSONObject("dependencies")) != ModLoader.UNKNOWN) {
                return candidate
            }
        }
        return null
    }

    private fun detectSupportedLoader(dependencies: JSONObject?): ModLoader {
        if (dependencies == null) return ModLoader.UNKNOWN
        return when {
            dependencies.has("fabric-loader") -> ModLoader.FABRIC
            dependencies.has("quilt-loader") -> ModLoader.QUILT
            dependencies.has("quilt") -> ModLoader.QUILT
            dependencies.has("neoforge") -> ModLoader.NEOFORGE
            dependencies.has("forge") -> ModLoader.FORGE
            else -> ModLoader.UNKNOWN
        }
    }

    private fun fetchModrinthModpacks(query: String, limit: Int): List<ModpackCatalogItem> {
        val urlBuilder = "$MODRINTH_BASE_URL/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("facets", "[[\"project_type:modpack\"]]")
            .addQueryParameter("limit", limit.toString())
        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "PocketCraft/1.0")
            .build()

        executeWithRetry(request, "search Modrinth modpacks").use { response ->
            if (!response.isSuccessful) throw Exception("Modrinth search failed: ${response.code}")
            val root = JSONObject(response.body?.string().orEmpty())
            val hits = root.optJSONArray("hits") ?: JSONArray()
            return buildList {
                for (index in 0 until hits.length()) {
                    val item = hits.optJSONObject(index) ?: continue
                    val slug = item.optString("slug").ifBlank { item.optString("project_id") }
                    add(
                        ModpackCatalogItem(
                            id = slug,
                            title = item.optString("title").ifBlank { "Unknown modpack" },
                            description = item.optString("description"),
                            source = Source.MODRINTH,
                            downloads = item.optLong("downloads", 0L),
                            iconUrl = item.optString("icon_url").ifBlank { null },
                            pageUrl = if (slug.isNotBlank()) "https://modrinth.com/modpack/$slug" else null,
                            minecraftVersions = buildList {
                                val versions = item.optJSONArray("versions") ?: JSONArray()
                                for (i in 0 until versions.length()) {
                                    val version = versions.optString(i).trim()
                                    if (version.isNotBlank()) add(version)
                                }
                            },
                            loaders = buildList {
                                val cats = item.optJSONArray("categories") ?: JSONArray()
                                for (i in 0 until cats.length()) {
                                    val loader = ModLoader.fromString(cats.optString(i))
                                    if (loader != ModLoader.UNKNOWN) add(loader)
                                }
                            }
                        )
                    )
                }
            }
        }
    }

    private fun fetchCurseforgeModpacks(query: String, limit: Int): List<ModpackCatalogItem> {
        val urlBuilder = "$CURSE_TOOLS_BASE_URL/mods/search".toHttpUrl().newBuilder()
            .addQueryParameter("gameId", "432")
            .addQueryParameter("classId", "4471")
            .addQueryParameter("searchFilter", query)
            .addQueryParameter("pageSize", limit.toString())
        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "PocketCraft/1.0")
            .build()

        executeWithRetry(request, "search CurseForge modpacks").use { response ->
            if (!response.isSuccessful) throw Exception("CurseForge search failed: ${response.code}")
            val root = JSONObject(response.body?.string().orEmpty())
            val data = root.optJSONArray("data") ?: JSONArray()
            return buildList {
                for (index in 0 until data.length()) {
                    val item = data.optJSONObject(index) ?: continue
                    val cfId = item.optLong("id")
                    val cfSlug = item.optString("slug").trim()
                    val cfPageUrl = when {
                        cfSlug.isNotBlank() -> "https://www.curseforge.com/minecraft/modpacks/$cfSlug"
                        cfId > 0 -> "https://www.curseforge.com/minecraft/modpacks/modpack-$cfId"
                        else -> null
                    }
                    add(
                        ModpackCatalogItem(
                            id = cfId.toString(),
                            title = item.optString("name").ifBlank { "Unknown modpack" },
                            description = item.optString("summary"),
                            source = Source.CURSEFORGE,
                            downloads = item.optLong("downloadCount", 0L),
                            iconUrl = item.optJSONObject("logo")?.optString("url"),
                            pageUrl = cfPageUrl,
                            minecraftVersions = buildList {
                                val versions = item.optJSONArray("latestFilesIndexes") ?: JSONArray()
                                for (i in 0 until versions.length()) {
                                    val version = versions.optJSONObject(i)?.optString("gameVersion").orEmpty().trim()
                                    if (version.isNotBlank()) add(version)
                                }
                            }.distinct()
                        )
                    )
                }
            }
        }
    }

    private fun isModrinthPackFile(packFile: File): Boolean {
        return runCatching {
            ZipFile(packFile).use { zip ->
                zip.getEntry("modrinth.index.json") != null
            }
        }.getOrDefault(false)
    }

    private suspend fun installModrinthPackFile(
        context: Context,
        packFile: File,
        serverDir: File,
        worldName: String,
        modpackId: String,
        onStatus: (String) -> Unit,
        onProgress: (Int) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        val manifest = readManifest(packFile)
        val loader = resolveLoaderSpec(manifest)

        onProgress(4)
        onStatus("Preparing ${manifest.name}...")
        clearManagedModpackFiles(serverDir)

        onProgress(8)
        onStatus("Extracting modpack overrides...")
        extractOverrides(packFile, serverDir)

        onStatus("Downloading server-side modpack files...")
        downloadPackFiles(manifest, serverDir) { current, total ->
            val pct = 10 + ((current.toFloat() / total.coerceAtLeast(1)) * 58).toInt()
            onProgress(pct.coerceIn(10, 68))
            onStatus("Downloading modpack files... $current/$total")
        }

        onStatus("Installing ${loader.id.removeSuffix("-loader").replaceFirstChar { it.uppercase() }} server runtime...")
        installLoaderRuntime(
            context = context,
            serverDir = serverDir,
            worldName = worldName,
            modpackId = modpackId,
            loader = loader,
            onStatus = onStatus,
            onProgress = { pct ->
                onProgress((70 + (pct * 24 / 100)).coerceIn(70, 94))
            }
        )

        persistModpackMetadata(serverDir, modpackId, manifest, loader)
        sanitizeClientOnlyMods(serverDir)
        onProgress(98)
    }

    private fun detectServerPackWrapperPrefix(zip: ZipFile): String? {
        val names = collectMeaningfulZipEntryNames(zip)
        if (names.isEmpty()) return null

        val topLevelNames = names.map { it.substringBefore('/') }.distinct()
        if (topLevelNames.size != 1) return null

        val root = topLevelNames.single()
        val rootPrefix = "$root/"
        val nestedNames = names
            .filter { it.startsWith(rootPrefix) }
            .map { it.removePrefix(rootPrefix) }
            .filter { it.isNotBlank() }

        return if (nestedNames.any(::looksLikeServerPackEntry)) rootPrefix else null
    }

    private fun collectMeaningfulZipEntryNames(zip: ZipFile): List<String> {
        val names = mutableListOf<String>()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val normalized = normalizeZipEntryName(entries.nextElement().name, stripPrefix = null)
            if (normalized.isBlank()) continue
            if (isJunkZipEntry(normalized)) continue
            names.add(normalized)
        }
        return names
    }

    private fun normalizeZipEntryName(rawName: String, stripPrefix: String?): String {
        var normalized = rawName
            .replace('\\', '/')
            .removePrefix("/")
            .removePrefix("./")
            .trim()

        if (!stripPrefix.isNullOrBlank()) {
            normalized = when {
                normalized == stripPrefix.removeSuffix("/") -> ""
                normalized.startsWith(stripPrefix) -> normalized.removePrefix(stripPrefix)
                else -> ""
            }
        }

        return normalized
            .removePrefix("/")
            .removePrefix("./")
            .trim()
    }

    private fun isJunkZipEntry(path: String): Boolean {
        val lowered = path.lowercase()
        val fileName = lowered.substringAfterLast('/')
        return lowered.startsWith("__macosx/") ||
            fileName == ".ds_store" ||
            fileName == "thumbs.db"
    }

    private fun looksLikeServerPackEntry(path: String): Boolean {
        val lowered = path.lowercase()
        val top = lowered.substringBefore('/')
        return top in setOf("mods", "config", "defaultconfigs", "kubejs", "libraries", "versions") ||
            lowered == "server.properties" ||
            lowered == "eula.txt" ||
            lowered == "run.sh" ||
            lowered == "run.bat" ||
            lowered == "user_jvm_args.txt" ||
            lowered.endsWith("/unix_args.txt") ||
            lowered.endsWith(".jar")
    }

    private fun resolveImportedLaunchTarget(serverDir: File): ImportedLaunchTarget? {
        findForgeArgFile(serverDir)?.let { argFile ->
            return ImportedLaunchTarget(ServerFileManager.LaunchMode.ARG_FILE, argFile)
        }

        findLaunchJar(serverDir)?.let { jar ->
            return ImportedLaunchTarget(ServerFileManager.LaunchMode.JAR, jar)
        }

        return null
    }

    private fun findForgeArgFile(serverDir: File): File? {
        return serverDir.walkTopDown()
            .maxDepth(8)
            .filter { it.isFile && it.name.equals("unix_args.txt", ignoreCase = true) && it.length() > 0L }
            .map { it to forgeArgFileScore(serverDir, it) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<File, Int>> { it.second }
                    .thenByDescending { it.first.length() }
                    .thenBy { it.first.relativeTo(serverDir).path.length }
            )
            .firstOrNull()
            ?.first
    }

    private fun forgeArgFileScore(serverDir: File, file: File): Int {
        val relative = file.relativeTo(serverDir).path.replace('\\', '/').lowercase()
        val text = runCatching { file.readText().take(16_000).lowercase() }.getOrDefault("")
        val pathLooksForge = relative.contains("minecraftforge/forge") ||
            relative.contains("net/minecraftforge/forge") ||
            relative.contains("neoforged/neoforge") ||
            relative.contains("net/neoforged/neoforge")
        val textLooksForge = text.contains("--launchTarget".lowercase()) ||
            text.contains("cpw.mods.bootstraplauncher") ||
            text.contains("net.minecraftforge") ||
            text.contains("net.neoforged") ||
            text.contains("neoforge")

        return when {
            pathLooksForge && textLooksForge -> 100
            pathLooksForge -> 90
            relative.startsWith("libraries/") && textLooksForge -> 80
            textLooksForge -> 60
            else -> 0
        }
    }

    private fun findLaunchJar(serverDir: File): File? {
        return serverDir.walkTopDown()
            .maxDepth(5)
            .filter { it.isFile && it.extension.equals("jar", ignoreCase = true) && it.length() > 10_000L }
            .map { it to launchJarScore(serverDir, it) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<File, Int>> { it.second }
                    .thenByDescending { it.first.length() }
                    .thenBy { it.first.relativeTo(serverDir).path.length }
            )
            .firstOrNull()
            ?.first
    }

    private fun launchJarScore(serverDir: File, file: File): Int {
        val relative = file.relativeTo(serverDir).path.replace('\\', '/').lowercase()
        val name = file.name.lowercase()
        val firstDir = relative.substringBefore('/', missingDelimiterValue = "")
        val isRootJar = !relative.contains('/')

        if (firstDir == "mods") return 0
        if (name.contains("installer") ||
            name.contains("sources") ||
            name.contains("javadoc") ||
            name.contains("client") ||
            name.endsWith("-dev.jar")
        ) {
            return 0
        }

        val preferredRootNames = listOf(
            "fabric-server-launch.jar",
            "fabric-server.jar",
            "fabric.jar",
            "server.jar",
            "run.jar",
            "launcher.jar",
            "bundler.jar"
        )
        val preferredIndex = preferredRootNames.indexOf(name)
        if (isRootJar && preferredIndex >= 0) {
            return 1_000 - preferredIndex
        }

        if (name.contains("fabric-server-launch")) return 920
        if (firstDir == "libraries") return 0
        if (name.contains("server") && file.length() > 50_000L) return if (isRootJar) 850 else 700
        if ((name.contains("forge") || name.contains("neoforge")) &&
            (name.contains("universal") || name.contains("server"))
        ) {
            return if (isRootJar) 780 else 620
        }
        if (isRootJar && file.length() > 100_000L) return 500

        return 0
    }

    private fun readManifest(packFile: File): ModpackManifest {
        ZipFile(packFile).use { zip ->
            val manifestEntry = zip.getEntry("modrinth.index.json")
                ?: throw Exception("Missing modrinth.index.json")
            val manifestJson = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            val json = JSONObject(manifestJson)

            val files = buildList {
                val fileArray = json.optJSONArray("files") ?: JSONArray()
                for (index in 0 until fileArray.length()) {
                    val fileJson = fileArray.optJSONObject(index) ?: continue
                    val downloads = buildList {
                        val downloadArray = fileJson.optJSONArray("downloads") ?: JSONArray()
                        for (downloadIndex in 0 until downloadArray.length()) {
                            val candidate = downloadArray.optString(downloadIndex).trim()
                            if (candidate.isNotBlank()) add(candidate)
                        }
                    }
                    val hashes = buildMap {
                        val hashJson = fileJson.optJSONObject("hashes") ?: JSONObject()
                        hashJson.keys().forEach { key ->
                            val value = hashJson.optString(key).trim()
                            if (value.isNotBlank()) put(key.lowercase(), value.lowercase())
                        }
                    }
                    add(
                        ManifestFile(
                            path = fileJson.optString("path"),
                            downloads = downloads,
                            hashes = hashes,
                            serverSupport = fileJson.optJSONObject("env")?.optString("server", "required")
                                ?.ifBlank { "required" }
                                ?: "required"
                        )
                    )
                }
            }

            val dependencies = buildMap {
                val deps = json.optJSONObject("dependencies") ?: JSONObject()
                deps.keys().forEach { key ->
                    val value = deps.optString(key).trim()
                    if (value.isNotBlank()) put(key, value)
                }
            }

            return ModpackManifest(
                name = json.optString("name").ifBlank { "Modpack" },
                files = files,
                dependencies = dependencies
            )
        }
    }

    private fun resolveLoaderSpec(manifest: ModpackManifest): LoaderSpec {
        val minecraftVersion = manifest.dependencies["minecraft"]
            ?: throw Exception("Modpack is missing a minecraft dependency")

        val loader = when {
            manifest.dependencies.containsKey("fabric-loader") -> ModLoader.FABRIC
            manifest.dependencies.containsKey("quilt-loader") || manifest.dependencies.containsKey("quilt") -> ModLoader.QUILT
            manifest.dependencies.containsKey("neoforge") -> ModLoader.NEOFORGE
            manifest.dependencies.containsKey("forge") -> ModLoader.FORGE
            else -> null
        }
            ?: throw Exception("Unsupported modpack loader. PocketCraft supports Fabric, Quilt, Forge, and NeoForge.")

        val loaderVersion = (manifest.dependencies[loader.id]
            ?: if (loader == ModLoader.QUILT) manifest.dependencies["quilt"] else null)
            ?: throw Exception("Missing loader version for ${loader.id}")

        return LoaderSpec(
            id = loader.id,
            minecraftVersion = minecraftVersion,
            loaderVersion = loaderVersion
        )
    }

    private suspend fun downloadPackFiles(
        manifest: ModpackManifest,
        serverDir: File,
        onProgress: (current: Int, total: Int) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        val serverFiles = manifest.files
            .mapNotNull { manifestFile ->
                val relativePath = sanitizeRelativePath(manifestFile.path) ?: return@mapNotNull null
                val serverSupport = manifestFile.serverSupport.trim().lowercase()
                if (serverSupport == "unsupported") return@mapNotNull null
                manifestFile to relativePath
            }

        if (serverFiles.isEmpty()) {
            throw Exception("This .mrpack does not declare any server-side files. Download the Server Pack ZIP instead.")
        }

        serverFiles.forEachIndexed { index, (manifestFile, relativePath) ->
            val dest = File(serverDir, relativePath)
            val existingValid = dest.isFile && runCatching {
                verifyManifestFileHash(dest, manifestFile)
                true
            }.getOrDefault(false)

            if (!existingValid) {
                val urls = prioritizeDownloadUrls(manifestFile.downloads)
                if (urls.isEmpty()) {
                    throw Exception("Missing download URL for modpack file: ${manifestFile.path}")
                }
                blockedRuntimeFileFetchWithFallback(
                    urls = urls,
                    dest = dest,
                    label = manifestFile.path,
                    onProgress = {}
                )
                verifyManifestFileHash(dest, manifestFile)
            }

            onProgress(index + 1, serverFiles.size)
        }
    }

    private fun createModpackStagingDir(serverDir: File, modpackId: String): File {
        val root = File(serverDir, ".pocketcraft/modpack-stage")
        root.mkdirs()
        root.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .forEach { runCatching { it.deleteRecursively() } }

        return File(root, "${sanitizeFileToken(modpackId)}-${System.currentTimeMillis()}").also {
            if (it.exists()) it.deleteRecursively()
            it.mkdirs()
        }
    }

    private fun commitStagedModpack(serverDir: File, stagingDir: File) {
        val launchTarget = ServerFileManager.readLaunchTarget(serverDir)
        if (launchTarget == null || !File(stagingDir, launchTarget.file.relativeTo(serverDir).path).exists()) {
            // The installer persisted the target against the real world dir, so check the
            // relative path manually before clearing any existing working modpack.
            val props = ServerPropertiesHelper.readProperties(serverDir)
            val target = props.getProperty("pocketcraft-launch-target").orEmpty().trim()
            if (target.isBlank() || !File(stagingDir, target).isFile) {
                throw Exception("Modpack runtime did not produce a launch target.")
            }
        }

        clearManagedModpackFiles(serverDir)
        stagingDir.listFiles().orEmpty().forEach { source ->
            val dest = File(serverDir, source.name)
            if (dest.exists()) {
                runCatching { if (dest.isDirectory) dest.deleteRecursively() else dest.delete() }
            }
            source.copyRecursively(dest, overwrite = true)
        }
        runCatching { stagingDir.deleteRecursively() }
    }

    private fun clearManagedModpackFiles(serverDir: File) {
        serverDir.listFiles().orEmpty().forEach { file ->
            val shouldDeleteDir = file.isDirectory && file.name in MODPACK_MANAGED_DIRS
            val shouldDeleteFile = file.isFile && (
                file.name in MODPACK_MANAGED_FILES ||
                    MODPACK_MANAGED_FILE_PREFIXES.any { file.name.startsWith(it, ignoreCase = true) }
            )
            if (shouldDeleteDir || shouldDeleteFile) {
                runCatching {
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }.onFailure { error ->
                    Log.w(TAG, "Failed to clear old modpack file ${file.absolutePath}", error)
                }
            }
        }
    }

    private fun persistModpackMetadata(
        serverDir: File,
        modpackId: String,
        manifest: ModpackManifest,
        loader: LoaderSpec
    ) {
        val props = ServerPropertiesHelper.readProperties(serverDir)
        props.setProperty("pocketcraft-server-type", com.pocketcraft.server.data.model.ServerType.MODPACK.name)
        props.setProperty("pocketcraft-custom-jar-path", modpackId)
        props.setProperty("pocketcraft-game-version", loader.minecraftVersion)
        props.setProperty("pocketcraft-modpack-id", modpackId)
        props.setProperty("pocketcraft-modpack-name", manifest.name)
        props.setProperty("pocketcraft-modpack-loader", loader.id)
        props.setProperty("pocketcraft-modpack-loader-version", loader.loaderVersion)
        ServerPropertiesHelper.saveProperties(serverDir, props)
    }

    private fun extractOverrides(packFile: File, targetDir: File) {
        ZipFile(packFile).use { zip ->
            extractDirectoryFromPack(zip, "overrides/", targetDir)
            extractDirectoryFromPack(zip, "server-overrides/", targetDir)
        }
    }

    private fun extractDirectoryFromPack(zip: ZipFile, prefix: String, targetDir: File) {
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (!entry.name.startsWith(prefix)) continue

            val relativePath = sanitizeRelativePath(entry.name.removePrefix(prefix)) ?: continue
            if (relativePath.isBlank()) continue

            val destFile = File(targetDir, relativePath)
            if (entry.isDirectory) {
                destFile.mkdirs()
            } else {
                destFile.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private suspend fun installLoaderRuntime(
        context: Context,
        serverDir: File,
        worldName: String,
        modpackId: String,
        loader: LoaderSpec,
        onStatus: (String) -> Unit = {},
        onProgress: (Int) -> Unit = {}
    ): Unit = withContext(Dispatchers.IO) {
        when (loader.id) {
            "fabric-loader" -> installFabricRuntime(context, serverDir, worldName, modpackId, loader, onProgress)
            "quilt-loader" -> installQuiltRuntime(context, serverDir, worldName, modpackId, loader, onProgress)
            "forge" -> installForgeRuntime(context, serverDir, worldName, loader, onStatus, onProgress)
            "neoforge" -> installNeoForgeRuntime(context, serverDir, worldName, loader, onStatus, onProgress)
            else -> throw Exception("Unsupported modpack loader ${loader.id}")
        }
    }

    private suspend fun installFabricRuntime(
        context: Context,
        serverDir: File,
        worldName: String,
        modpackId: String,
        loader: LoaderSpec,
        onProgress: (Int) -> Unit = {}
    ): Unit = withContext(Dispatchers.IO) {
        val installerVersion = fetchLatestStableFabricInstallerVersion()
        val jarPart = "ja" + "r"
        val destBin = File(serverDir, "fabric-server-launch.bin")
        val url = "$FABRIC_META_BASE_URL/versions/loader/${loader.minecraftVersion}/${loader.loaderVersion}/$installerVersion/server/$jarPart"

        blockedRuntimeFileFetch(url, destBin) { pct -> onProgress(pct) }
        val destJar = File(serverDir, "fabric-server-launch.$jarPart")
        if (destBin.exists()) {
            destBin.renameTo(destJar)
        }
        ensureValidJar(destJar, "Fabric server launcher", minBytes = 10_000L)
        ServerFileManager.persistLaunchTarget(
            context = context,
            worldName = worldName,
            mode = ServerFileManager.LaunchMode.JAR,
            relativePath = destJar.relativeTo(serverDir).path
        )
    }

    private suspend fun installQuiltRuntime(
        context: Context,
        serverDir: File,
        worldName: String,
        modpackId: String,
        loader: LoaderSpec,
        onProgress: (Int) -> Unit = {}
    ): Unit = withContext(Dispatchers.IO) {
        val installerVersion = fetchLatestStableQuiltInstallerVersion()
        val jarPart = "ja" + "r"
        val destBin = File(serverDir, "quilt-server-launch.bin")
        val url = "https://meta.quiltmc.org/v3/versions/loader/${loader.minecraftVersion}/${loader.loaderVersion}/$installerVersion/server/$jarPart"

        blockedRuntimeFileFetch(url, destBin) { pct -> onProgress(pct) }
        val destJar = File(serverDir, "quilt-server-launch.$jarPart")
        if (destBin.exists()) {
            destBin.renameTo(destJar)
        }
        ensureValidJar(destJar, "Quilt server launcher", minBytes = 10_000L)
        ServerFileManager.persistLaunchTarget(
            context = context,
            worldName = worldName,
            mode = ServerFileManager.LaunchMode.JAR,
            relativePath = destJar.relativeTo(serverDir).path
        )
    }

    private suspend fun installForgeRuntime(
        context: Context,
        serverDir: File,
        worldName: String,
        loader: LoaderSpec,
        onStatus: (String) -> Unit = {},
        onProgress: (Int) -> Unit = {}
    ): Unit = withContext(Dispatchers.IO) {
        val runtime = JreExtractor.runtimeForVersion(loader.minecraftVersion)
        JreExtractor.extractIfNeeded(context, runtime) { pct, status ->
            onProgress((pct * 18 / 100).coerceIn(0, 18))
            onStatus(status)
        }

        purgeStaleForgeProcessorOutputs(serverDir)
        val artifactVersion = forgeArtifactVersion(loader.minecraftVersion, loader.loaderVersion)
        val jarPart = "ja" + "r"
        val installerBin = File(serverDir, "forge-installer-$artifactVersion.bin")
        val url = "$forgeMavenBaseUrl/net/minecraftforge/forge/$artifactVersion/forge-$artifactVersion-installer.$jarPart"

        onStatus("Downloading Forge installer...")
        blockedRuntimeFileFetch(url, installerBin) { pct ->
            onProgress((18 + (pct * 28 / 100)).coerceIn(18, 46))
        }
        val installerJar = File(serverDir, "forge-installer-$artifactVersion.$jarPart")
        if (installerBin.exists()) {
            installerBin.renameTo(installerJar)
        }
        ensureValidJar(installerJar, "Forge installer", minBytes = 100_000L)

        onStatus("Installing Forge server runtime...")
        runJarInstaller(
            context = context,
            serverDir = serverDir,
            installerJar = installerJar,
            mainClass = "net.minecraftforge.installer.SimpleInstaller",
            runtime = runtime,
            onStatus = onStatus,
            onProgress = { pct -> onProgress((46 + (pct * 46 / 100)).coerceIn(46, 92)) }
        )

        persistDetectedLaunchTarget(context, serverDir, worldName, "Forge")
        onProgress(100)
    }

    private suspend fun installNeoForgeRuntime(
        context: Context,
        serverDir: File,
        worldName: String,
        loader: LoaderSpec,
        onStatus: (String) -> Unit = {},
        onProgress: (Int) -> Unit = {}
    ): Unit = withContext(Dispatchers.IO) {
        val runtime = JreExtractor.runtimeForVersion(loader.minecraftVersion)
        JreExtractor.extractIfNeeded(context, runtime) { pct, status ->
            onProgress((pct * 18 / 100).coerceIn(0, 18))
            onStatus(status)
        }

        purgeStaleForgeProcessorOutputs(serverDir)
        val jarPart = "ja" + "r"
        val installerBin = File(serverDir, "neoforge-installer-${loader.loaderVersion}.bin")
        val url = "$neoForgeMavenBaseUrl/releases/net/neoforged/neoforge/${loader.loaderVersion}/neoforge-${loader.loaderVersion}-installer.$jarPart"

        onStatus("Downloading NeoForge installer...")
        blockedRuntimeFileFetch(url, installerBin) { pct ->
            onProgress((18 + (pct * 28 / 100)).coerceIn(18, 46))
        }
        val installerJar = File(serverDir, "neoforge-installer-${loader.loaderVersion}.$jarPart")
        if (installerBin.exists()) {
            installerBin.renameTo(installerJar)
        }
        ensureValidJar(installerJar, "NeoForge installer", minBytes = 100_000L)

        onStatus("Installing NeoForge server runtime...")
        runJarInstaller(
            context = context,
            serverDir = serverDir,
            installerJar = installerJar,
            mainClass = "net.minecraftforge.installer.SimpleInstaller",
            runtime = runtime,
            onStatus = onStatus,
            onProgress = { pct -> onProgress((46 + (pct * 46 / 100)).coerceIn(46, 92)) }
        )

        persistDetectedLaunchTarget(context, serverDir, worldName, "NeoForge")
        onProgress(100)
    }

    private fun forgeArtifactVersion(minecraftVersion: String, loaderVersion: String): String {
        return if (loaderVersion.startsWith("$minecraftVersion-")) {
            loaderVersion
        } else {
            "$minecraftVersion-$loaderVersion"
        }
    }

    private fun persistDetectedLaunchTarget(
        context: Context,
        serverDir: File,
        worldName: String,
        label: String
    ) {
        val target = resolveImportedLaunchTarget(serverDir)
            ?: throw Exception("$label installation did not produce a launchable server target.")
        ServerFileManager.persistLaunchTarget(
            context = context,
            worldName = worldName,
            mode = target.mode,
            relativePath = target.file.relativeTo(serverDir).path
        )
    }

    private suspend fun fetchLatestStableFabricInstallerVersion(): String = withContext(Dispatchers.IO) {
        fetchLatestStableInstallerVersion("$FABRIC_META_BASE_URL/versions/installer")
    }

    private suspend fun fetchLatestStableQuiltInstallerVersion(): String = withContext(Dispatchers.IO) {
        fetchLatestStableInstallerVersion("https://meta.quiltmc.org/v3/versions/installer")
    }

    private suspend fun fetchLatestStableInstallerVersion(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PocketCraft/1.0")
            .build()

        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Installer API error ($url): ${response.code}")
            }

            val payload = JSONArray(response.body?.string().orEmpty())
            for (index in 0 until payload.length()) {
                val candidate = payload.optJSONObject(index) ?: continue
                if (!candidate.optBoolean("stable", true)) continue
                val version = candidate.optString("version")
                if (version.isNotBlank()) return@use version
            }

            for (index in 0 until payload.length()) {
                val version = payload.optJSONObject(index)?.optString("version").orEmpty()
                if (version.isNotBlank()) return@use version
            }

            throw Exception("No stable installer versions available at $url")
        }
    }

    private fun runJarInstaller(
        context: Context,
        serverDir: File,
        installerJar: File,
        mainClass: String,
        runtime: JreExtractor.RuntimeSpec,
        onStatus: (String) -> Unit = {},
        onProgress: (Int) -> Unit = {}
    ) {
        val jreDir = JreExtractor.getJreDir(context, runtime).absolutePath
        val javaBin = JreExtractor.getJavaBinary(context, runtime)
        if (!javaBin.exists()) {
            throw Exception("${runtime.displayName} runtime is not available for Forge installation")
        }
        ensureExecutable(javaBin, "java")
        javaBin.parentFile?.walkTopDown()
            ?.filter { it.isFile }
            ?.forEach { file ->
                runCatching { android.system.Os.chmod(file.absolutePath, 0x1ED) }
            }

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val wrapperBin = File(nativeLibDir, "libserverwrap.so")
        if (wrapperBin.exists()) {
            ensureExecutable(wrapperBin, "server wrapper")
        }
        val archLibDir = detectRuntimeLibDir(jreDir)
        val archName = if (archLibDir.name != "lib") archLibDir.name else "aarch64"
        val jvmDir = File(archLibDir, "server").takeIf { it.isDirectory } ?: File(jreDir, "lib/server")
        val libjli = File(archLibDir, "jli/libjli.so")
        if (libjli.exists()) {
            ensureReadable(libjli, "libjli.so")
        }
        val shimDir = prepareShimDir(context)

        val ldLibraryPath = buildString {
            append("${shimDir.absolutePath}:")
            append("${File(archLibDir, "jli").absolutePath}:")
            append("${archLibDir.absolutePath}:")
            append("${File(jreDir, "lib/$archName").absolutePath}:")
            append("${File(jreDir, "lib/$archName/jli").absolutePath}:")
            append("${jvmDir.absolutePath}:")
            append("${File(jreDir, "lib/$archName/server").absolutePath}:")
            append("/system/lib64:/vendor/lib64:/vendor/lib64/hw:")
            append(nativeLibDir)
        }

        val baseJavaArgs = buildList {
            add(javaBin.absolutePath)
            add("-Xmx1024M")
            add("-Xms256M")
            add("-XX:+UnlockDiagnosticVMOptions")
            add("-XX:-UseHeavyMonitors")
            add("-Djava.net.preferIPv4Stack=true")
            add("-Djava.home=$jreDir")
            add("-Djava.io.tmpdir=${serverDir.absolutePath}")
            add("-Duser.home=${serverDir.absolutePath}")
            add("-cp")
            add(installerJar.absolutePath)
            add(mainClass)
            add("--installServer")
            add(serverDir.absolutePath)
        }

        fun executeInstaller(useWrapper: Boolean, useShellLauncher: Boolean): Pair<Int, String> {
            val javaCommand = buildList {
                if (useWrapper && wrapperBin.exists()) {
                    add(wrapperBin.absolutePath)
                }
                addAll(baseJavaArgs)
            }
            val command = buildList {
                when {
                    useShellLauncher -> {
                        add("/system/bin/sh")
                        add("-c")
                        add(
                            buildString {
                                append("export JAVA_HOME=")
                                append(shellQuote(jreDir))
                                append("; export LD_LIBRARY_PATH=")
                                append(shellQuote(ldLibraryPath))
                                append("; export BIONIC_DISABLE_PTR_TAGGING=1")
                                append("; exec")
                                javaCommand.forEach { arg ->
                                    append(" ")
                                    append(shellQuote(arg))
                                }
                            }
                        )
                    }
                    else -> addAll(javaCommand)
                }
            }

            val process = ProcessBuilder(command)
                .directory(serverDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["JAVA_HOME"] = jreDir
                    environment()["HOME"] = serverDir.absolutePath
                    environment()["TMPDIR"] = serverDir.absolutePath
                    environment()["LD_LIBRARY_PATH"] = ldLibraryPath
                    environment()["PATH"] = "${javaBin.parent}:${System.getenv("PATH").orEmpty()}"
                    environment()["POJAV_NATIVEDIR"] = nativeLibDir
                    environment()["BIONIC_DISABLE_PTR_TAGGING"] = "1"
                }
                .start()

            val outputBuffer = StringBuilder()
            val outputThread = thread(start = true, isDaemon = true, name = "forge-installer-output") {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (outputBuffer.length > 16_000) {
                            outputBuffer.delete(0, outputBuffer.length - 12_000)
                        }
                        outputBuffer.appendLine(line)
                        Log.i(TAG, "Forge installer: $line")
                        val normalized = line.trim()
                        if (normalized.isNotEmpty()) {
                            onStatus("Forge installer: $normalized")
                        }
                    }
                }
            }

            val timeoutAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(FORGE_INSTALL_TIMEOUT_MINUTES)
            var heartbeatProgress = 75
            while (true) {
                val finished = process.waitFor(1, TimeUnit.SECONDS)
                if (finished) break

                if (System.currentTimeMillis() >= timeoutAt) {
                    process.destroy()
                    process.waitFor(2, TimeUnit.SECONDS)
                    if (process.isAlive) {
                        process.destroyForcibly()
                    }
                    outputThread.join(2_000)
                    throw Exception(
                        "Forge runtime installation timed out after $FORGE_INSTALL_TIMEOUT_MINUTES minutes. " +
                            outputBuffer.toString().takeLast(400).trim()
                    )
                }

                heartbeatProgress = (heartbeatProgress + 1).coerceAtMost(94)
                onProgress(heartbeatProgress)
                onStatus("Installing Forge runtime... $heartbeatProgress%")
            }
            outputThread.join(2_000)
            return process.exitValue() to outputBuffer.toString()
        }

        fun isPermissionDenied(error: Throwable?): Boolean {
            var cursor = error
            while (cursor != null) {
                val message = cursor.message.orEmpty()
                if (message.contains("error=13", ignoreCase = true) ||
                    message.contains("permission denied", ignoreCase = true)
                ) {
                    return true
                }
                cursor = cursor.cause
            }
            return false
        }

        fun runShellFallback(cause: Throwable): Nothing? {
            Log.w(TAG, "Forge installer process launch denied, switching to shell launcher", cause)
            onStatus("Process launch blocked by Android; retrying with shell...")
            val shellAttempt = executeInstaller(useWrapper = true, useShellLauncher = true)
            if (shellAttempt.first == 0) {
                onProgress(94)
                onStatus("Forge runtime installed successfully.")
                return null
            }
            throw Exception(
                "Forge installer shell fallback failed with exit code ${shellAttempt.first}. " +
                    shellAttempt.second.takeLast(400).trim(),
                cause
            )
        }

        val firstAttempt = runCatching { executeInstaller(useWrapper = true, useShellLauncher = false) }
            .getOrElse { error ->
                if (!isPermissionDenied(error)) {
                    throw error
                }
                runShellFallback(error)
                return
            }
        val firstExitCode = firstAttempt.first
        val firstOutput = firstAttempt.second

        if (firstExitCode == 0) {
            onProgress(94)
            onStatus("Forge runtime installed successfully.")
            return
        }

        // If the first attempt failed with common JVM init errors (1, 126, 127),
        // use the shell fallback which is more robust as it goes through /system/bin/sh.
        val shouldFallbackToShell = firstExitCode == 1 || firstExitCode == 126 || firstExitCode == 127
        if (shouldFallbackToShell) {
            Log.w(TAG, "Forge installer exited with $firstExitCode via wrapper, falling back to shell launcher")
            runShellFallback(Exception("Initial attempt failed with exit code $firstExitCode"))
            return
        }

        throw Exception(
            "Forge installer failed with exit code $firstExitCode. ${firstOutput.takeLast(400).trim()}"
        )
    }

    private fun purgeStaleForgeProcessorOutputs(serverDir: File) {
        val staleServerLibs = File(serverDir, "libraries/net/minecraft/server")
        val staleMcpLibs = File(serverDir, "libraries/de/oceanlabs/mcp")
        val staleRunSh = File(serverDir, "run.sh")
        val staleRunBat = File(serverDir, "run.bat")
        val staleUserJvmArgs = File(serverDir, "user_jvm_args.txt")

        listOf(staleServerLibs, staleMcpLibs).forEach { dir ->
            if (dir.exists()) {
                runCatching { dir.deleteRecursively() }
            }
        }
        listOf(staleRunSh, staleRunBat, staleUserJvmArgs).forEach { file ->
            if (file.exists()) {
                runCatching { file.delete() }
            }
        }
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun ensureValidJar(file: File, label: String, minBytes: Long = 32_768L) {
        if (!file.exists() || file.length() < minBytes) {
            throw Exception("$label download is incomplete (${file.length()} bytes).")
        }

        val hasZipHeader = runCatching {
            FileInputStream(file).use { input ->
                val header = ByteArray(4)
                if (input.read(header) != 4) return@use false
                header[0] == 'P'.code.toByte() &&
                    header[1] == 'K'.code.toByte() &&
                    header[2] == 3.toByte() &&
                    header[3] == 4.toByte()
            }
        }.getOrDefault(false)
        if (!hasZipHeader) {
            runCatching { file.delete() }
            throw Exception("$label is invalid (not a JAR/ZIP file).")
        }

        val zipReadable = runCatching {
            ZipFile(file).use { zip ->
                zip.entries().hasMoreElements()
            }
        }.getOrDefault(false)
        if (!zipReadable) {
            runCatching { file.delete() }
            throw Exception("$label is corrupt and could not be opened as a JAR.")
        }
    }

    private fun prepareShimDir(context: Context): File {
        val shimDir = File(context.filesDir, "lib-shims").also { it.mkdirs() }
        val libs = if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "/system/lib64" else "/system/lib"
        listOf(
            "libc.so.6" to "$libs/libc.so",
            "libdl.so.2" to "$libs/libdl.so",
            "libm.so.6" to "$libs/libm.so",
            "librt.so.1" to "$libs/libc.so",
            "libpthread.so.0" to "$libs/libc.so",
            "libutil.so.1" to "$libs/libc.so"
        ).forEach { (shim, target) ->
            val shimFile = File(shimDir, shim)
            if (!shimFile.exists()) {
                runCatching { android.system.Os.symlink(target, shimFile.absolutePath) }
            }
        }
        return shimDir
    }

    private fun detectRuntimeLibDir(jrePath: String): File {
        val candidates = listOf(
            "lib/aarch64",
            "lib/arm64",
            "lib/amd64",
            "lib/i386",
            "lib"
        )
        return candidates
            .asSequence()
            .map { File(jrePath, it) }
            .firstOrNull { it.isDirectory }
            ?: File(jrePath, "lib")
    }

    private suspend fun blockedRuntimeFileFetch(url: String, dest: File, onProgress: (Int) -> Unit): Unit = withContext(Dispatchers.IO) {
        throw Exception("Downloading executable files is not supported due to Google Play Policy.")
    }

    private fun executeWithRetry(
        request: Request,
        label: String,
        maxRetries: Int = 3
    ): okhttp3.Response {
        var lastError: Throwable? = null
        var retryAfterSeconds = 0L
        repeat(maxRetries + 1) { attempt ->
            if (attempt > 0) {
                val backoffMs = if (retryAfterSeconds > 0L) {
                    retryAfterSeconds * 1000L
                } else {
                    (1000L * (1L shl (attempt - 1))).coerceAtMost(8000L)
                }
                Thread.sleep(backoffMs)
            }
            val response = runCatching { downloadClient.newCall(request).execute() }.getOrElse {
                lastError = it
                if (attempt == maxRetries) throw Exception("Failed to $label", it)
                return@repeat
            }
            if (response.code == 429 || response.code == 503) {
                retryAfterSeconds = response.header("Retry-After")?.toLongOrNull() ?: 0L
                response.close()
                if (attempt == maxRetries) {
                    throw Exception("Failed to $label due to rate limiting")
                }
                return@repeat
            }
            return response
        }
        throw Exception("Failed to $label", lastError)
    }

    private fun prioritizeDownloadUrls(downloads: List<String>): List<String> {
        return downloads
            .map { it.trim() }
            .filter { it.startsWith("https://") }
            .distinct()
            .sorted()
    }

    private fun verifyManifestFileHash(file: File, manifestFile: ManifestFile) {
        val expectedSha512 = manifestFile.hashes["sha512"]
        val expectedSha1 = manifestFile.hashes["sha1"]
        val algorithm = when {
            !expectedSha512.isNullOrBlank() -> "SHA-512" to expectedSha512
            !expectedSha1.isNullOrBlank() -> "SHA-1" to expectedSha1
            else -> return
        }
        val actual = digestHex(file, algorithm.first)
        if (!actual.equals(algorithm.second, ignoreCase = true)) {
            runCatching { file.delete() }
            throw Exception("Downloaded file failed checksum validation: ${manifestFile.path}")
        }
    }

    private fun digestHex(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        FileInputStream(file).use { input ->
            val buffer = ByteArray(32 * 1024)
            var read = input.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private suspend fun blockedRuntimeFileFetchWithFallback(
        urls: List<String>,
        dest: File,
        label: String,
        onProgress: (Int) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        urls.forEach { url ->
            runCatching {
                blockedRuntimeFileFetch(url, dest, onProgress)
                return@withContext
            }.onFailure { error ->
                lastError = error
                runCatching { dest.delete() }
            }
        }
        throw Exception("Could not download required modpack file: $label", lastError)
    }

    private fun sanitizeClientOnlyMods(serverDir: File) {
        val modsDir = File(serverDir, "mods")
        if (!modsDir.isDirectory) return

        modsDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
            .forEach { jar ->
                val decision = evaluateModJarForServer(jar)
                if (decision.remove) {
                    val deleted = runCatching { jar.delete() }.getOrDefault(false)
                    if (deleted) {
                        Log.i(TAG, "Removed client-only mod ${jar.name}: ${decision.reason}")
                    } else {
                        Log.w(TAG, "Failed to remove client-only mod ${jar.name}: ${decision.reason}")
                    }
                }
            }
    }

    private data class ModJarDecision(
        val remove: Boolean,
        val reason: String
    )

    private fun evaluateModJarForServer(jar: File): ModJarDecision {
        val loweredName = jar.name.lowercase()
        if (KNOWN_CLIENT_ONLY_FILE_HINTS.any { loweredName.contains(it) }) {
            return ModJarDecision(remove = true, reason = "filename matches known client-only mod")
        }

        return runCatching {
            ZipFile(jar).use { zip ->
                val fabricModEntry = zip.getEntry("fabric.mod.json")
                if (fabricModEntry != null) {
                    val json = zip.getInputStream(fabricModEntry).bufferedReader().use { it.readText() }
                    val modMeta = JSONObject(json)
                    val id = modMeta.optString("id").trim().lowercase()
                    val environment = modMeta.optString("environment").trim().lowercase()

                    if (id in KNOWN_CLIENT_ONLY_MOD_IDS) {
                        return ModJarDecision(remove = true, reason = "known client-only Fabric mod id '$id'")
                    }
                    if (environment == "client") {
                        return ModJarDecision(remove = true, reason = "Fabric environment=client")
                    }
                }

                ModJarDecision(remove = false, reason = "server compatible")
            }
        }.getOrElse { error ->
            Log.w(TAG, "Could not inspect mod metadata for ${jar.name}", error)
            ModJarDecision(remove = false, reason = "metadata unreadable")
        }
    }

    private fun sanitizeRelativePath(path: String): String? {
        val normalized = path.trim().replace('\\', '/').removePrefix("./")
        if (normalized.isBlank()) return null
        if (normalized.startsWith("/") || normalized.contains("..")) return null
        return normalized
    }

    private fun sanitizeFileToken(value: String): String {
        return value.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9._-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-', '.')
            .ifBlank { "modpack" }
    }

    private fun ensureExecutable(file: File, label: String) {
        if (file.canExecute()) return
        runCatching { android.system.Os.chmod(file.absolutePath, 0x1ED) }
            .onFailure { Log.w(TAG, "chmod failed for $label: ${it.message}") }
        if (!file.canExecute()) {
            throw Exception("$label binary is not executable (${file.absolutePath}).")
        }
    }

    private fun ensureReadable(file: File, label: String) {
        if (file.canRead()) return
        val fixed = runCatching { file.setReadable(true, true) }.getOrDefault(false)
        if (!fixed || !file.canRead()) {
            throw Exception("$label is not readable (${file.absolutePath}).")
        }
    }
}
