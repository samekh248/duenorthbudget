package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import app.duenorth.budget.core.AssistantCopy
import app.duenorth.budget.core.AssistantReport
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.Finding
import app.duenorth.budget.core.FindingAction
import app.duenorth.budget.core.FindingKind
import app.duenorth.budget.core.FindingSide
import app.duenorth.budget.core.FindingSure
import app.duenorth.budget.core.MatchSide
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** The assistant pivot item: its findings with their fixes, and the all-clear when there are none. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h780dp-xxhdpi")
class AssistantScreenshotTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun findingsLight() = snap("assistant_light", dark = false, report = sampleAssistant())

    @Test
    fun findingsDark() = snap("assistant_dark", dark = true, report = sampleAssistant())

    @Test
    fun wrongAccountLight() =
        snap("assistant_wrong_account_light", dark = false, report = AssistantReport(sampleAssistant().findings.drop(1)))

    @Test
    fun clearLight() = snap("assistant_clear_light", dark = false, report = AssistantReport.EMPTY)

    @Test
    fun clearDark() = snap("assistant_clear_dark", dark = true, report = AssistantReport.EMPTY)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun snap(
        name: String,
        dark: Boolean,
        report: AssistantReport,
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
                inbox = emptyList(),
            )
        val actions =
            ScreenBar
                .specsFor(
                    ShellRoute.Home,
                    hasBudget = true,
                    mode = BudgetMode.ENVELOPE,
                    homeSection = ScreenBar.HOME_ASSISTANT,
                    undoLabel = "merge",
                ).map { ScreenAction(it.id, it.glyph, it.label, primary = it.primary) {} }
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
                        assistant = AssistantUi(report = report),
                    )
                    MetroAppBar(actions, false, {}, windowInsets = WindowInsets(0))
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }
}

internal fun sampleAssistant(): AssistantReport {
    fun side(
        id: String,
        payee: String,
        date: Int,
        amount: Long,
        account: String,
        category: String = "",
    ) = MatchSide(id, payee, date, amount, account, account.replaceFirstChar { it.uppercase() }, null, category, "")
    return AssistantReport(
        listOf(
            Finding(
                key = "dup:sync|import",
                kind = FindingKind.DUPLICATE,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.duplicate("Checking"),
                sides =
                    listOf(
                        FindingSide(AssistantCopy.BANK_SYNC, side("sync", "Costco", 20261012, -8417, "checking", "Groceries")),
                        FindingSide(AssistantCopy.FILE_IMPORT, side("import", "COSTCO WHSE #1123", 20261013, -8417, "checking")),
                    ),
                reasons = listOf(AssistantCopy.SAME_ACCOUNT, AssistantCopy.SAME_AMOUNT, "1 day apart"),
                actions = listOf(FindingAction.Merge(listOf("sync", "import")), FindingAction.Dismiss(AssistantCopy.NOT_DUPLICATES)),
                accountId = "checking",
            ),
            Finding(
                key = "wrong:gas|visa-gas",
                kind = FindingKind.WRONG_ACCOUNT,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.wrongAccount("Checking", "Visa"),
                sides =
                    listOf(
                        FindingSide(AssistantCopy.ENTERED, side("gas", "Shell", 20261012, -5420, "checking", "Gas")),
                        FindingSide(AssistantCopy.BANK_SYNC, side("visa-gas", "SHELL OIL 5741", 20261013, -5420, "visa")),
                    ),
                reasons = listOf(AssistantCopy.SAME_AMOUNT, "1 day apart", AssistantCopy.SAME_PAYEE, AssistantCopy.BOTH_UNMATCHED),
                actions = listOf(FindingAction.MoveAndMatch("gas", "visa-gas"), FindingAction.Dismiss(AssistantCopy.ITS_RIGHT)),
                accountId = "visa",
            ),
            Finding(
                key = "nobank:refund",
                kind = FindingKind.NO_BANK_MATCH,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.noBankMatch("Checking", 16),
                sides = listOf(FindingSide(AssistantCopy.ENTERED, side("refund", "Target refund", 20261015, 1999, "checking"))),
                reasons = listOf(AssistantCopy.daysOld(16), AssistantCopy.NOTHING_FROM_BANK),
                actions =
                    listOf(
                        FindingAction.Open("checking", "refund"),
                        FindingAction.Dismiss(AssistantCopy.KEEP_WAITING),
                        FindingAction.Delete("refund"),
                    ),
                accountId = "checking",
            ),
        ),
    )
}
