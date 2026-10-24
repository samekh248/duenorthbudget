package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountRow
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.NetWorthPage
import app.duenorth.budget.core.RemoteFile
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.components.AppBarButton
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroAppBar
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

    // The relief styles (settings, appearance, style): the month with goals, and the accounts list.
    @Test
    fun styleHomes() =
        MetroStyle.entries.filter { it.relief }.forEach { style ->
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
                    onAdd = {},
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

    @Test
    fun inboxDark() = snap("inbox_dark", dark = true, shell = sample(), section = HomeSections.headers.indexOf(HomeSections.INBOX))

    @Test
    fun inboxLight() = snap("inbox_light", dark = false, shell = sample(), section = HomeSections.headers.indexOf(HomeSections.INBOX))

    // Select mode: a check box on every row, and the bar acts on the checked ones.
    @Test
    fun inboxSelectLight() =
        snap(
            "inbox_select_light",
            dark = false,
            shell = sample(),
            section = HomeSections.headers.indexOf(HomeSections.INBOX),
            selection = setOf(sample().inbox.first().id),
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
        onAdd: (() -> Unit)? = null,
    ) {
        val inbox = section == HomeSections.headers.indexOf(HomeSections.INBOX)
        val shown = if (filed) shell.copy(inbox = shell.inbox.drop(1)) else shell
        val justFiled = if (filed) listOf(InboxFiled(shell.inbox.first(), 0, "Dining")) else emptyList()
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark, style = style) {
                CompositionLocalProvider(LocalFormatPrefs provides prefs) {
                    Column(Modifier.width(390.dp).height(780.dp)) {
                        HomePivot(
                            shell = shown,
                            loading = false,
                            openMonth = shell.month,
                            monthWindow = MonthWindow.around(shell.month),
                            initialSection = section,
                            onGesture = {},
                            modifier = Modifier.weight(1f),
                            inboxFiled = justFiled,
                            inboxSelection = selection,
                            netWorth = netWorth,
                            onAddTransaction = onAdd,
                        )
                        MetroAppBar(
                            actions =
                                when {
                                    inbox && selection != null ->
                                        listOf(
                                            AppBarButton(AppGlyph.Save, InboxCopy.ACCEPT, onClick = {}),
                                            AppBarButton(AppGlyph.Categories, InboxCopy.CATEGORIZE, onClick = {}),
                                            AppBarButton(AppGlyph.Cancel, InboxCopy.CANCEL, onClick = {}),
                                        )
                                    inbox ->
                                        listOf(
                                            AppBarButton(AppGlyph.Add, "add", onClick = {}),
                                            AppBarButton(AppGlyph.Select, InboxCopy.SELECT, onClick = {}),
                                        )
                                    style.relief ->
                                        listOf(
                                            AppBarButton(AppGlyph.Hold, "hold", onClick = {}),
                                            AppBarButton(AppGlyph.Add, "add", onClick = {}),
                                            AppBarButton(AppGlyph.Categories, "categories", onClick = {}),
                                        )
                                    else ->
                                        listOf(
                                            AppBarButton(AppGlyph.Budgets, "budgets", onClick = {}),
                                            AppBarButton(AppGlyph.Sync, "sync", onClick = {}),
                                            AppBarButton(AppGlyph.Appearance, "appearance", onClick = {}),
                                        )
                                }.asScreenActions(),
                            expanded = false,
                            onExpandedChange = {},
                        )
                    }
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
                Box(Modifier.width(390.dp).height(780.dp)) {
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
