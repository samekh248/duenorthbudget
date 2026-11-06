package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import app.duenorth.budget.core.BankSyncFailure
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** The budget pulled down: the sync panel open over it, with and without bank sync, and while a sync runs. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h780dp-xxhdpi")
class PullSyncScreenshotTest {
    @Before
    fun pinCalendarMonth() {
        CalendarMonth.pinned = YearMonth.of(2026, 10)
    }

    @After
    fun unpinCalendarMonth() {
        CalendarMonth.pinned = null
    }

    @get:Rule
    val compose = createHostComposeRule()

    private val synced = PullSyncUi(enabled = true, status = "synced 3:42 pm")

    @Test
    fun openLight() = snap("pull_sync_open_light", dark = false, synced)

    @Test
    fun openDark() = snap("pull_sync_open_dark", dark = true, synced)

    @Test
    fun banksLight() = snap("pull_sync_banks_light", dark = false, synced.copy(bankSync = true))

    @Test
    fun banksDark() = snap("pull_sync_banks_dark", dark = true, synced.copy(bankSync = true))

    // After sync now: the panel has folded to its grip, which says syncing until the sync is done.
    @Test
    fun syncingLight() = snap("pull_sync_syncing_light", dark = false, synced, tapSync = true)

    @Test
    fun syncingDark() = snap("pull_sync_syncing_dark", dark = true, synced, tapSync = true)

    @Test
    fun closedLight() = snap("pull_sync_closed_light", dark = false, synced, pull = false)

    private val failed =
        synced.copy(
            bankSync = true,
            failure = BankSyncUi(running = false, failures = listOf(BankSyncFailure("Credit card", "fetch did not finish"))),
        )

    // A failed bank sync: the quiet strip at the top of the page, then the panel it opens.
    @Test
    fun failedStripLight() = snap("pull_sync_failed_strip_light", dark = false, failed, pull = false)

    @Test
    fun failedStripDark() = snap("pull_sync_failed_strip_dark", dark = true, failed, pull = false)

    @Test
    fun failedOpenLight() = snap("pull_sync_failed_open_light", dark = false, failed)

    @Test
    fun failedOpenDark() = snap("pull_sync_failed_open_dark", dark = true, failed)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun snap(
        name: String,
        dark: Boolean,
        ui: PullSyncUi,
        pull: Boolean = true,
        tapSync: Boolean = false,
    ) {
        var shown by mutableStateOf(ui)
        val shell = sample()
        val actions =
            ScreenBar
                .specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE, homeSection = ScreenBar.HOME_BUDGET)
                .map { it.action() }
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.fillMaxSize()) {
                    PullSyncPanel(
                        shown,
                        onSync = { shown = shown.copy(syncing = true) },
                        onBankSync = {},
                        modifier = Modifier.weight(1f),
                    ) { inner ->
                        HomePivot(
                            shell = shell,
                            loading = false,
                            openMonth = shell.month,
                            monthWindow = MonthWindow.around(shell.month),
                            initialSection = HomeSections.headers.indexOf(HomeSections.BUDGET),
                            onGesture = {},
                            modifier = inner,
                        )
                    }
                    MetroAppBar(actions, false, {}, windowInsets = WindowInsets(0))
                }
            }
        }
        compose.waitForIdle()
        if (pull) {
            compose.onNodeWithTag("budget-list").performTouchInput {
                swipeDown(startY = top + 10f, endY = bottom - 10f, durationMillis = 400)
            }
            compose.waitForIdle()
        }
        if (tapSync) {
            compose.onNodeWithTag("pull-sync-now").performClick()
            compose.waitForIdle()
        }
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }

    private fun sample(): MonthShell =
        MonthShell(
            budgetId = "home",
            budgetName = "Home",
            mode = BudgetMode.ENVELOPE,
            currency = Currencies.byCode("USD")!!,
            month = YearMonth.of(2026, 10),
            headerLabel = "to budget",
            headerMinor = 42_000,
            groups =
                listOf(
                    GroupRow(
                        "g-bills",
                        "Bills",
                        124_000,
                        listOf(
                            CategoryRow("c-rent", "Rent", 110_000, 0, 110_000, false),
                            CategoryRow("c-power", "Power", 14_000, 0, 14_000, false),
                        ),
                    ),
                    GroupRow(
                        "g-food",
                        "Food",
                        -3_200,
                        listOf(
                            CategoryRow("c-groceries", "Groceries", 20_000, 28_000, -8_000, false),
                            CategoryRow("c-dining", "Dining", 8_000, 3_200, 4_800, false),
                        ),
                    ),
                ),
            accounts = emptyList(),
            inbox = emptyList(),
        )
}
