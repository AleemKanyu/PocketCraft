package com.pocketcraft.server.service

import android.content.Context
import android.os.Build
import android.util.Log
import com.pocketcraft.server.setup.JreExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object ModpackManager {
    private const val TAG = "ModpackManager"
    private const val MODRINTH_BASE_URL = "https://api.modrinth.com/v2"
    private const val CURSE_TOOLS_BASE_URL = "https://api.curse.tools/v1/cf"
    private const val FABRIC_META_BASE_URL = "https://meta.fabricmc.net/v2"
    private const val FORGE_COORDINATE_BASE = "net/minecraftforge/forge"
    private const val FORGE_INSTALL_TIMEOUT_MINUTES = 15L
    private val FORGE_INSTALLER_MIRRORS = listOf(
        "https://maven.minecraftforge.net",
        "https://maven.creeperhost.net"
    )
    private val KNOWN_CLIENT_ONLY_MOD_IDS = setOf(
        "better_client",
        "sodium",
        "reeses_sodium_options",
        "continuity",
        "iris",
        "indium"
    )
    private val KNOWN_CLIENT_ONLY_FILE_HINTS = listOf(
        "better_client",
        "sodium",
        "iris",
        "embeddium",
        "optifine"
    )
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
        val serverSupport: String
    )

    private data class LoaderSpec(
        val id: String,
        val minecraftVersion: String,
        val loaderVersion: String
    )

    enum class ModLoader(val id: String, val displayName: String) {
        FABRIC("fabric-loader", "Fabric"),
        FORGE("forge", "Forge"),
        QUILT("quilt-loader", "Quilt"),
        NEOFORGE("neoforge", "NeoForge"),
        UNKNOWN("unknown", "Unknown");

        companion object {
            fun fromId(id: String?): ModLoader {
                return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: UNKNOWN
            }

            fun fromString(value: String?): ModLoader {
                val lowered = value?.lowercase() ?: return UNKNOWN
                return when {
                    lowered.contains("neoforge") -> NEOFORGE
                    lowered.contains("fabric") -> FABRIC
                    lowered.contains("forge") -> FORGE
                    lowered.contains("quilt") -> QUILT
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
        val supportMessage: String? = null
    )

    suspend fun installModpack(
        context: Context,
        modpackId: String,
        onStatus: (String) -> Unit,
        onProgress: (Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            onStatus("Resolving modpack $modpackId...")
            val resolved = resolveModpack(modpackId)
            if (resolved.downloadUrl.isBlank()) {
                return@withContext Result.failure(Exception("Could not find download URL for $modpackId"))
            }

            val serverDir = ServerFileManager.getServerDir(context, modpackId)
            val tempPackFile = File(serverDir, "pack.mrpack")

            onStatus("Downloading modpack package...")
            downloadFile(resolved.downloadUrl, tempPackFile) { percent ->
                onProgress((percent * 0.2f).toInt().coerceIn(0, 20))
            }

            onStatus("Reading modpack manifest...")
            val manifest = readManifest(tempPackFile)
            val loader = resolveLoaderSpec(manifest)

            onStatus("Downloading server mods and configs...")
            downloadPackFiles(manifest, serverDir) { current, total ->
                val progress = if (total <= 0) 20 else 20 + ((current * 40f) / total).toInt()
                onProgress(progress.coerceIn(20, 60))
                if (total > 0) {
                    onStatus("Downloading server mods and configs... ($current/$total)")
                }
            }

            onStatus("Applying modpack overrides...")
            extractOverrides(tempPackFile, serverDir)
            tempPackFile.delete()

            onStatus("Removing client-only mods for server compatibility...")
            sanitizeClientOnlyMods(serverDir)

            onStatus("Installing ${loader.id} runtime...")
            installLoaderRuntime(
                context = context,
                serverDir = serverDir,
                modpackId = modpackId,
                loader = loader,
                onStatus = onStatus,
                onProgress = onProgress
            )

            onStatus("Finalizing modpack setup...")
            onProgress(100)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install modpack $modpackId", e)
            Result.failure(e)
        }
    }

    fun sanitizeInstalledModsForServer(serverDir: File) {
        sanitizeClientOnlyMods(serverDir)
    }

    suspend fun searchModpacks(
        query: String,
        limit: Int = 20
    ): Result<List<ModpackCatalogItem>> = withContext(Dispatchers.IO) {
        val normalizedLimit = limit.coerceIn(1, 50)
        val modrinthItems = runCatching {
            fetchModrinthModpacks(query = query, limit = normalizedLimit)
        }.getOrElse {
            emptyList()
        }
        val curseforgeItems = runCatching {
            fetchCurseforgeModpacks(query = query, limit = normalizedLimit)
        }.getOrElse {
            emptyList()
        }
        val withCompatibility = (modrinthItems + curseforgeItems)
            .distinctBy { "${it.source}:${it.id}" }
            .map { item ->
                when (item.source) {
                    Source.MODRINTH -> runCatching { resolveSupportedLoaderForModrinth(item.id) }.fold(
                        onSuccess = { loader ->
                            item.copy(
                                installSupported = true,
                                supportMessage = if (loader == ModLoader.UNKNOWN) "Experimental Support" else "Supports ${loader.displayName}",
                                loaders = if (loader == ModLoader.UNKNOWN) emptyList() else listOf(loader)
                            )
                        },
                        onFailure = {
                            item.copy(
                                installSupported = false,
                                supportMessage = "Could not verify loader support right now."
                            )
                        }
                    )
                    Source.CURSEFORGE -> item.copy(
                        installSupported = false,
                        supportMessage = "CurseForge listing shown for discovery; direct install is coming soon."
                    )
                }
            }
        Result.success(withCompatibility)
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
            dependencies.has("neoforge") -> ModLoader.NEOFORGE
            dependencies.has("forge") -> ModLoader.FORGE
            dependencies.has("quilt-loader") -> ModLoader.QUILT
            dependencies.has("quilt") -> ModLoader.QUILT
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
                    add(
                        ModpackCatalogItem(
                            id = item.optString("slug").ifBlank { item.optString("project_id") },
                            title = item.optString("title").ifBlank { "Unknown modpack" },
                            description = item.optString("description"),
                            source = Source.MODRINTH,
                            downloads = item.optLong("downloads", 0L),
                            iconUrl = item.optString("icon_url").ifBlank { null },
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
                    add(
                        ModpackCatalogItem(
                            id = item.optLong("id").toString(),
                            title = item.optString("name").ifBlank { "Unknown modpack" },
                            description = item.optString("summary"),
                            source = Source.CURSEFORGE,
                            downloads = item.optLong("downloadCount", 0L),
                            iconUrl = item.optJSONObject("logo")?.optString("url"),
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
                    add(
                        ManifestFile(
                            path = fileJson.optString("path"),
                            downloads = downloads,
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

        val loader = ModLoader.values()
            .filter { it != ModLoader.UNKNOWN }
            .firstOrNull { manifest.dependencies.containsKey(it.id) }
            ?: throw Exception("Unsupported modpack loader. PocketCraft supports Fabric, Forge, Quilt, and NeoForge.")

        val loaderVersion = manifest.dependencies[loader.id]
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
    ) = withContext(Dispatchers.IO) {
        val serverFiles = manifest.files.filter { manifestFile ->
            !manifestFile.serverSupport.equals("unsupported", ignoreCase = true)
        }

        if (serverFiles.isEmpty()) {
            onProgress(1, 1)
            return@withContext
        }

        serverFiles.forEachIndexed { index, file ->
            val relativePath = sanitizeRelativePath(file.path)
                ?: throw Exception("Refusing to install file outside the server directory: ${file.path}")
            val target = File(serverDir, relativePath)
            target.parentFile?.mkdirs()

            val downloadUrls = prioritizeDownloadUrls(file.downloads)
            if (downloadUrls.isEmpty()) {
                throw Exception("Missing download URL for ${file.path}")
            }

            downloadFileWithFallback(
                urls = downloadUrls,
                dest = target,
                label = file.path
            ) { }
            onProgress(index + 1, serverFiles.size)
        }
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
        modpackId: String,
        loader: LoaderSpec,
        onStatus: (String) -> Unit = {},
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        when (loader.id) {
            "fabric-loader" -> installFabricRuntime(context, serverDir, modpackId, loader)
            "quilt-loader" -> installQuiltRuntime(context, serverDir, modpackId, loader)
            "forge" -> installForgeRuntime(
                context = context,
                serverDir = serverDir,
                modpackId = modpackId,
                loader = loader,
                onStatus = onStatus,
                onProgress = onProgress
            )
            "neoforge" -> installNeoForgeRuntime(
                context = context,
                serverDir = serverDir,
                modpackId = modpackId,
                loader = loader,
                onStatus = onStatus,
                onProgress = onProgress
            )
            else -> throw Exception("Unsupported modpack loader ${loader.id}")
        }
    }

    private suspend fun installFabricRuntime(
        context: Context,
        serverDir: File,
        modpackId: String,
        loader: LoaderSpec
    ) = withContext(Dispatchers.IO) {
        val installerVersion = fetchLatestStableFabricInstallerVersion()
        val runtimeJar = File(serverDir, "modpack-$modpackId.jar")
        val runtimeUrl =
            "$FABRIC_META_BASE_URL/versions/loader/${loader.minecraftVersion}/${loader.loaderVersion}/$installerVersion/server/jar"

        downloadFile(runtimeUrl, runtimeJar) { }
        ServerFileManager.persistLaunchTarget(
            context = context,
            worldName = modpackId,
            mode = ServerFileManager.LaunchMode.JAR,
            relativePath = runtimeJar.name
        )
    }

    private suspend fun installQuiltRuntime(
        context: Context,
        serverDir: File,
        modpackId: String,
        loader: LoaderSpec
    ) = withContext(Dispatchers.IO) {
        val installerVersion = fetchLatestStableQuiltInstallerVersion()
        val runtimeJar = File(serverDir, "modpack-$modpackId.jar")
        // Quilt Meta URL: /v3/versions/loader/{game_version}/{loader_version}/{installer_version}/server/jar
        val runtimeUrl =
            "https://meta.quiltmc.org/v3/versions/loader/${loader.minecraftVersion}/${loader.loaderVersion}/$installerVersion/server/jar"

        downloadFile(runtimeUrl, runtimeJar) { }
        ServerFileManager.persistLaunchTarget(
            context = context,
            worldName = modpackId,
            mode = ServerFileManager.LaunchMode.JAR,
            relativePath = runtimeJar.name
        )
    }

    private suspend fun installForgeRuntime(
        context: Context,
        serverDir: File,
        modpackId: String,
        loader: LoaderSpec,
        onStatus: (String) -> Unit = {},
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        JreExtractor.extractIfNeeded(context)
        // JreExtractor.ensureRuntimePermissions(context)
        onProgress(62)

        val forgeCoordinate = resolveForgeCoordinate(loader)
        val installerJar = File(serverDir, "forge-installer-$forgeCoordinate.jar")
        val installerFileName = "forge-$forgeCoordinate-installer.jar"
        val installerUrls = FORGE_INSTALLER_MIRRORS.map { mirror ->
            "$mirror/$FORGE_COORDINATE_BASE/$forgeCoordinate/$installerFileName"
        }

        onStatus("Downloading Forge installer...")
        runCatching {
            downloadFileWithFallback(
                urls = installerUrls,
                dest = installerJar,
                label = "Forge installer $forgeCoordinate"
            ) { percent ->
                onProgress((62 + (percent * 0.13f)).toInt().coerceIn(62, 75))
            }
            ensureValidJar(installerJar, "Forge installer")
        }.getOrElse { firstError ->
            onStatus("Forge installer download looked corrupted. Retrying...")
            runCatching { installerJar.delete() }
            downloadFileWithFallback(
                urls = installerUrls,
                dest = installerJar,
                label = "Forge installer $forgeCoordinate"
            ) { percent ->
                onProgress((62 + (percent * 0.13f)).toInt().coerceIn(62, 75))
            }
            runCatching { ensureValidJar(installerJar, "Forge installer") }
                .getOrElse { secondError ->
                    throw Exception(
                        "Forge installer is still invalid after retry. ${secondError.message.orEmpty()}",
                        firstError
                    )
                }
        }
        onStatus("Running Forge installer (this can take a few minutes)...")
        purgeStaleForgeProcessorOutputs(serverDir)
        runJarInstaller(
            context = context,
            serverDir = serverDir,
            installerJar = installerJar,
            mainClass = "net.minecraftforge.installer.SimpleInstaller",
            onStatus = onStatus,
            onProgress = onProgress
        )

        val unixArgs = serverDir.walkTopDown()
            .firstOrNull { it.isFile && it.name == "unix_args.txt" }
            ?: throw Exception("Forge installer completed but no unix_args.txt launch target was generated.")

        ServerFileManager.persistLaunchTarget(
            context = context,
            worldName = modpackId,
            mode = ServerFileManager.LaunchMode.ARG_FILE,
            relativePath = unixArgs.relativeTo(serverDir).invariantSeparatorsPath
        )
        onProgress(95)
    }

    private suspend fun installNeoForgeRuntime(
        context: Context,
        serverDir: File,
        modpackId: String,
        loader: LoaderSpec,
        onStatus: (String) -> Unit = {},
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        JreExtractor.extractIfNeeded(context)
        onProgress(62)

        val version = loader.loaderVersion.trim()
        val installerJar = File(serverDir, "neoforge-installer-$version.jar")
        val installerUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/$version/neoforge-$version-installer.jar"

        onStatus("Downloading NeoForge installer...")
        runCatching {
            downloadFile(installerUrl, installerJar) { percent ->
                onProgress((62 + (percent * 0.13f)).toInt().coerceIn(62, 75))
            }
            ensureValidJar(installerJar, "NeoForge installer")
        }.getOrElse { firstError ->
            onStatus("NeoForge installer download failed. Retrying...")
            runCatching { installerJar.delete() }
            downloadFile(installerUrl, installerJar) { percent ->
                onProgress((62 + (percent * 0.13f)).toInt().coerceIn(62, 75))
            }
            runCatching { ensureValidJar(installerJar, "NeoForge installer") }
                .getOrElse { secondError ->
                    throw Exception(
                        "NeoForge installer is still invalid after retry. ${secondError.message.orEmpty()}",
                        firstError
                    )
                }
        }

        onStatus("Running NeoForge installer (this can take a few minutes)...")
        runJarInstaller(
            context = context,
            serverDir = serverDir,
            installerJar = installerJar,
            mainClass = "net.neoforged.installer.SimpleInstaller",
            onStatus = onStatus,
            onProgress = onProgress
        )

        val unixArgs = serverDir.walkTopDown()
            .firstOrNull { it.isFile && it.name == "unix_args.txt" }
            ?: throw Exception("NeoForge installer completed but no unix_args.txt launch target was generated.")

        ServerFileManager.persistLaunchTarget(
            context = context,
            worldName = modpackId,
            mode = ServerFileManager.LaunchMode.ARG_FILE,
            relativePath = unixArgs.relativeTo(serverDir).invariantSeparatorsPath
        )
        onProgress(95)
    }

    private fun resolveForgeCoordinate(loader: LoaderSpec): String {
        val mc = loader.minecraftVersion.trim()
        val rawLoader = loader.loaderVersion.trim()
        if (rawLoader.isBlank()) {
            throw Exception("Missing Forge loader version for Minecraft $mc")
        }

        // Modrinth manifests may provide "47.2.0" OR "1.20.1-47.2.0".
        return if (rawLoader.startsWith("$mc-")) rawLoader else "$mc-$rawLoader"
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
        onStatus: (String) -> Unit = {},
        onProgress: (Int) -> Unit = {}
    ) {
        val jreDir = JreExtractor.getJreDir(context).absolutePath
        val javaBin = JreExtractor.getJavaBinary(context)
        if (!javaBin.exists()) {
            throw Exception("Java runtime is not available for Forge installation")
        }
        ensureExecutable(javaBin, "java")

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
            val command = buildList {
                when {
                    useWrapper && wrapperBin.exists() -> {
                        add(wrapperBin.absolutePath)
                        addAll(baseJavaArgs)
                    }
                    useShellLauncher -> {
                        add("/system/bin/sh")
                        add("-c")
                        add(
                            buildString {
                                append("export JAVA_HOME=")
                                append(shellQuote(jreDir))
                                append("; export LD_LIBRARY_PATH=")
                                append(shellQuote(ldLibraryPath))
                                append("; exec")
                                baseJavaArgs.forEach { arg ->
                                    append(" ")
                                    append(shellQuote(arg))
                                }
                            }
                        )
                    }
                    else -> addAll(baseJavaArgs)
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
            val shellAttempt = executeInstaller(useWrapper = false, useShellLauncher = true)
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

    private fun ensureValidJar(file: File, label: String) {
        if (!file.exists() || file.length() < 32_768L) {
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
            "libpthread.so.0" to "$libs/libc.so"
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

    private suspend fun downloadFile(url: String, dest: File, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PocketCraft/1.0")
            .build()
        executeWithRetry(request, "download file").use { response ->
            if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")
            val body = response.body ?: throw Exception("Empty body")
            dest.parentFile?.mkdirs()
            val total = body.contentLength().coerceAtLeast(1L)
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var downloaded = 0L
                    var read = input.read(buffer)
                    while (read != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 100))
                        read = input.read(buffer)
                    }
                }
            }
        }
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
            .sortedBy { url ->
                when {
                    url.contains("cdn.modrinth.com", ignoreCase = true) -> 0
                    url.contains("modrinth.com", ignoreCase = true) -> 1
                    url.contains("github.com", ignoreCase = true) -> 2
                    else -> 3
                }
            }
    }

    private suspend fun downloadFileWithFallback(
        urls: List<String>,
        dest: File,
        label: String,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (url in urls) {
            repeat(2) { attempt ->
                runCatching {
                    downloadFile(url, dest, onProgress)
                }.onSuccess {
                    return@withContext
                }.onFailure { throwable ->
                    dest.delete()
                    val error = Exception("Attempt ${attempt + 1} failed for $url", throwable)
                    lastError = error
                    Log.w(TAG, "Download failed for $label via $url (attempt ${attempt + 1})", throwable)
                }
            }
        }

        throw Exception(
            "Failed to download $label from reliable sources. ${lastError?.message.orEmpty()}",
            lastError
        )
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

    private fun ensureExecutable(file: File, label: String) {
        if (file.canExecute()) return
        val fixed = runCatching { file.setExecutable(true, false) }.getOrDefault(false)
        if (!fixed || !file.canExecute()) {
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
