package app.duenorth.budget

import android.os.Looper
import app.duenorth.budget.core.ActualTransport
import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.KeyMaterial
import app.duenorth.budget.core.MemorySecretStore
import app.duenorth.budget.core.OnboardingCopy
import app.duenorth.budget.core.OnboardingPath
import app.duenorth.budget.core.OnboardingStep
import app.duenorth.budget.core.ProbeHttp
import app.duenorth.budget.core.ProbeResponse
import app.duenorth.budget.core.RemoteFile
import app.duenorth.budget.core.ServerProbe
import app.duenorth.budget.core.SignInMethod
import app.duenorth.budget.core.SyncAuthException
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.activeInstance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OnboardingFlowTest {
    private val transport = FakeTransport()

    @Test
    fun freshPhoneOpensOnWelcome() {
        val (_, model) = model("fresh")
        assertEquals(ShellRoute.Welcome, model.state.value.route)
        assertNotNull(model.state.value.onboarding)
        assertFalse(model.back())
    }

    @Test
    fun phoneWithABudgetNeverSeesWelcome() {
        val root = root("existing")
        library(root).create("Home", "USD")
        // A leftover progress file from an earlier install is dropped.
        library(root).onboarding().write(
            app.duenorth.budget.core
                .OnboardingProgress(step = OnboardingStep.ADDRESS),
        )
        val model = model(root)
        await { model.state.value.shell != null }
        assertEquals(ShellRoute.Home, model.state.value.route)
        assertNull(model.state.value.onboarding)
        assertNull(library(root).onboarding().read())
    }

    @Test
    fun phoneOnlyPathCreatesABudgetWithNoServerQuestions() {
        val (library, model) = model("phone")
        model.onboardingChoosePhone()
        assertEquals(ShellRoute.Create, model.state.value.route)
        // Back from the new-budget page returns to welcome.
        assertTrue(model.back())
        assertEquals(ShellRoute.Welcome, model.state.value.route)
        model.onboardingChoosePhone()
        model.create("", "USD")
        await { model.state.value.createError != null }
        assertTrue(library.list().isEmpty())
        model.create("Home", "EUR")
        await { model.state.value.shell != null }
        assertEquals(
            "Home",
            model.state.value.shell!!
                .budgetName,
        )
        assertNull(model.state.value.onboarding)
        await { library.onboarding().read() == null }
        assertTrue(transport.logins.isEmpty())
    }

    @Test
    fun serverPathSignsInNamesTheInstanceAndSendsANewBudget() {
        val (library, model) = model("server")
        model.onboardingChooseServer()
        assertEquals(ShellRoute.OnboardingAddress, model.state.value.route)
        model.onboardingAddress("budget.example.com")
        model.onboardingCheck()
        await { model.state.value.route == ShellRoute.OnboardingSignIn }
        val progress =
            model.state.value.onboarding!!
                .progress
        assertEquals("https://budget.example.com", progress.readyAddress)
        assertEquals(setOf(SignInMethod.PASSWORD), progress.methods)
        assertEquals("budget.example.com", progress.instanceLabel)

        model.onboardingLabel("home server")
        model.onboardingSignIn("wrong")
        await {
            model.state.value.onboarding
                ?.signInError != null
        }
        assertEquals(
            SyncCopy.SIGN_IN_FAILED,
            model.state.value.onboarding!!
                .signInError,
        )
        assertEquals(ShellRoute.OnboardingSignIn, model.state.value.route)

        model.onboardingSignIn(FakeTransport.PASSWORD)
        await { model.state.value.route == ShellRoute.OnboardingFiles }
        val active = library.settings().activeInstance()!!
        assertEquals("home server", active.label)
        assertEquals("https://budget.example.com", active.address)

        model.onboardingNewBudget()
        assertEquals(ShellRoute.Create, model.state.value.route)
        model.create("Household", "USD")
        await { model.state.value.shell != null }
        await { transport.uploads.isNotEmpty() }
        assertEquals("Household", transport.uploads.single())
        assertNull(model.state.value.onboarding)
    }

    @Test
    fun wrongAddressSaysWhyAndKeepsTheText() {
        val (_, model) = model("wrong", http = { throw IOException("refused") })
        model.onboardingChooseServer()
        model.onboardingAddress("nowhere.example")
        model.onboardingCheck()
        await {
            model.state.value.onboarding
                ?.addressError != null
        }
        val ui = model.state.value.onboarding!!
        assertEquals(OnboardingCopy.NOT_REACHED, ui.addressError)
        assertEquals("nowhere.example", ui.progress.addressText)
        assertEquals(ShellRoute.OnboardingAddress, model.state.value.route)
        model.onboardingAddress("")
        model.onboardingCheck()
        assertEquals(
            OnboardingCopy.ENTER_ADDRESS,
            model.state.value.onboarding!!
                .addressError,
        )
    }

    @Test
    fun leavingAndComingBackResumesTheSameStep() {
        val root = root("resume")
        val first = model(root)
        first.onboardingChooseServer()
        first.onboardingAddress("budget.example.com")
        first.onboardingCheck()
        await { first.state.value.route == ShellRoute.OnboardingSignIn }
        first.onboardingLabel("home")
        await { library(root).onboarding().read()?.instanceLabel == "home" }
        val saved = File(root, "onboarding.json").readText()
        assertFalse(saved.contains(FakeTransport.PASSWORD))

        // A new view model over the same files is the app after a force stop.
        val second = model(root)
        await { second.state.value.route == ShellRoute.OnboardingSignIn }
        val progress =
            second.state.value.onboarding!!
                .progress
        assertEquals("budget.example.com", progress.addressText)
        assertEquals("home", progress.instanceLabel)
        assertEquals(OnboardingPath.SERVER, progress.path)

        assertTrue(second.back())
        assertEquals(ShellRoute.OnboardingAddress, second.state.value.route)
        assertEquals(
            "budget.example.com",
            second.state.value.onboarding!!
                .progress.addressText,
        )
        assertTrue(second.back())
        assertEquals(ShellRoute.Welcome, second.state.value.route)
    }

    @Test
    fun addServerFromSettingsReturnsToTheServerItem() {
        val root = root("settings-add")
        library(root).create("Home", "USD")
        val model = model(root)
        await { model.state.value.shell != null }
        model.showSettings()
        model.selectSettingsPage(SettingsPage.SERVER)

        model.onboardingAddServer()
        assertEquals(ShellRoute.OnboardingAddress, model.state.value.route)
        assertEquals(
            "",
            model.state.value.onboarding!!
                .progress.addressText,
        )
        // Back from the address page: nothing saved, server item showing.
        assertTrue(model.back())
        assertEquals(ShellRoute.Settings, model.state.value.route)
        assertEquals(SettingsPage.SERVER, model.state.value.settingsPage)
        assertNull(model.state.value.onboarding)
        assertTrue(library(root).settings().instances.isEmpty())

        model.onboardingAddServer()
        model.onboardingAddress("budget.example.com")
        model.onboardingCheck()
        await { model.state.value.route == ShellRoute.OnboardingSignIn }
        model.onboardingLabel("home server")
        model.onboardingSignIn(FakeTransport.PASSWORD)
        // Signing in goes on to the budget picker; back from it leaves the phone's budget alone.
        await { model.state.value.route == ShellRoute.OnboardingFiles }
        assertEquals("home server", library(root).settings().activeInstance()?.label)
        assertTrue(model.back())
        assertEquals(ShellRoute.Settings, model.state.value.route)
        assertEquals(SettingsPage.SERVER, model.state.value.settingsPage)
        assertEquals(listOf("Home"), library(root).list().map { it.name })
        assertNull(model.state.value.onboarding)
        await { library(root).onboarding().read() == null }
    }

    @Test
    fun settingsAddServerResumesAfterARestart() {
        val root = root("settings-resume")
        library(root).create("Home", "USD")
        library(root).onboarding().write(
            app.duenorth.budget.core.OnboardingProgress(
                step = OnboardingStep.ADDRESS,
                path = OnboardingPath.SERVER,
                addressText = "budget.example.com",
                origin = app.duenorth.budget.core.OnboardingOrigin.SETTINGS,
            ),
        )
        val model = model(root)
        await { model.state.value.shell != null }
        assertEquals(ShellRoute.OnboardingAddress, model.state.value.route)
        assertEquals(
            "budget.example.com",
            model.state.value.onboarding!!
                .progress.addressText,
        )
        assertTrue(model.back())
        assertEquals(ShellRoute.Settings, model.state.value.route)
        assertEquals(SettingsPage.SERVER, model.state.value.settingsPage)
    }

    @Test
    fun settingsPagesReturnToTheirItem() {
        val root = root("settings-return")
        library(root).create("Home", "USD")
        val model = model(root)
        await { model.state.value.shell != null }
        model.showSettings()
        assertEquals(SettingsPage.BUDGET, model.state.value.settingsPage)

        model.selectSettingsPage(SettingsPage.ADVANCED)
        model.showEncryption(EncryptionMode.SET)
        assertEquals(ShellRoute.EncryptionPassword(EncryptionMode.SET), model.state.value.route)
        assertTrue(model.back())
        assertEquals(ShellRoute.Settings, model.state.value.route)
        assertEquals(SettingsPage.ADVANCED, model.state.value.settingsPage)

        // Setting a password returns to advanced and shows the budget as encrypted.
        model.showEncryption(EncryptionMode.SET)
        model.setEncryptionPassword("secret")
        await { model.state.value.route == ShellRoute.Settings && model.state.value.budgetEncrypted }
        assertEquals(SettingsPage.ADVANCED, model.state.value.settingsPage)

        model.showSettingsPayees()
        assertEquals(ShellRoute.Payees, model.state.value.route)
        assertTrue(model.back())
        assertEquals(SettingsPage.BUDGET, model.state.value.settingsPage)

        // New budget from settings opens it and comes back to the budget item.
        model.showSettingsCreate()
        model.create("Trip", "EUR")
        await {
            model.state.value.shell
                ?.budgetName == "Trip"
        }
        assertEquals(ShellRoute.Settings, model.state.value.route)
        assertEquals(SettingsPage.BUDGET, model.state.value.settingsPage)
        assertFalse(model.state.value.settingsReturn)

        // Leaving settings and coming back starts on the budget item.
        model.selectSettingsPage(SettingsPage.APPEARANCE)
        assertTrue(model.back())
        assertEquals(ShellRoute.Home, model.state.value.route)
        model.showSettings()
        assertEquals(SettingsPage.BUDGET, model.state.value.settingsPage)
    }

    @Test
    fun removingAServerAsksFirst() {
        val root = root("settings-remove")
        library(root).create("Home", "USD")
        val model = model(root)
        await { model.state.value.shell != null }
        model.showSettings()
        model.onboardingAddServer()
        model.onboardingAddress("budget.example.com")
        model.onboardingCheck()
        await { model.state.value.route == ShellRoute.OnboardingSignIn }
        model.onboardingSignIn(FakeTransport.PASSWORD)
        await { model.state.value.route == ShellRoute.OnboardingFiles }
        assertTrue(model.back())
        val id =
            library(root)
                .settings()
                .activeInstance()!!
                .id

        model.requestRemoveInstance(id)
        assertEquals(ShellRoute.ConfirmRemoveInstance(id), model.state.value.route)
        assertTrue(model.back())
        assertEquals(1, library(root).settings().instances.size)

        model.requestRemoveInstance(id)
        model.confirmRemoveInstance()
        await { library(root).settings().instances.isEmpty() }
        assertEquals(ShellRoute.Settings, model.state.value.route)
        assertEquals(SettingsPage.SERVER, model.state.value.settingsPage)
    }

    @Test
    fun pickingAServerBudgetFromSettingsReplacesThePhonesBudget() {
        val root = root("settings-pick")
        library(root).create("Home", "USD")
        transport.files += RemoteFile(fileId = "server-household", groupId = "g-1", name = "Household", encryptKeyId = null)
        transport.downloads["server-household"] = budgetBytes("Household")
        val model = model(root)
        await { model.state.value.shell != null }
        model.showSettings()
        model.selectSettingsPage(SettingsPage.SERVER)
        model.onboardingAddServer()
        model.onboardingAddress("budget.example.com")
        model.onboardingCheck()
        await { model.state.value.route == ShellRoute.OnboardingSignIn }
        model.onboardingSignIn(FakeTransport.PASSWORD)
        await { model.state.value.route == ShellRoute.OnboardingFiles }
        assertEquals(listOf("Household"), model.state.value.remoteFiles.map { it.name })

        // The confirmation names the budget being cleared; keep leaves it.
        model.onboardingOpen(model.state.value.remoteFiles.single())
        await { model.state.value.route == ShellRoute.ConfirmReplaceBudget }
        assertEquals(listOf("Home"), model.state.value.replaceBudgets.map { it.name })
        assertTrue(model.back())
        assertEquals(ShellRoute.OnboardingFiles, model.state.value.route)
        assertEquals(listOf("Home"), library(root).list().map { it.name })

        model.onboardingOpen(model.state.value.remoteFiles.single())
        await { model.state.value.route == ShellRoute.ConfirmReplaceBudget }
        model.confirmReplaceBudget()
        await {
            model.state.value.shell
                ?.budgetName == "Household"
        }
        // A budget with no experimental choice yet asks first, then shows its home.
        await { model.state.value.route is ShellRoute.ExperimentalOffer }
        model.declineExperimentalOffer()
        assertEquals(ShellRoute.Home, model.state.value.route)
        assertEquals(listOf("Household"), library(root).list().map { it.name })
        assertEquals("server-household", library(root).settings().openBudgetId)
        assertNull(model.state.value.onboarding)
        await { library(root).onboarding().read() == null }
    }

    @Test
    fun switchingToASignedInServerOpensItsBudgetPicker() {
        val root = root("settings-switch")
        library(root).create("Home", "USD")
        val model = model(root)
        await { model.state.value.shell != null }
        model.showSettings()
        model.onboardingAddServer()
        model.onboardingAddress("budget.example.com")
        model.onboardingCheck()
        await { model.state.value.route == ShellRoute.OnboardingSignIn }
        model.onboardingSignIn(FakeTransport.PASSWORD)
        await { model.state.value.route == ShellRoute.OnboardingFiles }
        assertTrue(model.back())
        val id =
            library(root)
                .settings()
                .activeInstance()!!
                .id

        model.confirmInstanceSwitch(id)
        await { model.state.value.route == ShellRoute.OnboardingFiles }
        assertTrue(model.back())
        assertEquals(ShellRoute.Settings, model.state.value.route)
        assertEquals(SettingsPage.SERVER, model.state.value.settingsPage)
    }

    @Test
    fun firstRunPickOpensWithoutAsking() {
        transport.files += RemoteFile(fileId = "server-household", groupId = "g-1", name = "Household", encryptKeyId = null)
        transport.downloads["server-household"] = budgetBytes("Household")
        val (library, model) = model("first-pick")
        model.onboardingChooseServer()
        model.onboardingAddress("budget.example.com")
        model.onboardingCheck()
        await { model.state.value.route == ShellRoute.OnboardingSignIn }
        model.onboardingSignIn(FakeTransport.PASSWORD)
        await { model.state.value.route == ShellRoute.OnboardingFiles }
        model.onboardingOpen(model.state.value.remoteFiles.single())
        await {
            model.state.value.shell
                ?.budgetName == "Household"
        }
        assertEquals(listOf("Household"), library.list().map { it.name })
        // The budget just picked offers its experimental features before its home.
        await { model.state.value.route is ShellRoute.ExperimentalOffer }
        assertNull((model.state.value.route as ShellRoute.ExperimentalOffer).current)
    }

    @Test
    fun settingUpAsksEvenWhenTheBudgetAlreadyChose() {
        // The choice syncs with the file, so a budget answered on another phone still asks while setting up.
        transport.files += RemoteFile(fileId = "server-household", groupId = "g-1", name = "Household", encryptKeyId = null)
        transport.downloads["server-household"] =
            budgetBytes("Household") { source, id -> source.setPreference(id, "flags.experimental", "true") }
        val (library, model) = model("first-pick-chosen")
        model.onboardingChooseServer()
        model.onboardingAddress("budget.example.com")
        model.onboardingCheck()
        await { model.state.value.route == ShellRoute.OnboardingSignIn }
        model.onboardingSignIn(FakeTransport.PASSWORD)
        await { model.state.value.route == ShellRoute.OnboardingFiles }
        model.onboardingOpen(
            model.state.value.remoteFiles
                .single(),
        )
        await { model.state.value.route is ShellRoute.ExperimentalOffer }
        assertEquals(true, (model.state.value.route as ShellRoute.ExperimentalOffer).current)
        // Turning them off here saves off on the budget.
        model.declineExperimentalOffer()
        assertEquals(ShellRoute.Home, model.state.value.route)
        await { !library.readFormatPrefs("server-household").experimental }
    }

    @Test
    fun aBareAddressThatOnlyAnswersOverHttpAsksBeforeSigningIn() {
        val httpOnly =
            ProbeHttp { url ->
                if (url.startsWith("https://")) throw IOException("refused")
                readyHttp.get(url)
            }
        val (_, model) = model("http-only", http = httpOnly)
        model.onboardingChooseServer()
        model.onboardingAddress("budget.lan")
        model.onboardingCheck()
        await {
            model.state.value.onboarding
                ?.addressError != null
        }
        val ui = model.state.value.onboarding!!
        assertEquals(OnboardingCopy.HTTP_ONLY, ui.addressError)
        assertEquals("http://budget.lan", ui.progress.addressText)
        assertNull(ui.progress.readyAddress)
        assertEquals(ShellRoute.OnboardingAddress, model.state.value.route)

        // `next` again is the person choosing http://.
        model.onboardingCheck()
        await { model.state.value.route == ShellRoute.OnboardingSignIn }
        assertEquals(
            "http://budget.lan",
            model.state.value.onboarding!!
                .progress.readyAddress,
        )
    }

    /** An Actual file made on another "phone", as the server would hand it back. */
    private fun budgetBytes(
        name: String,
        prepare: (BudgetLibrary, String) -> Unit = { _, _ -> },
    ): ByteArray {
        val source = root("source-$name")
        val made = library(source).create(name, "USD") as app.duenorth.budget.core.CreateResult.Created
        prepare(library(source), made.id)
        return File(source, "${made.id}/db.sqlite").readBytes()
    }

    private fun model(
        name: String,
        http: ProbeHttp = readyHttp,
    ): Pair<BudgetLibrary, ShellViewModel> {
        val root = root(name)
        return library(root) to model(root, http)
    }

    private fun model(
        root: File,
        http: ProbeHttp = readyHttp,
    ): ShellViewModel {
        val library = library(root)
        val sync = SyncCoordinator(library, transport, MemorySecretStore(), clock)
        val model = ShellViewModel(library, sync, probe = ServerProbe(http))
        await { !model.state.value.loading }
        return model
    }

    private fun root(name: String): File =
        File(RuntimeEnvironment.getApplication().filesDir, "onboarding-$name").apply {
            deleteRecursively()
            mkdirs()
        }

    private fun library(root: File) = BudgetLibrary(root, AndroidSessionOpener(), clock)

    private val clock = BudgetClock { LocalDate.of(2026, 10, 14) }

    private val readyHttp =
        ProbeHttp { url ->
            if (url.endsWith("/account/needs-bootstrap")) {
                ProbeResponse(200, """{"status":"ok","data":{"bootstrapped":true,"loginMethod":"password"}}""")
            } else {
                ProbeResponse(404, "")
            }
        }

    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!predicate()) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            check(System.nanoTime() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private class FakeTransport : ActualTransport {
        val logins = mutableListOf<String>()
        val files = mutableListOf<RemoteFile>()
        val downloads = mutableMapOf<String, ByteArray>()
        val uploads = java.util.Collections.synchronizedList(mutableListOf<String>())

        override fun login(
            address: String,
            password: String,
        ): String {
            logins += address
            if (password != PASSWORD) throw SyncAuthException(SyncCopy.SIGN_IN_FAILED)
            return "token"
        }

        override fun loginMethods(address: String): List<String> = listOf("password")

        override fun beginOpenId(
            address: String,
            returnUrl: String,
        ): String = throw IOException("no browser")

        override fun listFiles(
            address: String,
            token: String,
        ): List<RemoteFile> = files.toList()

        override fun download(
            address: String,
            token: String,
            fileId: String,
            onProgress: (received: Long, total: Long) -> Unit,
        ): ByteArray = downloads[fileId] ?: throw IOException("no files")

        override fun upload(
            address: String,
            token: String,
            fileId: String,
            groupId: String?,
            name: String,
            bytes: ByteArray,
        ): String {
            uploads += name
            return "group-1"
        }

        override fun sync(
            address: String,
            token: String,
            body: ByteArray,
        ): ByteArray = throw IOException("offline")

        override fun encryptionKey(
            address: String,
            token: String,
            fileId: String,
        ): KeyMaterial? = null

        companion object {
            const val PASSWORD = "server-secret"
        }
    }
}
