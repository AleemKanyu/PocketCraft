package com.pockethost.app

import com.pockethost.app.WorldImporter.ImportDimension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WorldImporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun zipOf(vararg entries: Pair<String, String>): File {
        val zip = tmp.newFile()
        ZipOutputStream(zip.outputStream()).use { out ->
            entries.forEach { (name, content) ->
                out.putNextEntry(ZipEntry(name))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return zip
    }

    /** A server dir that already holds an Overworld and an older Nether. */
    private fun serverDirWithWorld(): File {
        val serverDir = tmp.newFolder()
        File(serverDir, "world/region").mkdirs()
        File(serverDir, "world/level.dat").writeText("level")
        File(serverDir, "world/region/r.0.0.mca").writeText("overworld")
        File(serverDir, "world/DIM-1/region").mkdirs()
        File(serverDir, "world/DIM-1/region/r.9.9.mca").writeText("stale nether, larger than the import")
        return serverDir
    }

    private fun importDimension(zip: File, serverDir: File, dimension: ImportDimension) {
        val staging = tmp.newFolder()
        WorldImporter.extractZip(zip, staging) { _, _ -> }
        WorldImporter.installDimensionFromStaging(staging, serverDir, "world", dimension)
    }

    private fun assertNetherInstalled(serverDir: File) {
        assertEquals("nether", File(serverDir, "world_nether/DIM-1/region/r.0.0.mca").readText())
        assertEquals("overworld", File(serverDir, "world/region/r.0.0.mca").readText())
        assertFalse(File(serverDir, "world/DIM-1").exists())
        assertEquals(setOf("world", "world_nether", "server.properties"), serverDir.list()!!.toSet())
    }

    @Test
    fun `bukkit nether zip lands in the nether, not the overworld`() {
        val serverDir = serverDirWithWorld()
        importDimension(
            zipOf("world_nether/level.dat" to "x", "world_nether/DIM-1/region/r.0.0.mca" to "nether"),
            serverDir,
            ImportDimension.NETHER
        )
        assertNetherInstalled(serverDir)
    }

    @Test
    fun `flat region zip lands in the nether`() {
        val serverDir = serverDirWithWorld()
        importDimension(zipOf("region/r.0.0.mca" to "nether", "entities/r.0.0.mca" to "e"), serverDir, ImportDimension.NETHER)
        assertNetherInstalled(serverDir)
        assertTrue(File(serverDir, "world_nether/DIM-1/entities/r.0.0.mca").exists())
    }

    @Test
    fun `bare mca files are treated as the region folder`() {
        val serverDir = serverDirWithWorld()
        importDimension(zipOf("r.0.0.mca" to "nether"), serverDir, ImportDimension.NETHER)
        assertNetherInstalled(serverDir)
    }

    @Test
    fun `full world zip contributes only the requested dimension`() {
        val serverDir = serverDirWithWorld()
        importDimension(
            zipOf(
                "MyWorld/level.dat" to "x",
                "MyWorld/region/r.0.0.mca" to "other overworld",
                "MyWorld/DIM-1/region/r.0.0.mca" to "nether",
                "MyWorld/DIM1/region/r.0.0.mca" to "end"
            ),
            serverDir,
            ImportDimension.NETHER
        )
        assertNetherInstalled(serverDir)
    }

    @Test
    fun `end import uses the end layout`() {
        val serverDir = serverDirWithWorld()
        importDimension(zipOf("/world_the_end/DIM1/region/r.0.0.mca" to "end"), serverDir, ImportDimension.END)
        assertEquals("end", File(serverDir, "world_the_end/DIM1/region/r.0.0.mca").readText())
        assertTrue(File(serverDir, "world/DIM-1/region/r.9.9.mca").exists())
    }

    @Test
    fun `zip without region files is rejected and leaves the world alone`() {
        val serverDir = serverDirWithWorld()
        assertThrows(IOException::class.java) {
            importDimension(zipOf("readme.txt" to "hi"), serverDir, ImportDimension.NETHER)
        }
        assertTrue(File(serverDir, "world/DIM-1/region/r.9.9.mca").exists())
    }

    @Test
    fun `non zip file reports a readable error`() {
        val notZip = tmp.newFile().apply { writeText("definitely not a zip") }
        val error = assertThrows(IOException::class.java) { WorldImporter.openZip(notZip) }
        assertTrue(error.message!!.contains("not a valid ZIP"))
    }

    @Test
    fun `entries escaping the extraction root are skipped`() {
        val root = tmp.newFolder()
        val target = File(root, "out")
        WorldImporter.extractZip(zipOf("../evil.txt" to "x", "ok.txt" to "y"), target) { _, _ -> }
        assertFalse(File(root, "evil.txt").exists())
        assertTrue(File(target, "ok.txt").exists())
    }
}
