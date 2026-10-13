package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BankProvider
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec 010 T070: bank link in light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class BankLinkScreenshotTest {
    @Test
    fun tokenLight() = snap("bank_link_token_light", dark = false) { Screen(BankLinkSamples.ui(provider = BankProvider.SIMPLEFIN)) }

    @Test
    fun tokenDark() = snap("bank_link_token_dark", dark = true) { Screen(BankLinkSamples.ui(provider = BankProvider.SIMPLEFIN)) }

    @Test
    fun linkedLight() = snap("bank_link_linked_light", dark = false) { Screen(BankLinkSamples.linked()) }

    @Test
    fun linkedDark() = snap("bank_link_linked_dark", dark = true) { Screen(BankLinkSamples.linked()) }

    @Composable
    private fun Screen(ui: BankLinkUi) {
        BankLinkScreen(ui, BankLinkSamples.usd, {}, {}, {}, {}, {}, {}, {}, {})
    }

    private fun snap(
        name: String,
        dark: Boolean,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Box(Modifier.width(390.dp).height(780.dp)) { content() }
            }
        }
    }
}
