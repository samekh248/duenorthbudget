package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

/** One piece of other people's work the app ships, with the license its full text is under. */
data class OpenSourceNotice(
    val tag: String,
    val name: String,
    val copyright: String,
    val license: String,
    /** The license's full text, under the APK's assets. */
    val asset: String,
)

object LicensesCopy {
    const val TITLE = "open source licenses"
    const val ABOUT = "Due North Budget is built on the work below. Tap one to read its license."
    const val LINK = "open source licenses"
    const val LINK_HINT = "the code and fonts this app is built on"
    const val ABOUT_HEADING = "about"
    const val UNOFFICIAL =
        "Due North Budget is an independent app for Actual Budget files and servers. It is not made or endorsed by " +
            "the Actual Budget project, or by Microsoft."

    private const val APACHE = "Apache License 2.0"
    private const val APACHE_TEXT = "licenses/apache_2.0.txt"

    /** Everything in a release build that is not this app's own: the release runtime classpath, the font, and Actual. */
    val notices =
        listOf(
            OpenSourceNotice(
                "actual",
                "Actual Budget",
                "Copyright James Long",
                "MIT License",
                "licenses/actual_MIT.txt",
            ),
            OpenSourceNotice(
                "selawik",
                "Selawik font",
                "Copyright 2015 Microsoft Corporation, with Reserved Font Name Selawik",
                "SIL Open Font License 1.1",
                "licenses/selawik_OFL.txt",
            ),
            OpenSourceNotice(
                "androidx",
                "Android Jetpack: Compose, Activity, Lifecycle, WorkManager, Browser, Security, Profile Installer",
                "Copyright The Android Open Source Project",
                APACHE,
                APACHE_TEXT,
            ),
            OpenSourceNotice(
                "kotlin",
                "Kotlin, kotlinx.coroutines, kotlinx.serialization",
                "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors",
                APACHE,
                APACHE_TEXT,
            ),
            OpenSourceNotice("tink", "Tink", "Copyright Google LLC", APACHE, APACHE_TEXT),
            OpenSourceNotice("guava", "Guava ListenableFuture", "Copyright The Guava Authors", APACHE, APACHE_TEXT),
            OpenSourceNotice("jspecify", "JSpecify", "Copyright The JSpecify Authors", APACHE, APACHE_TEXT),
        )
}

/** Settings, general, open source licenses: who made what the app ships, each license a tap away. */
@Composable
fun LicensesScreen(
    modifier: Modifier = Modifier,
    /** Reads a license's full text; the app reads the APK's assets, tests can hand in their own. */
    readText: (String) -> String = rememberAssetReader(),
) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(MetroDimens.Gutter)
            .testTag("licenses"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText(LicensesCopy.TITLE, Metro.typography.header)
        MetroText(LicensesCopy.ABOUT, Metro.typography.body)
        LicensesCopy.notices.forEach { notice ->
            val shown = open == notice.tag
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MetroDimens.TouchTarget)
                    .metroPress { open = if (shown) null else notice.tag }
                    .testTag("license-${notice.tag}"),
            ) {
                MetroText(notice.name, Metro.typography.subheader)
                MetroText(notice.copyright, Metro.typography.caption, color = Metro.colors.secondary)
                MetroText(notice.license, Metro.typography.caption, color = Metro.accent.text)
            }
            if (shown) {
                MetroText(
                    remember(notice.asset) { readText(notice.asset) },
                    Metro.typography.caption,
                    Modifier.testTag("license-${notice.tag}-text"),
                    color = Metro.colors.secondary,
                )
            }
        }
        MetroText(LicensesCopy.UNOFFICIAL, Metro.typography.caption, color = Metro.colors.secondary)
    }
}

@Composable
private fun rememberAssetReader(): (String) -> String {
    val assets = LocalContext.current.assets
    return remember(assets) {
        { path -> runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrDefault("") }
    }
}
