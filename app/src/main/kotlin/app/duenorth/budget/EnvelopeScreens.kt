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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryAutomations
import app.duenorth.budget.core.CategoryMonthPage
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.EnvelopeCopy
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MoneyMath
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.ReviewTransactionRow
import app.duenorth.budget.core.monthLabel
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroSegmentedToggle
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.PillCorner
import app.duenorth.budget.design.components.layerClip
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.mobile10Fill
import java.time.YearMonth
import kotlin.math.floor

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
    // Desktop's budget automations, when they own the category: shown in place of the note's template lines.
    automations: CategoryAutomations? = null,
    automationsEditable: Boolean = false,
    templateAmounts: List<Long?> = emptyList(),
    onOpenTemplate: (Int) -> Unit = {},
    onMoveTemplate: (Int, Int) -> Unit = { _, _ -> },
    // The category's transactions this month, newest first; recent shows the top few once they are read.
    recent: CategoryMonthPage? = null,
    onSeeAll: () -> Unit = {},
    // A tap on a recent transaction opens its account's register at that transaction.
    onOpenTransaction: (ReviewTransactionRow) -> Unit = {},
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
        RecentSpending(recent?.takeIf { it.categoryId == categoryId && it.month == shell.month }, onSeeAll, onOpenTransaction)
        Spacer(Modifier.height(SECTION_GAP))
        BudgetedField(
            amount = amount,
            onAmount = { amount = it },
            carriedMinor = category.availableMinor - category.budgetedMinor - category.spentMinor,
            template = template?.let { prefs.editSignedAmount(it, shell.currency.decimals) },
            currency = shell.currency,
        )
        if (error != null) WarningText(error, Modifier.padding(top = 8.dp))
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
        if (automations != null) {
            AutomationTiles(
                automations,
                templateAmounts,
                shell.currency,
                Modifier.padding(top = 12.dp),
                editable = automationsEditable,
                onOpen = onOpenTemplate,
            )
        } else if (note != null) {
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
    onOpen: (ReviewTransactionRow) -> Unit,
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
                    .metroPress({ onOpen(row) })
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
            AmountField(amount, onAmount, "budgeted", Modifier.weight(1f).testTag("category-budget-amount"), decimals = currency.decimals)
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
        AmountField(
            amount,
            { amount = it },
            "amount",
            Modifier.padding(top = MetroDimens.Gutter).testTag("move-amount"),
            decimals = money.decimals,
        )
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
        if (error != null) WarningText(error)
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

/**
 * Covers an overspent category: where the money comes from, picked from to budget and every other category with
 * money available, and how much, which starts at what the category is short by. A source with less than that
 * offers all it has. The overspent category and the picked source show what they will have after the cover.
 */
@Composable
fun CoverCategoryScreen(
    shell: MonthShell,
    categoryId: String,
    error: String?,
    onSave: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    initialFrom: String = "",
) {
    val categories = shell.groups.flatMap { it.categories }
    val to = categories.first { it.id == categoryId }
    val prefs = LocalFormatPrefs.current
    val options = formatOptions()
    val money = shell.currency
    val envelope = shell.mode == BudgetMode.ENVELOPE
    val short = (-to.availableMinor).coerceAtLeast(0L)

    fun available(id: String): Long =
        if (id == MOVE_TO_BUDGET) shell.headerMinor else categories.firstOrNull { it.id == id }?.availableMinor ?: 0L

    // What a pick puts in the amount box: all that is short, or all the source has when that is less.
    fun suggested(id: String): Long = if (id == MOVE_TO_BUDGET || available(id) >= short) short else available(id).coerceAtLeast(0L)
    var fromId by remember { mutableStateOf(initialFrom) }
    var amount by remember {
        mutableStateOf(
            prefs.editAmount(if (initialFrom.isBlank()) short else suggested(initialFrom), money.decimals),
        )
    }
    // Until the person types an amount, picking a source fits the amount to it.
    var typed by remember { mutableStateOf(false) }
    val moved = MoneyMath.evaluate(amount, prefs.numberFormat, money.decimals)?.takeIf { it > 0 } ?: 0L

    fun pick(id: String) {
        fromId = id
        if (!typed) amount = prefs.editAmount(suggested(id), money.decimals)
    }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("cover-screen"),
    ) {
        MetroText(EnvelopeCopy.coverTitle(to.name), Metro.typography.subheader, maxLines = 2)
        MetroText(
            "${EnvelopeCopy.AVAILABLE} ${MoneyFormat.format(to.availableMinor + moved, money, options)}",
            Metro.typography.caption,
            Modifier.padding(top = 4.dp).testTag("cover-available"),
            color = if (to.availableMinor + moved < 0) Metro.colors.error else Metro.colors.secondary,
        )
        AmountField(
            amount,
            {
                amount = it
                typed = true
            },
            "amount",
            Modifier.padding(top = MetroDimens.Gutter).testTag("cover-amount"),
            decimals = money.decimals,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MetroDimens.Grid)) {
            if (short > 0) {
                QuickAmount("cover ${MoneyFormat.format(short, money, options)}", "cover-short") {
                    amount = prefs.editAmount(short, money.decimals)
                    typed = false
                }
            }
            val has = if (fromId.isBlank() || fromId == MOVE_TO_BUDGET) 0L else available(fromId)
            if (has in 1 until short) {
                QuickAmount("all ${MoneyFormat.format(has, money, options)}", "cover-all") {
                    amount = prefs.editAmount(has, money.decimals)
                    typed = false
                }
            }
        }
        if (error != null) WarningText(error)
        MetroText(EnvelopeCopy.COVER_FROM, Metro.typography.caption, Modifier.padding(top = 4.dp), color = Metro.colors.secondary)
        LazyColumn(Modifier.weight(1f).testTag("cover-sources")) {
            if (envelope) {
                item(key = MOVE_TO_BUDGET) {
                    MoveTarget(
                        name = shell.headerLabel,
                        available = MoneyFormat.format(shell.headerMinor - if (fromId == MOVE_TO_BUDGET) moved else 0L, money, options),
                        picked = fromId == MOVE_TO_BUDGET,
                        banded = false,
                        tag = "cover-from-$MOVE_TO_BUDGET",
                    ) { pick(MOVE_TO_BUDGET) }
                }
            }
            var band = 0
            shell.groups.forEach { group ->
                // Only categories with money to give, and never the one being covered.
                val sources = group.categories.filter { it.id != categoryId && it.availableMinor > 0 }
                if (sources.isEmpty()) return@forEach
                item(key = "group-${group.id}") {
                    MetroText(
                        group.name,
                        Metro.typography.groupHeader,
                        Modifier.padding(top = MetroDimens.Row, bottom = 4.dp),
                        color = Metro.colors.secondary,
                    )
                }
                sources.forEach { source ->
                    val banded = ledgerBanded(band++)
                    item(key = source.id) {
                        MoveTarget(
                            name = source.name,
                            available = MoneyFormat.format(source.availableMinor - if (fromId == source.id) moved else 0L, money, options),
                            picked = fromId == source.id,
                            banded = banded,
                            tag = "cover-from-${source.id}",
                        ) { pick(source.id) }
                    }
                }
            }
        }
        RememberFormSave { onSave(fromId, prefs.readAmount(amount, money.decimals)) }
        if (!formSaveOnBar()) {
            MetroButton("cover") { onSave(fromId, prefs.readAmount(amount, money.decimals)) }
        }
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
        AmountField(amount, { amount = it }, "amount for next month", decimals = shell.currency.decimals)
        if (error != null) WarningText(error)
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
    // While a month loads, the bar keeps its place as an empty track.
    val progress = if (showGoal) (if (figuresLoading()) 0f else category.goalProgress ?: 0f) else null
    val nameAir = ledgerNameAir(Metro.typography.body)
    // Overspent, the row is flagged: a red tint and stripe, the figure in red, and how far over under the name.
    // While a month loads, last month's figures would flag the wrong rows.
    val over = category.availableMinor < 0 && !figuresLoading()
    val budgeted = MoneyFormat.format(category.budgetedMinor, money, formatOptions)
    val spent = MoneyFormat.format(category.spentMinor, money, formatOptions)
    val available = MoneyFormat.format(category.availableMinor, money, formatOptions)
    val overLine =
        if (over) {
            val by = MoneyFormat.format(-category.availableMinor, money, formatOptions)
            // A tap opens the category's page, whose bar offers cover; tracking budgets have nothing to cover from.
            if (shell.mode == BudgetMode.TRACKING) EnvelopeCopy.overBy(by) else EnvelopeCopy.overByCover(by)
        } else {
            null
        }
    // The columns' headings sit above the list, out of TalkBack's reach, so the row names each figure itself.
    val spoken = Spoken.categoryLine(category.name, budgeted, spent, available, overLine, category.hidden)
    Column(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .then(
                if (over) {
                    Modifier.ledgerFlag(overspentTint, Metro.colors.errorFill)
                } else {
                    Modifier.ledgerFill(if (banded) ledgerBand else Color.Transparent)
                },
            ).then(if (onOpen != null) Modifier.rowPress({ onOpen(category.id) }) { category(category.id) } else Modifier)
            .testTag("category-${category.id}"),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = MetroDimens.TouchTarget).testTag("category-line-${category.id}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetroText(
                category.name,
                Metro.typography.body,
                Modifier
                    .weight(LEDGER_NAME)
                    .padding(end = MetroDimens.Gutter, top = nameAir, bottom = nameAir)
                    .testTag("category-name-${category.id}"),
                color = if (category.hidden) Metro.colors.secondary else Metro.colors.foreground,
                maxLines = 2,
            )
            LedgerFigure(budgeted, Metro.typography.body, Metro.colors.foreground, "category-budgeted-${category.id}")
            LedgerFigure(spent, Metro.typography.body, Metro.colors.foreground, "category-spent-${category.id}")
            LedgerFigure(
                available,
                if (over) Metro.typography.body.copy(fontWeight = FontWeight.SemiBold) else Metro.typography.body,
                if (over) Metro.colors.error else Metro.accent.text,
                "category-available-${category.id}",
            )
        }
        if (overLine != null) {
            MetroText(
                overLine,
                Metro.typography.caption,
                Modifier.padding(bottom = OVER_LINE_GAP).testTag("category-over-${category.id}"),
                color = Metro.colors.error,
            )
        }
        if (progress != null) GoalBar(progress, Modifier.padding(bottom = GOAL_BAR_GAP).testTag("category-goal-${category.id}"))
    }
}

/** The faint red behind an overspent row: the red swatch, light enough that the row's figures still read on it. */
internal val overspentTint: Color
    @Composable
    @ReadOnlyComposable
    get() = Metro.colors.errorFill.copy(alpha = if (Metro.colors.isDark) 0.24f else 0.09f)

private val OVER_LINE_GAP = 8.dp

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
            .layerClip(PillCorner)
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

/** A money figure in a ledger column: a [LedgerCell] that turns placeholder while a month loads. */
@Composable
internal fun RowScope.LedgerFigure(
    text: String,
    style: TextStyle,
    color: Color,
    tag: String,
) {
    FigureText(
        text,
        style.copy(textAlign = TextAlign.End),
        Modifier.weight(LEDGER_FIGURE).testTag(tag),
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * Air above and below a ledger row's name: what centering one line in a touch-target row leaves each side.
 * Padding the name by it keeps that air when the name wraps, where centering alone would squeeze it to nothing.
 */
@Composable
internal fun ledgerNameAir(style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(style, density) {
        val line = measurer.measure("Ag", style, maxLines = 1).size.height
        // Whole pixels, so the padded line never comes out a pixel taller than the touch target it fills.
        val air = floor((with(density) { MetroDimens.TouchTarget.toPx() } - line) / 2f).coerceAtLeast(0f)
        with(density) { air.toDp() }
    }
}

internal const val LEDGER_NAME = 1.8f
internal const val LEDGER_FIGURE = 1f
