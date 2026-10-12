package app.duenorth.budget.design

import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphInk
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGlyphTest {
    @Test
    fun everyGlyphDrawsInk() {
        AppGlyph.entries.forEach { glyph ->
            assertTrue(glyph.name, AppGlyphInk.strokeCount(glyph) > 0)
        }
    }
}
