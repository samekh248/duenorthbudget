package app.duenorth.budget

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountChoice
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.DateFormats
import app.duenorth.budget.core.EntryResult
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MoneyParse
import app.duenorth.budget.core.RegisterCopy
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RegisterRow
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.core.SplitPart
import app.duenorth.budget.core.TransactionDraft
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.MetroComboBox
import app.duenorth.budget.design.components.MetroComboValue
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroSegmentedToggle
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.controlFrame
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import java.util.UUID
import kotlin.math.abs

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
    // Split lines replace the category box; "split into more categories" under it starts them, one line at a time after.
    var splits by remember(row?.id) {
        mutableStateOf(
            row?.parts.orEmpty().map { part ->
                val text = MoneyParse.editText(abs(part.amountMinor), page.currency.decimals, FormatOptions(hideFraction, numberFormat))
                SplitLine(part.categoryId, text)
            },
        )
    }
    // A save refused over one split line: that line's index and why, shown on the red block under it.
    var lineError by remember(row?.id) { mutableStateOf<Pair<Int, String>?>(null) }
    val splitting = splits.isNotEmpty() && transferTo == null
    val showCategories = !splitting && (transferTo == null || mixed)
    // The full-screen list leads with what this payee is usually filed under, or failing that what was used last.
    val suggested = remember(page.suggestions, payee, expense) { page.suggestions.forPayee(payee, income = !expense) }
    val options = remember(page.categories, categoryId, suggested) { CategoryOptions.of(page.categories, categoryId, suggested) }

    fun typedAmount(text: String): Long? = RegisterEntry.signedAmount(text, page.currency.decimals, expense, numberFormat)

    fun editText(minor: Long): String = MoneyParse.editText(abs(minor), page.currency.decimals, FormatOptions(hideFraction, numberFormat))

    // The last line with no amount typed takes the rest of the transaction, so the final amount is never typed.
    val restIndex = splits.lastIndex.takeIf { it >= 1 && splits[it].amount.isBlank() }
    val total = typedAmount(amount) ?: 0L
    val rest = total - splits.filterIndexed { i, _ -> i != restIndex }.sumOf { typedAmount(it.amount) ?: 0L }
    // The rest counts only while it runs the transaction's way; past the total it is nothing, and the split is over.
    val restPart = rest.takeIf { it != 0L && (it < 0L) == expense }

    fun splitAmount(index: Int): Long? = if (index == restIndex) restPart else typedAmount(splits[index].amount)

    fun startSplit() {
        // The category becomes line one, and line two holds the rest until an amount goes on line one.
        if (transferTo == null) splits = listOf(SplitLine(categoryId, ""), SplitLine(null, ""))
    }

    fun addLine() {
        // The line that held the rest keeps what it holds now, and the new line takes the rest from here.
        val pinned =
            splits.mapIndexed { i, line ->
                if (i == restIndex &&
                    restPart != null
                ) {
                    line.copy(amount = editText(restPart))
                } else {
                    line
                }
            }
        splits = pinned + SplitLine(null, "")
    }

    fun splitParts(): Pair<List<SplitPart>?, Pair<Int?, String>?> {
        if (!splitting) return (if (row?.parent == true) emptyList<SplitPart>() else null) to null
        val parts =
            splits.mapIndexed { index, line ->
                val category = line.categoryId ?: return null to (index to RegisterCopy.CHOOSE_CATEGORY)
                // A rest line past the total is the whole split's problem; one with nothing left, or a bad amount, is the line's.
                if (index == restIndex && rest != 0L && restPart == null) return null to (null to RegisterCopy.SPLIT_MUST_ADD)
                val part = splitAmount(index) ?: return null to (index to RegisterCopy.ENTER_AMOUNT)
                SplitPart(category, part)
            }
        return if (parts.sumOf { it.amountMinor } == total) parts to null else null to (null to RegisterCopy.SPLIT_MUST_ADD)
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
                val (parts, refused) = splitParts()
                val (line, reason) = refused ?: (null to null)
                fieldError = if (reason != null && line == null) TransactionField.CATEGORY to reason else null
                lineError = if (reason != null && line != null) line to reason else null
                if (refused == null) onSave(parsed.value.copy(cleared = cleared, parts = parts))
            }
            is EntryResult.Rejected -> {
                lineError = null
                fieldError = TransactionField.of(parsed.reason) to parsed.reason
            }
        }
    }
    RememberFormSave(commit)

    fun errorFor(field: TransactionField): String? =
        // A save the budget refused names its field too, so the message sits under the box it is about.
        fieldError?.takeIf { it.first == field }?.second ?: error?.takeIf { TransactionField.of(it) == field && fieldError == null }
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
                    onAmount = { text ->
                        val (rest, minus) = RegisterEntry.leadingMinus(text)
                        amount = rest
                        if (minus) expense = true
                    },
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
                    // A transfer has no split, even one across the budget's edge that keeps a category.
                    if (transferTo == null) {
                        FormLink(TransactionFormCopy.SPLIT_START, "transaction-split-start", ::startSplit)
                    }
                }
            }
            if (splitting) {
                LabeledField(
                    TransactionFormCopy.SPLIT,
                    errorFor(TransactionField.CATEGORY),
                    Modifier.padding(top = MetroDimens.FieldGroupGap),
                    flagged = lineError != null,
                ) {
                    SplitLines(
                        lines = splits,
                        categories = page.categories,
                        restIndex = restIndex,
                        rest = restPart?.let(::editText) ?: editText(0L),
                        lineError = lineError,
                        left = total - splits.indices.sumOf { splitAmount(it) ?: 0L },
                        total = total,
                        expense = expense,
                        format = { MoneyFormat.format(it, page.currency, FormatOptions(hideFraction, numberFormat)) },
                        onLine = { index, line ->
                            // A line takes the transaction's sign, so a minus on one turns the whole of it to expense.
                            val (typed, minus) = RegisterEntry.leadingMinus(line.amount)
                            if (minus) expense = true
                            if (lineError?.first == index) lineError = null
                            splits = splits.mapIndexed { i, old -> if (i == index) line.copy(amount = typed) else old }
                        },
                        onRemove = { index ->
                            val left = splits.filterIndexed { i, _ -> i != index }
                            lineError = null
                            // One line left is no split: it becomes the category again.
                            if (left.size < 2) {
                                categoryId = left.firstOrNull()?.categoryId
                                splits = emptyList()
                            } else {
                                splits = left
                            }
                        },
                        onAdd = ::addLine,
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
                    options =
                        open.line?.let {
                            CategoryOptions.of(
                                page.categories,
                                splits.getOrNull(it)?.categoryId,
                                suggested,
                            )
                        } ?: options,
                    onPick = { picked ->
                        val line = open.line
                        if (line == null) {
                            categoryId = picked
                        } else {
                            splits = splits.mapIndexed { i, old -> if (i == line) old.copy(categoryId = picked) else old }
                            if (lineError?.first == line) lineError = null
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

/** One split line as typed: its category (none picked yet is null) and its amount box. */
data class SplitLine(
    val categoryId: String?,
    val amount: String,
)

/**
 * The split lines in place of the category box: each a category, its amount and a remove button on one row.
 * The line at [restIndex] has no amount typed and shows the [rest] it takes. A meter and what is left close the list.
 */
@Composable
private fun SplitLines(
    lines: List<SplitLine>,
    categories: List<CategoryChoice>,
    restIndex: Int?,
    rest: String,
    lineError: Pair<Int, String>?,
    left: Long,
    total: Long,
    expense: Boolean,
    format: (Long) -> String,
    onLine: (Int, SplitLine) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    onOpenFull: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("transaction-split"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEachIndexed { index, line ->
            val options = remember(categories, line.categoryId) { CategoryOptions.of(categories, line.categoryId) }
            Column {
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
                        // The rest line's hint is what it takes, until an amount typed on it fixes it.
                        if (index == restIndex) rest else TransactionFormCopy.AMOUNT,
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
                lineError?.takeIf { it.first == index }?.let { (_, reason) ->
                    FieldErrorBlock(reason, "transaction-split-$index-error")
                }
            }
        }
        FormLink(TransactionFormCopy.SPLIT_ADD, "transaction-split-add", onAdd)
        // Money out splits into money out: the sign follows the transaction's, so over means past the total.
        val over = left != 0L && (left < 0L) != expense
        val assigned = if (total == 0L) 0f else ((total - left).toFloat() / total).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(SPLIT_METER)
                .background(Metro.colors.chrome)
                .testTag("transaction-split-meter"),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(assigned)
                    .height(SPLIT_METER)
                    .background(if (over) Metro.colors.errorFill else Metro.accent.fill),
            )
        }
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

/** A plus and an accent label: an action that adds to the form, sitting under what it adds to. */
@Composable
private fun FormLink(
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyphIcon(AppGlyph.Add, Metro.accent.text)
        MetroText(label, Metro.typography.control, Modifier.padding(start = 8.dp), color = Metro.accent.text)
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
    const val SPLIT_START = "split into more categories"
    const val SPLIT_ADD = "add a category"
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
    /** Category ids the full-screen list shows first, in their own group. */
    val suggested: List<String> = emptyList(),
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
            suggested: List<String> = emptyList(),
        ): CategoryOptions {
            val shown = categories.filter { !it.hidden || it.id == current }
            val groups =
                shown
                    .groupBy { it.groupName }
                    .map { (name, rows) -> name to rows }
            val visible = categories.filter { !it.hidden }.map { it.id }.toSet()
            return CategoryOptions(groups, current, suggested.filter { it in visible })
        }
    }
}

private val NOTE_LINES = 2..6
private val SPLIT_AMOUNT_WIDTH = 110.dp
private val SPLIT_METER = 4.dp
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
    /** The label turns red for a problem shown further in, such as on one split line. */
    flagged: Boolean = false,
    content: @Composable () -> Unit,
) {
    // A field with a problem scrolls into view, so a save that did not go through never looks like nothing happened.
    val inView = remember { BringIntoViewRequester() }
    LaunchedEffect(error) {
        if (error != null) {
            // After the message is laid out, so the whole of it comes into view.
            withFrameNanos {}
            inView.bringIntoView()
        }
    }
    Column(modifier.fillMaxWidth().bringIntoViewRequester(inView)) {
        MetroText(
            label,
            Metro.typography.caption,
            Modifier.padding(bottom = MetroDimens.FieldLabelGap),
            color = if (error != null || flagged) Metro.colors.error else Metro.colors.secondary,
        )
        content()
        if (error != null) {
            FieldErrorBlock(error, "transaction-error-${TransactionField.of(error).name.lowercase()}", scroll = false)
        }
    }
}

/** The message on the red swatch, right under its box, so it can't pass for a hint; with [scroll], it brings itself into view. */
@Composable
private fun FieldErrorBlock(
    error: String,
    tag: String,
    scroll: Boolean = true,
) {
    val inView = remember { BringIntoViewRequester() }
    LaunchedEffect(error) {
        if (scroll) {
            withFrameNanos {}
            inView.bringIntoView()
        }
    }
    Row(
        Modifier
            .padding(top = MetroDimens.FieldLabelGap)
            .fillMaxWidth()
            .bringIntoViewRequester(inView)
            .background(Metro.colors.errorFill)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetroText("!", Metro.typography.body, Modifier.padding(end = 10.dp), color = Metro.colors.onErrorFill)
        MetroText(error, Metro.typography.body, Modifier.testTag(tag), color = Metro.colors.onErrorFill)
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
