package app.duenorth.budget.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.duenorth.budget.design.R
import kotlin.math.max
import kotlin.math.min

object MetroWeights {
    val Light = FontWeight.Light
    val Semilight = FontWeight(350)
    val Regular = FontWeight.Normal
    val Semibold = FontWeight.SemiBold
}

object MetroFonts {
    val family: FontFamily =
        FontFamily(
            Font(R.font.selawik_light, MetroWeights.Light),
            Font(R.font.selawik_semilight, MetroWeights.Semilight),
            Font(R.font.selawik_regular, MetroWeights.Regular),
            Font(R.font.selawik_semibold, MetroWeights.Semibold),
            Font(R.font.selawik_bold, FontWeight.Bold),
        )
}

@Immutable
data class MetroTypography(
    val panoramaTitle: TextStyle,
    val sectionHeader: TextStyle,
    val pageTitle: TextStyle,
    val header: TextStyle,
    val subheader: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
) {
    companion object {
        fun create(family: FontFamily = MetroFonts.family): MetroTypography =
            MetroTypography(
                panoramaTitle =
                    TextStyle(
                        fontFamily = family,
                        fontWeight = MetroWeights.Light,
                        fontSize = 118.sp,
                        letterSpacing = (-0.04).em,
                        lineHeight = 118.sp,
                    ),
                sectionHeader =
                    TextStyle(
                        fontFamily = family,
                        fontWeight = MetroWeights.Light,
                        fontSize = 40.sp,
                    ),
                pageTitle =
                    TextStyle(
                        fontFamily = family,
                        fontWeight = MetroWeights.Semibold,
                        fontSize = 13.sp,
                        letterSpacing = 0.06.em,
                    ),
                header =
                    TextStyle(
                        fontFamily = family,
                        fontWeight = MetroWeights.Light,
                        fontSize = 52.sp,
                    ),
                subheader =
                    TextStyle(
                        fontFamily = family,
                        fontWeight = MetroWeights.Semilight,
                        fontSize = 20.sp,
                    ),
                body =
                    TextStyle(
                        fontFamily = family,
                        fontWeight = MetroWeights.Regular,
                        fontSize = 15.sp,
                    ),
                caption =
                    TextStyle(
                        fontFamily = family,
                        fontWeight = MetroWeights.Regular,
                        fontSize = 13.sp,
                    ),
            )

        val Default: MetroTypography = create()
    }
}

@Immutable
data class MetroColors(
    val isDark: Boolean,
    val background: Color,
    val foreground: Color,
    val secondary: Color,
    val chrome: Color,
    val outline: Color,
) {
    companion object {
        val Light =
            MetroColors(
                isDark = false,
                background = Color(0xFFFFFFFF),
                foreground = Color(0xFF111111),
                secondary = Color(0xFF5C5C5C),
                chrome = Color(0xFFE5E5E5),
                outline = Color(0xFF8C8C8C),
            )

        val Dark =
            MetroColors(
                isDark = true,
                background = Color(0xFF000000),
                foreground = Color(0xFFFFFFFF),
                secondary = Color(0xFFA6A6A6),
                chrome = Color(0xFF1F1F1F),
                outline = Color(0xFF8C8C8C),
            )
    }
}

@Immutable
data class AccentColors(
    val text: Color,
    val fill: Color,
    val onFill: Color,
)

enum class Accent(
    val id: String,
    private val lightText: Long,
    private val darkValue: Long,
    private val lightFill: Long = lightText,
) {
    Magenta("magenta", 0xFFB0005E, 0xFFF0389A),
    LightOrange("light orange", 0xFFA85400, 0xFFFFA552, lightFill = 0xFFFFA552),
    Coral("coral", 0xFFB8402A, 0xFFFF8A6E, lightFill = 0xFFFF8A6E),
    Lime("lime", 0xFF5A6E00, 0xFFA4C400),
    Green("green", 0xFF3C7A0E, 0xFF60A917),
    Emerald("emerald", 0xFF007A00, 0xFF2DB52D),
    Teal("teal", 0xFF00787A, 0xFF00ABA9),
    Cyan("cyan", 0xFF0B6FA4, 0xFF1BA1E2),
    Cobalt("cobalt", 0xFF0050EF, 0xFF4D8BFF),
    Indigo("indigo", 0xFF6A00FF, 0xFF9A5CFF),
    Violet("violet", 0xFF8A00D4, 0xFFC25CFF),
    Pink("pink", 0xFFB0308F, 0xFFF472D0),
    Crimson("crimson", 0xFFA20025, 0xFFFF5C7A),
    Red("red", 0xFFC41100, 0xFFFF5C4D),
    Orange("orange", 0xFFB34A00, 0xFFFA6800),
    Amber("amber", 0xFF8F5F00, 0xFFF0A30A),
    Yellow("yellow", 0xFF7A6A00, 0xFFE3C800),
    Brown("brown", 0xFF825A2C, 0xFFC8955A),
    Olive("olive", 0xFF566B4F, 0xFF93AD89),
    Steel("steel", 0xFF576778, 0xFF8EA2B8),
    Mauve("mauve", 0xFF76608A, 0xFFA891BE),
    Taupe("taupe", 0xFF6E6240, 0xFFB5A577),
    ;

    fun text(dark: Boolean): Color = Color(if (dark) darkValue else lightText)

    fun fill(dark: Boolean): Color = Color(if (dark) darkValue else lightFill)

    fun colors(dark: Boolean): AccentColors {
        val fill = fill(dark)
        return AccentColors(text = text(dark), fill = fill, onFill = Contrast.bestOn(fill))
    }

    companion object {
        val Default = Magenta

        fun fromId(id: String): Accent = entries.firstOrNull { it.id == id } ?: Default
    }
}

object Contrast {
    const val MIN_TEXT = 4.5f

    fun ratio(
        a: Color,
        b: Color,
    ): Float {
        val left = a.luminance()
        val right = b.luminance()
        return (max(left, right) + 0.05f) / (min(left, right) + 0.05f)
    }

    fun bestOn(background: Color): Color =
        if (ratio(Color.White, background) >= ratio(Color.Black, background)) {
            Color.White
        } else {
            Color.Black
        }
}

val LocalMetroColors = staticCompositionLocalOf { MetroColors.Light }
val LocalAccent = staticCompositionLocalOf { Accent.Default.colors(dark = false) }
val LocalAppAccent = staticCompositionLocalOf { Accent.Default }
val LocalMetroTypography = staticCompositionLocalOf { MetroTypography.Default }
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

@Composable
fun MetroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Accent = Accent.Default,
    animationsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) MetroColors.Dark else MetroColors.Light
    CompositionLocalProvider(
        LocalMetroColors provides colors,
        LocalAccent provides accent.colors(darkTheme),
        LocalAppAccent provides accent,
        LocalMetroTypography provides MetroTypography.Default,
        LocalAnimationsEnabled provides animationsEnabled,
        content = content,
    )
}

object Metro {
    val colors: MetroColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMetroColors.current

    val accent: AccentColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAccent.current

    val typography: MetroTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalMetroTypography.current

    val animations: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalAnimationsEnabled.current
}

object MetroDimens {
    val Gutter = 12.dp
    val Grid = 24.dp
    val TouchTarget = 48.dp
    val Peek = 40.dp
    val AppBar = 72.dp
}
