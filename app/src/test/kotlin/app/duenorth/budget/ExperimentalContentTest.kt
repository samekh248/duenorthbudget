package app.duenorth.budget

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

/** Experimental features: asked once per budget on opening, and switched on their own page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class ExperimentalContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun openingABudgetAsksOnceAndRemembersPerBudget() {
        val (model, library) = household("experimental-offer")
        val householdId = model.state.value.settings.openBudgetId!!

        model.createFromSettings("Trip", "USD")
        await { model.state.value.route == ShellRoute.ExperimentalOffer }
        val tripId = model.state.value.settings.openBudgetId!!
        model.declineExperimentalOffer()
        assertEquals(ShellRoute.Home, model.state.value.route)
        await { library.readFormatPrefs(tripId).experimentalAsked }
        assertFalse(library.readFormatPrefs(tripId).experimental)

        // The household budget has its own choice, so switching to it asks again.
        model.requestSwitch(householdId)
        model.confirmSwitch()
        await { model.state.value.route == ShellRoute.ExperimentalOffer }
        model.acceptExperimentalOffer()
        assertEquals(ShellRoute.Home, model.state.value.route)
        await { library.readFormatPrefs(householdId).templatesOn }

        // Back on the trip budget, the earlier answer stands.
        model.requestSwitch(tripId)
        model.confirmSwitch()
        await { model.state.value.settings.openBudgetId == tripId && !model.state.value.formatPrefs.templatesOn }
        assertEquals(ShellRoute.Home, model.state.value.route)
    }

    @Test
    fun settingsOpensTheExperimentalPageAndBackReturnsToAdvanced() {
        val (model, _) = household("experimental-page")
        model.showSettings()
        model.showExperimental()
        assertEquals(ShellRoute.Experimental, model.state.value.route)
        assertEquals("SETTINGS", SectionTitle.of(model.state.value))
        model.setExperimental(true)
        model.setExperimentalFeature(ExperimentalFeature.TEMPLATES, true)
        assertTrue(model.state.value.formatPrefs.templatesOn)
        assertTrue(model.back())
        assertEquals(ShellRoute.Settings, model.state.value.route)
        assertEquals(SettingsPage.ADVANCED, model.state.value.settingsPage)
    }

    @Test
    fun featuresWaitForTheMasterSwitch() {
        compose.setContent {
            MetroTheme { ExperimentalScreen("Home", FormatPrefs(), {}, { _, _ -> }) }
        }
        compose.onNodeWithTag("experimental-templates-switch").assertDoesNotExist()
        compose.onNodeWithText(ExperimentalCopy.NEEDS_MASTER).assertExists()
    }

    @Test
    fun flippingAFeatureReportsIt() {
        var flipped: Pair<ExperimentalFeature, Boolean>? = null
        compose.setContent {
            MetroTheme {
                ExperimentalScreen("Home", FormatPrefs(experimental = true), {}, { feature, on -> flipped = feature to on })
            }
        }
        compose.onNodeWithTag("experimental-templates-switch-on", useUnmergedTree = true).performClick()
        assertEquals(ExperimentalFeature.TEMPLATES to true, flipped)
    }

    @Test
    fun advancedSummarySaysWhatIsOn() {
        assertEquals("off", ExperimentalCopy.summary(FormatPrefs()))
        assertEquals("on · no features picked", ExperimentalCopy.summary(FormatPrefs(experimental = true)))
        assertEquals("on · budget templates", ExperimentalCopy.summary(FormatPrefs(experimental = true, goalTemplates = true)))
    }
}
