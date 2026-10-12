package app.duenorth.budget.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duenorth.budget.design.theme.Accent
import app.duenorth.budget.design.theme.Contrast
import app.duenorth.budget.design.theme.MetroColors
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroTypography
import app.duenorth.budget.design.theme.MetroWeights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DesignRulesTest {
    @Test
    fun themeMatchesWindowsPhone81() {
        assertEquals(Color(0xFF000000), MetroColors.Light.foreground)
        assertEquals(Color(0xFF8C8C8C), MetroColors.Light.secondary)
        assertEquals(Color(0xFFDDDDDD), MetroColors.Light.chrome)
        assertEquals(Color(0xFF999999), MetroColors.Dark.secondary)
        assertEquals(Color(0xFF1F1F1F), MetroColors.Dark.chrome)
        assertEquals(Color(0xFFD80073), Accent.Magenta.fill(false))
        assertEquals(Color(0xFFD80073), Accent.Magenta.fill(true))
        assertEquals(Color(0xFF008A00), Accent.Emerald.fill(true))
        assertEquals(Color(0xFFAA00FF), Accent.Violet.fill(false))
        assertEquals(Color(0xFFE51400), Accent.Red.fill(true))
        val type = MetroTypography.Default
        assertEquals(118.sp, type.panoramaTitle.fontSize)
        assertEquals(MetroWeights.Light, type.panoramaTitle.fontWeight)
        assertEquals(20.sp, type.logoSubtitle.fontSize)
        assertEquals(42.667.sp, type.sectionHeader.fontSize)
        assertEquals(MetroWeights.Semilight, type.sectionHeader.fontWeight)
        assertEquals(16.sp, type.pageTitle.fontSize)
        assertEquals(48.sp, type.header.fontSize)
        assertEquals(26.sp, type.subheader.fontSize)
        assertEquals(20.sp, type.groupHeader.fontSize)
        assertEquals(16.sp, type.body.fontSize)
        assertEquals(18.sp, type.control.fontSize)
        assertEquals(14.sp, type.caption.fontSize)
        assertEquals(MetroWeights.Semilight, type.caption.fontWeight)
        assertEquals(57.5.dp, MetroDimens.AppBar)
        assertEquals(38.5.dp, MetroDimens.AppBarIcon)
        assertEquals(2.5.dp, MetroDimens.Stroke)
        assertEquals(57.5.dp, MetroDimens.ButtonMin)
        assertEquals(12.dp, MetroDimens.Gutter)
        assertEquals(48.dp, MetroDimens.TouchTarget)
    }

    @Test
    fun everyAccentMeetsCaptionContrast() {
        Accent.entries.forEach { accent ->
            val light = Contrast.ratio(accent.text(false), MetroColors.Light.background)
            val dark = Contrast.ratio(accent.text(true), MetroColors.Dark.background)
            assertTrue("${accent.id} light $light", light >= Contrast.MIN_TEXT)
            assertTrue("${accent.id} dark $dark", dark >= Contrast.MIN_TEXT)
        }
    }

    @Test
    fun titleMovesByStepBetweenAdjacentPages() {
        val step = 40f
        val even = PanoramaMotion.titleShiftPx(0f, 6, step, motionEnabled = true)
        val odd = PanoramaMotion.titleShiftPx(1f, 6, step, motionEnabled = true)
        val mid = PanoramaMotion.titleShiftPx(0.5f, 6, step, motionEnabled = true)
        assertEquals(0f, even)
        assertEquals(-step, odd)
        assertEquals(-step / 2f, mid)
        assertTrue(abs(odd - even) <= step + 0.01f)
        assertEquals(0f, PanoramaMotion.titleShiftPx(1f, 6, step, motionEnabled = false))
        assertEquals(0f, PanoramaMotion.titleShiftPx(1f, 1, step, motionEnabled = true))
    }

    @Test
    fun reducedMotionSkipsTiltButAPressIsStillAPress() {
        assertEquals(false, MotionPolicy.enabled(0f))
        assertEquals(true, MotionPolicy.enabled(1f))
        val still = PressFeedback.rotation(10f, 10f, 100f, 40f, animations = false)
        val tilt = PressFeedback.rotation(10f, 10f, 100f, 40f, animations = true)
        assertEquals(0f, still.first)
        assertEquals(0f, still.second)
        assertTrue(tilt.first != 0f || tilt.second != 0f)
    }
}
