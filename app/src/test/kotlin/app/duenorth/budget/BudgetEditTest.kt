package app.duenorth.budget

import android.os.Looper
import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.EnvelopeCopy
import app.duenorth.budget.core.HttpActualTransport
import app.duenorth.budget.core.MemorySecretStore
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.MoneyParse
import app.duenorth.budget.core.MonthFillKind
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.TemplateResult
import app.duenorth.budget.core.UndoCopy
import app.duenorth.budget.core.createFromMock
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/** The budget's edit mode: boxes typed into on the keypad, sums worked out, nothing saved until done. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BudgetEditTest {
    /** The budget library behind the model [household] made last. */
    private lateinit var library: BudgetLibrary

    @Test
    fun plusTypedOnTheKeypadAddsToTheOldAmountButSavesNothingUntilDone() {
        val model = household("plus")
        val before = model.state.value.shell!!
        model.startBudgetEdit()
        model.focusBudgetBox("c-food")
        listOf("+", "2", "5").forEach(model::typeBudgetKey)
        assertEquals("+25", model.edit().text)
        model.doneBudgetBox()
        assertNull(model.edit().categoryId)
        val food = before.category("c-food")!!.budgetedMinor + 2_500
        // Edit mode shows the new amount and to budget less it; the saved month is untouched.
        assertEquals(food, model.editing().category("c-food")!!.budgetedMinor)
        assertEquals(before.headerMinor - 2_500, model.editing().headerMinor)
        assertEquals(before.category("c-food")!!.budgetedMinor, model.saved().category("c-food")!!.budgetedMinor)

        model.endBudgetEdit()
        assertNull(model.state.value.budgetEdit)
        awaitMain { model.saved().category("c-food")?.budgetedMinor == food }
        awaitMain { model.saved().headerMinor == before.headerMinor - 2_500 }
    }

    @Test
    fun equalsWorksTheSumOutInTheBox() {
        val model = household("equals")
        model.startBudgetEdit()
        model.focusBudgetBox("c-food")
        listOf("3", "0", "0", "÷", "2", BudgetKeys.EQUALS).forEach(model::typeBudgetKey)
        assertEquals("150.00", model.edit().text)
    }

    @Test
    fun aSumThatDoesNotWorkOutKeepsTheKeypadOpen() {
        val model = household("bad")
        val before = model.state.value.shell!!
        model.startBudgetEdit()
        model.focusBudgetBox("c-food")
        listOf("5", "÷", "0").forEach(model::typeBudgetKey)
        model.doneBudgetBox()
        assertEquals(SyncCopy.ENTER_AMOUNT, model.state.value.editError)
        assertEquals("c-food", model.edit().categoryId)
        assertEquals(before.category("c-food")!!.budgetedMinor, model.editing().category("c-food")!!.budgetedMinor)
    }

    @Test
    fun backLeavesEditModeAndDropsEverythingChanged() {
        val model = household("back")
        val before = model.state.value.shell!!
        model.startBudgetEdit()
        model.focusBudgetBox("c-food")
        listOf("9", "9").forEach(model::typeBudgetKey)
        model.focusBudgetBox("c-rent")
        listOf("+", "5").forEach(model::typeBudgetKey)
        assertTrue(model.back())
        assertNull(model.state.value.budgetEdit)
        assertEquals(ShellRoute.Home, model.state.value.route)
        settleMain()
        assertEquals(before.groups, model.saved().groups)
        assertEquals(before.headerMinor, model.saved().headerMinor)
    }

    @Test
    fun cancelDropsEverythingChanged() {
        val model = household("cancel")
        val before = model.state.value.shell!!
        val undo = model.state.value.undoLabel
        val (from, to) =
            before.groups
                .flatMap { it.categories }
                .filter { it.availableMinor > 1_000 }
                .take(2)
        model.startBudgetEdit()
        model.focusBudgetBox("c-food")
        listOf("1", "2").forEach(model::typeBudgetKey)
        model.doneBudgetBox()
        model.envelopes.openMoveFrom(from.id)
        model.envelopes.submitMove(to.id, "10")
        assertEquals(ShellRoute.Home, model.state.value.route)
        model.cancelBudgetEdit()
        assertNull(model.state.value.budgetEdit)
        settleMain()
        assertEquals(before.groups, model.saved().groups)
        assertEquals(undo, model.state.value.undoLabel)
    }

    @Test
    fun movingToAnotherBoxKeepsTheFirstInEditMode() {
        val model = household("move")
        val before = model.state.value.shell!!
        val (first, second) = before.groups.flatMap { it.categories }.take(2)
        model.startBudgetEdit()
        model.focusBudgetBox(first.id)
        listOf("-", "1", "0").forEach(model::typeBudgetKey)
        model.focusBudgetBox(second.id)
        assertEquals(second.id, model.edit().categoryId)
        assertEquals("", model.edit().text)
        assertEquals(first.budgetedMinor - 1_000, model.editing().category(first.id)!!.budgetedMinor)
        assertEquals(first.budgetedMinor, model.saved().category(first.id)!!.budgetedMinor)
    }

    @Test
    fun tappingAvailableMovesMoneyInEditModeAndDoneSavesItAsOneUndo() {
        val model = household("move-money")
        val before = model.state.value.shell!!
        val (from, to) =
            before.groups
                .flatMap { it.categories }
                .filter { it.availableMinor > 1_000 }
                .take(2)
        model.startBudgetEdit()
        model.envelopes.openMoveFrom(from.id)
        assertEquals(ShellRoute.MoveCategory(from.id), model.state.value.route)
        // The screen reads the box as the person typed it, sums included.
        model.envelopes.submitMove(
            to.id,
            model.state.value.formatPrefs
                .readAmount("4+6"),
        )
        assertEquals(ShellRoute.Home, model.state.value.route)
        // Budgeted moves with available: the source gives up what it moved, the destination gains it.
        assertEquals(to.budgetedMinor + 1_000, model.editing().category(to.id)!!.budgetedMinor)
        assertEquals(to.availableMinor + 1_000, model.editing().category(to.id)!!.availableMinor)
        assertEquals(from.budgetedMinor - 1_000, model.editing().category(from.id)!!.budgetedMinor)
        assertEquals(from.availableMinor - 1_000, model.editing().category(from.id)!!.availableMinor)
        assertEquals(from.budgetedMinor, model.saved().category(from.id)!!.budgetedMinor)

        val other = before.groups.flatMap { it.categories }.first { it.id != from.id && it.id != to.id }
        model.focusBudgetBox(other.id)
        listOf("+", "5").forEach(model::typeBudgetKey)
        model.endBudgetEdit()
        awaitMain { model.saved().category(to.id)?.budgetedMinor == to.budgetedMinor + 1_000 }
        awaitMain { model.saved().category(other.id)?.budgetedMinor == other.budgetedMinor + 500 }
        assertEquals(from.budgetedMinor - 1_000, model.saved().category(from.id)!!.budgetedMinor)
        awaitMain { model.state.value.undoLabel != null }

        // One undo takes the whole edit back.
        model.month.undo()
        awaitMain { model.saved().groups == before.groups }
    }

    @Test
    fun movingMoreThanIsAvailableIsRefused() {
        val model = household("too-much")
        val before = model.state.value.shell!!
        val (from, to) =
            before.groups
                .flatMap { it.categories }
                .filter { it.availableMinor > 0 }
                .take(2)
        model.startBudgetEdit()
        model.envelopes.openMoveFrom(from.id)
        model.envelopes.submitMove(to.id, MoneyParse.formatMagnitude(from.availableMinor + 100, 2))
        assertEquals(EnvelopeCopy.NOT_ENOUGH, model.state.value.editError)
        assertEquals(ShellRoute.MoveCategory(from.id), model.state.value.route)
    }

    @Test
    fun movingToToBudgetRaisesIt() {
        val model = household("move-to-budget")
        val before = model.state.value.shell!!
        val from = before.groups.flatMap { it.categories }.first { it.availableMinor > 1_000 }
        model.startBudgetEdit()
        model.envelopes.openMoveFrom(from.id)
        model.envelopes.submitMove(MOVE_TO_BUDGET, "10")
        assertEquals(ShellRoute.Home, model.state.value.route)
        assertEquals(before.headerMinor + 1_000, model.editing().headerMinor)
        model.endBudgetEdit()
        awaitMain { model.saved().headerMinor == before.headerMinor + 1_000 }
    }

    @Test
    fun aMonthFillInEditModeFillsTheBoxesAndSavesOnDone() {
        val model = household("fill")
        val before = model.state.value.shell!!
        model.startBudgetEdit()
        model.month.fill(MonthFillKind.ZERO)
        awaitMain {
            model.state.value.budgetEdit
                ?.budgeted
                ?.isNotEmpty() == true
        }
        // Nothing to confirm in the sandbox, and nothing saved yet.
        assertNull(model.state.value.monthUi.fillConfirm)
        assertTrue(
            model
                .editing()
                .groups
                .flatMap { it.categories }
                .all { it.budgetedMinor == 0L },
        )
        assertEquals(before.groups, model.saved().groups)

        model.endBudgetEdit()
        awaitMain {
            model
                .saved()
                .groups
                .flatMap { it.categories }
                .all { it.budgetedMinor == 0L }
        }
    }

    @Test
    fun templatesInEditModeFillTheBoxesWithoutSaving() {
        val model = household("templates")
        model.settingsUi.setExperimental(true)
        model.settingsUi.setGoalTemplates(true)
        awaitMain { model.state.value.formatPrefs.templatesOn }
        // The screen shows the switch at once, but the file is written after; templates read the file.
        awaitMain { library.readFormatPrefs(model.state.value.settings.openBudgetId!!).templatesOn }
        model.envelopes.openBudget("c-gas")
        awaitMain { model.state.value.templates.note != null }
        model.templates.saveNote("#template 123")
        awaitMain { model.state.value.undoLabel == UndoCopy.CATEGORY_NOTE }
        model.back()
        val before = model.state.value.shell!!
        model.startBudgetEdit()
        model.templates.open()
        assertEquals(ShellRoute.Templates, model.state.value.route)
        model.templates.apply(overwrite = true)
        awaitMain { model.state.value.templates.result is TemplateResult.Applied }
        assertNull(model.state.value.templates.confirm)
        assertEquals(12_300L, model.edit().budgeted["c-gas"])
        settleMain()
        assertEquals(before.category("c-gas")!!.budgetedMinor, model.saved().category("c-gas")!!.budgetedMinor)
        assertEquals(UndoCopy.CATEGORY_NOTE, model.state.value.undoLabel)

        model.back()
        assertEquals(ShellRoute.Home, model.state.value.route)
        model.endBudgetEdit()
        awaitMain { model.saved().category("c-gas")?.budgetedMinor == 12_300L }
        awaitMain { model.state.value.undoLabel == UndoCopy.BUDGET_EDIT }
    }

    @Test
    fun leavingTheBudgetItemDropsEditMode() {
        val model = household("swipe")
        val before = model.state.value.shell!!
        model.startBudgetEdit()
        model.focusBudgetBox("c-food")
        listOf("1").forEach(model::typeBudgetKey)
        model.selectHomeSection(ScreenBar.HOME_ACCOUNTS)
        assertNull(model.state.value.budgetEdit)
        settleMain()
        assertEquals(before.groups, model.saved().groups)
    }

    private fun household(name: String): ShellViewModel {
        val context = RuntimeEnvironment.getApplication()
        val root =
            File(context.filesDir, "budget-edit-$name").apply {
                deleteRecursively()
                mkdirs()
            }
        library = BudgetLibrary(root, AndroidSessionOpener(), BudgetClock.System)
        library.createFromMock(MockBudgets.HOUSEHOLD) as CreateResult.Created
        val sync = SyncCoordinator(library, HttpActualTransport(), MemorySecretStore(), BudgetClock.System)
        val model = ShellViewModel(library, sync, io = Dispatchers.Main)
        awaitMain { !model.state.value.loading && model.state.value.shell != null }
        return model
    }

    private fun ShellViewModel.edit(): BudgetEdit = state.value.budgetEdit!!

    private fun ShellViewModel.editing(): MonthShell = state.value.editingShell()!!

    private fun ShellViewModel.saved(): MonthShell = state.value.shell!!

    private fun MonthShell.category(id: String): CategoryRow? = groups.flatMap { it.categories }.firstOrNull { it.id == id }
}
