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
        model.renameEnvelopeCategory(id, "Dining out ")
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
        model.renameCategoryGroup(id, "Everyday")
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
        model.renameEnvelopeCategory(category.id, "  ")
        assertEquals(category.name, model.category(category.id))
        settleMain()
        assertEquals(category.name, model.category(category.id))
        assertEquals(EnvelopeCopy.ENTER_NAME, model.state.value.editError)
    }

    private fun ShellViewModel.page() = state.value.categoryManage!!

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
        model.showManageCategories()
        awaitMain { model.state.value.categoryManage != null }
        return model
    }
}
