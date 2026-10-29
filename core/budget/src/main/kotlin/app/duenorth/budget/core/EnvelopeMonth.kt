package app.duenorth.budget.core

import java.time.YearMonth

object EnvelopeCopy {
    const val ENTER_AMOUNT = "enter an amount"
    const val NOT_ENOUGH = "not enough available"
    const val HOLD_TOO_MUCH = "cannot hold that much"
    const val STILL_USED = "category still has money or transactions"
    const val GROUP_STILL_USED = "group still has categories in use"
    const val MOVE = "move"
    const val HOLD = "hold"
    const val RELEASE = "release hold"
    const val ROLLOVER = "rollover overspending"
    const val NEXT = "next month"
    const val MANAGE = "categories"
    const val RENAME = "rename"
    const val HIDE = "hide"
    const val SHOW = "show"
    const val DELETE = "delete"
    const val ENTER_NAME = "enter a name"
    const val NO_GROUP = "pick a group"

    // The category page: what is available, recent spending, and the month's budget.
    const val AVAILABLE = "available"
    const val SPENT = "spent"
    const val LEFT = "left"
    const val OVER = "over"
    const val TEMPLATE = "template"
    const val BUDGETED = "budgeted this month"
    const val CARRIED_IN = "carried in"
    const val FILLS_TEMPLATE = "fills template"
    const val FILL_TEMPLATE = "fill to template"
    const val RECENT = "recent"
    const val THIS_MONTH = "this month"
    const val NO_RECENT = "no transactions this month"
    const val SEE_ALL = "see all"

    /** Under the rollover switch: where a negative balance goes, named by the month after [month]. */
    fun rolloverHint(month: YearMonth): String = "a negative balance carries into ${month.plusMonths(1).month.name.lowercase()}"
}

data class ManageCategoryRow(
    val id: String,
    val name: String,
    val hidden: Boolean,
    /** Money budgeted or transactions filed: such a category can only be hidden, never deleted. */
    val inUse: Boolean = false,
)

data class ManageGroupRow(
    val id: String,
    val name: String,
    val categories: List<ManageCategoryRow>,
    val hidden: Boolean = false,
)

data class CategoryManagePage(
    val groups: List<ManageGroupRow>,
)

sealed interface EnvelopeEditResult {
    data object Saved : EnvelopeEditResult

    data class Rejected(
        val reason: String,
    ) : EnvelopeEditResult
}

class EnvelopeBook(
    private val session: SqlSession,
    private val clock: SyncClock,
) {
    fun assignBudgeted(
        month: Int,
        categoryId: String,
        budgetedMinor: Long,
    ) {
        BudgetEdits.assign(session, clock, month, categoryId, budgetedMinor)
    }

    fun moveAvailable(
        month: Int,
        fromCategoryId: String,
        toCategoryId: String,
        amountMinor: Long,
    ): EnvelopeEditResult {
        if (amountMinor <= 0L) return EnvelopeEditResult.Rejected(EnvelopeCopy.ENTER_AMOUNT)
        val available = readAvailable(month, fromCategoryId) ?: return EnvelopeEditResult.Rejected(EnvelopeCopy.ENTER_AMOUNT)
        if (amountMinor > available) return EnvelopeEditResult.Rejected(EnvelopeCopy.NOT_ENOUGH)
        val fromBudgeted = readBudgeted(month, fromCategoryId)
        val toBudgeted = readBudgeted(month, toCategoryId)
        assignBudgeted(month, fromCategoryId, fromBudgeted - amountMinor)
        assignBudgeted(month, toCategoryId, toBudgeted + amountMinor)
        return EnvelopeEditResult.Saved
    }

    fun setCarryover(
        month: Int,
        categoryId: String,
        enabled: Boolean,
    ) {
        val id = BudgetEdits.assignmentId(month, categoryId)
        SyncSchema.recordLocal(session, clock, "zero_budgets", id, "month", month.toString())
        SyncSchema.recordLocal(session, clock, "zero_budgets", id, "category", categoryId)
        SyncSchema.recordLocal(session, clock, "zero_budgets", id, "carryover", if (enabled) "1" else "0")
        val budgeted = readBudgeted(month, categoryId)
        SyncSchema.recordLocal(session, clock, "zero_budgets", id, "amount", budgeted.toString())
    }

    fun setHold(
        month: Int,
        amountMinor: Long,
        toBudgetMinor: Long,
    ): EnvelopeEditResult {
        if (amountMinor < 0L) return EnvelopeEditResult.Rejected(EnvelopeCopy.ENTER_AMOUNT)
        if (amountMinor > toBudgetMinor) return EnvelopeEditResult.Rejected(EnvelopeCopy.HOLD_TOO_MUCH)
        // Actual's row id is the month as "2026-10"; SyncSchema maps it onto older phone files.
        val row = "%04d-%02d".format(month / 100, month % 100)
        SyncSchema.recordLocal(session, clock, "zero_budget_months", row, "buffered", amountMinor.toString())
        return EnvelopeEditResult.Saved
    }

    fun addGroup(name: String): String {
        val id = newId()
        SyncSchema.recordLocal(session, clock, "category_groups", id, "name", name.trim())
        SyncSchema.recordLocal(session, clock, "category_groups", id, "is_income", "0")
        SyncSchema.recordLocal(session, clock, "category_groups", id, "hidden", "0")
        SyncSchema.recordLocal(session, clock, "category_groups", id, "tombstone", "0")
        SyncSchema.recordLocal(session, clock, "category_groups", id, "sort_order", nextGroupOrder().toString())
        return id
    }

    fun addCategory(
        groupId: String,
        name: String,
    ): String {
        val id = newId()
        SyncSchema.recordLocal(session, clock, "categories", id, "name", name.trim())
        SyncSchema.recordLocal(session, clock, "categories", id, "cat_group", groupId)
        SyncSchema.recordLocal(session, clock, "categories", id, "is_income", "0")
        SyncSchema.recordLocal(session, clock, "categories", id, "hidden", "0")
        SyncSchema.recordLocal(session, clock, "categories", id, "tombstone", "0")
        SyncSchema.recordLocal(session, clock, "categories", id, "sort_order", nextCategoryOrder(groupId).toString())
        return id
    }

    fun renameGroup(
        groupId: String,
        name: String,
    ) {
        SyncSchema.recordLocal(session, clock, "category_groups", groupId, "name", name.trim())
    }

    fun renameCategory(
        categoryId: String,
        name: String,
    ) {
        SyncSchema.recordLocal(session, clock, "categories", categoryId, "name", name.trim())
    }

    fun hideCategory(
        categoryId: String,
        hidden: Boolean,
    ) {
        SyncSchema.recordLocal(session, clock, "categories", categoryId, "hidden", if (hidden) "1" else "0")
    }

    fun hideGroup(
        groupId: String,
        hidden: Boolean,
    ) {
        SyncSchema.recordLocal(session, clock, "category_groups", groupId, "hidden", if (hidden) "1" else "0")
    }

    /** Files [categoryId] under [groupId], last in it. */
    fun moveCategoryToGroup(
        categoryId: String,
        groupId: String,
    ): EnvelopeEditResult {
        val group =
            session
                .query(
                    "SELECT id FROM category_groups WHERE id = ? AND IFNULL(tombstone, 0) = 0 AND IFNULL(is_income, 0) = 0",
                    listOf(groupId),
                ).firstOrNull() ?: return EnvelopeEditResult.Rejected(EnvelopeCopy.NO_GROUP)
        val current =
            session
                .query("SELECT cat_group FROM categories WHERE id = ? AND IFNULL(tombstone, 0) = 0", listOf(categoryId))
                .firstOrNull() ?: return EnvelopeEditResult.Rejected(EnvelopeCopy.ENTER_NAME)
        if (current.str("cat_group") == group.str("id")) return EnvelopeEditResult.Saved
        SyncSchema.recordLocal(session, clock, "categories", categoryId, "sort_order", nextCategoryOrder(groupId).toString())
        SyncSchema.recordLocal(session, clock, "categories", categoryId, "cat_group", groupId)
        return EnvelopeEditResult.Saved
    }

    /** Places [groupId] before [targetId], or last when null, as Actual's drag does. */
    fun placeGroup(
        groupId: String,
        targetId: String?,
    ): EnvelopeEditResult {
        val items =
            session
                .query(
                    """
                    SELECT id, sort_order FROM category_groups
                    WHERE IFNULL(tombstone, 0) = 0 AND IFNULL(is_income, 0) = 0
                    ORDER BY sort_order, id
                    """.trimIndent(),
                ).map { it.str("id").orEmpty() to it.double("sort_order") }
        if (items.none { it.first == groupId }) return EnvelopeEditResult.Rejected(EnvelopeCopy.NO_GROUP)
        return place("category_groups", groupId, items, targetId)
    }

    /** Places [categoryId] before [targetId] in its own group, or last in it when null. */
    fun placeCategory(
        categoryId: String,
        targetId: String?,
    ): EnvelopeEditResult {
        val row =
            session
                .query("SELECT cat_group FROM categories WHERE id = ? AND IFNULL(tombstone, 0) = 0", listOf(categoryId))
                .firstOrNull() ?: return EnvelopeEditResult.Rejected(EnvelopeCopy.ENTER_NAME)
        val items =
            session
                .query(
                    """
                    SELECT id, sort_order FROM categories
                    WHERE cat_group = ? AND IFNULL(tombstone, 0) = 0
                    ORDER BY sort_order, id
                    """.trimIndent(),
                    listOf(row.str("cat_group").orEmpty()),
                ).map { it.str("id").orEmpty() to it.double("sort_order") }
        return place("categories", categoryId, items, targetId)
    }

    private fun place(
        table: String,
        id: String,
        items: List<Pair<String, Double>>,
        targetId: String?,
    ): EnvelopeEditResult {
        if (targetId == id) return EnvelopeEditResult.Saved
        val (updates, order) = AccountsBook.shoveSortOrders(items, targetId)
        updates.forEach { (row, sort) -> SyncSchema.recordLocal(session, clock, table, row, "sort_order", sort.toString()) }
        SyncSchema.recordLocal(session, clock, table, id, "sort_order", order.toString())
        return EnvelopeEditResult.Saved
    }

    fun deleteCategory(categoryId: String): EnvelopeEditResult {
        if (hasCategoryActivity(categoryId)) return EnvelopeEditResult.Rejected(EnvelopeCopy.STILL_USED)
        SyncSchema.recordLocal(session, clock, "categories", categoryId, "tombstone", "1")
        return EnvelopeEditResult.Saved
    }

    fun deleteGroup(groupId: String): EnvelopeEditResult {
        val living =
            session
                .query(
                    """
                    SELECT COUNT(*) AS n FROM categories
                    WHERE cat_group = ? AND IFNULL(tombstone, 0) = 0
                    """.trimIndent(),
                    listOf(groupId),
                ).first()
                .long("n")
        if (living > 0L) return EnvelopeEditResult.Rejected(EnvelopeCopy.GROUP_STILL_USED)
        SyncSchema.recordLocal(session, clock, "category_groups", groupId, "tombstone", "1")
        return EnvelopeEditResult.Saved
    }

    private fun hasCategoryActivity(categoryId: String): Boolean {
        val budgeted =
            session
                .query(
                    "SELECT COUNT(*) AS n FROM zero_budgets WHERE category = ? AND amount != 0",
                    listOf(categoryId),
                ).first()
                .long("n")
        if (budgeted > 0) return true
        val reflect =
            session
                .query(
                    "SELECT COUNT(*) AS n FROM reflect_budgets WHERE category = ? AND amount != 0",
                    listOf(categoryId),
                ).first()
                .long("n")
        if (reflect > 0) return true
        val txns =
            session
                .query(
                    """
                    SELECT COUNT(*) AS n FROM transactions
                    WHERE category = ? AND IFNULL(tombstone, 0) = 0
                    """.trimIndent(),
                    listOf(categoryId),
                ).first()
                .long("n")
        return txns > 0
    }

    private fun readBudgeted(
        month: Int,
        categoryId: String,
    ): Long =
        session
            .query(
                "SELECT amount FROM zero_budgets WHERE month = ? AND category = ?",
                listOf(month, categoryId),
            ).firstOrNull()
            ?.long("amount")
            ?: session
                .query(
                    "SELECT amount FROM reflect_budgets WHERE month = ? AND category = ?",
                    listOf(month, categoryId),
                ).firstOrNull()
                ?.long("amount")
            ?: 0L

    private fun readAvailable(
        month: Int,
        categoryId: String,
    ): Long? {
        val monthView = YearMonth.of(month / 100, month % 100)
        return ShellReader.loadDetailed(session, monthView).categories[categoryId]?.availableMinor
    }

    private fun newId(): String =
        java.util.UUID
            .randomUUID()
            .toString()

    private fun nextGroupOrder(): Double {
        val max =
            session
                .query("SELECT MAX(sort_order) AS m FROM category_groups WHERE IFNULL(tombstone, 0) = 0")
                .firstOrNull()
                ?.double("m")
                ?: 0.0
        return max + 1.0
    }

    private fun nextCategoryOrder(groupId: String): Double {
        val max =
            session
                .query(
                    "SELECT MAX(sort_order) AS m FROM categories WHERE cat_group = ? AND IFNULL(tombstone, 0) = 0",
                    listOf(groupId),
                ).firstOrNull()
                ?.double("m")
                ?: 0.0
        return max + 1.0
    }
}
