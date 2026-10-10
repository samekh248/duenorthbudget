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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.UpcomingScheduleRow
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

@Composable
fun DueSection(
    rows: List<UpcomingScheduleRow>,
    currency: CurrencySpec,
    onListGesture: (Boolean) -> Unit,
    onPost: (String) -> Unit,
    onSkip: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) {
        MetroText(
            "nothing due",
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
        items(rows, key = { it.id }) { row ->
            DueLine(row, currency, onPost, onSkip)
        }
    }
}

@Composable
private fun DueLine(
    row: UpcomingScheduleRow,
    currency: CurrencySpec,
    onPost: (String) -> Unit,
    onSkip: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MetroDimens.Gutter, vertical = 8.dp)
            .testTag("due-${row.id}"),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                MetroText(row.payee, Metro.typography.subheader, maxLines = 2)
                MetroText(
                    "${row.accountName} · ${RegisterEntry.registerDateLabel(row.nextDate)}",
                    Metro.typography.body,
                    color = Metro.colors.secondary,
                    maxLines = 2,
                )
            }
            MetroText(
                MoneyFormat.format(row.amountMinor, currency),
                Metro.typography.subheader,
                Modifier.widthIn(max = 148.dp),
            )
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroButton(if (row.dueToday) "post" else "post early", Modifier.weight(1f)) { onPost(row.id) }
            MetroButton("skip", Modifier.weight(1f)) { onSkip(row.id) }
        }
    }
}

@Composable
fun ScheduleDuplicateScreen(
    onPostAnyway: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText("matching transaction", Metro.typography.header)
        MetroText(
            "A similar transaction is already in this account within two days of the due date.",
            Metro.typography.body,
        )
        MetroButton("post anyway") { onPostAnyway() }
        MetroButton("cancel") { onCancel() }
    }
}
