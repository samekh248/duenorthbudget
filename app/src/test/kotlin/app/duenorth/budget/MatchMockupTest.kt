package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountChoice
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.RegisterAccount
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RegisterRow
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroPivot
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.PivotItem
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroTheme
import app.duenorth.budget.design.theme.MetroWeights
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Design mockups for matching two transactions that are the same purchase: the one the bank
 * sent and the one entered by hand. Three ideas, each shown in the inbox and on an account's
 * register: a pair panel that shows both and asks, a row that is already merged and waits for
 * approval, and a review page that compares the two field by field. Mock data only; renders
 * frames to app/build/match-mockups.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class MatchMockupTest {
    @Test fun pairInbox() = snap("a-inbox") { PairInbox() }

    @Test fun pairRegister() = snap("a-register") { PairRegister() }

    @Test fun mergedInbox() = snap("b-inbox") { MergedInbox() }

    @Test fun mergedRegister() = snap("b-register") { MergedRegister() }

    @Test fun reviewInbox() = snap("c-inbox") { ReviewInbox() }

    @Test fun reviewRegister() = snap("c-register") { ReviewRegister() }

    @Test fun reviewPage() = snap("c-compare") { ComparePage() }

    @Test fun afterRegister() = snap("z-after-register") { AfterRegister() }

    private fun snap(
        name: String,
        content: @Composable () -> Unit,
    ) = captureRoboImage("build/match-mockups/$name.png") {
        MetroTheme(darkTheme = false) {
            Box(Modifier.width(390.dp).height(780.dp).background(Metro.colors.background)) { content() }
        }
    }
}

/** The single transaction left after a match, on the real details page, its note carrying the evidence. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h780dp-xxhdpi")
class MatchAfterMockupTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun details() {
        val page = page(listOf(merged))
        compose.setContent {
            CompositionLocalProvider(LocalFormSaveHost provides FormSaveHost()) {
                MetroTheme(darkTheme = false) {
                    Column(Modifier.width(390.dp).height(780.dp)) {
                        SectionTitled("ACCOUNTS", Modifier.weight(1f)) {
                            TransactionForm(page = page, row = merged, initialDate = "2026-10-15", error = null, onSave = {})
                        }
                        MetroAppBar(
                            actions =
                                ScreenBar
                                    .specsFor(ShellRoute.EditTransaction("visa", merged.id), true, BudgetMode.ENVELOPE, transaction = TransactionMenu.ORDINARY)
                                    .map { ScreenAction(it.id, it.glyph, it.label, it.primary) {} },
                            expanded = false,
                            onExpandedChange = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/match-mockups/z-after-details.png")
    }
}

// --- mock data ------------------------------------------------------------------------------

private val usd = Currencies.byCode("USD")!!

private const val MATCH_NOTE = "with Sam\nMatched 10/18: bank CORNER COFFEE #42 (10/15) + entered Corner Coffee (10/14)"

private fun row(
    id: String,
    date: Int,
    payee: String,
    category: String,
    amount: Long,
    note: String = "",
    cleared: Boolean = false,
) = RegisterRow(
    id = id,
    date = date,
    payee = payee,
    categoryId = if (category.isBlank()) null else "c-${category.lowercase()}",
    categoryLabel = category,
    amountMinor = amount,
    note = note,
    reconciled = false,
    transferId = null,
    partnerOffBudget = null,
    partnerCategoryId = null,
    parent = false,
    parts = emptyList(),
    cleared = cleared,
)

/** What the bank sent: cleared, its own payee text, no category yet. */
private val bank = row("bank", 20261015, "CORNER COFFEE #42", "", -575, cleared = true)

/** What was entered by hand the day before: a clean payee, a category and a note. */
private val mine = row("mine", 20261014, "Corner Coffee", "Dining", -575, note = "with Sam")

/** The one transaction a match leaves: the bank's id, date and cleared state; the entry's payee, category and note. */
private val merged = row("bank", 20261015, "Corner Coffee", "Dining", -575, note = MATCH_NOTE, cleared = true)

private val others =
    listOf(
        row("tj", 20261012, "Trader Joe's", "Groceries", -8_420, cleared = true),
        row("bus", 20261010, "Bus", "Transit", -1_240, note = "monthly pass top-up", cleared = true),
        row("pay", 20261005, "Payment from Checking", "transfer", 20_000, note = "autopay", cleared = true),
    )

private fun page(rows: List<RegisterRow>) =
    RegisterPage(
        account = RegisterAccount("visa", "Visa", offBudget = false, type = "credit", closed = false),
        balanceMinor = -4_815,
        currency = usd,
        rows = rows,
        categories =
            listOf(
                CategoryChoice("c-dining", "Dining", "Fun", income = false),
                CategoryChoice("c-groceries", "Groceries", "Living", income = false),
                CategoryChoice("c-transit", "Transit", "Living", income = false),
            ),
        accounts = listOf(AccountChoice("checking", "Checking", offBudget = false, type = "checking", closed = false)),
    )

private data class Waiting(
    val date: Int,
    val payee: String,
    val detail: String,
    val amount: Long,
)

private val queue =
    listOf(
        Waiting(20261015, "Amazon", "Household · Visa · 2 items", -6_318),
        Waiting(20261014, "Shell", "Gas · Checking", -4_210),
        Waiting(20261014, "Acme Payroll", "Paycheck · Checking", 185_000),
    )

@Composable
private fun money(minor: Long) = MoneyFormat.format(minor, usd, formatOptions())

private fun day(date: Int) = RegisterEntry.registerDateLabel(date)

// --- shared chrome -------------------------------------------------------------------------

/** The home pivot on its inbox item, with the home bar along the bottom. */
@Composable
private fun InboxShell(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        MetroPivot(
            title = SectionTitleCopy.APP,
            items =
                HomeSections.headers.map { header ->
                    PivotItem(header) {
                        if (header == HomeSections.INBOX) {
                            Column(Modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) { content() }
                        }
                    }
                },
            initialItem = HomeSections.headers.indexOf(HomeSections.INBOX),
            modifier = Modifier.weight(1f),
        )
        MetroAppBar(
            actions =
                listOf(
                    ScreenAction("add", AppGlyph.Add, "add") {},
                    ScreenAction("select", AppGlyph.Select, "select") {},
                ),
            expanded = false,
            onExpandedChange = {},
        )
    }
}

/** The inbox's two totals, with the second one free to change per concept. */
@Composable
private fun InboxTotals(
    count: Int,
    right: @Composable () -> Unit = { BudgetTotal("spending", money(-11_103), Metro.colors.foreground, "s", alignEnd = true) },
) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        BudgetTotal("to categorize", "$count", Metro.accent.text, "c", modifier = Modifier.weight(1f))
        Box(Modifier.weight(1f), contentAlignment = Alignment.TopEnd) { right() }
    }
}

/** The inbox queue under the featured panel: date headings and banded rows, as the real one draws them. */
@Composable
private fun InboxQueue(rows: List<Waiting> = queue) {
    var last = 0
    rows.forEachIndexed { i, w ->
        if (w.date != last) {
            MetroText(day(w.date), Metro.typography.caption, Modifier.padding(top = 12.dp, bottom = 4.dp), color = Metro.accent.text)
            last = w.date
        }
        InboxLineMock(w.payee, w.detail, money(w.amount), banded = ledgerBanded(i))
    }
}

@Composable
private fun InboxLineMock(
    payee: String,
    detail: String,
    amount: String,
    banded: Boolean,
    detailColor: Color = Metro.colors.secondary,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .ledgerFill(if (banded) ledgerBand else Color.Transparent)
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(LEDGER_NAME + LEDGER_FIGURE).padding(end = MetroDimens.Gutter)) {
            MetroText(payee, Metro.typography.body, maxLines = 2)
            MetroText(detail, Metro.typography.caption, color = detailColor, maxLines = 2)
        }
        MetroText(
            amount,
            Metro.typography.body.copy(textAlign = TextAlign.End),
            Modifier.weight(LEDGER_FIGURE * 2),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The featured panel's chrome-filled slab, running past the gutter like the real one. */
@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 28.dp)
            .ledgerFill(Metro.colors.chrome)
            .padding(vertical = 20.dp),
        content = content,
    )
}

/** The one filled accent button on the page, the same as the inbox's accept. */
@Composable
private fun FilledButton(label: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.ButtonMin)
            .border(MetroDimens.Stroke, Metro.accent.fill)
            .background(Metro.accent.fill)
            .padding(horizontal = 9.5.dp, vertical = 3.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(label, Metro.typography.body.copy(fontWeight = MetroWeights.Semibold), color = Metro.accent.onFill)
    }
}

@Composable
private fun TwoButtons(
    primary: String,
    secondary: String,
    modifier: Modifier = Modifier,
) {
    MetroButtonGrid(modifier.padding(top = 12.dp)) {
        FilledButton(primary)
        MetroButton(secondary) {}
    }
}

/** An account's register page under the ACCOUNTS title, with its own bar. */
@Composable
private fun RegisterShell(
    balance: Long = -4_815,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SectionTitled("ACCOUNTS", Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) {
                MetroText("Visa", Metro.typography.header, maxLines = 1)
                BudgetTotal("balance owed", money(balance), Metro.accent.text, "b", modifier = Modifier.padding(top = MetroDimens.Gutter))
                content()
            }
        }
        MetroAppBar(
            actions =
                ScreenBar
                    .specsFor(ShellRoute.Register("visa"), true, BudgetMode.ENVELOPE, bankLinked = true)
                    .map { ScreenAction(it.id, it.glyph, it.label, it.primary) {} },
            expanded = false,
            onExpandedChange = {},
        )
    }
}

private enum class Bar { Cleared, Open, Pending }

/** A register row as the real one draws it: payee, quieter detail, accent amount, the cleared bar at the right edge. */
@Composable
private fun RegisterLineMock(
    r: RegisterRow,
    banded: Boolean,
    bar: Bar = if (r.cleared) Bar.Cleared else Bar.Open,
    detail: String = registerDetail(r),
    extra: (@Composable () -> Unit)? = null,
    fill: Color? = null,
) {
    val accent = Metro.accent.text
    val faint = Metro.colors.secondary.copy(alpha = 0.22f)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .ledgerFill(fill ?: if (banded) ledgerBand else Color.Transparent)
            .drawBehind {
                val gutter = MetroDimens.Gutter.toPx()
                val inset = 6.dp.toPx()
                val w = 16.dp.toPx()
                val left = size.width + gutter - 2.dp.toPx() - w
                val top = Offset(left, inset)
                val s = Size(w, size.height - 2 * inset)
                when (bar) {
                    Bar.Cleared -> drawRect(accent, top, s)
                    Bar.Open -> drawRect(faint, top, s)
                    Bar.Pending -> {
                        val stroke = 2.dp.toPx()
                        drawRect(
                            accent,
                            Offset(left + stroke / 2, inset + stroke / 2),
                            Size(w - stroke, s.height - stroke),
                            style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                        )
                    }
                }
            }.padding(vertical = 15.dp)
            .padding(end = 16.dp + 2.dp + 6.dp - MetroDimens.Gutter),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(LEDGER_NAME + LEDGER_FIGURE).padding(end = MetroDimens.Gutter)) {
                MetroText(r.payee, Metro.typography.body, maxLines = 2)
                if (detail.isNotBlank()) MetroText(detail, Metro.typography.caption, color = Metro.colors.secondary, maxLines = 2)
            }
            MetroText(
                money(r.amountMinor),
                Metro.typography.body.copy(textAlign = TextAlign.End),
                Modifier.weight(LEDGER_FIGURE * 2),
                color = accent,
                maxLines = 1,
                softWrap = false,
            )
        }
        extra?.invoke()
    }
}

@Composable
private fun DayHeading(
    text: String,
    color: Color = Metro.accent.text,
) {
    MetroText(text, Metro.typography.caption, Modifier.padding(top = 16.dp, bottom = 4.dp), color = color)
}

/** The rest of the register under whatever the concept puts at the top. */
@Composable
private fun RestOfRegister(startIndex: Int) {
    var last = 0
    others.forEachIndexed { i, r ->
        if (r.date != last) {
            DayHeading(day(r.date))
            last = r.date
        }
        RegisterLineMock(r, banded = ledgerBanded(startIndex + i))
    }
}

// --- A: pair panel -------------------------------------------------------------------------

/** One side of a pair: where it came from, then the transaction as a compact two-line row. */
@Composable
private fun PairSide(
    source: String,
    r: RegisterRow,
    detail: String,
) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(end = MetroDimens.Gutter)) {
            MetroText(source, Metro.typography.caption, color = Metro.accent.text)
            MetroText(r.payee, Metro.typography.body.copy(fontWeight = MetroWeights.Semibold), maxLines = 1)
            MetroText(detail, Metro.typography.caption, color = Metro.colors.secondary, maxLines = 1)
        }
        MetroText(money(r.amountMinor), Metro.typography.body, Modifier.padding(top = 18.dp), maxLines = 1, softWrap = false)
    }
}

/** The link between the two sides: a short accent rule with "same purchase?" beside it. */
@Composable
private fun PairLink() {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(22.dp).background(Metro.accent.fill))
        MetroText("same amount · 1 day apart · same account", Metro.typography.caption, Modifier.padding(start = 10.dp), color = Metro.colors.secondary)
    }
}

@Composable
private fun PairInbox() {
    InboxShell {
        InboxTotals(4) { BudgetTotal("to match", "1", Metro.colors.foreground, "m", alignEnd = true) }
        Panel {
            MetroText("possible match", Metro.typography.groupHeader, color = Metro.accent.text)
            PairSide("from the bank", bank, "${day(bank.date)} · Visa · no category")
            PairLink()
            PairSide("entered by you", mine, "${day(mine.date)} · Dining · with Sam")
            TwoButtons("match", "not a match")
        }
        InboxQueue()
    }
}

@Composable
private fun PairRegister() {
    RegisterShell {
        Spacer(Modifier.height(MetroDimens.Grid))
        DayHeading("possible match")
        Column(Modifier.fillMaxWidth().ledgerFill(Metro.accent.text.copy(alpha = 0.08f)).padding(bottom = 14.dp)) {
            // An accent bracket down the left edge ties the two rows together.
            Row {
                Box(Modifier.padding(top = 6.dp).width(3.dp).height(138.dp).background(Metro.accent.fill))
                Column(Modifier.padding(start = 10.dp)) {
                    RegisterLineMock(bank, banded = false, detail = "${day(bank.date)} · from the bank", fill = Color.Transparent)
                    RegisterLineMock(mine, banded = false, detail = "${day(mine.date)} · Dining · with Sam", fill = Color.Transparent)
                }
            }
            TwoButtons("match", "not a match", Modifier.padding(end = 0.dp))
        }
        RestOfRegister(startIndex = 1)
    }
}

// --- B: already merged, waiting for approval ------------------------------------------------

@Composable
private fun MergedInbox() {
    InboxShell {
        InboxTotals(4)
        Panel {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(end = MetroDimens.Gutter)) {
                    MetroText(day(merged.date), Metro.typography.caption, color = Metro.accent.text)
                    MetroText(merged.payee, Metro.typography.groupHeader, maxLines = 2)
                    MetroText("Visa · with Sam", Metro.typography.caption, color = Metro.colors.secondary)
                }
                MetroText(money(merged.amountMinor), Metro.typography.groupHeader, color = Metro.accent.text, maxLines = 1, softWrap = false)
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                MetroText("matched ", Metro.typography.caption, color = Metro.colors.secondary)
                MetroText("your entry from oct 14", Metro.typography.body.copy(fontWeight = MetroWeights.Semibold))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetroText("category ", Metro.typography.caption, color = Metro.colors.secondary)
                MetroText("Dining", Metro.typography.body.copy(fontWeight = MetroWeights.Semibold))
            }
            TwoButtons("approve", "unmatch")
        }
        InboxQueue()
    }
}

@Composable
private fun MergedRegister() {
    RegisterShell {
        // A slim strip under the balance while matches wait, with approve all.
        Row(
            Modifier.fillMaxWidth().padding(top = MetroDimens.Grid).ledgerFill(Metro.colors.chrome).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetroText("1 match to approve", Metro.typography.body, Modifier.weight(1f))
            MetroText("approve all", Metro.typography.body.copy(fontWeight = MetroWeights.Semibold), color = Metro.accent.text)
        }
        DayHeading(day(merged.date))
        RegisterLineMock(
            merged.copy(note = "with Sam"),
            banded = false,
            bar = Bar.Pending,
            extra = {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetroText("matched with oct 14 entry · ", Metro.typography.caption, color = Metro.accent.text)
                    MetroText("tap to approve", Metro.typography.caption.copy(fontWeight = FontWeight.SemiBold), color = Metro.accent.text)
                }
            },
        )
        RestOfRegister(startIndex = 1)
    }
}

// --- C: review page ------------------------------------------------------------------------

/** The full-width accent tile that leads to the review page. */
@Composable
private fun MatchTile(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().ledgerFill(Metro.accent.fill).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            MetroText("1 possible match", Metro.typography.groupHeader, color = Metro.accent.onFill)
            MetroText("Corner Coffee · -\$5.75", Metro.typography.caption, color = Metro.accent.onFill.copy(alpha = 0.8f))
        }
        MetroText("review", Metro.typography.body.copy(fontWeight = MetroWeights.Semibold), color = Metro.accent.onFill)
    }
}

@Composable
private fun ReviewInbox() {
    InboxShell {
        InboxTotals(4)
        MatchTile(Modifier.padding(top = 20.dp))
        Panel(Modifier.padding(top = 0.dp)) {
            MetroText(day(20261015), Metro.typography.caption, color = Metro.accent.text)
            Row(verticalAlignment = Alignment.Top) {
                MetroText("Amazon", Metro.typography.groupHeader, Modifier.weight(1f))
                MetroText(money(-6_318), Metro.typography.groupHeader, color = Metro.accent.text)
            }
            MetroText("Visa · 2 items", Metro.typography.caption, color = Metro.colors.secondary)
            Row(Modifier.padding(top = 10.dp)) {
                MetroText("suggested ", Metro.typography.caption, color = Metro.colors.secondary)
                MetroText("Household", Metro.typography.body.copy(fontWeight = MetroWeights.Semibold))
            }
            TwoButtons("accept", "categorize")
        }
        InboxQueue(queue.drop(1))
    }
}

@Composable
private fun ReviewRegister() {
    RegisterShell {
        MatchTile(Modifier.padding(top = MetroDimens.Grid))
        DayHeading(day(bank.date))
        RegisterLineMock(bank, banded = false, detail = "no category")
        DayHeading(day(mine.date))
        RegisterLineMock(mine, banded = true)
        RestOfRegister(startIndex = 2)
    }
}

/** One field of the compare page: its name, then the bank's value and yours, the kept one outlined in accent. */
@Composable
private fun CompareRow(
    field: String,
    bankValue: String,
    yours: String,
    keep: Int,
) {
    MetroText(field, Metro.typography.caption, Modifier.padding(top = 14.dp, bottom = 4.dp), color = Metro.colors.secondary)
    if (bankValue == yours) {
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            MetroText("$yours  ·  same on both", Metro.typography.body)
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)) {
        listOf(bankValue, yours).forEachIndexed { i, v -> Choice(v, i == keep, Modifier.weight(1f)) }
    }
}

@Composable
private fun Choice(
    value: String,
    kept: Boolean,
    modifier: Modifier,
    stroke: Dp = MetroDimens.Stroke,
) {
    Box(
        modifier
            .heightIn(min = MetroDimens.ButtonMin)
            .border(stroke, if (kept) Metro.accent.fill else Metro.colors.secondary.copy(alpha = 0.4f))
            .background(if (kept) Metro.accent.fill.copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        MetroText(
            value.ifBlank { "(none)" },
            Metro.typography.body.copy(fontWeight = if (kept) MetroWeights.Semibold else FontWeight.Normal),
            color = if (value.isBlank()) Metro.colors.secondary else Metro.colors.foreground,
            maxLines = 1,
        )
    }
}

@Composable
private fun ComparePage() {
    Column(Modifier.fillMaxSize()) {
        SectionTitled("ACCOUNTS", Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) {
                MetroText("match", Metro.typography.header, maxLines = 1)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)) {
                    MetroText("from the bank", Metro.typography.caption, Modifier.weight(1f), color = Metro.accent.text)
                    MetroText("entered by you", Metro.typography.caption, Modifier.weight(1f), color = Metro.accent.text)
                }
                CompareRow("amount", "-\$5.75", "-\$5.75", keep = 0)
                CompareRow("date", "Thu 10/15", "Wed 10/14", keep = 0)
                CompareRow("payee", "CORNER COFFEE #42", "Corner Coffee", keep = 1)
                CompareRow("category", "", "Dining", keep = 1)
                CompareRow("cleared", "cleared", "not cleared", keep = 0)
                MetroText("note after match", Metro.typography.caption, Modifier.padding(top = 18.dp, bottom = 4.dp), color = Metro.colors.secondary)
                Box(Modifier.fillMaxWidth().background(Metro.colors.chrome).padding(10.dp)) {
                    MetroText(MATCH_NOTE, Metro.typography.caption, maxLines = 4)
                }
            }
        }
        MetroAppBar(
            actions =
                listOf(
                    ScreenAction("match", AppGlyph.Save, "match") {},
                    ScreenAction("not", AppGlyph.Cancel, "not a match") {},
                ),
            expanded = false,
            onExpandedChange = {},
        )
    }
}

// --- after: one transaction ----------------------------------------------------------------

@Composable
private fun AfterRegister() {
    RegisterShell {
        Spacer(Modifier.height(MetroDimens.Grid))
        DayHeading(day(merged.date))
        RegisterLineMock(merged, banded = false)
        RestOfRegister(startIndex = 1)
    }
}
