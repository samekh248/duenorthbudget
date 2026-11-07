package app.duenorth.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

object InboxEmptyCopy {
    const val TITLE = "all caught up"
    const val BODY = "new and imported transactions wait here until you give them a category."
}

/**
 * The inbox with nothing in it: the "growth north" logo drawing itself in the accent (the bars fill left to
 * right, then the arrowhead lands), as the Due North Tasks "all clear" screen does with its own mark. It plays
 * each time the inbox empties and again on tap, and rests when animations are off.
 */
@Composable
fun InboxEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(MetroDimens.Gutter).testTag("inbox-empty"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BudgetLogo(LOGO_HEIGHT)
        Spacer(Modifier.height(30.dp))
        MetroText(InboxEmptyCopy.TITLE, Metro.typography.subheader.copy(textAlign = TextAlign.Center))
        MetroText(
            InboxEmptyCopy.BODY,
            Metro.typography.body.copy(textAlign = TextAlign.Center),
            color = Metro.colors.secondary,
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 290.dp),
        )
    }
}

private val LOGO_HEIGHT = 150.dp
