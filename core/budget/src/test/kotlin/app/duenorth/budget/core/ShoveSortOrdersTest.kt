package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** Moving an account lands it exactly where it was dropped, however the stored sort orders sit. */
class ShoveSortOrdersTest {
    @Test
    fun tiedSortOrdersStillMove() {
        // Accounts that came over with no sort order all read as 0; the name breaks the tie.
        val after = move(listOf("a" to 0.0, "b" to 0.0, "c" to 0.0, "d" to 0.0), "d", "b")
        assertEquals(listOf("a", "d", "b", "c"), after)
    }

    @Test
    fun everyMoveLandsWhereItWasDropped() {
        val random = Random(7)
        repeat(300) {
            val size = random.nextInt(2, 9)
            // Few distinct values, so ties and tight gaps come up often.
            var items = (0 until size).map { "${'a' + it}" to random.nextInt(0, 4) * random.nextDouble(0.0, 3.0).toInt().toDouble() }
            items = items.sortedWith(compareBy({ it.second }, { it.first }))
            repeat(6) {
                val order = items.map { it.first }
                val mover = order.random(random)
                val rest = order - mover
                val at = random.nextInt(0, rest.size + 1)
                val target = rest.getOrNull(at)
                val expected = rest.toMutableList().apply { add(at, mover) }
                items = apply(items, mover, target)
                assertEquals("move $mover before $target from $order", expected, items.map { it.first })
            }
        }
    }

    private fun move(
        items: List<Pair<String, Double>>,
        id: String,
        target: String?,
    ): List<String> = apply(items, id, target).map { it.first }

    /** What the accounts table holds after [AccountsBook.move], read back the way it orders them. */
    private fun apply(
        items: List<Pair<String, Double>>,
        id: String,
        target: String?,
    ): List<Pair<String, Double>> {
        val (updates, order) = AccountsBook.shoveSortOrders(items, target)
        val stored = items.toMap().toMutableMap()
        updates.forEach { (u, sort) -> stored[u] = sort }
        stored[id] = order
        return stored.toList().sortedWith(compareBy({ it.second }, { it.first }))
    }
}
