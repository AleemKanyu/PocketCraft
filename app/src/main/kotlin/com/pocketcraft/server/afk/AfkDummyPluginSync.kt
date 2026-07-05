package com.pocketcraft.server.afk

import android.content.Context
import com.pocketcraft.server.server.BundledPluginInstaller
import com.pocketcraft.server.service.ServerFileManager
import java.io.File
import java.util.Locale
import java.util.UUID

object AfkDummyPluginSync {
    private const val DUMMY_PREFIX = "AFK_"

    suspend fun syncWorldFromDatabase(context: Context, worldName: String) {
        val appContext = context.applicationContext
        val dao = AfkHelperDatabase.getInstance(appContext).afkFarmLocationDao()
        val normalizedActiveFarms = mutableListOf<AfkFarmLocationEntity>()
        dao.getAll().forEach { farm ->
            if (!farm.worldName.equals(worldName, ignoreCase = true) || !farm.isActive || farm.ownerPlayerUuid.isBlank()) {
                return@forEach
            }

            val uuid = farm.dummyUuid.ifBlank { UUID.randomUUID().toString() }
            val createdAt = farm.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis()
            val normalized = if (uuid != farm.dummyUuid || createdAt != farm.createdAt) {
                farm.copy(dummyUuid = uuid, createdAt = createdAt).also { dao.upsert(it) }
            } else {
                farm
            }
            normalizedActiveFarms += normalized
        }

        syncWorld(appContext, worldName, normalizedActiveFarms)
    }

    fun syncWorld(
        context: Context,
        worldName: String,
        activeFarms: List<AfkFarmLocationEntity>
    ) {
        val appContext = context.applicationContext
        val serverDir = ServerFileManager.getServerDir(appContext, worldName.ifBlank { "world" })
        BundledPluginInstaller.installBundledPlugins(appContext, serverDir)
        ensureDummyPluginSupport(serverDir)

        val dao = AfkHelperDatabase.getInstance(appContext).afkFarmLocationDao()
        val allFarms = runCatching {
            kotlinx.coroutines.runBlocking {
                dao.getAll().filter { it.worldName.equals(worldName, ignoreCase = true) }
            }
        }.getOrElse { emptyList() }

        val dummiesFile = File(serverDir, "plugins/dummyplayers/dummies.yml")
        val managedNames = allFarms.flatMap {
            listOf(
                it.dummyEntityName.lowercase(Locale.getDefault()),
                "$DUMMY_PREFIX${it.dummyEntityName}".lowercase(Locale.getDefault())
            )
        }.toSet()
        val activeNames = activeFarms.flatMap {
            listOf(
                it.dummyEntityName.lowercase(Locale.getDefault()),
                "$DUMMY_PREFIX${it.dummyEntityName}".lowercase(Locale.getDefault())
            )
        }.toSet()
        val mergedEntries = mutableListOf<DummyYamlEntry>()

        parseDummiesYaml(dummiesFile).forEach { entry ->
            val nameLower = entry.name.lowercase(Locale.getDefault())
            if (nameLower !in managedNames || nameLower in activeNames) {
                if (nameLower !in activeNames) {
                    mergedEntries += entry
                }
            }
        }

        activeFarms.forEach { farm ->
            mergedEntries += DummyYamlEntry(
                keyUuid = farm.dummyUuid,
                uuid = farm.dummyUuid,
                name = farm.dummyEntityName,
                ownerUuid = farm.ownerPlayerUuid,
                world = worldName,
                x = farm.x.toDouble(),
                y = farm.y.toDouble(),
                z = farm.z.toDouble(),
                yaw = farm.yaw.toDouble(),
                pitch = farm.pitch.toDouble(),
                createdAt = farm.createdAt
            )
        }

        writeDummiesYaml(dummiesFile, mergedEntries)
    }

    private fun ensureDummyPluginSupport(serverDir: File) {
        val dataDir = File(serverDir, "plugins/dummyplayers").also { it.mkdirs() }
        val configFile = File(dataDir, "config.yml")
        configFile.writeText(
            """
            max-dummies-per-player: 8
            name-prefix: ${yamlScalar(DUMMY_PREFIX)}
            chunk-loading:
              enabled: true
              radius: 2
            authentication:
              enabled: false
            """.trimIndent() + "\n"
        )
    }

    private fun parseDummiesYaml(file: File): List<DummyYamlEntry> {
        if (!file.exists()) return emptyList()
        val result = mutableListOf<DummyYamlEntry>()
        var currentKey: String? = null
        var fields = mutableMapOf<String, String>()

        fun flush() {
            val key = currentKey ?: return
            result += DummyYamlEntry(
                keyUuid = key,
                uuid = fields["uuid"].orEmpty().ifBlank { key },
                name = fields["name"].orEmpty(),
                ownerUuid = fields["owner"].orEmpty(),
                world = fields["world"].orEmpty(),
                x = fields["x"]?.toDoubleOrNull(),
                y = fields["y"]?.toDoubleOrNull(),
                z = fields["z"]?.toDoubleOrNull(),
                yaw = fields["yaw"]?.toDoubleOrNull(),
                pitch = fields["pitch"]?.toDoubleOrNull(),
                createdAt = fields["created-at"]?.toLongOrNull() ?: 0L
            )
            currentKey = null
            fields = mutableMapOf()
        }

        file.readLines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank() || line.trimStart().startsWith("#") || line.trim() == "dummies:") {
                return@forEach
            }

            val keyMatch = Regex("""^\s{2}([^:#]+):\s*$""").find(line)
            if (keyMatch != null) {
                flush()
                currentKey = keyMatch.groupValues[1].trim()
                return@forEach
            }

            val fieldMatch = Regex("""^\s{4}([^:#]+):\s*(.*)$""").find(line)
            if (fieldMatch != null && currentKey != null) {
                val key = fieldMatch.groupValues[1].trim()
                val value = parseYamlScalar(fieldMatch.groupValues[2].trim())
                fields[key] = value
            }
        }
        flush()
        return result
    }

    private fun writeDummiesYaml(file: File, entries: List<DummyYamlEntry>) {
        file.parentFile?.mkdirs()
        if (entries.isEmpty()) {
            file.writeText("dummies: {}\n")
            return
        }

        val sortedEntries = entries.sortedBy { it.name.lowercase(Locale.getDefault()) }
        val content = buildString {
            appendLine("dummies:")
            sortedEntries.forEach { entry ->
                appendLine("  ${entry.keyUuid}:")
                appendLine("    name: ${yamlScalar(entry.name)}")
                appendLine("    uuid: ${yamlScalar(entry.uuid.ifBlank { entry.keyUuid })}")
                appendLine("    owner: ${yamlScalar(entry.ownerUuid)}")
                appendLine("    world: ${yamlScalar(entry.world)}")
                appendLine("    x: ${entry.x ?: 0.0}")
                appendLine("    y: ${entry.y ?: 0.0}")
                appendLine("    z: ${entry.z ?: 0.0}")
                appendLine("    yaw: ${entry.yaw ?: 0.0}")
                appendLine("    pitch: ${entry.pitch ?: 0.0}")
                appendLine("    created-at: ${entry.createdAt}")
            }
        }
        file.writeText(content)
    }

    private fun parseYamlScalar(raw: String): String {
        if (raw.length >= 2 && raw.startsWith("'") && raw.endsWith("'")) {
            return raw.substring(1, raw.length - 1).replace("''", "'")
        }
        if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
        }
        return raw
    }

    private fun yamlScalar(value: String?): String {
        val safeValue = value.orEmpty()
        return "'${safeValue.replace("'", "''")}'"
    }

    private data class DummyYamlEntry(
        val keyUuid: String,
        val uuid: String,
        val name: String,
        val ownerUuid: String,
        val world: String,
        val x: Double?,
        val y: Double?,
        val z: Double?,
        val yaw: Double?,
        val pitch: Double?,
        val createdAt: Long
    )
}
