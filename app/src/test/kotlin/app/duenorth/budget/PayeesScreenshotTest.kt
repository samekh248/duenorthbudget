package app.duenorth.budget

import androidx.compose.runtime.Composable
import app.duenorth.budget.core.PayeeLookalikes
import app.duenorth.budget.core.PayeeRow
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The payees page laid out as the inbox: a look-alike panel, the other suggestions, then the payees in groups. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class PayeesScreenshotTest {
    private val rows =
        listOf(
            PayeeRow("acme", "Acme Payroll", false, 6),
            PayeeRow("amz", "Amazon", false, 14),
            PayeeRow("amzc", "AMAZON.COM", false, 3),
            PayeeRow("amzm", "Amazon Marketplace", false, 2),
            PayeeRow("cafe", "Corner Coffee", false, 9),
            PayeeRow("tstcafe", "TST* Corner Coffee", false, 4),
            PayeeRow("costco", "Costco", false, 5),
            PayeeRow("costco2", "COSTCO #1123", false, 2),
            PayeeRow("gym", "Old Gym", false, 0),
            PayeeRow("rent", "Riverside Property Management", false, 4),
            PayeeRow("shell", "Shell", false, 7),
            PayeeRow("shell2", "SHELL OIL 5123", false, 1),
            PayeeRow("savings", "Transfer: Savings", true, 3),
            PayeeRow("video", "Video Rental", false, 0),
        )

    @Test
    fun payeesLight() = snap("payees_light", dark = false) { Page() }

    @Test
    fun payeesDark() = snap("payees_dark", dark = true) { Page() }

    // Three names in the lead suggestion: each has a check box; with one left out, merge takes the other two.
    @Test
    fun payeesPickedLight() = snap("payees_picked_light", dark = false) { Page(picked = setOf("amz", "amzc")) }

    @Test
    fun payeesPickedDark() = snap("payees_picked_dark", dark = true) { Page(picked = setOf("amz", "amzc")) }

    // The merge page with a new name typed: the box shows under "a new name", and the panel up top follows it.
    @Test
    fun payeesMergeNewLight() =
        snap("payees_merge_new_light", dark = false) { Page(merge = listOf("amz", "amzc", "amzm"), mergeName = "Amazon Shopping") }

    @Test
    fun payeesMergeNewDark() =
        snap("payees_merge_new_dark", dark = true) { Page(merge = listOf("amz", "amzc", "amzm"), mergeName = "Amazon Shopping") }

    // Two payees spelled the same, case aside, are one choice on the merge page, with their counts added.
    @Test
    fun payeesMergeUniqueLight() = snap("payees_merge_unique_light", dark = false) { Page(merge = sameSpelling, payees = withTwins) }

    @Test
    fun payeesMergeUniqueDark() = snap("payees_merge_unique_dark", dark = true) { Page(merge = sameSpelling, payees = withTwins) }

    private val withTwins get() = rows + PayeeRow("amz2", "AMAZON", false, 4) + PayeeRow("amzc2", "amazon.com", false, 1)
    private val sameSpelling = listOf("amz", "amz2", "amzc", "amzc2")

    // Batch mode: a check box on every payee; two checked offers merge, cancel.
    @Test
    fun payeesBatchLight() = snap("payees_batch_light", dark = false) { Page(selection = setOf("costco", "costco2")) }

    @Test
    fun payeesBatchDark() = snap("payees_batch_dark", dark = true) { Page(selection = setOf("costco", "costco2")) }

    // The merge page: pick the name to keep, or type a new one.
    @Test
    fun payeesMergeLight() = snap("payees_merge_light", dark = false) { Page(merge = listOf("amz", "amzc", "amzm")) }

    @Test
    fun payeesMergeDark() = snap("payees_merge_dark", dark = true) { Page(merge = listOf("amz", "amzc", "amzm")) }

    @Composable
    private fun Page(
        selection: Set<String>? = null,
        merge: List<String>? = null,
        picked: Set<String>? = null,
        mergeName: String? = null,
        payees: List<PayeeRow> = rows,
    ) {
        PayeesScreen(
            payees = payees,
            error = null,
            mergeTarget = null,
            onRename = { _, _, _, _ -> },
            onConfirmMerge = { _, _ -> },
            onCancelMerge = {},
            lookalikes = PayeeLookalikes.find(payees),
            initialSelection = selection,
            initialMerge = merge,
            initialPicked = picked,
            initialMergeName = mergeName,
        )
    }

    private fun snap(
        name: String,
        dark: Boolean,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) { HostedPage { content() } }
        }
    }
}
