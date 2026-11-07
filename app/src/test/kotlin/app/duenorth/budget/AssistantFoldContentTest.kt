package app.duenorth.budget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.FindingKind
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An assistant card folds open and closed over a few frames, and simply opens and closes with animations off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class AssistantFoldContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    private val kind = FindingKind.WRONG_ACCOUNT
    private val fold = "assistant-fold-${kind.name}"

    private fun show(animations: Boolean) {
        var expanded by mutableStateOf(emptySet<FindingKind>())
        compose.setContent {
            MetroTheme(darkTheme = false, animationsEnabled = animations) {
                AssistantSection(
                    report = sampleAssistant(),
                    currency = Currencies.byCode("USD")!!,
                    onListGesture = {},
                    onAct = { _, _ -> },
                    expanded = expanded,
                    onToggleExpanded = { expanded = if (it in expanded) expanded - it else expanded + it },
                )
            }
        }
        compose.waitForIdle()
    }

    /** One frame for the tap to land, one for the card to change. */
    private fun twoFrames() = repeat(2) { compose.mainClock.advanceTimeByFrame() }

    private fun foldHeight() = compose.onNodeWithTag(fold).getBoundsInRoot().let { it.bottom - it.top }

    @Test
    fun aCardFoldsOpenAndClosedOverItsRun() {
        show(animations = true)
        compose.onAllNodesWithTag(fold).assertCountEquals(0)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("assistant-kind-${kind.name}").performClick()
        compose.mainClock.advanceTimeBy(FoldMotion.HEIGHT_MS / 2L)
        val midway = foldHeight()
        compose.mainClock.advanceTimeBy(FoldMotion.FADE_IN_DELAY_MS + FoldMotion.FADE_IN_MS.toLong())
        val full = foldHeight()
        assertTrue("open mid-way: $midway of $full", midway > 0.dp && midway < full)

        compose.onNodeWithTag("assistant-kind-${kind.name}").performClick()
        compose.mainClock.advanceTimeBy(FoldMotion.HEIGHT_MS / 2L)
        val closing = foldHeight()
        assertTrue("closing mid-way: $closing of $full", closing > 0.dp && closing < full)
        compose.mainClock.advanceTimeBy(FoldMotion.HEIGHT_MS.toLong())
        compose.onAllNodesWithTag(fold).assertCountEquals(0)
    }

    @Test
    fun withAnimationsOffACardOpensAndClosesAtOnce() {
        show(animations = false)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("assistant-kind-${kind.name}").performClick()
        twoFrames()
        val open = foldHeight()
        compose.mainClock.advanceTimeBy(FoldMotion.FADE_IN_DELAY_MS + FoldMotion.FADE_IN_MS.toLong())
        assertEquals(open, foldHeight())
        assertTrue(open > 0.dp)
        compose.onNodeWithTag("assistant-kind-${kind.name}").performClick()
        twoFrames()
        compose.onAllNodesWithTag(fold).assertCountEquals(0)
    }
}
