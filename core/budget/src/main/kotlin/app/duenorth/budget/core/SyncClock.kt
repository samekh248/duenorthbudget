package app.duenorth.budget.core

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Actual's hybrid logical clock. The text form sorts in time order, so a server
 * can ask for messages with `timestamp > since` using a string compare.
 */
class SyncClock(
    node: String,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val nodeId = node.padStart(NODE_LENGTH, '0').takeLast(NODE_LENGTH)
    private var millis: Long = 0
    private var counter: Int = 0

    @Synchronized
    fun send(): String {
        val physical = now()
        val nextMillis = maxOf(millis, physical)
        val nextCounter = if (millis == nextMillis) counter + 1 else 0
        check(nextMillis - physical <= MAX_DRIFT) { "clock drift" }
        check(nextCounter <= MAX_COUNTER) { "clock overflow" }
        millis = nextMillis
        counter = nextCounter
        return format(nextMillis, nextCounter, nodeId)
    }

    /** Move this clock forward of [stamp] when that stamp is one we already used. */
    @Synchronized
    fun restoreIfBehind(stamp: String?) {
        val text = stamp ?: return
        val parsed = parse(text) ?: return
        val current = format(millis, counter, nodeId)
        if (text <= current) return
        millis = parsed.millis
        counter = parsed.counter
    }

    @Synchronized
    fun observe(stamp: String) {
        val parsed = parse(stamp) ?: return
        val physical = now()
        if (parsed.millis - physical > MAX_DRIFT) return
        val nextMillis = maxOf(millis, physical, parsed.millis)
        val nextCounter =
            when {
                nextMillis == millis && nextMillis == parsed.millis -> maxOf(counter, parsed.counter) + 1
                nextMillis == millis -> counter + 1
                nextMillis == parsed.millis -> parsed.counter + 1
                else -> 0
            }
        if (nextMillis - physical > MAX_DRIFT || nextCounter > MAX_COUNTER) return
        millis = nextMillis
        counter = nextCounter
    }

    private data class Parsed(
        val millis: Long,
        val counter: Int,
    )

    companion object {
        const val ZERO = "1970-01-01T00:00:00.000Z-0000-0000000000000000"
        private const val NODE_LENGTH = 16
        private const val MAX_COUNTER = 0xFFFF
        private const val MAX_DRIFT = 5 * 60 * 1000L
        private val formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

        fun format(
            millis: Long,
            counter: Int,
            node: String,
        ): String {
            val time = formatter.format(Instant.ofEpochMilli(millis))
            val count = counter.toString(16).uppercase().padStart(4, '0')
            val id = node.padStart(NODE_LENGTH, '0').takeLast(NODE_LENGTH)
            return "$time-$count-$id"
        }

        private fun parse(stamp: String?): Parsed? {
            if (stamp == null) return null
            val parts = stamp.split('-')
            if (parts.size != 5) return null
            val millis = runCatching { Instant.parse(parts.take(3).joinToString("-")).toEpochMilli() }.getOrNull() ?: return null
            val counter = parts[3].toIntOrNull(16) ?: return null
            if (counter !in 0..MAX_COUNTER || parts[4].length > NODE_LENGTH) return null
            return Parsed(millis, counter)
        }
    }
}
