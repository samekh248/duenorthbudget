package app.duenorth.budget.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The look the person picks under settings, appearance. Metro is the flat Metro 2 look and the default. The
 * other three are the soft-relief (neumorphic) styles the owner chose on 2026-10-20 from three mockups: each
 * keeps Metro's type, pivot and app bar order, and draws its surfaces with light and shade instead of lines.
 */
enum class MetroStyle(
    val id: String,
    val label: String,
    /** Category groups and register days sit on raised cards. */
    val cards: Boolean = false,
    /** The app bar floats as a raised dock with a margin, instead of running edge to edge. */
    val dock: Boolean = false,
    /** Budget figures (to budget, held, available) sit in pressed-in wells. */
    val wells: Boolean = false,
    /** To budget sits on a raised accent tile. */
    val heroTile: Boolean = false,
    /** App-bar buttons are rounded squares, and add is filled with the accent. */
    val squareButtons: Boolean = false,
    /** Goal progress runs in a pressed-in groove with a lit fill. */
    val grooves: Boolean = false,
) {
    Metro("metro", "metro"),
    SoftClay("soft-clay", "soft clay", cards = true, dock = true, wells = true),
    QuietRelief("quiet-relief", "quiet relief"),
    GlowTiles("glow-tiles", "glow tiles", cards = true, dock = true, heroTile = true, squareButtons = true, grooves = true),
    ;

    val relief: Boolean get() = this != Metro

    companion object {
        val Default = Metro

        fun fromId(id: String): MetroStyle = entries.firstOrNull { it.id == id } ?: Default
    }
}

/**
 * How a soft-relief style lights its surfaces: a [highlight] thrown up and left and a [shade] thrown down and
 * right, [depth] apart and softened by [blur]. Small things (buttons, marks, wells) use half of each.
 */
@Immutable
data class MetroSurface(
    val style: MetroStyle,
    val highlight: Color,
    val shade: Color,
    val depth: Dp,
    val blur: Dp,
    /** Corner of a card; controls round to [controlCorner]. */
    val corner: Dp,
    val controlCorner: Dp,
) {
    companion object {
        val Flat =
            MetroSurface(
                style = MetroStyle.Metro,
                highlight = Color.Transparent,
                shade = Color.Transparent,
                depth = 0.dp,
                blur = 0.dp,
                corner = 0.dp,
                controlCorner = 0.dp,
            )

        fun of(
            style: MetroStyle,
            dark: Boolean,
        ): MetroSurface =
            when (style) {
                MetroStyle.Metro -> Flat
                MetroStyle.SoftClay ->
                    MetroSurface(
                        style,
                        highlight = Color(if (dark) 0xFF383D46 else 0xFFFFFFFF),
                        shade = Color(if (dark) 0xFF181A1F else 0xFFB3BCC9),
                        depth = 6.dp,
                        blur = 14.dp,
                        corner = 22.dp,
                        controlCorner = 26.dp,
                    )
                MetroStyle.QuietRelief ->
                    MetroSurface(
                        style,
                        highlight = Color(if (dark) 0xFF25272B else 0xFFFFFFFF),
                        shade = Color(if (dark) 0xFF08090A else 0xFFCDD2D9),
                        depth = 3.dp,
                        blur = 7.dp,
                        corner = 14.dp,
                        controlCorner = 12.dp,
                    )
                MetroStyle.GlowTiles ->
                    MetroSurface(
                        style,
                        highlight = Color(if (dark) 0xFF283044 else 0xFFF5F8FD),
                        shade = Color(if (dark) 0xFF0E121B else 0xFFA9B4C6),
                        depth = 8.dp,
                        blur = 18.dp,
                        corner = 22.dp,
                        controlCorner = 16.dp,
                    )
            }
    }
}

/** The page colors of each soft-relief style. Metro's own are [MetroColors.Light] and [MetroColors.Dark]. */
internal object StylePalettes {
    fun colors(
        style: MetroStyle,
        dark: Boolean,
    ): MetroColors {
        val metro = if (dark) MetroColors.Dark else MetroColors.Light
        val palette =
            when (style) {
                MetroStyle.Metro -> return metro
                MetroStyle.SoftClay ->
                    if (dark) {
                        Palette(0xFF2A2E35, 0xFFECEEF2, 0xFFA3A9B4, 0xFF22252B, 0xFF4A505B)
                    } else {
                        Palette(0xFFE4E8EE, 0xFF1D2128, 0xFF5B6370, 0xFFD3D9E2, 0xFFB3BCC9)
                    }
                MetroStyle.QuietRelief ->
                    if (dark) {
                        Palette(0xFF17181B, 0xFFF6F6F7, 0xFFA2A6AD, 0xFF222428, 0xFF3A3D42)
                    } else {
                        Palette(0xFFF3F4F6, 0xFF0E0F11, 0xFF5A5F67, 0xFFE4E7EB, 0xFFCDD2D9)
                    }
                MetroStyle.GlowTiles ->
                    if (dark) {
                        Palette(0xFF1B2130, 0xFFEEF2F8, 0xFF9CA8BD, 0xFF151A26, 0xFF3A4458)
                    } else {
                        Palette(0xFFDAE1EB, 0xFF141B27, 0xFF4F5B6F, 0xFFCBD3DF, 0xFFA9B4C6)
                    }
            }
        val background = Color(palette.background)
        return MetroColors(
            isDark = dark,
            background = background,
            foreground = Color(palette.foreground),
            secondary = Color(palette.secondary),
            chrome = Color(palette.chrome),
            outline = Color(palette.outline),
            error = Contrast.ensure(metro.error, background),
        )
    }

    private class Palette(
        val background: Long,
        val foreground: Long,
        val secondary: Long,
        val chrome: Long,
        val outline: Long,
    )
}

/** Moves [text] toward black (on a light page) or white (on a dark one) until it reads at [Contrast.MIN_TEXT]. */
fun Contrast.ensure(
    text: Color,
    background: Color,
): Color {
    if (ratio(text, background) >= MIN_TEXT) return text
    val toward = bestOn(background)
    var step = 0.05f
    while (step < 1f) {
        val moved = lerp(text, toward, step)
        if (ratio(moved, background) >= MIN_TEXT) return moved
        step += 0.05f
    }
    return toward
}
