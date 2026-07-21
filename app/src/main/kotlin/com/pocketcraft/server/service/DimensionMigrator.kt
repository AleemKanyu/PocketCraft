package com.pocketcraft.server.service

import android.content.Context
import com.pocketcraft.server.data.model.ServerType
import java.io.File
import java.util.Locale

object DimensionMigrator {

    fun syncDimensionsForServerType(context: Context, worldName: String, serverType: ServerType) {
        val serverDir = ServerFileManager.getServerDir(context, worldName)
        val props = ServerPropertiesHelper.readProperties(serverDir)
        val levelName = props.getProperty("level-name", "world").trim().ifBlank { "world" }
        val isBukkitBased = serverType == ServerType.PAPER || serverType == ServerType.PURPUR

        val netherBestFiles = mutableMapOf<String, File>()
        val endBestFiles = mutableMapOf<String, File>()

        // 1. Deep scan serverDir (including migration-backups, legacy folders, subfolders) for all .mca region files
        if (serverDir.exists() && serverDir.isDirectory) {
            serverDir.walkTopDown().maxDepth(6).forEach { file ->
                if (file.isFile && file.name.lowercase(Locale.getDefault()).endsWith(".mca")) {
                    val pathLower = file.absolutePath.lowercase(Locale.getDefault())
                    val isNether = pathLower.contains("dim-1") || pathLower.contains("_nether") || pathLower.contains("/nether/")
                    val isEnd = pathLower.contains("dim1") || pathLower.contains("_the_end") || pathLower.contains("/end/")

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

        // Also check legacy version directories if available (e.g. context.filesDir/servers/*)
        val parentServersDir = serverDir.parentFile?.parentFile
        if (parentServersDir != null && parentServersDir.isDirectory) {
            parentServersDir.walkTopDown().maxDepth(5).forEach { file ->
                if (file.isFile && file.name.lowercase(Locale.getDefault()).endsWith(".mca")) {
                    val pathLower = file.absolutePath.lowercase(Locale.getDefault())
                    val isNether = pathLower.contains("dim-1") || pathLower.contains("_nether") || pathLower.contains("/nether/")
                    val isEnd = pathLower.contains("dim1") || pathLower.contains("_the_end") || pathLower.contains("/end/")

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

        // 2. Define targets for active server type
        val rootLevelDir = File(serverDir, levelName)
        val bukkitNetherRoot = File(serverDir, "${levelName}_nether")
        val bukkitEndRoot = File(serverDir, "${levelName}_the_end")

        val netherTargets = if (isBukkitBased) {
            listOf(
                File(bukkitNetherRoot, "DIM-1/region"),
                File(bukkitNetherRoot, "region"),
                File(rootLevelDir, "DIM-1/region")
            )
        } else {
            listOf(
                File(rootLevelDir, "DIM-1/region"),
                File(bukkitNetherRoot, "DIM-1/region")
            )
        }

        val endTargets = if (isBukkitBased) {
            listOf(
                File(bukkitEndRoot, "DIM1/region"),
                File(bukkitEndRoot, "region"),
                File(rootLevelDir, "DIM1/region")
            )
        } else {
            listOf(
                File(rootLevelDir, "DIM1/region"),
                File(bukkitEndRoot, "DIM1/region")
            )
        }

        // 3. Deploy best region files into target locations
        deployBestRegionFiles(netherBestFiles, netherTargets)
        deployBestRegionFiles(endBestFiles, endTargets)

        // 4. Also sync non-region data subfolders (entities, poi, data)
        syncSubfolderContents(serverDir, levelName, isBukkitBased, "entities")
        syncSubfolderContents(serverDir, levelName, isBukkitBased, "poi")
        syncSubfolderContents(serverDir, levelName, isBukkitBased, "data")
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

    private fun syncSubfolderContents(serverDir: File, levelName: String, isBukkitBased: Boolean, subFolderName: String) {
        val rootLevelDir = File(serverDir, levelName)
        val bukkitNetherRoot = File(serverDir, "${levelName}_nether")
        val bukkitEndRoot = File(serverDir, "${levelName}_the_end")

        val netherSources = listOf(
            File(bukkitNetherRoot, "DIM-1/$subFolderName"),
            File(bukkitNetherRoot, subFolderName),
            File(rootLevelDir, "DIM-1/$subFolderName")
        )
        val netherTargets = if (isBukkitBased) {
            listOf(File(bukkitNetherRoot, "DIM-1/$subFolderName"), File(bukkitNetherRoot, subFolderName))
        } else {
            listOf(File(rootLevelDir, "DIM-1/$subFolderName"))
        }

        syncDirectoryFiles(netherSources, netherTargets)

        val endSources = listOf(
            File(bukkitEndRoot, "DIM1/$subFolderName"),
            File(bukkitEndRoot, subFolderName),
            File(rootLevelDir, "DIM1/$subFolderName")
        )
        val endTargets = if (isBukkitBased) {
            listOf(File(bukkitEndRoot, "DIM1/$subFolderName"), File(bukkitEndRoot, subFolderName))
        } else {
            listOf(File(rootLevelDir, "DIM1/$subFolderName"))
        }

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
