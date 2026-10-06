package com.pockethost.app.service

import android.content.Context
import com.pockethost.app.data.model.ServerType
import java.io.File
import java.util.Locale

object DimensionMigrator {

    fun syncDimensionsForServerType(context: Context, worldName: String, serverType: ServerType) {
        val serverDir = ServerFileManager.getServerDir(context, worldName)
        val props = ServerPropertiesHelper.readProperties(serverDir)
        val levelName = props.getProperty("level-name", "world").trim().ifBlank { "world" }

        val netherBestFiles = mutableMapOf<String, File>()
        val endBestFiles = mutableMapOf<String, File>()

        // 1. Deep scan serverDir (including migration-backups, legacy folders, subfolders) for all .mca region files
        if (serverDir.exists() && serverDir.isDirectory) {
            serverDir.walkTopDown().maxDepth(6).forEach { file ->
                if (file.isFile && file.name.lowercase(Locale.getDefault()).endsWith(".mca")) {
                    val pathLower = file.absolutePath.lowercase(Locale.getDefault())
                    val isNether = pathLower.contains("dim-1") || pathLower.contains("_nether") || pathLower.contains("/the_nether/") || pathLower.contains("/nether/")
                    val isEnd = pathLower.contains("dim1") || pathLower.contains("_the_end") || pathLower.contains("/the_end/") || pathLower.contains("/end/")

                    if (isNether) {
                        val currentBest = netherBestFiles[file.name]
                        if (currentBest == null || file.length() > currentBest.length()) {
                            netherBestFiles[file.name] = file
                        }
                    } else if (isEnd) {
                        val currentBest = endBestFiles[file.name]
                        if (currentBest == null || file.length() > currentBest.length()) {
                            endBestFiles[file.name] = file
                        }
                    }
                }
            }
        }

        // Also check legacy version directories if available (e.g. context.filesDir/servers/*).
        // The other worlds live under servers/worlds/ and are never a source: region files are
        // matched by name alone, so their Nether and End would be copied into this world.
        val parentServersDir = serverDir.parentFile?.parentFile
        val otherWorldsDir = serverDir.parentFile
        if (parentServersDir != null && parentServersDir.isDirectory) {
            parentServersDir.walkTopDown().onEnter { it != otherWorldsDir }.maxDepth(5).forEach { file ->
                if (file.isFile && file.name.lowercase(Locale.getDefault()).endsWith(".mca")) {
                    val pathLower = file.absolutePath.lowercase(Locale.getDefault())
                    val isNether = pathLower.contains("dim-1") || pathLower.contains("_nether") || pathLower.contains("/the_nether/") || pathLower.contains("/nether/")
                    val isEnd = pathLower.contains("dim1") || pathLower.contains("_the_end") || pathLower.contains("/the_end/") || pathLower.contains("/end/")

                    if (isNether) {
                        val currentBest = netherBestFiles[file.name]
                        if (currentBest == null || file.length() > currentBest.length()) {
                            netherBestFiles[file.name] = file
                        }
                    } else if (isEnd) {
                        val currentBest = endBestFiles[file.name]
                        if (currentBest == null || file.length() > currentBest.length()) {
                            endBestFiles[file.name] = file
                        }
                    }
                }
            }
        }

        // 2. Define targets in the primary unified world directory
        val rootLevelDir = File(serverDir, levelName)
        val bukkitNetherRoot = File(serverDir, "${levelName}_nether")
        val bukkitEndRoot = File(serverDir, "${levelName}_the_end")

        // A Minecraft 26.1+ world keeps its dimensions under dimensions/ for every server type.
        // Writing the DIM-1/DIM1 copies as well only doubled the size of the Nether and the End.
        val splitLayout = com.pockethost.app.server.usesSplitWorldLayout(rootLevelDir)

        val netherTargets = listOfNotNull(
            File(rootLevelDir, "DIM-1/region").takeUnless { splitLayout },
            File(rootLevelDir, "dimensions/minecraft/the_nether/region")
        )

        val endTargets = listOfNotNull(
            File(rootLevelDir, "DIM1/region").takeUnless { splitLayout },
            File(rootLevelDir, "dimensions/minecraft/the_end/region")
        )

        // 3. Deploy best region files into target locations
        deployBestRegionFiles(netherBestFiles, netherTargets)
        deployBestRegionFiles(endBestFiles, endTargets)

        // 4. Also sync non-region data subfolders (entities, poi, data)
        syncSubfolderContents(serverDir, levelName, "entities", splitLayout)
        syncSubfolderContents(serverDir, levelName, "poi", splitLayout)
        syncSubfolderContents(serverDir, levelName, "data", splitLayout)

        // 5. Clean up legacy Bukkit sibling folders (_nether, _the_end) so Paper/Purpur 1.20+
        // migration won't throw java.io.IOException: Refusing to overwrite existing migrated file
        if (bukkitNetherRoot.exists()) {
            runCatching { bukkitNetherRoot.deleteRecursively() }
        }
        if (bukkitEndRoot.exists()) {
            runCatching { bukkitEndRoot.deleteRecursively() }
        }

        // In a 26.1+ world the old-style folders have just been merged into dimensions/ and
        // nothing reads them again.
        if (splitLayout) {
            listOf("DIM-1", "DIM1").map { File(rootLevelDir, it) }.filter { it.exists() }.forEach {
                runCatching { it.deleteRecursively() }
            }
        }
    }

    private fun deployBestRegionFiles(bestFiles: Map<String, File>, targets: List<File>) {
        if (bestFiles.isEmpty()) return
        targets.forEach { targetDir ->
            if (!targetDir.exists()) targetDir.mkdirs()
            bestFiles.forEach { (fileName, bestFile) ->
                val targetFile = File(targetDir, fileName)
                // Overwrite if target is missing, smaller than bestFile, or is a dummy file (< 12KB) while bestFile is larger
                if (!targetFile.exists() || targetFile.length() < bestFile.length() || (targetFile.length() < 12288L && bestFile.length() > targetFile.length())) {
                    runCatching {
                        bestFile.copyTo(targetFile, overwrite = true)
                    }
                }
            }
        }
    }

    private fun syncSubfolderContents(serverDir: File, levelName: String, subFolderName: String, splitLayout: Boolean) {
        val rootLevelDir = File(serverDir, levelName)
        val bukkitNetherRoot = File(serverDir, "${levelName}_nether")
        val bukkitEndRoot = File(serverDir, "${levelName}_the_end")

        val netherSources = listOf(
            File(bukkitNetherRoot, "DIM-1/$subFolderName"),
            File(bukkitNetherRoot, subFolderName),
            File(rootLevelDir, "DIM-1/$subFolderName"),
            File(rootLevelDir, "dimensions/minecraft/the_nether/$subFolderName")
        )
        val netherTargets = listOfNotNull(
            File(rootLevelDir, "DIM-1/$subFolderName").takeUnless { splitLayout },
            File(rootLevelDir, "dimensions/minecraft/the_nether/$subFolderName")
        )

        syncDirectoryFiles(netherSources, netherTargets)

        val endSources = listOf(
            File(bukkitEndRoot, "DIM1/$subFolderName"),
            File(bukkitEndRoot, subFolderName),
            File(rootLevelDir, "DIM1/$subFolderName"),
            File(rootLevelDir, "dimensions/minecraft/the_end/$subFolderName")
        )
        val endTargets = listOfNotNull(
            File(rootLevelDir, "DIM1/$subFolderName").takeUnless { splitLayout },
            File(rootLevelDir, "dimensions/minecraft/the_end/$subFolderName")
        )

        syncDirectoryFiles(endSources, endTargets)
    }

    private fun syncDirectoryFiles(sources: List<File>, targets: List<File>) {
        val existingSources = sources.filter { it.exists() && it.isDirectory }
        if (existingSources.isEmpty()) return

        val bestFiles = mutableMapOf<String, File>()
        existingSources.forEach { sourceDir ->
            sourceDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val currentBest = bestFiles[file.name]
                    if (currentBest == null || file.length() > currentBest.length()) {
                        bestFiles[file.name] = file
                    }
                }
            }
        }

        if (bestFiles.isEmpty()) return

        targets.forEach { targetDir ->
            if (!targetDir.exists()) targetDir.mkdirs()
            bestFiles.forEach { (fileName, bestFile) ->
                val targetFile = File(targetDir, fileName)
                if (!targetFile.exists() || targetFile.length() < bestFile.length()) {
                    runCatching {
                        bestFile.copyTo(targetFile, overwrite = true)
                    }
                }
            }
        }
    }
}
