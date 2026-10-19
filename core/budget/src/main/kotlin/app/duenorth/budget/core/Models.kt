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

    /** The date in [pattern] after its short weekday, as the transaction date box shows it: "Thu 10/01/2026". */
    fun withWeekday(
        date: Int,
        pattern: String,
    ): String {
        val year = date / 10000
        val month = (date / 100) % 100
        val day = date % 100
        if (month !in 1..12 || day !in 1..DatePick.daysIn(year, month)) return format(date, pattern)
        val weekday =
            java.time.LocalDate
                .of(year, month, day)
                .dayOfWeek
                .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
        return "$weekday ${format(date, pattern)}"
    }

    /**
     * A typed date read in [pattern]'s order, with any separators, or as `yyyy-MM-dd`. Null when it is not a
     * real date.
     */
    fun parse(
        text: String,
        pattern: String,
    ): Int? {
        val parts = text.trim().split(Regex("\\D+")).filter { it.isNotEmpty() }
        if (parts.size != 3) return RegisterEntry.parseDate(text)
        val iso = parts[0].length == 4
        val order = if (iso) listOf(DateColumn.YEAR, DateColumn.MONTH, DateColumn.DAY) else DatePick.columns(pattern)
        val values = order.zip(parts.map { it.toIntOrNull() ?: return null }).toMap()
        val year = values.getValue(DateColumn.YEAR)
        val month = values.getValue(DateColumn.MONTH)
        val day = values.getValue(DateColumn.DAY)
        if (year !in 1..9999 || month !in 1..12 || day !in 1..DatePick.daysIn(year, month)) return null
        return year * 10000 + month * 100 + day
    }

    /** [text] as `yyyy-MM-dd` when it reads as a date in [pattern], otherwise unchanged so the form rejects it. */
    fun toIso(
        text: String,
        pattern: String,
    ): String = parse(text, pattern)?.let(RegisterEntry::formatIso) ?: text

    /** The widest value [withWeekday] can produce in [pattern], for sizing the date box. */
    fun widestSample(pattern: String): String = "Wed " + pattern.map { if (it.isLetter()) '0' else it }.joinToString("")
}

/** Which part of a date a picker column holds. */
enum class DateColumn { MONTH, DAY, YEAR }

/**
 * Date arithmetic for the transaction date picker (spec 013). Dates are Actual's `yyyyMMdd`
 * integers; every change clamps the day so the result is always a real date.
 */
object DatePick {
    const val FIRST_YEAR = 1900
    const val LAST_YEAR = 2100

    /** Picker columns in the order [pattern] writes them. */
    fun columns(pattern: String): List<DateColumn> =
        listOf(
            DateColumn.MONTH to pattern.indexOf("MM"),
            DateColumn.DAY to pattern.indexOf("dd"),
            DateColumn.YEAR to pattern.indexOf("yyyy"),
        ).sortedBy { (_, at) -> if (at < 0) Int.MAX_VALUE else at }
            .map { it.first }

    fun daysIn(
        year: Int,
        month: Int,
    ): Int =
        when (month) {
            2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

    fun year(date: Int): Int = date / 10000

    fun month(date: Int): Int = (date / 100) % 100

    fun day(date: Int): Int = date % 100

    fun of(
        year: Int,
        month: Int,
        day: Int,
    ): Int = year * 10000 + month * 100 + day.coerceIn(1, daysIn(year, month))

    fun withMonth(
        date: Int,
        month: Int,
    ): Int = of(year(date), month, day(date))

    fun withDay(
        date: Int,
        day: Int,
    ): Int = of(year(date), month(date), day)

    fun withYear(
        date: Int,
        year: Int,
    ): Int = of(year, month(date), day(date))

    /** The picker's years: 1900–2100, widened so [date]'s own year is always offered. */
    fun yearRange(date: Int): IntRange = minOf(FIRST_YEAR, year(date))..maxOf(LAST_YEAR, year(date))
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

    /** The group and decimal separators of a Settings number format, in that order. */
    internal fun separators(numberFormat: String): Pair<String, String> =
        when (numberFormat) {
            FormatOptions.NUMBER_DOT_COMMA -> "." to ","
            FormatOptions.NUMBER_SPACE_DOT -> " " to "."
            FormatOptions.NUMBER_COMMA_DOT_NO_GROUP -> "" to "."
            else -> "," to "."
        }

    internal fun group(
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
    /** Home shows hidden category groups and categories (menu `show-hidden`). */
    val showHiddenCategories: Boolean = false,
    /** The inbox's swipe preview has played; it plays once ever, on the first visit. */
    val inboxSwipeShown: Boolean = false,
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
    val hidden: Boolean = false,
    /** The month's template target (`goal`), written when templates are applied; null when the category has none. */
    val goalMinor: Long? = null,
    /** True for a `#goal` saving target, measured against the balance rather than what was budgeted. */
    val longGoal: Boolean = false,
) {
    /**
     * How far along the goal is, from 0 to 1, as Actual judges it: a `#goal` by the balance, any other
     * template by what was budgeted this month. Null when there is no goal to measure.
     */
    val goalProgress: Float?
        get() {
            val goal = goalMinor?.takeIf { it > 0 } ?: return null
            val funded = if (longGoal) availableMinor else budgetedMinor
            return (funded.toDouble() / goal).coerceIn(0.0, 1.0).toFloat()
        }
}

/** An income category for the month: what came in, and in tracking files what was budgeted. */
data class IncomeCategoryRow(
    val id: String,
    val name: String,
    val budgetedMinor: Long,
    val receivedMinor: Long,
    /** Envelope auto-hold: this month's income is held for next month. */
    val autoHold: Boolean,
    val hidden: Boolean = false,
)

data class IncomeGroupRow(
    val id: String,
    val name: String,
    val budgetedMinor: Long,
    val receivedMinor: Long,
    val categories: List<IncomeCategoryRow> = emptyList(),
    val hidden: Boolean = false,
)

data class GroupRow(
    val id: String,
    val name: String,
    val availableMinor: Long,
    val categories: List<CategoryRow> = emptyList(),
    val hidden: Boolean = false,
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
    /** YYYYMMDD, as the register stores it. */
    val date: Int = 0,
    val accountId: String = "",
    val accountName: String = "",
    val note: String = "",
    /** The category this payee last went to, offered as the swipe-right accept. */
    val suggestionId: String? = null,
    val suggestionName: String = "",
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
    /** Possible matches in on-budget accounts, waiting in the inbox for match or not a match. */
    val matches: List<MatchPair> = emptyList(),
)

object ShellCopy {
    const val NO_PAYEE = "no payee"
    const val ENTER_NAME = "enter a name"
    const val CHOOSE_CURRENCY = "choose a currency"
    const val UNKNOWN_SAMPLE = "unknown sample"
    const val TO_BUDGET = "to budget"
    const val BALANCE = "balance"
    const val NOTHING_TO_BUDGET = "nothing to budget"
    const val SHOW_HIDDEN = "show hidden categories"
    const val HIDE_HIDDEN = "hide hidden categories"
    const val NO_ACCOUNTS = "no accounts"
    const val NOTHING_TO_CATEGORIZE = "nothing to categorize"
    const val ON_BUDGET = "On budget"
    const val OFF_BUDGET = "Off budget"
    const val CLOSED_ACCOUNTS = "Closed accounts"
    const val ALL_ACCOUNTS = "all accounts"
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
