package app.duenorth.budget

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountChoice
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.RegisterAccount
import app.duenorth.budget.core.RegisterCopy
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RegisterRow
import app.duenorth.budget.core.TransactionDraft
import app.duenorth.budget.core.TransferDraft
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun longPressStartsSelectingAndTapsThenPick() {
        val started = mutableListOf<String>()
        val toggled = mutableListOf<String>()
        val opened = mutableListOf<String>()
        var selection by mutableStateOf<Set<String>?>(null)
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    RegisterScreen(
                        page = sample().copy(rows = listOf(row("cafe", "Cafe"), row("bus", "Bus"))),
                        onGesture = {},
                        onOpen = { opened += it },
                        onAdd = {},
                        onTransfer = {},
                        onReconcile = {},
                        selection = selection,
                        onSelect = { started += it },
                        onToggleSelected = { toggled += it },
                    )
                }
            }
        }
        compose.onNodeWithTag("register-select-cafe", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("register-row-cafe").performTouchInput { longClick() }
        assertEquals(listOf("cafe"), started)
        selection = setOf("cafe")
        compose.onNodeWithTag("register-row-cafe").assertIsSelected()
        compose.onNodeWithTag("register-row-bus").assertIsNotSelected()
        compose.onNodeWithTag("register-selected-count").assertTextEquals("1 ${RegisterCopy.SELECTED}")
        compose.onNodeWithTag("register-row-bus").performClick()
        assertEquals(listOf("bus"), toggled)
        assertEquals(emptyList<String>(), opened)
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
    fun blankPayeeIsRefusedUnderThePayee() {
        val host = form(row = null)
        compose.runOnIdle { host.save() }
        compose.onNodeWithTag("transaction-error-payee", useUnmergedTree = true).assertTextEquals(RegisterCopy.ENTER_PAYEE)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h891dp")
    fun titleAreaAndLabelsWithNoBodyButtons() {
        form()
        compose.onNodeWithTag("transaction-account", useUnmergedTree = true).assertTextEquals("Checking")
        compose.onNodeWithText(TransactionFormCopy.TITLE).assertIsDisplayed()
        listOf("date", "payee", "amount", "category", "note").forEach {
            compose
                .onAllNodesWithText(it)
                .fetchSemanticsNodes()
                .isNotEmpty()
                .let(::assertTrue)
        }
        compose.onNodeWithText("save").assertDoesNotExist()
        compose.onNodeWithText("split").assertDoesNotExist()
        compose.onNodeWithText("delete").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h891dp")
    fun fewCategoriesUnfoldInPlace() {
        form()
        compose.onNodeWithTag("transaction-category-value", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("transaction-category-list").assertDoesNotExist()
        compose.onNodeWithTag("transaction-category-null").performClick()
        compose.onNodeWithText(RegisterCopy.NO_CATEGORY).assertIsDisplayed()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h891dp")
    fun manyCategoriesOpenTheListAndKeepOtherEdits() {
        form(page = sample().copy(categories = many()))
        compose.onNode(hasSetTextAction() and hasParent(hasTestTag("transaction-payee"))).performTextReplacement("Edited")
        compose.onNodeWithTag("transaction-category-value", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("transaction-category-list").assertIsDisplayed()
        compose.onNodeWithTag("transaction-category-c-6").performClick()
        compose.onNodeWithTag("transaction-category-list").assertDoesNotExist()
        compose.onNodeWithText("Category 6").assertIsDisplayed()
        compose.onNodeWithText("Edited").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h891dp")
    fun backClosesTheListWithoutPicking() {
        form(page = sample().copy(categories = many()))
        compose.onNodeWithTag("transaction-category-value", useUnmergedTree = true).performClick()
        back()
        compose.onNodeWithTag("transaction-category-list").assertDoesNotExist()
        compose.onNodeWithText("Groceries").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h891dp")
    fun hiddenCategoryListedOnlyWhenCurrent() {
        val categories =
            many() +
                listOf(
                    CategoryChoice("c-old", "Old", "Living", income = false, hidden = true),
                    CategoryChoice("c-gone", "Gone", "Living", income = false, hidden = true),
                )
        val row = sample().rows.first().copy(categoryId = "c-old")
        form(page = sample().copy(categories = categories), row = row)
        compose.onNodeWithText("Old ${TransactionFormCopy.HIDDEN}").assertIsDisplayed()
        compose.onNodeWithTag("transaction-category-value", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("transaction-category-c-old").assertExists()
        compose.onNodeWithTag("transaction-category-c-gone").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h891dp")
    fun transferHasNoCategoryBox() {
        val row = sample().rows.first().copy(transferId = "t", partnerOffBudget = false)
        form(row = row)
        compose.onNodeWithTag("transaction-category").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h891dp")
    fun dateShowsTheFormatAndPickerAppliesOnDone() {
        val host = form(row = sample().rows.last(), dateFormat = "dd.MM.yyyy")
        compose.onNodeWithTag("transaction-date-value", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("transaction-date-picker").assertIsDisplayed()
        compose.onNodeWithText(TransactionFormCopy.CHOOSE_DATE).assertIsDisplayed()
        compose.onNodeWithTag("date-column-month-8").performClick()
        compose.waitForIdle()
        assertEquals(listOf("picker-done", "picker-cancel"), host.bar?.map { it.id })
        compose.runOnIdle { host.bar!!.first { it.id == "picker-done" }.run() }
        compose.onNodeWithTag("transaction-date-picker").assertDoesNotExist()
        compose.onNodeWithText("Tue 01.09.2026").assertIsDisplayed()
        assertEquals(null, host.bar)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h891dp")
    fun cancelAndBackLeaveTheDate() {
        val host = form(row = sample().rows.last())
        compose.onNodeWithTag("transaction-date-value", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("date-column-month-8").performClick()
        compose.waitForIdle()
        compose.runOnIdle { host.bar!!.first { it.id == "picker-cancel" }.run() }
        compose.onNodeWithText("Thu 10/01/2026").assertIsDisplayed()
        compose.onNodeWithTag("transaction-date-value", useUnmergedTree = true).performClick()
        back()
        compose.onNodeWithTag("transaction-date-picker").assertDoesNotExist()
        compose.onNodeWithText("Thu 10/01/2026").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h891dp")
    fun incomeSavesAPositiveAmount() {
        var saved: TransactionDraft? = null
        val host = form(onSave = { saved = it })
        compose.onNodeWithTag("transaction-direction-income").performClick()
        compose.runOnIdle { host.save() }
        assertEquals(1_240L, saved?.amountMinor)
        assertEquals(20261007, saved?.date)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h891dp")
    fun amountAndToggleShareALine() {
        form()
        val amount = compose.onNodeWithTag("amount-field").fetchSemanticsNode().boundsInRoot
        val toggle = compose.onNodeWithTag("transaction-direction").fetchSemanticsNode().boundsInRoot
        assertEquals(amount.top, toggle.top, 1f)
        assertTrue(amount.width < 390 * compose.density.density / 2)
    }

    @Test
    fun registerFollowsTheFormatSettings() {
        compose.setContent {
            MetroTheme {
                CompositionLocalProvider(
                    LocalFormatPrefs provides FormatPrefs(dateFormat = "dd.MM.yyyy", numberFormat = FormatOptions.NUMBER_DOT_COMMA),
                ) {
                    Box(Modifier.width(390.dp).height(640.dp)) {
                        RegisterScreen(
                            page = sample(balance = -123_456),
                            onGesture = {},
                            onOpen = {},
                            onAdd = {},
                            onTransfer = {},
                            onReconcile = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("register-day-20261007", useUnmergedTree = true).assertTextEquals("oct 7")
        compose.onAllNodesWithText("-$12,40").onFirst().assertIsDisplayed()
        compose.onNodeWithText("-$1.234,56").assertIsDisplayed()
    }

    @Test
    fun transferReadsTheDateAndAmountInTheChosenFormat() {
        var saved: TransferDraft? = null
        val host = FormSaveHost()
        compose.setContent {
            MetroTheme {
                CompositionLocalProvider(
                    LocalFormSaveHost provides host,
                    LocalFormatPrefs provides FormatPrefs(dateFormat = "dd.MM.yyyy", numberFormat = FormatOptions.NUMBER_DOT_COMMA),
                ) {
                    Box(Modifier.width(390.dp).height(780.dp)) {
                        TransferForm(page = sample(), initialDate = "2026-10-07", error = null, onSave = { saved = it })
                    }
                }
            }
        }
        compose.onNodeWithText("07.10.2026").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("1.234,56")
        compose.runOnIdle { host.save() }
        assertEquals(123_456L, saved?.amountMinor)
        assertEquals(20261007, saved?.date)
    }

    @Test
    fun amountIsShownAndReadInTheChosenNumberFormat() {
        var saved: TransactionDraft? = null
        val host = form(numberFormat = FormatOptions.NUMBER_DOT_COMMA, onSave = { saved = it })
        compose.onNodeWithText("12,40").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasParent(hasTestTag("amount-field"))).performTextReplacement("1.234,56")
        compose.runOnIdle { host.save() }
        assertEquals(-123_456L, saved?.amountMinor)
    }

    @Test
    fun hideDecimalsKeepsCentsWhileEditing() {
        var saved: TransactionDraft? = null
        val host = form(hideFraction = true, onSave = { saved = it })
        compose.onNodeWithText("12.40").assertIsDisplayed()
        compose.runOnIdle { host.save() }
        assertEquals(-1_240L, saved?.amountMinor)
    }

    @Test
    fun noteLineBreaksReadAsSpacesInTheRegister() {
        val row = sample().rows.first().copy(note = "first line\n  second")
        assertEquals("Groceries · first line second", registerDetail(row))
    }

    private fun form(
        page: RegisterPage = sample(),
        row: RegisterRow? = sample().rows.first(),
        dateFormat: String = "MM/dd/yyyy",
        numberFormat: String = FormatOptions.NUMBER_COMMA_DOT,
        hideFraction: Boolean = false,
        onSave: (TransactionDraft) -> Unit = {},
    ): FormSaveHost {
        val host = FormSaveHost()
        compose.setContent {
            MetroTheme {
                CompositionLocalProvider(LocalFormSaveHost provides host) {
                    Box(Modifier.width(390.dp).height(780.dp)) {
                        TransactionForm(
                            page = page,
                            row = row,
                            initialDate = "2026-10-07",
                            error = null,
                            onSave = onSave,
                            dateFormat = dateFormat,
                            numberFormat = numberFormat,
                            hideFraction = hideFraction,
                        )
                    }
                }
            }
        }
        return host
    }

    @Suppress("UNCHECKED_CAST")
    private fun back() {
        val rule = compose as AndroidComposeTestRule<*, ComponentActivity>
        compose.waitForIdle()
        compose.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun many() =
        listOf(CategoryChoice("c-food", "Groceries", "Living", income = false)) +
            (1..7).map { CategoryChoice("c-$it", "Category $it", if (it < 4) "Living" else "Fun", income = false) }

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
