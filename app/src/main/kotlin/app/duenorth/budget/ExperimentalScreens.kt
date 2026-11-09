package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.PhoneSettings
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroSectionHeading
import app.duenorth.budget.design.components.MetroSegmentedToggle
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.components.SectionSize
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

/**
 * The features behind `flags.experimental`. Most have their own Actual flag, so they are per
 * budget and follow the file to every device; one with no [preference] lives on this phone only.
 */
enum class ExperimentalFeature(
    val label: String,
    val hint: String,
    val where: String,
    val tag: String,
    /** The Actual budget preference that holds this feature's flag; null when it is this phone's setting. */
    val preference: String?,
) {
    TEMPLATES(
        "budget templates",
        "fill each category from a goal you set on it, with a goal bar on every budget row",
        "a category's page ••• → add template · budget edit ••• → templates",
        "templates",
        "flags.goalTemplatesEnabled",
    ),
    AUTOMATIONS(
        "budget automations",
        "edit a category's templates here instead of in its note, as Actual's budget automations do",
        "a category's page ••• → switch to automations · its tiles then edit in place",
        "automations",
        "flags.goalTemplatesUIEnabled",
    ),
    FREE_AFTER_PLANS(
        "free after later plans",
        "under to budget, how much is left once money already budgeted in later months comes out",
        "budget → under to budget · the to budget breakdown · this phone only, nothing is saved to the budget",
        "free-after-plans",
        null,
    ),
    ;

    /** The feature this one sits under, which must be on before it can be: budget automations need budget templates. */
    val parent: ExperimentalFeature?
        get() = if (this == AUTOMATIONS) TEMPLATES else null

    fun isOn(
        prefs: FormatPrefs,
        phone: PhoneSettings,
    ): Boolean =
        when (this) {
            TEMPLATES -> prefs.goalTemplates
            AUTOMATIONS -> prefs.goalTemplatesUI
            FREE_AFTER_PLANS -> phone.freeAfterPlans
        }

    companion object {
        /**
         * What `flags.experimental` should hold: on while any feature is on, off once none are. Settings no
         * longer shows that switch, so it follows the features instead of gating them.
         */
        fun anyOn(
            prefs: FormatPrefs,
            phone: PhoneSettings,
        ): Boolean = entries.any { it.isOn(prefs, phone) }
    }
}

object ExperimentalCopy {
    const val TITLE = "experimental features"
    const val OFFER_TITLE = "try experimental features?"
    const val ABOUT =
        "These are still being built. They can change or go away in an update, and Actual on a " +
            "computer may not show them yet."
    const val PER_BUDGET = "The choice is for this budget only, and follows it to every phone that opens it."
    const val FEATURES = "features"
    const val WHAT_YOU_GET = "what you get"
    const val TURN_ON = "turn on"
    const val KEEP_OFF = "keep off"
    const val KEEP_ON = "keep on"
    const val TURN_OFF = "turn off"
    const val NOW_ON = "This budget already has them on."
    const val NOW_OFF = "This budget has them off."
    const val LATER = "change this any time in settings → experimental"
    const val NEEDS_PARENT = "turn on budget templates to use this"
    const val ON = "on"
    const val OFF = "off"
}

/** Shown once when a budget opens without a choice yet: try the experimental features, or keep them off. */
@Composable
fun ExperimentalOfferScreen(
    budgetName: String,
    onTurnOn: () -> Unit,
    onKeepOff: () -> Unit,
    modifier: Modifier = Modifier,
    /** The budget's choice from before, when it has one; the buttons then say keep or change it. */
    current: Boolean? = null,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(MetroDimens.Gutter)
            .testTag("experimental-offer"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText(ExperimentalCopy.OFFER_TITLE, Metro.typography.header)
        MetroText(budgetName, Metro.typography.subheader, color = Metro.colors.secondary)
        MetroText(ExperimentalCopy.ABOUT, Metro.typography.body)
        MetroText(ExperimentalCopy.PER_BUDGET, Metro.typography.body)
        if (current != null) {
            MetroText(
                if (current) ExperimentalCopy.NOW_ON else ExperimentalCopy.NOW_OFF,
                Metro.typography.body,
                Modifier.testTag("experimental-offer-current"),
                color = Metro.accent.text,
            )
        }
        Heading(ExperimentalCopy.WHAT_YOU_GET)
        ExperimentalFeature.entries.forEach { feature ->
            Column(Modifier.testTag("experimental-offer-${feature.tag}")) {
                MetroText(feature.label, Metro.typography.subheader)
                MetroText(feature.hint, Metro.typography.caption, color = Metro.colors.secondary)
            }
        }
        // The two answers sit on the bar, so they show without scrolling past the explainer.
        val on = if (current == true) ExperimentalCopy.KEEP_ON else ExperimentalCopy.TURN_ON
        val off = if (current == true) ExperimentalCopy.TURN_OFF else ExperimentalCopy.KEEP_OFF
        PageBar(
            listOf(
                ScreenAction("experimental-offer-on", AppGlyph.Save, on, run = onTurnOn),
                ScreenAction("experimental-offer-off", AppGlyph.Cancel, off, run = onKeepOff),
            ),
        )
        MetroText(ExperimentalCopy.LATER, Metro.typography.caption, color = Metro.colors.secondary)
    }
}

/**
 * Settings → experimental: each feature's own switch, with what it does and where it shows up.
 * There is no master switch here; `flags.experimental` is kept on while any feature is on and off
 * once none are (see [ExperimentalFeature.anyOn]).
 */
@Composable
internal fun ExperimentalItem(
    prefs: FormatPrefs,
    onFeature: (ExperimentalFeature, Boolean) -> Unit,
    phone: PhoneSettings = PhoneSettings(),
) {
    Column(
        Modifier.testTag("experimental-screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText(ExperimentalCopy.ABOUT, Metro.typography.body)
        MetroText(ExperimentalCopy.PER_BUDGET, Metro.typography.body)
        Heading(ExperimentalCopy.FEATURES)
        ExperimentalFeature.entries.forEach { feature ->
            FeatureRow(feature, prefs, phone, onFeature)
        }
    }
}

@Composable
private fun FeatureRow(
    feature: ExperimentalFeature,
    prefs: FormatPrefs,
    phone: PhoneSettings,
    onFeature: (ExperimentalFeature, Boolean) -> Unit,
) {
    val parentOff = feature.parent?.let { !it.isOn(prefs, phone) } == true
    val usable = !parentOff
    val ink = if (usable) Metro.colors.foreground else Metro.colors.secondary
    Column(
        Modifier.fillMaxWidth().padding(vertical = MetroDimens.Row).testTag("experimental-${feature.tag}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (usable) {
            SwitchRow(feature.label, feature.isOn(prefs, phone), "experimental-${feature.tag}-switch") { onFeature(feature, it) }
        } else {
            MetroText(feature.label, Metro.typography.subheader, color = ink)
        }
        MetroText(feature.hint, Metro.typography.body, color = ink)
        MetroText(
            if (usable) feature.where else ExperimentalCopy.NEEDS_PARENT,
            Metro.typography.caption,
            Modifier.testTag("experimental-${feature.tag}-note"),
            color = if (usable) Metro.colors.secondary else Metro.accent.text,
        )
    }
}

/** A name with an off/on pair at the row's end, the switch the person flips. */
@Composable
internal fun SwitchRow(
    label: String,
    on: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MetroText(label, Metro.typography.subheader, Modifier.weight(1f).padding(end = 12.dp))
        MetroSegmentedToggle(
            first = ExperimentalCopy.OFF,
            second = ExperimentalCopy.ON,
            firstSelected = !on,
            onSelect = { first -> if (first == on) onChange(!first) },
            tag = tag,
        )
    }
}

@Composable
private fun Heading(text: String) {
    MetroSectionHeading(text, size = SectionSize.PAGE)
}

/** Words for [ExperimentalFeature.FREE_AFTER_PLANS]: to budget less what later months already hold. */
object FreeAfterPlansCopy {
    const val LABEL = "unassigned"
}

/** Experimental free-after-later-plans: on with this phone's setting, while the budget's `flags.experimental` agrees. */
val ShellUiState.freeAfterPlansOn: Boolean
    get() = formatPrefs.experimental && settings.freeAfterPlans
