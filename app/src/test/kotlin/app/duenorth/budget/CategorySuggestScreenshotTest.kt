package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.CategorySuggestions
import app.duenorth.budget.core.CategoryTarget
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.RegisterAccount
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RegisterRow
import app.duenorth.budget.design.theme.MetroStyle
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class CategorySuggestScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun inboxLight() = snapInbox("category_suggested_inbox_light", dark = false)

    @Test
    fun inboxDark() = snapInbox("category_suggested_inbox_dark", dark = true)

    @Test
    fun inboxMobile10Light() = snapInbox("category_suggested_inbox_w10m_light", dark = false, style = MetroStyle.Mobile10)

    @Test
    fun inboxMobile10Dark() = snapInbox("category_suggested_inbox_w10m_dark", dark = true, style = MetroStyle.Mobile10)

    @Test
    fun formLight() = snapForm("category_suggested_form_light", dark = false)

    @Test
    fun formDark() = snapForm("category_suggested_form_dark", dark = true)

    /** The inbox's picker for a coffee shop: what it is usually filed under leads, then every category. */
    private fun snapInbox(
        name: String,
        dark: Boolean,
        style: MetroStyle = MetroStyle.Default,
    ) {
        compose.setContent {
            MetroTheme(darkTheme = dark, style = style) {
                SectionTitled("INBOX", Modifier.width(390.dp).height(780.dp)) {
                    CategoryPickerScreen(
                        target =
                            CategoryTarget(
                                transactionId = "t",
                                accountId = "checking",
                                payee = "Corner Coffee",
                                amountMinor = -475,
                                currency = Currencies.byCode("USD")!!,
                                categories = categories,
                                suggested = listOf("c-coffee", "c-dining"),
                            ),
                        error = null,
                        onPick = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    /** The transaction form's full-screen category list, opened on a coffee shop's transaction. */
    private fun snapForm(
        name: String,
        dark: Boolean,
    ) {
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    TransactionForm(
                        page = page(),
                        row = row(),
                        initialDate = "2026-10-07",
                        error = null,
                        onSave = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("transaction-category-value", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private val categories =
        listOf(
            CategoryChoice("c-food", "Groceries", "Living", income = false),
            CategoryChoice("c-dining", "Dining out", "Living", income = false),
            CategoryChoice("c-coffee", "Coffee", "Living", income = false),
            CategoryChoice("c-house", "Household", "Living", income = false),
            CategoryChoice("c-rent", "Rent", "Bills", income = false),
            CategoryChoice("c-power", "Power", "Bills", income = false),
            CategoryChoice("c-phone", "Phone", "Bills", income = false),
            CategoryChoice("c-hobby", "Hobbies", "Fun", income = false),
            CategoryChoice("c-gifts", "Gifts", "Fun", income = false),
            CategoryChoice("c-salary", "Salary", "Income", income = true),
        )

    private fun page() =
        RegisterPage(
            account = RegisterAccount("checking", "Checking", offBudget = false, type = "checking", closed = false),
            balanceMinor = 0,
            currency = Currencies.byCode("USD")!!,
            rows = listOf(row()),
            categories = categories,
            accounts = emptyList(),
            suggestions =
                CategorySuggestions(
                    byPayee = mapOf("corner coffee" to listOf("c-coffee", "c-dining")),
                    recentSpending = listOf("c-food"),
                ),
        )

    private fun row() =
        RegisterRow(
            id = "t",
            date = 20261007,
            payee = "Corner Coffee",
            categoryId = null,
            categoryLabel = "",
            amountMinor = -475,
            note = "",
            reconciled = false,
            transferId = null,
            partnerOffBudget = null,
            partnerCategoryId = null,
            parent = false,
            parts = emptyList(),
        )
}
