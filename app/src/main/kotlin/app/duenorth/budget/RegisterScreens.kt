package app.duenorth.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
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
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import java.util.UUID

@Composable
fun RegisterScreen(
    page: RegisterPage,
    onGesture: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onTransfer: () -> Unit,
    onReconcile: () -> Unit,
    onImportFile: (() -> Unit)? = null,
    selection: Set<String>? = null,
    onSelect: (String) -> Unit = {},
    onToggleSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val rows = page.rows
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
            amount = MoneyFormat.format(page.balanceMinor, page.currency),
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
        if (rows.isEmpty()) {
            MetroText(RegisterCopy.NO_TRANSACTIONS, Metro.typography.body, Modifier.padding(top = MetroDimens.Grid))
        } else {
            RegisterList(
                rows,
                page,
                onGesture,
                selection,
                onPress = { id -> if (selection != null) onToggleSelected(id) else onOpen(id) },
                onLongPress = { id -> if (selection == null) onSelect(id) else onToggleSelected(id) },
                modifier = Modifier.weight(1f).padding(top = MetroDimens.Grid),
            )
        }
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
) {
    // A new visit starts at the newest row. Scroll position is not saved.
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onGesture(it) }
    }
    val days = registerDays(rows)
    val position = rows.withIndex().associate { it.value.id to it.index }
    LazyColumn(modifier, state = list) {
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
private fun RegisterLine(
    row: RegisterRow,
    page: RegisterPage,
    banded: Boolean,
    picked: Boolean?,
    onPress: () -> Unit,
    onLongPress: () -> Unit,
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
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .ledgerFill(fill)
            .drawBehind {
                // The bar sits at the right edge, past the screen gutter.
                val gutter = MetroDimens.Gutter.toPx()
                val inset = 6.dp.toPx()
                val left = size.width + gutter - CLEARED_BAR_INSET.toPx() - CLEARED_BAR.toPx()
                drawRect(bar, Offset(left, inset), Size(CLEARED_BAR.toPx(), size.height - 2 * inset))
                val c = measurer.measure(RegisterCopy.CLEARED_MARK, mark)
                drawText(c, topLeft = Offset(left + (CLEARED_BAR.toPx() - c.size.width) / 2, (size.height - c.size.height) / 2))
            }.metroPress(onPress, onLongPress)
            .padding(vertical = REGISTER_ROW_AIR)
            .padding(end = CLEARED_BAR + CLEARED_BAR_INSET + CLEARED_BAR_GAP - MetroDimens.Gutter)
            .semantics {
                stateDescription = if (row.cleared) RegisterCopy.CLEARED else RegisterCopy.NOT_CLEARED
                if (picked != null) selected = picked
            }.testTag("register-row-${row.id}"),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            if (picked != null) {
                SelectBox(picked, Modifier.width(SELECT_BOX).padding(top = 2.dp).testTag("register-select-${row.id}"))
            }
            Column(Modifier.weight(LEDGER_NAME + LEDGER_FIGURE).padding(end = MetroDimens.Gutter)) {
                MetroText(row.payee, Metro.typography.body, maxLines = 2)
                MetroText(
                    registerDetail(row),
                    Metro.typography.caption,
                    Modifier.testTag("register-detail-${row.id}"),
                    color = Metro.colors.secondary,
                    maxLines = 2,
                )
            }
            MetroText(
                MoneyFormat.format(row.amountMinor, page.currency),
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
                    MoneyFormat.format(part.amountMinor, page.currency),
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
private val CLEARED_BAR_INSET = 2.dp
private val CLEARED_BAR_GAP = 6.dp
private val SELECT_BOX = 32.dp

private val REGISTER_ROW_AIR = 15.dp

/** The quieter line under a payee: category, then the note. The date heads its day; the bar shows cleared. */
internal fun registerDetail(row: RegisterRow): String =
    listOfNotNull(
        row.categoryLabel.ifBlank { null },
        row.note.trim().ifBlank { null },
    ).joinToString(" · ")

@Composable
fun TransactionForm(
    page: RegisterPage,
    row: RegisterRow?,
    initialDate: String,
    error: String?,
    onSave: (TransactionDraft) -> Unit,
    onDelete: (String) -> Unit,
    onSplit: (String) -> Unit,
    onSchedule: (String) -> Unit = {},
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
            .verticalScroll(rememberScrollState())
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
            ChoiceLine(RegisterCopy.NO_CATEGORY, categoryId == null) { categoryId = null }
            page.categories.forEach { category ->
                ChoiceLine(category.name, category.id == categoryId) { categoryId = category.id }
            }
        }
        val commit = {
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
        RememberFormSave(commit)
        MetroButtonGrid(Modifier.padding(top = 8.dp)) {
            if (!formSaveOnBar()) {
                MetroButton("save", onClick = commit)
            }
            if (row != null) {
                if (row.transferId == null) {
                    MetroButton("split") { onSplit(row.id) }
                    MetroButton("schedule") { onSchedule(row.id) }
                }
                MetroButton("delete") { onDelete(row.id) }
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
            when (val parsed = RegisterEntry.split(row.amountMinor, page.currency.decimals, lines)) {
                is EntryResult.Ok -> {
                    localError = null
                    onSave(row.id, parsed.value)
                }
                is EntryResult.Rejected -> localError = parsed.reason
            }
        }
        RememberFormSave(commitSplit)
        MetroButtonGrid(Modifier.padding(top = MetroDimens.Grid)) {
            MetroButton("add part") {
                lines = lines + ("" to "")
            }
            if (!formSaveOnBar()) {
                MetroButton("save", onClick = commitSplit)
            }
            if (row.parent) {
                MetroButton("one category") { onUnsplit(row.id) }
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
        MetroField(date, { date = it }, "date", Modifier.padding(top = MetroDimens.Grid))
        MetroField(amount, { amount = it }, "amount", Modifier.padding(top = MetroDimens.Gutter))
        MetroField(note, { note = it }, "note", Modifier.padding(top = MetroDimens.Gutter))
        val shown = localError ?: error
        if (shown != null) {
            MetroText(shown, Metro.typography.body, Modifier.padding(top = MetroDimens.Gutter), color = Metro.accent.text)
        }
        LazyColumn(Modifier.weight(1f).padding(top = MetroDimens.Grid)) {
            items(others, key = { it.id }) { account ->
                ChoiceLine(account.name, account.id == toId) { toId = account.id }
            }
            if (needsCategory) {
                item {
                    MetroText(
                        "category",
                        Metro.typography.caption,
                        Modifier.padding(top = MetroDimens.Grid, bottom = MetroDimens.Gutter),
                        color = Metro.colors.secondary,
                    )
                }
                items(page.categories, key = { "cat-${it.id}" }) { category ->
                    ChoiceLine(category.name, category.id == categoryId) { categoryId = category.id }
                }
            }
        }
        val commitTransfer = {
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
        RememberFormSave(commitTransfer)
        if (!formSaveOnBar()) {
            MetroButton("save", Modifier.padding(top = MetroDimens.Grid), onClick = commitTransfer)
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
            MetroText(error, Metro.typography.body, Modifier.padding(top = MetroDimens.Gutter), color = Metro.accent.text)
        }
        LazyColumn(Modifier.weight(1f).padding(top = MetroDimens.Grid)) {
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
        MetroButtonGrid {
            MetroButton("change", onClick = onChange)
            MetroButton("keep", onClick = onKeep)
        }
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
