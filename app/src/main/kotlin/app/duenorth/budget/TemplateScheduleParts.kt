package app.duenorth.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.ScheduleRepeat
import app.duenorth.budget.core.ScheduleSummary
import app.duenorth.budget.core.Template
import app.duenorth.budget.core.TemplateAdjustment
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** The words of a template's adjust: exactly, more or less, then by how much. */
object TemplateAdjustCopy {
    const val BUDGET = "budget"
    val WAYS = listOf("exactly that", "more", "less")
    const val MORE_BY = "more by"
    const val LESS_BY = "less by"
    const val NOT_ALL = "less than 100%, or it takes away the whole bill"
    const val TOO_MUCH = "1000% at most"
    const val NOT_A_NUMBER = "type a number above 0"

    /** Why a typed adjust saves nothing; null while it is fine or still empty. */
    fun problem(
        text: String,
        less: Boolean,
        percent: Boolean,
        bad: Boolean,
    ): String? {
        if (!bad || text.isBlank()) return null
        val n = text.trim().toBigDecimalOrNull()
        return when {
            percent && less && n != null && n >= BigDecimal(100) -> NOT_ALL
            percent && !less && n != null && n > BigDecimal(1000) -> TOO_MUCH
            else -> NOT_A_NUMBER
        }
    }
}

/** What the schedule form says about the picked schedule: its amount, how often, when next, and its sum. */
object ScheduleFacts {
    const val EACH_BILL = "each bill"
    const val THIS_MONTH = "budgets this month"
    const val SAVE_AHEAD = "save up ahead"
    const val ONLY_WHEN_DUE = "only in the month it's due"

    /** The bill's size, positive, in minor units. */
    fun base(info: ScheduleSummary): Long = abs(info.amountMinor)

    fun money(
        info: ScheduleSummary,
        currency: CurrencySpec,
        options: FormatOptions,
    ): String = MoneyFormat.format(base(info), currency, options)

    /** "about $140.00 · every month · next dec 3". */
    fun line(
        info: ScheduleSummary,
        currency: CurrencySpec,
        options: FormatOptions,
    ): String =
        listOfNotNull(
            (if (info.amountOp == "isapprox") "${ScheduleScreenCopy.ABOUT} " else "") + money(info, currency, options),
            ScheduleScreenCopy.every(info.repeat, info.interval),
            info.nextDate.takeIf { it > 0 }?.let { "${ScheduleScreenCopy.NEXT} ${RegisterEntry.registerDateLabel(it)}" },
        ).joinToString(" · ")

    /** Once, every year, or every two months and more: saving ahead and paying when due budget differently. */
    fun lessThanMonthly(info: ScheduleSummary): Boolean =
        when (info.repeat) {
            ScheduleRepeat.ONCE, ScheduleRepeat.YEARLY -> true
            ScheduleRepeat.MONTHLY -> info.interval >= 2
            ScheduleRepeat.DAILY, ScheduleRepeat.WEEKLY -> false
        }

    fun cadenceHeading(info: ScheduleSummary): String = "it comes ${ScheduleScreenCopy.every(info.repeat, info.interval)}, so"

    /** What [adjustment] adds to or takes from a bill of [base]; percents round to the cent. */
    fun change(
        base: Long,
        adjustment: TemplateAdjustment,
        currency: CurrencySpec,
    ): Long =
        if (adjustment.percent) {
            BigDecimal(base).multiply(adjustment.amount).divide(BigDecimal(100), 0, RoundingMode.HALF_UP).toLong()
        } else {
            TemplateWords.minor(adjustment.amount, currency)
        }

    /** "+ 10%" or "− $15.00". */
    fun adjustmentLabel(
        adjustment: TemplateAdjustment,
        currency: CurrencySpec,
        options: FormatOptions,
    ): String {
        val sign = if (adjustment.amount.signum() < 0) "−" else "+"
        val size = adjustment.amount.abs()
        return if (adjustment.percent) {
            "$sign ${size.stripTrailingZeros().toPlainString()}%"
        } else {
            "$sign ${MoneyFormat.format(TemplateWords.minor(size, currency), currency, options)}"
        }
    }

    fun signed(
        minor: Long,
        currency: CurrencySpec,
        options: FormatOptions,
    ): String = (if (minor < 0) "−" else "+") + MoneyFormat.format(abs(minor), currency, options)

    /** The month the schedule next comes in, or null when it has no date. */
    fun dueMonth(info: ScheduleSummary): YearMonth? = info.nextDate.takeIf { it > 0 }?.let { YearMonth.of(it / 10000, (it / 100) % 100) }
}

/** The tinted sum under a schedule form: the bill, the change, each bill after it, and what this month gets. */
@Composable
internal fun ScheduleReceipt(
    draft: Template.Schedule?,
    info: ScheduleSummary?,
    thisMonth: Long?,
    currency: CurrencySpec,
    options: FormatOptions,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Metro.accent.fill.copy(alpha = 0.08f))
            .height(IntrinsicSize.Min)
            .testTag("template-preview"),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(Metro.accent.fill))
        Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (draft == null) {
                MetroText(TemplateEditorCopy.NEEDS, Metro.typography.subheader, color = Metro.colors.secondary)
                return@Column
            }
            val adjustment = draft.adjustment
            val base = info?.let(ScheduleFacts::base)
            ReceiptLine(draft.name, base?.let { MoneyFormat.format(it, currency, options) }.orEmpty(), "template-receipt-bill")
            if (adjustment != null) {
                val change = base?.let { ScheduleFacts.change(it, adjustment, currency) }
                ReceiptLine(
                    ScheduleFacts.adjustmentLabel(adjustment, currency, options),
                    change?.let { ScheduleFacts.signed(it, currency, options) }.orEmpty(),
                    "template-receipt-change",
                )
                if (base != null && change != null) {
                    Rule()
                    ReceiptLine(
                        ScheduleFacts.EACH_BILL,
                        MoneyFormat.format((base + change).coerceAtLeast(0), currency, options),
                        "template-receipt-each",
                    )
                }
            }
            Rule()
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetroText(ScheduleFacts.THIS_MONTH, Metro.typography.caption, Modifier.weight(1f), color = Metro.colors.secondary)
                MetroText(
                    thisMonth?.let { MoneyFormat.format(it, currency, options) } ?: "…",
                    Metro.typography.subheader,
                    Modifier.testTag("template-preview-amount"),
                )
            }
        }
    }
}

@Composable
private fun ReceiptLine(
    label: String,
    value: String,
    tag: String,
) {
    Row(Modifier.fillMaxWidth().testTag(tag)) {
        MetroText(label, Metro.typography.body, Modifier.weight(1f))
        MetroText(value, Metro.typography.body)
    }
}

@Composable
private fun Rule() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .height(1.dp)
            .background(Metro.colors.secondary.copy(alpha = 0.3f)),
    )
}

/**
 * Save up ahead or only in the month it's due, as two cards a tap picks, each with a strip of the months to the
 * next date: even bars when saving ahead, one bar in the month it's due otherwise. Without [info] the cards keep
 * their words and drop the strip.
 */
@Composable
internal fun DueChoices(
    info: ScheduleSummary?,
    full: Boolean,
    month: YearMonth,
    currency: CurrencySpec,
    options: FormatOptions,
    adjustment: TemplateAdjustment?,
    onPick: (full: Boolean) -> Unit,
) {
    val due = info?.let(ScheduleFacts::dueMonth)
    val months =
        due?.let { (ChronoUnit.MONTHS.between(month, it) + 1).toInt().coerceIn(2, 12) }?.let { n ->
            (0 until n).map {
                due.minusMonths(
                    (
                        n - 1 -
                            it
                    ).toLong(),
                )
            }
        }
    val bill =
        info?.let {
            val base = ScheduleFacts.base(it)
            MoneyFormat.format(
                (
                    base + (
                        adjustment?.let { a ->
                            ScheduleFacts.change(base, a, currency)
                        } ?: 0
                    )
                ).coerceAtLeast(0),
                currency,
                options,
            )
        }
    val dueName = due?.month?.name?.lowercase()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DueCard(
            ScheduleFacts.SAVE_AHEAD,
            if (bill != null && dueName != null) "a share each month, all $bill ready by $dueName" else "a share each month until it's due",
            selected = !full,
            bars = months?.map { 1f },
            months = months,
            tag = "template-full-ahead",
        ) { onPick(false) }
        DueCard(
            ScheduleFacts.ONLY_WHEN_DUE,
            if (bill != null && dueName != null) "$bill in $dueName, nothing before" else "the whole amount when it comes",
            selected = full,
            bars = months?.mapIndexed { i, _ -> if (i == months.lastIndex) 1f else 0f },
            months = months,
            tag = "template-full-due",
        ) { onPick(true) }
    }
}

@Composable
private fun DueCard(
    title: String,
    detail: String,
    selected: Boolean,
    bars: List<Float>?,
    months: List<YearMonth>?,
    tag: String,
    onClick: () -> Unit,
) {
    val accent = Metro.accent.fill
    val ink = Metro.colors.foreground
    val quiet = Metro.colors.secondary
    Row(
        Modifier
            .fillMaxWidth()
            .border(MetroDimens.Stroke, if (selected) accent else quiet.copy(alpha = 0.4f))
            .metroPress(onClick)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            }.padding(12.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(Modifier.padding(top = 3.dp).size(20.dp)) {
            val r = size.minDimension / 2
            drawCircle(if (selected) accent else ink, r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            if (selected) drawCircle(accent, r - 5.dp.toPx(), Offset(r, r))
        }
        Column(Modifier.weight(1f)) {
            MetroText(title, Metro.typography.body)
            MetroText(detail, Metro.typography.caption, color = quiet)
            if (bars != null && months != null) {
                // A sixth of the due month's bar for a month of saving, so the strips compare at a glance.
                val share = 1f / bars.size
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).height(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    bars.forEachIndexed { i, v ->
                        val h =
                            if (v == 0f) {
                                0.05f
                            } else if (bars.all { it == 1f }) {
                                share.coerceAtLeast(0.12f)
                            } else {
                                v
                            }
                        val lit = selected && (i == 0 || bars.count { it > 0f } == 1 && v > 0f)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(h)
                                .background(if (lit) accent else quiet.copy(alpha = 0.35f)),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    months.forEach {
                        MetroText(
                            it.month.name
                                .lowercase()
                                .take(3),
                            Metro.typography.caption.copy(textAlign = TextAlign.Center),
                            Modifier.weight(1f),
                            color = quiet,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
