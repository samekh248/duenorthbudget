package app.duenorth.budget.core

import java.time.YearMonth

sealed interface MonthResult {
    data class Applied(
        val book: BudgetBook,
    ) : MonthResult

    data class Refused(
        val reason: String,
    ) : MonthResult
}

object MonthEdits {
    fun assign(
        book: BudgetBook,
        month: YearMonth,
        categoryId: String,
        budgeted: Long,
    ): MonthResult {
        val category = liveExpense(book, categoryId) ?: return MonthResult.Refused(ShellCopy.ENTER_AMOUNT)
        val key = month.toActualMonth()
        val current = book.assignment(key, category.id)
        return MonthResult.Applied(book.withAssignment(current.copy(amount = budgeted)))
    }

    fun move(
        book: BudgetBook,
        month: YearMonth,
        fromId: String,
        toId: String,
        amount: Long,
    ): MonthResult {
        if (amount <= 0L) return MonthResult.Refused(ShellCopy.ENTER_AMOUNT)
        if (fromId == toId) return MonthResult.Refused(ShellCopy.NOT_ENOUGH_AVAILABLE)
        val source = liveExpense(book, fromId) ?: return MonthResult.Refused(ShellCopy.NOT_ENOUGH_AVAILABLE)
        val destination = liveExpense(book, toId) ?: return MonthResult.Refused(ShellCopy.NOT_ENOUGH_AVAILABLE)
        val available =
            book
                .month(month)
                .categories
                .firstOrNull { it.id == source.id }
                ?.availableMinor
                ?: return MonthResult.Refused(ShellCopy.NOT_ENOUGH_AVAILABLE)
        if (amount > available) return MonthResult.Refused(ShellCopy.NOT_ENOUGH_AVAILABLE)
        val key = month.toActualMonth()
        val from = book.assignment(key, source.id)
        val to = book.assignment(key, destination.id)
        return MonthResult.Applied(
            book
                .withAssignment(from.copy(amount = from.amount - amount))
                .withAssignment(to.copy(amount = to.amount + amount)),
        )
    }

    fun carryover(
        book: BudgetBook,
        month: YearMonth,
        categoryId: String,
        enabled: Boolean,
    ): MonthResult {
        val category = liveExpense(book, categoryId) ?: return MonthResult.Refused(ShellCopy.ENTER_AMOUNT)
        val key = month.toActualMonth()
        val current = book.assignment(key, category.id)
        return MonthResult.Applied(book.withAssignment(current.copy(carryover = enabled)))
    }

    fun hold(
        book: BudgetBook,
        month: YearMonth,
        amount: Long,
    ): MonthResult {
        if (book.mode != BudgetMode.ENVELOPE) return MonthResult.Refused(ShellCopy.NOT_ENOUGH_TO_HOLD)
        if (amount < 0L) return MonthResult.Refused(ShellCopy.NOT_ENOUGH_TO_HOLD)
        val key = month.toActualMonth()
        val room = book.month(month).shell.headerMinor + (book.buffers[key] ?: 0L)
        if (amount > room) return MonthResult.Refused(ShellCopy.NOT_ENOUGH_TO_HOLD)
        val next = book.buffers.toMutableMap()
        if (amount == 0L) next.remove(key) else next[key] = amount
        return MonthResult.Applied(book.copy(buffers = next))
    }

    fun addGroup(
        book: BudgetBook,
        name: String,
        id: String,
    ): MonthResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return MonthResult.Refused(ShellCopy.ENTER_NAME)
        val sort = book.groups.filter { !it.tombstone && !it.isIncome }.maxOfOrNull { it.sortOrder } ?: 0.0
        val group = GroupRecord(id, trimmed, isIncome = false, hidden = false, sortOrder = sort + 1.0, tombstone = false)
        return MonthResult.Applied(book.copy(groups = book.groups + group))
    }

    fun renameGroup(
        book: BudgetBook,
        id: String,
        name: String,
    ): MonthResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return MonthResult.Refused(ShellCopy.ENTER_NAME)
        if (book.groups.none { it.id == id && !it.tombstone }) return MonthResult.Refused(ShellCopy.ENTER_NAME)
        return MonthResult.Applied(
            book.copy(groups = book.groups.map { if (it.id == id) it.copy(name = trimmed) else it }),
        )
    }

    fun moveGroup(
        book: BudgetBook,
        id: String,
        direction: Int,
    ): MonthResult {
        val visible = book.groups.filter { !it.tombstone && !it.isIncome }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        val index = visible.indexOfFirst { it.id == id }
        val swap = index + direction
        if (index < 0 || swap !in visible.indices) return MonthResult.Applied(book)
        val order = visible.toMutableList()
        order.add(swap, order.removeAt(index))
        val sortById = order.mapIndexed { position, group -> group.id to position.toDouble() }.toMap()
        return MonthResult.Applied(
            book.copy(groups = book.groups.map { it.copy(sortOrder = sortById[it.id] ?: it.sortOrder) }),
        )
    }

    fun deleteGroup(
        book: BudgetBook,
        id: String,
    ): MonthResult {
        val group = book.groups.find { it.id == id && !it.tombstone } ?: return MonthResult.Applied(book)
        val members = book.categories.filter { it.groupId == group.id && !it.tombstone }
        if (members.any { blocksDelete(book, it.id) }) return MonthResult.Refused(ShellCopy.MOVE_FIRST)
        val memberIds = members.map { it.id }.toSet()
        return MonthResult.Applied(
            book.copy(
                groups = book.groups.map { if (it.id == id) it.copy(tombstone = true) else it },
                categories = book.categories.map { if (it.id in memberIds) it.copy(tombstone = true) else it },
            ),
        )
    }

    fun addCategory(
        book: BudgetBook,
        groupId: String,
        name: String,
        id: String,
    ): MonthResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return MonthResult.Refused(ShellCopy.ENTER_NAME)
        val group =
            book.groups.find { it.id == groupId && !it.tombstone && !it.isIncome }
                ?: return MonthResult.Refused(ShellCopy.ENTER_NAME)
        val sort =
            book.categories
                .filter { it.groupId == group.id && !it.tombstone }
                .maxOfOrNull { it.sortOrder } ?: 0.0
        val category =
            CategoryRecord(
                id = id,
                name = trimmed,
                groupId = group.id,
                isIncome = false,
                hidden = false,
                sortOrder = sort + 1.0,
                tombstone = false,
            )
        return MonthResult.Applied(book.copy(categories = book.categories + category))
    }

    fun renameCategory(
        book: BudgetBook,
        id: String,
        name: String,
    ): MonthResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return MonthResult.Refused(ShellCopy.ENTER_NAME)
        if (liveExpense(book, id) == null) return MonthResult.Refused(ShellCopy.ENTER_NAME)
        return MonthResult.Applied(
            book.copy(categories = book.categories.map { if (it.id == id) it.copy(name = trimmed) else it }),
        )
    }

    fun moveCategory(
        book: BudgetBook,
        id: String,
        direction: Int,
    ): MonthResult {
        val category = liveExpense(book, id) ?: return MonthResult.Applied(book)
        val visible =
            book.categories
                .filter { !it.tombstone && !it.isIncome && it.groupId == category.groupId }
                .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        val index = visible.indexOfFirst { it.id == id }
        val swap = index + direction
        if (index < 0 || swap !in visible.indices) return MonthResult.Applied(book)
        val order = visible.toMutableList()
        order.add(swap, order.removeAt(index))
        val sortById = order.mapIndexed { position, row -> row.id to position.toDouble() }.toMap()
        return MonthResult.Applied(
            book.copy(categories = book.categories.map { it.copy(sortOrder = sortById[it.id] ?: it.sortOrder) }),
        )
    }

    fun hidden(
        book: BudgetBook,
        id: String,
        hidden: Boolean,
    ): MonthResult {
        if (liveExpense(book, id) == null) return MonthResult.Applied(book)
        return MonthResult.Applied(
            book.copy(categories = book.categories.map { if (it.id == id) it.copy(hidden = hidden) else it }),
        )
    }

    fun deleteCategory(
        book: BudgetBook,
        id: String,
    ): MonthResult {
        if (liveExpense(book, id) == null) return MonthResult.Applied(book)
        if (blocksDelete(book, id)) return MonthResult.Refused(ShellCopy.MOVE_FIRST)
        return MonthResult.Applied(
            book.copy(categories = book.categories.map { if (it.id == id) it.copy(tombstone = true) else it }),
        )
    }

    private fun liveExpense(
        book: BudgetBook,
        id: String,
    ): CategoryRecord? = book.categories.find { it.id == id && !it.tombstone && !it.isIncome }

    private fun blocksDelete(
        book: BudgetBook,
        id: String,
    ): Boolean {
        if (id in book.categorized) return true
        if (book.assignments.any { it.categoryId == id && (it.amount != 0L || it.carryover) }) return true
        val months = loadedMonths(book)
        return months.any { month ->
            book.month(month).categories.any { it.id == id && it.availableMinor != 0L }
        }
    }

    private fun loadedMonths(book: BudgetBook): List<YearMonth> {
        val keys = book.spent.keys.map { it.first } + book.assignments.map { it.month } + book.buffers.keys
        if (keys.isEmpty()) return emptyList()
        val last = nextMonth(keys.max())
        return ActualMonthMath.monthsThrough(keys.min(), last).map { value ->
            YearMonth.of(value / 100, value % 100)
        }
    }

    private fun nextMonth(value: Int): Int {
        var year = value / 100
        var month = value % 100 + 1
        if (month == 13) {
            month = 1
            year += 1
        }
        return year * 100 + month
    }
}
