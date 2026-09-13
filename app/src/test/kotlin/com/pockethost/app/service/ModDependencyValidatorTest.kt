package com.pockethost.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModDependencyValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createModJar(dir: File, name: String, entries: Map<String, String>, rawEntries: Map<String, ByteArray> = emptyMap()): File {
        val file = File(dir, name)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            entries.forEach { (path, content) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            rawEntries.forEach { (path, bytes) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return file
    }

    private fun createNestedJarBytes(entries: Map<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.forEach { (path, content) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun testFabricModMissingDependencyIsDetected() {
        val modsDir = tempFolder.newFolder("mods")

        createModJar(modsDir, "biomesoplenty.jar", mapOf(
            "fabric.mod.json" to """
                {
                    "schemaVersion": 1,
                    "id": "biomesoplenty",
                    "name": "Biomes O' Plenty",
                    "version": "26.2.0.0.28",
                    "depends": {
                        "fabricloader": "*",
                        "minecraft": "26.2",
                        "java": ">=17",
                        "terrablender": ">=26.2.0.0.1"
                    }
                }
            """.trimIndent()
        ))

        val missing = ModDependencyValidator.detectMissingDependencies(modsDir)
        assertEquals(1, missing.size)
        assertEquals("biomesoplenty", missing[0].modId)
        assertEquals("terrablender", missing[0].dependencyId)
        assertEquals(">=26.2.0.0.1", missing[0].versionRequirement)
    }

    @Test
    fun testFabricModDependenciesSatisfied() {
        val modsDir = tempFolder.newFolder("mods")

        createModJar(modsDir, "biomesoplenty.jar", mapOf(
            "fabric.mod.json" to """
                {
                    "schemaVersion": 1,
                    "id": "biomesoplenty",
                    "name": "Biomes O' Plenty",
                    "version": "26.2.0.0.28",
                    "depends": {
                        "fabricloader": "*",
                        "minecraft": "26.2",
                        "java": ">=17",
                        "terrablender": ">=26.2.0.0.1"
                    }
                }
            """.trimIndent()
        ))

        createModJar(modsDir, "terrablender.jar", mapOf(
            "fabric.mod.json" to """
                {
                    "schemaVersion": 1,
                    "id": "terrablender",
                    "name": "TerraBlender",
                    "version": "26.2.0.0.2",
                    "depends": {
                        "minecraft": "26.2"
                    }
                }
            """.trimIndent()
        ))

        val missing = ModDependencyValidator.detectMissingDependencies(modsDir)
        assertTrue("All dependencies should be satisfied", missing.isEmpty())
    }

    @Test
    fun testNestedJarProvidesDependency() {
        val modsDir = tempFolder.newFolder("mods")

        val nestedLibBytes = createNestedJarBytes(mapOf(
            "fabric.mod.json" to """
                {
                    "schemaVersion": 1,
                    "id": "embedded_library",
                    "name": "Embedded Library",
                    "version": "1.0.0"
                }
            """.trimIndent()
        ))

        createModJar(
            modsDir,
            "parent_mod.jar",
            entries = mapOf(
                "fabric.mod.json" to """
                    {
                        "schemaVersion": 1,
                        "id": "parent_mod",
                        "name": "Parent Mod",
                        "version": "1.0.0",
                        "jars": [
                            { "file": "META-INF/jars/nested_lib.jar" }
                        ]
                    }
                """.trimIndent()
            ),
            rawEntries = mapOf(
                "META-INF/jars/nested_lib.jar" to nestedLibBytes
            )
        )

        createModJar(modsDir, "consumer_mod.jar", mapOf(
            "fabric.mod.json" to """
                {
                    "schemaVersion": 1,
                    "id": "consumer_mod",
                    "name": "Consumer Mod",
                    "version": "1.0.0",
                    "depends": {
                        "embedded_library": ">=1.0.0"
                    }
                }
            """.trimIndent()
        ))

        val missing = ModDependencyValidator.detectMissingDependencies(modsDir)
        assertTrue("Embedded library from JiJ should satisfy dependency", missing.isEmpty())
    }

    @Test
    fun testQuarantineMissingDependencies() {
        val serverDir = tempFolder.newFolder("server")
        val modsDir = File(serverDir, "mods").apply { mkdirs() }

        val bopJar = createModJar(modsDir, "biomesoplenty.jar", mapOf(
            "fabric.mod.json" to """
                {
                    "schemaVersion": 1,
                    "id": "biomesoplenty",
                    "name": "Biomes O' Plenty",
                    "version": "26.2.0.0.28",
                    "depends": {
                        "terrablender": ">=26.2.0.0.1"
                    }
                }
            """.trimIndent()
        ))

        val standaloneJar = createModJar(modsDir, "appleskin.jar", mapOf(
            "fabric.mod.json" to """
                {
                    "schemaVersion": 1,
                    "id": "appleskin",
                    "name": "AppleSkin",
                    "version": "1.0.0"
                }
            """.trimIndent()
        ))

        val quarantined = ModDependencyValidator.quarantineMissingDependencies(serverDir)
        assertEquals(1, quarantined.size)
        assertFalse(bopJar.exists())
        assertTrue(File(modsDir, "biomesoplenty.jar.disabled").exists())
        assertTrue(standaloneJar.exists())
    }
}
