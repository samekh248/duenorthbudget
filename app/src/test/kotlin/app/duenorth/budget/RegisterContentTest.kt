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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
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
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountChoice
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.CategoryTarget
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.RegisterAccount
import app.duenorth.budget.core.RegisterCopy
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RegisterRow
import app.duenorth.budget.core.RegisterSearch
import app.duenorth.budget.core.RegisterShow
import app.duenorth.budget.core.TransactionDraft
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
    fun searchNarrowsTheRowsAndCountsThem() {
        var search by mutableStateOf<RegisterSearch?>(RegisterSearch())
        val page =
            sample().copy(
                rows =
                    listOf(
                        row("cafe", "Cafe"),
                        row("bus", "Bus", date = 20261001).copy(amountMinor = -275, cleared = true, categoryLabel = "Transit"),
                        row("gift", "Gift shop", date = 20260930).copy(categoryId = null, categoryLabel = RegisterCopy.NO_CATEGORY),
                    ),
            )
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    RegisterScreen(
                        page = page,
                        onGesture = {},
                        onOpen = {},
                        onAdd = {},
                        onTransfer = {},
                        onReconcile = {},
                        search = search,
                        onSearch = { search = it },
                    )
                }
            }
        }
        val box = compose.onNode(hasSetTextAction() and hasParent(hasTestTag("register-search-box")))
        box.assertIsFocused()
        compose.onNodeWithTag("register-search-count").assertDoesNotExist()
        box.performTextInput("bus")
        compose.onNodeWithTag("register-row-bus").assertIsDisplayed()
        compose.onNodeWithTag("register-row-cafe").assertDoesNotExist()
        compose.onNodeWithTag("register-search-count").assertTextEquals(RegisterSearch.count(1, 3))
        box.performTextReplacement("2.75")
        compose.onNodeWithTag("register-row-bus").assertIsDisplayed()
        box.performTextReplacement("")
        compose.onNodeWithTag("register-show-not_cleared").performClick()
        assertEquals(RegisterShow.NOT_CLEARED, search?.show)
        compose.onNodeWithTag("register-row-bus").assertDoesNotExist()
        compose.onNodeWithTag("register-row-gift").assertIsDisplayed()
        compose.onNodeWithTag("register-show-no_category").performClick()
        compose.onNodeWithTag("register-row-gift").assertIsDisplayed()
        compose.onNodeWithTag("register-row-cafe").assertDoesNotExist()
        box.performTextInput("zzz")
        compose.onNodeWithTag("register-empty").assertTextEquals(RegisterSearch.NOTHING)
    }

    @Test
    fun offBudgetSearchHasNoCategoryChoice() {
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(640.dp)) {
                    RegisterScreen(
                        page = sample().copy(account = RegisterAccount("house", "House", offBudget = true, type = "other", closed = false)),
                        onGesture = {},
                        onOpen = {},
                        onAdd = {},
                        onTransfer = {},
                        onReconcile = {},
                        search = RegisterSearch(),
                    )
                }
            }
        }
        compose.onNodeWithTag("register-show-not_cleared").assertIsDisplayed()
        compose.onNodeWithTag("register-show-no_category").assertDoesNotExist()
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
        compose.onNodeWithText("transaction").assertDoesNotExist()
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
    fun listOpensOnTheCurrentNameSelectedAndFocused() {
        form(page = sample().copy(categories = many()))
        compose.onNodeWithTag("transaction-category-value", useUnmergedTree = true).performClick()
        val box = compose.onNode(hasSetTextAction() and hasParent(hasTestTag("category-filter")))
        box.assertIsFocused()
        box.assertTextEquals("Groceries", includeEditableText = true)
        box.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(0, "Groceries".length)))
        compose.onNodeWithTag("transaction-category-none").assertExists()
        compose.onNodeWithTag("transaction-category-c-6").assertExists()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h891dp")
    fun typingReplacesTheNameFiltersAndEnterPicksTheTopMatch() {
        form(page = sample().copy(categories = many()))
        compose.onNodeWithTag("transaction-category-value", useUnmergedTree = true).performClick()
        val box = compose.onNode(hasSetTextAction() and hasParent(hasTestTag("category-filter")))
        box.performTextInput("ory 5")
        box.assertTextEquals("ory 5", includeEditableText = true)
        compose.onNodeWithTag("transaction-category-c-5").assertExists()
        compose.onNodeWithTag("transaction-category-c-food").assertDoesNotExist()
        compose.onNodeWithTag("transaction-category-c-6").assertDoesNotExist()
        compose.onNodeWithTag("transaction-category-none").assertDoesNotExist()
        box.performImeAction()
        compose.onNodeWithTag("transaction-category-list").assertDoesNotExist()
        compose.onNodeWithText("Category 5").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h891dp")
    fun inboxPickerFiltersAndEnterPicksTheTopMatch() {
        val picked = mutableListOf<String?>()
        compose.setContent {
            MetroTheme {
                CategoryPickerScreen(
                    target =
                        CategoryTarget(
                            transactionId = "cafe",
                            accountId = "checking",
                            payee = "Cafe",
                            amountMinor = -1_240,
                            currency = Currencies.byCode("USD")!!,
                            categories = many(),
                            categoryId = "c-2",
                        ),
                    error = null,
                    onPick = { picked += it },
                )
            }
        }
        val box = compose.onNode(hasSetTextAction() and hasParent(hasTestTag("category-filter")))
        box.assertIsFocused()
        box.assertTextEquals("Category 2", includeEditableText = true)
        compose.onNodeWithText("Groceries").assertExists()
        box.performTextInput("gro")
        compose.onNodeWithText("Category 1").assertDoesNotExist()
        box.performImeAction()
        assertEquals(listOf<String?>("c-food"), picked)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h891dp")
    fun transferHasNoCategoryBox() {
        val row = sample().rows.first().copy(transferId = "t", partnerOffBudget = false, transferAccountId = "cash")
        form(row = row)
        compose.onNodeWithTag("transaction-category").assertDoesNotExist()
        compose.onNodeWithTag("transaction-transfer-label").assertTextEquals("transfer to Cash")
        compose.onNodeWithText("${TransactionFormCopy.ALSO_CHANGES} Cash").assertIsDisplayed()
    }

    // Option C (#76): an account typed in the payee box becomes a transfer tile, with the paired side shown.
    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h891dp")
    fun typingAnAccountOffersATransferAndSavesIt() {
        var saved: TransactionDraft? = null
        val host = form(row = null, onSave = { saved = it })
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("transaction-payee"))).performTextInput("ca")
        compose.onNodeWithTag("transaction-payee-transfer-cash").assertTextEquals("transfer to Cash").performClick()
        compose.onNodeWithTag("transaction-transfer-label").assertTextEquals("transfer to Cash")
        compose.onNodeWithTag("transaction-category").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasParent(hasTestTag("amount-field"))).performTextInput("5")
        compose.onNodeWithText("${TransactionFormCopy.ALSO_ADDS} Cash").assertIsDisplayed()
        compose.onNodeWithText("transfer from Checking").assertIsDisplayed()
        compose.onNodeWithTag("transaction-transfer-preview-amount").assertTextEquals("$5.00")
        compose.onNodeWithTag("transaction-direction-income").performClick()
        compose.onNodeWithTag("transaction-transfer-label").assertTextEquals("transfer from Cash")
        compose.runOnIdle { host.save() }
        assertEquals("cash", saved?.transferAccountId)
        assertEquals(500L, saved?.amountMinor)
        assertEquals(null, saved?.categoryId)
    }

    @Test
    fun clearingTheTransferGivesThePayeeBoxBack() {
        var saved: TransactionDraft? = null
        val row = sample().rows.first().copy(transferId = "t", partnerOffBudget = false, transferAccountId = "cash")
        val host = form(row = row, onSave = { saved = it })
        compose.onNodeWithTag("transaction-transfer-clear").performClick()
        compose.onNodeWithTag("transaction-transfer").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("transaction-payee"))).performTextInput("Cafe")
        compose.runOnIdle { host.save() }
        assertEquals(null, saved?.transferAccountId)
        assertEquals("Cafe", saved?.payee)
    }

    @Test
    fun anOffBudgetTransferKeepsItsCategory() {
        val page =
            sample().copy(
                accounts = listOf(AccountChoice("broker", "Brokerage", offBudget = true, type = "investment", closed = false)),
            )
        val row = page.rows.first().copy(transferId = "t", partnerOffBudget = true, transferAccountId = "broker")
        form(page = page, row = row)
        compose.onNodeWithTag("transaction-category").assertIsDisplayed()
        compose.onNodeWithTag("transaction-transfer-label").assertTextEquals("transfer to Brokerage")
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
