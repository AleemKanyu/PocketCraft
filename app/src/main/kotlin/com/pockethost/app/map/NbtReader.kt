package com.pockethost.app.map

/**
 * Minimal big-endian NBT reader, just enough to walk chunk NBT.
 *
 * Deliberately not a general-purpose library: it decodes a chunk into plain maps and lists
 * and nothing else, because the map renderer only ever needs `Heightmaps`, `yPos` and
 * `sections[].block_states`. Keeping it small keeps per-chunk allocation low, which matters
 * when thousands of chunks stream through it on a phone.
 */
object NbtReader {
    private const val TAG_END = 0
    private const val TAG_BYTE = 1
    private const val TAG_SHORT = 2
    private const val TAG_INT = 3
    private const val TAG_LONG = 4
    private const val TAG_FLOAT = 5
    private const val TAG_DOUBLE = 6
    private const val TAG_BYTE_ARRAY = 7
    private const val TAG_STRING = 8
    private const val TAG_LIST = 9
    private const val TAG_COMPOUND = 10
    private const val TAG_INT_ARRAY = 11
    private const val TAG_LONG_ARRAY = 12

    /** Cursor over the decompressed chunk bytes. */
    private class Cursor(val buf: ByteArray) {
        var pos = 0

        fun u8(): Int = buf[pos++].toInt() and 0xFF
        fun i8(): Byte = buf[pos++]

        fun u16(): Int {
            val v = ((buf[pos].toInt() and 0xFF) shl 8) or (buf[pos + 1].toInt() and 0xFF)
            pos += 2
            return v
        }

        fun i16(): Short = u16().toShort()

        fun i32(): Int {
            val v = ((buf[pos].toInt() and 0xFF) shl 24) or
                ((buf[pos + 1].toInt() and 0xFF) shl 16) or
                ((buf[pos + 2].toInt() and 0xFF) shl 8) or
                (buf[pos + 3].toInt() and 0xFF)
            pos += 4
            return v
        }

        fun i64(): Long {
            var v = 0L
            for (i in 0..7) v = (v shl 8) or (buf[pos + i].toLong() and 0xFFL)
            pos += 8
            return v
        }

        fun string(): String {
            val n = u16()
            val s = String(buf, pos, n, Charsets.UTF_8)
            pos += n
            return s
        }
    }

    /** Parses a chunk's root compound. Returns null if the bytes are not valid NBT. */
    fun parse(bytes: ByteArray): Map<String, Any>? = runCatching {
        val c = Cursor(bytes)
        if (c.u8() != TAG_COMPOUND) return null
        c.string() // root name, conventionally empty
        @Suppress("UNCHECKED_CAST")
        readPayload(c, TAG_COMPOUND) as Map<String, Any>
    }.getOrNull()

    private fun readPayload(c: Cursor, type: Int): Any? = when (type) {
        TAG_BYTE -> c.i8()
        TAG_SHORT -> c.i16()
        TAG_INT -> c.i32()
        TAG_LONG -> c.i64()
        TAG_FLOAT -> Float.fromBits(c.i32())
        TAG_DOUBLE -> Double.fromBits(c.i64())
        TAG_BYTE_ARRAY -> ByteArray(c.i32()).also { c.buf.copyInto(it, 0, c.pos, c.pos + it.size); c.pos += it.size }
        TAG_STRING -> c.string()
        TAG_LIST -> {
            val elementType = c.u8()
            val n = c.i32()
            val list = ArrayList<Any?>(if (n in 0..4096) n else 0)
            for (i in 0 until n) list.add(readPayload(c, elementType))
            list
        }
        TAG_COMPOUND -> {
            val map = HashMap<String, Any>()
            while (true) {
                val t = c.u8()
                if (t == TAG_END) break
                // Name must be read before the payload; reading them in the other order
                // would consume the value bytes first and misalign every later tag.
                val name = c.string()
                readPayload(c, t)?.let { map[name] = it }
            }
            map
        }
        TAG_INT_ARRAY -> IntArray(c.i32()) { c.i32() }
        TAG_LONG_ARRAY -> LongArray(c.i32()) { c.i64() }
        else -> null
    }

    /**
     * Unpacks a Minecraft 1.16+ packed index array. Entries never straddle a long boundary:
     * each long holds floor(64 / bitsPerEntry) entries and the spare high bits are padding.
     */
    fun unpack(longs: LongArray, count: Int, bitsPerEntry: Int): IntArray {
        val out = IntArray(count)
        if (bitsPerEntry <= 0 || longs.isEmpty()) return out
        val perLong = 64 / bitsPerEntry
        val mask = (1L shl bitsPerEntry) - 1L
        for (i in 0 until count) {
            val li = i / perLong
            if (li >= longs.size) break
            out[i] = ((longs[li] ushr ((i % perLong) * bitsPerEntry)) and mask).toInt()
        }
        return out
    }
}
