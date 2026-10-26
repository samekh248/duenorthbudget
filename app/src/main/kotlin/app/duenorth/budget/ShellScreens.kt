package app.duenorth.budget

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.duenorth.budget.core.AccountRow
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MockBudgetDataset
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.NetWorthPage
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.core.monthLabel
import app.duenorth.budget.design.MetroMotion
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.MetroAppBarHost
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroOutlineButton
import app.duenorth.budget.design.components.MetroPivot
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.PivotItem
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.components.relief
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.Relief
import java.time.YearMonth

@Composable
fun HomePivot(
    shell: MonthShell?,
    loading: Boolean,
    openMonth: YearMonth,
    monthWindow: MonthWindow,
    initialSection: Int = 0,
    onGesture: (Boolean) -> Unit,
    onSection: (Int) -> Unit = {},
    onAccount: (String) -> Unit = {},
    onInbox: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    onSelectMonth: (YearMonth) -> Unit = {},
    onBudgetCategory: ((String) -> Unit)? = null,
    onAddTransaction: (() -> Unit)? = null,
    formatOptions: FormatOptions = formatOptions(),
    onToBudget: (() -> Unit)? = null,
    notice: String? = null,
    onAcceptInbox: (String) -> Unit = onInbox,
    inboxFiled: List<InboxFiled> = emptyList(),
    onInboxFiledShown: (String) -> Unit = {},
    inboxActions: InboxActions = InboxActions(),
    /** Rows checked in the inbox's select mode; null when not selecting. */
    inboxSelection: Set<String>? = null,
    onInboxToggle: (String) -> Unit = {},
    inboxLead: String? = null,
    netWorth: NetWorthPage? = null,
    onNetWorth: () -> Unit = {},
    onMonthReview: () -> Unit = {},
    onReport: (Int) -> Unit = {},
    /** The budget's edit mode; null when not editing. */
    budgetEdit: BudgetEdit? = null,
    budgetEditActions: BudgetEditActions = BudgetEditActions(),
    editError: String? = null,
    /** Experimental: the free-after-later-plans line under to budget. */
    showAhead: Boolean = false,
    /** The accounts item's reorder mode: drag handles on its rows, and a drop moves the account. */
    accountsReorder: Boolean = false,
    onMoveAccount: (String, String?) -> Unit = { _, _ -> },
) {
    var pagerGesture by remember { mutableStateOf(false) }
    var listGesture by remember { mutableStateOf(false) }
    LaunchedEffect(pagerGesture, listGesture) { onGesture(pagerGesture || listGesture) }
    // Leaving Home mid-scroll (say, to move money from edit mode) must not leave the shell's repaints held back.
    DisposableEffect(Unit) { onDispose { onGesture(false) } }
    MetroPivot(
        title = SectionTitleCopy.APP,
        items =
            listOf(
                PivotItem(HomeSections.INBOX) {
                    if (shell == null) {
                        if (loading) Placeholder()
                    } else {
                        InboxSection(
                            shell,
                            onListGesture = { listGesture = it },
                            onInbox = onInbox,
                            onAdd = onAddTransaction,
                            formatOptions = formatOptions,
                            onAccept = onAcceptInbox,
                            filed = inboxFiled,
                            onFiledShown = onInboxFiledShown,
                            actions = inboxActions,
                            selection = inboxSelection,
                            onToggle = onInboxToggle,
                            lead = inboxLead,
                        )
                    }
                },
                PivotItem(HomeSections.BUDGET) {
                    if (shell == null) {
                        if (loading) Placeholder()
                    } else if (budgetEdit != null) {
                        BudgetEditSection(
                            shell,
                            budgetEdit,
                            budgetEditActions,
                            onListGesture = { listGesture = it },
                            error = editError,
                            formatOptions = formatOptions,
                        )
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            MonthControl(
                                month = openMonth,
                                window = monthWindow,
                                onSelect = onSelectMonth,
                                modifier =
                                    Modifier.padding(
                                        start = MetroDimens.Gutter,
                                        end = MetroDimens.Gutter,
                                        bottom = MetroDimens.Row,
                                    ),
                            )
                            BudgetSection(
                                shell,
                                onListGesture = { listGesture = it },
                                modifier = Modifier.weight(1f),
                                onCategory = onBudgetCategory,
                                formatOptions = formatOptions,
                                onToBudget = onToBudget,
                                notice = notice,
                                showAhead = showAhead,
                            )
                        }
                    }
                },
                PivotItem(HomeSections.ACCOUNTS) {
                    if (shell == null) {
                        if (loading) Placeholder()
                    } else {
                        AccountsSection(
                            shell.accounts,
                            shell,
                            onListGesture = { listGesture = it },
                            onAccount = onAccount,
                            formatOptions = formatOptions,
                            reorder = accountsReorder,
                            onMove = onMoveAccount,
                        )
                    }
                },
                PivotItem(HomeSections.REPORTS) {
                    ReportsHub(
                        netWorth = netWorth,
                        month = openMonth,
                        onNetWorth = onNetWorth,
                        onMonthReview = onMonthReview,
                        onReport = onReport,
                    )
                },
            ),
        modifier = modifier,
        initialItem = initialSection,
        // Edit mode holds the pivot on the budget, and reorder on the accounts, so a stray swipe cannot leave them.
        locked = budgetEdit != null || accountsReorder,
        onGesture = { pagerGesture = it },
        onSelected = onSection,
    )
}

/**
 * The open month, as on every screen that has one: the month centered on its own.
 * Tapping it opens a year of months to jump to; there are no step arrows beside it.
 */
@Composable
fun MonthControl(
    month: YearMonth,
    window: MonthWindow,
    onSelect: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val calendar = YearMonth.now()
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .heightIn(min = MetroDimens.TouchTarget)
                    .metroPress { expanded = !expanded }
                    .testTag("month-label"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetroText(monthLabel(month), Metro.typography.subheader, maxLines = 1, softWrap = false)
                // Says the month opens; turns over while it is open.
                Box(
                    Modifier
                        .graphicsLayer {
                            scaleX = 0.7f
                            scaleY = 0.7f
                            rotationZ = if (expanded) 180f else 0f
                        }.testTag("month-dropdown"),
                ) {
                    AppGlyphIcon(AppGlyph.ChevronDown, color = Metro.colors.secondary)
                }
            }
            // Off to the side, so the centered month doesn't shift when it comes and goes.
            if (month != calendar) {
                Box(Modifier.align(Alignment.CenterEnd)) {
                    MonthIconButton(AppGlyph.Calendar, "month-current") { onSelect(calendar) }
                }
            }
        }
        if (expanded) {
            MonthGrid(month, window, calendar) {
                onSelect(it)
                expanded = false
            }
        }
    }
}

/**
 * A year of months on one panel, four to a row, with the year stepped by its own arrows.
 * The open month is filled with the accent and this calendar month is in accent text; months
 * outside the window are dimmed and can't be picked.
 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    window: MonthWindow,
    calendar: YearMonth,
    onSelect: (YearMonth) -> Unit,
) {
    var year by remember(month) { mutableIntStateOf(month.year) }
    val earliest = window.oldest.year
    val latest = window.newest.year
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = MetroDimens.Row)
            .background(Metro.colors.chrome)
            .padding(MetroDimens.Row)
            .testTag("month-list"),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(MetroDimens.TouchTarget)) {
                    if (year > earliest) MonthIconButton(AppGlyph.ChevronLeft, "month-year-previous") { year -= 1 }
                }
                Box(Modifier.widthIn(min = 64.dp), contentAlignment = Alignment.Center) {
                    MetroText(year.toString(), Metro.typography.body, Modifier.testTag("month-year"), color = Metro.colors.secondary)
                }
                Box(Modifier.size(MetroDimens.TouchTarget)) {
                    if (year < latest) MonthIconButton(AppGlyph.ChevronRight, "month-year-next") { year += 1 }
                }
            }
        }
        (1..12).chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { number ->
                    val option = YearMonth.of(year, number)
                    MonthCell(
                        option = option,
                        selected = option == month,
                        current = option == calendar,
                        enabled = window.contains(option),
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthCell(
    option: YearMonth,
    selected: Boolean,
    current: Boolean,
    enabled: Boolean,
    onSelect: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color =
        when {
            selected -> Metro.accent.onFill
            !enabled -> Metro.colors.secondary.copy(alpha = 0.4f)
            current -> Metro.accent.text
            else -> Metro.colors.foreground
        }
    Box(
        modifier
            .height(MetroDimens.TouchTarget)
            .then(if (selected) Modifier.background(Metro.accent.fill) else Modifier)
            .then(if (enabled) Modifier.metroPress { onSelect(option) } else Modifier)
            .testTag("month-option-$option"),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(monthLabel(option).take(3), Metro.typography.body, color = color)
    }
}

@Composable
internal fun MonthIconButton(
    glyph: AppGlyph,
    tag: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(MetroDimens.TouchTarget)
            .metroPress(onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        AppGlyphIcon(glyph)
    }
}

@Composable
fun BudgetSection(
    shell: MonthShell,
    onListGesture: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onCategory: ((String) -> Unit)? = null,
    formatOptions: FormatOptions = formatOptions(),
    onToBudget: (() -> Unit)? = null,
    notice: String? = null,
    showAhead: Boolean = false,
) {
    val list = rememberLazyListState()
    val arrivals = remember { RowArrivals() }
    arrivals.prime(shell.groups.map { it.id })
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onListGesture(it) }
    }
    val style = Metro.style
    val error = shell.headerMinor < 0
    // Tile relief sets to budget on a flat accent tile (red while overbudgeted) and held on a raised neutral one.
    val tiles = style.heroTiles
    val onTile = if (tiles) (if (error) Metro.colors.onErrorFill else Metro.accent.onFill) else null
    val tile = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
    Column(modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    when {
                        tiles -> Modifier.padding(top = 6.dp)
                        // Mica holds both figures on one layer card.
                        style.layers -> Modifier.padding(top = 6.dp).relief(Relief.Raised).padding(horizontal = 12.dp, vertical = 8.dp)
                        else -> Modifier
                    },
                ),
            horizontalArrangement = if (tiles) Arrangement.spacedBy(MetroDimens.Gutter) else Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            BudgetTotal(
                label = shell.headerLabel,
                amount = MoneyFormat.format(shell.headerMinor, shell.currency, formatOptions),
                amountColor = onTile ?: Metro.accent.text,
                amountTag = "to-budget",
                error = error,
                labelColor = onTile,
                modifier =
                    (
                        if (tiles) {
                            Modifier
                                .weight(1.3f)
                                .relief(Relief.Raised, small = true, fill = if (error) Metro.colors.errorFill else Metro.accent.fill)
                                .then(tile)
                        } else {
                            Modifier
                        }
                    ).then(
                        if (onToBudget == null) {
                            Modifier
                        } else {
                            Modifier.metroPress(onClick = onToBudget).testTag("to-budget-figure")
                        },
                    ),
            )
            if (shell.bufferedMinor != 0L) {
                BudgetTotal(
                    label = "held",
                    amount = MoneyFormat.format(shell.bufferedMinor, shell.currency, formatOptions),
                    amountColor = Metro.colors.secondary,
                    amountTag = "held-amount",
                    alignEnd = true,
                    modifier = if (tiles) Modifier.weight(1f).relief(Relief.Raised, small = true).then(tile) else Modifier,
                )
            }
        }
        val ahead = shell.ahead
        if (showAhead && ahead != null && shell.mode == BudgetMode.ENVELOPE) {
            MetroText(
                FreeAfterPlansCopy.line(
                    MoneyFormat.format(ahead.freeMinor, shell.currency, formatOptions),
                    shell.month,
                    ahead.throughMonth,
                ),
                Metro.typography.caption,
                Modifier.padding(top = MetroDimens.Row).testTag("free-after-plans"),
                color = if (ahead.freeMinor < 0) Metro.colors.error else Metro.colors.secondary,
            )
        }
        if (notice != null) MonthNotice(notice, Modifier.padding(top = MetroDimens.Row))
        if (shell.groups.isEmpty() && shell.incomeGroups.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(top = MetroDimens.Grid),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BudgetLogo(EMPTY_LOGO_HEIGHT)
                MetroText(ShellCopy.NOTHING_TO_BUDGET, Metro.typography.caption, Modifier.padding(top = MetroDimens.Grid))
            }
        } else {
            val cards = ledgerBlocks
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = if (cards) 0.dp else MetroDimens.Grid)
                    .bleedGutter(cards),
                state = list,
                contentPadding = cardListPadding(cards, top = MetroDimens.Grid),
            ) {
                if (shell.groups.any { it.categories.isNotEmpty() }) {
                    item(key = "ledger-headings") {
                        LedgerHeadings(if (cards) Modifier.padding(horizontal = MetroDimens.Gutter) else Modifier)
                    }
                }
                // Bands alternate down the whole list, carrying on across group headings.
                val position =
                    (
                        shell.groups.flatMap { g -> g.categories.map { it.id } } +
                            shell.incomeGroups.flatMap { g -> g.categories.map { it.id } }
                    ).withIndex()
                        .associate { it.value to it.index }
                if (cards) {
                    // Each group is one block (panel or channel), so its heading and rows travel together.
                    shell.groups.forEach { group ->
                        item(key = group.id) {
                            LedgerGroup(arrivalModifier(group.id, arrivals), { GroupLine(group, shell, formatOptions = formatOptions) }) {
                                group.categories.forEach { CategoryLine(it, shell, onCategory, formatOptions = formatOptions) }
                            }
                            GroupGap()
                        }
                    }
                    shell.incomeGroups.forEach { group ->
                        item(key = "income-${group.id}") {
                            LedgerGroup(heading = { IncomeGroupLine(group, shell, formatOptions = formatOptions) }) {
                                group.categories.forEach { IncomeCategoryLine(it, shell, onCategory, formatOptions = formatOptions) }
                            }
                            GroupGap()
                        }
                    }
                    return@LazyColumn
                }
                shell.groups.forEach { group ->
                    item(key = group.id) {
                        GroupLine(
                            group,
                            shell,
                            arrivalModifier(group.id, arrivals),
                            formatOptions = formatOptions,
                        )
                    }
                    items(group.categories, key = { "category-${it.id}" }) { category ->
                        CategoryLine(
                            category,
                            shell,
                            onCategory,
                            formatOptions = formatOptions,
                            banded = ledgerBanded(position.getValue(category.id)),
                        )
                    }
                    item(key = "gap-${group.id}") { GroupGap() }
                }
                shell.incomeGroups.forEach { group ->
                    item(key = "income-${group.id}") {
                        IncomeGroupLine(
                            group,
                            shell,
                            formatOptions = formatOptions,
                        )
                    }
                    items(group.categories, key = { "income-category-${it.id}" }) { category ->
                        IncomeCategoryLine(
                            category,
                            shell,
                            onCategory,
                            formatOptions = formatOptions,
                            banded = ledgerBanded(position.getValue(category.id)),
                        )
                    }
                    item(key = "income-gap-${group.id}") { GroupGap() }
                }
            }
        }
    }
}

/** Air after a group's last category, so each group reads as its own block. */
@Composable
internal fun GroupGap() {
    Spacer(Modifier.height(MetroDimens.Grid))
}

/**
 * A caption over a figure. With [error] the figure is in the red, and it shows in more than color: the label
 * reads "overbudgeted" and the figure sits on a solid red block.
 */
@Composable
internal fun BudgetTotal(
    label: String,
    amount: String,
    amountColor: Color,
    amountTag: String,
    alignEnd: Boolean = false,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    /** Ink for the caption, on a tile; null keeps the page's secondary (or error) color. */
    labelColor: Color? = null,
) {
    val align = if (alignEnd) TextAlign.End else TextAlign.Start
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        MetroText(
            if (error) ShellCopy.OVERBUDGETED else label,
            Metro.typography.caption.copy(textAlign = align),
            color = labelColor?.copy(alpha = 0.85f) ?: if (error) Metro.colors.error else Metro.colors.secondary,
            modifier = if (error) Modifier.testTag("$amountTag-error") else Modifier,
        )
        if (error) {
            MetroText(
                amount,
                Metro.typography.subheader.copy(textAlign = align),
                color = Metro.colors.onErrorFill,
                modifier =
                    Modifier
                        .background(Metro.colors.errorFill)
                        .padding(horizontal = ERROR_BLOCK_PADDING)
                        .testTag(amountTag),
            )
        } else {
            MetroText(
                amount,
                Metro.typography.subheader.copy(textAlign = align),
                color = amountColor,
                modifier = Modifier.testTag(amountTag),
            )
        }
    }
}

private val ERROR_BLOCK_PADDING = 8.dp

@Composable
private fun GroupLine(
    group: GroupRow,
    shell: MonthShell,
    modifier: Modifier = Modifier,
    formatOptions: FormatOptions = formatOptions(),
) {
    GroupHeading(
        name = group.name,
        tag = "group",
        id = group.id,
        modifier = modifier,
        dim = group.hidden,
    ) {
        MetroText(
            MoneyFormat.format(group.availableMinor, shell.currency, formatOptions),
            Metro.typography.groupHeader,
            it,
        )
    }
}

/** A group's name in the group-header type, with its total (or a control) at the end. */
@Composable
internal fun GroupHeading(
    name: String,
    tag: String,
    id: String,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    trailing: @Composable (Modifier) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .padding(top = MetroDimens.Row)
            .testTag("$tag-$id"),
        verticalAlignment = Alignment.Top,
    ) {
        MetroText(
            name,
            Metro.typography.groupHeader,
            Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .testTag("$tag-name-$id"),
            color = if (dim) Metro.colors.secondary else Metro.colors.foreground,
            maxLines = 3,
        )
        trailing(Modifier.widthIn(max = 148.dp).testTag("$tag-amount-$id"))
    }
}

/**
 * Accounts on the same ledger as the budget: a total up top, then on-budget and off-budget
 * groups with their totals, and closed accounts folded away at the bottom, as Actual does.
 * In [reorder] mode each open account carries a handle on its left: drag it within its group, and the drop
 * hands [onMove] the account and the one it now sits before (null when it lands last).
 */
@Composable
fun AccountsSection(
    accounts: List<AccountRow>,
    shell: MonthShell,
    onListGesture: (Boolean) -> Unit,
    onAccount: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    formatOptions: FormatOptions = formatOptions(),
    reorder: Boolean = false,
    onMove: (String, String?) -> Unit = { _, _ -> },
) {
    if (accounts.isEmpty()) {
        MetroText(
            ShellCopy.NO_ACCOUNTS,
            Metro.typography.body,
            modifier.padding(MetroDimens.Gutter),
        )
        return
    }
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onListGesture(it) }
    }
    val arrivals = remember { RowArrivals() }
    arrivals.prime(accounts.map { it.id })
    val open = accounts.filter { !it.closed }
    val groups =
        listOf(
            Triple("on-budget", ShellCopy.ON_BUDGET, open.filter { !it.offBudget }),
            Triple("off-budget", ShellCopy.OFF_BUDGET, open.filter { it.offBudget }),
        ).filter { it.third.isNotEmpty() }
    val closed = accounts.filter { it.closed }
    // Bands alternate down the whole list, carrying on across group headings.
    val position = (groups.flatMap { it.third } + closed).withIndex().associate { it.value.id to it.index }
    var showClosed by rememberSaveable { mutableStateOf(false) }
    val money = { minor: Long -> MoneyFormat.format(minor, shell.currency, formatOptions) }
    val drag = remember { AccountDrag() }
    if (!reorder) drag.drop()
    // A row while reorder is on: lifted and shifted by the drag, opened by nothing.
    val line: @Composable (AccountRow, List<AccountRow>, Boolean) -> Unit = { account, rows, banded ->
        AccountLine(
            account,
            money(account.balanceMinor),
            if (reorder) ({}) else onAccount,
            arrivalModifier(account.id, arrivals).then(if (reorder) drag.follow(account.id, rows) else Modifier),
            banded = banded,
            lifted = drag.dragging == account.id,
            handle = if (reorder) drag.handle(account.id, rows, onMove) else null,
        )
    }
    Column(modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) {
        BudgetTotal(
            label = ShellCopy.ALL_ACCOUNTS,
            amount = money(open.sumOf { it.balanceMinor }),
            amountColor = Metro.accent.text,
            amountTag = "accounts-total",
        )
        if (reorder) {
            MetroText(
                AccountScreenCopy.REORDER_HINT,
                Metro.typography.caption,
                Modifier.padding(top = 4.dp).testTag("accounts-reorder-hint"),
                color = Metro.colors.secondary,
            )
        }
        val cards = ledgerBlocks
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = if (cards) 0.dp else MetroDimens.Grid)
                .bleedGutter(cards),
            state = list,
            contentPadding = cardListPadding(cards, top = MetroDimens.Grid),
        ) {
            if (cards) {
                groups.forEach { (id, name, rows) ->
                    item(key = "account-group-$id") {
                        LedgerGroup(heading = {
                            GroupHeading(name, "account-group", id) {
                                MetroText(money(rows.sumOf { a -> a.balanceMinor }), Metro.typography.groupHeader, it)
                            }
                        }) {
                            rows.forEach { line(it, rows, false) }
                        }
                        GroupGap()
                    }
                }
            }
            if (!cards) {
                groups.forEach { (id, name, rows) ->
                    item(key = "account-group-$id") {
                        GroupHeading(name, "account-group", id) {
                            MetroText(money(rows.sumOf { a -> a.balanceMinor }), Metro.typography.groupHeader, it)
                        }
                    }
                    items(rows, key = { it.id }) { line(it, rows, ledgerBanded(position.getValue(it.id))) }
                    item(key = "account-gap-$id") { GroupGap() }
                }
            }
            if (closed.isNotEmpty()) {
                item(key = "account-group-closed") {
                    GroupHeading(
                        ShellCopy.CLOSED_ACCOUNTS,
                        "account-group",
                        "closed",
                        Modifier.metroPress { showClosed = !showClosed },
                    ) {
                        Box(it.graphicsLayer { rotationZ = if (showClosed) 180f else 0f }) {
                            AppGlyphIcon(AppGlyph.ChevronDown)
                        }
                    }
                }
                if (showClosed) {
                    items(closed, key = { it.id }) {
                        AccountLine(
                            it,
                            money(it.balanceMinor),
                            if (reorder) ({}) else onAccount,
                            arrivalModifier(it.id, arrivals),
                            dim = true,
                            banded = ledgerBanded(position.getValue(it.id)),
                        )
                    }
                }
            }
        }
    }
}

/** The account a reorder drag holds, how far it has travelled, and each row's height, which turns distance into places. */
private class AccountDrag {
    var dragging by mutableStateOf<String?>(null)
    var offset by mutableFloatStateOf(0f)
    private val heights = mutableStateMapOf<String, Int>()

    /** The group the held row was picked up in, as it was then: a repaint mid-drag must not cancel the gesture. */
    private var held: List<AccountRow> = emptyList()

    /** Each row's group as last drawn, read when a drag starts; the gesture itself is keyed by the row alone. */
    private val groups = HashMap<String, List<AccountRow>>()

    fun drop() {
        dragging = null
        offset = 0f
        held = emptyList()
    }

    /**
     * Where the held account would land among [rows] if let go now: past every row whose middle its own middle
     * has crossed. Rows differ in height (a long name wraps), so each is measured rather than assumed.
     */
    fun target(rows: List<AccountRow>): Int? {
        val from = rows.indexOfFirst { it.id == dragging }.takeIf { it >= 0 } ?: return null
        val tops = rows.runningFold(0f) { top, row -> top + height(row.id) }
        val middle = tops[from] + offset + height(rows[from].id) / 2f
        return rows.indices.count { it != from && tops[it] + height(rows[it].id) / 2f < middle }
    }

    private fun height(id: String): Float = (heights[id] ?: heights.values.firstOrNull() ?: 1).toFloat()

    /** The held row follows the finger; the rows it passes slide out of its way by its height. */
    fun follow(
        id: String,
        rows: List<AccountRow>,
    ): Modifier {
        val from = rows.indexOfFirst { it.id == dragging }
        val index = rows.indexOfFirst { it.id == id }
        val to = target(rows)
        val lifted = dragging == id
        val gap = if (from < 0) 0f else height(rows[from].id)
        val shift =
            when {
                from < 0 || to == null || lifted -> 0f
                index in (from + 1)..to -> -gap
                index in to until from -> gap
                else -> 0f
            }
        return Modifier
            .onSizeChanged { heights[id] = it.height }
            .zIndex(if (lifted) 1f else 0f)
            .graphicsLayer { translationY = if (lifted) offset else shift }
    }

    /** The grip's own gesture: a drag straight away, no hold first, so the list keeps its scroll elsewhere. */
    fun handle(
        id: String,
        rows: List<AccountRow>,
        onMove: (String, String?) -> Unit,
    ): Modifier {
        groups[id] = rows
        return Modifier.pointerInput(id) {
            detectDragGestures(
                onDragStart = {
                    held = groups[id].orEmpty()
                    dragging = id
                    offset = 0f
                },
                onDrag = { change, amount ->
                    change.consume()
                    offset += amount.y
                },
                onDragEnd = {
                    val group = held
                    val from = group.indexOfFirst { it.id == id }
                    val to = target(group) ?: from
                    drop()
                    if (from >= 0 && to != from) onMove(id, if (to < from) group[to].id else group.getOrNull(to + 1)?.id)
                },
                onDragCancel = { drop() },
            )
        }
    }
}

/** One account under its group, in the category rows' body type, on a band when [banded]; [handle] adds a grip. */
@Composable
private fun AccountLine(
    account: AccountRow,
    balance: String,
    onAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    banded: Boolean = false,
    lifted: Boolean = false,
    handle: Modifier? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .ledgerFill(
                when {
                    lifted -> Metro.colors.chrome
                    banded -> ledgerBand
                    else -> Color.Transparent
                },
            ).metroPress { onAccount(account.id) }
            .testTag("account-${account.id}"),
    ) {
        Row(
            Modifier.fillMaxWidth().align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (handle != null) {
                Box(
                    handle
                        .heightIn(min = MetroDimens.TouchTarget)
                        .padding(end = 12.dp)
                        .testTag("account-handle-${account.id}"),
                    contentAlignment = Alignment.Center,
                ) { Grip() }
            }
            MetroText(
                account.name,
                Metro.typography.body,
                Modifier.weight(LEDGER_NAME + LEDGER_FIGURE).padding(end = MetroDimens.Gutter),
                color = if (dim) Metro.colors.secondary else Metro.colors.foreground,
                maxLines = 2,
            )
            MetroText(
                balance,
                Metro.typography.body.copy(textAlign = TextAlign.End),
                Modifier.weight(LEDGER_FIGURE * 2).testTag("account-balance-${account.id}"),
                color = if (dim) Metro.colors.secondary else Metro.accent.text,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
fun Placeholder() {
    Column(
        Modifier.padding(MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.fillMaxWidth(0.4f).height(14.dp).background(Metro.colors.secondary))
        Box(Modifier.fillMaxWidth(0.72f).height(40.dp).background(Metro.colors.secondary))
        repeat(4) {
            Box(Modifier.fillMaxWidth().height(18.dp).background(Metro.colors.chrome))
        }
    }
}

@Composable
fun CreateBudgetScreen(
    error: String?,
    onCreate: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    defaultName: String = "",
    defaultCurrency: String = "USD",
    /** Onboarding keeps the draft so a restart resumes it (spec 011). */
    onDraft: ((String, String) -> Unit)? = null,
) {
    var name by remember { mutableStateOf(defaultName) }
    var currency by remember { mutableStateOf(defaultCurrency) }
    val currencyIndex = Currencies.all.indexOfFirst { it.code == currency }.coerceAtLeast(0)
    val currencyList = rememberLazyListState(initialFirstVisibleItemIndex = currencyIndex)
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText("new budget", Metro.typography.header)
        MetroField(
            name,
            {
                name = it
                onDraft?.invoke(it, currency)
            },
            "name",
        )
        if (error != null) {
            MetroText(error, Metro.typography.body, color = Metro.accent.text)
        }
        LazyColumn(Modifier.weight(1f), state = currencyList) {
            items(Currencies.all, key = { it.code }) { item ->
                val selected = item.code == currency
                MetroText(
                    "${item.code}  ${item.symbol.trim()}",
                    Metro.typography.subheader,
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MetroDimens.TouchTarget)
                        .metroPress {
                            currency = item.code
                            onDraft?.invoke(name, item.code)
                        }.padding(vertical = MetroDimens.Row),
                    color = if (selected) Metro.accent.text else Metro.colors.foreground,
                )
            }
        }
        RememberFormSave { onCreate(name, currency) }
        if (!formSaveOnBar()) {
            MetroButton("create budget") { onCreate(name, currency) }
        }
    }
}

@Composable
fun BudgetsScreen(
    budgets: List<BudgetSummary>,
    openId: String?,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onSamples: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
    ) {
        MetroText("budgets", Metro.typography.header)
        LazyColumn(Modifier.weight(1f).padding(top = MetroDimens.Grid)) {
            items(budgets, key = { it.id }) { budget ->
                val open = budget.id == openId
                MetroText(
                    if (open) "${budget.name}  open" else budget.name,
                    Metro.typography.subheader,
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MetroDimens.TouchTarget)
                        .metroPress { onOpen(budget.id) }
                        .padding(vertical = MetroDimens.Row),
                    color = if (open) Metro.accent.text else Metro.colors.foreground,
                )
            }
        }
        MetroButtonGrid(Modifier.padding(top = 12.dp)) {
            MetroButton("new budget", onClick = onCreate)
            if (onSamples != null) {
                MetroButton(ShellCopy.SAMPLE_BUDGETS, Modifier.testTag("sample-budgets"), onClick = onSamples)
            }
        }
    }
}

@Composable
fun SampleBudgetsScreen(
    samples: List<MockBudgetDataset>,
    error: String?,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("sample-budgets-screen"),
        verticalArrangement = Arrangement.spacedBy(MetroDimens.Gutter),
    ) {
        MetroText(ShellCopy.SAMPLE_BUDGETS, Metro.typography.header)
        MetroText(
            "debug builds only · loads a filled Actual file on this phone",
            Metro.typography.caption,
            color = Metro.colors.secondary,
        )
        if (error != null) {
            MetroText(error, Metro.typography.body, color = Metro.accent.text)
        }
        LazyColumn(Modifier.weight(1f).padding(top = MetroDimens.Gutter)) {
            items(samples, key = { it.id }) { sample ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MetroDimens.TouchTarget)
                        .metroPress { onChoose(sample.id) }
                        .padding(vertical = MetroDimens.Row)
                        .testTag("sample-${sample.id}"),
                ) {
                    MetroText(sample.title, Metro.typography.subheader)
                    MetroText(
                        sample.summary,
                        Metro.typography.caption,
                        color = Metro.colors.secondary,
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmSwitchScreen(
    opening: String,
    leaving: String,
    onOpen: () -> Unit,
    onStay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MetroText("open $opening", Metro.typography.header)
        MetroText("leave $leaving on the phone", Metro.typography.subheader)
        MetroButtonGrid {
            MetroButton("open", onClick = onOpen)
            MetroButton("stay", onClick = onStay)
        }
    }
}

@Composable
fun ShellChrome(
    actions: List<ScreenAction>,
    syncing: Boolean = false,
    progress: Float? = null,
    statusBar: WindowInsets = WindowInsets.statusBars,
    /** False while a page puts its own panel where the bar goes, as the budget's keypad does. */
    bar: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // A new screen starts with its bar closed, as each screen in due north tasks has its own bar.
    LaunchedEffect(actions.map { it.id }) { expanded = false }
    // Composed after the app's back handler, so back closes an open bar before it leaves the screen.
    BackHandler(enabled = expanded) { expanded = false }
    MetroAppBarHost(Modifier.fillMaxSize()) {
        // The background runs up behind the status bar; titles start below it.
        Column(
            Modifier
                .fillMaxSize()
                .background(Metro.colors.background)
                .windowInsetsPadding(statusBar),
        ) {
            if (syncing) SyncProgressBar(progress)
            content(Modifier.weight(1f))
            if (bar) MetroAppBar(actions, expanded, { expanded = it })
        }
    }
}

private class RowArrivals {
    private var primed = false
    private val seen = HashSet<String>()

    fun prime(ids: List<String>) {
        if (primed) return
        seen.addAll(ids)
        primed = true
    }

    fun isNew(id: String): Boolean = primed && id !in seen

    fun saw(id: String) {
        seen.add(id)
    }
}

@Composable
private fun arrivalModifier(
    id: String,
    arrivals: RowArrivals,
): Modifier {
    val animations = Metro.animations
    val alpha = remember(id) { Animatable(if (arrivals.isNew(id) && animations) 0f else 1f) }
    LaunchedEffect(id) {
        if (alpha.value < 1f && animations) {
            alpha.animateTo(1f, tween(MetroMotion.QUICK_MS, easing = MetroMotion.EaseOut))
        } else {
            alpha.snapTo(1f)
        }
        arrivals.saw(id)
    }
    return Modifier.graphicsLayer { this.alpha = alpha.value }
}

/** The logo over "nothing to budget", sized like the Due North Tasks "all clear." logo. */
private val EMPTY_LOGO_HEIGHT = 140.dp
