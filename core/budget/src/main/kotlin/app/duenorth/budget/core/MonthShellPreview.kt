package app.duenorth.budget.core

/**
 * Applies envelope edits to an in-memory [MonthShell] so the UI can update before disk IO finishes.
 */
fun MonthShell.withBudgetedAssignment(
    categoryId: String,
    budgetedMinor: Long,
): MonthShell {
    val category = groups.flatMap { it.categories }.firstOrNull { it.id == categoryId } ?: return this
    val delta = budgetedMinor - category.budgetedMinor
    return copy(
        headerMinor = headerMinor - delta,
        groups =
            groups.map { group ->
                val touches = group.categories.any { it.id == categoryId }
                group.copy(
                    availableMinor = if (touches) group.availableMinor + delta else group.availableMinor,
                    categories =
                        group.categories.map { row ->
                            if (row.id == categoryId) {
                                row.copy(
                                    budgetedMinor = budgetedMinor,
                                    availableMinor = row.availableMinor + delta,
                                )
                            } else {
                                row
                            }
                        },
                )
            },
    )
}

fun MonthShell.withMoveAvailable(
    fromCategoryId: String,
    toCategoryId: String,
    amountMinor: Long,
): MonthShell {
    return copy(
        groups =
            groups.map { group ->
                group.copy(
                    categories =
                        group.categories.map { row ->
                            when (row.id) {
                                fromCategoryId -> row.copy(availableMinor = row.availableMinor - amountMinor)
                                toCategoryId -> row.copy(availableMinor = row.availableMinor + amountMinor)
                                else -> row
                            }
                        },
                )
            },
    )
}

fun MonthShell.withHold(amountMinor: Long): MonthShell =
    copy(
        headerMinor = headerMinor - amountMinor,
        bufferedMinor = bufferedMinor + amountMinor,
    )

fun MonthShell.withReleaseHold(): MonthShell =
    copy(
        headerMinor = headerMinor + bufferedMinor,
        bufferedMinor = 0L,
    )
