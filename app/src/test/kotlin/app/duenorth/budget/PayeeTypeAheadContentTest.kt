package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import app.duenorth.budget.core.PayeeRow
import app.duenorth.budget.design.components.LocalFieldSubmit
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PayeeTypeAheadContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    private val saved = listOf("Starbucks", "Corner Store", "Star Market", "Costar Cinema", "Bus")

    @Test
    fun matchesRankStartThenWordThenAnywhere() {
        assertEquals(listOf("Starbucks", "Star Market", "Costar Cinema"), PayeeTypeAhead.matches(saved, "star"))
        assertEquals(listOf("Corner Store", "Costar Cinema"), PayeeTypeAhead.matches(saved, " co"))
        assertEquals(listOf("Corner Store"), PayeeTypeAhead.matches(saved, "store"))
        assertEquals(emptyList<String>(), PayeeTypeAhead.matches(saved, "  "))
        assertEquals(2, PayeeTypeAhead.matches(saved, "s", limit = 2).size)
    }

    @Test
    fun namesPutMostUsedFirstAndLeaveOutTransfers() {
        val rows =
            listOf(
                PayeeRow("a", "Bus", transfer = false, transactionCount = 2),
                PayeeRow("b", "Cafe", transfer = false, transactionCount = 9),
                PayeeRow("c", "Checking", transfer = true, transactionCount = 40),
                PayeeRow("d", "", transfer = false, transactionCount = 1),
            )
        assertEquals(listOf("Cafe", "Bus"), PayeeTypeAhead.names(rows))
    }

    @Test
    fun noListUntilTyping() {
        show(initial = "Starbucks")
        compose.onAllNodesWithTag("payee-suggestions").assertCountEquals(0)
    }

    @Test
    fun tappingASuggestionFillsTheBox() {
        val box = show()
        input().performTextInput("mark")
        compose.onNodeWithTag("payee-suggestion-0").assertTextEquals("Star Market").performClick()
        assertEquals("Star Market", box.text)
        compose.onAllNodesWithTag("payee-suggestions").assertCountEquals(0)
    }

    // Enter takes the top match while the list is open, as the category filter does; then Enter submits (#69).
    @Test
    fun enterTakesTheTopMatchThenSubmits() {
        val box = show()
        input().performTextInput("sta")
        input().performImeAction()
        compose.waitForIdle()
        assertEquals("Starbucks", box.text)
        assertEquals(0, box.submitted)
        input().performImeAction()
        compose.waitForIdle()
        assertEquals(1, box.submitted)
    }

    @Test
    fun aNewNameIsKeptAsTyped() {
        val box = show()
        input().performTextInput("Star Deli")
        compose.onAllNodesWithTag("payee-suggestions").assertCountEquals(0)
        input().performImeAction()
        compose.waitForIdle()
        assertEquals("Star Deli", box.text)
        assertEquals(1, box.submitted)
    }

    @Test
    fun theNewRowKeepsAPrefixOfASavedName() {
        val box = show()
        input().performTextInput("Star")
        compose.onNodeWithTag("payee-suggestion-new").performClick()
        assertEquals("Star", box.text)
        compose.onAllNodesWithTag("payee-suggestions").assertCountEquals(0)
    }

    @Test
    fun noNewRowWhenNewNamesAreNotAllowed() {
        show(offerNew = false)
        input().performTextInput("Star")
        compose.onAllNodesWithTag("payee-suggestion-new").assertCountEquals(0)
    }

    private class Box {
        var text = ""
        var submitted = 0
    }

    private fun input() = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("payee")))

    private fun show(
        initial: String = "",
        offerNew: Boolean = true,
    ): Box {
        val box = Box().apply { text = initial }
        compose.setContent {
            MetroTheme {
                CompositionLocalProvider(LocalFieldSubmit provides { box.submitted++ }) {
                    Column {
                        var text by remember { mutableStateOf(initial) }
                        PayeeField(
                            text,
                            {
                                text = it
                                box.text = it
                            },
                            saved,
                            "payee",
                            "payee",
                            offerNew = offerNew,
                        )
                    }
                }
            }
        }
        if (initial.isNotEmpty()) input().performClick()
        return box
    }
}
