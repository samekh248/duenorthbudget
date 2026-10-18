package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountChoice
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.RegisterAccount
import app.duenorth.budget.core.RegisterPage
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
class PayeeTypeAheadScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun entryLight() = snap("payee_typeahead_light", dark = false)

    @Test
    fun entryDark() = snap("payee_typeahead_dark", dark = true)

    /** A new transaction with "co" typed in the payee box: saved payees that match, the top one in the accent. */
    private fun snap(
        name: String,
        dark: Boolean,
    ) {
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    TransactionForm(
                        page = page(),
                        row = null,
                        initialDate = "2026-10-07",
                        error = null,
                        onSave = {},
                        payees = listOf("Corner Coffee", "Costco", "City Power", "Bus", "Taco Corner"),
                    )
                }
            }
        }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("transaction-payee"))).performTextInput("co")
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun page() =
        RegisterPage(
            account = RegisterAccount("visa", "visa", offBudget = false, type = "credit", closed = false),
            balanceMinor = 0,
            currency = Currencies.byCode("USD")!!,
            rows = emptyList(),
            categories = listOf(CategoryChoice("c-food", "Groceries", "Living", income = false)),
            accounts = listOf(AccountChoice("cash", "Cash", offBudget = false, type = "checking", closed = false)),
        )
}
