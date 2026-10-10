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
    const val PREVIOUS = "previous month"
    const val NEXT = "next month"
    const val MANAGE = "categories"
    const val RENAME = "rename"
    const val HIDE = "hide"
    const val SHOW = "show"
    const val DELETE = "delete"
    const val MOVE_UP = "move up"
    const val MOVE_DOWN = "move down"
    const val ENTER_NAME = "enter a name"
}

data class ManageCategoryRow(
    val id: String,
    val name: String,
    val hidden: Boolean,
)

data class ManageGroupRow(
    val id: String,
    val name: String,
    val categories: List<ManageCategoryRow>,
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
        SyncSchema.recordLocal(session, clock, "zero_budget_months", month.toString(), "buffered", amountMinor.toString())
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

    fun moveGroupEarlier(groupId: String): EnvelopeEditResult = swapGroupOrder(groupId, earlier = true)

    fun moveGroupLater(groupId: String): EnvelopeEditResult = swapGroupOrder(groupId, earlier = false)

    fun moveCategoryEarlier(categoryId: String): EnvelopeEditResult = swapCategoryOrder(categoryId, earlier = true)

    fun moveCategoryLater(categoryId: String): EnvelopeEditResult = swapCategoryOrder(categoryId, earlier = false)

    private fun swapGroupOrder(
        groupId: String,
        earlier: Boolean,
    ): EnvelopeEditResult {
        val ordered =
            session.query(
                """
                SELECT id, sort_order FROM category_groups
                WHERE IFNULL(tombstone, 0) = 0 AND IFNULL(is_income, 0) = 0
                ORDER BY sort_order, id
                """.trimIndent(),
            )
        val index = ordered.indexOfFirst { it.str("id") == groupId }
        if (index < 0) return EnvelopeEditResult.Rejected(EnvelopeCopy.ENTER_NAME)
        val swapIndex = if (earlier) index - 1 else index + 1
        if (swapIndex !in ordered.indices) return EnvelopeEditResult.Saved
        return swapSort("category_groups", ordered[index], ordered[swapIndex])
    }

    private fun swapCategoryOrder(
        categoryId: String,
        earlier: Boolean,
    ): EnvelopeEditResult {
        val row =
            session
                .query(
                    "SELECT id, cat_group, sort_order FROM categories WHERE id = ? AND IFNULL(tombstone, 0) = 0",
                    listOf(categoryId),
                ).firstOrNull()
                ?: return EnvelopeEditResult.Rejected(EnvelopeCopy.ENTER_NAME)
        val groupId = row.str("cat_group").orEmpty()
        val ordered =
            session.query(
                """
                SELECT id, sort_order FROM categories
                WHERE cat_group = ? AND IFNULL(tombstone, 0) = 0
                ORDER BY sort_order, id
                """.trimIndent(),
                listOf(groupId),
            )
        val index = ordered.indexOfFirst { it.str("id") == categoryId }
        if (index < 0) return EnvelopeEditResult.Rejected(EnvelopeCopy.ENTER_NAME)
        val swapIndex = if (earlier) index - 1 else index + 1
        if (swapIndex !in ordered.indices) return EnvelopeEditResult.Saved
        return swapSort("categories", ordered[index], ordered[swapIndex])
    }

    private fun swapSort(
        table: String,
        left: SqlRow,
        right: SqlRow,
    ): EnvelopeEditResult {
        val leftId = left.str("id").orEmpty()
        val rightId = right.str("id").orEmpty()
        val leftOrder = left.double("sort_order")
        val rightOrder = right.double("sort_order")
        SyncSchema.recordLocal(session, clock, table, leftId, "sort_order", rightOrder.toString())
        SyncSchema.recordLocal(session, clock, table, rightId, "sort_order", leftOrder.toString())
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

    private fun newId(): String = java.util.UUID.randomUUID().toString()

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
