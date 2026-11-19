package app.duenorth.budget

import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.GroupRow
import java.time.LocalDate

/**
 * How the category balance widget (design B, the ledger list, half Metro and half Material 3) fills the space it is given. Each step up in size
 * shows more rather than the same thing larger:
 *
 * - one cell tall and narrow: up to three lines of name and whole balance, no title;
 * - one cell tall and wide: the month over a strip of categories side by side, name over balance;
 * - two cells or more: the month and days left over one row per category, as many as fit;
 * - wide enough: budgeted and spent columns beside what is left;
 * - tall enough: a spent bar under each row and a total of what is left in the rows shown.
 */
internal data class CategoryWidgetPlan(
    val shape: Shape,
    /** Lines (or strip columns) there is room for; the widget shows the first this many chosen categories. */
    val slots: Int,
    /** Budgeted and spent columns beside what is left. */
    val columns: Boolean = false,
    /** Balances with cents; narrow lists show whole amounts so the name keeps its room. */
    val cents: Boolean = false,
    /** A spent bar under each row. */
    val bars: Boolean = false,
    /** What is left in the rows shown, under the list. */
    val total: Boolean = false,
) {
    enum class Shape { COMPACT, STRIP, LIST }

    companion object {
        /** Under this the widget is one launcher cell tall. */
        const val SHORT_MAX_DP = 110

        /** From this width a short widget lays categories side by side, and a list shows cents. */
        const val WIDE_DP = 200

        /** From this width a list adds budgeted and spent columns. */
        const val COLUMNS_DP = 280

        /** From this height a list adds bars and a total. */
        const val TALL_DP = 250

        private const val PADDING = 24
        private const val HEADER = 28
        private const val CARD_PADDING = 4
        private const val COLUMN_HEADS = 16
        private const val FOOTER = 48
        private const val COMPACT_LINE = 22
        private const val STRIP_COLUMN = 100

        fun of(
            widthDp: Int,
            heightDp: Int,
        ): CategoryWidgetPlan {
            if (heightDp < SHORT_MAX_DP) {
                return if (widthDp >= WIDE_DP) {
                    CategoryWidgetPlan(Shape.STRIP, slots = (widthDp / STRIP_COLUMN).coerceIn(2, 5), cents = true)
                } else {
                    CategoryWidgetPlan(Shape.COMPACT, slots = ((heightDp - 16) / COMPACT_LINE).coerceIn(1, 3))
                }
            }
            val columns = widthDp >= COLUMNS_DP
            val tall = heightDp >= TALL_DP
            // widget_category_row: a 1dp rule over a 28dp line, and a 5dp bar with 3dp under it when tall.
            val rowHeight = if (tall) 37 else 29
            val room = heightDp - PADDING - HEADER - CARD_PADDING - (if (columns) COLUMN_HEADS else 0) - (if (tall) FOOTER else 0)
            return CategoryWidgetPlan(
                Shape.LIST,
                slots = (room / rowHeight).coerceAtLeast(1),
                columns = columns,
                cents = widthDp >= WIDE_DP,
                bars = tall,
                total = tall,
            )
        }

        /** Days left in [today]'s month, today included. */
        fun daysLeft(today: LocalDate): Int = today.lengthOfMonth() - today.dayOfMonth + 1

        /** The chosen categories found in this month's [groups], in the order chosen; a deleted one drops out. */
        fun resolve(
            ids: List<String>,
            groups: List<GroupRow>,
        ): List<CategoryRow> {
            val byId = groups.flatMap { it.categories }.associateBy { it.id }
            return ids.mapNotNull { byId[it] }
        }

        /** How much of what was budgeted is spent, 0 to 100; full when overspent or spent with nothing budgeted. */
        fun spentPercent(row: CategoryRow): Int {
            val spent = -row.spentMinor
            if (row.availableMinor < 0) return 100
            val base = row.availableMinor + spent
            if (base <= 0) return 0
            return (spent * 100 / base).toInt().coerceIn(0, 100)
        }
    }
}
