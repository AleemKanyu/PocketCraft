package com.pocketcraft.server

import android.content.Context
import android.net.Uri
import com.pocketcraft.server.service.PlayerDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

object WorldImporter {

    suspend fun importWorld(
        context: Context,
        zipUri: Uri,
        serverVersion: String,
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

                val serverDir = File(context.filesDir, "servers/$serverVersion")
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
                        val entryFile = File(worldDir, entry.name)

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
                fixWorldStructureAndMigrate(serverDir, worldDir, folderName)

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

    private fun fixWorldStructureAndMigrate(serverDir: File, root: File, folderName: String) {
        android.util.Log.d("WorldImporter", "Scanning root: ${root.absolutePath}")
        
        // 1. Detect if this is a full server backup (contains server.properties)
        // We look for server.properties anywhere in the extracted files
        val internalProps = root.walkTopDown().maxDepth(4).find { it.name == "server.properties" }
        if (internalProps != null) {
            val baseDir = internalProps.parentFile ?: root
            android.util.Log.i("WorldImporter", "Detected full server backup at ${baseDir.absolutePath}. Migrating everything...")
            
            val props = java.util.Properties().apply { 
                runCatching { internalProps.inputStream().use { load(it) } }
            }
            val internalWorldName = props.getProperty("level-name", "world")
            
            // Move ALL files and directories from baseDir up to serverDir
            // EXCEPT for the world folder which we handle specially
            baseDir.listFiles()?.forEach { file ->
                val target = if (file.name == internalWorldName || file.name == "world" || file.name == folderName) {
                    root // This is the main world, move its CONTENTS into root
                } else {
                    File(serverDir, file.name)
                }
                
                if (target.absolutePath == root.absolutePath && file.isDirectory) {
                    // Flatten world contents into root
                    android.util.Log.i("WorldImporter", "Merging world folder ${file.name} into ${root.name}")
                    file.listFiles()?.forEach { sub ->
                        val subTarget = File(root, sub.name)
                        if (sub.isDirectory) {
                            if (subTarget.exists() && subTarget.isDirectory) {
                                // Merge sub-directories (e.g. stats, playerdata)
                                sub.listFiles()?.forEach { grandChild ->
                                    val finalTarget = File(subTarget, grandChild.name)
                                    if (finalTarget.exists()) {
                                        // If both exist, keep the larger one (more stats/data)
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
                                // Target doesn't exist or is a file, just move/replace
                                if (subTarget.exists()) subTarget.deleteRecursively()
                                sub.renameTo(subTarget)
                            }
                        } else {
                            // It's a file
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
                    // Move other files/dirs up to server root
                    android.util.Log.d("WorldImporter", "Migrating server file/dir: ${file.name} -> ${target.absolutePath}")
                    if (target.exists()) target.deleteRecursively()
                    if (!file.renameTo(target)) {
                        file.copyTo(target, overwrite = true)
                        file.deleteRecursively()
                    }
                    
                    // Special case: if we just migrated server.properties, ensure it points to our new world name
                    if (file.name == "server.properties") {
                        runCatching {
                            val lines = target.readLines().toMutableList()
                            val levelIdx = lines.indexOfFirst { it.startsWith("level-name=") }
                            if (levelIdx != -1) {
                                lines[levelIdx] = "level-name=$folderName"
                            } else {
                                lines.add("level-name=$folderName")
                            }
                            target.writeText(lines.joinToString("\n"))
                        }
                    }
                }
            }
        }

        // 2. Fallback: Find level.dat to flatten nested world if not already handled
        // Search again because Step 1 might have moved things around
        val levelDat = root.walkTopDown().maxDepth(6).find { it.name == "level.dat" }
        android.util.Log.d("WorldImporter", "Post-migration level.dat search found: ${levelDat?.absolutePath}")
        
        if (levelDat == null) return
        var realRoot = levelDat.parentFile ?: return

        // 2. Ensure server.properties in root matches our new world name if not already updated
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

        // 3. Flatten the world folder if it's nested (e.g. world/world/level.dat)
        if (realRoot.absolutePath != root.absolutePath) {
            android.util.Log.i("WorldImporter", "Detected nested world root at ${realRoot.absolutePath}. Flattening to ${root.absolutePath}")
            
            // Move ALL children of the real root to the designated folder root
            realRoot.listFiles()?.forEach { file ->
                val target = File(root, file.name)
                if (target.exists()) target.deleteRecursively()
                if (!file.renameTo(target)) {
                    file.copyTo(target, overwrite = true)
                    file.deleteRecursively()
                }
            }
            
            // 3. Check for dimension siblings (Aternos often has world, world_nether, world_the_end together)
            // If we found the world root nested, its siblings might be the dimensions
            val parent = realRoot.parentFile
            if (parent != null && parent.absolutePath != serverDir.absolutePath && parent.absolutePath != root.absolutePath) {
                parent.listFiles()?.forEach { sibling ->
                    if (sibling.isDirectory && sibling != realRoot) {
                        if (File(sibling, "level.dat").exists() || File(sibling, "region").isDirectory) {
                            val target = File(serverDir, sibling.name)
                            if (!target.exists()) {
                                android.util.Log.i("WorldImporter", "Migrating dimension sibling: ${sibling.name}")
                                sibling.renameTo(target)
                            }
                        }
                    }
                }
            }

            // Cleanup the now-empty nested folders
            realRoot.delete()
            // Try to delete intermediate empty folders up to the root
            var current = realRoot.parentFile
            while (current != null && current.absolutePath != root.absolutePath && current.listFiles()?.isEmpty() == true) {
                val toDelete = current
                current = current.parentFile
                toDelete.delete()
            }
        }
    }

}
