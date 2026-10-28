package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
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
    const val WAITING = "syncing…"
    const val ALL_SYNCED = "every linked account synced"
    const val NONE_STARTED = "no linked account was reached"
    const val UNAVAILABLE = "bank sync needs the server: sign in and try again"
    const val FAILED = "bank sync did not finish"
    const val FINISHED_WITH_ERRORS = "sync had errors"
    const val SYNCING_BANKS = "syncing banks"

    fun failure(failure: BankSyncFailure) = "${failure.accountName}: ${failure.message}"

    fun more(count: Int) = "and $count more"
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
 * The failed sync's full page: one line per linked account this sync reached, synced or failed with
 * its reason, under the reason the sync as a whole stopped when it did. Follows a retry live.
 */
@Composable
fun BankSyncReportScreen(
    ui: BankSyncUi?,
    modifier: Modifier = Modifier,
) {
    val colors = Metro.colors
    LazyColumn(modifier.fillMaxSize().background(colors.background).testTag("bank-sync-report")) {
        item {
            MetroText(BankSyncCopy.TITLE, Metro.typography.header, Modifier.padding(MetroDimens.Gutter))
            val summary =
                when {
                    ui == null -> BankSyncCopy.ALL_SYNCED
                    ui.running -> BankSyncCopy.SYNCING_BANKS
                    ui.error != null -> ui.error
                    else -> BankSyncCopy.FINISHED_WITH_ERRORS
                }
            MetroText(
                summary,
                Metro.typography.body,
                Modifier.padding(horizontal = MetroDimens.Gutter).padding(bottom = MetroDimens.Gutter),
                color = if (ui != null && !ui.running) colors.error else colors.secondary,
            )
        }
        val failures = ui?.failures.orEmpty().associateBy { it.accountName }
        val names = (ui?.started.orEmpty() + failures.keys).distinct()
        if (ui != null && names.isEmpty() && !ui.running) {
            item {
                MetroText(BankSyncCopy.NONE_STARTED, Metro.typography.body, Modifier.padding(horizontal = MetroDimens.Gutter))
            }
        }
        items(names) { name ->
            val failure = failures[name]
            val fetching = ui?.running == true && ui.accountName == name && failure == null
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MetroDimens.TouchTarget)
                    .padding(horizontal = MetroDimens.Gutter, vertical = 8.dp)
                    .testTag("bank-sync-report-$name"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppGlyphIcon(
                    when {
                        failure != null -> AppGlyph.Cancel
                        fetching -> AppGlyph.Sync
                        else -> AppGlyph.Reconcile
                    },
                    if (failure != null) colors.error else Metro.accent.text,
                    Modifier.syncSpin(fetching),
                    size = 22.dp,
                )
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    MetroText(name, Metro.typography.subheader, maxLines = 1)
                    MetroText(
                        when {
                            failure != null -> failure.message
                            fetching -> BankSyncCopy.WAITING
                            else -> BankSyncCopy.SYNCED
                        },
                        Metro.typography.caption,
                        color = if (failure != null) colors.error else colors.secondary,
                    )
                }
            }
        }
    }
}
