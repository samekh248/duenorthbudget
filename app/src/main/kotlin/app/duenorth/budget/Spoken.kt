package app.duenorth.budget

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.theme.Metro

/**
 * What TalkBack says for rows whose meaning is in their layout: a ledger row's figures sit under column headings a
 * screen reader never reaches, so each figure is named, and a minus is spoken as a word rather than left to the
 * voice to read (or drop) a hyphen.
 */
object Spoken {
    const val WARNING = "warning"
    const val HIDDEN = "hidden"
    const val TRANSFER = "transfer"
    const val SPLIT = "split"

    /** A formatted amount with its minus spoken: "-$80.00" reads "minus $80.00". */
    fun money(formatted: String): String = if (formatted.startsWith("-")) "minus ${formatted.drop(1)}" else formatted

    /** One envelope row: its name, then each column by its heading, then how far over when it is overspent. */
    fun categoryLine(
        name: String,
        budgeted: String,
        spent: String,
        available: String,
        over: String? = null,
        hidden: Boolean = false,
    ): String =
        listOfNotNull(
            name,
            HIDDEN.takeIf { hidden },
            "budgeted ${money(budgeted)}",
            "spent ${money(spent)}",
            if (over == null) "available ${money(available)}" else "overspent, available ${money(available)}",
            over,
        ).joinToString(", ")

    /** One income row: budgeted on tracking files only, then what came in. */
    fun incomeLine(
        name: String,
        budgeted: String?,
        received: String,
        hidden: Boolean = false,
    ): String =
        listOfNotNull(
            name,
            HIDDEN.takeIf { hidden },
            budgeted?.let { "budgeted ${money(it)}" },
            "received ${money(received)}",
        ).joinToString(", ")

    /** A register row: payee, what kind, the detail line, the amount, then a split's parts with their amounts. */
    fun registerLine(
        payee: String,
        transfer: Boolean,
        detail: String,
        amount: String,
        parts: List<Pair<String, String>>,
    ): String =
        (
            listOfNotNull(payee, TRANSFER.takeIf { transfer }, detail.takeIf { it.isNotBlank() }, money(amount)) +
                parts.map { (name, value) -> "$name ${money(value)}" }
        ).joinToString(", ")

    fun warning(text: String): String = "$WARNING, $text"

    const val EXPANDED = "expanded"
    const val COLLAPSED = "collapsed"
}

/** A heading or control that opens and folds what is under it: TalkBack says which, since only a chevron shows it. */
fun Modifier.expandState(expanded: Boolean): Modifier = semantics { stateDescription = if (expanded) Spoken.EXPANDED else Spoken.COLLAPSED }

/**
 * A warning or error under a form or a figure: a bold "!" leads it, so it never reads as the accent's other uses (the
 * available column, links) by colour alone, and TalkBack says "warning" and announces it when it appears.
 */
@Composable
fun WarningText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Metro.typography.body,
    color: Color = Metro.accent.text,
) {
    Row(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = Spoken.warning(text)
            liveRegion = LiveRegionMode.Polite
        },
        verticalAlignment = Alignment.Top,
    ) {
        // The mark is for the eye; the spoken word comes from the row's description.
        MetroText(
            WARNING_MARK,
            style.copy(fontWeight = FontWeight.Bold),
            Modifier.padding(end = 8.dp).clearAndSetSemantics {},
            color = color,
        )
        MetroText(text, style, color = color)
    }
}

internal const val WARNING_MARK = "!"
