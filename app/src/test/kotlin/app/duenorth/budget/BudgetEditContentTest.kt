package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BudgetEditContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    // Money moved out of a category with nothing budgeted leaves its budgeted below zero; the box keeps the sign.
    @Test
    fun negativeBudgetedKeepsItsMinusSign() {
        val shell =
            MonthShell(
                budgetId = "b",
                budgetName = "Home",
                mode = BudgetMode.ENVELOPE,
                currency = Currencies.byCode("USD")!!,
                month = YearMonth.of(2026, 10),
                headerLabel = "to budget",
                headerMinor = 0,
                groups =
                    listOf(
                        GroupRow("g", "Debt", 20_000, listOf(CategoryRow("c-gm", "GM Financial", -10_000, 0, 20_000, false))),
                    ),
                accounts = emptyList(),
                inbox = emptyList(),
            )
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    BudgetEditSection(shell, BudgetEdit(), BudgetEditActions(), onListGesture = {})
                }
            }
        }
        compose
            .onNodeWithTag("edit-box-c-gm", useUnmergedTree = true)
            .onChildren()
            .onFirst()
            .assertTextEquals("-100.00")
    }
}
