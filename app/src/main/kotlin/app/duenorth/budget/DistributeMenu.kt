package app.duenorth.budget

import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.TemplateCopy
import app.duenorth.budget.core.TemplateResult
import app.duenorth.budget.design.components.MetroMenuItem
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import kotlin.math.abs

// Tapping a positive unassigned figure: the month's templates, run the way Actual's month menu runs them.

/** Words for the unassigned figure's menu, in Actual's own terms. */
object DistributeCopy {
    /** Actual's "Apply budget template": fills categories with nothing budgeted yet. */
    const val APPLY = TemplateCopy.APPLY

    /** Actual's "Overwrite with budget template": sets every templated category again. */
    const val OVERWRITE = TemplateCopy.OVERWRITE
    const val NOTHING = "nothing to fill this month"
    const val NO_TEMPLATES = "no category has a template yet"

    fun unreadable(count: Int): String = if (count == 1) "1 template line does not read" else "$count template lines do not read"

    fun categories(count: Int): String = if (count == 1) "1 category" else "$count categories"

    fun detail(
        option: DistributeOption,
        currency: CurrencySpec,
        format: FormatOptions,
    ): String {
        option.reason?.let { return it }
        val money = MoneyFormat.format(abs(option.amountMinor), currency, format)
        val where = categories(option.categories)
        // Apply only touches categories with nothing budgeted; overwrite sets every templated one again.
        val verb = if (option.overwrite) "resets" else "fills"
        val empty = if (option.overwrite) where else where.replace("categor", "empty categor")
        val sign = if (option.amountMinor < 0) "returns " else ""
        return "$sign$money · $verb $empty"
    }
}

/** One way to share out what is unassigned: what it would budget, or why it cannot. */
data class DistributeOption(
    val overwrite: Boolean,
    /** Categories whose budget would change. */
    val categories: Int = 0,
    /** What it takes from to budget, after what those categories already hold. */
    val amountMinor: Long = 0,
    /** Why it is off; null when it can run. */
    val reason: String? = null,
) {
    val enabled: Boolean get() = reason == null
}

/** The unassigned figure's menu, while it shows. */
data class DistributeMenu(
    val month: YearMonth,
    val options: List<DistributeOption>,
)

/** The budget view's hooks for the unassigned figure's menu. */
data class DistributeActions(
    /** Shows on a positive unassigned figure; null leaves the figure untappable. */
    val menu: DistributeMenu? = null,
    val onOpen: (() -> Unit)? = null,
    val onPick: (Boolean) -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/** The menu's lines, apply first, as Actual's month menu lists them. */
fun DistributeMenu.items(
    currency: CurrencySpec,
    format: FormatOptions,
    onPick: (Boolean) -> Unit,
): List<MetroMenuItem> =
    options.map { option ->
        MetroMenuItem(
            if (option.overwrite) DistributeCopy.OVERWRITE else DistributeCopy.APPLY,
            tag = if (option.overwrite) "distribute-overwrite" else "distribute-apply",
            detail = DistributeCopy.detail(option, currency, format),
            enabled = option.enabled,
        ) { onPick(option.overwrite) }
    }

/** What one preview run says about an option. */
internal fun distributeOption(
    overwrite: Boolean,
    result: TemplateResult,
    changes: Map<String, Pair<Long, Long>>,
): DistributeOption =
    when (result) {
        is TemplateResult.Applied ->
            if (changes.isEmpty()) {
                DistributeOption(overwrite, reason = if (result.categories == 0) DistributeCopy.NO_TEMPLATES else DistributeCopy.NOTHING)
            } else {
                DistributeOption(overwrite, changes.size, changes.values.sumOf { (before, after) -> after - before })
            }
        is TemplateResult.Invalid -> DistributeOption(overwrite, reason = DistributeCopy.unreadable(result.errors.size))
        is TemplateResult.Rejected -> DistributeOption(overwrite, reason = result.reason)
        is TemplateResult.NeedsConfirm -> DistributeOption(overwrite, reason = result.reason)
        TemplateResult.Valid -> DistributeOption(overwrite, reason = DistributeCopy.NOTHING)
    }

/** Opens the unassigned figure's menu: works out both template runs for the open month, writing nothing. */
internal fun ShellHost.openDistribute() {
    val host = this
    val state = host.state.value
    val shell = state.shell ?: return
    if (state.budgetEdit != null) return
    val month = state.openMonth
    if (!state.formatPrefs.templatesOn) {
        val off = listOf(false, true).map { DistributeOption(it, reason = TemplateCopy.OFF) }
        host.mutate { it.copy(templates = it.templates.copy(distribute = DistributeMenu(month, off))) }
        return
    }
    host.scope.launch {
        val options =
            withContext(host.io) {
                listOf(false, true).map { overwrite ->
                    val (result, changes) = host.library.previewTemplateChanges(shell.budgetId, month, overwrite)
                    distributeOption(overwrite, result, changes)
                }
            }
        host.mutate {
            // Only while the same month is still showing.
            if (it.openMonth != month ||
                it.budgetEdit != null
            ) {
                it
            } else {
                it.copy(templates = it.templates.copy(distribute = DistributeMenu(month, options)))
            }
        }
    }
}

internal fun ShellHost.closeDistribute() {
    mutate { it.copy(templates = it.templates.copy(distribute = null)) }
}

/** A line picked: runs Actual's apply or overwrite on the month as one undo step, then repaints it. */
internal fun ShellHost.distribute(overwrite: Boolean) {
    val host = this
    val state = host.state.value
    val menu = state.templates.distribute ?: return
    val budgetId = state.settings.openBudgetId ?: return
    host.mutate { it.copy(templates = it.templates.copy(distribute = null), monthUi = it.monthUi.copy(notice = null)) }
    host.scope.launch {
        // The menu already said what overwrite replaces, so picking it is the confirm.
        val result = withContext(host.io) { host.library.applyTemplates(budgetId, menu.month, overwrite, confirmed = true) }
        when (result) {
            is TemplateResult.Applied -> host.afterMonthWrite(budgetId)
            is TemplateResult.Invalid ->
                host.mutate { it.copy(monthUi = it.monthUi.copy(notice = DistributeCopy.unreadable(result.errors.size))) }
            is TemplateResult.Rejected -> host.mutate { it.copy(monthUi = it.monthUi.copy(notice = result.reason)) }
            is TemplateResult.NeedsConfirm, TemplateResult.Valid -> Unit
        }
    }
}
