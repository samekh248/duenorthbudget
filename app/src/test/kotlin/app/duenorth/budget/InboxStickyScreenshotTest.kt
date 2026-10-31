package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performSemanticsAction
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.theme.MetroStyle
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** The inbox queue scrolled: a day's date holds at the top of the list until the next date pushes it off. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h780dp-xxhdpi")
class InboxStickyScreenshotTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun stuckLight() = snap(dark = false, scrollDp = 120f, name = "inbox_sticky_stuck_light")

    @Test
    fun stuckDark() = snap(dark = true, scrollDp = 120f, name = "inbox_sticky_stuck_dark")

    @Test
    fun pushedLight() = snap(dark = false, scrollDp = 216f, name = "inbox_sticky_pushed_light")

    @Test
    fun pushedDark() = snap(dark = true, scrollDp = 216f, name = "inbox_sticky_pushed_dark")

    private fun snap(
        dark: Boolean,
        scrollDp: Float,
        name: String,
    ) {
        val shell =
            MonthShell(
                budgetId = "home",
                budgetName = "Home",
                mode = BudgetMode.ENVELOPE,
                currency = Currencies.byCode("USD")!!,
                month = YearMonth.of(2026, 10),
                headerLabel = "to budget",
                headerMinor = 0,
                groups = emptyList(),
                accounts = emptyList(),
                inbox = longInbox(),
            )
        compose.setContent {
            MetroTheme(darkTheme = dark, style = MetroStyle.Metro) {
                Column(Modifier.fillMaxSize()) {
                    HomePivot(
                        shell = shell,
                        loading = false,
                        openMonth = shell.month,
                        monthWindow = MonthWindow.around(shell.month),
                        initialSection = HomeSections.headers.indexOf(HomeSections.INBOX),
                        onGesture = {},
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        compose.waitForIdle()
        val px = scrollDp * compose.density.density
        compose
            .onAllNodes(hasScrollAction() and hasAnyDescendant(hasTestTag("inbox-row-d15-1")))
            .onLast()
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, px) }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }
}

private fun longInbox(): List<InboxRow> {
    val payees =
        listOf(
            "Corner Coffee" to -575L,
            "Shell" to -4_210L,
            "Market Basket" to -8_642L,
            "City Water" to -3_900L,
        )
    return listOf(20261018, 20261015, 20261012, 20261009).flatMapIndexed { d, date ->
        payees.mapIndexed { i, (payee, amount) ->
            InboxRow("d${date % 100}-$i", payee, amount - d * 101, date, "checking", "Checking", "", "c-$i", "Groceries")
        }
    }
}
