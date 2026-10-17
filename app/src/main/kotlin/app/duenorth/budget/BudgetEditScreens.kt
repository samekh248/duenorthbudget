package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MoneyMath
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.monthLabel
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

object BudgetEditCopy {
    const val EDIT = "edit budget"
    const val EDITING = "editing"
    const val DONE = "done"
    const val CANCEL = "cancel"
    const val COVER = "cover"
    const val ALL = "all"
    const val AVAILABLE = "available"
}

/** What the edit mode's boxes and keypad do. */
class BudgetEditActions(
    val onFocus: (String) -> Unit = {},
    val onKey: (String) -> Unit = {},
    val onCover: () -> Unit = {},
    val onAll: () -> Unit = {},
    val onDone: () -> Unit = {},
    /** A tap on a category's available figure: move money from it. */
    val onAvailable: (String) -> Unit = {},
    /** A tap on the pinned to budget figure; null when it does nothing. */
    val onToBudget: (() -> Unit)? = null,
)

/**
 * The budget item in edit mode: "editing october 2026", to budget pinned above the list while it holds
 * money, and the budgeted column as boxes. Tapping a box opens the keypad in the app bar's place; to budget
 * and the row's available follow what is typed before it is saved.
 */
@Composable
fun BudgetEditSection(
    shell: MonthShell,
    edit: BudgetEdit,
    actions: BudgetEditActions,
    onListGesture: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    formatOptions: FormatOptions = formatOptions(),
) {
    val prefs = LocalFormatPrefs.current
    val money = shell.currency
    val focused = edit.categoryId?.let { id -> shell.groups.flatMap { it.categories }.firstOrNull { it.id == id } }
    val typed = focused?.let { MoneyMath.evaluate(edit.text, prefs.numberFormat, money.decimals, it.budgetedMinor) }
    val delta = if (focused != null && typed != null) typed - focused.budgetedMinor else 0L
    val toBudget = if (shell.mode == BudgetMode.ENVELOPE) shell.headerMinor - delta else shell.headerMinor
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onListGesture(it) }
    }
    // The keypad takes the bottom of the screen; keep the open row in view above it, also as the list
    // shrinks to make room.
    LaunchedEffect(list, focused?.id) {
        val key = focused?.let { "edit-${it.id}" } ?: return@LaunchedEffect
        // Only when the box opens or the list's height changes, so scrolling by hand is left alone.
        snapshotFlow { list.layoutInfo.viewportEndOffset }.collect {
            val info = list.layoutInfo
            val row = info.visibleItemsInfo.firstOrNull { it.key == key }
            when {
                row == null -> {
                    val index = listIndex(shell, focused.id)
                    if (index >= 0) list.scrollToItem(index)
                }
                row.offset + row.size > info.viewportEndOffset -> list.scrollBy((row.offset + row.size - info.viewportEndOffset).toFloat())
                row.offset < info.viewportStartOffset -> list.scrollBy((row.offset - info.viewportStartOffset).toFloat())
            }
        }
    }
    Column(modifier.fillMaxSize().testTag("budget-edit")) {
        Row(
            Modifier.fillMaxWidth().padding(start = MetroDimens.Gutter, end = MetroDimens.Gutter, bottom = MetroDimens.Row),
            verticalAlignment = Alignment.Bottom,
        ) {
            MetroText("${BudgetEditCopy.EDITING} ", Metro.typography.subheader, color = Metro.accent.text)
            MetroText(monthLabel(shell.month), Metro.typography.subheader, maxLines = 1, softWrap = false)
        }
        // Nothing left to budget, nothing to keep in view.
        if (toBudget != 0L || shell.bufferedMinor != 0L) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = MetroDimens.Gutter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                if (toBudget != 0L) {
                    BudgetTotal(
                        label = shell.headerLabel,
                        amount = MoneyFormat.format(toBudget, money, formatOptions),
                        amountColor = Metro.accent.text,
                        amountTag = "edit-to-budget",
                        modifier = actions.onToBudget?.let { Modifier.metroPress(onClick = it) } ?: Modifier,
                    )
                } else {
                    Spacer(Modifier)
                }
                if (shell.bufferedMinor != 0L) {
                    BudgetTotal(
                        label = "held",
                        amount = MoneyFormat.format(shell.bufferedMinor, money, formatOptions),
                        amountColor = Metro.colors.secondary,
                        amountTag = "edit-held",
                        alignEnd = true,
                    )
                }
            }
        }
        if (error != null) {
            MonthNotice(error, Modifier.padding(horizontal = MetroDimens.Gutter, vertical = 4.dp), color = Metro.accent.text)
        }
        Row(
            Modifier.fillMaxWidth().padding(start = MetroDimens.Gutter, end = MetroDimens.Gutter, top = MetroDimens.Row),
            verticalAlignment = Alignment.Bottom,
        ) {
            Spacer(Modifier.weight(LEDGER_NAME))
            EditHeading("budgeted", BOX_WEIGHT)
            EditHeading(BudgetEditCopy.AVAILABLE, LEDGER_FIGURE)
        }
        val position =
            shell.groups
                .flatMap { g -> g.categories.map { it.id } }
                .withIndex()
                .associate { it.value to it.index }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal = MetroDimens.Gutter), state = list) {
            shell.groups.forEach { group ->
                item(key = group.id) {
                    val shift = if (group.categories.any { it.id == focused?.id }) delta else 0L
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .padding(top = MetroDimens.Row)
                            .testTag("edit-group-${group.id}"),
                        verticalAlignment = Alignment.Top,
                    ) {
                        MetroText(
                            group.name,
                            Metro.typography.groupHeader,
                            Modifier.weight(1f).padding(end = 12.dp),
                            color = if (group.hidden) Metro.colors.secondary else Metro.colors.foreground,
                            maxLines = 2,
                        )
                        MetroText(MoneyFormat.format(group.availableMinor + shift, money, formatOptions), Metro.typography.groupHeader)
                    }
                }
                items(group.categories, key = { "edit-${it.id}" }) { category ->
                    val open = category.id == focused?.id
                    EditLine(
                        category = category,
                        boxText =
                            when {
                                !open -> prefs.editSignedAmount(category.budgetedMinor, money.decimals)
                                else -> edit.text
                            },
                        hint = if (open && edit.text.isEmpty()) prefs.editSignedAmount(category.budgetedMinor, money.decimals) else null,
                        available = MoneyFormat.format(category.availableMinor + if (open) delta else 0L, money, formatOptions),
                        open = open,
                        banded = ledgerBanded(position.getValue(category.id)),
                        onFocus = { actions.onFocus(category.id) },
                        onAvailable = { actions.onAvailable(category.id) },
                    )
                }
                item(key = "edit-gap-${group.id}") { Spacer(Modifier.height(MetroDimens.Row)) }
            }
        }
        if (focused != null) {
            BudgetKeypad(
                category = focused,
                available = MoneyFormat.format(focused.availableMinor + delta, money, formatOptions),
                decimalPoint = MoneyMath.decimalPoint(prefs.numberFormat),
                canCover = focused.availableMinor < 0,
                canAll = shell.mode == BudgetMode.ENVELOPE && shell.headerMinor > 0,
                actions = actions,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.EditHeading(
    label: String,
    weight: Float,
) {
    MetroText(
        label,
        Metro.typography.caption.copy(textAlign = TextAlign.End),
        Modifier.weight(weight),
        color = Metro.colors.secondary,
        maxLines = 1,
        softWrap = false,
    )
}

/** Where [categoryId]'s row sits in the edit list: each group is a heading, its rows, then a gap. */
private fun listIndex(
    shell: MonthShell,
    categoryId: String,
): Int {
    var index = 0
    shell.groups.forEach { group ->
        index++
        val at = group.categories.indexOfFirst { it.id == categoryId }
        if (at >= 0) return index + at
        index += group.categories.size + 1
    }
    return -1
}

@Composable
private fun EditLine(
    category: CategoryRow,
    boxText: String,
    hint: String?,
    available: String,
    open: Boolean,
    banded: Boolean,
    onFocus: () -> Unit,
    onAvailable: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .ledgerFill(if (banded) ledgerBand else Color.Transparent)
            .testTag("edit-category-${category.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetroText(
            category.name,
            Metro.typography.body,
            Modifier.weight(LEDGER_NAME).padding(end = MetroDimens.Gutter),
            color = if (category.hidden) Metro.colors.secondary else Metro.colors.foreground,
            maxLines = 2,
        )
        // Windows Phone text boxes: filled at rest, white with an accent edge while typing.
        Box(
            Modifier
                .weight(BOX_WEIGHT)
                .height(36.dp)
                .background(if (open) Color.White else Metro.colors.chrome)
                .then(if (open) Modifier.border(2.dp, Metro.accent.fill) else Modifier)
                .metroPress(onClick = onFocus)
                .padding(horizontal = 8.dp)
                .testTag("edit-box-${category.id}"),
            contentAlignment = Alignment.CenterEnd,
        ) {
            MetroText(
                when {
                    hint != null -> hint
                    open -> "$boxText|"
                    else -> boxText
                },
                Metro.typography.body.copy(textAlign = TextAlign.End),
                color =
                    when {
                        hint != null -> Color(0xFF8F8F8F)
                        open -> Color.Black
                        else -> Metro.colors.foreground
                    },
                maxLines = 1,
                softWrap = false,
            )
        }
        // The available figure moves money from the category to another one or back to to budget.
        Box(
            Modifier
                .weight(LEDGER_FIGURE)
                .heightIn(min = MetroDimens.TouchTarget)
                .metroPress(onClick = onAvailable)
                .testTag("edit-available-${category.id}"),
            contentAlignment = Alignment.CenterEnd,
        ) {
            MetroText(
                available,
                Metro.typography.body.copy(textAlign = TextAlign.End),
                color = Metro.accent.text,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * The app's own number pad, in the app bar's place while a box is open: digits, the four operators, = to
 * work a sum out, cover (budget what's overspent) and all (everything left in to budget), and done.
 */
@Composable
private fun BudgetKeypad(
    category: CategoryRow,
    available: String,
    decimalPoint: String,
    canCover: Boolean,
    canAll: Boolean,
    actions: BudgetEditActions,
) {
    val rows =
        listOf(
            listOf("1", "2", "3", "+", BudgetKeys.BACKSPACE),
            listOf("4", "5", "6", "−", BudgetEditCopy.COVER),
            listOf("7", "8", "9", "×", BudgetEditCopy.ALL),
            listOf(decimalPoint, "0", BudgetKeys.EQUALS, "÷", BudgetEditCopy.DONE),
        )
    Column(
        Modifier
            .fillMaxWidth()
            .background(Metro.colors.chrome)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(6.dp)
            .testTag("budget-keypad"),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
            MetroText(category.name, Metro.typography.body, Modifier.weight(1f), maxLines = 1, softWrap = false)
            MetroText("${BudgetEditCopy.AVAILABLE} $available", Metro.typography.caption, color = Metro.colors.secondary)
        }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    val enabled =
                        when (key) {
                            BudgetEditCopy.COVER -> canCover
                            BudgetEditCopy.ALL -> canAll
                            else -> true
                        }
                    val accent = key == BudgetEditCopy.DONE
                    val run: () -> Unit =
                        when (key) {
                            BudgetEditCopy.COVER -> actions.onCover
                            BudgetEditCopy.ALL -> actions.onAll
                            BudgetEditCopy.DONE -> actions.onDone
                            "−" -> ({ actions.onKey("-") })
                            else -> ({ actions.onKey(key) })
                        }
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(3.dp)
                            .height(48.dp)
                            .background(if (accent) Metro.accent.fill else Metro.colors.background)
                            .then(if (enabled) Modifier.metroPress(onClick = run) else Modifier)
                            .testTag("key-$key"),
                        contentAlignment = Alignment.Center,
                    ) {
                        MetroText(
                            key,
                            if (key.length > 1) Metro.typography.body else Metro.typography.control,
                            color =
                                when {
                                    accent -> Metro.accent.onFill
                                    !enabled -> Metro.colors.secondary
                                    else -> Metro.colors.foreground
                                },
                        )
                    }
                }
            }
        }
    }
}

private const val BOX_WEIGHT = 1.3f
