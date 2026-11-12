package app.duenorth.budget

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.duenorth.budget.core.MoneyInput
import app.duenorth.budget.design.components.MetroField

/**
 * A [MetroField] for an amount of money. It takes only an amount in the person's number format, or a sum of
 * amounts ([MoneyInput]): whitespace typed or pasted is dropped, and anything else that is not an amount, such
 * as a letter, a symbol or a third decimal place, leaves the box as it was.
 */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    decimals: Int = 2,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    fill: Boolean = true,
    /** The number format typed in: Settings' own, unless the screen is given one. */
    numberFormat: String = LocalFormatPrefs.current.numberFormat,
) {
    MetroField(
        value,
        { typed -> MoneyInput.filter(value, typed, numberFormat, decimals).takeIf { it != value }?.let(onValueChange) },
        hint,
        modifier,
        keyboardOptions = keyboardOptions,
        fill = fill,
    )
}

/**
 * The same amount box holding a [TextFieldValue], for a screen that sets where the cursor sits or what is
 * selected, such as an amount that starts selected so typing replaces it. It takes the same text as the
 * [String] box; a cursor or selection move with no change to the text always goes through.
 */
@Composable
fun AmountField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    decimals: Int = 2,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    fill: Boolean = true,
    numberFormat: String = LocalFormatPrefs.current.numberFormat,
) {
    MetroField(
        value,
        { typed -> filterAmount(value, typed, numberFormat, decimals)?.let(onValueChange) },
        hint,
        modifier,
        keyboardOptions = keyboardOptions,
        fill = fill,
    )
}

/**
 * The box's next value when [typed] replaces [previous], as [MoneyInput.filter] takes text, or null to keep
 * [previous]. Whitespace dropped from [typed] moves the cursor back by as much as was dropped before it.
 */
internal fun filterAmount(
    previous: TextFieldValue,
    typed: TextFieldValue,
    numberFormat: String,
    decimals: Int,
): TextFieldValue? {
    if (typed.text == previous.text) return typed
    val text = MoneyInput.filter(previous.text, typed.text, numberFormat, decimals)
    return when (text) {
        typed.text -> typed
        previous.text -> null
        else -> {
            val end = typed.selection.end.coerceIn(0, typed.text.length)
            val cursor = (end - typed.text.take(end).count { it.isWhitespace() }).coerceIn(0, text.length)
            TextFieldValue(text, TextRange(cursor))
        }
    }
}
