package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.TemplateCopy
import app.duenorth.budget.core.TemplateResult
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import kotlin.math.roundToInt

// Tapping a positive unassigned figure: the month's templates, run the way Actual's month menu runs them.

/**
 * The panel's runs: Actual's apply and overwrite, in plain words, and an app-only apply that spends no more than
 * what is unassigned.
 */
enum class DistributeKind(
    val overwrite: Boolean,
    /** Spends no more than the unassigned figure. */
    val capped: Boolean,
    val tag: String,
) {
    UNASSIGNED(overwrite = false, capped = true, tag = "distribute-unassigned"),
    APPLY(overwrite = false, capped = false, tag = "distribute-apply"),
    OVERWRITE(overwrite = true, capped = false, tag = "distribute-overwrite"),
    ;

    val label: String
        get() =
            when (this) {
                UNASSIGNED -> DistributeCopy.UNASSIGNED
                APPLY -> DistributeCopy.APPLY
                OVERWRITE -> DistributeCopy.OVERWRITE
            }

    val what: String
        get() =
            when (this) {
                UNASSIGNED -> DistributeCopy.UNASSIGNED_WHAT
                APPLY -> DistributeCopy.APPLY_WHAT
                OVERWRITE -> DistributeCopy.OVERWRITE_WHAT
            }
}

/** Words for the unassigned figure's panel. */
object DistributeCopy {
    const val UNASSIGNED = "add unassigned only"
    const val UNASSIGNED_WHAT = "fills empty targets by priority, only from unassigned"

    /** Actual's "Apply budget template". */
    const val APPLY = "fill empty targets"
    const val APPLY_WHAT = "budgets templated categories that have nothing yet"

    /** Actual's "Overwrite with budget template". */
    const val OVERWRITE = "refill all targets"
    const val OVERWRITE_WHAT = "sets every templated category to its target again"
    const val NOTHING = "nothing to fill this month"
    const val NO_TEMPLATES = "no category has a template yet"

    fun unreadable(count: Int): String = if (count == 1) "1 template line does not read" else "$count template lines do not read"

    /** The categories a run changes, the first few by name. */
    fun names(names: List<String>): String =
        when {
            names.size <= NAMES_SHOWN -> names.joinToString(", ")
            else -> names.take(NAMES_SHOWN).joinToString(", ") + " +${names.size - NAMES_SHOWN} more"
        }

    private const val NAMES_SHOWN = 3
}

/** One way to share out what is unassigned: what it would budget, or why it cannot. */
data class DistributeOption(
    val kind: DistributeKind,
    /** Categories whose budget would change. */
    val categories: Int = 0,
    /** What it takes from to budget, after what those categories already hold. */
    val amountMinor: Long = 0,
    /** Why it is off; null when it can run. */
    val reason: String? = null,
    /** The changed categories' names, in budget order. */
    val names: List<String> = emptyList(),
) {
    val enabled: Boolean get() = reason == null
}

/** The unassigned figure's panel, while it shows. */
data class DistributeMenu(
    val month: YearMonth,
    val options: List<DistributeOption>,
    /** The unassigned figure when it opened: the most "add just the unassigned" spends. */
    val unassignedMinor: Long = 0,
)

/** The budget view's hooks for the unassigned figure's panel. */
data class DistributeActions(
    /** Shows on a positive unassigned figure; null leaves the figure untappable. */
    val menu: DistributeMenu? = null,
    val onOpen: (() -> Unit)? = null,
    val onPick: (DistributeKind) -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/**
 * The panel under the unassigned figure: a heading with the amount, then one row per run with what it does, what
 * it would budget on the right, and the categories it reaches. The rest of the screen dims; a tap there closes it.
 */
@Composable
fun DistributePopup(
    anchor: Rect,
    menu: DistributeMenu,
    currency: CurrencySpec,
    format: FormatOptions,
    onPick: (DistributeKind) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density)
    val bottom = WindowInsets.navigationBars.getBottom(density)
    Popup(popupPositionProvider = WholeWindow, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        DistributePanel(anchor, menu, currency, format, onPick, onDismiss, top, bottom)
    }
}

private object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

@Composable
fun DistributePanel(
    anchor: Rect,
    menu: DistributeMenu,
    currency: CurrencySpec,
    format: FormatOptions,
    onPick: (DistributeKind) -> Unit,
    onDismiss: () -> Unit,
    safeTop: Int = 0,
    safeBottom: Int = 0,
) {
    val scrim = Metro.colors.background.copy(alpha = SCRIM)
    Layout(
        content = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Metro.colors.chrome)
                    .pointerInput(Unit) { detectTapGestures {} }
                    .padding(vertical = MetroDimens.Row)
                    .testTag("distribute-menu"),
            ) {
                menu.options.forEach { option ->
                    OptionRow(option, currency, format) {
                        onDismiss()
                        onPick(option.kind)
                    }
                }
            }
        },
        modifier =
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(scrim, Offset.Zero, Size(size.width, anchor.top.coerceAtLeast(0f)))
                    drawRect(scrim, Offset(0f, anchor.bottom), Size(size.width, (size.height - anchor.bottom).coerceAtLeast(0f)))
                }.pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) { measurables, constraints ->
        val panel = measurables.single().measure(constraints.copy(minWidth = constraints.maxWidth, minHeight = 0))
        val below = anchor.bottom.roundToInt()
        val limit = constraints.maxHeight - safeBottom
        val y =
            if (below + panel.height <= limit) {
                below
            } else {
                (anchor.top.roundToInt() - panel.height).coerceIn(safeTop, (limit - panel.height).coerceAtLeast(safeTop))
            }
        layout(constraints.maxWidth, constraints.maxHeight) { panel.place(0, y) }
    }
}

@Composable
private fun OptionRow(
    option: DistributeOption,
    currency: CurrencySpec,
    format: FormatOptions,
    onClick: () -> Unit,
) {
    val enabled = option.enabled
    val ink = if (enabled) Metro.colors.foreground else Metro.colors.secondary
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .then(if (enabled) Modifier.metroPress(onClick = onClick) else Modifier.semantics(mergeDescendants = true) { disabled() })
            .padding(horizontal = INSET, vertical = 10.dp)
            .testTag(option.kind.tag),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            MetroText(
                option.kind.label,
                Metro.typography.subheader,
                Modifier.weight(1f),
                color = ink,
                maxLines = 1,
            )
            if (enabled) {
                MetroText(
                    MoneyFormat.format(option.amountMinor, currency, format),
                    Metro.typography.subheader,
                    Modifier.padding(start = 12.dp),
                    color = Metro.accent.text,
                    maxLines = 1,
                )
            }
        }
        // Every row keeps the same three single lines, so the options are all one size, on or off.
        MetroText(
            option.kind.what,
            Metro.typography.caption,
            color = Metro.colors.secondary,
            maxLines = 1,
        )
        MetroText(
            option.reason ?: DistributeCopy.names(option.names),
            Metro.typography.caption,
            color = if (enabled) ink else Metro.accent.text,
            maxLines = 1,
        )
    }
}

private val INSET = 24.dp
private const val SCRIM = 0.85f

/** What one preview run says about an option. */
internal fun distributeOption(
    kind: DistributeKind,
    result: TemplateResult,
    changes: Map<String, Pair<Long, Long>>,
    order: List<Pair<String, String>> = emptyList(),
): DistributeOption =
    when (result) {
        is TemplateResult.Applied ->
            if (changes.isEmpty()) {
                DistributeOption(kind, reason = if (result.categories == 0) DistributeCopy.NO_TEMPLATES else DistributeCopy.NOTHING)
            } else {
                DistributeOption(
                    kind,
                    changes.size,
                    changes.values.sumOf { (before, after) -> after - before },
                    names = order.filter { it.first in changes }.map { it.second },
                )
            }
        is TemplateResult.Invalid -> DistributeOption(kind, reason = DistributeCopy.unreadable(result.errors.size))
        is TemplateResult.Rejected -> DistributeOption(kind, reason = result.reason)
        is TemplateResult.NeedsConfirm -> DistributeOption(kind, reason = result.reason)
        TemplateResult.Valid -> DistributeOption(kind, reason = DistributeCopy.NOTHING)
    }

/** Opens the unassigned figure's menu: works out both template runs for the open month, writing nothing. */
internal fun ShellHost.openDistribute() {
    val host = this
    val state = host.state.value
    val shell = state.shell ?: return
    if (state.budgetEdit != null) return
    val month = state.openMonth
    // With no later plans to come out, unassigned is all of to budget.
    val unassigned = (shell.ahead?.freeMinor ?: shell.headerMinor).takeIf { it > 0 } ?: return
    if (!state.formatPrefs.templatesOn) {
        val off = DistributeKind.entries.map { DistributeOption(it, reason = TemplateCopy.OFF) }
        host.mutate { it.copy(templates = it.templates.copy(distribute = DistributeMenu(month, off, unassigned))) }
        return
    }
    val order = shell.groups.flatMap { group -> group.categories.map { it.id to it.name } }
    host.scope.launch {
        val options =
            withContext(host.io) {
                DistributeKind.entries.map { kind ->
                    val cap = if (kind.capped) unassigned else null
                    val (result, changes) = host.library.previewTemplateChanges(shell.budgetId, month, kind.overwrite, cap = cap)
                    distributeOption(kind, result, changes, order)
                }
            }
        host.mutate {
            // Only while the same month is still showing.
            if (it.openMonth != month ||
                it.budgetEdit != null
            ) {
                it
            } else {
                it.copy(templates = it.templates.copy(distribute = DistributeMenu(month, options, unassigned)))
            }
        }
    }
}

internal fun ShellHost.closeDistribute() {
    mutate { it.copy(templates = it.templates.copy(distribute = null)) }
}

/** A line picked: runs Actual's apply or overwrite on the month as one undo step, then repaints it. */
internal fun ShellHost.distribute(kind: DistributeKind) {
    val host = this
    val state = host.state.value
    val menu = state.templates.distribute ?: return
    val budgetId = state.settings.openBudgetId ?: return
    host.mutate { it.copy(templates = it.templates.copy(distribute = null), monthUi = it.monthUi.copy(notice = null)) }
    host.scope.launch {
        // The menu already said what overwrite replaces, so picking it is the confirm.
        val result =
            withContext(host.io) {
                host.library.applyTemplates(
                    budgetId,
                    menu.month,
                    kind.overwrite,
                    confirmed = true,
                    cap = menu.unassignedMinor.takeIf { kind.capped },
                )
            }
        when (result) {
            is TemplateResult.Applied -> host.afterMonthWrite(budgetId)
            is TemplateResult.Invalid ->
                host.mutate { it.copy(monthUi = it.monthUi.copy(notice = DistributeCopy.unreadable(result.errors.size))) }
            is TemplateResult.Rejected -> host.mutate { it.copy(monthUi = it.monthUi.copy(notice = result.reason)) }
            is TemplateResult.NeedsConfirm, TemplateResult.Valid -> Unit
        }
    }
}
