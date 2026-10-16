package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountChoice
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.RegisterAccount
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RegisterRow
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec 013: the transaction details page, its two picker pages and the in-place combo box. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class TransactionDetailsScreenshotTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun existingLight() = snap("transaction_light", dark = false)

    @Test
    fun existingDark() = snap("transaction_dark", dark = true)

    @Test
    fun newLight() = snap("transaction_new_light", dark = false, row = null)

    @Test
    fun newDark() = snap("transaction_new_dark", dark = true, row = null)

    @Test
    fun transferLight() = snap("transaction_transfer_light", dark = false, row = transfer())

    @Test
    fun transferDark() = snap("transaction_transfer_dark", dark = true, row = transfer())

    @Test
    fun comboInPlaceLight() = snap("transaction_combo_light", dark = false, categories = FEW) { open("transaction-category-value") }

    @Test
    fun comboInPlaceDark() = snap("transaction_combo_dark", dark = true, categories = FEW) { open("transaction-category-value") }

    @Test
    fun categoryListLight() = snap("transaction_category_list_light", dark = false) { open("transaction-category-value") }

    @Test
    fun categoryListDark() = snap("transaction_category_list_dark", dark = true) { open("transaction-category-value") }

    @Test
    fun datePickerLight() = snap("transaction_date_picker_light", dark = false) { open("transaction-date-value") }

    @Test
    fun datePickerDark() = snap("transaction_date_picker_dark", dark = true) { open("transaction-date-value") }

    @Test
    fun errorLight() = snap("transaction_error_light", dark = false, row = null) { it.save() }

    @Test
    fun errorDark() = snap("transaction_error_dark", dark = true, row = null) { it.save() }

    @Test
    fun largeFontLight() = snap("transaction_font2x_light", dark = false, fontScale = 2f)

    @Test
    fun largeFontDark() = snap("transaction_font2x_dark", dark = true, fontScale = 2f)

    private fun open(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performClick()
    }

    private fun snap(
        name: String,
        dark: Boolean,
        row: RegisterRow? = existing(),
        categories: List<CategoryChoice> = MANY,
        fontScale: Float = 1f,
        act: (FormSaveHost) -> Unit = {},
    ) {
        val host = FormSaveHost()
        val page = page(categories, row)
        val menu =
            when {
                row == null -> TransactionMenu.NEW
                row.transferId != null -> TransactionMenu.TRANSFER
                else -> TransactionMenu.ORDINARY
            }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalFormSaveHost provides host,
            ) {
                MetroTheme(darkTheme = dark) {
                    Column(Modifier.width(411.dp).height(891.dp)) {
                        SectionTitled("ACCOUNTS", Modifier.weight(1f)) {
                            TransactionForm(
                                page = page,
                                row = row,
                                initialDate = "2026-10-16",
                                error = null,
                                onSave = {},
                            )
                        }
                        val actions =
                            host.bar ?: ScreenBar
                                .specsFor(ShellRoute.EditTransaction("visa", row?.id), true, BudgetMode.ENVELOPE, transaction = menu)
                                .map { ScreenAction(it.id, it.glyph, it.label, it.primary) {} }
                        MetroAppBar(actions = actions, expanded = false, onExpandedChange = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { act(host) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun page(
        categories: List<CategoryChoice>,
        row: RegisterRow?,
    ) = RegisterPage(
        account = RegisterAccount("visa", "Visa", offBudget = false, type = "credit", closed = false),
        balanceMinor = -4_240,
        currency = Currencies.byCode("USD")!!,
        rows = listOfNotNull(row),
        categories = categories,
        accounts = listOf(AccountChoice("checking", "Checking", offBudget = false, type = "checking", closed = false)),
    )

    private fun existing() =
        RegisterRow(
            id = "bus",
            date = 20261001,
            payee = "Bus",
            categoryId = "c-transit",
            categoryLabel = "Transit",
            amountMinor = -1_240,
            note = "monthly pass top-up",
            reconciled = false,
            transferId = null,
            partnerOffBudget = null,
            partnerCategoryId = null,
            parent = false,
            parts = emptyList(),
        )

    private fun transfer() =
        existing().copy(
            id = "payment",
            payee = "Payment from Checking",
            categoryId = null,
            categoryLabel = "transfer",
            amountMinor = 20_000,
            note = "",
            transferId = "t-1",
            partnerOffBudget = false,
        )

    private companion object {
        val FEW =
            listOf(
                CategoryChoice("c-food", "Groceries", "Living", income = false),
                CategoryChoice("c-transit", "Transit", "Living", income = false),
                CategoryChoice("c-salary", "Salary", "Income", income = true),
            )
        val MANY =
            listOf(
                CategoryChoice("c-food", "Groceries", "Living", income = false),
                CategoryChoice("c-house", "Household", "Living", income = false),
                CategoryChoice("c-transit", "Transit", "Living", income = false),
                CategoryChoice("c-phone", "Phone", "Bills", income = false),
                CategoryChoice("c-power", "Power", "Bills", income = false),
                CategoryChoice("c-rent", "Rent", "Bills", income = false),
                CategoryChoice("c-fun", "Eating out", "Fun", income = false),
                CategoryChoice("c-gifts", "Gifts", "Fun", income = false),
                CategoryChoice("c-salary", "Salary", "Income", income = true),
            )
    }
}
