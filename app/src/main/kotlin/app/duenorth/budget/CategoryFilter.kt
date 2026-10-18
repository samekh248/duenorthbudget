package app.duenorth.budget

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.RegisterCopy
import app.duenorth.budget.design.components.LocalFieldSubmit
import app.duenorth.budget.design.components.MetroField

/**
 * The filter box atop a category list. It opens holding the current category's name, selected, so the
 * first key typed replaces it; until then the whole list shows.
 */
@Stable
class CategoryFilterState(
    initial: String,
) {
    var value by mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length)))
        private set

    /** The user has typed: the box's text now filters the list. */
    var typed by mutableStateOf(false)
        private set

    /** What the list is filtered by, blank while the current category's name still sits untouched. */
    val query: String get() = if (typed) value.text.trim() else ""

    fun change(next: TextFieldValue) {
        if (next.text != value.text) typed = true
        value = next
    }
}

@Composable
fun rememberCategoryFilter(initial: String): CategoryFilterState = remember { CategoryFilterState(initial) }

object CategoryFilter {
    const val HINT = "category"

    /** The categories whose name holds [query], in list order, ignoring case; all of them for a blank query. */
    fun <T> matches(
        rows: List<T>,
        query: String,
        name: (T) -> String,
    ): List<T> = if (query.isBlank()) rows else rows.filter { name(it).contains(query.trim(), ignoreCase = true) }

    /** "No category" heads the full list; while filtering it shows, last, only once [query] begins its name. */
    fun showsNone(query: String): Boolean = query.isBlank() || RegisterCopy.NO_CATEGORY.startsWith(query.trim(), ignoreCase = true)

    fun choices(
        categories: List<CategoryChoice>,
        query: String,
    ): List<CategoryChoice> = matches(categories, query) { it.name }
}

/**
 * The [state]'s box, focused with the keyboard up when the list opens. Enter picks through [onEnter]: the
 * top match while filtering, the current category before anything is typed.
 */
@Composable
fun CategoryFilterField(
    state: CategoryFilterState,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    CompositionLocalProvider(LocalFieldSubmit provides onEnter) {
        MetroField(
            state.value,
            state::change,
            CategoryFilter.HINT,
            modifier.focusRequester(focus).testTag("category-filter"),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        )
    }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
}
