package app.duenorth.budget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.LocalFieldSubmit
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.ScreenAction

/**
 * A category's name, open for typing in place of its title on the category's page. The bar turns to save and
 * cancel while it is open; enter saves and back cancels. The name starts selected, so typing replaces it.
 * Tag `category-rename-field`.
 */
@Composable
internal fun CategoryRenameField(
    name: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(name, TextRange(0, name.length))) }
    val save by rememberUpdatedState { onSave(text.text) }
    val cancel by rememberUpdatedState(onCancel)
    val bar =
        remember {
            listOf(
                ScreenAction("rename-save", AppGlyph.Save, CategoryManageCopy.SAVE) { save() },
                ScreenAction("rename-cancel", AppGlyph.Cancel, CategoryManageCopy.CANCEL) { cancel() },
            )
        }
    RememberBarOverride(bar)
    BackHandler { cancel() }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    CompositionLocalProvider(LocalFieldSubmit provides { save() }) {
        MetroField(
            text,
            { text = it },
            CategoryManageCopy.NAME,
            modifier.fillMaxWidth().focusRequester(focus).testTag("category-rename-field"),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
}
