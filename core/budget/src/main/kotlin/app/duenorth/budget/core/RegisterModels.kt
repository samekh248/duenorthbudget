package app.duenorth.budget.core

object RegisterCopy {
    const val ENTER_PAYEE = "enter a payee"
    const val ENTER_AMOUNT = "enter an amount"
    const val AMOUNT_ZERO = "amount cannot be zero"
    const val ENTER_DATE = "enter a date"
    const val CHOOSE_CATEGORY = "choose a category"
    const val CHOOSE_ACCOUNT = "choose an account"
    const val SPLIT_MUST_ADD = "split must add up"
    const val RECONCILE_WARN = "this reconciliation will no longer match"
    const val RECONCILE_CHANGE = "change a reconciled transaction?"
    const val NO_CATEGORY = "no category"
    const val SUGGESTED = "suggested"
    const val ALL_CATEGORIES = "all categories"
    const val SPLIT = "split"
    const val SCHEDULE = "schedule"
    const val DELETE = "delete"
    const val TRANSFER = "transfer"
    const val TRANSFER_TO = "transfer to"
    const val TRANSFER_FROM = "transfer from"
    const val OWED = "owed"
    const val SELECTED = "selected"
    const val CLEARED = "cleared"
    const val NOT_CLEARED = "not cleared"
    const val CLEARED_MARK = "c"
    const val CLEAR = "clear"
    const val UNCLEAR = "unclear"
    const val DONE = "done"
    const val NO_TRANSACTIONS = "no transactions"
    const val SEARCH = "search"
}

data class RegisterAccount(
    val id: String,
    val name: String,
    val offBudget: Boolean,
    val type: String,
    val closed: Boolean,
)

data class RegisterPart(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val amountMinor: Long,
)

data class RegisterRow(
    val id: String,
    val date: Int,
    val payee: String,
    val categoryId: String?,
    val categoryLabel: String,
    val amountMinor: Long,
    val note: String,
    val reconciled: Boolean,
    val transferId: String?,
    val partnerOffBudget: Boolean?,
    val partnerCategoryId: String?,
    val parent: Boolean,
    val parts: List<RegisterPart>,
    val cleared: Boolean = false,
    /** The account a transfer payee stands for; the payee line reads "transfer to/from" it. */
    val transferAccountId: String? = null,
)

data class CategoryChoice(
    val id: String,
    val name: String,
    val groupName: String,
    val income: Boolean,
    /** The category or its group is hidden in the budget (spec 013). */
    val hidden: Boolean = false,
)

data class AccountChoice(
    val id: String,
    val name: String,
    val offBudget: Boolean,
    val type: String,
    val closed: Boolean,
)

data class RegisterPage(
    val account: RegisterAccount,
    val balanceMinor: Long,
    val currency: CurrencySpec,
    val rows: List<RegisterRow>,
    val categories: List<CategoryChoice>,
    val accounts: List<AccountChoice>,
    /** Possible matches in this account, shown above the rows until matched or called not a match. */
    val matches: List<MatchPair> = emptyList(),
    /** What the transaction form's category list suggests first, by payee. */
    val suggestions: CategorySuggestions = CategorySuggestions.EMPTY,
) {
    fun matching(payeeQuery: String): List<RegisterRow> {
        val query = payeeQuery.trim()
        if (query.isEmpty()) return rows
        return rows.filter { it.payee.contains(query, ignoreCase = true) }
    }

    /** The rows [search] keeps, in register order. */
    fun searched(search: RegisterSearch): List<RegisterRow> = RegisterSearch.filter(rows, search, currency.decimals)
}

/** Which rows the register's search keeps besides the typed text. */
enum class RegisterShow(
    val label: String,
) {
    ALL("all"),
    NOT_CLEARED("not cleared"),
    NO_CATEGORY("no category"),
}

/**
 * The register's search: [query] matches payee, category, note, a split part's category, or an
 * amount; [show] narrows to rows not cleared or still missing a category.
 */
data class RegisterSearch(
    val query: String = "",
    val show: RegisterShow = RegisterShow.ALL,
) {
    val active: Boolean get() = query.isNotBlank() || show != RegisterShow.ALL

    companion object {
        const val HINT = "payee, category, note or amount"
        const val NOTHING = "nothing matches"

        fun filter(
            rows: List<RegisterRow>,
            search: RegisterSearch,
            decimals: Int,
        ): List<RegisterRow> {
            if (!search.active) return rows
            val query = search.query.trim()
            val amount = amountOf(query, decimals)
            return rows.filter { row ->
                shows(row, search.show) && (query.isEmpty() || hasText(row, query) || (amount != null && amount.matches(row)))
            }
        }

        /** "2 of 41", the caption over a search that keeps only some rows. */
        fun count(
            shown: Int,
            total: Int,
        ): String = "$shown of $total"

        private fun shows(
            row: RegisterRow,
            show: RegisterShow,
        ): Boolean =
            when (show) {
                RegisterShow.ALL -> true
                RegisterShow.NOT_CLEARED -> !row.cleared
                RegisterShow.NO_CATEGORY ->
                    row.categoryLabel == RegisterCopy.NO_CATEGORY || (row.parent && row.parts.any { it.categoryId.isBlank() })
            }

        private fun hasText(
            row: RegisterRow,
            query: String,
        ): Boolean =
            row.payee.contains(query, ignoreCase = true) ||
                row.categoryLabel.contains(query, ignoreCase = true) ||
                row.note.contains(query, ignoreCase = true) ||
                row.parts.any { it.categoryName.contains(query, ignoreCase = true) }

        /**
         * Reads [query] as an amount when it holds digits and no letters. "42.4" finds 42.40 exactly; "42"
         * finds every amount of 42 whole units (42.00 to 42.99). Either sign matches, and a split part's
         * amount counts. The last separator followed by no more than [decimals] digits is the decimal
         * point, so "1,234.50" and "1.234,50" both read as 1234.50.
         */
        internal fun amountOf(
            query: String,
            decimals: Int,
        ): AmountQuery? {
            if (query.none { it.isDigit() } || query.any { it.isLetter() }) return null
            val kept = query.filter { it.isDigit() || it == '.' || it == ',' }
            val point = kept.indexOfLast { it == '.' || it == ',' }
            val fraction = if (point >= 0) kept.substring(point + 1) else ""
            val decimal = point >= 0 && decimals > 0 && fraction.length in 1..decimals
            val whole = (if (decimal) kept.substring(0, point) else kept).filter { it.isDigit() }
            val units = whole.ifEmpty { "0" }.toLongOrNull() ?: return null
            val scale = (1..decimals).fold(1L) { acc, _ -> acc * 10 }
            return if (decimal) {
                AmountQuery(exact = units * scale + fraction.padEnd(decimals, '0').toLong(), scale = scale)
            } else {
                AmountQuery(units = units, scale = scale)
            }
        }
    }
}

/** A typed amount: an [exact] minor-unit value, or every value of so many whole [units]. */
internal data class AmountQuery(
    val exact: Long? = null,
    val units: Long? = null,
    val scale: Long,
) {
    fun matches(row: RegisterRow): Boolean = hits(row.amountMinor) || row.parts.any { hits(it.amountMinor) }

    private fun hits(minor: Long): Boolean {
        val size = kotlin.math.abs(minor)
        return if (exact != null) size == exact else size / scale == units
    }
}

data class CategoryTarget(
    val transactionId: String,
    val accountId: String,
    val payee: String,
    val amountMinor: Long,
    val currency: CurrencySpec,
    val categories: List<CategoryChoice>,
    /** The transaction's category now, which the picker opens on; null when it has none. */
    val categoryId: String? = null,
    /** Category ids the picker shows first, in their own group; empty hides the group. */
    val suggested: List<String> = emptyList(),
)

data class TransactionDraft(
    val id: String,
    val accountId: String,
    val date: Int,
    val payee: String,
    val amountMinor: Long,
    val categoryId: String?,
    val note: String?,
    val force: Boolean = false,
    val scheduleId: String? = null,
    /** A transfer: the other account, whose paired transaction this save adds, moves or keeps in step. */
    val transferAccountId: String? = null,
    /** The cleared flag the form set; null leaves it as it is (a new transaction comes in cleared, as in Actual). */
    val cleared: Boolean? = null,
    /**
     * The form's split lines: two or more split the transaction across them, empty puts a split
     * back to one category, and null leaves any split as it is.
     */
    val parts: List<SplitPart>? = null,
)

data class SplitPart(
    val categoryId: String,
    val amountMinor: Long,
)

sealed interface ForcedWrite {
    data class Save(
        val draft: TransactionDraft,
    ) : ForcedWrite

    data class Delete(
        val transactionId: String,
    ) : ForcedWrite

    data class Deletes(
        val transactionIds: List<String>,
    ) : ForcedWrite

    data class Category(
        val transactionId: String,
        val categoryId: String?,
    ) : ForcedWrite

    data class Categories(
        val assignments: Map<String, String?>,
    ) : ForcedWrite

    data class Split(
        val transactionId: String,
        val parts: List<SplitPart>,
    ) : ForcedWrite

    data class Unsplit(
        val transactionId: String,
    ) : ForcedWrite

    data class Cleared(
        val transactionIds: List<String>,
        val cleared: Boolean,
    ) : ForcedWrite

    data class Merge(
        val transactionIds: List<String>,
    ) : ForcedWrite
}

sealed interface WriteResult {
    data object Saved : WriteResult

    data class Rejected(
        val reason: String,
    ) : WriteResult

    data class Confirm(
        val reason: String,
        val retry: ForcedWrite,
    ) : WriteResult
}

sealed interface EntryResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : EntryResult<T>

    data class Rejected(
        val reason: String,
    ) : EntryResult<Nothing>
}
