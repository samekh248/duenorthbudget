package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BankSyncFailure
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.syncSpin
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

/** The accounts page's bank sync panel, just above the app bar. */
data class BankSyncUi(
    val running: Boolean = true,
    /** The linked account fetching now; null until the first one starts. */
    val accountName: String? = null,
    val failures: List<BankSyncFailure> = emptyList(),
    /** Bank sync as a whole could not run. */
    val error: String? = null,
)

object BankSyncCopy {
    const val SYNCING = "syncing"
    const val RETRY = "retry"
    const val DISMISS = "dismiss"
    const val UNAVAILABLE = "bank sync needs the server: sign in and try again"
    const val FAILED = "bank sync did not finish"
    const val FINISHED_WITH_ERRORS = "bank sync finished with errors"

    fun failure(failure: BankSyncFailure) = "${failure.accountName}: ${failure.message}"

    fun more(count: Int) = "and $count more"
}

/** At most this many failed accounts are named; the rest are counted. */
private const val SHOWN_FAILURES = 2

/**
 * A translucent strip laid over the bottom of the accounts page while banks fetch: a small turning
 * sync glyph and the account fetching now, with any account that failed under it. When the sync
 * ends with errors it stays, the glyph still, offering retry; a clean sync closes it.
 */
@Composable
fun BankSyncPanel(
    ui: BankSyncUi,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Metro.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.chrome.copy(alpha = 0.9f))
            .padding(horizontal = MetroDimens.Gutter * 2, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("bank-sync-panel"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppGlyphIcon(AppGlyph.Sync, colors.foreground, Modifier.syncSpin(ui.running), size = 16.dp)
            MetroText(
                when {
                    ui.running -> ui.accountName ?: BankSyncCopy.SYNCING
                    ui.error != null -> ui.error
                    else -> BankSyncCopy.FINISHED_WITH_ERRORS
                },
                Metro.typography.caption,
                Modifier.padding(start = 8.dp).weight(1f).testTag("bank-sync-status"),
                maxLines = 1,
            )
            if (!ui.running) {
                PanelAction(BankSyncCopy.RETRY, "bank-sync-retry", onRetry)
                PanelAction(BankSyncCopy.DISMISS, "bank-sync-dismiss", onDismiss)
            }
        }
        ui.failures.take(SHOWN_FAILURES).forEach { failure ->
            MetroText(
                BankSyncCopy.failure(failure),
                Metro.typography.caption,
                Modifier.padding(start = 24.dp).testTag("bank-sync-error"),
                color = colors.error,
                maxLines = 1,
            )
        }
        if (ui.failures.size > SHOWN_FAILURES) {
            MetroText(
                BankSyncCopy.more(ui.failures.size - SHOWN_FAILURES),
                Metro.typography.caption,
                Modifier.padding(start = 24.dp),
                color = colors.error,
            )
        }
    }
}

@Composable
private fun PanelAction(
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    MetroText(
        label,
        Metro.typography.caption,
        Modifier
            .heightIn(min = 40.dp)
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        color = Metro.accent.text,
    )
}
