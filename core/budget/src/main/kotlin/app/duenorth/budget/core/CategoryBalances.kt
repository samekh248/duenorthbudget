package app.duenorth.budget.core

import java.time.YearMonth

/** Each expense category's available balance in a month, as the budget page shows it, for the category pickers. */
object CategoryBalances {
    fun read(
        session: SqlSession,
        month: YearMonth,
    ): Map<String, Long> =
        runCatching { ShellReader.loadDetailed(session, month).categoryRows.associate { it.id to it.availableMinor } }
            .getOrDefault(emptyMap())

    /** [choices] with what each has available in [month]; income categories keep no balance. */
    fun attach(
        session: SqlSession,
        month: YearMonth,
        choices: List<CategoryChoice>,
    ): List<CategoryChoice> {
        if (choices.isEmpty()) return choices
        val balances = read(session, month)
        return choices.map { choice -> if (choice.income) choice else choice.copy(availableMinor = balances[choice.id]) }
    }
}
