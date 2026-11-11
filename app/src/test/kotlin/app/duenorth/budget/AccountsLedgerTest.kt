package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.duenorth.budget.ParityTestSupport.USD
import app.duenorth.budget.ParityTestSupport.await
import app.duenorth.budget.ParityTestSupport.drain
import app.duenorth.budget.ParityTestSupport.household
import app.duenorth.budget.core.AccountRow
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.LedgerFilter
import app.duenorth.budget.core.LedgerStatus
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/**
 * The accounts view's "All accounts" total opens the ledger with every account's transactions, unfiltered,
 * and back returns to the accounts view.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class AccountsLedgerTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun theTotalOpensTheUnfilteredLedgerAndBackReturnsToAccounts() {
        val (model, _) = household("ledger-all-accounts")
        model.selectHomeSection(ScreenBar.HOME_ACCOUNTS)
        drain()
        model.openLedger()
        await { model.state.value.ledger != null }

        assertEquals(ShellRoute.Ledger(null), model.state.value.route)
        val ids =
            model.state.value.ledger!!
                .rows
                .map { it.row.id }
        assertTrue(ids.containsAll(listOf("t-food-l", "t-gas-l", "t-save", "t-cafe")))
        assertEquals(HomeSections.ACCOUNTS.uppercase(), SectionTitle.of(model.state.value))

        model.back()
        drain()
        assertEquals(ShellRoute.Home, model.state.value.route)
        assertEquals(ScreenBar.HOME_ACCOUNTS, model.state.value.homeSection)
    }

    @Test
    fun theTotalStartsFreshAndBackClosesTheSheetFirst() {
        val (model, _) = household("ledger-all-accounts-fresh")
        model.selectHomeSection(ScreenBar.HOME_ACCOUNTS)
        drain()
        model.openLedger()
        await { model.state.value.ledger != null }
        model.register.toggleFilter()
        model.setLedgerFilter(LedgerFilter(status = LedgerStatus.CLEARED))
        assertEquals(LedgerFilter(status = LedgerStatus.CLEARED), model.state.value.ledgerFilters[LEDGER_FILTER_KEY])

        // Back drops the sheet, then leaves the ledger for the accounts view.
        model.back()
        assertEquals(false, model.state.value.registerUi.filterOpen)
        assertEquals(ShellRoute.Ledger(null), model.state.value.route)
        model.back()
        drain()
        assertEquals(ShellRoute.Home, model.state.value.route)

        // Coming back through the total starts with no filters left over.
        model.openLedger()
        assertNull(model.state.value.ledgerFilters[LEDGER_FILTER_KEY])
    }

    @Test
    fun tappingTheTotalOpensTheLedgerExceptWhileReordering() {
        val rows =
            listOf(
                AccountRow("a1", "Checking", false, false, 120_000),
                AccountRow("a2", "Savings", false, false, 50_000),
            )
        val shell =
            MonthShell(
                "b",
                "Home",
                BudgetMode.ENVELOPE,
                USD,
                YearMonth.of(2026, 10),
                "to budget",
                0,
                groups = emptyList(),
                accounts = rows,
                inbox = emptyList(),
            )
        var opened = 0
        val reorder = mutableStateOf(false)
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(900.dp)) {
                    AccountsSection(
                        rows,
                        shell,
                        onListGesture = {},
                        reorder = reorder.value,
                        onAllAccounts = { opened++ },
                    )
                }
            }
        }
        compose.onNodeWithTag("accounts-total-open").performClick()
        assertEquals(1, opened)

        reorder.value = true
        compose.waitForIdle()
        compose.onNodeWithTag("accounts-total-open").assertDoesNotExist()
        compose.onNodeWithTag("accounts-total", useUnmergedTree = true).assertExists()
        assertEquals(1, opened)
    }
}
