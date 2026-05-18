package com.pocketcraft.server.service

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

object ServerVersionMigrator {

    data class MigrationResult(
        val migratedWorldData: Boolean,
        val sourceWorldPath: String?
    )

    data class LegacyStorageMigrationResult(
        val migrated: Boolean,
        val sourceDir: String?,
        val backupDir: String?
    )

    fun migrateLegacyVersionStorageIfNeeded(
        context: Context,
        versionId: String,
        worldName: String
    ): LegacyStorageMigrationResult {
        val normalizedWorld = sanitizeWorldName(worldName)
        if (versionId.isBlank() || normalizedWorld.isBlank()) {
            return LegacyStorageMigrationResult(migrated = false, sourceDir = null, backupDir = null)
        }

        val legacyDir = File(context.filesDir, "servers/$versionId")
        if (!legacyDir.isDirectory) {
            return LegacyStorageMigrationResult(migrated = false, sourceDir = null, backupDir = null)
        }

        val targetDir = ServerFileManager.getServerDir(context, normalizedWorld)
        if (legacyDir.absolutePath == targetDir.absolutePath) {
            return LegacyStorageMigrationResult(migrated = false, sourceDir = null, backupDir = null)
        }

        val sourceProps = ServerPropertiesHelper.readProperties(legacyDir)
        val targetProps = ServerPropertiesHelper.readProperties(targetDir)
        val sourceLevelName = sanitizeWorldName(
            sourceProps.getProperty("level-name")
                ?.takeIf { it.isNotBlank() }
                ?: normalizedWorld
        )
        val targetLevelName = sanitizeWorldName(
            targetProps.getProperty("level-name")
                ?.takeIf { it.isNotBlank() }
                ?: sourceLevelName
        )

        val sourceWorldRoot = findWorldRoot(legacyDir, sourceLevelName) ?: findWorldRoot(legacyDir, "world")
        if (sourceWorldRoot == null) {
            return LegacyStorageMigrationResult(migrated = false, sourceDir = null, backupDir = null)
        }

        val marker = File(targetDir, ".legacy_storage_migrated_$versionId")
        if (marker.exists()) {
            return LegacyStorageMigrationResult(migrated = false, sourceDir = sourceWorldRoot.absolutePath, backupDir = null)
        }

        val targetWorlds = worldCandidates(targetDir, targetLevelName)
        var backupDir: File? = null
        if (targetWorlds.any(::containsWorldData)) {
            backupDir = File(
                targetDir,
                "migration-backups/legacy-$versionId-${System.currentTimeMillis()}"
            ).also { it.mkdirs() }
            targetWorlds.forEach { target ->
                if (target.exists()) {
                    moveFileOrDirectory(target, File(backupDir, target.name))
                }
            }
        }

        val sourceWorldBaseName = sourceWorldRoot.name
        val sourceCandidates = listOf(
            sourceWorldRoot to File(targetDir, targetLevelName),
            File(legacyDir, "${sourceWorldBaseName}_nether") to File(targetDir, "${targetLevelName}_nether"),
            File(legacyDir, "${sourceWorldBaseName}_the_end") to File(targetDir, "${targetLevelName}_the_end")
        )
        var migratedWorld = false
        sourceCandidates.forEach { (source, target) ->
            if (source.exists() && !target.exists() && moveFileOrDirectory(source, target)) {
                migratedWorld = true
            }
        }

        migrateLegacyServerFiles(legacyDir, targetDir)

        val mergedProps = java.util.Properties().apply {
            putAll(targetProps)
            putAll(sourceProps)
            setProperty("level-name", targetLevelName)
            setProperty(
                "pocketcraft-world-list",
                (readKnownWorlds(sourceProps, targetLevelName) +
                    readKnownWorlds(targetProps, targetLevelName) +
                    targetLevelName)
                    .distinctBy { it.lowercase(Locale.getDefault()) }
                    .joinToString(",")
            )
        }
        ServerPropertiesHelper.saveProperties(targetDir, mergedProps)
        runCatching { marker.writeText(sourceWorldRoot.absolutePath) }

        return LegacyStorageMigrationResult(
            migrated = migratedWorld,
            sourceDir = sourceWorldRoot.absolutePath,
            backupDir = backupDir?.absolutePath
        )
    }

    fun migrateActiveWorldIfNeeded(
        context: Context,
        fromVersionId: String,
        toVersionId: String,
        worldName: String
    ): MigrationResult {
        val normalizedWorld = sanitizeWorldName(worldName)
        if (normalizedWorld.isBlank() || fromVersionId == toVersionId) {
            return MigrationResult(migratedWorldData = false, sourceWorldPath = null)
        }

        val sourceDir = File(context.filesDir, "servers/$fromVersionId")
        if (!sourceDir.exists()) {
            return MigrationResult(migratedWorldData = false, sourceWorldPath = null)
        }

        val targetDir = File(context.filesDir, "servers/$toVersionId").also { it.mkdirs() }
        val sourceProps = ServerPropertiesHelper.readProperties(sourceDir)
        val targetProps = ServerPropertiesHelper.readProperties(targetDir)

        val sourceWorlds = worldCandidates(sourceDir, normalizedWorld)
        val targetWorlds = worldCandidates(targetDir, normalizedWorld)
        val targetAlreadyHasWorld = targetWorlds.any(File::exists)
        var migratedAnyWorldData = false

        if (!targetAlreadyHasWorld) {
            sourceWorlds.zip(targetWorlds).forEach { (source, target) ->
                if (!source.exists() || target.exists()) return@forEach
                if (moveFileOrDirectory(source, target)) {
                    migratedAnyWorldData = true
                }
            }
        }

        migrateWorldPluginProfile(sourceDir, targetDir, normalizedWorld)
        mergeNamedPlayerList(sourceDir, targetDir, "ops.json")
        mergeNamedPlayerList(sourceDir, targetDir, "whitelist.json")
        mergeNamedPlayerList(sourceDir, targetDir, "banned-players.json")
        mergeUserCache(sourceDir, targetDir)
        mergeWorldMetadata(sourceDir, targetDir, sourceProps, targetProps, normalizedWorld, fromVersionId, toVersionId)

        return MigrationResult(
            migratedWorldData = migratedAnyWorldData,
            sourceWorldPath = sourceWorlds.firstOrNull(File::exists)?.absolutePath
        )
    }

    private fun mergeWorldMetadata(
        sourceDir: File,
        targetDir: File,
        sourceProps: java.util.Properties,
        targetProps: java.util.Properties,
        worldName: String,
        fromVersionId: String,
        toVersionId: String
    ) {
        targetProps["level-name"] = worldName
        targetProps["pocketcraft-world-list"] = (
            readKnownWorlds(sourceProps, worldName) + readKnownWorlds(targetProps, worldName) + worldName
            ).distinctBy { it.lowercase(Locale.getDefault()) }
            .sortedBy { it.lowercase(Locale.getDefault()) }
            .joinToString(",")

        val displayKey = "pocketcraft-world-display.$worldName"
        val photoKey = "pocketcraft-world-photo.$worldName"
        val descriptionKey = "pocketcraft-world-description.$worldName"

        sourceProps.getProperty(displayKey)?.takeIf { it.isNotBlank() }?.let { targetProps[displayKey] = it }
        sourceProps.getProperty(descriptionKey)?.takeIf { it.isNotBlank() }?.let { targetProps[descriptionKey] = it }

        val sourcePhoto = sourceProps.getProperty(photoKey).orEmpty().trim()
        if (sourcePhoto.isNotBlank()) {
            targetProps[photoKey] = migratePhotoIfNeeded(sourcePhoto, sourceDir, targetDir, worldName, fromVersionId, toVersionId)
        }

        sourceProps.getProperty("motd")?.takeIf { it.isNotBlank() }?.let {
            if (targetProps.getProperty("motd").isNullOrBlank()) {
                targetProps["motd"] = it
            }
        }

        ServerPropertiesHelper.saveProperties(targetDir, targetProps)
    }

    private fun migratePhotoIfNeeded(
        rawUri: String,
        sourceDir: File,
        targetDir: File,
        worldName: String,
        fromVersionId: String,
        toVersionId: String
    ): String {
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return rawUri
        if (!uri.scheme.equals("file", ignoreCase = true)) return rawUri

        val sourcePath = uri.path.orEmpty()
        val sourcePhotosDir = File(sourceDir, "server_photos").absolutePath
        if (!sourcePath.startsWith(sourcePhotosDir)) return rawUri

        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) return rawUri

        val extension = sourceFile.extension.ifBlank { "jpg" }
        val targetPhotosDir = File(targetDir, "server_photos").also { it.mkdirs() }
        val targetFile = File(targetPhotosDir, "${worldName}_migrated.$extension")
        if (!targetFile.exists()) {
            runCatching { sourceFile.copyTo(targetFile, overwrite = true) }
        }
        return Uri.fromFile(targetFile).toString()
    }

    private fun migrateWorldPluginProfile(sourceDir: File, targetDir: File, worldName: String) {
        val sourceProfile = File(sourceDir, "world_plugin_profiles/$worldName")
        val targetProfile = File(targetDir, "world_plugin_profiles/$worldName")
        if (!sourceProfile.exists() || targetProfile.exists()) return
        moveFileOrDirectory(sourceProfile, targetProfile)
    }

    private fun mergeNamedPlayerList(sourceDir: File, targetDir: File, fileName: String) {
        val sourceFile = File(sourceDir, fileName)
        if (!sourceFile.exists()) return

        val targetFile = File(targetDir, fileName)
        val sourceEntries = readNamedPlayerEntries(sourceFile)
        val targetEntries = readNamedPlayerEntries(targetFile)
        val merged = linkedMapOf<String, JSONObject>()

        (targetEntries + sourceEntries).forEach { entry ->
            val key = buildPlayerKey(entry)
            if (key.isNotBlank()) {
                merged[key] = entry
            }
        }

        if (merged.isEmpty()) return
        val out = JSONArray()
        merged.values
            .sortedBy { it.optString("name").lowercase(Locale.getDefault()) }
            .forEach(out::put)
        targetFile.writeText(out.toString(2))
    }

    private fun mergeUserCache(sourceDir: File, targetDir: File) {
        val sourceFile = File(sourceDir, "usercache.json")
        if (!sourceFile.exists()) return

        val targetFile = File(targetDir, "usercache.json")
        val merged = linkedMapOf<String, JSONObject>()
        (readNamedPlayerEntries(targetFile) + readNamedPlayerEntries(sourceFile)).forEach { entry ->
            val key = entry.optString("uuid").trim().ifBlank {
                entry.optString("name").trim().lowercase(Locale.getDefault())
            }
            if (key.isNotBlank()) {
                merged[key] = entry
            }
        }
        if (merged.isEmpty()) return
        val out = JSONArray()
        merged.values
            .sortedBy { it.optString("name").lowercase(Locale.getDefault()) }
            .forEach(out::put)
        targetFile.writeText(out.toString(2))
    }

    private fun readNamedPlayerEntries(file: File): List<JSONObject> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            buildList {
                for (index in 0 until arr.length()) {
                    val obj = arr.optJSONObject(index) ?: continue
                    add(JSONObject(obj.toString()))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun buildPlayerKey(entry: JSONObject): String {
        val uuid = entry.optString("uuid").trim()
        if (uuid.isNotBlank()) return "uuid:$uuid"
        val name = entry.optString("name").trim().lowercase(Locale.getDefault())
        return if (name.isBlank()) "" else "name:$name"
    }

    private fun moveFileOrDirectory(source: File, target: File): Boolean {
        target.parentFile?.mkdirs()
        if (source.renameTo(target)) {
            return true
        }
        return runCatching {
            if (source.isDirectory) {
                source.copyRecursively(target, overwrite = false)
                source.deleteRecursively()
            } else {
                source.copyTo(target, overwrite = false)
                source.delete()
            }
            true
        }.getOrDefault(false)
    }

    private fun migrateLegacyServerFiles(sourceDir: File, targetDir: File) {
        val names = listOf(
            "eula.txt",
            "ops.json",
            "whitelist.json",
            "banned-players.json",
            "banned-ips.json",
            "usercache.json",
            "plugins",
            "mods",
            "resourcepacks",
            "config",
            "spigot.yml",
            "bukkit.yml",
            "paper.yml",
            "server-icon.png",
            "server_photos",
            "world_plugin_profiles"
        )
        names.forEach { name ->
            val source = File(sourceDir, name)
            val target = File(targetDir, name)
            if (source.exists() && !target.exists()) {
                moveFileOrDirectory(source, target)
            }
        }
    }

    private fun findWorldRoot(serverDir: File, preferredName: String): File? {
        val preferred = File(serverDir, preferredName)
        if (containsWorldData(preferred)) return preferred
        if (containsWorldData(serverDir)) return serverDir
        return serverDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory && !it.name.endsWith("_nether") && !it.name.endsWith("_the_end") }
            .firstOrNull(::containsWorldData)
    }

    private fun containsWorldData(file: File): Boolean {
        return file.exists() && (
            File(file, "level.dat").exists() ||
                File(file, "region").isDirectory ||
                File(file, "playerdata").isDirectory ||
                File(file, "stats").isDirectory
            )
    }

    private fun worldCandidates(serverDir: File, worldName: String): List<File> {
        return listOf(
            File(serverDir, worldName),
            File(serverDir, "${worldName}_nether"),
            File(serverDir, "${worldName}_the_end")
        )
    }

    private fun readKnownWorlds(props: java.util.Properties, activeWorld: String): List<String> {
        return (props.getProperty("pocketcraft-world-list").orEmpty()
            .split(',')
            .map(::sanitizeWorldName)
            .filter { it.isNotBlank() } + sanitizeWorldName(activeWorld))
            .distinctBy { it.lowercase(Locale.getDefault()) }
    }

    private fun sanitizeWorldName(input: String): String {
        val cleaned = input.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
        return cleaned.replace(Regex("_+"), "_").trim('_').ifBlank { "world" }
    }
}
