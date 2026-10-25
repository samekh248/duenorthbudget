package app.duenorth.budget

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.duenorth.budget.ParityTestSupport.await
import app.duenorth.budget.ParityTestSupport.household
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Experimental features: asked once per budget on opening, and switched in their own settings item. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class ExperimentalContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun openingABudgetAsksOnceAndRemembersPerBudget() {
        val (model, library) = household("experimental-offer")
        val householdId = model.state.value.settings.openBudgetId!!
        // Let the launch's own loads finish, so none lands after the new budget opens.
        repeat(40) {
            ParityTestSupport.drain()
            Thread.sleep(25)
        }

        model.createFromSettings("Trip", "USD")
        await { model.state.value.route is ShellRoute.ExperimentalOffer }
        val tripId = (model.state.value.route as ShellRoute.ExperimentalOffer).budgetId
        assertTrue(tripId != householdId)
        model.declineExperimentalOffer()
        assertEquals(ShellRoute.Home, model.state.value.route)
        await { library.readFormatPrefs(tripId).experimentalAsked }
        assertFalse(library.readFormatPrefs(tripId).experimental)

        // The household budget has its own choice, so switching to it asks again.
        await { model.state.value.settings.openBudgetId == tripId }
        model.requestSwitch(householdId)
        model.confirmSwitch()
        await { model.state.value.route is ShellRoute.ExperimentalOffer }
        model.acceptExperimentalOffer()
        assertEquals(ShellRoute.Home, model.state.value.route)
        await { library.readFormatPrefs(householdId).templatesOn }

        // Back on the trip budget, the earlier answer stands.
        await { model.state.value.settings.openBudgetId == householdId }
        model.requestSwitch(tripId)
        model.confirmSwitch()
        await { model.state.value.settings.openBudgetId == tripId && !model.state.value.formatPrefs.templatesOn }
        assertEquals(ShellRoute.Home, model.state.value.route)
    }

    @Test
    fun experimentalIsItsOwnSettingsItemWhileABudgetIsOpen() {
        assertTrue(SettingsPage.EXPERIMENTAL in SettingsPage.shown(budgetOpen = true))
        assertFalse(SettingsPage.EXPERIMENTAL in SettingsPage.shown(budgetOpen = false))
        val (model, _) = household("experimental-page")
        model.showSettings()
        model.selectSettingsPage(SettingsPage.EXPERIMENTAL)
        model.setExperimental(true)
        model.setExperimentalFeature(ExperimentalFeature.TEMPLATES, true)
        assertTrue(model.state.value.formatPrefs.templatesOn)
        assertEquals(ShellRoute.Settings, model.state.value.route)
    }

    @Test
    fun freeAfterPlansStaysOnThisPhone() {
        val (model, library) = household("experimental-free-after-plans")
        val budgetId = model.state.value.settings.openBudgetId!!
        model.setExperimental(true)
        assertFalse(model.state.value.freeAfterPlansOn)
        model.setExperimentalFeature(ExperimentalFeature.FREE_AFTER_PLANS, true)
        assertTrue(model.state.value.settings.freeAfterPlans)
        assertTrue(model.state.value.freeAfterPlansOn)
        // Nothing for it goes into the budget file, so nothing syncs.
        await { library.readFormatPrefs(budgetId).experimental }
        assertFalse(library.readFormatPrefs(budgetId).goalTemplates)
        model.setExperimental(false)
        await { !model.state.value.formatPrefs.experimental }
        assertFalse(model.state.value.freeAfterPlansOn)
    }

    @Test
    fun featuresWaitForTheMasterSwitch() {
        compose.setContent {
            MetroTheme { ExperimentalItem(FormatPrefs(), {}, { _, _ -> }) }
        }
        compose.onNodeWithTag("experimental-templates-switch").assertDoesNotExist()
        compose.onAllNodesWithText(ExperimentalCopy.NEEDS_MASTER).assertCountEquals(ExperimentalFeature.entries.size)
    }

    @Test
    fun flippingAFeatureReportsIt() {
        var flipped: Pair<ExperimentalFeature, Boolean>? = null
        compose.setContent {
            MetroTheme {
                ExperimentalItem(FormatPrefs(experimental = true), {}, { feature, on -> flipped = feature to on })
            }
        }
        compose.onNodeWithTag("experimental-templates-switch-on", useUnmergedTree = true).performClick()
        assertEquals(ExperimentalFeature.TEMPLATES to true, flipped)
    }
}
