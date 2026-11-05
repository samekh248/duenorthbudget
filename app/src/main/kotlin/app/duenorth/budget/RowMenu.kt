package app.duenorth.budget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import app.duenorth.budget.design.components.MetroContextMenu
import app.duenorth.budget.design.components.MetroMenuItem
import app.duenorth.budget.design.components.metroPress

/**
 * The one rule for list rows: tap opens the row, long-press shows its menu under it. A list keeps one of these and
 * draws [RowMenu] once; each row takes [rowPress].
 */
@Stable
class RowMenuState {
    var open by mutableStateOf<Pair<Rect, List<MetroMenuItem>>?>(null)
        internal set

    fun show(
        anchor: Rect,
        items: List<MetroMenuItem>,
    ) {
        if (items.isNotEmpty()) open = anchor to items
    }

    fun dismiss() {
        open = null
    }
}

@Composable
fun rememberRowMenu(): RowMenuState = remember { RowMenuState() }

/** Draws the open row's menu, if any: the shared Metro context menu. */
@Composable
fun RowMenu(
    state: RowMenuState,
    tag: String,
) {
    state.open?.let { (anchor, items) -> MetroContextMenu(anchor, items, onDismiss = state::dismiss, tag = tag) }
}

/**
 * Tap runs [onClick]; long-press opens [items] in [menu], anchored to this row. The items are built only when the
 * menu opens, so a row pays nothing for them until then.
 */
@Composable
fun Modifier.rowPress(
    menu: RowMenuState,
    onClick: () -> Unit,
    items: () -> List<MetroMenuItem>,
): Modifier {
    val bounds = remember { arrayOf(Rect.Zero) }
    return onGloballyPositioned { bounds[0] = it.boundsInWindow() }
        .metroPress(onClick, onLongClick = { menu.show(bounds[0], items()) })
}

/** The red, last line every row menu ends with when the row can be deleted, closed or removed. */
fun destructiveItem(
    label: String,
    tag: String,
    run: () -> Unit,
) = MetroMenuItem(label, tag, accent = true, run = run)

/** The words row menus share, so the same command reads the same in every list. */
object RowMenuCopy {
    const val SELECT = "select"
    const val EDIT = "edit"
    const val OPEN = "open"
}

/**
 * The commands each kind of row offers on long-press, built from the view model once at the app's root. Screens and
 * screenshot tests without it keep plain taps.
 */
interface RowMenuItems {
    fun category(categoryId: String): List<MetroMenuItem> = emptyList()

    fun account(accountId: String): List<MetroMenuItem> = emptyList()

    fun schedule(scheduleId: String): List<MetroMenuItem> = emptyList()

    fun rule(ruleId: String): List<MetroMenuItem> = emptyList()
}

/** The app's one row menu and where its commands come from; null outside the app (previews and tests). */
val LocalRowMenu = staticCompositionLocalOf<RowMenuState?> { null }
val LocalRowMenuItems = staticCompositionLocalOf<RowMenuItems?> { null }

/** [rowPress] on the app's own row menu: tap runs [onClick], long-press shows what [items] picks for this row. */
@Composable
fun Modifier.rowPress(
    onClick: () -> Unit,
    items: RowMenuItems.() -> List<MetroMenuItem>,
): Modifier {
    val menu = LocalRowMenu.current
    val source = LocalRowMenuItems.current
    if (menu == null || source == null) return metroPress(onClick)
    return rowPress(menu, onClick) { source.items() }
}
