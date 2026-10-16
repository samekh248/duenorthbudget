package app.duenorth.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.design.components.AppBarButton
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.MetroPivot
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.PivotItem
import app.duenorth.budget.design.components.asScreenActions
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Design mockups for the inbox restyle: three takes on the account register's ledger look
 * (day headings, faint bands, an edge marker bar) tuned for working through uncategorized
 * transactions. Mock data only; nothing here is wired to the app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class InboxMockupScreenshotTest {
    @Test fun aLight() = snap("inbox_mock_a_light", false, Variant.Ledger)

    @Test fun aDark() = snap("inbox_mock_a_dark", true, Variant.Ledger)

    @Test fun bLight() = snap("inbox_mock_b_light", false, Variant.Suggest)

    @Test fun bDark() = snap("inbox_mock_b_dark", true, Variant.Suggest)

    @Test fun cLight() = snap("inbox_mock_c_light", false, Variant.Batch)

    @Test fun cDark() = snap("inbox_mock_c_dark", true, Variant.Batch)

    private enum class Variant { Ledger, Suggest, Batch }

    private fun snap(
        name: String,
        dark: Boolean,
        variant: Variant,
    ) {
        captureRoboImage("build/inbox-mockups/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp).height(780.dp).background(Metro.colors.background)) {
                    MetroPivot(
                        title = SectionTitleCopy.APP,
                        items =
                            listOf(
                                PivotItem("inbox") {
                                    when (variant) {
                                        Variant.Ledger -> LedgerInbox()
                                        Variant.Suggest -> SuggestInbox()
                                        Variant.Batch -> BatchInbox()
                                    }
                                },
                                PivotItem("budget") {},
                                PivotItem("accounts") {},
                            ),
                        modifier = Modifier.weight(1f),
                    )
                    val bar =
                        when (variant) {
                            Variant.Ledger ->
                                listOf(
                                    AppBarButton(AppGlyph.Add, "add", onClick = {}),
                                    AppBarButton(AppGlyph.Import, "fetch", onClick = {}),
                                    AppBarButton(AppGlyph.Reconcile, "select", onClick = {}),
                                )
                            Variant.Suggest ->
                                listOf(
                                    AppBarButton(AppGlyph.Save, "accept all", onClick = {}),
                                    AppBarButton(AppGlyph.Import, "fetch", onClick = {}),
                                    AppBarButton(AppGlyph.Add, "add", onClick = {}),
                                )
                            Variant.Batch ->
                                listOf(
                                    AppBarButton(AppGlyph.Categories, "categorize", onClick = {}),
                                    AppBarButton(AppGlyph.Cancel, "done", onClick = {}),
                                )
                        }
                    MetroAppBar(actions = bar.asScreenActions(), expanded = false, onExpandedChange = {})
                }
            }
        }
    }
}

private data class MockItem(
    val id: String,
    val date: String,
    val payee: String,
    val amountMinor: Long,
    val account: String,
    val note: String = "",
    val suggestion: String? = null,
    val fresh: Boolean = false,
)

private val usd = Currencies.byCode("USD")!!

private val items =
    listOf(
        MockItem("coffee", "oct 15", "Corner Coffee", -575, "Visa", suggestion = "Dining", fresh = true),
        MockItem("shell", "oct 15", "Shell", -4_210, "Checking", suggestion = "Gas", fresh = true),
        MockItem("amazon", "oct 14", "Amazon", -6_318, "Visa", "2 items", fresh = true),
        MockItem("acme", "oct 14", "Acme Payroll", 185_000, "Checking", suggestion = "Paycheck"),
        MockItem("rent", "oct 12", "Riverside Property Management", -120_000, "Checking", "October rent", suggestion = "Rent"),
        MockItem("atm", "oct 11", "(no payee)", -2_000, "Checking", "ATM withdrawal"),
        MockItem("coffee2", "oct 10", "Corner Coffee", -450, "Visa", suggestion = "Dining"),
        MockItem("bus", "oct 10", "Metro Transit", -275, "Visa", suggestion = "Transit"),
    )

private fun money(minor: Long) = MoneyFormat.format(minor, usd)

private fun detail(item: MockItem) = listOfNotNull(item.account, item.note.ifBlank { null }).joinToString(" · ")

/** Count and total above the list, in the register's balance style. */
@Composable
private fun InboxSummary(
    left: String,
    leftValue: String,
    right: String,
    rightValue: String,
) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        BudgetTotal(left, leftValue, Metro.accent.text, "l", modifier = Modifier.weight(1f))
        BudgetTotal(right, rightValue, Metro.colors.foreground, "r", alignEnd = true, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DayHeading(
    text: String,
    trailing: String? = null,
) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
        MetroText(text, Metro.typography.caption, Modifier.weight(1f), color = Metro.accent.text)
        if (trailing != null) MetroText(trailing, Metro.typography.caption, color = Metro.colors.secondary)
    }
}

/** Ledger row shell: band, an optional left marker bar past the gutter, and the register's air. */
@Composable
private fun InboxRowFrame(
    banded: Boolean,
    marker: Color?,
    tint: Color? = null,
    content: @Composable () -> Unit,
) {
    val fill = tint ?: if (banded) ledgerBand else Color.Transparent
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .ledgerFill(fill)
            .drawBehind {
                if (marker != null) {
                    val gutter = MetroDimens.Gutter.toPx()
                    val inset = 6.dp.toPx()
                    drawRect(marker, Offset(-gutter + 2.dp.toPx(), inset), Size(5.dp.toPx(), size.height - 2 * inset))
                }
            }.padding(vertical = 15.dp),
    ) { content() }
}

@Composable
private fun PayeeAndAmount(
    item: MockItem,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(LEDGER_NAME + LEDGER_FIGURE).padding(end = MetroDimens.Gutter)) {
            MetroText(item.payee, Metro.typography.body, maxLines = 2)
            second()
        }
        MetroText(
            money(item.amountMinor),
            Metro.typography.body.copy(textAlign = TextAlign.End),
            Modifier.weight(LEDGER_FIGURE * 2),
            color = Metro.accent.text,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/* ---------- A: the register, as an inbox ---------- */

@Composable
private fun LedgerInbox() {
    val days = items.groupBy { it.date }
    var index = 0
    Column(Modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) {
        InboxSummary("to categorize", "${items.size}", "new since last visit", "${items.count { it.fresh }}")
        LazyColumn(Modifier.weight(1f)) {
            days.forEach { (day, rows) ->
                item { DayHeading(day) }
                items(rows) { item ->
                    val banded = ledgerBanded(index++)
                    InboxRowFrame(banded, marker = if (item.fresh) Metro.accent.text else null) {
                        PayeeAndAmount(item, second = {
                            MetroText(detail(item), Metro.typography.caption, color = Metro.colors.secondary, maxLines = 2)
                        }, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

/* ---------- B: suggestions with one-tap accept ---------- */

@Composable
private fun SuggestInbox() {
    val days = items.groupBy { it.date }
    var index = 0
    val suggested = items.count { it.suggestion != null }
    Column(Modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) {
        InboxSummary("to categorize", "${items.size}", "suggested", "$suggested")
        LazyColumn(Modifier.weight(1f)) {
            days.forEach { (day, rows) ->
                item { DayHeading(day) }
                items(rows) { item ->
                    val banded = ledgerBanded(index++)
                    val marker = if (item.suggestion != null) Metro.accent.text else Metro.colors.secondary.copy(alpha = 0.22f)
                    InboxRowFrame(banded, marker) {
                        Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            PayeeAndAmount(item, second = {
                                if (item.suggestion != null) {
                                    MetroText(item.suggestion, Metro.typography.caption, color = Metro.accent.text)
                                } else {
                                    MetroText("choose a category", Metro.typography.caption, color = Metro.colors.secondary)
                                }
                                MetroText(detail(item), Metro.typography.caption, color = Metro.colors.secondary, maxLines = 1)
                            }, modifier = Modifier.weight(1f))
                            RoundAction(if (item.suggestion != null) AppGlyph.Save else AppGlyph.Categories)
                        }
                    }
                }
            }
        }
    }
}

/** A small Metro round button: a thin circle holding a glyph. */
@Composable
private fun RoundAction(glyph: AppGlyph) {
    Box(
        Modifier
            .padding(start = MetroDimens.Gutter)
            .size(40.dp)
            .border(2.dp, Metro.colors.foreground, CircleShape),
        contentAlignment = Alignment.Center,
    ) { AppGlyphIcon(glyph) }
}

/* ---------- C: batch by payee, in select mode ---------- */

@Composable
private fun BatchInbox() {
    val groups = items.groupBy { it.payee }.entries.sortedByDescending { it.value.size }
    val picked = setOf("coffee", "coffee2")
    var index = 0
    Column(Modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) {
        InboxSummary("selected", "${picked.size}", "selected total", money(items.filter { it.id in picked }.sumOf { it.amountMinor }))
        LazyColumn(Modifier.weight(1f)) {
            groups.forEach { (payee, rows) ->
                item {
                    DayHeading(payee.lowercase(), trailing = if (rows.size > 1) "${rows.size} · ${money(rows.sumOf { it.amountMinor })}" else null)
                }
                items(rows) { item ->
                    val on = item.id in picked
                    val banded = ledgerBanded(index++)
                    InboxRowFrame(banded, marker = null, tint = if (on) Metro.accent.text.copy(alpha = 0.10f) else null) {
                        Row(verticalAlignment = Alignment.Top) {
                            CheckBox(on, Modifier.width(32.dp).padding(top = 2.dp))
                            Row(Modifier.weight(1f)) {
                                Column(Modifier.weight(LEDGER_NAME + LEDGER_FIGURE).padding(end = MetroDimens.Gutter)) {
                                    MetroText(item.date, Metro.typography.body)
                                    MetroText(
                                        listOfNotNull(item.account, item.suggestion?.let { "usually $it" }, item.note.ifBlank { null })
                                            .joinToString(" · "),
                                        Metro.typography.caption,
                                        color = Metro.colors.secondary,
                                        maxLines = 2,
                                    )
                                }
                                MetroText(
                                    money(item.amountMinor),
                                    Metro.typography.body.copy(textAlign = TextAlign.End),
                                    Modifier.weight(LEDGER_FIGURE * 2),
                                    color = Metro.accent.text,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckBox(
    on: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = Metro.accent.text
    val outline = Metro.colors.secondary
    val mark = Metro.colors.background
    Box(modifier) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = 1.5.dp.toPx()
            if (on) {
                drawRect(accent)
                val w = size.width
                val check =
                    Path().apply {
                        moveTo(w * 0.24f, w * 0.52f)
                        lineTo(w * 0.42f, w * 0.70f)
                        lineTo(w * 0.77f, w * 0.32f)
                    }
                drawPath(check, mark, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawRect(outline, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
            }
        }
    }
}
