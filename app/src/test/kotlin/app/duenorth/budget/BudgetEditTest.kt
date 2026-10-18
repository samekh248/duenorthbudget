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
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.SyncCopy
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
        await { model.saved().category("c-food")?.budgetedMinor == food }
        await { model.saved().headerMinor == before.headerMinor - 2_500 }
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
        settle(model)
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
        model.openMoveFrom(from.id)
        model.submitMoveCategory(to.id, "10")
        assertEquals(ShellRoute.Home, model.state.value.route)
        model.cancelBudgetEdit()
        assertNull(model.state.value.budgetEdit)
        settle(model)
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
        model.openMoveFrom(from.id)
        assertEquals(ShellRoute.MoveCategory(from.id), model.state.value.route)
        // The screen reads the box as the person typed it, sums included.
        model.submitMoveCategory(
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
        await { model.saved().category(to.id)?.budgetedMinor == to.budgetedMinor + 1_000 }
        await { model.saved().category(other.id)?.budgetedMinor == other.budgetedMinor + 500 }
        assertEquals(from.budgetedMinor - 1_000, model.saved().category(from.id)!!.budgetedMinor)
        await { model.state.value.undoLabel != null }

        // One undo takes the whole edit back.
        model.undo()
        await { model.saved().groups == before.groups }
    }

    @Test
    fun movingMoreThanIsAvailableIsRefused() {
        val model = household("too-much")
        val before = model.state.value.shell!!
        val (from, to) = before.groups.flatMap { it.categories }.filter { it.availableMinor > 0 }.take(2)
        model.startBudgetEdit()
        model.openMoveFrom(from.id)
        model.submitMoveCategory(to.id, MoneyParse.formatMagnitude(from.availableMinor + 100, 2))
        assertEquals(EnvelopeCopy.NOT_ENOUGH, model.state.value.editError)
        assertEquals(ShellRoute.MoveCategory(from.id), model.state.value.route)
    }

    @Test
    fun movingToToBudgetRaisesIt() {
        val model = household("move-to-budget")
        val before = model.state.value.shell!!
        val from = before.groups.flatMap { it.categories }.first { it.availableMinor > 1_000 }
        model.startBudgetEdit()
        model.openMoveFrom(from.id)
        model.submitMoveCategory(MOVE_TO_BUDGET, "10")
        assertEquals(ShellRoute.Home, model.state.value.route)
        assertEquals(before.headerMinor + 1_000, model.editing().headerMinor)
        model.endBudgetEdit()
        await { model.saved().headerMinor == before.headerMinor + 1_000 }
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
        settle(model)
        assertEquals(before.groups, model.saved().groups)
    }

    private fun household(name: String): ShellViewModel {
        val context = RuntimeEnvironment.getApplication()
        val root =
            File(context.filesDir, "budget-edit-$name").apply {
                deleteRecursively()
                mkdirs()
            }
        val library = BudgetLibrary(root, AndroidSessionOpener(), BudgetClock.System)
        library.createFromMock(MockBudgets.HOUSEHOLD) as CreateResult.Created
        val sync = SyncCoordinator(library, HttpActualTransport(), MemorySecretStore(), BudgetClock.System)
        val model = ShellViewModel(library, sync)
        await { !model.state.value.loading && model.state.value.shell != null }
        return model
    }

    private fun ShellViewModel.edit(): BudgetEdit = state.value.budgetEdit!!

    private fun ShellViewModel.editing(): MonthShell = state.value.editingShell()!!

    private fun ShellViewModel.saved(): MonthShell = state.value.shell!!

    // Gives any write a chance to land, so a test that expects none would see it.
    private fun settle(model: ShellViewModel) {
        repeat(20) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun MonthShell.category(id: String): CategoryRow? = groups.flatMap { it.categories }.firstOrNull { it.id == id }

    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!predicate()) {
            if (System.nanoTime() > deadline) error("timed out")
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(15)
        }
    }
}
