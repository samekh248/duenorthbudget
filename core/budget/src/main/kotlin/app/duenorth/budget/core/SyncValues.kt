package app.duenorth.budget.core

/**
 * Actual's message value encoding: `0:` is null, `N:<number>`, `S:<text>`.
 * See `serializeValue` / `deserializeValue` in Actual's sync code.
 */
object SyncValues {
    /** Text form a book passes to [SyncSchema.recordLocal]. */
    fun raw(value: Any?): String? =
        when (value) {
            null -> null
            is Boolean -> if (value) "1" else "0"
            is Double -> number(value)
            is Float -> number(value.toDouble())
            is Number -> value.toLong().toString()
            else -> value.toString()
        }

    fun serialize(
        column: String,
        raw: String?,
    ): String {
        if (raw == null) return "0:"
        if (column !in SyncSchema.numericColumns) return "S:$raw"
        val parsed = raw.toDoubleOrNull() ?: return "0:"
        return "N:" + number(parsed)
    }

    /** Values without a type prefix come from phone messages written before spec 010. */
    fun deserialize(wire: String?): String? {
        if (wire == null) return null
        return when {
            wire.startsWith("0:") -> null
            wire.startsWith("N:") -> wire.substring(2).toDoubleOrNull()?.let(::number)
            wire.startsWith("S:") -> wire.substring(2)
            else -> wire
        }
    }

    /** A stored message value as it goes on the wire, prefixing legacy raw values. */
    fun wire(
        column: String,
        stored: String?,
    ): String {
        if (stored != null && (stored.startsWith("0:") || stored.startsWith("N:") || stored.startsWith("S:"))) {
            return stored
        }
        return serialize(column, stored)
    }

    /** Comparable form of a cell, so 3, 3.0 and "3" agree for numeric columns. */
    fun canonical(
        column: String,
        value: Any?,
    ): String? {
        val text = raw(value) ?: return null
        if (column !in SyncSchema.numericColumns) return text
        return text.toDoubleOrNull()?.let(::number) ?: text
    }

    private fun number(value: Double): String =
        if (value % 1.0 == 0.0 && kotlin.math.abs(value) < MAX_EXACT) {
            value.toLong().toString()
        } else {
            value.toString()
        }

    private const val MAX_EXACT = 9.0e15
}
