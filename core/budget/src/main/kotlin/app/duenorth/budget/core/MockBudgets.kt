package app.duenorth.budget.core

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.YearMonth

/**
 * Catalog of seed budgets for debug builds. Dates are anchored to [today] so the
 * open month, inbox, due schedules, and review always have something to show.
 */
data class MockBudgetDataset(
    val id: String,
    val title: String,
    val summary: String,
)

object MockBudgets {
    const val HOUSEHOLD = "household"
    const val FULL_TOUR = "full-tour"
    const val WIDE = "wide"

    val all: List<MockBudgetDataset> =
        listOf(
            MockBudgetDataset(
                id = HOUSEHOLD,
                title = "household",
                summary = "checking, savings, envelopes, leftover to budget, and an inbox",
            ),
            MockBudgetDataset(
                id = FULL_TOUR,
                title = "full tour",
                summary = "credit card, schedules, rules, hold, overspend, reconcile mix, bank link",
            ),
            MockBudgetDataset(
                id = WIDE,
                title = "wide month",
                summary = "many category groups and a long register for scroll and gesture checks",
            ),
        )

    fun byId(id: String): MockBudgetDataset? = all.firstOrNull { it.id == id }

    fun seed(
        session: SqlSession,
        datasetId: String,
        today: LocalDate,
    ) {
        val dataset = byId(datasetId) ?: error("unknown mock budget: $datasetId")
        when (dataset.id) {
            HOUSEHOLD -> seedHousehold(session, today)
            FULL_TOUR -> seedFullTour(session, today)
            WIDE -> seedWide(session, today)
        }
    }

    private fun seedHousehold(
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

    private fun seedFullTour(
        session: SqlSession,
        today: LocalDate,
    ) {
        val thisMonth = YearMonth.from(today)
        val lastMonth = thisMonth.minusMonths(1)
        val last = lastMonth.toActualMonth()
        val current = thisMonth.toActualMonth()
        val todayDate = today.toActualDate()
        val yesterday = today.minusDays(1).toActualDate()
        val nextWeek = today.plusDays(7)

        prefs(session, "USD")
        group(session, "g-inc", "Income", income = true, order = 0)
        group(session, "g-liv", "Living", order = 1)
        group(session, "g-fun", "Fun", order = 2)
        group(session, "g-bills", "Bills", order = 3)
        group(session, "g-pay", "Credit Card", order = 4)
        category(session, "c-sal", "Salary", "g-inc", income = true, order = 0)
        category(session, "c-food", "Groceries", "g-liv", order = 0)
        category(session, "c-gas", "Transport", "g-liv", order = 1)
        category(session, "c-fun", "Dining", "g-fun", order = 0)
        category(session, "c-rent", "Rent", "g-bills", order = 0)
        category(session, "c-util", "Utilities", "g-bills", order = 1)
        category(session, "c-card", "Card Payment", "g-pay", order = 0)

        account(session, "checking", "Checking", type = "checking", order = 0, bankLinked = true)
        account(session, "cash", "Cash", type = "checking", order = 1)
        account(session, "card", "Visa", type = "credit", order = 2)
        account(session, "savings", "Savings", offBudget = true, type = "savings", order = 3)
        session.exec(
            "INSERT OR REPLACE INTO preferences (id, value) VALUES (?, ?)",
            listOf(ActualSchema.cardPaymentKey("card"), "c-card"),
        )

        payee(session, "p-work", "Acme Payroll")
        payee(session, "p-rent", "Oak Street Rent")
        payee(session, "p-cafe", "Cafe Nero")
        payee(session, "p-cafe2", "Cafe Nero Downtown")
        payee(session, "p-market", "Market Basket")
        payee(session, "p-amazon", "Amazon")
        payee(session, "p-power", "City Power")
        payee(session, "p-uber", "Uber")
        payee(session, "p-netflix", "Netflix")

        budget(session, "b-food-l", last, "c-food", 400_000)
        budget(session, "b-gas-l", last, "c-gas", 100_000)
        budget(session, "b-fun-l", last, "c-fun", 150_000)
        budget(session, "b-rent-l", last, "c-rent", 1_500_000)
        budget(session, "b-util-l", last, "c-util", 140_000)
        // Groceries deliberately underfunded vs this month's spend for overspend cover flows.
        budget(session, "b-food", current, "c-food", 80_000)
        budget(session, "b-gas", current, "c-gas", 120_000)
        budget(session, "b-fun", current, "c-fun", 100_000)
        budget(session, "b-rent", current, "c-rent", 1_500_000)
        budget(session, "b-util", current, "c-util", 140_000)
        session.exec(
            "INSERT INTO zero_budget_months (id, buffered) VALUES (?, ?)",
            listOf(current, 50_000L),
        )

        txn(session, "t-pay", "checking", "c-sal", 5_000_000, "p-work", date(lastMonth, 15), 1.0, cleared = true)
        txn(session, "t-rent-l", "checking", "c-rent", -1_500_000, "p-rent", date(lastMonth, 1), 0.5, cleared = true)
        txn(session, "t-food-l", "checking", "c-food", -320_000, "p-market", date(lastMonth, 10), 2.0, cleared = true)
        txn(session, "t-fun-l", "card", "c-fun", -90_000, "p-cafe", date(lastMonth, 20), 3.0, cleared = true)
        txn(session, "t-save", "savings", null, 250_000, null, date(thisMonth, 1), 1.0, cleared = true)

        // Cleared / uncleared mix for reconcile.
        txn(session, "t-clear-1", "checking", "c-gas", -45_000, "p-uber", yesterday, 10.0, cleared = true)
        txn(session, "t-open-1", "checking", "c-food", -120_000, "p-market", yesterday, 11.0, cleared = false)
        txn(session, "t-open-2", "checking", "c-fun", -35_000, "p-cafe", todayDate, 12.0, cleared = false)

        // Inbox (uncategorized) plus a card charge.
        txn(session, "t-inbox-1", "checking", null, -2_200, "p-amazon", todayDate, 13.0, cleared = false)
        txn(session, "t-inbox-2", "checking", null, -6_500, "p-cafe2", todayDate, 14.0, cleared = false)
        txn(session, "t-card", "card", "c-fun", -48_000, "p-amazon", todayDate, 15.0, cleared = true)

        // Transfer checking -> cash.
        txn(session, "t-xfer-a", "checking", null, -50_000, null, yesterday, 16.0, transfer = "t-xfer-b", cleared = true)
        txn(session, "t-xfer-b", "cash", null, 50_000, null, yesterday, 16.0, transfer = "t-xfer-a", cleared = true)

        // Split: groceries + dining under one parent.
        txn(session, "t-split", "checking", null, -90_000, "p-market", yesterday, 17.0, parent = true, cleared = true)
        txn(session, "t-split-a", "checking", "c-food", -60_000, "p-market", yesterday, 17.1, childOf = "t-split", cleared = true)
        txn(session, "t-split-b", "checking", "c-fun", -30_000, "p-market", yesterday, 17.2, childOf = "t-split", cleared = true)

        RulesBook(session).insertPayeeCategoryRule("p-uber", "c-gas")

        insertMonthlySchedule(
            session,
            scheduleId = "sched-rent",
            ruleId = "rule-rent",
            name = "Rent",
            accountId = "checking",
            payeeId = "p-rent",
            amount = -1_500_000,
            categoryId = "c-rent",
            next = today,
        )
        insertMonthlySchedule(
            session,
            scheduleId = "sched-netflix",
            ruleId = "rule-netflix",
            name = "Netflix",
            accountId = "card",
            payeeId = "p-netflix",
            amount = -1_599,
            categoryId = "c-fun",
            next = nextWeek,
        )
    }

    private fun seedWide(
        session: SqlSession,
        today: LocalDate,
    ) {
        val thisMonth = YearMonth.from(today)
        val lastMonth = thisMonth.minusMonths(1)
        val last = lastMonth.toActualMonth()
        val current = thisMonth.toActualMonth()

        prefs(session, "USD")
        group(session, "g-inc", "Income", income = true, order = 0)
        category(session, "c-sal", "Salary", "g-inc", income = true, order = 0)
        account(session, "checking", "Checking", type = "checking", order = 0)
        payee(session, "p-work", "Acme Payroll")
        payee(session, "p-shop", "Shop")

        txn(session, "t-pay", "checking", "c-sal", 8_000_000, "p-work", date(lastMonth, 15), 1.0)

        repeat(24) { index ->
            val groupId = "g-$index"
            val categoryId = "c-$index"
            group(session, groupId, "group ${index + 1}", order = index + 1)
            category(session, categoryId, "category ${index + 1}", groupId, order = 0)
            budget(session, "b-l-$index", last, categoryId, 50_000)
            budget(session, "b-$index", current, categoryId, 50_000)
            txn(
                session,
                "t-l-$index",
                "checking",
                categoryId,
                -20_000,
                "p-shop",
                date(lastMonth, (index % 28) + 1),
                (index + 2).toDouble(),
            )
            txn(
                session,
                "t-$index",
                "checking",
                categoryId,
                -5_000 - index * 100L,
                "p-shop",
                date(thisMonth, minOf((index % 28) + 1, today.dayOfMonth.coerceAtLeast(1))),
                (index + 100).toDouble(),
            )
        }

        // Extra register rows for lazy-list pressure.
        repeat(80) { index ->
            txn(
                session,
                "t-reg-$index",
                "checking",
                "c-${index % 24}",
                -1_000L - index,
                "p-shop",
                date(thisMonth, minOf((index % 28) + 1, today.dayOfMonth.coerceAtLeast(1))),
                (200 + index).toDouble(),
            )
        }
    }

    private fun prefs(
        session: SqlSession,
        currency: String,
    ) {
        session.exec(
            "INSERT OR REPLACE INTO preferences (id, value) VALUES (?, ?)",
            listOf("budgetType", "envelope"),
        )
        session.exec(
            "INSERT OR REPLACE INTO preferences (id, value) VALUES (?, ?)",
            listOf("currency", currency),
        )
    }

    private fun group(
        session: SqlSession,
        id: String,
        name: String,
        income: Boolean = false,
        order: Int,
    ) {
        session.exec(
            """
            INSERT INTO category_groups (id, name, is_income, sort_order, tombstone)
            VALUES (?, ?, ?, ?, 0)
            """.trimIndent(),
            listOf(id, name, if (income) 1 else 0, order.toDouble()),
        )
    }

    private fun category(
        session: SqlSession,
        id: String,
        name: String,
        groupId: String,
        income: Boolean = false,
        order: Int,
    ) {
        session.exec(
            """
            INSERT INTO categories (id, name, is_income, cat_group, sort_order, tombstone)
            VALUES (?, ?, ?, ?, ?, 0)
            """.trimIndent(),
            listOf(id, name, if (income) 1 else 0, groupId, order.toDouble()),
        )
    }

    private fun account(
        session: SqlSession,
        id: String,
        name: String,
        offBudget: Boolean = false,
        type: String,
        order: Int,
        bankLinked: Boolean = false,
    ) {
        session.exec(
            """
            INSERT INTO accounts (
                id, name, offbudget, closed, type, sort_order, tombstone,
                account_id, account_sync_source
            ) VALUES (?, ?, ?, 0, ?, ?, 0, ?, ?)
            """.trimIndent(),
            listOf(
                id,
                name,
                if (offBudget) 1 else 0,
                type,
                order.toDouble(),
                if (bankLinked) "bank-$id" else null,
                if (bankLinked) "simpleFin" else null,
            ),
        )
    }

    private fun payee(
        session: SqlSession,
        id: String,
        name: String,
    ) {
        session.exec(
            "INSERT INTO payees (id, name, tombstone) VALUES (?, ?, 0)",
            listOf(id, name),
        )
    }

    private fun budget(
        session: SqlSession,
        id: String,
        month: Int,
        categoryId: String,
        amount: Long,
        carryover: Boolean = false,
    ) {
        session.exec(
            """
            INSERT INTO zero_budgets (id, month, category, amount, carryover)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            listOf(id, month, categoryId, amount, if (carryover) 1 else 0),
        )
    }

    private fun txn(
        session: SqlSession,
        id: String,
        account: String,
        category: String?,
        amount: Long,
        payee: String?,
        date: Int,
        sort: Double,
        transfer: String? = null,
        parent: Boolean = false,
        childOf: String? = null,
        cleared: Boolean = true,
        note: String? = null,
    ) {
        session.exec(
            """
            INSERT INTO transactions (
                id, isParent, isChild, parent_id, acct, category, amount, description, notes,
                date, transferred_id, sort_order, cleared, reconciled, tombstone
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0)
            """.trimIndent(),
            listOf(
                id,
                if (parent) 1 else 0,
                if (childOf != null) 1 else 0,
                childOf,
                account,
                category,
                amount,
                payee,
                note,
                date,
                transfer,
                sort,
                if (cleared) 1 else 0,
            ),
        )
    }

    private fun insertMonthlySchedule(
        session: SqlSession,
        scheduleId: String,
        ruleId: String,
        name: String,
        accountId: String,
        payeeId: String,
        amount: Long,
        categoryId: String?,
        next: LocalDate,
    ) {
        val nextIso = next.toString()
        val conditions =
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("op", "is")
                        put("field", "account")
                        put("value", accountId)
                        put("type", "id")
                    },
                )
                add(
                    buildJsonObject {
                        put("op", "is")
                        put("field", "payee")
                        put("value", payeeId)
                        put("type", "id")
                    },
                )
                add(
                    buildJsonObject {
                        put("op", "is")
                        put("field", "amount")
                        put("value", amount)
                        put("type", "number")
                    },
                )
                if (categoryId != null) {
                    add(
                        buildJsonObject {
                            put("op", "is")
                            put("field", "category")
                            put("value", categoryId)
                            put("type", "id")
                        },
                    )
                }
                add(
                    buildJsonObject {
                        put("op", "isapprox")
                        put("field", "date")
                        put(
                            "value",
                            buildJsonObject {
                                put("frequency", "monthly")
                                put("start", nextIso)
                                put("interval", 1)
                                put("endMode", "never")
                            },
                        )
                    },
                )
            }
        RulesBook(session).insertRule(
            stage = null,
            conditions = conditions,
            actions =
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("op", "link-schedule")
                            put("value", scheduleId)
                        },
                    )
                },
            id = ruleId,
        )
        session.exec(
            """
            INSERT INTO schedules (id, name, rule, completed, posts_transaction, tombstone)
            VALUES (?, ?, ?, 0, 0, 0)
            """.trimIndent(),
            listOf(scheduleId, name, ruleId),
        )
        session.exec(
            """
            INSERT INTO schedules_next_date (
                id, schedule_id, local_next_date, local_next_date_ts, base_next_date, base_next_date_ts
            ) VALUES (?, ?, ?, 1, ?, 1)
            """.trimIndent(),
            listOf("nd-$scheduleId", scheduleId, nextIso, nextIso),
        )
    }

    private fun date(
        month: YearMonth,
        day: Int,
    ): Int = month.atDay(day.coerceIn(1, month.lengthOfMonth())).toActualDate()

    private fun LocalDate.toActualDate(): Int = year * 10000 + monthValue * 100 + dayOfMonth
}
