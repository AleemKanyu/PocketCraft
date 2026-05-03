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
            // Move DIM-1 and DIM1 out to levelName_nether and levelName_the_end
            val fabricNether = File(rootLevelDir, "DIM-1")
            val bukkitNether = File(serverDir, "${levelName}_nether/DIM-1")
            val bukkitNetherRoot = File(serverDir, "${levelName}_nether")
            
            if (fabricNether.exists() && fabricNether.isDirectory) {
                if (!bukkitNetherRoot.exists()) bukkitNetherRoot.mkdirs()
                copyOrMoveDir(fabricNether, bukkitNether)
            }

            val fabricEnd = File(rootLevelDir, "DIM1")
            val bukkitEnd = File(serverDir, "${levelName}_the_end/DIM1")
            val bukkitEndRoot = File(serverDir, "${levelName}_the_end")

            if (fabricEnd.exists() && fabricEnd.isDirectory) {
                if (!bukkitEndRoot.exists()) bukkitEndRoot.mkdirs()
                copyOrMoveDir(fabricEnd, bukkitEnd)
            }
        } else {
            // Fabric / Custom JAR: Move levelName_nether/DIM-1 and levelName_the_end/DIM1 inside levelName
            val bukkitNether = File(serverDir, "${levelName}_nether/DIM-1")
            val fabricNether = File(rootLevelDir, "DIM-1")

            if (bukkitNether.exists() && bukkitNether.isDirectory && !fabricNether.exists()) {
                copyOrMoveDir(bukkitNether, fabricNether)
            }

            val bukkitEnd = File(serverDir, "${levelName}_the_end/DIM1")
            val fabricEnd = File(rootLevelDir, "DIM1")

            if (bukkitEnd.exists() && bukkitEnd.isDirectory && !fabricEnd.exists()) {
                copyOrMoveDir(bukkitEnd, fabricEnd)
            }
        }
    }

    private fun copyOrMoveDir(source: File, target: File) {
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
