package com.pocketcraft.server.server

import java.io.File
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

object TestJna {
    fun testPatch(librariesDir: File) {
        val jnaJar = librariesDir.walkTopDown().firstOrNull {
            it.isFile && it.name.startsWith("jna-") && !it.name.contains("jna-platform") && !it.name.contains("jna-jpms") && it.name.endsWith(".jar")
        }
        println("Found jna jar: $jnaJar")
        if (jnaJar != null) {
            val jnaBytes = jnaJar.readBytes()
            val zis = ZipInputStream(ByteArrayInputStream(jnaBytes))
            zis.use { stream ->
                var entry = stream.nextEntry
                while (entry != null) {
                    if (entry.name.contains("linux-aarch64/libjnidispatch.so")) {
                        println("Found libjnidispatch.so entry!")
                        val libBytes = stream.readBytes()
                        println("Read ${libBytes.size} bytes. Checking ELF...")
                        val patched = patchElfDtNeeded(libBytes)
                        println("Patched result: ${patched?.size}")
                    }
                    entry = stream.nextEntry
                }
            }
        }
    }
    
    private fun patchElfDtNeeded(data: ByteArray): ByteArray? {
        if (data.size < 64) return null
        if (data[0] != 0x7f.toByte() || data[1] != 'E'.code.toByte() ||
            data[2] != 'L'.code.toByte() || data[3] != 'F'.code.toByte()) return null
        if (data[4] != 2.toByte()) return null
        println("Valid ELF64 file!")
        return data.copyOf()
    }
}
