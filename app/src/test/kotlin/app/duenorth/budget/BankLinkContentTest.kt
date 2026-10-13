package app.duenorth.budget

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import app.duenorth.budget.core.BankProvider
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.ExternalAccount
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 010 T070: bank link from the register, the token field, and account pick. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class BankLinkContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun registerMenuOffersBankLink() {
        assertTrue("bank-link" in ScreenBar.specsFor(ShellRoute.Register("a"), true, BudgetMode.ENVELOPE).map { it.id })
    }

    @Test
    fun onlyConfiguredProvidersAndSimpleFinAreOffered() {
        show(BankLinkSamples.ui(configured = listOf(BankProvider.PLUGGY)))
        compose.onNodeWithTag("bank-provider-pluggyai").assertExists()
        compose.onNodeWithTag("bank-provider-simpleFin").assertExists()
        compose.onNodeWithTag("bank-provider-goCardless").assertDoesNotExist()
    }

    @Test
    fun theTokenIsSentOnceAndTheFieldEmpties() {
        val sent = mutableListOf<String>()
        show(BankLinkSamples.ui(provider = BankProvider.SIMPLEFIN), onToken = { sent += it })
        val field = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("bank-token")))
        field.performScrollTo().performTextInput("setup-token")
        compose.onNodeWithTag("bank-token-send").performScrollTo().performClick()
        assertEquals(listOf("setup-token"), sent)
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test
    fun pickingAnAccountLinksIt() {
        val linked = mutableListOf<ExternalAccount>()
        show(BankLinkSamples.ui(provider = BankProvider.SIMPLEFIN, accounts = BankLinkSamples.accounts), onLink = { linked += it })
        compose.onNodeWithTag("bank-account-ACT-2").performScrollTo().performClick()
        assertEquals(listOf(BankLinkSamples.accounts[1]), linked)
    }

    @Test
    fun aLinkedAccountCanBeRemovedAndErrorsShow() {
        var removed = 0
        show(BankLinkSamples.linked().copy(error = "couldn't reach the bank provider"), onRemove = { removed++ })
        compose.onNodeWithTag("bank-linked").assertExists()
        compose.onNodeWithTag("bank-link-error").assertExists()
        compose.onNodeWithTag("bank-link-remove").performClick()
        assertEquals(1, removed)
    }

    @Test
    fun goCardlessAsksForACountryThenTheBank() {
        var country = ""
        show(BankLinkSamples.ui(configured = listOf(BankProvider.GOCARDLESS), provider = BankProvider.GOCARDLESS), onCountry = {
            country =
                it
        })
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("bank-country"))).performScrollTo().performTextInput("GB")
        compose.onNodeWithTag("bank-country-find").performScrollTo().performClick()
        assertEquals("GB", country)
    }

    private fun show(
        ui: BankLinkUi,
        onToken: (String) -> Unit = {},
        onCountry: (String) -> Unit = {},
        onLink: (ExternalAccount) -> Unit = {},
        onRemove: () -> Unit = {},
    ) {
        compose.setContent {
            MetroTheme {
                BankLinkScreen(ui, BankLinkSamples.usd, {}, onToken, onCountry, {}, {}, {}, onLink, onRemove)
            }
        }
    }
}
