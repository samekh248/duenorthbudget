package app.duenorth.budget.core

import java.time.LocalDate
import java.time.YearMonth

object ShellReader {
    fun read(
        session: SqlSession,
        metadata: BudgetMetadata,
        today: LocalDate,
    ): MonthShell = BudgetBookStore.load(session, metadata).month(YearMonth.from(today)).shell
}
