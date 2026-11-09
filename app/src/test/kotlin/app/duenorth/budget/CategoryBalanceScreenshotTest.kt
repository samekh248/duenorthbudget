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

/** Every category picker shows each category's balance this month at the end of its line; overspent reads red. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class CategoryBalanceScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun listLight() = snapList("category_balance_list_light", dark = false)

    @Test
    fun listDark() = snapList("category_balance_list_dark", dark = true)

    @Test
    fun listMobile10Light() = snapList("category_balance_list_w10m_light", dark = false, style = MetroStyle.Mobile10)

    @Test
    fun comboLight() = snapCombo("category_balance_combo_light", dark = false)

    @Test
    fun comboDark() = snapCombo("category_balance_combo_dark", dark = true)

    /** The inbox's full-screen category picker. */
    private fun snapList(
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
                                currency = usd,
                                categories = many,
                                categoryId = "c-coffee",
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

    /** The transaction form's category box unfolded in place, as it is with five choices or fewer. */
    private fun snapCombo(
        name: String,
        dark: Boolean,
    ) {
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    TransactionForm(page = page(), row = row(), initialDate = "2026-10-07", error = null, onSave = {})
                }
            }
        }
        compose.onNodeWithTag("transaction-category-value", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private val usd = Currencies.byCode("USD")!!

    private val many =
        listOf(
            CategoryChoice("c-food", "Groceries", "Living", income = false, availableMinor = 21_450),
            CategoryChoice("c-dining", "Dining out", "Living", income = false, availableMinor = -1_275),
            CategoryChoice("c-coffee", "Coffee", "Living", income = false, availableMinor = 1_825),
            CategoryChoice("c-house", "Household", "Living", income = false, availableMinor = 0),
            CategoryChoice("c-rent", "Rent", "Bills", income = false, availableMinor = 145_000),
            CategoryChoice("c-power", "Power", "Bills", income = false, availableMinor = 6_310),
            CategoryChoice("c-phone", "Phone", "Bills", income = false, availableMinor = 4_500),
            CategoryChoice("c-salary", "Salary", "Income", income = true),
        )

    private val few =
        listOf(
            CategoryChoice("c-food", "Groceries", "Living", income = false, availableMinor = 21_450),
            CategoryChoice("c-dining", "Dining out", "Living", income = false, availableMinor = -1_275),
            CategoryChoice("c-transit", "Transit", "Living", income = false, availableMinor = 3_800),
            CategoryChoice("c-salary", "Salary", "Income", income = true),
        )

    private fun page() =
        RegisterPage(
            account = RegisterAccount("checking", "Checking", offBudget = false, type = "checking", closed = false),
            balanceMinor = 0,
            currency = usd,
            rows = listOf(row()),
            categories = few,
            accounts = emptyList(),
        )

    private fun row() =
        RegisterRow(
            id = "t",
            date = 20261007,
            payee = "Bus",
            categoryId = "c-transit",
            categoryLabel = "Transit",
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
