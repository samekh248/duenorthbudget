package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.MetroStyle
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** The budget's edit mode: boxes in the budgeted column, to budget pinned, and the keypad in the bar's place. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class BudgetEditScreenshotTest {
    @Test
    fun editLight() = snap("budget_edit_light", dark = false, edit = BudgetEdit())

    @Test
    fun editDark() = snap("budget_edit_dark", dark = true, edit = BudgetEdit())

    // "+80" typed on Groceries: to budget and the row's available follow before it is saved.
    @Test
    fun keypadLight() = snap("budget_edit_keypad_light", dark = false, edit = BudgetEdit("c-groceries", "+80"))

    @Test
    fun keypadDark() = snap("budget_edit_keypad_dark", dark = true, edit = BudgetEdit("c-groceries", "+80"))

    // Everything budgeted: the pinned to budget goes away.
    // The relief styles on the keypad: keys stand up, carved cuts the operators in.
    @Test
    fun styleKeypads() =
        MetroStyle.entries.filter { it.relief }.forEach { style ->
            for (dark in listOf(false, true)) {
                snap("style_${style.id}_keypad_${if (dark) "dark" else "light"}", dark, BudgetEdit("c-groceries", "+80"), style = style)
            }
        }

    @Test
    fun fullyBudgetedLight() = snap("budget_edit_zero_light", dark = false, edit = BudgetEdit(), toBudget = 0, held = 0)

    // Tapping Gas's available: 20 moving to Groceries, with both figures showing the result.
    @Test
    fun moveLight() = moveSnap("budget_edit_move_light", dark = false)

    @Test
    fun moveDark() = moveSnap("budget_edit_move_dark", dark = true)

    private fun moveSnap(
        name: String,
        dark: Boolean,
    ) {
        val shell = sample()
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    ShellChrome(
                        actions =
                            ScreenBar.specsFor(ShellRoute.MoveCategory("c-gas"), true, BudgetMode.ENVELOPE).map {
                                ScreenAction(it.id, it.glyph, it.label, it.primary) {}
                            },
                        statusBar =
                            androidx.compose.foundation.layout
                                .WindowInsets(0),
                    ) { modifier ->
                        MoveCategoryScreen(
                            shell = shell,
                            fromCategoryId = "c-gas",
                            error = null,
                            onSave = { _, _ -> },
                            modifier = modifier,
                            initialTo = "c-groceries",
                            initialAmount = "20",
                        )
                    }
                }
            }
        }
    }

    private fun snap(
        name: String,
        dark: Boolean,
        edit: BudgetEdit,
        toBudget: Long = 42_000,
        held: Long = 25_000,
        style: MetroStyle = MetroStyle.Metro,
    ) {
        val shell = sample().copy(headerMinor = toBudget, bufferedMinor = held)
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark, style = style) {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    ShellChrome(
                        actions =
                            ScreenBar
                                .specsFor(ShellRoute.Home, true, BudgetMode.ENVELOPE, undoLabel = "assign", budgetEditing = true)
                                .map { ScreenAction(it.id, it.glyph, it.label, it.primary) {} },
                        statusBar =
                            androidx.compose.foundation.layout
                                .WindowInsets(0),
                        bar = edit.categoryId == null,
                    ) { modifier ->
                        HomePivot(
                            shell = shell,
                            loading = false,
                            openMonth = shell.month,
                            monthWindow = MonthWindow.around(shell.month),
                            onGesture = {},
                            modifier = modifier,
                            initialSection = HomeSections.headers.indexOf(HomeSections.BUDGET),
                            budgetEdit = edit,
                        )
                    }
                }
            }
        }
    }

    private fun sample(): MonthShell =
        MonthShell(
            budgetId = "home",
            budgetName = "Home",
            mode = BudgetMode.ENVELOPE,
            currency = Currencies.byCode("USD")!!,
            month = YearMonth.of(2026, 10),
            headerLabel = "to budget",
            headerMinor = 42_000,
            bufferedMinor = 25_000,
            groups =
                listOf(
                    GroupRow(
                        "g-bills",
                        "Bills",
                        124_000,
                        listOf(
                            CategoryRow("c-rent", "Rent", 110_000, 0, 110_000, false),
                            CategoryRow("c-power", "Power", 14_000, 0, 14_000, false),
                        ),
                    ),
                    GroupRow(
                        "g-every",
                        "Everyday",
                        18_640,
                        listOf(
                            CategoryRow("c-gas", "Gas", 12_000, 4_360, 7_640, false),
                            CategoryRow("c-phone", "Phone", 11_000, 0, 11_000, false),
                        ),
                    ),
                    GroupRow(
                        "g-food",
                        "Food",
                        -3_200,
                        listOf(
                            CategoryRow("c-groceries", "Groceries", 20_000, 28_000, -8_000, false),
                            CategoryRow("c-dining", "Dining", 8_000, 3_200, 4_800, false),
                        ),
                    ),
                    GroupRow("g-save", "Savings", 80_000, listOf(CategoryRow("c-rainy", "Rainy day", 80_000, 0, 80_000, false))),
                ),
            accounts = emptyList(),
            inbox = emptyList(),
        )
}
