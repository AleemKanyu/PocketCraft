package com.pockethost.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModpackManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createModJar(name: String, entries: Map<String, String>): File {
        val file = tempFolder.newFile(name)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            entries.forEach { (path, content) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return file
    }

    @Test
    fun testForgeClientOnlyModIsRemoved() {
        val jar = createModJar("shader_engine_1.20.1.jar", mapOf(
            "META-INF/mods.toml" to """
                modLoader="javafml"
                loaderVersion="[47,)"
                [[mods]]
                modId="oculus"
                version="1.6.4"
                displayName="Oculus"
            """.trimIndent()
        ))

        val decision = ModpackManager.evaluateModJarForServer(jar)
        assertTrue("Oculus should be marked for removal", decision.remove)
        assertTrue("Reason should mention Forge/NeoForge mod id", decision.reason.contains("oculus"))
    }

    @Test
    fun testForgeServerCompatibleModIsKept() {
        val jar = createModJar("create-1.20.1.jar", mapOf(
            "META-INF/mods.toml" to """
                modLoader="javafml"
                loaderVersion="[47,)"
                [[mods]]
                modId="create"
                version="0.5.1"
                displayName="Create"
            """.trimIndent()
        ))

        val decision = ModpackManager.evaluateModJarForServer(jar)
        assertFalse("Create should be kept for server", decision.remove)
    }

    @Test
    fun testNeoForgeClientOnlyModIsRemoved() {
        val jar = createModJar("keybind_helper.jar", mapOf(
            "META-INF/neoforge.mods.toml" to """
                modLoader="javafml"
                loaderVersion="[1,)"
                [[mods]]
                modId="controlling"
                version="12.0.2"
                displayName="Controlling"
            """.trimIndent()
        ))

        val decision = ModpackManager.evaluateModJarForServer(jar)
        assertTrue("Controlling should be marked for removal", decision.remove)
        assertTrue("Reason should mention controlling", decision.reason.contains("controlling"))
    }

    @Test
    fun testFabricClientEnvironmentIsRemoved() {
        val jar = createModJar("custom-client-mod.jar", mapOf(
            "fabric.mod.json" to """
                {
                    "schemaVersion": 1,
                    "id": "some_random_client_gui",
                    "name": "Random Client GUI",
                    "environment": "client"
                }
            """.trimIndent()
        ))

        val decision = ModpackManager.evaluateModJarForServer(jar)
        assertTrue("Fabric mod with environment=client should be removed", decision.remove)
        assertTrue("Reason should mention Fabric environment=client", decision.reason.contains("environment=client"))
    }

    @Test
    fun testFabricServerCompatibleModIsKept() {
        val jar = createModJar("farmersdelight-fabric.jar", mapOf(
            "fabric.mod.json" to """
                {
                    "schemaVersion": 1,
                    "id": "farmersdelight",
                    "name": "Farmer's Delight",
                    "environment": "*"
                }
            """.trimIndent()
        ))

        val decision = ModpackManager.evaluateModJarForServer(jar)
        assertFalse("Farmer's Delight should be kept for server", decision.remove)
    }

    @Test
    fun testQuiltClientEnvironmentIsRemoved() {
        val jar = createModJar("quilt-client-mod.jar", mapOf(
            "quilt.mod.json" to """
                {
                    "schema_version": 1,
                    "quilt_loader": {
                        "id": "quilt_client_tool",
                        "version": "1.0.0"
                    },
                    "minecraft": {
                        "environment": "client"
                    }
                }
            """.trimIndent()
        ))

        val decision = ModpackManager.evaluateModJarForServer(jar)
        assertTrue("Quilt client mod should be removed", decision.remove)
    }

    @Test
    fun testLegacyForgeMcmodInfoIsRemoved() {
        val jar = createModJar("minimap_legacy.jar", mapOf(
            "mcmod.info" to """
                [
                    {
                        "modid": "voxelmap",
                        "name": "VoxelMap",
                        "version": "1.9.9"
                    }
                ]
            """.trimIndent()
        ))

        val decision = ModpackManager.evaluateModJarForServer(jar)
        assertTrue("Legacy Forge client mod should be removed", decision.remove)
    }

    @Test
    fun testFilenameHintIsRemovedEvenWithoutMetadata() {
        val jar = createModJar("rubidium-mc1.20.1-0.7.1.jar", emptyMap())
        val decision = ModpackManager.evaluateModJarForServer(jar)
        assertTrue("Filename hint should mark mod for removal", decision.remove)
        assertTrue("Reason should mention filename matches", decision.reason.contains("filename matches"))
    }
}
