package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

/** The words every question shares: the answer that leaves things as they are is always keep. */
object ConfirmCopy {
    const val KEEP = "keep"

    /** A question's title always ends in a question mark. */
    fun question(title: String): String = if (title.endsWith("?")) title else "$title?"
}

/**
 * Every question the app asks before a change: the title as a question, what it changes, and the two answers.
 * In the app the answers sit on the application bar, in place of the page underneath's, so a question drawn over
 * a form never leaves that form's save live; on its own (a preview) the page draws them as body buttons. Tag [tag];
 * the answers are `[tag]-yes` and `[tag]-keep` unless [yesTag] and [keepTag] say otherwise.
 */
@Composable
fun ConfirmPage(
    title: String,
    yes: String,
    onYes: () -> Unit,
    onKeep: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    body: String? = null,
    note: String? = null,
    yesTag: String = "$tag-yes",
    keepTag: String = "$tag-keep",
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            // Drawn over a page: taps stop here instead of reaching the rows underneath.
            .pointerInput(Unit) { detectTapGestures {} }
            .verticalScroll(rememberScrollState())
            .padding(MetroDimens.Gutter)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            MetroText(ConfirmCopy.question(title), Metro.typography.header, Modifier.testTag("$tag-title"), maxLines = 4)
            if (caption != null) MetroText(caption, Metro.typography.caption, Modifier.padding(top = 4.dp), color = Metro.colors.secondary)
        }
        if (body != null) MetroText(body, Metro.typography.subheader, Modifier.testTag("$tag-body"))
        if (note != null) MetroText(note, Metro.typography.body, color = Metro.colors.secondary)
        content()
        ConfirmAnswers(yes, onYes, onKeep, yesTag, keepTag)
    }
}

/**
 * A question's two answers: on the application bar when the app hosts the page, as a pair of body buttons when it
 * does not. For a question with its own layout, such as the template apply list.
 */
@Composable
fun ConfirmAnswers(
    yes: String?,
    onYes: () -> Unit,
    onKeep: () -> Unit,
    yesTag: String,
    keepTag: String,
) {
    if (formSaveOnBar()) {
        val yesNow = rememberUpdatedState(onYes)
        val keepNow = rememberUpdatedState(onKeep)
        val bar =
            remember(yes) {
                listOfNotNull(
                    yes?.let { ScreenAction(CONFIRM_YES, AppGlyph.Save, it) { yesNow.value() } },
                    ScreenAction(CONFIRM_KEEP, AppGlyph.Cancel, ConfirmCopy.KEEP) { keepNow.value() },
                )
            }
        RememberBarOverride(bar)
    } else {
        MetroButtonGrid(Modifier.padding(top = 12.dp)) {
            if (yes != null) MetroButton(yes, Modifier.testTag(yesTag), onClick = onYes)
            MetroButton(ConfirmCopy.KEEP, Modifier.testTag(keepTag), onClick = onKeep)
        }
    }
}

/** Bar ids for a question's answers; the bar tags them `app-bar-confirm-yes` and `app-bar-confirm-keep`. */
const val CONFIRM_YES = "confirm-yes"
const val CONFIRM_KEEP = "confirm-keep"
