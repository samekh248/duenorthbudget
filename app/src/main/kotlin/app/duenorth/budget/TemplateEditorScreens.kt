package app.duenorth.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.Template
import app.duenorth.budget.core.TemplateKind
import app.duenorth.budget.core.TemplateLines
import app.duenorth.budget.core.TemplateNote
import app.duenorth.budget.core.monthLabel
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroComboBox
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroSegmentedToggle
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.components.relief
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.Relief
import java.math.BigDecimal
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// The template editor, option A of the design (template tiles): each template line in a category's note is a tile on
// the category page; add template, in the page's `•••` menu, picks a kind from a grid, and one form adds or edits a template with a live preview. Lines
// are still written to the note, so Actual desktop reads them as before.

object TemplateEditorCopy {
    const val SECTION = "templates"
    const val ADD = "add template"
    const val EMPTY = "no templates yet. add one from the ••• menu."
    const val KINDS = "add template"
    const val KINDS_HINT = "what should this category get?"
    const val ASKS = "this month"
    const val DRAG_HINT = "hold and drag a tile to change the order money is given out. tap a tile to change it."
    const val UNREADABLE = "can't read this line"
    const val REPLACE_HINT = "pick what this line should be, or remove it."
    const val REMOVE = "remove template"
    const val REMOVE_LINE = "remove line"
    const val ORDER = "order"
    const val ORDER_HINT = "lower numbers are given money first, across every category"
    const val PREVIEW = "this month"
    const val NEEDS = "fill in the amount"
    const val NO_SCHEDULES = "no schedules yet. add one under accounts, schedules."
    const val NOTE = "note"
}

/** A tile's two lines: what the template does, and how. */
object TemplateWords {
    fun title(
        template: Template,
        currency: CurrencySpec,
        options: FormatOptions,
    ): String {
        fun money(value: BigDecimal) = MoneyFormat.format(minor(value, currency), currency, options)
        return when (template) {
            is Template.Simple ->
                template.monthly.let { monthly ->
                    if (monthly == null) "top up to ${money(template.limit ?: BigDecimal.ZERO)}" else "${money(monthly)} every month"
                }
            is Template.By -> "${money(template.amount)} by ${monthLabel(template.month)}"
            is Template.Schedule -> template.name
            is Template.Percentage -> "${plain(template.percent)}% of ${template.category}"
            is Template.Average -> "average of ${template.months} ${if (template.months == 1) "month" else "months"}"
            is Template.Remainder -> "what's left"
            is Template.Goal -> "goal of ${money(template.amount)}"
        }
    }

    fun caption(
        template: Template,
        currency: CurrencySpec,
        options: FormatOptions,
    ): String =
        when (template) {
            is Template.Simple -> {
                val limit = template.limit
                when {
                    template.monthly == null -> "cap a balance"
                    limit != null -> "held up to ${MoneyFormat.format(minor(limit, currency), currency, options)}"
                    else -> "same each month"
                }
            }
            is Template.By -> "save by a date, ${repeatLabel(template)}"
            is Template.Schedule -> if (template.full) "pay a schedule, only when it's due" else "pay a schedule, saving up ahead"
            is Template.Percentage -> if (template.previous) "share of last month" else "share of this month"
            is Template.Average -> "like before"
            is Template.Remainder ->
                if (template.weight.compareTo(BigDecimal.ONE) ==
                    0
                ) {
                    "an equal share of what remains"
                } else {
                    "${plain(template.weight)} shares of what remains"
                }
            is Template.Goal -> "shows progress, budgets nothing"
        }

    fun repeatLabel(template: Template.By): String {
        val step = template.repeatMonths ?: return "once"
        return when {
            template.annual && step == 12 -> "every year"
            template.annual -> "every ${step / 12} years"
            step == 1 -> "every month"
            else -> "every $step months"
        }
    }

    fun minor(
        value: BigDecimal,
        currency: CurrencySpec,
    ): Long = value.movePointRight(currency.decimals).toDouble().roundToLong()

    private fun plain(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
}

/** A tile's face: raised under tile relief, a layer card under mica, a hairline box under Metro. */
@Composable
private fun Modifier.templateTile(selected: Boolean = false): Modifier {
    val style = Metro.style
    val accent = Metro.accent.fill
    return this
        .then(
            if (style.relief) {
                relief(Relief.Raised)
            } else {
                border(MetroDimens.Stroke, if (selected) accent else Metro.colors.secondary.copy(alpha = 0.5f))
            },
        ).then(
            if (selected && style.relief) {
                Modifier.border(MetroDimens.Stroke, accent, if (style.layers) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp))
            } else {
                Modifier
            },
        )
}

/**
 * The category page's templates: one tile per template line in note order, with what each asks for this month, an
 * add tile, and a hold-and-drag reorder that hands the category's priorities out again in the new order.
 */
@Composable
fun TemplateTiles(
    note: String,
    amounts: List<Long?>,
    currency: CurrencySpec,
    onOpen: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val split = remember(note) { TemplateNote.parse(note) }
    val options = formatOptions()
    val asks = amounts.filterNotNull().sum()
    val gap = 12.dp
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    var dragging by remember(note) { mutableStateOf<Int?>(null) }
    var offset by remember(note) { mutableFloatStateOf(0f) }
    var step by remember { mutableFloatStateOf(1f) }
    val target =
        dragging?.let { from -> (from + (offset / step).roundToInt()).coerceIn(0, split.entries.lastIndex) }
    Column(modifier.fillMaxWidth().testTag("template-tiles"), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            MetroText(TemplateEditorCopy.SECTION, Metro.typography.subheader, Modifier.weight(1f))
            if (split.entries.isNotEmpty()) {
                MetroText("${TemplateEditorCopy.ASKS} ", Metro.typography.caption, color = Metro.colors.secondary)
                MetroText(
                    MoneyFormat.format(asks, currency, options),
                    Metro.typography.body,
                    Modifier.testTag("template-asks"),
                    color = Metro.accent.text,
                )
            }
        }
        split.entries.forEachIndexed { index, entry ->
            val lifted = dragging == index
            val shift =
                when {
                    dragging == null || target == null || lifted -> 0f
                    index in (dragging!! + 1)..target -> -step
                    index in target until dragging!! -> step
                    else -> 0f
                }
            TemplateTile(
                entry = entry,
                amount = amounts.getOrNull(index),
                currency = currency,
                options = options,
                modifier =
                    Modifier
                        .onSizeChanged { step = it.height + gapPx }
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (lifted) offset else shift
                            if (lifted) {
                                scaleX = 1.03f
                                scaleY = 1.03f
                            }
                        }.pointerInput(note, index) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragging = index
                                    offset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                },
                                onDragEnd = {
                                    val to = target ?: index
                                    dragging = null
                                    offset = 0f
                                    if (to != index) onMove(index, to)
                                },
                                onDragCancel = {
                                    dragging = null
                                    offset = 0f
                                },
                            )
                        }.testTag("template-tile-$index"),
                onClick = { onOpen(index) },
            )
        }
        if (split.entries.isEmpty()) {
            MetroText(TemplateEditorCopy.EMPTY, Metro.typography.caption, color = Metro.colors.secondary)
        }
        if (split.entries.size > 1) {
            MetroText(TemplateEditorCopy.DRAG_HINT, Metro.typography.caption, color = Metro.colors.secondary)
        }
    }
}

@Composable
private fun TemplateTile(
    entry: TemplateNote.Entry,
    amount: Long?,
    currency: CurrencySpec,
    options: FormatOptions,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val template = entry.template
    Row(
        modifier
            .fillMaxWidth()
            .templateTile(selected = template == null)
            .metroPress(onClick)
            .padding(start = 4.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Grip(Modifier.padding(end = 4.dp))
        TemplateGlyph(template?.let { TemplateKind.of(it) }, Metro.accent.text, Modifier.padding(end = 10.dp))
        Column(Modifier.weight(1f)) {
            MetroText(
                if (template == null) TemplateEditorCopy.UNREADABLE else TemplateWords.title(template, currency, options),
                Metro.typography.body,
                maxLines = 1,
                softWrap = false,
            )
            MetroText(
                if (template == null) entry.line else TemplateWords.caption(template, currency, options),
                Metro.typography.caption,
                color = if (template == null) Metro.accent.text else Metro.colors.secondary,
                maxLines = 2,
            )
        }
        if (template != null) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                MetroText(amount?.let { MoneyFormat.format(it, currency, options) } ?: "—", Metro.typography.body)
                PriorityBadge(template, Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** A template's place in the order: its priority filled with the accent, or an outlined word for the two that run last. */
@Composable
private fun PriorityBadge(
    template: Template,
    modifier: Modifier = Modifier,
) {
    val priority = template.priority
    val shape = if (Metro.style.layers) RoundedCornerShape(12.dp) else RoundedCornerShape(0.dp)
    val label =
        when (template) {
            is Template.Remainder -> "last"
            is Template.Goal -> "goal"
            else -> priority.toString()
        }
    val filled = priority != null
    Box(
        modifier
            .heightIn(min = 22.dp)
            .clip(shape)
            .then(
                if (filled) {
                    Modifier.background(Metro.accent.fill)
                } else {
                    Modifier.border(1.5.dp, Metro.colors.secondary, shape)
                },
            ).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(label, Metro.typography.caption, color = if (filled) Metro.accent.onFill else Metro.colors.secondary)
    }
}

/** Two columns of dots: the tile can be held and dragged. */
@Composable
internal fun Grip(modifier: Modifier = Modifier) {
    val color = Metro.colors.secondary
    Canvas(modifier.size(width = 14.dp, height = 22.dp)) {
        val r = 1.5.dp.toPx()
        for (x in listOf(4.dp, 10.dp)) for (y in listOf(5.dp, 11.dp, 17.dp)) drawCircle(color, r, Offset(x.toPx(), y.toPx()))
    }
}

/** A line drawing for each kind (and a mark for a line that does not read), on a 26-unit grid. */
@Composable
fun TemplateGlyph(
    kind: TemplateKind?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(26.dp)) {
        val u = size.width / 26f
        val stroke = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun p(
            x: Float,
            y: Float,
        ) = Offset(x * u, y * u)

        fun line(vararg points: Float) {
            val path =
                Path().apply {
                    moveTo(points[0] * u, points[1] * u)
                    var i = 2
                    while (i < points.size) {
                        lineTo(points[i] * u, points[i + 1] * u)
                        i += 2
                    }
                }
            drawPath(path, color, style = stroke)
        }

        fun DrawScope.box(
            x: Float,
            y: Float,
            w: Float,
            h: Float,
        ) = drawRect(color, p(x, y), Size(w * u, h * u), style = stroke)

        fun calendar() {
            box(3f, 5f, 20f, 17f)
            line(3f, 10f, 23f, 10f)
            line(8f, 3f, 8f, 7f)
            line(18f, 3f, 18f, 7f)
        }
        when (kind) {
            TemplateKind.MONTHLY -> {
                calendar()
                line(9f, 16f, 17f, 16f)
            }
            TemplateKind.SCHEDULE -> {
                calendar()
                line(9f, 16f, 12f, 19f, 17f, 13f)
            }
            TemplateKind.BY_DATE -> {
                line(6f, 23f, 6f, 3f)
                line(6f, 4f, 19f, 4f, 16f, 8f, 19f, 12f, 6f, 12f)
            }
            TemplateKind.PERCENT -> {
                line(20f, 5f, 6f, 21f)
                drawCircle(color, 3f * u, p(7.5f, 7.5f), style = stroke)
                drawCircle(color, 3f * u, p(18.5f, 18.5f), style = stroke)
            }
            TemplateKind.AVERAGE -> {
                line(3f, 21f, 23f, 21f)
                box(5f, 12f, 3f, 9f)
                box(11.5f, 8f, 3f, 13f)
                box(18f, 14f, 3f, 7f)
            }
            TemplateKind.REMAINDER -> {
                drawArc(color, 0f, 270f, true, p(3f, 3f), Size(20f * u, 20f * u), style = stroke)
                drawArc(color, 270f, 90f, true, p(5f, 1f), Size(20f * u, 20f * u), style = stroke)
            }
            TemplateKind.CAP -> {
                line(3f, 8f, 23f, 8f)
                line(3f, 19f, 23f, 19f)
                line(13f, 8f, 13f, 19f)
            }
            TemplateKind.GOAL -> {
                drawCircle(color, 10f * u, p(13f, 13f), style = stroke)
                drawCircle(color, 6f * u, p(13f, 13f), style = stroke)
                drawCircle(color, 2f * u, p(13f, 13f), style = stroke)
            }
            null -> {
                drawCircle(color, 10f * u, p(13f, 13f), style = stroke)
                line(13f, 7f, 13f, 14.5f)
                drawCircle(color, 1.2f * u, p(13f, 18.5f))
            }
        }
    }
}

/** Add, step one: a grid of kinds, each with an example. With [line], it replaces a line that does not read. */
@Composable
fun TemplateKindsScreen(
    categoryName: String,
    line: String?,
    onPick: (TemplateKind) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val examples =
        mapOf(
            TemplateKind.MONTHLY to "\$400 every month",
            TemplateKind.BY_DATE to "\$1,200 by december",
            TemplateKind.SCHEDULE to "whatever Rent needs",
            TemplateKind.PERCENT to "10% of all income",
            TemplateKind.AVERAGE to "average of 3 months",
            TemplateKind.REMAINDER to "split what remains",
            TemplateKind.CAP to "up to \$600 held",
            TemplateKind.GOAL to "\$500, budgets nothing",
        )
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(MetroDimens.Gutter)
            .testTag("template-kinds"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText(TemplateEditorCopy.KINDS, Metro.typography.header, maxLines = 1, softWrap = false)
        MetroText(categoryName, Metro.typography.caption, color = Metro.colors.secondary)
        if (line != null) {
            MetroText(TemplateEditorCopy.UNREADABLE, Metro.typography.subheader)
            MetroText(line, Metro.typography.body, color = Metro.accent.text)
            MetroText(TemplateEditorCopy.REPLACE_HINT, Metro.typography.caption, color = Metro.colors.secondary)
        } else {
            MetroText(TemplateEditorCopy.KINDS_HINT, Metro.typography.caption, color = Metro.colors.secondary)
        }
        TemplateKind.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { kind ->
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 112.dp)
                            .templateTile()
                            .metroPress { onPick(kind) }
                            .padding(10.dp)
                            .testTag("template-kind-${kind.name.lowercase()}"),
                    ) {
                        TemplateGlyph(kind, Metro.accent.text)
                        Spacer(Modifier.height(10.dp))
                        MetroText(kind.label, Metro.typography.control)
                        MetroText(examples.getValue(kind), Metro.typography.caption, color = Metro.colors.secondary)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (line != null) {
            MetroButtonGrid { MetroButton(TemplateEditorCopy.REMOVE_LINE, Modifier.testTag("template-remove"), onClick = onRemove) }
        }
    }
}

/** Where the money for a share of income comes from. */
data class PercentSource(
    val value: String,
    val label: String,
)

/**
 * Add or edit, step two: the blanks for [kind], the template's order, and what it would give this month with the
 * line it will write. Save is on the app bar; a template that is not finished yet saves nothing.
 */
@Composable
fun TemplateEditorScreen(
    categoryName: String,
    kind: TemplateKind,
    initial: Template,
    isNew: Boolean,
    month: YearMonth,
    currency: CurrencySpec,
    schedules: List<String>,
    sources: List<PercentSource>,
    preview: Pair<Template, Long?>?,
    onPreview: (Template) -> Unit,
    onSave: (Template) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = LocalFormatPrefs.current
    val options = formatOptions()
    val decimals = currency.decimals

    fun edit(value: BigDecimal?): String = value?.let { prefs.editAmount(TemplateWords.minor(it, currency), decimals) }.orEmpty()

    fun read(text: String): BigDecimal? =
        text
            .takeIf { it.isNotBlank() }
            ?.let {
                prefs
                    .readAmount(
                        it,
                        decimals,
                    ).toBigDecimalOrNull()
            }?.takeIf { it.signum() >= 0 }
            ?.let(::tidy)

    fun plain(value: BigDecimal?) = value?.stripTrailingZeros()?.toPlainString().orEmpty()

    fun number(text: String): BigDecimal? = text.toBigDecimalOrNull()?.let(::tidy)

    var amount by remember(initial) {
        mutableStateOf(
            when (initial) {
                is Template.Simple -> edit(initial.monthly ?: initial.limit)
                is Template.By -> edit(initial.amount)
                is Template.Goal -> edit(initial.amount)
                else -> ""
            }.let { if (it == edit(BigDecimal.ZERO)) "" else it },
        )
    }
    var cap by remember(initial) { mutableStateOf(if (initial is Template.Simple && initial.monthly != null) edit(initial.limit) else "") }
    var byMonth by remember(initial) { mutableStateOf((initial as? Template.By)?.month ?: month.plusMonths(11)) }
    val repeats = listOf("once", "every month", "every year", "every … months", "every … years")
    var repeat by remember(initial) {
        mutableStateOf(
            (initial as? Template.By)?.let { by ->
                val step = by.repeatMonths
                when {
                    step == null -> 0
                    by.annual && step == 12 -> 2
                    by.annual -> 4
                    step == 1 -> 1
                    else -> 3
                }
            } ?: 0,
        )
    }
    var count by remember(initial) {
        mutableStateOf(
            (initial as? Template.By)?.let { by -> by.repeatMonths?.let { if (by.annual) it / 12 else it } }?.takeIf { it >= 2 }?.toString()
                ?: "2",
        )
    }
    var schedule by remember(initial) { mutableStateOf((initial as? Template.Schedule)?.name ?: schedules.firstOrNull().orEmpty()) }
    var full by remember(initial) { mutableStateOf((initial as? Template.Schedule)?.full ?: false) }
    var percent by remember(initial) { mutableStateOf(plain((initial as? Template.Percentage)?.percent)) }
    var source by remember(initial) { mutableStateOf((initial as? Template.Percentage)?.category ?: TemplateLines.ALL_INCOME) }
    var previous by remember(initial) { mutableStateOf((initial as? Template.Percentage)?.previous ?: false) }
    var months by remember(initial) { mutableStateOf(((initial as? Template.Average)?.months ?: 3).toString()) }
    var weight by remember(initial) { mutableStateOf(plain((initial as? Template.Remainder)?.weight ?: BigDecimal.ONE)) }
    var priority by remember(initial) { mutableStateOf(initial.priority ?: 0) }

    val draft: Template? =
        when (kind) {
            TemplateKind.MONTHLY ->
                read(amount)?.takeIf { it.signum() > 0 }?.let { monthly ->
                    val limit = read(cap)
                    if (cap.isNotBlank() && limit == null) null else Template.Simple(monthly, limit, priority)
                }
            TemplateKind.CAP -> read(amount)?.takeIf { it.signum() > 0 }?.let { Template.Simple(null, it, priority) }
            TemplateKind.BY_DATE ->
                read(amount)?.takeIf { it.signum() > 0 }?.let { total ->
                    val n = count.toIntOrNull()?.takeIf { it >= 2 }
                    when (repeat) {
                        0 -> Template.By(total, byMonth, null, annual = false, repeat = null, priority = priority)
                        1 -> Template.By(total, byMonth, 1, annual = false, repeat = 1, priority = priority)
                        2 -> Template.By(total, byMonth, 12, annual = true, repeat = null, priority = priority)
                        3 -> n?.let { Template.By(total, byMonth, it, annual = false, repeat = it, priority = priority) }
                        else -> n?.let { Template.By(total, byMonth, 12 * it, annual = true, repeat = it, priority = priority) }
                    }
                }
            TemplateKind.SCHEDULE -> schedule.takeIf { it.isNotBlank() }?.let { Template.Schedule(it, full, priority) }
            TemplateKind.PERCENT ->
                number(percent)?.takeIf { it.signum() > 0 }?.let { Template.Percentage(it, previous, source, priority) }
            TemplateKind.AVERAGE -> months.toIntOrNull()?.takeIf { it > 0 }?.let { Template.Average(it, priority) }
            TemplateKind.REMAINDER -> number(weight)?.takeIf { it.signum() > 0 }?.let { Template.Remainder(it) }
            TemplateKind.GOAL -> read(amount)?.takeIf { it.signum() > 0 }?.let { Template.Goal(it) }
        }
    LaunchedEffect(draft) { draft?.let(onPreview) }
    RememberFormSave { draft?.let(onSave) }

    val number = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(MetroDimens.Gutter)
            .testTag("template-editor"),
        verticalArrangement = Arrangement.spacedBy(MetroDimens.FieldLabelGap),
    ) {
        MetroText(kind.label, Metro.typography.header, maxLines = 1, softWrap = false)
        MetroText(categoryName, Metro.typography.caption, color = Metro.colors.secondary)

        @Composable
        fun label(text: String) = MetroText(text, Metro.typography.caption, Modifier.padding(top = 12.dp), color = Metro.colors.secondary)
        when (kind) {
            TemplateKind.MONTHLY -> {
                label("amount each month")
                MetroField(amount, { amount = it }, "0", Modifier.testTag("template-amount"), keyboardOptions = number)
                label("hold no more than (leave empty for no cap)")
                MetroField(cap, { cap = it }, "no cap", Modifier.testTag("template-cap"), keyboardOptions = number)
            }
            TemplateKind.CAP -> {
                label("keep it topped up to")
                MetroField(amount, { amount = it }, "0", Modifier.testTag("template-amount"), keyboardOptions = number)
            }
            TemplateKind.BY_DATE -> {
                label("amount")
                MetroField(amount, { amount = it }, "0", Modifier.testTag("template-amount"), keyboardOptions = number)
                label("by")
                val choices = (0L until 36L).map { month.plusMonths(it) }.let { if (byMonth in it) it else listOf(byMonth) + it }
                MetroComboBox(choices, byMonth, { monthLabel(it) }, { byMonth = it }, "template-month", fullScreenThreshold = Int.MAX_VALUE)
                label("then")
                MetroComboBox(repeats.indices.toList(), repeat, { repeats[it] }, { repeat = it }, "template-repeat")
                if (repeat >= 3) {
                    label(if (repeat == 3) "how many months" else "how many years")
                    MetroField(count, {
                        count = it
                    }, "2", Modifier.testTag("template-count"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            }
            TemplateKind.SCHEDULE -> {
                label("schedule")
                if (schedules.isEmpty() && schedule.isBlank()) {
                    MetroText(TemplateEditorCopy.NO_SCHEDULES, Metro.typography.body, color = Metro.colors.secondary)
                } else {
                    val choices = if (schedule.isBlank() || schedule in schedules) schedules else listOf(schedule) + schedules
                    MetroComboBox(choices, schedule, { it }, { schedule = it }, "template-schedule")
                }
                label("when it comes less often than monthly")
                MetroSegmentedToggle("save up ahead", "only when due", !full, { full = !it }, "template-full")
            }
            TemplateKind.PERCENT -> {
                label("percent")
                MetroField(percent, { percent = it }, "10", Modifier.testTag("template-percent"), keyboardOptions = number)
                label("of")
                val choices = if (sources.any { it.value == source }) sources else listOf(PercentSource(source, source)) + sources
                MetroComboBox(choices, choices.first { it.value == source }, { it.label }, { source = it.value }, "template-source")
                label("from")
                MetroSegmentedToggle("this month", "last month", !previous, { previous = !it }, "template-previous")
            }
            TemplateKind.AVERAGE -> {
                label("months to average")
                MetroField(months, {
                    months = it
                }, "3", Modifier.testTag("template-months"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            TemplateKind.REMAINDER -> {
                label("shares of what's left (1 is an equal share)")
                MetroField(weight, { weight = it }, "1", Modifier.testTag("template-weight"), keyboardOptions = number)
            }
            TemplateKind.GOAL -> {
                label("goal")
                MetroField(amount, { amount = it }, "0", Modifier.testTag("template-amount"), keyboardOptions = number)
            }
        }
        if (initial.priority != null) {
            label(TemplateEditorCopy.ORDER)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper("−", "template-order-down") { priority = (priority - 1).coerceAtLeast(0) }
                Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                    MetroText(priority.toString(), Metro.typography.subheader, Modifier.testTag("template-order"))
                }
                Stepper("+", "template-order-up") { priority += 1 }
            }
            MetroText(TemplateEditorCopy.ORDER_HINT, Metro.typography.caption, color = Metro.colors.secondary)
        }
        Spacer(Modifier.height(12.dp))
        TemplatePreview(kind, draft, preview?.takeIf { it.first == draft }?.second, month, currency, options)
        if (!isNew) {
            Spacer(Modifier.height(12.dp))
            MetroButtonGrid { MetroButton(TemplateEditorCopy.REMOVE, Modifier.testTag("template-remove"), onClick = onRemove) }
        }
    }
}

/** A typed number as the note's parser reads it back ("1200", "12.5"), so a draft compares equal to its saved line. */
private fun tidy(value: BigDecimal): BigDecimal = value.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }

@Composable
private fun Stepper(
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(MetroDimens.TouchTarget)
            .templateTile()
            .metroPress(onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(label, Metro.typography.subheader, color = Metro.colors.foreground)
    }
}

/** What the draft would give this month, a bar for each month a by-date template saves over, and the note line. */
@Composable
private fun TemplatePreview(
    kind: TemplateKind,
    draft: Template?,
    amount: Long?,
    month: YearMonth,
    currency: CurrencySpec,
    options: FormatOptions,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .templateTile()
            .padding(12.dp)
            .testTag("template-preview"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MetroText(TemplateEditorCopy.PREVIEW, Metro.typography.caption, color = Metro.colors.secondary)
        if (draft == null) {
            MetroText(TemplateEditorCopy.NEEDS, Metro.typography.subheader, color = Metro.colors.secondary)
            return@Column
        }
        val shown =
            when (kind) {
                TemplateKind.REMAINDER -> "what's left"
                TemplateKind.GOAL -> "nothing; it tracks"
                else -> amount?.let { MoneyFormat.format(it, currency, options) } ?: "…"
            }
        Row(verticalAlignment = Alignment.Bottom) {
            MetroText(shown, Metro.typography.subheader, Modifier.weight(1f).testTag("template-preview-amount"))
            if (draft is Template.By) {
                val left = ChronoUnit.MONTHS.between(month, draft.month) + 1
                if (left > 0) {
                    MetroText(
                        if (left == 1L) "last month to save" else "$left months left",
                        Metro.typography.caption,
                        color = Metro.colors.secondary,
                    )
                }
            }
        }
        if (draft is Template.By) {
            val left = (ChronoUnit.MONTHS.between(month, draft.month) + 1).toInt()
            if (left in 1..24) {
                Row(
                    Modifier.fillMaxWidth().height(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    repeat(left) { i ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(if (i == 0) Metro.accent.fill else Metro.accent.fill.copy(alpha = 0.45f)),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    MetroText(monthLabel(month).take(3), Metro.typography.caption, color = Metro.colors.secondary)
                    Spacer(Modifier.weight(1f))
                    MetroText(monthLabel(draft.month).take(3), Metro.typography.caption, color = Metro.colors.secondary)
                }
            }
        }
        MetroText(TemplateLines.format(draft), Metro.typography.caption, Modifier.testTag("template-line"), color = Metro.colors.secondary)
    }
}

/** The category note without its template lines, which the tiles above show and edit. */
@Composable
fun CategoryNoteText(
    note: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = remember(note) { TemplateNote.parse(note).text }
    var draft by remember(text) { mutableStateOf(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetroText(TemplateEditorCopy.NOTE, Metro.typography.caption, color = Metro.colors.secondary)
        MetroField(draft, { draft = it }, TemplateEditorCopy.NOTE, Modifier.testTag("category-note"), lines = 2..6)
        if (draft != text) {
            MetroButton("save ${TemplateEditorCopy.NOTE}", Modifier.testTag("category-note-save")) { onSave(draft) }
        }
    }
}
