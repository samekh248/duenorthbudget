package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The accounts bar's sync turns while its sync runs, as in due north tasks, and rests when it ends. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class AppBarSyncTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun syncButtonTurnsOnlyWhileSyncing() {
        var syncing by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MetroTheme(darkTheme = false) {
                Column(Modifier.width(390.dp)) {
                    MetroAppBar(accounts(syncing), false, {}, windowInsets = WindowInsets(0))
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("app-bar-bank-sync").assert(notSyncing)
        compose.onRoot().captureRoboImage("src/test/snapshots/app_bar_accounts_sync_light.png")
        syncing = true
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("app-bar-bank-sync").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "syncing"))
        // A quarter turn in: a still that shows the glyph mid-spin.
        compose.mainClock.advanceTimeBy(300)
        compose.onRoot().captureRoboImage("src/test/snapshots/app_bar_accounts_syncing_light.png")
        syncing = false
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("app-bar-bank-sync").assert(notSyncing)
    }

    private val notSyncing = SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)

    private fun accounts(syncing: Boolean) =
        listOf(
            ScreenAction("add", AppGlyph.Add, "add") {},
            ScreenAction("bank-sync", AppGlyph.Sync, AccountScreenCopy.SYNC, spinning = syncing) {},
            ScreenAction("schedules", AppGlyph.Calendar, "schedules") {},
            ScreenAction("add-account", AppGlyph.Add, "add account", primary = false) {},
        )
}
