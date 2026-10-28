package app.duenorth.budget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.CategoryManagePage
import app.duenorth.budget.core.EnvelopeCopy
import app.duenorth.budget.core.ManageCategoryRow
import app.duenorth.budget.core.ManageGroupRow
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.LocalFieldSubmit
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroContextMenu
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroMenuItem
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

object CategoryManageCopy {
    const val NEW_GROUP = "new group"
    const val NEW_CATEGORY = "new category"
    const val REORDER = "reorder"
    const val REORDER_HINT = "drag a group or category by its handle"
    const val DONE = "done"
    const val SAVE = "save"
    const val CANCEL = "cancel"
    const val NAME = "name"
    const val RENAME = "rename"
    const val ADD_CATEGORY = "add category"
    const val MOVE_TO_GROUP = "move to group"
    const val HIDE = "hide"
    const val SHOW = "show"
    const val HIDE_GROUP = "hide group"
    const val SHOW_GROUP = "show group"
    const val HIDE_HINT = "keeps its history"
    const val DELETE = "delete"
    const val HIDDEN = "hidden"
    const val IN_USE = "has transactions · hide it instead"
    const val DELETE_CATEGORY_BODY = "it has no transactions and no money budgeted, so nothing else in the budget changes"
    const val DELETE_CATEGORY_NOTE = "deleting syncs to your other devices; to keep it without seeing it, hide it instead"
    const val DELETE_GROUP_BODY = "the group is empty, so nothing else in the budget changes"
    const val DELETE_GROUP_NOTE = "deleting syncs to your other devices"

    fun addTo(group: String) = "add category to $group"

    fun count(n: Int) = if (n == 1) "1 category" else "$n categories"

    fun emptyFirst(n: Int) = "move or delete its ${count(n)} first"

    fun hidesAll(n: Int) = "hides all ${count(n)}"

    fun delete(name: String) = "delete $name?"
}

/** What a tap opened over the list: a category's actions, a group's, or the groups a category can move to. */
private sealed interface ManageSheet {
    data class Category(
        val group: ManageGroupRow,
        val category: ManageCategoryRow,
    ) : ManageSheet

    data class Group(
        val group: ManageGroupRow,
    ) : ManageSheet

    data class MoveTo(
        val group: ManageGroupRow,
        val category: ManageCategoryRow,
    ) : ManageSheet
}

/** A row being named in place: an existing one, or a new one at the end of its list. */
private sealed interface ManageEdit {
    data class Category(
        val id: String,
    ) : ManageEdit

    data class Group(
        val id: String,
    ) : ManageEdit

    data class NewCategory(
        val groupId: String,
    ) : ManageEdit

    data object NewGroup : ManageEdit
}

private sealed interface ManageDelete {
    val name: String

    data class Category(
        val id: String,
        override val name: String,
    ) : ManageDelete

    data class Group(
        val id: String,
        override val name: String,
    ) : ManageDelete
}

/** The page's mode, which picks the bar. */
private enum class ManageMode { LIST, EDIT, REORDER, OVER }

/**
 * Categories, folded into their groups: tap a group to open it, tap a category (or hold either) for its actions.
 * Names change in place on the row, delete asks on its own page, and reorder puts drag handles on the rows.
 * The page owns its bar. Tag `manage-categories`.
 */
@Composable
fun ManageCategoriesScreen(
    manage: CategoryManagePage,
    error: String?,
    onAddGroup: (String) -> Unit,
    onAddCategory: (String, String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onHideCategory: (String, Boolean) -> Unit,
    onHideGroup: (String, Boolean) -> Unit,
    onMoveToGroup: (String, String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onPlaceGroup: (String, String?) -> Unit,
    onPlaceCategory: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(listOf<String>()) }
    var sheet by remember { mutableStateOf<ManageSheet?>(null) }
    // The row a menu belongs to, in window coordinates.
    var anchor by remember { mutableStateOf(Rect.Zero) }
    var edit by remember { mutableStateOf<ManageEdit?>(null) }
    var text by remember { mutableStateOf(TextFieldValue()) }
    var confirm by remember { mutableStateOf<ManageDelete?>(null) }
    var reorder by rememberSaveable { mutableStateOf(false) }

    fun startEdit(
        next: ManageEdit,
        name: String,
    ) {
        sheet = null
        edit = next
        text = TextFieldValue(name, TextRange(0, name.length))
    }

    val save by rememberUpdatedState {
        val name = text.text
        when (val current = edit) {
            is ManageEdit.Category -> onRenameCategory(current.id, name)
            is ManageEdit.Group -> onRenameGroup(current.id, name)
            is ManageEdit.NewCategory -> if (name.isNotBlank()) onAddCategory(current.groupId, name)
            ManageEdit.NewGroup -> if (name.isNotBlank()) onAddGroup(name)
            null -> Unit
        }
        edit = null
    }
    val newGroup by rememberUpdatedState { startEdit(ManageEdit.NewGroup, "") }
    val mode =
        when {
            confirm != null || sheet != null -> ManageMode.OVER
            edit != null -> ManageMode.EDIT
            reorder -> ManageMode.REORDER
            else -> ManageMode.LIST
        }
    val bar =
        remember(mode) {
            when (mode) {
                ManageMode.LIST ->
                    listOf(
                        ScreenAction("manage-new-group", AppGlyph.Add, CategoryManageCopy.NEW_GROUP) { newGroup() },
                        ScreenAction("manage-reorder", AppGlyph.Categories, CategoryManageCopy.REORDER, primary = false) {
                            reorder = true
                        },
                    )
                ManageMode.EDIT ->
                    listOf(
                        ScreenAction("manage-save", AppGlyph.Save, CategoryManageCopy.SAVE) { save() },
                        ScreenAction("manage-cancel", AppGlyph.Cancel, CategoryManageCopy.CANCEL) { edit = null },
                    )
                ManageMode.REORDER -> listOf(ScreenAction("manage-done", AppGlyph.Save, CategoryManageCopy.DONE) { reorder = false })
                ManageMode.OVER -> emptyList()
            }
        }
    RememberBarOverride(bar)
    // Back closes whatever is open over the list before it leaves the page.
    BackHandler(enabled = mode != ManageMode.LIST) {
        when {
            confirm != null -> confirm = null
            sheet is ManageSheet.MoveTo -> (sheet as ManageSheet.MoveTo).let { sheet = ManageSheet.Category(it.group, it.category) }
            sheet != null -> sheet = null
            edit != null -> edit = null
            else -> reorder = false
        }
    }

    Box(modifier.fillMaxSize().background(Metro.colors.background).testTag("manage-categories")) {
        val drag = remember { RowDrag() }
        if (!reorder) drag.drop()
        val categoryDrag = remember { RowDrag() }
        if (!reorder) categoryDrag.drop()
        val groupIds = manage.groups.map { it.id }
        Column(Modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) {
            MetroText(EnvelopeCopy.MANAGE, Metro.typography.header)
            if (reorder) {
                MetroText(
                    CategoryManageCopy.REORDER_HINT,
                    Metro.typography.caption,
                    Modifier.padding(top = 4.dp).testTag("manage-reorder-hint"),
                    color = Metro.colors.secondary,
                )
            }
            if (error != null) {
                MetroText(error, Metro.typography.body, Modifier.padding(top = 4.dp), color = Metro.accent.text)
            }
            CompositionLocalProvider(LocalFieldSubmit provides { save() }) {
                LazyColumn(Modifier.weight(1f).padding(top = MetroDimens.Row)) {
                    manage.groups.forEach { group ->
                        val expanded = group.id in open
                        item(key = "manage-group-${group.id}") {
                            val renaming = edit == ManageEdit.Group(group.id)
                            GroupLine(
                                group = group,
                                expanded = expanded,
                                renaming = renaming,
                                text = text,
                                onText = { text = it },
                                onToggle = { open = if (expanded) open - group.id else open + group.id },
                                onMenu = { bounds ->
                                    anchor = bounds
                                    sheet = ManageSheet.Group(group)
                                },
                                modifier = if (reorder) drag.follow(group.id, groupIds) else Modifier,
                                lifted = drag.dragging == group.id,
                                handle = if (reorder) drag.handle(group.id, groupIds, onPlaceGroup) else null,
                                reorder = reorder,
                            )
                        }
                        if (expanded) {
                            val ids = group.categories.map { it.id }
                            items(group.categories, key = { "manage-cat-${it.id}" }) { category ->
                                val index = ids.indexOf(category.id)
                                CategoryLine(
                                    category = category,
                                    renaming = edit == ManageEdit.Category(category.id),
                                    text = text,
                                    onText = { text = it },
                                    onOpen = { bounds ->
                                        anchor = bounds
                                        sheet = ManageSheet.Category(group, category)
                                    },
                                    banded = ledgerBanded(index),
                                    modifier = if (reorder) categoryDrag.follow(category.id, ids) else Modifier,
                                    lifted = categoryDrag.dragging == category.id,
                                    handle = if (reorder) categoryDrag.handle(category.id, ids, onPlaceCategory) else null,
                                )
                            }
                            item(key = "manage-add-${group.id}") {
                                if (edit == ManageEdit.NewCategory(group.id)) {
                                    NameField(
                                        text,
                                        { text = it },
                                        CategoryManageCopy.NEW_CATEGORY,
                                        Modifier.padding(start = MetroDimens.Gutter),
                                    )
                                } else if (!reorder) {
                                    AddLine(CategoryManageCopy.addTo(group.name), "manage-add-category-${group.id}") {
                                        startEdit(ManageEdit.NewCategory(group.id), "")
                                    }
                                }
                            }
                        }
                    }
                    if (edit == ManageEdit.NewGroup) {
                        item(key = "manage-new-group") {
                            NameField(text, { text = it }, CategoryManageCopy.NEW_GROUP, Modifier.padding(top = MetroDimens.Row))
                        }
                    }
                }
            }
        }

        // Every row's commands open in the Metro context menu under the row, as the inbox's do.
        val menu: List<MetroMenuItem>? =
            when (val current = sheet) {
                is ManageSheet.Category -> {
                    val hidden = current.category.hidden
                    listOfNotNull(
                        MetroMenuItem(CategoryManageCopy.RENAME, "manage-action-rename") {
                            startEdit(ManageEdit.Category(current.category.id), current.category.name)
                        },
                        MetroMenuItem(CategoryManageCopy.MOVE_TO_GROUP, "manage-action-move", detail = "${current.group.name} ›") {
                            sheet = ManageSheet.MoveTo(current.group, current.category)
                        }.takeIf { manage.groups.size > 1 },
                        MetroMenuItem(
                            if (hidden) CategoryManageCopy.SHOW else CategoryManageCopy.HIDE,
                            "manage-action-hide",
                            detail = if (hidden) null else CategoryManageCopy.HIDE_HINT,
                        ) { onHideCategory(current.category.id, !hidden) },
                        MetroMenuItem(
                            CategoryManageCopy.DELETE,
                            "manage-action-delete",
                            detail = if (current.category.inUse) CategoryManageCopy.IN_USE else null,
                            enabled = !current.category.inUse,
                            accent = true,
                        ) { confirm = ManageDelete.Category(current.category.id, current.category.name) },
                    )
                }
                is ManageSheet.Group -> {
                    val group = current.group
                    val hides = CategoryManageCopy.hidesAll(group.categories.size).takeIf { !group.hidden && group.categories.isNotEmpty() }
                    listOf(
                        MetroMenuItem(
                            CategoryManageCopy.RENAME,
                            "manage-action-rename",
                        ) { startEdit(ManageEdit.Group(group.id), group.name) },
                        MetroMenuItem(CategoryManageCopy.ADD_CATEGORY, "manage-action-add") {
                            if (group.id !in open) open = open + group.id
                            startEdit(ManageEdit.NewCategory(group.id), "")
                        },
                        MetroMenuItem(
                            if (group.hidden) CategoryManageCopy.SHOW_GROUP else CategoryManageCopy.HIDE_GROUP,
                            "manage-action-hide",
                            detail = hides,
                        ) { onHideGroup(group.id, !group.hidden) },
                        MetroMenuItem(
                            CategoryManageCopy.DELETE,
                            "manage-action-delete",
                            detail = if (group.categories.isEmpty()) null else CategoryManageCopy.emptyFirst(group.categories.size),
                            enabled = group.categories.isEmpty(),
                            accent = true,
                        ) { confirm = ManageDelete.Group(group.id, group.name) },
                    )
                }
                // Move to opens a second menu under the same row, one line per other group.
                is ManageSheet.MoveTo ->
                    manage.groups.filter { it.id != current.group.id }.map { target ->
                        MetroMenuItem(target.name, "manage-move-${target.id}") { onMoveToGroup(current.category.id, target.id) }
                    }
                null -> null
            }
        if (menu != null) {
            // A command closes the menu first; one that opens the next menu or a question sets that after.
            MetroContextMenu(anchor, menu, onDismiss = { sheet = null }, tag = "manage-sheet")
        }

        confirm?.let { current ->
            DeletePage(
                current = current,
                onDelete = {
                    confirm = null
                    when (current) {
                        is ManageDelete.Category -> onDeleteCategory(current.id)
                        is ManageDelete.Group -> onDeleteGroup(current.id)
                    }
                },
                onKeep = { confirm = null },
            )
        }
    }
}

@Composable
private fun GroupLine(
    group: ManageGroupRow,
    expanded: Boolean,
    renaming: Boolean,
    text: TextFieldValue,
    onText: (TextFieldValue) -> Unit,
    onToggle: () -> Unit,
    onMenu: (Rect) -> Unit,
    modifier: Modifier,
    lifted: Boolean,
    handle: Modifier?,
    reorder: Boolean,
) {
    // Where the row sits, so its menu opens under it.
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val hiddenCount = group.categories.count { it.hidden }
    val summary =
        buildList {
            add(CategoryManageCopy.count(group.categories.size))
            if (group.hidden) {
                add(CategoryManageCopy.HIDDEN)
            } else if (hiddenCount > 0) {
                add("$hiddenCount ${CategoryManageCopy.HIDDEN}")
            }
        }.joinToString(" · ")
    Column(
        modifier
            .fillMaxWidth()
            .background(if (lifted) Metro.colors.chrome else Color.Transparent)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .then(if (renaming || reorder) Modifier else Modifier.metroPress(onToggle, onLongClick = { onMenu(bounds) }))
            .padding(top = MetroDimens.Row)
            .testTag("manage-group-${group.id}"),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = MetroDimens.TouchTarget), verticalAlignment = Alignment.CenterVertically) {
            if (handle != null) {
                Box(
                    handle.heightIn(min = MetroDimens.TouchTarget).padding(end = 12.dp).testTag("manage-group-handle-${group.id}"),
                    contentAlignment = Alignment.Center,
                ) { Grip() }
            }
            Column(Modifier.weight(1f)) {
                if (renaming) {
                    NameField(text, onText, CategoryManageCopy.NAME)
                } else {
                    MetroText(
                        group.name,
                        Metro.typography.subheader,
                        color =
                            when {
                                expanded && !reorder -> Metro.accent.text
                                group.hidden -> Metro.colors.secondary
                                else -> Metro.colors.foreground
                            },
                        maxLines = 2,
                    )
                }
                MetroText(summary, Metro.typography.caption, color = Metro.colors.secondary)
            }
            if (!renaming && !reorder) {
                if (expanded) {
                    MetroText(
                        "•••",
                        Metro.typography.body,
                        Modifier
                            .heightIn(min = MetroDimens.TouchTarget)
                            .metroPress { onMenu(bounds) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("manage-group-menu-${group.id}"),
                        color = Metro.accent.text,
                    )
                }
                AppGlyphIcon(
                    if (expanded) AppGlyph.ChevronDown else AppGlyph.ChevronRight,
                    if (expanded) Metro.accent.text else Metro.colors.secondary,
                )
            }
        }
        if (!expanded) {
            Box(
                Modifier
                    .padding(top = MetroDimens.Row)
                    .fillMaxWidth()
                    .heightIn(min = 1.dp)
                    .background(ledgerBand),
            )
        }
    }
}

@Composable
private fun CategoryLine(
    category: ManageCategoryRow,
    renaming: Boolean,
    text: TextFieldValue,
    onText: (TextFieldValue) -> Unit,
    onOpen: (Rect) -> Unit,
    banded: Boolean,
    modifier: Modifier,
    lifted: Boolean,
    handle: Modifier?,
) {
    // Where the row sits, so its menu opens under it.
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .ledgerFill(
                when {
                    lifted -> Metro.colors.chrome
                    banded && !renaming -> ledgerBand
                    else -> Color.Transparent
                },
            ).onGloballyPositioned { bounds = it.boundsInWindow() }
            .then(if (renaming || handle != null) Modifier else Modifier.metroPress({ onOpen(bounds) }, onLongClick = { onOpen(bounds) }))
            .padding(start = MetroDimens.Gutter)
            .testTag("manage-category-${category.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (handle != null) {
            Box(
                handle.heightIn(min = MetroDimens.TouchTarget).padding(end = 12.dp).testTag("manage-category-handle-${category.id}"),
                contentAlignment = Alignment.Center,
            ) { Grip() }
        }
        if (renaming) {
            NameField(text, onText, CategoryManageCopy.NAME, Modifier.weight(1f))
        } else {
            MetroText(
                category.name,
                Metro.typography.body,
                Modifier.weight(1f),
                color = if (category.hidden) Metro.colors.secondary else Metro.colors.foreground,
                maxLines = 2,
            )
            if (category.hidden) {
                MetroText(CategoryManageCopy.HIDDEN, Metro.typography.caption, color = Metro.colors.secondary)
            }
        }
    }
}

/** A name typed in place, focused with its text selected so typing replaces it; Enter saves. */
@Composable
private fun NameField(
    value: TextFieldValue,
    onValue: (TextFieldValue) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    MetroField(
        value,
        onValue,
        hint,
        modifier.fillMaxWidth().focusRequester(focus).testTag("manage-name-field"),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
}

@Composable
private fun AddLine(
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick)
            .padding(start = MetroDimens.Gutter)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyphIcon(AppGlyph.Add, Metro.accent.text)
        MetroText(label, Metro.typography.body, Modifier.padding(start = 8.dp), color = Metro.accent.text)
    }
}

@Composable
private fun DeletePage(
    current: ManageDelete,
    onDelete: () -> Unit,
    onKeep: () -> Unit,
) {
    val category = current is ManageDelete.Category
    ConfirmPage(
        title = CategoryManageCopy.delete(current.name),
        body = if (category) CategoryManageCopy.DELETE_CATEGORY_BODY else CategoryManageCopy.DELETE_GROUP_BODY,
        note = if (category) CategoryManageCopy.DELETE_CATEGORY_NOTE else CategoryManageCopy.DELETE_GROUP_NOTE,
        yes = CategoryManageCopy.DELETE,
        onYes = onDelete,
        onKeep = onKeep,
        tag = "manage-delete",
        yesTag = "manage-delete-confirm",
        keepTag = "manage-delete-keep",
    )
}
