package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.CategoryTarget
import app.duenorth.budget.core.EntryResult
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MoneyParse
import app.duenorth.budget.core.RegisterCopy
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RegisterRow
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.core.SplitPart
import app.duenorth.budget.core.TransactionDraft
import app.duenorth.budget.core.TransferDraft
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import java.util.UUID

@Composable
fun RegisterScreen(
    page: RegisterPage,
    filter: String,
    onFilter: (String) -> Unit,
    onGesture: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onTransfer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = page.matching(filter)
    val owed = page.account.type == "credit" && page.balanceMinor < 0
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(horizontal = MetroDimens.Gutter),
    ) {
        MetroText(page.account.name, Metro.typography.header, maxLines = 2)
        MetroText(ShellCopy.BALANCE, Metro.typography.caption, color = Metro.colors.secondary)
        MetroText(
            MoneyFormat.format(page.balanceMinor, page.currency),
            Metro.typography.header,
            color = Metro.accent.text,
            modifier = Modifier.testTag("register-balance"),
        )
        if (owed) {
            MetroText(RegisterCopy.OWED, Metro.typography.caption, color = Metro.colors.secondary)
        }
        MetroField(
            value = filter,
            onValueChange = onFilter,
            hint = "payee",
            modifier = Modifier.padding(top = 12.dp).testTag("payee-filter"),
        )
        MetroButton("add", Modifier.padding(top = 12.dp), onClick = onAdd)
        MetroButton("transfer", Modifier.padding(top = 8.dp), onClick = onTransfer)
        if (rows.isEmpty()) {
            MetroText(RegisterCopy.NO_TRANSACTIONS, Metro.typography.body, Modifier.padding(top = 16.dp))
        } else {
            RegisterList(rows, page, onGesture, onOpen, Modifier.weight(1f).padding(top = 8.dp))
        }
    }
}

@Composable
private fun RegisterList(
    rows: List<RegisterRow>,
    page: RegisterPage,
    onGesture: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A new visit starts at the newest row. Scroll position is not saved.
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onGesture(it) }
    }
    LazyColumn(modifier, state = list) {
        items(rows, key = { it.id }) { row ->
            RegisterLine(row, page, onOpen)
        }
    }
}

@Composable
private fun RegisterLine(
    row: RegisterRow,
    page: RegisterPage,
    onOpen: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .metroPress { onOpen(row.id) }
            .padding(vertical = 8.dp)
            .testTag("register-row-${row.id}"),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                MetroText(
                    RegisterEntry.registerDateLabel(row.date),
                    Metro.typography.caption,
                    color = Metro.colors.secondary,
                )
                MetroText(row.payee, Metro.typography.subheader, maxLines = 2)
                MetroText(row.categoryLabel, Metro.typography.body, color = Metro.colors.secondary, maxLines = 2)
            }
            MetroText(
                MoneyFormat.format(row.amountMinor, page.currency),
                Metro.typography.subheader,
                Modifier.widthIn(max = 148.dp),
            )
        }
        row.parts.forEach { part ->
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                MetroText(part.categoryName, Metro.typography.body, Modifier.weight(1f), maxLines = 2)
                MetroText(MoneyFormat.format(part.amountMinor, page.currency), Metro.typography.body)
            }
        }
    }
}

@Composable
fun TransactionForm(
    page: RegisterPage,
    row: RegisterRow?,
    initialDate: String,
    error: String?,
    onSave: (TransactionDraft) -> Unit,
    onDelete: (String) -> Unit,
    onSplit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draftId = remember(row?.id, page.account.id) { row?.id ?: UUID.randomUUID().toString() }
    var date by remember(row?.id) { mutableStateOf(row?.let { RegisterEntry.formatIso(it.date) } ?: initialDate) }
    var payee by remember(row?.id) {
        mutableStateOf(if (row == null || row.payee == ShellCopy.NO_PAYEE) "" else row.payee)
    }
    var amount by remember(row?.id) {
        mutableStateOf(row?.let { MoneyParse.formatMagnitude(it.amountMinor, page.currency.decimals) }.orEmpty())
    }
    var expense by remember(row?.id) { mutableStateOf(row?.amountMinor?.let { it <= 0L } ?: true) }
    var note by remember(row?.id) { mutableStateOf(row?.note.orEmpty()) }
    var categoryId by remember(row?.id) { mutableStateOf(row?.categoryId ?: row?.partnerCategoryId) }
    var localError by remember(row?.id) { mutableStateOf<String?>(null) }
    val mixed =
        row?.transferId != null &&
            row.partnerOffBudget != null &&
            row.partnerOffBudget != page.account.offBudget
    val showCategories = row?.parent != true && (row?.transferId == null || mixed)
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
    ) {
        MetroText(if (row == null) "transaction" else row.payee, Metro.typography.header, maxLines = 2)
        MetroField(date, { date = it }, "date", Modifier.padding(top = 12.dp))
        MetroField(payee, { payee = it }, "payee", Modifier.padding(top = 8.dp))
        MetroField(amount, { amount = it }, "amount", Modifier.padding(top = 8.dp).testTag("amount-field"))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DirectionChoice("expense", expense) { expense = true }
            DirectionChoice("income", !expense) { expense = false }
        }
        MetroField(note, { note = it }, "note", Modifier.padding(top = 8.dp))
        val shown = localError ?: error
        if (shown != null) {
            MetroText(shown, Metro.typography.body, Modifier.padding(top = 8.dp), color = Metro.accent.text)
        }
        if (showCategories) {
            MetroText(
                "category",
                Metro.typography.caption,
                Modifier.padding(top = 12.dp, bottom = 4.dp),
                color = Metro.colors.secondary,
            )
            LazyColumn(Modifier.weight(1f)) {
                item {
                    ChoiceLine(RegisterCopy.NO_CATEGORY, categoryId == null) { categoryId = null }
                }
                items(page.categories, key = { it.id }) { category ->
                    ChoiceLine(category.name, category.id == categoryId) { categoryId = category.id }
                }
            }
        } else {
            Column(Modifier.weight(1f)) {}
        }
        MetroButton("save", Modifier.padding(top = 8.dp)) {
            when (
                val parsed =
                    RegisterEntry.transaction(
                        id = draftId,
                        accountId = page.account.id,
                        dateText = date,
                        payee = payee,
                        amountText = amount,
                        decimals = page.currency.decimals,
                        expense = expense,
                        categoryId = if (showCategories) categoryId else null,
                        note = note,
                    )
            ) {
                is EntryResult.Ok -> {
                    localError = null
                    onSave(parsed.value)
                }
                is EntryResult.Rejected -> localError = parsed.reason
            }
        }
        if (row != null) {
            if (row.transferId == null) {
                MetroButton("split", Modifier.padding(top = 8.dp)) { onSplit(row.id) }
            }
            MetroButton("delete", Modifier.padding(top = 8.dp)) { onDelete(row.id) }
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
    val seed =
        if (row.parts.size >= 2) {
            row.parts.map { it.categoryId to MoneyParse.formatMagnitude(it.amountMinor, page.currency.decimals) }
        } else {
            listOf((row.categoryId ?: "") to MoneyParse.formatMagnitude(row.amountMinor, page.currency.decimals), "" to "")
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
            MoneyFormat.format(row.amountMinor, page.currency),
            Metro.typography.subheader,
            color = Metro.accent.text,
        )
        val shown = localError ?: error
        if (shown != null) {
            MetroText(shown, Metro.typography.body, color = Metro.accent.text)
        }
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
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
        MetroButton("add part", Modifier.padding(top = 8.dp)) {
            lines = lines + ("" to "")
        }
        MetroButton("save", Modifier.padding(top = 8.dp)) {
            when (val parsed = RegisterEntry.split(row.amountMinor, page.currency.decimals, lines)) {
                is EntryResult.Ok -> {
                    localError = null
                    onSave(row.id, parsed.value)
                }
                is EntryResult.Rejected -> localError = parsed.reason
            }
        }
        if (row.parent) {
            MetroButton("one category", Modifier.padding(top = 8.dp)) { onUnsplit(row.id) }
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
    Column(Modifier.padding(top = 12.dp)) {
        MetroText("part ${index + 1}", Metro.typography.caption, color = Metro.colors.secondary)
        MetroField(amount, onAmount, "amount", Modifier.padding(top = 4.dp))
        categories.forEach { category ->
            ChoiceLine(category.name, category.id == categoryId) { onCategory(category.id) }
        }
    }
}

@Composable
fun TransferForm(
    page: RegisterPage,
    initialDate: String,
    error: String?,
    onSave: (TransferDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draftId = remember(page.account.id) { UUID.randomUUID().toString() }
    val others = page.accounts.filter { it.id != page.account.id }
    var toId by remember(page.account.id) { mutableStateOf(others.firstOrNull()?.id.orEmpty()) }
    var date by remember { mutableStateOf(initialDate) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var categoryId by remember(toId) { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    val selected = others.firstOrNull { it.id == toId }
    val needsCategory = selected != null && selected.offBudget != page.account.offBudget
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
    ) {
        MetroText("transfer", Metro.typography.header)
        MetroField(date, { date = it }, "date", Modifier.padding(top = 12.dp))
        MetroField(amount, { amount = it }, "amount", Modifier.padding(top = 8.dp))
        MetroField(note, { note = it }, "note", Modifier.padding(top = 8.dp))
        val shown = localError ?: error
        if (shown != null) {
            MetroText(shown, Metro.typography.body, Modifier.padding(top = 8.dp), color = Metro.accent.text)
        }
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            items(others, key = { it.id }) { account ->
                ChoiceLine(account.name, account.id == toId) { toId = account.id }
            }
            if (needsCategory) {
                item {
                    MetroText(
                        "category",
                        Metro.typography.caption,
                        Modifier.padding(top = 12.dp),
                        color = Metro.colors.secondary,
                    )
                }
                items(page.categories, key = { "cat-${it.id}" }) { category ->
                    ChoiceLine(category.name, category.id == categoryId) { categoryId = category.id }
                }
            }
        }
        MetroButton("save", Modifier.padding(top = 8.dp)) {
            when (
                val parsed =
                    RegisterEntry.transfer(
                        id = draftId,
                        fromAccountId = page.account.id,
                        toAccountId = toId,
                        dateText = date,
                        amountText = amount,
                        decimals = page.currency.decimals,
                        categoryId = if (needsCategory) categoryId else null,
                        note = note,
                    )
            ) {
                is EntryResult.Ok -> {
                    localError = null
                    onSave(parsed.value)
                }
                is EntryResult.Rejected -> localError = parsed.reason
            }
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
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
    ) {
        MetroText(target.payee, Metro.typography.header, maxLines = 2)
        MetroText(
            MoneyFormat.format(target.amountMinor, target.currency),
            Metro.typography.subheader,
            color = Metro.accent.text,
        )
        if (error != null) {
            MetroText(error, Metro.typography.body, Modifier.padding(top = 8.dp), color = Metro.accent.text)
        }
        LazyColumn(Modifier.weight(1f).padding(top = 12.dp)) {
            item { ChoiceLine(RegisterCopy.NO_CATEGORY, false) { onPick(null) } }
            items(target.categories, key = { it.id }) { category ->
                ChoiceLine(category.name, false) { onPick(category.id) }
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
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MetroText(RegisterCopy.RECONCILE_WARN, Metro.typography.header)
        MetroButton("change", onClick = onChange)
        MetroButton("keep", onClick = onKeep)
    }
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
        Modifier.heightIn(min = MetroDimens.TouchTarget).metroPress(onClick).padding(vertical = 8.dp),
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
            .padding(vertical = 8.dp),
        color = if (selected) Metro.accent.text else Metro.colors.foreground,
        maxLines = 2,
    )
}
