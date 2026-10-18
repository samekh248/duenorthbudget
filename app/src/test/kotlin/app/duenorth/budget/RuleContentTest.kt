package app.duenorth.budget

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.duenorth.budget.ParityTestSupport.await
import app.duenorth.budget.ParityTestSupport.household
import app.duenorth.budget.core.AccountRow
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.RuleDraft
import app.duenorth.budget.core.RuleEditorCopy
import app.duenorth.budget.core.RuleNames
import app.duenorth.budget.core.RuleSaveResult
import app.duenorth.budget.core.RuleSummary
import app.duenorth.budget.core.RuleValues
import app.duenorth.budget.core.ScheduleDraft
import app.duenorth.budget.core.ScheduleRepeat
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 010 T050: schedule editor, rule list, rule editor, and the learning switch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class RuleContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    private val names = RuleNames(payees = mapOf("p1" to "Cafe"), accounts = mapOf("a1" to "Checking"), categories = mapOf("c1" to "Food"))

    private fun field(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag))).performScrollTo()

    @Test
    fun barEntries() {
        val home = ScreenBar.specsFor(ShellRoute.Home, true, BudgetMode.ENVELOPE).map { it.id }
        assertTrue("add-schedule" !in home && "rules" in home)
        assertTrue("add-schedule" in ScreenBar.specsFor(ShellRoute.Schedules, true, BudgetMode.ENVELOPE).map { it.id })
        assertEquals(listOf("add-rule"), ScreenBar.specsFor(ShellRoute.Rules, true, BudgetMode.ENVELOPE).map { it.id })
        assertEquals(listOf("save", "cancel"), ScreenBar.specsFor(ShellRoute.RuleEditor(null), true, BudgetMode.ENVELOPE).map { it.id })
        assertTrue("delete-rule" in ScreenBar.specsFor(ShellRoute.RuleEditor("r1"), true, BudgetMode.ENVELOPE).map { it.id })
        assertTrue("delete-schedule" in ScreenBar.specsFor(ShellRoute.ScheduleEditor("s1"), true, BudgetMode.ENVELOPE).map { it.id })
    }

    @Test
    fun ruleListMarksKeptAndStage() {
        var opened: String? = null
        val rules =
            listOf(
                RuleSummary("r1", "pre", listOf("imported_description contains CORNER"), listOf("payee set Corner Shop"), true, true),
                RuleSummary("r2", null, listOf("payee is Cafe"), listOf("category set Food"), false, false),
            )
        compose.setContent { MetroTheme { RulesScreen(rules, { opened = it }) } }
        compose.onNodeWithTag("rule-notes-r1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("rule-line-r2").performClick()
        assertEquals("r2", opened)
    }

    @Test
    fun ruleEditorAddsAPartByNameAndKeepsTheRest() {
        var saved: RuleDraft? = null
        val kept = RuleValues.part("notes", "contains", "ref", names, 2, action = false)!!.copy(kept = true)
        val draft = RuleDraft("r1", null, "and", listOf(kept), emptyList())
        compose.setContent { MetroTheme { RuleEditorScreen(draft, names, 2, null, { saved = it }) } }
        compose.onNodeWithTag("rule-condition-0-kept", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("rule-condition-0-remove").assertDoesNotExist()
        compose.onNodeWithTag("rule-add-action-field-category").performScrollTo().performClick()
        field("rule-add-action-value").performTextInput("food")
        compose.onNodeWithTag("rule-add-action-button").performScrollTo().performClick()
        compose.onNodeWithTag("rule-stage-post").performClick()
        compose.onNodeWithTag("rule-save").performScrollTo().performClick()
        val result = saved!!
        assertEquals("post", result.stage)
        assertEquals(1, result.conditions.size)
        assertEquals("category", result.actions.single().field)
        assertEquals("category set Food", RuleValues.describe(result.actions.single(), names, 2))
    }

    @Test
    fun scheduleEditorBuildsADraft() {
        var form: ScheduleForm? = null
        val draft = ScheduleDraft(accountId = "a1", startIso = "2026-10-07")
        compose.setContent {
            MetroTheme {
                ScheduleEditorScreen(
                    draft = draft,
                    accounts = listOf(AccountRow("a1", "Checking", false, false, 0), AccountRow("a2", "Savings", false, false, 0)),
                    categories = listOf(CategoryChoice("c1", "Food", "Living", false)),
                    decimals = 2,
                    error = null,
                    onSave = { form = it },
                )
            }
        }
        compose.onNodeWithTag("schedule-kept").assertDoesNotExist()
        field("schedule-name").performTextInput("Internet")
        field("schedule-payee").performTextInput("Cafe")
        field("schedule-amount").performTextInput("-60")
        compose.onNodeWithTag("schedule-account-a2").performScrollTo().performClick()
        compose.onNodeWithTag("schedule-repeat-weekly").performScrollTo().performClick()
        compose.onNodeWithTag("schedule-end-after_n_occurrences").performScrollTo().performClick()
        compose.onNodeWithTag("schedule-posts").performScrollTo().performClick()
        compose.onNodeWithTag("schedule-save").performScrollTo().performClick()
        val built = form!!.toDraft(draft, 2)!!
        assertEquals("Internet", built.name)
        assertEquals("a2", built.accountId)
        assertEquals(-6000L, built.amountMinor)
        assertEquals(ScheduleRepeat.WEEKLY, built.repeat)
        assertTrue(built.postsTransaction)
        assertEquals(null, form!!.copy(amount = "sixty").toDraft(draft, 2))
    }

    @Test
    fun scheduleAndRuleThroughTheModel() {
        val (model, library) = household("rules")
        val budgetId = model.state.value.settings.openBudgetId!!
        model.openNewSchedule()
        await {
            model.state.value.scheduleDraft != null &&
                model.state.value.categories
                    .isNotEmpty()
        }
        val base = model.state.value.scheduleDraft!!
        model.submitSchedule(ScheduleForm.from(base, 2).copy(name = "Gym", payee = "Gym", amount = "-25", repeat = ScheduleRepeat.MONTHLY))
        await {
            model.state.value.route == ShellRoute.Home &&
                model.state.value.schedules
                    .any { it.payee == "Gym" }
        }
        val gym =
            model.state.value.schedules
                .first { it.payee == "Gym" }
        model.openSchedules()
        assertEquals(ShellRoute.Schedules, model.state.value.route)
        model.toggleSchedulesCalendar()
        assertTrue(model.state.value.schedulesCalendar)
        model.openScheduleDetails(gym.id)
        await {
            model.state.value.scheduleDetails
                ?.summary
                ?.id == gym.id
        }
        model.editOpenSchedule()
        await {
            model.state.value.scheduleDraft
                ?.id == gym.id
        }
        assertEquals(
            -2500L,
            model.state.value.scheduleDraft!!
                .amountMinor,
        )
        model.back()
        assertEquals(ShellRoute.ScheduleDetails(gym.id), model.state.value.route)
        // Deleting from the editor leaves the schedule's page too, back to the list.
        model.editOpenSchedule()
        await {
            model.state.value.scheduleDraft
                ?.id == gym.id
        }
        model.deleteOpenSchedule()
        await {
            model.state.value.route == ShellRoute.Schedules &&
                model.state.value.schedules
                    .none { it.id == gym.id }
        }
        model.back()

        model.openRules()
        await {
            model.state.value.route == ShellRoute.Rules &&
                model.state.value.payees
                    .isNotEmpty()
        }
        val before = library.listRules(budgetId).size
        model.openNewRule()
        model.submitRule(RuleDraft(null, null, "and", emptyList(), emptyList()))
        await { model.state.value.editError != null }
        assertEquals(RuleEditorCopy.NEEDS_CONDITION, model.state.value.editError)
        val payee =
            model.state.value.payees
                .first()
        val category =
            model.state.value.categories
                .first { !it.income }
        val names = model.ruleNames()
        val draft =
            RuleDraft(
                null,
                null,
                "and",
                listOf(RuleValues.part("payee", "is", payee.name, names, 2, action = false)!!),
                listOf(RuleValues.part("category", "set", category.name, names, 2, action = true)!!),
            )
        model.submitRule(draft)
        await { model.state.value.route == ShellRoute.Rules && model.state.value.rulesList.size == before + 1 }
        assertEquals(RuleEditorCopy.SAVE_RULE, model.state.value.undoLabel)
        val ruleId =
            model.state.value.rulesList
                .first { r -> r.conditions.any { payee.name in it } }
                .id
        model.openRule(ruleId)
        await {
            model.state.value.ruleDraft
                ?.id == ruleId
        }
        model.deleteOpenRule()
        await { library.listRules(budgetId).size == before }
        assertTrue(library.deleteRule(budgetId, ruleId) is RuleSaveResult.Rejected)
    }

    @Test
    fun learningSwitchFollowsThePreference() {
        val (model, library) = household("learning")
        val budgetId = model.state.value.settings.openBudgetId!!
        assertTrue(model.state.value.formatPrefs.learnCategories)
        model.setLearning(false)
        await { !library.learningEnabled(budgetId) }
        assertEquals(false, model.state.value.formatPrefs.learnCategories)
    }
}
