package app.duenorth.budget

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountRow
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MockBudgetDataset
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MonthReviewPage
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.NetWorthPage
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.core.ThemeMode
import app.duenorth.budget.core.UpcomingScheduleRow
import app.duenorth.budget.core.monthLabel
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.MetroAppBarHost
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroOutlineButton
import app.duenorth.budget.design.components.MetroPivot
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.PivotItem
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Accent
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import java.time.YearMonth

@Composable
fun HomePivot(
    shell: MonthShell?,
    review: MonthReviewPage?,
    netWorth: NetWorthPage?,
    upcoming: List<UpcomingScheduleRow>,
    loading: Boolean,
    openMonth: YearMonth,
    monthWindow: MonthWindow,
    initialSection: Int = 0,
    onGesture: (Boolean) -> Unit,
    onAccount: (String) -> Unit = {},
    onInbox: (String) -> Unit = {},
    onReviewCategory: (String) -> Unit = {},
    onNetWorthIncludeOffBudget: (Boolean) -> Unit = {},
    onPostSchedule: (String) -> Unit = {},
    onSkipSchedule: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    onOpenSchedule: (String) -> Unit = {},
    onSelectMonth: (YearMonth) -> Unit = {},
    onBudgetCategory: ((String) -> Unit)? = null,
    onAddTransaction: (() -> Unit)? = null,
    formatOptions: FormatOptions = FormatOptions(),
    onToBudget: (() -> Unit)? = null,
    notice: String? = null,
) {
    var pagerGesture by remember { mutableStateOf(false) }
    var listGesture by remember { mutableStateOf(false) }
    LaunchedEffect(pagerGesture, listGesture) { onGesture(pagerGesture || listGesture) }
    MetroPivot(
        title = "DUE NORTH BUDGET",
        items =
            listOf(
                PivotItem("budget") {
                    if (shell == null) {
                        if (loading) Placeholder()
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            BudgetMonthTitle(
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
                            )
                        }
                    }
                },
                PivotItem("accounts") {
                    if (shell == null) {
                        if (loading) Placeholder()
                    } else {
                        AccountsSection(
                            shell.accounts,
                            shell,
                            onListGesture = { listGesture = it },
                            onAccount = onAccount,
                            formatOptions = formatOptions,
                        )
                    }
                },
                PivotItem("due") {
                    if (shell == null) {
                        if (loading) Placeholder()
                    } else {
                        DueSection(
                            rows = upcoming,
                            currency = shell.currency,
                            onListGesture = { listGesture = it },
                            onPost = onPostSchedule,
                            onSkip = onSkipSchedule,
                            formatOptions = formatOptions,
                            onOpen = onOpenSchedule,
                        )
                    }
                },
                PivotItem("review") {
                    MonthReviewSection(
                        review = review,
                        loading = loading,
                        onListGesture = { listGesture = it },
                        onCategory = onReviewCategory,
                    )
                },
                PivotItem("net worth") {
                    NetWorthSection(
                        page = netWorth,
                        loading = loading,
                        onIncludeOffBudget = onNetWorthIncludeOffBudget,
                    )
                },
                PivotItem("inbox") {
                    if (shell == null) {
                        if (loading) Placeholder()
                    } else {
                        InboxSection(
                            shell,
                            onListGesture = { listGesture = it },
                            onInbox = onInbox,
                            onAdd = onAddTransaction,
                            formatOptions = formatOptions,
                        )
                    }
                },
            ),
        modifier = modifier,
        initialItem = initialSection,
        onGesture = { pagerGesture = it },
    )
}

@Composable
fun MonthControl(
    month: YearMonth,
    window: MonthWindow,
    onSelect: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val previous = window.previous(month)
    val next = window.next(month)
    val calendar = YearMonth.now()
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (previous != null) {
                MonthIconButton(AppGlyph.ChevronLeft, "month-previous") { onSelect(previous) }
            }
            MetroText(
                monthLabel(month),
                Metro.typography.subheader,
                Modifier
                    .weight(1f)
                    .metroPress { expanded = !expanded }
                    .testTag("month-label"),
            )
            if (month != calendar) {
                MonthIconButton(AppGlyph.Calendar, "month-current") { onSelect(calendar) }
            }
            if (next != null) {
                MonthIconButton(AppGlyph.ChevronRight, "month-next") { onSelect(next) }
            }
        }
        if (expanded) {
            MonthOptions(month, window) {
                onSelect(it)
                expanded = false
            }
        }
    }
}

/** View title for the budget section: "october 2026 budget", opened as the month list. */
@Composable
private fun BudgetMonthTitle(
    month: YearMonth,
    window: MonthWindow,
    onSelect: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val phrase = monthLabel(month)
    val style = Metro.typography.subheader
    Column(modifier.fillMaxWidth()) {
        SubcomposeLayout(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MetroDimens.TouchTarget)
                .semantics { heading() }
                .metroPress { expanded = !expanded }
                .testTag("month-label"),
        ) { constraints ->
            // Widths near Constraints.Infinity make this text measure about one pixel per character.
            val loose = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = 2_048)
            val iconPx = 22.dp.roundToPx()
            val gap = MetroDimens.Gutter.roundToPx()
            val safety = 1.dp.roundToPx()
            val maxW = constraints.maxWidth
            val textAvail = (maxW - gap - iconPx - safety).coerceAtLeast(0)

            fun textWidth(
                id: String,
                text: String,
                textStyle: TextStyle,
            ): Int =
                subcompose(id) {
                    MetroText(text, textStyle, Modifier.clearAndSetSemantics {}, maxLines = 1, softWrap = false)
                }.first().measure(loose).width

            val natural = textWidth("title-natural", phrase, style)
            var scale = if (natural <= textAvail || natural == 0) 1f else textAvail.toFloat() / natural
            val sized = textWidth("title-sized", phrase, style.at(scale))
            if (sized > textAvail && sized > 0) {
                scale *= (textAvail - safety).coerceAtLeast(0).toFloat() / sized
            }
            val title =
                subcompose("title") {
                    MetroText(phrase, style.at(scale), maxLines = 1, softWrap = false)
                }.first().measure(loose)
            val chevron =
                subcompose("chevron") {
                    Box(
                        Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f }.testTag("month-dropdown"),
                    ) {
                        AppGlyphIcon(AppGlyph.ChevronDown)
                    }
                }.first().measure(loose)
            val height = maxOf(constraints.minHeight, title.height, chevron.height)
            val width = if (maxW == Constraints.Infinity) title.width + gap + chevron.width + safety else maxW
            layout(width, height) {
                title.placeRelative(0, ((height - title.height) / 2).coerceAtLeast(0))
                val iconX = (title.width + gap).coerceAtMost((width - chevron.width).coerceAtLeast(0))
                chevron.placeRelative(iconX, ((height - chevron.height) / 2).coerceAtLeast(0))
            }
        }
        if (expanded) {
            MonthOptions(month, window) {
                onSelect(it)
                expanded = false
            }
        }
    }
}

@Composable
private fun MonthOptions(
    month: YearMonth,
    window: MonthWindow,
    onSelect: (YearMonth) -> Unit,
) {
    LazyColumn(
        Modifier
            .fillMaxWidth()
            .padding(top = MetroDimens.Gutter)
            .heightIn(max = 288.dp)
            .testTag("month-list"),
    ) {
        items(window.months, key = { it.toString() }) { option ->
            MetroText(
                monthLabel(option),
                Metro.typography.body,
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MetroDimens.TouchTarget)
                    .metroPress { onSelect(option) }
                    .padding(vertical = MetroDimens.Row)
                    .testTag("month-option-$option"),
                color = if (option == month) Metro.accent.text else Metro.colors.foreground,
            )
        }
    }
}

private fun TextStyle.at(scale: Float): TextStyle {
    if (scale >= 0.999f) return this
    var next = copy(fontSize = fontSize * scale)
    if (lineHeight != TextUnit.Unspecified) next = next.copy(lineHeight = lineHeight * scale)
    if (letterSpacing != TextUnit.Unspecified) next = next.copy(letterSpacing = letterSpacing * scale)
    return next
}

@Composable
private fun MonthIconButton(
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
    formatOptions: FormatOptions = FormatOptions(),
    onToBudget: (() -> Unit)? = null,
    notice: String? = null,
) {
    val list = rememberLazyListState()
    val arrivals = remember { RowArrivals() }
    arrivals.prime(shell.groups.map { it.id })
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onListGesture(it) }
    }
    Column(modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            BudgetTotal(
                label = shell.headerLabel,
                amount = MoneyFormat.format(shell.headerMinor, shell.currency, formatOptions),
                amountColor = Metro.accent.text,
                amountTag = "to-budget",
                modifier =
                    if (onToBudget == null) {
                        Modifier
                    } else {
                        Modifier.metroPress(onClick = onToBudget).testTag("to-budget-figure")
                    },
            )
            if (shell.bufferedMinor != 0L) {
                BudgetTotal(
                    label = "held",
                    amount = MoneyFormat.format(shell.bufferedMinor, shell.currency, formatOptions),
                    amountColor = Metro.colors.secondary,
                    amountTag = "held-amount",
                    alignEnd = true,
                )
            }
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
            LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = MetroDimens.Grid), state = list) {
                if (shell.groups.any { it.categories.isNotEmpty() }) {
                    item(key = "ledger-headings") { LedgerHeadings() }
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
                        CategoryLine(category, shell, onCategory, formatOptions = formatOptions)
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
                        IncomeCategoryLine(category, shell, onCategory, formatOptions = formatOptions)
                    }
                    item(key = "income-gap-${group.id}") { GroupGap() }
                }
            }
        }
    }
}

/** Air after a group's last category, so each group reads as its own block. */
@Composable
private fun GroupGap() {
    Spacer(Modifier.height(MetroDimens.Grid))
}

@Composable
private fun BudgetTotal(
    label: String,
    amount: String,
    amountColor: Color,
    amountTag: String,
    alignEnd: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val align = if (alignEnd) TextAlign.End else TextAlign.Start
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        MetroText(label, Metro.typography.caption.copy(textAlign = align), color = Metro.colors.secondary)
        MetroText(
            amount,
            Metro.typography.subheader.copy(textAlign = align),
            color = amountColor,
            modifier = Modifier.testTag(amountTag),
        )
    }
}

@Composable
private fun GroupLine(
    group: GroupRow,
    shell: MonthShell,
    modifier: Modifier = Modifier,
    formatOptions: FormatOptions = FormatOptions(),
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .padding(top = MetroDimens.Row)
            .testTag("group-${group.id}"),
        verticalAlignment = Alignment.Top,
    ) {
        MetroText(
            group.name,
            Metro.typography.groupHeader,
            Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .testTag("group-name-${group.id}"),
            maxLines = 3,
        )
        MetroText(
            MoneyFormat.format(group.availableMinor, shell.currency, formatOptions),
            Metro.typography.groupHeader,
            Modifier
                .widthIn(max = 148.dp)
                .testTag("group-amount-${group.id}"),
        )
    }
}

@Composable
fun AccountsSection(
    accounts: List<AccountRow>,
    shell: MonthShell,
    onListGesture: (Boolean) -> Unit,
    onAccount: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    formatOptions: FormatOptions = FormatOptions(),
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
    val onBudget = accounts.filter { !it.offBudget }
    val offBudget = accounts.filter { it.offBudget }
    LazyColumn(modifier.fillMaxSize(), state = list) {
        if (onBudget.isNotEmpty()) {
            item { SectionLabel(ShellCopy.ON_BUDGET, first = true) }
            items(onBudget, key = { it.id }) {
                AccountLine(it, shell, onAccount, arrivalModifier(it.id, arrivals), formatOptions)
            }
        }
        if (offBudget.isNotEmpty()) {
            item { SectionLabel(ShellCopy.OFF_BUDGET, first = onBudget.isEmpty()) }
            items(offBudget, key = { it.id }) {
                AccountLine(it, shell, onAccount, arrivalModifier(it.id, arrivals), formatOptions)
            }
        }
    }
}

@Composable
private fun AccountLine(
    account: AccountRow,
    shell: MonthShell,
    onAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
    formatOptions: FormatOptions = FormatOptions(),
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress { onAccount(account.id) }
            .padding(horizontal = MetroDimens.Gutter, vertical = MetroDimens.Row)
            .testTag("account-${account.id}"),
        verticalAlignment = Alignment.Top,
    ) {
        MetroText(
            account.name,
            Metro.typography.subheader,
            Modifier.weight(1f).padding(end = 12.dp),
            maxLines = 3,
        )
        MetroText(
            MoneyFormat.format(account.balanceMinor, shell.currency, formatOptions),
            Metro.typography.subheader,
            Modifier.widthIn(max = 148.dp),
        )
    }
}

@Composable
fun InboxSection(
    shell: MonthShell,
    onListGesture: (Boolean) -> Unit,
    onInbox: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    onAdd: (() -> Unit)? = null,
    formatOptions: FormatOptions = FormatOptions(),
) {
    if (shell.inbox.isEmpty()) {
        Column(modifier.padding(MetroDimens.Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroText(ShellCopy.NOTHING_TO_CATEGORIZE, Metro.typography.body)
            if (onAdd != null) MetroButton("add", onClick = onAdd)
        }
        return
    }
    val list = rememberLazyListState()
    val arrivals = remember { RowArrivals() }
    arrivals.prime(shell.inbox.map { it.id })
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onListGesture(it) }
    }
    Column(modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), state = list) {
            items(shell.inbox, key = { it.id }) { row ->
                InboxLine(row, shell, onInbox, arrivalModifier(row.id, arrivals), formatOptions)
            }
        }
        if (onAdd != null) {
            MetroButton("add", Modifier.padding(MetroDimens.Gutter), onClick = onAdd)
        }
    }
}

@Composable
private fun InboxLine(
    row: InboxRow,
    shell: MonthShell,
    onInbox: (String) -> Unit,
    modifier: Modifier = Modifier,
    formatOptions: FormatOptions = FormatOptions(),
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress { onInbox(row.id) }
            .padding(horizontal = MetroDimens.Gutter, vertical = MetroDimens.Row),
        verticalAlignment = Alignment.Top,
    ) {
        MetroText(row.payee, Metro.typography.subheader, Modifier.weight(1f).padding(end = 12.dp), maxLines = 3)
        MetroText(
            MoneyFormat.format(row.amountMinor, shell.currency, formatOptions),
            Metro.typography.subheader,
            Modifier.widthIn(max = 148.dp),
        )
    }
}

@Composable
private fun SectionLabel(
    text: String,
    first: Boolean = false,
) {
    MetroText(
        text,
        Metro.typography.groupHeader,
        Modifier.padding(
            start = MetroDimens.Gutter,
            top = if (first) 0.dp else MetroDimens.Grid,
            bottom = MetroDimens.Row,
        ),
        color = Metro.colors.secondary,
    )
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
        MetroOutlineButton("new budget", Modifier.padding(top = 12.dp), onClick = onCreate)
        if (onSamples != null) {
            MetroOutlineButton(
                ShellCopy.SAMPLE_BUDGETS,
                Modifier
                    .padding(top = MetroDimens.Gutter)
                    .testTag("sample-budgets"),
                onClick = onSamples,
            )
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
        MetroOutlineButton("open", onClick = onOpen)
        MetroOutlineButton("stay", onClick = onStay)
    }
}

@Composable
fun AppearanceScreen(
    themeMode: String,
    accentId: String,
    onTheme: (String) -> Unit,
    onAccent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
    ) {
        MetroText("appearance", Metro.typography.header)
        ThemeMode.entries.forEach { mode ->
            val selected = ThemeMode.fromStored(themeMode) == mode
            val label =
                when (mode) {
                    ThemeMode.SYSTEM -> "follow phone"
                    ThemeMode.LIGHT -> "light"
                    ThemeMode.DARK -> "dark"
                }
            MetroText(
                label,
                Metro.typography.subheader,
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MetroDimens.TouchTarget)
                    .metroPress { onTheme(mode.stored()) }
                    .padding(vertical = MetroDimens.Row),
                color = if (selected) Metro.accent.text else Metro.colors.foreground,
            )
        }
        MetroText(
            "accent",
            Metro.typography.caption,
            Modifier.padding(top = MetroDimens.Grid, bottom = MetroDimens.Gutter),
            color = Metro.colors.secondary,
        )
        AccentGrid(accentId, onAccent)
    }
}

@Composable
private fun AccentGrid(
    selected: String,
    onAccent: (String) -> Unit,
) {
    val accents = Accent.entries
    Column(verticalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)) {
        accents.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)) {
                row.forEach { accent ->
                    val fill = accent.fill(Metro.colors.isDark)
                    Box(
                        Modifier
                            .size(52.dp)
                            .background(fill)
                            .then(
                                if (accent.id == selected) {
                                    Modifier.border(3.dp, Metro.colors.foreground)
                                } else {
                                    Modifier
                                },
                            ).metroPress { onAccent(accent.id) },
                    )
                }
            }
        }
    }
}

@Composable
fun ShellChrome(
    actions: List<ScreenAction>,
    syncing: Boolean = false,
    progress: Float? = null,
    statusBar: WindowInsets = WindowInsets.statusBars,
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
            MetroAppBar(actions, expanded, { expanded = it })
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
            alpha.animateTo(1f, tween(160))
        } else {
            alpha.snapTo(1f)
        }
        arrivals.saw(id)
    }
    return Modifier.graphicsLayer { this.alpha = alpha.value }
}

/** The logo over "nothing to budget", sized like the Due North Tasks "all clear." logo. */
private val EMPTY_LOGO_HEIGHT = 140.dp
