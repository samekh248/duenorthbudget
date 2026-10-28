package app.duenorth.budget.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import kotlin.math.roundToInt

/**
 * One line of a [MetroContextMenu]: a lowercase command, with an optional gray [detail] at its end (why it is
 * off, or where it leads). [accent] marks a command that removes something.
 */
data class MetroMenuItem(
    val label: String,
    val tag: String? = null,
    val detail: String? = null,
    val enabled: Boolean = true,
    val accent: Boolean = false,
    val run: () -> Unit,
)

/**
 * The Metro context menu, the one long-press (or tap) menu in the app: the rest of the screen dims, the pressed
 * row at [anchor] (window coordinates) stays bright, and a full-width panel of lowercase commands opens under it,
 * or over it when there is no room below. A command closes the menu before it runs.
 */
@Composable
fun MetroContextMenu(
    anchor: Rect,
    items: List<MetroMenuItem>,
    onDismiss: () -> Unit,
    tag: String = "context-menu",
) {
    // Read before the popup: its own window does not get the screen's insets.
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density)
    val bottom = WindowInsets.navigationBars.getBottom(density)
    Popup(
        popupPositionProvider = WholeWindow,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        MetroContextMenuPanel(anchor, items, onDismiss, tag, safeTop = top, safeBottom = bottom)
    }
}

private object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

/** The menu as drawn inside its popup; on its own for previews and layout tests. */
@Composable
fun MetroContextMenuPanel(
    anchor: Rect,
    items: List<MetroMenuItem>,
    onDismiss: () -> Unit,
    tag: String = "context-menu",
    safeTop: Int = 0,
    safeBottom: Int = 0,
) {
    val scrim = Metro.colors.background.copy(alpha = MENU_SCRIM)
    Layout(
        content = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Metro.colors.chrome)
                    // A tap on the panel itself, such as on a command that is off, keeps the menu open.
                    .pointerInput(Unit) { detectTapGestures {} }
                    .padding(vertical = MetroDimens.Row)
                    .testTag(tag),
            ) {
                items.forEach { item -> MenuLine(item, onDismiss) }
            }
        },
        modifier =
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(scrim, Offset.Zero, Size(size.width, anchor.top.coerceAtLeast(0f)))
                    drawRect(scrim, Offset(0f, anchor.bottom), Size(size.width, (size.height - anchor.bottom).coerceAtLeast(0f)))
                }.pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) { measurables, constraints ->
        val panel = measurables.single().measure(constraints.copy(minWidth = constraints.maxWidth, minHeight = 0))
        val below = anchor.bottom.roundToInt()
        // Keep the panel clear of the navigation bar and the status bar.
        val limit = constraints.maxHeight - safeBottom
        val y =
            if (below + panel.height <= limit) {
                below
            } else {
                (anchor.top.roundToInt() - panel.height).coerceIn(safeTop, (limit - panel.height).coerceAtLeast(safeTop))
            }
        layout(constraints.maxWidth, constraints.maxHeight) { panel.place(0, y) }
    }
}

@Composable
private fun MenuLine(
    item: MetroMenuItem,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .then(
                if (item.enabled) {
                    Modifier.metroPress {
                        onDismiss()
                        item.run()
                    }
                } else {
                    Modifier.semantics(mergeDescendants = true) { disabled() }
                },
            ).padding(horizontal = MENU_INSET, vertical = 8.dp)
            .then(if (item.tag != null) Modifier.testTag(item.tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetroText(
            item.label,
            Metro.typography.subheader,
            Modifier.weight(1f, fill = false),
            color =
                when {
                    !item.enabled -> Metro.colors.secondary
                    item.accent -> Metro.accent.text
                    else -> Metro.colors.foreground
                },
            maxLines = 1,
            // Ellipsis rather than clip, so descenders such as g and y are never cut at the line's edge.
            softWrap = false,
        )
        if (item.detail != null) {
            MetroText(
                item.detail,
                Metro.typography.caption.copy(textAlign = TextAlign.End),
                Modifier.weight(1f).padding(start = 12.dp),
                color = Metro.colors.secondary,
                maxLines = 2,
            )
        }
    }
}

private val MENU_INSET = 24.dp
private const val MENU_SCRIM = 0.7f
