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
import app.duenorth.budget.core.FindingFigure
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
    fun wrongAccountOpenLight() =
        snap("assistant_wrong_account_light", dark = false, report = sampleAssistant(), expanded = setOf(FindingKind.WRONG_ACCOUNT))

    @Test
    fun wrongAccountOpenDark() =
        snap("assistant_wrong_account_dark", dark = true, report = sampleAssistant(), expanded = setOf(FindingKind.WRONG_ACCOUNT))

    @Test
    fun moreLight() =
        snap("assistant_more_light", dark = false, report = sampleAssistantMore(), expanded = setOf(FindingKind.NEAR_DUPLICATE))

    @Test
    fun allKindsLight() =
        snap(
            "assistant_all_light",
            dark = false,
            report =
                AssistantReport(
                    sampleAssistant().findings + sampleAssistantBudget().findings + sampleAssistantSchedules().findings +
                        sampleAssistantMore().findings,
                ),
        )

    @Test
    fun accountsDark() =
        snap(
            "assistant_accounts_dark",
            dark = true,
            report = AssistantReport(sampleAssistantMore().findings.drop(2)),
            expanded = setOf(FindingKind.STALE_SYNC, FindingKind.RECONCILE),
        )

    @Test
    fun budgetLight() =
        snap(
            "assistant_budget_light",
            dark = false,
            report = sampleAssistantBudget(),
            expanded = setOf(FindingKind.OVERSPENT),
        )

    @Test
    fun billsDark() =
        snap(
            "assistant_bills_dark",
            dark = true,
            report = AssistantReport(sampleAssistantBudget().findings.takeLast(2)),
            expanded = setOf(FindingKind.BILLS_AHEAD, FindingKind.BANK_BALANCE),
        )

    @Test
    fun oneKindStartsOpenLight() =
        snap("assistant_one_kind_light", dark = false, report = AssistantReport(sampleAssistantBudget().findings.take(1)))

    @Test
    fun schedulesLight() =
        snap(
            "assistant_schedules_light",
            dark = false,
            report = sampleAssistantSchedules(),
            expanded = setOf(FindingKind.MISSED_SCHEDULE, FindingKind.PRICE_CHANGE),
        )

    @Test
    fun tidyDark() =
        snap(
            "assistant_tidy_dark",
            dark = true,
            report = AssistantReport(sampleAssistantSchedules().findings.drop(2)),
            expanded = setOf(FindingKind.RECURRING, FindingKind.LOOKALIKE_PAYEE),
        )

    @Test
    fun clearLight() = snap("assistant_clear_light", dark = false, report = AssistantReport.EMPTY)

    @Test
    fun clearDark() = snap("assistant_clear_dark", dark = true, report = AssistantReport.EMPTY)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun snap(
        name: String,
        dark: Boolean,
        report: AssistantReport,
        expanded: Set<FindingKind> = emptySet(),
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
                        assistant = AssistantUi(report = report, expanded = expanded),
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

internal fun sampleAssistantMore(): AssistantReport {
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
                key = "near:dinner|bank",
                kind = FindingKind.NEAR_DUPLICATE,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.nearDuplicate("Luigi's", "LUIGIS TRATT"),
                sides =
                    listOf(
                        FindingSide(AssistantCopy.ENTERED, side("dinner", "Luigi's", 20261020, -4000, "visa", "Dining")),
                        FindingSide(AssistantCopy.BANK_SYNC, side("bank", "LUIGIS TRATT", 20261022, -4720, "visa")),
                    ),
                reasons = listOf(AssistantCopy.SAME_ACCOUNT, "2 days apart"),
                actions = listOf(FindingAction.AdoptAndMatch("dinner", "bank"), FindingAction.Dismiss(AssistantCopy.NOT_THE_SAME)),
                accountId = "visa",
                difference = -720,
            ),
            Finding(
                key = "transfer:out|in",
                kind = FindingKind.TRANSFER,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.transfer("Checking", "Visa"),
                sides =
                    listOf(
                        FindingSide(AssistantCopy.BANK_SYNC, side("out", "CHASE CARD PAYMENT", 20261020, -50000, "checking", "Bills")),
                        FindingSide(AssistantCopy.BANK_SYNC, side("in", "PAYMENT THANK YOU", 20261021, 50000, "visa")),
                    ),
                reasons = listOf(AssistantCopy.OPPOSITE_AMOUNTS, "1 day apart", AssistantCopy.DIFFERENT_ACCOUNTS),
                actions = listOf(FindingAction.LinkTransfer("out", "in"), FindingAction.Dismiss(AssistantCopy.NOT_A_TRANSFER)),
                accountId = "checking",
            ),
            Finding(
                key = "stale:savings|1",
                kind = FindingKind.STALE_SYNC,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.staleSync("Ally savings", 5),
                sides = emptyList(),
                reasons = emptyList(),
                actions = listOf(FindingAction.SyncBank, FindingAction.Dismiss(AssistantCopy.LATER)),
                accountId = "savings",
            ),
            Finding(
                key = "reconcile:visa|20260830",
                kind = FindingKind.RECONCILE,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.reconcile("Visa", "aug 30", 41),
                sides = emptyList(),
                reasons = emptyList(),
                actions = listOf(FindingAction.Reconcile("visa"), FindingAction.Dismiss(AssistantCopy.LATER)),
                accountId = "visa",
            ),
        ),
    )
}

internal fun sampleAssistantBudget(): AssistantReport =
    AssistantReport(
        listOf(
            Finding(
                key = "overspent:202610|dining|-4280",
                kind = FindingKind.OVERSPENT,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.overspent("Dining"),
                sides = emptyList(),
                reasons = emptyList(),
                actions = listOf(FindingAction.BudgetCategory("dining"), FindingAction.Dismiss(AssistantCopy.LATER)),
                accountId = "",
                figures =
                    listOf(
                        FindingFigure(AssistantCopy.BUDGETED, 20000),
                        FindingFigure(AssistantCopy.SPENT, -24280),
                        FindingFigure(AssistantCopy.AVAILABLE, -4280),
                    ),
            ),
            Finding(
                key = "below:202610|car|15000",
                kind = FindingKind.UNDERFUNDED,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.underfunded("Car repairs", longGoal = false),
                sides = emptyList(),
                reasons = emptyList(),
                actions = listOf(FindingAction.BudgetCategory("car"), FindingAction.Dismiss(AssistantCopy.LATER)),
                accountId = "",
                figures = listOf(FindingFigure(AssistantCopy.BUDGETED, 15000), FindingFigure(AssistantCopy.GOAL, 25000)),
            ),
            Finding(
                key = "tobudget:202610|31250",
                kind = FindingKind.TO_BUDGET,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.toBudget("October", over = false),
                sides = emptyList(),
                reasons = emptyList(),
                actions = listOf(FindingAction.HoldForNext, FindingAction.Dismiss(AssistantCopy.LATER)),
                accountId = "",
                figures = listOf(FindingFigure(AssistantCopy.TO_BUDGET, 31250)),
            ),
            Finding(
                key = "bills:20261105|checking|-42100",
                kind = FindingKind.BILLS_AHEAD,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.billsAhead("Checking", "nov 5", 14),
                sides = emptyList(),
                reasons = emptyList(),
                actions = listOf(FindingAction.OpenSchedules, FindingAction.Dismiss(AssistantCopy.LATER)),
                accountId = "checking",
                figures =
                    listOf(
                        FindingFigure(AssistantCopy.BALANCE_NOW, 112900),
                        FindingFigure(AssistantCopy.dueIn(14), -155000),
                        FindingFigure(AssistantCopy.LOWEST, -42100),
                    ),
            ),
            Finding(
                key = "balance:checking|118900|112900",
                kind = FindingKind.BANK_BALANCE,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.bankBalance("Checking"),
                sides = emptyList(),
                reasons = emptyList(),
                actions = listOf(FindingAction.Reconcile("checking"), FindingAction.Dismiss(AssistantCopy.LATER)),
                accountId = "checking",
                difference = 6000,
                figures = listOf(FindingFigure(AssistantCopy.AT_THE_BANK, 118900), FindingFigure(AssistantCopy.IN_REGISTER, 112900)),
            ),
        ),
    )

internal fun sampleAssistantSchedules(): AssistantReport =
    AssistantReport(
        listOf(
            Finding(
                key = "missed:rent|20261020",
                kind = FindingKind.MISSED_SCHEDULE,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.missed("Rent", "oct 20"),
                sides = emptyList(),
                reasons = listOf("Checking"),
                actions =
                    listOf(
                        FindingAction.PostSchedule("rent"),
                        FindingAction.SkipSchedule("rent"),
                        FindingAction.Dismiss(AssistantCopy.LATER),
                    ),
                accountId = "checking",
                figures = listOf(FindingFigure(AssistantCopy.AMOUNT, -150000)),
            ),
            Finding(
                key = "price:netflix|-1799",
                kind = FindingKind.PRICE_CHANGE,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.priceChange("Netflix"),
                sides = emptyList(),
                reasons = emptyList(),
                actions = listOf(FindingAction.UpdateScheduleAmount("netflix", -1799), FindingAction.Dismiss(AssistantCopy.KEEP_IT)),
                accountId = "visa",
                difference = -300,
                figures = listOf(FindingFigure(AssistantCopy.SCHEDULE_SAYS, -1499), FindingFigure(AssistantCopy.LAST_TWO, -1799)),
            ),
            Finding(
                key = "recurring:gym|out",
                kind = FindingKind.RECURRING,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.recurring("Planet Fitness", 4),
                sides = emptyList(),
                reasons = listOf(AssistantCopy.nextAround("nov 1")),
                actions = listOf(FindingAction.MakeSchedule("gym-oct", "2026-11-01"), FindingAction.Dismiss(AssistantCopy.NOT_A_BILL)),
                accountId = "",
                figures = listOf(FindingFigure(AssistantCopy.LAST_PAID, -2499)),
            ),
            Finding(
                key = "rule:shell|gas",
                kind = FindingKind.RULE_IDEA,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.ruleIdea("Shell", "Gas", 7),
                sides = emptyList(),
                reasons = emptyList(),
                actions = listOf(FindingAction.MakeRule("shell", "gas"), FindingAction.Dismiss(AssistantCopy.NO_THANKS)),
                accountId = "",
            ),
            Finding(
                key = "lookalike:costco-bank|costco",
                kind = FindingKind.LOOKALIKE_PAYEE,
                sure = FindingSure.MAYBE,
                headline = AssistantCopy.lookalike("COSTCO #1123", "Costco"),
                sides = emptyList(),
                reasons = listOf(AssistantCopy.usedTimes("COSTCO #1123", 3), AssistantCopy.usedTimes("Costco", 14)),
                actions = listOf(FindingAction.MergePayees("costco-bank", "Costco"), FindingAction.Dismiss(AssistantCopy.NOT_THE_SAME)),
                accountId = "",
            ),
        ),
    )
