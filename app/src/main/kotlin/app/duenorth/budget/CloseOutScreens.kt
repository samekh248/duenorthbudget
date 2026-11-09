package app.duenorth.budget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duenorth.budget.core.CloseOutCopy
import app.duenorth.budget.core.CloseOutItem
import app.duenorth.budget.core.CloseOutKind
import app.duenorth.budget.core.CloseOutOutcome
import app.duenorth.budget.core.CloseOutReview
import app.duenorth.budget.core.CoverSource
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.design.MetroMotion
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroWeights
import app.duenorth.budget.design.turnstileIn
import java.time.LocalDate

/** What the assistant's close out card shows: the month's review, and whether it was already closed on this phone. */
data class CloseOutCard(
    val review: CloseOutReview,
    val closed: Boolean,
    val today: LocalDate = LocalDate.now(),
)

/** Close out's timings, in ms, on the app's Metro curves (Motion.kt). */
internal object CloseOutMotion {
    /** Each tile's flip, edge-on to flat. */
    const val FLIP = 380

    /** The longest gap between two tiles' flips; a long list shares [FLIP_SPREAD] out instead. */
    const val STAGGER = 90
    const val FLIP_SPREAD = 1400
    const val FIRST = 400

    /** Settled tiles flip one after another on the way back from the cards, slower so each one reads. */
    const val SETTLE_GAP = 320
    const val FILL = 600
    const val RISE = 360
}

/**
 * The assistant's close out card: an accent block in the month's last days with what is left over and what went over,
 * red when more than five went over; once the month is all square, a quiet row that says so.
 */
@Composable
internal fun CloseOutAssistantCard(
    card: CloseOutCard,
    money: (Long) -> String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val review = card.review
    val today = card.today
    if (card.closed) {
        Row(
            modifier
                .fillMaxWidth()
                .background(Metro.colors.chrome)
                .metroPress(onOpen)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("close-out-card-closed"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                MetroText(CloseOutCopy.closed(review.month), Metro.typography.groupHeader, maxLines = 1)
                MetroText(CloseOutCopy.CLOSED_HINT, Metro.typography.caption, color = Metro.colors.secondary, maxLines = 2)
            }
            Box(
                Modifier.size(40.dp).border(2.dp, Metro.accent.text, CircleShape),
                contentAlignment = Alignment.Center,
            ) { MetroText("✓", Metro.typography.body, color = Metro.accent.text) }
        }
        return
    }
    val fill = if (review.rough) Metro.colors.errorFill else Metro.accent.fill
    val ink = if (review.rough) Metro.colors.onErrorFill else Metro.accent.onFill
    Column(
        modifier
            .fillMaxWidth()
            .background(fill)
            .metroPress(onOpen)
            .padding(16.dp)
            .testTag("close-out-card"),
    ) {
        MetroText(CloseOutCopy.endsIn(review.month, today), kicker, color = ink.copy(alpha = 0.85f), maxLines = 1)
        MetroText(
            CloseOutCopy.title(review.month),
            Metro.typography.subheader,
            Modifier.padding(top = 2.dp),
            color = ink,
            maxLines = 1,
        )
        MetroText(CloseOutCopy.settle(review.month.plusMonths(1)), Metro.typography.caption, color = ink.copy(alpha = 0.85f))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                CloseOutCopy.LEFT to "${review.left.size} · ${money(review.leftMinor)}",
                CloseOutCopy.OVER to "${review.over.size} · ${money(review.overMinor)}",
            ).forEach { (label, figure) ->
                Column(Modifier.weight(1f).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 6.dp)) {
                    MetroText(label, Metro.typography.caption, color = ink, maxLines = 1)
                    MetroText(figure, Metro.typography.groupHeader, color = ink, maxLines = 1, softWrap = false)
                }
            }
        }
        Box(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .border(2.dp, ink)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            MetroText(
                CloseOutCopy.start(review.rough),
                Metro.typography.body.copy(fontWeight = MetroWeights.Semibold),
                color = ink,
                maxLines = 1,
            )
        }
    }
}

/**
 * Close out: the review card while no category is open, every category a tile, then the categories still to settle one
 * card at a time. The review flips each tile in (red over, accent left over, outlined even) and then says how the month
 * went; back from the cards, each settled tile flips to even, and the last one fills the card with the accent.
 */
@Composable
fun CloseOutScreen(
    ui: CloseOutUi,
    currency: CurrencySpec,
    onFix: () -> Unit,
    onReview: () -> Unit,
    onSkip: () -> Unit,
    onCover: (CoverSource) -> Unit,
    onRollOver: () -> Unit,
    onReturn: () -> Unit,
    onTakeFromNext: () -> Unit,
    onShown: (List<CloseOutItem>) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** Screenshot tests pin the review's clock; null runs it. */
    clockMs: Int? = null,
) {
    val review = ui.review
    if (review == null) {
        Box(modifier.fillMaxSize()) { Placeholder() }
        return
    }
    val options = formatOptions()
    val money = { minor: Long -> MoneyFormat.format(minor, currency, options) }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MetroDimens.Gutter)
            .padding(bottom = MetroDimens.Gutter)
            .testTag("close-out"),
    ) {
        MetroText(CloseOutCopy.ASSISTANT_TITLE, Metro.typography.header, maxLines = 1)
        val item = review.items.firstOrNull { it.categoryId == ui.step }
        if (item == null) {
            ReviewCard(review, ui, money, onFix, onDone, onShown, clockMs)
        } else {
            FixCard(review, item, ui, money, onReview, onSkip, onCover, onRollOver, onReturn, onTakeFromNext)
        }
    }
}

private val kicker
    @Composable get() = Metro.typography.caption.copy(fontWeight = MetroWeights.Semibold, fontSize = 13.sp, letterSpacing = 1.sp)

private val tagStyle
    @Composable get() = Metro.typography.caption.copy(fontWeight = MetroWeights.Semibold, fontSize = 12.sp, letterSpacing = 0.5.sp)

/** Metro's ease out (Motion.kt) at [t] of the way through, from 0 to 1. */
private fun ease(t: Float): Float = MetroMotion.EaseOut.transform(t.coerceIn(0f, 1f))

private fun progress(
    clock: Int,
    start: Int,
    length: Int,
): Float = ((clock - start).toFloat() / length).coerceIn(0f, 1f)

@Composable
private fun ReviewCard(
    review: CloseOutReview,
    ui: CloseOutUi,
    money: (Long) -> String,
    onFix: () -> Unit,
    onDone: () -> Unit,
    onShown: (List<CloseOutItem>) -> Unit,
    clockMs: Int?,
) {
    // Taken once per visit, so marking what was shown never restarts the motion.
    val first = remember { ui.shown == null }
    val before = remember { ui.shown }
    val changed = remember { review.items.filter { it.settled && before != null && before[it.categoryId]?.settled == false } }
    val count = review.items.size.coerceAtLeast(1)
    val stagger = minOf(CloseOutMotion.STAGGER, CloseOutMotion.FLIP_SPREAD / count)
    val flipsEnd =
        if (first) {
            CloseOutMotion.FIRST + stagger * (count - 1) + CloseOutMotion.FLIP
        } else {
            CloseOutMotion.FIRST + CloseOutMotion.SETTLE_GAP * changed.size
        }
    val verdictAt = flipsEnd + 150
    val riseAt = verdictAt + 250
    val fillAt = flipsEnd + 200
    val end = maxOf(riseAt + CloseOutMotion.RISE, if (review.allSquare) fillAt + CloseOutMotion.FILL + 200 else 0)
    val animations = Metro.animations
    val clock = remember { Animatable(if (animations && clockMs == null) 0f else end.toFloat()) }
    LaunchedEffect(Unit) {
        // Marked at once, so leaving mid-motion still counts as seen; [before] keeps what this visit flips from.
        onShown(review.items)
        if (clockMs == null && clock.value < end) clock.animateTo(end.toFloat(), tween(end - clock.value.toInt(), easing = LinearEasing))
    }
    val t = clockMs ?: clock.value.toInt()
    val fill = if (review.allSquare) ease(progress(t, fillAt, CloseOutMotion.FILL)) else 0f
    val filled = fill > 0.5f
    val onAccent = Metro.accent.onFill
    val ink = if (filled) onAccent else Metro.colors.foreground
    val quiet = if (filled) onAccent.copy(alpha = 0.8f) else Metro.colors.secondary
    val accentFill = Metro.accent.fill
    val turn = ease(progress(t, 0, MetroMotion.PAGE_MS))
    Column(
        Modifier
            .padding(top = MetroDimens.Row)
            .fillMaxWidth()
            .graphicsLayer {
                transformOrigin =
                    androidx.compose.ui.graphics
                        .TransformOrigin(0f, 0.5f)
                cameraDistance = 16f * density
                rotationY = 40f * (1f - turn)
                alpha = (turn / 0.45f).coerceAtMost(1f)
            }.border(2.dp, if (fill > 0f) accentFill else Metro.colors.outline.copy(alpha = 0.5f))
            .drawBehind {
                if (fill > 0f) drawRect(accentFill, Offset(0f, size.height * (1f - fill)), Size(size.width, size.height * fill))
            }.padding(16.dp)
            .testTag("close-out-review"),
    ) {
        MetroText(
            if (filled) CloseOutCopy.closedKicker(review.month) else CloseOutCopy.kicker(review.month),
            kicker,
            color = quiet,
            maxLines = 1,
        )
        // The first visit names the month, then gives the verdict; later visits open on the verdict.
        val verdict = if (first) ease(progress(t, verdictAt, 300)) else 1f
        val headline =
            when {
                filled || (review.allSquare && first) -> CloseOutCopy.ALL_SQUARE
                verdict < 0.5f ->
                    review.month.month.name
                        .lowercase() + "."
                review.rough -> CloseOutCopy.ROUGH_MONTH
                else -> CloseOutCopy.ALMOST_SQUARE
            }
        MetroText(
            headline,
            Metro.typography.header,
            Modifier
                .graphicsLayer {
                    if (!filled) {
                        val a = if (verdict < 0.5f) 1f - verdict * 2f else verdict * 2f - 1f
                        alpha = a
                        translationY = if (verdict >= 0.5f) (1f - a) * 14f * density else 0f
                    }
                }.testTag("close-out-headline"),
            color = if (review.rough && !filled && verdict >= 0.5f) Metro.colors.error else ink,
            maxLines = 1,
        )
        // Counters tick as each tile lands.
        val landed =
            review.items.filterIndexed { index, _ ->
                !first || progress(t, CloseOutMotion.FIRST + index * stagger, CloseOutMotion.FLIP) > 0.5f
            }
        // Each tile counts as the face it shows right now: its old one until a changed tile has turned.
        val faces =
            landed.map {
                if (it.settled(t, changed, first)) {
                    it
                } else {
                    before?.get(it.categoryId)?.takeIf { old ->
                        !old.settled
                    } ?: it
                }
            }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Tally(
                CloseOutCopy.OVER,
                faces.count {
                    !it.settled && it.kind == CloseOutKind.OVER
                },
                Metro.colors.errorFill,
                ink,
                quiet,
                Modifier.weight(1f),
            )
            Tally(
                CloseOutCopy.LEFT,
                faces.count {
                    !it.settled && it.kind == CloseOutKind.LEFT
                },
                accentFill,
                ink,
                quiet,
                Modifier.weight(1f),
                outlined = filled,
            )
            Tally(CloseOutCopy.EVEN, faces.count { it.settled }, null, ink, quiet, Modifier.weight(1f))
        }
        val dim = if (filled) 0f else ease(progress(t, verdictAt, 300))
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            review.items.chunked(4).forEachIndexed { row, tiles ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    tiles.forEachIndexed { column, item ->
                        val index = row * 4 + column
                        val changedAt = changed.indexOfFirst { it.categoryId == item.categoryId }
                        val flip = if (first) ease(progress(t, CloseOutMotion.FIRST + index * stagger, CloseOutMotion.FLIP)) else 1f
                        // A changed tile turns edge-on showing its old face, then comes back flat showing it settled.
                        val turn =
                            if (changedAt >=
                                0
                            ) {
                                progress(t, CloseOutMotion.FIRST + changedAt * CloseOutMotion.SETTLE_GAP, CloseOutMotion.FLIP)
                            } else {
                                1f
                            }
                        val showSettled = item.settled && turn >= 0.5f
                        val angle =
                            when {
                                first -> -90f * (1f - flip)
                                turn < 0.5f -> 90f * (turn * 2f) * (turn * 2f)
                                turn < 1f -> -90f * (1f - ease((turn - 0.5f) * 2f))
                                else -> 0f
                            }
                        Tile(
                            if (showSettled) item else before?.get(item.categoryId) ?: item,
                            showSettled,
                            ui.outcomes[item.categoryId],
                            money,
                            final = filled,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .graphicsLayer {
                                        cameraDistance = 12f * density
                                        rotationX = angle
                                        alpha =
                                            if (first && flip == 0f) {
                                                0f
                                            } else if (showSettled && !filled) {
                                                1f - 0.55f * dim
                                            } else {
                                                1f
                                            }
                                    },
                        )
                    }
                    repeat(4 - tiles.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        val rise = ease(progress(t, riseAt, CloseOutMotion.RISE))
        val hint =
            when {
                filled -> CloseOutCopy.CLOSED_HINT
                review.rough ->
                    CloseOutCopy.roughHint(review.over.count { !it.kept }, money(-review.overMinor), money(review.leftMinor))
                review.allSquare -> CloseOutCopy.CLOSED_HINT
                else -> CloseOutCopy.FIX_HINT
            }
        MetroText(
            hint,
            Metro.typography.body,
            Modifier.padding(top = 14.dp).graphicsLayer {
                alpha = if (filled) 1f else rise
                translationY = (1f - rise) * 16f * density
            },
            color = quiet,
        )
        val todo = review.todo.size
        val done = review.allSquare
        Box(
            Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
                .height(56.dp)
                .graphicsLayer {
                    alpha = if (filled) 1f else rise
                    translationY = (1f - rise) * 40f * density
                }.background(if (done) (if (filled) onAccent else accentFill) else accentFill)
                .metroPress(if (done) onDone else onFix)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("close-out-fix"),
            contentAlignment = Alignment.BottomStart,
        ) {
            MetroText(
                if (done) CloseOutCopy.goTo(review.month.plusMonths(1)) else CloseOutCopy.fix(todo),
                Metro.typography.control,
                color = if (done && filled) accentFill else onAccent,
                maxLines = 1,
            )
        }
    }
}

/** Whether [this] shows as settled at [t]: a tile that changed since the last visit only once its flip has turned. */
private fun CloseOutItem.settled(
    t: Int,
    changed: List<CloseOutItem>,
    first: Boolean,
): Boolean {
    if (!settled) return false
    if (first) return true
    val at = changed.indexOfFirst { it.categoryId == categoryId }
    return at < 0 || progress(t, CloseOutMotion.FIRST + at * CloseOutMotion.SETTLE_GAP, CloseOutMotion.FLIP) >= 0.5f
}

@Composable
private fun Tally(
    label: String,
    count: Int,
    swatch: Color?,
    ink: Color,
    quiet: Color,
    modifier: Modifier,
    outlined: Boolean = false,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .then(if (swatch != null && !outlined) Modifier.background(swatch) else Modifier.border(1.5.dp, quiet)),
            )
            MetroText(label, Metro.typography.caption, Modifier.padding(start = 5.dp), color = ink, maxLines = 1)
        }
        MetroText("$count", Metro.typography.subheader, color = ink, maxLines = 1)
    }
}

@Composable
private fun Tile(
    item: CloseOutItem,
    showSettled: Boolean,
    outcome: CloseOutOutcome?,
    money: (Long) -> String,
    final: Boolean,
    modifier: Modifier,
) {
    val (back, ink) =
        when {
            final -> Color.White to Metro.accent.fill
            showSettled -> Color.Transparent to Metro.colors.foreground
            item.kind == CloseOutKind.OVER -> Metro.colors.errorFill to Metro.colors.onErrorFill
            else -> Metro.accent.fill to Metro.accent.onFill
        }
    val tag =
        when {
            showSettled || final -> "✓"
            item.kind == CloseOutKind.OVER -> CloseOutCopy.OVER_TAG
            else -> CloseOutCopy.LEFT_TAG
        }
    val figure =
        when {
            !showSettled && !final -> money(item.availableMinor)
            else ->
                when (outcome) {
                    CloseOutOutcome.COVERED -> CloseOutCopy.COVERED
                    CloseOutOutcome.ROLLED -> CloseOutCopy.ROLLED
                    CloseOutOutcome.RETURNED -> CloseOutCopy.RETURNED
                    CloseOutOutcome.NEXT_MONTH -> CloseOutCopy.NEXT_MONTH
                    null ->
                        when {
                            item.kept && item.kind == CloseOutKind.OVER -> CloseOutCopy.NEXT_MONTH
                            item.kept -> CloseOutCopy.ROLLED
                            else -> CloseOutCopy.EVEN
                        }
                }
        }
    Column(
        modifier
            .heightIn(min = 70.dp)
            .background(back)
            .then(if (showSettled && !final) Modifier.border(2.dp, Metro.colors.secondary.copy(alpha = 0.6f)) else Modifier)
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .testTag("close-out-tile-${item.categoryId}"),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        MetroText(tag, tagStyle, color = ink, maxLines = 1)
        Column {
            MetroText(
                item.name,
                Metro.typography.caption.copy(fontSize = 12.sp, lineHeight = 15.sp),
                color = ink,
                maxLines = 1,
                softWrap = false,
            )
            MetroText(
                figure,
                Metro.typography.caption.copy(fontSize = 14.sp, lineHeight = 17.sp),
                color = ink,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** One category to settle: what it is short or has left, and its choices as tiles. */
@Composable
private fun FixCard(
    review: CloseOutReview,
    item: CloseOutItem,
    ui: CloseOutUi,
    money: (Long) -> String,
    onReview: () -> Unit,
    onSkip: () -> Unit,
    onCover: (CoverSource) -> Unit,
    onRollOver: () -> Unit,
    onReturn: () -> Unit,
    onTakeFromNext: () -> Unit,
) {
    val over = item.kind == CloseOutKind.OVER
    val next = review.month.plusMonths(1)
    val todo = review.todo
    val index = todo.indexOfFirst { it.categoryId == item.categoryId }.coerceAtLeast(0)
    Row(Modifier.fillMaxWidth().padding(top = MetroDimens.Row), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        todo.forEachIndexed { i, _ ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(
                        if (i == index) {
                            Metro.colors.foreground
                        } else if (i < index) {
                            Metro.accent.fill
                        } else {
                            Metro.colors.chrome
                        },
                    ),
            )
        }
    }
    MetroText(
        CloseOutCopy.step(index + 1, todo.size),
        Metro.typography.caption,
        Modifier.padding(top = 4.dp),
        color = Metro.colors.secondary,
    )
    Column(
        Modifier
            .padding(top = MetroDimens.Row)
            .fillMaxWidth()
            .turnstileIn(item.categoryId)
            .border(2.dp, Metro.colors.outline.copy(alpha = 0.5f))
            .padding(16.dp)
            .testTag("close-out-step"),
    ) {
        MetroText(
            if (over) "OVERSPENT" else "LEFT OVER",
            kicker,
            color = if (over) Metro.colors.error else Metro.accent.text,
            maxLines = 1,
        )
        MetroText(item.name, Metro.typography.header, maxLines = 1, softWrap = false)
        MetroText(
            money(item.availableMinor),
            Metro.typography.header.copy(fontSize = 40.sp, lineHeight = 46.sp),
            color = if (over) Metro.colors.error else Metro.accent.text,
            maxLines = 1,
        )
        MetroText(
            CloseOutCopy.facts(money(item.budgetedMinor), money(-item.spentMinor)),
            Metro.typography.caption,
            color = Metro.colors.secondary,
        )
        if (over) {
            MetroText(CloseOutCopy.COVER_FROM, Metro.typography.body, Modifier.padding(top = 20.dp), color = Metro.colors.secondary)
            val sources = review.sources(item).take(5)
            if (sources.isEmpty()) {
                MetroText(CloseOutCopy.NOTHING_TO_COVER_FROM, Metro.typography.body, Modifier.padding(top = 8.dp))
            }
            ChoiceTiles(
                sources.mapIndexed { i, source ->
                    Choice(
                        source.name,
                        money(source.availableMinor),
                        filled = i == 0,
                        wide = i == 0,
                        tag = "close-out-source-${source.categoryId ?: "to-budget"}",
                    ) {
                        onCover(source)
                    }
                },
            )
            MetroText(
                CloseOutCopy.takeFrom(next),
                Metro.typography.body,
                Modifier.padding(top = 16.dp).metroPress(onTakeFromNext).testTag("close-out-next-month"),
                color = Metro.accent.text,
            )
        } else {
            MetroText(CloseOutCopy.WHAT_NOW, Metro.typography.body, Modifier.padding(top = 20.dp), color = Metro.colors.secondary)
            ChoiceTiles(
                listOf(
                    Choice(
                        CloseOutCopy.ROLL_OVER,
                        CloseOutCopy.keepFor(money(item.availableMinor), item.name, next),
                        filled = true,
                        wide = true,
                        tag = "close-out-roll-over",
                        onClick = onRollOver,
                    ),
                    Choice(CloseOutCopy.BACK_TO_BUDGET, CloseOutCopy.TO_BUDGET, tag = "close-out-return", onClick = onReturn),
                ),
            )
        }
        ui.error?.let { MetroText(it, Metro.typography.body, Modifier.padding(top = 8.dp), color = Metro.colors.error) }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            MetroText(
                "‹ ${CloseOutCopy.REVIEW_LINK}",
                Metro.typography.caption,
                Modifier.metroPress(onReview).testTag("close-out-review-link"),
                color = Metro.colors.secondary,
            )
            MetroText(
                "${CloseOutCopy.SKIP} ›",
                Metro.typography.caption,
                Modifier.metroPress(onSkip).testTag("close-out-skip"),
                color = Metro.colors.secondary,
            )
        }
    }
}

private class Choice(
    val label: String,
    val figure: String,
    val filled: Boolean = false,
    val wide: Boolean = false,
    val tag: String,
    val onClick: () -> Unit,
)

@Composable
private fun ChoiceTiles(choices: List<Choice>) {
    val rows = mutableListOf<List<Choice>>()
    var pending = mutableListOf<Choice>()
    choices.forEach { choice ->
        if (choice.wide) {
            rows += listOf(choice)
        } else {
            pending += choice
            if (pending.size == 2) {
                rows += pending
                pending = mutableListOf()
            }
        }
    }
    if (pending.isNotEmpty()) rows += pending
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { choice ->
                    val ink = if (choice.filled) Metro.accent.onFill else Metro.colors.foreground
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 84.dp)
                            .then(
                                if (choice.filled) {
                                    Modifier.background(Metro.accent.fill)
                                } else {
                                    Modifier.border(2.dp, Metro.colors.outline.copy(alpha = 0.6f))
                                },
                            ).metroPress(choice.onClick)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag(choice.tag),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        MetroText(choice.label, Metro.typography.caption, color = ink, maxLines = 1)
                        MetroText(choice.figure, Metro.typography.groupHeader, color = ink, maxLines = 2)
                    }
                }
                if (row.size == 1 && !row.first().wide) Spacer(Modifier.weight(1f))
            }
        }
    }
}
