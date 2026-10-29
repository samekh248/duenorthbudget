package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BankSyncFailure
import app.duenorth.budget.core.BankSyncFix
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.syncSpin
import app.duenorth.budget.design.metroTilt
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

/** The accounts page's bank sync panel, just above the app bar. */
data class BankSyncUi(
    val running: Boolean = true,
    /** The linked account fetching now; null until the first one starts. */
    val accountName: String? = null,
    /** Every linked account this sync has started, in order. */
    val started: List<String> = emptyList(),
    val failures: List<BankSyncFailure> = emptyList(),
    /** Bank sync as a whole could not run. */
    val error: String? = null,
)

object BankSyncCopy {
    const val SYNCING = "syncing"
    const val RETRY = "retry"
    const val DISMISS = "dismiss"
    const val CLOSE = "×"
    const val DETAILS = "details"
    const val TITLE = "bank sync"
    const val SYNCED = "synced"
    const val FAILED_LABEL = "failed"
    const val FAILED_HEADING = "didn't sync"
    const val SYNCING_HEADING = "syncing now"
    const val WAITING = "syncing…"
    const val ALL_SYNCED = "every linked account synced"
    const val NONE_STARTED = "no linked account was reached"
    const val UNAVAILABLE = "bank sync needs the server: sign in and try again"
    const val FAILED = "bank sync did not finish"
    const val FINISHED_WITH_ERRORS = "sync had errors"
    const val SYNCING_BANKS = "syncing banks"
    const val FIX_SIMPLEFIN = "fix at SimpleFIN"
    const val FIX_TOKEN = "new setup token"
    const val FIX_RELINK = "link again"

    /** The button that mends a failed account, or null when nothing on the phone can. */
    fun fix(fix: BankSyncFix?) =
        when (fix) {
            BankSyncFix.SIMPLEFIN -> FIX_SIMPLEFIN
            BankSyncFix.TOKEN -> FIX_TOKEN
            BankSyncFix.RELINK -> FIX_RELINK
            null -> null
        }

    fun failure(failure: BankSyncFailure) = "${failure.accountName}: ${failure.message}"

    fun more(count: Int) = "and $count more"

    fun syncedOf(
        synced: Int,
        total: Int,
    ) = "$synced of $total synced"
}

/** At most this many failed accounts are named; the rest are counted. */
private const val SHOWN_FAILURES = 1

/**
 * The bank sync status, as a WP toast: a solid accent tile floating a gutter in from the edges just
 * above the app bar, over whatever page is showing. While banks fetch it turns a small sync glyph
 * beside the account fetching now, with any account that failed under it. A sync that ends with
 * errors turns the tile red and offers retry and close; a clean sync takes it away.
 */
@Composable
fun BankSyncPanel(
    ui: BankSyncUi,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** A tap on a failed sync's tile opens its full page. */
    onOpen: () -> Unit = {},
) {
    val colors = Metro.colors
    val failed = !ui.running
    val fill = if (failed) colors.errorFill else Metro.accent.fill
    val on = if (failed) colors.onErrorFill else Metro.accent.onFill
    val failures =
        ui.failures.take(SHOWN_FAILURES).map(BankSyncCopy::failure) +
            listOfNotNull((ui.failures.size - SHOWN_FAILURES).takeIf { it > 0 }?.let(BankSyncCopy::more))
    Row(
        modifier
            .fillMaxWidth()
            .padding(MetroDimens.Gutter)
            .then(
                if (failed) {
                    Modifier
                        .metroTilt()
                        .clickable(interactionSource = null, indication = null, onClickLabel = BankSyncCopy.DETAILS, onClick = onOpen)
                } else {
                    Modifier
                },
            ).background(fill)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("bank-sync-panel"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyphIcon(AppGlyph.Sync, on, Modifier.syncSpin(ui.running), size = 22.dp)
        Column(Modifier.padding(start = 12.dp, end = 8.dp).weight(1f)) {
            MetroText(
                when {
                    !failed -> BankSyncCopy.SYNCING_BANKS
                    ui.error != null -> ui.error
                    else -> BankSyncCopy.FINISHED_WITH_ERRORS
                },
                Metro.typography.body,
                Modifier.testTag("bank-sync-title"),
                color = on,
                maxLines = 1,
            )
            if (!failed && ui.accountName != null) {
                MetroText(
                    ui.accountName,
                    Metro.typography.caption,
                    Modifier.testTag("bank-sync-status"),
                    color = on.copy(alpha = 0.85f),
                    maxLines = 1,
                )
            }
            failures.forEach { line ->
                MetroText(line, Metro.typography.caption, Modifier.testTag("bank-sync-error"), color = on, maxLines = 1)
            }
        }
        if (failed) {
            TileAction(BankSyncCopy.RETRY, BankSyncCopy.RETRY, "bank-sync-retry", Metro.typography.body, on, onRetry)
            TileAction(BankSyncCopy.CLOSE, BankSyncCopy.DISMISS, "bank-sync-dismiss", Metro.typography.subheader, on, onDismiss)
        }
    }
}

@Composable
private fun TileAction(
    text: String,
    label: String,
    tag: String,
    style: TextStyle,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .defaultMinSize(minWidth = MetroDimens.TouchTarget, minHeight = MetroDimens.TouchTarget)
            .metroTilt()
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(text, style, Modifier.padding(horizontal = 8.dp), color = color)
    }
}

/**
 * The failed sync's full page, laid out as a schedule's details are: the accounts section line, the
 * page's name, how many accounts synced and failed, then a ledger per outcome with each account and
 * what happened to it, with the button that mends it when there is one. Follows a retry live.
 */
@Composable
fun BankSyncReportScreen(
    ui: BankSyncUi?,
    modifier: Modifier = Modifier,
    /** A failed account's fix: SimpleFIN's own site, a new setup token, or linking again. */
    onFix: (BankSyncFailure) -> Unit = {},
) {
    val colors = Metro.colors
    val failures = ui?.failures.orEmpty().associateBy { it.accountName }
    val names = (ui?.started.orEmpty() + failures.keys).distinct()
    val failed = names.filter { it in failures }
    val fetching = ui?.accountName?.takeIf { ui.running && it !in failures }
    val synced = names.filter { it !in failures && it != fetching }
    LazyColumn(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = MetroDimens.Gutter)
            .testTag("bank-sync-report"),
    ) {
        item(key = "title") { MetroText(BankSyncCopy.TITLE, Metro.typography.header, maxLines = 1) }
        item(key = "totals") {
            Row(Modifier.fillMaxWidth().padding(top = MetroDimens.Row, bottom = MetroDimens.Grid)) {
                BudgetTotal(
                    label =
                        when {
                            ui == null -> BankSyncCopy.ALL_SYNCED
                            ui.running -> BankSyncCopy.SYNCING_BANKS
                            else -> ui.error ?: BankSyncCopy.FINISHED_WITH_ERRORS
                        },
                    amount = BankSyncCopy.syncedOf(synced.size, names.size),
                    amountColor = Metro.accent.text,
                    amountTag = "bank-sync-report-synced",
                    modifier = Modifier.weight(1f),
                )
                BudgetTotal(
                    label = BankSyncCopy.FAILED_LABEL,
                    amount = failed.size.toString(),
                    amountColor = if (failed.isEmpty()) colors.foreground else colors.error,
                    amountTag = "bank-sync-report-failed",
                    alignEnd = true,
                )
            }
        }
        if (ui != null && names.isEmpty() && !ui.running) {
            item(key = "none") { MetroText(BankSyncCopy.NONE_STARTED, Metro.typography.body, color = colors.secondary) }
        }
        if (failed.isNotEmpty()) {
            item(key = "failed") { ReportHeading(BankSyncCopy.FAILED_HEADING) }
            failed.forEachIndexed { i, name ->
                val failure = failures.getValue(name)
                item(key = "failed-$name") {
                    ReportLine(name, failure.message, ledgerBanded(i), error = true, fix = BankSyncCopy.fix(failure.fix)) { onFix(failure) }
                }
            }
        }
        if (fetching != null) {
            item(key = "now") { ReportHeading(BankSyncCopy.SYNCING_HEADING) }
            item(key = "now-$fetching") { ReportLine(fetching, BankSyncCopy.WAITING, banded = false) }
        }
        if (synced.isNotEmpty()) {
            item(key = "synced") { ReportHeading(BankSyncCopy.SYNCED) }
            synced.forEachIndexed { i, name ->
                item(key = "synced-$name") { ReportLine(name, null, ledgerBanded(i)) }
            }
        }
        item(key = "end") { Spacer(Modifier.height(MetroDimens.Grid)) }
    }
}

@Composable
private fun ReportHeading(label: String) {
    MetroText(label, Metro.typography.caption, Modifier.padding(top = 14.dp, bottom = 4.dp), color = Metro.accent.text)
}

/** An account on a ledger line: its name, why it failed under it, and the button that mends it. */
@Composable
private fun ReportLine(
    name: String,
    detail: String?,
    banded: Boolean,
    error: Boolean = false,
    fix: String? = null,
    onFix: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .ledgerFill(if (banded) ledgerBand else Color.Transparent)
            .padding(vertical = 6.dp)
            .testTag("bank-sync-report-$name"),
        verticalArrangement = Arrangement.Center,
    ) {
        MetroText(name, Metro.typography.body, maxLines = 2)
        if (detail != null) {
            MetroText(detail, Metro.typography.caption, color = if (error) Metro.colors.error else Metro.colors.secondary, maxLines = 2)
        }
        if (fix != null) {
            MetroButton(fix, Modifier.padding(top = 8.dp, bottom = 4.dp).testTag("bank-sync-fix-$name"), onFix)
        }
    }
}
