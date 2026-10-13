package app.duenorth.budget.core.parity

import app.duenorth.budget.core.AccountWriteResult
import app.duenorth.budget.core.AccountsBook
import app.duenorth.budget.core.SqlSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** Spec 010 T028: account actions against desktop Actual 26.8.1 (`parity/accounts/`). */
class AccountsParityTest {
    @Test
    fun createOnBudgetWithBalance() =
        scenario("create-on-budget-with-balance", newIds = { listOf(it.id("accounts", "Wallet")) + newRows(it, "Wallet") }) {
            it.create("Wallet", offBudget = false, balanceMinor = 50_000L)
        }

    @Test
    fun createOffBudgetWithBalance() =
        scenario("create-off-budget-with-balance", newIds = { listOf(it.id("accounts", "House")) + newRows(it, "House") }) {
            it.create("House", offBudget = true, balanceMinor = 25_000_000L)
        }

    @Test
    fun createWithNoBalance() =
        scenario("create-zero", newIds = { listOf(it.id("accounts", "Empty")) + newRows(it, "Empty") }) {
            it.create("Empty", offBudget = false, balanceMinor = 0L)
        }

    @Test
    fun renameAndNote() =
        scenario("rename-and-note") { book ->
            val savings = base.id("accounts", "Savings")
            book.rename(savings, "Rainy Day")
            book.setNote(savings, "Six months of expenses")
        }

    @Test
    fun reorderPutsTheAccountBeforeItsTarget() =
        scenario("reorder") { it.move(base.id("accounts", "Visa"), base.id("accounts", "Checking")) }

    @Test
    fun closeAtZeroBalance() = scenario("close-zero-balance") { it.close(base.id("accounts", "Zeroed"), null, null) }

    @Test
    fun closeWithNoTransactionsDeletesTheAccount() =
        scenario("close-no-transactions") { it.close(base.id("accounts", "Unused"), null, null) }

    @Test
    fun closeOnBudgetIntoOnBudget() =
        scenario("close-on-to-on", newIds = { closeIds(it) }) {
            it.close(base.id("accounts", "Savings"), base.id("accounts", "Checking"), null)
        }

    @Test
    fun closeOnBudgetIntoOffBudgetCarriesTheCategory() =
        scenario("close-on-to-off-with-category", newIds = { closeIds(it) }) {
            it.close(base.id("accounts", "Savings"), base.id("accounts", "Brokerage"), base.id("categories", "General"))
        }

    @Test
    fun forceCloseDeletesTheAccountAndUnlinksPartners() =
        scenario("force-close") { it.close(base.id("accounts", "Visa"), null, null, forced = true, confirmed = true) }

    @Test
    fun closeThenReopen() =
        scenario("close-then-reopen", newIds = { closeIds(it) }) { book ->
            val savings = base.id("accounts", "Savings")
            book.close(savings, base.id("accounts", "Checking"), null)
            book.reopen(savings)
        }

    /** Names as they are before any action; a scenario's own ids reflect its result (renamed, deleted). */
    private val base = ParityFixture.load("accounts", "reorder")

    private lateinit var fixture: ParityFixture

    /** The fixture's ids for the rows Actual created, in the order [AccountsBook] asks for them. */
    private fun newRows(
        fixture: ParityFixture,
        account: String,
    ): List<String> {
        val accountId = fixture.id("accounts", account)
        val payee = fixture.expectedRows("payees").first { it["transfer_acct"] == accountId }["id"] as String
        val txn = fixture.expectedRows("transactions").firstOrNull { it["acct"] == accountId }?.get("id") as String?
        return listOfNotNull(payee, txn)
    }

    /** The close transfer: the closing account's side, then the destination's. */
    private fun closeIds(fixture: ParityFixture): List<String> {
        val savings = fixture.id("accounts", "Savings")
        val rows = fixture.expectedRows("transactions").filter { it["notes"] == "Closing account" }
        val from = rows.first { it["acct"] == savings }["id"] as String
        return listOf(from, rows.first { it["id"] != from }["id"] as String)
    }

    private fun scenario(
        name: String,
        newIds: (ParityFixture) -> List<String> = { emptyList() },
        action: (AccountsBook) -> AccountWriteResult,
    ) {
        fixture = ParityFixture.load("accounts", name)
        val queue = ArrayDeque(newIds(fixture))
        fixture.open().use { opened ->
            opened.db { session ->
                val result = action(AccountsBook(session, fixture.today) { queue.removeFirst() })
                assertTrue("$name: $result", result is AccountWriteResult.Saved)
                assertTrue("$name: unused ids $queue", queue.isEmpty())
                fixture.assertTable(session, "accounts")
                fixture.assertTable(session, "payees")
                fixture.assertTable(session, "transactions", ignore = setOf("sort_order"))
                fixture.assertTable(session, "notes")
                assertBalances(session)
            }
            val shell = opened.library.readShell(opened.budgetId, YearMonth.of(2026, 10))!!
            assertEquals("$name to budget", fixture.long("2026-10", "toBudget"), shell.headerMinor)
        }
    }

    private fun assertBalances(session: SqlSession) {
        val balances = fixture.value("balances") as kotlinx.serialization.json.JsonObject
        for ((account, _) in balances) {
            val id = fixture.id("accounts", account)
            val got =
                session
                    .query(
                        """
                        SELECT COALESCE(SUM(amount), 0) AS b FROM transactions
                        WHERE acct = ? AND IFNULL(tombstone, 0) = 0 AND IFNULL(isParent, 0) = 0
                        """.trimIndent(),
                        listOf(id),
                    ).first()
                    .long("b")
            assertEquals("${fixture.scenario} balance of $account", fixture.long("balances", account), got)
        }
    }
}
