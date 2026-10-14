package app.duenorth.budget.core

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth

enum class BudgetMode {
    ENVELOPE,
    TRACKING,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    fun stored(): String = name.lowercase()

    companion object {
        fun fromStored(value: String): ThemeMode = entries.firstOrNull { it.stored() == value.lowercase() } ?: SYSTEM
    }
}

fun interface BudgetClock {
    fun today(): LocalDate

    companion object {
        val System: BudgetClock = BudgetClock { LocalDate.now() }
    }
}

data class CurrencySpec(
    val code: String,
    val symbol: String,
    val decimals: Int,
)

object Currencies {
    val all: List<CurrencySpec> =
        listOf(
            CurrencySpec("USD", "$", 2),
            CurrencySpec("EUR", "€", 2),
            CurrencySpec("GBP", "£", 2),
            CurrencySpec("JPY", "¥", 0),
            CurrencySpec("CAD", "CA$", 2),
            CurrencySpec("AUD", "A$", 2),
            CurrencySpec("CHF", "CHF ", 2),
            CurrencySpec("CNY", "CN¥", 2),
            CurrencySpec("INR", "₹", 2),
            CurrencySpec("KRW", "₩", 0),
        )

    fun byCode(code: String): CurrencySpec? = all.firstOrNull { it.code == code }

    /** The listed currency for the phone's region, or USD (spec 011 R9). */
    fun forLocale(locale: java.util.Locale): String {
        val code =
            runCatching {
                java.util.Currency
                    .getInstance(locale)
                    .currencyCode
            }.getOrNull()
        return code?.takeIf { byCode(it) != null } ?: "USD"
    }
}

data class FormatOptions(
    val hideFraction: Boolean = false,
    val numberFormat: String = NUMBER_COMMA_DOT,
) {
    companion object {
        const val NUMBER_COMMA_DOT = "comma-dot"
        const val NUMBER_DOT_COMMA = "dot-comma"
        const val NUMBER_SPACE_DOT = "space-dot"
        const val NUMBER_COMMA_DOT_NO_GROUP = "comma-dot-without-commas"
    }
}

data class FormatPrefs(
    val dateFormat: String = "MM/dd/yyyy",
    val numberFormat: String = FormatOptions.NUMBER_COMMA_DOT,
    val hideFraction: Boolean = false,
    val firstDayOfWeekIdx: Int = 0,
    val experimental: Boolean = false,
    val budgetType: String = "envelope",
    /** Actual's `learn-categories`; absent means on (spec 010 US3). */
    val learnCategories: Boolean = true,
    /** Actual's `flags.goalTemplatesEnabled`; templates also need [experimental] (spec 010 US5). */
    val goalTemplates: Boolean = false,
) {
    val templatesOn: Boolean get() = experimental && goalTemplates
}

data class PendingExport(
    val fileName: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PendingExport) return false
        return fileName == other.fileName && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * fileName.hashCode() + bytes.contentHashCode()
}

object DateFormats {
    val choices =
        listOf(
            "MM/dd/yyyy",
            "dd/MM/yyyy",
            "yyyy-MM-dd",
            "MM.dd.yyyy",
            "dd.MM.yyyy",
        )

    fun format(
        date: Int,
        pattern: String,
    ): String {
        val year = date / 10000
        val month = (date / 100) % 100
        val day = date % 100
        if (month !in 1..12 || day !in 1..31) return date.toString()
        val mm = month.toString().padStart(2, '0')
        val dd = day.toString().padStart(2, '0')
        val yyyy = year.toString().padStart(4, '0')
        return when (pattern) {
            "dd/MM/yyyy" -> "$dd/$mm/$yyyy"
            "yyyy-MM-dd" -> "$yyyy-$mm-$dd"
            "MM.dd.yyyy" -> "$mm.$dd.$yyyy"
            "dd.MM.yyyy" -> "$dd.$mm.$yyyy"
            else -> "$mm/$dd/$yyyy"
        }
    }
}

object MoneyFormat {
    fun format(
        minor: Long,
        currency: CurrencySpec,
        options: FormatOptions = FormatOptions(),
    ): String {
        val negative = minor < 0
        val absolute = if (minor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minor)
        val (groupSep, decimalSep) = separators(options.numberFormat)
        var scale = 1L
        repeat(currency.decimals) { scale *= 10 }
        val whole = if (currency.decimals == 0) absolute else absolute / scale
        val fraction = if (currency.decimals == 0) 0L else absolute % scale
        val digits =
            if (options.hideFraction || currency.decimals == 0) {
                group(whole, groupSep)
            } else {
                group(whole, groupSep) + decimalSep + fraction.toString().padStart(currency.decimals, '0')
            }
        val body = currency.symbol + digits
        return if (negative) "-$body" else body
    }

    fun parse(
        text: String,
        decimals: Int,
    ): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == "-" || trimmed == "+") return null
        val negative = trimmed.startsWith("-")
        val body = trimmed.removePrefix("-").removePrefix("+")
        val parts = body.split('.')
        if (parts.size > 2) return null
        if (parts[0].any { !it.isDigit() }) return null
        if (parts.size == 2 && (parts[1].length > decimals || parts[1].any { !it.isDigit() })) return null
        val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
        var scale = 1L
        repeat(decimals) { scale *= 10 }
        val fraction =
            if (decimals == 0 || parts.size == 1) {
                0L
            } else {
                parts[1].padEnd(decimals, '0').toLongOrNull() ?: return null
            }
        val minor = whole * scale + fraction
        return if (negative) -minor else minor
    }

    private fun separators(numberFormat: String): Pair<String, String> =
        when (numberFormat) {
            FormatOptions.NUMBER_DOT_COMMA -> "." to ","
            FormatOptions.NUMBER_SPACE_DOT -> " " to "."
            FormatOptions.NUMBER_COMMA_DOT_NO_GROUP -> "" to "."
            else -> "," to "."
        }

    private fun group(
        value: Long,
        separator: String,
    ): String {
        val raw = value.toString()
        if (separator.isEmpty()) return raw
        val out = StringBuilder()
        raw.forEachIndexed { index, char ->
            if (index > 0 && (raw.length - index) % 3 == 0) out.append(separator)
            out.append(char)
        }
        return out.toString()
    }
}

fun PhoneSettings.activeInstance(): SavedInstance? = instances.firstOrNull { it.id == activeInstanceId }

fun PhoneSettings.activeAddress(): String? = activeInstance()?.address

fun YearMonth.toActualMonth(): Int = year * 100 + monthValue

fun monthLabel(month: YearMonth): String = "${month.month.name.lowercase()} ${month.year}"

@Serializable
data class SavedInstance(
    val id: String,
    val address: String,
    val label: String? = null,
)

@Serializable
data class PhoneSettings(
    val openBudgetId: String? = null,
    val themeMode: String = "system",
    val accent: String = "magenta",
    val netWorthIncludeOffBudget: Boolean = true,
    val serverAddress: String? = null,
    val instances: List<SavedInstance> = emptyList(),
    val activeInstanceId: String? = null,
    val syncNode: String? = null,
    val askEachTime: List<String> = emptyList(),
    /** Accounts list shows closed accounts (spec 010 US2, menu `show-closed`). */
    val showClosedAccounts: Boolean = false,
)

@Serializable
data class BudgetMetadata(
    val id: String,
    val budgetName: String,
)

data class BudgetSummary(
    val id: String,
    val name: String,
    val currencyCode: String,
)

data class CategoryRow(
    val id: String,
    val name: String,
    val budgetedMinor: Long,
    val spentMinor: Long,
    val availableMinor: Long,
    val carryover: Boolean,
)

/** An income category for the month: what came in, and in tracking files what was budgeted. */
data class IncomeCategoryRow(
    val id: String,
    val name: String,
    val budgetedMinor: Long,
    val receivedMinor: Long,
    /** Envelope auto-hold: this month's income is held for next month. */
    val autoHold: Boolean,
)

data class IncomeGroupRow(
    val id: String,
    val name: String,
    val budgetedMinor: Long,
    val receivedMinor: Long,
    val categories: List<IncomeCategoryRow> = emptyList(),
)

data class GroupRow(
    val id: String,
    val name: String,
    val availableMinor: Long,
    val categories: List<CategoryRow> = emptyList(),
)

data class AccountRow(
    val id: String,
    val name: String,
    val offBudget: Boolean,
    val closed: Boolean,
    val balanceMinor: Long,
)

data class InboxRow(
    val id: String,
    val payee: String,
    val amountMinor: Long,
)

data class MonthShell(
    val budgetId: String,
    val budgetName: String,
    val mode: BudgetMode,
    val currency: CurrencySpec,
    val month: YearMonth,
    val headerLabel: String,
    val headerMinor: Long,
    val bufferedMinor: Long = 0L,
    val groups: List<GroupRow>,
    val accounts: List<AccountRow>,
    val inbox: List<InboxRow>,
    /** Income groups, shown after the expense groups as desktop Actual does. */
    val incomeGroups: List<IncomeGroupRow> = emptyList(),
)

object ShellCopy {
    const val NO_PAYEE = "no payee"
    const val ENTER_NAME = "enter a name"
    const val CHOOSE_CURRENCY = "choose a currency"
    const val UNKNOWN_SAMPLE = "unknown sample"
    const val TO_BUDGET = "to budget"
    const val BALANCE = "balance"
    const val NOTHING_TO_BUDGET = "nothing to budget"
    const val NO_ACCOUNTS = "no accounts"
    const val NOTHING_TO_CATEGORIZE = "nothing to categorize"
    const val ON_BUDGET = "on budget"
    const val OFF_BUDGET = "off budget"
    const val SAMPLE_BUDGETS = "sample budgets"
}

sealed interface CreateResult {
    data class Created(
        val id: String,
    ) : CreateResult

    data class Rejected(
        val reason: String,
    ) : CreateResult
}

/**
 * Holds a newer shell until the finger lifts, so a refresh cannot reorder the row under it.
 */
class RefreshGate<T> {
    var visible: T? = null
        private set

    var gestureActive: Boolean = false
        private set

    private var pending: T? = null

    fun beginGesture() {
        gestureActive = true
    }

    /** @return true when [next] is now the visible shell */
    fun offer(next: T): Boolean {
        if (gestureActive) {
            pending = next
            return false
        }
        visible = next
        pending = null
        return true
    }

    fun endGesture() {
        gestureActive = false
        pending?.let { visible = it }
        pending = null
    }
}
