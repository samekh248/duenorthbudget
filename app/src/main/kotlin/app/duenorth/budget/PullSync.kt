package app.duenorth.budget

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    const val OPEN_HINT = "pull down or tap for details"
}

/** What the pull-down sync panel shows, and whether a pull opens it at all. */
data class PullSyncUi(
    /** Off for a budget kept only on the phone, while signed out, and while a mode holds the page. */
    val enabled: Boolean = false,
    /** The last sync, as the server sync names it: "synced 3:42 pm", or why the last try failed. */
    val status: String = "",
    /** A server or bank sync is running, whoever started it. */
    val syncing: Boolean = false,
    /** How far the server sync has got, when it knows; null for a bank sync or none. */
    val progress: Float? = null,
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

/** The finished sync's status stays on screen this long before the panel closes. */
private const val DONE_PAUSE_MS = 700L

/**
 * The home pivot's pull-down sync panel. With a page's list at its top, pulling further down
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

    // Turned off mid-pull (a mode opened, the budget closed): the panel goes away with it.
    LaunchedEffect(ui.enabled) { if (!ui.enabled && (open || revealed.value > 0f)) settle(false) }

    // After a tap, the panel shows the sync running, then the new status a moment, then closes.
    LaunchedEffect(ui.syncing, requested) {
        when {
            requested == Requested.ASKED && ui.syncing -> requested = Requested.RUNNING
            requested == Requested.ASKED -> {
                delay(START_WAIT_MS)
                if (!current.syncing) {
                    requested = Requested.NONE
                    settle(false)
                }
            }
            requested == Requested.RUNNING && !ui.syncing -> {
                delay(DONE_PAUSE_MS)
                requested = Requested.NONE
                settle(false)
            }
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
        // A failed sync waits as a quiet strip at the top of the page; a tap (or a pull) opens the panel that explains it.
        val failure = ui.failure
        if (failure != null && !open && revealed.value <= 0f) {
            PullSyncStrip(failure, onOpen = { settle(true) })
        }
        val shownDp = with(density) { revealed.value.toDp() }
        Box(
            Modifier
                .fillMaxWidth()
                .height(shownDp)
                .clipToBounds(),
        ) {
            // Measured at its full height and pinned to the bottom, so it slides out from above the page.
            PullSyncSheet(
                ui = ui,
                running = requested != Requested.NONE || ui.syncing,
                onSync = {
                    requested = Requested.ASKED
                    onSync()
                },
                onBankSync = {
                    requested = Requested.ASKED
                    onBankSync()
                },
                onDetails = {
                    settle(false)
                    onDetails()
                },
                onDismissFailure = onDismissFailure,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .wrapContentHeight(Alignment.Bottom, unbounded = true)
                        .onSizeChanged { panelHeight = it.height.toFloat() },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
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

/** An upward flick faster than this, in pixels a second, shuts the panel however far it was pulled. */
private const val FLICK_SHUT = 1_200f

/** The panel itself: the status line, its buttons, and the grip that says it pushes back up. */
@Composable
internal fun PullSyncSheet(
    ui: PullSyncUi,
    running: Boolean,
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
            .padding(top = MetroDimens.Row),
    ) {
        val syncing = running && ui.syncing
        val status =
            when {
                syncing -> PullSyncCopy.SYNCING
                ui.status.isBlank() -> PullSyncCopy.NOT_YET
                else -> ui.status
            }
        MetroText(
            status,
            Metro.typography.subheader,
            Modifier
                .padding(horizontal = MetroDimens.Gutter)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("pull-sync-status"),
            color = if (syncing) Metro.accent.text else Metro.colors.foreground,
            maxLines = 2,
        )
        Box(
            Modifier
                .padding(top = MetroDimens.Row)
                .fillMaxWidth()
                .height(3.dp)
                .background(if (syncing) Metro.colors.outline else Metro.colors.chrome),
        ) {
            val fraction = ui.progress?.takeIf { syncing }
            if (fraction != null) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).background(Metro.accent.fill))
            }
        }
        val failure = ui.failure
        if (failure != null && !syncing) PullSyncFailure(failure, onDetails, onDismissFailure)
        if (ui.canSync) {
            MetroButtonGrid(Modifier.padding(horizontal = MetroDimens.Gutter).padding(top = MetroDimens.Row)) {
                MetroButton(PullSyncCopy.SYNC_NOW, Modifier.testTag("pull-sync-now")) { if (!ui.syncing) onSync() }
                if (ui.bankSync) {
                    MetroButton(PullSyncCopy.BANK_SYNC, Modifier.testTag("pull-sync-banks")) { if (!ui.syncing) onBankSync() }
                }
            }
        }
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
        progress = syncProgress,
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
 * A failed bank sync, waiting at the top of the page in the panel's own grey: a sync glyph and
 * its headline in the error colour, and a hint that it opens. No red fill, so it reads as part of
 * the page rather than over it.
 */
@Composable
private fun PullSyncStrip(
    failure: BankSyncUi,
    onOpen: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Metro.colors.chrome)
            .clickable(onClickLabel = BankSyncCopy.DETAILS, onClick = onOpen)
            .padding(horizontal = MetroDimens.Gutter, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("pull-sync-strip"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyphIcon(AppGlyph.Sync, Metro.colors.error, size = 16.dp)
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            MetroText(failure.headline(), Metro.typography.body, color = Metro.colors.error, maxLines = 1)
            MetroText(PullSyncCopy.OPEN_HINT, Metro.typography.caption, color = Metro.colors.secondary, maxLines = 1)
        }
        AppGlyphIcon(AppGlyph.ChevronDown, Metro.colors.secondary, size = 16.dp)
    }
}

/** The failed sync inside the panel: its headline, the accounts that failed, and details and dismiss as quiet text. */
@Composable
private fun PullSyncFailure(
    failure: BankSyncUi,
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    val lines =
        failure.failures.take(SHOWN_FAILURES).map(BankSyncCopy::failure) +
            listOfNotNull((failure.failures.size - SHOWN_FAILURES).takeIf { it > 0 }?.let(BankSyncCopy::more))
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MetroDimens.Gutter)
            .padding(top = MetroDimens.Row)
            .testTag("pull-sync-failure"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppGlyphIcon(AppGlyph.Sync, Metro.colors.error, size = 16.dp)
            MetroText(
                failure.headline(),
                Metro.typography.body,
                Modifier.padding(start = 8.dp).testTag("pull-sync-failure-title"),
                color = Metro.colors.error,
                maxLines = 1,
            )
        }
        lines.forEach { line ->
            MetroText(
                line,
                Metro.typography.caption,
                Modifier.padding(start = 24.dp, top = 2.dp).testTag("pull-sync-failure-line"),
                color = Metro.colors.secondary,
                maxLines = 1,
            )
        }
        Row(Modifier.padding(start = 12.dp)) {
            QuietAction(BankSyncCopy.DETAILS, "pull-sync-details", onDetails)
            QuietAction(BankSyncCopy.DISMISS, "pull-sync-dismiss", onDismiss)
        }
    }
}

/** A text-only action in the accent, at full touch height, so the failure's controls don't shout. */
@Composable
private fun QuietAction(
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .defaultMinSize(minWidth = MetroDimens.TouchTarget, minHeight = MetroDimens.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(label, Metro.typography.body, color = Metro.accent.text)
    }
}
