package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The categories page in light and dark: folded groups, a category's and a group's actions, rename, delete, reorder. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class CategoryManageScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun listLight() = snap("categories_light", dark = false) { open("g-food") }

    @Test
    fun listDark() = snap("categories_dark", dark = true) { open("g-food") }

    @Test
    fun categoryMenuLight() = snap("categories_category_menu_light", dark = false) { menu("g-save", "c-vac") }

    @Test
    fun categoryMenuDark() = snap("categories_category_menu_dark", dark = true) { menu("g-save", "c-vac") }

    @Test
    fun groupMenuLight() = snap("categories_group_menu_light", dark = false) { groupMenu() }

    @Test
    fun groupMenuDark() = snap("categories_group_menu_dark", dark = true) { groupMenu() }

    @Test
    fun renameLight() = snap("categories_rename_light", dark = false) { rename() }

    @Test
    fun renameDark() = snap("categories_rename_dark", dark = true) { rename() }

    @Test
    fun deleteLight() = snap("categories_delete_light", dark = false) { delete() }

    @Test
    fun deleteDark() = snap("categories_delete_dark", dark = true) { delete() }

    @Test
    fun reorderLight() = snap("categories_reorder_light", dark = false) { reorder() }

    @Test
    fun reorderDark() = snap("categories_reorder_dark", dark = true) { reorder() }

    private val host = FormSaveHost()

    private fun open(group: String) = compose.onNodeWithTag("manage-group-$group").performClick()

    private fun menu(
        group: String,
        category: String,
    ) {
        open(group)
        compose.onNodeWithTag("manage-category-$category").performClick()
    }

    private fun groupMenu() {
        open("g-save")
        compose.onNodeWithTag("manage-group-menu-g-save").performClick()
    }

    private fun rename() {
        menu("g-save", "c-vac")
        compose.onNodeWithTag("manage-action-rename").performClick()
    }

    private fun delete() {
        menu("g-save", "c-gym")
        compose.onNodeWithTag("manage-action-delete").performClick()
    }

    private fun reorder() {
        open("g-food")
        host.bar!!.first { it.id == "manage-reorder" }.run()
    }

    private fun snap(
        name: String,
        dark: Boolean,
        steps: () -> Unit,
    ) {
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalFormSaveHost provides host) {
                    Column(Modifier.width(390.dp).height(780.dp)) {
                        Box(Modifier.weight(1f)) {
                            ManageCategoriesScreen(
                                CategoryManageSamples.page,
                                null,
                                {},
                                { _, _ -> },
                                { _, _ -> },
                                { _, _ -> },
                                { _, _ -> },
                                { _, _ -> },
                                { _, _ -> },
                                {},
                                {},
                                { _, _ -> },
                                { _, _ -> },
                            )
                        }
                        MetroAppBar(host.bar.orEmpty(), false, {}, windowInsets = WindowInsets(bottom = 0.dp))
                    }
                }
            }
        }
        steps()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }
}
