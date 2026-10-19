package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth
import kotlin.math.abs

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
                    HomePivot(
                        shell = shell,
                        loading = false,
                        openMonth = shell?.month ?: YearMonth.of(2026, 10),
                        monthWindow = MonthWindow.around(YearMonth.of(2026, 10)),
                        onGesture = {},
                        modifier = Modifier,
                    )
                }
            }
        }
        compose.onNodeWithText("$0.00").assertIsDisplayed()
        compose.onNodeWithText(ShellCopy.NOTHING_TO_BUDGET).assertIsDisplayed()
        compose.onNodeWithTag("pivot-title").assertTextEquals("DUE NORTH BUDGET")
        compose.onNodeWithTag("pivot-header-0").assertTextEquals("budget").assertIsSelected()
        compose.onNodeWithTag("pivot-header-1").assertTextEquals("accounts").assertIsNotSelected()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [33], qualifiers = "w411dp-h891dp")
    fun eachPivotItemFillsTheWidthAndAHeaderOpensIt() {
        val shell = sample()
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    HomePivot(
                        shell = shell,
                        loading = false,
                        openMonth = shell.month,
                        monthWindow = MonthWindow.around(shell.month),
                        onGesture = {},
                    )
                }
            }
        }
        val root = compose.onNodeWithTag("pivot").fetchSemanticsNode().boundsInRoot
        val headers = compose.onNodeWithTag("pivot-headers").fetchSemanticsNode().boundsInRoot
        val accounts = compose.onNodeWithTag("pivot-header-1").fetchSemanticsNode().boundsInRoot
        assertTrue("the next header waits in the header row", accounts.left < root.right && accounts.top < headers.bottom)
        compose.onNodeWithTag("to-budget").assertIsDisplayed()
        compose.onNodeWithTag("pivot-header-1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("pivot-header-1").assertIsSelected()
        compose.onNodeWithTag("pivot-header-0").assertIsNotSelected()
        val lead = compose.onNodeWithTag("pivot-header-1").fetchSemanticsNode().boundsInRoot
        val gutter = 12f * compose.density.density
        assertTrue("the chosen header leads the row, $lead", abs(lead.left - (root.left + gutter)) <= 1f)
        // No peek: the budget item leaves the screen entirely.
        compose.onNodeWithTag("to-budget").assertIsNotDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [33], qualifiers = "w411dp-h891dp")
    fun budgetTitleIsTheMonthDropdown() {
        val shell = sample().copy(budgetName = "Home")
        val openMonth = mutableStateOf(shell.month)
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    HomePivot(
                        shell = shell,
                        loading = false,
                        openMonth = openMonth.value,
                        monthWindow = MonthWindow.around(shell.month),
                        onGesture = {},
                        onSelectMonth = { openMonth.value = it },
                    )
                }
            }
        }
        compose.onNodeWithTag("budget-name").assertDoesNotExist()
        compose.onNodeWithText("Home").assertDoesNotExist()
        compose.onNodeWithTag("month-previous").assertDoesNotExist()
        compose.onNodeWithTag("month-next").assertDoesNotExist()
        compose.onNodeWithText("october 2026").assertIsDisplayed()
        val density = compose.density.density
        val title = compose.onNodeWithText("october 2026", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val dropdown = compose.onNodeWithTag("month-dropdown", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("pivot").fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag("pivot-header-0").fetchSemanticsNode().boundsInRoot
        val titleDp = title.height / density
        assertTrue("month reads as a subheader under the pivot header, ${titleDp}dp", titleDp >= 24f)
        assertTrue("month sits under the header row", title.top >= header.bottom)
        assertTrue("dropdown follows the title: $title vs $dropdown", dropdown.left + 1f >= title.right)
        assertTrue(
            "dropdown shares the title row: $title vs $dropdown",
            dropdown.top < title.bottom && title.top < dropdown.bottom,
        )
        assertTrue("dropdown stays inside the page", dropdown.right <= root.right - 12f * density + 1f)
        compose.onNodeWithTag("to-budget").assertIsDisplayed()
        compose.onNodeWithTag("month-label").performClick()
        compose.onNodeWithTag("month-list").assertIsDisplayed()
        // The list opens on the open month, with earlier months above and later months below.
        val list = compose.onNodeWithTag("month-list").fetchSemanticsNode().boundsInRoot
        val currentNode = compose.onNodeWithTag("month-option-2026-10")
        currentNode.assertIsDisplayed()
        val current = currentNode.fetchSemanticsNode().boundsInRoot
        assertTrue("open month is centered: $current in $list", abs(current.center.y - list.center.y) <= current.height)
        compose.onNodeWithTag("month-option-2026-09").assertIsDisplayed()
        compose.onNodeWithTag("month-option-2026-11").assertIsDisplayed()
        compose.onNodeWithTag("month-option-2024-10").assertDoesNotExist()
        compose.onNodeWithTag("month-option-2026-08").performClick()
        compose.onNodeWithText("august 2026").assertIsDisplayed()
        compose.onNodeWithTag("month-list").assertDoesNotExist()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [33], qualifiers = "w411dp-h891dp")
    fun groupTextIsSmallerThanTheTotals() {
        val shell =
            sample().copy(
                headerMinor = 42_000,
                groups = listOf(GroupRow("g", "Bills", 124_000)),
            )
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(420.dp)) {
                    BudgetSection(shell, onListGesture = {})
                }
            }
        }
        val total =
            compose
                .onNodeWithTag("to-budget")
                .fetchSemanticsNode()
                .boundsInRoot.height
        val name =
            compose
                .onNodeWithTag("group-name-g")
                .fetchSemanticsNode()
                .boundsInRoot.height
        val amount =
            compose
                .onNodeWithTag("group-amount-g")
                .fetchSemanticsNode()
                .boundsInRoot.height
        assertTrue("group name is smaller than to-budget: $name vs $total", name < total)
        assertTrue("group amount is smaller than to-budget: $amount vs $total", amount < total)
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
                    BudgetSection(
                        shell,
                        onListGesture = {},
                    )
                }
            }
        }
        val name = compose.onNodeWithTag("group-name-g").fetchSemanticsNode().boundsInRoot
        val amount = compose.onNodeWithTag("group-amount-g").fetchSemanticsNode().boundsInRoot
        assertTrue("name overlaps amount: $name vs $amount", name.right <= amount.left + 1f)
        compose.onNodeWithText("A very long category group name that has to stay readable on one row").assertIsDisplayed()
    }

    @Test
    fun budgetSectionNestsCategoriesUnderTheirGroup() {
        var opened: String? = null
        val categories = listOf(CategoryRow("c-food", "Groceries", 0L, 0L, 12_000L, false))
        val shell =
            sample().copy(
                bufferedMinor = 25_000L,
                groups = listOf(GroupRow("g", "Everyday", 12_000L, categories)),
            )
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    BudgetSection(
                        shell,
                        onListGesture = {},
                        onCategory = { opened = it },
                    )
                }
            }
        }
        compose.onNodeWithTag("group-g").assertIsDisplayed()
        compose.onNodeWithText("Everyday", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("held-amount").assertTextEquals("$250.00")
        val toBudget = compose.onNodeWithTag("to-budget").fetchSemanticsNode().boundsInRoot
        val held = compose.onNodeWithTag("held-amount").fetchSemanticsNode().boundsInRoot
        val heldLabel = compose.onNodeWithText("held").fetchSemanticsNode().boundsInRoot
        val toLabel = compose.onNodeWithText("to budget").fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("group-g").fetchSemanticsNode().boundsInRoot
        assertTrue("held shares the to-budget row: $toBudget vs $held", held.top < toBudget.bottom && toBudget.top < held.bottom)
        assertTrue("held is on the right: $held vs $row", held.right >= row.right - 1f && held.left > (toBudget.right + row.right) / 2f)
        assertTrue("held caption is right aligned: $heldLabel vs $held", kotlin.math.abs(heldLabel.right - held.right) <= 1f)
        assertTrue("held caption ends with the figure: $heldLabel vs $held", heldLabel.left > held.left + 1f)
        assertTrue(
            "held label sits on the caption: $heldLabel vs $toLabel",
            heldLabel.top < toLabel.bottom && toLabel.top < heldLabel.bottom,
        )
        assertTrue("held label sits above its amount: $heldLabel vs $held", heldLabel.bottom <= held.top + 1f)
        assertTrue("to-budget stays on the left: $toBudget vs $row", toBudget.left <= row.left + 1f)
        val group = compose.onNodeWithTag("group-g").fetchSemanticsNode().boundsInRoot
        val category = compose.onNodeWithTag("category-c-food").fetchSemanticsNode().boundsInRoot
        assertTrue("category sits under its group: $group vs $category", category.top >= group.bottom - 1f)
        compose.onNodeWithText("Groceries", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("category-c-food").performClick()
        assertEquals("c-food", opened)
    }

    @Test
    fun threeHundredCategoriesStayLazyWhileEditable() {
        val categories = (0 until 300).map { CategoryRow("c$it", "category $it", 0L, 0L, 0L, false) }
        val group = GroupRow("g", "one group", 0L, categories)
        val shell = sample().copy(groups = listOf(group))
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(320.dp)) {
                    BudgetSection(shell, onListGesture = {}, onCategory = {})
                }
            }
        }
        compose.onNodeWithTag("category-c0").assertIsDisplayed()
        compose.onNodeWithTag("category-c299").assertDoesNotExist()
    }

    @Test
    fun categoryLedgerKeepsFiguresInColumns() {
        val category = CategoryRow("c-food", "Groceries", 20_000L, 28_000L, -8_000L, false)
        val group = GroupRow("g", "Food", -3_200L, listOf(category))
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    BudgetSection(sample().copy(groups = listOf(group)), onListGesture = {}, onCategory = {})
                }
            }
        }
        compose.onNodeWithTag("category-budgeted-c-food", useUnmergedTree = true).assertTextEquals("$200.00")
        compose.onNodeWithTag("category-spent-c-food", useUnmergedTree = true).assertTextEquals("$280.00")
        compose.onNodeWithTag("category-available-c-food", useUnmergedTree = true).assertTextEquals("-$80.00")
        val name = compose.onNodeWithText("Groceries", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val budgeted = compose.onNodeWithTag("category-budgeted-c-food", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val spent = compose.onNodeWithTag("category-spent-c-food", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val available = compose.onNodeWithTag("category-available-c-food", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val headBudgeted = compose.onNodeWithTag("ledger-budgeted", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val headAvailable = compose.onNodeWithTag("ledger-available", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("name stays left of budgeted: $name vs $budgeted", name.right <= budgeted.left + 1f)
        assertTrue("spent follows budgeted: $budgeted vs $spent", budgeted.right <= spent.left + 1f)
        assertTrue("available follows spent: $spent vs $available", spent.right <= available.left + 1f)
        assertTrue("budgeted column lines up: $budgeted vs $headBudgeted", kotlin.math.abs(budgeted.right - headBudgeted.right) <= 1f)
        assertTrue(
            "available column lines up: $available vs $headAvailable",
            kotlin.math.abs(available.right - headAvailable.right) <= 1f,
        )
    }

    @Test
    fun threeHundredGroupsStayLazy() {
        val groups = (0 until 300).map { GroupRow("g$it", "group $it", 100L) }
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(320.dp)) {
                    BudgetSection(
                        sample().copy(
                            groups = groups,
                        ),
                        onListGesture = {},
                    )
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

    @Test
    fun sampleBudgetsScreenListsDatasets() {
        compose.setContent {
            MetroTheme {
                SampleBudgetsScreen(
                    samples = app.duenorth.budget.core.MockBudgets.all,
                    error = null,
                    onChoose = {},
                )
            }
        }
        compose.onNodeWithTag("sample-budgets-screen").assertIsDisplayed()
        compose.onNodeWithTag("sample-household").assertIsDisplayed()
        compose.onNodeWithTag("sample-full-tour").assertIsDisplayed()
        compose.onNodeWithTag("sample-wide").assertIsDisplayed()
        compose.onNodeWithText(ShellCopy.SAMPLE_BUDGETS).assertIsDisplayed()
    }

    @Test
    fun budgetsScreenHidesSamplesUnlessDeveloperMode() {
        compose.setContent {
            MetroTheme {
                BudgetsScreen(
                    budgets = emptyList(),
                    openId = null,
                    onOpen = {},
                    onCreate = {},
                    onSamples = null,
                )
            }
        }
        compose.onAllNodesWithTag("sample-budgets").assertCountEquals(0)
    }

    @Test
    fun budgetsScreenShowsSamplesInDeveloperMode() {
        compose.setContent {
            MetroTheme {
                BudgetsScreen(
                    budgets = emptyList(),
                    openId = null,
                    onOpen = {},
                    onCreate = {},
                    onSamples = {},
                )
            }
        }
        compose.onNodeWithTag("sample-budgets").assertIsDisplayed()
    }

    @Test
    fun goalBarsShowOnGoalRowsOnlyWhileTemplatesAreOn() {
        val shell =
            sample().copy(
                groups =
                    listOf(
                        GroupRow(
                            "g-bills",
                            "Bills",
                            18_000,
                            listOf(
                                CategoryRow("c-power", "Power", 10_000, 0, 10_000, false, goalMinor = 14_000),
                                CategoryRow("c-dining", "Dining", 8_000, 0, 8_000, false),
                            ),
                        ),
                    ),
            )
        val templatesOn = mutableStateOf(true)
        compose.setContent {
            MetroTheme {
                CompositionLocalProvider(
                    LocalFormatPrefs provides FormatPrefs(experimental = true, goalTemplates = templatesOn.value),
                ) {
                    Box(Modifier.width(390.dp).height(640.dp)) { BudgetSection(shell, onListGesture = {}) }
                }
            }
        }
        compose
            .onNodeWithTag("category-goal-c-power")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(10f / 14f, 0f..1f)))
        compose.onAllNodesWithTag("category-goal-c-dining").assertCountEquals(0)
        templatesOn.value = false
        compose.onAllNodesWithTag("category-goal-c-power").assertCountEquals(0)
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
