package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class MonthShellPreviewTest {
    private val shell =
        MonthShell(
            budgetId = "b",
            budgetName = "Home",
            mode = BudgetMode.ENVELOPE,
            currency = Currencies.byCode("USD")!!,
            month = YearMonth.of(2026, 10),
            headerLabel = ShellCopy.TO_BUDGET,
            headerMinor = 50_000L,
            groups =
                listOf(
                    GroupRow(
                        "g",
                        "Expenses",
                        10_000L,
                        listOf(
                            CategoryRow("a", "A", 5_000L, 0L, 5_000L, false),
                            CategoryRow("b", "B", 5_000L, 0L, 5_000L, false),
                        ),
                    ),
                ),
            accounts = emptyList(),
            inbox = emptyList(),
        )

    @Test
    fun assignPreviewMovesHeaderAndAvailable() {
        val next = shell.withBudgetedAssignment("a", 8_000L)
        assertEquals(47_000L, next.headerMinor)
        assertEquals(8_000L, next.groups.first().categories.first { it.id == "a" }.availableMinor)
    }

    @Test
    fun movePreviewKeepsHeader() {
        val next = shell.withMoveAvailable("a", "b", 1_000L)
        assertEquals(shell.headerMinor, next.headerMinor)
        assertEquals(4_000L, next.groups.first().categories.first { it.id == "a" }.availableMinor)
        assertEquals(6_000L, next.groups.first().categories.first { it.id == "b" }.availableMinor)
    }
}
