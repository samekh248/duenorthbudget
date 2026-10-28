package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.ImportCopy
import app.duenorth.budget.core.ImportPreview
import app.duenorth.budget.core.ImportPreviewRow
import app.duenorth.budget.core.ImportReviewPage
import app.duenorth.budget.core.ImportReviewRow
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.PreviewRowStatus
import app.duenorth.budget.core.RegisterCopy
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

@Composable
fun ImportPreviewScreen(
    preview: ImportPreview,
    error: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
    ) {
        MetroText("import", Metro.typography.header)
        MetroText(
            "${ImportCopy.PREVIEW_ADDING} ${preview.adding} · ${ImportCopy.PREVIEW_SKIPPING} ${preview.skipping}",
            Metro.typography.caption,
            color = Metro.colors.secondary,
            modifier = Modifier.testTag("import-preview-counts"),
        )
        if (error != null) {
            MetroText(error, Metro.typography.body, color = Metro.accent.text)
        }
        LazyColumn(Modifier.weight(1f).padding(top = MetroDimens.Grid)) {
            items(preview.rows, key = { it.index }) { row ->
                PreviewRowLine(row, preview)
            }
        }
        // save/cancel live on the application bar
    }
}

@Composable
private fun PreviewRowLine(
    row: ImportPreviewRow,
    preview: ImportPreview,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = MetroDimens.Row),
    ) {
        Column(Modifier.weight(1f)) {
            MetroText(row.payee, Metro.typography.body, maxLines = 2)
            MetroText(
                RegisterEntry.registerDateLabel(row.date, dateFormat()),
                Metro.typography.caption,
                color = Metro.colors.secondary,
            )
        }
        Column {
            MetroText(
                MoneyFormat.format(row.amountMinor, preview.currency, formatOptions()),
                Metro.typography.body,
                color = Metro.accent.text,
            )
            if (row.status == PreviewRowStatus.Duplicate) {
                MetroText(ImportCopy.DUPLICATE, Metro.typography.caption, color = Metro.colors.secondary)
            }
        }
    }
}

@Composable
fun ImportReviewScreen(
    page: ImportReviewPage,
    error: String?,
    onCategory: (String, String?) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
    ) {
        MetroText(ImportCopy.REVIEW_HEADER, Metro.typography.header)
        MetroText(
            "${page.uncategorizedCount} uncategorized",
            Metro.typography.caption,
            color = Metro.colors.secondary,
            modifier = Modifier.testTag("import-review-inbox"),
        )
        if (error != null) {
            MetroText(error, Metro.typography.body, color = Metro.accent.text)
        }
        LazyColumn(Modifier.weight(1f).padding(top = MetroDimens.Grid), state = listState) {
            items(page.rows, key = { it.id }) { row ->
                ReviewRowEditor(row, page.categories, page, onCategory)
            }
        }
        PageBar(listOf(ScreenAction("import-done", AppGlyph.Save, ImportCopy.DONE, run = onDone)))
    }
}

@Composable
private fun ReviewRowEditor(
    row: ImportReviewRow,
    categories: List<CategoryChoice>,
    page: ImportReviewPage,
    onCategory: (String, String?) -> Unit,
) {
    var expanded by remember(row.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .metroPress { expanded = !expanded }
            .padding(vertical = MetroDimens.Row)
            .testTag("import-review-row-${row.id}"),
    ) {
        MetroText(row.payee, Metro.typography.body, maxLines = 2)
        MetroText(
            MoneyFormat.format(row.amountMinor, page.currency, formatOptions()),
            Metro.typography.caption,
            color = Metro.accent.text,
        )
        MetroText(row.categoryLabel, Metro.typography.caption, color = Metro.colors.secondary)
        if (expanded) {
            ChoiceLine(RegisterCopy.NO_CATEGORY, row.categoryId == null) { onCategory(row.id, null) }
            categories.forEach { category ->
                ChoiceLine(category.name, category.id == row.categoryId) { onCategory(row.id, category.id) }
            }
        }
    }
}

@Composable
private fun ChoiceLine(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    MetroText(
        if (selected) "• $label" else label,
        Metro.typography.body,
        Modifier
            .fillMaxWidth()
            .metroPress(onClick = onClick)
            .padding(vertical = MetroDimens.Row),
        color = if (selected) Metro.accent.text else Metro.colors.foreground,
    )
}
