package com.pocketcraft.server

import android.content.Context
import android.net.Uri
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
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val tempFile = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}.zip")
            try {
                // Step 1: Copy to local temp file to avoid ContentResolver stream instabilities
                context.contentResolver.openInputStream(zipUri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw Exception("Could not open source file")

                val serverDir = ServerFileManager.getServerDir(context, serverVersionId)
                val worldDir = File(serverDir, folderName)

                // Step 2: Delete existing world
                if (worldDir.exists()) {
                    android.util.Log.d("WorldImporter", "Deleting existing world at ${worldDir.absolutePath}")
                    worldDir.deleteRecursively()
                }
                worldDir.mkdirs()

                // Step 3: Extract from local file using ZipFile (more robust than ZipInputStream)
                android.util.Log.i("WorldImporter", "Extracting world zip: ${tempFile.length()} bytes")
                ZipFile(tempFile).use { zip ->
                    val totalEntries = zip.size().toFloat()
                    var processed = 0
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
                            onProgress(processed / totalEntries)
                            continue
                        }
                        if (normalizedName.startsWith("__MACOSX/") || normalizedName.endsWith(".DS_Store")) {
                            processed++
                            onProgress(processed / totalEntries)
                            continue
                        }

                        val entryFile = File(worldDir, normalizedName)

                        // Prevent zip slip attack
                        if (!entryFile.canonicalPath.startsWith(worldDir.canonicalPath)) {
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
                        onProgress(processed / totalEntries)
                    }
                }

                android.util.Log.i("WorldImporter", "Extraction complete. Checking structure...")

                // Step 4: Fix nested structure and migrate server files
                android.util.Log.d("WorldImporter", "Starting fixWorldStructureAndMigrate for $folderName")
                fixWorldStructureAndMigrate(serverDir, worldDir, serverType, folderName)

                // Step 5: Fix player data UUIDs (Online -> Offline conversion)
                android.util.Log.d("WorldImporter", "Starting fixOfflineUuids for $folderName")
                PlayerDataManager.fixOfflineUuids(serverDir, folderName)

                Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.e("WorldImporter", "Import failed", e)
                Result.failure(e)
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }

    fun normalizeRestoredServerBackup(serverDir: File, serverType: com.pocketcraft.server.data.model.ServerType) {
        val targetRoot = File(serverDir, "world")
        val propsFile = File(serverDir, "server.properties")
        val importedBaseWorldName = runCatching {
            java.util.Properties().apply {
                if (propsFile.exists()) {
                    propsFile.inputStream().use { load(it) }
                }
            }.getProperty("level-name")
        }.getOrNull()?.trim().orEmpty().ifBlank { null }

        val targetWorld = "world"
        moveRootLevelWorldContentIntoTarget(serverDir, targetRoot)

        serverDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .forEach { file ->
                val mappedName = mapImportedDimensionName(
                    sourceName = file.name,
                    importedBaseWorldName = importedBaseWorldName ?: "world",
                    targetBaseWorldName = targetWorld,
                    serverType = serverType
                )

                when {
                    mappedName == targetWorld && file.absolutePath != targetRoot.absolutePath -> {
                        android.util.Log.i("WorldImporter", "Remapping restored world ${file.name} -> ${targetRoot.name}")
                        mergeDirectoryContents(file, targetRoot)
                        if (file.exists() && file.absolutePath != targetRoot.absolutePath) {
                            file.deleteRecursively()
                        }
                    }
                    mappedName != file.name -> {
                        val isDim = mappedName == "DIM-1" || mappedName == "DIM1"
                        val isVanillaStyle = serverType == com.pocketcraft.server.data.model.ServerType.FABRIC ||
                                serverType == com.pocketcraft.server.data.model.ServerType.MODPACK

                        val target = if (isDim && isVanillaStyle) {
                            File(targetRoot, mappedName)
                        } else {
                            File(serverDir, mappedName)
                        }

                        android.util.Log.i("WorldImporter", "Remapping restored dimension ${file.name} -> ${target.absolutePath}")
                        if (target.exists()) target.deleteRecursively()
                        target.parentFile?.mkdirs()
                        if (!file.renameTo(target)) {
                            file.copyRecursively(target, overwrite = true)
                            file.deleteRecursively()
                        }
                    }
                }
            }

        if (!File(targetRoot, "level.dat").exists()) {
            val nestedLevelDat = serverDir.walkTopDown()
                .maxDepth(6)
                .firstOrNull { it.isFile && it.name == "level.dat" && !it.absolutePath.startsWith(targetRoot.absolutePath) }
            val nestedRoot = nestedLevelDat?.parentFile
            if (nestedRoot != null && nestedRoot.absolutePath != targetRoot.absolutePath) {
                android.util.Log.i("WorldImporter", "Normalizing nested restored world ${nestedRoot.absolutePath} -> ${targetRoot.absolutePath}")
                mergeDirectoryContents(nestedRoot, targetRoot)

                val parent = nestedRoot.parentFile
                if (parent != null && parent.absolutePath != serverDir.absolutePath && parent.absolutePath != targetRoot.absolutePath) {
                    parent.listFiles()
                        .orEmpty()
                        .filter { it.isDirectory && it != nestedRoot }
                        .forEach { sibling ->
                            if (File(sibling, "level.dat").exists() || File(sibling, "region").isDirectory) {
                                val mappedName = mapImportedDimensionName(
                                    sourceName = sibling.name,
                                    importedBaseWorldName = importedBaseWorldName ?: nestedRoot.name,
                                    targetBaseWorldName = targetWorld,
                                    serverType = serverType
                                )
                                val target = File(serverDir, mappedName)
                                if (target.exists()) target.deleteRecursively()
                                if (!sibling.renameTo(target)) {
                                    sibling.copyRecursively(target, overwrite = true)
                                    sibling.deleteRecursively()
                                }
                            }
                        }
                }

                if (nestedRoot.exists() && nestedRoot.absolutePath != targetRoot.absolutePath) {
                    nestedRoot.deleteRecursively()
                }
            }
        }

        fixWorldStructureAndMigrate(serverDir, targetRoot, serverType, targetWorld)
        ensureRestoredServerProperties(serverDir, targetWorld)
    }

    private fun fixWorldStructureAndMigrate(serverDir: File, root: File, serverType: com.pocketcraft.server.data.model.ServerType, folderName: String) {
        android.util.Log.i("WorldImporter", "Fixing world structure in ${root.absolutePath} for $folderName")
        
        // 1. Detect if this is a full server backup (contains server.properties)
        val internalProps = root.walkTopDown().maxDepth(4).find { it.name == "server.properties" }
        var importedBaseWorldName: String? = null
        if (internalProps != null) {
            val baseDir = internalProps.parentFile ?: root
            android.util.Log.i("WorldImporter", "Detected full server backup at ${baseDir.absolutePath}. Migrating everything...")
            
            val props = java.util.Properties().apply { 
                runCatching { internalProps.inputStream().use { load(it) } }
            }
            val internalWorldName = props.getProperty("level-name", "world")
            importedBaseWorldName = internalWorldName
            
            baseDir.listFiles()?.forEach { file ->
                val mappedName = mapImportedDimensionName(file.name, internalWorldName, folderName, serverType)
                val isVanillaStyle = serverType == com.pocketcraft.server.data.model.ServerType.FABRIC ||
                        serverType == com.pocketcraft.server.data.model.ServerType.MODPACK

                val target = if (mappedName == folderName) {
                    root 
                } else if (isVanillaStyle && mappedName.startsWith("DIM")) {
                    File(root, mappedName)
                } else {
                    File(serverDir, mappedName)
                }
                
                if (target.absolutePath == root.absolutePath && file.isDirectory && file.absolutePath != root.absolutePath) {
                    android.util.Log.i("WorldImporter", "Merging world folder ${file.name} into ${root.name}")
                    file.listFiles()?.forEach { sub ->
                        val subTarget = File(root, sub.name)
                        if (sub.isDirectory) {
                            if (subTarget.exists() && subTarget.isDirectory) {
                                sub.listFiles()?.forEach { grandChild ->
                                    val finalTarget = File(subTarget, grandChild.name)
                                    if (finalTarget.exists()) {
                                        if (grandChild.length() > finalTarget.length()) {
                                            finalTarget.delete()
                                            grandChild.renameTo(finalTarget)
                                        } else {
                                            grandChild.delete()
                                        }
                                    } else {
                                        grandChild.renameTo(finalTarget)
                                    }
                                }
                                sub.deleteRecursively()
                            } else {
                                if (subTarget.exists()) subTarget.deleteRecursively()
                                sub.renameTo(subTarget)
                            }
                        } else {
                            if (subTarget.exists()) {
                                if (sub.length() > subTarget.length()) {
                                    subTarget.delete()
                                    sub.renameTo(subTarget)
                                } else {
                                    sub.delete()
                                }
                            } else {
                                sub.renameTo(subTarget)
                            }
                        }
                    }
                    file.deleteRecursively()
                } else if (target.absolutePath != file.absolutePath) {
                    android.util.Log.d("WorldImporter", "Migrating server file/dir: ${file.name} -> ${target.absolutePath}")
                    val pocketcraftProps = mutableListOf<String>()
                    if (target.name == "server.properties" && target.exists()) {
                        runCatching {
                            target.readLines().forEach { 
                                if (it.startsWith("pocketcraft-")) pocketcraftProps.add(it)
                            }
                        }
                    }
                    if (target.exists()) target.deleteRecursively()
                    if (!file.renameTo(target)) {
                        file.copyTo(target, overwrite = true)
                        file.deleteRecursively()
                    }
                    if (file.name == "server.properties") {
                        runCatching {
                            var lines = target.readLines().toMutableList()
                            lines.removeAll { it.startsWith("pocketcraft-") }
                            val levelIdx = lines.indexOfFirst { it.startsWith("level-name=") }
                            if (levelIdx != -1) lines[levelIdx] = "level-name=$folderName"
                            else lines.add("level-name=$folderName")
                            lines.addAll(pocketcraftProps)
                            target.writeText(lines.joinToString("\n"))
                        }
                    }
                }
            }
        }

        val levelDat = root.walkTopDown().maxDepth(6).find { it.name == "level.dat" }
        android.util.Log.d("WorldImporter", "Post-migration level.dat search found: ${levelDat?.absolutePath}")
        
        if (levelDat == null) return
        var realRoot = levelDat.parentFile ?: return

        val propsFile = File(serverDir, "server.properties")
        if (propsFile.exists()) {
            val lines = propsFile.readLines().toMutableList()
            val currentLevel = lines.find { it.startsWith("level-name=") }?.substringAfter('=')
            if (currentLevel != folderName) {
                val idx = lines.indexOfFirst { it.startsWith("level-name=") }
                if (idx != -1) lines[idx] = "level-name=$folderName"
                else lines.add("level-name=$folderName")
                propsFile.writeText(lines.joinToString("\n"))
            }
        }

        if (realRoot.absolutePath != root.absolutePath) {
            android.util.Log.i("WorldImporter", "Detected nested world root at ${realRoot.absolutePath}. Flattening to ${root.absolutePath}")
            realRoot.listFiles()?.forEach { file ->
                val target = File(root, file.name)
                if (target.exists()) target.deleteRecursively()
                if (!file.renameTo(target)) {
                    file.copyRecursively(target, overwrite = true)
                    file.deleteRecursively()
                }
            }
            
            val parent = realRoot.parentFile
            if (parent != null && parent.absolutePath != serverDir.absolutePath && parent.absolutePath != root.absolutePath) {
                parent.listFiles()?.forEach { sibling ->
                    if (sibling.isDirectory && sibling != realRoot) {
                        if (File(sibling, "level.dat").exists() || File(sibling, "region").isDirectory) {
                            val mappedName = mapImportedDimensionName(
                                sourceName = sibling.name,
                                importedBaseWorldName = importedBaseWorldName ?: root.name,
                                targetBaseWorldName = folderName,
                                serverType = serverType
                            )
                            val isVanillaStyle = serverType == com.pocketcraft.server.data.model.ServerType.FABRIC ||
                                    serverType == com.pocketcraft.server.data.model.ServerType.MODPACK

                            val target = if (isVanillaStyle && mappedName.startsWith("DIM")) {
                                File(root, mappedName)
                            } else {
                                File(serverDir, mappedName)
                            }

                            android.util.Log.i("WorldImporter", "Relocating dimension sibling: ${sibling.name} -> ${target.name}")
                            if (target.exists()) target.deleteRecursively()
                            if (!sibling.renameTo(target)) {
                                sibling.copyRecursively(target, overwrite = true)
                                sibling.deleteRecursively()
                            }
                        }
                    }
                }
            }

            realRoot.delete()
            var current = realRoot.parentFile
            while (current != null && current.absolutePath != root.absolutePath && current.listFiles()?.isEmpty() == true) {
                val toDelete = current
                current = current.parentFile
                toDelete.delete()
            }
        }

        migrateDimensionsToTargetStructure(serverDir, root, serverType, folderName)
    }

    private fun migrateDimensionsToTargetStructure(
        serverDir: File,
        worldDir: File,
        serverType: com.pocketcraft.server.data.model.ServerType,
        targetBaseWorldName: String
    ) {
        val isVanillaStyle = serverType == com.pocketcraft.server.data.model.ServerType.FABRIC ||
                serverType == com.pocketcraft.server.data.model.ServerType.MODPACK

        val dimensionCandidates = worldDir.listFiles()?.filter { it.isDirectory } ?: return
        dimensionCandidates.forEach { candidate ->
            val mappedName = mapImportedDimensionName(
                sourceName = candidate.name,
                importedBaseWorldName = targetBaseWorldName,
                targetBaseWorldName = targetBaseWorldName,
                serverType = serverType
            )

            if (mappedName != candidate.name) {
                val target = if (isVanillaStyle && mappedName.startsWith("DIM")) {
                    File(worldDir, mappedName)
                } else {
                    File(serverDir, mappedName)
                }

                if (target.absolutePath != candidate.absolutePath) {
                    android.util.Log.i("WorldImporter", "Migrating dimension child: ${candidate.name} -> ${target.name}")
                    if (target.exists()) target.deleteRecursively()
                    if (!candidate.renameTo(target)) {
                        candidate.copyRecursively(target, overwrite = true)
                        candidate.deleteRecursively()
                    }
                }
            }
        }
    }

    private fun mapImportedDimensionName(
        sourceName: String,
        importedBaseWorldName: String,
        targetBaseWorldName: String,
        serverType: com.pocketcraft.server.data.model.ServerType
    ): String {
        val cleanSource = sourceName.trim()
        val lowerSource = cleanSource.lowercase()
        
        val isVanillaStyle = serverType == com.pocketcraft.server.data.model.ServerType.FABRIC ||
                serverType == com.pocketcraft.server.data.model.ServerType.MODPACK

        // Match Overworld
        if (cleanSource.equals(importedBaseWorldName, ignoreCase = true) || lowerSource == "world") {
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
            "advancements",
            "data",
            "datapacks",
            "DIM-1",
            "DIM1",
            "entities",
            "icon.png",
            "level.dat",
            "level.dat_old",
            "playerdata",
            "poi",
            "region",
            "session.lock",
            "stats",
            "uid.dat"
        )
        val reservedRootNames = setOf(
            targetRoot.name,
            "${targetRoot.name}_nether",
            "${targetRoot.name}_the_end",
            "plugins",
            "mods",
            "resourcepacks",
            "config",
            "libraries",
            "logs",
            "cache",
            "crash-reports",
            "world_plugin_profiles",
            "server_photos"
        )

        val rootItems = serverDir.listFiles().orEmpty()
        val hasRootWorldPayload = rootItems.any { item ->
            item.name in worldFileNames && item.name !in reservedRootNames
        }
        if (!hasRootWorldPayload) return

        android.util.Log.i("WorldImporter", "Moving root-level restored world content into ${targetRoot.name}")
        rootItems.forEach { item ->
            if (item.name !in worldFileNames) return@forEach
            if (item.name in reservedRootNames) return@forEach
            if (item.absolutePath == targetRoot.absolutePath) return@forEach
            val destination = File(targetRoot, item.name)
            when {
                item.isDirectory && destination.isDirectory -> {
                    mergeDirectoryContents(item, destination)
                    item.deleteRecursively()
                }
                item.isDirectory -> {
                    if (destination.exists()) destination.deleteRecursively()
                    if (!item.renameTo(destination)) {
                        item.copyRecursively(destination, overwrite = true)
                        item.deleteRecursively()
                    }
                }
                else -> {
                    if (destination.exists()) destination.delete()
                    if (!item.renameTo(destination)) {
                        item.copyTo(destination, overwrite = true)
                        item.delete()
                    }
                }
            }
        }
    }

}
