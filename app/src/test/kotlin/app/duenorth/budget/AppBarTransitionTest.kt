package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroStyle
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Moving between screens trades the bar's buttons: the old screen's ease out, the new one's ease in. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class AppBarTransitionTest {
    @get:Rule
    val compose = createHostComposeRule()

    private var budgetEdits = 0

    @Test
    fun leavingButtonsEaseOutAndArrivingOnesEaseIn() {
        var actions by mutableStateOf(budget)
        compose.mainClock.autoAdvance = false
        setBar(dark = false, style = MetroStyle.Metro, animations = true) { actions }
        compose.mainClock.advanceTimeByFrame()
        actions = accounts
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(60)
        // Mid-way, the budget's own button is still drawn on its way out, beside the accounts' arriving ones.
        compose.onNodeWithTag("app-bar-edit-budget").assertExists()
        compose.onNodeWithTag("app-bar-bank-sync").assertExists()
        // A tap on a leaving button runs nothing: its screen is already gone.
        compose.onNodeWithTag("app-bar-edit-budget").performClick()
        assertEquals(0, budgetEdits)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("app-bar-edit-budget").assertDoesNotExist()
        // The button both screens share is never doubled.
        compose.onAllNodesWithTag("app-bar-add").assertCountEquals(1)
        compose.onNodeWithTag("app-bar-schedules").assertExists()
    }

    @Test
    fun withAnimationsOffTheBarSwapsAtOnce() {
        var actions by mutableStateOf(budget)
        compose.mainClock.autoAdvance = false
        setBar(dark = false, style = MetroStyle.Metro, animations = false) { actions }
        compose.mainClock.advanceTimeByFrame()
        actions = accounts
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("app-bar-edit-budget").assertDoesNotExist()
        compose.onNodeWithTag("app-bar-bank-sync").assertExists()
    }

    /**
     * Frames of the budget's bar turning into the accounts' and the register's and back, for the review GIF. Written
     * under build/ only when DUENORTH_APP_BAR_FRAMES is set, so the suite itself records nothing.
     */
    @Test
    fun framesMetroLight() = frames(MetroStyle.Metro, dark = false)

    @Test
    fun framesMetroDark() = frames(MetroStyle.Metro, dark = true)

    @Test
    fun framesMobile10Light() = frames(MetroStyle.Mobile10, dark = false)

    @Test
    fun framesMobile10Dark() = frames(MetroStyle.Mobile10, dark = true)

    @Test
    fun framesTonalLight() = frames(MetroStyle.Tonal, dark = false)

    @Test
    fun framesTonalDark() = frames(MetroStyle.Tonal, dark = true)

    private fun frames(
        style: MetroStyle,
        dark: Boolean,
    ) {
        if (System.getenv("DUENORTH_APP_BAR_FRAMES") == null) return
        var actions by mutableStateOf(budget)
        compose.mainClock.autoAdvance = false
        setBar(dark, style, animations = true) { actions }
        compose.mainClock.advanceTimeBy(500)
        val name = "${style.name.lowercase()}_${if (dark) "dark" else "light"}"
        var frame = 0
        for (next in listOf(accounts, register, budget)) {
            repeat(HOLD) { capture(name, frame++) }
            actions = next
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
            repeat(FRAMES) {
                capture(name, frame++)
                compose.mainClock.advanceTimeBy(FRAME_MS)
            }
        }
        repeat(HOLD) { capture(name, frame++) }
    }

    private fun capture(
        name: String,
        frame: Int,
    ) {
        compose.onRoot().captureRoboImage("build/app-bar-frames/$name/%03d.png".format(frame))
    }

    private fun setBar(
        dark: Boolean,
        style: MetroStyle,
        animations: Boolean,
        actions: () -> List<ScreenAction>,
    ) {
        compose.setContent {
            MetroTheme(darkTheme = dark, style = style, animationsEnabled = animations) {
                Column(Modifier.width(390.dp)) {
                    Box(Modifier.fillMaxWidth().height(24.dp).background(Metro.colors.background))
                    MetroAppBar(actions(), false, {}, windowInsets = WindowInsets(0))
                }
            }
        }
    }

    private val budget =
        listOf(
            ScreenAction("add", AppGlyph.Add, "add") {},
            ScreenAction("edit-budget", AppGlyph.Edit, "edit budget", caption = "budget") { budgetEdits += 1 },
            ScreenAction("settings", AppGlyph.Settings, "settings", primary = false) {},
        )

    private val accounts =
        listOf(
            ScreenAction("add", AppGlyph.Add, "add") {},
            ScreenAction("bank-sync", AppGlyph.Sync, "sync") {},
            ScreenAction("schedules", AppGlyph.Calendar, "schedules") {},
            ScreenAction("settings", AppGlyph.Settings, "settings", primary = false) {},
        )

    private val register =
        listOf(
            ScreenAction("add", AppGlyph.Add, "add") {},
            ScreenAction("transfer", AppGlyph.Transfer, "transfer") {},
            ScreenAction("reconcile", AppGlyph.Reconcile, "reconcile") {},
            ScreenAction("import", AppGlyph.Import, "import") {},
        )

    private companion object {
        const val HOLD = 4
        const val FRAMES = 14
        const val FRAME_MS = 25L
    }
}
