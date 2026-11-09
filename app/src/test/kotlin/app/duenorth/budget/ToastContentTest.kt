package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.TemplateCopy
import app.duenorth.budget.core.TemplateResult
import app.duenorth.budget.core.WriteResult
import app.duenorth.budget.core.createFromMock
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroStyle
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The global toast: it outlives the page that posted it, times out on success, and stays up on failure. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class ToastContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun aToastStaysUpWhenThePageUnderItChanges() {
        var page by mutableStateOf("category")
        var toast by mutableStateOf<Toast?>(null)
        compose.setContent {
            MetroTheme {
                Box(Modifier.fillMaxSize()) {
                    // The page is swapped as navigation swaps it; the host is not part of it.
                    MetroText(page, Metro.typography.body, Modifier.testTag("page-$page"))
                    ToastHost(toast, { id -> if (toast?.id == id) toast = null }, {}, Modifier.align(Alignment.TopCenter))
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            toast = Toast(ToastKind.Success, ToastCopy.CATEGORY_DELETED, "Dining", ToastAction.Undo)
            page = "budget"
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("page-budget").assertExists()
        compose.onNodeWithTag("toast").assertExists()
        compose.mainClock.advanceTimeBy(ToastTiming.WITH_ACTION_MS + 1_000)
        compose.waitForIdle()
        assertNull(toast)
    }

    @Test
    fun aFailureStaysUntilTapped() {
        var toast by mutableStateOf<Toast?>(Toast(ToastKind.Failure, ToastCopy.TRANSACTION_SAVE_FAILED, "Server unreachable"))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MetroTheme(style = MetroStyle.Mobile10) {
                ToastHost(toast, { id -> if (toast?.id == id) toast = null }, {})
            }
        }
        compose.mainClock.advanceTimeBy(30_000)
        compose.onNodeWithTag("toast").assertExists()
        compose.onNodeWithTag("toast").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        assertNull(toast)
    }

    @Test
    fun undoOnTheToastRunsItsAction() {
        val posted = Toast(ToastKind.Success, ToastCopy.TRANSACTION_SAVED, action = ToastAction.Undo)
        var acted: Toast? = null
        compose.setContent { MetroTheme(style = MetroStyle.Mica) { ToastHost(posted, {}, { acted = it }) } }
        compose.onNodeWithTag("toast-action", useUnmergedTree = true).performClick()
        assertEquals(posted, acted)
    }

    @Test
    fun aWriteResultPicksTheToast() {
        val host = host("toast-write-result")
        host.toastFor(WriteResult.Saved, ToastCopy.TRANSACTION_ADDED, ToastCopy.TRANSACTION_SAVE_FAILED)
        val saved = host.state.value.toast!!
        assertEquals(ToastKind.Success, saved.kind)
        assertEquals(ToastAction.Undo, saved.action)
        host.toastFor(WriteResult.Rejected("No account"), ToastCopy.TRANSACTION_ADDED, ToastCopy.TRANSACTION_SAVE_FAILED)
        val failed = host.state.value.toast!!
        assertEquals(ToastKind.Failure, failed.kind)
        assertEquals("No account", failed.message)
        assertNotEquals(saved.id, failed.id)
    }

    @Test
    fun addingACategoryPostsItsToast() {
        val host = host("toast-add-category")
        val group =
            host.state.value.shell!!
                .groups
                .first()
        EnvelopesHolder(host).addCategory(group.id, "Pets")
        val toast = host.state.value.toast!!
        assertEquals(ToastKind.Success, toast.kind)
        assertEquals(ToastCopy.CATEGORY_ADDED, toast.title)
        assertEquals("Pets", toast.message)
    }

    @Test
    fun aRefusedCategoryWriteSaysWhy() {
        val host = host("toast-refused-category")
        val category =
            host.state.value.shell!!
                .groups
                .flatMap { it.categories }
                .first()
        // The first category holds money, so deleting it without saying where the money goes is refused.
        EnvelopesHolder(host).deleteCategory(category.id)
        val toast = host.state.value.toast!!
        assertEquals(ToastKind.Failure, toast.kind)
        assertEquals(ToastCopy.CATEGORY_SAVE_FAILED, toast.title)
    }

    @Test
    fun applyingTemplatesOnTheMonthSaysHowItWent() {
        val host = host("toast-apply-templates")
        val budgetId = host.state.value.settings.openBudgetId!!
        host.mutate { it.copy(route = ShellRoute.Templates) }
        // Templates are off in a new budget, so apply is refused and says why.
        TemplatesHolder(host).apply(overwrite = false)
        val refused = host.state.value.toast!!
        assertEquals(ToastKind.Failure, refused.kind)
        assertEquals(ToastCopy.TEMPLATES_FAILED, refused.title)
        assertEquals(TemplateCopy.OFF, refused.message)

        host.library.setPreference(budgetId, "flags.experimental", "true")
        host.library.setPreference(budgetId, "flags.goalTemplatesEnabled", "true")
        TemplatesHolder(host).apply(overwrite = false)
        val applied = host.state.value.toast!!
        assertEquals(ToastKind.Success, applied.kind)
        assertEquals(ToastCopy.TEMPLATES_APPLIED, applied.title)
        assertEquals(ToastAction.Undo, applied.action)
    }

    @Test
    fun aTemplatesRunSaysHowItWent() {
        val host = host("toast-templates-result")
        host.toastTemplates(TemplateResult.Applied(3), written = false)
        val filled = host.state.value.toast!!
        assertEquals(ToastCopy.TEMPLATES_FILLED, filled.title)
        assertEquals("3 categories", filled.message)
        // Edit mode only fills the boxes, so there is nothing yet to undo.
        assertNull(filled.action)
        host.toastTemplates(TemplateResult.Rejected("Budget is locked"))
        val failed = host.state.value.toast!!
        assertEquals(ToastKind.Failure, failed.kind)
        assertEquals(ToastCopy.TEMPLATES_FAILED, failed.title)
        assertEquals("Budget is locked", failed.message)
    }

    private fun host(name: String): FakeShellHost {
        val root =
            File(RuntimeEnvironment.getApplication().filesDir, name).apply {
                deleteRecursively()
                mkdirs()
            }
        val library = BudgetLibrary(root, AndroidSessionOpener(), BudgetClock.System)
        val id = (library.createFromMock(MockBudgets.HOUSEHOLD) as CreateResult.Created).id
        return FakeShellHost(library, ShellUiState(settings = library.settings(), shell = library.readShell(id)))
    }
}
