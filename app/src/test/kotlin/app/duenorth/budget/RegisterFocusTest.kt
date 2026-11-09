package app.duenorth.budget

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Opening the register at a transaction (from a category's recent spending) scrolls to it and flashes it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class RegisterFocusTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun itemsCountHeadingsPendingAndPairs() {
        val days = listOf(listOf("a", "b"), listOf("c"), listOf("d", "e", "f"))
        // Plain list: each day is a heading and its rows.
        assertEquals(FocusItems(0, 2), focusItems("b", emptyList(), emptyList(), true, days, cards = false))
        assertEquals(FocusItems(5, 8), focusItems("f", emptyList(), emptyList(), true, days, cards = false))
        // Cards: a day is one item.
        assertEquals(FocusItems(2, 2), focusItems("f", emptyList(), emptyList(), true, days, cards = true))
        // A pair, and the pending heading with its rows open, come first.
        val pairs = listOf(listOf("m1", "m2"))
        val pending = listOf("p1", "p2")
        assertEquals(FocusItems(0, 0), focusItems("m2", pairs, pending, true, days, cards = false))
        assertEquals(FocusItems(1, 3), focusItems("p2", pairs, pending, true, days, cards = false))
        assertEquals(FocusItems(4, 5), focusItems("a", pairs, pending, true, days, cards = false))
        assertEquals(FocusItems(1, 2), focusItems("p2", pairs, pending, true, days, cards = true))
        assertEquals(FocusItems(3, 3), focusItems("a", pairs, pending, true, days, cards = true))
        // Folded pending: its heading is the item, and it adds nothing after it.
        assertEquals(FocusItems(2, 4), focusItems("b", pairs, pending, false, days, cards = false))
        assertNull(focusItems("zz", pairs, pending, true, days, cards = false))
    }

    @Test
    fun scrollsAnOldTransactionIntoViewAndClearsTheFocus() {
        val many = (1..60).map { row("r$it", 20261031 - it / 2, "Payee $it") }
        var focused = 0
        compose.setContent {
            MetroTheme {
                RegisterScreen(
                    page = registerPage(many),
                    onGesture = {},
                    onOpen = {},
                    onAdd = {},
                    onReconcile = {},
                    focus = "r58",
                    onFocused = { focused++ },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("register-row-r58").assertIsDisplayed()
        assertEquals(1, focused)
    }
}
