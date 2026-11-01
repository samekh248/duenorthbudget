package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** A category's page renames its category: `•••` rename opens the name over the month, and the bar saves or cancels it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h780dp-xxhdpi")
class CategoryRenameScreenshotTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun menuLight() = snap("category_rename_menu_light", dark = false, renaming = false)

    @Test
    fun menuDark() = snap("category_rename_menu_dark", dark = true, renaming = false)

    @Test
    fun openLight() = snap("category_rename_open_light", dark = false, renaming = true)

    @Test
    fun openDark() = snap("category_rename_open_dark", dark = true, renaming = true)

    @Test
    fun saveOnTheBarSavesWhatWasTyped() {
        val saved = mutableListOf<String>()
        page(dark = false, renaming = true, onSave = { saved += it })
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("category-rename-field"))).performTextReplacement("Food shop")
        compose.onNodeWithTag("app-bar-rename-save").performClick()
        assertEquals(listOf("Food shop"), saved)
    }

    @Test
    fun cancelOnTheBarCloses() {
        var cancelled = 0
        page(dark = false, renaming = true, onCancel = { cancelled++ })
        compose.onNodeWithTag("app-bar-rename-cancel").performClick()
        assertEquals(1, cancelled)
    }

    private fun snap(
        name: String,
        dark: Boolean,
        renaming: Boolean,
    ) {
        page(dark, renaming)
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }

    private fun page(
        dark: Boolean,
        renaming: Boolean,
        onSave: (String) -> Unit = {},
        onCancel: () -> Unit = {},
    ) {
        val shell = sample()
        val routeBar =
            ScreenBar.specsFor(ShellRoute.CategoryBudget("c-groceries"), true, BudgetMode.ENVELOPE).map {
                ScreenAction(it.id, it.glyph, it.label, primary = it.primary) {}
            }
        compose.setContent {
            val host = rememberFormSaveHost()
            MetroTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalFormSaveHost provides host) {
                    Column(Modifier.fillMaxSize()) {
                        EditorWithMonth(
                            openMonth = shell.month,
                            monthWindow = MonthWindow.around(shell.month),
                            onSelectMonth = {},
                            modifier = Modifier.weight(1f),
                            title = "Groceries",
                            titleField =
                                if (renaming) {
                                    { field -> CategoryRenameField("Groceries", onSave, onCancel, field) }
                                } else {
                                    null
                                },
                        ) { inner ->
                            CategoryBudgetScreen(
                                shell = shell,
                                categoryId = "c-groceries",
                                error = null,
                                onSave = {},
                                onMove = {},
                                onToggleCarryover = {},
                                modifier = inner,
                                showName = false,
                            )
                        }
                        // The menu shows open on the page's own bar; renaming, the field's save and cancel take its place.
                        MetroAppBar(host.bar ?: routeBar, !renaming, {}, windowInsets = WindowInsets(bottom = 0.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
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
            groups =
                listOf(
                    GroupRow(
                        "g-food",
                        "Food",
                        12_000,
                        listOf(CategoryRow("c-groceries", "Groceries", 40_000, -28_000, 12_000, false)),
                    ),
                ),
            accounts = emptyList(),
            inbox = emptyList(),
        )
}
