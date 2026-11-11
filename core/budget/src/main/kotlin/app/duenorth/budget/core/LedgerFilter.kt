package app.duenorth.budget.core

import java.time.LocalDate

object LedgerFilterCopy {
    const val FILTER = "filter"
    const val TITLE = "filter"
    const val CLEAR_ALL = "clear all"
    const val EDIT = "edit"
    const val DONE = "done"
    const val CATEGORY = "category"
    const val ACCOUNT = "account"
    const val DATES = "dates"
    const val AMOUNT = "amount"
    const val STATUS = "status"
    const val TEXT = "payee or notes"
    const val TEXT_HINT = "contains…"
    const val FLAGS = "flags"
    const val ADD = "add"
    const val OVER = "over"
    const val UNDER = "under"
    const val TO = "to"
    const val NO_LIMIT = "no limit"
    const val FROM = "from"
    const val CATEGORY_HINT = "category"

    fun filters(count: Int): String = if (count == 1) "1 filter" else "$count filters"

    /** "3 filters · 12 of 41 shown", the strip's second line. */
    fun shown(
        count: Int,
        shown: Int,
        total: Int,
    ): String = "${filters(count)} · $shown of $total shown"
}

/** When a transaction happened, as the filter sheet offers it: fixed spans counted back from today. */
enum class LedgerDates(
    val label: String,
) {
    ANY("any time"),
    THIS_MONTH("this month"),
    LAST_MONTH("last month"),
    DAYS_90("90 days"),
    THIS_YEAR("this year"),
    ;

    /** The first and last day kept, as Actual's `yyyymmdd` integers; null for [ANY]. */
    fun span(today: LocalDate): IntRange? =
        when (this) {
            ANY -> null
            THIS_MONTH -> day(today.withDayOfMonth(1))..day(today.withDayOfMonth(today.lengthOfMonth()))
            LAST_MONTH -> today.minusMonths(1).let { day(it.withDayOfMonth(1))..day(it.withDayOfMonth(it.lengthOfMonth())) }
            DAYS_90 -> day(today.minusDays(89))..day(today)
            THIS_YEAR -> day(today.withDayOfYear(1))..day(today.withDayOfYear(today.lengthOfYear()))
        }

    private fun day(date: LocalDate): Int = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
}

/** Whether a transaction has cleared at the bank. Pending rows are also not cleared. */
enum class LedgerStatus(
    val label: String,
) {
    ANY("any"),
    PENDING("pending"),
    NOT_CLEARED("not cleared"),
    CLEARED("cleared"),
}

/** Kinds of transaction the sheet can narrow to. With more than one picked, a row must be every one. */
enum class LedgerFlag(
    val label: String,
) {
    SPLIT("split"),
    TRANSFER("transfer"),
    NO_CATEGORY("no category"),
    HAS_NOTES("has notes"),
}

/**
 * The filters on a ledger, any account's register or the all-accounts ledger. Each field narrows the rows and an
 * empty one keeps them all. The amounts are kept as typed, so the boxes hold exactly what was entered. They match
 * a transaction's size, either sign.
 */
data class LedgerFilter(
    val categoryIds: Set<String> = emptySet(),
    /** Only the all-accounts ledger offers this; a register is one account already. */
    val accountIds: Set<String> = emptySet(),
    val dates: LedgerDates = LedgerDates.ANY,
    val minAmount: String = "",
    val maxAmount: String = "",
    val status: LedgerStatus = LedgerStatus.ANY,
    val text: String = "",
    val flags: Set<LedgerFlag> = emptySet(),
) {
    /** How many fields narrow the rows; the amount range counts once. */
    val count: Int
        get() =
            listOf(
                categoryIds.isNotEmpty(),
                accountIds.isNotEmpty(),
                dates != LedgerDates.ANY,
                minAmount.isNotBlank() || maxAmount.isNotBlank(),
                status != LedgerStatus.ANY,
                text.isNotBlank(),
                flags.isNotEmpty(),
            ).count { it }

    val active: Boolean get() = count > 0

    fun min(decimals: Int): Long? = MoneyFormat.parse(minAmount, decimals)?.let { kotlin.math.abs(it) }

    fun max(decimals: Int): Long? = MoneyFormat.parse(maxAmount, decimals)?.let { kotlin.math.abs(it) }

    /** Whether [row] passes every filter. [accountId] is the row's account, where the ledger spans several. */
    fun matches(
        row: RegisterRow,
        today: LocalDate,
        decimals: Int,
        accountId: String? = null,
    ): Boolean {
        if (categoryIds.isNotEmpty() && !hasCategory(row)) return false
        if (accountIds.isNotEmpty() && accountId != null && accountId !in accountIds) return false
        val span = dates.span(today)
        if (span != null && row.date !in span) return false
        val size = kotlin.math.abs(row.amountMinor)
        min(decimals)?.let { if (size < it) return false }
        max(decimals)?.let { if (size > it) return false }
        val statusOk =
            when (status) {
                LedgerStatus.ANY -> true
                LedgerStatus.PENDING -> row.pending
                LedgerStatus.NOT_CLEARED -> !row.cleared
                LedgerStatus.CLEARED -> row.cleared
            }
        if (!statusOk) return false
        val query = text.trim()
        if (query.isNotEmpty() && !hasText(row, query)) return false
        return flags.all { has(row, it) }
    }

    fun filter(
        rows: List<RegisterRow>,
        today: LocalDate,
        decimals: Int,
    ): List<RegisterRow> = if (!active) rows else rows.filter { matches(it, today, decimals) }

    /**
     * The strip's first line: what each filter keeps, in the sheet's order, as "Groceries · this month · over $50".
     * Two categories are named; more read "Groceries +2".
     */
    fun summary(
        categories: List<CategoryChoice>,
        accounts: List<AccountChoice>,
        currency: CurrencySpec,
        options: FormatOptions = FormatOptions(),
    ): String {
        val parts = mutableListOf<String>()
        if (categoryIds.isNotEmpty()) parts += names(categoryIds, categories.associate { it.id to it.name })
        if (accountIds.isNotEmpty()) parts += names(accountIds, accounts.associate { it.id to it.name })
        if (dates != LedgerDates.ANY) parts += dates.label
        val low = min(currency.decimals)
        val high = max(currency.decimals)
        val money = { minor: Long -> MoneyFormat.format(minor, currency, options) }
        when {
            low != null && high != null -> parts += "${money(low)} ${LedgerFilterCopy.TO} ${money(high)}"
            low != null -> parts += "${LedgerFilterCopy.OVER} ${money(low)}"
            high != null -> parts += "${LedgerFilterCopy.UNDER} ${money(high)}"
        }
        if (status != LedgerStatus.ANY) parts += status.label
        if (text.isNotBlank()) parts += "“${text.trim()}”"
        flags.sortedBy { it.ordinal }.forEach { parts += it.label }
        return parts.joinToString(" · ")
    }

    private fun names(
        ids: Set<String>,
        byId: Map<String, String>,
    ): String {
        val named = ids.map { if (it.isEmpty()) RegisterCopy.NO_CATEGORY else byId[it] ?: it }.sorted()
        return if (named.size <= 2) named.joinToString(", ") else "${named.first()} +${named.size - 1}"
    }

    private fun hasCategory(row: RegisterRow): Boolean =
        (row.categoryId != null && row.categoryId in categoryIds) || row.parts.any { it.categoryId in categoryIds }

    private fun hasText(
        row: RegisterRow,
        query: String,
    ): Boolean =
        row.payee.contains(query, ignoreCase = true) ||
            row.note.contains(query, ignoreCase = true) ||
            row.parts.any { it.payee?.contains(query, ignoreCase = true) == true || it.note?.contains(query, ignoreCase = true) == true }

    private fun has(
        row: RegisterRow,
        flag: LedgerFlag,
    ): Boolean =
        when (flag) {
            LedgerFlag.SPLIT -> row.parent
            LedgerFlag.TRANSFER -> row.transferId != null
            LedgerFlag.NO_CATEGORY ->
                row.transferId == null &&
                    (row.categoryLabel == RegisterCopy.NO_CATEGORY || (row.parent && row.parts.any { it.categoryId.isBlank() }))
            LedgerFlag.HAS_NOTES -> row.note.isNotBlank() || row.parts.any { !it.note.isNullOrBlank() }
        }

    fun withCategory(
        id: String,
        on: Boolean,
    ): LedgerFilter = copy(categoryIds = if (on) categoryIds + id else categoryIds - id)

    fun withFlag(
        flag: LedgerFlag,
        on: Boolean,
    ): LedgerFilter = copy(flags = if (on) flags + flag else flags - flag)

    companion object {
        val NONE = LedgerFilter()
    }
}
