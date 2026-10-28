package app.duenorth.budget

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.graphicsLayer
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
import app.duenorth.budget.core.MatchCopy
import app.duenorth.budget.core.MatchPair
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.design.MetroMotion
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroContextMenu
import app.duenorth.budget.design.components.MetroContextMenuPanel
import app.duenorth.budget.design.components.MetroMenuItem
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.controlFrame
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroWeights
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

object InboxCopy {
    const val TO_CATEGORIZE = "to categorize"
    const val SPENDING = "spending"
    const val SELECTED = "selected"
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
    const val SYNC = "sync"
    const val READY = "ready to accept"
    const val NEEDS_CATEGORY = "needs a category"
}

/** What a row's tap and long-press menu can do, beyond accept and categorize. */
class InboxActions(
    val onDetails: (String) -> Unit = {},
    val onSplit: (String) -> Unit = {},
    val onSchedule: (String) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onOpenAccount: (String) -> Unit = {},
    val onMatch: (MatchPair) -> Unit = {},
    val onNotAMatch: (MatchPair) -> Unit = {},
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
    formatOptions: FormatOptions = formatOptions(),
    onAccept: (String) -> Unit = onInbox,
    filed: List<InboxFiled> = emptyList(),
    onFiledShown: (String) -> Unit = {},
    actions: InboxActions = InboxActions(),
    selection: Set<String>? = null,
    onToggle: (String) -> Unit = {},
    lead: String? = null,
    /** How the menu's sync went; one line under the totals. */
    notice: String? = null,
) {
    // Both sides of a possible match wait in the pair panel, not in the queue.
    val paired = shell.matches.flatMapTo(mutableSetOf()) { it.ids }
    val pair = shell.matches.firstOrNull()?.takeIf { selection == null }
    val inbox = shell.inbox.filter { it.id !in paired }.sortedBy { if (it.id == lead) 0 else 1 }
    val entries = inboxEntries(inbox, filed)
    var menu by remember { mutableStateOf<Pair<InboxRow, Rect>?>(null) }
    if (entries.isEmpty() && pair == null) {
        Column(modifier.fillMaxSize()) {
            if (notice != null) MonthNotice(notice, Modifier.padding(horizontal = MetroDimens.Gutter))
            InboxEmpty(Modifier.weight(1f))
        }
        return
    }
    val list = rememberLazyListState()
    val selectList = rememberLazyListState()
    LaunchedEffect(list, selectList) {
        snapshotFlow { list.isScrollInProgress || selectList.isScrollInProgress }.collect { onListGesture(it) }
    }
    val waiting = inbox.filter { row -> filed.none { it.row.id == row.id } }
    val money = { minor: Long -> MoneyFormat.format(minor, shell.currency, formatOptions) }
    val animations = Metro.animations
    Column(modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            BudgetTotal(
                InboxCopy.TO_CATEGORIZE,
                "${waiting.size}",
                Metro.accent.text,
                "inbox-count",
                modifier = Modifier.weight(1f),
            )
            // Keyed on whether select mode is on, so a new count updates in place and only the mode change fades.
            AnimatedContent(
                selection,
                Modifier.weight(1f),
                transitionSpec = { modeFade(animations) },
                contentAlignment = Alignment.TopEnd,
                label = "inbox-total",
                contentKey = { it != null },
            ) { picked ->
                if (picked != null) {
                    BudgetTotal(
                        InboxCopy.SELECTED,
                        "${picked.size}",
                        Metro.colors.foreground,
                        "inbox-selected",
                        alignEnd = true,
                    )
                } else if (shell.matches.isNotEmpty()) {
                    BudgetTotal(
                        MatchCopy.TO_MATCH,
                        "${shell.matches.size}",
                        Metro.colors.foreground,
                        "inbox-to-match",
                        alignEnd = true,
                    )
                } else {
                    BudgetTotal(
                        InboxCopy.SPENDING,
                        money(waiting.filter { it.amountMinor < 0 }.sumOf { it.amountMinor }),
                        Metro.colors.foreground,
                        "inbox-spending",
                        alignEnd = true,
                    )
                }
            }
        }
        if (notice != null) MonthNotice(notice, Modifier.padding(bottom = 4.dp))
        // Select mode fades through: the panel and queue fade out, then the checked list fades in.
        AnimatedContent(
            selection,
            Modifier.weight(1f),
            transitionSpec = { modeFade(animations) },
            label = "inbox-mode",
            contentKey = { it != null },
        ) { picked ->
            if (picked != null) {
                LazyColumn(Modifier.fillMaxSize(), state = selectList) {
                    // Two groups: rows with a category to accept, then rows that still need one.
                    // Rows just filed stay in the list until their confirmation has played.
                    inboxSelectGroups(entries).forEach { (title, group) ->
                        item(key = "group-$title") { InboxGroupHeading(title, group.size) }
                        inboxDayItems(group) { i, entry ->
                            InboxLine(
                                entry = entry,
                                amount = money(entry.row.amountMinor),
                                banded = ledgerBanded(i),
                                onFiledShown = onFiledShown,
                                onTap = { onToggle(entry.row.id) },
                                onMenu = null,
                                checked = entry.row.id in picked,
                            )
                        }
                    }
                }
            } else {
                InboxQueue(
                    pair = pair,
                    entries = entries,
                    list = list,
                    money = money,
                    onAccept = onAccept,
                    onInbox = onInbox,
                    onFiledShown = onFiledShown,
                    actions = actions,
                    onMenu = { row, bounds -> menu = row to bounds },
                )
            }
        }
    }
    menu?.let { (row, anchor) ->
        InboxMenu(anchor, inboxMenuItems(row, onAccept, onInbox, actions), onDismiss = { menu = null })
    }
}

/** The featured panel over the waiting queue: what the inbox shows outside select mode. */
@Composable
private fun InboxQueue(
    pair: MatchPair?,
    entries: List<InboxEntry>,
    list: LazyListState,
    money: (Long) -> String,
    onAccept: (String) -> Unit,
    onInbox: (String) -> Unit,
    onFiledShown: (String) -> Unit,
    actions: InboxActions,
    onMenu: (InboxRow, Rect) -> Unit,
) {
    // A possible match leads the inbox: it takes the panel and every waiting row stays in the queue.
    if (pair != null) {
        Column(Modifier.fillMaxSize()) {
            MatchPanel(
                pair = pair,
                money = money,
                onMatch = { actions.onMatch(pair) },
                onNotAMatch = { actions.onNotAMatch(pair) },
                modifier = Modifier.padding(top = FEATURED_AIR_ABOVE, bottom = FEATURED_AIR_BELOW),
            )
            LazyColumn(Modifier.weight(1f), state = list) {
                inboxDayItems(entries) { i, entry ->
                    InboxLine(
                        entry = entry,
                        amount = money(entry.row.amountMinor),
                        banded = ledgerBanded(i),
                        onFiledShown = onFiledShown,
                        onTap = { actions.onDetails(entry.row.id) },
                        onMenu = { bounds -> onMenu(entry.row, bounds) },
                        onAccept = onAccept,
                        onCategorize = onInbox,
                    )
                }
            }
        }
        return
    }
    if (entries.isEmpty()) return
    // The top entry is featured. When it has just been filed, the next waiting one rises into the panel.
    val head = entries.first()
    val next = if (head.filedTo != null) entries.drop(1).firstOrNull { it.filedTo == null } else null
    val rise = remember(head.row.id, head.filedTo) { Animatable(if (head.filedTo == null) 1f else 0f) }
    val animations = Metro.animations
    val shown by rememberUpdatedState(onFiledShown)
    LaunchedEffect(head.row.id, head.filedTo) {
        if (head.filedTo == null) return@LaunchedEffect
        delay(FILED_HOLD_MS)
        if (animations && next != null) {
            rise.animateTo(1f, tween(MetroMotion.PAGE_MS, easing = MetroMotion.EaseOut))
        } else {
            rise.snapTo(1f)
        }
        shown(head.row.id)
    }
    // Read only in layout and drawing, so the rise never recomposes the panel or the list.
    val risen = { rise.value }
    val featured = if (head.filedTo == null) head.row else next?.row
    Column(Modifier.fillMaxSize()) {
        FeaturedPanel(
            row = featured,
            amount = featured?.let { money(it.amountMinor) }.orEmpty(),
            filed = head.takeIf { it.filedTo != null },
            rise = risen,
            onAccept = onAccept,
            onCategorize = onInbox,
            onDetails = actions.onDetails,
            onMenu = onMenu,
            modifier = Modifier.padding(top = FEATURED_AIR_ABOVE, bottom = FEATURED_AIR_BELOW),
        )
        LazyColumn(Modifier.weight(1f), state = list) {
            inboxDayItems(entries.drop(1), leaving = next?.let { it to risen }) { i, entry ->
                InboxLine(
                    entry = entry,
                    amount = money(entry.row.amountMinor),
                    banded = ledgerBanded(i),
                    onFiledShown = onFiledShown,
                    onTap = { actions.onDetails(entry.row.id) },
                    onMenu = { bounds -> onMenu(entry.row, bounds) },
                    onAccept = onAccept,
                    onCategorize = onInbox,
                    // The row rising into the panel leaves the queue as it goes.
                    leaving = if (entry == next) risen else NOT_LEAVING,
                )
            }
        }
    }
}

/** The select-mode change: the old view fades out, then the new one fades in, so the two never overlap. */
private fun modeFade(animations: Boolean): ContentTransform =
    if (animations) {
        fadeIn(tween(MODE_IN_MS, delayMillis = MODE_OUT_MS, easing = MetroMotion.EaseOut)) togetherWith
            fadeOut(tween(MODE_OUT_MS))
    } else {
        EnterTransition.None togetherWith ExitTransition.None
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

/** The inbox's long-press menu: the shared Metro context menu, tagged `inbox-menu`. */
@Composable
private fun InboxMenu(
    anchor: Rect,
    items: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    MetroContextMenu(anchor, items.menuItems(), onDismiss, tag = INBOX_MENU)
}

@Composable
internal fun InboxMenuPanel(
    anchor: Rect,
    items: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
    safeTop: Int = 0,
    safeBottom: Int = 0,
) {
    MetroContextMenuPanel(anchor, items.menuItems(), onDismiss, INBOX_MENU, safeTop, safeBottom)
}

private fun List<Pair<String, () -> Unit>>.menuItems() = map { (label, run) -> MetroMenuItem(label, run = run) }

private const val INBOX_MENU = "inbox-menu"

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
    rise: () -> Float,
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
            .clipBelow()
            .ledgerFill(Metro.colors.chrome)
            // The next transaction can be taller or shorter: the panel eases to its size instead of jumping.
            .easedHeight()
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .testTag("inbox-featured"),
    ) {
        if (row != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clipBelow()
                    .then(if (filed != null) Modifier.rising(rise) else Modifier)
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
                    .graphicsLayer { alpha = 1f - window(rise(), 0f, RISE_FADE_TO) }
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
private fun AcceptButton(onClick: () -> Unit) = AccentButton(InboxCopy.ACCEPT, Modifier.testTag("inbox-featured-accept"), onClick)

/** A filled button in the accent colour, for the one action a panel leads with. */
@Composable
internal fun AccentButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.ButtonMin)
            .border(MetroDimens.Stroke, Metro.accent.fill)
            .background(Metro.accent.fill)
            .metroPress(onClick)
            .padding(horizontal = 9.5.dp, vertical = 3.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(label, Metro.typography.body.copy(fontWeight = MetroWeights.Semibold), color = Metro.accent.onFill)
    }
}

/** Where [t] sits between [from] and [to], clamped to 0..1. */
private fun window(
    t: Float,
    from: Float,
    to: Float,
) = ((t - from) / (to - from)).coerceIn(0f, 1f)

/** Rises content into its slot from below as [rise] goes from 0 to 1, fading it in on the way. */
private fun Modifier.rising(rise: () -> Float): Modifier =
    graphicsLayer {
        val t = rise()
        translationY = size.height * (1f - t)
        alpha = window(t, RISE_FADE_FROM, 1f)
    }

/** Eases the slot's height to its content's whenever the content changes size, without clipping its sides. */
@Composable
private fun Modifier.easedHeight(): Modifier {
    if (!Metro.animations) return this
    val scope = rememberCoroutineScope()
    val height = remember { EasedHeight() }
    return layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        val target = p.height.toFloat()
        val anim = height.anim ?: Animatable(target).also { height.anim = it }
        if (anim.targetValue != target) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                anim.animateTo(target, tween(MetroMotion.SHORT_MS, easing = MetroMotion.Standard))
            }
        }
        layout(p.width, anim.value.roundToInt()) { p.place(0, 0) }
    }
}

private class EasedHeight {
    var anim: Animatable<Float, AnimationVector1D>? = null
}

/** Clips only what falls below the slot, so the panel's colour still runs past the gutter. */
private fun Modifier.clipBelow(): Modifier =
    drawWithContent { clipRect(-size.width, 0f, size.width * 2, size.height) { this@drawWithContent.drawContent() } }

/** Select mode's groups, each in inbox order: rows ready to accept, then rows that need a category. Empty groups are left out. */
internal fun inboxSelectGroups(entries: List<InboxEntry>): List<Pair<String, List<InboxEntry>>> {
    val (ready, needs) = entries.partition { it.row.suggestionId != null }
    return listOf(InboxCopy.READY to ready, InboxCopy.NEEDS_CATEGORY to needs).filter { it.second.isNotEmpty() }
}

/** A select-mode group's heading: its name, with how many rows wait under it. */
@Composable
private fun InboxGroupHeading(
    title: String,
    count: Int,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = GROUP_AIR_ABOVE, bottom = 2.dp)
            .testTag("inbox-group-$title"),
        verticalAlignment = Alignment.Bottom,
    ) {
        MetroText(title, Metro.typography.groupHeader, Modifier.weight(1f), maxLines = 1)
        MetroText("$count", Metro.typography.groupHeader, color = Metro.colors.secondary, maxLines = 1)
    }
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

/**
 * [entries] grouped under date headings, each row drawn by [line] with its index across the whole
 * list for banding. [leaving] is a row on its way out and how far it has gone; a heading goes with
 * it when nothing else in its day is still waiting.
 */
private fun LazyListScope.inboxDayItems(
    entries: List<InboxEntry>,
    leaving: Pair<InboxEntry, () -> Float>? = null,
    line: @Composable (Int, InboxEntry) -> Unit,
) {
    var start = 0
    inboxDays(entries).forEach { day ->
        val waiting = day.filter { it.filedTo == null }
        val first = day.first()
        item(key = "day-${first.row.date}-${first.row.id}") {
            InboxDayHeading(
                first.row.date,
                filed = waiting.isEmpty(),
                leaving = leaving?.takeIf { (entry, _) -> waiting == listOf(entry) }?.second ?: NOT_LEAVING,
            )
        }
        val offset = start
        itemsIndexed(day, key = { _, it -> it.row.id }) { i, entry -> line(offset + i, entry) }
        start += day.size
    }
}

/** A day's date over its rows. It collapses once every row under it has been filed, or with [leaving]. */
@Composable
private fun InboxDayHeading(
    date: Int,
    filed: Boolean,
    leaving: () -> Float,
) {
    val height = remember { Animatable(1f) }
    val animations = Metro.animations
    LaunchedEffect(filed) {
        if (filed) {
            delay(FILED_HOLD_MS)
            if (animations) height.animateTo(0f, collapseSpec) else height.snapTo(0f)
        } else {
            height.snapTo(1f)
        }
    }
    MetroText(
        RegisterEntry.registerDateLabel(date),
        Metro.typography.caption,
        Modifier
            .collapse { height.value * (1f - leaving()) }
            .graphicsLayer { alpha = 1f - leaving() }
            .padding(top = DAY_AIR_ABOVE, bottom = 4.dp)
            .testTag("inbox-day-$date"),
        color = Metro.accent.text,
    )
}

/** The quieter line under a payee: the suggested category, the account, then the note. The date is the row's heading. */
internal fun inboxDetail(row: InboxRow): String =
    listOfNotNull(
        row.suggestionName.ifBlank { InboxCopy.NO_CATEGORY },
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
    leaving: () -> Float = NOT_LEAVING,
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
        if (animations) height.animateTo(0f, collapseSpec) else height.snapTo(0f)
        shown(row.id)
    }
    val acceptsHere = row.suggestionId != null
    Box(
        Modifier
            .fillMaxWidth()
            .collapse { height.value * (1f - leaving()) }
            .graphicsLayer { alpha = 1f - leaving() }
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
            .controlFrame(fill = if (checked) Metro.accent.fill else Metro.colors.background)
            .background(if (checked) Metro.accent.fill else Color.Transparent)
            .testTag(if (checked) "inbox-checked-$id" else "inbox-unchecked-$id"),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) AppGlyphIcon(AppGlyph.Save, Metro.accent.onFill)
    }
}

/**
 * Shrinks a slot to [fraction] of its height, clipping only the bottom so bands still run past the
 * gutter. [fraction] is read in layout and drawing, so a collapse never recomposes the row.
 */
private fun Modifier.collapse(fraction: () -> Float): Modifier =
    drawWithContent {
        if (fraction() >= 1f) {
            drawContent()
        } else {
            clipRect(-size.width, 0f, size.width * 2, size.height) { this@drawWithContent.drawContent() }
        }
    }.layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        layout(p.width, (p.height * fraction().coerceIn(0f, 1f)).roundToInt()) { p.place(0, 0) }
    }

private val NOT_LEAVING = { 0f }

private val collapseSpec = tween<Float>(MetroMotion.SHORT_MS, easing = MetroMotion.Standard)

private val INBOX_ROW_AIR = 15.dp
private val FEATURED_AIR_ABOVE = 24.dp
private val FEATURED_AIR_BELOW = 28.dp
internal val FEATURED_PAD = 20.dp
private val FEATURED_EMPTY = 96.dp
private val CHECK_SIZE = 26.dp
internal const val FILED_HOLD_MS = 900L
private val DAY_AIR_ABOVE = 12.dp
private val GROUP_AIR_ABOVE = 20.dp
private const val MODE_OUT_MS = 90
private const val MODE_IN_MS = 180

/** The confirmation is gone by this share of the rise; the next transaction starts showing at the other. */
private const val RISE_FADE_TO = 0.4f
private const val RISE_FADE_FROM = 0.3f
