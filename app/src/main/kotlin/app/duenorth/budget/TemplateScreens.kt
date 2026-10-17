package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.TemplateCopy
import app.duenorth.budget.core.TemplateResult
import app.duenorth.budget.core.monthLabel
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import java.time.YearMonth

/**
 * Check, apply, and overwrite from category notes (spec 010 US5). Bad lines are listed by
 * category, and nothing is written while any remain.
 */
@Composable
fun TemplatesScreen(
    month: YearMonth,
    result: TemplateResult?,
    busy: Boolean,
    confirm: String?,
    onCheck: () -> Unit,
    onApply: () -> Unit,
    onOverwrite: () -> Unit,
    onConfirm: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(MetroDimens.Gutter)
            .testTag("templates-screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText(TemplateCopy.TITLE, Metro.typography.header)
        MetroText(monthLabel(month), Metro.typography.caption, color = Metro.colors.secondary)
        if (confirm != null) {
            MetroText(confirm, Metro.typography.subheader, Modifier.testTag("templates-confirm"))
            MetroButtonGrid {
                MetroButton(TemplateCopy.OVERWRITE, Modifier.testTag("templates-confirm-yes"), onClick = onConfirm)
                MetroButton(MonthActionCopy.KEEP, Modifier.testTag("templates-confirm-keep"), onClick = onKeep)
            }
            return@Column
        }
        TemplateOutcome(result, busy)
        MetroButtonGrid {
            MetroButton(TemplateCopy.APPLY, Modifier.testTag("templates-apply"), onClick = onApply)
            MetroButton(TemplateCopy.OVERWRITE, Modifier.testTag("templates-overwrite"), onClick = onOverwrite)
            MetroButton(TemplateCopy.CHECK, Modifier.testTag("templates-check"), onClick = onCheck)
        }
    }
}

@Composable
private fun TemplateOutcome(
    result: TemplateResult?,
    busy: Boolean,
) {
    val tag = Modifier.testTag("templates-result")
    when {
        busy || result == null -> MetroText("…", Metro.typography.subheader, tag, color = Metro.colors.secondary)
        result == TemplateResult.Valid -> MetroText(TemplateCopy.ALL_VALID, Metro.typography.subheader, tag)
        result is TemplateResult.Applied -> MetroText(TemplateCopy.APPLIED, Metro.typography.subheader, tag)
        result is TemplateResult.Rejected -> MetroText(result.reason, Metro.typography.subheader, tag, color = Metro.accent.text)
        result is TemplateResult.NeedsConfirm -> MetroText(result.reason, Metro.typography.subheader, tag)
        result is TemplateResult.Invalid ->
            Column(tag, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                result.errors.forEachIndexed { index, error ->
                    MetroText(error.categoryName, Metro.typography.subheader, Modifier.testTag("templates-error-$index"))
                    MetroText(error.line, Metro.typography.body, color = Metro.accent.text)
                }
            }
    }
}

/** A category's note; template lines in it budget the category (spec 010 T061). */
@Composable
fun CategoryNoteEditor(
    note: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(note) { mutableStateOf(note) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetroText(TemplateCopy.NOTE, Metro.typography.caption, color = Metro.colors.secondary)
        MetroField(text, { text = it }, TemplateCopy.NOTE, Modifier.testTag("category-note"), lines = 3..8)
        if (text != note) {
            MetroButton("save ${TemplateCopy.SAVE_NOTE}", Modifier.testTag("category-note-save")) { onSave(text) }
        }
    }
}
