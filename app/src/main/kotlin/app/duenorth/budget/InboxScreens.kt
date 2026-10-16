package app.duenorth.budget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

object InboxCopy {
    const val TO_CATEGORIZE = "to categorize"
    const val SPENDING = "spending"
    const val ACCEPT = "accept"
    const val CATEGORIZE = "categorize"
    const val FILED_TO = "filed to"
    const val NO_CATEGORY = "no category"
    const val EDIT = "edit"
    const val SPLIT = "split"
    const val SCHEDULE = "schedule"
    const val DELETE = "delete"
    const val GO_TO = "go to"
}

/** What a row's tap and long-press menu can do, beyond the two swipes. */
class InboxActions(
    val onDetails: (String) -> Unit = {},
    val onSplit: (String) -> Unit = {},
    val onSchedule: (String) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onOpenAccount: (String) -> Unit = {},
)

/**
 * The inbox as a ledger: uncategorized transactions under day headings on faint bands, like the
 * account register. Swipe a row right to accept the category its payee last went to, or left to
 * pick one; a filed row turns accent in place, then collapses. The first time the inbox is shown
 * ([peek]), the top row slides each way to show both swipes.
 */
@Composable
fun InboxSection(
    shell: MonthShell,
    onListGesture: (Boolean) -> Unit,
    onInbox: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    onAdd: (() -> Unit)? = null,
    formatOptions: FormatOptions = formatOptions(),
    onAccept: (String) -> Unit = onInbox,
    filed: List<InboxFiled> = emptyList(),
    onFiledShown: (String) -> Unit = {},
    peek: Boolean = false,
    onPeeked: () -> Unit = {},
    actions: InboxActions = InboxActions(),
) {
    val entries = inboxEntries(shell.inbox, filed)
    var menu by remember { mutableStateOf<Pair<InboxRow, Rect>?>(null) }
    if (entries.isEmpty()) {
        Column(modifier.padding(MetroDimens.Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroText(ShellCopy.NOTHING_TO_CATEGORIZE, Metro.typography.body)
            if (onAdd != null) MetroButton("add", onClick = onAdd)
        }
        return
    }
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onListGesture(it) }
    }
    val waiting = shell.inbox.filter { row -> filed.none { it.row.id == row.id } }
    Column(modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            BudgetTotal(
                InboxCopy.TO_CATEGORIZE,
                "${waiting.size}",
                Metro.accent.text,
                "inbox-count",
                modifier = Modifier.weight(1f),
            )
            BudgetTotal(
                InboxCopy.SPENDING,
                MoneyFormat.format(waiting.filter { it.amountMinor < 0 }.sumOf { it.amountMinor }, shell.currency, formatOptions),
                Metro.colors.foreground,
                "inbox-spending",
                alignEnd = true,
                modifier = Modifier.weight(1f),
            )
        }
        LazyColumn(Modifier.weight(1f), state = list) {
            var index = 0
            inboxDays(entries).forEach { day ->
                val first = day.first()
                val headingLeaves = day.all { it.filedTo != null }
                item(key = "day-${first.row.date}-${first.row.id}") {
                    InboxDayHeading(first.row.date, leaving = headingLeaves)
                }
                val start = index
                index += day.size
                items(day, key = { it.row.id }) { entry ->
                    InboxLine(
                        entry = entry,
                        shell = shell,
                        formatOptions = formatOptions,
                        banded = ledgerBanded(start + day.indexOf(entry)),
                        onAccept = onAccept,
                        onCategorize = onInbox,
                        onFiledShown = onFiledShown,
                        peek = peek && entry == entries.first() && entry.filedTo == null,
                        onPeeked = onPeeked,
                        onDetails = actions.onDetails,
                        onMenu = { bounds -> menu = entry.row to bounds },
                    )
                }
            }
        }
        if (onAdd != null && shell.inbox.isEmpty()) {
            MetroButton("add", Modifier.padding(MetroDimens.Gutter), onClick = onAdd)
        }
    }
    menu?.let { (row, anchor) ->
        InboxMenu(anchor, inboxMenuItems(row, onAccept, onInbox, actions), onDismiss = { menu = null })
    }
}

/** Every action a row offers, in the order its long-press menu lists them. */
internal fun inboxMenuItems(
    row: InboxRow,
    onAccept: (String) -> Unit,
    onCategorize: (String) -> Unit,
    actions: InboxActions,
): List<Pair<String, () -> Unit>> =
    listOfNotNull(
        row.suggestionId?.let { InboxCopy.ACCEPT to { onAccept(row.id) } },
        InboxCopy.CATEGORIZE to { onCategorize(row.id) },
        InboxCopy.EDIT to { actions.onDetails(row.id) },
        InboxCopy.SPLIT to { actions.onSplit(row.id) },
        InboxCopy.SCHEDULE to { actions.onSchedule(row.id) },
        InboxCopy.DELETE to { actions.onDelete(row.id) },
        row.accountName.ifBlank { null }?.let { account ->
            "${InboxCopy.GO_TO} ${account.lowercase()}" to { actions.onOpenAccount(row.accountId) }
        },
    )

/**
 * The Metro context menu: the rest of the screen dims, the pressed row stays bright, and a
 * full-width panel of lowercase actions opens under it (over it when there is no room below).
 */
@Composable
private fun InboxMenu(
    anchor: Rect,
    items: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    // Read before the popup: its own window does not get the screen's insets.
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density)
    val bottom = WindowInsets.navigationBars.getBottom(density)
    Popup(
        popupPositionProvider = WholeWindow,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        InboxMenuPanel(anchor, items, onDismiss, safeTop = top, safeBottom = bottom)
    }
}

private object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

@Composable
internal fun InboxMenuPanel(
    anchor: Rect,
    items: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
    safeTop: Int = 0,
    safeBottom: Int = 0,
) {
    val scrim = Metro.colors.background.copy(alpha = MENU_SCRIM)
    Layout(
        content = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Metro.colors.chrome)
                    .padding(vertical = MetroDimens.Row)
                    .testTag("inbox-menu"),
            ) {
                items.forEach { (label, run) ->
                    MetroText(
                        label,
                        Metro.typography.subheader,
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = MetroDimens.TouchTarget)
                            .metroPress {
                                onDismiss()
                                run()
                            }.padding(horizontal = MENU_INSET, vertical = 8.dp),
                        maxLines = 1,
                        // Ellipsis rather than clip, so descenders such as g and y are never cut at the line's edge.
                        softWrap = false,
                    )
                }
            }
        },
        modifier =
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(scrim, Offset.Zero, Size(size.width, anchor.top.coerceAtLeast(0f)))
                    drawRect(scrim, Offset(0f, anchor.bottom), Size(size.width, (size.height - anchor.bottom).coerceAtLeast(0f)))
                }.pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) { measurables, constraints ->
        val panel = measurables.single().measure(constraints.copy(minWidth = constraints.maxWidth, minHeight = 0))
        val below = anchor.bottom.roundToInt()
        // Keep the panel clear of the navigation bar and the status bar.
        val limit = constraints.maxHeight - safeBottom
        val y =
            if (below + panel.height <= limit) {
                below
            } else {
                (anchor.top.roundToInt() - panel.height).coerceIn(safeTop, (limit - panel.height).coerceAtLeast(safeTop))
            }
        layout(constraints.maxWidth, constraints.maxHeight) { panel.place(0, y) }
    }
}

/** One inbox row as shown: the row, and the category it was just filed under while its confirmation plays. */
internal data class InboxEntry(
    val row: InboxRow,
    val filedTo: String? = null,
)

/**
 * The inbox rows plus the rows just filed, each back at the place it left so its confirmation
 * plays where the row was.
 */
internal fun inboxEntries(
    inbox: List<InboxRow>,
    filed: List<InboxFiled>,
): List<InboxEntry> {
    val byId = filed.associateBy { it.row.id }
    val out = inbox.map { InboxEntry(it, byId[it.id]?.categoryName) }.toMutableList()
    filed
        .filter { f -> inbox.none { it.id == f.row.id } }
        .sortedBy { it.index }
        .forEach { f -> out.add(f.index.coerceIn(0, out.size), InboxEntry(f.row, f.categoryName)) }
    return out
}

/** Runs of entries that share a date, in inbox order. */
internal fun inboxDays(entries: List<InboxEntry>): List<List<InboxEntry>> =
    entries.fold(mutableListOf<MutableList<InboxEntry>>()) { days, entry ->
        if (days
                .lastOrNull()
                ?.first()
                ?.row
                ?.date == entry.row.date
        ) {
            days.last() += entry
        } else {
            days += mutableListOf(entry)
        }
        days
    }

/** The quieter line under a payee: the suggested category, the account, then the note. */
internal fun inboxDetail(row: InboxRow): String =
    listOfNotNull(
        row.suggestionName.ifBlank { InboxCopy.NO_CATEGORY },
        row.accountName.ifBlank { null },
        row.note.ifBlank { null },
    ).joinToString(" · ")

@Composable
private fun InboxDayHeading(
    date: Int,
    leaving: Boolean,
) {
    // A day whose last row is being filed collapses with it.
    val height = remember { Animatable(1f) }
    val animations = Metro.animations
    LaunchedEffect(leaving) {
        if (leaving) {
            delay(FILED_HOLD_MS)
            if (animations) height.animateTo(0f, tween(COLLAPSE_MS)) else height.snapTo(0f)
        } else {
            height.snapTo(1f)
        }
    }
    MetroText(
        RegisterEntry.registerDateLabel(date),
        Metro.typography.caption,
        Modifier.collapse(height.value).padding(top = 16.dp, bottom = 4.dp).testTag("inbox-day-$date"),
        color = Metro.accent.text,
    )
}

@Composable
private fun InboxLine(
    entry: InboxEntry,
    shell: MonthShell,
    formatOptions: FormatOptions,
    banded: Boolean,
    onAccept: (String) -> Unit,
    onCategorize: (String) -> Unit,
    onFiledShown: (String) -> Unit,
    peek: Boolean,
    onPeeked: () -> Unit,
    onDetails: (String) -> Unit,
    onMenu: (Rect) -> Unit,
) {
    val row = entry.row
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val animations = Metro.animations
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val height = remember { Animatable(1f) }
    var width by remember { mutableIntStateOf(0) }
    val accept by rememberUpdatedState(onAccept)
    val categorize by rememberUpdatedState(onCategorize)
    val shown by rememberUpdatedState(onFiledShown)
    val peeked by rememberUpdatedState(onPeeked)
    val filedTo = entry.filedTo

    // First visit: slide part way right, then part way left, to show what each swipe does.
    LaunchedEffect(peek, width) {
        if (!peek || width == 0) return@LaunchedEffect
        if (animations) {
            delay(PEEK_DELAY_MS)
            val reach = width * PEEK_REACH
            offset.animateTo(reach, tween(PEEK_MS, easing = FastOutSlowInEasing))
            delay(PEEK_HOLD_MS)
            offset.animateTo(0f, tween(PEEK_MS, easing = FastOutSlowInEasing))
            delay(PEEK_GAP_MS)
            offset.animateTo(-reach, tween(PEEK_MS, easing = FastOutSlowInEasing))
            delay(PEEK_HOLD_MS)
            offset.animateTo(0f, tween(PEEK_MS, easing = FastOutSlowInEasing))
        }
        peeked()
    }
    // Filed: hold the confirmation in the row's own slot, then collapse it away.
    LaunchedEffect(filedTo) {
        if (filedTo == null) {
            height.snapTo(1f)
            return@LaunchedEffect
        }
        offset.snapTo(0f)
        delay(FILED_HOLD_MS)
        if (animations) height.animateTo(0f, tween(COLLAPSE_MS)) else height.snapTo(0f)
        shown(row.id)
    }

    fun settle() {
        scope.launch {
            val w = width.toFloat()
            when {
                w > 0 && offset.value > w * COMMIT -> {
                    offset.animateTo(w, tween(EXIT_MS, easing = LinearOutSlowInEasing))
                    accept(row.id)
                }
                w > 0 && offset.value < -w * COMMIT -> {
                    offset.animateTo(-w, tween(EXIT_MS, easing = LinearOutSlowInEasing))
                    categorize(row.id)
                    // Back in place for when the picker is dismissed without a choice.
                    delay(RETURN_DELAY_MS)
                    offset.snapTo(0f)
                }
                else -> offset.animateTo(0f, tween(EXIT_MS))
            }
        }
    }

    val fill = if (banded) ledgerBand else Color.Transparent
    val acceptsHere = row.suggestionId != null
    Box(
        Modifier
            .fillMaxWidth()
            .collapse(height.value)
            .onSizeChanged { width = it.width }
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .testTag("inbox-row-${row.id}")
            .semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction(if (acceptsHere) InboxCopy.ACCEPT else InboxCopy.CATEGORIZE) {
                            accept(row.id)
                            true
                        },
                        CustomAccessibilityAction(InboxCopy.CATEGORIZE) {
                            categorize(row.id)
                            true
                        },
                    )
            },
    ) {
        // What a swipe uncovers: accept on the left as the row moves right, categorize on the right.
        if (offset.value != 0f && filedTo == null) {
            val right = offset.value > 0
            val accepting = right && acceptsHere
            val ink = if (accepting) Metro.accent.onFill else Color.White
            Row(
                Modifier
                    .matchParentSize()
                    .ledgerFill(if (accepting) Metro.accent.fill else Metro.colors.secondary),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (right) Arrangement.Start else Arrangement.End,
            ) {
                val label = if (accepting) InboxCopy.ACCEPT else InboxCopy.CATEGORIZE
                val glyph = if (accepting) AppGlyph.Save else AppGlyph.Categories
                if (right) {
                    AppGlyphIcon(glyph, ink)
                    MetroText(label, Metro.typography.caption, Modifier.padding(start = 8.dp), color = ink)
                } else {
                    MetroText(label, Metro.typography.caption, Modifier.padding(end = 8.dp), color = ink)
                    AppGlyphIcon(glyph, ink)
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val p = measurable.measure(constraints)
                    layout(p.width, p.height) { p.place(offset.value.roundToInt(), 0) }
                }.alpha(if (filedTo != null) 0f else 1f)
                .heightIn(min = MetroDimens.TouchTarget)
                .ledgerFill(if (offset.value != 0f) Metro.colors.background else fill)
                .ledgerFill(if (offset.value != 0f) fill else Color.Transparent)
                .pointerInput(row.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Drags that start near either edge are left to the pivot, so its swipe still works over the list.
                        val edge = size.width * SWIPE_DEAD_ZONE
                        if (down.position.x < edge || down.position.x > size.width - edge) return@awaitEachGesture
                        val drag =
                            awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                                change.consume()
                                scope.launch { offset.snapTo(offset.value + over) }
                            } ?: return@awaitEachGesture
                        horizontalDrag(drag.id) { change ->
                            val dx = change.positionChange().x
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + dx) }
                        }
                        settle()
                    }
                }.metroPress(onClick = { onDetails(row.id) }, onLongClick = { onMenu(bounds) })
                .padding(vertical = INBOX_ROW_AIR),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(LEDGER_NAME + LEDGER_FIGURE).padding(end = MetroDimens.Gutter)) {
                    MetroText(row.payee, Metro.typography.body, maxLines = 2)
                    MetroText(
                        inboxDetail(row),
                        Metro.typography.caption,
                        Modifier.testTag("inbox-detail-${row.id}"),
                        color = Metro.colors.secondary,
                        maxLines = 2,
                    )
                }
                MetroText(
                    MoneyFormat.format(row.amountMinor, shell.currency, formatOptions),
                    Metro.typography.body.copy(textAlign = TextAlign.End),
                    Modifier.weight(LEDGER_FIGURE * 2).testTag("inbox-amount-${row.id}"),
                    color = Metro.accent.text,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        if (filedTo != null) {
            // The confirmation takes the row's own slot, the same size as the row.
            Row(
                Modifier
                    .matchParentSize()
                    .ledgerFill(Metro.accent.fill)
                    .testTag("inbox-filed-${row.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppGlyphIcon(AppGlyph.Save, Metro.accent.onFill)
                Column(Modifier.padding(start = MetroDimens.Gutter)) {
                    MetroText(
                        "${InboxCopy.FILED_TO} ${filedTo.lowercase()}",
                        Metro.typography.body,
                        color = Metro.accent.onFill,
                        maxLines = 1,
                    )
                    MetroText(row.payee, Metro.typography.caption, color = Metro.accent.onFill.copy(alpha = 0.75f), maxLines = 1)
                }
            }
        }
    }
}

/** Shrinks a slot to [fraction] of its height, clipping only the bottom so bands still run past the gutter. */
private fun Modifier.collapse(fraction: Float): Modifier =
    if (fraction >= 1f) {
        this
    } else {
        drawWithContent { clipRect(-size.width, 0f, size.width * 2, size.height) { this@drawWithContent.drawContent() } }
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                layout(p.width, (p.height * fraction.coerceAtLeast(0f)).roundToInt()) { p.place(0, 0) }
            }
    }

private val INBOX_ROW_AIR = 15.dp
private val MENU_INSET = 24.dp
private const val MENU_SCRIM = 0.7f

/** A swipe past this share of the row's width files it; less springs back. */
private const val COMMIT = 0.35f

/** The share of the row at each side where a drag belongs to the pivot, not the row. */
internal const val SWIPE_DEAD_ZONE = 0.1f
private const val PEEK_REACH = 0.3f
private const val PEEK_DELAY_MS = 500L
private const val PEEK_MS = 320
private const val PEEK_HOLD_MS = 350L
private const val PEEK_GAP_MS = 200L
private const val EXIT_MS = 200
private const val RETURN_DELAY_MS = 600L
internal const val FILED_HOLD_MS = 900L
private const val COLLAPSE_MS = 240
