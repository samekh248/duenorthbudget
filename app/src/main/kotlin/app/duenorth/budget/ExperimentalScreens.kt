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
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroSegmentedToggle
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

/**
 * The features behind `flags.experimental`, each with its own Actual flag. All of them are
 * per budget: the flags are budget preferences, so they follow the file to every device.
 */
enum class ExperimentalFeature(
    val label: String,
    val hint: String,
    val where: String,
    val tag: String,
    /** The Actual budget preference that holds this feature's flag. */
    val preference: String,
) {
    TEMPLATES(
        "budget templates",
        "fill each category from a goal you set on it, with a goal bar on every budget row",
        "a category's page → add template · budget edit ••• → templates",
        "templates",
        "flags.goalTemplatesEnabled",
    ),
    ;

    fun isOn(prefs: FormatPrefs): Boolean =
        when (this) {
            TEMPLATES -> prefs.goalTemplates
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
    const val NEEDS_MASTER = "turn on experimental features to use this"
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
        MetroButtonGrid(Modifier.padding(top = MetroDimens.Row)) {
            val on = if (current == true) ExperimentalCopy.KEEP_ON else ExperimentalCopy.TURN_ON
            val off = if (current == true) ExperimentalCopy.TURN_OFF else ExperimentalCopy.KEEP_OFF
            MetroButton(on, Modifier.testTag("experimental-offer-on"), onClick = onTurnOn)
            MetroButton(off, Modifier.testTag("experimental-offer-off"), onClick = onKeepOff)
        }
        MetroText(ExperimentalCopy.LATER, Metro.typography.caption, color = Metro.colors.secondary)
    }
}

/**
 * Settings → experimental: the master switch, then each feature with what it does and where it
 * shows up. Features stay listed while the switch is off, so the item says what turning it on
 * would bring.
 */
@Composable
internal fun ExperimentalItem(
    prefs: FormatPrefs,
    onExperimental: (Boolean) -> Unit,
    onFeature: (ExperimentalFeature, Boolean) -> Unit,
) {
    Column(
        Modifier.testTag("experimental-screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SwitchRow(
            label = ExperimentalCopy.TITLE,
            on = prefs.experimental,
            tag = "experimental-master",
            onChange = onExperimental,
        )
        MetroText(ExperimentalCopy.ABOUT, Metro.typography.body)
        MetroText(ExperimentalCopy.PER_BUDGET, Metro.typography.body)
        Heading(ExperimentalCopy.FEATURES)
        ExperimentalFeature.entries.forEach { feature ->
            FeatureRow(feature, prefs, onFeature)
        }
    }
}

@Composable
private fun FeatureRow(
    feature: ExperimentalFeature,
    prefs: FormatPrefs,
    onFeature: (ExperimentalFeature, Boolean) -> Unit,
) {
    val usable = prefs.experimental
    val ink = if (usable) Metro.colors.foreground else Metro.colors.secondary
    Column(
        Modifier.fillMaxWidth().padding(vertical = MetroDimens.Row).testTag("experimental-${feature.tag}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (usable) {
            SwitchRow(feature.label, feature.isOn(prefs), "experimental-${feature.tag}-switch") { onFeature(feature, it) }
        } else {
            MetroText(feature.label, Metro.typography.subheader, color = ink)
        }
        MetroText(feature.hint, Metro.typography.body, color = ink)
        MetroText(
            if (usable) feature.where else ExperimentalCopy.NEEDS_MASTER,
            Metro.typography.caption,
            Modifier.testTag("experimental-${feature.tag}-note"),
            color = if (usable) Metro.colors.secondary else Metro.accent.text,
        )
    }
}

/** A name with an off/on pair at the row's end, the switch the person flips. */
@Composable
private fun SwitchRow(
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
    MetroText(
        text,
        Metro.typography.groupHeader,
        Modifier.padding(top = MetroDimens.Gutter),
        color = Metro.colors.secondary,
    )
}
