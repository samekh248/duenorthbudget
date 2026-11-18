package app.duenorth.budget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.RegisterCopy
import app.duenorth.budget.core.SavedSplit
import app.duenorth.budget.core.SavedSplitCopy
import app.duenorth.budget.core.SavedSplits
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroSegmentedToggle
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

/** "Groceries 60% · Household the rest": each line's category and what it takes, for a saved split's row. */
internal fun savedSplitSummary(
    split: SavedSplit,
    categories: Map<String, String>,
    currency: CurrencySpec,
    format: FormatOptions,
): String =
    SavedSplits.summary(
        split,
        { id -> id?.let(categories::get) ?: RegisterCopy.NO_CATEGORY },
        { MoneyFormat.format(it, currency, format) },
    )

/** The category names a saved split's summary uses, by id. */
internal fun List<CategoryChoice>.namesById(): Map<String, String> = associate { it.id to it.name }

/**
 * The transaction form's saved split list, drawn over the form: each saved split, with what its lines take. A tap fills
 * the form's split lines with it.
 */
@Composable
internal fun SavedSplitListPage(
    splits: List<SavedSplit>,
    summary: (SavedSplit) -> String,
    onPick: (SavedSplit) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    RememberBarOverride(emptyList())
    Column(
        Modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MetroDimens.Gutter)
            .testTag("saved-split-list"),
    ) {
        MetroText(SavedSplitCopy.TITLE, Metro.typography.header, maxLines = 1)
        Column(Modifier.padding(top = MetroDimens.Gutter)) {
            splits.forEach { split ->
                SavedSplitRow(split, summary(split), Modifier.metroPress { onPick(split) }.testTag("saved-split-pick-${split.id}"))
            }
        }
    }
}

/**
 * Save this split, drawn over the form: a name, and whether each line keeps its amount or its share of the total.
 * The bar's save keeps it; a name already used replaces that split.
 */
@Composable
internal fun SaveSplitPage(
    initialName: String,
    onSave: (name: String, shares: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    var name by remember { mutableStateOf(initialName) }
    var shares by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val save by rememberUpdatedState {
        if (name.isBlank()) error = SavedSplitCopy.NAME_FIRST else onSave(name.trim(), shares)
    }
    val cancel by rememberUpdatedState(onCancel)
    val bar =
        remember {
            listOf(
                ScreenAction("saved-split-save", AppGlyph.Save, TransactionFormCopy.DONE) { save() },
                ScreenAction("saved-split-cancel", AppGlyph.Cancel, TransactionFormCopy.CANCEL) { cancel() },
            )
        }
    RememberBarOverride(bar)
    Column(
        Modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MetroDimens.Gutter)
            .testTag("saved-split-save-page"),
    ) {
        MetroText(SavedSplitCopy.SAVE_TITLE, Metro.typography.header, maxLines = 1)
        LabeledField(SavedSplitCopy.NAME, error, Modifier.padding(top = MetroDimens.FieldGroupGap)) {
            MetroField(
                name,
                {
                    name = it
                    error = null
                },
                SavedSplitCopy.NAME,
                Modifier.testTag("saved-split-name"),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
        }
        LabeledField(SavedSplitCopy.KEEP, null, Modifier.padding(top = MetroDimens.FieldGroupGap)) {
            MetroSegmentedToggle(
                first = SavedSplitCopy.AMOUNTS,
                second = SavedSplitCopy.SHARES,
                firstSelected = !shares,
                onSelect = { shares = !it },
                tag = "saved-split-keep",
            )
            MetroText(
                if (shares) SavedSplitCopy.SHARES_HINT else SavedSplitCopy.AMOUNTS_HINT,
                Metro.typography.caption,
                Modifier.padding(top = MetroDimens.FieldLabelGap).testTag("saved-split-keep-hint"),
                color = Metro.colors.secondary,
            )
        }
    }
}

/** Settings › general › saved splits: every saved split, with what it takes and who it is suggested for, to delete. */
@Composable
internal fun SavedSplitsScreen(
    splits: List<SavedSplit>,
    summary: (SavedSplit) -> String,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(Metro.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MetroDimens.Gutter)
            .testTag("saved-splits"),
    ) {
        MetroText(SavedSplitCopy.TITLE, Metro.typography.header, maxLines = 1)
        if (splits.isEmpty()) {
            MetroText(
                SavedSplitCopy.NONE,
                Metro.typography.body,
                Modifier.padding(top = MetroDimens.Gutter).testTag("saved-splits-empty"),
                color = Metro.colors.secondary,
            )
        }
        Column(Modifier.padding(top = MetroDimens.Gutter)) {
            splits.forEach { split ->
                SavedSplitRow(split, summary(split), Modifier.testTag("saved-split-${split.id}")) {
                    Box(
                        Modifier
                            .size(MetroDimens.TouchTarget)
                            .metroPress { onDelete(split.id) }
                            .semantics { contentDescription = "${SavedSplitCopy.DELETE} ${split.name}" }
                            .testTag("saved-split-delete-${split.id}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppGlyphIcon(AppGlyph.Delete, Metro.colors.secondary)
                    }
                }
            }
        }
    }
}

/** One saved split: its name, what each line takes, and the payees it is suggested for. */
@Composable
private fun SavedSplitRow(
    split: SavedSplit,
    summary: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            MetroText(split.name, Metro.typography.groupHeader, maxLines = 1)
            MetroText(summary, Metro.typography.caption, maxLines = 2)
            MetroText(
                if (split.payees.isEmpty()) {
                    SavedSplitCopy.ANY_PAYEE
                } else {
                    "${SavedSplitCopy.MATCHES} ${split.payees.joinToString(", ")}"
                },
                Metro.typography.caption,
                color = Metro.colors.secondary,
                maxLines = 2,
            )
        }
        trailing()
    }
}
