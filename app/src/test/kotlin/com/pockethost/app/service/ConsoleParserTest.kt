package com.pockethost.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleParserTest {

    @Test
    fun `join and leave lines are recognised`() {
        assertEquals("Steve" to "", ConsoleParser.parseJoin("[17:30:06 INFO]: Steve joined the game"))
        assertEquals(".Alex" to "", ConsoleParser.parseJoin("[17:30:06 INFO]: .Alex JOINED THE GAME"))
        assertEquals("Steve", ConsoleParser.parseLeave("[17:31:00 INFO]: Steve left the game"))
        assertEquals("Steve", ConsoleParser.parseLeave("[17:31:00 INFO]: Steve lost connection: Disconnected"))
        assertNull(ConsoleParser.parseJoin("[17:30:06 INFO]: Preparing spawn area: 42%"))
        assertNull(ConsoleParser.parseLeave("[17:30:06 INFO]: Preparing spawn area: 42%"))
    }

    @Test
    fun `command tps and chunky lines are recognised`() {
        assertEquals(
            "Steve" to "/ram",
            ConsoleParser.parseCommand("[17:30:06 INFO]: Steve issued server command: /ram")
        )
        assertEquals(19.98f, ConsoleParser.parseTps("[17:31:00 INFO]: TPS from last 1m, 5m, 15m: 19.98, 19.99, 20.0"))
        assertEquals(
            ChunkyProgress(3500, 10000, 35.0f),
            ConsoleParser.parseChunkyProgress("[Chunky] Task world:overworld [3500/10000] [35.00%] [50.5 cps]")
        )
        assertNull(ConsoleParser.parseCommand("[17:30:06 INFO]: Steve joined the game"))
        assertNull(ConsoleParser.parseTps("[17:30:06 INFO]: Steve joined the game"))
    }

    @Test
    fun `ping replies from each server flavour are parsed`() {
        val telemetry = ConsoleParser.parsePing(
            "[17:30:06 INFO]: [PocketCraftPing] Steve:10@127.0.0.1#abc-123!1,64,-3,world Alex:42"
        )
        assertEquals(ParsedPlayerPing(10, "127.0.0.1", "abc-123", 1, 64, -3, "world"), telemetry["Steve"])
        assertEquals(42, telemetry["Alex"]?.pingMs)

        assertEquals(42, ConsoleParser.parsePing("§aSteve §fhas a ping of 42ms")["Steve"]?.pingMs)
        assertEquals(42, ConsoleParser.parsePing("Steve's ping is 42ms")["Steve"]?.pingMs)
        assertEquals(37, ConsoleParser.parsePing("Steve has the following entity data: 37")["Steve"]?.pingMs)
        assertTrue(ConsoleParser.parsePing("[17:30:06 INFO]: Steve joined the game").isEmpty())
    }

    @Test
    fun `a very long line is rejected quickly`() {
        val line = "x".repeat(400_000) + " ping latency entity data"
        val started = System.nanoTime()
        assertTrue(ConsoleParser.parsePing(line).isEmpty())
        assertNull(ConsoleParser.parseJoin(line))
        assertNull(ConsoleParser.parseLeave(line))
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("took ${elapsedMs}ms", elapsedMs < 1000)
    }

    @Test
    fun `ansi colour codes are stripped`() {
        assertEquals("Done (1.2s)!", ConsoleParser.stripAnsi("\u001B[32mDone (1.2s)!\u001B[0m"))
        assertEquals("plain", ConsoleParser.stripAnsi("plain"))
    }
}
