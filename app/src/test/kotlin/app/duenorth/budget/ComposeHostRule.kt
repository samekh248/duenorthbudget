package app.duenorth.budget

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule

/**
 * Hosts Compose without a launcher filter on [ComponentActivity].
 * The stock rule launches that activity as MAIN/LAUNCHER, which would make a
 * debug install open an empty window after the splash.
 */
fun createHostComposeRule(): ComposeContentTestRule {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val scenarioRule = ActivityScenarioRule<ComponentActivity>(Intent(context, ComponentActivity::class.java))
    return AndroidComposeTestRule(scenarioRule) { rule ->
        lateinit var activity: ComponentActivity
        rule.scenario.onActivity { activity = it }
        activity
    }
}
