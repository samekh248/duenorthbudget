package app.duenorth.budget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import app.duenorth.budget.core.CategoryTarget
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.RegisterCopy
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroWeights

/**
 * The full-screen category list for budgets with more than five choices (FR-011, FR-012), under a focused
 * filter box that holds the current category's name, selected, so typing replaces it and narrows the list.
 */
@Composable
internal fun CategoryListPage(
    options: CategoryOptions,
    onPick: (String?) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    RememberBarOverride(emptyList())
    val filter = rememberCategoryFilter(options.current?.let(options::name).orEmpty())
    val query = filter.query
    val rows =
        remember(options, query) {
            buildList {
                if (query.isBlank() && options.suggested.isNotEmpty()) {
                    add(CategoryRow.Group(RegisterCopy.SUGGESTED, "suggested"))
                    options.suggested.forEach { add(CategoryRow.Choice(it, suggested = true)) }
                    add(CategoryRow.Group(RegisterCopy.ALL_CATEGORIES, "all"))
                }
                if (query.isBlank()) add(CategoryRow.None)
                options.groups.forEachIndexed { index, (name, choices) ->
                    val shown = CategoryFilter.choices(choices, query)
                    if (shown.isNotEmpty()) {
                        add(CategoryRow.Group(name, "$index"))
                        shown.forEach { add(CategoryRow.Choice(it.id)) }
                    }
                }
                if (query.isNotBlank() && CategoryFilter.showsNone(query)) add(CategoryRow.None)
            }
        }
    // The list opens on the current category, or at the top when the suggested group leads it.
    val start =
        remember(options) {
            if (options.suggested.isNotEmpty()) {
                0
            } else {
                rows.indexOfFirst { it is CategoryRow.Choice && it.id == options.current }.coerceAtLeast(0)
            }
        }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
    LaunchedEffect(query) { if (filter.typed) state.scrollToItem(0) }
    val enter = {
        if (!filter.typed) {
            onPick(options.current)
        } else {
            when (val top = rows.firstOrNull { it !is CategoryRow.Group }) {
                CategoryRow.None -> onPick(null)
                is CategoryRow.Choice -> onPick(top.id)
                else -> Unit
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(horizontal = MetroDimens.Gutter)
            .testTag("transaction-category-list"),
    ) {
        MetroText(TransactionFormCopy.CATEGORY, Metro.typography.header, maxLines = 1)
        CategoryFilterField(filter, enter, Modifier.padding(top = MetroDimens.Gutter))
        LazyColumn(Modifier.weight(1f).padding(top = MetroDimens.Gutter), state = state) {
            items(rows.size) { index ->
                when (val entry = rows[index]) {
                    CategoryRow.None ->
                        CategoryListRow(RegisterCopy.NO_CATEGORY, options.current == null, "transaction-category-none") { onPick(null) }
                    is CategoryRow.Group ->
                        CategoryGroupLabel(entry.name, "transaction-category-group-${entry.key}", afterSuggestions = entry.key == "all")
                    is CategoryRow.Choice -> {
                        val tag = if (entry.suggested) "transaction-category-suggested-${entry.id}" else "transaction-category-${entry.id}"
                        CategoryListRow(options.label(entry.id), entry.id == options.current, tag) { onPick(entry.id) }
                    }
                }
            }
        }
    }
}

/** A line of the full-screen category list: "no category", a group's caption, or a category to pick. */
private sealed interface CategoryRow {
    data object None : CategoryRow

    data class Group(
        val name: String,
        val key: String,
    ) : CategoryRow

    data class Choice(
        val id: String,
        val suggested: Boolean = false,
    ) : CategoryRow
}

/**
 * The caption over a run of categories: a group's name, or the suggested group's. The caption that follows the suggestions
 * opens with a wider gap, so the suggestions read as their own block above the full list.
 */
@Composable
private fun CategoryGroupLabel(
    name: String,
    tag: String,
    afterSuggestions: Boolean = false,
) {
    // Windows 10 Mobile heads each group as its list views do: body type, semibold, in the accent.
    val mobile10 = Metro.style.mobile10
    MetroText(
        name.ifBlank { " " },
        if (mobile10) Metro.typography.body.copy(fontWeight = MetroWeights.Semibold) else Metro.typography.caption,
        Modifier
            .padding(top = if (afterSuggestions) MetroDimens.Grid + MetroDimens.Gutter else MetroDimens.Gutter)
            .then(if (mobile10) Modifier.padding(horizontal = MetroDimens.Gutter) else Modifier)
            .testTag(tag),
        color = if (mobile10) Metro.accent.text else Metro.colors.secondary,
    )
}

/** A category choice: Metro's subheader, or under Windows 10 Mobile the body type of a W10M list view. */
private val choiceStyle: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = if (Metro.style.mobile10) Metro.typography.body else Metro.typography.subheader

/**
 * How a category choice shows it is the current one. Metro colours the choice in the accent; Windows 10 Mobile, whose
 * group captions already wear the accent, marks it as its list views do, with an accent-tinted band behind foreground text.
 */
@Composable
private fun Modifier.choiceSelection(selected: Boolean): Modifier =
    if (!Metro.style.mobile10) {
        this
    } else {
        val band = if (selected) background(Metro.accent.fill.copy(alpha = 0.4f)) else this
        band.padding(horizontal = MetroDimens.Gutter)
    }

/** A choice's ink: the accent for the current one, except under Windows 10 Mobile, whose band marks it instead. */
@Composable
@ReadOnlyComposable
private fun choiceColor(selected: Boolean) = if (selected && !Metro.style.mobile10) Metro.accent.text else Metro.colors.foreground

@Composable
private fun CategoryListRow(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    MetroText(
        label,
        choiceStyle,
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick)
            .choiceSelection(selected)
            .padding(vertical = MetroDimens.Row)
            .testTag(tag),
        color = choiceColor(selected),
        maxLines = 2,
    )
}

@Composable
fun CategoryPickerScreen(
    target: CategoryTarget,
    error: String?,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = target.categoryId?.let { id -> target.categories.firstOrNull { it.id == id } }
    val filter = rememberCategoryFilter(current?.name.orEmpty())
    val query = filter.query
    val shown = remember(target.categories, query) { CategoryFilter.choices(target.categories, query) }
    val none = CategoryFilter.showsNone(query)
    val enter = {
        when {
            !filter.typed -> onPick(current?.id)
            query.isBlank() && none -> onPick(null)
            shown.isNotEmpty() -> onPick(shown.first().id)
            none -> onPick(null)
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
    ) {
        MetroText(target.payee, Metro.typography.header, maxLines = 2)
        MetroText(
            MoneyFormat.format(target.amountMinor, target.currency, formatOptions()),
            Metro.typography.subheader,
            color = Metro.accent.text,
        )
        if (error != null) {
            MetroText(error, Metro.typography.body, Modifier.padding(top = MetroDimens.Gutter), color = Metro.accent.text)
        }
        CategoryFilterField(filter, enter, Modifier.padding(top = MetroDimens.Grid))
        val list = rememberLazyListState()
        LaunchedEffect(query) { if (filter.typed) list.scrollToItem(0) }
        val suggested =
            remember(target.categories, target.suggested) {
                val byId = target.categories.associateBy { it.id }
                target.suggested.mapNotNull(byId::get)
            }
        LazyColumn(Modifier.weight(1f).padding(top = MetroDimens.Gutter), state = list) {
            if (query.isBlank() && suggested.isNotEmpty()) {
                item(key = "suggested") { CategoryGroupLabel(RegisterCopy.SUGGESTED, "category-group-suggested") }
                items(suggested, key = { "suggested-${it.id}" }) { category ->
                    ChoiceLine(category.name, category.id == current?.id, Modifier.testTag("category-suggested-${category.id}")) {
                        onPick(category.id)
                    }
                }
                item(key = "all") { CategoryGroupLabel(RegisterCopy.ALL_CATEGORIES, "category-group-all", afterSuggestions = true) }
            }
            if (query.isBlank()) {
                item { ChoiceLine(RegisterCopy.NO_CATEGORY, current == null) { onPick(null) } }
            }
            items(shown, key = { it.id }) { category ->
                ChoiceLine(category.name, category.id == current?.id) { onPick(category.id) }
            }
            if (query.isNotBlank() && none) {
                item { ChoiceLine(RegisterCopy.NO_CATEGORY, current == null) { onPick(null) } }
            }
        }
    }
}

@Composable
private fun ChoiceLine(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    MetroText(
        label,
        choiceStyle,
        modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick)
            .choiceSelection(selected)
            .padding(vertical = MetroDimens.Row),
        color = choiceColor(selected),
        maxLines = 2,
    )
}
