package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

class OnboardingStoreTest {
    @Test
    fun progressRoundTripsAndClears() {
        val dir = Files.createTempDirectory("onboarding").toFile()
        val store = OnboardingStore(File(dir, "onboarding.json"))
        assertNull(store.read())
        val progress =
            OnboardingProgress(
                step = OnboardingStep.SIGN_IN,
                path = OnboardingPath.SERVER,
                addressText = "budget.example.com",
                readyAddress = "https://budget.example.com",
                methods = setOf(SignInMethod.PASSWORD),
                instanceLabel = "home server",
            )
        store.write(progress)
        assertEquals(progress, store.read())
        store.clear()
        assertNull(store.read())
    }

    @Test
    fun progressWithoutOriginReadsAsFirstRun() {
        val dir = Files.createTempDirectory("onboarding").toFile()
        val file = File(dir, "onboarding.json")
        file.writeText("""{"step":"ADDRESS","addressText":"a.example"}""")
        assertEquals(OnboardingOrigin.FIRST_RUN, OnboardingStore(file).read()?.origin)
        val settings = OnboardingProgress(step = OnboardingStep.ADDRESS, origin = OnboardingOrigin.SETTINGS)
        OnboardingStore(file).write(settings)
        assertEquals(settings, OnboardingStore(file).read())
    }

    @Test
    fun progressHasNoPlaceForAPassword() {
        val dir = Files.createTempDirectory("onboarding").toFile()
        val file = File(dir, "onboarding.json")
        OnboardingStore(file).write(OnboardingProgress(addressText = "a.example"))
        assertFalse(file.readText().contains("password", ignoreCase = true))
    }

    @Test
    fun currencyFollowsTheRegionWhenListed() {
        assertEquals("EUR", Currencies.forLocale(Locale.GERMANY))
        assertEquals("JPY", Currencies.forLocale(Locale.JAPAN))
        assertEquals("USD", Currencies.forLocale(Locale("pt", "BR")))
    }
}
