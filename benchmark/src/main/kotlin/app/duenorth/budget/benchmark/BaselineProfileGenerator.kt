package app.duenorth.budget.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the baseline profile: the classes and methods a cold start, the budget list, and the pivot run,
 * so ART compiles them at install instead of interpreting them on the first launches.
 * Run with ./gradlew :app:generateBaselineProfile; the result lands in app/src/release/generated/baselineProfiles.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
            seedBudget()
            pressHome()
            startActivityAndWait()
            waitForBudget()
            flingThrough(openBudgetList())
            swipeThroughPivot()
        }
}
