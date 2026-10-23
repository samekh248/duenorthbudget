package app.duenorth.budget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LinkSafetyTest {
    @Test
    fun onlyWebPagesOpenInTheBrowserTab() {
        assertTrue(isWebUrl("https://ob.gocardless.com/psd2/start/abc"))
        assertTrue(isWebUrl("http://192.168.1.20:5006/openid"))
        assertTrue(isWebUrl(" HTTPS://budget.example.com "))
        assertFalse(isWebUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(isWebUrl("file:///data/data/app.duenorth.budget/shared_prefs/due-north-secrets.xml"))
        assertFalse(isWebUrl("javascript:alert(1)"))
        assertFalse(isWebUrl("market://details?id=x"))
        assertFalse(isWebUrl("budget.example.com"))
    }

    @Test
    fun onlyTheSignInReturnAddressCompletesASignIn() {
        assertTrue(ShellViewModel.isOpenIdReturn("duenorth://oauth?token=abc"))
        assertTrue(ShellViewModel.isOpenIdReturn("duenorth://oauth/openid-cb?token=abc"))
        assertFalse(ShellViewModel.isOpenIdReturn("duenorth://elsewhere?token=abc"))
        assertFalse(ShellViewModel.isOpenIdReturn("https://evil.example/oauth?token=abc"))
        assertFalse(ShellViewModel.isOpenIdReturn("not a link"))
    }
}
