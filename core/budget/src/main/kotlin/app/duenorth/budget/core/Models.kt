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
    ): String {
        val negative = minor < 0
        val absolute = if (minor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minor)
        val digits =
            if (currency.decimals == 0) {
                group(absolute)
            } else {
                var scale = 1L
                repeat(currency.decimals) { scale *= 10 }
                val whole = absolute / scale
                val fraction = (absolute % scale).toString().padStart(currency.decimals, '0')
                group(whole) + "." + fraction
            }
        val body = currency.symbol + digits
        return if (negative) "-$body" else body
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
    val netWorthIncludeOffBudget: Boolean = true,
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
