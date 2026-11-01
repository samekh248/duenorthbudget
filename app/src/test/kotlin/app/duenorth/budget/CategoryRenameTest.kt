package app.duenorth.budget

import android.os.Looper
import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.EnvelopeCopy
import app.duenorth.budget.core.HttpActualTransport
import app.duenorth.budget.core.MemorySecretStore
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.createFromMock
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/** Renames on the categories page show at once, before the write and the reads after it land. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CategoryRenameTest {
    @Test
    fun aRenamedCategoryShowsItsNewNameRightAway() {
        val model = household("category")
        val id =
            model
                .page()
                .groups
                .first()
                .categories
                .first()
                .id
        model.envelopes.renameCategory(id, "Dining out ")
        // Nothing has run on the main thread yet, so this is the page before the write lands.
        assertEquals("Dining out", model.category(id))
        settleMain()
        assertEquals("Dining out", model.category(id))
    }

    @Test
    fun aRenamedGroupShowsItsNewNameRightAway() {
        val model = household("group")
        val id =
            model
                .page()
                .groups
                .first()
                .id
        model.envelopes.renameGroup(id, "Everyday")
        assertEquals(
            "Everyday",
            model
                .page()
                .groups
                .first { it.id == id }
                .name,
        )
        settleMain()
        assertEquals(
            "Everyday",
            model
                .page()
                .groups
                .first { it.id == id }
                .name,
        )
    }

    @Test
    fun aBlankNameKeepsTheOldOne() {
        val model = household("blank")
        val category =
            model
                .page()
                .groups
                .first()
                .categories
                .first()
        model.envelopes.renameCategory(category.id, "  ")
        assertEquals(category.name, model.category(category.id))
        settleMain()
        assertEquals(category.name, model.category(category.id))
        assertEquals(EnvelopeCopy.ENTER_NAME, model.state.value.editError)
    }

    @Test
    fun theCategoryPageRenamesItsCategoryAndTheTitleChangesAtOnce() {
        val model = household("page")
        val id =
            model.state.value.shell!!
                .groups
                .first()
                .categories
                .first()
                .id
        model.envelopes.openBudget(id)
        model.envelopes.startRenameCategory()
        assertEquals(id, model.state.value.envelopes.renaming)
        model.envelopes.renameCategory(id, " Dining out")
        // The name closes and the page's title (read from the month) shows the new name before the write lands.
        assertEquals(null, model.state.value.envelopes.renaming)
        assertEquals("Dining out", model.shellName(id))
        settleMain()
        assertEquals("Dining out", model.shellName(id))
        assertEquals(null, model.state.value.editError)
    }

    @Test
    fun cancellingTheCategoryPageRenameKeepsTheName() {
        val model = household("page-cancel")
        val category =
            model.state.value.shell!!
                .groups
                .first()
                .categories
                .first()
        model.envelopes.openBudget(category.id)
        model.envelopes.startRenameCategory()
        model.envelopes.cancelRenameCategory()
        assertEquals(null, model.state.value.envelopes.renaming)
        assertEquals(category.name, model.shellName(category.id))
    }

    @Test
    fun renameOnlyOpensOnACategoryPage() {
        val model = household("page-elsewhere")
        model.envelopes.startRenameCategory()
        assertEquals(null, model.state.value.envelopes.renaming)
    }

    private fun ShellViewModel.shellName(id: String) =
        state.value.shell!!
            .groups
            .flatMap { it.categories }
            .first { it.id == id }
            .name

    private fun ShellViewModel.page() = state.value.envelopes.manage!!

    private fun ShellViewModel.category(id: String) =
        page()
            .groups
            .flatMap { it.categories }
            .first { it.id == id }
            .name

    private fun household(name: String): ShellViewModel {
        val context = RuntimeEnvironment.getApplication()
        val root =
            File(context.filesDir, "category-rename-$name").apply {
                deleteRecursively()
                mkdirs()
            }
        val library = BudgetLibrary(root, AndroidSessionOpener(), BudgetClock.System)
        library.createFromMock(MockBudgets.HOUSEHOLD) as CreateResult.Created
        val sync = SyncCoordinator(library, HttpActualTransport(), MemorySecretStore(), BudgetClock.System)
        val model = ShellViewModel(library, sync, io = Dispatchers.Main)
        awaitMain { !model.state.value.loading && model.state.value.shell != null }
        model.envelopes.showManage()
        awaitMain { model.state.value.envelopes.manage != null }
        return model
    }
}
