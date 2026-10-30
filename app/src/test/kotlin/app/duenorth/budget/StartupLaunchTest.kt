package app.duenorth.budget

import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.HttpActualTransport
import app.duenorth.budget.core.MemorySecretStore
import app.duenorth.budget.core.OnboardingOrigin
import app.duenorth.budget.core.OnboardingPath
import app.duenorth.budget.core.OnboardingProgress
import app.duenorth.budget.core.OnboardingStep
import app.duenorth.budget.core.SignInMethod
import app.duenorth.budget.core.SyncCoordinator
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/** The real application, with the keystore-backed secret store swapped for memory (Robolectric has no AndroidKeyStore). */
class StartupTestApplication : DueNorthApplication() {
    override fun onCreate() {
        library = BudgetLibrary(File(filesDir, "budgets"), AndroidSessionOpener(), BudgetClock.System)
        sync = SyncCoordinator(library, HttpActualTransport(), MemorySecretStore(), BudgetClock.System)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = StartupTestApplication::class)
class StartupLaunchTest {
    @Test
    fun freshInstallLaunches() {
        launchAndSettle()
    }

    @Test
    fun launchWithABudgetOnThePhone() {
        val app = RuntimeEnvironment.getApplication() as DueNorthApplication
        val model = ShellViewModel(app.library, app.sync, developerMode = true, io = Dispatchers.Main)
        awaitMain { !model.state.value.loading }
        model.budgetFiles.create("Home", "USD")
        awaitMain { model.state.value.shell != null }
        launchAndSettle()
    }

    @Test
    fun launchWithAnAddServerPathLeftOpenInSettings() {
        for (step in listOf(OnboardingStep.ADDRESS, OnboardingStep.SIGN_IN, OnboardingStep.FILES)) {
            val app = RuntimeEnvironment.getApplication() as DueNorthApplication
            val model = ShellViewModel(app.library, app.sync, developerMode = true, io = Dispatchers.Main)
            awaitMain { !model.state.value.loading }
            if (model.state.value.shell == null) {
                model.budgetFiles.create("Home", "USD")
                awaitMain { model.state.value.shell != null }
            }
            app.library.onboarding().write(
                OnboardingProgress(
                    step = step,
                    path = OnboardingPath.SERVER,
                    addressText = "https://budget.example.com",
                    readyAddress = if (step == OnboardingStep.ADDRESS) null else "https://budget.example.com",
                    methods = if (step == OnboardingStep.ADDRESS) emptySet() else setOf(SignInMethod.PASSWORD),
                    origin = OnboardingOrigin.SETTINGS,
                ),
            )
            println("launching with settings-origin step $step")
            launchAndSettle()
        }
    }

    private fun launchAndSettle() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            repeat(50) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20)
            }
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
