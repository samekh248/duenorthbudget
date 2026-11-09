package app.duenorth.budget.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Cold start to the open budget (Principle I: within 1 s on a Pixel 6a class phone).
 * timeToFullDisplayMs ends when the month is read and drawn (ReportBudgetDrawn in the app),
 * timeToInitialDisplayMs at the first frame. Measured without and with the baseline profile.
 */
@RunWith(Parameterized::class)
class StartupBenchmark(
    private val compilation: CompilationMode,
) {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStart() =
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilation,
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = {
                seedBudget()
                // The seed started the process; a cold start begins without one.
                killProcess()
            },
        ) {
            startActivityAndWait()
            waitForBudget()
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes() = listOf(CompilationMode.None(), CompilationMode.Partial(BaselineProfileMode.Require))
    }
}

internal const val ITERATIONS = 10
