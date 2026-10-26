package app.duenorth.budget

import app.duenorth.budget.core.CategoryManagePage
import app.duenorth.budget.core.ManageCategoryRow
import app.duenorth.budget.core.ManageGroupRow

/** A budget's categories as the categories page reads them: some used, one hidden, one empty group. */
object CategoryManageSamples {
    private fun cat(
        id: String,
        name: String,
        inUse: Boolean = true,
        hidden: Boolean = false,
    ) = ManageCategoryRow(id, name, hidden, inUse)

    val page =
        CategoryManagePage(
            listOf(
                ManageGroupRow(
                    "g-bills",
                    "Bills",
                    listOf(
                        cat("c-rent", "Rent"),
                        cat("c-power", "Electric"),
                        cat("c-water", "Water"),
                        cat("c-net", "Internet"),
                        cat("c-phone", "Phone"),
                        cat("c-ins", "Insurance"),
                    ),
                ),
                ManageGroupRow(
                    "g-food",
                    "Food",
                    listOf(cat("c-groc", "Groceries"), cat("c-rest", "Restaurants"), cat("c-coffee", "Coffee")),
                ),
                ManageGroupRow(
                    "g-car",
                    "Transport",
                    listOf(cat("c-gas", "Gas"), cat("c-car", "Car payment"), cat("c-park", "Parking"), cat("c-transit", "Transit")),
                ),
                ManageGroupRow(
                    "g-save",
                    "Savings",
                    listOf(
                        cat("c-emerg", "Emergency"),
                        cat("c-vac", "Vacation"),
                        cat("c-gift", "Gifts"),
                        cat("c-gym", "Old gym", inUse = false, hidden = true),
                    ),
                ),
                ManageGroupRow("g-pets", "Pets", emptyList()),
            ),
        )
}
