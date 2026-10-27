package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.duenorth.budget.ParityTestSupport.USD
import app.duenorth.budget.ParityTestSupport.await
import app.duenorth.budget.ParityTestSupport.household
import app.duenorth.budget.core.AccountDetail
import app.duenorth.budget.core.AccountRow
import app.duenorth.budget.core.AccountsCopy
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/** Spec 010 T036: account screens, their bar entries, and the close gate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class AccountContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    private val checking = AccountDetail("a1", "Checking", false, false, 120_000, 4, "", false)
    private val savings = AccountDetail("a2", "Savings", false, false, 50_000, 2, "", false)
    private val house = AccountDetail("a3", "House", true, false, 25_000_000, 1, "", false)
    private val old = AccountDetail("a4", "Old card", false, true, 0, 3, "", false)

    @Test
    fun barEntriesForAccountsAndRegister() {
        val home = ScreenBar.specsFor(ShellRoute.Home, true, BudgetMode.ENVELOPE).map { it.id }
        assertFalse("add-account" in home)
        val accounts = ScreenBar.specsFor(ShellRoute.Home, true, BudgetMode.ENVELOPE, homeSection = ScreenBar.HOME_ACCOUNTS)
        assertEquals(listOf("add", "schedules", "add-account", "reorder", "show-closed", "settings"), accounts.map { it.id })
        assertTrue(accounts.filter { it.id in listOf("add-account", "reorder", "show-closed") }.none { it.primary })
        assertEquals(
            AccountScreenCopy.HIDE_CLOSED,
            ScreenBar
                .specsFor(ShellRoute.Home, true, BudgetMode.ENVELOPE, showClosed = true, homeSection = ScreenBar.HOME_ACCOUNTS)
                .single { it.id == "show-closed" }
                .label,
        )
        val reordering =
            ScreenBar.specsFor(ShellRoute.Home, true, BudgetMode.ENVELOPE, homeSection = ScreenBar.HOME_ACCOUNTS, accountsReorder = true)
        assertEquals(listOf("reorder"), reordering.map { it.id })
        assertEquals(AccountScreenCopy.DONE, reordering.single().label)
        val register = ScreenBar.specsFor(ShellRoute.Register("a1"), true, BudgetMode.ENVELOPE).map { it.id }
        assertTrue("edit-account" in register && "close" in register && "reopen" !in register)
        val closedRegister = ScreenBar.specsFor(ShellRoute.Register("a1"), true, BudgetMode.ENVELOPE, accountClosed = true).map { it.id }
        assertTrue("reopen" in closedRegister && "close" !in closedRegister)
        val close = ScreenBar.specsFor(ShellRoute.CloseAccount("a1"), true, BudgetMode.ENVELOPE)
        assertEquals(listOf("save", "cancel"), close.map { it.id })
        assertTrue(close.all { it.primary })
        assertEquals(AccountScreenCopy.CLOSE, close.first().label)
        assertTrue(ScreenBar.specsFor(ShellRoute.CloseAccount("a1"), true, BudgetMode.ENVELOPE, accountForceConfirm = true).isEmpty())
    }

    @Test
    fun reorderShowsHandlesAndADragMovesTheAccount() {
        val rows =
            listOf(
                AccountRow("a1", "Checking", false, false, 120_000),
                AccountRow("a2", "Savings", false, false, 50_000),
                AccountRow("a3", "Visa", false, false, -2_500),
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
        var opened: String? = null
        var moved: Pair<String, String?>? = null
        val reorder = mutableStateOf(false)
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(900.dp)) {
                    AccountsSection(
                        rows,
                        shell,
                        onListGesture = {},
                        onAccount = { opened = it },
                        reorder = reorder.value,
                        onMove = { id, target -> moved = id to target },
                    )
                }
            }
        }
        compose.onNodeWithTag("account-handle-a1", useUnmergedTree = true).assertDoesNotExist()
        reorder.value = true
        compose.waitForIdle()
        compose.onNodeWithTag("accounts-reorder-hint", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("account-a2").performClick()
        assertNull(opened)
        compose.onNodeWithTag("account-handle-a1", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, 30f))
            moveBy(Offset(0f, height * 2.4f))
            up()
        }
        compose.waitForIdle()
        assertEquals("a1" to null, moved)
        compose.onNodeWithTag("account-handle-a3", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, -30f))
            moveBy(Offset(0f, -height * 2.4f))
            up()
        }
        compose.waitForIdle()
        assertEquals("a3" to "a1", moved)
    }

    @Test
    fun closeAsksForDestinationAndCategoryWhenMoneyLeavesTheBudget() {
        var closed: Pair<String?, String?>? = null
        var forced = 0
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(900.dp)) {
                    CloseAccountScreen(
                        account = savings,
                        others = listOf(checking, savings, house),
                        categories = listOf(CategoryChoice("food", "Food", "Living", false), CategoryChoice("pay", "Pay", "Income", true)),
                        currency = USD,
                        error = null,
                        onClose = { d, c -> closed = d to c },
                        onForceClose = { forced++ },
                    )
                }
            }
        }
        compose.onNodeWithTag("account-close-destination-a2").assertDoesNotExist()
        compose.onNodeWithTag("account-close-category-food").assertDoesNotExist()
        compose.onNodeWithTag("account-close-destination-a3").performClick()
        compose.onNodeWithTag("account-close-category-pay").assertDoesNotExist()
        compose.onNodeWithTag("account-close-category-food").performClick()
        compose.onNodeWithTag("account-close-confirm").performClick()
        assertEquals("a3" to "food", closed)
        compose.onNodeWithTag("account-force-close").performClick()
        assertEquals(1, forced)
    }

    @Test
    fun addCloseAndReopenThroughTheModel() {
        val (model, library) = household("accounts")
        val budgetId = model.state.value.settings.openBudgetId!!
        model.reloadAccounts()
        await {
            model.state.value.accountsList
                .isNotEmpty()
        }
        val before = model.state.value.accountsList.size
        model.openAddAccount()
        assertEquals(ShellRoute.AddAccount, model.state.value.route)
        model.submitAddAccount("", offBudget = false, balanceText = "")
        await { model.state.value.editError != null }
        assertEquals(AccountsCopy.ENTER_NAME, model.state.value.editError)
        model.submitAddAccount("Wallet", offBudget = false, balanceText = "25")
        await { model.state.value.route == ShellRoute.Home && model.state.value.accountsList.size == before + 1 }
        val wallet =
            model.state.value.accountsList
                .single { it.name == "Wallet" }
        assertEquals(2_500L, wallet.balanceMinor)
        assertEquals(AccountsCopy.ADD, model.state.value.undoLabel)

        model.openAccount(wallet.id)
        await { model.state.value.route == ShellRoute.Register(wallet.id) }
        model.openCloseAccount()
        await {
            model.state.value.accountDetail
                ?.id == wallet.id
        }
        model.submitCloseAccount(destinationId = null, categoryId = null)
        await { model.state.value.editError != null }
        assertEquals(AccountsCopy.CHOOSE_DESTINATION, model.state.value.editError)
        val other =
            model.state.value.accountsList
                .first { it.id != wallet.id && !it.offBudget && !it.closed }
        model.submitCloseAccount(destinationId = other.id, categoryId = null)
        await { library.readAccount(budgetId, wallet.id)?.closed == true }
        await {
            model.state.value.route !is ShellRoute.CloseAccount &&
                model.state.value.shell!!
                    .accounts
                    .none { it.id == wallet.id }
        }

        model.toggleShowClosedAccounts()
        await {
            model.state.value.settings.showClosedAccounts &&
                model.state.value.accountsList
                    .any { it.id == wallet.id && it.closed }
        }
        model.reopenAccount(wallet.id)
        await { library.readAccount(budgetId, wallet.id)?.closed == false }
    }

    @Test
    fun forceCloseWritesNothingUntilConfirmed() {
        val (model, library) = household("force-close")
        val budgetId = model.state.value.settings.openBudgetId!!
        val account = library.readAccounts(budgetId, includeClosed = false).first { it.transactionCount > 0 && it.balanceMinor != 0L }
        model.openAccount(account.id)
        await { model.state.value.route == ShellRoute.Register(account.id) }
        model.openCloseAccount()
        await {
            model.state.value.accountDetail
                ?.id == account.id
        }
        model.submitCloseAccount(null, null, forced = true)
        await { model.state.value.accountForceConfirm != null }
        assertEquals(account.id, library.readAccount(budgetId, account.id)?.id)
        model.keepAccount()
        assertNull(model.state.value.accountForceConfirm)
        model.submitCloseAccount(null, null, forced = true)
        await { model.state.value.accountForceConfirm != null }
        model.confirmForceClose()
        await { library.readAccount(budgetId, account.id) == null }
    }
}
