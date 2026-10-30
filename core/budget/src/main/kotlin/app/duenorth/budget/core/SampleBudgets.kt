package app.duenorth.budget.core

/** One sample budget a debug build can create, as the sample budgets page lists it. */
data class MockBudgetDataset(
    val id: String,
    val title: String,
    val summary: String,
)

/**
 * The sample budgets a debug build offers: the catalog, and a way to create one. Their data lives in the
 * :core:samples module, which only debug builds include, so release builds carry none of it.
 */
interface SampleBudgets {
    val all: List<MockBudgetDataset>

    /** The dataset debug builds create every new budget from. */
    val walkthrough: String

    /** The name pre-filled on the new budget forms in debug builds. */
    val walkthroughName: String

    /** The empty file made the way release builds make one, which starts the get started list. */
    val brandNew: String

    /** Creates a budget from [datasetId], named [name] or else the dataset's title. */
    fun create(
        library: BudgetLibrary,
        datasetId: String,
        name: String? = null,
    ): CreateResult
}
