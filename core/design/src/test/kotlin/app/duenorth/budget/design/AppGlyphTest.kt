package app.duenorth.budget.design

import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphInk
import app.duenorth.budget.design.components.Mdl2GlyphInk
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGlyphTest {
    @Test
    fun everyGlyphDrawsInk() {
        AppGlyph.entries.forEach { glyph ->
            assertTrue(glyph.name, AppGlyphInk.strokeCount(glyph) > 0)
        }
    }

    // The edit budget button reached the bar while Windows 10 Mobile was being drawn, without a glyph of its own
    // there, and the budget crashed as soon as it drew.
    @Test
    fun windows10MobileHasInkForEveryGlyph() {
        AppGlyph.entries.forEach { glyph -> assertTrue(glyph.name, Mdl2GlyphInk.covers(glyph)) }
    }
}
