package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/** The move screen's amount: it opens holding everything available, selected and focused, so typing replaces it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MoveAmountContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    private var saved: Pair<String, String>? = null

    private fun show(from: String = "c-gas") {
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    MoveCategoryScreen(
                        shell = sample(),
                        fromCategoryId = from,
                        error = null,
                        onSave = { to, amount -> saved = to to amount },
                        initialTo = "c-dining",
                    )
                }
            }
        }
    }

    private fun box() = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("move-amount")))

    private fun text() = box().fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun selection() = box().fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]

    private fun move() {
        compose.onNodeWithText("move", ignoreCase = true).performClick()
        compose.waitForIdle()
    }

    // Gas has 76.40 available: the box opens with all of it, wholly selected, and has the focus.
    @Test
    fun opensWithEverythingAvailableSelectedAndFocused() {
        show()
        compose.waitForIdle()
        assertEquals("76.40", text())
        assertEquals(TextRange(0, 5), selection())
        box().assertIsFocused()
        // The old "all" quick pick is gone; the box itself starts at all.
        compose.onNodeWithTag("move-all").assertDoesNotExist()
        compose.onNodeWithTag("move-from-available").assertExists()
    }

    // Saving untouched moves everything available.
    @Test
    fun savingUntouchedMovesItAll() {
        show()
        move()
        assertEquals("c-dining" to "76.40", saved)
    }

    // The first key typed replaces the whole amount, and later keys add to it.
    @Test
    fun typingReplacesTheAmount() {
        show()
        box().performTextInput("2")
        box().performTextInput("5")
        compose.waitForIdle()
        assertEquals("25", text())
        move()
        assertEquals("c-dining" to "25", saved)
    }

    // The amount box still takes only an amount: a letter typed over the selection leaves it as it was.
    @Test
    fun aLetterLeavesTheAmountSelected() {
        show()
        box().performTextInput("a")
        compose.waitForIdle()
        assertEquals("76.40", text())
        assertEquals(TextRange(0, 5), selection())
        box().performTextInput("12.345")
        compose.waitForIdle()
        assertEquals("76.40", text())
    }

    // A category with nothing available opens empty, still focused for typing.
    @Test
    fun nothingAvailableOpensEmpty() {
        show(from = "c-groceries")
        compose.waitForIdle()
        assertEquals("", text())
        box().assertIsFocused()
    }

    @Test
    fun prefillIsEverythingAvailableOrNothing() {
        val prefs = FormatPrefs()
        assertEquals("1,234.56", moveAllPrefill(123_456, 2, prefs))
        assertEquals("", moveAllPrefill(0, 2, prefs))
        assertEquals("", moveAllPrefill(-500, 2, prefs))
        assertEquals(TextRange(0, 4), selectedAmount("1.50").selection)
    }

    @Test
    fun filterAmountKeepsCursorAndSelection() {
        val format = FormatOptions.NUMBER_COMMA_DOT
        val all = selectedAmount("76.40")
        // A selection change alone goes through.
        val moved = all.copy(selection = TextRange(2))
        assertEquals(moved, filterAmount(all, moved, format, 2))
        // A digit over the selection replaces it.
        val typed = TextFieldValue("7", TextRange(1))
        assertEquals(typed, filterAmount(all, typed, format, 2))
        // A letter is refused.
        assertNull(filterAmount(all, TextFieldValue("x", TextRange(1)), format, 2))
        // Pasted whitespace is dropped, and the cursor follows the text it kept.
        assertEquals(
            TextFieldValue("1234", TextRange(4)),
            filterAmount(TextFieldValue(""), TextFieldValue(" 12 34 ", TextRange(6)), format, 2),
        )
    }

    private fun sample(): MonthShell =
        MonthShell(
            budgetId = "home",
            budgetName = "Home",
            mode = BudgetMode.ENVELOPE,
            currency = Currencies.byCode("USD")!!,
            month = YearMonth.of(2026, 10),
            headerLabel = "to budget",
            headerMinor = 42_000,
            bufferedMinor = 0,
            groups =
                listOf(
                    GroupRow(
                        "g-every",
                        "Everyday",
                        7_640,
                        listOf(CategoryRow("c-gas", "Gas", 12_000, 4_360, 7_640, false)),
                    ),
                    GroupRow(
                        "g-food",
                        "Food",
                        -3_200,
                        listOf(
                            CategoryRow("c-groceries", "Groceries", 20_000, 28_000, -8_000, false),
                            CategoryRow("c-dining", "Dining", 8_000, 3_200, 4_800, false),
                        ),
                    ),
                ),
            accounts = emptyList(),
            inbox = emptyList(),
        )
}
