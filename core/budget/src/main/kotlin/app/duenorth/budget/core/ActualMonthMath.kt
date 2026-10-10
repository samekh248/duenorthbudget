package app.duenorth.budget.core

import java.time.YearMonth

/**
 * Actual's envelope sheet (`envelope.ts` in @actual-app/core 26.8.1) and the tracking
 * month balance, in integer minor units. See contracts/budget-file.md.
 */
object ActualMonthMath {
    data class GroupFact(
        val id: String,
        val isIncome: Boolean,
        val sortOrder: Double,
    )

    data class CategoryFact(
        val id: String,
        val groupId: String,
        val isIncome: Boolean,
    )

    data class AssignmentFact(
        val month: Int,
        val categoryId: String,
        val amount: Long,
        val carryover: Boolean,
    )

    data class Figures(
        val headerLabel: String,
        val headerMinor: Long,
        val groupAvailable: Map<String, Long>,
    )

    data class CategoryMonthStat(
        val categoryId: String,
        val groupId: String,
        val budgetedMinor: Long,
        val spentMinor: Long,
        val availableMinor: Long,
        val carryover: Boolean,
    )

    data class DetailedFigures(
        val figures: Figures,
        val categories: Map<String, CategoryMonthStat>,
        val bufferedMinor: Long,
    )

    fun projectDetailed(
        mode: BudgetMode,
        month: YearMonth,
        groups: List<GroupFact>,
        categories: List<CategoryFact>,
        spentByCategoryMonth: Map<Pair<Int, String>, Long>,
        assignments: List<AssignmentFact>,
        buffers: Map<Int, Long>,
    ): DetailedFigures {
        val target = month.toActualMonth()
        val expense = categories.filter { !it.isIncome }
        val income = categories.filter { it.isIncome }
        return if (mode == BudgetMode.TRACKING) {
            trackingDetailed(target, groups, expense, spentByCategoryMonth, assignments)
        } else {
            envelopeDetailed(target, groups, expense, income, spentByCategoryMonth, assignments, buffers)
        }
    }

    fun project(
        mode: BudgetMode,
        month: YearMonth,
        groups: List<GroupFact>,
        categories: List<CategoryFact>,
        spentByCategoryMonth: Map<Pair<Int, String>, Long>,
        assignments: List<AssignmentFact>,
        buffers: Map<Int, Long>,
    ): Figures {
        val target = month.toActualMonth()
        val expense = categories.filter { !it.isIncome }
        val income = categories.filter { it.isIncome }
        return if (mode == BudgetMode.TRACKING) {
            tracking(target, groups, expense, spentByCategoryMonth, assignments)
        } else {
            envelope(target, groups, expense, income, spentByCategoryMonth, assignments, buffers)
        }
    }

    private fun tracking(
        target: Int,
        groups: List<GroupFact>,
        expense: List<CategoryFact>,
        spent: Map<Pair<Int, String>, Long>,
        assignments: List<AssignmentFact>,
    ): Figures {
        val available = HashMap<String, Long>()
        for (category in expense) {
            val balance = assignment(assignments, target, category.id).amount + (spent[target to category.id] ?: 0L)
            available[category.groupId] = (available[category.groupId] ?: 0L) + balance
        }
        val shown = groups.filter { !it.isIncome }.map { it.id }.toSet()
        val header = shown.sumOf { available[it] ?: 0L }
        return Figures(ShellCopy.BALANCE, header, available.filterKeys { it in shown })
    }

    private fun trackingDetailed(
        target: Int,
        groups: List<GroupFact>,
        expense: List<CategoryFact>,
        spent: Map<Pair<Int, String>, Long>,
        assignments: List<AssignmentFact>,
    ): DetailedFigures {
        val stats = HashMap<String, CategoryMonthStat>()
        val available = HashMap<String, Long>()
        for (category in expense) {
            val budgeted = assignment(assignments, target, category.id).amount
            val spentMinor = spent[target to category.id] ?: 0L
            val balance = budgeted + spentMinor
            stats[category.id] =
                CategoryMonthStat(
                    categoryId = category.id,
                    groupId = category.groupId,
                    budgetedMinor = budgeted,
                    spentMinor = spentMinor,
                    availableMinor = balance,
                    carryover = false,
                )
            available[category.groupId] = (available[category.groupId] ?: 0L) + balance
        }
        val shown = groups.filter { !it.isIncome }.map { it.id }.toSet()
        val header = shown.sumOf { available[it] ?: 0L }
        return DetailedFigures(
            figures = Figures(ShellCopy.BALANCE, header, available.filterKeys { it in shown }),
            categories = stats,
            bufferedMinor = 0L,
        )
    }

    private fun envelopeDetailed(
        target: Int,
        groups: List<GroupFact>,
        expense: List<CategoryFact>,
        income: List<CategoryFact>,
        spent: Map<Pair<Int, String>, Long>,
        assignments: List<AssignmentFact>,
        buffers: Map<Int, Long>,
    ): DetailedFigures {
        val dataMonths = HashSet<Int>()
        dataMonths.addAll(spent.keys.map { it.first })
        dataMonths.addAll(assignments.map { it.month })
        dataMonths.addAll(buffers.keys)
        val months = monthsThrough(dataMonths.minOrNull()?.let { minOf(it, target) } ?: target, target)

        var previousLeft = HashMap<String, Long>()
        var previousCarry = HashMap<String, Boolean>()
        var previousToBudget = 0L
        var previousBuffered = 0L
        var toBudget = 0L
        var currentLeft = HashMap<String, Long>()
        var currentCarry = HashMap<String, Boolean>()
        var bufferedForTarget = 0L

        for (month in months) {
            val incomeSum = income.sumOf { spent[month to it.id] ?: 0L }
            val fromLast = previousToBudget + previousBuffered
            val lastOver =
                expense.sumOf { category ->
                    if (previousCarry[category.id] == true) {
                        0L
                    } else {
                        minOf(0L, previousLeft[category.id] ?: 0L)
                    }
                }
            val budgetedSum = expense.sumOf { assignment(assignments, month, it.id).amount }
            val manual = buffers[month] ?: 0L
            val auto =
                income.sumOf { category ->
                    if (assignment(assignments, month, category.id).carryover) {
                        spent[month to category.id] ?: 0L
                    } else {
                        0L
                    }
                }
            val buffered = if (manual != 0L) manual else auto
            if (month == target) bufferedForTarget = buffered
            toBudget = incomeSum + fromLast + lastOver + (-budgetedSum) - buffered

            val left = HashMap<String, Long>()
            val carry = HashMap<String, Boolean>()
            for (category in expense) {
                val row = assignment(assignments, month, category.id)
                val previous = previousLeft[category.id] ?: 0L
                val carried = if (previousCarry[category.id] == true) previous else maxOf(0L, previous)
                left[category.id] = row.amount + (spent[month to category.id] ?: 0L) + carried
                carry[category.id] = row.carryover
            }
            previousLeft = left
            previousCarry = carry
            previousToBudget = toBudget
            previousBuffered = buffered
            currentLeft = left
            currentCarry = carry
        }

        val available = HashMap<String, Long>()
        val stats = HashMap<String, CategoryMonthStat>()
        for (category in expense) {
            val row = assignment(assignments, target, category.id)
            val spentMinor = spent[target to category.id] ?: 0L
            val avail = currentLeft[category.id] ?: 0L
            stats[category.id] =
                CategoryMonthStat(
                    categoryId = category.id,
                    groupId = category.groupId,
                    budgetedMinor = row.amount,
                    spentMinor = spentMinor,
                    availableMinor = avail,
                    carryover = currentCarry[category.id] == true,
                )
            available[category.groupId] = (available[category.groupId] ?: 0L) + avail
        }
        val shown = groups.filter { !it.isIncome }.map { it.id }.toSet()
        return DetailedFigures(
            figures = Figures(ShellCopy.TO_BUDGET, toBudget, available.filterKeys { it in shown }),
            categories = stats,
            bufferedMinor = bufferedForTarget,
        )
    }

    private fun envelope(
        target: Int,
        groups: List<GroupFact>,
        expense: List<CategoryFact>,
        income: List<CategoryFact>,
        spent: Map<Pair<Int, String>, Long>,
        assignments: List<AssignmentFact>,
        buffers: Map<Int, Long>,
    ): Figures {
        val dataMonths = HashSet<Int>()
        dataMonths.addAll(spent.keys.map { it.first })
        dataMonths.addAll(assignments.map { it.month })
        dataMonths.addAll(buffers.keys)
        val months = monthsThrough(dataMonths.minOrNull()?.let { minOf(it, target) } ?: target, target)

        var previousLeft = HashMap<String, Long>()
        var previousCarry = HashMap<String, Boolean>()
        var previousToBudget = 0L
        var previousBuffered = 0L
        var toBudget = 0L
        var currentLeft = HashMap<String, Long>()

        for (month in months) {
            val incomeSum = income.sumOf { spent[month to it.id] ?: 0L }
            val fromLast = previousToBudget + previousBuffered
            val lastOver =
                expense.sumOf { category ->
                    if (previousCarry[category.id] == true) {
                        0L
                    } else {
                        minOf(0L, previousLeft[category.id] ?: 0L)
                    }
                }
            val budgetedSum = expense.sumOf { assignment(assignments, month, it.id).amount }
            val manual = buffers[month] ?: 0L
            val auto =
                income.sumOf { category ->
                    if (assignment(assignments, month, category.id).carryover) {
                        spent[month to category.id] ?: 0L
                    } else {
                        0L
                    }
                }
            val buffered = if (manual != 0L) manual else auto
            toBudget = incomeSum + fromLast + lastOver + (-budgetedSum) - buffered

            val left = HashMap<String, Long>()
            val carry = HashMap<String, Boolean>()
            for (category in expense) {
                val row = assignment(assignments, month, category.id)
                val previous = previousLeft[category.id] ?: 0L
                val carried = if (previousCarry[category.id] == true) previous else maxOf(0L, previous)
                left[category.id] = row.amount + (spent[month to category.id] ?: 0L) + carried
                carry[category.id] = row.carryover
            }
            previousLeft = left
            previousCarry = carry
            previousToBudget = toBudget
            previousBuffered = buffered
            currentLeft = left
        }

        val available = HashMap<String, Long>()
        for (category in expense) {
            available[category.groupId] = (available[category.groupId] ?: 0L) + (currentLeft[category.id] ?: 0L)
        }
        val shown = groups.filter { !it.isIncome }.map { it.id }.toSet()
        return Figures(ShellCopy.TO_BUDGET, toBudget, available.filterKeys { it in shown })
    }

    private fun assignment(
        assignments: List<AssignmentFact>,
        month: Int,
        categoryId: String,
    ): AssignmentFact =
        assignments.firstOrNull { it.month == month && it.categoryId == categoryId }
            ?: AssignmentFact(month, categoryId, 0L, false)

    internal fun monthsThrough(
        start: Int,
        end: Int,
    ): List<Int> {
        if (start > end) return listOf(end)
        val months = mutableListOf<Int>()
        var year = start / 100
        var month = start % 100
        while (year * 100 + month <= end) {
            months.add(year * 100 + month)
            month += 1
            if (month == 13) {
                month = 1
                year += 1
            }
        }
        return months
    }
}
