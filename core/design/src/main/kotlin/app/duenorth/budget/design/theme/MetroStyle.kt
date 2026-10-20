package app.duenorth.budget.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The look the person picks under settings, appearance. Metro is the flat Metro 2 look and the default. The
 * other three are Metro with soft relief, chosen by the owner on 2026-10-20 from three mockups. Each keeps
 * Metro's type, pivot, square corners, flat accent fills, round app-bar buttons and grid; only light and shade
 * are added, and each style follows one rule for where they go.
 */
enum class MetroStyle(
    val id: String,
    val label: String,
    /** Category groups and register days lift off the page as square panels (tile relief). */
    val panels: Boolean = false,
    /** Category rows and register days sit in channels cut into the page (carved). */
    val channels: Boolean = false,
    /** To budget is a flat accent tile, held a raised neutral one (tile relief). */
    val heroTiles: Boolean = false,
    /** Add is the one accent button on the app bar (tile relief). */
    val filledAdd: Boolean = false,
    /** Controls are cut into the page and only the chosen part rises (carved); otherwise controls stand up. */
    val carvedControls: Boolean = false,
) {
    Metro("metro", "metro"),
    PressedMetro("pressed-metro", "pressed metro"),
    TileRelief("tile-relief", "tile relief", panels = true, heroTiles = true, filledAdd = true),
    Carved("carved", "carved", channels = true, carvedControls = true),
    ;

    val relief: Boolean get() = this != Metro

    /** The relief of a control at rest: raised, or pressed in under the carved style. */
    val control: Relief get() = if (carvedControls) Relief.Inset else Relief.Raised

    companion object {
        val Default = Metro

        fun fromId(id: String): MetroStyle = entries.firstOrNull { it.id == id } ?: Default
    }
}

/** Raised stands off the page; inset is pressed into it. */
enum class Relief { Raised, Inset }

/**
 * How a relief style lights its surfaces: a [highlight] thrown up and left and a [shade] thrown down and right,
 * [depth] apart and softened by [blur]. Both are translucent, so the same light works on the page, on the app
 * bar's chrome and on an accent fill. Small things (buttons, keys, boxes) use half of each.
 */
@Immutable
data class MetroSurface(
    val style: MetroStyle,
    val highlight: Color,
    val shade: Color,
    val depth: Dp,
    val blur: Dp,
) {
    companion object {
        val Flat = MetroSurface(MetroStyle.Metro, Color.Transparent, Color.Transparent, 0.dp, 0.dp)

        fun of(
            style: MetroStyle,
            dark: Boolean,
        ): MetroSurface =
            if (style == MetroStyle.Metro) {
                Flat
            } else {
                MetroSurface(
                    style,
                    highlight = if (dark) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.95f),
                    shade = if (dark) Color.Black.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.17f),
                    depth = 5.dp,
                    blur = 14.dp,
                )
            }
    }
}

/**
 * The page colors of each relief style: Metro's black and white ink on a light or dark grey, since relief needs
 * a page that is neither white nor black. Metro's own are [MetroColors.Light] and [MetroColors.Dark].
 */
internal object StylePalettes {
    fun colors(
        style: MetroStyle,
        dark: Boolean,
    ): MetroColors {
        val metro = if (dark) MetroColors.Dark else MetroColors.Light
        val (background, chrome) =
            when (style) {
                MetroStyle.Metro -> return metro
                MetroStyle.PressedMetro -> if (dark) 0xFF121212 to 0xFF1F1F1F else 0xFFF2F2F2 to 0xFFE3E3E3
                MetroStyle.TileRelief -> if (dark) 0xFF101010 to 0xFF1F1F1F else 0xFFEEEEEE to 0xFFE3E3E3
                MetroStyle.Carved -> if (dark) 0xFF1B1B1B to 0xFF262626 else 0xFFE4E4E4 to 0xFFD6D6D6
            }
        val page = Color(background)
        return metro.copy(
            background = page,
            chrome = Color(chrome),
            secondary = Contrast.ensure(Color(if (dark) 0xFF9E9E9E else 0xFF636363), page),
            error = Contrast.ensure(metro.error, page),
        )
    }
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
