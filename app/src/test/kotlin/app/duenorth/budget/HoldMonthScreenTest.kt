package app.duenorth.budget

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The hold form asks how much more to hold, starting at everything to budget, and shows the totals after. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HoldMonthScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val saved = mutableListOf<String>()
    private var released = 0

    private fun show(held: Long = 20_000) {
        compose.setContent {
            MetroTheme(darkTheme = false) {
                HoldMonthScreen(TemplateSamples.shell.copy(bufferedMinor = held), null, { saved += it }, { released++ })
            }
        }
    }

    private fun field() = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("hold-amount")))

    @Test
    fun startsWithEverythingToBudget() {
        show()
        field().assertTextEquals("552.00", includeEditableText = true)
        // Holding all of it leaves nothing to budget, on top of the $200 already held.
        compose.onNodeWithText("$0.00").assertExists()
        compose.onNodeWithText("$752.00").assertExists()
        compose.onNodeWithText("$200.00 held now").assertExists()
    }

    @Test
    fun savesOnlyTheExtra() {
        show()
        field().performTextReplacement("100")
        compose.onNodeWithText("$452.00").assertExists()
        compose.onNodeWithText("$300.00").assertExists()
        compose.onNodeWithText(app.duenorth.budget.core.EnvelopeCopy.HOLD).performClick()
        assertEquals(listOf("100"), saved)
    }

    @Test
    fun releaseLinkShowsOnlyWhileSomethingIsHeld() {
        show(held = 0)
        compose.onNodeWithTag("hold-release").assertDoesNotExist()
        compose.onNodeWithTag("hold-held-now").assertDoesNotExist()
    }

    @Test
    fun releaseLinkReleasesTheHold() {
        show()
        compose.onNodeWithText("release $200.00 held").performClick()
        assertTrue(released == 1)
        assertTrue(saved.isEmpty())
    }
}
