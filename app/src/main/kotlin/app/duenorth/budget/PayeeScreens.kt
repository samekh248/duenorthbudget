package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.PayeeRow
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

@Composable
fun PayeesScreen(
    payees: List<PayeeRow>,
    error: String?,
    mergeTarget: String?,
    onRename: (String, String, Boolean, Boolean) -> Unit,
    onConfirmMerge: (String, String) -> Unit,
    onCancelMerge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<PayeeRow?>(null) }
    var name by remember(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    var rememberRule by remember(editing?.id) { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background),
    ) {
        if (mergeTarget != null && editing != null) {
            Column(Modifier.padding(MetroDimens.Gutter)) {
                MetroText("merge payees", Metro.typography.header)
                MetroText(
                    "This name already exists. Confirm to merge transactions into the existing payee.",
                    Metro.typography.body,
                    Modifier.padding(top = 8.dp),
                )
                MetroButton("merge", Modifier.padding(top = 12.dp)) {
                    onConfirmMerge(editing!!.id, name)
                    editing = null
                }
                MetroButton("cancel", Modifier.padding(top = 8.dp)) { onCancelMerge() }
            }
        } else if (editing != null) {
            Column(Modifier.padding(MetroDimens.Gutter)) {
                MetroText("rename payee", Metro.typography.header)
                MetroField(name, { name = it }, "name", Modifier.padding(top = 8.dp))
                RowChoice("remember as rule", rememberRule) { rememberRule = !rememberRule }
                if (error != null) {
                    MetroText(error, Metro.typography.body, Modifier.padding(top = 8.dp), color = Metro.accent.text)
                }
                MetroButton("save", Modifier.padding(top = 12.dp)) {
                    onRename(editing!!.id, name, false, rememberRule)
                }
                MetroButton("cancel", Modifier.padding(top = 8.dp)) { editing = null }
            }
        } else {
            if (error != null) {
                MetroText(error, Metro.typography.body, Modifier.padding(MetroDimens.Gutter), color = Metro.accent.text)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(payees.filter { !it.transfer }, key = { it.id }) { payee ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = MetroDimens.TouchTarget)
                            .metroPress {
                                editing = payee
                                name = payee.name
                            }
                            .padding(horizontal = MetroDimens.Gutter, vertical = 10.dp),
                    ) {
                        MetroText(payee.name, Metro.typography.subheader)
                        MetroText(
                            "${payee.transactionCount} transactions",
                            Metro.typography.caption,
                            color = Metro.colors.secondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowChoice(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    MetroText(
        label,
        Metro.typography.body,
        Modifier
            .padding(top = 8.dp)
            .metroPress(onClick = onSelect),
        color = if (selected) Metro.accent.text else Metro.colors.secondary,
    )
}
