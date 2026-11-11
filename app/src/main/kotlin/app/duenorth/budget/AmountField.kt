package app.duenorth.budget

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
