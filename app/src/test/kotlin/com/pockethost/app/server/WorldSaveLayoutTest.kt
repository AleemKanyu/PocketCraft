package com.pockethost.app.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.GZIPOutputStream

class WorldSaveLayoutTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun levelDat(worldDir: File, content: String): File {
        val file = File(worldDir, "level.dat")
        GZIPOutputStream(file.outputStream()).use { it.write(content.toByteArray(Charsets.ISO_8859_1)) }
        return file
    }

    @Test
    fun `small level dat of a 26 world is valid`() {
        val world = tmp.newFolder("world")
        File(world, "data/minecraft").mkdirs()
        File(world, "data/minecraft/world_gen_settings.dat").writeText("x")
        val file = levelDat(world, "Data" + (1..300).joinToString("") { "a$it" } + "DataVersion")

        assertTrue(usesSplitWorldLayout(world))
        assertTrue(isValidLevelDat(file, world))
    }

    @Test
    fun `dimensions folder alone marks the 26 layout`() {
        val world = tmp.newFolder("world")
        File(world, "dimensions/minecraft/overworld/region").mkdirs()
        assertTrue(usesSplitWorldLayout(world))
    }

    @Test
    fun `older world still needs generator settings in level dat`() {
        val world = tmp.newFolder("world")
        File(world, "region").mkdirs()
        val filler = (1..600).joinToString("") { "a$it" }
        assertFalse(usesSplitWorldLayout(world))
        assertFalse(isValidLevelDat(levelDat(world, "Data${filler}DataVersion"), world))
        assertTrue(isValidLevelDat(levelDat(world, "Data${filler}WorldGenSettings"), world))
    }

    @Test
    fun `garbage is never valid`() {
        val world = tmp.newFolder("world")
        File(world, "dimensions").mkdirs()
        val file = File(world, "level.dat").apply { writeText("not gzip at all, but long enough to pass the size check....") }
        assertFalse(isValidLevelDat(file, world))
    }

    @Test
    fun `seed is read from generator settings`() {
        val file = tmp.newFile("world_gen_settings.dat")
        val nbt = byteArrayOf(10, 0, 0, 4, 0, 4) + "seed".toByteArray() +
            java.nio.ByteBuffer.allocate(8).putLong(69420025046859268L).array() + byteArrayOf(0)
        GZIPOutputStream(file.outputStream()).use { it.write(nbt) }

        assertEquals(69420025046859268L, readWorldGenSeed(file))
        assertNull(readWorldGenSeed(tmp.newFile("empty.dat")))
    }

    @Test
    fun `server type record returns the previous type`() {
        val serverDir = tmp.newFolder("server")
        assertNull(recordWorldServerType(serverDir, "PAPER"))
        assertEquals("PAPER", recordWorldServerType(serverDir, "FABRIC"))
        assertEquals("FABRIC", recordWorldServerType(serverDir, "FABRIC"))
    }

    @Test
    fun `only a world coming from paper or purpur is cleaned`() {
        val world = tmp.newFolder("world")
        val missing = File(world, "level.dat")
        assertTrue(cameFromBukkitServer("PAPER", missing))
        assertTrue(cameFromBukkitServer("PURPUR", missing))
        assertFalse(cameFromBukkitServer("FABRIC", missing))
        assertFalse(cameFromBukkitServer("VANILLA", missing))
        // No record yet: the brands in level.dat decide.
        assertFalse(cameFromBukkitServer(null, missing))
        assertTrue(cameFromBukkitServer(null, levelDat(world, "ServerBrands Paper fabric")))
        assertFalse(cameFromBukkitServer(null, levelDat(world, "ServerBrands fabric vanilla")))
    }
}
