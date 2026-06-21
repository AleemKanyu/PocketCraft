package com.pocketcraft.server

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.pocketcraft.server.service.PlayerDataManager
import com.pocketcraft.server.service.ServerFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

object WorldImporter {

    suspend fun importWorld(
        context: Context,
        zipUri: Uri,
        serverType: com.pocketcraft.server.data.model.ServerType,
        serverVersionId: String,
        folderName: String = "world",
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val tempFile = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}.zip")
            try {
                // Step 1: Copy to local temp file to avoid ContentResolver stream instabilities
                onProgress(0f, "Importing selected world file...")
                val sourceSize = queryContentLength(context, zipUri)
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
                // Extract directly into serverDir, NOT a subfolder named after the world.
                // This prevents the 'world/world/world' deep nesting issue.
                val extractRoot = serverDir

                // Step 2: Extract from local file using ZipFile (more robust than ZipInputStream)
                android.util.Log.i("WorldImporter", "Extracting world zip: ${tempFile.length()} bytes")
                ZipFile(tempFile).use { zip ->
                    val totalEntries = zip.size().toFloat().coerceAtLeast(1f)
                    var processed = 0
                    var lastPercent = -1
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val rawName = entry.name
                        val normalizedName = rawName
                            .replace('\\', '/')
                            .removePrefix("/")
                            .removePrefix("./")
                            .trim()
                        if (normalizedName.isBlank()) {
                            processed++
                            val currentPercent = (processed * 100 / totalEntries.toInt()).coerceIn(0, 100)
                            if (currentPercent != lastPercent) {
                                lastPercent = currentPercent
                                val progress = 0.35f + ((processed / totalEntries) * 0.60f)
                                onProgress(progress.coerceIn(0.35f, 0.95f), "Extracting world files...")
                            }
                            continue
                        }
                        if (normalizedName.startsWith("__MACOSX/") || normalizedName.endsWith(".DS_Store")) {
                            processed++
                            val currentPercent = (processed * 100 / totalEntries.toInt()).coerceIn(0, 100)
                            if (currentPercent != lastPercent) {
                                lastPercent = currentPercent
                                val progress = 0.35f + ((processed / totalEntries) * 0.60f)
                                onProgress(progress.coerceIn(0.35f, 0.95f), "Extracting world files...")
                            }
                            continue
                        }

                        val entryFile = File(extractRoot, normalizedName)

                        // Prevent zip slip attack
                        if (!entryFile.canonicalPath.startsWith(extractRoot.canonicalPath)) {
                            android.util.Log.e("WorldImporter", "Zip slip detected: ${entry.name}")
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
                        val currentPercent = (processed * 100 / totalEntries.toInt()).coerceIn(0, 100)
                        if (currentPercent != lastPercent) {
                            lastPercent = currentPercent
                            val progress = 0.35f + ((processed / totalEntries) * 0.60f)
                            onProgress(progress.coerceIn(0.35f, 0.95f), "Extracting world files...")
                        }
                    }
                }

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
            }
        }

    private fun queryContentLength(context: Context, uri: Uri): Long {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            if (descriptor.length > 0L) return descriptor.length
        }
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getLong(sizeIndex).takeIf { it > 0L } ?: -1L
            }
        }
        return -1L
    }

    fun normalizeRestoredServerBackup(serverDir: File, serverType: com.pocketcraft.server.data.model.ServerType, targetWorld: String = "world") {
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
            android.util.Log.e("WorldImporter", "No level.dat found in extracted backup.")
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
        val isVanillaStyle = serverType == com.pocketcraft.server.data.model.ServerType.FABRIC ||
                serverType == com.pocketcraft.server.data.model.ServerType.MODPACK

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

        ensureRestoredServerProperties(serverDir, targetWorld)
    }

    private fun mapImportedDimensionName(sourceName: String, importedBaseWorldName: String, targetBaseWorldName: String, serverType: com.pocketcraft.server.data.model.ServerType): String {
        val cleanSource = sourceName.trim()
        val lowerSource = cleanSource.lowercase()
        val isVanillaStyle = serverType == com.pocketcraft.server.data.model.ServerType.FABRIC || 
                            serverType == com.pocketcraft.server.data.model.ServerType.MODPACK

        // Match Overworld
        if (lowerSource == "overworld" || lowerSource == "world" || lowerSource == importedBaseWorldName.lowercase()) {
            return targetBaseWorldName
        }

        // Match Nether
        if (
            lowerSource == "dim-1" ||
            lowerSource == "nether" ||
            lowerSource == "world_nether" ||
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
            "session.lock", "stats", "uid.dat"
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
