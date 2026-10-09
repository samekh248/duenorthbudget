package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountChoice
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.RegisterAccount
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RegisterPart
import app.duenorth.budget.core.RegisterRow
import app.duenorth.budget.design.components.AppBarButton
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class RegisterScreenshotTest {
    @Test
    fun registerLight() = snapRegister("register_light", dark = false)

    @Test
    fun registerDark() = snapRegister("register_dark", dark = true)

    @Test
    fun entryLight() = snapEntry("entry_light", dark = false)

    @Test
    fun entryDark() = snapEntry("entry_dark", dark = true)

    private fun snapRegister(
        name: String,
        dark: Boolean,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp).height(780.dp)) {
                    RegisterScreen(
                        page = page(),
                        filter = "",
                        onFilter = {},
                        onGesture = {},
                        onOpen = {},
                        onAdd = {},
                        onTransfer = {},
                        modifier = Modifier.weight(1f),
                    )
                    bar()
                }
            }
        }
    }

    private fun snapEntry(
        name: String,
        dark: Boolean,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp).height(780.dp)) {
                    TransactionForm(
                        page = page(),
                        row = page().rows.last(),
                        initialDate = "2026-10-07",
                        error = null,
                        onSave = {},
                        onDelete = {},
                        onSplit = {},
                        modifier = Modifier.weight(1f),
                    )
                    bar()
                }
            }
        }
    }

    @Composable
    private fun bar() {
        MetroAppBar(
            buttons =
                listOf(
                    AppBarButton(AppGlyph.Budgets, "budgets") {},
                    AppBarButton(AppGlyph.Appearance, "appearance") {},
                ),
            expanded = false,
            onExpandedChange = {},
        )
    }

    private fun page() =
        RegisterPage(
            account = RegisterAccount("visa", "visa", offBudget = false, type = "credit", closed = false),
            balanceMinor = -4_240,
            currency = Currencies.byCode("USD")!!,
            rows =
                listOf(
                    RegisterRow(
                        id = "cafe",
                        date = 20261007,
                        payee = "Cafe",
                        categoryId = null,
                        categoryLabel = "split",
                        amountMinor = -3_000,
                        note = "",
                        reconciled = false,
                        transferId = null,
                        partnerOffBudget = null,
                        partnerCategoryId = null,
                        parent = true,
                        parts =
                            listOf(
                                RegisterPart("a", "c-food", "Groceries", -2_000),
                                RegisterPart("b", "c-house", "Household", -1_000),
                            ),
                    ),
                    RegisterRow(
                        id = "bus",
                        date = 20261001,
                        payee = "Bus",
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
                    ),
                ),
            categories =
                listOf(
                    CategoryChoice("c-food", "Groceries", "Living", income = false),
                    CategoryChoice("c-house", "Household", "Living", income = false),
                ),
            accounts = listOf(AccountChoice("checking", "Checking", false, "checking", false)),
        )
}
