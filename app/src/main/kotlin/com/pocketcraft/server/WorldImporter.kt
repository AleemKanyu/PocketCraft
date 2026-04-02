package com.pocketcraft.server

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object WorldImporter {

    suspend fun importWorld(
        context: Context,
        zipUri: Uri,
        serverVersion: String,
        folderName: String = "world"
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

                val worldDir = File(context.filesDir, "servers/$serverVersion/$folderName")

                // Step 2: Delete existing world
                if (worldDir.exists()) {
                    android.util.Log.d("WorldImporter", "Deleting existing world at ${worldDir.absolutePath}")
                    worldDir.deleteRecursively()
                }
                worldDir.mkdirs()

                // Step 3: Extract from local file using ZipFile (more robust than ZipInputStream)
                android.util.Log.i("WorldImporter", "Extracting world zip: ${tempFile.length()} bytes")
                java.util.zip.ZipFile(tempFile).use { zip ->
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
                    }
                }

                android.util.Log.i("WorldImporter", "Extraction complete. Checking structure...")

                // Step 4: Fix nested structure (Aternos often zips a folder)
                // We look for 'level.dat' to identify the real world root
                fixWorldStructure(worldDir)

                Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.e("WorldImporter", "Import failed", e)
                Result.failure(e)
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }

    private fun fixWorldStructure(root: File) {
        // Find level.dat anywhere in the structure (up to 2 levels deep)
        val levelDat = root.walkTopDown().maxDepth(3).find { it.name == "level.dat" } ?: return
        val realRoot = levelDat.parentFile ?: return
        
        if (realRoot != root) {
            android.util.Log.i("WorldImporter", "Detected nested world root at ${realRoot.name}. Moving files...")
            realRoot.listFiles()?.forEach { file ->
                val target = File(root, file.name)
                if (target.exists()) target.deleteRecursively()
                file.renameTo(target)
            }
            // Cleanup empty nested folder
            realRoot.delete()
        }
    }

}
