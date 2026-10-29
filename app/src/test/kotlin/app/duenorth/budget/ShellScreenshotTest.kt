package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountRow
import app.duenorth.budget.core.ActualMonthMath
import app.duenorth.budget.core.BankSyncFailure
import app.duenorth.budget.core.BankSyncFix
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.ImportCopy
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.NetWorthPage
import app.duenorth.budget.core.RemoteFile
import app.duenorth.budget.core.SimpleFinError
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.components.AppBarButton
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.components.asScreenActions
import app.duenorth.budget.design.theme.MetroStyle
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class ShellScreenshotTest {
    @Test
    fun homeLight() = snap("home_light", dark = false, shell = sample())

    @Test
    fun homeDark() = snap("home_dark", dark = true, shell = sample())

    // The budget's real bar: add and edit budget as buttons, the rest in the menu.
    @Test
    fun budgetBarLight() = budgetBarSnap("home_budget_bar_light", dark = false, expanded = false)

    @Test
    fun budgetBarDark() = budgetBarSnap("home_budget_bar_dark", dark = true, expanded = false)

    @Test
    fun budgetBarExpandedLight() = budgetBarSnap("home_budget_bar_expanded_light", dark = false, expanded = true)

    @Test
    fun budgetBarExpandedDark() = budgetBarSnap("home_budget_bar_expanded_dark", dark = true, expanded = true)

    @Test
    fun emptyLight() =
        snap(
            "empty_light",
            dark = false,
            shell = sample().copy(headerMinor = 0, groups = emptyList(), accounts = emptyList(), inbox = emptyList()),
        )

    // More budgeted than there is to budget: the figure turns red.
    @Test
    fun homeOverbudgetedLight() = snap("home_overbudgeted_light", dark = false, shell = sample().copy(headerMinor = -12_550))

    @Test
    fun homeOverbudgetedDark() = snap("home_overbudgeted_dark", dark = true, shell = sample().copy(headerMinor = -12_550))

    // Home with show hidden categories on: hidden groups and categories come back dimmed.
    @Test
    fun homeGoalsLight() = snap("home_goals_light", dark = false, shell = withGoals(), prefs = templatesOn)

    @Test
    fun homeGoalsDark() = snap("home_goals_dark", dark = true, shell = withGoals(), prefs = templatesOn)

    private val templatesOn = FormatPrefs(experimental = true, goalTemplates = true)

    // Experimental: what to budget leaves once later months' budgeting comes out.
    @Test
    fun homeFreeAfterPlansLight() = snap("home_free_after_plans_light", dark = false, shell = withPlans(), showAhead = true)

    @Test
    fun homeFreeAfterPlansDark() = snap("home_free_after_plans_dark", dark = true, shell = withPlans(), showAhead = true)

    // Later plans overrun what's left to budget: the figure turns red.
    @Test
    fun homeFreeAfterPlansShortLight() =
        snap("home_free_after_plans_short_light", dark = false, shell = withPlans(-6_500), showAhead = true)

    @Test
    fun homeFreeAfterPlansShortDark() = snap("home_free_after_plans_short_dark", dark = true, shell = withPlans(-6_500), showAhead = true)

    // The other styles: tile relief gives the figure its own raised tile, mica holds it on the one card.
    @Test
    fun homeFreeAfterPlansStyles() =
        MetroStyle.entries.filter { it != MetroStyle.Metro }.forEach { style ->
            for (dark in listOf(false, true)) {
                val theme = if (dark) "dark" else "light"
                snap("style_${style.id}_home_free_after_plans_$theme", dark, withPlans(), showAhead = true, style = style)
            }
        }

    private fun withPlans(free: Long): MonthShell = sample().let { it.copy(ahead = ActualMonthMath.FuturePlans(free, 202612)) }

    private fun withPlans(): MonthShell = sample().let { it.copy(ahead = ActualMonthMath.FuturePlans(it.headerMinor - 40_000, 202612)) }

    /** Rent and Rainy day are on target, Power and Gas are short, Rainy day saves toward a #goal. */
    private fun withGoals(): MonthShell =
        sample().let { shell ->
            val goals =
                mapOf(
                    "c-rent" to (110_000L to false),
                    "c-power" to (18_000L to false),
                    "c-gas" to (16_000L to false),
                    "c-groceries" to (20_000L to false),
                    "c-rainy" to (200_000L to true),
                )
            shell.copy(
                groups =
                    shell.groups.map { group ->
                        group.copy(
                            categories =
                                group.categories.map { row ->
                                    goals[row.id]?.let { (goal, long) -> row.copy(goalMinor = goal, longGoal = long) } ?: row
                                },
                        )
                    },
            )
        }

    // The other styles (settings, appearance, style): the month with goals, the accounts list and the inbox.
    @Test
    fun styleHomes() =
        MetroStyle.entries.filter { it != MetroStyle.Metro }.forEach { style ->
            for (dark in listOf(false, true)) {
                val theme = if (dark) "dark" else "light"
                snap("style_${style.id}_home_$theme", dark, withGoals(), prefs = templatesOn, style = style)
                snap(
                    "style_${style.id}_accounts_$theme",
                    dark,
                    sample().copy(accounts = manyAccounts()),
                    section = HomeSections.headers.indexOf(HomeSections.ACCOUNTS),
                    style = style,
                )
                snap(
                    "style_${style.id}_inbox_$theme",
                    dark,
                    sample(),
                    section = HomeSections.headers.indexOf(HomeSections.INBOX),
                    style = style,
                )
            }
        }

    // The empty inbox's tray in every style, since the style draws the tray.
    @Test
    fun inboxEmpty() =
        MetroStyle.entries.forEach { style ->
            for (dark in listOf(false, true)) {
                val name = if (style == MetroStyle.Metro) "inbox_empty" else "style_${style.id}_inbox_empty"
                snap(
                    "${name}_${if (dark) "dark" else "light"}",
                    dark,
                    sample().copy(inbox = emptyList()),
                    section = HomeSections.headers.indexOf(HomeSections.INBOX),
                    style = style,
                )
            }
        }

    @Test
    fun styleOverbudgetedDark() =
        snap(
            "style_tile-relief_home_overbudgeted_dark",
            dark = true,
            shell = sample().copy(headerMinor = -12_550),
            style = MetroStyle.TileRelief,
        )

    @Test
    fun homeHiddenShownLight() = snap("home_hidden_shown_light", dark = false, shell = withHidden())

    @Test
    fun homeHiddenShownDark() = snap("home_hidden_shown_dark", dark = true, shell = withHidden())

    @Test
    fun categoryLedgerLight() = categorySnap("category_ledger_light", dark = false)

    @Test
    fun categoryLedgerDark() = categorySnap("category_ledger_dark", dark = true)

    @Test
    fun accountsLight() =
        snap(
            "accounts_light",
            dark = false,
            shell = sample().copy(accounts = manyAccounts()),
            section = HomeSections.headers.indexOf(HomeSections.ACCOUNTS),
        )

    @Test
    fun accountsDark() =
        snap(
            "accounts_dark",
            dark = true,
            shell = sample().copy(accounts = manyAccounts()),
            section = HomeSections.headers.indexOf(HomeSections.ACCOUNTS),
        )

    // Bank sync on the accounts page: a strip just above the bar names the account fetching now.
    @Test
    fun accountsBankSyncingLight() = bankSyncSnap("accounts_bank_syncing_light", dark = false, BankSyncUi(accountName = "Joint checking"))

    @Test
    fun accountsBankSyncingDark() = bankSyncSnap("accounts_bank_syncing_dark", dark = true, BankSyncUi(accountName = "Joint checking"))

    // An account failed mid-sync: its short error shows under the one fetching now.
    @Test
    fun accountsBankSyncErrorLight() =
        bankSyncSnap("accounts_bank_sync_error_light", dark = false, BankSyncUi(accountName = "Savings", failures = listOf(cardFailure)))

    @Test
    fun accountsBankSyncErrorDark() =
        bankSyncSnap("accounts_bank_sync_error_dark", dark = true, BankSyncUi(accountName = "Savings", failures = listOf(cardFailure)))

    // Done with an error: the glyph rests and the strip offers retry.
    @Test
    fun accountsBankSyncRetryLight() =
        bankSyncSnap("accounts_bank_sync_retry_light", dark = false, BankSyncUi(running = false, failures = listOf(cardFailure)))

    @Test
    fun accountsBankSyncRetryDark() =
        bankSyncSnap("accounts_bank_sync_retry_dark", dark = true, BankSyncUi(running = false, failures = listOf(cardFailure)))

    // The tile stays over every page while it matters: here over the budget.
    @Test
    fun budgetBankSyncingDark() =
        bankSyncSnap("budget_bank_syncing_dark", dark = true, BankSyncUi(accountName = "Checking"), HomeSections.BUDGET)

    // Tapping a failed sync's tile opens this page: each account the sync reached, and why one failed.
    @Test
    fun bankSyncReportLight() = reportSnap("bank_sync_report_light", dark = false)

    @Test
    fun bankSyncReportDark() = reportSnap("bank_sync_report_dark", dark = true)

    private fun reportSnap(
        name: String,
        dark: Boolean,
    ) {
        val ui =
            BankSyncUi(
                running = false,
                started = listOf("Checking", "Joint checking", "Credit card", "Savings"),
                failures =
                    listOf(
                        BankSyncFailure("Credit card", SimpleFinError.NEEDS_ATTENTION, "card", BankSyncFix.SIMPLEFIN),
                        BankSyncFailure("Savings", SimpleFinError.ACCOUNT_MISSING, "savings", BankSyncFix.RELINK),
                    ),
            )
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp).height(780.dp)) {
                    SectionTitled(HomeSections.ACCOUNTS.uppercase(), Modifier.weight(1f)) { BankSyncReportScreen(ui) }
                    MetroAppBar(
                        ScreenBar
                            .specsFor(ShellRoute.BankSyncReport, hasBudget = true, mode = BudgetMode.ENVELOPE)
                            .map { ScreenAction(it.id, it.glyph, it.label, it.primary) {} },
                        expanded = false,
                        onExpandedChange = {},
                    )
                }
            }
        }
    }

    private val cardFailure = BankSyncFailure("Credit card", ImportCopy.FETCH_FAILED)

    private fun bankSyncSnap(
        name: String,
        dark: Boolean,
        ui: BankSyncUi,
        section: String = HomeSections.ACCOUNTS,
    ) = snap(
        name,
        dark = dark,
        shell = sample().copy(accounts = manyAccounts()),
        section = HomeSections.headers.indexOf(section),
        bankSync = ui,
    )

    @Test
    fun accountsReorderLight() =
        snap(
            "accounts_reorder_light",
            dark = false,
            shell = sample().copy(accounts = manyAccounts()),
            section = HomeSections.headers.indexOf(HomeSections.ACCOUNTS),
            accountsReorder = true,
        )

    @Test
    fun accountsReorderDark() =
        snap(
            "accounts_reorder_dark",
            dark = true,
            shell = sample().copy(accounts = manyAccounts()),
            section = HomeSections.headers.indexOf(HomeSections.ACCOUNTS),
            accountsReorder = true,
        )

    @Test
    fun inboxDark() = snap("inbox_dark", dark = true, shell = sample(), section = HomeSections.headers.indexOf(HomeSections.INBOX))

    @Test
    fun inboxLight() = snap("inbox_light", dark = false, shell = sample(), section = HomeSections.headers.indexOf(HomeSections.INBOX))

    // Select mode: a check box on every row, grouped into ready to accept and needs a category, and
    // the bar acts on the checked ones. Only cancel until a row is checked.
    @Test
    fun inboxSelectNoneLight() = selectSnap("inbox_select_none_light", dark = false, emptySet())

    @Test
    fun inboxSelectNoneDark() = selectSnap("inbox_select_none_dark", dark = true, emptySet())

    // Every checked row has a category: accept, categorize, cancel, then delete set apart.
    @Test
    fun inboxSelectLight() = selectSnap("inbox_select_light", dark = false, setOf("coffee", "shell"))

    @Test
    fun inboxSelectDark() = selectSnap("inbox_select_dark", dark = true, setOf("coffee", "shell"))

    // A checked row with no category hides accept.
    @Test
    fun inboxSelectNeedsCategoryLight() = selectSnap("inbox_select_needs_category_light", dark = false, setOf("coffee", "atm"))

    @Test
    fun inboxSelectNeedsCategoryDark() = selectSnap("inbox_select_needs_category_dark", dark = true, setOf("coffee", "atm"))

    private fun selectSnap(
        name: String,
        dark: Boolean,
        selection: Set<String>,
    ) = snap(
        name,
        dark = dark,
        // Four rows, so both groups fit on the screen.
        shell = sample().let { it.copy(inbox = it.inbox.filter { row -> row.id in setOf("coffee", "shell", "rent", "atm") }) },
        section = HomeSections.headers.indexOf(HomeSections.INBOX),
        selection = selection,
    )

    // Corner Coffee just filed: the confirmation fills the featured panel before the next one rises in.
    @Test
    fun inboxFiledLight() =
        snap("inbox_filed_light", dark = false, shell = sample(), section = HomeSections.headers.indexOf(HomeSections.INBOX), filed = true)

    @Test
    fun inboxFiledDark() =
        snap("inbox_filed_dark", dark = true, shell = sample(), section = HomeSections.headers.indexOf(HomeSections.INBOX), filed = true)

    // Reports on the home pivot: the reporting hub, with today's net worth on its line.
    @Test
    fun reportsHubLight() = snap("reports_hub_light", dark = false, shell = sample(), section = reportsSection, netWorth = netWorth())

    @Test
    fun reportsHubDark() = snap("reports_hub_dark", dark = true, shell = sample(), section = reportsSection, netWorth = netWorth())

    private val reportsSection = HomeSections.headers.indexOf(HomeSections.REPORTS)

    private fun netWorth() = NetWorthPage(Currencies.byCode("USD")!!, 1_284_250, 21_830_000, includeOffBudget = true)

    // Edge to edge with a 32dp status bar (gray strip): the pivot title starts below it.
    @Test
    fun homeUnderStatusBarDark() = statusBarSnap("home_status_bar_dark", dark = true, inset = 32.dp)

    @Test
    fun serverLight() = serverSnap("server_light", dark = false)

    @Test
    fun serverDark() = serverSnap("server_dark", dark = true)

    private fun snap(
        name: String,
        dark: Boolean,
        shell: MonthShell,
        section: Int = HomeSections.headers.indexOf(HomeSections.BUDGET),
        filed: Boolean = false,
        selection: Set<String>? = null,
        netWorth: NetWorthPage? = null,
        prefs: FormatPrefs = FormatPrefs(),
        style: MetroStyle = MetroStyle.Metro,
        showAhead: Boolean = false,
        accountsReorder: Boolean = false,
        bankSync: BankSyncUi? = null,
    ) {
        val inbox = section == HomeSections.headers.indexOf(HomeSections.INBOX)
        val accounts = section == ScreenBar.HOME_ACCOUNTS
        val shown = if (filed) shell.copy(inbox = shell.inbox.drop(1)) else shell
        val justFiled = if (filed) listOf(InboxFiled(shell.inbox.first(), 0, "Dining")) else emptyList()
        // A still of a running sync: a turning glyph never lets the capture settle.
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark, style = style, animationsEnabled = bankSync?.running != true) {
                CompositionLocalProvider(LocalFormatPrefs provides prefs) {
                    Column(Modifier.width(390.dp).height(780.dp)) {
                        Box(Modifier.weight(1f)) {
                            HomePivot(
                                shell = shown,
                                loading = false,
                                openMonth = shell.month,
                                monthWindow = MonthWindow.around(shell.month),
                                initialSection = section,
                                onGesture = {},
                                modifier = Modifier.fillMaxSize(),
                                inboxFiled = justFiled,
                                inboxSelection = selection,
                                netWorth = netWorth,
                                showAhead = showAhead,
                                accountsReorder = accountsReorder,
                            )
                            bankSync?.let { BankSyncPanel(it, {}, {}, Modifier.align(Alignment.BottomCenter)) }
                        }
                        MetroAppBar(
                            actions =
                                when {
                                    inbox && selection != null -> emptyList()
                                    inbox ->
                                        listOfNotNull(
                                            AppBarButton(
                                                AppGlyph.Select,
                                                InboxCopy.SELECT,
                                                onClick = {},
                                            ).takeIf { shown.inbox.isNotEmpty() },
                                            AppBarButton(AppGlyph.Add, "add", onClick = {}),
                                        )
                                    style.relief ->
                                        listOf(AppBarButton(AppGlyph.Add, "add", onClick = {}))
                                    else ->
                                        listOf(
                                            AppBarButton(AppGlyph.Budgets, "budgets", onClick = {}),
                                            AppBarButton(AppGlyph.Sync, "sync", onClick = {}),
                                            AppBarButton(AppGlyph.Appearance, "appearance", onClick = {}),
                                        )
                                }.asScreenActions().let { fake ->
                                    if (inbox && selection != null) {
                                        ScreenBar
                                            .specsFor(
                                                ShellRoute.Home,
                                                hasBudget = true,
                                                mode = BudgetMode.ENVELOPE,
                                                homeSection = ScreenBar.HOME_INBOX,
                                                inboxSelecting = true,
                                                inboxPick = InboxPick.of(selection, shown.inbox),
                                            ).filter { it.primary }
                                            .map { ScreenAction(it.id, it.glyph, it.label, it.primary, apart = it.apart) {} }
                                    } else if (!accounts) {
                                        fake
                                    } else {
                                        ScreenBar
                                            .specsFor(
                                                ShellRoute.Home,
                                                hasBudget = true,
                                                mode = BudgetMode.ENVELOPE,
                                                homeSection = ScreenBar.HOME_ACCOUNTS,
                                                accountsReorder = accountsReorder,
                                                accountsBankSync = bankSync != null,
                                            ).map {
                                                ScreenAction(
                                                    it.id,
                                                    it.glyph,
                                                    it.label,
                                                    it.primary,
                                                    spinning =
                                                        it.id == "bank-sync" && bankSync?.running == true,
                                                ) {}
                                            }
                                    }
                                },
                            expanded = false,
                            onExpandedChange = {},
                        )
                    }
                }
            }
        }
    }

    private fun budgetBarSnap(
        name: String,
        dark: Boolean,
        expanded: Boolean,
    ) {
        val shell = sample()
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp).height(780.dp)) {
                    Box(Modifier.weight(1f)) {
                        HomePivot(
                            shell = shell,
                            loading = false,
                            openMonth = shell.month,
                            monthWindow = MonthWindow.around(shell.month),
                            initialSection = HomeSections.headers.indexOf(HomeSections.BUDGET),
                            onGesture = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    MetroAppBar(
                        actions =
                            ScreenBar
                                .specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE)
                                .map { ScreenAction(it.id, it.glyph, it.label, it.primary) {} },
                        expanded = expanded,
                        onExpandedChange = {},
                    )
                }
            }
        }
    }

    private fun statusBarSnap(
        name: String,
        dark: Boolean,
        inset: Dp,
    ) {
        val shell = sample()
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    ShellChrome(actions = emptyList(), statusBar = WindowInsets(top = inset)) { modifier ->
                        HomePivot(
                            shell = shell,
                            loading = false,
                            openMonth = shell.month,
                            monthWindow = MonthWindow.around(shell.month),
                            initialSection = HomeSections.headers.indexOf(HomeSections.INBOX),
                            onGesture = {},
                            modifier = modifier,
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(32.dp).background(Color(0x80808080)))
                }
            }
        }
    }

    private fun categorySnap(
        name: String,
        dark: Boolean,
    ) {
        val group =
            GroupRow(
                "g-food",
                "Food",
                -3_200,
                listOf(
                    CategoryRow("c-groceries", "Groceries", 20_000, 28_000, -8_000, false),
                    CategoryRow("c-dining", "Dining", 8_000, 4_800, 3_200, false),
                    CategoryRow("c-coffee", "Coffee", 3_000, 3_000, 0, false),
                    CategoryRow("c-house", "Household", 4_000, 2_400, 1_600, false),
                ),
            )
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    BudgetSection(
                        shell = sample().copy(groups = listOf(group)),
                        onListGesture = {},
                        onCategory = {},
                    )
                }
            }
        }
    }

    private fun serverSnap(
        name: String,
        dark: Boolean,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                HostedPage {
                    ServerScreen(
                        address = "http://192.168.1.20:5006",
                        signedIn = true,
                        files = listOf(RemoteFile("file-home", "group-1", "Home", null)),
                        status = "synced 3:04 pm",
                        error = null,
                        onConnect = { _, _ -> },
                        onOpen = {},
                        onReplace = {},
                        onUpload = {},
                        onSync = {},
                        onSignOut = {},
                    )
                }
            }
        }
    }

    private fun withHidden(): MonthShell =
        sample().let { shell ->
            shell.copy(
                groups =
                    shell.groups.map { group ->
                        if (group.id == "g-every") {
                            group.copy(categories = group.categories + CategoryRow("c-gym", "Gym", 0, 0, 0, false, hidden = true))
                        } else {
                            group
                        }
                    } +
                        GroupRow(
                            "g-old",
                            "Old house",
                            0,
                            listOf(CategoryRow("c-hoa", "HOA", 0, 0, 0, false)),
                            hidden = true,
                        ),
            )
        }

    private fun manyAccounts(): List<AccountRow> =
        listOf(
            AccountRow("checking", "Checking", offBudget = false, closed = false, balanceMinor = 226_000),
            AccountRow("joint", "Joint checking", offBudget = false, closed = false, balanceMinor = 84_250),
            AccountRow("card", "Credit card", offBudget = false, closed = false, balanceMinor = -41_870),
            AccountRow("savings", "Savings", offBudget = true, closed = false, balanceMinor = 1_250_000),
            AccountRow("mortgage", "Mortgage", offBudget = true, closed = false, balanceMinor = -18_400_000),
            AccountRow("old-card", "Old card", offBudget = false, closed = true, balanceMinor = 0),
        )

    private fun sample(): MonthShell =
        MonthShell(
            budgetId = "home",
            budgetName = "Home",
            mode = BudgetMode.ENVELOPE,
            currency = Currencies.byCode("USD")!!,
            month = YearMonth.of(2026, 10),
            headerLabel = "to budget",
            headerMinor = 42_000,
            bufferedMinor = 25_000,
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
                        "g-every",
                        "Everyday",
                        18_640,
                        listOf(
                            CategoryRow("c-gas", "Gas", 12_000, 4_360, 7_640, false),
                            CategoryRow("c-phone", "Phone", 11_000, 0, 11_000, false),
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
                    GroupRow("g-save", "Savings", 80_000, listOf(CategoryRow("c-rainy", "Rainy day", 80_000, 0, 80_000, false))),
                ),
            accounts =
                listOf(
                    AccountRow("checking", "Checking", offBudget = false, closed = false, balanceMinor = 226_000),
                    AccountRow("savings", "Savings", offBudget = true, closed = false, balanceMinor = 10_000),
                ),
            inbox =
                listOf(
                    InboxRow("coffee", "Corner Coffee", -575, 20261015, "visa", "Visa", "", "c-dining", "Dining"),
                    InboxRow("amazon", "Amazon", -6_318, 20261015, "visa", "Visa", "2 items", "c-house", "Household"),
                    InboxRow("shell", "Shell", -4_210, 20261014, "checking", "Checking", "", "c-gas", "Gas"),
                    InboxRow("acme", "Acme Payroll", 185_000, 20261014, "checking", "Checking", "", "c-pay", "Paycheck"),
                    InboxRow(
                        "rent",
                        "Riverside Property Management",
                        -120_000,
                        20261012,
                        "checking",
                        "Checking",
                        "October rent",
                        "c-rent",
                        "Rent",
                    ),
                    InboxRow("atm", "(no payee)", -2_000, 20261011, "checking", "Checking", "ATM withdrawal"),
                ),
        )
}
