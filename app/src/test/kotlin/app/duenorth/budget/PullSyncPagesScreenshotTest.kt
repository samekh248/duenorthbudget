package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.PayeeLookalikes
import app.duenorth.budget.core.PayeeRow
import app.duenorth.budget.core.SavedInstance
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.theme.MetroStyle
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The pull-down sync panel off home: the register, payees and settings pulled down open it as home does, in
 * every style. Each `_before` shot is the same pull with no panel, as those pages behaved until now.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h780dp-xxhdpi")
class PullSyncPagesScreenshotTest {
    @get:Rule
    val compose = createHostComposeRule()

    private enum class Page(
        val route: ShellRoute,
        val title: String?,
    ) {
        REGISTER(ShellRoute.Register("visa"), "ACCOUNTS"),
        PAYEES(ShellRoute.Payees, null),
        SETTINGS(ShellRoute.Settings, null),
    }

    @Test
    fun registerLight() = pair("register", Page.REGISTER, dark = false)

    @Test
    fun registerDark() = pair("register", Page.REGISTER, dark = true)

    @Test
    fun payeesLight() = pair("payees", Page.PAYEES, dark = false)

    @Test
    fun payeesDark() = pair("payees", Page.PAYEES, dark = true)

    @Test
    fun settingsLight() = pair("settings", Page.SETTINGS, dark = false)

    @Test
    fun settingsDark() = pair("settings", Page.SETTINGS, dark = true)

    // The panel is one piece drawn from the style's own colours, so each style's register shows it the same way.
    @Test
    fun registerStyles() =
        MetroStyle.entries.forEach { style ->
            listOf(false, true).forEach { dark ->
                snap("pull_sync_page_register_${style.id}_${if (dark) "dark" else "light"}", Page.REGISTER, dark, panel = true, style)
            }
        }

    private fun pair(
        name: String,
        page: Page,
        dark: Boolean,
    ) {
        val theme = if (dark) "dark" else "light"
        snap("pull_sync_page_${name}_${theme}_before", page, dark, panel = false)
        snap("pull_sync_page_${name}_$theme", page, dark, panel = true)
    }

    private data class Shot(
        val page: Page,
        val dark: Boolean,
        val panel: Boolean,
        val style: MetroStyle,
    )

    /** The one content a test sets; each shot swaps it, keyed so the panel starts shut every time. */
    private var shot by mutableStateOf<Shot?>(null)

    private fun snap(
        name: String,
        page: Page,
        dark: Boolean,
        panel: Boolean,
        style: MetroStyle = MetroStyle.Default,
    ) {
        if (shot == null) {
            shot = Shot(page, dark, panel, style)
            compose.setContent { shot?.let { key(it) { Shown(it) } } }
        } else {
            shot = Shot(page, dark, panel, style)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("page").performTouchInput { swipeDown(startY = top + 400f, endY = bottom - 20f, durationMillis = 400) }
        compose.waitForIdle()
        if (panel) compose.onNodeWithTag("pull-sync-scrim").assertIsDisplayed()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }

    @Composable
    private fun Shown(shot: Shot) {
        val actions = ScreenBar.specsFor(shot.page.route, hasBudget = true, mode = BudgetMode.ENVELOPE).map { it.action() }
        val host = rememberFormSaveHost()
        MetroTheme(darkTheme = shot.dark, style = shot.style) {
            // A page that draws its own bar (payees) hands it to the host, as in the app.
            CompositionLocalProvider(LocalFormSaveHost provides host) {
                Column(Modifier.fillMaxSize()) {
                    val titled: @Composable (Modifier) -> Unit = { m -> SectionTitled(shot.page.title, m) { Content(shot.page) } }
                    if (shot.panel) {
                        PullSyncPanel(
                            PullSyncUi(enabled = true, status = "synced 3:42 pm", bankSync = true),
                            onSync = {},
                            onBankSync = {},
                            modifier = Modifier.weight(1f).testTag("page"),
                        ) { titled(it) }
                    } else {
                        titled(Modifier.weight(1f).testTag("page"))
                    }
                    MetroAppBar(host.bar ?: actions, false, {}, windowInsets = WindowInsets(0))
                }
            }
        }
    }

    @Composable
    private fun Content(page: Page) {
        when (page) {
            Page.REGISTER ->
                RegisterScreen(page = registerSample(), onGesture = {}, onOpen = {}, onAdd = {}, onReconcile = {})
            Page.PAYEES ->
                PayeesScreen(
                    payees = payees,
                    error = null,
                    mergeTarget = null,
                    onRename = { _, _, _, _ -> },
                    onConfirmMerge = { _, _ -> },
                    onCancelMerge = {},
                    lookalikes = PayeeLookalikes.find(payees),
                )
            Page.SETTINGS -> Settings()
        }
    }

    private val payees =
        listOf(
            PayeeRow("acme", "Acme Payroll", false, 6),
            PayeeRow("amz", "Amazon", false, 14),
            PayeeRow("cafe", "Corner Coffee", false, 9),
            PayeeRow("costco", "Costco", false, 5),
            PayeeRow("rent", "Riverside Property Management", false, 4),
            PayeeRow("shell", "Shell", false, 7),
            PayeeRow("savings", "Transfer: Savings", true, 3),
        )

    private val home = SavedInstance("i1", "http://192.168.1.20:5006", "home server")

    @Composable
    private fun Settings() {
        SettingsScreen(
            page = SettingsPage.BUDGET,
            instances = listOf(home),
            activeInstance = home,
            instanceReachable = true,
            signedIn = true,
            budgets = listOf(BudgetSummary("home", "Home", "USD")),
            openBudgetId = "home",
            themeMode = "light",
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
}
