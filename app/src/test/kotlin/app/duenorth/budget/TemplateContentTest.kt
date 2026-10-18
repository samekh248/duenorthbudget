package app.duenorth.budget

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.duenorth.budget.ParityTestSupport.await
import app.duenorth.budget.ParityTestSupport.household
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.TemplateCopy
import app.duenorth.budget.core.TemplateResult
import app.duenorth.budget.core.UndoCopy
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 010 T063: the templates menu gate, check results, overwrite confirm, and the category note. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class TemplateContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun editModeMenuHasTemplatesOnlyWhenBothFlagsAreOn() {
        for (mode in listOf(BudgetMode.ENVELOPE, BudgetMode.TRACKING)) {
            assertFalse("templates" in ScreenBar.specsFor(ShellRoute.Home, true, mode, budgetEditing = true).map { it.id })
            assertTrue("templates" in ScreenBar.specsFor(ShellRoute.Home, true, mode, templates = true, budgetEditing = true).map { it.id })
            // Templates moved off the budget's own menu into edit mode's.
            assertFalse("templates" in ScreenBar.specsFor(ShellRoute.Home, true, mode, templates = true).map { it.id })
        }
    }

    @Test
    fun badLinesAreListedByCategory() {
        compose.setContent {
            MetroTheme {
                TemplatesScreen(TemplateSamples.october, TemplateSamples.invalid, false, null, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("templates-error-0", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("#template fifty dollars").assertExists()
        compose.onNodeWithText("Car").assertExists()
    }

    @Test
    fun overwriteConfirmOffersOverwriteAndKeep() {
        var confirmed = 0
        var kept = 0
        compose.setContent {
            MetroTheme {
                TemplatesScreen(
                    TemplateSamples.october,
                    TemplateResult.Valid,
                    false,
                    TemplateCopy.OVERWRITE_CONFIRM,
                    {},
                    {},
                    {},
                    { confirmed++ },
                    { kept++ },
                )
            }
        }
        compose.onNodeWithText(TemplateCopy.OVERWRITE_CONFIRM).assertExists()
        compose.onNodeWithTag("templates-apply").assertDoesNotExist()
        compose.onNodeWithTag("templates-confirm-yes").performClick()
        compose.onNodeWithTag("templates-confirm-keep").performClick()
        assertEquals(1 to 1, confirmed to kept)
    }

    @Test
    fun noteSavesOnlyAfterAChange() {
        var saved: String? = null
        compose.setContent {
            MetroTheme {
                CategoryBudgetScreen(
                    TemplateSamples.shell,
                    "c-food",
                    null,
                    {},
                    {},
                    {},
                    note = "",
                    onSaveNote = { saved = it },
                )
            }
        }
        compose.onNodeWithTag("category-note-save").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("category-note"))).performTextInput("#template 50")
        compose.onNodeWithTag("category-note-save").performClick()
        assertEquals("#template 50", saved)
    }

    @Test
    fun noteIsHiddenWhileTemplatesAreOff() {
        compose.setContent {
            MetroTheme { CategoryBudgetScreen(TemplateSamples.shell, "c-food", null, {}, {}, {}) }
        }
        compose.onNodeWithTag("category-note").assertDoesNotExist()
    }

    @Test
    fun noteThenOverwriteBudgetsTheCategoryAsOneUndoStep() {
        val (model, library) = household("templates")
        model.openTemplates()
        assertEquals(ShellRoute.Home, model.state.value.route)
        model.setExperimental(true)
        model.setGoalTemplates(true)
        await { model.state.value.formatPrefs.templatesOn }
        model.openCategoryBudget("c-gas")
        await { model.state.value.categoryNote != null }
        model.saveCategoryNote("#template 123")
        await { model.state.value.undoLabel == UndoCopy.CATEGORY_NOTE }
        model.openTemplates()
        assertEquals(ShellRoute.Templates, model.state.value.route)
        await { model.state.value.templateResult == TemplateResult.Valid }
        model.applyTemplates(overwrite = true)
        await { model.state.value.templateConfirm != null }
        model.confirmTemplateOverwrite()
        await {
            model.state.value.shell
                ?.category("c-gas")
                ?.budgetedMinor == 12_300L
        }
        assertEquals(TemplateResult.Applied(1), model.state.value.templateResult)
        assertEquals(UndoCopy.TEMPLATES, library.undoLabel(model.state.value.settings.openBudgetId!!))
    }

    private fun MonthShell.category(id: String): CategoryRow? = groups.flatMap { it.categories }.firstOrNull { it.id == id }
}
