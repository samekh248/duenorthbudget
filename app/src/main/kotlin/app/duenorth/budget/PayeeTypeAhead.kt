package app.duenorth.budget

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountChoice
import app.duenorth.budget.core.PayeeRow
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.LocalFieldSubmit
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

object PayeeTypeAhead {
    /** How many saved payees the list under the box offers at once. */
    const val LIMIT = 5
    const val NEW = "new payee"

    /** The saved payees a box can offer, most used first; transfer payees are accounts, not names to type. */
    fun names(rows: List<PayeeRow>): List<String> =
        rows
            .filter { !it.transfer && it.name.isNotBlank() }
            .sortedWith(compareByDescending<PayeeRow> { it.transactionCount }.thenBy { it.name.lowercase() })
            .map { it.name }
            .distinct()

    /**
     * The names that match [query], ignoring case: those it begins first, then those with a word it begins,
     * then those holding it anywhere, each in [names] order. Nothing for a blank query.
     */
    fun matches(
        names: List<String>,
        query: String,
        limit: Int = LIMIT,
    ): List<String> {
        val typed = query.trim()
        if (typed.isEmpty()) return emptyList()

        fun rank(name: String): Int? =
            when {
                name.startsWith(typed, ignoreCase = true) -> 0
                name.split(' ', '-', '/', '.').any { it.startsWith(typed, ignoreCase = true) } -> 1
                name.contains(typed, ignoreCase = true) -> 2
                else -> null
            }
        return names
            .mapNotNull { name -> rank(name)?.let { name to it } }
            .sortedBy { it.second }
            .map { it.first }
            .take(limit)
    }

    /**
     * The accounts a transfer can go to from [accountId], matched to [query] by name as [matches] ranks
     * payees: open accounts other than this one. Nothing for a blank query.
     */
    fun transfers(
        accounts: List<AccountChoice>,
        accountId: String?,
        query: String,
        limit: Int = LIMIT,
    ): List<AccountChoice> {
        val open = accounts.filter { it.id != accountId && !it.closed }
        val names = matches(open.map { it.name }.distinct(), query, limit)
        return names.flatMap { name -> open.filter { it.name == name } }.take(limit)
    }

    /** True when [query] already names a saved payee exactly, so there is nothing new to add. */
    fun isSaved(
        names: List<String>,
        query: String,
    ): Boolean = names.any { it.equals(query.trim(), ignoreCase = true) }
}

/**
 * A payee box with a type-ahead list of [names] (saved payees) under it. The list shows once the user types,
 * while the box has focus. Tapping a name puts it in the box; Enter takes the top match, as Enter in a
 * category filter does, and with the list closed Enter submits the page as any one-line box does. When
 * [offerNew] is set and the text names no saved payee, the list ends with a row that keeps the text as typed,
 * which saves as a new payee. With [onTransfer], accounts whose names match lead the list as "transfer to"
 * (or "from", when not [expense]) rows, as Actual offers transfer payees; picking one hands over the account.
 */
@Composable
fun PayeeField(
    value: String,
    onValueChange: (String) -> Unit,
    names: List<String>,
    hint: String,
    tag: String,
    modifier: Modifier = Modifier,
    offerNew: Boolean = true,
    transfers: (String) -> List<AccountChoice> = { emptyList() },
    expense: Boolean = true,
    onTransfer: (AccountChoice) -> Unit = {},
) {
    // Suggestions wait for typing, so a form opening on a saved payee doesn't open the list over it.
    var typed by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val accounts = if (typed && focused) transfers(value) else emptyList()
    val matches = if (typed && focused) PayeeTypeAhead.matches(names, value, PayeeTypeAhead.LIMIT - accounts.size) else emptyList()
    val showNew = offerNew && (matches.isNotEmpty() || accounts.isNotEmpty()) && !PayeeTypeAhead.isSaved(names, value)
    val pick = { name: String ->
        typed = false
        onValueChange(name)
    }
    val pickAccount = { account: AccountChoice ->
        typed = false
        onTransfer(account)
    }
    val submit = LocalFieldSubmit.current
    val enter: () -> Unit = {
        val account = accounts.firstOrNull()
        val top = matches.firstOrNull()
        when {
            account != null -> pickAccount(account)
            top != null -> pick(top)
            else -> submit?.invoke()
        }
    }
    Column(modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalFieldSubmit provides enter) {
            MetroField(
                value,
                {
                    if (it != value) typed = true
                    onValueChange(it)
                },
                hint,
                Modifier.onFocusChanged { focused = it.isFocused }.testTag(tag),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        }
        if (matches.isNotEmpty() || accounts.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(MetroDimens.Stroke, Metro.accent.fill)
                    .testTag("$tag-suggestions"),
            ) {
                accounts.forEachIndexed { index, account ->
                    TransferSuggestionLine(
                        RegisterEntry.transferLabel(if (expense) -1 else 1, account.name),
                        if (index == 0) Metro.accent.text else Metro.colors.foreground,
                        "$tag-transfer-${account.id}",
                    ) { pickAccount(account) }
                }
                matches.forEachIndexed { index, name ->
                    SuggestionLine(
                        name,
                        // The top match is what Enter takes, so it wears the accent.
                        if (index == 0 && accounts.isEmpty()) Metro.accent.text else Metro.colors.foreground,
                        "$tag-suggestion-$index",
                    ) { pick(name) }
                }
                if (showNew) {
                    SuggestionLine("${PayeeTypeAhead.NEW} “${value.trim()}”", Metro.colors.secondary, "$tag-suggestion-new") {
                        pick(value.trim())
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionLine(
    text: String,
    color: Color,
    tag: String,
    onClick: () -> Unit,
) {
    MetroText(
        text,
        Metro.typography.control,
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick)
            .padding(horizontal = 10.dp, vertical = MetroDimens.Row)
            .testTag(tag),
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

/** A suggested transfer: the transfer glyph, then "transfer to" and the account, in the suggestion's color. */
@Composable
private fun TransferSuggestionLine(
    text: String,
    color: Color,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick)
            .padding(horizontal = 10.dp, vertical = MetroDimens.Row)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyphIcon(AppGlyph.Transfer, color)
        Spacer(Modifier.width(10.dp))
        MetroText(text, Metro.typography.control, color = color, maxLines = 1, softWrap = false)
    }
}
