package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.text.input.ImeAction
import app.duenorth.budget.design.components.LocalFieldSubmit
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FieldSubmitContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    // Enter in a one-line box submits the page; a multi-line box keeps Enter for a new line.
    @Test
    fun enterSubmitsOneLineBoxesOnly() {
        var submitted = 0
        compose.setContent {
            MetroTheme {
                CompositionLocalProvider(LocalFieldSubmit provides { submitted++ }) {
                    Column {
                        var amount by remember { mutableStateOf("") }
                        var note by remember { mutableStateOf("") }
                        MetroField(amount, { amount = it }, "amount", Modifier.testTag("one-line"))
                        MetroField(note, { note = it }, "note", Modifier.testTag("multi-line"), lines = 2..6)
                    }
                }
            }
        }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("one-line"))).performImeAction()
        compose.waitForIdle()
        assertEquals(1, submitted)
        compose
            .onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("multi-line")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ImeAction, ImeAction.Default))
    }

    // A phone being tapped is in touch mode, where letting go of a box leaves nothing focused; off it,
    // Android hands focus straight back to the first box on the page.
    private fun touchMode(view: android.view.View) {
        val root = view.rootView.parent
        root.javaClass.getDeclaredMethod("ensureTouchModeLocally", Boolean::class.java).apply { isAccessible = true }(root, true)
        check(view.isInTouchMode)
    }

    // Enter lets go of the box so the keyboard closes and the rest of the page shows, with or without a save.
    @Test
    fun enterClearsFocus() {
        lateinit var view: android.view.View
        compose.setContent {
            view = LocalView.current
            MetroTheme {
                var name by remember { mutableStateOf("") }
                MetroField(name, { name = it }, "name", Modifier.testTag("name"))
            }
        }
        touchMode(view)
        val box = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("name")))
        box.performClick()
        box.assertIsFocused()
        box.performImeAction()
        compose.waitForIdle()
        box.assertIsNotFocused()
    }
}
