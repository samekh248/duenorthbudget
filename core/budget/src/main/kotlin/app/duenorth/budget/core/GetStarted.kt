package app.duenorth.budget.core

/** One line of a new budget's get started list, in the order the list shows them. */
enum class GetStartedStep {
    ACCOUNTS,
    CATEGORIES,
    ASSIGN,
    SCHEDULE,
}

data class GetStartedLine(
    val step: GetStartedStep,
    val done: Boolean,
)

/**
 * The get started list a budget made on this phone shows over its month until every line is done
 * or the person hides it. Each line is done when the budget itself shows it, so nothing is ticked by hand.
 */
object GetStarted {
    fun lines(
        shell: MonthShell,
        schedules: List<ScheduleSummary>,
    ): List<GetStartedLine> {
        val categories = shell.groups.flatMap { it.categories }
        return listOf(
            GetStartedLine(GetStartedStep.ACCOUNTS, shell.accounts.any { !it.closed }),
            GetStartedLine(GetStartedStep.CATEGORIES, categories.isNotEmpty()),
            // Done once something is budgeted and to budget has come down to zero.
            GetStartedLine(GetStartedStep.ASSIGN, categories.any { it.budgetedMinor != 0L } && shell.headerMinor == 0L),
            GetStartedLine(GetStartedStep.SCHEDULE, schedules.isNotEmpty()),
        )
    }

    /** The list to show for [shell], or null when the budget has none or every line is done. */
    fun showing(
        settings: PhoneSettings,
        shell: MonthShell?,
        schedules: List<ScheduleSummary>,
    ): List<GetStartedLine>? {
        if (shell == null || shell.budgetId !in settings.getStarted) return null
        return lines(shell, schedules).takeUnless { lines -> lines.all { it.done } }
    }
}

object GetStartedCopy {
    const val TITLE = "get started"
    const val ACCOUNTS = "add your accounts"
    const val CATEGORIES = "set up categories"
    const val ASSIGN = "assign this month's money"
    const val SCHEDULE = "add a bill that repeats"
    const val HIDE = "hide this list"

    fun label(step: GetStartedStep): String =
        when (step) {
            GetStartedStep.ACCOUNTS -> ACCOUNTS
            GetStartedStep.CATEGORIES -> CATEGORIES
            GetStartedStep.ASSIGN -> ASSIGN
            GetStartedStep.SCHEDULE -> SCHEDULE
        }

    fun progress(lines: List<GetStartedLine>): String {
        val done = lines.count { it.done }
        return if (done == 0) "${lines.size} steps · about 5 minutes" else "$done of ${lines.size} done"
    }

    fun waiting(amount: String) = "$amount waiting"

    fun over(amount: String) = "$amount over"
}

/** What a new file starts with, as desktop Actual names it. */
object NewBudgetCopy {
    const val INCOME = "Income"
    const val STARTING_BALANCES = "Starting Balances"
}

/** A starter category group: its name, its categories, and whether it starts checked. */
data class StarterGroup(
    val name: String,
    val categories: List<String>,
    val picked: Boolean = true,
)

/** The starter set behind get started's categories line, to trim before it is added. */
object StarterCategories {
    val all: List<StarterGroup> =
        listOf(
            StarterGroup("Bills", listOf("Rent or mortgage", "Electric", "Water", "Internet", "Phone")),
            StarterGroup("Everyday", listOf("Groceries", "Gas", "Household")),
            StarterGroup("Fun", listOf("Eating out", "Entertainment")),
            StarterGroup("Kids", listOf("Childcare", "School"), picked = false),
            StarterGroup("Savings", listOf("Emergency fund", "Vacation")),
        )
}

object StarterCategoriesCopy {
    const val TITLE = "categories"
    const val LEAD = "start from a set and trim it. you can rename or add more later."
    const val ADD = "add"
    const val OWN = "make my own"
    const val PICK_ONE = "check at least one group"
}
