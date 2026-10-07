package app.duenorth.budget.core

import java.time.YearMonth

data class GroupRecord(
    val id: String,
    val name: String,
    val isIncome: Boolean,
    val hidden: Boolean,
    val sortOrder: Double,
    val tombstone: Boolean,
)

data class CategoryRecord(
    val id: String,
    val name: String,
    val groupId: String,
    val isIncome: Boolean,
    val hidden: Boolean,
    val sortOrder: Double,
    val tombstone: Boolean,
)

data class CategoryMonth(
    val id: String,
    val groupId: String,
    val name: String,
    val sortOrder: Double,
    val hidden: Boolean,
    val budgetedMinor: Long,
    val spentMinor: Long,
    val availableMinor: Long,
    val carryover: Boolean,
)

data class BudgetBook(
    val id: String,
    val name: String,
    val mode: BudgetMode,
    val currency: CurrencySpec,
    val groups: List<GroupRecord>,
    val categories: List<CategoryRecord>,
    val spent: Map<Pair<Int, String>, Long>,
    val categorized: Set<String>,
    val assignments: List<ActualMonthMath.AssignmentFact>,
    val buffers: Map<Int, Long>,
    val accounts: List<AccountRow>,
    val inbox: List<InboxRow>,
)

data class MonthPage(
    val shell: MonthShell,
    val categories: List<CategoryMonth>,
)

fun BudgetBook.month(month: YearMonth): MonthPage {
    val expenseGroups =
        groups
            .filter { !it.tombstone && !it.isIncome }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
    val groupIds = expenseGroups.map { it.id }.toSet()
    val expense =
        categories.filter { category ->
            !category.tombstone && !category.isIncome && category.groupId in groupIds
        }
    val income =
        categories.filter { category ->
            !category.tombstone && category.isIncome
        }
    val figures =
        ActualMonthMath.project(
            mode = mode,
            month = month,
            groups =
                groups.filter { !it.tombstone }.map { group ->
                    ActualMonthMath.GroupFact(group.id, group.isIncome, group.sortOrder)
                },
            categories =
                (expense + income).map { category ->
                    ActualMonthMath.CategoryFact(category.id, category.groupId, category.isIncome)
                },
            spentByCategoryMonth = spent,
            assignments = assignments,
            buffers = if (mode == BudgetMode.ENVELOPE) buffers else emptyMap(),
        )
    val key = month.toActualMonth()
    val rows =
        expense
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .map { category ->
                val row =
                    assignments.firstOrNull { it.month == key && it.categoryId == category.id }
                        ?: ActualMonthMath.AssignmentFact(key, category.id, 0L, false)
                CategoryMonth(
                    id = category.id,
                    groupId = category.groupId,
                    name = category.name,
                    sortOrder = category.sortOrder,
                    hidden = category.hidden,
                    budgetedMinor = row.amount,
                    spentMinor = spent[key to category.id] ?: 0L,
                    availableMinor = figures.categoryAvailable[category.id] ?: 0L,
                    carryover = row.carryover,
                )
            }
    val visible = rows.filter { !it.hidden }
    return MonthPage(
        shell =
            MonthShell(
                budgetId = id,
                budgetName = name,
                mode = mode,
                currency = currency,
                month = month,
                headerLabel = figures.headerLabel,
                headerMinor = figures.headerMinor,
                groups =
                    expenseGroups.map { group ->
                        GroupRow(
                            id = group.id,
                            name = group.name,
                            availableMinor = visible.filter { it.groupId == group.id }.sumOf { it.availableMinor },
                        )
                    },
                accounts = accounts,
                inbox = inbox,
                bufferedMinor = if (mode == BudgetMode.ENVELOPE) buffers[key] ?: 0L else 0L,
            ),
        categories = rows,
    )
}

internal fun BudgetBook.withAssignment(fact: ActualMonthMath.AssignmentFact): BudgetBook {
    val rest = assignments.filterNot { it.month == fact.month && it.categoryId == fact.categoryId }
    val kept =
        if (fact.amount == 0L && !fact.carryover) {
            rest
        } else {
            rest + fact
        }
    return copy(assignments = kept)
}

internal fun BudgetBook.assignment(
    month: Int,
    categoryId: String,
): ActualMonthMath.AssignmentFact =
    assignments.firstOrNull { it.month == month && it.categoryId == categoryId }
        ?: ActualMonthMath.AssignmentFact(month, categoryId, 0L, false)
