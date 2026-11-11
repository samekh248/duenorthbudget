package app.duenorth.budget

import app.duenorth.budget.core.BankLinkCopy
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.EnvelopeCopy
import app.duenorth.budget.design.components.MetroMenuItem

/**
 * Every list row's long-press menu, read from the view model when a menu opens. The order is the same in each:
 * the row's verbs, then its destructive line last, in the accent.
 */
internal class AppRowMenus(
    private val model: ShellViewModel,
) : RowMenuItems {
    private val state get() = model.state.value

    /** A month's category: move money, cover it while it is overspent, rename it on its page, hide it from the month. */
    override fun category(categoryId: String): List<MetroMenuItem> {
        val shell = state.shell ?: return emptyList()
        val category = shell.groups.flatMap { it.categories }.firstOrNull { it.id == categoryId } ?: return emptyList()
        return listOfNotNull(
            MetroMenuItem(RowMenuCopy.OPEN, "category-menu-open") { model.envelopes.openBudget(categoryId) },
            MetroMenuItem(EnvelopeCopy.MOVE, "category-menu-move") { model.envelopes.openMoveFrom(categoryId) }
                .takeIf { shell.mode != BudgetMode.TRACKING },
            MetroMenuItem(EnvelopeCopy.COVER, "category-menu-cover") { model.envelopes.openCover(categoryId) }
                .takeIf { shell.mode != BudgetMode.TRACKING && category.availableMinor < 0 && state.budgetEdit == null },
            MetroMenuItem(EnvelopeCopy.RENAME, "category-menu-rename") {
                model.envelopes.openBudget(categoryId)
                model.envelopes.startRenameCategory()
            },
            MetroMenuItem(if (category.hidden) EnvelopeCopy.SHOW else EnvelopeCopy.HIDE, "category-menu-hide") {
                model.envelopes.hideCategory(categoryId, !category.hidden)
            },
        )
    }

    /** An account: its register's commands without opening the register first. Close is last. */
    override fun account(accountId: String): List<MetroMenuItem> {
        val account = state.shell?.accounts?.firstOrNull { it.id == accountId } ?: return emptyList()

        // Each command opens the register under it, so back from the command lands on the account.
        fun onRegister(run: () -> Unit): () -> Unit =
            {
                model.register.openAccount(accountId)
                run()
            }
        return listOfNotNull(
            MetroMenuItem(AccountScreenCopy.EDIT, "account-menu-edit", run = onRegister(model.accounts::openEdit)),
            MetroMenuItem(ScreenBarCopy.RECONCILE, "account-menu-reconcile", run = onRegister(model.reconcile::open))
                .takeIf { !account.closed },
            MetroMenuItem(BankLinkCopy.TITLE, "account-menu-bank-link", run = onRegister(model.bankLink::open))
                .takeIf { !account.closed && state.activeInstance != null },
            if (account.closed) {
                MetroMenuItem(AccountScreenCopy.REOPEN, "account-menu-reopen") { model.accounts.reopen(accountId) }
            } else {
                destructiveItem(AccountScreenCopy.CLOSE, "account-menu-close", onRegister(model.accounts::openClose))
            },
        )
    }

    /** A schedule: edit, post or skip its next date, delete. */
    override fun schedule(scheduleId: String): List<MetroMenuItem> =
        listOf(
            MetroMenuItem(RowMenuCopy.EDIT, "schedule-menu-edit") { model.schedules.openEditor(scheduleId) },
            MetroMenuItem(ScheduleScreenCopy.POST, "schedule-menu-post") { model.schedules.post(scheduleId) },
            MetroMenuItem(ScheduleScreenCopy.SKIP, "schedule-menu-skip") { model.schedules.skip(scheduleId) },
            destructiveItem(ScheduleScreenCopy.DELETE, "schedule-menu-delete") { model.schedules.delete(scheduleId) },
        )

    /** A rule: edit, delete. */
    override fun rule(ruleId: String): List<MetroMenuItem> =
        listOf(
            MetroMenuItem(RowMenuCopy.EDIT, "rule-menu-edit") { model.rules.openEditor(ruleId) },
            destructiveItem(RuleScreenCopy.DELETE, "rule-menu-delete") { model.rules.delete(ruleId) },
        )
}
