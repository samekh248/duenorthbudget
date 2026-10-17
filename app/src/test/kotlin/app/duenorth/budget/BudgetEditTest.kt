package app.duenorth.budget

import android.os.Looper
import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.HttpActualTransport
import app.duenorth.budget.core.MemorySecretStore
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.SyncCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/** The budget's edit mode: boxes typed into on the keypad, sums worked out, each box saved as it closes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BudgetEditTest {
    @Test
    fun plusTypedOnTheKeypadAddsToTheOldAmountAndComesOutOfToBudget() {
        val model = household("plus")
        val before = model.state.value.shell!!
        model.startBudgetEdit()
        model.focusBudgetBox("c-food")
        listOf("+", "2", "5").forEach(model::typeBudgetKey)
        assertEquals(
            "+25",
            model.state.value.budgetEdit!!
                .text,
        )
        model.doneBudgetBox()
        assertEquals(BudgetEdit(), model.state.value.budgetEdit)
        val food = before.category("c-food")!!.budgetedMinor + 2_500
        await {
            model.state.value.shell
                ?.category("c-food")
                ?.budgetedMinor == food
        }
        await {
            model.state.value.shell
                ?.headerMinor == before.headerMinor - 2_500
        }
    }

    @Test
    fun equalsWorksTheSumOutInTheBox() {
        val model = household("equals")
        model.startBudgetEdit()
        model.focusBudgetBox("c-food")
        listOf("3", "0", "0", "÷", "2", BudgetKeys.EQUALS).forEach(model::typeBudgetKey)
        assertEquals(
            "150.00",
            model.state.value.budgetEdit!!
                .text,
        )
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
        assertEquals(
            "c-food",
            model.state.value.budgetEdit!!
                .categoryId,
        )
        assertEquals(
            before.category("c-food")!!.budgetedMinor,
            model.state.value.shell!!
                .category("c-food")!!
                .budgetedMinor,
        )
    }

    @Test
    fun backClosesTheKeypadWithoutSavingThenLeavesEditMode() {
        val model = household("back")
        val before = model.state.value.shell!!
        model.startBudgetEdit()
        model.focusBudgetBox("c-food")
        listOf("9", "9").forEach(model::typeBudgetKey)
        model.back()
        assertEquals(BudgetEdit(), model.state.value.budgetEdit)
        assertEquals(
            before.category("c-food")!!.budgetedMinor,
            model.state.value.shell!!
                .category("c-food")!!
                .budgetedMinor,
        )
        model.back()
        assertNull(model.state.value.budgetEdit)
        assertEquals(ShellRoute.Home, model.state.value.route)
    }

    @Test
    fun movingToAnotherBoxSavesTheFirst() {
        val model = household("move")
        val before = model.state.value.shell!!
        val (first, second) = before.groups.flatMap { it.categories }.take(2)
        model.startBudgetEdit()
        model.focusBudgetBox(first.id)
        listOf("-", "1", "0").forEach(model::typeBudgetKey)
        model.focusBudgetBox(second.id)
        assertEquals(BudgetEdit(second.id), model.state.value.budgetEdit)
        await {
            model.state.value.shell
                ?.category(first.id)
                ?.budgetedMinor == first.budgetedMinor - 1_000
        }
    }

    @Test
    fun tappingAvailableMovesMoneyToAnotherCategoryAndBackToEditMode() {
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
        await { model.state.value.route == ShellRoute.Home }
        assertEquals(BudgetEdit(), model.state.value.budgetEdit)
        await {
            model.state.value.shell
                ?.category(to.id)
                ?.availableMinor == to.availableMinor + 1_000
        }
        // Budgeted moves with available: the source gives up what it moved, the destination gains it.
        assertEquals(
            to.budgetedMinor + 1_000,
            model.state.value.shell!!
                .category(to.id)!!
                .budgetedMinor,
        )
        assertEquals(
            from.budgetedMinor - 1_000,
            model.state.value.shell!!
                .category(from.id)!!
                .budgetedMinor,
        )
        assertEquals(
            from.availableMinor - 1_000,
            model.state.value.shell!!
                .category(from.id)!!
                .availableMinor,
        )
    }

    @Test
    fun movingToToBudgetRaisesIt() {
        val model = household("move-to-budget")
        val before = model.state.value.shell!!
        val from = before.groups.flatMap { it.categories }.first { it.availableMinor > 1_000 }
        model.startBudgetEdit()
        model.openMoveFrom(from.id)
        model.submitMoveCategory(MOVE_TO_BUDGET, "10")
        await { model.state.value.route == ShellRoute.Home }
        await {
            model.state.value.shell
                ?.headerMinor == before.headerMinor + 1_000
        }
    }

    @Test
    fun leavingTheBudgetItemEndsEditMode() {
        val model = household("swipe")
        model.startBudgetEdit()
        model.selectHomeSection(ScreenBar.HOME_ACCOUNTS)
        assertNull(model.state.value.budgetEdit)
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
