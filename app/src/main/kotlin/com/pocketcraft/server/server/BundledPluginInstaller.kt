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
        "PocketCraftCompanion.jar",
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
        "ViaVersion-5.10.0.jar.disabled"
    )

    fun installBundledPlugins(context: Context, serverDir: File) {
        val pluginsDir = File(serverDir, "plugins").also { it.mkdirs() }
        removeLegacyPlugins(pluginsDir)
        removeOutdatedBundledPlugins(context, pluginsDir)

        BUNDLED_PLUGINS.forEach { pluginName ->
            val destFile = File(pluginsDir, pluginName)
            val disabledFile = File(pluginsDir, "$pluginName.disabled")
            if (!destFile.exists() && !disabledFile.exists()) {
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
            val disabledFile = File(pluginsDir, "$pluginName.disabled")
            if (!disabledFile.exists()) {
                copyAssetPlugin(context, pluginName, destFile)
            }
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
            val nameLower = file.name.lowercase().removeSuffix(".disabled")
            if (nameLower in bundledNamesLower && file.name !in BUNDLED_PLUGINS && !file.name.endsWith(".disabled")) {
                if (!file.delete()) {
                    Log.w(TAG, "Could not delete duplicate bundled plugin ${file.name}")
                }
            }
        }
    }

    private fun removeOutdatedBundledPlugins(context: Context, pluginsDir: File) {
        BUNDLED_PLUGINS.forEach { pluginName ->
            val existingFile = File(pluginsDir, pluginName)
            val disabledFile = File(pluginsDir, "$pluginName.disabled")
            val targetFile = when {
                existingFile.exists() -> existingFile
                disabledFile.exists() -> disabledFile
                else -> return@forEach
            }

            runCatching {
                val assetPath = if (pluginName == "PocketCraftCompanion.jar") "default_plugins/$pluginName" else "plugins/$pluginName"
                val assetSize = context.assets.open(assetPath).use { input ->
                    var size = 0L
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        size += read
                    }
                    size
                }
                if (targetFile.length() != assetSize) {
                    if (!targetFile.delete()) {
                        Log.w(TAG, "Could not delete stale bundled plugin ${targetFile.name}")
                    }
                }
            }.onFailure { error ->
                Log.w(TAG, "Could not compare bundled plugin $pluginName: ${error.message}")
            }
        }
    }

    private fun copyAssetPlugin(context: Context, pluginName: String, destFile: File) {
        runCatching {
            val assetPath = if (pluginName == "PocketCraftCompanion.jar") "default_plugins/$pluginName" else "plugins/$pluginName"
            context.assets.open(assetPath).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "Could not install bundled plugin $pluginName: ${error.message}")
        }
    }
}
