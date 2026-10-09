package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.MonthReviewPage
import app.duenorth.budget.core.NetWorthPage
import app.duenorth.budget.core.ReviewCategoryRow
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReviewContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun monthReviewShowsIncomeAndCategories() {
        val review =
            MonthReviewPage(
                month = YearMonth.of(2026, 10),
                currency = Currencies.byCode("USD")!!,
                incomeMinor = 10_000,
                categories =
                    listOf(
                        ReviewCategoryRow("c-food", "Groceries", -4_000),
                        ReviewCategoryRow("c-fun", "Fun", -1_000),
                    ),
            )
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    MonthReviewSection(
                        review = review,
                        loading = false,
                        onListGesture = {},
                        onPreviousMonth = {},
                        onCategory = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("review-income").assertIsDisplayed()
        compose.onNodeWithTag("review-category-c-food").assertIsDisplayed()
    }

    @Test
    fun netWorthShowsTotal() {
        val page =
            NetWorthPage(
                currency = Currencies.byCode("USD")!!,
                onBudgetMinor = 800,
                offBudgetMinor = 500,
                includeOffBudget = true,
            )
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(420.dp)) {
                    NetWorthSection(page = page, loading = false, onIncludeOffBudget = {})
                }
            }
        }
        compose.onNodeWithTag("net-worth-total").assertIsDisplayed()
    }
}
