package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AssistantCopy
import app.duenorth.budget.core.AssistantReport
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.Finding
import app.duenorth.budget.core.FindingAction
import app.duenorth.budget.core.FindingKind
import app.duenorth.budget.core.FindingSide
import app.duenorth.budget.core.FindingSure
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.MatchCopy
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroWeights

/** How many findings of a kind show before "show more". */
internal const val ASSISTANT_FIRST = 3

/**
 * The assistant pivot item: a count of what needs a look, then each kind of finding under its heading, a few at a
 * time, every one with its fix and its "that's fine" side by side.
 */
@Composable
fun AssistantSection(
    report: AssistantReport?,
    currency: CurrencySpec,
    onListGesture: (Boolean) -> Unit,
    onAct: (Finding, FindingAction) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Set<FindingKind> = emptySet(),
    onToggleExpanded: (FindingKind) -> Unit = {},
    settling: Set<String> = emptySet(),
    formatOptions: FormatOptions = formatOptions(),
) {
    if (report == null) {
        Box(modifier.fillMaxSize()) { Placeholder() }
        return
    }
    val findings = report.findings.filter { it.key !in settling }
    if (findings.isEmpty()) {
        AssistantClear(modifier)
        return
    }
    val money = { minor: Long -> MoneyFormat.format(minor, currency, formatOptions) }
    val list = rememberLazyListState()
    LaunchedEffect(list) { snapshotFlow { list.isScrollInProgress }.collect { onListGesture(it) } }
    val byKind = AssistantReport(findings).byKind()
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter).testTag("assistant-list"),
        state = list,
    ) {
        item(key = "count") {
            BudgetTotal(
                AssistantCopy.TO_LOOK_AT,
                "${findings.size}",
                Metro.accent.text,
                "assistant-count",
            )
        }
        byKind.forEach { (kind, all) ->
            val open = kind in expanded
            val shown = if (open) all else all.take(ASSISTANT_FIRST)
            item(key = "kind-${kind.name}") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp).testTag("assistant-kind-${kind.name}"),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    MetroText(kind.title, Metro.typography.subheader, Modifier.weight(1f), maxLines = 1)
                    MetroText("${all.size}", Metro.typography.subheader, color = Metro.accent.text, maxLines = 1)
                }
            }
            items(shown, key = { it.key }) { finding ->
                FindingPanel(finding, money, onAct, Modifier.padding(bottom = 12.dp))
            }
            if (all.size > ASSISTANT_FIRST) {
                item(key = "more-${kind.name}") {
                    MetroText(
                        if (open) AssistantCopy.LESS else AssistantCopy.more(all.size - ASSISTANT_FIRST),
                        Metro.typography.body,
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = MetroDimens.TouchTarget)
                            .metroPress { onToggleExpanded(kind) }
                            .padding(vertical = 12.dp)
                            .testTag("assistant-more-${kind.name}"),
                        color = Metro.accent.text,
                    )
                }
            }
        }
        item(key = "end") { Box(Modifier.height(MetroDimens.Row)) }
    }
}

/** Nothing to look at: says so in the middle of the item. */
@Composable
private fun AssistantClear(modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(MetroDimens.Gutter).testTag("assistant-clear"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MetroText(AssistantCopy.ALL_CLEAR, Metro.typography.subheader.copy(textAlign = TextAlign.Center))
        MetroText(
            AssistantCopy.ALL_CLEAR_DETAIL,
            Metro.typography.body.copy(textAlign = TextAlign.Center),
            color = Metro.colors.secondary,
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 290.dp),
        )
    }
}

/** One finding: what the assistant thinks, the transactions, why, and the buttons, the fix in the accent. */
@Composable
internal fun FindingPanel(
    finding: Finding,
    money: (Long) -> String,
    onAct: (Finding, FindingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .ledgerFill(Metro.colors.chrome)
            .padding(vertical = 14.dp)
            .testTag("finding-${finding.key}"),
    ) {
        if (finding.sure == FindingSure.MAYBE) {
            MetroText(AssistantCopy.MAYBE, Metro.typography.caption, color = Metro.accent.text)
        }
        MetroText(finding.headline, Metro.typography.body)
        finding.sides.forEachIndexed { index, side ->
            FindingSideLine(side, money, showAccount = finding.kind == FindingKind.WRONG_ACCOUNT || index == 0)
        }
        FindingReasons(finding.reasons)
        MetroButtonGrid(Modifier.padding(top = 12.dp)) {
            finding.actions.forEachIndexed { index, action ->
                val tag = "finding-action-$index-${finding.key}"
                if (index == 0) {
                    AccentButton(action.label, Modifier.testTag(tag)) { onAct(finding, action) }
                } else {
                    MetroButton(action.label, Modifier.testTag(tag)) { onAct(finding, action) }
                }
            }
        }
    }
}

@Composable
private fun FindingSideLine(
    side: FindingSide,
    money: (Long) -> String,
    showAccount: Boolean,
) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            MetroText(side.source, Metro.typography.caption, color = Metro.accent.text)
            MetroText(side.side.payee, Metro.typography.body.copy(fontWeight = MetroWeights.Semibold), maxLines = 1)
            MetroText(
                listOfNotNull(
                    RegisterEntry.registerDateLabel(side.side.date),
                    side.side.accountName.takeIf { showAccount && it.isNotBlank() },
                    side.side.categoryName.ifBlank { MatchCopy.NO_CATEGORY },
                ).joinToString(" · "),
                Metro.typography.caption,
                color = Metro.colors.secondary,
                maxLines = 1,
            )
        }
        MetroText(money(side.side.amountMinor), Metro.typography.body, maxLines = 1, softWrap = false)
    }
}

/** The accent rule with why the assistant thinks so. */
@Composable
private fun FindingReasons(reasons: List<String>) {
    if (reasons.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(22.dp).background(Metro.accent.fill))
        MetroText(
            reasons.joinToString(" · "),
            Metro.typography.caption,
            Modifier.padding(start = 10.dp),
            color = Metro.colors.secondary,
            maxLines = 2,
        )
    }
}
