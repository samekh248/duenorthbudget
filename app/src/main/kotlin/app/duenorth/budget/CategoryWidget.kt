package app.duenorth.budget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.edit
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.MoneyFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The home screen widget that shows what is left this month in the categories its owner chose (design B, the
 * ledger list, half Metro and half Material 3). It reads the budget straight from disk, so it shows the phone's copy as of the last sync or edit,
 * and it lays itself out again for whatever size it is given ([CategoryWidgetPlan]). A row opens its category in the
 * app; the title opens the category picker again.
 */
class CategoryWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) = refresh(context, manager, ids)

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        options: Bundle,
    ) = refresh(context, manager, intArrayOf(id))

    override fun onDeleted(
        context: Context,
        ids: IntArray,
    ) {
        ids.forEach { CategoryWidgetStore.forget(context, it) }
    }

    /** Reads the budget off the main thread; the broadcast stays alive until every widget is drawn. */
    private fun refresh(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        val pending = goAsync()
        scope.launch {
            try {
                ids.forEach { id -> runCatching { draw(context, manager, id) } }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        internal const val EXTRA_BUDGET = "app.duenorth.budget.widget.BUDGET"
        internal const val EXTRA_CATEGORY = "app.duenorth.budget.widget.CATEGORY"

        /** Portrait size a launcher has not reported yet: four cells by two. */
        private const val FALLBACK_WIDTH_DP = 320
        private const val FALLBACK_HEIGHT_DP = 180

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Draws every placed widget again, after the app or a sync changed the budget. Cheap when none is placed. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, CategoryWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, CategoryWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }

        /** Reads [id]'s categories and size and draws it; call off the main thread. */
        internal fun draw(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
        ) {
            val library = (context.applicationContext as? DueNorthApplication)?.library
            val content = library?.let { load(it, CategoryWidgetStore.read(context, id), LocalDate.now()) }
            val options = manager.getAppWidgetOptions(id)
            // The portrait size: the launcher's narrowest width and tallest height.
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: FALLBACK_WIDTH_DP
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: FALLBACK_HEIGHT_DP
            manager.updateAppWidget(id, views(context, id, CategoryWidgetPlan.of(width, height), content))
        }

        /** What [choice] shows this month, or null when its budget is gone from the phone. */
        internal fun load(
            library: BudgetLibrary,
            choice: CategoryWidgetStore.Choice?,
            today: LocalDate,
        ): Content? {
            val budgetId = choice?.budgetId ?: library.settings().openBudgetId ?: return null
            val shell = library.readShell(budgetId) ?: return null
            val options = runCatching { library.readFormatPrefs(budgetId).options }.getOrDefault(FormatOptions())
            return Content(
                budgetId = budgetId,
                today = today,
                currency = shell.currency,
                options = options,
                rows = CategoryWidgetPlan.resolve(choice?.categoryIds.orEmpty(), shell.groups),
                chosen = choice != null,
            )
        }

        /** The widget's views for [plan]; [content] null draws the note that asks to open the app. */
        internal fun views(
            context: Context,
            id: Int,
            plan: CategoryWidgetPlan,
            content: Content?,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_category)
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context, id, null))
            views.setOnClickPendingIntent(R.id.widget_header, openPicker(context, id))
            views.removeAllViews(R.id.widget_rows)
            views.removeAllViews(R.id.widget_strip)
            views.removeAllViews(R.id.widget_lines)
            if (content == null || content.rows.isEmpty()) {
                views.setTextViewText(R.id.widget_title, CategoryWidgetCopy.TITLE)
                views.setViewVisibility(R.id.widget_days, View.GONE)
                views.setViewVisibility(R.id.widget_header, if (plan.shape == CategoryWidgetPlan.Shape.COMPACT) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.widget_rows, View.GONE)
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setTextViewText(R.id.widget_empty, CategoryWidgetCopy.empty(content))
                views.setOnClickPendingIntent(
                    R.id.widget_empty,
                    if (content ==
                        null
                    ) {
                        openApp(context, id, null)
                    } else {
                        openPicker(context, id)
                    },
                )
                return views
            }
            val money = { minor: Long, cents: Boolean ->
                MoneyFormat.format(minor, content.currency, if (cents) content.options else content.options.copy(hideFraction = true))
            }
            val shown = content.rows.take(plan.slots)
            views.setTextViewText(R.id.widget_title, monthTitle(content.today, plan))
            views.setTextViewText(
                R.id.widget_days,
                CategoryWidgetCopy.daysLeft(
                    content.today,
                    short =
                        plan.shape == CategoryWidgetPlan.Shape.LIST && !plan.cents,
                ),
            )
            when (plan.shape) {
                CategoryWidgetPlan.Shape.COMPACT -> {
                    views.setViewVisibility(R.id.widget_header, View.GONE)
                    views.setViewVisibility(R.id.widget_rows, View.GONE)
                    views.setViewVisibility(R.id.widget_lines, View.VISIBLE)
                    shown.forEachIndexed { index, row ->
                        views.addView(
                            R.id.widget_lines,
                            row(context, id, index, R.layout.widget_category_line, row, money(row.availableMinor, false)),
                        )
                    }
                }
                CategoryWidgetPlan.Shape.STRIP -> {
                    views.setViewVisibility(R.id.widget_rows, View.GONE)
                    views.setViewVisibility(R.id.widget_strip, View.VISIBLE)
                    shown.forEachIndexed { index, row ->
                        views.addView(
                            R.id.widget_strip,
                            row(context, id, index, R.layout.widget_category_column, row, money(row.availableMinor, true)),
                        )
                    }
                }
                CategoryWidgetPlan.Shape.LIST -> {
                    views.setViewVisibility(R.id.widget_heads, if (plan.columns) View.VISIBLE else View.GONE)
                    shown.forEachIndexed { index, row ->
                        val layout = if (plan.columns) R.layout.widget_category_row else R.layout.widget_category_row_narrow
                        val line = row(context, id, index, layout, row, money(row.availableMinor, plan.cents))
                        if (index == 0) line.setViewVisibility(R.id.row_rule, View.GONE)
                        if (plan.columns) {
                            line.setTextViewText(R.id.row_budgeted, money(row.budgetedMinor, false))
                            line.setTextViewText(R.id.row_spent, money(-row.spentMinor, false))
                            line.setViewVisibility(R.id.row_budgeted, View.VISIBLE)
                            line.setViewVisibility(R.id.row_spent, View.VISIBLE)
                        }
                        if (plan.bars) {
                            val over = row.availableMinor < 0
                            line.setViewVisibility(if (over) R.id.row_bar_over else R.id.row_bar, View.VISIBLE)
                            if (!over) line.setProgressBar(R.id.row_bar, 100, CategoryWidgetPlan.spentPercent(row), false)
                        }
                        views.addView(R.id.widget_rows, line)
                    }
                    if (plan.total) {
                        val total = shown.sumOf { it.availableMinor }
                        views.setViewVisibility(R.id.widget_footer, View.VISIBLE)
                        views.setTextViewText(R.id.widget_total_label, CategoryWidgetCopy.leftIn(shown.size))
                        val figure = if (total < 0) R.id.widget_total_over else R.id.widget_total
                        views.setViewVisibility(R.id.widget_total, if (total < 0) View.GONE else View.VISIBLE)
                        views.setViewVisibility(R.id.widget_total_over, if (total < 0) View.VISIBLE else View.GONE)
                        views.setTextViewText(figure, money(total, true))
                    }
                }
            }
            return views
        }

        private fun row(
            context: Context,
            id: Int,
            index: Int,
            layout: Int,
            row: CategoryRow,
            amount: String,
        ): RemoteViews {
            val line = RemoteViews(context.packageName, layout)
            val over = row.availableMinor < 0
            line.setTextViewText(R.id.row_name, row.name.lowercase(Locale.getDefault()))
            line.setTextViewText(if (over) R.id.row_amount_over else R.id.row_amount, amount)
            line.setViewVisibility(R.id.row_amount, if (over) View.GONE else View.VISIBLE)
            line.setViewVisibility(R.id.row_amount_over, if (over) View.VISIBLE else View.GONE)
            line.setOnClickPendingIntent(R.id.row, openApp(context, id, row.id, index))
            return line
        }

        private fun monthTitle(
            today: LocalDate,
            plan: CategoryWidgetPlan,
        ): String {
            val month = today.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase(Locale.getDefault())
            return if (plan.shape == CategoryWidgetPlan.Shape.LIST && plan.cents) "$month · AVAILABLE" else month
        }

        /** Opens the app, and with [categoryId] that category's budget page. Each row needs its own request code. */
        private fun openApp(
            context: Context,
            id: Int,
            categoryId: String?,
            index: Int = -1,
        ): PendingIntent {
            val intent =
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (categoryId != null) {
                CategoryWidgetStore.read(context, id)?.budgetId?.let { intent.putExtra(EXTRA_BUDGET, it) }
                intent.putExtra(EXTRA_CATEGORY, categoryId)
            }
            return PendingIntent.getActivity(
                context,
                id * REQUESTS + index + 1,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private fun openPicker(
            context: Context,
            id: Int,
        ): PendingIntent =
            PendingIntent.getActivity(
                context,
                id * REQUESTS + REQUESTS - 1,
                Intent(context, CategoryWidgetPicker::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** Request codes kept for one widget: the app, one per row, and the picker. */
        private const val REQUESTS = 64
    }

    /** What one widget shows: its budget's month and the chosen categories found in it. */
    internal data class Content(
        val budgetId: String,
        val today: LocalDate,
        val currency: CurrencySpec,
        val options: FormatOptions,
        val rows: List<CategoryRow>,
        /** False until categories were chosen for this widget. */
        val chosen: Boolean,
    )
}

internal object CategoryWidgetCopy {
    const val TITLE = "CATEGORY BALANCES"
    const val NO_BUDGET = "open due north to load a budget"
    const val CHOOSE = "tap to choose categories"
    const val GONE = "these categories are gone; tap to choose again"

    fun empty(content: CategoryWidget.Content?): String =
        when {
            content == null -> NO_BUDGET
            !content.chosen -> CHOOSE
            else -> GONE
        }

    /** The days-left chip; [short] on a narrow widget, where the month needs the room. */
    fun daysLeft(
        today: LocalDate,
        short: Boolean,
    ): String {
        val days = CategoryWidgetPlan.daysLeft(today)
        return when {
            days == 1 -> "last day"
            short -> "${days}d"
            else -> "$days days left"
        }
    }

    fun leftIn(count: Int): String = if (count == 1) "left in this one" else "left in these $count"
}

/** Which budget and categories each placed widget shows, kept on this phone by widget id. */
internal object CategoryWidgetStore {
    private const val FILE = "category-widget"

    data class Choice(
        val budgetId: String,
        val categoryIds: List<String>,
    )

    fun read(
        context: Context,
        id: Int,
    ): Choice? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val budget = prefs.getString("$id.budget", null) ?: return null
        val categories =
            prefs
                .getString("$id.categories", null)
                .orEmpty()
                .split(',')
                .filter { it.isNotBlank() }
        return Choice(budget, categories)
    }

    fun save(
        context: Context,
        id: Int,
        choice: Choice,
    ) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit(commit = true) {
            putString("$id.budget", choice.budgetId)
            putString("$id.categories", choice.categoryIds.joinToString(","))
        }
    }

    fun forget(
        context: Context,
        id: Int,
    ) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
            remove("$id.budget")
            remove("$id.categories")
        }
    }
}
