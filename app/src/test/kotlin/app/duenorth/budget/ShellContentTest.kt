package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.core.SyncCopy
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
    val compose = createHostComposeRule()

    @Test
    fun showsToBudgetAndEmptyNotes() {
        val shell = sample().copy(groups = emptyList(), accounts = emptyList(), inbox = emptyList(), headerMinor = 0)
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    HomePanorama(
                        shell = shell,
                        review = null,
                        netWorth = null,
                        upcoming = emptyList(),
                        loading = false,
                        onGesture = {},
                        modifier = Modifier,
                    )
                }
            }
        }
        compose.onNodeWithText("$0.00").assertIsDisplayed()
        compose.onNodeWithText(ShellCopy.NOTHING_TO_BUDGET).assertIsDisplayed()
        val root = compose.onNodeWithTag("panorama").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("panorama-title").fetchSemanticsNode().boundsInRoot
        val subtitle = compose.onNodeWithTag("panorama-subtitle").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("panorama-title").assertTextEquals("budget")
        compose.onNodeWithTag("panorama-subtitle").assertTextEquals("due north")
        assertTrue("title left ${title.left}", title.left >= root.left - 1f)
        assertTrue("title right ${title.right} > ${root.right}", title.right <= root.right + 1f)
        assertTrue("title top ${title.top} root ${root.top}", title.top >= root.top - 1f)
        assertTrue("title bottom ${title.bottom}", title.bottom <= root.bottom + 1f)
        val tuckDp = (title.bottom - subtitle.top) / compose.density.density
        assertTrue("due north should tuck under the title, ${tuckDp}dp", tuckDp in 34f..46f)
        val indentDp = (subtitle.left - title.left) / compose.density.density
        assertTrue("due north should indent from the title, ${indentDp}dp", indentDp in 12f..20f)
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

    @Test
    fun wrongBudgetPasswordShowsNoAmounts() {
        compose.setContent {
            val error = remember { mutableStateOf<String?>(null) }
            MetroTheme {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    BudgetPasswordScreen(
                        error = error.value,
                        askEachTime = true,
                        onAskEachTime = {},
                        onSubmit = {
                            error.value = SyncCopy.PASSWORD_WRONG
                        },
                    )
                }
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("violet-quartz-991")
        compose.onNodeWithTag("budget-unlock").performClick()
        compose.onNodeWithText(SyncCopy.PASSWORD_WRONG).assertIsDisplayed()
        compose.onNodeWithText("$40.00").assertDoesNotExist()
        compose.onNodeWithText("Groceries").assertDoesNotExist()
        compose.onNodeWithText("violet-quartz-991").assertDoesNotExist()
    }

    @Test
    fun syncProgressStaysAThinBar() {
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp)) {
                    SyncProgressBar(0.4f)
                }
            }
        }
        val bounds = compose.onNodeWithTag("sync-progress").fetchSemanticsNode().boundsInRoot
        val heightDp = bounds.height / compose.density.density
        assertTrue("progress bar is ${heightDp}dp", heightDp in 3f..5f)
        compose.onNodeWithTag("sync-progress").assertIsDisplayed()
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
