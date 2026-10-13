package app.duenorth.budget.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duenorth.budget.design.PanoramaMotion
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroFonts
import app.duenorth.budget.design.theme.MetroWeights
import kotlinx.coroutines.launch

@Composable
fun MetroText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Metro.colors.foreground,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = if (softWrap) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

@Composable
fun Modifier.metroPress(onClick: () -> Unit): Modifier {
    val animations = Metro.animations
    val pressedColor = if (Metro.colors.isDark) Color(0x73FFFFFF) else Color(0x40000000)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    return this
        .clickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        ).graphicsLayer {
            rotationZ = if (pressed && animations) 2.5f else 0f
        }.background(if (pressed) pressedColor else Color.Transparent)
}

@Composable
fun MetroField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    conceal: Boolean = false,
) {
    val colors = Metro.colors
    val field = if (colors.isDark) Color.White else Color(0xFFBFBFBF)
    val ink = Color.Black
    val hintColor = if (colors.isDark) Color(0xFF909090) else Color(0xFF8F8F8F)
    Box(
        modifier
            .fillMaxWidth()
            .background(field)
            .heightIn(min = MetroDimens.TouchTarget)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            MetroText(hint, Metro.typography.control, color = hintColor)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = Metro.typography.control.copy(color = ink),
            cursorBrush = SolidColor(Metro.accent.fill),
            visualTransformation = if (conceal) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun MetroButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    MetroActionButton(label, modifier.fillMaxWidth(), onClick)
}

/** Compact outlined action from the budget mockups (previous / hold / new budget). */
@Composable
fun MetroOutlineButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    MetroActionButton(label, modifier, onClick)
}

@Composable
private fun MetroActionButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = Metro.colors
    val accent = Metro.accent
    val animations = Metro.animations
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .heightIn(min = MetroDimens.ButtonMin)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ).graphicsLayer { rotationZ = if (pressed && animations) 2.5f else 0f }
            .border(MetroDimens.Stroke, colors.foreground)
            .background(if (pressed) accent.fill else Color.Transparent)
            .padding(horizontal = 9.5.dp, vertical = 3.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(
            label,
            Metro.typography.body.copy(fontWeight = MetroWeights.Semibold),
            color = if (pressed) Color.White else colors.foreground,
        )
    }
}

enum class AppGlyph {
    Budgets,
    Appearance,
    Sync,
    Payees,
    Hold,
    Categories,
    Add,
    Settings,
    Transfer,
    Reconcile,
    Import,
    Save,
    Cancel,
    ChevronLeft,
    ChevronRight,
    ChevronDown,
    Calendar,
    Breakdown,
    Copy,
    Average,
    Zero,
    Undo,
}

@Immutable
data class ScreenAction(
    val id: String,
    val glyph: AppGlyph,
    val label: String,
    val primary: Boolean = true,
    val run: () -> Unit,
)

@Immutable
data class AppBarButton(
    val glyph: AppGlyph,
    val label: String,
    val onClick: () -> Unit,
    val id: String = label,
)

@Composable
fun MetroAppBar(
    actions: List<ScreenAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Metro.colors
    val primary = actions.filter { it.primary }
    val secondary = actions.filter { !it.primary }
    Column(modifier.fillMaxWidth().background(colors.chrome)) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MetroDimens.AppBar),
        ) {
            Row(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(end = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(MetroDimens.ControlGap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                primary.forEach { action ->
                    AppBarIcon(action, expanded)
                }
            }
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(width = 56.dp, height = MetroDimens.TouchTarget)
                    .testTag("app-bar-ellipsis")
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = { onExpandedChange(!expanded) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                MetroText("•••", EllipsisLabel, color = colors.foreground)
            }
        }
        if (expanded && secondary.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MetroDimens.Gutter, vertical = MetroDimens.Gutter),
            ) {
                secondary.forEach { action ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = MetroDimens.TouchTarget)
                            .metroPress(action.run)
                            .testTag("app-bar-menu-${action.id}")
                            .padding(horizontal = MetroDimens.Gutter),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppGlyphIcon(action.glyph)
                            MetroText(action.label, Metro.typography.body)
                        }
                    }
                }
            }
        }
    }
}

private val AppBarLabel =
    TextStyle(
        fontFamily = MetroFonts.family,
        fontWeight = MetroWeights.Regular,
        fontSize = 9.5.sp,
    )

private val EllipsisLabel =
    TextStyle(
        fontFamily = MetroFonts.family,
        fontWeight = MetroWeights.Semilight,
        fontSize = 20.sp,
    )

@Composable
private fun AppBarIcon(
    action: ScreenAction,
    expanded: Boolean,
) {
    val colors = Metro.colors
    val animations = Metro.animations
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(MetroDimens.TouchTarget)
                .testTag("app-bar-${action.id}")
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = action.run,
                ).graphicsLayer { rotationZ = if (pressed && animations) 2.5f else 0f },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(MetroDimens.AppBarIcon)
                    .border(MetroDimens.Stroke, colors.foreground, CircleShape)
                    .background(if (pressed) colors.foreground else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AppGlyphIcon(action.glyph, if (pressed) colors.background else colors.foreground)
            }
        }
        if (expanded) {
            MetroText(action.label, AppBarLabel, color = colors.foreground)
        }
    }
}

fun List<AppBarButton>.asScreenActions(): List<ScreenAction> =
    map { ScreenAction(id = it.id, glyph = it.glyph, label = it.label, primary = true, run = it.onClick) }

object AppGlyphInk {
    fun strokeCount(glyph: AppGlyph): Int =
        when (glyph) {
            AppGlyph.Budgets -> 3
            AppGlyph.Appearance -> 3
            AppGlyph.Sync -> 6
            AppGlyph.Payees -> 2
            AppGlyph.Hold -> 2
            AppGlyph.Categories -> 3
            AppGlyph.Add -> 2
            AppGlyph.Settings -> 2
            AppGlyph.Transfer -> 4
            AppGlyph.Reconcile -> 2
            AppGlyph.Import -> 3
            AppGlyph.Save -> 2
            AppGlyph.Cancel -> 2
            AppGlyph.ChevronLeft -> 2
            AppGlyph.ChevronRight -> 2
            AppGlyph.ChevronDown -> 2
            AppGlyph.Calendar -> 3
            AppGlyph.Breakdown -> 4
            AppGlyph.Copy -> 2
            AppGlyph.Average -> 3
            AppGlyph.Zero -> 1
            AppGlyph.Undo -> 3
        }

    fun DrawScope.draw(
        glyph: AppGlyph,
        color: Color,
    ) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Square)
        val w = size.width
        val h = size.height
        when (glyph) {
            AppGlyph.Budgets -> {
                drawRect(
                    color,
                    topLeft = Offset(3.dp.toPx(), 6.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(16.dp.toPx(), 13.dp.toPx()),
                    style = stroke,
                )
                drawLine(color, Offset(3.dp.toPx(), 6.dp.toPx()), Offset(8.dp.toPx(), 3.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(8.dp.toPx(), 3.dp.toPx()), Offset(14.dp.toPx(), 6.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Appearance -> {
                drawCircle(color, radius = 5.dp.toPx(), style = stroke)
                drawLine(color, Offset(w / 2, 1.dp.toPx()), Offset(w / 2, 4.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(w / 2, h - 1.dp.toPx()), Offset(w / 2, h - 4.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Sync -> {
                drawLine(color, Offset(4.dp.toPx(), 7.dp.toPx()), Offset(16.dp.toPx(), 7.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(13.dp.toPx(), 4.dp.toPx()), Offset(17.dp.toPx(), 7.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(13.dp.toPx(), 10.dp.toPx()), Offset(17.dp.toPx(), 7.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(18.dp.toPx(), 15.dp.toPx()), Offset(6.dp.toPx(), 15.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(9.dp.toPx(), 12.dp.toPx()), Offset(5.dp.toPx(), 15.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(9.dp.toPx(), 18.dp.toPx()), Offset(5.dp.toPx(), 15.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Payees -> {
                drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(w / 2, 7.dp.toPx()), style = stroke)
                drawLine(color, Offset(5.dp.toPx(), 18.dp.toPx()), Offset(17.dp.toPx(), 18.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Hold -> {
                drawLine(color, Offset(7.dp.toPx(), 5.dp.toPx()), Offset(7.dp.toPx(), 17.dp.toPx()), strokeWidth = 2.2.dp.toPx())
                drawLine(color, Offset(15.dp.toPx(), 5.dp.toPx()), Offset(15.dp.toPx(), 17.dp.toPx()), strokeWidth = 2.2.dp.toPx())
            }
            AppGlyph.Categories -> {
                drawRect(
                    color,
                    topLeft = Offset(4.dp.toPx(), 5.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(14.dp.toPx(), 12.dp.toPx()),
                    style = stroke,
                )
                drawLine(color, Offset(4.dp.toPx(), 9.dp.toPx()), Offset(18.dp.toPx(), 9.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(4.dp.toPx(), 13.dp.toPx()), Offset(18.dp.toPx(), 13.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Add -> {
                drawLine(color, Offset(w / 2, 4.dp.toPx()), Offset(w / 2, 18.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(4.dp.toPx(), h / 2), Offset(18.dp.toPx(), h / 2), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.Settings -> {
                drawCircle(color, radius = 3.dp.toPx(), style = stroke)
                drawCircle(color, radius = 8.dp.toPx(), style = stroke)
            }
            AppGlyph.Transfer -> {
                drawLine(color, Offset(4.dp.toPx(), 8.dp.toPx()), Offset(16.dp.toPx(), 8.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(13.dp.toPx(), 5.dp.toPx()), Offset(17.dp.toPx(), 8.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(18.dp.toPx(), 14.dp.toPx()), Offset(6.dp.toPx(), 14.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(9.dp.toPx(), 17.dp.toPx()), Offset(5.dp.toPx(), 14.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Reconcile -> {
                drawLine(color, Offset(5.dp.toPx(), 11.dp.toPx()), Offset(9.dp.toPx(), 16.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(9.dp.toPx(), 16.dp.toPx()), Offset(17.dp.toPx(), 6.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.Import -> {
                drawLine(color, Offset(w / 2, 4.dp.toPx()), Offset(w / 2, 14.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(7.dp.toPx(), 11.dp.toPx()), Offset(w / 2, 15.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(15.dp.toPx(), 11.dp.toPx()), Offset(w / 2, 15.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Save -> {
                drawLine(color, Offset(5.dp.toPx(), 11.dp.toPx()), Offset(9.dp.toPx(), 16.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(9.dp.toPx(), 16.dp.toPx()), Offset(17.dp.toPx(), 6.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.Cancel -> {
                drawLine(color, Offset(6.dp.toPx(), 6.dp.toPx()), Offset(16.dp.toPx(), 16.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(16.dp.toPx(), 6.dp.toPx()), Offset(6.dp.toPx(), 16.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.ChevronLeft -> {
                drawLine(color, Offset(13.dp.toPx(), 5.dp.toPx()), Offset(7.dp.toPx(), 11.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(7.dp.toPx(), 11.dp.toPx()), Offset(13.dp.toPx(), 17.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.ChevronRight -> {
                drawLine(color, Offset(9.dp.toPx(), 5.dp.toPx()), Offset(15.dp.toPx(), 11.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(15.dp.toPx(), 11.dp.toPx()), Offset(9.dp.toPx(), 17.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.ChevronDown -> {
                drawLine(color, Offset(5.dp.toPx(), 8.dp.toPx()), Offset(11.dp.toPx(), 14.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(11.dp.toPx(), 14.dp.toPx()), Offset(17.dp.toPx(), 8.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.Calendar -> {
                drawRect(
                    color,
                    topLeft = Offset(4.dp.toPx(), 6.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(14.dp.toPx(), 12.dp.toPx()),
                    style = stroke,
                )
                drawLine(color, Offset(4.dp.toPx(), 10.dp.toPx()), Offset(18.dp.toPx(), 10.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(8.dp.toPx(), 4.dp.toPx()), Offset(8.dp.toPx(), 8.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Breakdown -> {
                drawLine(color, Offset(4.dp.toPx(), 5.dp.toPx()), Offset(18.dp.toPx(), 5.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(4.dp.toPx(), 9.dp.toPx()), Offset(14.dp.toPx(), 9.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(4.dp.toPx(), 13.dp.toPx()), Offset(16.dp.toPx(), 13.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(4.dp.toPx(), 18.dp.toPx()), Offset(18.dp.toPx(), 18.dp.toPx()), strokeWidth = 2.2.dp.toPx())
            }
            AppGlyph.Copy -> {
                drawRect(
                    color,
                    topLeft = Offset(4.dp.toPx(), 7.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(11.dp.toPx(), 11.dp.toPx()),
                    style = stroke,
                )
                drawRect(
                    color,
                    topLeft = Offset(8.dp.toPx(), 3.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(11.dp.toPx(), 11.dp.toPx()),
                    style = stroke,
                )
            }
            AppGlyph.Average -> {
                drawLine(color, Offset(4.dp.toPx(), 15.dp.toPx()), Offset(4.dp.toPx(), 18.dp.toPx()), strokeWidth = 2.2.dp.toPx())
                drawLine(color, Offset(11.dp.toPx(), 9.dp.toPx()), Offset(11.dp.toPx(), 18.dp.toPx()), strokeWidth = 2.2.dp.toPx())
                drawLine(color, Offset(3.dp.toPx(), 12.dp.toPx()), Offset(19.dp.toPx(), 12.dp.toPx()), strokeWidth = 1.2.dp.toPx())
            }
            AppGlyph.Zero -> {
                drawOval(
                    color,
                    topLeft = Offset(6.dp.toPx(), 4.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(10.dp.toPx(), 14.dp.toPx()),
                    style = stroke,
                )
            }
            AppGlyph.Undo -> {
                drawLine(color, Offset(5.dp.toPx(), 9.dp.toPx()), Offset(15.dp.toPx(), 9.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(5.dp.toPx(), 9.dp.toPx()), Offset(9.dp.toPx(), 5.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawArc(
                    color,
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(11.dp.toPx(), 9.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(8.dp.toPx(), 8.dp.toPx()),
                    style = stroke,
                )
            }
        }
    }
}

@Composable
fun AppGlyphIcon(
    glyph: AppGlyph,
    color: Color = Metro.colors.foreground,
) {
    Canvas(Modifier.size(22.dp)) {
        with(AppGlyphInk) { draw(glyph, color) }
    }
}

/** Draws this child higher by [distance] and gives that space back to the parent. */
private fun Modifier.pullUp(distance: Dp): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val dy = distance.roundToPx()
        val height = (placeable.height - dy).coerceAtLeast(0)
        layout(placeable.width, height) {
            placeable.placeRelative(0, -dy)
        }
    }

@Immutable
class PanoramaSection(
    val header: String,
    val headerBlock: (@Composable (onActivate: () -> Unit) -> Unit)? = null,
    val content: @Composable () -> Unit,
)

@Composable
fun MetroPanorama(
    title: String,
    sections: List<PanoramaSection>,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    initialSection: Int = 0,
    onGesture: (Boolean) -> Unit = {},
) {
    val count = sections.size
    val loops = 200
    val origin = remember(count) { (loops / 2) * count }
    val pager = rememberPagerState(initialPage = origin + initialSection) { loops * count }
    val scope = rememberCoroutineScope()
    val type = Metro.typography
    val colors = Metro.colors
    val animations = Metro.animations
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val stepPx =
        remember(type.logoSubtitle, density) {
            measurer
                .measure(text = "due", style = type.logoSubtitle)
                .size.width
                .toFloat()
        }
    var titleBlockWidth by remember { mutableIntStateOf(0) }
    var titleScale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.collect { onGesture(it) }
    }

    fun sectionOf(page: Int): Int = Math.floorMod(page - origin, count)

    fun goTo(index: Int) {
        scope.launch {
            val current = sectionOf(pager.currentPage)
            var delta = index - current
            if (delta > count / 2) delta -= count
            if (delta < -count / 2) delta += count
            val target = pager.currentPage + delta
            if (animations) {
                pager.animateScrollToPage(target)
            } else {
                pager.scrollToPage(target)
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("panorama"),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val viewport = constraints.maxWidth.toFloat()
            val inset = stepPx.coerceAtLeast(0f)
            val available = (viewport - inset).coerceAtLeast(1f)
            val measured = titleBlockWidth.toFloat()
            titleScale =
                if (measured > available && measured > 0f) {
                    (available / measured).coerceIn(0.35f, 1f)
                } else {
                    1f
                }
            val pagePosition = (pager.currentPage - origin) + pager.currentPageOffsetFraction
            val shift =
                PanoramaMotion.titleShiftPx(
                    pagePosition = pagePosition,
                    sectionCount = count,
                    stepPx = stepPx,
                    motionEnabled = animations,
                )
            // One logo: the title and "due north" stay locked and shift together.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = with(density) { inset.toDp() }.coerceAtLeast(MetroDimens.Gutter),
                        top = 8.dp,
                        end = MetroDimens.Gutter,
                    ).onGloballyPositioned { titleBlockWidth = it.size.width }
                    .graphicsLayer {
                        translationX = shift
                        scaleX = titleScale
                        scaleY = titleScale
                        transformOrigin =
                            androidx.compose.ui.graphics
                                .TransformOrigin(0f, 0f)
                    },
            ) {
                MetroText(
                    title,
                    type.panoramaTitle,
                    Modifier.testTag("panorama-title"),
                    maxLines = 1,
                    softWrap = false,
                )
                if (subtitle.isNotEmpty()) {
                    MetroText(
                        subtitle,
                        type.logoSubtitle,
                        Modifier
                            .pullUp(40.dp)
                            .padding(start = 16.dp)
                            .testTag("panorama-subtitle"),
                        color = colors.secondary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(end = if (count > 1) MetroDimens.Peek else 0.dp),
            beyondViewportPageCount = 1,
            userScrollEnabled = count > 1,
        ) { page ->
            val index = sectionOf(page)
            val section = sections[index]
            Column(Modifier.fillMaxSize()) {
                val block = section.headerBlock
                if (block != null) {
                    block { goTo(index) }
                } else {
                    MetroText(
                        section.header,
                        type.sectionHeader,
                        Modifier
                            .padding(start = MetroDimens.Gutter, bottom = MetroDimens.Grid)
                            .semantics { heading() }
                            .metroPress { goTo(index) }
                            .heightIn(min = MetroDimens.TouchTarget),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    section.content()
                }
            }
        }
    }
}
