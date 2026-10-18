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
    // A move lowers the source's budgeted and raises the destination's, as EnvelopeBook.moveAvailable writes it.
    fun shift(id: String) =
        when (id) {
            fromCategoryId -> -amountMinor
            toCategoryId -> amountMinor
            else -> 0L
        }
    return copy(
        groups =
            groups.map { group ->
                group.copy(
                    availableMinor = group.availableMinor + group.categories.sumOf { shift(it.id) },
                    categories =
                        group.categories.map { row ->
                            val delta = shift(row.id)
                            if (delta == 0L) {
                                row
                            } else {
                                row.copy(budgetedMinor = row.budgetedMinor + delta, availableMinor = row.availableMinor + delta)
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
