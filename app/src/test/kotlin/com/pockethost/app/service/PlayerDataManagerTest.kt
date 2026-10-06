package com.pockethost.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlayerDataManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val uuid = "069a79f4-44e9-4726-a5be-fca90e38aaf5"

    @Test
    fun `older worlds keep player files in the world root`() {
        val world = tmp.newFolder("world")
        File(world, "playerdata").mkdirs()
        val dirs = PlayerDataManager.playerDirs(world)
        assertEquals(File(world, "playerdata"), dirs.data)
        assertEquals(File(world, "stats"), dirs.stats)
        assertEquals(File(world, "advancements"), dirs.advancements)
    }

    @Test
    fun `26 worlds keep player files under players`() {
        val world = tmp.newFolder("world")
        File(world, "players/data").mkdirs()
        val dirs = PlayerDataManager.playerDirs(world)
        assertEquals(File(world, "players/data"), dirs.data)
        assertEquals(File(world, "players/stats"), dirs.stats)
        assertEquals(File(world, "players/advancements"), dirs.advancements)

        // A 26 world that no player has joined yet has no players/ folder at all.
        val fresh = tmp.newFolder("fresh")
        File(fresh, "dimensions/minecraft/overworld").mkdirs()
        assertEquals(File(fresh, "players/data"), PlayerDataManager.playerDirs(fresh).data)
    }

    @Test
    fun `zip entries are sorted into player folders for both layouts`() {
        assertEquals("data", PlayerDataManager.classifyPlayerEntry("world/playerdata/$uuid.dat"))
        assertEquals("data", PlayerDataManager.classifyPlayerEntry("/world/players/data/$uuid.dat"))
        assertEquals("stats", PlayerDataManager.classifyPlayerEntry("stats/$uuid.json"))
        assertEquals("stats", PlayerDataManager.classifyPlayerEntry("world\\players\\stats\\$uuid.json"))
        assertEquals("advancements", PlayerDataManager.classifyPlayerEntry("world/advancements/$uuid.json"))
        // Datapack advancements and other world data are not player files.
        assertNull(PlayerDataManager.classifyPlayerEntry("world/data/minecraft/advancements/story/root.json"))
        assertNull(PlayerDataManager.classifyPlayerEntry("world/data/$uuid.dat"))
        assertNull(PlayerDataManager.classifyPlayerEntry("world/playerdata/$uuid.dat_old"))
        assertNull(PlayerDataManager.classifyPlayerEntry("world/level.dat"))
    }

    @Test
    fun `import kind is read from the file header`() {
        assertEquals(
            PlayerDataManager.PlayerImportKind.ZIP,
            PlayerDataManager.detectImportKind(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4))
        )
        assertEquals(
            PlayerDataManager.PlayerImportKind.SINGLE_PLAYER_FILE,
            PlayerDataManager.detectImportKind(byteArrayOf(0x1f, 0x8b.toByte(), 8, 0))
        )
        assertEquals(PlayerDataManager.PlayerImportKind.UNKNOWN, PlayerDataManager.detectImportKind(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun `a known player keeps the uuid the server already uses`() {
        val serverDir = tmp.newFolder("server")
        assertEquals(PlayerDataManager.getOfflineUuid("Steve"), PlayerDataManager.resolveImportUuid(serverDir, "Steve"))

        File(serverDir, "usercache.json").writeText(
            """[{"name":".Alex","uuid":"00000000-0000-0000-0009-01f946be3ae7"}]"""
        )
        assertEquals("00000000-0000-0000-0009-01f946be3ae7", PlayerDataManager.resolveImportUuid(serverDir, ".alex"))
    }
}
