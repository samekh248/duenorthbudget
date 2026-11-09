package app.duenorth.budget.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing of the two motions Principle I names: scrolling the budget and swiping the pivot.
 * frameOverrunMs above 0 is a frame that missed its deadline, which is a dropped frame a person can see.
 * Each run restarts the app, so it begins from the top of the list, never from where the last one left off.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun budgetScroll() =
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            // No startup mode: a cold start mode would kill the app between setup and measuring.
            // The setup restarts it itself, so each run still begins fresh at the top of the list.
            startupMode = null,
            iterations = ITERATIONS,
            setupBlock = {
                seedBudget()
                killProcess()
                startActivityAndWait()
                waitForBudget()
                openBudgetList()
            },
        ) {
            flingThrough(budgetList())
        }

    @Test
    fun pivotSwipe() =
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            // No startup mode: a cold start mode would kill the app between setup and measuring.
            // The setup restarts it itself, so each run still begins fresh at the top of the list.
            startupMode = null,
            iterations = ITERATIONS,
            setupBlock = {
                seedBudget()
                killProcess()
                startActivityAndWait()
                waitForBudget()
            },
        ) {
            swipeThroughPivot()
        }
}
