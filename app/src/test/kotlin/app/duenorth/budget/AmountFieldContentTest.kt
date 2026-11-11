package app.duenorth.budget

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AmountFieldContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    private var amount by mutableStateOf("")

    private fun show(numberFormat: String = FormatOptions.NUMBER_COMMA_DOT) {
        compose.setContent {
            CompositionLocalProvider(LocalFormatPrefs provides FormatPrefs(numberFormat = numberFormat)) {
                MetroTheme {
                    AmountField(amount, { amount = it }, "amount", Modifier.testTag("amount"))
                }
            }
        }
    }

    private fun box() = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("amount")))

    // Letters, symbols and spaces typed into an amount box never reach it.
    @Test
    fun typingKeepsOnlyACurrencyAmount() {
        show()
        box().performTextInput("12")
        box().performTextInput("a")
        box().performTextInput(" ")
        box().performTextInput("$")
        box().performTextInput(".5")
        box().performTextInput("0")
        box().performTextInput("1")
        compose.waitForIdle()
        assertEquals("12.50", amount)
    }

    // Pasted text loses its whitespace; pasted text that is not an amount leaves the box as it was.
    @Test
    fun pastingDropsWhitespaceAndRefusesTheRest() {
        show()
        box().performTextReplacement(" 1,234.56 \n")
        compose.waitForIdle()
        assertEquals("1,234.56", amount)
        box().performTextReplacement("USD 99")
        compose.waitForIdle()
        assertEquals("1,234.56", amount)
    }

    // The dot-comma format takes its comma as the decimal point, two places at most.
    @Test
    fun followsTheNumberFormat() {
        show(FormatOptions.NUMBER_DOT_COMMA)
        box().performTextReplacement("1.234,567")
        compose.waitForIdle()
        assertEquals("", amount)
        box().performTextReplacement("1.234,56")
        compose.waitForIdle()
        assertEquals("1.234,56", amount)
    }
}
