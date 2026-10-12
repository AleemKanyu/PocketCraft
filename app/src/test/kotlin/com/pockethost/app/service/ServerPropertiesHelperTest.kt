package com.pockethost.app.service

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties

class ServerPropertiesHelperTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `ampersand colour codes become section sign codes`() {
        assertEquals("§aGreen §lBold", ServerPropertiesHelper.translateColorCodes("&aGreen &lBold"))
        assertEquals("Tom & Jerry &z", ServerPropertiesHelper.translateColorCodes("Tom & Jerry &z"))
    }

    @Test
    fun `file rewritten by the server in UTF-8 keeps its colour codes`() {
        val file = File(temp.root, "server.properties")
        file.writeText("motd=§aHello ⚔\nmax-players=5\n", Charsets.UTF_8)

        val props = Properties()
        ServerPropertiesHelper.loadInto(file, props)

        assertEquals("§aHello ⚔", props.getProperty("motd"))
    }

    @Test
    fun `file saved by the app with escapes reads back the same`() {
        val file = File(temp.root, "server.properties")
        val saved = Properties().apply { setProperty("motd", "§aHello: world") }
        file.outputStream().use { saved.store(it, null) }

        val props = Properties()
        ServerPropertiesHelper.loadInto(file, props)

        assertEquals("§aHello: world", props.getProperty("motd"))
    }

    @Test
    fun `world details survive the server writing back its old copy`() {
        val key = "pocketcraft-world-description.world"
        val file = File(temp.root, "server.properties")
        ServerPropertiesHelper.saveWorldDetails(temp.root, mapOf(key to "New text"))
        file.writeText("$key=Old text\nmotd=x\n", Charsets.UTF_8)

        val props = Properties()
        ServerPropertiesHelper.loadInto(file, props)
        assertEquals("New text", props.getProperty(key))

        ServerPropertiesHelper.removeWorldDetails(temp.root, listOf(key))
        val after = Properties()
        ServerPropertiesHelper.loadInto(file, after)
        assertEquals("Old text", after.getProperty(key))
    }
}
