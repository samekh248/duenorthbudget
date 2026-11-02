package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import app.duenorth.budget.ParityTestSupport.await
import app.duenorth.budget.ParityTestSupport.household
import app.duenorth.budget.core.ActualMonthMath
import app.duenorth.budget.core.TemplateCopy
import app.duenorth.budget.core.TemplateError
import app.duenorth.budget.core.TemplateResult
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tapping a positive unassigned figure: Actual's apply and overwrite, each saying what it would budget, then run. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class DistributeMenuTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun optionsReadWhatAPreviewRunWouldDo() {
        val order = listOf("b" to "Bills", "x" to "Other", "a" to "Gas")
        val filled = distributeOption(false, TemplateResult.Applied(3), mapOf("a" to (0L to 12_300L), "b" to (5_000L to 8_000L)), order)
        assertEquals(DistributeOption(false, 2, 15_300L, names = listOf("Bills", "Gas")), filled)
        assertEquals(DistributeCopy.NOTHING, distributeOption(true, TemplateResult.Applied(2), emptyMap()).reason)
        assertEquals(DistributeCopy.NO_TEMPLATES, distributeOption(false, TemplateResult.Applied(0), emptyMap()).reason)
        val bad = distributeOption(false, TemplateResult.Invalid(listOf(TemplateError("Car", "#template x"))), emptyMap())
        assertFalse(bad.enabled)
        assertEquals("1 template line does not read", bad.reason)
        assertEquals("Rent, Power, Gas +2 more", DistributeCopy.names(listOf("Rent", "Power", "Gas", "Food", "Fun")))
    }

    @Test
    fun positiveUnassignedOpensTheMenuAndAPickRunsIt() {
        var opened = 0
        val menu =
            DistributeMenu(
                TemplateSamples.october,
                listOf(
                    DistributeOption(false, 2, 15_300L, names = listOf("Rent", "Gas")),
                    DistributeOption(true, reason = DistributeCopy.NOTHING),
                ),
            )
        val shell = TemplateSamples.shell.copy(ahead = ActualMonthMath.FuturePlans(40_000, 202612))
        compose.setContent {
            MetroTheme {
                Column(Modifier.fillMaxSize()) {
                    HomePivot(
                        shell = shell,
                        loading = false,
                        openMonth = shell.month,
                        monthWindow = MonthWindow.around(shell.month),
                        initialSection = HomeSections.headers.indexOf(HomeSections.BUDGET),
                        onGesture = {},
                        showAhead = true,
                        distribute = DistributeActions(menu = if (opened > 0) menu else null, onOpen = { opened++ }),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        compose.onNodeWithTag("free-after-plans-figure").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun menuListsApplyAndOverwriteWithWhatEachBudgets() {
        var picked: Boolean? = null
        val menu =
            DistributeMenu(
                TemplateSamples.october,
                listOf(
                    DistributeOption(false, 2, 15_300L, names = listOf("Rent", "Gas")),
                    DistributeOption(true, reason = DistributeCopy.NOTHING),
                ),
            )
        val shell = TemplateSamples.shell.copy(ahead = ActualMonthMath.FuturePlans(40_000, 202612))
        compose.setContent {
            MetroTheme {
                BudgetSection(
                    shell,
                    onListGesture = {},
                    showAhead = true,
                    distribute = DistributeActions(menu = menu, onOpen = {}, onPick = { picked = it }),
                )
            }
        }
        compose.onNodeWithText(DistributeCopy.APPLY).assertExists()
        compose.onNodeWithText("\$153.00").assertExists()
        compose.onNodeWithText("Rent, Gas").assertExists()
        compose.onNodeWithText(DistributeCopy.NOTHING).assertExists()
        compose.onNodeWithTag("distribute-overwrite").assertIsNotEnabled()
        // An option that is off is the same size as one that is on.
        assertEquals(
            compose.onNodeWithTag("distribute-apply").getBoundsInRoot().height,
            compose.onNodeWithTag("distribute-overwrite").getBoundsInRoot().height,
        )
        compose.onNodeWithTag("distribute-apply").performClick()
        assertEquals(false, picked)
    }

    @Test
    fun negativeUnassignedIsNotTappable() {
        val shell = TemplateSamples.shell.copy(ahead = ActualMonthMath.FuturePlans(-6_500, 202612))
        compose.setContent {
            MetroTheme {
                BudgetSection(shell, onListGesture = {}, showAhead = true, distribute = DistributeActions(onOpen = {}))
            }
        }
        compose.onNodeWithTag("free-after-plans-figure").assertHasNoClickAction()
    }

    @Test
    fun templatesOffSaysSoOnBothLines() {
        val (model, _) = household("distribute-off")
        model.templates.openDistribute()
        val menu = model.state.value.templates.distribute
        assertNotNull(menu)
        assertTrue(menu!!.options.all { it.reason == TemplateCopy.OFF })
    }

    @Test
    fun pickingAnOptionBudgetsWhatItsLineSaid() {
        val (model, library) = household("distribute-run")
        model.settingsUi.setExperimental(true)
        model.settingsUi.setGoalTemplates(true)
        await { model.state.value.formatPrefs.templatesOn }
        val budgetId = model.state.value.settings.openBudgetId!!
        library.saveCategoryNote(budgetId, "c-gas", "#template 123")
        await { library.templatePlan(budgetId, model.state.value.openMonth) != null }
        val before =
            model.state.value.shell!!
                .headerMinor

        model.templates.openDistribute()
        await { model.state.value.templates.distribute != null }
        val overwrite =
            model.state.value.templates.distribute!!
                .options
                .first { it.overwrite }
        assertTrue(overwrite.reason, overwrite.enabled)

        model.templates.distribute(overwrite = true)
        assertNull(model.state.value.templates.distribute)
        await {
            model.state.value.shell!!
                .headerMinor == before - overwrite.amountMinor
        }
        assertEquals(
            12_300L,
            model.state.value.shell!!
                .groups
                .flatMap { it.categories }
                .first { it.id == "c-gas" }
                .budgetedMinor,
        )
    }
}
