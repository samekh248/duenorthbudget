package app.duenorth.budget.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * What an assistant on the phone (Gemini, through Android AppFunctions) can read and do in the open budget.
 * Plain Kotlin, so the JVM tests cover it; the app's AppFunction service only maps these to the platform's types.
 *
 * Names come from people speaking, so every lookup forgives case and accepts a unique start or part of a name.
 * When a name fits none or several, [AgentProblem] says which names would, so the assistant can ask.
 */
class AgentActions(
    private val library: BudgetLibrary,
    private val clock: BudgetClock,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) {
    /** The month at a glance: to budget, overspending, the inbox and the assistant's findings. */
    fun overview(): AgentOverview {
        val (budgetId, shell) = openShell()
        val expense = shell.groups.flatMap { it.categories }.filterNot { it.hidden }
        val report = library.readAssistant(budgetId) ?: AssistantReport.EMPTY
        return AgentOverview(
            budgetName = shell.budgetName,
            month = shell.month.toString(),
            currencyCode = shell.currency.code,
            headline = AgentMoney.labelled(shell.headerLabel, shell.headerMinor, shell.currency),
            headerLabel = shell.headerLabel,
            headerAmount = AgentMoney.decimal(shell.headerMinor),
            overspent = expense.filter { it.availableMinor < 0 }.map { balanceOf(it, groupOf(shell, it.id), shell.currency) },
            inboxCount = shell.inbox.size,
            possibleMatches = shell.matches.size,
            assistantFindings = report.byKind().map { (kind, findings) -> "${kind.title}: ${findings.size}" },
            accounts = shell.accounts.filterNot { it.closed }.map { it.name },
        )
    }

    /** What [categoryName] has left this month, with what was budgeted and spent. */
    fun categoryBalance(categoryName: String): AgentCategoryBalance {
        val (_, shell) = openShell()
        val rows = shell.groups.flatMap { group -> group.categories.map { group to it } }
        val (group, row) = pick(categoryName, rows, "category") { it.second.name to it.second.hidden }
        return balanceOf(row, group.name, shell.currency)
    }

    /** Every expense category with its balance this month, in budget order. */
    fun categoryBalances(): List<AgentCategoryBalance> {
        val (_, shell) = openShell()
        return shell.groups
            .filterNot { it.hidden }
            .flatMap { group -> group.categories.filterNot { it.hidden }.map { balanceOf(it, group.name, shell.currency) } }
    }

    /** Transactions waiting for a category, newest first, at most [limit] of them. */
    fun inbox(limit: Int = 10): AgentInbox {
        val (_, shell) = openShell()
        val rows = shell.inbox.sortedByDescending { it.date }
        return AgentInbox(
            count = rows.size,
            possibleMatches = shell.matches.size,
            items =
                rows.take(limit.coerceIn(1, 50)).map { row ->
                    AgentInboxItem(
                        payee = row.payee,
                        amount = AgentMoney.decimal(row.amountMinor),
                        display = AgentMoney.display(row.amountMinor, shell.currency),
                        date = AgentDates.iso(row.date),
                        account = row.accountName,
                        suggestedCategory = row.suggestionName.ifBlank { null },
                    )
                },
        )
    }

    /**
     * Adds a transaction to the open budget. [amount] is a positive figure; [income] makes it money in.
     * With no [categoryName] it lands in the inbox, the same as one entered on the phone without a category.
     * With no [accountName] it goes to the only open on-budget account, and asks which one when there are several.
     */
    fun addTransaction(
        payee: String,
        amount: Double,
        income: Boolean = false,
        accountName: String? = null,
        categoryName: String? = null,
        date: String? = null,
        note: String? = null,
    ): AgentAddedTransaction {
        if (payee.isBlank()) throw AgentProblem.Invalid("Say who the transaction was with.")
        val magnitude = AgentMoney.minorOf(amount)
        if (magnitude <= 0L) throw AgentProblem.Invalid("The amount must be more than zero.")
        val (budgetId, shell) = openShell()
        val day = AgentDates.parse(date, clock.today())

        val open = shell.accounts.filterNot { it.closed }
        if (open.isEmpty()) throw AgentProblem.NotFound("This budget has no open accounts yet.")
        val account =
            if (accountName.isNullOrBlank()) {
                open.filterNot { it.offBudget }.singleOrNull()
                    ?: open.singleOrNull()
                    ?: throw AgentProblem.Invalid("Which account? It could be ${AgentNames.list(open.map { it.name })}.")
            } else {
                pick(accountName, open, "account") { it.name to false }
            }

        val category =
            categoryName?.takeIf { it.isNotBlank() }?.let { name ->
                val pool =
                    if (income) {
                        shell.incomeGroups.flatMap { g -> g.categories.map { it.id to (it.name to it.hidden) } }
                    } else {
                        shell.groups.flatMap { g -> g.categories.map { it.id to (it.name to it.hidden) } }
                    }
                pick(name, pool, if (income) "income category" else "category") { it.second }
            }
        // Off-budget accounts take no category, as in Actual.
        val categoryId = category?.first?.takeUnless { account.offBudget }

        val id = ids()
        val signed = if (income) magnitude else -magnitude
        val draft =
            TransactionDraft(
                id = id,
                accountId = account.id,
                date = AgentDates.stored(day),
                payee = payee.trim(),
                amountMinor = signed,
                categoryId = categoryId,
                note = note?.trim()?.ifEmpty { null },
            )
        when (val result = library.saveTransaction(budgetId, draft)) {
            WriteResult.Saved -> Unit
            is WriteResult.Rejected -> throw AgentProblem.Invalid(result.reason)
            is WriteResult.Confirm -> throw AgentProblem.Invalid(result.reason)
        }
        // A rule may have filled in the category, so whether it waits in the inbox comes from a fresh read.
        val waiting = library.readShell(budgetId)?.inbox?.any { it.id == id } == true
        return AgentAddedTransaction(
            transactionId = id,
            payee = draft.payee,
            amount = AgentMoney.decimal(signed),
            display = AgentMoney.display(signed, shell.currency),
            date = day.toString(),
            account = account.name,
            category = categoryId?.let { category?.second?.first },
            inInbox = waiting,
        )
    }

    private fun openShell(): Pair<String, MonthShell> {
        val budgetId = library.settings().openBudgetId ?: throw AgentProblem.NotFound(NO_BUDGET)
        val shell = library.readShell(budgetId) ?: throw AgentProblem.NotFound(NO_BUDGET)
        return budgetId to shell
    }

    private fun groupOf(
        shell: MonthShell,
        categoryId: String,
    ): String =
        shell.groups
            .firstOrNull { g -> g.categories.any { it.id == categoryId } }
            ?.name
            .orEmpty()

    private fun balanceOf(
        row: CategoryRow,
        group: String,
        currency: CurrencySpec,
    ) = AgentCategoryBalance(
        category = row.name,
        group = group,
        available = AgentMoney.decimal(row.availableMinor),
        budgeted = AgentMoney.decimal(row.budgetedMinor),
        spent = AgentMoney.decimal(-row.spentMinor),
        currencyCode = currency.code,
        display = AgentMoney.display(row.availableMinor, currency),
        overspent = row.availableMinor < 0,
    )

    /** The one item whose name best fits [query]: exact, then a start, then a part; visible ones before hidden. */
    private fun <T> pick(
        query: String,
        items: List<T>,
        what: String,
        nameOf: (T) -> Pair<String, Boolean>,
    ): T {
        val wanted = AgentNames.fold(query)
        if (wanted.isEmpty()) throw AgentProblem.Invalid("Say which $what.")
        val tiers: List<(String) -> Boolean> =
            listOf({ it == wanted }, { it.startsWith(wanted) }, { it.contains(wanted) })
        for (fits in tiers) {
            val found = items.filter { fits(AgentNames.fold(nameOf(it).first)) }
            val shown = found.filterNot { nameOf(it).second }.ifEmpty { found }
            when {
                shown.size == 1 -> return shown.single()
                shown.size > 1 -> throw AgentProblem.Invalid(
                    "More than one $what fits \"$query\": ${AgentNames.list(shown.map { nameOf(it).first })}.",
                )
            }
        }
        val names = items.filterNot { nameOf(it).second }.map { nameOf(it).first }
        throw AgentProblem.NotFound("No $what is called \"$query\". There's ${AgentNames.list(names.take(12))}.")
    }

    companion object {
        const val NO_BUDGET = "No budget is open in Due North yet. Open the app and pick one first."
    }
}

/** Why an action could not be done, worded for the assistant to read back. */
sealed class AgentProblem(
    message: String,
) : Exception(message) {
    /** The ask can be fixed: a missing or unclear name, a zero amount, a bad date. */
    class Invalid(
        message: String,
    ) : AgentProblem(message)

    /** Nothing fits: no open budget, no such category or account. */
    class NotFound(
        message: String,
    ) : AgentProblem(message)
}

data class AgentOverview(
    val budgetName: String,
    /** yyyy-MM. */
    val month: String,
    val currencyCode: String,
    /** Such as "To budget: $120.00". */
    val headline: String,
    val headerLabel: String,
    val headerAmount: String,
    val overspent: List<AgentCategoryBalance>,
    val inboxCount: Int,
    val possibleMatches: Int,
    /** One line per kind, such as "overspent: 2". */
    val assistantFindings: List<String>,
    val accounts: List<String>,
)

data class AgentCategoryBalance(
    val category: String,
    val group: String,
    /** Plain decimals, such as "-12.50", so an assistant reads them without knowing the stored units. */
    val available: String,
    val budgeted: String,
    val spent: String,
    val currencyCode: String,
    /** Available with the currency symbol, such as "$1,234.56". */
    val display: String,
    val overspent: Boolean,
)

data class AgentInbox(
    val count: Int,
    val possibleMatches: Int,
    val items: List<AgentInboxItem>,
)

data class AgentInboxItem(
    val payee: String,
    val amount: String,
    val display: String,
    /** yyyy-MM-dd. */
    val date: String,
    val account: String,
    val suggestedCategory: String?,
)

data class AgentAddedTransaction(
    val transactionId: String,
    val payee: String,
    val amount: String,
    val display: String,
    val date: String,
    val account: String,
    val category: String?,
    /** No category yet, so it waits in the inbox. */
    val inInbox: Boolean,
)

internal object AgentMoney {
    fun minorOf(amount: Double): Long {
        if (amount.isNaN() || amount.isInfinite()) return 0L
        return BigDecimal
            .valueOf(kotlin.math.abs(amount))
            .setScale(CurrencySpec.STORED_DECIMALS, RoundingMode.HALF_UP)
            .movePointRight(CurrencySpec.STORED_DECIMALS)
            .toLong()
    }

    fun decimal(minor: Long): String = BigDecimal.valueOf(minor, CurrencySpec.STORED_DECIMALS).toPlainString()

    fun display(
        minor: Long,
        currency: CurrencySpec,
    ): String {
        val magnitude =
            BigDecimal
                .valueOf(kotlin.math.abs(minor), CurrencySpec.STORED_DECIMALS)
                .setScale(currency.shownDecimals.coerceIn(0, CurrencySpec.STORED_DECIMALS), RoundingMode.HALF_UP)
        val grouped = String.format(java.util.Locale.US, "%,.${magnitude.scale()}f", magnitude)
        val symbol = currency.symbol.ifBlank { currency.code + " " }
        return (if (minor < 0) "-" else "") + symbol + grouped
    }

    fun labelled(
        label: String,
        minor: Long,
        currency: CurrencySpec,
    ): String = label.replaceFirstChar { it.uppercase() } + ": " + display(minor, currency)
}

internal object AgentDates {
    fun parse(
        text: String?,
        today: LocalDate,
    ): LocalDate {
        val trimmed = text?.trim().orEmpty()
        return when (trimmed.lowercase()) {
            "", "today" -> today
            "yesterday" -> today.minusDays(1)
            else ->
                try {
                    LocalDate.parse(trimmed)
                } catch (_: DateTimeParseException) {
                    throw AgentProblem.Invalid("Give the date as yyyy-MM-dd, such as $today.")
                }
        }
    }

    fun stored(date: LocalDate): Int = date.year * 10_000 + date.monthValue * 100 + date.dayOfMonth

    fun iso(stored: Int): String = if (stored <= 0) "" else LocalDate.of(stored / 10_000, stored / 100 % 100, stored % 100).toString()
}

internal object AgentNames {
    fun fold(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")

    fun list(names: List<String>): String =
        when (names.size) {
            0 -> "nothing"
            1 -> names.single()
            else -> names.dropLast(1).joinToString(", ") + " or " + names.last()
        }
}
