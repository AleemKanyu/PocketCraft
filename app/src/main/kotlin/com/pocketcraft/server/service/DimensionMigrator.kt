package com.pocketcraft.server.service

import android.content.Context
import com.pocketcraft.server.data.model.ServerType
import com.pocketcraft.server.data.repository.ServerConfigRepository
import java.io.File

object DimensionMigrator {

    fun syncDimensionsForServerType(context: Context, worldName: String, serverType: ServerType) {
        val serverDir = ServerFileManager.getServerDir(context, worldName)
        val props = ServerPropertiesHelper.readProperties(serverDir)
        val levelName = props.getProperty("level-name", "world")

        val rootLevelDir = File(serverDir, levelName)
        if (!rootLevelDir.exists()) return

        val isBukkitBased = serverType == ServerType.PAPER || serverType == ServerType.PURPUR

        if (isBukkitBased) {
            // Move DIM-1 and DIM1 contents out to levelName_nether and levelName_the_end
            val fabricNether = File(rootLevelDir, "DIM-1")
            val bukkitNetherRoot = File(serverDir, "${levelName}_nether")
            
            if (fabricNether.exists() && fabricNether.isDirectory) {
                val targetNested = File(bukkitNetherRoot, "DIM-1")
                moveContents(fabricNether, targetNested)
            }

            // Convert any flat structure (region/ at root of world_nether) down to DIM-1/
            val flatNetherRegion = File(bukkitNetherRoot, "region")
            if (flatNetherRegion.exists() && flatNetherRegion.isDirectory) {
                val targetNested = File(bukkitNetherRoot, "DIM-1")
                listOf("region", "entities", "poi", "data").forEach { folder ->
                    val src = File(bukkitNetherRoot, folder)
                    if (src.exists() && src.isDirectory) {
                        copyOrMoveDir(src, File(targetNested, folder))
                    }
                }
            }

            val fabricEnd = File(rootLevelDir, "DIM1")
            val bukkitEndRoot = File(serverDir, "${levelName}_the_end")

            if (fabricEnd.exists() && fabricEnd.isDirectory) {
                val targetNested = File(bukkitEndRoot, "DIM1")
                moveContents(fabricEnd, targetNested)
            }

            // Convert any flat structure (region/ at root of world_the_end) down to DIM1/
            val flatEndRegion = File(bukkitEndRoot, "region")
            if (flatEndRegion.exists() && flatEndRegion.isDirectory) {
                val targetNested = File(bukkitEndRoot, "DIM1")
                listOf("region", "entities", "poi", "data").forEach { folder ->
                    val src = File(bukkitEndRoot, folder)
                    if (src.exists() && src.isDirectory) {
                        copyOrMoveDir(src, File(targetNested, folder))
                    }
                }
            }
        } else {
            // Fabric / Custom JAR: Move levelName_nether contents inside levelName/DIM-1 and levelName_the_end inside levelName/DIM1
            val bukkitNetherRoot = File(serverDir, "${levelName}_nether")
            val fabricNether = File(rootLevelDir, "DIM-1")

            if (bukkitNetherRoot.exists() && bukkitNetherRoot.isDirectory) {
                val flatNetherRegion = File(bukkitNetherRoot, "region")
                if (flatNetherRegion.exists() && flatNetherRegion.isDirectory) {
                    val nestedNether = File(bukkitNetherRoot, "DIM-1")
                    listOf("region", "entities", "poi", "data").forEach { folder ->
                        val src = File(bukkitNetherRoot, folder)
                        if (src.exists() && src.isDirectory) {
                            copyOrMoveDir(src, File(nestedNether, folder))
                        }
                    }
                }
                val nestedNether = File(bukkitNetherRoot, "DIM-1")
                if (nestedNether.exists() && nestedNether.isDirectory) {
                    moveContents(nestedNether, fabricNether)
                }
            }

            val bukkitEndRoot = File(serverDir, "${levelName}_the_end")
            val fabricEnd = File(rootLevelDir, "DIM1")

            if (bukkitEndRoot.exists() && bukkitEndRoot.isDirectory) {
                val flatEndRegion = File(bukkitEndRoot, "region")
                if (flatEndRegion.exists() && flatEndRegion.isDirectory) {
                    val nestedEnd = File(bukkitEndRoot, "DIM1")
                    listOf("region", "entities", "poi", "data").forEach { folder ->
                        val src = File(bukkitEndRoot, folder)
                        if (src.exists() && src.isDirectory) {
                            copyOrMoveDir(src, File(nestedEnd, folder))
                        }
                    }
                }
                val nestedEnd = File(bukkitEndRoot, "DIM1")
                if (nestedEnd.exists() && nestedEnd.isDirectory) {
                    moveContents(nestedEnd, fabricEnd)
                }
            }
        }
    }

    private fun moveContents(sourceDir: File, targetDir: File) {
        if (!sourceDir.exists() || !sourceDir.isDirectory) return
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        sourceDir.listFiles()?.forEach { file ->
            val destFile = File(targetDir, file.name)
            copyOrMoveDir(file, destFile)
        }
        sourceDir.deleteRecursively()
    }

    private fun copyOrMoveDir(source: File, target: File) {
        if (target.exists()) {
            if (source.isDirectory && target.isDirectory) {
                // Merge contents recursively
                source.listFiles()?.forEach { file ->
                    copyOrMoveDir(file, File(target, file.name))
                }
                source.deleteRecursively()
                return
            } else {
                target.deleteRecursively()
            }
        }
        if (!target.parentFile!!.exists()) {
            target.parentFile!!.mkdirs()
        }
        if (source.renameTo(target)) {
            return
        }
        runCatching {
            source.copyRecursively(target, overwrite = true)
            source.deleteRecursively()
        }
    }
}
