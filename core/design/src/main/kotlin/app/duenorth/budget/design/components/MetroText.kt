package app.duenorth.budget.design.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import app.duenorth.budget.design.theme.Metro

@Composable
fun MetroText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Metro.colors.foreground,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
) {
    BasicText(
        text = if (Metro.style.mobile10) sentenceCase(text) else text,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = if (softWrap) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

/**
 * Windows 10 Mobile writes labels in sentence case where Metro writes them in lowercase: the first letter is raised
 * when the text starts in lowercase, so names, figures and all-caps titles are left as they are.
 */
fun sentenceCase(text: String): String =
    if (text.firstOrNull()?.isLowerCase() ==
        true
    ) {
        text.replaceFirstChar { it.titlecaseChar() }
    } else {
        text
    }
