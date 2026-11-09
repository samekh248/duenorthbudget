package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import app.duenorth.budget.core.AssistantReport
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.CloseOutOutcome
import app.duenorth.budget.core.CloseOutReview
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * End-of-month close out: the assistant's card, the review with every category as a tile, a card for an overspent
 * category and one with money left, and the all square finish.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h780dp-xxhdpi")
class CloseOutScreenshotTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun cardLight() = assistant("close_out_card_light", dark = false, review = normal)

    @Test
    fun cardDark() = assistant("close_out_card_dark", dark = true, review = normal)

    @Test
    fun cardRoughLight() = assistant("close_out_card_rough_light", dark = false, review = rough)

    @Test
    fun cardClosedDark() = assistant("close_out_card_closed_dark", dark = true, review = settled, closed = true)

    @Test
    fun reviewLight() = page("close_out_review_light", dark = false, ui = CloseOutUi(review = normal))

    @Test
    fun reviewDark() = page("close_out_review_dark", dark = true, ui = CloseOutUi(review = normal))

    @Test
    fun reviewRoughLight() {
        page("close_out_review_rough_light", dark = false, ui = CloseOutUi(review = rough))
        compose.onNodeWithTag("close-out-headline").assertTextEquals("rough month.")
    }

    @Test
    fun coverLight() = page("close_out_cover_light", dark = false, ui = CloseOutUi(review = normal, step = "dining"))

    @Test
    fun coverDark() = page("close_out_cover_dark", dark = true, ui = CloseOutUi(review = normal, step = "dining"))

    @Test
    fun leftOverLight() = page("close_out_left_over_light", dark = false, ui = CloseOutUi(review = normal, step = "coffee"))

    @Test
    fun allSquareLight() {
        page("close_out_all_square_light", dark = false, ui = squareUi)
        compose.onNodeWithTag("close-out-headline").assertTextEquals("all square.")
    }

    @Test
    fun allSquareDark() = page("close_out_all_square_dark", dark = true, ui = squareUi)

    /** Frames of the motion for a preview: CLOSE_OUT_FRAMES=1 ./gradlew :app:testDebugUnitTest --tests '*CloseOutScreenshotTest.frames'. */
    @Test
    fun frames() {
        Assume.assumeTrue(System.getenv("CLOSE_OUT_FRAMES") != null)
        val clock = androidx.compose.runtime.mutableIntStateOf(0)
        var ui by androidx.compose.runtime.mutableStateOf(CloseOutUi(review = normal))
        compose.setContent {
            MetroTheme(darkTheme = false) {
                CloseOutScreen(ui, shell.currency, {}, {}, {}, {}, {}, {}, {}, {}, {}, Modifier.fillMaxSize(), clockMs = clock.intValue)
            }
        }
        var frame = 0

        fun run(until: Int) {
            for (t in 0..until step 67) {
                clock.intValue = t
                compose.waitForIdle()
                captureScreenRoboImage("build/close-out-frames/%03d.png".format(frame++))
            }
        }
        run(3600)
        // A fix card in between, as in the app, so the review comes back fresh and flips what changed.
        ui = CloseOutUi(review = normal, step = "dining")
        compose.waitForIdle()
        repeat(12) { captureScreenRoboImage("build/close-out-frames/%03d.png".format(frame++)) }
        ui = squareUi
        compose.waitForIdle()
        run(4200)
    }

    private fun assistant(
        name: String,
        dark: Boolean,
        review: CloseOutReview,
        closed: Boolean = false,
    ) {
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.fillMaxSize()) {
                    HomePivot(
                        shell = shell,
                        loading = false,
                        openMonth = shell.month,
                        monthWindow = MonthWindow.around(shell.month),
                        initialSection = ScreenBar.HOME_ASSISTANT,
                        onGesture = {},
                        modifier = Modifier.weight(1f),
                        assistant = AssistantUi(report = sampleAssistant()),
                        closeOut = CloseOutCard(review, closed, today = LocalDate.of(2026, 10, 29)),
                    )
                    MetroAppBar(emptyList(), false, {}, windowInsets = WindowInsets(0))
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }

    private fun page(
        name: String,
        dark: Boolean,
        ui: CloseOutUi,
    ) {
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                SectionTitled("ASSISTANT", Modifier.fillMaxSize()) {
                    CloseOutScreen(ui, shell.currency, {}, {}, {}, {}, {}, {}, {}, {}, {}, Modifier.fillMaxSize(), clockMs = 10_000)
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }

    private val october = YearMonth.of(2026, 10)

    private fun category(
        id: String,
        name: String,
        budgeted: Long,
        available: Long,
    ) = CategoryRow(id, name, budgeted, available - budgeted, available, false)

    private val categories =
        listOf(
            category("rent", "Rent", 120_000, 0),
            category("electric", "Electric", 6_250, -5_750),
            category("water", "Water", 7_500, 2_600),
            category("internet", "Internet", 8_750, 2_950),
            category("phone", "Phone", 10_000, 0),
            category("groceries", "Groceries", 11_250, 8_150),
            category("dining", "Dining", 12_500, -9_275),
            category("coffee", "Coffee", 13_750, 8_850),
            category("household", "Household", 15_000, 0),
            category("fuel", "Fuel", 16_250, -8_600),
            category("movies", "Movies", 5_000, 0),
            category("books", "Books", 4_000, 0),
            category("games", "Games", 3_000, 1_200),
            category("travel", "Travel", 20_000, 0),
        )

    private val shell =
        MonthShell(
            budgetId = "home",
            budgetName = "Home",
            mode = BudgetMode.ENVELOPE,
            currency = Currencies.byCode("USD")!!,
            month = october,
            headerLabel = "to budget",
            headerMinor = 12_000,
            groups = listOf(GroupRow("g", "Everything", 0, categories)),
            accounts = emptyList(),
            inbox = emptyList(),
        )

    private val normal = CloseOutReview.of(shell, emptySet())

    private val rough =
        CloseOutReview.of(
            shell.copy(
                groups =
                    listOf(
                        GroupRow(
                            "g",
                            "Everything",
                            0,
                            categories.map {
                                when (it.id) {
                                    "phone" -> it.copy(availableMinor = -1_200)
                                    "household" -> it.copy(availableMinor = -4_120)
                                    "movies" -> it.copy(availableMinor = -1_800)
                                    "travel" -> it.copy(availableMinor = -14_000)
                                    else -> it
                                }
                            },
                        ),
                    ),
            ),
            emptySet(),
        )

    private val settled = normal.copy(items = normal.items.map { it.copy(kept = it.availableMinor != 0L) })

    private val squareUi =
        CloseOutUi(
            review =
                settled.copy(
                    items =
                        settled.items.map {
                            if (it.availableMinor <
                                0
                            ) {
                                it.copy(availableMinor = 0, kept = false)
                            } else {
                                it
                            }
                        },
                ),
            shown = normal.items.associateBy { it.categoryId },
            outcomes =
                normal.items
                    .filter { !it.settled }
                    .associate { it.categoryId to if (it.availableMinor < 0) CloseOutOutcome.COVERED else CloseOutOutcome.ROLLED },
        )

    init {
        // The rough month really is rough, and the sample's report stays non-empty so the card sits above findings.
        check(rough.rough && !normal.rough)
        check(AssistantReport(sampleAssistant().findings).findings.isNotEmpty())
    }
}
