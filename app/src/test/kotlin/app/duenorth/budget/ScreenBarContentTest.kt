package app.duenorth.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.MetroAppBarHost
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScreenBarContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun collapsedPrimaryRunsOnceAndEllipsisDoesNotNavigate() {
        var addClicks = 0
        var navigated = false
        compose.setContent {
            var expanded by remember { mutableStateOf(false) }
            MetroTheme {
                Column {
                    MetroAppBar(
                        actions =
                            listOf(
                                ScreenAction("add", AppGlyph.Add, "add") { addClicks += 1 },
                                ScreenAction("hold", AppGlyph.Hold, "hold") {},
                            ),
                        expanded = expanded,
                        onExpandedChange = {
                            expanded = it
                            navigated = true
                        },
                    )
                }
            }
        }
        compose.onNodeWithTag("app-bar-add").assertIsDisplayed().performClick()
        assertEquals(1, addClicks)
        compose.onNodeWithTag("app-bar-ellipsis").performClick()
        // Expanding is not a navigation away from the screen.
        assertEquals(true, navigated)
        compose.onNodeWithTag("app-bar-add").performClick()
        assertEquals(2, addClicks)
        compose.onAllNodesWithTag("app-bar-menu-hold").assertCountEquals(0)
    }

    @Test
    fun runningAnActionClosesTheBar() {
        var expanded by mutableStateOf(true)
        compose.setContent {
            MetroTheme {
                Column {
                    MetroAppBar(
                        actions = listOf(ScreenAction("add", AppGlyph.Add, "add") {}),
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    )
                }
            }
        }
        compose.onAllNodesWithText("add").assertCountEquals(1)
        compose.onNodeWithTag("app-bar-add").performClick()
        assertFalse(expanded)
        compose.onAllNodesWithText("add").assertCountEquals(0)
    }

    @Test
    fun touchOutsideClosesTheBarAndIsSwallowed() {
        var expanded by mutableStateOf(true)
        var behind = 0
        compose.setContent {
            MetroTheme {
                MetroAppBarHost(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag("behind")
                                .clickable { behind += 1 },
                        )
                        MetroAppBar(
                            actions = listOf(ScreenAction("add", AppGlyph.Add, "add") {}),
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("behind").performClick()
        assertFalse(expanded)
        assertEquals(0, behind)
        // Once closed, the same touch reaches the screen again.
        compose.onNodeWithTag("behind").performClick()
        assertEquals(1, behind)
    }

    @Test
    fun aPageWithNoActionsShowsNoBar() {
        compose.setContent {
            MetroTheme {
                Column { MetroAppBar(actions = emptyList(), expanded = false, onExpandedChange = {}) }
            }
        }
        compose.onAllNodesWithTag("app-bar-ellipsis").assertCountEquals(0)
    }
}
