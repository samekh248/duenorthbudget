package app.duenorth.budget.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.Relief

/**
 * What Enter does in a one-line [MetroField]: the app provides its form's save, so pressing Enter (or the
 * keyboard's done key) submits the page the way the bar's save does. Either way the box lets go of focus and
 * the keyboard closes, so the rest of the page shows; null does only that.
 */
val LocalFieldSubmit = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * A text box. One line unless [lines] is given: Enter in a one-line box submits the form through
 * [LocalFieldSubmit] and closes the keyboard (a box asking for [ImeAction.Next] moves on instead), and only
 * a multi-line box takes Enter as a new line.
 */
@Composable
fun MetroField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    conceal: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    fill: Boolean = true,
    lines: IntRange? = null,
    /** Fixed text before what is typed, such as `https://`: shown in the box, never editable. */
    prefix: String? = null,
    /** Makes [prefix] an accent button, for a page that lets the person pick it. */
    onPrefixClick: (() -> Unit)? = null,
) {
    val submit = LocalFieldSubmit.current
    MetroFieldFrame(value.isEmpty(), hint, modifier, fill, prefix, onPrefixClick) { style, cursor ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            cursorBrush = cursor,
            visualTransformation = if (conceal) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = fieldKeyboard(keyboardOptions, lines),
            keyboardActions = fieldActions(lines, submit),
            singleLine = lines == null,
            minLines = lines?.first ?: 1,
            maxLines = lines?.last ?: Int.MAX_VALUE,
            modifier = Modifier.fillMaxWidth().then(if (lines != null) Modifier.padding(vertical = 10.dp) else Modifier),
        )
    }
}

/**
 * The same one-line box over a [TextFieldValue], for a box that sets its own selection, such as one that
 * opens with its text selected so typing replaces it. Enter submits through [LocalFieldSubmit].
 */
@Composable
fun MetroField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    fill: Boolean = true,
) {
    val submit = LocalFieldSubmit.current
    MetroFieldFrame(value.text.isEmpty(), hint, modifier, fill) { style, cursor ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            cursorBrush = cursor,
            keyboardOptions = fieldKeyboard(keyboardOptions, null),
            keyboardActions = fieldActions(null, submit),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MetroFieldFrame(
    empty: Boolean,
    hint: String,
    modifier: Modifier,
    fill: Boolean,
    prefix: String? = null,
    onPrefixClick: (() -> Unit)? = null,
    field: @Composable (TextStyle, SolidColor) -> Unit,
) {
    val colors = Metro.colors
    // A relief style cuts the box into the page and writes in the page's own ink.
    val relief = Metro.style.relief
    // Windows 10 Mobile outlines the box on the page in a 2dp border that turns to the accent while typing.
    val mobile10 = Metro.style.mobile10
    val background = if (colors.isDark) Color.White else Color(0xFFBFBFBF)
    val hintColor =
        when {
            relief || mobile10 -> colors.secondary
            colors.isDark -> Color(0xFF909090)
            else -> Color(0xFF8F8F8F)
        }
    // Mica marks the field being typed in with an accent line under it, as Windows 11 does.
    var focused by remember { mutableStateOf(false) }
    val focusLine = Metro.accent.fill
    Box(
        modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .then(
                when {
                    relief -> Modifier.relief(Relief.Inset, small = true)
                    mobile10 -> Modifier.onFocusChanged { focused = it.hasFocus }.border(2.dp, if (focused) focusLine else colors.outline)
                    else -> Modifier.background(background)
                },
            ).then(
                if (Metro.style.layers) {
                    Modifier.onFocusChanged { focused = it.hasFocus }.layerFocusLine(focused)
                } else {
                    Modifier
                },
            ).heightIn(min = MetroDimens.TouchTarget)
            .padding(horizontal = if (relief) 14.dp else 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val ink = Metro.typography.control.copy(color = if (relief || mobile10) colors.foreground else Color.Black)
        if (prefix == null) {
            if (empty) {
                MetroText(hint, Metro.typography.control, color = hintColor)
            }
            field(ink, SolidColor(Metro.accent.fill))
        } else {
            // The prefix is part of the value but cannot be typed over: a darker grey than the hint,
            // so an empty box still reads as prefix then hint.
            val prefixColor = if (relief || mobile10) colors.secondary else Color(0xFF5A5A5A)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onPrefixClick == null) {
                    MetroText(
                        prefix,
                        Metro.typography.control,
                        Modifier.testTag("field-prefix"),
                        color = prefixColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                } else {
                    // A pickable prefix is a small accent button, apart from the typed text.
                    val accent = Metro.accent
                    Row(
                        Modifier
                            .padding(end = 8.dp)
                            .heightIn(min = MetroDimens.TouchTarget - 12.dp)
                            .background(accent.fill)
                            .metroPress(onPrefixClick)
                            .padding(horizontal = 8.dp)
                            .testTag("field-prefix"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MetroText(prefix, Metro.typography.control, color = accent.onFill, maxLines = 1, softWrap = false)
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (empty) {
                        MetroText(hint, Metro.typography.control, color = hintColor, maxLines = 1)
                    }
                    field(ink, SolidColor(Metro.accent.fill))
                }
            }
        }
    }
}

private fun fieldKeyboard(
    options: KeyboardOptions,
    lines: IntRange?,
): KeyboardOptions =
    if (lines == null && options.imeAction in setOf(ImeAction.Default, ImeAction.Unspecified)) {
        options.copy(imeAction = ImeAction.Done)
    } else {
        options
    }

@Composable
private fun fieldActions(
    lines: IntRange?,
    submit: (() -> Unit)?,
): KeyboardActions {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    if (lines != null) return KeyboardActions.Default
    // Letting go of the box, not only hiding the keyboard, so nothing is left waiting for typing.
    val enter = {
        submit?.invoke()
        focus.clearFocus()
        keyboard?.hide()
    }
    return KeyboardActions(
        onDone = { enter() },
        onGo = { enter() },
        onSend = { enter() },
        onSearch = { enter() },
    )
}
