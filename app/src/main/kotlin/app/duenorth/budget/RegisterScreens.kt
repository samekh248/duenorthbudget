package app.duenorth.budget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountChoice
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.CategoryTarget
import app.duenorth.budget.core.DateColumn
import app.duenorth.budget.core.DateFormats
import app.duenorth.budget.core.DatePick
import app.duenorth.budget.core.EntryResult
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.MatchCopy
import app.duenorth.budget.core.MatchPair
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MoneyParse
import app.duenorth.budget.core.RegisterCopy
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RegisterRow
import app.duenorth.budget.core.RegisterSearch
import app.duenorth.budget.core.RegisterShow
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.core.SplitPart
import app.duenorth.budget.core.TransactionDraft
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.LocalFieldSubmit
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroComboBox
import app.duenorth.budget.design.components.MetroComboValue
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroPickerColumn
import app.duenorth.budget.design.components.MetroSegmentedToggle
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.components.controlFrame
import app.duenorth.budget.design.components.drawRelief
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.Relief
import java.time.LocalDate
import java.time.Month
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import java.time.format.TextStyle as DateTextStyle

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
    val listed = rows.filter { it.id !in paired }
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
                    LedgerGroup(Modifier.padding(bottom = 4.dp), heading = {}) {
                        day.forEach { row ->
                            RegisterLine(
                                row,
                                page,
                                banded = false,
                                picked = selection?.let { row.id in it },
                                onPress = { onPress(row.id) },
                                onLongPress = { onLongPress(row.id) },
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
                )
            }
        }
    }
}

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
) {
    val accent = Metro.accent.text
    val fill =
        when {
            picked == true -> accent.copy(alpha = 0.10f)
            banded -> ledgerBand
            else -> Color.Transparent
        }
    val bar = if (row.cleared) accent else Metro.colors.secondary.copy(alpha = 0.22f)
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
                    val topLeft = Offset(size.width - side, (size.height - side) / 2)
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
                        if (row.cleared) accentFill else ground,
                        small = true,
                    )
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
                drawRect(bar, Offset(left, inset), Size(CLEARED_BAR.toPx(), size.height - 2 * inset))
                val c = measurer.measure(RegisterCopy.CLEARED_MARK, mark)
                drawText(c, topLeft = Offset(left + (CLEARED_BAR.toPx() - c.size.width) / 2, (size.height - c.size.height) / 2))
            }.metroPress(onPress, onLongPress)
            .padding(vertical = REGISTER_ROW_AIR)
            .padding(
                end =
                    if (relief) {
                        CLEARED_KEY + CLEARED_BAR_GAP
                    } else {
                        CLEARED_BAR + CLEARED_BAR_INSET + CLEARED_BAR_GAP -
                            MetroDimens.Gutter
                    },
            ).semantics {
                stateDescription = if (row.cleared) RegisterCopy.CLEARED else RegisterCopy.NOT_CLEARED
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

/** A square checkbox: accent with a check when [on], an outline when not. */
@Composable
private fun SelectBox(
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

private val CLEARED_BAR = 16.dp

/** The cleared key of a relief style: a touch smaller than the payee's two lines. */
private val CLEARED_KEY = 28.dp

/** Selawik's x-height, as a fraction of its em: the cleared key centres its c on half of it. */
private const val SELAWIK_X_HEIGHT = 0.5f

private val CLEARED_BAR_INSET = 2.dp
private val CLEARED_BAR_GAP = 6.dp
private val SELECT_BOX = 32.dp

private val REGISTER_ROW_AIR = 15.dp

/** How strongly a possible match's rows take the accent. */
private const val MATCH_TINT = 0.08f

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
fun TransactionForm(
    page: RegisterPage,
    row: RegisterRow?,
    initialDate: String,
    error: String?,
    onSave: (TransactionDraft) -> Unit,
    modifier: Modifier = Modifier,
    dateFormat: String = FormatPrefs().dateFormat,
    numberFormat: String = FormatPrefs().numberFormat,
    hideFraction: Boolean = FormatPrefs().hideFraction,
    payees: List<String> = emptyList(),
    /** A new transaction's starting amount, signed: a reconcile's "add it" fills in what balances it. */
    initialAmountMinor: Long? = null,
    /** A new transaction from home: no account until one is picked, rather than the first in the list. */
    chooseAccount: Boolean = false,
) {
    val draftId = remember(row?.id, page.account.id) { row?.id ?: UUID.randomUUID().toString() }
    var date by remember(row?.id) {
        mutableIntStateOf(row?.date ?: RegisterEntry.parseDate(initialDate) ?: RegisterEntry.parseDate(RegisterEntry.todayIso())!!)
    }
    var payee by remember(row?.id) {
        mutableStateOf(if (row == null || row.payee == ShellCopy.NO_PAYEE) "" else row.payee)
    }
    val startAmount = row?.amountMinor ?: initialAmountMinor
    var amount by remember(row?.id) {
        mutableStateOf(
            startAmount
                ?.let {
                    MoneyParse.editText(it, page.currency.decimals, FormatOptions(hideFraction, numberFormat))
                }.orEmpty(),
        )
    }
    var expense by remember(row?.id) { mutableStateOf(startAmount?.let { it <= 0L } ?: true) }
    var note by remember(row?.id) { mutableStateOf(row?.note.orEmpty()) }
    var categoryId by remember(row?.id) { mutableStateOf(row?.categoryId ?: row?.partnerCategoryId) }
    // The account the transaction sits in: the register's, or the one picked here (a new one from home starts on none).
    var accountId by remember(row?.id, page.account.id) {
        mutableStateOf(if (chooseAccount && row == null) null else page.account.id)
    }
    // A new transaction starts uncleared, unless a reconcile's "add it" fills in one the statement already shows.
    var cleared by remember(row?.id) { mutableStateOf(row?.cleared ?: (initialAmountMinor != null)) }
    val accountChoices =
        remember(page.accounts, page.account, accountId) {
            val open = page.accounts.filter { !it.closed || it.id == accountId }
            val own = page.account.let { AccountChoice(it.id, it.name, it.offBudget, it.type, it.closed) }
            if (open.any { it.id == own.id } || accountId != own.id) open else listOf(own) + open
        }
    val account = page.accounts.firstOrNull { it.id == accountId }
    val accountName = account?.name ?: if (accountId == null) TransactionFormCopy.NEW else page.account.name
    val offBudget = account?.offBudget ?: page.account.offBudget
    // The account this transaction transfers to or from, picked from the payee box (option C, #76).
    var transferTo by remember(row?.id) { mutableStateOf(row?.transferAccountId) }
    val transferAccount = transferTo?.let { id -> page.accounts.firstOrNull { it.id == id } }
    var fieldError by remember(row?.id) { mutableStateOf<Pair<TransactionField, String>?>(null) }
    var overlay by remember(row?.id) { mutableStateOf<FormOverlay>(FormOverlay.None) }
    // A transfer between two budget accounts takes no category; one that crosses the budget's edge does, as in Actual.
    val mixed = transferAccount != null && transferAccount.offBudget != offBudget
    // Split lines replace the category box; the form starts on one category and add split adds a line at a time.
    var splits by remember(row?.id) {
        mutableStateOf(
            row?.parts.orEmpty().map { part ->
                val text = MoneyParse.editText(abs(part.amountMinor), page.currency.decimals, FormatOptions(hideFraction, numberFormat))
                SplitLine(part.categoryId, text)
            },
        )
    }
    val splitting = splits.isNotEmpty() && transferTo == null
    val showCategories = !splitting && (transferTo == null || mixed)
    val options = remember(page.categories, categoryId) { CategoryOptions.of(page.categories, categoryId) }
    RememberFormAction(FORM_ADD_SPLIT) {
        // A transfer has no split; the first tap turns the category into the first line.
        if (transferTo == null) {
            splits = if (splits.isEmpty()) listOf(SplitLine(categoryId, ""), SplitLine(null, "")) else splits + SplitLine(null, "")
        }
    }

    fun splitAmount(line: SplitLine): Long? = RegisterEntry.signedAmount(line.amount, page.currency.decimals, expense, numberFormat)

    fun splitParts(total: Long): Pair<List<SplitPart>?, String?> {
        if (!splitting) return (if (row?.parent == true) emptyList<SplitPart>() else null) to null
        val parts =
            splits.map { line ->
                val category = line.categoryId ?: return null to RegisterCopy.CHOOSE_CATEGORY
                SplitPart(category, splitAmount(line) ?: return null to RegisterCopy.ENTER_AMOUNT)
            }
        return if (parts.sumOf { it.amountMinor } == total) parts to null else null to RegisterCopy.SPLIT_MUST_ADD
    }
    val commit = commit@{
        val target =
            accountId ?: run {
                fieldError = TransactionField.ACCOUNT to TransactionFormCopy.CHOOSE_ACCOUNT
                return@commit
            }
        when (
            val parsed =
                RegisterEntry.transaction(
                    id = draftId,
                    accountId = target,
                    date = date,
                    payee = transferAccount?.name ?: payee,
                    amountText = amount,
                    decimals = page.currency.decimals,
                    expense = expense,
                    categoryId = if (showCategories) categoryId else null,
                    note = note,
                    numberFormat = numberFormat,
                    transferAccountId = transferTo,
                )
        ) {
            is EntryResult.Ok -> {
                val (parts, refused) = splitParts(parsed.value.amountMinor)
                if (refused != null) {
                    fieldError = TransactionField.CATEGORY to refused
                } else {
                    fieldError = null
                    onSave(parsed.value.copy(cleared = cleared, parts = parts))
                }
            }
            is EntryResult.Rejected -> fieldError = TransactionField.of(parsed.reason) to parsed.reason
        }
    }
    RememberFormSave(commit)

    fun errorFor(field: TransactionField): String? =
        fieldError?.takeIf { it.first == field }?.second ?: error?.takeIf { field == TransactionField.NOTE && fieldError == null }
    Box(modifier.background(Metro.colors.background).testTag("transaction-form")) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MetroDimens.Gutter)
                .padding(bottom = MetroDimens.Gutter),
        ) {
            // The page is titled with the transaction's account, under the area's line.
            MetroText(
                accountName,
                Metro.typography.header,
                Modifier.testTag("transaction-account"),
                maxLines = 1,
                softWrap = false,
            )
            LabeledField(
                TransactionFormCopy.ACCOUNT,
                errorFor(TransactionField.ACCOUNT),
                Modifier.padding(top = MetroDimens.FieldGroupGap),
            ) {
                MetroComboBox<String?>(
                    choices = accountChoices.map { it.id },
                    selected = accountId,
                    label = { id -> accountChoices.firstOrNull { it.id == id }?.name.orEmpty() },
                    onPick = { picked ->
                        accountId = picked
                        if (fieldError?.first == TransactionField.ACCOUNT) fieldError = null
                        // A transfer can't go to the account it comes from.
                        if (transferTo == picked) {
                            transferTo = null
                            payee = ""
                        }
                    },
                    tag = "transaction-account-picker",
                )
            }
            LabeledField(TransactionFormCopy.DATE, errorFor(TransactionField.DATE), Modifier.padding(top = MetroDimens.FieldGroupGap)) {
                val width = rememberFieldWidth(DateFormats.widestSample(dateFormat), Metro.typography.control, DATE_CHEVRON)
                Box(
                    Modifier
                        .widthIn(max = width)
                        .fillMaxWidth()
                        .controlFrame()
                        .testTag("transaction-date"),
                ) {
                    MetroComboValue(
                        text = DateFormats.withWeekday(date, dateFormat),
                        tag = "transaction-date-value",
                        onClick = { overlay = FormOverlay.DatePicker(date) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            LabeledField(TransactionFormCopy.PAYEE, errorFor(TransactionField.PAYEE), Modifier.padding(top = MetroDimens.FieldGroupGap)) {
                if (transferTo != null) {
                    TransferTile(
                        RegisterEntry.transferLabel(if (expense) -1 else 1, transferAccount?.name.orEmpty()),
                        onClear = {
                            transferTo = null
                            payee = ""
                        },
                    )
                } else {
                    PayeeField(
                        payee,
                        { payee = it },
                        payees,
                        TransactionFormCopy.PAYEE,
                        "transaction-payee",
                        transfers = { query -> PayeeTypeAhead.transfers(page.accounts, accountId, query) },
                        expense = expense,
                        onTransfer = { transferTo = it.id },
                    )
                }
            }
            LabeledField(TransactionFormCopy.AMOUNT, errorFor(TransactionField.AMOUNT), Modifier.padding(top = MetroDimens.FieldGroupGap)) {
                AmountLine(
                    amount = amount,
                    onAmount = { amount = it },
                    expense = expense,
                    onExpense = { expense = it },
                    sample = amountSample(page, numberFormat),
                )
            }
            if (showCategories) {
                LabeledField(
                    TransactionFormCopy.CATEGORY,
                    errorFor(TransactionField.CATEGORY),
                    Modifier.padding(top = MetroDimens.FieldGroupGap),
                ) {
                    MetroComboBox(
                        choices = options.choices,
                        selected = categoryId,
                        label = { id -> options.label(id) },
                        onPick = { categoryId = it },
                        tag = "transaction-category",
                        fullScreenThreshold = CategoryOptions.IN_PLACE_LIMIT,
                        onOpenFull = { overlay = FormOverlay.CategoryList() },
                    )
                }
            }
            if (splitting) {
                LabeledField(
                    TransactionFormCopy.SPLIT,
                    errorFor(TransactionField.CATEGORY),
                    Modifier.padding(top = MetroDimens.FieldGroupGap),
                ) {
                    SplitLines(
                        lines = splits,
                        categories = page.categories,
                        left =
                            (RegisterEntry.signedAmount(amount, page.currency.decimals, expense, numberFormat) ?: 0L) -
                                splits.sumOf { splitAmount(it) ?: 0L },
                        expense = expense,
                        format = { MoneyFormat.format(it, page.currency, FormatOptions(hideFraction, numberFormat)) },
                        onLine = { index, line -> splits = splits.mapIndexed { i, old -> if (i == index) line else old } },
                        onRemove = { index ->
                            val rest = splits.filterIndexed { i, _ -> i != index }
                            // One line left is no split: it becomes the category again.
                            if (rest.size < 2) {
                                categoryId = rest.firstOrNull()?.categoryId
                                splits = emptyList()
                            } else {
                                splits = rest
                            }
                        },
                        onOpenFull = { overlay = FormOverlay.CategoryList(it) },
                    )
                }
            }
            LabeledField(TransactionFormCopy.CLEARED, null, Modifier.padding(top = MetroDimens.FieldGroupGap)) {
                MetroSegmentedToggle(
                    first = TransactionFormCopy.UNCLEARED,
                    second = TransactionFormCopy.CLEARED,
                    firstSelected = !cleared,
                    onSelect = { cleared = !it },
                    tag = "transaction-cleared",
                )
            }
            LabeledField(TransactionFormCopy.NOTE, errorFor(TransactionField.NOTE), Modifier.padding(top = MetroDimens.FieldGroupGap)) {
                MetroField(
                    note,
                    { note = it },
                    TransactionFormCopy.NOTE,
                    Modifier.testTag("transaction-note"),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                    lines = NOTE_LINES,
                )
            }
            if (transferAccount != null) {
                TransferPreview(
                    page = page,
                    from = accountName,
                    other = transferAccount,
                    adding = row?.transferId == null,
                    signed = RegisterEntry.signedAmount(amount, page.currency.decimals, expense, numberFormat),
                    date = date,
                    category = if (showCategories) categoryId?.let(options::name) else null,
                    format = FormatOptions(hideFraction, numberFormat),
                )
            }
        }
        when (val open = overlay) {
            FormOverlay.None -> Unit
            is FormOverlay.CategoryList ->
                CategoryListPage(
                    options = open.line?.let { CategoryOptions.of(page.categories, splits.getOrNull(it)?.categoryId) } ?: options,
                    onPick = { picked ->
                        val line = open.line
                        if (line == null) {
                            categoryId = picked
                        } else {
                            splits = splits.mapIndexed { i, old -> if (i == line) old.copy(categoryId = picked) else old }
                        }
                        overlay = FormOverlay.None
                    },
                    onBack = { overlay = FormOverlay.None },
                )
            is FormOverlay.DatePicker ->
                DatePickerPage(
                    date = open.draft,
                    dateFormat = dateFormat,
                    onChange = { overlay = FormOverlay.DatePicker(it) },
                    onDone = {
                        date = open.draft
                        overlay = FormOverlay.None
                    },
                    onCancel = { overlay = FormOverlay.None },
                )
        }
    }
}

/** The bar's add split action, which the transaction form carries out. */
const val FORM_ADD_SPLIT = "add-split"

/** One split line as typed: its category (none picked yet is null) and its amount box. */
data class SplitLine(
    val categoryId: String?,
    val amount: String,
)

/**
 * The split lines in place of the category box: each a category and its amount, with a remove button,
 * then what is left to assign of the transaction's amount (option A).
 */
@Composable
private fun SplitLines(
    lines: List<SplitLine>,
    categories: List<CategoryChoice>,
    left: Long,
    expense: Boolean,
    format: (Long) -> String,
    onLine: (Int, SplitLine) -> Unit,
    onRemove: (Int) -> Unit,
    onOpenFull: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("transaction-split"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEachIndexed { index, line ->
            val options = remember(categories, line.categoryId) { CategoryOptions.of(categories, line.categoryId) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MetroComboBox(
                    choices = options.choices,
                    selected = line.categoryId,
                    label = { id -> if (id == null) TransactionFormCopy.CHOOSE_CATEGORY else options.label(id) },
                    onPick = { onLine(index, line.copy(categoryId = it)) },
                    tag = "transaction-split-$index-category",
                    modifier = Modifier.weight(1f),
                    fullScreenThreshold = CategoryOptions.IN_PLACE_LIMIT,
                    onOpenFull = { onOpenFull(index) },
                )
                MetroField(
                    line.amount,
                    { onLine(index, line.copy(amount = it)) },
                    TransactionFormCopy.AMOUNT,
                    Modifier.width(SPLIT_AMOUNT_WIDTH).testTag("transaction-split-$index-amount"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    fill = false,
                )
                Box(
                    Modifier
                        .size(MetroDimens.TouchTarget)
                        .metroPress { onRemove(index) }
                        .semantics { contentDescription = TransactionFormCopy.REMOVE_LINE }
                        .testTag("transaction-split-$index-remove"),
                    contentAlignment = Alignment.Center,
                ) {
                    AppGlyphIcon(AppGlyph.Cancel, Metro.colors.secondary)
                }
            }
        }
        // Money out splits into money out: the sign follows the transaction's, so over means past the total.
        val over = left != 0L && (left < 0L) != expense
        MetroText(
            when {
                left == 0L -> TransactionFormCopy.ADDS_UP
                over -> "${format(abs(left))} ${TransactionFormCopy.OVER}"
                else -> "${format(abs(left))} ${TransactionFormCopy.LEFT}"
            },
            Metro.typography.body,
            Modifier.testTag("transaction-split-left"),
            color = if (left == 0L) Metro.colors.secondary else Metro.accent.text,
        )
    }
}

/** The picked transfer, in place of the payee box: an accent tile reading "transfer to" the account, with a clear button. */
@Composable
private fun TransferTile(
    label: String,
    onClear: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .background(Metro.accent.fill)
            .padding(start = 12.dp)
            .testTag("transaction-transfer"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyphIcon(AppGlyph.Transfer, Metro.accent.onFill)
        MetroText(
            label,
            Metro.typography.control,
            Modifier.weight(1f).padding(start = 10.dp).testTag("transaction-transfer-label"),
            color = Metro.accent.onFill,
            maxLines = 1,
        )
        Box(
            Modifier
                .size(MetroDimens.TouchTarget)
                .metroPress(onClear)
                .semantics { contentDescription = TransactionFormCopy.CLEAR_TRANSFER }
                .testTag("transaction-transfer-clear"),
            contentAlignment = Alignment.Center,
        ) {
            AppGlyphIcon(AppGlyph.Cancel, Metro.accent.onFill)
        }
    }
}

/** The transaction a transfer adds or keeps in step in the other account, as it will read on that register. */
@Composable
private fun TransferPreview(
    page: RegisterPage,
    from: String,
    other: AccountChoice,
    adding: Boolean,
    signed: Long?,
    date: Int,
    category: String?,
    format: FormatOptions,
) {
    Column(Modifier.fillMaxWidth().padding(top = MetroDimens.FieldGroupGap).testTag("transaction-transfer-preview")) {
        MetroText(
            "${if (adding) TransactionFormCopy.ALSO_ADDS else TransactionFormCopy.ALSO_CHANGES} ${other.name}",
            Metro.typography.caption,
            color = Metro.accent.text,
        )
        Row(
            Modifier
                .padding(top = MetroDimens.FieldLabelGap)
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(Metro.accent.text.copy(alpha = MATCH_TINT)),
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(Metro.accent.fill))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
                MetroText(RegisterEntry.transferLabel(-(signed ?: -1L), from), Metro.typography.body, maxLines = 2)
                MetroText(
                    listOf(other.name, RegisterEntry.registerDateLabel(date), category ?: RegisterCopy.NO_CATEGORY).joinToString(" · "),
                    Metro.typography.caption,
                    color = Metro.colors.secondary,
                    maxLines = 2,
                )
            }
            if (signed != null) {
                MetroText(
                    MoneyFormat.format(-signed, page.currency, format),
                    Metro.typography.body,
                    Modifier.padding(10.dp).testTag("transaction-transfer-preview-amount"),
                    color = Metro.accent.text,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

object TransactionFormCopy {
    const val ACCOUNT = "account"
    const val DATE = "date"
    const val PAYEE = "payee"
    const val AMOUNT = "amount"
    const val CATEGORY = "category"
    const val NOTE = "note"
    const val CLEARED = "cleared"
    const val SPLIT = "split"
    const val LEFT = "left to assign"
    const val OVER = "over"
    const val ADDS_UP = "split adds up"
    const val REMOVE_LINE = "remove line"
    const val CHOOSE_CATEGORY = "category"
    const val UNCLEARED = "uncleared"
    const val EXPENSE = "expense"
    const val INCOME = "income"
    const val CHOOSE_DATE = "choose date"
    const val DONE = "done"
    const val CANCEL = "cancel"
    const val HIDDEN = "(hidden)"
    const val ALSO_ADDS = "also adds to"
    const val ALSO_CHANGES = "also changes in"
    const val CLEAR_TRANSFER = "not a transfer"
    const val NEW = "new transaction"
    const val CHOOSE_ACCOUNT = "choose an account"
}

/** Which field a save refusal belongs to, so its message sits under that field (FR-022). */
enum class TransactionField {
    ACCOUNT,
    DATE,
    PAYEE,
    AMOUNT,
    CATEGORY,
    NOTE,
    ;

    companion object {
        fun of(reason: String): TransactionField =
            when (reason) {
                RegisterCopy.ENTER_DATE -> DATE
                RegisterCopy.SPLIT_MUST_ADD -> CATEGORY
                RegisterCopy.ENTER_PAYEE -> PAYEE
                RegisterCopy.ENTER_AMOUNT, RegisterCopy.AMOUNT_ZERO -> AMOUNT
                RegisterCopy.CHOOSE_CATEGORY -> CATEGORY
                else -> NOTE
            }
    }
}

/** A page drawn over the transaction form; the form's edits stay where they are underneath. */
sealed interface FormOverlay {
    data object None : FormOverlay

    /** The full category list, for the category box or, with [line], a split line. */
    data class CategoryList(
        val line: Int? = null,
    ) : FormOverlay

    data class DatePicker(
        val draft: Int,
    ) : FormOverlay
}

/**
 * The category choices the transaction form offers: "no category" first, then visible categories
 * in budget order, plus a hidden category only when it is the current one (spec 013).
 */
class CategoryOptions private constructor(
    val groups: List<Pair<String, List<CategoryChoice>>>,
    val current: String?,
) {
    /** Category ids in list order, `null` standing for "no category". */
    val choices: List<String?> = listOf<String?>(null) + groups.flatMap { (_, rows) -> rows.map { it.id } }

    private val byId = groups.flatMap { it.second }.associateBy { it.id }

    fun label(id: String?): String {
        if (id == null) return RegisterCopy.NO_CATEGORY
        val choice = byId[id] ?: return ""
        return if (choice.hidden) "${choice.name} ${TransactionFormCopy.HIDDEN}" else choice.name
    }

    /** The category's own name, without the hidden mark. */
    fun name(id: String): String = byId[id]?.name.orEmpty()

    companion object {
        /** WP8.1 unfolds a combo box in place up to five items; past that it opens a full-screen list. */
        const val IN_PLACE_LIMIT = 5

        fun of(
            categories: List<CategoryChoice>,
            current: String?,
        ): CategoryOptions {
            val shown = categories.filter { !it.hidden || it.id == current }
            val groups =
                shown
                    .groupBy { it.groupName }
                    .map { (name, rows) -> name to rows }
            return CategoryOptions(groups, current)
        }
    }
}

private val NOTE_LINES = 2..6
private val SPLIT_AMOUNT_WIDTH = 110.dp
private val DATE_CHEVRON = 42.dp
private const val AMOUNT_WHOLE = 9_999_999L

/** The widest amount the box is sized for: seven whole digits in the budget's format and decimals. */
private fun amountSample(
    page: RegisterPage,
    numberFormat: String,
): String {
    var scale = 1L
    repeat(page.currency.decimals) { scale *= 10 }
    val text = MoneyFormat.format(AMOUNT_WHOLE * scale, page.currency, FormatOptions(numberFormat = numberFormat))
    return text
        .filter {
            it.isDigit() || it == '.' || it == ',' || it == ' ' || it == ' '
        }.map { if (it.isDigit()) '0' else it }
        .joinToString("")
}

/** A field's width: [sample] measured in [style], plus the field's padding and [extra] (a chevron). */
@Composable
private fun rememberFieldWidth(
    sample: String,
    style: TextStyle,
    extra: Dp = 0.dp,
): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(sample, style, extra, density) {
        val px = measurer.measure(sample, style, maxLines = 1, softWrap = false).size.width
        with(density) { px.toDp() } + FIELD_PADDING * 2 + extra + 2.dp
    }
}

private val FIELD_PADDING = 10.dp

/** A label over its control, a quarter grid step apart, and the field's error under it (FR-002, FR-020, FR-022). */
@Composable
private fun LabeledField(
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        MetroText(label, Metro.typography.caption, Modifier.padding(bottom = MetroDimens.FieldLabelGap), color = Metro.colors.secondary)
        content()
        if (error != null) {
            MetroText(
                error,
                Metro.typography.body,
                Modifier
                    .padding(
                        top = MetroDimens.FieldLabelGap,
                    ).testTag("transaction-error-${TransactionField.of(error).name.lowercase()}"),
                color = Metro.accent.text,
            )
        }
    }
}

/** The amount box sized to its longest value, with expense/income beside it; the toggle wraps under at large fonts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountLine(
    amount: String,
    onAmount: (String) -> Unit,
    expense: Boolean,
    onExpense: (Boolean) -> Unit,
    sample: String,
) {
    val width = rememberFieldWidth(sample, Metro.typography.control)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(MetroDimens.FieldLabelGap),
    ) {
        MetroField(
            amount,
            onAmount,
            TransactionFormCopy.AMOUNT,
            Modifier.width(width).testTag("amount-field"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            fill = false,
        )
        MetroSegmentedToggle(
            first = TransactionFormCopy.EXPENSE,
            second = TransactionFormCopy.INCOME,
            firstSelected = expense,
            onSelect = onExpense,
            tag = "transaction-direction",
        )
    }
}

/**
 * The full-screen category list for budgets with more than five choices (FR-011, FR-012), under a focused
 * filter box that holds the current category's name, selected, so typing replaces it and narrows the list.
 */
@Composable
private fun CategoryListPage(
    options: CategoryOptions,
    onPick: (String?) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    RememberBarOverride(emptyList())
    val filter = rememberCategoryFilter(options.current?.let(options::name).orEmpty())
    val query = filter.query
    val rows =
        remember(options, query) {
            buildList<Pair<String, String?>?> {
                if (query.isBlank()) add(null)
                options.groups.forEach { (name, choices) ->
                    val shown = CategoryFilter.choices(choices, query)
                    if (shown.isNotEmpty()) {
                        add(name to null)
                        shown.forEach { add("" to it.id) }
                    }
                }
                if (query.isNotBlank() && CategoryFilter.showsNone(query)) add(null)
            }
        }
    val start = remember(options) { rows.indexOfFirst { it?.second != null && it.second == options.current }.coerceAtLeast(0) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
    LaunchedEffect(query) { if (filter.typed) state.scrollToItem(0) }
    val enter = {
        if (!filter.typed) {
            onPick(options.current)
        } else {
            val top = rows.indexOfFirst { it == null || it.second != null }
            if (top >= 0) onPick(rows[top]?.second)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(horizontal = MetroDimens.Gutter)
            .testTag("transaction-category-list"),
    ) {
        MetroText(TransactionFormCopy.CATEGORY, Metro.typography.header, maxLines = 1)
        CategoryFilterField(filter, enter, Modifier.padding(top = MetroDimens.Gutter))
        LazyColumn(Modifier.weight(1f).padding(top = MetroDimens.Gutter), state = state) {
            items(rows.size) { index ->
                val entry = rows[index]
                when {
                    entry == null ->
                        CategoryListRow(RegisterCopy.NO_CATEGORY, options.current == null, "transaction-category-none") { onPick(null) }
                    entry.second == null ->
                        MetroText(
                            entry.first.ifBlank { " " },
                            Metro.typography.caption,
                            Modifier.padding(top = MetroDimens.Gutter).testTag("transaction-category-group-$index"),
                            color = Metro.colors.secondary,
                        )
                    else -> {
                        val id = entry.second!!
                        CategoryListRow(options.label(id), id == options.current, "transaction-category-$id") { onPick(id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryListRow(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    MetroText(
        label,
        Metro.typography.subheader,
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick)
            .padding(vertical = MetroDimens.Row)
            .testTag(tag),
        color = if (selected) Metro.accent.text else Metro.colors.foreground,
        maxLines = 2,
    )
}

/** The WP8.1 date picker page: looping month, day and year columns in the settings order (FR-005–FR-007). */
@Composable
internal fun DatePickerPage(
    date: Int,
    dateFormat: String,
    onChange: (Int) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    val done by rememberUpdatedState(onDone)
    val cancel by rememberUpdatedState(onCancel)
    val bar =
        remember {
            listOf(
                ScreenAction("picker-done", AppGlyph.Save, TransactionFormCopy.DONE) { done() },
                ScreenAction("picker-cancel", AppGlyph.Cancel, TransactionFormCopy.CANCEL) { cancel() },
            )
        }
    RememberBarOverride(bar)
    val years = DatePick.yearRange(date)
    Column(
        Modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(horizontal = MetroDimens.Gutter)
            .testTag("transaction-date-picker"),
    ) {
        MetroText(TransactionFormCopy.CHOOSE_DATE, Metro.typography.header, maxLines = 1)
        Row(
            Modifier.fillMaxWidth().padding(top = MetroDimens.Grid),
            horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter),
        ) {
            DatePick.columns(dateFormat).forEach { column ->
                val cell = Modifier.weight(1f)
                when (column) {
                    DateColumn.MONTH ->
                        MetroPickerColumn(
                            count = 12,
                            position = DatePick.month(date) - 1,
                            label = { at ->
                                "%02d".format(at + 1) to
                                    Month.of(at + 1).getDisplayName(DateTextStyle.SHORT, Locale.ENGLISH).lowercase()
                            },
                            onPick = { at -> onChange(DatePick.withMonth(date, at + 1)) },
                            tag = "date-column-month",
                            modifier = cell,
                        )
                    DateColumn.DAY -> {
                        val days = DatePick.daysIn(DatePick.year(date), DatePick.month(date))
                        MetroPickerColumn(
                            count = days,
                            position = DatePick.day(date) - 1,
                            label = { at ->
                                val weekday =
                                    LocalDate
                                        .of(DatePick.year(date), DatePick.month(date), at + 1)
                                        .dayOfWeek
                                        .getDisplayName(DateTextStyle.FULL, Locale.ENGLISH)
                                        .lowercase()
                                "%02d".format(at + 1) to weekday
                            },
                            onPick = { at -> onChange(DatePick.withDay(date, at + 1)) },
                            tag = "date-column-day",
                            modifier = cell,
                        )
                    }
                    DateColumn.YEAR ->
                        MetroPickerColumn(
                            count = years.last - years.first + 1,
                            position = DatePick.year(date) - years.first,
                            label = { at -> (years.first + at).toString() to "" },
                            onPick = { at -> onChange(DatePick.withYear(date, years.first + at)) },
                            tag = "date-column-year",
                            modifier = cell,
                        )
                }
            }
        }
    }
}

@Composable
fun SplitForm(
    page: RegisterPage,
    row: RegisterRow,
    error: String?,
    onSave: (String, List<SplitPart>) -> Unit,
    onUnsplit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = LocalFormatPrefs.current
    val seed =
        if (row.parts.size >= 2) {
            row.parts.map { it.categoryId to prefs.editAmount(it.amountMinor, page.currency.decimals) }
        } else {
            listOf((row.categoryId ?: "") to prefs.editAmount(row.amountMinor, page.currency.decimals), "" to "")
        }
    var lines by remember(row.id) { mutableStateOf(seed) }
    var localError by remember(row.id) { mutableStateOf<String?>(null) }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
    ) {
        MetroText("split", Metro.typography.header)
        MetroText(
            MoneyFormat.format(row.amountMinor, page.currency, formatOptions()),
            Metro.typography.subheader,
            color = Metro.accent.text,
        )
        val shown = localError ?: error
        if (shown != null) {
            MetroText(shown, Metro.typography.body, color = Metro.accent.text)
        }
        LazyColumn(Modifier.weight(1f).padding(top = MetroDimens.Grid)) {
            items(lines.size) { index ->
                SplitLineEditor(
                    index = index,
                    amount = lines[index].second,
                    categoryId = lines[index].first,
                    categories = page.categories,
                    onAmount = { text ->
                        lines = lines.mapIndexed { i, line -> if (i == index) line.first to text else line }
                    },
                    onCategory = { category ->
                        lines = lines.mapIndexed { i, line -> if (i == index) category to line.second else line }
                    },
                )
            }
        }
        val commitSplit = {
            val typed = lines.map { (category, text) -> category to prefs.readAmount(text, page.currency.decimals) }
            when (val parsed = RegisterEntry.split(row.amountMinor, page.currency.decimals, typed)) {
                is EntryResult.Ok -> {
                    localError = null
                    onSave(row.id, parsed.value)
                }
                is EntryResult.Rejected -> localError = parsed.reason
            }
        }
        RememberFormSave(commitSplit)
        // In the app add split sits on the bar and one category in its menu, as on a transaction's details.
        RememberFormAction(FORM_ADD_SPLIT) { lines = lines + ("" to "") }
        if (!formSaveOnBar()) {
            MetroButtonGrid(Modifier.padding(top = MetroDimens.Grid)) {
                MetroButton(RegisterCopy.ADD_SPLIT) { lines = lines + ("" to "") }
                MetroButton("save", onClick = commitSplit)
                if (row.parent) {
                    MetroButton(RegisterCopy.ONE_CATEGORY) { onUnsplit(row.id) }
                }
            }
        }
    }
}

@Composable
private fun SplitLineEditor(
    index: Int,
    amount: String,
    categoryId: String,
    categories: List<CategoryChoice>,
    onAmount: (String) -> Unit,
    onCategory: (String) -> Unit,
) {
    Column(Modifier.padding(top = MetroDimens.Grid)) {
        MetroText("part ${index + 1}", Metro.typography.caption, color = Metro.colors.secondary)
        MetroField(amount, onAmount, "amount", Modifier.padding(top = MetroDimens.Gutter))
        categories.forEach { category ->
            ChoiceLine(category.name, category.id == categoryId) { onCategory(category.id) }
        }
    }
}

@Composable
fun CategoryPickerScreen(
    target: CategoryTarget,
    error: String?,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = target.categoryId?.let { id -> target.categories.firstOrNull { it.id == id } }
    val filter = rememberCategoryFilter(current?.name.orEmpty())
    val query = filter.query
    val shown = remember(target.categories, query) { CategoryFilter.choices(target.categories, query) }
    val none = CategoryFilter.showsNone(query)
    val enter = {
        when {
            !filter.typed -> onPick(current?.id)
            query.isBlank() && none -> onPick(null)
            shown.isNotEmpty() -> onPick(shown.first().id)
            none -> onPick(null)
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
    ) {
        MetroText(target.payee, Metro.typography.header, maxLines = 2)
        MetroText(
            MoneyFormat.format(target.amountMinor, target.currency, formatOptions()),
            Metro.typography.subheader,
            color = Metro.accent.text,
        )
        if (error != null) {
            MetroText(error, Metro.typography.body, Modifier.padding(top = MetroDimens.Gutter), color = Metro.accent.text)
        }
        CategoryFilterField(filter, enter, Modifier.padding(top = MetroDimens.Grid))
        val list = rememberLazyListState()
        LaunchedEffect(query) { if (filter.typed) list.scrollToItem(0) }
        LazyColumn(Modifier.weight(1f).padding(top = MetroDimens.Gutter), state = list) {
            if (query.isBlank()) {
                item { ChoiceLine(RegisterCopy.NO_CATEGORY, current == null) { onPick(null) } }
            }
            items(shown, key = { it.id }) { category ->
                ChoiceLine(category.name, category.id == current?.id) { onPick(category.id) }
            }
            if (query.isNotBlank() && none) {
                item { ChoiceLine(RegisterCopy.NO_CATEGORY, current == null) { onPick(null) } }
            }
        }
    }
}

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

@Composable
private fun DirectionChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    MetroText(
        label,
        Metro.typography.subheader,
        Modifier.heightIn(min = MetroDimens.TouchTarget).metroPress(onClick).padding(vertical = MetroDimens.Row),
        color = if (selected) Metro.accent.text else Metro.colors.foreground,
    )
}

@Composable
private fun ChoiceLine(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    MetroText(
        label,
        Metro.typography.subheader,
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick)
            .padding(vertical = MetroDimens.Row),
        color = if (selected) Metro.accent.text else Metro.colors.foreground,
        maxLines = 2,
    )
}
