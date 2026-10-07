package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountRow
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.core.ThemeMode
import app.duenorth.budget.core.monthLabel
import app.duenorth.budget.design.components.AppBarButton
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroPanorama
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.PanoramaSection
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Accent
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

@Composable
fun HomePanorama(
    shell: MonthShell?,
    loading: Boolean,
    initialSection: Int = 0,
    onGesture: (Boolean) -> Unit,
    onAccount: (String) -> Unit = {},
    onInbox: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var pagerGesture by remember { mutableStateOf(false) }
    var listGesture by remember { mutableStateOf(false) }
    LaunchedEffect(pagerGesture, listGesture) { onGesture(pagerGesture || listGesture) }
    MetroPanorama(
        title = "budget",
        subtitle = "due north",
        sections =
            listOf(
                PanoramaSection("budget") {
                    if (shell == null) {
                        if (loading) Placeholder()
                    } else {
                        BudgetSection(shell, onListGesture = { listGesture = it })
                    }
                },
                PanoramaSection("accounts") {
                    if (shell == null) {
                        if (loading) Placeholder()
                    } else {
                        AccountsSection(
                            shell.accounts,
                            shell,
                            onListGesture = { listGesture = it },
                            onAccount = onAccount,
                        )
                    }
                },
                PanoramaSection("inbox") {
                    if (shell == null) {
                        if (loading) Placeholder()
                    } else {
                        InboxSection(shell, onListGesture = { listGesture = it }, onInbox = onInbox)
                    }
                },
            ),
        modifier = modifier,
        initialSection = initialSection,
        onGesture = { pagerGesture = it },
    )
}

@Composable
fun BudgetSection(
    shell: MonthShell,
    onListGesture: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onListGesture(it) }
    }
    Column(modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) {
        MetroText(monthLabel(shell.month), Metro.typography.caption, color = Metro.colors.secondary)
        MetroText(shell.headerLabel, Metro.typography.caption, color = Metro.colors.secondary)
        MetroText(
            MoneyFormat.format(shell.headerMinor, shell.currency),
            Metro.typography.header,
            color = Metro.accent.text,
            modifier = Modifier.testTag("to-budget"),
        )
        if (shell.groups.isEmpty()) {
            MetroText(ShellCopy.NOTHING_TO_BUDGET, Metro.typography.body, Modifier.padding(top = 12.dp))
        } else {
            LazyColumn(Modifier.weight(1f), state = list) {
                items(shell.groups, key = { it.id }) { group ->
                    GroupLine(group, shell)
                }
            }
        }
    }
}

@Composable
private fun GroupLine(
    group: GroupRow,
    shell: MonthShell,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MetroText(
            group.name,
            Metro.typography.subheader,
            Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .testTag("group-name-${group.id}"),
            maxLines = 3,
        )
        MetroText(
            MoneyFormat.format(group.availableMinor, shell.currency),
            Metro.typography.subheader,
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
    val onBudget = accounts.filter { !it.offBudget }
    val offBudget = accounts.filter { it.offBudget }
    LazyColumn(modifier.fillMaxSize(), state = list) {
        if (onBudget.isNotEmpty()) {
            item { SectionLabel(ShellCopy.ON_BUDGET) }
            items(onBudget, key = { it.id }) { AccountLine(it, shell, onAccount) }
        }
        if (offBudget.isNotEmpty()) {
            item { SectionLabel(ShellCopy.OFF_BUDGET) }
            items(offBudget, key = { it.id }) { AccountLine(it, shell, onAccount) }
        }
    }
}

@Composable
private fun AccountLine(
    account: AccountRow,
    shell: MonthShell,
    onAccount: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress { onAccount(account.id) }
            .padding(horizontal = MetroDimens.Gutter, vertical = 8.dp)
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
            MoneyFormat.format(account.balanceMinor, shell.currency),
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
) {
    if (shell.inbox.isEmpty()) {
        MetroText(
            ShellCopy.NOTHING_TO_CATEGORIZE,
            Metro.typography.body,
            modifier.padding(MetroDimens.Gutter),
        )
        return
    }
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onListGesture(it) }
    }
    LazyColumn(modifier.fillMaxSize(), state = list) {
        items(shell.inbox, key = { it.id }) { row -> InboxLine(row, shell, onInbox) }
    }
}

@Composable
private fun InboxLine(
    row: InboxRow,
    shell: MonthShell,
    onInbox: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress { onInbox(row.id) }
            .padding(horizontal = MetroDimens.Gutter, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MetroText(row.payee, Metro.typography.subheader, Modifier.weight(1f).padding(end = 12.dp), maxLines = 3)
        MetroText(
            MoneyFormat.format(row.amountMinor, shell.currency),
            Metro.typography.subheader,
            Modifier.widthIn(max = 148.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    MetroText(
        text,
        Metro.typography.caption,
        Modifier.padding(start = MetroDimens.Gutter, top = 16.dp, bottom = 4.dp),
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
) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("USD") }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText("new budget", Metro.typography.header)
        MetroField(name, { name = it }, "name")
        if (error != null) {
            MetroText(error, Metro.typography.body, color = Metro.accent.text)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(Currencies.all, key = { it.code }) { item ->
                val selected = item.code == currency
                MetroText(
                    "${item.code}  ${item.symbol.trim()}",
                    Metro.typography.subheader,
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MetroDimens.TouchTarget)
                        .metroPress { currency = item.code }
                        .padding(vertical = 10.dp),
                    color = if (selected) Metro.accent.text else Metro.colors.foreground,
                )
            }
        }
        MetroButton("create budget") { onCreate(name, currency) }
    }
}

@Composable
fun BudgetsScreen(
    budgets: List<BudgetSummary>,
    openId: String?,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
    ) {
        MetroText("budgets", Metro.typography.header)
        LazyColumn(Modifier.weight(1f)) {
            items(budgets, key = { it.id }) { budget ->
                val open = budget.id == openId
                MetroText(
                    if (open) "${budget.name}  open" else budget.name,
                    Metro.typography.subheader,
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MetroDimens.TouchTarget)
                        .metroPress { onOpen(budget.id) }
                        .padding(vertical = 10.dp),
                    color = if (open) Metro.accent.text else Metro.colors.foreground,
                )
            }
        }
        MetroButton("new budget", onClick = onCreate)
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
        MetroButton("open", onClick = onOpen)
        MetroButton("stay", onClick = onStay)
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
                    .padding(vertical = 8.dp),
                color = if (selected) Metro.accent.text else Metro.colors.foreground,
            )
        }
        MetroText(
            "accent",
            Metro.typography.caption,
            Modifier.padding(top = 12.dp, bottom = 8.dp),
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        accents.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    buttons: List<AppBarButton>,
    content: @Composable (Modifier) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Metro.colors.background)) {
        content(Modifier.weight(1f))
        MetroAppBar(buttons, expanded, { expanded = it })
    }
}
