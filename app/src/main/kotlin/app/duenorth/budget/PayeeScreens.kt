package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.PayeeRow
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroSegmentedToggle
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.ScreenAction
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
            ConfirmPage(
                title = PayeeScreenCopy.MERGE_TITLE,
                body = PayeeScreenCopy.MERGE_BODY,
                yes = PayeeScreenCopy.MERGE,
                onYes = {
                    onConfirmMerge(editing!!.id, name)
                    editing = null
                },
                onKeep = onCancelMerge,
                tag = "payee-merge",
            )
        } else if (editing != null) {
            Column(Modifier.padding(MetroDimens.Gutter)) {
                MetroText("rename payee", Metro.typography.header)
                MetroField(name, { name = it }, "name", Modifier.padding(top = MetroDimens.Grid))
                RowChoice("remember as rule", rememberRule) { rememberRule = !rememberRule }
                if (error != null) {
                    MetroText(error, Metro.typography.body, Modifier.padding(top = MetroDimens.Gutter), color = Metro.accent.text)
                }
                PageBar(
                    listOf(
                        ScreenAction("payee-save", AppGlyph.Save, "save") { onRename(editing!!.id, name, false, rememberRule) },
                        ScreenAction("payee-cancel", AppGlyph.Cancel, "cancel") { editing = null },
                    ),
                )
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
                            }.padding(horizontal = MetroDimens.Gutter, vertical = MetroDimens.Row),
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

/** Remember as rule as the off/on pair every setting in the app uses. */
@Composable
private fun RowChoice(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = MetroDimens.Gutter), verticalAlignment = Alignment.CenterVertically) {
        MetroText(label, Metro.typography.body, Modifier.weight(1f).padding(end = 12.dp))
        MetroSegmentedToggle(
            first = ExperimentalCopy.OFF,
            second = ExperimentalCopy.ON,
            firstSelected = !selected,
            onSelect = { first -> if (first == selected) onSelect() },
            tag = "payee-remember-rule",
        )
    }
}

/** Words for the payees page's questions. */
object PayeeScreenCopy {
    const val MERGE_TITLE = "merge payees?"
    const val MERGE_BODY = "this name already exists; merging moves these transactions to the existing payee"
    const val MERGE = "merge"
}
