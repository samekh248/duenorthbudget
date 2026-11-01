package app.duenorth.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duenorth.budget.core.DateFormats
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.ReconcileCopy
import app.duenorth.budget.core.ReconcileHelp
import app.duenorth.budget.core.ReconcileHint
import app.duenorth.budget.core.ReconcilePage
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RegisterRow
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.MetroComboValue
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.components.controlFrame
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

/**
 * Reconcile on the register itself: the account's own rows, with a reconcile header where the balance
 * was. Before a statement is entered the header asks for it, starting at the cleared balance; after,
 * it shows statement, cleared and the difference over a strip of locked, cleared and not cleared rows,
 * tapping a row clears or unclears it, and when the numbers are off a numbered list of leads points at the
 * likeliest causes, each with a one-tap fix and what it would leave. Locked (reconciled) rows sit under their
 * own heading with a lock for a mark. Finishing while still off offers an adjustment instead of refusing.
 */
@Composable
fun ReconcileRegisterScreen(
    register: RegisterPage,
    page: ReconcilePage?,
    error: String?,
    onGesture: (Boolean) -> Unit,
    onStart: (balanceText: String, dateIso: String) -> Unit,
    onToggle: (String) -> Unit,
    onSetCleared: (List<String>, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onAddMissing: () -> Unit,
    onFinish: (adjust: Boolean) -> Unit,
    onFlip: (String) -> Unit = {},
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    today: Int = RegisterEntry.parseDate(RegisterEntry.todayIso())!!,
) {
    val prefs = LocalFormatPrefs.current
    val decimals = register.currency.decimals
    // The reconcile page carries the live marks; a row it does not know keeps the register's.
    val marks = page?.rows?.associateBy { it.id }.orEmpty()
    val rows =
        register.rows.map { row ->
            marks[row.id]?.let { row.copy(cleared = it.cleared, reconciled = it.reconciled) } ?: row
        }
    val clearedBalance = register.rows.filter { it.cleared || it.reconciled }.sumOf { it.amountMinor }
    var balance by rememberSaveable(register.account.id) { mutableStateOf(prefs.editSignedAmount(clearedBalance, decimals)) }
    var date by rememberSaveable(register.account.id) { mutableIntStateOf(today) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var confirming by rememberSaveable(page?.statementDate) { mutableStateOf(false) }
    if (page == null && picking != null) {
        DatePickerPage(
            date = picking!!,
            dateFormat = prefs.dateFormat,
            onChange = { picking = it },
            onDone = {
                date = picking!!
                picking = null
            },
            onCancel = { picking = null },
        )
        return
    }
    val start by rememberUpdatedState { onStart(prefs.readAmount(balance, decimals), RegisterEntry.formatIso(date)) }
    val finish by rememberUpdatedState {
        if (page != null && page.differenceMinor != 0L) confirming = true else onFinish(false)
    }
    val cancel by rememberUpdatedState(onCancel)
    val adjust by rememberUpdatedState { onFinish(true) }
    val bar =
        remember(page == null, confirming) {
            when {
                page == null ->
                    listOf(
                        ScreenAction("reconcile-start", AppGlyph.Reconcile, ReconcileCopy.START) { start() },
                        ScreenAction("reconcile-cancel", AppGlyph.Cancel, ReconcileCopy.CANCEL) { cancel() },
                    )
                confirming ->
                    listOf(
                        ScreenAction("reconcile-adjust", AppGlyph.Save, ReconcileCopy.ADD_AND_FINISH) { adjust() },
                        ScreenAction("reconcile-keep-looking", AppGlyph.Undo, ReconcileCopy.KEEP_LOOKING) { confirming = false },
                    )
                else ->
                    listOf(
                        ScreenAction("reconcile-finish", AppGlyph.Save, ReconcileCopy.FINISH) { finish() },
                        ScreenAction("reconcile-cancel", AppGlyph.Cancel, ReconcileCopy.CANCEL) { cancel() },
                    )
            }
        }
    RememberBarOverride(bar)
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(horizontal = MetroDimens.Gutter)
            .testTag("reconcile-register"),
    ) {
        MetroText(register.account.name, Metro.typography.header, maxLines = 2)
        if (page == null) {
            StartPanel(
                balance = balance,
                onBalance = { balance = it },
                dateText = DateFormats.withWeekday(date, prefs.dateFormat),
                onPickDate = { picking = date },
                error = error,
            )
        } else {
            ReconcileHeader(page, tally(rows))
            if (error != null) {
                MetroText(error, Metro.typography.body, Modifier.padding(top = 8.dp), color = Metro.accent.text)
            }
        }
        val hints = if (page != null && !confirming) ReconcileHelp.hints(page) else emptyList()
        ReconcileList(
            register = register,
            rows = rows,
            page = page,
            hints = hints,
            confirming = confirming,
            onGesture = onGesture,
            onToggle = onToggle,
            onSetCleared = onSetCleared,
            onFlip = onFlip,
            onOpen = onOpen,
            onAddMissing = onAddMissing,
            modifier = Modifier.weight(1f).padding(top = MetroDimens.Grid),
        )
    }
}

@Composable
private fun StartPanel(
    balance: String,
    onBalance: (String) -> Unit,
    dateText: String,
    onPickDate: () -> Unit,
    error: String?,
) {
    Column(
        Modifier
            .padding(top = MetroDimens.Gutter)
            .fillMaxWidth()
            .border(MetroDimens.Stroke, Metro.accent.fill)
            .padding(12.dp)
            .testTag("reconcile-start"),
    ) {
        MetroText(ReconcileCopy.RECONCILE, Metro.typography.caption, color = Metro.accent.text)
        Label(ReconcileCopy.STATEMENT_BALANCE, Modifier.padding(top = 8.dp))
        MetroField(balance, onBalance, ReconcileCopy.STATEMENT, Modifier.testTag("statement-balance"))
        MetroText(ReconcileCopy.PREFILLED, Metro.typography.caption, Modifier.padding(top = 4.dp), color = Metro.colors.secondary)
        Label(ReconcileCopy.STATEMENT_DATE, Modifier.padding(top = 10.dp))
        Box(Modifier.widthIn(max = 240.dp).fillMaxWidth().controlFrame()) {
            MetroComboValue(dateText, "statement-date", onPickDate, Modifier.fillMaxWidth())
        }
        if (error != null) {
            MetroText(error, Metro.typography.body, Modifier.padding(top = 8.dp), color = Metro.accent.text)
        }
    }
}

@Composable
private fun Label(
    text: String,
    modifier: Modifier = Modifier,
) {
    MetroText(text, Metro.typography.caption, modifier.padding(bottom = MetroDimens.FieldLabelGap), color = Metro.colors.secondary)
}

/** How many open rows are locked, cleared and not cleared, with the day the locked ones run through. */
private data class Tally(
    val locked: Int,
    val cleared: Int,
    val notCleared: Int,
    val lockedThrough: Int?,
)

private fun tally(rows: List<RegisterRow>): Tally {
    val locked = rows.filter { it.reconciled }
    return Tally(
        locked = locked.size,
        cleared = rows.count { it.cleared && !it.reconciled },
        notCleared = rows.count { !it.cleared && !it.reconciled },
        lockedThrough = locked.maxOfOrNull { it.date },
    )
}

/**
 * Statement, cleared and the difference, on chrome while it is off and on the accent once it balances, over
 * a strip that splits the account's transactions into locked, cleared and not cleared.
 */
@Composable
private fun ReconcileHeader(
    page: ReconcilePage,
    tally: Tally,
) {
    val balanced = page.differenceMinor == 0L
    val fill = if (balanced) Metro.accent.fill else Metro.colors.chrome
    val ink = if (balanced) Metro.accent.onFill else Metro.colors.foreground
    val soft = if (balanced) Metro.accent.onFill.copy(alpha = 0.8f) else Metro.colors.secondary
    val options = formatOptions()
    Column(
        Modifier
            .padding(top = MetroDimens.Gutter)
            .fillMaxWidth()
            .background(fill)
            .padding(12.dp)
            .testTag(if (balanced) "reconcile-balanced" else "reconcile-off"),
    ) {
        Figure(
            "${ReconcileCopy.STATEMENT} · ${RegisterEntry.registerDateLabel(page.statementDate)}",
            MoneyFormat.format(page.statementMinor, page.currency, options),
            soft,
            ink,
            "statement-amount",
        )
        Figure(ReconcileCopy.CLEARED, MoneyFormat.format(page.clearedTotalMinor, page.currency, options), soft, ink, "cleared-total")
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (balanced) {
                AppGlyphIcon(AppGlyph.Save, ink)
                MetroText(ReconcileCopy.BALANCED, Metro.typography.subheader, Modifier.padding(start = 8.dp).weight(1f), color = ink)
                MetroText("${ReconcileCopy.FINISH_LOCKS} ${tally.cleared}", Metro.typography.caption, color = ink)
            } else {
                MetroText(ReconcileCopy.OFF_BY, Metro.typography.subheader, Modifier.weight(1f), color = ink)
                MetroText(
                    MoneyFormat.format(page.differenceMinor, page.currency, options),
                    Metro.typography.subheader,
                    Modifier.testTag("reconcile-difference"),
                    color = Metro.accent.text,
                )
            }
        }
        StatusStrip(tally, balanced, ink, soft)
    }
}

/** Locked, cleared and not cleared as one bar in proportion, with the counts under it. */
@Composable
private fun StatusStrip(
    tally: Tally,
    balanced: Boolean,
    ink: Color,
    soft: Color,
) {
    val lockInk = (if (balanced) ink else Metro.colors.foreground).copy(alpha = LOCK_ALPHA)
    val clearedInk = if (balanced) ink else Metro.accent.fill
    val openInk = (if (balanced) ink else Metro.colors.secondary).copy(alpha = 0.25f)
    val parts =
        listOf(tally.locked to lockInk, tally.cleared to clearedInk, tally.notCleared to openInk).filter { it.first > 0 }
    Row(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .height(8.dp)
            .testTag("reconcile-strip"),
    ) {
        parts.forEachIndexed { i, (count, color) ->
            Box(
                Modifier
                    .weight(count.toFloat())
                    .fillMaxHeight()
                    .padding(start = if (i == 0) 0.dp else 2.dp)
                    .background(color),
            )
        }
    }
    Row(Modifier.padding(top = 6.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val through = tally.lockedThrough?.let { RegisterEntry.registerDateLabel(it) }
        if (tally.locked > 0 && through != null) {
            Key(lockInk, "${tally.locked}", ReconcileCopy.lockedThrough(through), ink, soft, "strip-locked")
        }
        Key(clearedInk, "${tally.cleared}", ReconcileCopy.CLEARED, ink, soft, "strip-cleared")
        Key(openInk, "${tally.notCleared}", ReconcileCopy.NOT_CLEARED, ink, soft, "strip-not-cleared")
    }
}

@Composable
private fun Key(
    swatch: Color,
    count: String,
    label: String,
    ink: Color,
    soft: Color,
    tag: String,
) {
    Row(Modifier.padding(end = 12.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(swatch))
        MetroText(count, Metro.typography.caption.copy(fontWeight = FontWeight.SemiBold), Modifier.padding(start = 4.dp), color = ink)
        MetroText(label, Metro.typography.caption, Modifier.padding(start = 3.dp), color = soft, maxLines = 1)
    }
}

@Composable
private fun Figure(
    label: String,
    value: String,
    soft: Color,
    ink: Color,
    tag: String,
) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        MetroText(label, Metro.typography.caption, Modifier.weight(1f), color = soft)
        MetroText(value, Metro.typography.caption, Modifier.testTag(tag), color = ink)
    }
}

/**
 * The register's rows under the header: the leads first, then what is still open, newest first by day, with
 * days after the statement dimmed and rows older than the last reconcile called out, then the locked rows
 * under their own heading, each with a lock where the cleared mark goes.
 */
@Composable
private fun ReconcileList(
    register: RegisterPage,
    rows: List<RegisterRow>,
    page: ReconcilePage?,
    hints: List<ReconcileHint>,
    confirming: Boolean,
    onGesture: (Boolean) -> Unit,
    onToggle: (String) -> Unit,
    onSetCleared: (List<String>, Boolean) -> Unit,
    onFlip: (String) -> Unit,
    onOpen: (String) -> Unit,
    onAddMissing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onGesture(it) }
    }
    var showLocked by rememberSaveable { mutableStateOf(true) }
    val open = rows.filter { !it.reconciled }
    val done = rows.filter { it.reconciled }
    val lockedThrough = done.maxOfOrNull { it.date }
    val outlined = ReconcileHelp.rowsOf(hints)
    val statementDate = page?.statementDate
    LazyColumn(modifier.testTag("reconcile-list"), state = list) {
        if (page != null && confirming) {
            item(key = "adjust") { AdjustPanel(register.account.name, page) }
        } else if (page != null && hints.isNotEmpty()) {
            item(key = "hints") { LeadsCard(page, hints, onSetCleared, onFlip, onOpen, onAddMissing) }
        }
        ledgerDays(
            rows = open,
            page = register,
            label = { date ->
                val day = RegisterEntry.registerDateLabel(date)
                when {
                    statementDate != null && date > statementDate -> "$day · ${ReconcileCopy.AFTER_STATEMENT}"
                    lockedThrough != null && date <= lockedThrough -> "$day · ${ReconcileCopy.BEFORE_LAST}"
                    else -> day
                }
            },
            dim = { row -> statementDate != null && row.date > statementDate && row.id !in outlined },
            outlined = outlined,
            onPress = { id -> if (page != null) onToggle(id) },
            onLongPress = onOpen,
        )
        if (done.isNotEmpty() && lockedThrough != null) {
            item(key = "locked") {
                Column(
                    Modifier
                        .padding(top = 18.dp)
                        .fillMaxWidth()
                        .heightIn(min = MetroDimens.TouchTarget)
                        .metroPress({ showLocked = !showLocked })
                        .testTag("locked-heading"),
                ) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(Metro.colors.foreground.copy(alpha = LOCK_ALPHA)))
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val lockInk = Metro.colors.foreground.copy(alpha = LOCK_ALPHA)
                        Canvas(Modifier.size(16.dp)) { drawLock(center, lockInk) }
                        MetroText(ReconcileCopy.LOCKED, Metro.typography.body, Modifier.padding(start = 6.dp))
                        MetroText(
                            ReconcileCopy.reconciledThrough(RegisterEntry.registerDateLabel(lockedThrough), done.size),
                            Metro.typography.caption.copy(textAlign = TextAlign.End),
                            Modifier.weight(1f).padding(start = 8.dp),
                            color = Metro.colors.secondary,
                            maxLines = 1,
                        )
                        AppGlyphIcon(if (showLocked) AppGlyph.ChevronDown else AppGlyph.ChevronRight, Metro.colors.secondary)
                    }
                    MetroText(ReconcileCopy.LOCKED_NOTE, Metro.typography.caption, color = Metro.colors.secondary)
                }
            }
            if (showLocked) {
                ledgerDays(
                    rows = done,
                    page = register,
                    label = { RegisterEntry.registerDateLabel(it) },
                    dim = { false },
                    outlined = emptySet(),
                    onPress = {},
                    onLongPress = onOpen,
                    keyPrefix = "done-",
                )
            }
        }
    }
}

private fun LazyListScope.ledgerDays(
    rows: List<RegisterRow>,
    page: RegisterPage,
    label: (Int) -> String,
    dim: (RegisterRow) -> Boolean,
    outlined: Set<String>,
    onPress: (String) -> Unit,
    onLongPress: (String) -> Unit,
    keyPrefix: String = "",
) {
    val position = rows.withIndex().associate { it.value.id to it.index }
    registerDays(rows).forEach { day ->
        val faint = day.all(dim)
        item(key = "${keyPrefix}day-${day.first().id}") {
            MetroText(
                label(day.first().date),
                Metro.typography.caption,
                Modifier.padding(top = 16.dp, bottom = 4.dp).alpha(if (faint) DIM else 1f),
                color = Metro.accent.text,
            )
        }
        items(day, key = { "$keyPrefix${it.id}" }) { row ->
            Box(if (row.id in outlined) Modifier.hintOutline().testTag("reconcile-hinted-${row.id}") else Modifier) {
                RegisterLine(
                    row,
                    page,
                    banded = ledgerBanded(position.getValue(row.id)),
                    picked = null,
                    onPress = { onPress(row.id) },
                    onLongPress = { onLongPress(row.id) },
                    modifier = Modifier.alpha(if (dim(row)) DIM else 1f),
                    showLock = true,
                )
            }
        }
    }
}

/** An accent outline around a row a hint points at, running out to the cleared bar past the gutter. */
@Composable
private fun Modifier.hintOutline(): Modifier {
    val accent = Metro.accent.fill
    return drawWithContent {
        drawContent()
        val gutter = MetroDimens.Gutter.toPx()
        val stroke = 2.dp.toPx()
        drawRect(
            accent,
            Offset(-gutter + stroke, stroke / 2),
            Size(size.width + 2 * gutter - 2 * stroke, size.height - stroke),
            style = Stroke(stroke),
        )
    }
}

/**
 * The leads for the difference, numbered, most likely first: what kind of problem each is, the rows it
 * points at, and a one-tap fix with the difference it would leave.
 */
@Composable
private fun LeadsCard(
    page: ReconcilePage,
    hints: List<ReconcileHint>,
    onSetCleared: (List<String>, Boolean) -> Unit,
    onFlip: (String) -> Unit,
    onOpen: (String) -> Unit,
    onAddMissing: () -> Unit,
) {
    val options = formatOptions()

    fun money(minor: Long) = MoneyFormat.format(minor, page.currency, options)

    fun day(date: Int) = RegisterEntry.registerDateLabel(date)
    Tinted(Modifier.testTag("reconcile-hints")) {
        MetroText(
            ReconcileCopy.leads(hints.size),
            Metro.typography.caption,
            Modifier.padding(bottom = 2.dp),
            color = Metro.colors.secondary,
        )
        hints.forEachIndexed { i, hint ->
            val leaves = ReconcileHelp.after(page, hint)
            val lead =
                when (hint) {
                    is ReconcileHint.Exact ->
                        Lead(
                            ReconcileCopy.TAG_EXACT,
                            ReconcileCopy.exact(hint.row.payee, money(hint.row.amountMinor), day(hint.row.date)),
                            ReconcileCopy.CLEAR_IT,
                            "hint-clear-${hint.row.id}",
                        ) {
                            onSetCleared(listOf(hint.row.id), true)
                        }
                    is ReconcileHint.Flip ->
                        Lead(
                            ReconcileCopy.TAG_FLIP,
                            ReconcileCopy.flip(hint.row.payee, money(hint.row.amountMinor), hint.row.cleared),
                            ReconcileCopy.FLIP_IT,
                            "hint-flip",
                        ) {
                            onFlip(hint.row.id)
                        }
                    is ReconcileHint.Pair ->
                        Lead(
                            ReconcileCopy.TAG_PAIR,
                            "${hint.first.payee} ${money(hint.first.amountMinor)} + ${hint.second.payee} ${money(hint.second.amountMinor)}",
                            ReconcileCopy.CLEAR_BOTH,
                            "hint-pair",
                        ) { onSetCleared(listOf(hint.first.id, hint.second.id), true) }
                    is ReconcileHint.Uncleared ->
                        Lead(
                            ReconcileCopy.TAG_MAYBE_NOT,
                            ReconcileCopy.maybeNot(hint.row.payee, money(hint.row.amountMinor)),
                            ReconcileCopy.UNCLEAR_IT,
                            "hint-unclear",
                        ) {
                            onSetCleared(listOf(hint.row.id), false)
                        }
                    is ReconcileHint.Late ->
                        Lead(
                            ReconcileCopy.TAG_LATE,
                            ReconcileCopy.late(hint.row.payee, money(hint.row.amountMinor), day(hint.row.date)),
                            ReconcileCopy.CLEAR_IT,
                            "hint-late",
                        ) {
                            onSetCleared(listOf(hint.row.id), true)
                        }
                    is ReconcileHint.Duplicate -> {
                        val text =
                            ReconcileCopy.duplicate(
                                hint.second.payee,
                                money(hint.second.amountMinor),
                                day(hint.first.date),
                                day(hint.second.date),
                            )
                        if (leaves != null) {
                            Lead(
                                ReconcileCopy.TAG_DUPLICATE,
                                text,
                                ReconcileCopy.UNCLEAR_ONE,
                                "hint-duplicate",
                            ) { onSetCleared(listOf(hint.second.id), false) }
                        } else {
                            Lead(ReconcileCopy.TAG_DUPLICATE, text, ReconcileCopy.OPEN_IT, "hint-duplicate") { onOpen(hint.second.id) }
                        }
                    }
                    is ReconcileHint.Stale ->
                        Lead(
                            ReconcileCopy.staleTag(hint.days),
                            ReconcileCopy.stale(hint.row.payee, money(hint.row.amountMinor), day(hint.row.date)),
                            ReconcileCopy.CLEAR_IT,
                            "hint-stale",
                        ) {
                            onSetCleared(listOf(hint.row.id), true)
                        }
                    ReconcileHint.Swapped ->
                        Lead(
                            ReconcileCopy.TAG_SWAPPED,
                            ReconcileCopy.swapped(money(kotlin.math.abs(page.differenceMinor))),
                            null,
                            "hint-swapped",
                        ) {}
                    ReconcileHint.Missing ->
                        Lead(
                            ReconcileCopy.TAG_MISSING,
                            ReconcileCopy.MISSING,
                            ReconcileCopy.ADD_IT,
                            "hint-missing",
                            onAddMissing,
                        )
                }
            LeadRow(i + 1, first = i == 0, lead, leaves?.let { ReconcileCopy.leaves(money(it)) }, divider = i > 0)
        }
    }
}

private class Lead(
    val tag: String,
    val text: String,
    val action: String?,
    val testTag: String,
    val onClick: () -> Unit,
)

@Composable
private fun LeadRow(
    number: Int,
    first: Boolean,
    lead: Lead,
    leaves: String?,
    divider: Boolean,
) {
    val accent = Metro.accent.fill
    if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Metro.colors.secondary.copy(alpha = 0.22f)))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (lead.action != null) Modifier.metroPress(lead.onClick) else Modifier)
            .padding(vertical = 7.dp)
            .testTag(lead.testTag),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(20.dp)
                .then(if (first) Modifier.background(accent) else Modifier.border(1.5.dp, accent)),
            contentAlignment = Alignment.Center,
        ) {
            MetroText(
                "$number",
                Metro.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = if (first) Metro.accent.onFill else Metro.accent.text,
            )
        }
        Column(Modifier.weight(1f).padding(start = 8.dp, end = 8.dp)) {
            MetroText(
                lead.tag.uppercase(),
                Metro.typography.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
                color = Metro.accent.text,
            )
            MetroText(lead.text, Metro.typography.caption.copy(fontSize = 14.sp), maxLines = 3)
        }
        if (lead.action != null) {
            Column(horizontalAlignment = Alignment.End) {
                MetroText(lead.action, Metro.typography.caption.copy(fontSize = 14.sp), color = Metro.accent.text)
                if (leaves != null) {
                    MetroText(
                        leaves,
                        Metro.typography.caption.copy(fontSize = 11.sp),
                        Modifier.testTag("${lead.testTag}-leaves"),
                        color = Metro.colors.secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun Tinted(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(Metro.accent.text.copy(alpha = HINT_TINT)),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(Metro.accent.fill))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp), content = content)
    }
}

/** Finish while still off: the adjustment it would add, so the account matches the bank. */
@Composable
private fun AdjustPanel(
    account: String,
    page: ReconcilePage,
) {
    val options = formatOptions()
    val locks = page.rows.count { it.cleared && !it.reconciled }
    Column(
        Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .border(MetroDimens.Stroke, Metro.accent.fill)
            .padding(12.dp)
            .testTag("reconcile-adjust-panel"),
    ) {
        MetroText(ReconcileCopy.FINISH_ANYWAY, Metro.typography.subheader)
        MetroText(
            ReconcileCopy.adjustExplain(account),
            Metro.typography.body,
            Modifier.padding(top = 6.dp),
            color = Metro.colors.secondary,
        )
        Row(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .heightIn(min = MetroDimens.TouchTarget)
                .background(ledgerBand)
                .padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                MetroText(ReconcileCopy.ADJUSTMENT_PAYEE, Metro.typography.body, maxLines = 2)
                MetroText(RegisterEntry.registerDateLabel(page.statementDate), Metro.typography.caption, color = Metro.colors.secondary)
            }
            MetroText(
                MoneyFormat.format(page.differenceMinor, page.currency, options),
                Metro.typography.body,
                Modifier.padding(horizontal = 10.dp).testTag("adjust-amount"),
                color = Metro.accent.text,
            )
        }
        MetroText(ReconcileCopy.NO_CATEGORY_LATER, Metro.typography.caption, Modifier.padding(top = 8.dp), color = Metro.colors.secondary)
        MetroText(
            "${ReconcileCopy.FINISH_LOCKS} ${locks + 1}",
            Metro.typography.caption,
            Modifier.padding(top = 4.dp),
            color = Metro.accent.text,
        )
    }
}

private const val DIM = 0.45f
private const val LOCK_ALPHA = 0.72f
private const val HINT_TINT = 0.08f
