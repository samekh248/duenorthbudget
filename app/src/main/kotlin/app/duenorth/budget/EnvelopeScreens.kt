package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryMonthPage
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.EnvelopeCopy
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MoneyMath
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.monthLabel
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroSegmentedToggle
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.mobile10Fill
import java.time.YearMonth

@Composable
fun CategoryBudgetScreen(
    shell: MonthShell,
    categoryId: String,
    error: String?,
    onSave: (String) -> Unit,
    onMove: () -> Unit,
    onToggleCarryover: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    // Off where the page heads itself with the name over the month control.
    showName: Boolean = true,
    note: String? = null,
    onSaveNote: (String) -> Unit = {},
    templateAmounts: List<Long?> = emptyList(),
    onOpenTemplate: (Int) -> Unit = {},
    onMoveTemplate: (Int, Int) -> Unit = { _, _ -> },
    // The category's transactions this month, newest first; recent shows the top few once they are read.
    recent: CategoryMonthPage? = null,
    onSeeAll: () -> Unit = {},
) {
    val category = shell.groups.flatMap { it.categories }.first { it.id == categoryId }
    val prefs = LocalFormatPrefs.current
    // Signed, so a negative budget (money moved out) reads as one.
    val initial = prefs.editSignedAmount(category.budgetedMinor, shell.currency.decimals)
    var amount by remember(category, prefs) { mutableStateOf(initial) }
    // A changed note waiting for the bar's save; null while the note is as saved.
    var noteDraft by remember(note) { mutableStateOf<String?>(null) }
    // "+500" typed over the amount adds to it. This box starts filled in, so a leading minus is a negative
    // amount ("-150"), not "take 150 off".
    val base = if (amount.trimStart().startsWith("-")) null else category.budgetedMinor
    // What the templates ask this month, else the goal they last wrote; a #goal targets the balance, not the budget.
    val template = templateAmounts.filterNotNull().takeIf { it.isNotEmpty() }?.sum() ?: category.goalMinor?.takeUnless { category.longGoal }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(MetroDimens.Gutter),
    ) {
        if (showName) {
            MetroText(category.name, Metro.typography.header)
            MetroText(monthLabel(shell.month), Metro.typography.caption, color = Metro.colors.secondary)
            Spacer(Modifier.height(MetroDimens.Grid))
        }
        // Only while budget templates are on (spec 010 US5): the note's own text heads the page, its template lines
        // show as tiles with the budget.
        if (note != null) {
            CategoryNoteText(note, onSaveNote, onDraft = { noteDraft = it })
            Spacer(Modifier.height(SECTION_GAP))
        }
        AvailableBar(category, template, shell.currency)
        Spacer(Modifier.height(SECTION_GAP))
        RecentSpending(recent?.takeIf { it.categoryId == categoryId && it.month == shell.month }, onSeeAll)
        Spacer(Modifier.height(SECTION_GAP))
        BudgetedField(
            amount = amount,
            onAmount = { amount = it },
            carriedMinor = category.availableMinor - category.budgetedMinor - category.spentMinor,
            template = template?.let { prefs.editSignedAmount(it, shell.currency.decimals) },
            currency = shell.currency,
        )
        if (error != null) MetroText(error, Metro.typography.body, Modifier.padding(top = 8.dp), color = Metro.accent.text)
        // One save on the bar keeps both a changed note and the amount.
        RememberFormSave {
            noteDraft?.let(onSaveNote)
            onSave(prefs.readAmount(amount, shell.currency.decimals, base = base))
        }
        // In the app the bar carries save and move; the buttons are for the page on its own.
        if (!formSaveOnBar()) {
            MetroButtonGrid(Modifier.padding(top = MetroDimens.Gutter)) {
                MetroButton("save") { onSave(prefs.readAmount(amount, shell.currency.decimals, base = base)) }
                MetroButton(EnvelopeCopy.MOVE, onClick = onMove)
            }
        }
        if (note != null) {
            TemplateTiles(
                note,
                templateAmounts,
                shell.currency,
                onOpenTemplate,
                onMoveTemplate,
                Modifier.padding(top = 12.dp),
            )
        }
        Spacer(Modifier.height(SECTION_GAP))
        RolloverSwitch(category.carryover, shell.month, onToggleCarryover)
    }
}

/** Air after each of the category page's sections; the budget and its templates sit closer, as one. */
private val SECTION_GAP = 48.dp

/**
 * What is available, with a bar of the month's money: spent in the foreground, what is left in the accent, and a tick
 * where the template's amount falls. Overspent, the over part is red and the legend names how far over.
 */
@Composable
private fun AvailableBar(
    category: CategoryRow,
    template: Long?,
    currency: CurrencySpec,
) {
    val options = formatOptions()
    val spent = (-category.spentMinor).coerceAtLeast(0)
    val left = category.availableMinor.coerceAtLeast(0)
    val over = (-category.availableMinor).coerceAtLeast(0)
    val total = (spent + left + over).coerceAtLeast(1)
    val scale = maxOf(total, template ?: 0)
    val track = Metro.colors.secondary.copy(alpha = 0.25f)
    // Windows 10 Mobile draws a thin progress bar, spending in the grey ink instead of black.
    val mobile10 = Metro.style.mobile10
    val barHeight = if (mobile10) 4.dp else BAR_HEIGHT
    val spentInk = if (mobile10) Metro.colors.secondary else Metro.colors.foreground
    Column(Modifier.fillMaxWidth().testTag("category-available")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            MetroText(EnvelopeCopy.AVAILABLE, Metro.typography.caption, Modifier.weight(1f), color = Metro.colors.secondary)
            MetroText(
                MoneyFormat.format(category.availableMinor, currency, options),
                Metro.typography.subheader,
                Modifier.testTag("category-available-amount"),
                color = if (category.availableMinor < 0) Metro.colors.error else Metro.colors.foreground,
            )
        }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(BAR_HEIGHT + 8.dp), contentAlignment = Alignment.CenterStart) {
            Row(Modifier.fillMaxWidth().height(barHeight).background(track)) {
                if (spent + left + over == 0L) return@Row
                if (spent > 0) Box(Modifier.weight(spent.toFloat() / scale).fillMaxHeight().background(spentInk))
                if (left > 0) Box(Modifier.weight(left.toFloat() / scale).fillMaxHeight().background(Metro.accent.fill))
                if (over > 0) Box(Modifier.weight(over.toFloat() / scale).fillMaxHeight().background(Metro.colors.error))
                if (scale > total) Spacer(Modifier.weight((scale - total).toFloat() / scale))
            }
            if (template != null && template > 0) {
                val at = (template.toFloat() / scale).coerceIn(0f, 1f)
                Row(Modifier.fillMaxSize()) {
                    if (at > 0f) Spacer(Modifier.weight(at))
                    Box(Modifier.width(2.dp).fillMaxHeight().background(Metro.accent.text))
                    if (at < 1f) Spacer(Modifier.weight(1f - at))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendKey(spentInk, "${EnvelopeCopy.SPENT} ${MoneyFormat.format(spent, currency, options)}")
            if (category.availableMinor < 0) {
                LegendKey(Metro.colors.error, "${EnvelopeCopy.OVER} ${MoneyFormat.format(over, currency, options)}")
            } else {
                LegendKey(Metro.accent.fill, "${EnvelopeCopy.LEFT} ${MoneyFormat.format(left, currency, options)}")
            }
            if (template != null && template > 0) {
                LegendKey(Metro.accent.text, "${EnvelopeCopy.TEMPLATE} ${MoneyFormat.format(template, currency, options)}", tick = true)
            }
        }
    }
}

private val BAR_HEIGHT = 8.dp

@Composable
private fun LegendKey(
    color: Color,
    text: String,
    tick: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(if (tick) 2.dp else 10.dp).height(10.dp).background(color))
        MetroText(text, Metro.typography.caption, Modifier.padding(start = 5.dp), color = Metro.colors.secondary, maxLines = 1)
    }
}

/** The category's last few transactions this month, and a way to the whole month's list. */
@Composable
private fun RecentSpending(
    page: CategoryMonthPage?,
    onSeeAll: () -> Unit,
) {
    val options = formatOptions()
    val dates = dateFormat()
    Column(Modifier.fillMaxWidth().testTag("category-recent")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            MetroText(EnvelopeCopy.RECENT, Metro.typography.subheader, Modifier.weight(1f))
            if (page != null && page.rows.isNotEmpty()) {
                MetroText("${page.rows.size} ${EnvelopeCopy.THIS_MONTH}", Metro.typography.caption, color = Metro.colors.secondary)
            }
        }
        if (page == null) return@Column
        if (page.rows.isEmpty()) {
            MetroText(EnvelopeCopy.NO_RECENT, Metro.typography.body, Modifier.padding(top = 8.dp), color = Metro.colors.secondary)
            return@Column
        }
        page.rows.take(RECENT_ROWS).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .testTag("category-recent-${row.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetroText(
                    RegisterEntry.registerDateLabel(row.date, dates),
                    Metro.typography.caption,
                    Modifier.widthIn(min = 56.dp).padding(end = 12.dp),
                    color = Metro.colors.secondary,
                    maxLines = 1,
                )
                MetroText(row.payee, Metro.typography.body, Modifier.weight(1f).padding(end = 12.dp), maxLines = 1)
                MetroText(MoneyFormat.format(row.amountMinor, page.currency, options), Metro.typography.body, maxLines = 1)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Metro.colors.secondary.copy(alpha = 0.25f)))
        }
        MetroText(
            "${EnvelopeCopy.SEE_ALL} ›",
            Metro.typography.body,
            Modifier
                .padding(top = 4.dp)
                .heightIn(min = 40.dp)
                .metroPress(onSeeAll)
                .padding(vertical = 9.dp)
                .testTag("category-see-all"),
            color = Metro.accent.text,
        )
    }
}

private const val RECENT_ROWS = 3

/**
 * The month's budget, with what carried in from last month beside its label, and a key that fills the box with
 * what the templates ask; once the box holds that amount the key stays lit to say so.
 */
@Composable
private fun BudgetedField(
    amount: String,
    onAmount: (String) -> Unit,
    carriedMinor: Long,
    template: String?,
    currency: CurrencySpec,
) {
    val label =
        if (carriedMinor == 0L) {
            EnvelopeCopy.BUDGETED
        } else {
            "${EnvelopeCopy.BUDGETED} · ${MoneyFormat.format(carriedMinor, currency, formatOptions())} ${EnvelopeCopy.CARRIED_IN}"
        }
    Column(Modifier.fillMaxWidth()) {
        MetroText(label, Metro.typography.caption, Modifier.padding(bottom = MetroDimens.FieldLabelGap), color = Metro.colors.secondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MetroField(amount, onAmount, "budgeted", Modifier.weight(1f).testTag("category-budget-amount"))
            if (template != null) {
                val filled = amount.trim() == template
                Box(
                    Modifier
                        .heightIn(min = MetroDimens.TouchTarget)
                        .then(
                            when {
                                filled -> Modifier.background(Metro.accent.fill)
                                Metro.style.mobile10 -> Modifier.background(Metro.colors.mobile10Fill)
                                else -> Modifier.border(2.dp, Metro.colors.foreground)
                            },
                        ).metroPress { onAmount(template) }
                        .padding(horizontal = 12.dp)
                        .testTag("category-fill-template"),
                    contentAlignment = Alignment.Center,
                ) {
                    MetroText(
                        if (filled) "✓ ${EnvelopeCopy.FILLS_TEMPLATE}" else EnvelopeCopy.FILL_TEMPLATE,
                        Metro.typography.body,
                        color = if (filled) Metro.accent.onFill else Metro.colors.foreground,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Rollover overspending as an off/on pair, with a line on what it does. */
@Composable
private fun RolloverSwitch(
    on: Boolean,
    month: YearMonth,
    onChange: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Metro.colors.secondary.copy(alpha = 0.25f)))
        Row(Modifier.fillMaxWidth().padding(vertical = MetroDimens.Row), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                MetroText(EnvelopeCopy.ROLLOVER, Metro.typography.body)
                MetroText(EnvelopeCopy.rolloverHint(month), Metro.typography.caption, color = Metro.colors.secondary)
            }
            MetroSegmentedToggle(
                first = ExperimentalCopy.OFF,
                second = ExperimentalCopy.ON,
                firstSelected = !on,
                onSelect = { first -> if (first == on) onChange(!first) },
                tag = "category-rollover",
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Metro.colors.secondary.copy(alpha = 0.25f)))
    }
}

/** The to-budget pick on the move screen, which moves money back out of the category. */
const val MOVE_TO_BUDGET = "to-budget"

/**
 * Moves money from one category: the amount (sums work, and all takes everything available), then where it
 * goes, picked from to budget and every other category with what each has available. Once an amount reads,
 * the source and the picked destination show what they will have after the move.
 */
@Composable
fun MoveCategoryScreen(
    shell: MonthShell,
    fromCategoryId: String,
    error: String?,
    onSave: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    initialTo: String = "",
    initialAmount: String = "",
) {
    val from = shell.groups.flatMap { it.categories }.first { it.id == fromCategoryId }
    val prefs = LocalFormatPrefs.current
    val options = formatOptions()
    val money = shell.currency
    var toId by remember { mutableStateOf(initialTo) }
    var amount by remember { mutableStateOf(initialAmount) }
    val moved = MoneyMath.evaluate(amount, prefs.numberFormat, money.decimals)?.takeIf { it > 0 } ?: 0L
    val envelope = shell.mode == BudgetMode.ENVELOPE
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("move-screen"),
    ) {
        MetroText("${EnvelopeCopy.MOVE} from ${from.name}", Metro.typography.subheader, maxLines = 2)
        MetroText(
            "available ${MoneyFormat.format(from.availableMinor - moved, money, options)}",
            Metro.typography.caption,
            Modifier.padding(top = 4.dp).testTag("move-from-available"),
            color = Metro.colors.secondary,
        )
        MetroField(amount, { amount = it }, "amount", Modifier.padding(top = MetroDimens.Gutter).testTag("move-amount"))
        // Quick picks: everything the category has, or what the picked category is overspent by.
        val target = shell.groups.flatMap { it.categories }.firstOrNull { it.id == toId }
        val cover =
            target
                ?.availableMinor
                ?.takeIf { it < 0 }
                ?.let { minOf(-it, from.availableMinor) }
                ?.takeIf { it > 0 }
        Row(horizontalArrangement = Arrangement.spacedBy(MetroDimens.Grid)) {
            if (from.availableMinor > 0) {
                QuickAmount("all ${MoneyFormat.format(from.availableMinor, money, options)}", "move-all") {
                    amount = prefs.editAmount(from.availableMinor, money.decimals)
                }
            }
            if (cover != null) {
                QuickAmount("cover ${MoneyFormat.format(cover, money, options)}", "move-cover") {
                    amount = prefs.editAmount(cover, money.decimals)
                }
            }
        }
        if (error != null) MetroText(error, Metro.typography.body, color = Metro.accent.text)
        MetroText("to", Metro.typography.caption, Modifier.padding(top = 4.dp), color = Metro.colors.secondary)
        LazyColumn(Modifier.weight(1f)) {
            if (envelope) {
                item(key = MOVE_TO_BUDGET) {
                    MoveTarget(
                        name = shell.headerLabel,
                        available = MoneyFormat.format(shell.headerMinor + if (toId == MOVE_TO_BUDGET) moved else 0L, money, options),
                        picked = toId == MOVE_TO_BUDGET,
                        banded = false,
                        tag = "move-to-$MOVE_TO_BUDGET",
                    ) { toId = MOVE_TO_BUDGET }
                }
            }
            var band = 0
            shell.groups.forEach { group ->
                val targets = group.categories.filter { it.id != fromCategoryId }
                if (targets.isEmpty()) return@forEach
                item(key = "group-${group.id}") {
                    MetroText(
                        group.name,
                        Metro.typography.groupHeader,
                        Modifier.padding(top = MetroDimens.Row, bottom = 4.dp),
                        color = Metro.colors.secondary,
                    )
                }
                targets.forEach { target ->
                    val banded = ledgerBanded(band++)
                    item(key = target.id) {
                        MoveTarget(
                            name = target.name,
                            available = MoneyFormat.format(target.availableMinor + if (toId == target.id) moved else 0L, money, options),
                            picked = toId == target.id,
                            banded = banded,
                            tag = "move-to-${target.id}",
                        ) { toId = target.id }
                    }
                }
            }
        }
        RememberFormSave { onSave(toId, prefs.readAmount(amount, money.decimals)) }
        if (!formSaveOnBar()) {
            MetroButton("move") { onSave(toId, prefs.readAmount(amount, money.decimals)) }
        }
    }
}

@Composable
private fun QuickAmount(
    label: String,
    tag: String,
    onPick: () -> Unit,
) {
    MetroText(
        label,
        Metro.typography.body,
        Modifier
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick = onPick)
            .padding(vertical = MetroDimens.Row)
            .testTag(tag),
        color = Metro.accent.text,
    )
}

@Composable
private fun MoveTarget(
    name: String,
    available: String,
    picked: Boolean,
    banded: Boolean,
    tag: String,
    onPick: () -> Unit,
) {
    val ink = if (picked) Metro.accent.onFill else Metro.colors.foreground
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .ledgerFill(
                if (picked) {
                    Metro.accent.fill
                } else if (banded) {
                    ledgerBand
                } else {
                    Color.Transparent
                },
            ).metroPress(onClick = onPick)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetroText(name, Metro.typography.body, Modifier.weight(1f).padding(end = MetroDimens.Gutter), color = ink, maxLines = 2)
        MetroText(available, Metro.typography.body, color = if (picked) ink else Metro.accent.text, maxLines = 1, softWrap = false)
    }
}

@Composable
fun HoldMonthScreen(
    shell: MonthShell,
    error: String?,
    onSave: (String) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = LocalFormatPrefs.current
    var amount by remember { mutableStateOf("") }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText(EnvelopeCopy.HOLD, Metro.typography.header)
        MetroText(
            "to budget ${MoneyFormat.format(shell.headerMinor, shell.currency, formatOptions())}",
            Metro.typography.subheader,
        )
        if (shell.bufferedMinor != 0L) {
            MetroText(
                "held ${MoneyFormat.format(shell.bufferedMinor, shell.currency, formatOptions())}",
                Metro.typography.caption,
                color = Metro.colors.secondary,
            )
        }
        MetroField(amount, { amount = it }, "amount for next month")
        if (error != null) MetroText(error, Metro.typography.body, color = Metro.accent.text)
        RememberFormSave { onSave(prefs.readAmount(amount, shell.currency.decimals)) }
        MetroButtonGrid {
            if (!formSaveOnBar()) {
                MetroButton(EnvelopeCopy.HOLD) { onSave(prefs.readAmount(amount, shell.currency.decimals)) }
            }
            if (shell.bufferedMinor != 0L) {
                MetroButton(EnvelopeCopy.RELEASE, onClick = onRelease)
            }
        }
    }
}

@Composable
fun CategoryLine(
    category: CategoryRow,
    shell: MonthShell,
    onOpen: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    formatOptions: FormatOptions = formatOptions(),
    banded: Boolean = false,
    /**
     * Goals only show while templates are on, as Actual only judges them then. Every row keeps a bar while they are,
     * an empty track until templates give the category a goal, so the rows line up.
     */
    showGoal: Boolean = LocalFormatPrefs.current.templatesOn,
) {
    val money = shell.currency
    val progress = if (showGoal) category.goalProgress ?: 0f else null
    Column(
        modifier
            .fillMaxWidth()
            .ledgerFill(if (banded) ledgerBand else Color.Transparent)
            .then(if (onOpen != null) Modifier.metroPress { onOpen(category.id) } else Modifier)
            .testTag("category-${category.id}"),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = MetroDimens.TouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetroText(
                category.name,
                Metro.typography.body,
                Modifier.weight(LEDGER_NAME).padding(end = MetroDimens.Gutter),
                color = if (category.hidden) Metro.colors.secondary else Metro.colors.foreground,
                maxLines = 2,
            )
            LedgerCell(
                MoneyFormat.format(category.budgetedMinor, money, formatOptions),
                Metro.typography.body,
                Metro.colors.foreground,
                "category-budgeted-${category.id}",
            )
            LedgerCell(
                MoneyFormat.format(category.spentMinor, money, formatOptions),
                Metro.typography.body,
                Metro.colors.foreground,
                "category-spent-${category.id}",
            )
            LedgerCell(
                MoneyFormat.format(category.availableMinor, money, formatOptions),
                Metro.typography.body,
                Metro.accent.text,
                "category-available-${category.id}",
            )
        }
        if (progress != null) GoalBar(progress, Modifier.padding(bottom = GOAL_BAR_GAP).testTag("category-goal-${category.id}"))
    }
}

/** A thin bar across the row: the accent fills as much of the goal as is funded, the rest is a faint track. */
@Composable
private fun GoalBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    // Mica rounds the bar's ends, as Windows 11 draws a progress bar.
    val layers = Metro.style.layers
    Row(
        modifier
            .fillMaxWidth()
            .height(if (layers) GOAL_BAR_HEIGHT + 1.dp else GOAL_BAR_HEIGHT)
            .then(if (layers) Modifier.clip(RoundedCornerShape(2.dp)) else Modifier)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) },
    ) {
        if (progress > 0f) Box(Modifier.weight(progress).fillMaxHeight().background(Metro.accent.fill))
        if (progress < 1f) Box(Modifier.weight(1f - progress).fillMaxHeight().background(Metro.colors.secondary.copy(alpha = 0.25f)))
    }
}

private val GOAL_BAR_HEIGHT = 3.dp
private val GOAL_BAR_GAP = 6.dp

@Composable
internal fun LedgerHeadings(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Spacer(Modifier.weight(LEDGER_NAME))
        LedgerCell("budgeted", Metro.typography.caption, Metro.colors.secondary, "ledger-budgeted")
        LedgerCell("spent", Metro.typography.caption, Metro.colors.secondary, "ledger-spent")
        LedgerCell("available", Metro.typography.caption, Metro.colors.secondary, "ledger-available")
    }
}

@Composable
internal fun RowScope.LedgerCell(
    text: String,
    style: TextStyle,
    color: Color,
    tag: String,
) {
    MetroText(
        text,
        style.copy(textAlign = TextAlign.End),
        Modifier.weight(LEDGER_FIGURE).testTag(tag),
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

internal const val LEDGER_NAME = 1.8f
internal const val LEDGER_FIGURE = 1f
