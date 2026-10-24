package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.TemplateChip
import app.duenorth.budget.core.TemplateCopy
import app.duenorth.budget.core.TemplateKind
import app.duenorth.budget.core.TemplateLane
import app.duenorth.budget.core.TemplatePlan
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.components.relief
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.Relief

// The templates screen, option D of the template editor design (priority waterfall): every category's templates as
// chips in priority lanes, with a meter of what is left to share after each lane.

object TemplateLanesCopy {
    const val START = "to share"
    const val LEFT = "left"
    const val REMAINDER = "what's left, by share"
    const val GOALS = "goals, which budget nothing"
    const val NEW_LANE = "drop here for a new priority"
    const val DRAG_HINT = "tap a chip to change it. hold and drag it to another lane to change its priority."
    const val UNREADABLE = "can't be read"
    const val FIX = "fix"
    const val FIX_FIRST = "fix these lines before applying"
    const val WOULD = "templates would"
    const val NOW = "now"
    const val AFTER = "templates"
    const val NOTHING = "nothing would change"
    const val KEEP = "keep"
}

/** One category whose budgeted amount applying templates would change. */
data class TemplateApplyRow(
    val categoryId: String,
    val name: String,
    val before: Long,
    val after: Long,
)

/** What apply or overwrite would do this month, shown before anything changes. */
data class TemplateApplyPreview(
    val overwrite: Boolean,
    val rows: List<TemplateApplyRow>,
)

/** The lane a chip was dropped on: an existing priority, or one past the highest. */
private const val NEW_LANE_KEY = Int.MIN_VALUE

/**
 * Every lane of [plan]: its priority, its chips (category, what it asks, what it gets), and a meter of what is left
 * after it. A chip with a priority can be held and dragged to another lane, or below them all for a new priority.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateLanes(
    plan: TemplatePlan,
    currency: CurrencySpec,
    onOpen: (TemplateChip) -> Unit,
    onMove: (TemplateChip, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lanes = remember { mutableStateMapOf<Int, ClosedFloatingPointRange<Float>>() }
    var dragging by remember(plan) { mutableStateOf<TemplateChip?>(null) }
    var pointer by remember(plan) { mutableStateOf<Float?>(null) }
    val hovered = pointer?.let { y -> lanes.entries.firstOrNull { y in it.value }?.key }
    val highest = plan.lanes.mapNotNull { it.priority }.maxOrNull() ?: -1

    fun drop(chip: TemplateChip) {
        val target = hovered ?: return
        val priority = if (target == NEW_LANE_KEY) highest + 1 else target
        if (priority != chip.template?.priority) onMove(chip, priority)
    }
    Column(modifier.fillMaxWidth().testTag("template-lanes"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MetroText(TemplateLanesCopy.DRAG_HINT, Metro.typography.caption, color = Metro.colors.secondary)
        plan.lanes.forEachIndexed { laneIndex, lane ->
            val key = lane.priority
            val target = key != null && dragging != null && hovered == key
            Column(
                Modifier
                    .fillMaxWidth()
                    .zIndex(if (dragging != null && lane.chips.any { it == dragging }) 1f else 0f)
                    .then(
                        if (key !=
                            null
                        ) {
                            Modifier.onGloballyPositioned { lanes[key] = it.boundsInRoot().let { b -> b.top..b.bottom } }
                        } else {
                            Modifier
                        },
                    ).then(if (target) Modifier.border(MetroDimens.Stroke, Metro.accent.fill) else Modifier)
                    .padding(if (target) 4.dp else 0.dp)
                    .testTag("template-lane-$laneIndex"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LaneHeading(lane)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lane.chips.forEach { chip ->
                        LaneChip(
                            chip = chip,
                            currency = currency,
                            lifted = dragging == chip,
                            draggable = chip.template?.priority != null,
                            onClick = { onOpen(chip) },
                            onDragStart = {
                                dragging = chip
                                pointer = it
                            },
                            onDrag = { pointer = it },
                            onDragEnd = {
                                drop(chip)
                                dragging = null
                                pointer = null
                            },
                        )
                    }
                }
                lane.leftAfter?.let { left -> Meter(left, plan.start, currency) }
            }
        }
        if (dragging != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .onGloballyPositioned { lanes[NEW_LANE_KEY] = it.boundsInRoot().let { b -> b.top..b.bottom } }
                    .border(
                        MetroDimens.Stroke,
                        if (hovered ==
                            NEW_LANE_KEY
                        ) {
                            Metro.accent.fill
                        } else {
                            Metro.colors.secondary.copy(alpha = 0.5f)
                        },
                    ).testTag("template-new-lane"),
                contentAlignment = Alignment.Center,
            ) {
                MetroText(TemplateLanesCopy.NEW_LANE, Metro.typography.body, color = Metro.colors.secondary)
            }
        }
        if (plan.unreadable.isNotEmpty()) {
            plan.unreadable.forEachIndexed { index, chip ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (Metro.style.relief) Modifier.relief(Relief.Raised) else Modifier)
                        .border(
                            MetroDimens.Stroke,
                            Metro.accent.fill,
                            if (Metro.style.layers) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp),
                        ).metroPress { onOpen(chip) }
                        .padding(10.dp)
                        .testTag("template-unreadable-$index"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TemplateGlyph(null, Metro.accent.text, Modifier.padding(end = 10.dp))
                    Column(Modifier.weight(1f)) {
                        MetroText("${chip.categoryName} ${TemplateLanesCopy.UNREADABLE}", Metro.typography.body)
                        MetroText(chip.line, Metro.typography.caption, color = Metro.accent.text)
                    }
                    MetroText(TemplateLanesCopy.FIX, Metro.typography.control, color = Metro.accent.text)
                }
            }
        }
    }
}

@Composable
private fun LaneHeading(lane: TemplateLane) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val shape = if (Metro.style.layers) RoundedCornerShape(12.dp) else RoundedCornerShape(0.dp)
        val filled = lane.kind == TemplateLane.Kind.PRIORITY
        Box(
            Modifier
                .heightIn(min = 24.dp)
                .clip(shape)
                .then(if (filled) Modifier.background(Metro.accent.fill) else Modifier.border(1.5.dp, Metro.colors.secondary, shape))
                .padding(horizontal = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            MetroText(
                when (lane.kind) {
                    TemplateLane.Kind.PRIORITY -> lane.priority.toString()
                    TemplateLane.Kind.REMAINDER -> "last"
                    TemplateLane.Kind.GOAL -> "goal"
                },
                Metro.typography.caption,
                color = if (filled) Metro.accent.onFill else Metro.colors.secondary,
            )
        }
        MetroText(
            when (lane.kind) {
                TemplateLane.Kind.PRIORITY -> "priority ${lane.priority}"
                TemplateLane.Kind.REMAINDER -> TemplateLanesCopy.REMAINDER
                TemplateLane.Kind.GOAL -> TemplateLanesCopy.GOALS
            },
            Metro.typography.caption,
            color = Metro.colors.secondary,
        )
    }
}

@Composable
private fun LaneChip(
    chip: TemplateChip,
    currency: CurrencySpec,
    lifted: Boolean,
    draggable: Boolean,
    onClick: () -> Unit,
    onDragStart: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val options = formatOptions()
    var offset by remember { mutableStateOf(Offset.Zero) }
    var top by remember { mutableStateOf(0f) }
    val short = chip.given != null && chip.asks != null && chip.given!! < chip.asks!!
    Row(
        Modifier
            .onGloballyPositioned { top = it.boundsInRoot().top - offset.y }
            .zIndex(if (lifted) 1f else 0f)
            .graphicsLayer {
                translationX = if (lifted) offset.x else 0f
                translationY = if (lifted) offset.y else 0f
                if (lifted) {
                    scaleX = 1.05f
                    scaleY = 1.05f
                }
            }.then(
                if (Metro.style.relief) {
                    Modifier.relief(Relief.Raised, small = true)
                } else {
                    Modifier.border(MetroDimens.Stroke, if (lifted) Metro.accent.fill else Metro.colors.secondary.copy(alpha = 0.5f))
                },
            ).then(
                if (draggable) {
                    Modifier.pointerInput(chip) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { start ->
                                offset = Offset.Zero
                                onDragStart(top + start.y)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                offset += amount
                                onDrag(top + change.position.y + offset.y - amount.y)
                            },
                            onDragEnd = {
                                onDragEnd()
                                offset = Offset.Zero
                            },
                            onDragCancel = {
                                offset = Offset.Zero
                                onDragEnd()
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ).metroPress(onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp)
            .testTag("template-chip-${chip.categoryId}-${chip.index}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(18.dp)) {
            TemplateGlyph(chip.template?.let { TemplateKind.of(it) }, Metro.accent.text, Modifier.size(18.dp))
        }
        MetroText(chip.categoryName, Metro.typography.body, maxLines = 1, softWrap = false)
        val amount = chip.given ?: chip.asks
        if (amount != null) {
            MetroText(
                MoneyFormat.format(amount, currency, options),
                Metro.typography.caption,
                // Short of what it asks: to budget ran out before this one.
                color = if (short) Metro.accent.text else Metro.colors.secondary,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** How much of what there was to share is still left after a lane. */
@Composable
private fun Meter(
    left: Long,
    start: Long,
    currency: CurrencySpec,
) {
    val fraction = if (start <= 0) 0f else (left.toFloat() / start).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .weight(1f)
                .height(6.dp)
                .then(if (Metro.style.layers) Modifier.clip(RoundedCornerShape(3.dp)) else Modifier)
                .background(Metro.colors.secondary.copy(alpha = 0.25f)),
        ) {
            if (fraction > 0f) Box(Modifier.weight(fraction).fillMaxHeight().background(Metro.accent.fill))
            if (fraction < 1f) Box(Modifier.weight(1f - fraction).fillMaxHeight())
        }
        MetroText(
            "${MoneyFormat.format(left, currency, formatOptions())} ${TemplateLanesCopy.LEFT}",
            Metro.typography.caption,
            color = if (left < 0) Metro.accent.text else Metro.colors.secondary,
        )
    }
}

/** Apply's before and after: every category it would change, then fill or keep. */
@Composable
fun TemplateApplyList(
    preview: TemplateApplyPreview,
    currency: CurrencySpec,
    onConfirm: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = formatOptions()
    Column(modifier.fillMaxWidth().testTag("template-apply-list"), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        MetroText(
            "${TemplateLanesCopy.WOULD} ${if (preview.overwrite) TemplateCopy.OVERWRITE else TemplateCopy.APPLY}",
            Metro.typography.subheader,
            Modifier.padding(bottom = 8.dp),
        )
        if (preview.rows.isEmpty()) {
            MetroText(TemplateLanesCopy.NOTHING, Metro.typography.body, color = Metro.colors.secondary)
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                MetroText("", Metro.typography.caption, Modifier.weight(1f))
                MetroText(TemplateLanesCopy.NOW, Metro.typography.caption, Modifier.width(96.dp), color = Metro.colors.secondary)
                MetroText(TemplateLanesCopy.AFTER, Metro.typography.caption, Modifier.width(96.dp), color = Metro.colors.secondary)
            }
            preview.rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).testTag("template-apply-${row.categoryId}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetroText(row.name, Metro.typography.body, Modifier.weight(1f), maxLines = 1, softWrap = false)
                    MetroText(
                        MoneyFormat.format(row.before, currency, options),
                        Metro.typography.body,
                        Modifier.width(96.dp),
                        color = Metro.colors.secondary,
                    )
                    MetroText(
                        MoneyFormat.format(row.after, currency, options),
                        Metro.typography.body,
                        Modifier.width(96.dp),
                        color = Metro.accent.text,
                    )
                }
            }
        }
        MetroButtonGrid(Modifier.padding(top = 12.dp)) {
            if (preview.rows.isNotEmpty()) {
                MetroButton(
                    "${if (preview.overwrite) TemplateCopy.OVERWRITE else TemplateCopy.APPLY} ${preview.rows.size}",
                    Modifier.testTag("template-apply-confirm"),
                    onClick = onConfirm,
                )
            }
            MetroButton(TemplateLanesCopy.KEEP, Modifier.testTag("template-apply-keep"), onClick = onKeep)
        }
    }
}
