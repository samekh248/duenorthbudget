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

    /**
     * SQL that is true when the transaction aliased [t] came from the bank and the bank has not posted it yet: the
     * same rule as [isPending]. A split's parts go by their parent, which holds the bank's copy and the cleared flag.
     */
    fun pendingSql(t: String): String =
        """
        EXISTS (
            SELECT 1 FROM transactions src
            WHERE src.id = CASE WHEN IFNULL($t.isChild, 0) = 1 THEN $t.parent_id ELSE $t.id END
                AND IFNULL(src.cleared, 0) = 0
                AND IFNULL(src.reconciled, 0) = 0
                AND REPLACE(REPLACE(src.raw_synced_data, ' ', ''), char(10), '') LIKE '%"booked":false%'
        )
        """.trimIndent()

    /**
     * SQL that is true when the transaction aliased [t] is pending money coming in. Such a row sits in the account,
     * but the budget leaves it out (no income for to budget, no refund in a category) until the bank posts it or the
     * person clears it. Money going out still counts the moment it shows up.
     */
    fun pendingInflowSql(t: String): String = "($t.amount > 0 AND ${pendingSql(t)})"
}
