package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShellContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsToBudgetAndEmptyNotes() {
        val shell = sample().copy(groups = emptyList(), accounts = emptyList(), inbox = emptyList(), headerMinor = 0)
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    HomePanorama(shell, loading = false, onGesture = {}, modifier = Modifier)
                }
            }
        }
        compose.onNodeWithText("$0.00").assertIsDisplayed()
        compose.onNodeWithText(ShellCopy.NOTHING_TO_BUDGET).assertIsDisplayed()
        compose.onNodeWithText("due north").assertIsDisplayed()
    }

    @Test
    fun longNameStaysBesideTheAmount() {
        val shell =
            sample().copy(
                groups =
                    listOf(
                        GroupRow(
                            "g",
                            "A very long category group name that has to stay readable on one row",
                            999_999_999_999L,
                        ),
                    ),
            )
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(420.dp)) {
                    BudgetSection(shell, onListGesture = {})
                }
            }
        }
        val name = compose.onNodeWithTag("group-name-g").fetchSemanticsNode().boundsInRoot
        val amount = compose.onNodeWithTag("group-amount-g").fetchSemanticsNode().boundsInRoot
        assertTrue("name overlaps amount: $name vs $amount", name.right <= amount.left + 1f)
        compose.onNodeWithText("A very long category group name that has to stay readable on one row").assertIsDisplayed()
    }

    @Test
    fun threeHundredGroupsStayLazy() {
        val groups = (0 until 300).map { GroupRow("g$it", "group $it", 100L) }
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(320.dp)) {
                    BudgetSection(sample().copy(groups = groups), onListGesture = {})
                }
            }
        }
        compose.onNodeWithTag("group-name-g0").assertIsDisplayed()
        compose.onNodeWithTag("group-name-g299").assertDoesNotExist()
    }

    private fun sample(): MonthShell =
        MonthShell(
            budgetId = "home",
            budgetName = "Home",
            mode = BudgetMode.ENVELOPE,
            currency = Currencies.byCode("USD")!!,
            month = YearMonth.of(2026, 10),
            headerLabel = "to budget",
            headerMinor = 0,
            groups = emptyList(),
            accounts = emptyList(),
            inbox = emptyList(),
        )
}
