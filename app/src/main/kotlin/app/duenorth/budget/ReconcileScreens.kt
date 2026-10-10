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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.ReconcileCopy
import app.duenorth.budget.core.ReconcilePage
import app.duenorth.budget.core.ReconcileRow
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

@Composable
fun ReconcileStartScreen(
    accountName: String,
    error: String?,
    onStart: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var balance by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(RegisterEntry.todayIso()) }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText(accountName, Metro.typography.header)
        MetroText(ReconcileCopy.RECONCILE, Metro.typography.caption, color = Metro.colors.secondary)
        MetroField(balance, { balance = it }, ReconcileCopy.STATEMENT, modifier = Modifier.testTag("statement-balance"))
        MetroField(date, { date = it }, "date", modifier = Modifier.testTag("statement-date"))
        if (error != null) {
            MetroText(error, Metro.typography.body, color = Metro.accent.text)
        }
        MetroButton("start") { onStart(balance, date) }
    }
}

@Composable
fun ReconcileScreen(
    page: ReconcilePage,
    error: String?,
    onGesture: (Boolean) -> Unit,
    onToggle: (String) -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onGesture(it) }
    }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(horizontal = MetroDimens.Gutter),
    ) {
        MetroText(page.account.name, Metro.typography.header)
        MetroText(ReconcileCopy.STATEMENT, Metro.typography.caption, color = Metro.colors.secondary)
        MetroText(
            MoneyFormat.format(page.statementMinor, page.currency),
            Metro.typography.subheader,
            modifier = Modifier.testTag("statement-amount"),
        )
        MetroText(
            ReconcileCopy.CLEARED,
            Metro.typography.caption,
            Modifier.padding(top = 8.dp),
            color = Metro.colors.secondary,
        )
        MetroText(
            MoneyFormat.format(page.clearedTotalMinor, page.currency),
            Metro.typography.subheader,
            modifier = Modifier.testTag("cleared-total"),
        )
        MetroText(
            ReconcileCopy.DIFFERENCE,
            Metro.typography.caption,
            Modifier.padding(top = 8.dp),
            color = Metro.colors.secondary,
        )
        MetroText(
            MoneyFormat.format(page.differenceMinor, page.currency),
            Metro.typography.header,
            color = Metro.accent.text,
            modifier = Modifier.testTag("reconcile-difference"),
        )
        if (error != null) {
            MetroText(
                error,
                Metro.typography.body,
                Modifier.padding(top = 8.dp),
                color = Metro.accent.text,
            )
        }
        LazyColumn(Modifier.weight(1f).padding(top = 12.dp), state = list) {
            items(page.rows, key = { it.id }) { row -> ReconcileLine(row, page, onToggle) }
        }
        MetroButton(ReconcileCopy.FINISH, Modifier.padding(vertical = 8.dp), onClick = onFinish)
        MetroButton(ReconcileCopy.CANCEL, onClick = onCancel)
    }
}

@Composable
private fun ReconcileLine(
    row: ReconcileRow,
    page: ReconcilePage,
    onToggle: (String) -> Unit,
) {
    val canToggle = !row.reconciled
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .then(if (canToggle) Modifier.metroPress { onToggle(row.id) } else Modifier)
            .padding(vertical = 8.dp)
            .testTag("reconcile-row-${row.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            MetroText(row.payee, Metro.typography.subheader, maxLines = 2)
            MetroText(RegisterEntry.registerDateLabel(row.date), Metro.typography.caption, color = Metro.colors.secondary)
        }
        val mark =
            when {
                row.reconciled -> "R"
                row.cleared -> "C"
                else -> "·"
            }
        MetroText(mark, Metro.typography.subheader, Modifier.widthIn(min = 24.dp))
        MetroText(
            MoneyFormat.format(row.amountMinor, page.currency),
            Metro.typography.subheader,
            Modifier.widthIn(max = 120.dp),
        )
    }
}
