package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountRow
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.RuleDraft
import app.duenorth.budget.core.RuleNames
import app.duenorth.budget.core.RuleSummary
import app.duenorth.budget.core.RuleValues
import app.duenorth.budget.core.ScheduleDraft
import app.duenorth.budget.core.ScheduleEnd
import app.duenorth.budget.core.ScheduleRepeat
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec 010 T050: schedule editor, rule list, and rule editor in light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class RuleScreenshotTest {
    private val names =
        RuleNames(
            payees = mapOf("p1" to "Cafe", "p2" to "Corner Shop"),
            accounts = mapOf("a1" to "Checking"),
            categories = mapOf("c1" to "Food", "c2" to "Bills"),
        )
    private val rules =
        listOf(
            RuleSummary("r1", "pre", listOf("imported_description contains CORNER"), listOf("payee set Corner Shop"), true, true),
            RuleSummary("r2", null, listOf("payee is Cafe"), listOf("category set Food"), false, true),
            RuleSummary("r3", "post", listOf("amount lt -100.00"), listOf("notes append-notes  (big)"), false, false),
        )

    @Test
    fun scheduleLight() = snap("schedule_editor_light", dark = false) { SectionTitled("DUE") { Schedule() } }

    @Test
    fun scheduleDark() = snap("schedule_editor_dark", dark = true) { SectionTitled("DUE") { Schedule() } }

    @Test
    fun rulesLight() = snap("rules_list_light", dark = false) { RulesScreen(rules, {}) }

    @Test
    fun rulesDark() = snap("rules_list_dark", dark = true) { RulesScreen(rules, {}) }

    @Test
    fun ruleEditorLight() = snap("rule_editor_light", dark = false) { Editor() }

    @Test
    fun ruleEditorDark() = snap("rule_editor_dark", dark = true) { Editor() }

    @Composable
    private fun Schedule() {
        ScheduleEditorScreen(
            draft =
                ScheduleDraft(
                    id = "s1",
                    name = "Internet",
                    accountId = "a1",
                    payeeName = "Cafe",
                    amountMinor = -6000L,
                    repeat = ScheduleRepeat.MONTHLY,
                    startIso = "2026-10-07",
                    end = ScheduleEnd.AFTER,
                    endOccurrences = 12,
                    postsTransaction = true,
                ),
            accounts = listOf(AccountRow("a1", "Checking", false, false, 0), AccountRow("a2", "Savings", false, false, 0)),
            categories = listOf(CategoryChoice("c1", "Food", "Living", false), CategoryChoice("c2", "Bills", "Living", false)),
            decimals = 2,
            error = null,
            onSave = {},
        )
    }

    @Composable
    private fun Editor() {
        val conditions =
            listOf(
                RuleValues.part("payee", "is", "Cafe", names, 2, action = false)!!,
                RuleValues.part("notes", "matches", "^ref-[0-9]+$", names, 2, action = false)!!.copy(kept = true),
            )
        val actions = listOf(RuleValues.part("category", "set", "Food", names, 2, action = true)!!)
        RuleEditorScreen(RuleDraft("r1", null, "and", conditions, actions), names, 2, null, {})
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
