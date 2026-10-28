package app.duenorth.budget

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The shared question page: its answers take the application bar in the app and hand it back when it closes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class ConfirmPageTest {
    @get:Rule
    val compose = createHostComposeRule()

    private val pageBar = listOf(ScreenAction("save", AppGlyph.Save, "save") {})

    @Test
    fun theTitleIsAlwaysAQuestion() {
        assertEquals("rebuild totals?", ConfirmCopy.question("rebuild totals"))
        assertEquals("delete Gym?", ConfirmCopy.question("delete Gym?"))
    }

    @Test
    fun inTheAppTheAnswersTakeTheBarAndGiveItBack() {
        val host = FormSaveHost()
        val asking = mutableStateOf(true)
        val answers = mutableListOf<String>()
        compose.setContent {
            MetroTheme {
                CompositionLocalProvider(LocalFormSaveHost provides host) {
                    // The page underneath has its own bar, as the categories page does.
                    RememberBarOverride(pageBar)
                    if (asking.value) {
                        ConfirmPage(
                            title = "rebuild totals",
                            body = "this changes the budget on this phone",
                            yes = "rebuild",
                            onYes = { answers += "yes" },
                            onKeep = { answers += "keep" },
                            tag = "confirm",
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("rebuild totals?").assertExists()
        assertEquals(0, compose.onAllNodes(hasTestTag("confirm-yes")).fetchSemanticsNodes().size)
        val bar = host.bar.orEmpty()
        assertEquals(listOf(CONFIRM_YES to "rebuild", CONFIRM_KEEP to ConfirmCopy.KEEP), bar.map { it.id to it.label })
        bar.forEach { it.run() }
        assertEquals(listOf("yes", "keep"), answers)
        asking.value = false
        compose.waitForIdle()
        assertEquals(pageBar, host.bar)
    }

    @Test
    fun withNoAppTheAnswersAreBodyButtons() {
        var kept = false
        compose.setContent {
            MetroTheme {
                ConfirmPage(title = "remove home", yes = "remove", onYes = {}, onKeep = { kept = true }, tag = "confirm")
            }
        }
        compose.onNodeWithTag("confirm-yes").assertExists()
        compose.onNodeWithTag("confirm-keep").performClick()
        assertEquals(true, kept)
    }

    @Test
    fun closingTheOnlyOverrideLeavesTheRoutesBar() {
        val host = FormSaveHost()
        val asking = mutableStateOf(true)
        compose.setContent {
            MetroTheme {
                CompositionLocalProvider(LocalFormSaveHost provides host) {
                    if (asking.value) ConfirmPage(title = "x", yes = "y", onYes = {}, onKeep = {}, tag = "confirm")
                }
            }
        }
        compose.waitForIdle()
        assertEquals(2, host.bar?.size)
        asking.value = false
        compose.waitForIdle()
        assertNull(host.bar)
    }
}
