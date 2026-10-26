package app.duenorth.budget

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.duenorth.budget.core.CategoryMonthPage
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.EnvelopeCopy
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.ReviewTransactionRow
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The category page: carried in, recent with its see all, the fill-to-template key and the rollover switch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class CategoryPageContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    // $100 carried in, $2,000 budgeted, $1,600 spent.
    private val shell =
        TemplateSamples.shell.copy(
            groups = listOf(GroupRow("g-liv", "Living", 50_000, listOf(CategoryRow("c-food", "Food", 200_000, -160_000, 50_000, false)))),
        )

    private val rows = (1..5).map { ReviewTransactionRow("t$it", 20261020 + it, "Payee $it", -1_000L * it) }

    @Test
    fun showsCarriedInAndTheLatestThreeTransactions() {
        var seeAll = 0
        compose.setContent {
            MetroTheme {
                CategoryBudgetScreen(
                    shell,
                    "c-food",
                    null,
                    {},
                    {},
                    {},
                    recent = CategoryMonthPage("c-food", "Food", TemplateSamples.october, shell.currency, -15_000, rows),
                    onSeeAll = { seeAll++ },
                )
            }
        }
        compose.onNodeWithText("${EnvelopeCopy.BUDGETED} · \$100.00 ${EnvelopeCopy.CARRIED_IN}").assertExists()
        compose.onNodeWithText("5 ${EnvelopeCopy.THIS_MONTH}").assertExists()
        compose.onNodeWithTag("category-recent-t1").assertExists()
        compose.onNodeWithTag("category-recent-t3").assertExists()
        compose.onNodeWithTag("category-recent-t4").assertDoesNotExist()
        compose.onNodeWithTag("category-see-all").performScrollTo().performClick()
        assertEquals(1, seeAll)
    }

    @Test
    fun recentIgnoresAnotherCategorysTransactions() {
        compose.setContent {
            MetroTheme {
                CategoryBudgetScreen(
                    shell,
                    "c-food",
                    null,
                    {},
                    {},
                    {},
                    recent = CategoryMonthPage("c-gas", "Gas", TemplateSamples.october, shell.currency, -15_000, rows),
                )
            }
        }
        compose.onNodeWithTag("category-recent-t1").assertDoesNotExist()
    }

    @Test
    fun fillToTemplatePutsTheTemplatesAmountInTheBox() {
        var saved: String? = null
        compose.setContent {
            MetroTheme {
                CategoryBudgetScreen(
                    shell,
                    "c-food",
                    null,
                    { saved = it },
                    {},
                    {},
                    note = TemplateSamples.NOTE,
                    templateAmounts = listOf(220_000),
                )
            }
        }
        compose.onNodeWithText(EnvelopeCopy.FILL_TEMPLATE).performScrollTo().performClick()
        compose.onNodeWithText("✓ ${EnvelopeCopy.FILLS_TEMPLATE}").assertExists()
        // Typing another amount turns the key back into an offer.
        compose
            .onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("category-budget-amount")))
            .performTextReplacement("2100")
        compose.onNodeWithText(EnvelopeCopy.FILL_TEMPLATE).assertExists()
        assertEquals(null, saved)
    }

    @Test
    fun rolloverSwitchTurnsCarryoverOn() {
        var carryover: Boolean? = null
        compose.setContent {
            MetroTheme { CategoryBudgetScreen(shell, "c-food", null, {}, {}, { carryover = it }) }
        }
        compose.onNodeWithText(EnvelopeCopy.rolloverHint(TemplateSamples.october)).assertExists()
        compose.onNode(hasText("on") and hasAnyAncestor(hasTestTag("category-rollover"))).performScrollTo().performClick()
        assertEquals(true, carryover)
    }

    @Test
    fun overspentLegendSaysHowFarOver() {
        val over =
            shell.copy(
                groups = listOf(GroupRow("g-liv", "Living", -8_000, listOf(CategoryRow("c-food", "Food", 20_000, -28_000, -8_000, false)))),
            )
        compose.setContent { MetroTheme { CategoryBudgetScreen(over, "c-food", null, {}, {}, {}) } }
        compose.onNodeWithTag("category-available-amount", useUnmergedTree = true).assertTextEquals("-\$80.00")
        compose.onNodeWithText("${EnvelopeCopy.OVER} \$80.00").assertExists()
    }
}
