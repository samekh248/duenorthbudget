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
import app.duenorth.budget.core.Template
import app.duenorth.budget.core.TemplateCopy
import app.duenorth.budget.core.TemplateKind
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
import java.math.BigDecimal

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
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("category-note"))).performTextInput("buy in bulk")
        compose.onNodeWithTag("category-note-save").performClick()
        assertEquals("buy in bulk", saved)
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

    @Test
    fun tilesShowEachTemplateWhatItAsksAndTheLineThatDoesNotRead() {
        var opened: Int? = null
        compose.setContent {
            MetroTheme {
                CategoryBudgetScreen(
                    TemplateSamples.shell,
                    "c-food",
                    null,
                    {},
                    {},
                    {},
                    note = TemplateSamples.TILES,
                    templateAmounts = TemplateSamples.tileAmounts,
                    onOpenTemplate = { opened = it },
                )
            }
        }
        compose.onNodeWithText("\$400.00 every month").assertExists()
        compose.onNodeWithText("held up to \$600.00").assertExists()
        compose.onNodeWithText("10% of all income").assertExists()
        compose.onNodeWithText("goal of \$500.00").assertExists()
        compose.onNodeWithText(TemplateEditorCopy.UNREADABLE).assertExists()
        compose.onNodeWithTag("template-asks", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("\$560.00").assertExists()
        // The note box shows only the person's own words.
        compose.onNodeWithText("weekly shop on sunday").assertExists()
        compose.onNodeWithTag("template-tile-1").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theFormWritesTheLineAsItIsFilledIn() {
        compose.setContent {
            MetroTheme {
                TemplateEditorScreen(
                    categoryName = "Food",
                    kind = TemplateKind.MONTHLY,
                    initial = TemplateKind.MONTHLY.blank(1, TemplateSamples.october, ""),
                    isNew = true,
                    month = TemplateSamples.october,
                    currency = TemplateSamples.shell.currency,
                    schedules = emptyList(),
                    sources = emptyList(),
                    preview = null,
                    onPreview = {},
                    onSave = {},
                    onRemove = {},
                )
            }
        }
        compose.onNodeWithText(TemplateEditorCopy.NEEDS).assertExists()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("template-amount"))).performTextInput("400")
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("template-cap"))).performTextInput("600")
        compose.onNodeWithText("#template-1 400 up to 600").assertExists()
        compose.onNodeWithTag("template-order-up").performClick()
        compose.onNodeWithText("#template-2 400 up to 600").assertExists()
        compose.onNodeWithTag("template-remove").assertDoesNotExist()
    }

    @Test
    fun addEditMoveAndRemoveTemplatesThroughTheNote() {
        val (model, library) = household("template-editor")
        model.setExperimental(true)
        model.setGoalTemplates(true)
        await { model.state.value.formatPrefs.templatesOn }
        model.openCategoryBudget("c-gas")
        await { model.state.value.categoryNote != null }
        model.saveCategoryNote("fill up on fridays")
        await { model.state.value.categoryNote == "fill up on fridays" }

        model.openTemplateKinds()
        assertEquals(ShellRoute.TemplateKinds("c-gas"), model.state.value.route)
        model.pickTemplateKind(TemplateKind.MONTHLY)
        val route = model.state.value.route as ShellRoute.TemplateEditor
        assertEquals(Template.Simple(BigDecimal.ZERO, null, 0), model.state.value.templateDraftStart(route))
        model.saveTemplate(Template.Simple(BigDecimal("120"), null, 0))
        assertEquals(ShellRoute.CategoryBudget("c-gas"), model.state.value.route)
        assertEquals("fill up on fridays\n#template 120", model.state.value.categoryNote)

        model.openTemplateKinds()
        model.pickTemplateKind(TemplateKind.AVERAGE)
        assertEquals(1, (model.state.value.templateDraftStart(model.state.value.route as ShellRoute.TemplateEditor)).priority)
        model.saveTemplate(Template.Average(3, 1))
        assertEquals("fill up on fridays\n#template 120\n#template-1 average 3 months", model.state.value.categoryNote)

        model.moveTemplate(1, 0)
        assertEquals("fill up on fridays\n#template average 3 months\n#template-1 120", model.state.value.categoryNote)

        model.openTemplate(1)
        assertEquals(ShellRoute.TemplateEditor("c-gas", 1, TemplateKind.MONTHLY), model.state.value.route)
        model.removeTemplate()
        assertEquals(ShellRoute.CategoryBudget("c-gas"), model.state.value.route)
        assertEquals("fill up on fridays\n#template average 3 months", model.state.value.categoryNote)

        model.refreshTemplateAmounts()
        await { model.state.value.categoryTemplateAmounts.size == 1 }
        await { library.readCategoryNote(model.state.value.settings.openBudgetId!!, "c-gas") == model.state.value.categoryNote }
    }

    private fun MonthShell.category(id: String): CategoryRow? = groups.flatMap { it.categories }.firstOrNull { it.id == id }
}
