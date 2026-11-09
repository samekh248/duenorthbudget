package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.SavedInstance
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.theme.MetroStyle
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * The accessibility pass, held by a test: on the screens a person spends their time on, every control TalkBack can
 * reach has a name it can speak, is at least a 48dp touch target, and every row that shows more than one amount
 * says which amount is which, since a screen reader can't see the column headings.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class AccessibilityAuditTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Before
    fun pinCalendarMonth() {
        CalendarMonth.pinned = YearMonth.of(2026, 10)
    }

    @After
    fun unpinCalendarMonth() {
        CalendarMonth.pinned = null
    }

    @Test
    fun budget() = audit { Home(HomeSections.headers.indexOf(HomeSections.BUDGET), FormatPrefs(experimental = true, goalTemplates = true)) }

    @Test
    fun accounts() = audit { Home(HomeSections.headers.indexOf(HomeSections.ACCOUNTS)) }

    @Test
    fun inbox() = audit { Home(HomeSections.headers.indexOf(HomeSections.INBOX)) }

    @Test
    fun register() =
        audit {
            Column(Modifier.width(390.dp).height(780.dp)) {
                SectionTitled("ACCOUNTS", Modifier.weight(1f)) {
                    RegisterScreen(page = registerSample(), onGesture = {}, onOpen = {}, onAdd = {}, onReconcile = {})
                }
                MetroAppBar(
                    actions = ScreenBar.specsFor(ShellRoute.Register("visa"), true, BudgetMode.ENVELOPE).map { it.action() },
                    expanded = true,
                    onExpandedChange = {},
                    windowInsets = WindowInsets(bottom = 0.dp),
                )
            }
        }

    @Test
    fun categoryPage() = audit(MetroStyle.entries) { CategoryPage() }

    @Test
    fun settings() = SettingsPage.entries.forEach { page -> auditOnce(page) }

    @Test
    fun accountsInEveryStyle() = audit(MetroStyle.entries) { Home(HomeSections.headers.indexOf(HomeSections.ACCOUNTS)) }

    @Test
    fun inboxInEveryStyle() = audit(MetroStyle.entries) { Home(HomeSections.headers.indexOf(HomeSections.INBOX)) }

    @Test
    fun budgetInEveryStyle() = audit(MetroStyle.entries) { Home(HomeSections.headers.indexOf(HomeSections.BUDGET)) }

    private fun auditOnce(page: SettingsPage) {
        // One setContent per test, so the pages are walked by switching what the one host shows.
        if (page != SettingsPage.entries.first()) return
        val shown = mutableStateOf(page)
        compose.setContent { MetroTheme { Box(Modifier.width(390.dp).height(1600.dp)) { SettingsSample(shown.value) } } }
        val problems =
            SettingsPage.entries.flatMap { each ->
                shown.value = each
                compose.waitForIdle()
                A11yAudit.problems(compose.onRoot().fetchSemanticsNode(), compose.density.density).map { "${each.header}: $it" }
            }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Composable
    private fun CategoryPage() {
        val shell =
            TemplateSamples.shell.copy(
                groups = listOf(GroupRow("g-liv", "Living", -2_000, listOf(CategoryRow("c-food", "Food", 20_000, -22_000, -2_000, false)))),
            )
        CompositionLocalProvider(LocalFormSaveHost provides rememberFormSaveHost()) {
            Column(Modifier.width(390.dp).height(1600.dp)) {
                CategoryBudgetScreen(shell, "c-food", "That amount is too large", {}, {}, {}, Modifier.weight(1f))
                MetroAppBar(
                    ScreenBar.specsFor(ShellRoute.CategoryBudget("c-food"), true, BudgetMode.ENVELOPE).map { it.action() },
                    true,
                    {},
                    windowInsets = WindowInsets(bottom = 0.dp),
                )
            }
        }
    }

    @Composable
    private fun Home(
        section: Int,
        prefs: FormatPrefs = FormatPrefs(),
    ) {
        val shell = shellSample()
        CompositionLocalProvider(LocalFormatPrefs provides prefs) {
            Column(Modifier.width(390.dp).height(1600.dp)) {
                Box(Modifier.weight(1f)) {
                    HomePivot(
                        shell = shell,
                        loading = false,
                        openMonth = shell.month,
                        monthWindow = MonthWindow.around(shell.month),
                        initialSection = section,
                        onGesture = {},
                        modifier = Modifier.fillMaxSize(),
                        onBudgetCategory = {},
                        onToBudget = {},
                    )
                }
                MetroAppBar(
                    actions =
                        ScreenBar
                            .specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE, homeSection = section)
                            .map { it.action() },
                    expanded = true,
                    onExpandedChange = {},
                    windowInsets = WindowInsets(bottom = 0.dp),
                )
            }
        }
    }

    /** Shows [content] in each of [styles] in turn and fails with every problem found in any of them. */
    private fun audit(
        styles: List<MetroStyle> = listOf(MetroStyle.Metro),
        content: @Composable () -> Unit,
    ) {
        val current = mutableStateOf(styles.first())
        compose.setContent { MetroTheme(style = current.value) { content() } }
        val problems =
            styles.flatMap { style ->
                current.value = style
                compose.waitForIdle()
                A11yAudit.problems(compose.onRoot().fetchSemanticsNode(), compose.density.density).map { "${style.id}: $it" }
            }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}

/** The checks [AccessibilityAuditTest] runs over a screen's semantics, shared with the 200% font-scale baselines. */
internal object A11yAudit {
    private val MONEY = Regex("""-?[$€£¥]\s?\d""")

    fun problems(
        root: SemanticsNode,
        density: Float,
    ): List<String> {
        val out = mutableListOf<String>()

        fun walk(node: SemanticsNode) {
            val config = node.config
            val clickable = config.getOrNull(SemanticsActions.OnClick) != null
            val texts = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            val described = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().filter { it.isNotBlank() }
            val tag = config.getOrNull(SemanticsProperties.TestTag) ?: "? in ${ancestorTag(node)}"
            val shown = (described + texts).joinToString(" | ").take(80)
            if (clickable && !config.contains(SemanticsProperties.Disabled)) {
                if (described.isEmpty() && texts.none { it.isNotBlank() }) out += "no spoken name: tag=$tag"
                val min = 48 * density - 1
                if (node.size.width < min || node.size.height < min) {
                    out +=
                        "under 48dp (${(node.size.width / density).toInt()}x${(node.size.height / density).toInt()}dp): tag=$tag \"$shown\""
                }
            }
            if (config.isMergingSemanticsOfDescendants && described.isEmpty() && texts.count { MONEY.containsMatchIn(it) } >= 2) {
                out += "amounts without labels: tag=$tag \"$shown\""
            }
            node.children.forEach(::walk)
        }
        walk(root)
        return out
    }

    private fun ancestorTag(node: SemanticsNode): String {
        var at = node.parent
        while (at != null) {
            at.config.getOrNull(SemanticsProperties.TestTag)?.let { return it }
            at = at.parent
        }
        return "root"
    }
}

/** The settings pivot as the app shows it with a budget open, signed in to a server. */
@Composable
internal fun SettingsSample(
    page: SettingsPage,
    dark: Boolean = false,
) {
    val home = SavedInstance("i1", "http://192.168.1.20:5006", "home server")
    SettingsScreen(
        page = page,
        instances = listOf(home, SavedInstance("i2", "https://budget.example.com", "office")),
        activeInstance = home,
        instanceReachable = true,
        signedIn = true,
        budgets = listOf(BudgetSummary("home", "Home", "USD"), BudgetSummary("trip", "Trip", "USD")),
        openBudgetId = "home",
        themeMode = if (dark) "dark" else "light",
        accentId = "magenta",
        formatPrefs = FormatPrefs(),
        cloudFileId = "cloud-1",
        encrypted = false,
        onPage = {},
        onMakeActive = {},
        onRemoveInstance = {},
        onOpenBudget = {},
        onTheme = {},
        onAccent = {},
        onPayees = {},
        onDateFormat = {},
        onNumberFormat = {},
        onFirstDay = {},
        onHideFraction = {},
        onExport = {},
        onEncryption = {},
        onRequestAdvanced = {},
    )
}
