package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.CategoryMonth
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MonthContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun unparseableAmountStaysOnTheCategory() {
        val category = row("c-g", "Groceries", 0)
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    CategoryScreen(
                        category = category,
                        others = emptyList(),
                        currency = Currencies.byCode("USD")!!,
                        notice = ShellCopy.ENTER_AMOUNT,
                        onAssign = {},
                        onMove = { _, _ -> },
                        onCarryover = {},
                        onHide = {},
                        onDelete = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("notice").assertTextEquals(ShellCopy.ENTER_AMOUNT)
        compose.onNodeWithTag("category-available").assertIsDisplayed()
    }

    @Test
    fun threeHundredCategoriesStayPutWhenTheAmountChanges() {
        val currency = Currencies.byCode("USD")!!
        val categories = (0 until 300).map { row("c$it", "category $it", if (it == 0) 0 else 100) }
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    GroupScreen(
                        groupName = "Living",
                        categories = categories,
                        currency = currency,
                        notice = null,
                        onListGesture = {},
                        onCategory = {},
                        onAssign = { _, _ -> },
                        onRename = {},
                        onAdd = {},
                        onUp = {},
                        onDown = {},
                        onDelete = {},
                        onShow = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("category-name-c0").assertIsDisplayed()
        compose.onNodeWithTag("category-name-c299").assertDoesNotExist()
        compose.onNodeWithTag("assign-c0").performClick()
        compose.onNodeWithTag("category-name-c0").assertIsDisplayed()
        compose.onNodeWithTag("category-name-c299").assertDoesNotExist()
    }

    private fun row(
        id: String,
        name: String,
        available: Long,
    ) = CategoryMonth(
        id = id,
        groupId = "g",
        name = name,
        sortOrder = 0.0,
        hidden = false,
        budgetedMinor = available,
        spentMinor = 0,
        availableMinor = available,
        carryover = false,
    )
}
