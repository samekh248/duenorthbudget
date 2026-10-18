package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.MatchCopy
import app.duenorth.budget.core.MatchPair
import app.duenorth.budget.core.MatchSide
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroWeights

/**
 * A possible match in the inbox's featured panel: the bank's transaction over the one entered by
 * hand, what they share between them, and match or not a match.
 */
@Composable
internal fun MatchPanel(
    pair: MatchPair,
    money: (Long) -> String,
    onMatch: () -> Unit,
    onNotAMatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .ledgerFill(Metro.colors.chrome)
            .padding(vertical = FEATURED_PAD)
            .testTag("match-panel-${pair.key}"),
    ) {
        MetroText(MatchCopy.POSSIBLE_MATCH, Metro.typography.groupHeader, color = Metro.accent.text)
        MatchSideLine(MatchCopy.FROM_BANK, pair.bank, money, showAccount = true)
        MatchLink(pair)
        MatchSideLine(MatchCopy.ENTERED, pair.entered, money, showAccount = false)
        MatchButtons(pair, onMatch, onNotAMatch)
    }
}

/** One side of a pair: where it came from, then its payee, date, category and note. */
@Composable
private fun MatchSideLine(
    source: String,
    side: MatchSide,
    money: (Long) -> String,
    showAccount: Boolean,
) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            MetroText(source, Metro.typography.caption, color = Metro.accent.text)
            MetroText(side.payee, Metro.typography.body.copy(fontWeight = MetroWeights.Semibold), maxLines = 1)
            MetroText(
                matchDetail(side, showAccount),
                Metro.typography.caption,
                color = Metro.colors.secondary,
                maxLines = 1,
            )
        }
        MetroText(money(side.amountMinor), Metro.typography.body, maxLines = 1, softWrap = false)
    }
}

/** The quieter line under a side's payee: date, then the account on the bank's side, category and note. */
internal fun matchDetail(
    side: MatchSide,
    showAccount: Boolean,
): String =
    listOfNotNull(
        RegisterEntry.registerDateLabel(side.date),
        side.accountName.takeIf { showAccount && it.isNotBlank() },
        side.categoryName.ifBlank { MatchCopy.NO_CATEGORY },
        side.note
            .lineSequence()
            .firstOrNull()
            ?.ifBlank { null },
    ).joinToString(" · ")

/** The accent rule between the two sides, with why they look like one purchase. */
@Composable
private fun MatchLink(pair: MatchPair) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(22.dp).background(Metro.accent.fill))
        MetroText(
            listOf(MatchCopy.SAME_AMOUNT, MatchCopy.apart(pair.daysApart), MatchCopy.SAME_ACCOUNT).joinToString(" · "),
            Metro.typography.caption,
            Modifier.padding(start = 10.dp),
            color = Metro.colors.secondary,
            maxLines = 1,
        )
    }
}

@Composable
internal fun MatchButtons(
    pair: MatchPair,
    onMatch: () -> Unit,
    onNotAMatch: () -> Unit,
) {
    MetroButtonGrid(Modifier.padding(top = 12.dp)) {
        AccentButton(MatchCopy.MATCH, Modifier.testTag("match-${pair.key}"), onMatch)
        MetroButton(MatchCopy.NOT_A_MATCH, Modifier.testTag("not-a-match-${pair.key}"), onNotAMatch)
    }
}
