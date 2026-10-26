package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyboardClearanceContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    // The keyboard covers the 50dp bar first, so a 200dp keyboard takes 150dp from the page, and the
    // box at the foot of a long page scrolls up to sit above it.
    @Test
    fun theBoxBeingTypedInScrollsAboveTheKeyboard() {
        var keyboard by mutableStateOf(0.dp)
        compose.setContent {
            val bar = with(LocalDensity.current) { 50.dp.roundToPx() }
            MetroTheme {
                Column(Modifier.height(400.dp)) {
                    Box(Modifier.weight(1f).aboveKeyboard(WindowInsets(bottom = keyboard)) { bar }) {
                        Column(Modifier.fillMaxSize().testTag("page").verticalScroll(rememberScrollState())) {
                            Spacer(Modifier.height(280.dp))
                            MetroField("", {}, "note", Modifier.testTag("note"))
                        }
                    }
                    Spacer(Modifier.height(50.dp))
                }
            }
        }
        val box = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("note")))
        box.performClick()
        compose.waitForIdle()
        assertEquals(350.dp, compose.onNodeWithTag("page").getBoundsInRoot().height)

        keyboard = 200.dp
        compose.waitForIdle()
        val page = compose.onNodeWithTag("page").getBoundsInRoot()
        assertEquals(200.dp, page.height)
        assertTrue(box.getBoundsInRoot().bottom <= page.bottom)
    }
}
