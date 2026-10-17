package app.duenorth.budget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
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
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroWeights
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

object InboxCopy {
    const val TO_CATEGORIZE = "to categorize"
    const val SPENDING = "spending"
    const val SELECTED = "selected"
    const val UP_NEXT = "up next"
    const val SUGGESTED = "suggested"
    const val NO_SUGGESTION = "no suggestion"
    const val ACCEPT = "accept"
    const val CATEGORIZE = "categorize"
    const val SELECT = "select"
    const val CANCEL = "cancel"
    const val TRANSACTIONS = "transactions"
    const val FILED_TO = "filed to"
    const val NO_CATEGORY = "no category"
    const val EDIT = "edit"
    const val SPLIT = "split"
    const val SCHEDULE = "schedule"
    const val DELETE = "delete"
    const val GO_TO = "go to"
}

/** What a row's tap and long-press menu can do, beyond accept and categorize. */
class InboxActions(
    val onDetails: (String) -> Unit = {},
    val onSplit: (String) -> Unit = {},
    val onSchedule: (String) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onOpenAccount: (String) -> Unit = {},
)

/**
 * The inbox: the top transaction stands apart in a panel with accept and categorize buttons, and
 * the rest wait below it as a ledger. Once the top one is filed, its confirmation fills the panel
 * and the next one rises into its place. Every row's long press opens a menu of all its actions,
 * and the bar's select ([selection] not null) gives every row a check box instead.
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
    actions: InboxActions = InboxActions(),
    selection: Set<String>? = null,
    onToggle: (String) -> Unit = {},
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
    val money = { minor: Long -> MoneyFormat.format(minor, shell.currency, formatOptions) }
    Column(modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            BudgetTotal(
                InboxCopy.TO_CATEGORIZE,
                "${waiting.size}",
                Metro.accent.text,
                "inbox-count",
                modifier = Modifier.weight(1f),
            )
            if (selection != null) {
                BudgetTotal(
                    InboxCopy.SELECTED,
                    "${selection.size}",
                    Metro.colors.foreground,
                    "inbox-selected",
                    alignEnd = true,
                    modifier = Modifier.weight(1f),
                )
            } else {
                BudgetTotal(
                    InboxCopy.SPENDING,
                    money(waiting.filter { it.amountMinor < 0 }.sumOf { it.amountMinor }),
                    Metro.colors.foreground,
                    "inbox-spending",
                    alignEnd = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (selection != null) {
            LazyColumn(Modifier.weight(1f), state = list) {
                // Rows just filed stay in the list until their confirmation has played.
                itemsIndexed(entries, key = { _, it -> it.row.id }) { i, entry ->
                    InboxLine(
                        entry = entry,
                        amount = money(entry.row.amountMinor),
                        banded = ledgerBanded(i),
                        onFiledShown = onFiledShown,
                        onTap = { onToggle(entry.row.id) },
                        onMenu = null,
                        checked = entry.row.id in selection,
                    )
                }
            }
            return@Column
        }
        // The top entry is featured. When it has just been filed, the next waiting one rises into the panel.
        val head = entries.first()
        val next = if (head.filedTo != null) entries.drop(1).firstOrNull { it.filedTo == null } else null
        val rise = remember(head.row.id, head.filedTo) { Animatable(if (head.filedTo == null) 1f else 0f) }
        val animations = Metro.animations
        val shown by rememberUpdatedState(onFiledShown)
        LaunchedEffect(head.row.id, head.filedTo) {
            if (head.filedTo == null) return@LaunchedEffect
            delay(FILED_HOLD_MS)
            if (animations && next != null) rise.animateTo(1f, tween(RISE_MS, easing = FastOutSlowInEasing)) else rise.snapTo(1f)
            shown(head.row.id)
        }
        val featured = if (head.filedTo == null) head.row else next?.row
        FeaturedPanel(
            row = featured,
            amount = featured?.let { money(it.amountMinor) }.orEmpty(),
            filed = head.takeIf { it.filedTo != null },
            rise = rise.value,
            onAccept = onAccept,
            onCategorize = onInbox,
            onDetails = actions.onDetails,
            onMenu = { row, bounds -> menu = row to bounds },
            modifier = Modifier.padding(top = FEATURED_AIR_ABOVE, bottom = FEATURED_AIR_BELOW),
        )
        val queue = entries.drop(1)
        if (queue.any { it.filedTo == null }) {
            MetroText(InboxCopy.UP_NEXT, Metro.typography.caption, Modifier.padding(bottom = 4.dp), color = Metro.colors.secondary)
        }
        LazyColumn(Modifier.weight(1f), state = list) {
            itemsIndexed(queue, key = { _, it -> it.row.id }) { i, entry ->
                InboxLine(
                    entry = entry,
                    amount = money(entry.row.amountMinor),
                    banded = ledgerBanded(i),
                    onFiledShown = onFiledShown,
                    onTap = { actions.onDetails(entry.row.id) },
                    onMenu = { bounds -> menu = entry.row to bounds },
                    onAccept = onAccept,
                    onCategorize = onInbox,
                    // The row rising into the panel leaves the queue as it goes.
                    leaving = if (entry == next) rise.value else 0f,
                )
            }
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

/**
 * The featured transaction: a full-width panel with the date, payee, amount, the suggested
 * category, and the two buttons. [filed] lays its confirmation over the panel while [row], the
 * next transaction, rises in by [rise]; the panel keeps the size of the transaction it shows.
 */
@Composable
private fun FeaturedPanel(
    row: InboxRow?,
    amount: String,
    filed: InboxEntry?,
    rise: Float,
    onAccept: (String) -> Unit,
    onCategorize: (String) -> Unit,
    onDetails: (String) -> Unit,
    onMenu: (InboxRow, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier
            .fillMaxWidth()
            .ledgerFill(Metro.colors.chrome)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .testTag("inbox-featured"),
    ) {
        if (row != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clipBelow()
                    .then(if (filed != null) Modifier.alpha(window(rise, RISE_FADE_FROM, 1f)).rising(1f - rise) else Modifier)
                    .metroPress(onClick = { onDetails(row.id) }, onLongClick = { onMenu(row, bounds) })
                    .padding(vertical = FEATURED_PAD)
                    .testTag("inbox-featured-${row.id}"),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(end = MetroDimens.Gutter)) {
                        MetroText(RegisterEntry.registerDateLabel(row.date), Metro.typography.caption, color = Metro.accent.text)
                        MetroText(row.payee, Metro.typography.groupHeader, maxLines = 2)
                        MetroText(
                            listOfNotNull(row.accountName.ifBlank { null }, row.note.ifBlank { null }).joinToString(" · "),
                            Metro.typography.caption,
                            color = Metro.colors.secondary,
                            maxLines = 2,
                        )
                    }
                    MetroText(
                        amount,
                        Metro.typography.groupHeader.copy(textAlign = TextAlign.End),
                        Modifier.testTag("inbox-featured-amount"),
                        color = Metro.accent.text,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (row.suggestionId != null) {
                        MetroText("${InboxCopy.SUGGESTED} ", Metro.typography.caption, color = Metro.colors.secondary)
                        MetroText(row.suggestionName, Metro.typography.body.copy(fontWeight = MetroWeights.Semibold))
                    } else {
                        MetroText(InboxCopy.NO_SUGGESTION, Metro.typography.caption, color = Metro.colors.secondary)
                    }
                }
                MetroButtonGrid(Modifier.padding(top = 12.dp)) {
                    if (row.suggestionId != null) AcceptButton { onAccept(row.id) }
                    MetroButton(InboxCopy.CATEGORIZE, Modifier.testTag("inbox-featured-categorize")) { onCategorize(row.id) }
                }
            }
        }
        if (filed != null) {
            Row(
                Modifier
                    .then(if (row != null) Modifier.matchParentSize() else Modifier.heightIn(min = FEATURED_EMPTY))
                    .alpha(1f - window(rise, 0f, RISE_FADE_TO))
                    .testTag("inbox-filed-${filed.row.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppGlyphIcon(AppGlyph.Save, Metro.accent.text)
                Column(Modifier.padding(start = MetroDimens.Gutter)) {
                    MetroText("${InboxCopy.FILED_TO} ${filed.filedTo.orEmpty().lowercase()}", Metro.typography.groupHeader, maxLines = 1)
                    MetroText(filed.row.payee, Metro.typography.caption, color = Metro.colors.secondary, maxLines = 1)
                }
            }
        }
    }
}

/** The featured accept: the one filled button on the page, in the accent colour. */
@Composable
private fun AcceptButton(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.ButtonMin)
            .border(MetroDimens.Stroke, Metro.accent.fill)
            .background(Metro.accent.fill)
            .metroPress(onClick)
            .padding(horizontal = 9.5.dp, vertical = 3.5.dp)
            .testTag("inbox-featured-accept"),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(InboxCopy.ACCEPT, Metro.typography.body.copy(fontWeight = MetroWeights.Semibold), color = Metro.accent.onFill)
    }
}

/** Where [t] sits between [from] and [to], clamped to 0..1. */
private fun window(
    t: Float,
    from: Float,
    to: Float,
) = ((t - from) / (to - from)).coerceIn(0f, 1f)

/** Pushes content down by [fraction] of its own height, so it rises into its slot from below. */
private fun Modifier.rising(fraction: Float): Modifier =
    layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        layout(p.width, p.height) { p.place(0, (p.height * fraction).roundToInt()) }
    }

/** Clips only what falls below the slot, so the panel's colour still runs past the gutter. */
private fun Modifier.clipBelow(): Modifier =
    drawWithContent { clipRect(-size.width, 0f, size.width * 2, size.height) { this@drawWithContent.drawContent() } }

/** The quieter line under a payee: the suggested category, the date, the account, then the note. */
internal fun inboxDetail(row: InboxRow): String =
    listOfNotNull(
        row.suggestionName.ifBlank { InboxCopy.NO_CATEGORY },
        row.date.takeIf { it > 0 }?.let(RegisterEntry::registerDateLabel),
        row.accountName.ifBlank { null },
        row.note.ifBlank { null },
    ).joinToString(" · ")

/**
 * One waiting row. A tap opens it ([onTap]; in select mode it checks the row instead) and a long
 * press opens its menu. A row filed from its menu turns accent in place, then collapses; [leaving]
 * collapses the row that is rising into the featured panel.
 */
@Composable
private fun InboxLine(
    entry: InboxEntry,
    amount: String,
    banded: Boolean,
    onFiledShown: (String) -> Unit,
    onTap: () -> Unit,
    onMenu: ((Rect) -> Unit)?,
    onAccept: (String) -> Unit = {},
    onCategorize: (String) -> Unit = {},
    checked: Boolean? = null,
    leaving: Float = 0f,
) {
    val row = entry.row
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val animations = Metro.animations
    val height = remember { Animatable(1f) }
    val accept by rememberUpdatedState(onAccept)
    val categorize by rememberUpdatedState(onCategorize)
    val shown by rememberUpdatedState(onFiledShown)
    val filedTo = entry.filedTo
    // Filed: hold the confirmation in the row's own slot, then collapse it away.
    LaunchedEffect(filedTo) {
        if (filedTo == null) {
            height.snapTo(1f)
            return@LaunchedEffect
        }
        delay(FILED_HOLD_MS)
        if (animations) height.animateTo(0f, tween(COLLAPSE_MS)) else height.snapTo(0f)
        shown(row.id)
    }
    val acceptsHere = row.suggestionId != null
    Box(
        Modifier
            .fillMaxWidth()
            .collapse(height.value * (1f - leaving))
            .alpha(1f - leaving)
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
        Row(
            Modifier
                .fillMaxWidth()
                .alpha(if (filedTo != null) 0f else 1f)
                .heightIn(min = MetroDimens.TouchTarget)
                .ledgerFill(if (banded) ledgerBand else Color.Transparent)
                .metroPress(onClick = onTap, onLongClick = onMenu?.let { open -> { open(bounds) } })
                .padding(vertical = INBOX_ROW_AIR),
            verticalAlignment = if (checked != null) Alignment.CenterVertically else Alignment.Top,
        ) {
            if (checked != null) InboxCheck(checked, row.id)
            Column(
                Modifier
                    .weight(LEDGER_NAME + LEDGER_FIGURE)
                    .padding(start = if (checked != null) MetroDimens.Gutter else 0.dp, end = MetroDimens.Gutter),
            ) {
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
                amount,
                Metro.typography.body.copy(textAlign = TextAlign.End),
                Modifier.weight(LEDGER_FIGURE * 2).testTag("inbox-amount-${row.id}"),
                maxLines = 1,
                softWrap = false,
            )
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

/** The select-mode check box: an outlined square, filled with the accent and a tick when checked. */
@Composable
private fun InboxCheck(
    checked: Boolean,
    id: String,
) {
    Box(
        Modifier
            .size(CHECK_SIZE)
            .border(MetroDimens.Stroke, Metro.colors.foreground)
            .background(if (checked) Metro.accent.fill else Color.Transparent)
            .testTag(if (checked) "inbox-checked-$id" else "inbox-unchecked-$id"),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) AppGlyphIcon(AppGlyph.Save, Metro.accent.onFill)
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
private val FEATURED_AIR_ABOVE = 24.dp
private val FEATURED_AIR_BELOW = 28.dp
private val FEATURED_PAD = 20.dp
private val FEATURED_EMPTY = 96.dp
private val CHECK_SIZE = 26.dp
private val MENU_INSET = 24.dp
private const val MENU_SCRIM = 0.7f
internal const val FILED_HOLD_MS = 900L
private const val COLLAPSE_MS = 240
private const val RISE_MS = 420

/** The confirmation is gone by this share of the rise; the next transaction starts showing at the other. */
private const val RISE_FADE_TO = 0.4f
private const val RISE_FADE_FROM = 0.3f
