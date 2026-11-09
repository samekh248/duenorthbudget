package app.duenorth.budget.core

import app.duenorth.budget.core.SampleSeed.account
import app.duenorth.budget.core.SampleSeed.budget
import app.duenorth.budget.core.SampleSeed.category
import app.duenorth.budget.core.SampleSeed.date
import app.duenorth.budget.core.SampleSeed.group
import app.duenorth.budget.core.SampleSeed.payee
import app.duenorth.budget.core.SampleSeed.prefs
import app.duenorth.budget.core.SampleSeed.txn
import java.time.LocalDate
import java.time.YearMonth

/**
 * The one sample budget every build carries, release included: the welcome page offers it as "try a sample budget"
 * so a person can look around before making their own. Debug builds also list it as "household" with the others.
 * Dates are anchored to today, so the open month, last month and the inbox always have something to show.
 */
object HouseholdSample {
    /** What the sample is called on the phone, so it reads as a sample in the budget list. */
    const val NAME = "Sample budget"

    fun seed(
        session: SqlSession,
        today: LocalDate,
    ) {
        val thisMonth = YearMonth.from(today)
        val lastMonth = thisMonth.minusMonths(1)
        val last = lastMonth.toActualMonth()
        val current = thisMonth.toActualMonth()

        prefs(session, "USD")
        group(session, "g-inc", "Income", income = true, order = 0)
        group(session, "g-liv", "Living", order = 1)
        group(session, "g-bills", "Bills", order = 2)
        category(session, "c-sal", "Salary", "g-inc", income = true, order = 0)
        category(session, "c-food", "Food", "g-liv", order = 0)
        category(session, "c-gas", "Gas", "g-liv", order = 1)
        category(session, "c-rent", "Rent", "g-bills", order = 0)
        category(session, "c-util", "Utilities", "g-bills", order = 1)

        account(session, "checking", "Checking", type = "checking", order = 0)
        account(session, "savings", "Savings", offBudget = true, type = "savings", order = 1)

        payee(session, "p-work", "Acme Payroll")
        payee(session, "p-cafe", "Cafe")
        payee(session, "p-bus", "Bus")
        payee(session, "p-rent", "Oak Street Rent")
        payee(session, "p-power", "City Power")
        payee(session, "p-market", "Market")

        budget(session, "b-food-l", last, "c-food", 200_000)
        budget(session, "b-gas-l", last, "c-gas", 80_000)
        budget(session, "b-rent-l", last, "c-rent", 1_200_000)
        budget(session, "b-util-l", last, "c-util", 120_000)
        budget(session, "b-food", current, "c-food", 220_000)
        budget(session, "b-gas", current, "c-gas", 80_000)
        budget(session, "b-rent", current, "c-rent", 1_200_000)
        budget(session, "b-util", current, "c-util", 120_000)

        txn(session, "t-pay", "checking", "c-sal", 4_200_000, "p-work", date(lastMonth, 15), 1.0)
        txn(session, "t-food-l", "checking", "c-food", -150_000, "p-cafe", date(lastMonth, 16), 2.0)
        txn(session, "t-gas-l", "checking", "c-gas", -60_000, "p-bus", date(lastMonth, 18), 3.0)
        txn(session, "t-rent-l", "checking", "c-rent", -1_200_000, "p-rent", date(lastMonth, 1), 0.5)
        txn(session, "t-util-l", "checking", "c-util", -95_000, "p-power", date(lastMonth, 12), 1.5)
        txn(session, "t-save", "savings", null, 500_000, null, date(thisMonth, 1), 1.0)
        txn(session, "t-cafe", "checking", null, -2_500, "p-cafe", date(thisMonth, minOf(2, today.dayOfMonth)), 2.0)
        txn(session, "t-market", "checking", null, -4_800, "p-market", date(thisMonth, minOf(3, today.dayOfMonth)), 3.0)
        txn(session, "t-bus", "checking", null, -1_000, "p-bus", date(thisMonth, minOf(4, today.dayOfMonth)), 4.0)
    }
}

/** Creates the [HouseholdSample] budget, named [name], and makes it the open budget. */
fun BudgetLibrary.createSample(name: String = HouseholdSample.NAME): CreateResult =
    createSeeded(name) { session, today -> HouseholdSample.seed(session, today) }
