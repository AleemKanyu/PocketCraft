# MODPACK_VERSION_FIX_AGENT.md
## PocketCraft — Modpack Download Fix + Instant Version Loading + Offline Mode

You are an expert Android/Kotlin engineer working on PocketCraft (`com.pocketcraft.server`).
Apply all three fixes below in order. Each section is self-contained.

---

## FIX 1 — Modpack Download ("Unsupported modpack loader" Error)

### Root Cause
The UI shows "Unsupported modpack loader. Please choos…" for Fabric-based modpacks
(e.g. Fabulously Optimized, Cobblemon [Fabric], COBBLEVERSE). This means the modpack
installer is checking the modpack's loader type and rejecting anything that isn't Paper/Spigot.
Fabric modpacks require a **Fabric server JAR** as the base, not Paper. The app must:
1. Detect the modpack's required loader (Fabric / Forge / Quilt / NeoForge).
2. Auto-select the correct server JAR for that loader.
3. Download and install mods into the correct folder structure.

### Supported Loaders After This Fix
| Loader | Server JAR source | Mod folder |
|--------|-------------------|------------|
| Fabric | FabricMC installer API | `mods/` |
| Forge  | Forge Maven         | `mods/` |
| Quilt  | QuiltMC installer API | `mods/` |
| NeoForge | NeoForge Maven     | `mods/` |

### Changes

#### `ModpackManager.kt` (create if it doesn't exist, otherwise edit)

**Step 1 — Data model**
```kotlin
enum class ModLoader { FABRIC, FORGE, QUILT, NEOFORGE, UNKNOWN }

data class ModpackInfo(
    val id: String,
    val name: String,
    val iconUrl: String,
    val downloads: Long,
    val source: String,          // "modrinth" or "curseforge"
    val loader: ModLoader,
    val minecraftVersion: String,
    val loaderVersion: String,   // e.g. "0.15.11" for Fabric
    val modIds: List<String>     // individual mod project IDs to download
)
```

**Step 2 — Parse loader from Modrinth API response**
When fetching modpack details from Modrinth, read the `loaders` array in the version response:
```kotlin
fun parseLoader(loaders: List<String>): ModLoader = when {
    loaders.any { it.equals("fabric", ignoreCase = true) }    -> ModLoader.FABRIC
    loaders.any { it.equals("forge", ignoreCase = true) }     -> ModLoader.FORGE
    loaders.any { it.equals("quilt", ignoreCase = true) }     -> ModLoader.QUILT
    loaders.any { it.equals("neoforge", ignoreCase = true) }  -> ModLoader.NEOFORGE
    else -> ModLoader.UNKNOWN
}
```

**Step 3 — Download the correct server JAR for the modpack's loader**

```kotlin
suspend fun downloadServerJar(loader: ModLoader, mcVersion: String, loaderVersion: String, destDir: File): File {
    return when (loader) {

        ModLoader.FABRIC -> {
            // Fabric server launcher — runs as a wrapper that bootstraps fabric-server
            val url = "https://meta.fabricmc.net/v2/versions/loader/$mcVersion/$loaderVersion/1.0.1/server/jar"
            downloadFile(url, File(destDir, "server.jar"))
        }

        ModLoader.FORGE -> {
            // Forge installer JAR — must be run with --installServer flag
            val url = "https://maven.minecraftforge.net/net/minecraftforge/forge/$mcVersion-$loaderVersion/forge-$mcVersion-$loaderVersion-installer.jar"
            val installer = downloadFile(url, File(destDir, "forge-installer.jar"))
            runForgeInstaller(installer, destDir)   // runs: java -jar forge-installer.jar --installServer
            File(destDir, "server.jar")
        }

        ModLoader.QUILT -> {
            val url = "https://quiltmc.org/api/v1/download-latest-installer/java-universal"
            val installer = downloadFile(url, File(destDir, "quilt-installer.jar"))
            runQuiltInstaller(installer, destDir, mcVersion)
            File(destDir, "quilt-server-launch.jar")
        }

        ModLoader.NEOFORGE -> {
            val url = "https://maven.neoforged.net/releases/net/neoforged/neoforge/$loaderVersion/neoforge-$loaderVersion-installer.jar"
            val installer = downloadFile(url, File(destDir, "neoforge-installer.jar"))
            runNeoForgeInstaller(installer, destDir)
            File(destDir, "server.jar")
        }

        ModLoader.UNKNOWN -> throw IllegalStateException("Cannot install modpack: unsupported loader")
    }
}
```

**Step 4 — Download all mods in the modpack**

For Modrinth modpacks (`.mrpack` format), download the mrpack file and extract it:
```kotlin
suspend fun installMrpack(mrpackUrl: String, serverDir: File) {
    // 1. Download .mrpack (it's a zip)
    val mrpack = downloadFile(mrpackUrl, File(serverDir, "modpack.mrpack"))

    // 2. Unzip and read modrinth.index.json
    ZipFile(mrpack).use { zip ->
        val indexEntry = zip.getEntry("modrinth.index.json")
        val index = JSONObject(zip.getInputStream(indexEntry).bufferedReader().readText())
        val files = index.getJSONArray("files")

        // 3. Download each mod file
        val modsDir = File(serverDir, "mods").also { it.mkdirs() }
        for (i in 0 until files.length()) {
            val file = files.getJSONObject(i)
            val path = file.getString("path")           // e.g. "mods/sodium-1.20.jar"
            val downloads = file.getJSONArray("downloads")
            val downloadUrl = downloads.getString(0)
            // Only download server-side files (skip client-only mods)
            val env = file.optJSONObject("env")
            val serverEnv = env?.optString("server") ?: "required"
            if (serverEnv == "unsupported") continue    // skip client-only mods
            downloadFile(downloadUrl, File(serverDir, path))
        }

        // 4. Extract overrides folder (config files, etc.)
        zip.entries().asSequence()
            .filter { it.name.startsWith("overrides/") && !it.isDirectory }
            .forEach { entry ->
                val dest = File(serverDir, entry.name.removePrefix("overrides/"))
                dest.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
    }
    mrpack.delete()
}
```

For CurseForge modpacks, use the CurseForge API to resolve each mod's download URL using
the project ID and file ID from the manifest. Requires a CurseForge API key stored in
`AppPreferences` or `BuildConfig`.

**Step 5 — Remove the "Unsupported loader" error block**
Find the check that throws/displays "Unsupported modpack loader" and replace it with a call
to `downloadServerJar(modpack.loader, ...)`. Only show the error if `loader == ModLoader.UNKNOWN`.

**Step 6 — Auto-switch server type chip in UI**
When the user selects a modpack, automatically switch the "Server Type" selection chip to
match the modpack's loader (Fabric / Forge etc.) and disable manual chip switching while a
modpack is selected. Show a grey info label: "Server type set by modpack".

---

## FIX 2 — Instant Version Loading (Eliminate Slow API Fetch on Every Open)

### Root Cause
Every time the Configure Server screen opens, the app fetches version lists from PaperMC /
PurpurMC / FabricMC APIs over the network. With no cache, on a slow connection this takes
5–15 seconds. The fix is a **two-layer cache**: memory (instant) + disk (fast, survives
app restart) + background refresh (keeps data fresh silently).

### Changes

#### `VersionCacheManager.kt` (create this file)

```kotlin
object VersionCacheManager {

    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L   // 6 hours
    private const val CACHE_FILE = "version_cache.json"

    data class VersionCache(
        val paper: List<String>,
        val purpur: List<String>,
        val fabric: List<String>,
        val forge: List<String>,
        val modpacks: List<CachedModpack>,
        val fetchedAt: Long
    )

    data class CachedModpack(
        val id: String,
        val name: String,
        val iconUrl: String,
        val downloads: Long,
        val source: String,
        val loader: String,
        val minecraftVersion: String
    )

    // In-memory cache — survives configuration changes, cleared on process death
    private var memoryCache: VersionCache? = null

    fun getCached(context: Context): VersionCache? {
        // 1. Try memory first (instant)
        memoryCache?.let { return it }

        // 2. Try disk cache
        val file = File(context.filesDir, CACHE_FILE)
        if (!file.exists()) return null
        return try {
            val cache = Json.decodeFromString<VersionCache>(file.readText())
            memoryCache = cache
            cache
        } catch (e: Exception) { null }
    }

    fun isCacheStale(cache: VersionCache): Boolean {
        return System.currentTimeMillis() - cache.fetchedAt > CACHE_TTL_MS
    }

    fun saveToCache(context: Context, cache: VersionCache) {
        memoryCache = cache
        File(context.filesDir, CACHE_FILE).writeText(Json.encodeToString(cache))
    }
}
```

#### Version fetching logic (in your ViewModel or Repository)

Replace the current blocking fetch with a **cache-first, refresh-in-background** pattern:

```kotlin
fun loadVersions(context: Context) {
    viewModelScope.launch {
        // Step 1: Show cached data instantly (zero wait)
        val cached = VersionCacheManager.getCached(context)
        if (cached != null) {
            _versions.emit(cached)          // UI shows immediately
        } else {
            _isLoading.emit(true)           // only show spinner on very first launch
        }

        // Step 2: If cache is stale or missing, refresh in background
        if (cached == null || VersionCacheManager.isCacheStale(cached)) {
            try {
                val fresh = fetchAllVersionsFromApis()   // network call
                VersionCacheManager.saveToCache(context, fresh)
                _versions.emit(fresh)                    // update UI silently
            } catch (e: Exception) {
                // Network failed — cached data is still showing, no crash
                if (cached == null) _error.emit("No internet. Connect to load versions.")
            } finally {
                _isLoading.emit(false)
            }
        }
    }
}
```

#### API fetch parallelism
Run all API calls in parallel instead of sequentially:

```kotlin
suspend fun fetchAllVersionsFromApis(): VersionCache {
    return coroutineScope {
        val paper    = async { fetchPaperVersions() }
        val purpur   = async { fetchPurpurVersions() }
        val fabric   = async { fetchFabricVersions() }
        val forge    = async { fetchForgeVersions() }
        val modpacks = async { fetchModpacks() }

        VersionCache(
            paper    = paper.await(),
            purpur   = purpur.await(),
            fabric   = fabric.await(),
            forge    = forge.await(),
            modpacks = modpacks.await(),
            fetchedAt = System.currentTimeMillis()
        )
    }
}
```

This cuts the fetch time from sequential (sum of all calls) to parallel (slowest single call).

#### Modpack search — filter locally
Once modpacks are cached, the search bar should filter the **local cached list** instantly
without making any API calls. Only make a new API search call if the user types a query not
present in the cache at all (debounced 600ms after typing stops).

---

## FIX 3 — Offline Mode (Show Downloaded Versions When No Internet)

### Root Cause
When there's no internet, the version selector shows a loading spinner forever or crashes.
The fix uses the disk cache from Fix 2, and additionally tracks which server JARs the user
has already downloaded locally.

### Changes

#### `VersionCacheManager.kt` — add downloaded versions tracking

```kotlin
// Add to VersionCacheManager:
fun getDownloadedVersions(serverDir: File): Map<String, List<String>> {
    // Scan the server JARs directory for already-downloaded versions
    // Structure assumed: serverDir/paper/1.20.4/server.jar, etc.
    val result = mutableMapOf<String, MutableList<String>>()
    serverDir.listFiles()?.forEach { loaderDir ->
        if (loaderDir.isDirectory) {
            val versions = loaderDir.listFiles()
                ?.filter { it.isDirectory && File(it, "server.jar").exists() }
                ?.map { it.name }
                ?: emptyList()
            if (versions.isNotEmpty()) result[loaderDir.name] = versions.toMutableList()
        }
    }
    return result
}
```

#### Version selector UI composable

```kotlin
// In the version selector screen:
val isOnline by networkMonitor.isOnline.collectAsState()
val cached = VersionCacheManager.getCached(context)
val downloadedVersions = VersionCacheManager.getDownloadedVersions(serverDir)

if (!isOnline) {
    // Show offline banner
    OfflineBanner()   // amber chip: "Offline — showing cached & downloaded versions"

    if (cached == null && downloadedVersions.isEmpty()) {
        // Truly nothing to show
        EmptyState(
            icon = Icons.Outlined.WifiOff,
            title = "No versions available offline",
            subtitle = "Connect to the internet to load server versions"
        )
        return
    }
}

// Show cached versions normally — they render fine offline
// Highlight downloaded versions with a "Downloaded" green badge
```

#### Version list item — show downloaded badge
For each version item in the list, check if it's already downloaded locally:

```kotlin
@Composable
fun VersionItem(version: String, loader: String, isDownloaded: Boolean) {
    Row {
        Text(version)
        if (isDownloaded) {
            Spacer(Modifier.width(8.dp))
            Badge(
                containerColor = Color(0xFF3DDC84),   // PocketCraft green
                contentColor = Color.Black
            ) { Text("Downloaded", fontSize = 10.sp) }
        }
    }
}
```

#### Offline modpack behaviour
- If offline and a modpack is selected that hasn't been fully downloaded → show red error:
  "This modpack requires internet to download. Connect and try again."
- If a modpack's server JAR + mods are already in `serverDir` → allow starting it offline.

---

## FIX 4 — Instant Plugins / Mods / Resource Packs Loading

### Root Cause
The Plugins screen (and any Mods / Resource Packs tab) fetches listings from external APIs
(Modrinth, Hangar, SpigotMC, CurseForge) on every open with no caching. This causes the
same slow load problem as Fix 2 — the user sees a spinner for 5–15 seconds before any
content appears. The fix applies the identical cache-first pattern from Fix 2, extended to
cover plugins, mods, and resource packs as separate cache buckets.

### Changes

#### Extend `VersionCacheManager.kt` with a content cache

Add a second cache file specifically for browsable content (plugins, mods, resource packs)
with a shorter TTL since these update more frequently than version lists:

```kotlin
// Add inside VersionCacheManager:

private const val CONTENT_CACHE_FILE = "content_cache.json"
private const val CONTENT_CACHE_TTL_MS = 2 * 60 * 60 * 1000L   // 2 hours

@Serializable
data class ContentCache(
    val plugins: List<CachedContentItem>,
    val mods: List<CachedContentItem>,
    val resourcePacks: List<CachedContentItem>,
    val fetchedAt: Long
)

@Serializable
data class CachedContentItem(
    val id: String,
    val name: String,
    val description: String,
    val iconUrl: String,
    val downloads: Long,
    val source: String,          // "modrinth", "hangar", "spigotmc", "curseforge"
    val category: String,        // "plugin", "mod", "resourcepack"
    val supportedLoaders: List<String>,   // ["paper", "fabric", "forge", etc.]
    val latestVersion: String,
    val downloadUrl: String
)

private var memoryContentCache: ContentCache? = null

fun getCachedContent(context: Context): ContentCache? {
    memoryContentCache?.let { return it }
    val file = File(context.filesDir, CONTENT_CACHE_FILE)
    if (!file.exists()) return null
    return try {
        val cache = Json.decodeFromString<ContentCache>(file.readText())
        memoryContentCache = cache
        cache
    } catch (e: Exception) { null }
}

fun isContentCacheStale(cache: ContentCache): Boolean {
    return System.currentTimeMillis() - cache.fetchedAt > CONTENT_CACHE_TTL_MS
}

fun saveContentToCache(context: Context, cache: ContentCache) {
    memoryContentCache = cache
    File(context.filesDir, CONTENT_CACHE_FILE).writeText(Json.encodeToString(cache))
}
```

#### `PluginManager.kt` — cache-first loading

Replace the current blocking fetch in the plugin browser with the same pattern as Fix 2:

```kotlin
fun loadPlugins(context: Context, serverLoader: ModLoader) {
    viewModelScope.launch {

        // Step 1: Show cache instantly
        val cached = VersionCacheManager.getCachedContent(context)
        if (cached != null) {
            val filtered = cached.plugins.filter { item ->
                item.supportedLoaders.any { it.equals(serverLoader.name, ignoreCase = true) }
            }
            _plugins.emit(filtered)       // UI populates immediately
        } else {
            _isLoading.emit(true)
        }

        // Step 2: Refresh in background if stale
        if (cached == null || VersionCacheManager.isContentCacheStale(cached)) {
            try {
                val fresh = fetchAllContent(serverLoader)
                VersionCacheManager.saveContentToCache(context, fresh)
                _plugins.emit(fresh.plugins)
                _mods.emit(fresh.mods)
                _resourcePacks.emit(fresh.resourcePacks)
            } catch (e: Exception) {
                if (cached == null) _error.emit("No internet. Showing cached plugins.")
            } finally {
                _isLoading.emit(false)
            }
        }
    }
}
```

#### Parallel API fetch for all content sources

Fetch from all sources simultaneously:

```kotlin
suspend fun fetchAllContent(serverLoader: ModLoader): ContentCache {
    return coroutineScope {
        // Plugins (Paper/Purpur servers)
        val hangar    = async { fetchFromHangar() }         // hangar.papermc.io API
        val spigot    = async { fetchFromSpigotMC() }       // spiget.org API
        val modrinthP = async { fetchModrinthPlugins() }    // Modrinth, category=plugin

        // Mods (Fabric/Forge/Quilt servers)
        val modrinthM = async { fetchModrinthMods(serverLoader) }   // Modrinth, category=mod
        val curseforge = async { fetchCurseForgeContent() }

        // Resource packs (all server types)
        val modrinthR = async { fetchModrinthResourcePacks() }

        val allPlugins = hangar.await() + spigot.await() + modrinthP.await()
        val allMods = modrinthM.await() + curseforge.await()
        val allResourcePacks = modrinthR.await()

        ContentCache(
            plugins = allPlugins.sortedByDescending { it.downloads },
            mods = allMods.sortedByDescending { it.downloads },
            resourcePacks = allResourcePacks.sortedByDescending { it.downloads },
            fetchedAt = System.currentTimeMillis()
        )
    }
}
```

#### Search — filter locally first, API fallback

```kotlin
fun searchContent(query: String, category: String, context: Context) {
    viewModelScope.launch {
        // Always filter local cache first — instant results
        val cached = VersionCacheManager.getCachedContent(context)
        if (cached != null) {
            val localResults = when (category) {
                "plugin"       -> cached.plugins
                "mod"          -> cached.mods
                "resourcepack" -> cached.resourcePacks
                else           -> emptyList()
            }.filter { it.name.contains(query, ignoreCase = true) ||
                       it.description.contains(query, ignoreCase = true) }

            _searchResults.emit(localResults)   // show instantly from cache
        }

        // If the local results look thin (< 5 items), hit the API after 600ms debounce
        delay(600)
        if (query.isNotBlank()) {
            try {
                val apiResults = fetchSearchResults(query, category)
                _searchResults.emit(apiResults)
            } catch (e: Exception) { /* keep local results */ }
        }
    }
}
```

#### Plugins / Mods / Resource Packs screen composable

- On screen entry, call `loadPlugins()` / `loadMods()` / `loadResourcePacks()` — the
  ViewModel returns cached data immediately so the list renders on the first frame.
- Show a subtle "Updating…" shimmer on the top of the list (not a full-screen spinner)
  while the background refresh is running. This makes it clear data is fresh without
  blocking the UI.
- Offline: if `isOnline == false`, show the amber offline banner at the top
  ("Offline — showing cached results") and disable the install button for items not
  already downloaded locally. Already-installed plugins/mods show a green "Installed" badge.

#### Already-installed detection

```kotlin
fun getInstalledPlugins(pluginsDir: File): Set<String> {
    // Match cached item filenames against files in the server's plugins/ directory
    return pluginsDir.listFiles()
        ?.filter { it.extension == "jar" }
        ?.map { it.nameWithoutExtension.lowercase() }
        ?.toSet()
        ?: emptySet()
}
```

Show a green "Installed" badge on any list item whose JAR is already present in `plugins/`
or `mods/`. This also works offline — it reads local files, no network needed.

---

## Summary of Files to Touch

| File | Change |
|---|---|
| `ModpackManager.kt` | Add loader detection, Fabric/Forge/Quilt/NeoForge JAR download, mrpack extraction, CurseForge mod resolution, remove "Unsupported loader" block |
| `VersionCacheManager.kt` | **Create new** — disk + memory cache for versions AND content (plugins/mods/packs), stale check, downloaded versions scan |
| ViewModel / Repository for version screen | Replace blocking fetch with cache-first + background refresh + parallel API calls |
| `PluginManager.kt` | Add cache-first loading, parallel multi-source fetch, local search filter, installed badge detection |
| Version selector composable | Show cached data immediately, add "Downloaded" badge, offline banner, empty offline state |
| Configure Server composable | Auto-switch server type chip based on modpack loader, disable manual chip when modpack selected |
| Plugins / Mods / Resource Packs screen composable | Cache-first render, "Updating…" shimmer, offline banner, "Installed" badge, disable install when offline + not downloaded |

---

## Do Not Change
- Existing Paper / Purpur / Fabric server JAR download logic for non-modpack server types
- `NetworkMonitor.kt` — reuse existing `isOnline` flow for offline detection
- Relay, player, or backup logic
