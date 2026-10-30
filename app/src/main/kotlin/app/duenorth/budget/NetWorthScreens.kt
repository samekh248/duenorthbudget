package app.duenorth.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.NetWorthHistory
import app.duenorth.budget.core.NetWorthSpan
import app.duenorth.budget.core.ReportsCopy
import app.duenorth.budget.core.ReviewCopy
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/**
 * Reports → net worth: today's total over a line of how it moved across the picked span, then
 * each side of the budget. Press and slide along the line to read any day on it.
 */
@Composable
fun NetWorthGraphScreen(
    ui: NetWorthUi,
    includeOffBudget: Boolean,
    onSpan: (NetWorthSpan) -> Unit,
    onIncludeOffBudget: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val history = ui.history
    var scrub by remember(history, includeOffBudget) { mutableStateOf<Int?>(null) }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .verticalScroll(rememberScrollState(), enabled = scrub == null)
            .testTag("net-worth-graph-screen"),
    ) {
        MetroText(ReviewCopy.NET_WORTH, Metro.typography.header, Modifier.padding(horizontal = MetroDimens.Gutter))
        if (history == null) {
            if (ui.loading) Placeholder()
            return@Column
        }
        val options = formatOptions()
        val shown = history.points.getOrNull(scrub ?: history.points.lastIndex)
        Column(Modifier.padding(horizontal = MetroDimens.Gutter)) {
            MetroText(
                MoneyFormat.format(shown?.total(includeOffBudget) ?: 0L, history.currency, options),
                Metro.typography.header,
                Modifier.testTag("net-worth-total"),
                color = Metro.accent.text,
            )
            MetroText(
                if (scrub != null && shown != null) day(shown.date) else change(history, includeOffBudget, options),
                Metro.typography.caption,
                Modifier.testTag("net-worth-change"),
                color = if (scrub == null && history.change(includeOffBudget) < 0) Metro.colors.error else Metro.colors.secondary,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MetroDimens.Gutter, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NetWorthSpan.entries.forEach { span ->
                Choice(span.label, span == ui.span, "net-worth-span-${span.name.lowercase()}") { onSpan(span) }
            }
        }
        NetWorthLine(history, includeOffBudget, scrub) { scrub = it }
        Row(Modifier.fillMaxWidth().padding(horizontal = MetroDimens.Gutter)) {
            MetroText(
                history.first
                    ?.date
                    ?.let(::day)
                    .orEmpty(),
                Metro.typography.caption,
                Modifier.weight(1f).testTag("net-worth-start"),
                color = Metro.colors.secondary,
            )
            MetroText("today", Metro.typography.caption, color = Metro.colors.secondary)
        }
        Column(Modifier.padding(horizontal = MetroDimens.Gutter).padding(top = MetroDimens.Grid)) {
            val last = history.last
            Side(ReviewCopy.ON_BUDGET, last?.onBudgetMinor ?: 0L, history, options)
            Side(ReviewCopy.OFF_BUDGET, last?.offBudgetMinor ?: 0L, history, options)
            MetroText(
                if (includeOffBudget) "hide off budget" else ReviewCopy.INCLUDE_OFF_BUDGET,
                Metro.typography.subheader,
                Modifier
                    .heightIn(min = MetroDimens.TouchTarget)
                    .metroPress { onIncludeOffBudget(!includeOffBudget) }
                    .padding(top = MetroDimens.Row, bottom = MetroDimens.Row)
                    .testTag("net-worth-toggle"),
                color = Metro.accent.text,
            )
        }
    }
}

/** "+$5,875.00 since jun 1, 2026": how far the total moved over the span. */
private fun change(
    history: NetWorthHistory,
    includeOffBudget: Boolean,
    options: FormatOptions,
): String {
    val moved = history.change(includeOffBudget)
    val sign = if (moved > 0) "+" else ""
    val since =
        history.first
            ?.date
            ?.let(::day)
            .orEmpty()
    return "$sign${MoneyFormat.format(moved, history.currency, options)} ${ReportsCopy.NET_WORTH_SINCE} $since"
}

private fun day(date: LocalDate): String = date.format(DAY).lowercase()

@Composable
private fun Side(
    label: String,
    amountMinor: Long,
    history: NetWorthHistory,
    options: FormatOptions,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = MetroDimens.Row), verticalAlignment = Alignment.CenterVertically) {
        MetroText(label, Metro.typography.subheader, Modifier.weight(1f))
        MetroText(MoneyFormat.format(amountMinor, history.currency, options), Metro.typography.subheader)
    }
}

/**
 * The line itself: a flat accent stroke over a solid flat fill down to the bottom, three hairline guides labelled with
 * the top and bottom of the scale, a dashed zero line when the total crosses it, and a dot on today.
 */
@Composable
private fun NetWorthLine(
    history: NetWorthHistory,
    includeOffBudget: Boolean,
    scrub: Int?,
    onScrub: (Int?) -> Unit,
) {
    val values = remember(history, includeOffBudget) { history.points.map { it.total(includeOffBudget) } }
    val low = values.minOrNull() ?: 0L
    val high = values.maxOrNull() ?: 0L
    // Some room above and below the line; a flat line sits in the middle.
    val pad = if (high == low) maxOf(abs(high) / 10, 100L) else (high - low) / 8
    val top = high + pad
    val bottom = low - pad
    val options = formatOptions()
    val topLabel = MoneyFormat.format(high, history.currency, options)
    val bottomLabel = MoneyFormat.format(low, history.currency, options)
    val measurer = rememberTextMeasurer()
    val labelStyle = Metro.typography.caption.copy(color = Metro.colors.secondary)
    val line = Metro.accent.fill
    val guide = Metro.colors.chrome
    val zero = Metro.colors.outline
    val background = Metro.colors.background
    val cursor = Metro.colors.foreground
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(horizontal = MetroDimens.Gutter, vertical = MetroDimens.Row)
            .testTag("net-worth-graph")
            .semantics { contentDescription = "net worth from $bottomLabel to $topLabel" }
            .pointerInput(values) {
                if (values.size < 2) return@pointerInput
                val at = { x: Float -> ((x / size.width) * values.lastIndex).roundToInt().coerceIn(0, values.lastIndex) }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onScrub(at(down.position.x))
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        onScrub(at(change.position.x))
                        change.consume()
                    }
                    onScrub(null)
                }
            },
    ) {
        val span = (top - bottom).toFloat()
        val y = { value: Long -> size.height * (1f - (value - bottom) / span) }
        val x = { index: Int -> if (values.size < 2) size.width else size.width * index / values.lastIndex }
        val hair = 1.dp.toPx()

        for (fraction in listOf(0f, 0.5f, 1f)) {
            val gy = size.height * fraction
            drawLine(guide, Offset(0f, gy), Offset(size.width, gy), hair)
        }
        if (bottom < 0 && top > 0) {
            drawLine(zero, Offset(0f, y(0)), Offset(size.width, y(0)), hair, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
        label(measurer.measure(topLabel, labelStyle), Offset(0f, y(high)), above = true)
        label(measurer.measure(bottomLabel, labelStyle), Offset(0f, y(low)), above = false)

        if (values.isEmpty()) return@Canvas
        val stroke = Path()
        values.forEachIndexed { index, value ->
            if (index == 0) stroke.moveTo(x(index), y(value)) else stroke.lineTo(x(index), y(value))
        }
        val area =
            Path().apply {
                addPath(stroke)
                lineTo(x(values.lastIndex), size.height)
                lineTo(x(0), size.height)
                close()
            }
        drawPath(area, line.copy(alpha = AREA_ALPHA))
        drawPath(stroke, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val marked = scrub ?: values.lastIndex
        val dot = Offset(x(marked), y(values[marked]))
        if (scrub != null) drawLine(cursor, Offset(dot.x, 0f), Offset(dot.x, size.height), hair)
        drawCircle(background, 6.dp.toPx(), dot)
        drawCircle(line, 4.dp.toPx(), dot)
    }
}

/** A scale label sitting just above (or below) its value, kept inside the graph. */
private fun DrawScope.label(
    text: TextLayoutResult,
    at: Offset,
    above: Boolean,
) {
    val gap = 4.dp.toPx()
    val top = if (above) at.y - text.size.height - gap else at.y + gap
    drawText(text, topLeft = Offset(at.x, top.coerceIn(0f, size.height - text.size.height)))
}

/** How strongly the area under the line is filled: plainly the accent, while the line and labels still read over it. */
private const val AREA_ALPHA = 0.4f
