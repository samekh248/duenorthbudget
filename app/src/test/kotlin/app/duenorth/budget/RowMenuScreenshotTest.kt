package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.duenorth.budget.core.AccountChoice
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.RegisterAccount
import app.duenorth.budget.core.RegisterCopy
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RegisterRow
import app.duenorth.budget.design.components.MetroMenuItem
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The register's long-press menu: tap opens a row, long-press shows its menu with select first and delete last. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h780dp-xxhdpi")
class RowMenuScreenshotTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun registerMenuLight() = snap("register_row_menu_light", dark = false)

    @Test
    fun registerMenuDark() = snap("register_row_menu_dark", dark = true)

    @Test
    fun longPressShowsTheMenuInsteadOfStartingSelect() {
        val ran = mutableListOf<String>()
        val selected = mutableListOf<String>()
        show(dark = false, ran = ran, onSelect = { selected += it })
        compose.onNodeWithTag("register-row-cafe").performTouchInput { longClick() }
        compose.waitForIdle()
        // Long-press no longer jumps into select mode; select is the menu's first line.
        assertEquals(emptyList<String>(), selected)
        compose.onNodeWithText(RowMenuCopy.SELECT).performClick()
        assertEquals(listOf("select cafe"), ran)
        compose.onNodeWithTag("register-menu").assertDoesNotExist()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun snap(
        name: String,
        dark: Boolean,
    ) {
        show(dark)
        compose.onNodeWithTag("register-row-cafe").performTouchInput { longClick() }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }

    private fun show(
        dark: Boolean,
        ran: MutableList<String> = mutableListOf(),
        onSelect: (String) -> Unit = {},
    ) {
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.fillMaxSize()) {
                    RegisterScreen(
                        page = sample(),
                        onGesture = {},
                        onOpen = {},
                        onAdd = {},
                        onReconcile = {},
                        onSelect = onSelect,
                        rowMenu = { row ->
                            listOf(
                                MetroMenuItem(RowMenuCopy.SELECT) { ran += "select ${row.id}" },
                                MetroMenuItem(RowMenuCopy.EDIT) { ran += "edit ${row.id}" },
                                MetroMenuItem(RegisterCopy.CLEAR) { ran += "clear ${row.id}" },
                                MetroMenuItem(RegisterCopy.SCHEDULE) { ran += "schedule ${row.id}" },
                                destructiveItem(RegisterCopy.DELETE, "register-menu-delete") { ran += "delete ${row.id}" },
                            )
                        },
                    )
                }
            }
        }
    }

    private fun sample() =
        RegisterPage(
            account = RegisterAccount("checking", "Checking", offBudget = false, type = "checking", closed = false),
            balanceMinor = -2_480,
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
