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
    fun titleMovesSlowerThanTheSection() {
        val width = 800f
        val start = PanoramaMotion.titleShiftPx(0f, 3, width)
        val next = PanoramaMotion.titleShiftPx(1f, 3, width)
        val titleDelta = next - start
        val sectionDelta = -1000f
        assertTrue(titleDelta < 0f)
        assertTrue(abs(titleDelta) < abs(sectionDelta))
        assertEquals(0f, start)
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
