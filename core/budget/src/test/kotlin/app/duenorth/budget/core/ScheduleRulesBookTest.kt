package app.duenorth.budget.core

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ScheduleRulesBookTest {
    private val clock = BudgetClock { LocalDate.of(2026, 10, 9) }

    @Test
    fun postMonthlyScheduleMovesNextDate() {
        db { session, book ->
            val schedules = SchedulesBook(session, clock)
            val scheduleId = seedMonthlyRent(session, amount = -90_000, next = "2026-10-09")
            assertEquals(1, schedules.listUpcoming().count { it.id == scheduleId })
            assertTrue(schedules.post(scheduleId) is ScheduleWriteResult.Posted)
            val page = book.read("checking")!!
            assertEquals(1, page.rows.size)
            assertEquals(-90_000L, page.rows.single().amountMinor)
            val row = schedules.listUpcoming().single { it.id == scheduleId }
            assertEquals(20261109, row.nextDate)
        }
    }

    @Test
    fun skipDoesNotCreateTransaction() {
        db { session, book ->
            val schedules = SchedulesBook(session, clock)
            val scheduleId = seedMonthlyRent(session, amount = -90_000, next = "2026-10-09")
            assertTrue(schedules.skip(scheduleId) is ScheduleWriteResult.Skipped)
            assertEquals(0, book.read("checking")!!.rows.size)
            val row = schedules.listUpcoming().single { it.id == scheduleId }
            assertEquals(20261109, row.nextDate)
        }
    }

    @Test
    fun rulesFillCategoryOnSave() {
        db { session, book ->
            val rules = RulesBook(session)
            val payeeId = "p-market"
            session.exec("INSERT INTO payees (id, name) VALUES (?, ?)", listOf(payeeId, "Market"))
            rules.insertPayeeCategoryRule(payeeId, "c-food")
            val saved =
                book.save(
                    TransactionDraft(
                        id = "t1",
                        accountId = "checking",
                        date = 20261009,
                        payee = "Market",
                        amountMinor = -500,
                        categoryId = null,
                        note = null,
                    ),
                )
            assertTrue(saved is WriteResult.Saved)
            val row = book.read("checking")!!.rows.single()
            assertEquals("Groceries", row.categoryLabel)
            assertEquals(-500L, spent(session, "c-food"))
        }
    }

    @Test
    fun laterStageRuleOverridesEarlier() {
        db { session, book ->
            val rules = RulesBook(session)
            val payeeId = "p-market"
            session.exec("INSERT INTO payees (id, name) VALUES (?, ?)", listOf(payeeId, "Market"))
            rules.insertPayeeCategoryRule(payeeId, "c-food", stage = null)
            val clearCategory =
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("op", "is")
                            put("field", "payee")
                            put("value", payeeId)
                            put("type", "id")
                        },
                    )
                }
            val clearAction =
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("op", "set")
                            put("field", "category")
                            put("value", JsonNull)
                        },
                    )
                }
            rules.insertRule("post", clearCategory, clearAction)
            book.save(
                TransactionDraft(
                    id = "t1",
                    accountId = "checking",
                    date = 20261009,
                    payee = "Market",
                    amountMinor = -500,
                    categoryId = null,
                    note = null,
                ),
            )
            val row = book.read("checking")!!.rows.single()
            assertEquals(RegisterCopy.NO_CATEGORY, row.categoryLabel)
        }
    }

    @Test
    fun disabledRuleDoesNothing() {
        db { session, book ->
            val rules = RulesBook(session)
            val payeeId = "p-market"
            session.exec("INSERT INTO payees (id, name) VALUES (?, ?)", listOf(payeeId, "Market"))
            val ruleId = rules.insertPayeeCategoryRule(payeeId, "c-food")
            rules.setEnabled(ruleId, enabled = false)
            book.save(
                TransactionDraft(
                    id = "t1",
                    accountId = "checking",
                    date = 20261009,
                    payee = "Market",
                    amountMinor = -500,
                    categoryId = null,
                    note = null,
                ),
            )
            assertEquals(RegisterCopy.NO_CATEGORY, book.read("checking")!!.rows.single().categoryLabel)
        }
    }

    @Test
    fun mergePayeesRewritesTransactions() {
        db { session, _ ->
            session.exec("INSERT INTO payees (id, name) VALUES ('p-amzn', 'AMZN')")
            session.exec("INSERT INTO payees (id, name) VALUES ('p-amz', 'Amazon')")
            session.exec(
                """
                INSERT INTO transactions (id, acct, amount, description, date, tombstone)
                VALUES ('t1', 'checking', -100, 'p-amzn', 20261001, 0)
                """.trimIndent(),
            )
            val payees = PayeeBook(session)
            assertTrue(payees.rename("p-amzn", "Amazon", confirmMerge = true) is PayeeWriteResult.Saved)
            val names =
                session
                    .query(
                        """
                        SELECT p.name AS name
                        FROM transactions t
                        JOIN payees p ON p.id = t.description
                        WHERE t.id = 't1'
                        """.trimIndent(),
                    ).map { it.str("name") }
            assertEquals(listOf("Amazon"), names)
            assertEquals(
                1,
                session
                    .query("SELECT COUNT(*) AS n FROM payees WHERE IFNULL(tombstone,0)=0")
                    .first()
                    .long("n"),
            )
        }
    }

    private fun seedMonthlyRent(
        session: SqlSession,
        amount: Long,
        next: String,
    ): String {
        val rules = RulesBook(session)
        val scheduleId = "sched-rent"
        val dateCond =
            buildJsonObject {
                put("op", "isapprox")
                put("field", "date")
                put(
                    "value",
                    buildJsonObject {
                        put("frequency", "monthly")
                        put("start", next)
                        put("interval", 1)
                        put("endMode", "never")
                    },
                )
            }
        val conditions =
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("op", "is")
                        put("field", "account")
                        put("value", "checking")
                        put("type", "id")
                    },
                )
                add(
                    buildJsonObject {
                        put("op", "is")
                        put("field", "payee")
                        put("value", "p-rent")
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
                add(dateCond)
            }
        session.exec("INSERT INTO payees (id, name) VALUES ('p-rent', 'Rent')")
        val ruleId =
            rules.insertRule(
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
                id = "rule-rent",
            )
        session.exec(
            """
            INSERT INTO schedules (id, name, rule, completed, posts_transaction, tombstone)
            VALUES (?, 'Rent', ?, 0, 0, 0)
            """.trimIndent(),
            listOf(scheduleId, ruleId),
        )
        session.exec(
            """
            INSERT INTO schedules_next_date (
                id, schedule_id, local_next_date, local_next_date_ts, base_next_date, base_next_date_ts
            ) VALUES ('nd1', ?, ?, 1, ?, 1)
            """.trimIndent(),
            listOf(scheduleId, next, next),
        )
        return scheduleId
    }

    private fun spent(
        session: SqlSession,
        category: String,
    ) = session
        .query(
            """
            SELECT COALESCE(SUM(amount), 0) AS spent
            FROM transactions
            WHERE category = ? AND IFNULL(tombstone, 0) = 0 AND IFNULL(isParent, 0) = 0
            """.trimIndent(),
            listOf(category),
        ).first()
        .long("spent")

    private fun db(block: (SqlSession, RegisterBook) -> Unit) {
        val file = File.createTempFile("sched", ".sqlite")
        file.delete()
        JdbcSessionOpener().use(file) { session ->
            ActualSchema.create(session)
            seed(session)
            ActualSchema.ensure(session)
            block(session, RegisterBook(session))
        }
    }

    private fun seed(session: SqlSession) {
        session.exec(
            "INSERT INTO category_groups (id, name) VALUES ('g-food', 'Food')",
        )
        session.exec(
            "INSERT INTO categories (id, name, is_income, cat_group) VALUES ('c-food', 'Groceries', 0, 'g-food')",
        )
        session.exec(
            "INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('checking', 'Checking', 0, 'checking', 0)",
        )
        session.exec(
            "INSERT INTO zero_budgets (id, month, category, amount, carryover) VALUES ('b-food', 202610, 'c-food', 100000, 0)",
        )
    }
}
