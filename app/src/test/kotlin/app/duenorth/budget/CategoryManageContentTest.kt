package app.duenorth.budget

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The categories page: groups fold, rows open their actions, names change in place, delete asks first. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class CategoryManageContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    private val calls = mutableListOf<String>()
    private val host = FormSaveHost()

    private fun show() {
        compose.setContent {
            MetroTheme {
                CompositionLocalProvider(LocalFormSaveHost provides host) {
                    ManageCategoriesScreen(
                        manage = CategoryManageSamples.page,
                        error = null,
                        onAddGroup = { calls += "add-group $it" },
                        onAddCategory = { g, n -> calls += "add $g $n" },
                        onRenameGroup = { g, n -> calls += "rename-group $g $n" },
                        onRenameCategory = { c, n -> calls += "rename $c $n" },
                        onHideCategory = { c, h -> calls += "hide $c $h" },
                        onHideGroup = { g, h -> calls += "hide-group $g $h" },
                        onMoveToGroup = { c, g -> calls += "move $c $g" },
                        onDeleteCategory = { calls += "delete $it" },
                        onDeleteGroup = { calls += "delete-group $it" },
                        onPlaceGroup = { g, t -> calls += "place-group $g $t" },
                        onPlaceCategory = { c, t -> calls += "place $c $t" },
                    )
                }
            }
        }
    }

    private fun bar(): List<String> {
        compose.waitForIdle()
        return host.bar.orEmpty().map { it.id }
    }

    private fun field() = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("manage-name-field")))

    @Test
    fun groupsStartFoldedAndOpenOnTap() {
        show()
        assertEquals(0, compose.onAllNodes(hasTestTag("manage-category-c-rent")).fetchSemanticsNodes().size)
        compose.onNodeWithTag("manage-group-g-bills").performClick()
        compose.onNodeWithTag("manage-category-c-rent").assertIsDisplayed()
        compose.onNodeWithTag("manage-add-category-g-bills").assertIsDisplayed()
        assertEquals(listOf("manage-new-group", "manage-reorder"), bar())
    }

    @Test
    fun renameACategoryInPlace() {
        show()
        compose.onNodeWithTag("manage-group-g-food").performClick()
        compose.onNodeWithTag("manage-category-c-rest").performClick()
        compose.onNodeWithTag("manage-sheet").assertIsDisplayed()
        assertEquals(emptyList<String>(), bar())
        compose.onNodeWithTag("manage-action-rename").performClick()
        assertEquals(listOf("manage-save", "manage-cancel"), bar())
        field().performTextReplacement("Dining out")
        field().performImeAction()
        compose.waitForIdle()
        assertEquals(listOf("rename c-rest Dining out"), calls)
        assertEquals(listOf("manage-new-group", "manage-reorder"), bar())
    }

    @Test
    fun aUsedCategoryCannotBeDeleted() {
        show()
        compose.onNodeWithTag("manage-group-g-food").performClick()
        compose.onNodeWithTag("manage-category-c-coffee").performClick()
        compose.onNodeWithTag("manage-action-delete").performClick()
        assertEquals(0, compose.onAllNodes(hasTestTag("manage-delete")).fetchSemanticsNodes().size)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun anUnusedCategoryDeletesAfterTheConfirmPage() {
        show()
        compose.onNodeWithTag("manage-group-g-save").performClick()
        compose.onNodeWithTag("manage-category-c-gym").performClick()
        compose.onNodeWithTag("manage-action-delete").performClick()
        compose.onNodeWithTag("manage-delete").assertIsDisplayed()
        compose.onNodeWithTag("manage-delete-keep").performClick()
        assertEquals(emptyList<String>(), calls)
        compose.onNodeWithTag("manage-category-c-gym").performClick()
        compose.onNodeWithTag("manage-action-delete").performClick()
        compose.onNodeWithTag("manage-delete-confirm").performClick()
        assertEquals(listOf("delete c-gym"), calls)
    }

    @Test
    fun moveACategoryToAnotherGroup() {
        show()
        compose.onNodeWithTag("manage-group-g-food").performClick()
        compose.onNodeWithTag("manage-category-c-coffee").performClick()
        compose.onNodeWithTag("manage-action-move").performClick()
        compose.onNodeWithTag("manage-move-g-bills").performClick()
        assertEquals(listOf("move c-coffee g-bills"), calls)
    }

    @Test
    fun holdAGroupForItsMenu() {
        show()
        compose.onNodeWithTag("manage-group-g-pets").performTouchInput { longClick() }
        compose.onNodeWithTag("manage-sheet").assertIsDisplayed()
        compose.onNodeWithTag("manage-action-delete").performClick()
        compose.onNodeWithTag("manage-delete-confirm").performClick()
        compose.onNodeWithTag("manage-group-g-bills").performTouchInput { longClick() }
        compose.onNodeWithTag("manage-action-delete").performClick()
        compose.onNodeWithTag("manage-action-hide").performClick()
        assertEquals(listOf("delete-group g-pets", "hide-group g-bills true"), calls)
    }

    @Test
    fun addAGroupAndACategory() {
        show()
        host.bar!!.first { it.id == "manage-new-group" }.run()
        compose.waitForIdle()
        field().performTextReplacement("Kids")
        host.bar!!.first { it.id == "manage-save" }.run()
        compose.waitForIdle()
        compose.onNodeWithTag("manage-group-g-food").performClick()
        compose.onNodeWithTag("manage-add-category-g-food").performClick()
        field().performTextReplacement("Snacks")
        field().performImeAction()
        compose.waitForIdle()
        assertEquals(listOf("add-group Kids", "add g-food Snacks"), calls)
    }

    @Test
    fun reorderShowsHandlesAndDoneEndsIt() {
        show()
        compose.onNodeWithTag("manage-group-g-food").performClick()
        host.bar!!.first { it.id == "manage-reorder" }.run()
        compose.waitForIdle()
        compose.onNodeWithTag("manage-group-handle-g-bills").assertIsDisplayed()
        compose.onNodeWithTag("manage-category-handle-c-groc").assertIsDisplayed()
        assertEquals(listOf("manage-done"), bar())
        host.bar!!.first().run()
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(hasTestTag("manage-group-handle-g-bills")).fetchSemanticsNodes().size)
        assertNull(calls.firstOrNull())
    }
}
