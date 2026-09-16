package com.pockethost.app.service

import android.content.Context
import android.util.Log
import com.pockethost.app.data.model.ServerType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class MissingModDependency(
    val modId: String,
    val modName: String,
    val modFileName: String,
    val dependencyId: String,
    val versionRequirement: String? = null
)

object ModDependencyValidator {

    private const val TAG = "ModDependencyValidator"

    // System/loader virtual mod IDs that are always satisfied by the game environment
    private val BUILTIN_ENVIRONMENT_MOD_IDS = setOf(
        "minecraft",
        "java",
        "fabricloader",
        "fabric-loader",
        "quilt_loader",
        "quiltloader",
        "forge",
        "neoforge"
    )

    data class ModMetadata(
        val id: String,
        val name: String,
        val version: String,
        val file: File,
        val provides: Set<String>,
        val depends: Map<String, String?> // dependency modId -> version requirement
    )

    /**
     * Checks all enabled JAR mods in the given world directory and returns any missing dependencies.
     */
    fun detectMissingDependencies(
        context: Context,
        worldName: String,
        serverType: ServerType? = null
    ): List<MissingModDependency> {
        val modsDir = PluginManager.getModsDir(context, worldName)
        return detectMissingDependencies(modsDir, serverType)
    }

    /**
     * Checks all enabled JAR mods in the given directory and returns any missing dependencies.
     */
    fun detectMissingDependencies(
        modsDir: File,
        serverType: ServerType? = null
    ): List<MissingModDependency> {
        if (!modsDir.exists() || !modsDir.isDirectory) return emptyList()

        val jarFiles = modsDir.listFiles { file ->
            file.isFile && file.extension.equals("jar", ignoreCase = true) && !file.name.endsWith(".disabled")
        } ?: return emptyList()

        if (jarFiles.isEmpty()) return emptyList()

        val installedMods = mutableListOf<ModMetadata>()
        val availableModIds = mutableSetOf<String>()

        // 1. Parse all mod JARs and collect provided IDs (including nested JARs)
        for (jar in jarFiles) {
            val meta = parseModJar(jar) ?: continue
            installedMods.add(meta)
            availableModIds.add(meta.id.lowercase(Locale.US))
            meta.provides.forEach { availableModIds.add(it.lowercase(Locale.US)) }
        }

        // Special satisfaction rule: if "fabric-api" is present, it satisfies "fabric" and common fabric modules
        if (availableModIds.contains("fabric-api")) {
            availableModIds.add("fabric")
        }
        if (availableModIds.contains("fabric")) {
            availableModIds.add("fabric-api")
        }

        // 2. Identify missing dependencies
        val missingList = mutableListOf<MissingModDependency>()

        for (mod in installedMods) {
            for ((depId, versionReq) in mod.depends) {
                val normalizedDepId = depId.lowercase(Locale.US)

                // Skip built-in environment IDs
                if (normalizedDepId in BUILTIN_ENVIRONMENT_MOD_IDS) continue

                // Check if satisfied by any installed mod or provide alias
                if (!availableModIds.contains(normalizedDepId)) {
                    missingList.add(
                        MissingModDependency(
                            modId = mod.id,
                            modName = mod.name,
                            modFileName = mod.file.name,
                            dependencyId = depId,
                            versionRequirement = versionReq
                        )
                    )
                }
            }
        }

        return missingList
    }

    /**
     * Disables a mod JAR by renaming it to .jar.disabled.
     */
    fun disableMod(file: File): Boolean {
        if (!file.exists() || !file.name.endsWith(".jar", ignoreCase = true)) return false
        val target = File(file.parentFile, "${file.name}.disabled")
        return file.renameTo(target)
    }

    /**
     * Enables a disabled mod JAR by renaming .jar.disabled to .jar.
     */
    fun enableMod(file: File): Boolean {
        if (!file.exists() || !file.name.endsWith(".disabled", ignoreCase = true)) return false
        val newName = file.name.removeSuffix(".disabled")
        val target = File(file.parentFile, newName)
        return file.renameTo(target)
    }

    /**
     * Re-enables mods that were quarantined earlier and whose dependencies are now present.
     *
     * Quarantine is one-way on its own: the mod is renamed to .jar.disabled and nothing ever
     * renames it back, so installing the dependency afterwards left the mod switched off forever
     * and the player with no indication that a file rename was all that stood in the way.
     *
     * Restoration repeats until nothing more can be restored, because one restored mod can satisfy
     * another's dependency.
     */
    fun restoreSatisfiedMods(
        serverDir: File,
        onOutput: (String) -> Unit = {}
    ): List<File> {
        val modsDir = File(serverDir, "mods")
        if (!modsDir.isDirectory) return emptyList()

        val restored = mutableListOf<File>()
        // Bounded so a metadata quirk can never spin here.
        repeat(5) {
            val disabled = modsDir.listFiles { file ->
                file.isFile && file.name.endsWith(".jar.disabled", ignoreCase = true)
            }?.toList().orEmpty()
            if (disabled.isEmpty()) return@repeat

            val available = mutableSetOf<String>()
            modsDir.listFiles { file ->
                file.isFile && file.extension.equals("jar", ignoreCase = true) && !file.name.endsWith(".disabled")
            }?.forEach { jar ->
                parseModJar(jar)?.let { meta ->
                    available.add(meta.id.lowercase(Locale.US))
                    meta.provides.forEach { available.add(it.lowercase(Locale.US)) }
                }
            }
            if (available.contains("fabric-api")) available.add("fabric")
            if (available.contains("fabric")) available.add("fabric-api")

            var restoredThisPass = false
            for (candidate in disabled) {
                val meta = parseModJar(candidate) ?: continue
                val unmet = meta.depends.keys.filter { dep ->
                    val normalized = dep.lowercase(Locale.US)
                    normalized !in BUILTIN_ENVIRONMENT_MOD_IDS && normalized !in available
                }
                if (unmet.isNotEmpty()) continue
                if (enableMod(candidate)) {
                    restoredThisPass = true
                    restored.add(candidate)
                    onOutput("[PocketHost] Re-enabled '${meta.name}' — its dependencies are installed now.")
                    Log.i(TAG, "Restored ${candidate.name}; dependencies satisfied.")
                }
            }
            if (!restoredThisPass) return@repeat
        }
        return restored
    }

    /**
     * Fail-safe quarantine for pre-launch: scans mods and automatically disables any mod
     * that has unsatisfied required dependencies to prevent startup crash loops.
     */
    fun quarantineMissingDependencies(
        serverDir: File,
        onOutput: (String) -> Unit = {}
    ): List<File> {
        val modsDir = File(serverDir, "mods")
        if (!modsDir.exists() || !modsDir.isDirectory) return emptyList()

        val missing = detectMissingDependencies(modsDir)
        if (missing.isEmpty()) return emptyList()

        val quarantinedFiles = mutableListOf<File>()
        val filesToQuarantine = missing.map { it.modFileName }.distinct()

        for (fileName in filesToQuarantine) {
            val jarFile = File(modsDir, fileName)
            if (jarFile.exists()) {
                val causes = missing.filter { it.modFileName == fileName }
                val depSummary = causes.joinToString(", ") { "${it.dependencyId} ${it.versionRequirement.orEmpty()}".trim() }
                onOutput("[PocketHost] Warning: Disabling '${jarFile.name}' because required dependency is missing: $depSummary")
                Log.w(TAG, "Quarantining ${jarFile.name}: missing required dependencies [$depSummary]")
                if (disableMod(jarFile)) {
                    quarantinedFiles.add(jarFile)
                }
            }
        }

        return quarantinedFiles
    }

    /**
     * Parses a single mod JAR file, checking Fabric, Quilt, and Forge/NeoForge metadata.
     */
    fun parseModJar(jar: File): ModMetadata? {
        if (!jar.isFile) return null
        return runCatching {
            ZipFile(jar).use { zip ->
                // 1. Fabric mod metadata
                val fabricEntry = zip.getEntry("fabric.mod.json")
                if (fabricEntry != null) {
                    val content = zip.getInputStream(fabricEntry).bufferedReader().use { it.readText() }
                    return@use parseFabricModJson(jar, zip, content)
                }

                // 2. Quilt mod metadata
                val quiltEntry = zip.getEntry("quilt.mod.json")
                if (quiltEntry != null) {
                    val content = zip.getInputStream(quiltEntry).bufferedReader().use { it.readText() }
                    return@use parseQuiltModJson(jar, zip, content)
                }

                // 3. NeoForge metadata
                val neoforgeEntry = zip.getEntry("META-INF/neoforge.mods.toml")
                if (neoforgeEntry != null) {
                    val content = zip.getInputStream(neoforgeEntry).bufferedReader().use { it.readText() }
                    return@use parseModsToml(jar, content, isNeoForge = true)
                }

                // 4. Forge metadata
                val forgeEntry = zip.getEntry("META-INF/mods.toml")
                if (forgeEntry != null) {
                    val content = zip.getInputStream(forgeEntry).bufferedReader().use { it.readText() }
                    return@use parseModsToml(jar, content, isNeoForge = false)
                }

                null
            }
        }.getOrNull()
    }

    private fun parseFabricModJson(jar: File, zip: ZipFile, content: String): ModMetadata {
        val json = JSONObject(content)
        val id = json.optString("id").trim()
        val name = json.optString("name").takeIf { it.isNotBlank() } ?: id
        val version = json.optString("version").trim()

        val provides = mutableSetOf<String>()
        val providesArray = json.optJSONArray("provides")
        if (providesArray != null) {
            for (i in 0 until providesArray.length()) {
                val p = providesArray.optString(i).trim()
                if (p.isNotBlank()) provides.add(p)
            }
        }

        // Also discover Jar-in-Jar embedded libraries
        discoverNestedFabricJars(zip, json, provides)

        val depends = mutableMapOf<String, String?>()
        val dependsObj = json.optJSONObject("depends")
        if (dependsObj != null) {
            val keys = dependsObj.keys()
            while (keys.hasNext()) {
                val depId = keys.next().trim()
                val depVal = dependsObj.opt(depId)
                val versionConstraint = when (depVal) {
                    is String -> depVal
                    is JSONArray -> {
                        val list = mutableListOf<String>()
                        for (i in 0 until depVal.length()) {
                            list.add(depVal.optString(i))
                        }
                        list.joinToString(", ")
                    }
                    else -> depVal?.toString()
                }
                if (depId.isNotBlank()) {
                    depends[depId] = versionConstraint
                }
            }
        }

        return ModMetadata(
            id = id,
            name = name,
            version = version,
            file = jar,
            provides = provides,
            depends = depends
        )
    }

    /**
     * Inspects embedded JARs in META-INF/jars to find additional mod IDs and provides
     * packaged inside this mod (JiJ - Jar-in-Jar).
     */
    private fun discoverNestedFabricJars(zip: ZipFile, rootJson: JSONObject, provides: MutableSet<String>) {
        val nestedFiles = mutableSetOf<String>()

        val jarsArray = rootJson.optJSONArray("jars")
        if (jarsArray != null) {
            for (i in 0 until jarsArray.length()) {
                val fileObj = jarsArray.optJSONObject(i)
                val f = fileObj?.optString("file")?.trim()
                if (!f.isNullOrBlank()) nestedFiles.add(f)
            }
        }

        // Also check any META-INF/jars/ entries in the ZIP
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (!entry.isDirectory && entry.name.startsWith("META-INF/jars/") && entry.name.endsWith(".jar")) {
                nestedFiles.add(entry.name)
            }
        }

        for (nestedPath in nestedFiles) {
            val entry = zip.getEntry(nestedPath) ?: continue
            runCatching {
                ZipInputStream(zip.getInputStream(entry)).use { zis ->
                    var nestedEntry = zis.nextEntry
                    while (nestedEntry != null) {
                        if (nestedEntry.name == "fabric.mod.json") {
                            val nestedContent = zis.bufferedReader().readText()
                            val nestedJson = JSONObject(nestedContent)
                            val nestedId = nestedJson.optString("id").trim()
                            if (nestedId.isNotBlank()) {
                                provides.add(nestedId)
                            }
                            val nestedProvides = nestedJson.optJSONArray("provides")
                            if (nestedProvides != null) {
                                for (pIdx in 0 until nestedProvides.length()) {
                                    val np = nestedProvides.optString(pIdx).trim()
                                    if (np.isNotBlank()) provides.add(np)
                                }
                            }
                            break
                        }
                        nestedEntry = zis.nextEntry
                    }
                }
            }
        }
    }

    private fun parseQuiltModJson(jar: File, zip: ZipFile, content: String): ModMetadata {
        val json = JSONObject(content)
        val loaderObj = json.optJSONObject("quilt_loader")
        val id = loaderObj?.optString("id")?.trim() ?: json.optString("id").trim()
        val name = json.optJSONObject("metadata")?.optString("name")?.takeIf { it.isNotBlank() } ?: id
        val version = loaderObj?.optString("version")?.trim() ?: json.optString("version").trim()

        val provides = mutableSetOf<String>()
        val providesArray = loaderObj?.optJSONArray("provides")
        if (providesArray != null) {
            for (i in 0 until providesArray.length()) {
                val p = providesArray.optString(i).trim()
                if (p.isNotBlank()) provides.add(p)
            }
        }

        val depends = mutableMapOf<String, String?>()
        val dependsArray = loaderObj?.optJSONArray("depends")
        if (dependsArray != null) {
            for (i in 0 until dependsArray.length()) {
                val depObj = dependsArray.optJSONObject(i)
                if (depObj != null) {
                    val depId = depObj.optString("id").trim()
                    val ver = depObj.optString("versions").takeIf { it.isNotBlank() }
                    if (depId.isNotBlank()) depends[depId] = ver
                } else {
                    val depStr = dependsArray.optString(i).trim()
                    if (depStr.isNotBlank()) depends[depStr] = null
                }
            }
        }

        return ModMetadata(
            id = id,
            name = name,
            version = version,
            file = jar,
            provides = provides,
            depends = depends
        )
    }

    private fun parseModsToml(jar: File, content: String, isNeoForge: Boolean): ModMetadata {
        var modId = jar.nameWithoutExtension
        var modName = modId
        var version = ""
        val depends = mutableMapOf<String, String?>()

        // Simple TOML line scanner for modId, displayName, version and dependencies
        val lines = content.lines()
        var inDependencyBlock = false
        var currentDepId = ""
        var currentDepMandatory = true
        var currentDepVersion = ""

        for (rawLine in lines) {
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) continue

            if (line.startsWith("[[dependencies") || line.startsWith("[dependencies")) {
                if (inDependencyBlock && currentDepId.isNotBlank() && currentDepMandatory) {
                    depends[currentDepId] = currentDepVersion.takeIf { it.isNotBlank() }
                }
                inDependencyBlock = true
                currentDepId = ""
                currentDepMandatory = true
                currentDepVersion = ""
                continue
            }

            if (line.startsWith("[[")) {
                if (inDependencyBlock && currentDepId.isNotBlank() && currentDepMandatory) {
                    depends[currentDepId] = currentDepVersion.takeIf { it.isNotBlank() }
                }
                inDependencyBlock = false
                continue
            }

            if (!inDependencyBlock) {
                if (line.startsWith("modId=") || line.startsWith("modId =")) {
                    modId = line.substringAfter('=').trim().trim('"', '\'')
                } else if (line.startsWith("displayName=") || line.startsWith("displayName =")) {
                    modName = line.substringAfter('=').trim().trim('"', '\'')
                } else if (line.startsWith("version=") || line.startsWith("version =")) {
                    version = line.substringAfter('=').trim().trim('"', '\'')
                }
            } else {
                if (line.startsWith("modId=") || line.startsWith("modId =")) {
                    currentDepId = line.substringAfter('=').trim().trim('"', '\'')
                } else if (line.startsWith("mandatory=") || line.startsWith("mandatory =")) {
                    currentDepMandatory = line.substringAfter('=').trim().toBooleanStrictOrNull() ?: true
                } else if (line.startsWith("type=") || line.startsWith("type =")) {
                    val depType = line.substringAfter('=').trim().trim('"', '\'')
                    currentDepMandatory = depType.equals("required", ignoreCase = true)
                } else if (line.startsWith("versionRange=") || line.startsWith("versionRange =")) {
                    currentDepVersion = line.substringAfter('=').trim().trim('"', '\'')
                }
            }
        }

        if (inDependencyBlock && currentDepId.isNotBlank() && currentDepMandatory) {
            depends[currentDepId] = currentDepVersion.takeIf { it.isNotBlank() }
        }

        return ModMetadata(
            id = modId,
            name = modName,
            version = version,
            file = jar,
            provides = emptySet(),
            depends = depends
        )
    }
}
