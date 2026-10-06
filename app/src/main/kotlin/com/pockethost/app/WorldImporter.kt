package com.pockethost.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.pockethost.app.service.PlayerDataManager
import com.pockethost.app.service.ServerFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dalvik.system.ZipPathValidator
import java.io.File
import java.util.zip.ZipException
import java.util.zip.ZipFile

object WorldImporter {

    enum class ImportDimension(
        val label: String,
        internal val bukkitSuffix: String,
        internal val vanillaFolder: String,
        internal val namespacedFolder: String,
        internal val folderNames: Set<String>,
        internal val folderSuffixes: Set<String>
    ) {
        OVERWORLD("Overworld", "", "", "overworld", emptySet(), emptySet()),
        NETHER("Nether", "_nether", "DIM-1", "the_nether", setOf("dim-1", "the_nether"), setOf("_nether", "nether")),
        END("End", "_the_end", "DIM1", "the_end", setOf("dim1", "the_end"), setOf("_the_end", "_end", "end"))
    }

    suspend fun importWorld(
        context: Context,
        zipUri: Uri,
        serverType: com.pockethost.app.data.model.ServerType,
        serverVersionId: String,
        folderName: String = "world",
        dimension: ImportDimension = ImportDimension.OVERWORLD,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val stagingDir = File(context.cacheDir, "import_staging_${System.currentTimeMillis()}")
            val tempFile = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}.zip")
            try {
                // Step 1: Copy to local temp file to avoid ContentResolver stream instabilities
                onProgress(0f, "Importing selected world file...")
                val sourceSize = queryContentLength(context, zipUri)
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        zipUri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                context.contentResolver.openInputStream(zipUri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        var lastPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (sourceSize > 0L) {
                                val percent = ((copied * 35L) / sourceSize).toInt().coerceIn(0, 35)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent / 100f, "Importing selected world file...")
                                }
                            }
                        }
                    }
                } ?: throw Exception("Could not open source file")
                onProgress(0.35f, "Preparing to extract world files...")

                val serverDir = ServerFileManager.getServerDir(context, folderName)

                if (dimension != ImportDimension.OVERWORLD) {
                    // A Nether/End zip is extracted away from the server directory first. Its
                    // region/ folder would otherwise be indistinguishable from the Overworld's
                    // once both sit in the same tree.
                    extractZip(tempFile, stagingDir, onProgress)
                    onProgress(0.97f, "Installing ${dimension.label} files...")
                    installDimensionFromStaging(stagingDir, serverDir, folderName, dimension)
                    onProgress(1f, "World import complete!")
                    return@withContext Result.success(Unit)
                }

                // Extract directly into serverDir, NOT a subfolder named after the world.
                // This prevents the 'world/world/world' deep nesting issue.
                extractZip(tempFile, serverDir, onProgress)

                android.util.Log.i("WorldImporter", "Extraction complete. Checking structure...")

                // Log top-level serverDir contents after extraction to help debug missing playerdata
                try {
                    val topLevel = serverDir.listFiles()?.map { it.name } ?: emptyList()
                    android.util.Log.d("WorldImporter", "Post-extract serverDir contents: ${topLevel.joinToString(", ")}")
                    val rootPlayerdataExists = File(serverDir, "playerdata").exists()
                    val targetPlayerdataExists = File(File(serverDir, folderName), "playerdata").exists()
                    android.util.Log.d("WorldImporter", "playerdata at server root=$rootPlayerdataExists, playerdata in target($folderName)=$targetPlayerdataExists")
                } catch (e: Exception) {
                    android.util.Log.w("WorldImporter", "Failed to list serverDir contents", e)
                }

                // Step 3: Fix nested structure and migrate server files
                // Note: The ServerStateHolder.flattenWorldStructure will also run during next refresh.
                onProgress(0.97f, "Normalizing imported world files...")
                normalizeRestoredServerBackup(serverDir, serverType, folderName)

                // Step 4: Fix player data UUIDs (Online -> Offline conversion)
                onProgress(0.99f, "Finalizing imported world...")
                android.util.Log.d("WorldImporter", "Starting fixOfflineUuids for $folderName. serverDir=${serverDir.absolutePath}")
                try {
                    val rootPlayerdata = File(serverDir, "playerdata")
                    val targetPlayerdata = File(File(serverDir, folderName), "playerdata")
                    android.util.Log.d("WorldImporter", "playerdata root exists=${rootPlayerdata.exists()}, target exists=${targetPlayerdata.exists()}")
                } catch (e: Exception) {
                    android.util.Log.w("WorldImporter", "Error checking playerdata locations", e)
                }
                PlayerDataManager.fixOfflineUuids(serverDir, folderName)
                onProgress(1f, "World import complete!")

                Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.e("WorldImporter", "Import failed", e)
                Result.failure(e)
            } finally {
                if (tempFile.exists()) tempFile.delete()
                if (stagingDir.exists()) stagingDir.deleteRecursively()
            }
        }

    /**
     * Opens a zip the way the importers need it: archives written by some desktop and
     * hosting-panel tools store entries as "/world/level.dat", which Android 14+ refuses to
     * even open for apps targeting SDK 34+. Every caller normalizes entry names and does its
     * own zip-slip check, so the platform check is lifted for the open and then put back.
     */
    internal fun openZip(file: File): ZipFile {
        val firstFailure = try {
            return ZipFile(file)
        } catch (e: ZipException) {
            e
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) throw notAZip(firstFailure)
        ZipPathValidator.clearCallback()
        try {
            return ZipFile(file)
        } catch (e: ZipException) {
            throw notAZip(e)
        } finally {
            ZipPathValidator.setCallback(object : ZipPathValidator.Callback {
                override fun onZipEntryAccess(path: String) {
                    if (path.startsWith("/") || path.split('/').any { it == ".." }) {
                        throw ZipException("Invalid zip entry path: $path")
                    }
                }
            })
        }
    }

    private fun notAZip(cause: ZipException) =
        java.io.IOException("The selected file is not a valid ZIP archive. Pick the world's .zip file.", cause)

    internal fun extractZip(zipFile: File, extractRoot: File, onProgress: (Float, String) -> Unit) {
        android.util.Log.i("WorldImporter", "Extracting world zip: ${zipFile.length()} bytes")
        extractRoot.mkdirs()
        val rootPath = extractRoot.canonicalPath
        openZip(zipFile).use { zip ->
            val totalEntries = zip.size().coerceAtLeast(1)
            var processed = 0
            var lastPercent = -1
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val normalizedName = entry.name
                    .replace('\\', '/')
                    .trimStart('/')
                    .removePrefix("./")
                    .trim()
                val skip = normalizedName.isBlank() ||
                    normalizedName.startsWith("__MACOSX/") ||
                    normalizedName.endsWith(".DS_Store")
                if (!skip) {
                    val entryFile = File(extractRoot, normalizedName)

                    // Prevent zip slip attack. The separator is part of the comparison so a
                    // crafted "../worldsomething/x" entry cannot land in a sibling server
                    // directory whose name merely starts with the extraction root's name.
                    val entryPath = entryFile.canonicalPath
                    if (entryPath != rootPath && !entryPath.startsWith(rootPath + File.separator)) {
                        android.util.Log.e("WorldImporter", "Zip slip detected: ${entry.name}")
                    } else if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            entryFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                processed++
                val currentPercent = processed * 100 / totalEntries
                if (currentPercent != lastPercent) {
                    lastPercent = currentPercent
                    val progress = 0.35f + ((processed.toFloat() / totalEntries) * 0.60f)
                    onProgress(progress.coerceIn(0.35f, 0.95f), "Extracting world files...")
                }
            }
        }
    }

    private fun childDirIgnoreCase(dir: File, name: String): File? =
        dir.listFiles()?.firstOrNull { it.isDirectory && it.name.equals(name, ignoreCase = true) }

    private fun hasRegionFiles(dir: File): Boolean =
        dir.listFiles()?.any { it.isFile && it.extension.equals("mca", ignoreCase = true) } == true

    internal fun installDimensionFromStaging(
        stagingDir: File,
        serverDir: File,
        worldName: String,
        dimension: ImportDimension
    ) {
        fun depth(dir: File) = dir.absolutePath.count { it == File.separatorChar }

        // Directories that hold a populated region/ folder, shallowest first.
        val dimensionRoots = stagingDir.walkTopDown().maxDepth(8)
            .filter { it.isDirectory && childDirIgnoreCase(it, "region")?.let(::hasRegionFiles) == true }
            .sortedBy(::depth)
            .toList()

        val sourceRoot = dimensionRoots.firstOrNull { it.name.lowercase() in dimension.folderNames }
            ?: dimensionRoots.firstOrNull { root ->
                val name = root.name.lowercase()
                dimension.folderSuffixes.any { name == it || (it.startsWith("_") && name.endsWith(it)) }
            }
            ?: dimensionRoots.firstOrNull()

        // Fall back to a zip of the bare .mca files (the contents of a region folder).
        val looseRegionDir = if (sourceRoot == null) {
            stagingDir.walkTopDown().maxDepth(8)
                .filter { it.isDirectory && hasRegionFiles(it) }
                .minByOrNull(::depth)
        } else null

        if (sourceRoot == null && looseRegionDir == null) {
            throw java.io.IOException("This ZIP has no ${dimension.label} region (.mca) files.")
        }

        // The import replaces the dimension: DimensionMigrator keeps whichever copy of a
        // region file is larger, so stale chunks left behind would beat the imported ones.
        val worldRoot = File(serverDir, worldName)
        val bukkitRoot = File(serverDir, worldName + dimension.bukkitSuffix)
        listOf(
            bukkitRoot,
            File(worldRoot, dimension.vanillaFolder),
            File(worldRoot, "dimensions/minecraft/${dimension.namespacedFolder}")
        ).forEach { if (it.exists()) it.deleteRecursively() }

        // Bukkit-style sibling layout; DimensionMigrator moves it to wherever the server type
        // expects the dimension.
        val destination = File(bukkitRoot, dimension.vanillaFolder)
        if (sourceRoot != null) {
            android.util.Log.i("WorldImporter", "Installing ${dimension.label} from ${sourceRoot.relativeTo(stagingDir).path.ifEmpty { "." }}")
            listOf("region", "entities", "poi").forEach { name ->
                childDirIgnoreCase(sourceRoot, name)?.let { mergeDirectoryContents(it, File(destination, name)) }
            }
        } else {
            mergeDirectoryContents(looseRegionDir!!, File(destination, "region"))
        }

        ensureRestoredServerProperties(serverDir, worldName)
    }

    private fun queryContentLength(context: Context, uri: Uri): Long {
        runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                if (descriptor.length > 0L) return descriptor.length
            }
        }
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && cursor.moveToFirst()) {
                    val size = cursor.getLong(sizeIndex)
                    if (size > 0L) return size
                }
            }
        }
        return -1L
    }

    fun normalizeRestoredServerBackup(serverDir: File, serverType: com.pockethost.app.data.model.ServerType, targetWorld: String = "world") {
        val targetRoot = File(serverDir, targetWorld)
        
        // 0. Preliminary cleanup: Normalize core folder names to lowercase to avoid case-sensitivity issues on Android
        val coreFolders = setOf("playerdata", "stats", "advancements", "data", "datapacks", "region", "poi", "entities")
        serverDir.walkTopDown().maxDepth(5).filter { it.isDirectory }.forEach { dir ->
            if (dir.name.lowercase() in coreFolders && dir.name != dir.name.lowercase()) {
                val lowercaseDir = File(dir.parentFile, dir.name.lowercase())
                if (!lowercaseDir.exists()) {
                    android.util.Log.d("WorldImporter", "Normalizing folder case: ${dir.name} -> ${lowercaseDir.name}")
                    dir.renameTo(lowercaseDir)
                } else {
                    android.util.Log.d("WorldImporter", "Merging case-mismatched folder: ${dir.name} into ${lowercaseDir.name}")
                    mergeDirectoryContents(dir, lowercaseDir)
                    dir.deleteRecursively()
                }
            }
        }

        // 1. Locate level.dat to find the overworld directory
        val levelDat = serverDir.walkTopDown().maxDepth(6).firstOrNull { it.isFile && it.name.lowercase() == "level.dat" }
        if (levelDat == null) {
            // Region-only export with no level.dat
            val hasRegionFiles = serverDir.walkTopDown().maxDepth(6).any { 
                it.isFile && it.extension.lowercase() == "mca" 
            }
            if (hasRegionFiles) {
                android.util.Log.i("WorldImporter", "No level.dat found, but region files detected. Processing as region-only import for $targetWorld.")
                normalizeRegionOnlyImport(serverDir, targetWorld)
                return
            }
            android.util.Log.e("WorldImporter", "No level.dat or region files found in extracted backup.")
            return
        }
        val overworldDir = levelDat.parentFile ?: serverDir

        // 2. Locate server.properties to find the backup root and imported name
        val propsFile = serverDir.walkTopDown().maxDepth(6).firstOrNull { it.isFile && it.name == "server.properties" }
        val backupRoot = propsFile?.parentFile ?: overworldDir
        
        var importedBaseWorldName = overworldDir.name
        if (propsFile != null && propsFile.exists()) {
            runCatching {
                java.util.Properties().apply {
                    propsFile.inputStream().use { load(it) }
                }.getProperty("level-name")?.trim()?.takeIf { it.isNotBlank() }?.let {
                    importedBaseWorldName = it
                }
            }
        }
        
        if (importedBaseWorldName == "world" && overworldDir.name != "world") {
            // If properties says "world" but the folder is something else, prioritize the folder name
            // unless the folder is the root serverDir.
            if (overworldDir.absolutePath != serverDir.absolutePath && overworldDir.absolutePath != backupRoot.absolutePath) {
                importedBaseWorldName = overworldDir.name
            }
        }

        android.util.Log.i("WorldImporter", "Normalizing backup. backupRoot=${backupRoot.name}, overworldDir=${overworldDir.name}, importedName=$importedBaseWorldName")

        val tempSafeTarget = File(serverDir, "pocketcraft_temp_target_${System.currentTimeMillis()}")
        val isVanillaStyle = serverType == com.pockethost.app.data.model.ServerType.FABRIC ||
                serverType == com.pockethost.app.data.model.ServerType.MODPACK ||
                serverType == com.pockethost.app.data.model.ServerType.VANILLA

        // 3. Move files to their correct locations
        // We iterate over backupRoot. If backupRoot == overworldDir, everything is a world file (except known server files).
        val knownServerFiles = setOf("server.properties", "banned-ips.json", "banned-players.json", "ops.json", "whitelist.json", "usercache.json", "bukkit.yml", "spigot.yml", "paper.yml")
        
        val itemsToProcess = backupRoot.listFiles().orEmpty().toList()
        
        itemsToProcess.forEach { file ->
            val isWorldFile = backupRoot == overworldDir && file.name !in knownServerFiles && !file.isDirectory
            
            if (file.isDirectory) {
                val mappedName = mapImportedDimensionName(
                    sourceName = file.name,
                    importedBaseWorldName = importedBaseWorldName,
                    targetBaseWorldName = targetWorld,
                    serverType = serverType
                )
                
                val dest = when {
                    mappedName == targetWorld || file == overworldDir -> tempSafeTarget
                    mappedName == "${targetWorld}_nether" || mappedName == "${targetWorld}_the_end" -> File(serverDir, mappedName)
                    mappedName == "DIM-1" || mappedName == "DIM1" -> File(tempSafeTarget, mappedName)
                    backupRoot == overworldDir -> File(tempSafeTarget, file.name)
                    else -> File(serverDir, file.name)
                }
                
                if (file.absolutePath != dest.absolutePath) {
                    android.util.Log.d("WorldImporter", "Migrating directory: ${file.name} -> ${dest.absolutePath}")
                    mergeDirectoryContents(file, dest)
                    file.deleteRecursively()
                }
            } else {
                val dest = if (isWorldFile || file.parentFile == overworldDir) {
                    File(tempSafeTarget, file.name)
                } else {
                    File(serverDir, file.name)
                }
                
                if (file.absolutePath != dest.absolutePath) {
                    dest.parentFile?.mkdirs()
                    if (dest.exists()) dest.delete()
                    if (!file.renameTo(dest)) {
                        file.copyTo(dest, overwrite = true)
                        file.delete()
                    }
                }
            }
        }
        
        // 4. Move temp target to real target
        if (tempSafeTarget.exists()) {
            mergeDirectoryContents(tempSafeTarget, targetRoot)
            tempSafeTarget.deleteRecursively()
        }

        // 4b. If some world content ended up at the server root (level.dat, playerdata, etc),
        // move that content into the chosen target world directory so player files are found.
        try {
            android.util.Log.d("WorldImporter", "Checking for root-level world content to move into ${targetRoot.name}")
            moveRootLevelWorldContentIntoTarget(serverDir, targetRoot)
        } catch (e: Exception) {
            android.util.Log.w("WorldImporter", "Failed to move root-level world content into target", e)
        }
        
        // 5. Cleanup leftover extraction folders
        if (backupRoot.exists() && backupRoot.absolutePath != serverDir.absolutePath && backupRoot.absolutePath != targetRoot.absolutePath) {
            backupRoot.deleteRecursively()
        }
        if (overworldDir.exists() && overworldDir.absolutePath != serverDir.absolutePath && overworldDir.absolutePath != targetRoot.absolutePath) {
            overworldDir.deleteRecursively()
        }
        
        // Cleanup any other empty directories at the root
        serverDir.listFiles()?.filter { it.isDirectory && it.listFiles().isNullOrEmpty() }?.forEach {
            it.deleteRecursively()
        }

        // Post-import dimension folder structure normalization for Spigot/Paper style
        if (!isVanillaStyle) {
            val netherDir = File(serverDir, "${targetWorld}_nether")
            val endDir = File(serverDir, "${targetWorld}_the_end")

            val nestedNetherInRoot = File(targetRoot, "DIM-1")
            if (nestedNetherInRoot.exists() && nestedNetherInRoot.isDirectory) {
                if (!netherDir.exists()) netherDir.mkdirs()
                mergeDirectoryContents(nestedNetherInRoot, netherDir)
                val targetNested = File(netherDir, "DIM-1")
                mergeDirectoryContents(nestedNetherInRoot, targetNested)
                nestedNetherInRoot.deleteRecursively()
            }

            val nestedEndInRoot = File(targetRoot, "DIM1")
            if (nestedEndInRoot.exists() && nestedEndInRoot.isDirectory) {
                if (!endDir.exists()) endDir.mkdirs()
                mergeDirectoryContents(nestedEndInRoot, endDir)
                val targetNested = File(endDir, "DIM1")
                mergeDirectoryContents(nestedEndInRoot, targetNested)
                nestedEndInRoot.deleteRecursively()
            }
        }

        ensureRestoredServerProperties(serverDir, targetWorld)
    }

    /** A world zip without level.dat: just region data, optionally with DIM-1/DIM1 beside it. */
    private fun normalizeRegionOnlyImport(serverDir: File, targetWorld: String) {
        val targetRoot = File(serverDir, targetWorld)
        val dimensionFolders = ImportDimension.NETHER.folderNames + ImportDimension.END.folderNames
        val worldRoot = serverDir.walkTopDown().maxDepth(6)
            .filter { it.isDirectory && it.name.lowercase() !in dimensionFolders }
            .filter { childDirIgnoreCase(it, "region")?.let(::hasRegionFiles) == true }
            .minByOrNull { it.absolutePath.count { c -> c == File.separatorChar } }

        when {
            worldRoot == null || worldRoot.absolutePath == targetRoot.absolutePath -> Unit
            worldRoot.absolutePath == serverDir.absolutePath -> moveRootLevelWorldContentIntoTarget(serverDir, targetRoot)
            else -> {
                mergeDirectoryContents(worldRoot, targetRoot)
                worldRoot.deleteRecursively()
            }
        }

        // Clean up empty directories
        serverDir.listFiles()?.filter { it.isDirectory && it.listFiles().isNullOrEmpty() }?.forEach {
            it.deleteRecursively()
        }

        ensureRestoredServerProperties(serverDir, targetWorld)
    }

    private fun mapImportedDimensionName(sourceName: String, importedBaseWorldName: String, targetBaseWorldName: String, serverType: com.pockethost.app.data.model.ServerType): String {
        val cleanSource = sourceName.trim()
        val lowerSource = cleanSource.lowercase()
        val isVanillaStyle = serverType == com.pockethost.app.data.model.ServerType.FABRIC || 
                            serverType == com.pockethost.app.data.model.ServerType.MODPACK ||
                            serverType == com.pockethost.app.data.model.ServerType.VANILLA

        // Match Overworld
        if (lowerSource == "overworld" || lowerSource == "world" || lowerSource == importedBaseWorldName.lowercase()) {
            return targetBaseWorldName
        }

        // Match Nether
        if (
            lowerSource == "dim-1" ||
            lowerSource == "nether" ||
            lowerSource == "world_nether" ||
            lowerSource.endsWith("_nether") ||
            lowerSource == "${importedBaseWorldName.lowercase()}_nether"
        ) {
            return if (isVanillaStyle) "DIM-1" else "${targetBaseWorldName}_nether"
        }

        // Match The End
        if (
            lowerSource == "dim1" ||
            lowerSource == "the_end" ||
            lowerSource == "end" ||
            lowerSource == "world_the_end" ||
            lowerSource.endsWith("_the_end") ||
            lowerSource.endsWith("_end") ||
            lowerSource == "${importedBaseWorldName.lowercase()}_the_end"
        ) {
            return if (isVanillaStyle) "DIM1" else "${targetBaseWorldName}_the_end"
        }

        return cleanSource
    }

    private fun mergeDirectoryContents(source: File, target: File) {
        if (!target.exists()) target.mkdirs()
        source.listFiles()?.forEach { child ->
            val destination = File(target, child.name)
            when {
                child.isDirectory && destination.isDirectory -> {
                    mergeDirectoryContents(child, destination)
                    child.deleteRecursively()
                }
                child.isDirectory -> {
                    if (destination.exists()) destination.deleteRecursively()
                    if (!child.renameTo(destination)) {
                        child.copyRecursively(destination, overwrite = true)
                        child.deleteRecursively()
                    }
                }
                else -> {
                    if (destination.exists()) destination.delete()
                    if (!child.renameTo(destination)) {
                        child.copyTo(destination, overwrite = true)
                        child.delete()
                    }
                }
            }
        }
    }

    private fun ensureRestoredServerProperties(serverDir: File, targetWorldName: String) {
        val propsFile = File(serverDir, "server.properties")
        runCatching {
            val lines = if (propsFile.exists()) {
                propsFile.readLines().toMutableList()
            } else {
                mutableListOf()
            }
            val index = lines.indexOfFirst { it.startsWith("level-name=") }
            if (index >= 0) {
                lines[index] = "level-name=$targetWorldName"
            } else {
                lines.add("level-name=$targetWorldName")
            }
            propsFile.writeText(lines.joinToString("\n"))
        }
    }

    private fun moveRootLevelWorldContentIntoTarget(serverDir: File, targetRoot: File) {
        val worldFileNames = setOf(
            "advancements", "data", "datapacks", "dim-1", "dim1", "entities",
            "icon.png", "level.dat", "level.dat_old", "playerdata", "poi", "region",
            "session.lock", "stats", "uid.dat",
            // Minecraft 26.1+ keeps chunks under dimensions/ and player files under players/.
            "dimensions", "players"
        )
        if (!targetRoot.exists()) {
            targetRoot.mkdirs()
        }
        serverDir.listFiles()?.forEach { file ->
            val fileNameLower = file.name.lowercase()
            val shouldMove = fileNameLower in worldFileNames || 
                           (file.isDirectory && File(file, "level.dat").exists())

            if (shouldMove && file.absolutePath != targetRoot.absolutePath && !file.absolutePath.startsWith(targetRoot.absolutePath)) {
                val dest = File(targetRoot, file.name)
                try {
                    android.util.Log.d("WorldImporter", "Moving root world item: ${file.name} -> ${dest.absolutePath}")
                    if (dest.exists()) {
                        if (file.isDirectory) {
                            mergeDirectoryContents(file, dest)
                            file.deleteRecursively()
                            android.util.Log.d("WorldImporter", "Merged and deleted root directory ${file.name}")
                        } else {
                            file.delete()
                            android.util.Log.d("WorldImporter", "Deleted duplicate root file ${file.name} because target exists")
                        }
                    } else {
                        if (!file.renameTo(dest)) {
                            if (file.isDirectory) {
                                file.copyRecursively(dest, overwrite = true)
                                file.deleteRecursively()
                                android.util.Log.d("WorldImporter", "Copied and deleted root directory ${file.name}")
                            } else {
                                file.copyTo(dest, overwrite = true)
                                file.delete()
                                android.util.Log.d("WorldImporter", "Copied and deleted root file ${file.name}")
                            }
                        } else {
                            android.util.Log.d("WorldImporter", "Renamed root item ${file.name} -> ${dest.name}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("WorldImporter", "Failed to move root-level content ${file.name}", e)
                }
            }
        }
    }
}
