package app.duenorth.budget.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pending bank rows, the way Actual keeps them: a synced row carries the bank's copy in `raw_synced_data`, and
 * one the bank had not posted (`booked: false`) comes in not cleared. It stays pending until it is cleared, which
 * the sync does when the bank posts it, or the person does by hand. A locked row is never pending.
 */
object BankPending {
    /** A pending row and its posted copy sit this close, as Actual's own matching allows. */
    const val POSTED_WITHIN_DAYS = 7

    private val json = Json { ignoreUnknownKeys = true }

    fun isPending(
        rawSyncedData: String?,
        cleared: Boolean,
        reconciled: Boolean,
    ): Boolean = !cleared && !reconciled && unbooked(rawSyncedData)

    /** The bank's copy says it had not posted the row. */
    fun unbooked(rawSyncedData: String?): Boolean {
        if (rawSyncedData.isNullOrBlank() || !rawSyncedData.contains("booked")) return false
        val row = runCatching { json.parseToJsonElement(rawSyncedData) as? JsonObject }.getOrNull() ?: return false
        return row["booked"]?.jsonPrimitive?.booleanOrNull == false
    }
}
