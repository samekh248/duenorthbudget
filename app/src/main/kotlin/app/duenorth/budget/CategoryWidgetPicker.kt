package app.duenorth.budget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.PhoneSettings
import app.duenorth.budget.core.PinnedCategories
import app.duenorth.budget.core.ThemeMode
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.MetroSectionHeading
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.components.SectionSize
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Accent
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroStyle
import app.duenorth.budget.design.theme.MetroTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Chooses the categories a [CategoryWidget] shows, when the widget is placed and again from its title. Starts from
 * the widget's own choice, or the categories pinned to the assistant for a new widget.
 */
class CategoryWidgetPicker : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id =
            intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out of a new widget's picker leaves the widget unplaced.
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val library = (application as DueNorthApplication).library
        setContent {
            var loaded by remember { mutableStateOf<PickerLoad?>(null) }
            var chosen by remember { mutableStateOf<Set<String>>(emptySet()) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                val load =
                    withContext(Dispatchers.IO) {
                        val settings = library.settings()
                        val saved = CategoryWidgetStore.read(this@CategoryWidgetPicker, id)
                        val budgetId = saved?.budgetId ?: settings.openBudgetId
                        val shell = budgetId?.let { runCatching { library.readShell(it) }.getOrNull() }
                        val options = budgetId?.let { runCatching { library.readFormatPrefs(it).options }.getOrNull() } ?: FormatOptions()
                        PickerLoad(
                            settings = settings,
                            budgetId = shell?.budgetId,
                            groups = shell?.groups.orEmpty(),
                            currency = shell?.currency,
                            options = options,
                            start = saved?.categoryIds ?: PinnedCategories.ids(settings, budgetId),
                        )
                    }
                chosen = load.start.toSet()
                loaded = load
            }
            val settings = loaded?.settings ?: PhoneSettings()
            val dark =
                when (ThemeMode.fromStored(settings.themeMode)) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            MetroTheme(darkTheme = dark, accent = Accent.fromId(settings.accent), style = MetroStyle.fromId(settings.style)) {
                val load = loaded
                CategoryWidgetPickerScreen(
                    groups = load?.groups.orEmpty(),
                    chosen = chosen,
                    currency = load?.currency,
                    options = load?.options ?: FormatOptions(),
                    loading = load == null,
                    onToggle = { category -> chosen = if (category in chosen) chosen - category else chosen + category },
                    onCancel = { finish() },
                    onDone =
                        done@{
                            val budgetId = load?.budgetId ?: return@done
                            // Budget order, so the widget reads like the budget page.
                            val ordered =
                                load.groups
                                    .flatMap { it.categories }
                                    .map { it.id }
                                    .filter { it in chosen }
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    CategoryWidgetStore.save(this@CategoryWidgetPicker, id, CategoryWidgetStore.Choice(budgetId, ordered))
                                    runCatching {
                                        CategoryWidget.draw(
                                            this@CategoryWidgetPicker,
                                            AppWidgetManager.getInstance(this@CategoryWidgetPicker),
                                            id,
                                        )
                                    }
                                }
                                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                finish()
                            }
                        },
                )
            }
        }
    }

    private data class PickerLoad(
        val settings: PhoneSettings,
        val budgetId: String?,
        val groups: List<GroupRow>,
        val currency: CurrencySpec?,
        val options: FormatOptions,
        val start: List<String>,
    )
}

internal object CategoryWidgetPickerCopy {
    const val TITLE = "CATEGORY WIDGET"
    const val HEADER = "categories"
    const val HINT = "the widget shows what is left this month in the ones you tick, in budget order, as many as its size fits."
    const val NO_BUDGET = "open due north and load a budget first."
    const val DONE = "done"
    const val CANCEL = "cancel"
}

/** The picker page: every visible category under its group, with a box to tick and this month's balance. */
@Composable
internal fun CategoryWidgetPickerScreen(
    groups: List<GroupRow>,
    chosen: Set<String>,
    currency: CurrencySpec?,
    options: FormatOptions,
    loading: Boolean,
    onToggle: (String) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = groups.filter { !it.hidden }.map { g -> g to g.categories.filter { !it.hidden } }.filter { it.second.isNotEmpty() }
    Column(modifier.fillMaxSize().background(Metro.colors.background).statusBarsPadding()) {
        MetroText(
            CategoryWidgetPickerCopy.TITLE,
            Metro.typography.pageTitle,
            Modifier.padding(start = MetroDimens.Gutter, top = MetroDimens.Row, end = MetroDimens.Gutter),
            maxLines = 1,
            softWrap = false,
        )
        MetroText(
            CategoryWidgetPickerCopy.HEADER,
            Metro.typography.sectionHeader,
            Modifier.padding(horizontal = MetroDimens.Gutter),
            maxLines = 1,
            softWrap = false,
        )
        MetroText(
            if (!loading && shown.isEmpty()) CategoryWidgetPickerCopy.NO_BUDGET else CategoryWidgetPickerCopy.HINT,
            Metro.typography.caption,
            Modifier.padding(start = MetroDimens.Gutter, end = MetroDimens.Gutter, bottom = MetroDimens.Row),
            color = Metro.colors.secondary,
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("widget-picker-list")) {
            shown.forEach { (group, categories) ->
                item(key = "g-${group.id}") {
                    MetroSectionHeading(group.name, Modifier.padding(horizontal = MetroDimens.Gutter), size = SectionSize.PAGE)
                }
                items(categories, key = { it.id }) { category ->
                    val on = category.id in chosen
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .metroPress { onToggle(category.id) }
                            .semantics(mergeDescendants = true) {
                                role = Role.Checkbox
                                selected = on
                            }.padding(horizontal = MetroDimens.Gutter, vertical = 10.dp)
                            .testTag("widget-pick-${category.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PickBox(on)
                        MetroText(category.name, Metro.typography.body, Modifier.weight(1f), maxLines = 1, softWrap = false)
                        LocalFormat(options) { CategoryBalance(category.availableMinor, currency, Modifier) }
                    }
                }
            }
        }
        val actions =
            buildList {
                if (chosen.isNotEmpty() && !loading) add(ScreenAction("done", AppGlyph.Save, CategoryWidgetPickerCopy.DONE, run = onDone))
                add(ScreenAction("cancel", AppGlyph.Cancel, CategoryWidgetPickerCopy.CANCEL, run = onCancel))
            }
        MetroAppBar(actions, expanded, { expanded = it })
    }
}

/** The budget's number format for [CategoryBalance], which reads it from the app's local. */
@Composable
private fun LocalFormat(
    options: FormatOptions,
    content: @Composable () -> Unit,
) {
    val prefs =
        app.duenorth.budget.core
            .FormatPrefs(hideFraction = options.hideFraction, numberFormat = options.numberFormat)
    androidx.compose.runtime.CompositionLocalProvider(LocalFormatPrefs provides prefs) { content() }
}

/** A Metro check box: an outlined square, filled with the accent and a check when ticked. */
@Composable
private fun PickBox(on: Boolean) {
    val ink = Metro.colors.foreground
    val accent = Metro.accent
    Box(
        Modifier
            .size(24.dp)
            .then(if (on) Modifier.background(accent.fill) else Modifier.border(2.dp, ink)),
    ) {
        if (on) {
            Canvas(Modifier.fillMaxSize().padding(5.dp)) {
                val w = size.width
                val h = size.height
                val stroke = 2.5.dp.toPx()
                drawLine(accent.onFill, Offset(0f, h * 0.55f), Offset(w * 0.38f, h * 0.9f), stroke, StrokeCap.Square)
                drawLine(accent.onFill, Offset(w * 0.38f, h * 0.9f), Offset(w, h * 0.1f), stroke, StrokeCap.Square)
            }
        }
    }
}
