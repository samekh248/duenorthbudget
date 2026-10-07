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
}

object MoneyFormat {
    fun format(
        minor: Long,
        currency: CurrencySpec,
    ): String = signed(minor, currency, withSymbol = true)

    fun plain(
        minor: Long,
        currency: CurrencySpec,
    ): String = signed(minor, currency, withSymbol = false)

    /**
     * Parses a decimal amount in [currency]. Returns minor units, or null when the text is not that amount.
     */
    fun parse(
        text: String,
        currency: CurrencySpec,
    ): Long? {
        var raw = text.trim().replace(" ", "").replace(",", "")
        if (raw.isEmpty()) return null
        var sign = 1L
        when {
            raw.startsWith("+") -> raw = raw.substring(1)
            raw.startsWith("-") -> {
                sign = -1
                raw = raw.substring(1)
            }
        }
        if (raw.isEmpty()) return null
        val parts = raw.split('.')
        if (parts.size > 2) return null
        val whole = parts[0]
        val fraction = if (parts.size == 2) parts[1] else ""
        if (whole.any { !it.isDigit() } || fraction.any { !it.isDigit() }) return null
        if (whole.isEmpty() && fraction.isEmpty()) return null
        if (currency.decimals == 0) {
            if (parts.size == 2) return null
            val value = whole.toLongOrNull() ?: return null
            return sign * value
        }
        if (fraction.length > currency.decimals) return null
        val wholeValue = if (whole.isEmpty()) 0L else whole.toLongOrNull() ?: return null
        val padded = fraction.padEnd(currency.decimals, '0')
        val fractionValue = if (padded.isEmpty()) 0L else padded.toLongOrNull() ?: return null
        val scale = scale(currency.decimals) ?: return null
        val scaled =
            try {
                Math.addExact(Math.multiplyExact(wholeValue, scale), fractionValue)
            } catch (_: ArithmeticException) {
                return null
            }
        return try {
            Math.multiplyExact(sign, scaled)
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun signed(
        minor: Long,
        currency: CurrencySpec,
        withSymbol: Boolean,
    ): String {
        val negative = minor < 0
        val absolute = if (minor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minor)
        val digits =
            if (currency.decimals == 0) {
                group(absolute)
            } else {
                val scale = scale(currency.decimals) ?: 1L
                val whole = absolute / scale
                val fraction = (absolute % scale).toString().padStart(currency.decimals, '0')
                if (withSymbol) {
                    group(whole) + "." + fraction
                } else {
                    whole.toString() + "." + fraction
                }
            }
        val body = if (withSymbol) currency.symbol + digits else digits
        return if (negative) "-$body" else body
    }

    private fun scale(decimals: Int): Long? {
        var scale = 1L
        repeat(decimals) {
            scale =
                try {
                    Math.multiplyExact(scale, 10L)
                } catch (_: ArithmeticException) {
                    return null
                }
        }
        return scale
    }

    private fun group(value: Long): String {
        val raw = value.toString()
        val out = StringBuilder()
        raw.forEachIndexed { index, char ->
            if (index > 0 && (raw.length - index) % 3 == 0) out.append(',')
            out.append(char)
        }
        return out.toString()
    }
}

fun YearMonth.toActualMonth(): Int = year * 100 + monthValue

fun monthLabel(month: YearMonth): String = "${month.month.name.lowercase()} ${month.year}"

@Serializable
data class PhoneSettings(
    val openBudgetId: String? = null,
    val themeMode: String = "system",
    val accent: String = "magenta",
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

data class GroupRow(
    val id: String,
    val name: String,
    val availableMinor: Long,
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
    val groups: List<GroupRow>,
    val accounts: List<AccountRow>,
    val inbox: List<InboxRow>,
    val bufferedMinor: Long = 0,
)

object ShellCopy {
    const val NO_PAYEE = "no payee"
    const val ENTER_NAME = "enter a name"
    const val CHOOSE_CURRENCY = "choose a currency"
    const val TO_BUDGET = "to budget"
    const val BALANCE = "balance"
    const val NOTHING_TO_BUDGET = "nothing to budget"
    const val NO_ACCOUNTS = "no accounts"
    const val NOTHING_TO_CATEGORIZE = "nothing to categorize"
    const val ON_BUDGET = "on budget"
    const val OFF_BUDGET = "off budget"
    const val ENTER_AMOUNT = "enter an amount"
    const val NOT_ENOUGH_AVAILABLE = "not enough available"
    const val NOT_ENOUGH_TO_HOLD = "not enough to hold"
    const val MOVE_FIRST = "move the money or the history first"
    const val TO_BUDGET_NEGATIVE = "to budget is negative"
    const val HELD = "held for next month"
    const val PREVIOUS = "previous"
    const val NEXT = "next"
    const val HOLD = "hold"
    const val RELEASE = "release"
    const val NEW_GROUP = "new group"
    const val NEW_CATEGORY = "new category"
    const val ROLLOVER = "rollover overspending"
    const val STOP_ROLLOVER = "stop rollover"
    const val HIDE = "hide"
    const val SHOW = "show"
    const val DELETE = "delete"
    const val MOVE = "move"
    const val COVER = "cover"
    const val ASSIGN = "assign"
    const val RENAME = "rename"
    const val UP = "up"
    const val DOWN = "down"
    const val SPENT = "spent"
    const val AVAILABLE = "available"
    const val BUDGETED = "budgeted"
    const val HIDDEN = "hidden"
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
