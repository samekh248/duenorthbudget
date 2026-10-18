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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountDetail
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

/** Words for spec 010's account screens (`contracts/accounts.md`). */
object AccountScreenCopy {
    const val TITLE = "accounts"
    const val ADD = "add account"
    const val MANAGE = "manage"
    const val SHOW_CLOSED = "show closed accounts"
    const val HIDE_CLOSED = "hide closed accounts"
    const val REORDER = "reorder"
    const val REORDER_HINT = "tap an account to move it up"
    const val DONE = "done"
    const val ON_BUDGET = "on budget"
    const val OFF_BUDGET = "off budget"
    const val CLOSED = "closed"
    const val REOPEN = "reopen"
    const val EDIT = "edit account"
    const val CLOSE = "close account"
    const val FORCE_CLOSE = "force close"
    const val KEEP = "keep"
    const val NAME = "name"
    const val NOTE = "note"
    const val BALANCE = "starting balance"
    const val BALANCE_LEFT = "balance"
    const val DESTINATION = "move the balance to"
    const val CATEGORY = "category"
    const val ZERO_CLOSES = "this account has no balance, so it closes as is"
    const val FORCE_HINT = "or force close to delete it and its transactions"
}

/** Every account with its balance; reorder mode and the closed list come from the `•••` menu. Tag `accounts-screen`. */
@Composable
fun AccountsScreen(
    accounts: List<AccountDetail>,
    currency: CurrencySpec,
    showClosed: Boolean,
    reorder: Boolean,
    onAccount: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onReopen: (String) -> Unit,
    onDoneReorder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val open = accounts.filter { !it.closed }
    val onBudget = open.filter { !it.offBudget }
    val offBudget = open.filter { it.offBudget }
    val closed = if (showClosed) accounts.filter { it.closed } else emptyList()
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("accounts-screen"),
    ) {
        MetroText(AccountScreenCopy.TITLE, Metro.typography.header)
        if (reorder) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MetroText(
                    AccountScreenCopy.REORDER_HINT,
                    Metro.typography.caption,
                    Modifier.weight(1f).testTag("accounts-reorder-hint"),
                    color = Metro.colors.secondary,
                )
                MetroText(
                    AccountScreenCopy.DONE,
                    Metro.typography.subheader,
                    Modifier
                        .heightIn(min = MetroDimens.TouchTarget)
                        .metroPress(onDoneReorder)
                        .padding(vertical = MetroDimens.Row)
                        .testTag("accounts-reorder-done"),
                    color = Metro.accent.text,
                )
            }
        }
        LazyColumn(Modifier.weight(1f).padding(top = MetroDimens.Gutter)) {
            if (onBudget.isNotEmpty()) {
                item { AccountHeading(AccountScreenCopy.ON_BUDGET) }
                items(onBudget, key = { it.id }) { account ->
                    ManagedAccountLine(account, currency) { if (reorder) onMoveUp(account.id) else onAccount(account.id) }
                }
            }
            if (offBudget.isNotEmpty()) {
                item { AccountHeading(AccountScreenCopy.OFF_BUDGET) }
                items(offBudget, key = { it.id }) { account ->
                    ManagedAccountLine(account, currency) { if (reorder) onMoveUp(account.id) else onAccount(account.id) }
                }
            }
            if (closed.isNotEmpty()) {
                item { AccountHeading(AccountScreenCopy.CLOSED) }
                items(closed, key = { it.id }) { account ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            ManagedAccountLine(account, currency, dim = true) { onAccount(account.id) }
                        }
                        MetroText(
                            AccountScreenCopy.REOPEN,
                            Metro.typography.body,
                            Modifier
                                .heightIn(min = MetroDimens.TouchTarget)
                                .metroPress { onReopen(account.id) }
                                .padding(start = MetroDimens.Gutter, top = MetroDimens.Row)
                                .testTag("account-reopen-${account.id}"),
                            color = Metro.accent.text,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountHeading(label: String) {
    MetroText(
        label,
        Metro.typography.caption,
        Modifier.padding(top = MetroDimens.Gutter, bottom = 4.dp),
        color = Metro.colors.secondary,
    )
}

@Composable
private fun ManagedAccountLine(
    account: AccountDetail,
    currency: CurrencySpec,
    dim: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick)
            .padding(vertical = MetroDimens.Row)
            .testTag("accounts-line-${account.id}"),
        verticalAlignment = Alignment.Top,
    ) {
        MetroText(
            account.name,
            Metro.typography.subheader,
            Modifier.weight(1f).padding(end = 12.dp),
            color = if (dim) Metro.colors.secondary else Metro.colors.foreground,
            maxLines = 2,
        )
        MetroText(
            MoneyFormat.format(account.balanceMinor, currency, formatOptions()),
            Metro.typography.subheader,
            Modifier.widthIn(max = 148.dp),
            color = if (dim) Metro.colors.secondary else Metro.colors.foreground,
            maxLines = 1,
        )
    }
}

/** New account: name, on or off budget, and an optional starting balance. Tag `account-add`. */
@Composable
fun AddAccountScreen(
    error: String?,
    onSave: (name: String, offBudget: Boolean, balance: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    val prefs = LocalFormatPrefs.current
    var balance by remember { mutableStateOf("") }
    var offBudget by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("account-add"),
        verticalArrangement = Arrangement.spacedBy(MetroDimens.Gutter),
    ) {
        MetroText(AccountScreenCopy.ADD, Metro.typography.header)
        MetroField(name, { name = it }, AccountScreenCopy.NAME, Modifier.testTag("account-add-name"))
        MetroField(balance, { balance = it }, AccountScreenCopy.BALANCE, Modifier.testTag("account-add-balance"))
        Row {
            Choice(AccountScreenCopy.ON_BUDGET, !offBudget, "account-add-on") { offBudget = false }
            Choice(AccountScreenCopy.OFF_BUDGET, offBudget, "account-add-off") { offBudget = true }
        }
        if (error != null) MetroText(error, Metro.typography.body, Modifier.testTag("account-error"), color = Metro.accent.text)
        RememberFormSave { onSave(name, offBudget, prefs.readAmount(balance)) }
        if (!formSaveOnBar()) {
            MetroButton("save", Modifier.testTag("account-add-save")) { onSave(name, offBudget, prefs.readAmount(balance)) }
        }
    }
}

/** Rename and note. Tag `account-edit`. */
@Composable
fun EditAccountScreen(
    account: AccountDetail,
    error: String?,
    onSave: (name: String, note: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(account.id) { mutableStateOf(account.name) }
    var note by remember(account.id) { mutableStateOf(account.note) }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("account-edit"),
        verticalArrangement = Arrangement.spacedBy(MetroDimens.Gutter),
    ) {
        MetroText(AccountScreenCopy.EDIT, Metro.typography.header)
        MetroField(name, { name = it }, AccountScreenCopy.NAME, Modifier.testTag("account-edit-name"))
        MetroField(note, { note = it }, AccountScreenCopy.NOTE, Modifier.testTag("account-edit-note"), lines = 2..6)
        if (error != null) MetroText(error, Metro.typography.body, Modifier.testTag("account-error"), color = Metro.accent.text)
        RememberFormSave { onSave(name, note) }
        if (!formSaveOnBar()) {
            MetroButton("save", Modifier.testTag("account-edit-save")) { onSave(name, note) }
        }
    }
}

/**
 * Close: picks where a balance goes, and a category when it leaves the budget. Nothing is
 * written until the close button. Force close asks again on its own screen. Tag `account-close`.
 */
@Composable
fun CloseAccountScreen(
    account: AccountDetail,
    others: List<AccountDetail>,
    categories: List<CategoryChoice>,
    currency: CurrencySpec,
    error: String?,
    onClose: (destinationId: String?, categoryId: String?) -> Unit,
    onForceClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinations = others.filter { it.id != account.id && !it.closed }
    var destinationId by remember(account.id) { mutableStateOf<String?>(null) }
    var categoryId by remember(account.id) { mutableStateOf<String?>(null) }
    val destination = destinations.firstOrNull { it.id == destinationId }
    val needsBalanceMove = account.balanceMinor != 0L && account.transactionCount > 0
    val needsCategory = needsBalanceMove && !account.offBudget && destination?.offBudget == true
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("account-close"),
    ) {
        MetroText(AccountScreenCopy.CLOSE, Metro.typography.header)
        MetroText(account.name, Metro.typography.caption, color = Metro.colors.secondary)
        MetroText(
            "${AccountScreenCopy.BALANCE_LEFT} ${MoneyFormat.format(account.balanceMinor, currency, formatOptions())}",
            Metro.typography.subheader,
            Modifier.padding(top = MetroDimens.Gutter).testTag("account-close-balance"),
        )
        if (error != null) {
            MetroText(
                error,
                Metro.typography.body,
                Modifier.padding(top = MetroDimens.Gutter).testTag("account-error"),
                color = Metro.accent.text,
            )
        }
        LazyColumn(Modifier.weight(1f).padding(top = MetroDimens.Gutter)) {
            if (!needsBalanceMove) {
                item { MetroText(AccountScreenCopy.ZERO_CLOSES, Metro.typography.body, color = Metro.colors.secondary) }
            } else {
                item { AccountHeading(AccountScreenCopy.DESTINATION) }
                items(destinations, key = { "dest-${it.id}" }) { other ->
                    Choice(other.name, other.id == destinationId, "account-close-destination-${other.id}") {
                        destinationId = other.id
                        if (other.offBudget == account.offBudget) categoryId = null
                    }
                }
                if (needsCategory) {
                    item { AccountHeading(AccountScreenCopy.CATEGORY) }
                    items(categories.filter { !it.income }, key = { "cat-${it.id}" }) { category ->
                        Choice(category.name, category.id == categoryId, "account-close-category-${category.id}") {
                            categoryId = category.id
                        }
                    }
                }
            }
        }
        MetroButton(AccountScreenCopy.CLOSE, Modifier.padding(top = MetroDimens.Gutter).testTag("account-close-confirm")) {
            onClose(destinationId, if (needsCategory) categoryId else null)
        }
        if (needsBalanceMove) {
            MetroText(
                AccountScreenCopy.FORCE_HINT,
                Metro.typography.caption,
                Modifier.padding(top = MetroDimens.Gutter),
                color = Metro.colors.secondary,
            )
            MetroButton(AccountScreenCopy.FORCE_CLOSE, Modifier.padding(top = 8.dp).testTag("account-force-close")) { onForceClose() }
        }
    }
}

/** The force-close gate; nothing is written until [onConfirm]. Tag `account-force-close-confirm`. */
@Composable
fun ForceCloseConfirmScreen(
    reason: String,
    onConfirm: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("account-force-close-confirm"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MetroText(AccountScreenCopy.FORCE_CLOSE, Metro.typography.header)
        MetroText(reason, Metro.typography.subheader)
        MetroButtonGrid {
            MetroButton(AccountScreenCopy.FORCE_CLOSE, Modifier.testTag("account-force-close-yes"), onClick = onConfirm)
            MetroButton(AccountScreenCopy.KEEP, Modifier.testTag("account-force-close-keep"), onClick = onKeep)
        }
    }
}

@Composable
internal fun Choice(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    MetroText(
        label,
        Metro.typography.subheader,
        Modifier
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick)
            .padding(end = MetroDimens.Grid, top = MetroDimens.Row, bottom = MetroDimens.Row)
            .testTag(tag),
        color = if (selected) Metro.accent.text else Metro.colors.foreground,
        maxLines = 2,
    )
}
