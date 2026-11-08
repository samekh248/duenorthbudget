package app.duenorth.budget.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Every kind of edit the undo line can name: run it, undo it, and the whole file reads as it did
 * before. Local bookkeeping that is not budget data (a finished reconcile's statement, the last
 * import's review list) is left out of the comparison.
 */
class UndoEveryEditTest {
    private val october = YearMonth.of(2026, 10)

    class Ctx(
        val library: BudgetLibrary,
        val sync: SyncCoordinator,
        val id: String,
        val snap: (Ctx) -> Map<String, Map<String, Map<String, Any?>>>,
    ) {
        var before: Map<String, Map<String, Map<String, Any?>>> = emptyMap()

        fun reset() {
            library.journal.clear()
            before = snap(this)
        }
    }

    private fun scenarios(): List<Pair<String, (Ctx) -> Any?>> =
        listOf(
            "assign" to { c -> c.sync.assign(c.id, "c-food", october, "40") },
            "budget edit" to { c -> c.sync.assignBudgets(c.id, october, mapOf("c-food" to 7_000L, "c-gas" to 1_000L)) },
            "move" to { c -> c.sync.moveCategory(c.id, "c-food", "c-gas", october, "5") },
            "rollover" to { c -> c.sync.setCategoryCarryover(c.id, "c-food", october, true) },
            "hold" to { c -> c.sync.holdForNextMonth(c.id, october, "10") },
            "release hold" to { c ->
                c.sync.holdForNextMonth(c.id, october, "10")
                c.reset()
                c.sync.releaseMonthHold(c.id, october)
            },
            "fill copy last" to { c -> c.sync.fillMonth(c.id, october, MonthFillKind.COPY_LAST, true) },
            "fill average" to { c -> c.sync.fillMonth(c.id, october, MonthFillKind.AVERAGE, true) },
            "fill zero" to { c -> c.sync.fillMonth(c.id, october, MonthFillKind.ZERO, true) },
            "to budget" to { c -> c.sync.transferToBudget(c.id, "c-food", october, "5") },
            "auto hold" to { c -> c.sync.setAutoHold(c.id, "c-sal", october, AutoHoldChoice.ON) },
            "add group" to { c -> c.sync.addCategoryGroup(c.id, "Fun") },
            "starter categories" to { c -> c.sync.addStarterCategories(c.id, listOf(StarterGroup("Home", listOf("Rent", "Power")))) },
            "add category" to { c -> c.sync.addEnvelopeCategory(c.id, "g-food", "Snacks") },
            "hide category" to { c -> c.sync.hideEnvelopeCategory(c.id, "c-gas", true) },
            "rename group" to { c -> c.sync.renameCategoryGroup(c.id, "g-food", "Eats") },
            "rename category" to { c -> c.sync.renameEnvelopeCategory(c.id, "c-food", "Food") },
            "hide group" to { c -> c.sync.hideCategoryGroup(c.id, "g-food", true) },
            "link bank" to { c ->
                c.sync.linkBankAccount(
                    c.id,
                    "checking",
                    BankProvider.SIMPLEFIN,
                    ExternalAccount("ext-1", "Chk", "Bank", "bank.com", 12_000L),
                )
            },
            "unlink bank" to { c ->
                c.sync.linkBankAccount(
                    c.id,
                    "checking",
                    BankProvider.SIMPLEFIN,
                    ExternalAccount("ext-1", "Chk", "Bank", "bank.com", 12_000L),
                )
                c.reset()
                c.sync.unlinkBankAccount(c.id, "checking")
            },
            "add transaction (quick)" to { c -> c.sync.addTransaction(c.id, "Cafe", "-4") },
            "add account" to { c -> c.library.createAccount(c.id, "Cash", false, 5_000L) },
            "rename account" to { c -> c.library.renameAccount(c.id, "checking", "Everyday") },
            "account note" to { c -> c.library.setAccountNote(c.id, "checking", "hello") },
            "reorder account" to { c -> c.library.moveAccount(c.id, "visa", "checking") },
            "close account" to { c -> c.library.closeAccount(c.id, "visa", "checking", null, confirmed = true) },
            "force close account" to { c -> c.library.closeAccount(c.id, "visa", null, null, forced = true, confirmed = true) },
            "reopen account" to { c ->
                c.library.closeAccount(c.id, "visa", "checking", null, confirmed = true)
                c.reset()
                c.library.reopenAccount(c.id, "visa")
            },
            "new transaction" to
                { c ->
                    c.library.saveTransaction(c.id, TransactionDraft("t-new", "checking", 20261008, "Bakery", -900, "c-food", "bread"))
                },
            "edit transaction" to
                { c ->
                    c.library.saveTransaction(c.id, TransactionDraft("t-groc", "checking", 20261005, "Grocer", -1_500, "c-food", "more"))
                },
            "new transfer" to { c ->
                c.library.saveTransaction(
                    c.id,
                    TransactionDraft("t-xfer", "checking", 20261008, "", -2_000, null, null, transferAccountId = "visa"),
                )
            },
            "categorize" to { c -> c.library.setTransactionCategory(c.id, "t-gas", "c-gas", false) },
            "categorize many" to { c -> c.library.setTransactionsCategory(c.id, mapOf("t-gas" to "c-gas", "t-groc" to "c-gas"), false) },
            "delete" to { c -> c.library.deleteTransaction(c.id, "t-groc", false) },
            "delete many" to { c -> c.library.deleteTransactions(c.id, listOf("t-groc", "t-gas"), false) },
            "clear" to { c -> c.library.setTransactionsCleared(c.id, listOf("t-groc"), true, false) },
            "unclear" to { c -> c.library.setTransactionsCleared(c.id, listOf("t-pay"), false, false) },
            "split" to
                { c -> c.library.splitTransaction(c.id, "t-groc", listOf(SplitPart("c-food", -700), SplitPart("c-gas", -500)), false) },
            "unsplit" to { c ->
                c.library.splitTransaction(c.id, "t-groc", listOf(SplitPart("c-food", -700), SplitPart("c-gas", -500)), false)
                c.reset()
                c.library.unsplitTransaction(c.id, "t-groc", false)
            },
            "move to account" to { c -> c.library.moveTransaction(c.id, "t-gas", "visa") },
            "match" to { c -> c.library.matchTransactions(c.id, "b-dinner", "t-dinner") },
            "merge" to { c -> c.library.mergeTransactions(c.id, listOf("b-dinner", "t-dinner"), false) },
            "adopt & match" to { c -> c.library.adoptAndMatch(c.id, "t-dinner", "b-dinner") },
            "move & merge" to { c -> c.library.moveAndMatch(c.id, "t-dinner", "b-dinner-visa") },
            "link transfer" to { c -> c.library.linkTransfer(c.id, "b-out", "b-in") },
            "not a match" to { c -> c.library.notAMatch(c.id, "b-dinner", "t-dinner") },
            "dismiss" to { c -> c.library.dismissFinding(c.id, "uncategorized:t-gas") },
            "make rule" to { c -> c.library.makeCategoryRule(c.id, "p-grocer", "c-food") },
            "rename payee" to { c -> c.library.renamePayee(c.id, "p-grocer", "Grocery Co") },
            "rename payee merge" to { c -> c.library.renamePayee(c.id, "p-grocer", "Gas Station", confirmMerge = true) },
            "rename payee + rule" to { c -> c.library.renamePayee(c.id, "p-grocer", "Grocery Co", rememberRule = true) },
            "flip sign" to { c ->
                c.library.startReconcile(c.id, "checking", "5000", "2026-10-07")
                c.reset()
                c.library.flipReconcileSign(c.id, "checking", "t-groc")
            },
            "reconcile" to { c ->
                c.library.startReconcile(c.id, "checking", "5000", "2026-10-07")
                c.library.toggleReconcileCleared(c.id, "checking", "t-groc")
                c.reset()
                c.library.finishReconcile(c.id, "checking", adjust = true)
            },
            "save schedule" to { c ->
                c.library.saveSchedule(
                    c.id,
                    ScheduleDraft(name = "Gym", accountId = "checking", payeeName = "Gym", amountMinor = -3_000, startIso = "2026-10-25"),
                )
            },
            "edit schedule" to { c ->
                c.library.saveSchedule(
                    c.id,
                    ScheduleDraft(
                        id = c.ctxSchedule(),
                        name = "Rent!",
                        accountId = "checking",
                        payeeName = "Landlord",
                        amountMinor = -60_000,
                        startIso = "2026-10-20",
                    ),
                )
            },
            "schedule amount" to { c -> c.library.updateScheduleAmount(c.id, c.ctxSchedule(), -55_000) },
            "post schedule" to { c -> c.library.postSchedule(c.id, c.ctxSchedule(), forceDuplicate = true) },
            "skip schedule" to { c -> c.library.skipSchedule(c.id, c.ctxSchedule()) },
            "move schedule" to { c ->
                c.library.useDatabase(c.id) { it.exec("ALTER TABLE schedules ADD COLUMN sort_order REAL") }
                c.library.saveSchedule(
                    c.id,
                    ScheduleDraft(name = "Gym", accountId = "checking", payeeName = "Gym", amountMinor = -3_000, startIso = "2026-10-20"),
                )
                c.library.useDatabase(c.id) { s ->
                    s.query("SELECT id FROM schedules").forEachIndexed { i, r ->
                        SyncSchema.write(
                            s,
                            "schedules",
                            r.str("id")!!,
                            "sort_order",
                            (i + 1) * 1000,
                        )
                    }
                }
                c.reset()
                val ids = c.library.useDatabase(c.id) { s -> s.query("SELECT id FROM schedules").map { it.str("id")!! } }!!
                c.library.moveSchedule(c.id, ids.last(), null)
            },
            "delete schedule" to { c -> c.library.deleteSchedule(c.id, c.ctxSchedule()) },
            "schedule from txn" to { c -> c.library.createScheduleFromTransaction(c.id, "t-groc", "monthly", "2026-11-05") },
            "category note" to { c -> c.library.saveCategoryNote(c.id, "c-food", "#template 50") },
            "to automations" to { c ->
                c.library.saveCategoryNote(c.id, "c-food", "#template 50")
                c.reset()
                c.library.migrateCategoryAutomations(c.id, "c-food")
            },
            "save automations" to { c ->
                c.library.saveCategoryNote(c.id, "c-food", "#template 50")
                val a = c.library.migrateCategoryAutomations(c.id, "c-food")!!
                c.reset()
                c.library.saveCategoryAutomations(c.id, "c-food", CategoryAutomations(a.entries + a.entries))
            },
            "back to notes" to { c ->
                c.library.saveCategoryNote(c.id, "c-food", "#template 50")
                c.library.migrateCategoryAutomations(c.id, "c-food")
                c.reset()
                c.library.unmigrateCategoryAutomations(c.id, "c-food")
            },
            "templates" to { c ->
                c.library.saveCategoryNote(c.id, "c-food", "#template 50")
                c.reset()
                c.library.applyTemplates(c.id, october, overwrite = true, confirmed = true)
            },
            "cleanup" to { c ->
                c.library.saveCategoryNote(c.id, "c-food", "#cleanup source")
                c.reset()
                c.library.cleanupMonth(c.id, october)
            },
            "save rule" to { c ->
                c.library.saveRule(
                    c.id,
                    RuleDraft(
                        null,
                        null,
                        "and",
                        listOf(RulePart.condition("notes", "contains", kotlinx.serialization.json.JsonPrimitive("x"))),
                        listOf(RulePart.action("category", "set", kotlinx.serialization.json.JsonPrimitive("c-food"))),
                    ),
                )
            },
            "delete rule" to { c ->
                c.library.makeCategoryRule(c.id, "p-grocer", "c-food")
                val rule =
                    c.library.useDatabase(
                        c.id,
                    ) { it.query("SELECT id FROM rules WHERE IFNULL(tombstone,0)=0").first().str("id")!! }!!
                c.reset()
                c.library.deleteRule(c.id, rule)
            },
            "import" to { c ->
                c.library.confirmImport(
                    c.id,
                    "checking",
                    listOf(ParsedImportRow(20261009, "Bookshop", -2_500, financialId = "imp-1"), ParsedImportRow(20261010, "Grocer", -800)),
                )
            },
            "import review category" to { c ->
                c.library.confirmImport(c.id, "checking", listOf(ParsedImportRow(20261009, "Bookshop", -2_500, financialId = "imp-1")))
                val t =
                    c.library.useDatabase(
                        c.id,
                    ) { it.query("SELECT id FROM transactions WHERE financial_id='imp-1'").first().str("id")!! }!!
                c.reset()
                c.library.setImportReviewCategory(c.id, t, "c-food")
            },
        )

    private fun Ctx.ctxSchedule(): String =
        library.useDatabase(id) {
            it.query("SELECT id FROM schedules WHERE IFNULL(tombstone,0)=0").first().str("id")!!
        }!!

    @Test
    fun everyEditUndoesBackToTheFileItStartedFrom() {
        val failures =
            scenarios().mapNotNull { (name, action) ->
                val line = runCatching { run(name, action) }.getOrElse { "ERROR  | $name | ${it::class.simpleName}: ${it.message}" }
                line.takeUnless { it.startsWith("OK") }
            }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun run(
        name: String,
        action: (Ctx) -> Any?,
    ): String {
        val root = tempRoot()
        try {
            val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { TODAY })
            val sync = SyncCoordinator(library, HttpActualTransport(), MemorySecretStore(), BudgetClock { TODAY }, ZoneOffset.UTC)
            val id = (library.create("Home", "USD") as CreateResult.Created).id
            library.switchTo(id)
            val c = Ctx(library, sync, id) { snapshot(it) }
            seed(c)
            c.reset()
            val result = action(c)
            val before = c.before
            val label = library.undoLabel(id)
            val afterAction = snapshot(c)
            val changed = diff(before, afterAction)
            if (label == null) return "NOSTEP | $name | result=$result | changed=${changed.size} ${changed.take(3)}"
            if (changed.isEmpty()) return "NOCHG  | $name | label=$label result=$result"
            val undone = library.undo(id)
            val after = snapshot(c)
            val left = diff(before, after)
            val more = if (library.undoLabel(id) != null) " (more steps left: ${library.undoLabel(id)})" else ""
            return if (undone is UndoResult.Undone && left.isEmpty()) {
                "OK     | $name | label=$label$more"
            } else {
                "BROKEN | $name | label=$label undo=$undone result=$result$more\n         left: ${left.take(
                    12,
                ).joinToString("\n         ")}"
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun seed(c: Ctx) {
        c.library.useDatabase(c.id) { s ->
            fun w(
                t: String,
                r: String,
                vararg v: Pair<String, Any?>,
            ) = SyncSchema.writeRow(s, t, r, *v)
            w("accounts", "checking", "name" to "Checking", "offbudget" to 0, "closed" to 0, "sort_order" to 1, "tombstone" to 0)
            w("accounts", "savings", "name" to "Savings", "offbudget" to 0, "closed" to 0, "sort_order" to 2, "tombstone" to 0)
            w("accounts", "visa", "name" to "Visa", "offbudget" to 0, "closed" to 0, "sort_order" to 3, "tombstone" to 0)
            w("category_groups", "g-inc", "name" to "Income", "is_income" to 1, "sort_order" to 0, "tombstone" to 0)
            w("category_groups", "g-food", "name" to "Food", "is_income" to 0, "sort_order" to 1, "tombstone" to 0)
            w("categories", "c-sal", "name" to "Salary", "cat_group" to "g-inc", "is_income" to 1, "sort_order" to 0, "tombstone" to 0)
            w("categories", "c-food", "name" to "Groceries", "cat_group" to "g-food", "is_income" to 0, "sort_order" to 1, "tombstone" to 0)
            w("categories", "c-gas", "name" to "Gas", "cat_group" to "g-food", "is_income" to 0, "sort_order" to 2, "tombstone" to 0)
            w("payees", "p-grocer", "name" to "Grocer", "tombstone" to 0)
            w("payees", "p-gas", "name" to "Gas Station", "tombstone" to 0)
            w("payees", "p-emp", "name" to "Employer", "tombstone" to 0)
            w("payees", "p-corner", "name" to "Corner Cafe", "tombstone" to 0)

            fun txn(
                id: String,
                acct: String,
                amount: Long,
                payee: String?,
                date: Int,
                category: String?,
                cleared: Int = 1,
                fin: String? = null,
            ) = w(
                "transactions",
                id,
                "acct" to acct,
                "amount" to amount,
                "description" to payee,
                "date" to date,
                "category" to category,
                "cleared" to cleared,
                "reconciled" to 0,
                "isParent" to 0,
                "isChild" to 0,
                "tombstone" to 0,
                "sort_order" to date.toDouble(),
                "financial_id" to fin,
            )
            txn("t-pay", "checking", 300_000, "p-emp", 20260901, "c-sal")
            txn("t-pay2", "checking", 300_000, "p-emp", 20261001, "c-sal")
            txn("t-groc", "checking", -1_200, "p-grocer", 20261005, "c-food", cleared = 0)
            txn("t-gas", "savings", -500, "p-gas", 20261006, null)
            txn("t-dinner", "checking", -4_000, "p-corner", 20261020, "c-food", cleared = 0)
            txn("b-dinner", "checking", -4_000, "p-corner", 20261021, null, fin = "f-d")
            txn("b-dinner-visa", "visa", -4_000, "p-corner", 20261021, null, fin = "f-dv")
            txn("b-out", "checking", -50_000, null, 20261020, null, fin = "f-out")
            txn("b-in", "visa", 50_000, null, 20261021, null, fin = "f-in")
            w("zero_budgets", "202609-c-food", "month" to 202609, "category" to "c-food", "amount" to 3_000, "carryover" to 0)
            w("zero_budgets", "202608-c-food", "month" to 202608, "category" to "c-food", "amount" to 2_000, "carryover" to 0)
            s.exec("INSERT OR REPLACE INTO preferences (id, value) VALUES ('flags.experimental', 'true')")
            s.exec("INSERT OR REPLACE INTO preferences (id, value) VALUES ('flags.goalTemplatesEnabled', 'true')")
            s.exec("INSERT OR REPLACE INTO preferences (id, value) VALUES ('flags.goalTemplatesUIEnabled', 'true')")
            w("zero_budgets", "202610-c-food", "month" to 202610, "category" to "c-food", "amount" to 2_500, "carryover" to 0)
        }
        c.library.saveSchedule(
            c.id,
            ScheduleDraft(name = "Rent", accountId = "checking", payeeName = "Landlord", amountMinor = -50_000, startIso = "2026-10-20"),
        )
    }

    private val skip = setOf("messages", "sync_state", "conflicts", "messages_crdt", "messages_clock")

    private val localKeys = listOf("dueNorthReconcile:", "dueNorthStatement", ImportBatch.PREFERENCE_ID)

    private fun snapshot(c: Ctx): Map<String, Map<String, Map<String, Any?>>> =
        c.library.useDatabase(c.id) { s ->
            val tables = s.query("SELECT name FROM sqlite_master WHERE type IN ('table','view')").mapNotNull { it.str("name") }
            tables.filter { it !in skip && !it.startsWith("sqlite_") }.associateWith { t ->
                val rows = runCatching { s.query("SELECT rowid AS __rowid, * FROM \"$t\"") }.getOrElse { s.query("SELECT * FROM \"$t\"") }
                rows
                    .filter { r -> t != "preferences" || localKeys.none { r.str("id").orEmpty().startsWith(it) } }
                    .filter { r -> (r.values["tombstone"]?.toString() ?: "0") !in setOf("1", "1.0") }
                    .filter { r ->
                        // A row whose cells are all empty reads as no row.
                        r.values
                            .filterKeys { it != "id" && it != "__rowid" && it != "month" && it != "category" && it != "schedule_id" }
                            .values
                            .any { v ->
                                v != null && v.toString() !in setOf("0", "0.0", "")
                            }
                    }.associate { r ->
                        (r.values["id"] ?: r.values["__rowid"]).toString() to
                            r.values.filterKeys { it != "__rowid" }.mapValues { (_, v) -> norm(v) }
                    }
            }
        }!!

    private fun norm(v: Any?): Any? =
        when (v) {
            is Int -> v.toLong()
            is Double -> if (v == Math.floor(v)) v.toLong() else v
            else -> v
        }

    private fun diff(
        a: Map<String, Map<String, Map<String, Any?>>>,
        b: Map<String, Map<String, Map<String, Any?>>>,
    ): List<String> {
        val out = mutableListOf<String>()
        for (t in (a.keys + b.keys)) {
            val ra = a[t].orEmpty()
            val rb = b[t].orEmpty()
            for (r in ra.keys + rb.keys) {
                val x = ra[r]
                val y = rb[r]
                when {
                    x == null -> out += "$t/$r added ${y!!.filterValues { it != null }}"
                    y == null -> out += "$t/$r removed"
                    x != y -> {
                        val cols = (x.keys + y.keys).filter { x[it] != y[it] }
                        out += "$t/$r " + cols.joinToString { "$it: ${x[it]} -> ${y[it]}" }
                    }
                }
            }
        }
        return out
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 7)
    }
}
