package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import app.duenorth.budget.design.theme.MetroWeights
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

/**
 * Design mockups for the inbox without swipes: the top transaction is featured with accept and
 * categorize buttons, the rest wait below, and the app bar gains select for multiselect. Three
 * ideas: a panel, an accent tile, and large type over a compact queue. Renders numbered frames
 * plus a timing file that a script turns into an animated preview. Mock data only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class InboxFeaturedMockupTest {
    @Test fun panelLight() = render(Idea.PANEL, dark = false)

    @Test fun panelDark() = render(Idea.PANEL, dark = true)

    @Test fun tileLight() = render(Idea.TILE, dark = false)

    @Test fun tileDark() = render(Idea.TILE, dark = true)

    @Test fun typeLight() = render(Idea.TYPE, dark = false)

    @Test fun typeDark() = render(Idea.TYPE, dark = true)

    private fun render(
        idea: Idea,
        dark: Boolean,
    ) {
        val dir =
            File("build/inbox-featured/${idea.name.lowercase()}-${if (dark) "dark" else "light"}").apply {
                deleteRecursively()
                mkdirs()
            }
        val timing = StringBuilder()
        timeline().forEachIndexed { i, (scene, ms) ->
            val name = "%03d".format(i)
            captureRoboImage("${dir.path}/$name.png") {
                MetroTheme(darkTheme = dark) { FeaturedScreen(idea, scene) }
            }
            timing.append("$name.png $ms ${scene.caption}\n")
        }
        File(dir, "timing.txt").writeText(timing.toString())
    }
}

private enum class Idea { PANEL, TILE, TYPE }

/** One frame: which item is featured, what is pressed, and how far the next item has risen. */
private data class Scene(
    val first: Int = 0,
    val pressed: String? = null,
    val filed: String? = null,
    val rise: Float = 1f,
    val select: Boolean = false,
    val menu: Boolean = false,
    val caption: String = "",
)

private fun ease(t: Float) = if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2).let { it * it } / 2

private fun timeline(): List<Pair<Scene, Int>> {
    val out = mutableListOf<Pair<Scene, Int>>()

    fun tween(
        frames: Int,
        scene: (Float) -> Scene,
    ) = (1..frames).forEach { out += scene(ease(it / frames.toFloat())) to 40 }

    val intro = "the top transaction is featured, with its own buttons"
    out += Scene(caption = intro) to 1800
    val accept = "accept files it to the suggested category"
    out += Scene(pressed = "accept", caption = accept) to 250
    out += Scene(first = 1, filed = "Dining", rise = 0f, caption = accept) to 900
    val next = "the next transaction moves up into its place"
    tween(12) { Scene(first = 1, filed = "Dining", rise = it, caption = next) }
    out += Scene(first = 1, caption = next) to 1500
    val categorize = "categorize picks a category, then files it"
    out += Scene(first = 1, pressed = "categorize", caption = categorize) to 250
    out += Scene(first = 2, filed = "Household", rise = 0f, caption = categorize) to 900
    tween(12) { Scene(first = 2, filed = "Household", rise = it, caption = next) }
    out += Scene(first = 2, caption = next) to 1300
    val hold = "press and hold any transaction for every action"
    out += Scene(first = 2, menu = true, caption = hold) to 1900
    val select = "select in the app bar picks several at once"
    out += Scene(first = 2, select = true, caption = select) to 2200
    return out
}

private data class Item(
    val date: String,
    val payee: String,
    val amountMinor: Long,
    val account: String,
    val suggestion: String?,
    val note: String = "",
)

private val usd = Currencies.byCode("USD")!!

private val items =
    listOf(
        Item("oct 15", "Corner Coffee", -575, "Visa", "Dining"),
        Item("oct 15", "Amazon", -6_318, "Visa", "Household", "2 items"),
        Item("oct 14", "Shell", -4_210, "Checking", "Gas"),
        Item("oct 14", "Acme Payroll", 185_000, "Checking", "Paycheck"),
        Item("oct 12", "Riverside Property Management", -120_000, "Checking", "Rent", "October rent"),
        Item("oct 11", "(no payee)", -2_000, "Checking", null, "ATM withdrawal"),
        Item("oct 10", "Metro Transit", -275, "Visa", "Transit"),
        Item("oct 9", "Green Grocer", -4_812, "Visa", "Groceries"),
    )

private fun money(minor: Long) = MoneyFormat.format(minor, usd)

private fun detail(item: Item) = listOfNotNull(item.account, item.note.ifBlank { null }).joinToString(" · ")

@Composable
private fun FeaturedScreen(
    idea: Idea,
    scene: Scene,
) {
    Column(Modifier.width(390.dp).height(780.dp).background(Metro.colors.background)) {
        MetroPivot(
            title = SectionTitleCopy.APP,
            items =
                listOf(
                    PivotItem("inbox") { Inbox(idea, scene) },
                    PivotItem("budget") {},
                    PivotItem("accounts") {},
                ),
            modifier = Modifier.weight(1f),
        )
        MetroAppBar(
            actions =
                if (scene.select) {
                    listOf(
                        AppBarButton(AppGlyph.Save, "accept", onClick = {}),
                        AppBarButton(AppGlyph.Categories, "categorize", onClick = {}),
                        AppBarButton(AppGlyph.Cancel, "cancel", onClick = {}),
                    )
                } else {
                    listOf(
                        AppBarButton(AppGlyph.Add, "add", onClick = {}),
                        AppBarButton(AppGlyph.Reconcile, "select", onClick = {}),
                    )
                }.asScreenActions(),
            expanded = false,
            onExpandedChange = {},
        )
    }
}

@Composable
private fun Inbox(
    idea: Idea,
    scene: Scene,
) {
    val left = items.drop(scene.first)
    Column(Modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            BudgetTotal("to categorize", "${left.size}", Metro.accent.text, "l", modifier = Modifier.weight(1f))
            BudgetTotal(
                if (scene.select) "selected" else "spending",
                if (scene.select) "2" else money(left.filter { it.amountMinor < 0 }.sumOf { it.amountMinor }),
                Metro.colors.foreground,
                "r",
                alignEnd = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (scene.select) {
            Column(Modifier.weight(1f).clipToBounds()) {
                left.forEachIndexed { i, item -> SelectRow(item, banded = ledgerBanded(i), checked = i == 0 || i == 2) }
            }
            return@Column
        }
        // Filing: the slot shows the confirmation, then the next item rises into it while the queue loses its head.
        val moving = scene.filed != null
        Featured(idea, left.first(), scene)
        Box(Modifier.weight(1f).clipToBounds()) {
            Column {
                MetroText(
                    "up next",
                    Metro.typography.caption,
                    Modifier.padding(top = if (idea == Idea.TYPE) 20.dp else 16.dp, bottom = 4.dp),
                    color = Metro.colors.secondary,
                )
                if (moving) {
                    Box(Modifier.collapse(1f - scene.rise).alpha(1f - scene.rise)) { QueueRow(idea, left.first(), banded = false) }
                }
                left.drop(1).forEachIndexed { i, item ->
                    QueueRow(idea, item, banded = ledgerBanded(i + if (moving) 1 else 0))
                }
            }
            if (scene.menu) HeldMenu(if (idea == Idea.TYPE) 120.dp else 150.dp)
        }
    }
}

/** Pushes content down by [fraction] of its own height, so it rises into a clipped slot. */
private fun Modifier.rising(fraction: Float): Modifier =
    layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        layout(p.width, p.height) { p.place(0, (p.height * fraction).roundToInt()) }
    }

/** Shrinks a slot to [fraction] of its height, clipping only the bottom so bands still reach the screen edge. */
private fun Modifier.collapse(fraction: Float): Modifier =
    drawWithContent { clipRect(-size.width, 0f, size.width * 2, size.height) { this@drawWithContent.drawContent() } }
        .layout { measurable, constraints ->
            val p = measurable.measure(constraints)
            layout(p.width, (p.height * fraction).roundToInt()) { p.place(0, 0) }
        }

private fun window(
    t: Float,
    from: Float,
    to: Float,
) = ((t - from) / (to - from)).coerceIn(0f, 1f)

/**
 * The featured slot: its background stays put while the content changes. The slot is always sized by
 * the item, so it does not jump; the confirmation lies over it, then the next item rises in.
 */
@Composable
private fun Featured(
    idea: Idea,
    item: Item,
    scene: Scene,
) {
    val filing = scene.filed != null
    val itemAlpha = if (filing) window(scene.rise, 0.35f, 1f) else 1f
    val filedAlpha = if (filing) 1f - window(scene.rise, 0f, 0.4f) else 0f
    val back =
        when (idea) {
            Idea.PANEL -> Metro.colors.chrome
            Idea.TILE -> Metro.accent.fill
            Idea.TYPE -> Color.Transparent
        }
    val ink = if (idea == Idea.TILE) Metro.accent.onFill else Metro.colors.foreground
    Box(
        Modifier
            .fillMaxWidth()
            .ledgerFill(back)
            .padding(vertical = if (idea == Idea.TYPE) 4.dp else 16.dp)
            .drawWithContent { clipRect(-size.width, 0f, size.width * 2, size.height) { this@drawWithContent.drawContent() } },
    ) {
        Box(Modifier.alpha(itemAlpha).rising(if (filing) 1f - window(scene.rise, 0.3f, 1f) else 0f)) {
            when (idea) {
                Idea.PANEL -> PanelFeatured(item, scene)
                Idea.TILE -> TileFeatured(item, scene)
                Idea.TYPE -> TypeFeatured(item, scene)
            }
        }
        if (filing && filedAlpha > 0f) {
            val done = items[items.indexOf(item) - 1]
            Filed(scene.filed!!, done, if (idea == Idea.TYPE) Metro.accent.text else ink, Modifier.matchParentSize().alpha(filedAlpha))
        }
    }
}

/** A: a raised panel in chrome grey, the full screen width, holding the transaction and its buttons. */
@Composable
private fun PanelFeatured(
    item: Item,
    scene: Scene,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = MetroDimens.Gutter)) {
                MetroText(item.date, Metro.typography.caption, color = Metro.accent.text)
                MetroText(item.payee, Metro.typography.groupHeader, maxLines = 2)
                MetroText(detail(item), Metro.typography.caption, color = Metro.colors.secondary)
            }
            MetroText(
                money(item.amountMinor),
                Metro.typography.groupHeader.copy(textAlign = TextAlign.End),
                color = Metro.accent.text,
                maxLines = 1,
                softWrap = false,
            )
        }
        Suggestion(item, Metro.colors.foreground, Metro.colors.secondary)
        Buttons(item, scene, ink = Metro.colors.foreground, fill = Metro.accent.fill, onFill = Metro.accent.onFill)
    }
}

/** B: a solid accent tile, like a live tile, so the eye lands on it first. */
@Composable
private fun TileFeatured(
    item: Item,
    scene: Scene,
) {
    val ink = Metro.accent.onFill
    Column(Modifier.fillMaxWidth()) {
        MetroText("${item.date} · ${detail(item)}", Metro.typography.caption, color = ink.copy(alpha = 0.8f))
        Row(verticalAlignment = Alignment.Bottom) {
            MetroText(
                item.payee,
                Metro.typography.subheader,
                Modifier.weight(1f).padding(end = MetroDimens.Gutter),
                color = ink,
                maxLines = 2,
            )
            MetroText(money(item.amountMinor), Metro.typography.subheader, color = ink, maxLines = 1, softWrap = false)
        }
        Suggestion(item, ink, ink.copy(alpha = 0.8f))
        Buttons(item, scene, ink = ink, fill = ink, onFill = Metro.accent.fill)
    }
}

/** C: no box at all; the transaction is set in large type and the queue below shrinks to one line a row. */
@Composable
private fun TypeFeatured(
    item: Item,
    scene: Scene,
) {
    Column(Modifier.fillMaxWidth()) {
        MetroText(money(item.amountMinor), Metro.typography.header, color = Metro.accent.text, maxLines = 1, softWrap = false)
        MetroText(item.payee, Metro.typography.subheader, maxLines = 2)
        MetroText("${item.date} · ${detail(item)}", Metro.typography.caption, Modifier.padding(top = 2.dp), color = Metro.colors.secondary)
        Suggestion(item, Metro.colors.foreground, Metro.colors.secondary)
        Buttons(item, scene, ink = Metro.colors.foreground, fill = Metro.accent.fill, onFill = Metro.accent.onFill)
    }
}

@Composable
private fun Suggestion(
    item: Item,
    ink: Color,
    dim: Color,
) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        MetroText(if (item.suggestion != null) "suggested " else "no suggestion", Metro.typography.caption, color = dim)
        if (item.suggestion != null) MetroText(item.suggestion, Metro.typography.body.copy(fontWeight = MetroWeights.Semibold), color = ink)
    }
}

/** Accept fills with [fill]; categorize keeps the plain outline. Without a suggestion there is only categorize. */
@Composable
private fun Buttons(
    item: Item,
    scene: Scene,
    ink: Color,
    fill: Color,
    onFill: Color,
) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)) {
        if (item.suggestion != null) {
            MockButton("accept", Modifier.weight(1f), ink, solid = fill, onSolid = onFill, pressed = scene.pressed == "accept")
        }
        MockButton("categorize", Modifier.weight(1f), ink, solid = null, onSolid = onFill, pressed = scene.pressed == "categorize")
        if (item.suggestion == null) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun MockButton(
    label: String,
    modifier: Modifier,
    ink: Color,
    solid: Color?,
    onSolid: Color,
    pressed: Boolean,
) {
    val back =
        if (pressed) {
            Metro.colors.foreground
                .copy(alpha = 0.25f)
                .compositeOver(solid ?: Color.Transparent)
        } else {
            solid
                ?: Color.Transparent
        }
    Box(
        modifier
            .heightIn(min = MetroDimens.ButtonMin)
            .border(MetroDimens.Stroke, if (solid != null) solid else ink)
            .background(back)
            .padding(horizontal = 9.5.dp, vertical = 3.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(label, Metro.typography.body.copy(fontWeight = MetroWeights.Semibold), color = if (solid != null) onSolid else ink)
    }
}

private fun Color.compositeOver(under: Color): Color {
    if (under.alpha == 0f) return this
    val a = alpha
    return Color(red * a + under.red * (1 - a), green * a + under.green * (1 - a), blue * a + under.blue * (1 - a), 1f)
}

/** The featured slot's confirmation, laid over the slot so it is the same size as the item it replaces. */
@Composable
private fun Filed(
    category: String,
    item: Item,
    ink: Color,
    modifier: Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AppGlyphIcon(AppGlyph.Save, ink)
        Column(Modifier.padding(start = MetroDimens.Gutter)) {
            MetroText("filed to ${category.lowercase()}", Metro.typography.groupHeader, color = ink)
            MetroText(item.payee, Metro.typography.caption, color = ink.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun QueueRow(
    idea: Idea,
    item: Item,
    banded: Boolean,
) {
    val fill = if (banded) ledgerBand else Color.Transparent
    if (idea == Idea.TYPE) {
        // One line a row: the queue is there to glance at, the featured item is there to act on.
        Row(
            Modifier.fillMaxWidth().heightIn(min = MetroDimens.TouchTarget).ledgerFill(fill),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetroText(
                item.payee,
                Metro.typography.body,
                Modifier.weight(1f).padding(end = MetroDimens.Gutter),
                maxLines = 1,
                softWrap = false,
            )
            MetroText(
                item.suggestion ?: "no category",
                Metro.typography.caption,
                Modifier.padding(end = MetroDimens.Gutter),
                color = Metro.colors.secondary,
                maxLines = 1,
            )
            MetroText(money(item.amountMinor), Metro.typography.body, color = Metro.colors.secondary, maxLines = 1, softWrap = false)
        }
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .ledgerFill(fill)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(LEDGER_NAME + LEDGER_FIGURE).padding(end = MetroDimens.Gutter)) {
            MetroText(item.payee, Metro.typography.body, maxLines = 1, softWrap = false)
            MetroText(
                listOfNotNull(item.suggestion ?: "no category", item.date, item.account).joinToString(" · "),
                Metro.typography.caption,
                color = Metro.colors.secondary,
                maxLines = 1,
                softWrap = false,
            )
        }
        MetroText(
            money(item.amountMinor),
            Metro.typography.body.copy(textAlign = TextAlign.End),
            Modifier.weight(LEDGER_FIGURE * 2),
            color = Metro.colors.foreground,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun SelectRow(
    item: Item,
    banded: Boolean,
    checked: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .ledgerFill(if (banded) ledgerBand else Color.Transparent)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .border(MetroDimens.Stroke, Metro.colors.foreground)
                .background(if (checked) Metro.accent.fill else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) AppGlyphIcon(AppGlyph.Save, Metro.accent.onFill)
        }
        Column(Modifier.weight(1f).padding(horizontal = MetroDimens.Gutter)) {
            MetroText(item.payee, Metro.typography.body, maxLines = 1, softWrap = false)
            MetroText(
                listOfNotNull(item.suggestion ?: "no category", item.date, item.account).joinToString(" · "),
                Metro.typography.caption,
                color = Metro.colors.secondary,
                maxLines = 1,
            )
        }
        MetroText(money(item.amountMinor), Metro.typography.body, maxLines = 1, softWrap = false)
    }
}

/** The long-press menu as it opens over a queued row (drawn inline here; the app uses a popup). */
@Composable
private fun HeldMenu(top: Dp) {
    Column(
        Modifier
            .padding(top = top)
            .fillMaxWidth()
            .ledgerFill(Metro.colors.chrome)
            .padding(vertical = 8.dp),
    ) {
        listOf("accept", "categorize", "edit", "split", "delete").forEach {
            MetroText(
                it,
                Metro.typography.subheader,
                Modifier.heightIn(min = 44.dp).padding(start = 12.dp, top = 8.dp),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
