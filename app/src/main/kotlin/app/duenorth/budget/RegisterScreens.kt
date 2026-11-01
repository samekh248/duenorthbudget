package app.duenorth.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.MatchCopy
import app.duenorth.budget.core.MatchPair
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.RegisterCopy
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RegisterRow
import app.duenorth.budget.core.RegisterSearch
import app.duenorth.budget.core.RegisterShow
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.LayerCheckBox
import app.duenorth.budget.design.components.LocalFieldSubmit
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.drawRelief
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.Relief

@Composable
fun RegisterScreen(
    page: RegisterPage,
    onGesture: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onReconcile: () -> Unit,
    onImportFile: (() -> Unit)? = null,
    selection: Set<String>? = null,
    onSelect: (String) -> Unit = {},
    onToggleSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    onMatch: (MatchPair) -> Unit = {},
    onNotAMatch: (MatchPair) -> Unit = {},
    search: RegisterSearch? = null,
    onSearch: (RegisterSearch) -> Unit = {},
    /** Whether the pending section starts open; a tap on its heading folds it. */
    pendingOpen: Boolean = true,
) {
    val rows = if (search == null) page.rows else page.searched(search)
    val owed = page.account.type == "credit" && page.balanceMinor < 0
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(horizontal = MetroDimens.Gutter),
    ) {
        MetroText(page.account.name, Metro.typography.header, maxLines = 2)
        BudgetTotal(
            label = if (owed) "${ShellCopy.BALANCE} ${RegisterCopy.OWED}" else ShellCopy.BALANCE,
            amount = MoneyFormat.format(page.balanceMinor, page.currency, formatOptions()),
            amountColor = Metro.accent.text,
            amountTag = "register-balance",
            modifier = Modifier.padding(top = MetroDimens.Gutter),
        )
        if (selection != null) {
            MetroText(
                "${selection.size} ${RegisterCopy.SELECTED}",
                Metro.typography.body.copy(textAlign = TextAlign.End),
                Modifier.fillMaxWidth().padding(top = MetroDimens.Gutter).testTag("register-selected-count"),
                color = Metro.accent.text,
            )
        }
        if (search != null) {
            RegisterSearchBar(search, page, shown = rows.size, onSearch = onSearch)
        }
        if (rows.isEmpty()) {
            MetroText(
                if (search?.active == true) RegisterSearch.NOTHING else RegisterCopy.NO_TRANSACTIONS,
                Metro.typography.body,
                Modifier.padding(top = MetroDimens.Grid).testTag("register-empty"),
            )
        } else {
            RegisterList(
                rows,
                page,
                onGesture,
                selection,
                onPress = { id -> if (selection != null) onToggleSelected(id) else onOpen(id) },
                onLongPress = { id -> if (selection == null) onSelect(id) else onToggleSelected(id) },
                modifier = Modifier.weight(1f).padding(top = if (search == null) MetroDimens.Grid else 0.dp),
                onMatch = onMatch,
                onNotAMatch = onNotAMatch,
                searching = search?.active == true,
                pendingOpen = pendingOpen,
            )
        }
    }
}

/**
 * The register's search, under the balance: a box that opens focused with the keyboard up, then
 * all, not cleared, and no category (an off-budget account has no categories, so it gets the first two).
 * While anything narrows the rows, "2 of 41" says how many are left.
 */
@Composable
private fun RegisterSearchBar(
    search: RegisterSearch,
    page: RegisterPage,
    shown: Int,
    onSearch: (RegisterSearch) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxWidth().padding(top = MetroDimens.Gutter).testTag("register-search")) {
        CompositionLocalProvider(LocalFieldSubmit provides { keyboard?.hide() }) {
            MetroField(
                search.query,
                { onSearch(search.copy(query = it)) },
                RegisterSearch.HINT,
                Modifier.focusRequester(focus).testTag("register-search-box"),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            )
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            RegisterShow.entries
                .filter { it != RegisterShow.NO_CATEGORY || !page.account.offBudget }
                .forEach { option ->
                    Choice(option.label, option == search.show, "register-show-${option.name.lowercase()}") {
                        onSearch(search.copy(show = option))
                    }
                }
        }
        if (search.active) {
            MetroText(
                RegisterSearch.count(shown, page.rows.size),
                Metro.typography.caption,
                Modifier.testTag("register-search-count"),
                color = Metro.colors.secondary,
            )
        }
    }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
}

@Composable
private fun RegisterList(
    rows: List<RegisterRow>,
    page: RegisterPage,
    onGesture: (Boolean) -> Unit,
    selection: Set<String>?,
    onPress: (String) -> Unit,
    onLongPress: (String) -> Unit,
    modifier: Modifier = Modifier,
    onMatch: (MatchPair) -> Unit = {},
    onNotAMatch: (MatchPair) -> Unit = {},
    searching: Boolean = false,
    pendingOpen: Boolean = true,
) {
    // A new visit starts at the newest row. Scroll position is not saved.
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onGesture(it) }
    }
    // Possible matches sit above the rows, each pair held together, until matched or called not a match.
    // A search lists only the rows it keeps, so possible matches wait until it closes.
    val pairs =
        if (selection == null &&
            !searching
        ) {
            page.matches.filter { pair -> pair.ids.all { id -> rows.any { it.id == id } } }
        } else {
            emptyList()
        }
    val paired = pairs.flatMapTo(mutableSetOf()) { it.ids }
    // Rows the bank has not posted yet sit apart, above the rest, under a heading that folds them away.
    val pending = rows.filter { it.pending && it.id !in paired }
    var showPending by rememberSaveable { mutableStateOf(pendingOpen) }
    val listed = rows.filter { it.id !in paired && !it.pending }
    val days = registerDays(listed)
    val position = listed.withIndex().associate { it.value.id to it.index }
    val byId = rows.associateBy { it.id }
    val cards = ledgerBlocks
    LazyColumn(modifier.bleedGutter(cards), state = list, contentPadding = cardListPadding(cards)) {
        pairs.forEach { pair ->
            item(key = "match-${pair.key}") {
                RegisterMatch(
                    pair = pair,
                    bank = byId.getValue(pair.bank.id),
                    entered = byId.getValue(pair.entered.id),
                    page = page,
                    onPress = onPress,
                    onMatch = { onMatch(pair) },
                    onNotAMatch = { onNotAMatch(pair) },
                )
            }
        }
        if (pending.isNotEmpty()) {
            item(key = "pending") {
                PendingHeading(pending, page, showPending) { showPending = !showPending }
            }
            if (showPending) {
                val line: @Composable (RegisterRow, Boolean, Dp) -> Unit = { row, banded, air ->
                    RegisterLine(
                        row,
                        page,
                        banded = banded,
                        picked = selection?.let { row.id in it },
                        onPress = { onPress(row.id) },
                        onLongPress = { onLongPress(row.id) },
                        detail = pendingDetail(row),
                        extraAir = air,
                    )
                }
                if (cards) {
                    item(key = "pending-rows") {
                        LedgerGroup(Modifier.padding(top = 8.dp, bottom = 4.dp), bottomAir = false, heading = {}) {
                            pending.forEach { row -> line(row, false, if (row === pending.last()) MetroDimens.Row else 0.dp) }
                        }
                    }
                } else {
                    items(pending, key = { "pending-${it.id}" }) { row ->
                        line(row, ledgerBanded(pending.indexOf(row)), 0.dp)
                    }
                }
            }
        }
        if (cards) {
            // Each day is one block (panel or channel) under its date, so the rows of a day travel together.
            days.forEach { day ->
                item(key = "day-${day.first().id}") {
                    MetroText(
                        RegisterEntry.registerDateLabel(day.first().date),
                        Metro.typography.caption,
                        Modifier.padding(top = 16.dp, bottom = 8.dp).testTag("register-day-${day.first().date}"),
                        color = Metro.accent.text,
                    )
                    LedgerGroup(Modifier.padding(bottom = 4.dp), bottomAir = false, heading = {}) {
                        day.forEach { row ->
                            RegisterLine(
                                row,
                                page,
                                banded = false,
                                picked = selection?.let { row.id in it },
                                onPress = { onPress(row.id) },
                                onLongPress = { onLongPress(row.id) },
                                // The last row holds the panel's bottom air, so a picked one is tinted to the edge.
                                extraAir = if (row === day.last()) MetroDimens.Row else 0.dp,
                                showLock = true,
                            )
                        }
                    }
                }
            }
            return@LazyColumn
        }
        days.forEach { day ->
            item(key = "day-${day.first().id}") {
                MetroText(
                    RegisterEntry.registerDateLabel(day.first().date),
                    Metro.typography.caption,
                    Modifier.padding(top = 16.dp, bottom = 4.dp).testTag("register-day-${day.first().date}"),
                    color = Metro.accent.text,
                )
            }
            items(day, key = { it.id }) { row ->
                RegisterLine(
                    row,
                    page,
                    banded = ledgerBanded(position.getValue(row.id)),
                    picked = selection?.let { row.id in it },
                    onPress = { onPress(row.id) },
                    onLongPress = { onLongPress(row.id) },
                    showLock = true,
                )
            }
        }
    }
}

/**
 * The pending section's heading, built like reconcile's locked one: a rule, a clock, "pending" with how many and
 * what they come to, and a chevron that folds the rows away.
 */
@Composable
private fun PendingHeading(
    rows: List<RegisterRow>,
    page: RegisterPage,
    open: Boolean,
    onToggle: () -> Unit,
) {
    val ink = Metro.colors.secondary
    Column(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onToggle)
            .semantics { stateDescription = if (open) RegisterCopy.PENDING_OPEN else RegisterCopy.PENDING_FOLDED }
            .testTag("register-pending-heading"),
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(ink.copy(alpha = PENDING_RULE_ALPHA)))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(16.dp)) { drawClock(center, ink) }
            MetroText(RegisterCopy.PENDING, Metro.typography.body, Modifier.padding(start = 6.dp))
            MetroText(
                "${rows.size} · ${MoneyFormat.format(rows.sumOf { it.amountMinor }, page.currency, formatOptions())}",
                Metro.typography.caption.copy(textAlign = TextAlign.End),
                Modifier.weight(1f).padding(start = 8.dp, end = 4.dp).testTag("register-pending-total"),
                color = ink,
                maxLines = 1,
            )
            AppGlyphIcon(if (open) AppGlyph.ChevronDown else AppGlyph.ChevronRight, ink)
        }
        MetroText(RegisterCopy.PENDING_NOTE, Metro.typography.caption, color = ink)
    }
}

/** A pending row has no day heading over it, so its date leads the quieter line. */
private fun pendingDetail(row: RegisterRow): String =
    listOf(RegisterEntry.registerDateLabel(row.date), registerDetail(row)).filter { it.isNotBlank() }.joinToString(" · ")

private const val PENDING_RULE_ALPHA = 0.45f

/**
 * A possible match on the register: both rows on an accent tint, tied by a bar down the left edge,
 * each with its date and where it came from, and match or not a match under them.
 */
@Composable
private fun RegisterMatch(
    pair: MatchPair,
    bank: RegisterRow,
    entered: RegisterRow,
    page: RegisterPage,
    onPress: (String) -> Unit,
    onMatch: () -> Unit,
    onNotAMatch: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("register-match-${pair.key}")) {
        MetroText(
            MatchCopy.POSSIBLE_MATCH,
            Metro.typography.caption,
            Modifier.padding(top = 16.dp, bottom = 4.dp),
            color = Metro.accent.text,
        )
        Column(Modifier.fillMaxWidth().ledgerFill(Metro.accent.text.copy(alpha = MATCH_TINT)).padding(bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .padding(vertical = 6.dp)
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(Metro.accent.fill),
                )
                Column(Modifier.padding(start = 10.dp)) {
                    listOf(
                        bank to "${RegisterEntry.registerDateLabel(bank.date)} · ${MatchCopy.FROM_BANK}",
                        entered to
                            listOf(
                                RegisterEntry.registerDateLabel(entered.date),
                                registerDetail(entered),
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                    ).forEach { (row, detail) ->
                        RegisterLine(
                            row,
                            page,
                            banded = false,
                            picked = null,
                            onPress = { onPress(row.id) },
                            onLongPress = { onPress(row.id) },
                            detail = detail,
                        )
                    }
                }
            }
            MatchButtons(pair, onMatch, onNotAMatch)
        }
    }
}

/** Runs of rows that share a date, in register order, each shown under its day heading. */
internal fun registerDays(rows: List<RegisterRow>): List<List<RegisterRow>> =
    rows.fold(mutableListOf<MutableList<RegisterRow>>()) { days, row ->
        if (days.lastOrNull()?.first()?.date == row.date) days.last() += row else days += mutableListOf(row)
        days
    }

/**
 * One transaction on the ledger: payee in the category rows' body type, with the
 * category and note on a quieter line under it, and the amount in the figure columns.
 * A bar at the right edge, running past a split's parts, carries a centered "c": accent
 * with a solid "c" when the transaction is cleared, faint with a dim "c" when it is not.
 * Every other row sits on a faint full-width band. In select mode ([picked] not null) a checkbox leads the row and a
 * picked row takes an accent tint.
 */
@Composable
internal fun RegisterLine(
    row: RegisterRow,
    page: RegisterPage,
    banded: Boolean,
    picked: Boolean?,
    onPress: () -> Unit,
    onLongPress: () -> Unit,
    detail: String = registerDetail(row),
    modifier: Modifier = Modifier,
    /** Air under the row inside its tint: the panel's bottom air, on a day panel's last row. */
    extraAir: Dp = 0.dp,
    /** Shows a reconciled row's mark as a lock, as Actual does, in place of the cleared "c". */
    showLock: Boolean = false,
) {
    val accent = Metro.accent.text
    val locked = showLock && row.reconciled
    val lockInk = Metro.colors.foreground.copy(alpha = LOCK_ALPHA)
    val fill =
        when {
            picked == true -> accent.copy(alpha = 0.10f)
            banded -> ledgerBand
            else -> Color.Transparent
        }
    val pending = row.pending && !locked
    val clockInk = Metro.colors.secondary
    val bar =
        when {
            locked -> lockInk
            row.cleared -> accent
            else -> Metro.colors.secondary.copy(alpha = 0.22f)
        }
    val letter = if (row.cleared) Metro.colors.background else Metro.colors.secondary.copy(alpha = 0.7f)
    val measurer = rememberTextMeasurer()
    val mark = Metro.typography.caption.copy(color = letter, fontWeight = FontWeight.SemiBold)
    // A relief style marks cleared with a small square key at the row's end instead of the edge bar: standing up
    // and dim when not cleared, pressed in and filled with the accent when cleared.
    val surface = Metro.surface
    val relief = surface.style.relief
    val ground = Metro.colors.background
    val accentFill = Metro.accent.fill
    val keyMark =
        Metro.typography.caption.copy(
            color = if (row.cleared) Metro.accent.onFill else Metro.colors.secondary,
            fontWeight = FontWeight.SemiBold,
        )
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .ledgerFill(fill)
            .drawBehind {
                if (relief) {
                    val side = CLEARED_KEY.toPx()
                    val topLeft = Offset(size.width - side, (size.height - extraAir.toPx() - side) / 2)
                    // Mica rounds the key into a dot, a hairline ring until it is cleared.
                    drawRelief(
                        surface,
                        when {
                            row.cleared -> Relief.Inset
                            surface.style.layers -> Relief.Raised
                            else -> surface.style.control
                        },
                        topLeft,
                        Size(side, side),
                        if (surface.style.layers) side / 2 else 0f,
                        when {
                            locked -> lockInk
                            row.cleared -> accentFill
                            else -> ground
                        },
                        small = true,
                    )
                    if (locked) {
                        drawLock(Offset(topLeft.x + side / 2, topLeft.y + side / 2), ground)
                        return@drawBehind
                    }
                    if (pending) {
                        drawClock(Offset(topLeft.x + side / 2, topLeft.y + side / 2), clockInk)
                        return@drawBehind
                    }
                    // Centred on the letter's x-height, not its line box, which leaves room above a lowercase c.
                    val c = measurer.measure(RegisterCopy.CLEARED_MARK, keyMark)
                    val middle = c.firstBaseline - keyMark.fontSize.toPx() * SELAWIK_X_HEIGHT / 2
                    drawText(c, topLeft = Offset(topLeft.x + (side - c.size.width) / 2, topLeft.y + side / 2 - middle))
                    return@drawBehind
                }
                // The bar sits at the right edge, past the screen gutter.
                val gutter = MetroDimens.Gutter.toPx()
                val inset = 6.dp.toPx()
                val left = size.width + gutter - CLEARED_BAR_INSET.toPx() - CLEARED_BAR.toPx()
                val height = size.height - extraAir.toPx()
                drawRect(bar, Offset(left, inset), Size(CLEARED_BAR.toPx(), height - 2 * inset))
                if (locked) {
                    drawLock(Offset(left + CLEARED_BAR.toPx() / 2, height / 2), ground)
                    return@drawBehind
                }
                if (pending) {
                    drawClock(Offset(left + CLEARED_BAR.toPx() / 2, height / 2), clockInk)
                    return@drawBehind
                }
                val c = measurer.measure(RegisterCopy.CLEARED_MARK, mark)
                drawText(c, topLeft = Offset(left + (CLEARED_BAR.toPx() - c.size.width) / 2, (height - c.size.height) / 2))
            }.metroPress(onPress, onLongPress)
            .padding(top = REGISTER_ROW_AIR, bottom = REGISTER_ROW_AIR + extraAir)
            .padding(
                end =
                    if (relief) {
                        CLEARED_KEY + CLEARED_BAR_GAP
                    } else {
                        CLEARED_BAR + CLEARED_BAR_INSET + CLEARED_BAR_GAP -
                            MetroDimens.Gutter
                    },
            ).semantics {
                stateDescription =
                    when {
                        locked -> RegisterCopy.LOCKED
                        pending -> RegisterCopy.PENDING
                        row.cleared -> RegisterCopy.CLEARED
                        else -> RegisterCopy.NOT_CLEARED
                    }
                if (picked != null) selected = picked
            }.testTag("register-row-${row.id}"),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            if (picked != null) {
                SelectBox(picked, Modifier.width(SELECT_BOX).padding(top = 2.dp).testTag("register-select-${row.id}"))
            }
            Column(Modifier.weight(LEDGER_NAME + LEDGER_FIGURE).padding(end = MetroDimens.Gutter)) {
                if (row.transferAccountId != null) {
                    // A transfer leads with its glyph, as Actual marks transfer payees.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppGlyphIcon(AppGlyph.Transfer, accent)
                        MetroText(row.payee, Metro.typography.body, Modifier.padding(start = 6.dp), maxLines = 2)
                    }
                } else {
                    MetroText(row.payee, Metro.typography.body, maxLines = 2)
                }
                MetroText(
                    detail,
                    Metro.typography.caption,
                    Modifier.testTag("register-detail-${row.id}"),
                    color = Metro.colors.secondary,
                    maxLines = 2,
                )
            }
            MetroText(
                MoneyFormat.format(row.amountMinor, page.currency, formatOptions()),
                Metro.typography.body.copy(textAlign = TextAlign.End),
                Modifier.weight(LEDGER_FIGURE * 2).testTag("register-amount-${row.id}"),
                color = accent,
                maxLines = 1,
                softWrap = false,
            )
        }
        row.parts.forEach { part ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = MetroDimens.Gutter + if (picked != null) SELECT_BOX else 0.dp, top = MetroDimens.Gutter),
                verticalAlignment = Alignment.Top,
            ) {
                MetroText(
                    part.categoryName,
                    Metro.typography.caption,
                    Modifier.weight(LEDGER_NAME + LEDGER_FIGURE).padding(end = MetroDimens.Gutter),
                    color = Metro.colors.foreground,
                    maxLines = 2,
                )
                MetroText(
                    MoneyFormat.format(part.amountMinor, page.currency, formatOptions()),
                    Metro.typography.caption.copy(textAlign = TextAlign.End),
                    Modifier.weight(LEDGER_FIGURE * 2),
                    color = Metro.colors.foreground,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** A square checkbox: accent with a check when [on], an outline when not. Mica draws Windows 11's instead. */
@Composable
private fun SelectBox(
    on: Boolean,
    modifier: Modifier = Modifier,
) {
    if (Metro.style.layers) {
        Box(modifier) { LayerCheckBox(on, Modifier.size(20.dp)) }
        return
    }
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

private val CLEARED_BAR = 16.dp

/** A locked row's bar: the foreground, a little softened, so it reads apart from the accent of cleared. */
private const val LOCK_ALPHA = 0.72f

/** A small padlock centred on [center]: a body with a shackle over it, Actual's mark for a reconciled row. */
internal fun DrawScope.drawLock(
    center: Offset,
    color: Color,
) {
    val w = 8.dp.toPx()
    val h = 6.dp.toPx()
    val top = center.y - h / 2 + 2.dp.toPx()
    drawRect(color, Offset(center.x - w / 2, top), Size(w, h))
    val r = 2.4.dp.toPx()
    val shackle =
        Path().apply {
            moveTo(center.x - r, top)
            lineTo(center.x - r, top - 1.5.dp.toPx())
            arcTo(Rect(center.x - r, top - 1.5.dp.toPx() - r, center.x + r, top - 1.5.dp.toPx() + r), 180f, 180f, false)
            lineTo(center.x + r, top)
        }
    drawPath(shackle, color, style = Stroke(1.5.dp.toPx()))
}

/** A small clock centred on [center]: a ring with its hands at four o'clock, the mark of a row not posted yet. */
internal fun DrawScope.drawClock(
    center: Offset,
    color: Color,
) {
    val r = 4.5.dp.toPx()
    val stroke = 1.3.dp.toPx()
    drawCircle(color, r, center, style = Stroke(stroke))
    drawLine(color, center, Offset(center.x, center.y - r * 0.62f), stroke, StrokeCap.Round)
    drawLine(color, center, Offset(center.x + r * 0.5f, center.y + r * 0.3f), stroke, StrokeCap.Round)
}

/** The cleared key of a relief style: a touch smaller than the payee's two lines. */
private val CLEARED_KEY = 28.dp

/** Selawik's x-height, as a fraction of its em: the cleared key centres its c on half of it. */
private const val SELAWIK_X_HEIGHT = 0.5f

private val CLEARED_BAR_INSET = 2.dp
private val CLEARED_BAR_GAP = 6.dp
private val SELECT_BOX = 32.dp

private val REGISTER_ROW_AIR = 15.dp

/** How strongly a possible match's rows take the accent. */
internal const val MATCH_TINT = 0.08f

/** The quieter line under a payee: category, then the note. The date heads its day; the bar shows cleared. */
internal fun registerDetail(row: RegisterRow): String =
    listOfNotNull(
        row.categoryLabel.ifBlank { null },
        row.note
            .trim()
            .replace(LINE_BREAK, " ")
            .ifBlank { null },
    ).joinToString(" · ")

/** A line break and the spaces around it, read as one space on the register's one-line detail. */
private val LINE_BREAK = Regex("\\s*\\R\\s*")

@Composable
fun ReconcileWarningScreen(
    onChange: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmPage(
        title = RegisterCopy.RECONCILE_CHANGE,
        body = RegisterCopy.RECONCILE_WARN,
        yes = "change",
        onYes = onChange,
        onKeep = onKeep,
        tag = "reconcile-warning",
        modifier = modifier,
    )
}
