package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountRow
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.RemoteFile
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.components.AppBarButton
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.asScreenActions
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

    @Test
    fun categoryLedgerLight() = categorySnap("category_ledger_light", dark = false)

    @Test
    fun categoryLedgerDark() = categorySnap("category_ledger_dark", dark = true)

    @Test
    fun accountsLight() = snap("accounts_light", dark = false, shell = sample(), section = 1)

    @Test
    fun inboxDark() = snap("inbox_dark", dark = true, shell = sample(), section = 5)

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
        section: Int = 0,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp).height(780.dp)) {
                    HomePivot(
                        shell = shell,
                        review = null,
                        netWorth = null,
                        upcoming = emptyList(),
                        loading = false,
                        openMonth = shell.month,
                        monthWindow = MonthWindow.around(shell.month),
                        initialSection = section,
                        onGesture = {},
                        modifier = Modifier.weight(1f),
                    )
                    MetroAppBar(
                        actions =
                            listOf(
                                AppBarButton(AppGlyph.Budgets, "budgets", onClick = {}),
                                AppBarButton(AppGlyph.Sync, "sync", onClick = {}),
                                AppBarButton(AppGlyph.Appearance, "appearance", onClick = {}),
                            ).asScreenActions(),
                        expanded = false,
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
                            review = null,
                            netWorth = null,
                            upcoming = emptyList(),
                            loading = false,
                            openMonth = shell.month,
                            monthWindow = MonthWindow.around(shell.month),
                            initialSection = 5,
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
            inbox = listOf(InboxRow("cafe", "Cafe", -2_500), InboxRow("bus", "Bus", -1_000)),
        )
}
