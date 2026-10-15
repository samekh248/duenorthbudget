package app.duenorth.budget

import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.ActualMonthMath
import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.HttpActualTransport
import app.duenorth.budget.core.IncomeCategoryRow
import app.duenorth.budget.core.IncomeGroupRow
import app.duenorth.budget.core.MemorySecretStore
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MonthFillCopy
import app.duenorth.budget.core.MonthFillKind
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.UndoCopy
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.time.YearMonth

/** Spec 010 T026: month action screens and their view model wiring. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MonthActionContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun breakdownShowsEachPartAndTheTotal() {
        val parts = ActualMonthMath.MonthBreakdown(420_000, 10_000, -5_000, -300_000, -20_000, 105_000)
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    BreakdownScreen(YearMonth.of(2026, 10), parts, USD)
                }
            }
        }
        compose.onNodeWithTag("breakdown-screen").assertExists()
        mapOf(
            "breakdown-income" to parts.incomeMinor,
            "breakdown-carried" to parts.carriedMinor,
            "breakdown-overspent" to parts.overspentMinor,
            "breakdown-budgeted" to parts.budgetedMinor,
            "breakdown-held" to parts.heldMinor,
            "breakdown-total" to parts.totalMinor,
        ).forEach { (tag, amount) ->
            compose.onNodeWithTag("$tag-amount", useUnmergedTree = true).assertTextEquals(MoneyFormat.format(amount, USD))
        }
        val sum = parts.incomeMinor + parts.carriedMinor + parts.overspentMinor + parts.budgetedMinor + parts.heldMinor
        assertEquals(parts.totalMinor, sum)
    }

    @Test
    fun fillConfirmButtonsCallBack() {
        var confirmed = 0
        var kept = 0
        compose.setContent {
            MetroTheme {
                FillConfirmScreen(
                    FillConfirm(MonthFillKind.COPY_LAST, MonthFillCopy.REPLACES),
                    YearMonth.of(2026, 10),
                    onConfirm = { confirmed++ },
                    onKeep = { kept++ },
                )
            }
        }
        compose.onNodeWithTag("fill-confirm-keep").performClick()
        compose.onNodeWithTag("fill-confirm-yes").performClick()
        assertEquals(1, confirmed)
        assertEquals(1, kept)
    }

    @Test
    fun fillWritesNothingUntilConfirmed() {
        val (model, library) = household("fill-confirm")
        val budgetId =
            model.state.value.shell!!
                .budgetId
        val month =
            model.state.value.shell!!
                .month
        model.fillMonth(MonthFillKind.COPY_LAST)
        await { model.state.value.fillConfirm != null }
        assertEquals(
            MonthFillCopy.REPLACES,
            model.state.value.fillConfirm!!
                .reason,
        )
        assertEquals(220_000L, budgeted(library, budgetId, month, "c-food"))

        model.keepFill()
        assertNull(model.state.value.fillConfirm)
        drain()
        assertEquals(220_000L, budgeted(library, budgetId, month, "c-food"))

        model.fillMonth(MonthFillKind.COPY_LAST)
        await { model.state.value.fillConfirm != null }
        model.confirmFill()
        await {
            model.state.value.shell
                ?.category("c-food")
                ?.budgetedMinor == 200_000L
        }
        assertEquals(200_000L, budgeted(library, budgetId, month, "c-food"))
        assertEquals(MonthFillCopy.COPY_LAST, model.state.value.undoLabel)
    }

    @Test
    fun zeroAlwaysAsks() {
        val (model, library) = household("zero")
        val budgetId =
            model.state.value.shell!!
                .budgetId
        val month =
            model.state.value.shell!!
                .month
        model.fillMonth(MonthFillKind.ZERO)
        await { model.state.value.fillConfirm != null }
        assertEquals(
            MonthFillCopy.ZERO_CONFIRM,
            model.state.value.fillConfirm!!
                .reason,
        )
        assertEquals(80_000L, budgeted(library, budgetId, month, "c-gas"))
        model.confirmFill()
        await {
            model.state.value.shell
                ?.category("c-gas")
                ?.budgetedMinor == 0L
        }
    }

    @Test
    fun transferToBudgetLowersTheCategoryAndRaisesTheHeader() {
        val (model, _) = household("to-budget")
        val before = model.state.value.shell!!
        model.openCategoryBudget("c-food")
        model.openToBudget()
        assertEquals(ShellRoute.ToBudget("c-food"), model.state.value.route)
        model.submitToBudget("25")
        // Optimistic: painted before the write finishes.
        assertEquals(
            before.headerMinor + 2_500,
            model.state.value.shell!!
                .headerMinor,
        )
        await { model.state.value.route == ShellRoute.CategoryBudget("c-food") }
        await { model.state.value.undoLabel == MonthFillCopy.TO_BUDGET }
        // The repaint after sync settles the figures; an earlier month load may land in between.
        await {
            model.state.value.shell
                ?.category("c-food")
                ?.budgetedMinor == before.category("c-food")!!.budgetedMinor - 2_500
        }
        assertEquals(
            before.category("c-food")!!.budgetedMinor - 2_500,
            model.state.value.shell!!
                .category("c-food")!!
                .budgetedMinor,
        )
        assertEquals(
            before.headerMinor + 2_500,
            model.state.value.shell!!
                .headerMinor,
        )
    }

    @Test
    fun transferMoreThanAvailableRollsBack() {
        val (model, _) = household("to-budget-refused")
        val before = model.state.value.shell!!
        model.openCategoryBudget("c-food")
        model.openToBudget()
        model.submitToBudget("999999")
        await { model.state.value.editError != null }
        assertEquals(
            before.headerMinor,
            model.state.value.shell!!
                .headerMinor,
        )
        assertEquals(ShellRoute.ToBudget("c-food"), model.state.value.route)
    }

    @Test
    fun incomeCategoriesShowUnderTheirGroupOnHome() {
        var opened: String? = null
        val shell = sample(BudgetMode.ENVELOPE)
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    HomePivot(
                        shell = shell,
                        review = null,
                        netWorth = null,
                        upcoming = emptyList(),
                        loading = false,
                        openMonth = shell.month,
                        monthWindow = MonthWindow.around(shell.month),
                        onGesture = {},
                        onBudgetCategory = { opened = it },
                    )
                }
            }
        }
        compose
            .onNodeWithTag("income-group-received-g-inc", useUnmergedTree = true)
            .assertTextEquals(MoneyFormat.format(420_000, USD))
        compose.onNodeWithTag("income-group-g-inc").assertIsDisplayed()
        // The full-width pivot item lays out more rows, so scroll the list to the category first.
        compose
            .onAllNodes(hasScrollToNodeAction() and hasAnyDescendant(hasTestTag("income-group-g-inc")))
            .onLast()
            .performScrollToNode(hasTestTag("income-category-c-sal"))
        compose.onNodeWithTag("income-category-c-sal").performClick()
        assertEquals("c-sal", opened)
    }

    @Test
    fun envelopeIncomeCategoryTurnsAutoHoldOn() {
        val (model, _) = household("auto-hold")
        val salary =
            model.state.value.shell!!
                .incomeCategory("c-sal")!!
        assertFalse(salary.autoHold)
        model.openCategoryBudget("c-sal")
        compose.setContent {
            MetroTheme {
                val state = model.state.collectAsState().value
                IncomeCategoryScreen(
                    shell = state.shell!!,
                    category = state.shell!!.incomeCategory("c-sal")!!,
                    error = state.editError,
                    onSave = model::saveCategoryBudget,
                    onAutoHold = model::chooseAutoHold,
                )
            }
        }
        compose.onNodeWithTag("income-budget-amount").assertDoesNotExist()
        compose.onNodeWithTag("auto-hold-on").performClick()
        await {
            model.state.value.shell
                ?.incomeCategory("c-sal")
                ?.autoHold == true
        }
        await { model.state.value.undoLabel == MonthFillCopy.AUTO_HOLD }
    }

    @Test
    fun trackingBudgetsIncome() {
        val (model, library) = household("tracking-income", tracking = true)
        val budgetId =
            model.state.value.shell!!
                .budgetId
        val month =
            model.state.value.shell!!
                .month
        assertEquals(
            BudgetMode.TRACKING,
            model.state.value.shell!!
                .mode,
        )
        model.openCategoryBudget("c-sal")
        model.saveCategoryBudget("5000")
        await {
            model.state.value.shell
                ?.incomeCategory("c-sal")
                ?.budgetedMinor == 500_000L
        }
        assertEquals(500_000L, library.readShell(budgetId, month)!!.incomeCategory("c-sal")!!.budgetedMinor)
    }

    @Test
    fun trackingHidesBreakdownToBudgetAndAutoHold() {
        val home = ScreenBar.specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.TRACKING).map { it.id }
        assertEquals(
            listOf("add", "categories", "settings", "copy-last", "average", "zero", "add-schedule", "rules", "reports"),
            home,
        )
        val category = ScreenBar.specsFor(ShellRoute.CategoryBudget("c"), hasBudget = true, mode = BudgetMode.TRACKING).map { it.id }
        assertFalse(category.contains("to-budget"))
        val envelope = ScreenBar.specsFor(ShellRoute.CategoryBudget("c"), hasBudget = true, mode = BudgetMode.ENVELOPE).map { it.id }
        assertTrue(envelope.contains("to-budget"))

        var opened = 0
        val shell = sample(BudgetMode.TRACKING)
        val mode = androidx.compose.runtime.mutableStateOf(BudgetMode.TRACKING)
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    HomePivot(
                        shell = shell.copy(mode = mode.value),
                        review = null,
                        netWorth = null,
                        upcoming = emptyList(),
                        loading = false,
                        openMonth = shell.month,
                        monthWindow = MonthWindow.around(shell.month),
                        onGesture = {},
                        onToBudget = if (mode.value == BudgetMode.ENVELOPE) ({ opened++ }) else null,
                    )
                }
            }
        }
        compose.onNodeWithTag("to-budget-figure").assertDoesNotExist()
        mode.value = BudgetMode.ENVELOPE
        compose.waitForIdle()
        compose.onNodeWithTag("to-budget-figure").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun undoIsAbsentWhenEmptyAndPresentAfterAnAssign() {
        val (model, library) = household("undo")
        val budgetId =
            model.state.value.shell!!
                .budgetId
        val month =
            model.state.value.shell!!
                .month
        assertNull(model.state.value.undoLabel)
        assertFalse(homeIds(model).contains("undo"))

        model.openCategoryBudget("c-food")
        model.saveCategoryBudget("250")
        await { model.state.value.undoLabel != null }
        val undo = ScreenBar.specsFor(ShellRoute.Home, true, BudgetMode.ENVELOPE, model.state.value.undoLabel).single { it.id == "undo" }
        assertEquals("undo assign", undo.label)
        assertFalse(undo.primary)
        assertEquals(25_000L, budgeted(library, budgetId, month, "c-food"))

        model.undo()
        // The label clears when the step is popped; the notice follows the write.
        await { model.state.value.monthNotice == "${MonthActionCopy.UNDONE} ${UndoCopy.ASSIGN}" }
        await { model.state.value.undoLabel == null }
        assertEquals(220_000L, budgeted(library, budgetId, month, "c-food"))
        await {
            model.state.value.shell
                ?.category("c-food")
                ?.budgetedMinor == 220_000L
        }
        assertFalse(homeIds(model).contains("undo"))
    }

    private fun homeIds(model: ShellViewModel) =
        ScreenBar.specsFor(ShellRoute.Home, true, BudgetMode.ENVELOPE, model.state.value.undoLabel).map { it.id }

    private fun household(
        name: String,
        tracking: Boolean = false,
    ): Pair<ShellViewModel, BudgetLibrary> {
        val context = RuntimeEnvironment.getApplication()
        val root =
            File(context.filesDir, "month-actions-$name").apply {
                deleteRecursively()
                mkdirs()
            }
        val library = BudgetLibrary(root, AndroidSessionOpener(), BudgetClock.System)
        val created = library.createFromMock(MockBudgets.HOUSEHOLD) as CreateResult.Created
        if (tracking) {
            library.useDatabase(created.id) { session ->
                session.exec("UPDATE preferences SET value = 'tracking' WHERE id = 'budgetType'")
            }
        }
        val sync = SyncCoordinator(library, HttpActualTransport(), MemorySecretStore(), BudgetClock.System)
        val model = ShellViewModel(library, sync)
        await { !model.state.value.loading && model.state.value.shell != null }
        return model to library
    }

    private fun budgeted(
        library: BudgetLibrary,
        budgetId: String,
        month: YearMonth,
        categoryId: String,
    ): Long = library.readShell(budgetId, month)!!.category(categoryId)!!.budgetedMinor

    private fun MonthShell.category(id: String): CategoryRow? = groups.flatMap { it.categories }.firstOrNull { it.id == id }

    private fun sample(mode: BudgetMode) =
        MonthShell(
            budgetId = "b",
            budgetName = "Home",
            mode = mode,
            currency = USD,
            month = YearMonth.of(2026, 10),
            headerLabel = "to budget",
            headerMinor = 12_000,
            groups =
                listOf(
                    GroupRow("g", "Living", 5_000, listOf(CategoryRow("c", "Food", 5_000, 0, 5_000, false))),
                ),
            accounts = emptyList(),
            inbox = emptyList(),
            incomeGroups =
                listOf(
                    IncomeGroupRow("g-inc", "Income", 0, 420_000, listOf(IncomeCategoryRow("c-sal", "Salary", 0, 420_000, false))),
                ),
        )

    private fun drain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!predicate()) {
            if (System.nanoTime() > deadline) error("timed out")
            drain()
            Thread.sleep(15)
        }
    }

    private companion object {
        val USD = Currencies.byCode("USD")!!
    }
}
