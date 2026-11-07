package app.duenorth.budget

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object PullSyncCopy {
    const val NOT_YET = "not synced yet"
    const val SYNCING = "syncing…"
    const val SYNC_NOW = "sync now"
    const val BANK_SYNC = "bank sync"
    const val CLOSE_HINT = "swipe up to close"

    /** The server sync's "synced 3:42 pm" reads "last synced 3:42 pm" here; a failure's own words stay as they are. */
    fun lastSynced(status: String) = if (status.startsWith(SYNCED)) "last $status" else status

    private const val SYNCED = "synced "
}

/** What the pull-down sync panel shows, and whether a pull opens it at all. */
data class PullSyncUi(
    /** Off for a budget kept only on the phone, while signed out, and while a mode holds the page. */
    val enabled: Boolean = false,
    /** The last sync, as the server sync names it: "synced 3:42 pm", or why the last try failed. */
    val status: String = "",
    /** A server or bank sync is running, whoever started it. */
    val syncing: Boolean = false,
    /** The budget has bank-linked accounts, so the panel offers bank sync beside sync now. */
    val bankSync: Boolean = false,
    /** The budget is on a server this phone is signed in to, so the panel can sync it. */
    val canSync: Boolean = true,
    /** The last bank sync's failure, kept until dismissed: a quiet strip over the page, and its lines in the panel. */
    val failure: BankSyncUi? = null,
)

/** Where the panel is after a tap on one of its syncs: waiting for it to start, then for it to end. */
private enum class Requested { NONE, ASKED, RUNNING }

/** A pull shorter than this share of the panel springs back shut; longer, and the panel stays open. */
private const val OPEN_SHARE = 0.5f

/** The page moves half as far as the finger once the list is at its top, so the pull feels held back. */
private const val PULL_RESISTANCE = 0.5f

/** How far past the panel's own height a pull can stretch it before it stops. */
private const val OVERSTRETCH = 1.25f

/** A sync asked for that never starts (one was already running) gives up waiting after this long. */
private const val START_WAIT_MS = 1_500L

/**
 * The home pivot's pull-down sync panel, on every section. With a page's list at its top (or on a page with
 * no list at all), pulling further down
 * draws a panel out from above the page: the last sync's time, and sync now (and bank sync when
 * accounts are linked). Pulling never syncs by itself, so a stray swipe costs nothing: a short
 * pull springs back, a longer one leaves the panel open, and only a tap on its buttons syncs.
 * Pushing back up, a tap on the page, or back closes it; after a sync it closes on its own.
 */
@Composable
fun PullSyncPanel(
    ui: PullSyncUi,
    onSync: () -> Unit,
    onBankSync: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the failed bank sync's page, with every account and why each failed. */
    onDetails: () -> Unit = {},
    /** Clears the failed bank sync, and the strip with it. */
    onDismissFailure: () -> Unit = {},
    /** Bounce the panel down and back up once, so someone new sees it is there; [onPeeked] says it played. */
    peek: Boolean = false,
    onPeeked: () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val fallbackHeight = with(density) { 160.dp.toPx() }
    var panelHeight by remember { mutableFloatStateOf(0f) }
    val revealed = remember { Animatable(0f) }
    var open by remember { mutableStateOf(false) }
    var requested by remember { mutableStateOf(Requested.NONE) }
    val current by rememberUpdatedState(ui)

    fun height() = if (panelHeight > 0f) panelHeight else fallbackHeight

    fun settle(toOpen: Boolean) {
        open = toOpen
        scope.launch { revealed.animateTo(if (toOpen) height() else 0f) }
    }

    // The first-run hint: a moment after the page settles, the panel drops partway, holds, and tucks back up.
    LaunchedEffect(peek && ui.enabled) {
        if (!peek || !ui.enabled) return@LaunchedEffect
        delay(PEEK_DELAY_MS)
        if (open || revealed.value > 0f) return@LaunchedEffect
        // In the panel's own scope: recording the peek turns [peek] off, which must not cut the bounce short.
        scope.launch {
            revealed.animateTo(
                height() * PEEK_SHARE,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            )
            delay(PEEK_HOLD_MS)
            if (!open) revealed.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
        }
        onPeeked()
    }

    // Turned off mid-pull (a mode opened, the budget closed): the panel goes away with it.
    LaunchedEffect(ui.enabled) { if (!ui.enabled && (open || revealed.value > 0f)) settle(false) }

    // After a tap the panel has folded away; the syncing strip stays until the sync it started is done.
    LaunchedEffect(ui.syncing, requested) {
        when {
            requested == Requested.ASKED && ui.syncing -> requested = Requested.RUNNING
            requested == Requested.ASKED -> {
                delay(START_WAIT_MS)
                if (!current.syncing) requested = Requested.NONE
            }
            requested == Requested.RUNNING && !ui.syncing -> requested = Requested.NONE
        }
    }

    BackHandler(enabled = open) { settle(false) }

    val connection =
        remember {
            object : NestedScrollConnection {
                // Moving back up first shuts the panel, before the list under it scrolls.
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y >= 0f || revealed.value <= 0f) return Offset.Zero
                    val next = (revealed.value + available.y).coerceAtLeast(0f)
                    val used = next - revealed.value
                    scope.launch { revealed.snapTo(next) }
                    return Offset(0f, used)
                }

                // What the list could not scroll, at its top, draws the panel out; a fling landing there does not.
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!current.enabled || available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                    val before = revealed.value
                    val next = (before + available.y * PULL_RESISTANCE).coerceAtMost(height() * OVERSTRETCH)
                    val threshold = height() * OPEN_SHARE
                    if (before < threshold && next >= threshold) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch { revealed.snapTo(next) }
                    return Offset(0f, available.y)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    val shown = revealed.value
                    if (shown <= 0f) {
                        if (open) open = false
                        return Velocity.Zero
                    }
                    // A flick upward shuts it whatever the distance; otherwise it is how far it came.
                    val toOpen = available.y > -FLICK_SHUT && shown >= height() * OPEN_SHARE
                    settle(toOpen)
                    return available
                }
            }
        }

    Column(modifier.nestedScroll(connection)) {
        // A sync started from the panel shows as the panel's folded grip saying syncing, while the app's own sync
        // animation runs along the top; it goes once the sync is done. A failed sync then waits as a quiet strip:
        // a tap opens its page, its × dismisses it, and a pull still opens the panel.
        val failure = ui.failure
        val folded = !open && revealed.value <= 0f
        if (folded && requested != Requested.NONE) {
            PullSyncSyncing()
        } else if (folded && failure != null) {
            PullSyncStrip(failure, onDetails = onDetails, onDismiss = onDismissFailure)
        }
        val shownDp = with(density) { revealed.value.toDp() }
        Box(
            Modifier
                .fillMaxWidth()
                .height(shownDp)
                .clipToBounds(),
        ) {
            // Measured at its full height and pinned to the bottom, so it slides out from above the page.
            // Whichever button is pressed, the panel folds back up.
            PullSyncSheet(
                ui = ui,
                onSync = {
                    requested = Requested.ASKED
                    settle(false)
                    onSync()
                },
                onBankSync = {
                    requested = Requested.ASKED
                    settle(false)
                    onBankSync()
                },
                onDetails = {
                    settle(false)
                    onDetails()
                },
                onDismissFailure = {
                    settle(false)
                    onDismissFailure()
                },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .wrapContentHeight(Alignment.Bottom, unbounded = true)
                        .onSizeChanged { panelHeight = it.height.toFloat() },
            )
        }
        // A page with no list to scroll (an empty inbox, the assistant with nothing found, a featured row) still
        // opens the panel: a drag no list took scrolls nothing here, so all of it passes to the pull above.
        val still = rememberScrollableState { 0f }
        Box(Modifier.weight(1f).fillMaxWidth().scrollable(still, Orientation.Vertical)) {
            content(Modifier.fillMaxSize())
            // While it is open a tap on the page shuts the panel rather than opening a row.
            if (open && revealed.value > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag("pull-sync-scrim")
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            settle(false)
                        },
                )
            }
        }
    }
}

/** The first-run peek waits this long after the budget shows, so the bounce isn't lost in the page loading. */
private const val PEEK_DELAY_MS = 900L

/** How much of the panel the peek shows: enough to read its status and see the buttons start. */
private const val PEEK_SHARE = 0.6f

/** How long the peek holds before it tucks back up. */
private const val PEEK_HOLD_MS = 900L

/**
 * Whether the pull-down sync panel still owes its one-time peek. Only a fresh install owes it: the
 * first look after an update from an older version records it as already seen, so people who have
 * used the app before never get the bounce.
 */
object PullSyncPeek {
    private const val PREFS = "pull_sync_peek"
    private const val DONE = "done"

    /** Decides once per install; after that the stored answer stands. */
    fun pending(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(DONE)) {
            val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            // A fresh install was never updated: its install and last update times are the same moment.
            val fresh = info != null && info.firstInstallTime == info.lastUpdateTime
            prefs.edit().putBoolean(DONE, !fresh).apply()
        }
        return !prefs.getBoolean(DONE, true)
    }

    fun markDone(context: Context) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(DONE, true)
            .apply()
    }
}

/** An upward flick faster than this, in pixels a second, shuts the panel however far it was pulled. */
private const val FLICK_SHUT = 1_200f

/** The panel itself: a failed sync's row, its buttons, the last sync under them, and the grip that says it pushes back up. */
@Composable
internal fun PullSyncSheet(
    ui: PullSyncUi,
    onSync: () -> Unit,
    onBankSync: () -> Unit,
    modifier: Modifier = Modifier,
    onDetails: () -> Unit = {},
    onDismissFailure: () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Metro.colors.chrome)
            .testTag("pull-sync-panel")
            .padding(top = 4.dp),
    ) {
        val failure = ui.failure
        if (failure != null && !ui.syncing) PullSyncFailure(failure, onDetails, onDismissFailure)
        if (ui.canSync) {
            MetroButtonGrid(Modifier.padding(horizontal = MetroDimens.Gutter).padding(top = MetroDimens.Row)) {
                MetroButton(PullSyncCopy.SYNC_NOW, Modifier.testTag("pull-sync-now")) { if (!ui.syncing) onSync() }
                if (ui.bankSync) {
                    MetroButton(PullSyncCopy.BANK_SYNC, Modifier.testTag("pull-sync-banks")) { if (!ui.syncing) onBankSync() }
                }
            }
        }
        // The last sync sits under the buttons, centred over the grip.
        val status =
            when {
                ui.syncing -> PullSyncCopy.SYNCING
                ui.status.isBlank() -> PullSyncCopy.NOT_YET
                else -> PullSyncCopy.lastSynced(ui.status)
            }
        MetroText(
            status,
            Metro.typography.body.copy(textAlign = TextAlign.Center),
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MetroDimens.Gutter)
                .padding(top = MetroDimens.Row)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("pull-sync-status"),
            maxLines = 2,
        )
        Column(
            Modifier.fillMaxWidth().padding(top = MetroDimens.Row, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .background(Metro.colors.secondary, RoundedCornerShape(2.dp)),
            )
            MetroText(
                PullSyncCopy.CLOSE_HINT,
                Metro.typography.caption,
                Modifier.padding(top = 4.dp),
                color = Metro.colors.secondary,
            )
        }
    }
}

/** The panel's state from the shell's: on only on home, with a server budget open and no mode holding the page. */
internal fun ShellUiState.pullSync(): PullSyncUi {
    val server = shell != null && !locked && signedIn && !cloudFileId.isNullOrBlank()
    val modeOpen = budgetEdit != null || inbox.selection != null || accounts.reorder
    val failure = bankSync.panel?.takeIf { !it.running }
    return PullSyncUi(
        enabled = (server || failure != null) && shell != null && !locked && route == ShellRoute.Home && !modeOpen,
        canSync = server,
        failure = failure,
        status = syncStatus,
        syncing = syncing || bankSync.syncing,
        bankSync =
            server &&
                shell
                    ?.accounts
                    .orEmpty()
                    .any { it.bankSync && !it.closed },
    )
}

/** At most this many failed accounts are named in the panel; the rest are counted. */
private const val SHOWN_FAILURES = 2

/** The failed sync's headline: what went wrong as a whole, or that some accounts did not sync. */
private fun BankSyncUi.headline() = error ?: BankSyncCopy.FINISHED_WITH_ERRORS

/**
 * A failed bank sync, waiting at the top of the page in the panel's own grey: the same row the
 * panel shows, with no red fill, so it reads as part of the page rather than over it.
 */
@Composable
private fun PullSyncStrip(
    failure: BankSyncUi,
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    PullSyncFailureRow(
        failure,
        shown = 1,
        onDetails = onDetails,
        onDismiss = onDismiss,
        tag = "pull-sync-strip",
        modifier =
            Modifier
                .background(Metro.colors.chrome)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("pull-sync-strip"),
    )
}

/** The failed sync inside the panel: the same row, naming up to [SHOWN_FAILURES] accounts. */
@Composable
private fun PullSyncFailure(
    failure: BankSyncUi,
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    PullSyncFailureRow(
        failure,
        shown = SHOWN_FAILURES,
        onDetails = onDetails,
        onDismiss = onDismiss,
        tag = "pull-sync-failure",
        modifier = Modifier.padding(top = 4.dp).testTag("pull-sync-failure"),
    )
}

/**
 * A failed sync's row: its headline in the error colour, the accounts that failed, and an × on
 * the right that dismisses it. A tap anywhere else on the row opens the full page.
 */
@Composable
private fun PullSyncFailureRow(
    failure: BankSyncUi,
    shown: Int,
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
    /** Tags the row's parts, so the strip's and the panel's can be told apart. */
    tag: String,
    modifier: Modifier = Modifier,
) {
    val lines =
        failure.failures.take(shown).map(BankSyncCopy::failure) +
            listOfNotNull((failure.failures.size - shown).takeIf { it > 0 }?.let(BankSyncCopy::more))
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = BankSyncCopy.DETAILS, onClick = onDetails)
            .padding(start = MetroDimens.Gutter, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyphIcon(AppGlyph.Sync, Metro.colors.error, size = 16.dp)
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            MetroText(
                failure.headline(),
                Metro.typography.body,
                Modifier.testTag("$tag-title"),
                color = Metro.colors.error,
                maxLines = 1,
            )
            lines.forEach { line ->
                MetroText(
                    line,
                    Metro.typography.caption,
                    Modifier.testTag("$tag-line"),
                    color = Metro.colors.secondary,
                    maxLines = 1,
                )
            }
        }
        Box(
            Modifier
                .defaultMinSize(minWidth = MetroDimens.TouchTarget, minHeight = MetroDimens.TouchTarget)
                .clickable(role = Role.Button, onClickLabel = BankSyncCopy.DISMISS, onClick = onDismiss)
                .semantics { contentDescription = BankSyncCopy.DISMISS }
                .testTag("$tag-dismiss"),
            contentAlignment = Alignment.Center,
        ) {
            MetroText(BankSyncCopy.CLOSE, Metro.typography.subheader, color = Metro.colors.secondary)
        }
    }
}

/** The panel folded back to its grip, saying syncing until the sync a button started is done. */
@Composable
private fun PullSyncSyncing() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Metro.colors.chrome)
            .padding(top = 8.dp, bottom = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("pull-sync-syncing"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(36.dp)
                .height(4.dp)
                .background(Metro.colors.secondary, RoundedCornerShape(2.dp)),
        )
        MetroText(PullSyncCopy.SYNCING, Metro.typography.caption, Modifier.padding(top = 4.dp), color = Metro.colors.secondary)
    }
}
