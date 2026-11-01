package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.ReconcileCopy
import app.duenorth.budget.core.ReconcilePage
import app.duenorth.budget.core.ReconcileRow
import app.duenorth.budget.core.RegisterAccount
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RegisterRow
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Reconcile on the register: the inline start, the header, the hints, and finishing while off. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class ReconcileRegisterTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun startBeginsAtTheClearedBalanceAndSendsIt() {
        var started: Pair<String, String>? = null
        val host = show(page = null, onStart = { b, d -> started = b to d })
        compose.onNodeWithTag("reconcile-start").assertIsDisplayed()
        compose.onNodeWithText("3,058.60").assertIsDisplayed()
        compose.runOnIdle { host.bar!!.first { it.id == "reconcile-start" }.run() }
        assertEquals("3058.60" to "2026-09-30", started)
    }

    @Test
    fun tappingARowTogglesItsClearedMark() {
        val toggled = mutableListOf<String>()
        show(page = off(), onToggle = { toggled += it })
        compose.onNodeWithTag("register-row-shell").performClick()
        assertEquals(listOf("shell"), toggled)
    }

    @Test
    fun anExactMatchIsOutlinedAndClearedFromTheHint() {
        val cleared = mutableListOf<List<String>>()
        show(page = off(), onSetCleared = { ids, on -> if (on) cleared += ids })
        compose.onNodeWithTag("reconcile-hinted-shell", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("hint-clear-shell").performClick()
        assertEquals(listOf(listOf("shell")), cleared)
    }

    @Test
    fun withoutAnExactMatchTheCluesShow() {
        val cleared = mutableListOf<List<String>>()
        var added = false
        show(page = clues(), onSetCleared = { ids, _ -> cleared += ids }, onAddMissing = { added = true })
        compose.onNodeWithTag("hint-pair").performClick()
        compose.onNodeWithTag("hint-missing").performClick()
        assertEquals(listOf(listOf("spotify", "parking")), cleared)
        assertEquals(true, added)
    }

    @Test
    fun finishWhileOffAsksBeforeAddingAnAdjustment() {
        val finished = mutableListOf<Boolean>()
        val host = show(page = off(), onFinish = { finished += it })
        compose.runOnIdle { host.bar!!.first { it.id == "reconcile-finish" }.run() }
        compose.onNodeWithTag("reconcile-adjust-panel").assertIsDisplayed()
        compose.onNodeWithTag("adjust-amount", useUnmergedTree = true).assertTextEquals("-$42.17")
        assertEquals(emptyList<Boolean>(), finished)
        compose.runOnIdle { host.bar!!.first { it.id == "reconcile-adjust" }.run() }
        assertEquals(listOf(true), finished)
    }

    @Test
    fun balancedFinishesStraightAway() {
        val finished = mutableListOf<Boolean>()
        val host = show(page = balanced(), onFinish = { finished += it })
        compose.onNodeWithTag("reconcile-balanced").assertIsDisplayed()
        compose.onNodeWithText("${ReconcileCopy.FINISH_LOCKS} 4").assertIsDisplayed()
        compose.runOnIdle { host.bar!!.first { it.id == "reconcile-finish" }.run() }
        assertEquals(listOf(false), finished)
    }

    @Test
    fun lockedRowsShowUnderTheirOwnHeadingAndFoldOnATap() {
        show(page = off())
        compose.onNodeWithTag("reconcile-list").performScrollToNode(hasTestTag("register-row-old"))
        compose.onNodeWithTag("register-row-old").assertExists()
        compose.onNodeWithTag("locked-heading").performClick()
        compose.onNodeWithTag("register-row-old").assertDoesNotExist()
    }

    @Test
    fun theStripCountsLockedClearedAndNotCleared() {
        show(page = off())
        compose.onNodeWithText("locked thru aug 30").assertIsDisplayed()
        compose.onNodeWithTag("strip-cleared").assertIsDisplayed()
    }

    @Test
    fun aSignFlipLeadIsFirstAndFlipsTheRow() {
        val flipped = mutableListOf<String>()
        show(page = leads(), register = richRegister(), onFlip = { flipped += it })
        compose.onNodeWithTag("hint-flip-leaves", useUnmergedTree = true).assertTextEquals("→ $0.00")
        compose.onNodeWithTag("hint-flip").performClick()
        assertEquals(listOf("amazon"), flipped)
        compose.onNodeWithTag("hint-duplicate").assertIsDisplayed()
        compose.onNodeWithTag("hint-stale").assertIsDisplayed()
    }

    @Test
    fun addItStartsTheNewTransactionAtTheDifference() {
        compose.setContent {
            MetroTheme {
                CompositionLocalProvider(LocalFormSaveHost provides FormSaveHost()) {
                    Box(Modifier.width(390.dp).height(780.dp)) {
                        TransactionForm(
                            page = register(),
                            row = null,
                            initialDate = "2026-09-30",
                            error = null,
                            onSave = {},
                            initialAmountMinor = -4_217,
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("42.17").assertIsDisplayed()
        compose.onNodeWithText("Wed 09/30/2026").assertIsDisplayed()
    }

    @Test fun snapStartLight() = snap("reconcile_start_light", null, dark = false)

    @Test fun snapStartDark() = snap("reconcile_start_dark", null, dark = true)

    @Test fun snapExactLight() = snap("reconcile_exact_light", off(), dark = false)

    @Test fun snapExactDark() = snap("reconcile_exact_dark", off(), dark = true)

    @Test fun snapCluesLight() = snap("reconcile_clues_light", clues(), dark = false)

    @Test fun snapCluesDark() = snap("reconcile_clues_dark", clues(), dark = true)

    @Test fun snapAdjustLight() = snap("reconcile_adjust_light", off(), dark = false, finish = true)

    @Test fun snapAdjustDark() = snap("reconcile_adjust_dark", off(), dark = true, finish = true)

    @Test fun snapLeadsLight() = snap("reconcile_leads_light", leads(), dark = false, register = richRegister())

    @Test fun snapLeadsDark() = snap("reconcile_leads_dark", leads(), dark = true, register = richRegister())

    @Test fun snapLockedLight() =
        snap(
            "reconcile_locked_light",
            leads(),
            dark = false,
            register = richRegister(),
            scrollTo = "locked-heading",
        )

    @Test fun snapLockedDark() = snap("reconcile_locked_dark", leads(), dark = true, register = richRegister(), scrollTo = "locked-heading")

    @Test fun snapBalancedLight() = snap("reconcile_balanced_light", balanced(), dark = false)

    @Test fun snapBalancedDark() = snap("reconcile_balanced_dark", balanced(), dark = true)

    private fun show(
        page: ReconcilePage?,
        onStart: (String, String) -> Unit = { _, _ -> },
        onToggle: (String) -> Unit = {},
        onSetCleared: (List<String>, Boolean) -> Unit = { _, _ -> },
        onAddMissing: () -> Unit = {},
        onFinish: (Boolean) -> Unit = {},
        register: RegisterPage = register(),
        onFlip: (String) -> Unit = {},
    ): FormSaveHost {
        val host = FormSaveHost()
        compose.setContent {
            MetroTheme {
                CompositionLocalProvider(LocalFormSaveHost provides host) {
                    Box(Modifier.width(390.dp).height(780.dp)) {
                        screen(page, onStart, onToggle, onSetCleared, onAddMissing, onFinish, register = register, onFlip = onFlip)
                    }
                }
            }
        }
        return host
    }

    @Composable
    private fun screen(
        page: ReconcilePage?,
        onStart: (String, String) -> Unit = { _, _ -> },
        onToggle: (String) -> Unit = {},
        onSetCleared: (List<String>, Boolean) -> Unit = { _, _ -> },
        onAddMissing: () -> Unit = {},
        onFinish: (Boolean) -> Unit = {},
        modifier: Modifier = Modifier,
        register: RegisterPage = register(),
        onFlip: (String) -> Unit = {},
    ) {
        ReconcileRegisterScreen(
            register = register,
            page = page,
            error = null,
            onGesture = {},
            onStart = onStart,
            onToggle = onToggle,
            onSetCleared = onSetCleared,
            onOpen = {},
            onAddMissing = onAddMissing,
            onFinish = onFinish,
            onCancel = {},
            onFlip = onFlip,
            modifier = modifier,
            today = 20260930,
        )
    }

    private fun snap(
        name: String,
        page: ReconcilePage?,
        dark: Boolean,
        finish: Boolean = false,
        register: RegisterPage = register(),
        scrollTo: String? = null,
    ) {
        val host = FormSaveHost()
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalFormSaveHost provides host) {
                    Column(Modifier.width(390.dp).height(780.dp)) {
                        SectionTitled("ACCOUNTS", Modifier.weight(1f)) { screen(page, register = register) }
                        MetroAppBar(host.bar.orEmpty(), false, {}, windowInsets = WindowInsets(0))
                    }
                }
            }
        }
        if (finish) compose.runOnIdle { host.bar!!.first { it.id == "reconcile-finish" }.run() }
        if (scrollTo != null) compose.onNodeWithTag("reconcile-list").performScrollToNode(hasTestTag(scrollTo))
        compose.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun register() =
        RegisterPage(
            account = RegisterAccount("checking", "Checking", offBudget = false, type = "checking", closed = false),
            balanceMinor = 232_179,
            currency = Currencies.byCode("USD")!!,
            rows =
                listOf(
                    line("netflix", 20261002, "Netflix", "Subscriptions", -1_549),
                    line("shell", 20260929, "Shell", "Gas", -4_217),
                    line("spotify", 20260929, "Spotify", "Subscriptions", -1_099),
                    line("parking", 20260929, "Parking", "Transport", -450),
                    line("employer", 20260928, "Employer", "Paycheck", 155_000, cleared = true),
                    line("safeway", 20260926, "Safeway", "Groceries", -8_634, cleared = true),
                    line("water", 20260922, "City Water", "Utilities", -6_120, cleared = true),
                    line("old", 20260830, "Employer", "Paycheck", 175_000, cleared = true, reconciled = true),
                    line("old2", 20260828, "Safeway", "Groceries", -9_386, cleared = true, reconciled = true),
                ),
            categories = emptyList(),
            accounts = emptyList(),
        )

    /** The mockup's account: a refund entered the wrong way round, a doubled Safeway, an old check, and locked rows. */
    private fun richRegister() =
        RegisterPage(
            account = RegisterAccount("checking", "Checking", offBudget = false, type = "checking", closed = false),
            balanceMinor = 0,
            currency = Currencies.byCode("USD")!!,
            rows =
                listOf(
                    line("netflix", 20261002, "Netflix", "Subscriptions", -1_549),
                    line("shell", 20260929, "Shell", "Gas", -4_217, cleared = true),
                    line("spotify", 20260929, "Spotify", "Subscriptions", -1_099),
                    line("amazon", 20260928, "Amazon", "Refund", 2_883, cleared = true),
                    line("employer", 20260928, "Employer", "Paycheck", 155_000, cleared = true),
                    line("safeway", 20260926, "Safeway", "Groceries", -8_634, cleared = true),
                    line("safeway2", 20260926, "Safeway", "Groceries", -8_634),
                    line("water", 20260922, "City Water", "Utilities", -6_120, cleared = true),
                    line("check", 20260714, "Check #1043", "Rent share", -12_000),
                    line("old", 20260830, "Employer", "Paycheck", 175_000, cleared = true, reconciled = true),
                    line("old2", 20260828, "Safeway", "Groceries", -9_386, cleared = true, reconciled = true),
                    line("old3", 20260820, "Comcast", "Internet", -7_999, cleared = true, reconciled = true),
                    line("old4", 20260815, "Employer", "Paycheck", 175_000, cleared = true, reconciled = true),
                ),
            categories = emptyList(),
            accounts = emptyList(),
        )

    /** Off by -57.66: twice the Amazon refund, so flipping it balances. */
    private fun leads(): ReconcilePage {
        val rich = richRegister()
        val cleared = rich.rows.filter { it.cleared }.sumOf { it.amountMinor }
        return page(
            cleared - 5_766,
            rich.rows
                .filter { it.cleared }
                .map { it.id }
                .toSet(),
            rich,
        )
    }

    private fun line(
        id: String,
        date: Int,
        payee: String,
        category: String,
        amount: Long,
        cleared: Boolean = false,
        reconciled: Boolean = false,
    ) = RegisterRow(
        id = id,
        date = date,
        payee = payee,
        categoryId = null,
        categoryLabel = category,
        amountMinor = amount,
        note = "",
        reconciled = reconciled,
        transferId = null,
        partnerOffBudget = null,
        partnerCategoryId = null,
        parent = false,
        parts = emptyList(),
        cleared = cleared,
    )

    /** The register's rows as the reconcile page sees them, with [cleared] ids marked. */
    private fun page(
        statement: Long,
        cleared: Set<String>,
        register: RegisterPage = register(),
    ): ReconcilePage {
        val rows =
            register.rows.map {
                ReconcileRow(it.id, it.date, it.payee, it.amountMinor, it.id in cleared || it.reconciled, it.reconciled)
            }
        val total = rows.filter { it.cleared }.sumOf { it.amountMinor }
        return ReconcilePage(
            account = register.account,
            currency = Currencies.byCode("USD")!!,
            statementMinor = statement,
            statementDate = 20260930,
            clearedTotalMinor = total,
            differenceMinor = statement - total,
            rows = rows,
            active = true,
        )
    }

    // Cleared so far: 1,550.00 - 86.34 - 61.20 + 1,750.00 - 93.86 = 3,058.60.
    private fun off() = page(301_643, setOf("employer", "safeway", "water"))

    private fun clues() = page(300_094, setOf("employer", "safeway", "water", "shell"))

    private fun balanced() = page(301_643, setOf("employer", "safeway", "water", "shell"))
}
