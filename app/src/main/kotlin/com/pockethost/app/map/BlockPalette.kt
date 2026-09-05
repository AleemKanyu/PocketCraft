package com.pockethost.app.map

/**
 * Flat per-block colours for the map renderer.
 *
 * This is what makes the renderer cheap enough for a phone. A full renderer resolves each
 * block's model and samples its textures; here every block collapses to a single averaged
 * colour, so drawing a column costs one lookup instead of building geometry.
 *
 * A handful of exact entries cover the blocks that dominate a landscape, and ordered
 * substring rules catch the thousands of variants (every wood type, every colour of wool)
 * without listing them. Results are memoised because a region asks for the same few dozen
 * block names a quarter of a million times.
 */
object BlockPalette {

    /** Blocks that should never be drawn as a surface. */
    val AIR_BLOCKS = hashSetOf("air", "cave_air", "void_air", "barrier", "light")

    private val EXACT: Map<String, Int> = hashMapOf(
        "grass_block" to 0x7AB64B, "dirt" to 0x866043, "coarse_dirt" to 0x77543A,
        "podzol" to 0x5A3C1B, "mycelium" to 0x6F6066, "farmland" to 0x5B3D24,
        "dirt_path" to 0x947E47, "rooted_dirt" to 0x91674A, "mud" to 0x3C3336,
        "stone" to 0x7E7E7E, "cobblestone" to 0x7A7A7A, "deepslate" to 0x515157,
        "andesite" to 0x888888, "diorite" to 0xBFBFBF, "granite" to 0x9A6B54,
        "gravel" to 0x837F7E, "sand" to 0xDBD3A0, "red_sand" to 0xA95421,
        "sandstone" to 0xD7CD9E, "clay" to 0xA0A7B4, "tuff" to 0x6D6D66,
        "calcite" to 0xDFDEDA, "water" to 0x3A5FA8, "ice" to 0x8EB6EF,
        "packed_ice" to 0x8DB4EF, "blue_ice" to 0x74A8F7, "snow" to 0xF0F6F6,
        "snow_block" to 0xF0F6F6, "powder_snow" to 0xF7FAFA, "lava" to 0xE7630F,
        "magma_block" to 0x8E401F, "netherrack" to 0x6E2E2E, "soul_sand" to 0x513D30,
        "soul_soil" to 0x4B382C, "basalt" to 0x4C4B51, "blackstone" to 0x2C262C,
        "end_stone" to 0xDCDFA4, "obsidian" to 0x140C21, "bedrock" to 0x333333,
        "moss_block" to 0x597B2E, "glass" to 0xC8E4E8, "bricks" to 0x965A4A,
        "bookshelf" to 0x9B7B4C, "hay_block" to 0xA48C0B, "melon" to 0x6FA12A,
        "pumpkin" to 0xC57618, "carved_pumpkin" to 0xC57618, "sea_lantern" to 0xB0C7BE,
        "glowstone" to 0xB69254, "crying_obsidian" to 0x22064B, "amethyst_block" to 0x866BC3,
        "sculk" to 0x0D1C24, "cactus" to 0x0D7F24, "bamboo" to 0x77A42A,
        "netherite_block" to 0x443B3D, "ancient_debris" to 0x5C4038,
        "gilded_blackstone" to 0x513037, "honey_block" to 0xE0993B,
        "slime_block" to 0x76BE68, "dried_kelp_block" to 0x374822
    )

    /**
     * Ordered substring rules; first match wins, so put specific fragments before generic
     * ones (`deepslate_` before `_ore`, `_leaves` before `_log`).
     */
    private val RULES: List<Pair<String, Int>> = listOf(
        "_leaves" to 0x4A7A2E, "_log" to 0x6B512E, "_wood" to 0x6B512E,
        "_stem" to 0x6B512E, "_hyphae" to 0x6B512E, "_planks" to 0xB08A55,
        "_stairs" to 0x8A8278, "_slab" to 0x8A8278, "_wall" to 0x7A7A7A,
        "_fence" to 0xB08A55, "_door" to 0xA07B4A, "_trapdoor" to 0xA07B4A,
        "_concrete" to 0x8C8C8C, "_terracotta" to 0x965A43, "_glazed" to 0xB0A090,
        "_wool" to 0xD0D0D0, "_carpet" to 0xD0D0D0, "_bed" to 0xA03030,
        "_shulker_box" to 0x8A5E8A, "deepslate" to 0x515157, "_ore" to 0x7E7E7E,
        "_sandstone" to 0xD7CD9E, "_bricks" to 0x965A4A, "_copper" to 0xC06C50,
        "_glass" to 0xC8E4E8, "coral" to 0xC74A8E, "_mushroom" to 0xB57A5A,
        "water" to 0x3A5FA8, "lava" to 0xE7630F, "kelp" to 0x2E6B34,
        "seagrass" to 0x3E7A3A, "grass" to 0x7AB64B, "fern" to 0x5A8C3A,
        "flower" to 0xC06080, "tulip" to 0xC04040, "orchid" to 0xC060A0,
        "sapling" to 0x4A7A2E, "vine" to 0x3F6E28, "moss" to 0x597B2E,
        "crop" to 0x86A83A, "wheat" to 0xC2B050, "carrot" to 0xC77A1E,
        "potato" to 0xB0A040, "beetroot" to 0x9C3A2E, "sugar_cane" to 0x8FBE6B,
        "warped" to 0x1A7A77, "crimson" to 0x8B2D3A, "nether" to 0x6E2E2E,
        "snow" to 0xF0F6F6, "ice" to 0x8EB6EF, "sculk" to 0x0D1C24,
        "prismarine" to 0x5F9C8E, "purpur" to 0xA57AA5, "quartz" to 0xE3DED6,
        "dripstone" to 0x866C5C, "amethyst" to 0x866BC3, "candle" to 0xE0D0A0,
        "stone" to 0x7E7E7E, "iron" to 0xD8D8D8, "gold" to 0xF2CF4E,
        "diamond" to 0x5DECF5, "emerald" to 0x2CBF56, "lapis" to 0x1F479B,
        "redstone" to 0xAA1E19, "coal" to 0x2F2F2F
    )

    private const val DEFAULT_COLOR = 0x8A8A8A

    private val cache = HashMap<String, Int>(512)

    /** Returns 0xRRGGBB for a block id such as `minecraft:oak_leaves`. */
    fun colorOf(blockName: String): Int {
        cache[blockName]?.let { return it }

        val bare = blockName.substringAfterLast(':')
        var color = EXACT[bare]
        if (color == null) {
            for ((fragment, value) in RULES) {
                if (bare.contains(fragment)) {
                    color = value
                    break
                }
            }
        }
        val resolved = color ?: DEFAULT_COLOR
        cache[blockName] = resolved
        return resolved
    }

    fun isAir(blockName: String): Boolean = AIR_BLOCKS.contains(blockName.substringAfterLast(':'))
}
