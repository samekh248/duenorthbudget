package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.LedgerPage
import app.duenorth.budget.core.LedgerRow
import app.duenorth.budget.core.RegisterRow
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A category's "see all": the ledger across every account, under a chip that clears the category filter. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class LedgerScreenshotTest {
    @Test
    fun filteredLight() = snap("ledger_category_light", dark = false)

    @Test
    fun filteredDark() = snap("ledger_category_dark", dark = true)

    private fun snap(
        name: String,
        dark: Boolean,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp).height(780.dp)) {
                    SectionTitled("BUDGET", Modifier.weight(1f)) {
                        LedgerScreen(page = ledgerSample(), onGesture = {}, onOpen = {}, onClearFilter = {})
                    }
                }
            }
        }
    }
}

internal fun ledgerSample(): LedgerPage {
    fun row(
        id: String,
        date: Int,
        payee: String,
        amount: Long,
        account: String,
        cleared: Boolean,
        note: String = "",
    ) = LedgerRow(
        row =
            RegisterRow(
                id = id,
                date = date,
                payee = payee,
                categoryId = "c-food",
                categoryLabel = "Groceries",
                amountMinor = amount,
                note = note,
                reconciled = false,
                transferId = null,
                partnerOffBudget = null,
                partnerCategoryId = null,
                parent = false,
                parts = emptyList(),
                cleared = cleared,
            ),
        accountId = account.lowercase(),
        accountName = account,
    )
    return LedgerPage(
        currency = Currencies.byCode("USD")!!,
        categoryId = "c-food",
        categoryName = "Groceries",
        rows =
            listOf(
                row("t1", 20261110, "Corner Market", -4_812, "Checking", cleared = false),
                row("t2", 20261110, "Corner Coffee", -575, "Visa", cleared = false),
                row("t3", 20261106, "Farmers Market", -2_200, "Cash", cleared = true, note = "eggs and bread"),
                row("t4", 20261031, "Big Box Grocery", -13_467, "Visa", cleared = true),
                row("t5", 20261024, "Corner Market", -3_950, "Checking", cleared = true),
                row("t6", 20261012, "Big Box Grocery", -11_020, "Visa", cleared = true),
            ),
    )
}
