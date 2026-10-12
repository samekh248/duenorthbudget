package app.duenorth.budget.design

import app.duenorth.budget.design.theme.Accent
import app.duenorth.budget.design.theme.Contrast
import app.duenorth.budget.design.theme.MetroColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DesignRulesTest {
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
