package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountChoice
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.RegisterAccount
import app.duenorth.budget.core.RegisterCopy
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RegisterRow
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RegisterContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun newestRowBalanceAndOwedCredit() {
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    RegisterScreen(
                        page = sample(balance = -4_000, type = "credit"),
                        onGesture = {},
                        onOpen = {},
                        onAdd = {},
                        onTransfer = {},
                        onReconcile = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("register-row-cafe").assertIsDisplayed()
        compose.onNodeWithText("Cafe").assertIsDisplayed()
        compose.onNodeWithTag("register-detail-cafe", useUnmergedTree = true).assertTextEquals("Groceries")
        compose.onNodeWithTag("register-day-20261007", useUnmergedTree = true).assertTextEquals("oct 7")
        compose.onNodeWithTag("register-balance").assertIsDisplayed()
        compose.onNodeWithText("balance ${RegisterCopy.OWED}").assertIsDisplayed()
    }

    @Test
    fun fiveHundredRowsStayLazy() {
        val rows = (0 until 500).map { row("row-$it", "payee $it") }
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(320.dp)) {
                    RegisterScreen(
                        page = sample().copy(rows = rows),
                        onGesture = {},
                        onOpen = {},
                        onAdd = {},
                        onTransfer = {},
                        onReconcile = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("register-row-row-0").assertIsDisplayed()
        compose.onNodeWithTag("register-row-row-499").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h891dp")
    fun blankPayeeIsRefusedOnTheForm() {
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(700.dp)) {
                    TransactionForm(
                        page = sample(),
                        row = null,
                        initialDate = "2026-10-07",
                        error = null,
                        onSave = {},
                        onDelete = {},
                        onSplit = {},
                    )
                }
            }
        }
        compose.onNodeWithText("save").performClick()
        compose.onNodeWithText(RegisterCopy.ENTER_PAYEE).assertIsDisplayed()
    }

    private fun sample(
        balance: Long = -1_240,
        type: String = "checking",
    ) = RegisterPage(
        account = RegisterAccount("checking", "Checking", offBudget = false, type = type, closed = false),
        balanceMinor = balance,
        currency = Currencies.byCode("USD")!!,
        rows = listOf(row("cafe", "Cafe"), row("bus", "Bus", date = 20261001)),
        categories = listOf(CategoryChoice("c-food", "Groceries", "Living", income = false)),
        accounts = listOf(AccountChoice("cash", "Cash", offBudget = false, type = "checking", closed = false)),
    )

    private fun row(
        id: String,
        payee: String,
        date: Int = 20261007,
    ) = RegisterRow(
        id = id,
        date = date,
        payee = payee,
        categoryId = "c-food",
        categoryLabel = "Groceries",
        amountMinor = -1_240,
        note = "",
        reconciled = false,
        transferId = null,
        partnerOffBudget = null,
        partnerCategoryId = null,
        parent = false,
        parts = emptyList(),
    )
}
