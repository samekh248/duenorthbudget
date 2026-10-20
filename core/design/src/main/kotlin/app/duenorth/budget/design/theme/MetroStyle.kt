package app.duenorth.budget.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The look the person picks under settings, appearance. Metro is the flat Metro 2 look and the default.
 * Pressed metro, tile relief and carved are Metro with soft relief, chosen by the owner on 2026-10-20 from three
 * mockups. Each keeps Metro's type, pivot, square corners, flat accent fills, round app-bar buttons and grid; only
 * light and shade are added, and each style follows one rule for where they go. Mica is the Windows 11 look,
 * chosen the same day from the acrylic mockups: a tinted page, rounded layer cards edged by a hairline, and an
 * acrylic app bar and keypad that float above the page.
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
    /** Surfaces are rounded layer cards with a hairline edge instead of light and shade; bars float (mica). */
    val layers: Boolean = false,
) {
    Metro("metro", "metro"),
    PressedMetro("pressed-metro", "pressed metro"),
    TileRelief("tile-relief", "tile relief", panels = true, heroTiles = true, filledAdd = true),
    Carved("carved", "carved", channels = true, carvedControls = true),
    Mica("mica", "mica", panels = true, filledAdd = true, layers = true),
    ;

    /** Whether surfaces are drawn by [MetroSurface] (relief or layers) rather than Metro's outlines. */
    val relief: Boolean get() = this != Metro

    /**
     * The relief of a control at rest: raised, or pressed in under the carved style. Under mica, inset is a field:
     * a layer card with a line under it.
     */
    val control: Relief get() = if (carvedControls || layers) Relief.Inset else Relief.Raised

    /** The segmented toggle's two options share one track (carved, mica) rather than standing apart. */
    val trackedToggle: Boolean get() = carvedControls || layers

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
 *
 * Under mica a surface is instead a [layer] card on the [page], edged by a [stroke] hairline, and what floats
 * (the app bar, the keypad) is [acrylic] with the [shade] as its drop shadow. A field is a card with an
 * [underline].
 */
@Immutable
data class MetroSurface(
    val style: MetroStyle,
    val highlight: Color,
    val shade: Color,
    val depth: Dp,
    val blur: Dp,
    val page: Color = Color.Transparent,
    val layer: Color = Color.Transparent,
    val stroke: Color = Color.Transparent,
    val acrylic: Color = Color.Transparent,
    val underline: Color = Color.Transparent,
) {
    companion object {
        val Flat = MetroSurface(MetroStyle.Metro, Color.Transparent, Color.Transparent, 0.dp, 0.dp)

        fun of(
            style: MetroStyle,
            dark: Boolean,
        ): MetroSurface =
            if (style == MetroStyle.Metro) {
                Flat
            } else if (style.layers) {
                MetroSurface(
                    style,
                    highlight = Color.Transparent,
                    shade = if (dark) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.16f),
                    depth = 8.dp,
                    blur = 24.dp,
                    page = StylePalettes.colors(style, dark).background,
                    layer = if (dark) Color.White.copy(alpha = 0.055f) else Color.White.copy(alpha = 0.72f),
                    stroke = if (dark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.07f),
                    // Acrylic without a backdrop blur: opaque enough that rows passing under it do not read through.
                    acrylic = if (dark) Color(0xF02C2C30) else Color(0xF2F9F9F9),
                    underline = StylePalettes.colors(style, dark).secondary,
                )
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
                MetroStyle.Mica -> if (dark) 0xFF1E1D22 to 0xFF2C2C30 else 0xFFF1EFF3 to 0xFFF9F9F9
            }
        val page = Color(background)
        return metro.copy(
            background = page,
            chrome = Color(chrome),
            secondary =
                Contrast.ensure(
                    Color(
                        when {
                            style.layers -> if (dark) 0xFFA9A6B0 else 0xFF5B5862
                            dark -> 0xFF9E9E9E
                            else -> 0xFF636363
                        },
                    ),
                    page,
                ),
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
