package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.components.AppBarButton
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.MetroPivot
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.PivotItem
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * Design mockups for a budget edit mode, reached from the budget's `•••` menu or by tapping the
 * to budget figure. Three ideas, each on the budget pivot item with to budget pinned at the top
 * while it holds money: type into the budgeted column on a keypad, nudge each category with
 * minus and plus by a chosen step, or pick a source and a destination and move between them.
 * Mock data only; renders frames to app/build/budget-edit-mockups.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class BudgetEditMockupTest {
    @Test fun entry() = snap("0-entry") { Entry() }

    @Test fun keypadOverview() = snap("a-1") { Keypad(Moves()) }

    @Test fun keypadTyping() = snap("a-2") { Keypad(Moves(mapOf("c-groceries" to 8_000)), focused = "c-groceries", typed = "280") }

    @Test fun keypadZero() = snap("a-3") { Keypad(Moves(zeroed)) }

    @Test fun nudgeOverview() = snap("b-1") { Nudge(Moves(), step = 1_000) }

    @Test fun nudgeTapped() = snap("b-2") { Nudge(Moves(mapOf("c-groceries" to 8_000)), step = 1_000, nudged = "c-groceries") }

    @Test fun nudgeZero() = snap("b-3") { Nudge(Moves(zeroed), step = 5_000) }

    @Test fun moveOverview() = snap("c-1") { Move(Moves()) }

    @Test fun movePicked() = snap("c-2") { Move(Moves(), from = "c-phone", to = "c-groceries") }

    @Test fun moveZero() = snap("c-3") { Move(Moves(zeroed)) }

    private fun snap(
        name: String,
        content: @Composable () -> Unit,
    ) = captureRoboImage("build/budget-edit-mockups/$name.png") {
        MetroTheme(darkTheme = false) {
            Box(Modifier.width(390.dp).height(780.dp).background(Metro.colors.background)) { content() }
        }
    }
}

/** Groceries covered and the rest of to budget put away in the rainy day fund. */
private val zeroed = mapOf("c-groceries" to 8_000L, "c-rainy" to 34_000L)

private const val TO_BUDGET = 42_000L

private val usd = Currencies.byCode("USD")!!

private data class Cat(
    val id: String,
    val name: String,
    val budgeted: Long,
    val spent: Long,
) {
    val available get() = budgeted - spent
}

private data class Group(
    val id: String,
    val name: String,
    val cats: List<Cat>,
)

/** Money moved into categories since edit mode opened; whatever goes in comes out of to budget. */
private data class Moves(
    val added: Map<String, Long> = emptyMap(),
) {
    val toBudget = TO_BUDGET - added.values.sum()

    val groups =
        listOf(
            Group("g-bills", "Bills", listOf(Cat("c-rent", "Rent", 110_000, 0), Cat("c-power", "Power", 14_000, 0))),
            Group("g-every", "Everyday", listOf(Cat("c-gas", "Gas", 12_000, 4_360), Cat("c-phone", "Phone", 11_000, 0))),
            Group("g-food", "Food", listOf(Cat("c-groceries", "Groceries", 20_000, 28_000), Cat("c-dining", "Dining", 8_000, 3_200))),
            Group("g-save", "Savings", listOf(Cat("c-rainy", "Rainy day", 80_000, 0))),
        ).map { g -> g.copy(cats = g.cats.map { it.copy(budgeted = it.budgeted + (added[it.id] ?: 0)) }) }

    fun cat(id: String) = groups.flatMap { it.cats }.first { it.id == id }
}

@Composable
private fun money(minor: Long) = MoneyFormat.format(minor, usd, formatOptions())

// --- shared chrome -------------------------------------------------------------------------

/** The home pivot, budget item open, with the edit mode's own bar along the bottom. */
@Composable
private fun EditShell(
    bar: @Composable () -> Unit = { EditBar() },
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        MetroPivot(
            title = SectionTitleCopy.APP,
            items = HomeSections.headers.map { header -> PivotItem(header) { if (header == HomeSections.BUDGET) content() } },
            modifier = Modifier.weight(1f),
        )
        bar()
    }
}

@Composable
private fun EditBar(primary: List<ScreenAction> = emptyList()) {
    MetroAppBar(
        actions =
            primary.ifEmpty {
                listOf(
                    ScreenAction("done", AppGlyph.Save, "done", run = {}),
                    ScreenAction("undo", AppGlyph.Undo, "undo", run = {}),
                )
            } +
                listOf(
                    ScreenAction("copy", AppGlyph.Copy, "copy last month", primary = false, run = {}),
                    ScreenAction("average", AppGlyph.Average, "3 month average", primary = false, run = {}),
                    ScreenAction("zero", AppGlyph.Zero, "set all to zero", primary = false, run = {}),
                ),
        expanded = false,
        onExpandedChange = {},
    )
}

/** "editing october 2026" where the month title sits outside edit mode. */
@Composable
private fun EditingTitle() {
    Row(
        Modifier.fillMaxWidth().padding(start = MetroDimens.Gutter, end = MetroDimens.Gutter, bottom = MetroDimens.Row),
        verticalAlignment = Alignment.Bottom,
    ) {
        MetroText("editing ", Metro.typography.subheader, color = Metro.accent.text)
        MetroText("october 2026", Metro.typography.subheader)
    }
}

/** to budget and held, the same pair as the budget's header, kept above the list while it scrolls. */
@Composable
private fun PinnedTotals(
    moves: Moves,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {
        BudgetTotal("held", money(25_000), Metro.colors.secondary, "held", alignEnd = true)
    },
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = MetroDimens.Gutter),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        BudgetTotal("to budget", money(moves.toBudget), Metro.accent.text, "to-budget")
        trailing()
    }
}

@Composable
private fun GroupRowLine(
    group: Group,
    moves: Moves,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(top = MetroDimens.Row),
        verticalAlignment = Alignment.Top,
    ) {
        MetroText(group.name, Metro.typography.groupHeader, Modifier.weight(1f))
        MetroText(money(group.cats.sumOf { it.available }), Metro.typography.groupHeader)
    }
}

@Composable
private fun Headings(vararg cells: Pair<String, Float>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = MetroDimens.Gutter), verticalAlignment = Alignment.Bottom) {
        Spacer(Modifier.weight(LEDGER_NAME))
        cells.forEach { (label, weight) ->
            MetroText(
                label,
                Metro.typography.caption.copy(textAlign = TextAlign.End),
                Modifier.weight(weight),
                color = Metro.colors.secondary,
                maxLines = 1,
            )
        }
    }
}

/** The ledger: every group and its categories, banded down the list as the budget is. */
@Composable
private fun Ledger(
    moves: Moves,
    modifier: Modifier = Modifier,
    scrolledPast: Int = 0,
    row: @Composable (Cat, Boolean) -> Unit,
) {
    // scrolledPast: groups already scrolled off the top, as the list scrolls an edited row into view.
    Column(modifier.fillMaxWidth().clipToBounds().padding(horizontal = MetroDimens.Gutter)) {
        var i = moves.groups.take(scrolledPast).sumOf { it.cats.size }
        moves.groups.drop(scrolledPast).forEach { group ->
            GroupRowLine(group, moves)
            group.cats.forEach { cat -> row(cat, ledgerBanded(i++)) }
            Spacer(Modifier.height(MetroDimens.Row))
        }
    }
}

@Composable
private fun RowScope.Figure(
    text: String,
    color: Color = Metro.colors.foreground,
    weight: Float = LEDGER_FIGURE,
) {
    MetroText(
        text,
        Metro.typography.body.copy(textAlign = TextAlign.End),
        Modifier.weight(weight),
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun availableColor(cat: Cat) = if (cat.available < 0) Color(0xFFC41100) else Metro.accent.text

// --- entry ---------------------------------------------------------------------------------

/** Today's budget with the `•••` menu open: edit budget heads the menu. Tapping to budget opens it too. */
@Composable
private fun Entry() {
    val shell = entryShell()
    Column(Modifier.fillMaxSize()) {
        HomePivot(
            shell = shell,
            loading = false,
            openMonth = shell.month,
            monthWindow = MonthWindow.around(shell.month),
            onGesture = {},
            modifier = Modifier.weight(1f),
            onToBudget = {},
        )
        MetroAppBar(
            actions =
                listOf(
                    AppBarButton(AppGlyph.Add, "add", onClick = {}),
                    AppBarButton(AppGlyph.Hold, "hold", onClick = {}),
                    AppBarButton(AppGlyph.Categories, "categories", onClick = {}),
                ).map { ScreenAction(it.id, it.glyph, it.label, run = {}) } +
                    listOf(
                        ScreenAction("edit", AppGlyph.Settings, "edit budget", primary = false, run = {}),
                        ScreenAction("hidden", AppGlyph.Categories, "show hidden categories", primary = false, run = {}),
                        ScreenAction("copy", AppGlyph.Copy, "copy last month", primary = false, run = {}),
                        ScreenAction("settings", AppGlyph.Settings, "settings", primary = false, run = {}),
                    ),
            expanded = true,
            onExpandedChange = {},
        )
    }
}

private fun entryShell(): MonthShell {
    val moves = Moves()
    return MonthShell(
        budgetId = "home",
        budgetName = "Home",
        mode = BudgetMode.ENVELOPE,
        currency = usd,
        month = YearMonth.of(2026, 10),
        headerLabel = "to budget",
        headerMinor = TO_BUDGET,
        bufferedMinor = 25_000,
        groups =
            moves.groups.map { g ->
                GroupRow(
                    g.id,
                    g.name,
                    g.cats.sumOf { it.available },
                    g.cats.map { CategoryRow(it.id, it.name, it.budgeted, it.spent, it.available, false) },
                )
            },
        accounts = emptyList(),
        inbox = emptyList(),
    )
}

// --- A: keypad -----------------------------------------------------------------------------

/**
 * The budgeted column turns into text boxes. Tapping one opens a keypad in place of the app bar;
 * every keystroke moves the money in or out of to budget at once, so the figure up top counts down
 * as you type. Lowering one category hands money back to to budget for the next.
 */
@Composable
private fun Keypad(
    moves: Moves,
    focused: String? = null,
    typed: String = "",
) {
    EditShell(bar = { if (focused != null) KeypadPanel(moves.cat(focused)) else EditBar() }) {
        Column(Modifier.fillMaxSize()) {
            EditingTitle()
            if (moves.toBudget != 0L) PinnedTotals(moves, Modifier.padding(bottom = MetroDimens.Row))
            Headings("budgeted" to 1.3f, "available" to LEDGER_FIGURE)
            Ledger(moves, Modifier.weight(1f), scrolledPast = if (focused != null) 2 else 0) { cat, banded ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).ledgerFill(if (banded) ledgerBand else Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetroText(cat.name, Metro.typography.body, Modifier.weight(LEDGER_NAME))
                    val focus = cat.id == focused
                    Box(
                        Modifier
                            .weight(1.3f)
                            .height(36.dp)
                            .background(if (focus) Color.White else Color(0xFFD6D6D6))
                            .then(if (focus) Modifier.border(2.dp, Metro.accent.fill) else Modifier)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        MetroText(
                            if (focus) "$typed|" else money(cat.budgeted).removePrefix("$"),
                            Metro.typography.body.copy(textAlign = TextAlign.End),
                            color = Color.Black,
                            maxLines = 1,
                        )
                    }
                    Figure(money(cat.available), availableColor(cat))
                }
            }
        }
    }
}

/** A Metro number pad with the two amounts people reach for most: cover what's overspent, or all that's left. */
@Composable
private fun KeypadPanel(cat: Cat) {
    val keys =
        listOf(
            listOf("1", "2", "3", "⌫"),
            listOf("4", "5", "6", "cover"),
            listOf("7", "8", "9", "all"),
            listOf(".", "0", "00", "done"),
        )
    Column(Modifier.fillMaxWidth().background(Metro.colors.chrome).padding(MetroDimens.Gutter / 2)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
            MetroText(cat.name, Metro.typography.body, Modifier.weight(1f))
            MetroText("available ${money(cat.available)}", Metro.typography.caption, color = Metro.colors.secondary)
        }
        keys.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    val accent = key == "done"
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(3.dp)
                            .height(50.dp)
                            .background(if (accent) Metro.accent.fill else Metro.colors.background),
                        contentAlignment = Alignment.Center,
                    ) {
                        MetroText(
                            key,
                            if (key.length > 1) Metro.typography.body else Metro.typography.control,
                            color = if (accent) Metro.accent.onFill else Metro.colors.foreground,
                        )
                    }
                }
            }
        }
    }
}

// --- B: nudge ------------------------------------------------------------------------------

/**
 * Every category gets minus and plus around its budgeted figure. Each tap moves one step between
 * that category and to budget; the step sits beside to budget, and pressing and holding repeats.
 */
@Composable
private fun Nudge(
    moves: Moves,
    step: Long,
    nudged: String? = null,
) {
    EditShell {
        Column(Modifier.fillMaxSize()) {
            EditingTitle()
            val steps = @Composable {
                StepPicker(step)
            }
            if (moves.toBudget != 0L) {
                PinnedTotals(moves, Modifier.padding(bottom = MetroDimens.Row), trailing = steps)
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = MetroDimens.Gutter, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                    steps()
                }
            }
            Headings("budgeted" to 1.9f, "available" to LEDGER_FIGURE)
            Ledger(moves, Modifier.weight(1f)) { cat, banded ->
                val hit = cat.id == nudged
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).ledgerFill(if (banded) ledgerBand else Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(LEDGER_NAME)) {
                        MetroText(cat.name, Metro.typography.body)
                        if (hit) MetroText("+${money(8_000)}", Metro.typography.caption, color = Metro.accent.text)
                    }
                    Row(Modifier.weight(1.9f), verticalAlignment = Alignment.CenterVertically) {
                        NudgeButton("−", pressed = false)
                        MetroText(
                            money(cat.budgeted),
                            Metro.typography.body.copy(textAlign = TextAlign.Center),
                            Modifier.weight(1f),
                            color = if (hit) Metro.accent.text else Metro.colors.foreground,
                            maxLines = 1,
                            softWrap = false,
                        )
                        NudgeButton("+", pressed = hit)
                    }
                    Figure(money(cat.available), availableColor(cat))
                }
            }
        }
    }
}

@Composable
private fun NudgeButton(
    label: String,
    pressed: Boolean,
) {
    Box(
        Modifier
            .size(32.dp)
            .background(if (pressed) Metro.accent.fill else Color.Transparent)
            .border(2.dp, if (pressed) Metro.accent.fill else Metro.colors.foreground),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(label, Metro.typography.control, color = if (pressed) Metro.accent.onFill else Metro.colors.foreground)
    }
}

@Composable
private fun StepPicker(step: Long) {
    Column(horizontalAlignment = Alignment.End) {
        MetroText("step", Metro.typography.caption, color = Metro.colors.secondary)
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(100L, 1_000L, 5_000L, 10_000L).forEach { s ->
                val on = s == step
                Box(
                    Modifier
                        .heightIn(min = 30.dp)
                        .width(44.dp)
                        .background(if (on) Metro.accent.fill else Color.Transparent)
                        .border(2.dp, if (on) Metro.accent.fill else Metro.colors.outline),
                    contentAlignment = Alignment.Center,
                ) {
                    MetroText(
                        money(s).removeSuffix(".00"),
                        Metro.typography.caption,
                        color = if (on) Metro.accent.onFill else Metro.colors.foreground,
                    )
                }
            }
        }
    }
}

// --- C: move -------------------------------------------------------------------------------

/**
 * To budget is a tile at the top. Tap where money comes from (the tile or any category), then
 * where it goes; a move panel takes the app bar's place with the amount and quick picks.
 */
@Composable
private fun Move(
    moves: Moves,
    from: String? = null,
    to: String? = null,
) {
    val picked = from != null && to != null
    EditShell(bar = { if (picked) MovePanel(moves.cat(from!!), moves.cat(to!!)) else EditBar() }) {
        Column(Modifier.fillMaxSize()) {
            EditingTitle()
            if (moves.toBudget != 0L) ToBudgetTile(moves)
            MetroText(
                when {
                    picked -> "now set the amount below"
                    else -> "tap where money comes from (to budget or a category), then where it goes"
                },
                Metro.typography.caption,
                Modifier.padding(horizontal = MetroDimens.Gutter, vertical = MetroDimens.Row),
                color = Metro.colors.secondary,
            )
            Headings("budgeted" to LEDGER_FIGURE, "available" to LEDGER_FIGURE)
            Ledger(moves, Modifier.weight(1f), scrolledPast = if (picked) 1 else 0) { cat, banded ->
                val source = cat.id == from
                val target = cat.id == to
                val ink = if (source) Metro.accent.onFill else Metro.colors.foreground
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .ledgerFill(if (source) Metro.accent.fill else if (banded) ledgerBand else Color.Transparent)
                        .then(if (target) Modifier.border(2.dp, Metro.accent.fill) else Modifier)
                        .padding(horizontal = if (target) 6.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(LEDGER_NAME)) {
                        MetroText(cat.name, Metro.typography.body, color = ink)
                        if (source) MetroText("from", Metro.typography.caption, color = ink)
                        if (target) MetroText("to", Metro.typography.caption, color = Metro.accent.text)
                    }
                    Figure(money(cat.budgeted), ink)
                    Figure(money(cat.available), if (source) ink else availableColor(cat))
                }
            }
        }
    }
}

@Composable
private fun ToBudgetTile(moves: Moves) {
    Row(
        Modifier
            .padding(horizontal = MetroDimens.Gutter)
            .fillMaxWidth()
            .background(Metro.accent.fill)
            .padding(horizontal = MetroDimens.Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            MetroText("to budget", Metro.typography.caption, color = Metro.accent.onFill)
            MetroText(money(moves.toBudget), Metro.typography.subheader, color = Metro.accent.onFill)
        }
        Column(horizontalAlignment = Alignment.End) {
            MetroText("held", Metro.typography.caption, color = Metro.accent.onFill)
            MetroText(money(25_000), Metro.typography.body, color = Metro.accent.onFill)
        }
    }
}

@Composable
private fun MovePanel(
    from: Cat,
    to: Cat,
) {
    Column(Modifier.fillMaxWidth().background(Metro.colors.chrome).padding(MetroDimens.Gutter)) {
        MetroText("move ${from.name} → ${to.name}", Metro.typography.body)
        Box(
            Modifier.padding(vertical = 8.dp).fillMaxWidth().height(44.dp).background(Color.White).border(2.dp, Metro.accent.fill).padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            MetroText("80.00|", Metro.typography.control, color = Color.Black)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("cover ${money(-to.available)}", on = true)
            Chip("all ${money(from.available)}", on = false)
        }
    }
    EditBar(
        primary =
            listOf(
                ScreenAction("move", AppGlyph.Transfer, "move", run = {}),
                ScreenAction("cancel", AppGlyph.Cancel, "cancel", run = {}),
            ),
    )
}

@Composable
private fun Chip(
    label: String,
    on: Boolean,
) {
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .background(if (on) Metro.accent.fill else Color.Transparent)
            .border(2.dp, if (on) Metro.accent.fill else Metro.colors.foreground)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(label, Metro.typography.body, color = if (on) Metro.accent.onFill else Metro.colors.foreground)
    }
}
