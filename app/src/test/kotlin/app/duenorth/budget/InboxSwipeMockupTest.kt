package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.CategoryTarget
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.design.components.AppBarButton
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.MetroPivot
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.PivotItem
import app.duenorth.budget.design.components.asScreenActions
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

/**
 * Design mockup for the swipe inbox (mockup A, no new-item bar): swipe right accepts the
 * suggested category, swipe left changes it. Renders numbered frames plus a timing file that a
 * script turns into an animated preview. Mock data only; nothing here is wired to the app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class InboxSwipeMockupTest {
    @Test fun light() = render("light", dark = false)

    @Test fun dark() = render("dark", dark = true)

    private fun render(
        theme: String,
        dark: Boolean,
    ) {
        val dir = File("build/inbox-swipe/$theme").apply { deleteRecursively(); mkdirs() }
        val timing = StringBuilder()
        timeline().forEachIndexed { i, (scene, ms) ->
            val name = "%03d".format(i)
            captureRoboImage("${dir.path}/$name.png") {
                MetroTheme(darkTheme = dark) { SwipeScreen(scene) }
            }
            timing.append("$name.png $ms ${scene.caption}\n")
        }
        File(dir, "timing.txt").writeText(timing.toString())
    }
}

/** What one frame shows: which rows remain, the moving row's offset, and any confirmation. */
private data class Scene(
    val gone: Int = 0,
    val offset: Float = 0f,
    val done: String? = null,
    val collapse: Float = 1f,
    val picker: Boolean = false,
    val caption: String = "",
)

private fun ease(t: Float) = if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2).let { it * it } / 2

private fun accel(t: Float) = t * t

private fun timeline(): List<Pair<Scene, Int>> {
    val out = mutableListOf<Pair<Scene, Int>>()
    val step = 40

    fun tween(
        frames: Int,
        curve: (Float) -> Float,
        scene: (Float) -> Scene,
    ) = (1..frames).forEach { out += scene(curve(it / frames.toFloat())) to step }

    val intro = "first load: the top row shows both swipes"
    out += Scene(caption = intro) to 700
    tween(8, ::ease) { Scene(offset = 0.3f * it, caption = intro) }
    out += Scene(offset = 0.3f, caption = intro) to 450
    tween(8, ::ease) { Scene(offset = 0.3f * (1 - it), caption = intro) }
    out += Scene(caption = intro) to 250
    tween(8, ::ease) { Scene(offset = -0.3f * it, caption = intro) }
    out += Scene(offset = -0.3f, caption = intro) to 450
    tween(8, ::ease) { Scene(offset = -0.3f * (1 - it), caption = intro) }
    out += Scene(caption = intro) to 1000

    val right = "swipe right: accept the suggested category"
    tween(4, ::ease) { Scene(offset = 0.15f * it, caption = right) }
    tween(6, ::accel) { Scene(offset = 0.15f + 0.85f * it, caption = right) }
    out += Scene(done = "Dining", caption = right) to 1100
    tween(6, ::ease) { Scene(done = "Dining", collapse = 1 - it, caption = right) }
    out += Scene(gone = 1, caption = right) to 800

    val left = "swipe left: pick a different category"
    tween(4, ::ease) { Scene(gone = 1, offset = -0.15f * it, caption = left) }
    tween(6, ::accel) { Scene(gone = 1, offset = -0.15f - 0.85f * it, caption = left) }
    out += Scene(gone = 1, offset = -1f, caption = left) to 150
    out += Scene(gone = 1, picker = true, caption = left) to 1400
    out += Scene(gone = 1, done = "Shopping", caption = left) to 1100
    tween(6, ::ease) { Scene(gone = 1, done = "Shopping", collapse = 1 - it, caption = left) }
    out += Scene(gone = 2, caption = left) to 1500
    return out
}

private data class SwipeItem(
    val id: String,
    val date: String,
    val payee: String,
    val amountMinor: Long,
    val account: String,
    val category: String?,
    val note: String = "",
)

private val usd = Currencies.byCode("USD")!!

private val swipeItems =
    listOf(
        SwipeItem("coffee", "oct 15", "Corner Coffee", -575, "Visa", "Dining"),
        SwipeItem("amazon", "oct 15", "Amazon", -6_318, "Visa", "Household", "2 items"),
        SwipeItem("shell", "oct 14", "Shell", -4_210, "Checking", "Gas"),
        SwipeItem("acme", "oct 14", "Acme Payroll", 185_000, "Checking", "Paycheck"),
        SwipeItem("rent", "oct 12", "Riverside Property Management", -120_000, "Checking", "Rent", "October rent"),
        SwipeItem("atm", "oct 11", "(no payee)", -2_000, "Checking", null, "ATM withdrawal"),
        SwipeItem("bus", "oct 10", "Metro Transit", -275, "Visa", "Transit"),
    )

@Composable
private fun SwipeScreen(scene: Scene) {
    Column(Modifier.width(390.dp).height(780.dp).background(Metro.colors.background)) {
        if (scene.picker) {
            val item = swipeItems[scene.gone]
            CategoryPickerScreen(
                target =
                    CategoryTarget(
                        item.id,
                        "visa",
                        item.payee,
                        item.amountMinor,
                        usd,
                        listOf("Dining", "Gas", "Groceries", "Household", "Rent", "Shopping", "Transit")
                            .map { CategoryChoice(it.lowercase(), it, "Living", income = false) },
                    ),
                error = null,
                onPick = {},
            )
            return@Column
        }
        MetroPivot(
            title = SectionTitleCopy.APP,
            items =
                listOf(
                    PivotItem("inbox") { SwipeInbox(scene) },
                    PivotItem("budget") {},
                    PivotItem("accounts") {},
                ),
            modifier = Modifier.weight(1f),
        )
        MetroAppBar(
            actions =
                listOf(
                    AppBarButton(AppGlyph.Add, "add", onClick = {}),
                    AppBarButton(AppGlyph.Import, "fetch", onClick = {}),
                    AppBarButton(AppGlyph.Reconcile, "select", onClick = {}),
                ).asScreenActions(),
            expanded = false,
            onExpandedChange = {},
        )
    }
}

@Composable
private fun SwipeInbox(scene: Scene) {
    // The confirmed row stays in the list until it has collapsed away.
    val rows = swipeItems.drop(scene.gone)
    Column(Modifier.fillMaxSize().clipToBounds().padding(horizontal = MetroDimens.Gutter)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            val left = swipeItems.size - scene.gone - if (scene.done != null) 1 else 0
            BudgetTotal("to categorize", "$left", Metro.accent.text, "l", modifier = Modifier.weight(1f))
            BudgetTotal(
                "spending",
                MoneyFormat.format(rows.drop(if (scene.done != null) 1 else 0).filter { it.amountMinor < 0 }.sumOf { it.amountMinor }, usd),
                Metro.colors.foreground,
                "r",
                alignEnd = true,
                modifier = Modifier.weight(1f),
            )
        }
        Column(Modifier.weight(1f)) {
            var index = 0
            rows.groupBy { it.date }.forEach { (day, dayRows) ->
                val headingGoes = scene.done != null && dayRows.size == 1 && dayRows.first() == rows.first()
                MetroText(
                    day,
                    Metro.typography.caption,
                    Modifier.collapse(if (headingGoes) scene.collapse else 1f).padding(top = 16.dp, bottom = 4.dp),
                    color = Metro.accent.text,
                )
                dayRows.forEach { item ->
                    val top = item == rows.first()
                    SwipeRow(
                        item,
                        banded = ledgerBanded(index++),
                        offset = if (top) scene.offset else 0f,
                        done = if (top) scene.done else null,
                        collapse = if (top) scene.collapse else 1f,
                    )
                }
            }
        }
    }
}

/** Shrinks a slot to [fraction] of its height, for rows leaving the list. */
private fun Modifier.collapse(fraction: Float): Modifier =
    if (fraction >= 1f) {
        this
    } else {
        // Clip only the bottom, so ledger bands still run past the gutter.
        drawWithContent { clipRect(-size.width, 0f, size.width * 2, size.height) { this@drawWithContent.drawContent() } }
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                layout(p.width, (p.height * fraction).roundToInt()) { p.place(0, 0) }
            }
    }

/** Moves content sideways by [fraction] of its width. */
private fun Modifier.slide(fraction: Float): Modifier =
    layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        layout(p.width, p.height) { p.place((p.width * fraction).roundToInt(), 0) }
    }

@Composable
private fun SwipeRow(
    item: SwipeItem,
    banded: Boolean,
    offset: Float,
    done: String?,
    collapse: Float,
) {
    val fill = if (banded) ledgerBand else Color.Transparent
    Box(Modifier.fillMaxWidth().collapse(collapse)) {
        // What the swipe uncovers: accept on the left (row moving right), categorize on the right.
        if (offset != 0f) {
            val accept = offset > 0
            Row(
                Modifier
                    .matchParentSize()
                    .ledgerFill(if (accept) Metro.accent.fill else Metro.colors.secondary),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (accept) Arrangement.Start else Arrangement.End,
            ) {
                val ink = if (accept) Metro.accent.onFill else Color.White
                if (accept) {
                    AppGlyphIcon(AppGlyph.Save, ink)
                    MetroText("accept", Metro.typography.caption, Modifier.padding(start = 8.dp), color = ink)
                } else {
                    MetroText("categorize", Metro.typography.caption, Modifier.padding(end = 8.dp), color = ink)
                    AppGlyphIcon(AppGlyph.Categories, ink)
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .slide(offset)
                .alpha(if (done != null) 0f else 1f)
                .heightIn(min = MetroDimens.TouchTarget)
                .ledgerFill(if (offset != 0f) Metro.colors.background else fill)
                .ledgerFill(if (offset != 0f) fill else Color.Transparent)
                .padding(vertical = 15.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(LEDGER_NAME + LEDGER_FIGURE).padding(end = MetroDimens.Gutter)) {
                    MetroText(item.payee, Metro.typography.body, maxLines = 2)
                    MetroText(
                        listOfNotNull(item.category ?: "no category", item.account, item.note.ifBlank { null }).joinToString(" · "),
                        Metro.typography.caption,
                        color = Metro.colors.secondary,
                        maxLines = 2,
                    )
                }
                MetroText(
                    MoneyFormat.format(item.amountMinor, usd),
                    Metro.typography.body.copy(textAlign = TextAlign.End),
                    Modifier.weight(LEDGER_FIGURE * 2),
                    color = Metro.accent.text,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        if (done != null) {
            // Confirmation: the row's own slot, same size, turns accent with a check and the category it went to.
            Row(
                Modifier
                    .matchParentSize()
                    .ledgerFill(Metro.accent.fill),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppGlyphIcon(AppGlyph.Save, Metro.accent.onFill)
                Column(Modifier.padding(start = MetroDimens.Gutter)) {
                    MetroText("filed to ${done.lowercase()}", Metro.typography.body, color = Metro.accent.onFill)
                    MetroText(item.payee, Metro.typography.caption, color = Metro.accent.onFill.copy(alpha = 0.75f))
                }
            }
        }
    }
}
