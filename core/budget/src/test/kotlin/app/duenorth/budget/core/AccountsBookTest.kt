package app.duenorth.budget.core

import app.duenorth.budget.core.parity.ParityFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 010 T029: account validation; nothing is written when a close or create is refused. */
class AccountsBookTest {
    private val fixture = ParityFixture.load("accounts", "close-zero-balance")

    @Test
    fun blankNameIsRefused() =
        withBook { book, session ->
            assertEquals(AccountWriteResult.Rejected(AccountsCopy.ENTER_NAME), book.create("  ", offBudget = false, balanceMinor = 100L))
            assertEquals(
                AccountWriteResult.Rejected(AccountsCopy.ENTER_NAME),
                book.rename(fixture.id("accounts", "Savings"), ""),
            )
            assertEquals(0, messages(session))
        }

    @Test
    fun closeWithABalanceNeedsADestination() =
        withBook { book, session ->
            val savings = fixture.id("accounts", "Savings")
            assertEquals(AccountWriteResult.Rejected(AccountsCopy.CHOOSE_DESTINATION), book.close(savings, null, null))
            assertEquals(AccountWriteResult.Rejected(AccountsCopy.SAME_ACCOUNT), book.close(savings, savings, null))
            assertFalse(book.detail(savings)!!.closed)
            assertEquals(0, messages(session))
        }

    @Test
    fun closeOnBudgetIntoOffBudgetNeedsACategory() =
        withBook { book, session ->
            val result = book.close(fixture.id("accounts", "Savings"), fixture.id("accounts", "Brokerage"), null)
            assertEquals(AccountWriteResult.Rejected(AccountsCopy.CHOOSE_CATEGORY), result)
            assertEquals(0, messages(session))
        }

    @Test
    fun forceCloseNeedsItsOwnConfirm() =
        withBook { book, session ->
            val visa = fixture.id("accounts", "Visa")
            assertEquals(AccountWriteResult.NeedsConfirm(AccountsCopy.FORCE_CONFIRM), book.close(visa, null, null, forced = true))
            assertEquals(0, messages(session))
            assertTrue(book.close(visa, null, null, forced = true, confirmed = true) is AccountWriteResult.Saved)
            assertEquals(null, book.detail(visa))
        }

    @Test
    fun closedAccountsLeaveTheListUntilShown() =
        withBook { book, _ ->
            val zeroed = fixture.id("accounts", "Zeroed")
            book.close(zeroed, null, null)
            assertFalse(book.list(includeClosed = false).any { it.id == zeroed })
            assertTrue(book.list(includeClosed = true).single { it.id == zeroed }.closed)
            book.reopen(zeroed)
            assertTrue(book.list(includeClosed = false).any { it.id == zeroed })
        }

    @Test
    fun shellHidesClosedAccountsUnlessTheSettingIsOn() {
        fixture.open().use { opened ->
            val zeroed = fixture.id("accounts", "Zeroed")
            opened.db { AccountsBook(it, fixture.today).close(zeroed, null, null) }
            assertFalse(
                opened.library
                    .readShell(opened.budgetId)!!
                    .accounts
                    .any { it.id == zeroed },
            )
            opened.library.updateSettings { it.copy(showClosedAccounts = true) }
            assertTrue(
                opened.library
                    .readShell(opened.budgetId)!!
                    .accounts
                    .single { it.id == zeroed }
                    .closed,
            )
        }
    }

    @Test
    fun createAndCloseAreOneUndoStepEach() {
        fixture.open().use { opened ->
            val library = opened.library
            val created = library.createAccount(opened.budgetId, "Wallet", offBudget = false, balanceMinor = 1_500L)
            val id = (created as AccountWriteResult.Saved).accountId
            assertEquals("undo ${AccountsCopy.ADD}", "undo " + library.undoLabel(opened.budgetId))
            library.undo(opened.budgetId)
            opened.db { session ->
                assertEquals(null, AccountsBook(session, fixture.today).detail(id))
                assertEquals(
                    0,
                    session.query("SELECT id FROM transactions WHERE acct = ? AND tombstone = 0", listOf(id)).size,
                )
            }
        }
    }

    private fun messages(session: SqlSession): Int = session.query("SELECT timestamp FROM messages").size

    private fun withBook(block: (AccountsBook, SqlSession) -> Unit) {
        fixture.open().use { opened -> opened.db { session -> block(AccountsBook(session, fixture.today), session) } }
    }
}
