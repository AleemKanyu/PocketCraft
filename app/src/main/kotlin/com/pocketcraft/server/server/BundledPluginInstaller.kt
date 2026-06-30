package com.pocketcraft.server.server

import android.content.Context
import android.util.Log
import java.io.File

object BundledPluginInstaller {
    private const val TAG = "BundledPluginInstaller"

    private val BUNDLED_PLUGINS = listOf(
        "Geyser-Spigot.jar",
        "floodgate-spigot.jar",
        "ViaVersion.jar",
        "DummyPlayers.jar"
    )

    private val LEGACY_PLUGIN_NAMES = listOf(
        "Floodgate-Spigot.jar",
        "floodgate-Spigot.jar",
        "Floodgate-spigot.jar",
        "geyser-spigot.jar",
        "geyser-Spigot.jar",
        "Geyser-Spigot-latest.jar",
        "floodgate-spigot-latest.jar",
        "ViaVersion.jar.disabled",
        "ViaVersion-5.9.1.jar",
        "ViaVersion-5.10.0.jar",
        "ViaVersion-5.10.0.jar.disabled",
        "dummyplayers-1.0.0.jar",
        "dummyplayers.jar"
    )

    fun installBundledPlugins(context: Context, serverDir: File) {
        val pluginsDir = File(serverDir, "plugins").also { it.mkdirs() }
        removeLegacyPlugins(pluginsDir)
        removeOutdatedBundledPlugins(context, pluginsDir)

        BUNDLED_PLUGINS.forEach { pluginName ->
            val destFile = File(pluginsDir, pluginName)
            if (!destFile.exists()) {
                copyAssetPlugin(context, pluginName, destFile)
            }
        }
    }

    fun reinstallBundledPlugins(context: Context, serverDir: File) {
        forceReinstallBundledPlugins(context, serverDir)
    }

    fun forceReinstallBundledPlugins(context: Context, serverDir: File) {
        val pluginsDir = File(serverDir, "plugins").also { it.mkdirs() }
        removeLegacyPlugins(pluginsDir)
        BUNDLED_PLUGINS.forEach { pluginName ->
            val destFile = File(pluginsDir, pluginName)
            copyAssetPlugin(context, pluginName, destFile)
        }
    }

    private fun removeLegacyPlugins(pluginsDir: File) {
        LEGACY_PLUGIN_NAMES.forEach { name ->
            File(pluginsDir, name).takeIf { it.exists() }?.let { file ->
                if (!file.delete()) {
                    Log.w(TAG, "Could not delete legacy plugin ${file.name}")
                }
            }
        }

        val bundledNamesLower = BUNDLED_PLUGINS.map { it.lowercase() }.toSet()
        pluginsDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val nameLower = file.name.lowercase()
            if (nameLower in bundledNamesLower && file.name !in BUNDLED_PLUGINS) {
                if (!file.delete()) {
                    Log.w(TAG, "Could not delete duplicate bundled plugin ${file.name}")
                }
            }
        }
    }

    private fun removeOutdatedBundledPlugins(context: Context, pluginsDir: File) {
        BUNDLED_PLUGINS.forEach { pluginName ->
            val existingFile = File(pluginsDir, pluginName)
            if (!existingFile.exists()) return@forEach

            runCatching {
                val assetSize = context.assets.openFd("plugins/$pluginName").use { it.length }
                if (existingFile.length() != assetSize) {
                    if (!existingFile.delete()) {
                        Log.w(TAG, "Could not delete stale bundled plugin ${existingFile.name}")
                    }
                }
            }.onFailure { error ->
                Log.w(TAG, "Could not compare bundled plugin $pluginName: ${error.message}")
            }
        }
    }

    private fun copyAssetPlugin(context: Context, pluginName: String, destFile: File) {
        runCatching {
            context.assets.open("plugins/$pluginName").use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "Could not install bundled plugin $pluginName: ${error.message}")
        }
    }
}
