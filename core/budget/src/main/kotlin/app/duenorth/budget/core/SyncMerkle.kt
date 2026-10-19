package app.duenorth.budget.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/**
 * Actual's merkle trie (`@actual-app/crdt` merkle.ts), so the phone can tell when it and the
 * server hold different messages. Keys are base-3 digits of the minute a timestamp falls in;
 * each node's hash is the XOR of the murmur hashes of every timestamp under it.
 */
class SyncMerkle private constructor(
    var hash: Int = 0,
    val children: java.util.TreeMap<Char, SyncMerkle> = java.util.TreeMap(),
) {
    fun insert(timestamp: String) {
        val millis = millisOf(timestamp) ?: return
        val stamp = murmur3(timestamp)
        val key = java.lang.Long.toString(millis / 1000 / 60, 3)
        var node = this
        node.hash = node.hash xor stamp
        for (digit in key) {
            node = node.children.getOrPut(digit) { SyncMerkle() }
            node.hash = node.hash xor stamp
        }
    }

    /** Keeps the newest [keep] children at every level, as the server does after each batch. */
    fun prune(keep: Int = 2): SyncMerkle {
        if (hash == 0) return this
        val next = SyncMerkle(hash)
        children.keys
            .toList()
            .takeLast(keep)
            .forEach { digit -> next.children[digit] = children.getValue(digit).prune(keep) }
        return next
    }

    fun toJson(): String = json().toString()

    private fun json(): JsonObject =
        buildJsonObject {
            children.forEach { (digit, child) -> put(digit.toString(), child.json()) }
            put("hash", JsonPrimitive(hash))
        }

    companion object {
        private val parser = Json { ignoreUnknownKeys = true }

        fun build(timestamps: Iterable<String>): SyncMerkle {
            val trie = SyncMerkle()
            timestamps.forEach(trie::insert)
            return trie.prune()
        }

        /** The server's merkle text, or null when it is missing or not a trie. */
        fun parse(text: String): SyncMerkle? {
            if (text.isBlank()) return null
            val root = runCatching { parser.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
            return runCatching { fromJson(root) }.getOrNull()
        }

        private fun fromJson(node: JsonObject): SyncMerkle {
            val trie = SyncMerkle((node["hash"] as? JsonPrimitive)?.longOrNull?.toInt() ?: 0)
            node.forEach { (key, value) ->
                if (key.length == 1 && key[0] in '0'..'2' && value is JsonObject) trie.children[key[0]] = fromJson(value)
            }
            return trie
        }

        /**
         * The earliest time, in epoch millis, from which [a] and [b] may hold different messages;
         * null when they match.
         */
        fun diff(
            a: SyncMerkle,
            b: SyncMerkle,
        ): Long? {
            if (a.hash == b.hash) return null
            var left = a
            var right = b
            val key = StringBuilder()
            while (true) {
                val digits = (left.children.keys + right.children.keys).sorted()
                var found: Char? = null
                for (digit in digits) {
                    val one = left.children[digit] ?: break
                    val two = right.children[digit] ?: break
                    if (one.hash != two.hash) {
                        found = digit
                        break
                    }
                }
                if (found == null) return keyToMillis(key.toString())
                key.append(found)
                left = left.children[found] ?: SyncMerkle()
                right = right.children[found] ?: SyncMerkle()
            }
        }

        /** A `since` for the sync request that asks for every message from [millis] on. */
        fun since(millis: Long): String = SyncClock.format(millis, 0, "0")

        private fun keyToMillis(key: String): Long {
            val full = key.padEnd(16, '0')
            return java.lang.Long.parseLong(full, 3) * 1000 * 60
        }

        private fun millisOf(timestamp: String): Long? {
            val parts = timestamp.split('-')
            if (parts.size != 5) return null
            return runCatching { Instant.parse(parts.take(3).joinToString("-")).toEpochMilli() }.getOrNull()
        }

        /** murmurhash3 x86 32-bit, seed 0, over the timestamp's (ASCII) characters, as the `murmurhash` package. */
        internal fun murmur3(text: String): Int {
            val data = text.toByteArray(Charsets.ISO_8859_1)
            val c1 = -0x3361d2af
            val c2 = 0x1b873593
            var h1 = 0
            val blocks = data.size / 4
            for (i in 0 until blocks) {
                val offset = i * 4
                var k1 =
                    (data[offset].toInt() and 0xff) or
                        ((data[offset + 1].toInt() and 0xff) shl 8) or
                        ((data[offset + 2].toInt() and 0xff) shl 16) or
                        ((data[offset + 3].toInt() and 0xff) shl 24)
                k1 *= c1
                k1 = Integer.rotateLeft(k1, 15)
                k1 *= c2
                h1 = h1 xor k1
                h1 = Integer.rotateLeft(h1, 13)
                h1 = h1 * 5 + -0x19ab949c
            }
            var k1 = 0
            val tail = blocks * 4
            when (data.size and 3) {
                3 -> {
                    k1 = k1 xor ((data[tail + 2].toInt() and 0xff) shl 16)
                    k1 = k1 xor ((data[tail + 1].toInt() and 0xff) shl 8)
                    k1 = k1 xor (data[tail].toInt() and 0xff)
                }
                2 -> {
                    k1 = k1 xor ((data[tail + 1].toInt() and 0xff) shl 8)
                    k1 = k1 xor (data[tail].toInt() and 0xff)
                }
                1 -> k1 = k1 xor (data[tail].toInt() and 0xff)
            }
            if (data.size and 3 != 0) {
                k1 *= c1
                k1 = Integer.rotateLeft(k1, 15)
                k1 *= c2
                h1 = h1 xor k1
            }
            h1 = h1 xor data.size
            h1 = h1 xor (h1 ushr 16)
            h1 *= -0x7a143595
            h1 = h1 xor (h1 ushr 13)
            h1 *= -0x3d4d51cb
            h1 = h1 xor (h1 ushr 16)
            return h1
        }
    }
}
