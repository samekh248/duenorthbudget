package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountChoice
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.RegisterAccount
import app.duenorth.budget.core.RegisterCopy
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RegisterPart
import app.duenorth.budget.core.RegisterRow
import app.duenorth.budget.core.RegisterSearch
import app.duenorth.budget.core.TransactionMerge
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class RegisterScreenshotTest {
    @Test
    fun registerLight() = snapRegister("register_light", dark = false)

    @Test
    fun registerDark() = snapRegister("register_dark", dark = true)

    @Test
    fun selectLight() = snapSelect("register_select_light", dark = false)

    @Test
    fun selectDark() = snapSelect("register_select_dark", dark = true)

    @Test
    fun selectMicaLight() = snapSelect("style_mica_register_select_light", dark = false, MetroStyle.Mica)

    @Test
    fun selectMicaDark() = snapSelect("style_mica_register_select_dark", dark = true, MetroStyle.Mica)

    @Test
    fun bankLinkedLight() = snapBankLinked("register_bank_linked_light", dark = false, expanded = false)

    @Test
    fun bankLinkedDark() = snapBankLinked("register_bank_linked_dark", dark = true, expanded = false)

    @Test
    fun bankLinkedMenuLight() = snapBankLinked("register_bank_linked_menu_light", dark = false, expanded = true)

    @Test
    fun bankLinkedMenuDark() = snapBankLinked("register_bank_linked_menu_dark", dark = true, expanded = true)

    /** A bank-linked account: the register's own bar, sync among its icons. */
    private fun snapBankLinked(
        name: String,
        dark: Boolean,
        expanded: Boolean,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp).height(780.dp)) {
                    SectionTitled("ACCOUNTS", Modifier.weight(1f)) {
                        RegisterScreen(
                            page = page(),
                            onGesture = {},
                            onOpen = {},
                            onAdd = {},
                            onReconcile = {},
                        )
                    }
                    MetroAppBar(
                        actions =
                            ScreenBar
                                .specsFor(ShellRoute.Register("visa"), true, BudgetMode.ENVELOPE, bankLinked = true)
                                .map { ScreenAction(it.id, it.glyph, it.label, it.primary) {} },
                        expanded = expanded,
                        onExpandedChange = {},
                    )
                }
            }
        }
    }

    // Two picked rows of one amount, the bank's copy and the one entered by hand: merge joins the select bar.
    @Test
    fun mergeLight() = snapMerge("register_select_merge_light", dark = false)

    @Test
    fun mergeDark() = snapMerge("register_select_merge_dark", dark = true)

    private fun snapMerge(
        name: String,
        dark: Boolean,
    ) {
        val base = page()
        val coffee = base.rows.first { it.id == "coffee" }
        val bank =
            coffee.copy(
                id = "coffee-bank",
                payee = "CORNER COFFEE #42",
                date = 20261008,
                categoryId = null,
                categoryLabel = "no category",
            )
        val shown = base.copy(rows = listOf(bank) + base.rows)
        val picked = setOf("coffee", "coffee-bank")
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp).height(780.dp)) {
                    SectionTitled("ACCOUNTS", Modifier.weight(1f)) {
                        RegisterScreen(
                            page = shown,
                            onGesture = {},
                            onOpen = {},
                            onAdd = {},
                            onReconcile = {},
                            selection = picked,
                        )
                    }
                    MetroAppBar(
                        actions =
                            ScreenBar
                                .specsFor(
                                    ShellRoute.Register("visa"),
                                    hasBudget = true,
                                    mode = BudgetMode.ENVELOPE,
                                    selecting = true,
                                    registerMerge =
                                        TransactionMerge.canMerge(TransactionMerge.ofRegister(shown.rows.filter { it.id in picked })),
                                ).map { ScreenAction(it.id, it.glyph, it.label, it.primary, apart = it.apart) {} },
                        expanded = false,
                        onExpandedChange = {},
                    )
                }
            }
        }
    }

    private fun snapSelect(
        name: String,
        dark: Boolean,
        style: MetroStyle = MetroStyle.Metro,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark, style = style) {
                Column(Modifier.width(390.dp).height(780.dp)) {
                    SectionTitled("ACCOUNTS", Modifier.weight(1f)) {
                        RegisterScreen(
                            page = page(),
                            onGesture = {},
                            onOpen = {},
                            onAdd = {},
                            onReconcile = {},
                            selection = setOf("cafe", "coffee"),
                        )
                    }
                    MetroAppBar(
                        actions =
                            listOf(
                                AppBarButton(AppGlyph.Reconcile, RegisterCopy.CLEAR, onClick = {}),
                                AppBarButton(AppGlyph.Undo, RegisterCopy.UNCLEAR, onClick = {}),
                                AppBarButton(AppGlyph.Cancel, RegisterCopy.DONE, onClick = {}),
                            ).asScreenActions(),
                        expanded = false,
                        onExpandedChange = {},
                    )
                }
            }
        }
    }

    @Test
    fun searchLight() = snapSearch("register_search_light", dark = false)

    @Test
    fun searchDark() = snapSearch("register_search_dark", dark = true)

    // The relief styles on the register (plain and searching) and the transaction form.
    @Test
    fun styleRegisters() =
        MetroStyle.entries.filter { it != MetroStyle.Metro }.forEach { style ->
            for (dark in listOf(false, true)) {
                val theme = if (dark) "dark" else "light"
                snapSearch("style_${style.id}_register_$theme", dark, style, search = null)
                snapSearch("style_${style.id}_register_search_$theme", dark, style)
                snapEntry("style_${style.id}_entry_$theme", dark, style)
            }
        }

    /** The search open over the rows: "groc" keeps Corner Coffee and the split with a groceries part. */
    private fun snapSearch(
        name: String,
        dark: Boolean,
        style: MetroStyle = MetroStyle.Metro,
        search: RegisterSearch? = RegisterSearch(query = "groc"),
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark, style = style) {
                Column(Modifier.width(390.dp).height(780.dp)) {
                    SectionTitled("ACCOUNTS", Modifier.weight(1f)) {
                        RegisterScreen(
                            page = page(),
                            onGesture = {},
                            onOpen = {},
                            onAdd = {},
                            onReconcile = {},
                            search = search,
                        )
                    }
                    MetroAppBar(
                        actions =
                            ScreenBar
                                .specsFor(ShellRoute.Register("visa"), true, BudgetMode.ENVELOPE)
                                .map { ScreenAction(it.id, it.glyph, it.label, it.primary) {} },
                        expanded = false,
                        onExpandedChange = {},
                    )
                }
            }
        }
    }

    // Bank rows not posted yet under their own folding heading, then the rest with reconciled rows locked.
    @Test
    fun pendingLight() = snapPending("register_pending_light", dark = false)

    @Test
    fun pendingDark() = snapPending("register_pending_dark", dark = true)

    @Test
    fun pendingFoldedLight() = snapPending("register_pending_folded_light", dark = false, folded = true)

    private fun snapPending(
        name: String,
        dark: Boolean,
        folded: Boolean = false,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp).height(780.dp)) {
                    SectionTitled("ACCOUNTS", Modifier.weight(1f)) {
                        RegisterScreen(
                            page = pendingPage(),
                            onGesture = {},
                            onOpen = {},
                            onAdd = {},
                            onReconcile = {},
                            pendingOpen = !folded,
                        )
                    }
                    bar()
                }
            }
        }
    }

    private fun pendingPage(): RegisterPage {
        fun row(
            id: String,
            date: Int,
            payee: String,
            category: String?,
            amount: Long,
            cleared: Boolean = true,
            reconciled: Boolean = false,
            pending: Boolean = false,
        ) = RegisterRow(
            id = id,
            date = date,
            payee = payee,
            categoryId = category?.let { "c-$it" },
            categoryLabel = category ?: "no category",
            amountMinor = amount,
            note = "",
            reconciled = reconciled,
            transferId = null,
            partnerOffBudget = null,
            partnerCategoryId = null,
            parent = false,
            parts = emptyList(),
            cleared = cleared,
            pending = pending,
        )
        return page().copy(
            account = RegisterAccount("checking", "checking", offBudget = false, type = "checking", closed = false),
            balanceMinor = 31_870,
            rows =
                listOf(
                    row("p-stream", 20261031, "Streamer", null, -1_599, cleared = false, pending = true),
                    row("p-gas", 20261030, "Shell Oil 5521", "Gas", -4_812, cleared = false, pending = true),
                    row("p-coffee", 20261030, "Corner Coffee", "Groceries", -575, cleared = false, pending = true),
                    row("market", 20261029, "Riverside Market", "Groceries", -8_420),
                    row("power", 20261024, "City Power", "Utilities", -9_310, reconciled = true),
                    row("rent", 20261020, "Riverside Property Management", "Household", -145_000, reconciled = true),
                    row("bus", 20261018, "Metro Transit", "Transit", -250, reconciled = true),
                ),
        )
    }

    @Test
    fun entryLight() = snapEntry("entry_light", dark = false)

    @Test
    fun entryDark() = snapEntry("entry_dark", dark = true)

    private fun snapRegister(
        name: String,
        dark: Boolean,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp).height(780.dp)) {
                    SectionTitled("ACCOUNTS", Modifier.weight(1f)) {
                        RegisterScreen(
                            page = page(),
                            onGesture = {},
                            onOpen = {},
                            onAdd = {},
                            onReconcile = {},
                        )
                    }
                    bar()
                }
            }
        }
    }

    private fun snapEntry(
        name: String,
        dark: Boolean,
        style: MetroStyle = MetroStyle.Metro,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark, style = style) {
                Column(Modifier.width(390.dp).height(780.dp)) {
                    TransactionForm(
                        page = page(),
                        row = page().rows.last(),
                        initialDate = "2026-10-07",
                        error = null,
                        onSave = {},
                        modifier = Modifier.weight(1f),
                    )
                    bar()
                }
            }
        }
    }

    @Composable
    private fun bar() {
        MetroAppBar(
            actions =
                listOf(
                    AppBarButton(AppGlyph.Budgets, "budgets", onClick = {}),
                    AppBarButton(AppGlyph.Appearance, "appearance", onClick = {}),
                ).asScreenActions(),
            expanded = false,
            onExpandedChange = {},
        )
    }

    private fun page() =
        RegisterPage(
            account = RegisterAccount("visa", "visa", offBudget = false, type = "credit", closed = false),
            balanceMinor = -4_240,
            currency = Currencies.byCode("USD")!!,
            rows =
                listOf(
                    RegisterRow(
                        id = "cafe",
                        date = 20261007,
                        payee = "Cafe",
                        categoryId = null,
                        categoryLabel = "split",
                        amountMinor = -3_000,
                        note = "",
                        reconciled = false,
                        transferId = null,
                        partnerOffBudget = null,
                        partnerCategoryId = null,
                        parent = true,
                        cleared = false,
                        parts =
                            listOf(
                                RegisterPart("a", "c-food", "Groceries", -2_000),
                                RegisterPart("b", "c-house", "Household", -1_000),
                            ),
                    ),
                    RegisterRow(
                        id = "coffee",
                        date = 20261007,
                        payee = "Corner Coffee",
                        categoryId = "c-food",
                        categoryLabel = "Groceries",
                        amountMinor = -575,
                        note = "",
                        reconciled = false,
                        transferId = null,
                        partnerOffBudget = null,
                        partnerCategoryId = null,
                        parent = false,
                        cleared = false,
                        parts = emptyList(),
                    ),
                    RegisterRow(
                        id = "payment",
                        date = 20261005,
                        payee = "transfer from Checking",
                        categoryId = null,
                        categoryLabel = "transfer",
                        amountMinor = 20_000,
                        note = "autopay",
                        reconciled = true,
                        transferId = "t-1",
                        partnerOffBudget = false,
                        partnerCategoryId = null,
                        parent = false,
                        cleared = true,
                        parts = emptyList(),
                        transferAccountId = "checking",
                    ),
                    RegisterRow(
                        id = "rent",
                        date = 20261003,
                        payee = "Riverside Property Management",
                        categoryId = "c-house",
                        categoryLabel = "Household",
                        amountMinor = -1_000,
                        note = "October rent, late fee waived",
                        reconciled = true,
                        transferId = null,
                        partnerOffBudget = null,
                        partnerCategoryId = null,
                        parent = false,
                        cleared = true,
                        parts = emptyList(),
                    ),
                    RegisterRow(
                        id = "bus",
                        date = 20261001,
                        payee = "Bus",
                        categoryId = "c-food",
                        categoryLabel = "Groceries",
                        amountMinor = -1_240,
                        note = "",
                        reconciled = true,
                        transferId = null,
                        partnerOffBudget = null,
                        partnerCategoryId = null,
                        parent = false,
                        cleared = true,
                        parts = emptyList(),
                    ),
                ),
            categories =
                listOf(
                    CategoryChoice("c-food", "Groceries", "Living", income = false),
                    CategoryChoice("c-house", "Household", "Living", income = false),
                ),
            accounts = listOf(AccountChoice("checking", "Checking", false, "checking", false)),
        )
}
