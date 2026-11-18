package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountChoice
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.RegisterAccount
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.SavedSplit
import app.duenorth.budget.core.SavedSplitGuess
import app.duenorth.budget.core.SavedSplitLine
import app.duenorth.budget.core.SplitPart
import app.duenorth.budget.core.TransactionDraft
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** Saved splits: the inbox offers the one a transaction looks like, and the form uses and saves them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class SavedSplitContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun theFeaturedPanelOffersTheSavedSplitAheadOfACategory() {
        val split = mutableListOf<String>()
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    InboxSection(splitShell(), onListGesture = {}, actions = InboxActions(onSplit = { split += it }))
                }
            }
        }
        compose.onNodeWithText(SPLIT_NAME, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("inbox-featured-split-reasons", useUnmergedTree = true).assertTextEquals("names “Costco”")
        compose.onNodeWithText("Groceries", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("-\$50.47", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("inbox-featured-accept").assertDoesNotExist()
        compose.onNodeWithTag("inbox-featured-split").performClick()
        assertEquals(listOf("costco"), split)
    }

    @Test
    fun theMenuQueueAndConfirmationNameTheSplit() {
        val split = mutableListOf<String>()
        val items = inboxMenuItems(splitInbox.first(), {}, {}, InboxActions(onSplit = { split += it }))
        assertEquals("split as costco run", items.first().first)
        items.first().second()
        assertEquals(listOf("costco"), split)
        assertEquals("split as costco run? · Checking", inboxDetail(splitInbox.first()))
        assertEquals("split as costco run", filedLine(InboxEntry(splitInbox.first(), SPLIT_NAME, split = true)))
    }

    @Test
    fun aSavedSplitFillsTheLinesForTheAmountTyped() {
        var saved: TransactionDraft? = null
        val host = form(onSave = { saved = it })
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("transaction-payee"))).performTextInput("Costco")
        compose.onNode(hasSetTextAction() and hasParent(hasTestTag("amount-field"))).performTextInput("84.12")
        compose.onNodeWithTag("transaction-split-use").performScrollTo().performClick()
        compose.onNodeWithTag("saved-split-pick-costco").performClick()
        compose.onNodeWithTag("transaction-split-0-category-value").assertTextEquals("Groceries")
        compose.onNodeWithTag("transaction-split-1-category-value").assertTextEquals("Household")
        compose.runOnIdle { host.save() }
        assertEquals(listOf(SplitPart("c-food", -5_047), SplitPart("c-house", -3_365)), saved?.parts)
    }

    @Test
    fun aSplitThatAddsUpCanBeSavedAsShares() {
        val kept = mutableListOf<SavedSplit>()
        val host = form(onSaveSplit = { kept += it }, savedSplits = emptyList())
        compose.onNodeWithTag("transaction-split-use").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("transaction-payee"))).performTextInput("Costco")
        compose.onNode(hasSetTextAction() and hasParent(hasTestTag("amount-field"))).performTextInput("200")
        compose.onNodeWithTag("transaction-split-start").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("transaction-split-0-amount"))).performTextInput("120")
        compose.onNodeWithTag("transaction-split-save").performScrollTo().performClick()
        compose.onNodeWithTag("saved-split-save-page").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("saved-split-name"))).performTextReplacement(SPLIT_NAME)
        compose.onNodeWithText("shares").performClick()
        compose.runOnIdle { host.bar!!.first { it.id == "saved-split-save" }.run() }
        val split = kept.single()
        assertEquals(SPLIT_NAME, split.name)
        assertEquals(listOf(6_000, null), split.lines.map { it.shareBp })
        assertEquals(listOf("Costco"), split.payees)
        assertEquals(-20_000L, split.totalMinor)
    }

    private fun form(
        onSave: (TransactionDraft) -> Unit = {},
        onSaveSplit: (SavedSplit) -> Unit = {},
        savedSplits: List<SavedSplit> = listOf(costco),
    ): FormSaveHost {
        val host = FormSaveHost()
        compose.setContent {
            MetroTheme {
                CompositionLocalProvider(LocalFormSaveHost provides host) {
                    Box(Modifier.width(390.dp).height(780.dp)) {
                        TransactionForm(
                            page = splitPage(),
                            row = null,
                            initialDate = "2026-11-07",
                            error = null,
                            onSave = onSave,
                            savedSplits = savedSplits,
                            onSaveSplit = onSaveSplit,
                        )
                    }
                }
            }
        }
        return host
    }
}

/** The inbox with a saved split featured, the settings page that lists them, and the page that saves one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class SavedSplitScreenshotTest {
    @Test fun inboxLight() = inbox("inbox_saved_split_light", dark = false)

    @Test fun inboxDark() = inbox("inbox_saved_split_dark", dark = true)

    @Test fun listLight() = list("saved_splits_light", dark = false)

    @Test fun listDark() = list("saved_splits_dark", dark = true)

    @Test fun saveLight() = save("saved_split_save_light", dark = false)

    @Test fun saveDark() = save("saved_split_save_dark", dark = true)

    private fun inbox(
        name: String,
        dark: Boolean,
    ) = captureRoboImage("src/test/snapshots/$name.png") {
        MetroTheme(darkTheme = dark) {
            Column(Modifier.width(390.dp).height(780.dp)) {
                HomePivot(
                    shell = splitShell(),
                    loading = false,
                    openMonth = YearMonth.of(2026, 11),
                    monthWindow = MonthWindow.around(YearMonth.of(2026, 11)),
                    initialSection = HomeSections.headers.indexOf(HomeSections.ASSISTANT),
                    onGesture = {},
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    private fun list(
        name: String,
        dark: Boolean,
    ) = captureRoboImage("src/test/snapshots/$name.png") {
        MetroTheme(darkTheme = dark) {
            val names = mapOf("c-food" to "Groceries", "c-house" to "Household", "c-rent" to "Rent", "c-parking" to "Parking")
            SavedSplitsScreen(
                splits = listOf(costco, rent),
                summary = { savedSplitSummary(it, names, Currencies.byCode("USD")!!, FormatOptions()) },
                onDelete = {},
                modifier = Modifier.width(390.dp).height(780.dp),
            )
        }
    }

    private fun save(
        name: String,
        dark: Boolean,
    ) = captureRoboImage("src/test/snapshots/$name.png") {
        MetroTheme(darkTheme = dark) {
            Box(Modifier.width(390.dp).height(780.dp)) {
                SaveSplitPage(initialName = "Costco", onSave = { _, _ -> }, onCancel = {})
            }
        }
    }
}

private const val SPLIT_NAME = "Costco run"

private val costco =
    SavedSplit(
        id = "costco",
        name = SPLIT_NAME,
        lines = listOf(SavedSplitLine("c-food", shareBp = 6_000), SavedSplitLine("c-house")),
        payees = listOf("Costco"),
        totalMinor = -20_000,
    )

private val rent =
    SavedSplit(
        id = "rent",
        name = "Rent + parking",
        lines = listOf(SavedSplitLine("c-rent", amountMinor = 180_000), SavedSplitLine("c-parking")),
        payees = listOf("Oak Street Apartments"),
        totalMinor = -195_000,
    )

private val splitInbox =
    listOf(
        InboxRow(
            "costco",
            "COSTCO WHSE #0482",
            -8_412,
            20261106,
            "checking",
            "Checking",
            suggestionId = "c-food",
            suggestionName = "Groceries",
            split =
                SavedSplitGuess(
                    "costco",
                    SPLIT_NAME,
                    listOf(SplitPart("c-food", -5_047), SplitPart("c-house", -3_365)),
                    listOf("names “Costco”"),
                    listOf("Groceries", "Household"),
                ),
        ),
        InboxRow("tj", "TRADER JOE'S #552", -6_418, 20261105, "checking", "Checking", "", "c-food", "Groceries"),
    )

private fun splitShell() =
    MonthShell(
        budgetId = "b",
        budgetName = "Home",
        mode = BudgetMode.ENVELOPE,
        currency = Currencies.byCode("USD")!!,
        month = YearMonth.of(2026, 11),
        headerLabel = "to budget",
        headerMinor = 0,
        groups =
            listOf(
                GroupRow(
                    "g",
                    "Living",
                    0,
                    listOf(CategoryRow("c-food", "Groceries", 0, 0, 0, false), CategoryRow("c-house", "Household", 0, 0, 0, false)),
                ),
            ),
        accounts = emptyList(),
        inbox = splitInbox,
        savedSplits = listOf(costco, rent),
    )

private fun splitPage() =
    RegisterPage(
        account = RegisterAccount("checking", "Checking", offBudget = false, type = "checking", closed = false),
        balanceMinor = 0,
        currency = Currencies.byCode("USD")!!,
        rows = emptyList(),
        categories =
            listOf(
                CategoryChoice("c-food", "Groceries", "Living", income = false),
                CategoryChoice("c-house", "Household", "Living", income = false),
            ),
        accounts = listOf(AccountChoice("checking", "Checking", offBudget = false, type = "checking", closed = false)),
    )
