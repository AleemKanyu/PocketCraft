package com.pocketcraft.server.service

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class InventoryItem(
    val slot: Int,
    val id: String,
    val count: Int
)

data class PlayerLiveSnapshot(
    val currentPos: PlayerLocation? = null,
    val respawnPos: PlayerLocation? = null,
    val lastDeathPos: PlayerLocation? = null,
    val health: Float? = null,
    val hunger: Int? = null,
    val inventory: List<InventoryItem> = emptyList()
)

data class PlayerLocation(
    val x: Double,
    val y: Double,
    val z: Double,
    val dimension: String
) {
    fun formatted(): String = String.format("X %.2f  Y %.2f  Z %.2f", x, y, z)

    fun dimensionDisplay(): String = when (dimension) {
        "minecraft:the_nether" -> "The Nether"
        "minecraft:the_end" -> "The End"
        else -> "Overworld"
    }

    fun dimensionIcon(): String = when (dimension) {
        "minecraft:the_nether" -> "🔥"
        "minecraft:the_end" -> "🌑"
        else -> "🌍"
    }
}

object NBTParser {

    fun parseInventory(output: String): List<InventoryItem> {
        val items = mutableListOf<InventoryItem>()
        android.util.Log.d("INV", "Parsing RCON output (length ${output.length})")

        // The RCON response looks like:
        //   "… has the following entity data: [{…item…}, {…item…}, …]"
        // The outer [ ] is the Inventory list; each item compound is a { } inside it.
        // We walk char-by-char tracking both [ ] list depth and { } brace depth so
        // that nested compound tags (enchantments, components, etc.) don't break things.
        val blocks = mutableListOf<String>()
        var listDepth  = 0   // tracks '[' / ']'
        var braceDepth = 0   // tracks '{' / '}'
        var blockStart = -1
        for (i in output.indices) {
            when (output[i]) {
                '[' -> listDepth++
                ']' -> listDepth--
                '{' -> {
                    // Capture the opening brace of each top-level item compound.
                    // "Top-level" means braceDepth is 0, i.e. we are not already inside
                    // a nested compound.  The outer list brackets don't count as braces.
                    if (braceDepth == 0) blockStart = i
                    braceDepth++
                }
                '}' -> {
                    braceDepth--
                    if (braceDepth == 0 && blockStart != -1) {
                        blocks.add(output.substring(blockStart, i + 1))
                        blockStart = -1
                    }
                }
            }
        }

        val slotRegex  = Regex("""Slot\s*:\s*(-?\d+)b?""",          RegexOption.IGNORE_CASE)
        val idRegex    = Regex("""\bid\s*:\s*"([^"]+)"""",           RegexOption.IGNORE_CASE)
        val countRegex = Regex("""(?:count|Count)\s*:\s*(\d+)b?""", RegexOption.IGNORE_CASE)

        for (block in blocks) {
            val slot  = slotRegex.find(block)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: continue
            val id    = idRegex.find(block)?.groupValues?.getOrNull(1)?.trim()          ?: continue
            if (id.isBlank()) continue
            val count = countRegex.find(block)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1

            android.util.Log.d("INV", "Block parsed → slot=$slot id=$id count=$count")
            items += InventoryItem(slot = slot, id = id, count = count.coerceAtLeast(1))
        }

        android.util.Log.d("INV", "Parsed ${items.size} item entries from ${blocks.size} blocks")
        return items.sortedBy { it.slot }
    }

    fun parseInventoryLists(slotLine: String, idLine: String, countLine: String): List<InventoryItem> {
        val items = mutableListOf<InventoryItem>()
        val slots = Regex("""(-?\d+)b?""").findAll(slotLine).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
        val ids = Regex(""""([^"]+)"""").findAll(idLine).map { it.groupValues[1] }.toList()
        val counts = Regex("""(-?\d+)b?""").findAll(countLine).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()

        val minSize = minOf(slots.size, ids.size)
        for (i in 0 until minSize) {
            val count = counts.getOrNull(i) ?: 1
            items.add(InventoryItem(slots[i], ids[i], count.coerceAtLeast(1)))
        }

        android.util.Log.d("INV", "Parsed ${items.size} items from batched slots/ids/counts lists")
        return items.sortedBy { it.slot }
    }

    fun parsePosition(output: String): Triple<Double, Double, Double>? {
        val posMatch = Regex("\\[\\s*(-?[\\d.]+)d?\\s*,\\s*(-?[\\d.]+)d?\\s*,\\s*(-?[\\d.]+)d?\\s*]", RegexOption.IGNORE_CASE)
            .find(output) ?: return null

        return Triple(
            posMatch.groupValues[1].toDoubleOrNull() ?: return null,
            posMatch.groupValues[2].toDoubleOrNull() ?: return null,
            posMatch.groupValues[3].toDoubleOrNull() ?: return null
        )
    }

    fun parseBlockPosition(output: String): Triple<Double, Double, Double>? {
        val vectorMatch = Regex("""\[\s*I;\s*(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*]""", RegexOption.IGNORE_CASE)
            .find(output)
        if (vectorMatch != null) {
            return Triple(
                vectorMatch.groupValues[1].toDoubleOrNull() ?: return null,
                vectorMatch.groupValues[2].toDoubleOrNull() ?: return null,
                vectorMatch.groupValues[3].toDoubleOrNull() ?: return null
            )
        }

        val plainMatch = Regex("""(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)""").find(output) ?: return null
        return Triple(
            plainMatch.groupValues[1].toDoubleOrNull() ?: return null,
            plainMatch.groupValues[2].toDoubleOrNull() ?: return null,
            plainMatch.groupValues[3].toDoubleOrNull() ?: return null
        )
    }

    fun parseDimension(output: String): String {
        val lower = output.lowercase()
        return when {
            "minecraft:the_nether" in lower || "the_nether" in lower -> "minecraft:the_nether"
            "minecraft:the_end" in lower || "the_end" in lower -> "minecraft:the_end"
            else -> "minecraft:overworld"
        }
    }

    fun parseIntValue(output: String): Int? {
        val match = Regex("(-?\\d+)").find(output) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    fun parseDataIntValue(output: String): Int? {
        val tail = output.substringAfterLast(':', output).substringAfterLast('=', output).trim()
        return Regex("""-?\d+""").find(tail)?.value?.toIntOrNull()
    }

    fun parseFloatValue(output: String): Float? {
        val match = Regex("""(-?\d+(?:\.\d+)?)""").find(output) ?: return null
        return match.groupValues[1].toFloatOrNull()
    }

    fun parseDataFloatValue(output: String): Float? {
        val tail = output.substringAfterLast(':', output).substringAfterLast('=', output).trim()
        return Regex("""-?\d+(?:\.\d+)?""").find(tail)?.value?.toFloatOrNull()
    }

    /**
     * Attempts to parse a player's .dat file using simple byte scanning for known tags.
     * This is a fallback when live data is unavailable.
     */
    fun parsePlayerData(datFile: File): PlayerLiveSnapshot? {
        if (!datFile.exists()) return null
        
        return try {
            val bytes = GZIPInputStream(FileInputStream(datFile)).use { it.readBytes() }
            
            // Search for "Pos" (List of Doubles)
            val posIndex = indexOfTag(bytes, "Pos", 9) // Type 9 is List
            val currentPos = if (posIndex != -1) {
                // Pos tag: Type(1) + NameLen(2) + Name("Pos") + ElementType(1) + Size(4)
                val start = posIndex + 1 + 2 + 3 + 1 + 4
                if (start + 24 <= bytes.size) {
                    val x = readDouble(bytes, start)
                    val y = readDouble(bytes, start + 8)
                    val z = readDouble(bytes, start + 16)
                    PlayerLocation(x, y, z, "minecraft:overworld")
                } else null
            } else null

            // Search for "Dimension" (String)
            val dimension = findStringTag(bytes, "Dimension") ?: "minecraft:overworld"

            val sx = findIntTag(bytes, "SpawnX")
            val sy = findIntTag(bytes, "SpawnY")
            val sz = findIntTag(bytes, "SpawnZ")
            val sDim = findStringTag(bytes, "SpawnDimension")
            
            val respawnPos = if (sx != null && sy != null && sz != null) {
                PlayerLocation(sx.toDouble(), sy.toDouble(), sz.toDouble(), sDim ?: "minecraft:overworld")
            } else null

            val health = findFloatTag(bytes, "Health")
            val hunger = findIntTag(bytes, "foodLevel")

            val deathIdx = indexOfTag(bytes, "LastDeathLocation", 10)
            val lastDeathPos = if (deathIdx != -1) {
                val searchEnd = (deathIdx + 200).coerceAtMost(bytes.size)
                val posIdx = indexOfTagInRange(bytes, "pos", 11, deathIdx, searchEnd)
                val dimIdx = indexOfTagInRange(bytes, "dimension", 8, deathIdx, searchEnd)
                
                val p = if (posIdx != -1) {
                    val start = posIdx + 1 + 2 + 3 + 4 // type(1) + nameLen(2) + "pos"(3) + arraySize(4)
                    if (start + 12 <= bytes.size) {
                        val dx = (((bytes[start].toInt() and 0xFF) shl 24) or
                                 ((bytes[start + 1].toInt() and 0xFF) shl 16) or
                                 ((bytes[start + 2].toInt() and 0xFF) shl 8) or
                                 (bytes[start + 3].toInt() and 0xFF)).toDouble()
                        val dy = (((bytes[start + 4].toInt() and 0xFF) shl 24) or
                                 ((bytes[start + 5].toInt() and 0xFF) shl 16) or
                                 ((bytes[start + 6].toInt() and 0xFF) shl 8) or
                                 (bytes[start + 7].toInt() and 0xFF)).toDouble()
                        val dz = (((bytes[start + 8].toInt() and 0xFF) shl 24) or
                                 ((bytes[start + 9].toInt() and 0xFF) shl 16) or
                                 ((bytes[start + 10].toInt() and 0xFF) shl 8) or
                                 (bytes[start + 11].toInt() and 0xFF)).toDouble()
                        Triple(dx, dy, dz)
                    } else null
                } else null

                val d = if (dimIdx != -1) {
                    val start = dimIdx + 1 + 2 + 9 // type(1) + nameLen(2) + "dimension"(9)
                    if (start + 2 <= bytes.size) {
                        val len = ((bytes[start].toInt() and 0xFF) shl 8) or (bytes[start + 1].toInt() and 0xFF)
                        if (start + 2 + len <= bytes.size) {
                            String(bytes, start + 2, len)
                        } else null
                    } else null
                } else null

                if (p != null) {
                    PlayerLocation(p.first, p.second, p.third, d ?: "minecraft:overworld")
                } else null
            } else null

            val invIndex = indexOfTag(bytes, "Inventory", 9)
            val inventory = if (invIndex != -1) {
                // Skip tag header to reach list content
                parseBinaryInventory(bytes, invIndex + 1 + 2 + 9 + 1 + 4)
            } else emptyList()
            
            PlayerLiveSnapshot(
                currentPos = currentPos?.copy(dimension = dimension),
                respawnPos = respawnPos,
                lastDeathPos = lastDeathPos,
                health = health,
                hunger = hunger,
                inventory = inventory
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Modifies player data file while offline.
     * Returns true if the update was successful.
     */
    fun updatePlayerData(datFile: File, updates: Map<String, Any>): Boolean {
        if (!datFile.exists()) return false
        var success = false
        try {
            val bytes = GZIPInputStream(FileInputStream(datFile)).use { it.readBytes() }
            var mutableBytes = bytes.copyOf()
            var modified = false
            
            updates.forEach { (name, value) ->
                when (value) {
                    is Float -> {
                        val idx = indexOfTag(mutableBytes, name, 5)
                        if (idx != -1) {
                            val start = idx + 1 + 2 + name.length
                            if (start + 4 <= mutableBytes.size) {
                                val bits = java.lang.Float.floatToIntBits(value)
                                mutableBytes[start] = (bits shr 24).toByte()
                                mutableBytes[start + 1] = (bits shr 16).toByte()
                                mutableBytes[start + 2] = (bits shr 8).toByte()
                                mutableBytes[start + 3] = bits.toByte()
                                modified = true
                            }
                        }
                    }
                    is Int -> {
                        val idx = indexOfTag(mutableBytes, name, 3)
                        if (idx != -1) {
                            val start = idx + 1 + 2 + name.length
                            if (start + 4 <= mutableBytes.size) {
                                mutableBytes[start] = (value shr 24).toByte()
                                mutableBytes[start + 1] = (value shr 16).toByte()
                                mutableBytes[start + 2] = (value shr 8).toByte()
                                mutableBytes[start + 3] = value.toByte()
                                modified = true
                            }
                        }
                    }
                    is PlayerLocation -> {
                        // 1. Update Pos (List of 3 Doubles)
                        val posIdx = indexOfTag(mutableBytes, "Pos", 9)
                        if (posIdx != -1) {
                            val start = posIdx + 1 + 2 + 3 + 1 + 4
                            if (start + 24 <= mutableBytes.size) {
                                val xBits = java.lang.Double.doubleToRawLongBits(value.x)
                                for (b in 0..7) {
                                    mutableBytes[start + b] = (xBits shr (56 - b * 8)).toByte()
                                }
                                val yBits = java.lang.Double.doubleToRawLongBits(value.y)
                                for (b in 0..7) {
                                    mutableBytes[start + 8 + b] = (yBits shr (56 - b * 8)).toByte()
                                }
                                val zBits = java.lang.Double.doubleToRawLongBits(value.z)
                                for (b in 0..7) {
                                    mutableBytes[start + 16 + b] = (zBits shr (56 - b * 8)).toByte()
                                }
                                modified = true
                            }
                        }
                        
                        // 2. Update Dimension (String)
                        val dimIdx = indexOfTag(mutableBytes, "Dimension", 8)
                        if (dimIdx != -1) {
                            val start = dimIdx + 1 + 2 + 9 // "Dimension".length = 9
                            if (start + 2 <= mutableBytes.size) {
                                val oldLen = ((mutableBytes[start].toInt() and 0xFF) shl 8) or (mutableBytes[start + 1].toInt() and 0xFF)
                                val oldEnd = start + 2 + oldLen
                                if (oldEnd <= mutableBytes.size) {
                                    val newBytes = value.dimension.toByteArray(Charsets.UTF_8)
                                    val newLen = newBytes.size
                                    
                                    val prefix = mutableBytes.copyOfRange(0, start)
                                    val lenBytes = byteArrayOf(
                                        (newLen shr 8).toByte(),
                                        newLen.toByte()
                                    )
                                    val suffix = mutableBytes.copyOfRange(oldEnd, mutableBytes.size)
                                    
                                    mutableBytes = prefix + lenBytes + newBytes + suffix
                                    modified = true
                                }
                            }
                        }
                    }
                }
            }
            
            if (modified) {
                GZIPOutputStream(FileOutputStream(datFile)).use { it.write(mutableBytes) }
                success = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return success
    }

    /**
     * Parses world spawn from level.dat
     */
    fun parseLevelData(levelFile: File): PlayerLocation? {
        if (!levelFile.exists()) return null
        return try {
            val bytes = GZIPInputStream(FileInputStream(levelFile)).use { it.readBytes() }
            val sx = findIntTag(bytes, "SpawnX")
            val sy = findIntTag(bytes, "SpawnY")
            val sz = findIntTag(bytes, "SpawnZ")
            if (sx != null && sy != null && sz != null) {
                PlayerLocation(sx.toDouble(), sy.toDouble(), sz.toDouble(), "minecraft:overworld")
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun parseLevelDay(levelFile: File): Long? {
        if (!levelFile.exists()) return null
        return try {
            val bytes = GZIPInputStream(FileInputStream(levelFile)).use { it.readBytes() }
            // "Time" is the cumulative world age in ticks.
            // "DayTime" is the current day-cycle position and can be very small on an old world.
            val worldTime = findLongTag(bytes, "Time") ?: findLongTag(bytes, "DayTime") ?: return null
            (worldTime / 24000L).coerceAtLeast(0L)
        } catch (e: Exception) {
            null
        }
    }

    private fun indexOfTag(data: ByteArray, name: String, type: Byte): Int {
        val nameBytes = name.toByteArray()
        for (i in 0 until data.size - nameBytes.size - 3) {
            if (data[i] == type) {
                val len = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i + 2].toInt() and 0xFF)
                if (len == nameBytes.size) {
                    var match = true
                    for (j in nameBytes.indices) {
                        if (data[i + 3 + j] != nameBytes[j]) {
                            match = false
                            break
                        }
                    }
                    if (match) return i
                }
            }
        }
        return -1
    }

    private fun readDouble(data: ByteArray, offset: Int): Double {
        val bits = ((data[offset].toLong() and 0xFF) shl 56) or
                ((data[offset + 1].toLong() and 0xFF) shl 48) or
                ((data[offset + 2].toLong() and 0xFF) shl 40) or
                ((data[offset + 3].toLong() and 0xFF) shl 32) or
                ((data[offset + 4].toLong() and 0xFF) shl 24) or
                ((data[offset + 5].toLong() and 0xFF) shl 16) or
                ((data[offset + 6].toLong() and 0xFF) shl 8) or
                ((data[offset + 7].toLong() and 0xFF))
        return Double.fromBits(bits)
    }

    private fun findIntTag(data: ByteArray, name: String): Int? {
        val idx = indexOfTag(data, name, 3)
        if (idx == -1) return null
        val start = idx + 1 + 2 + name.length
        return if (start + 4 <= data.size) {
            ((data[start].toInt() and 0xFF) shl 24) or
                    ((data[start + 1].toInt() and 0xFF) shl 16) or
                    ((data[start + 2].toInt() and 0xFF) shl 8) or
                    (data[start + 3].toInt() and 0xFF)
        } else null
    }

    private fun findLongTag(data: ByteArray, name: String): Long? {
        val idx = indexOfTag(data, name, 4)
        if (idx == -1) return null
        val start = idx + 1 + 2 + name.length
        return if (start + 8 <= data.size) {
            ((data[start].toLong() and 0xFF) shl 56) or
                ((data[start + 1].toLong() and 0xFF) shl 48) or
                ((data[start + 2].toLong() and 0xFF) shl 40) or
                ((data[start + 3].toLong() and 0xFF) shl 32) or
                ((data[start + 4].toLong() and 0xFF) shl 24) or
                ((data[start + 5].toLong() and 0xFF) shl 16) or
                ((data[start + 6].toLong() and 0xFF) shl 8) or
                (data[start + 7].toLong() and 0xFF)
        } else null
    }

    private fun findFloatTag(data: ByteArray, name: String): Float? {
        val idx = indexOfTag(data, name, 5)
        if (idx == -1) return null
        val start = idx + 1 + 2 + name.length
        return if (start + 4 <= data.size) {
            java.lang.Float.intBitsToFloat(
                ((data[start].toInt() and 0xFF) shl 24) or
                        ((data[start + 1].toInt() and 0xFF) shl 16) or
                        ((data[start + 2].toInt() and 0xFF) shl 8) or
                        (data[start + 3].toInt() and 0xFF)
            )
        } else null
    }

    private fun findStringTag(data: ByteArray, name: String): String? {
        val idx = indexOfTag(data, name, 8)
        if (idx == -1) return null
        val start = idx + 1 + 2 + name.length
        if (start + 2 > data.size) return null
        val len = ((data[start].toInt() and 0xFF) shl 8) or (data[start + 1].toInt() and 0xFF)
        return if (start + 2 + len <= data.size) String(data, start + 2, len) else null
    }

    private fun parseBinaryInventory(data: ByteArray, listStart: Int): List<InventoryItem> {
        val items = mutableListOf<InventoryItem>()
        var current = listStart
        val end = (listStart + 10000).coerceAtMost(data.size)
        
        android.util.Log.d("INV", "Scanning binary inventory starting at $listStart, total size ${data.size}")
        
        while (current < end - 15) {
            // Scan for "Slot", "id", "Count" in any order within a reasonable distance
            val slotIdx = indexOfTagInRange(data, "Slot", 1, current, end)
            if (slotIdx == -1) break
            
            // Look for 'id' and 'Count' within 100 bytes of 'Slot'
            val windowEnd = (slotIdx + 100).coerceAtMost(end)
            val idIdx = indexOfTagInRange(data, "id", 8, current, windowEnd)
            val countIdx = indexOfTagInRange(data, "Count", 1, current, windowEnd)
            
            if (idIdx != -1 && countIdx != -1) {
                try {
                    val slot = data[slotIdx + 1 + 2 + 4].toInt()
                    val idLen = ((data[idIdx + 1 + 2 + 2].toInt() and 0xFF) shl 8) or (data[idIdx + 1 + 2 + 2 + 1].toInt() and 0xFF)
                    val id = String(data, idIdx + 1 + 2 + 2 + 2, idLen)
                    val count = data[countIdx + 1 + 2 + 5].toInt()
                    
                    if (id.isNotBlank()) {
                        android.util.Log.d("INV", "Found item: $id x$count in slot $slot")
                        items.add(InventoryItem(slot, id, count.coerceAtLeast(1)))
                    }
                    current = Math.max(slotIdx, Math.max(idIdx, countIdx)) + 5
                } catch (e: Exception) {
                    current = slotIdx + 5
                }
            } else {
                current = slotIdx + 5
            }
        }
        return items
    }

    private fun indexOfTagInRange(data: ByteArray, name: String, type: Byte, start: Int, end: Int): Int {
        val nameBytes = name.toByteArray()
        for (i in start until end - nameBytes.size - 3) {
            if (data[i] == type) {
                val len = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i + 2].toInt() and 0xFF)
                if (len == nameBytes.size) {
                    var match = true
                    for (j in nameBytes.indices) {
                        if (data[i + 3 + j] != nameBytes[j]) {
                            match = false
                            break
                        }
                    }
                    if (match) return i
                }
            }
        }
        return -1
    }

    fun parseLastDeathLocation(output: String): PlayerLocation? {
        val dimensionMatch = Regex("dimension\\s*[:=]\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(output)
            ?.groupValues
            ?.getOrNull(1)
        val position = parseBlockPosition(output) ?: parsePosition(output) ?: return null
        return PlayerLocation(
            x = position.first,
            y = position.second,
            z = position.third,
            dimension = dimensionMatch ?: parseDimension(output)
        )
    }

    fun cleanPaperDatapack(levelFile: File, onOutput: (String) -> Unit): Boolean {
        if (!levelFile.exists()) return false
        return try {
            val bytes = GZIPInputStream(FileInputStream(levelFile)).use { it.readBytes() }
            val dpIdx = indexOfTag(bytes, "DataPacks", 10)
            if (dpIdx == -1) {
                onOutput("[PocketCraft] NBT check: DataPacks tag not found.")
                return false
            }
            val enabledIdx = indexOfTagInRange(bytes, "Enabled", 9, dpIdx + 12, bytes.size)
            if (enabledIdx == -1) {
                onOutput("[PocketCraft] NBT check: Enabled datapacks tag not found.")
                return false
            }
            
            // Check list element type is TagString (8)
            val elemType = bytes[enabledIdx + 10]
            if (elemType.toInt() != 8) {
                onOutput("[PocketCraft] NBT check: Enabled datapacks element type is not String ($elemType)")
                return false
            }
            
            val listSizeOffset = enabledIdx + 11
            val listSize = ((bytes[listSizeOffset].toInt() and 0xFF) shl 24) or
                           ((bytes[listSizeOffset + 1].toInt() and 0xFF) shl 16) or
                           ((bytes[listSizeOffset + 2].toInt() and 0xFF) shl 8) or
                           (bytes[listSizeOffset + 3].toInt() and 0xFF)
            
            var currentPos = enabledIdx + 15
            var foundPaperIdx = -1
            var paperStrLen = 0
            
            for (i in 0 until listSize) {
                if (currentPos + 2 > bytes.size) break
                val strLen = ((bytes[currentPos].toInt() and 0xFF) shl 8) or (bytes[currentPos + 1].toInt() and 0xFF)
                if (currentPos + 2 + strLen > bytes.size) break
                val strVal = String(bytes, currentPos + 2, strLen, Charsets.UTF_8)
                if (strVal == "paper" || strVal == "pack:paper") {
                    foundPaperIdx = currentPos
                    paperStrLen = strLen
                    break
                }
                currentPos += 2 + strLen
            }
            
            if (foundPaperIdx != -1) {
                onOutput("[PocketCraft] NBT check: Found 'paper' datapack in Enabled list. Removing it...")
                val newListSize = listSize - 1
                val sizeBytes = byteArrayOf(
                    (newListSize shr 24).toByte(),
                    (newListSize shr 16).toByte(),
                    (newListSize shr 8).toByte(),
                    newListSize.toByte()
                )
                
                val prefix = bytes.copyOfRange(0, listSizeOffset)
                val middle = bytes.copyOfRange(enabledIdx + 15, foundPaperIdx)
                val suffix = bytes.copyOfRange(foundPaperIdx + 2 + paperStrLen, bytes.size)
                
                val newBytes = prefix + sizeBytes + middle + suffix
                
                GZIPOutputStream(FileOutputStream(levelFile)).use { it.write(newBytes) }
                onOutput("[PocketCraft] NBT check: Successfully removed 'paper' datapack from level.dat.")
                return true
            } else {
                onOutput("[PocketCraft] NBT check: 'paper' datapack not found in Enabled list.")
                return false
            }
        } catch (e: Exception) {
            onOutput("[PocketCraft] NBT clean failed: ${e.message}")
            false
        }
    }
}
