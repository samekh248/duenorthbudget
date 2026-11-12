package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroWeights

/**
 * The category balances pinned across the top of the assistant, as small chrome tiles: nothing at all with none
 * pinned, one row for up to three, and two rows for four to six (two by two for four, so no row holds a lone tile).
 * A tile wide enough sets its name and balance on one line; narrower ones stack the balance under the name. A tap
 * opens the category; a long-press shows its menu, where it unpins.
 */
@Composable
internal fun PinnedBalances(
    pinned: List<CategoryRow>,
    money: (Long) -> String,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pinned.isEmpty()) return
    val columns = PinnedLayout.columns(pinned.size)
    BoxWithConstraints(modifier.fillMaxWidth().testTag("assistant-pins")) {
        val tileWidth = (maxWidth - PinnedLayout.Gap * (columns - 1)) / columns
        val oneLine = tileWidth >= PinnedLayout.OneLineMin
        Column(verticalArrangement = Arrangement.spacedBy(PinnedLayout.Gap)) {
            pinned.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PinnedLayout.Gap)) {
                    row.forEach { category ->
                        PinnedTile(category, money, oneLine, Modifier.weight(1f)) { onOpen(category.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinnedTile(
    pin: CategoryRow,
    money: (Long) -> String,
    oneLine: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    val tile =
        modifier
            .background(Metro.colors.chrome)
            .rowPress(onOpen) { category(pin.id) }
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 10.dp, vertical = if (oneLine) 9.dp else 6.dp)
            .testTag("assistant-pin-${pin.id}")
    if (oneLine) {
        Row(tile, verticalAlignment = Alignment.CenterVertically) {
            PinName(pin.name, Modifier.weight(1f).padding(end = 8.dp))
            PinBalance(pin.availableMinor, money)
        }
    } else {
        Column(tile) {
            PinName(pin.name, Modifier.fillMaxWidth())
            PinBalance(pin.availableMinor, money)
        }
    }
}

@Composable
private fun PinName(
    name: String,
    modifier: Modifier,
) = MetroText(name, Metro.typography.caption, modifier, color = Metro.colors.secondary, maxLines = 1, softWrap = false)

/** The balance, red when overspent; a long figure in a narrow tile shrinks a little rather than cut off. */
@Composable
private fun PinBalance(
    availableMinor: Long,
    money: (Long) -> String,
) {
    val style = Metro.typography.body.copy(fontWeight = MetroWeights.Semibold)
    BasicText(
        money(availableMinor),
        style = style.copy(color = if (availableMinor < 0) Metro.colors.error else Metro.colors.foreground),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
    )
}

/** How the pinned tiles lay out for each count. */
internal object PinnedLayout {
    /** Space between tiles, the same both ways. */
    val Gap: Dp = 4.dp

    /** A tile at least this wide sets its name and balance on one line. */
    val OneLineMin: Dp = 150.dp

    /** Tiles across: everything in one row up to three, then rows of two for four and of three for five or six. */
    fun columns(count: Int): Int =
        when {
            count <= 3 -> count.coerceAtLeast(1)
            count == 4 -> 2
            else -> 3
        }
}
