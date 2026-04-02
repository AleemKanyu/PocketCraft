package com.pocketcraft.server.service

data class InventoryItem(
    val slot: Int,
    val id: String,
    val count: Int
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
        val inventoryMatch = Regex("Inventory\\s*:\\s*\\[(.*)]", RegexOption.DOT_MATCHES_ALL)
            .find(output) ?: return items

        val inventoryContent = inventoryMatch.groupValues[1]
        val itemPattern = Regex("\\{([^{}]+)}")
        itemPattern.findAll(inventoryContent).forEach { match ->
            val entry = match.value
            val slot = Regex("Slot\\s*:\\s*(-?\\d+)b", RegexOption.IGNORE_CASE)
                .find(entry)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: return@forEach
            val id = Regex("id\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                .find(entry)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                .orEmpty()
            if (id.isBlank()) return@forEach

            val count = Regex("Count\\s*:\\s*(\\d+)b", RegexOption.IGNORE_CASE)
                .find(entry)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: 1

            items += InventoryItem(slot = slot, id = id, count = count.coerceAtLeast(1))
        }

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

    fun parseFloatValue(output: String): Float? {
        val match = Regex("""(-?\d+(?:\.\d+)?)""").find(output) ?: return null
        return match.groupValues[1].toFloatOrNull()
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
}
