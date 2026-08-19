package com.pockethost.app.server

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

        val propsFile = File(serverDir, "server.properties")
        val version = if (propsFile.exists()) {
            val props = java.util.Properties().apply { runCatching { load(propsFile.inputStream()) } }
            props.getProperty("pocketcraft-game-version", "1.21.1")
        } else {
            "1.21.1"
        }
        val minor = com.pockethost.app.setup.JreExtractor.parseMinecraftMinor(version) ?: 21
        val isPre112 = minor < 12

        BUNDLED_PLUGINS.forEach { pluginName ->
            val isBridgePlugin = pluginName.startsWith("Geyser", ignoreCase = true) || pluginName.startsWith("floodgate", ignoreCase = true)
            if (isPre112 && isBridgePlugin) {
                // Geyser & Floodgate do not support Minecraft versions < 1.12.2
                val destFile = File(pluginsDir, pluginName)
                if (destFile.exists()) {
                    destFile.renameTo(File(pluginsDir, "$pluginName.disabled"))
                }
                return@forEach
            }

            val destFile = File(pluginsDir, pluginName)
            val disabledFile = File(pluginsDir, "$pluginName.disabled")
            if (!disabledFile.exists() && (!destFile.exists() || !isZipValid(destFile))) {
                copyAssetPlugin(context, pluginName, destFile)
            }
        }
    }

    fun isZipValid(file: File): Boolean {
        if (!file.exists() || file.length() < 1000L) return false
        return try {
            java.util.zip.ZipFile(file).use { it.size() > 0 }
        } catch (e: Throwable) {
            false
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
        val prefs = context.getSharedPreferences("bundled_plugins_meta", Context.MODE_PRIVATE)
        val lastInstalledVersion = prefs.getInt("installed_app_version_code", -1)
        val currentVersion = try {
            com.pockethost.app.BuildConfig.VERSION_CODE
        } catch (e: Throwable) {
            1
        }

        val appUpdated = lastInstalledVersion != currentVersion

        BUNDLED_PLUGINS.forEach { pluginName ->
            val existingFile = File(pluginsDir, pluginName)
            val disabledFile = File(pluginsDir, "$pluginName.disabled")
            val targetFile = when {
                existingFile.exists() -> existingFile
                disabledFile.exists() -> disabledFile
                else -> return@forEach
            }

            // Only delete if app was updated to a new version, or if the ZIP is corrupted on disk
            if (appUpdated || !isZipValid(targetFile)) {
                if (!targetFile.delete()) {
                    Log.w(TAG, "Could not delete stale/corrupted bundled plugin ${targetFile.name}")
                }
            }
        }

        if (appUpdated) {
            prefs.edit().putInt("installed_app_version_code", currentVersion).apply()
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
