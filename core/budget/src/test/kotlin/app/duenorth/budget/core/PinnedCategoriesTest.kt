package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedCategoriesTest {
    @Test
    fun pinsInOrderAndUnpins() {
        var settings = PhoneSettings()
        settings = PinnedCategories.toggle(settings, "home", "food")
        settings = PinnedCategories.toggle(settings, "home", "gas")
        assertEquals(listOf("food", "gas"), PinnedCategories.ids(settings, "home"))
        settings = PinnedCategories.toggle(settings, "home", "food")
        assertEquals(listOf("gas"), PinnedCategories.ids(settings, "home"))
        settings = PinnedCategories.toggle(settings, "home", "gas")
        assertTrue(settings.pinnedCategories.isEmpty())
    }

    @Test
    fun stopsAtSixAndKeepsBudgetsApart() {
        var settings = PhoneSettings()
        (1..7).forEach { settings = PinnedCategories.toggle(settings, "home", "c$it") }
        assertEquals((1..6).map { "c$it" }, PinnedCategories.ids(settings, "home"))
        assertFalse(PinnedCategories.hasRoom(settings, "home"))
        assertTrue(PinnedCategories.hasRoom(settings, "work"))
        assertEquals(emptyList<String>(), PinnedCategories.ids(settings, "work"))
    }

    @Test
    fun resolvesInPinOrderSkippingMissing() {
        fun row(id: String) = CategoryRow(id, id, 0, 0, 100, false)
        val groups = listOf(GroupRow("g", "g", 0, listOf(row("a"), row("b"), row("c"))))
        assertEquals(listOf("c", "a"), PinnedCategories.resolve(listOf("c", "gone", "a"), groups).map { it.id })
    }
}
