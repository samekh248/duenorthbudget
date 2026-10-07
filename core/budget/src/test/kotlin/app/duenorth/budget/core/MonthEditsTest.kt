package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.YearMonth

class MonthEditsTest {
    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)
    private val december = YearMonth.of(2026, 12)
    private val usd = Currencies.byCode("USD")!!

    @Test
    fun assignMovesToBudgetAndAvailableTogether() {
        val start = funded(50_000)
        val assigned = apply(MonthEdits.assign(start, october, "c-g", 12_000))
        val page = assigned.month(october)
        assertEquals(38_000L, page.shell.headerMinor)
        assertEquals(12_000L, page.categories.single().budgetedMinor)
        assertEquals(12_000L, page.categories.single().availableMinor)
        val reversed = apply(MonthEdits.assign(assigned, october, "c-g", 0))
        assertEquals(50_000L, reversed.month(october).shell.headerMinor)
        assertEquals(
            0L,
            reversed
                .month(october)
                .categories
                .single()
                .availableMinor,
        )
    }

    @Test
    fun loweringBudgetedReturnsTheSameDelta() {
        val assigned = apply(MonthEdits.assign(funded(50_000), october, "c-g", 12_000))
        val lowered = apply(MonthEdits.assign(assigned, october, "c-g", 2_000))
        assertEquals(48_000L, lowered.month(october).shell.headerMinor)
        assertEquals(
            2_000L,
            lowered
                .month(october)
                .categories
                .single()
                .availableMinor,
        )
    }

    @Test
    fun unparseableAmountIsNull() {
        assertNull(MoneyFormat.parse("abc", usd))
        assertNull(MoneyFormat.parse("", usd))
        assertNull(MoneyFormat.parse("12.345", usd))
        assertEquals(12_000L, MoneyFormat.parse("120", usd))
        assertEquals(12_050L, MoneyFormat.parse("120.50", usd))
        assertEquals(-1_500L, MoneyFormat.parse("-15", usd))
        assertEquals(1_500L, MoneyFormat.parse("1500", Currencies.byCode("JPY")!!))
        assertNull(MoneyFormat.parse("15.00", Currencies.byCode("JPY")!!))
    }

    @Test
    fun moveKeepsToBudgetAndRefusesTooMuch() {
        var book = funded(10_000, eating = true)
        book = apply(MonthEdits.assign(book, october, "c-g", 8_000))
        book = apply(MonthEdits.assign(book, october, "c-e", 2_000))
        val before = book.month(october).shell.headerMinor
        val moved = apply(MonthEdits.move(book, october, "c-g", "c-e", 1_500))
        val page = moved.month(october)
        assertEquals(before, page.shell.headerMinor)
        assertEquals(6_500L, page.categories.first { it.id == "c-g" }.availableMinor)
        assertEquals(3_500L, page.categories.first { it.id == "c-e" }.availableMinor)
        val refused = MonthEdits.move(moved, october, "c-g", "c-e", 6_501)
        assertTrue(refused is MonthResult.Refused)
        assertEquals(ShellCopy.NOT_ENOUGH_AVAILABLE, (refused as MonthResult.Refused).reason)
        assertEquals(
            6_500L,
            moved
                .month(october)
                .categories
                .first { it.id == "c-g" }
                .availableMinor,
        )
    }

    @Test
    fun categoriesInDifferentGroupsCanExchange() {
        val book =
            funded(10_000, eating = true).copy(
                groups =
                    funded(0).groups +
                        GroupRecord("g2", "Fun", isIncome = false, hidden = false, sortOrder = 2.0, tombstone = false),
                categories =
                    funded(0, eating = true).categories.map {
                        if (it.id == "c-e") it.copy(groupId = "g2") else it
                    },
            )
        val moved =
            apply(
                MonthEdits.move(
                    apply(MonthEdits.assign(apply(MonthEdits.assign(book, october, "c-g", 8_000)), october, "c-e", 2_000)),
                    october,
                    "c-g",
                    "c-e",
                    1_000,
                ),
            )
        assertEquals(
            3_000L,
            moved
                .month(october)
                .shell.groups
                .first { it.id == "g2" }
                .availableMinor,
        )
        assertEquals(
            7_000L,
            moved
                .month(october)
                .categories
                .first { it.id == "c-g" }
                .availableMinor,
        )
    }

    @Test
    fun uncoveredOverspendLowersNextMonthAndRolloverKeepsIt() {
        val overspent = funded(0).copy(spent = mapOf((202610 to "c-g") to -3_000L))
        val next = overspent.month(november)
        assertEquals(-3_000L, next.shell.headerMinor)
        assertEquals(0L, next.categories.single().availableMinor)
        val rolling = apply(MonthEdits.carryover(overspent, october, "c-g", true))
        val kept = rolling.month(november)
        assertEquals(0L, kept.shell.headerMinor)
        assertEquals(-3_000L, kept.categories.single().availableMinor)
        val stopped = apply(MonthEdits.carryover(rolling, november, "c-g", false))
        assertEquals(
            -3_000L,
            stopped
                .month(november)
                .categories
                .single()
                .availableMinor,
        )
        val following = stopped.month(december)
        assertEquals(-3_000L, following.shell.headerMinor)
        assertEquals(0L, following.categories.single().availableMinor)
    }

    @Test
    fun coveringOverspendSparesNextMonth() {
        var book = funded(5_000, eating = true)
        book = book.copy(spent = book.spent + ((202610 to "c-g") to -3_000L))
        book = apply(MonthEdits.assign(book, october, "c-e", 5_000))
        val covered = apply(MonthEdits.move(book, october, "c-e", "c-g", 3_000))
        assertEquals(
            0L,
            covered
                .month(october)
                .categories
                .first { it.id == "c-g" }
                .availableMinor,
        )
        assertEquals(0L, covered.month(november).shell.headerMinor)
    }

    @Test
    fun holdIsIncludedInNextMonthWithoutAddingItTwice() {
        val start = funded(40_000)
        val unheldNext = start.month(november).shell.headerMinor
        val held = apply(MonthEdits.hold(start, october, 25_000))
        assertEquals(15_000L, held.month(october).shell.headerMinor)
        assertEquals(25_000L, held.month(october).shell.bufferedMinor)
        assertEquals(unheldNext, held.month(november).shell.headerMinor)
        val released = apply(MonthEdits.hold(held, october, 0))
        assertEquals(40_000L, released.month(october).shell.headerMinor)
        assertEquals(0L, released.month(october).shell.bufferedMinor)
        val refused = MonthEdits.hold(start, october, 40_001)
        assertEquals(ShellCopy.NOT_ENOUGH_TO_HOLD, (refused as MonthResult.Refused).reason)
        assertEquals(40_000L, start.month(october).shell.headerMinor)
    }

    @Test
    fun newCategorySurvivesNextMonthWithoutChangingLastAssignment() {
        val added = apply(MonthEdits.addGroup(funded(20_000), "Bills", "g-bills"))
        val withCategory = apply(MonthEdits.addCategory(added, "g-bills", "Power", "c-power"))
        val assigned = apply(MonthEdits.assign(withCategory, october, "c-power", 4_000))
        assertEquals(
            4_000L,
            assigned
                .month(october)
                .categories
                .first { it.id == "c-power" }
                .budgetedMinor,
        )
        assertEquals(
            0L,
            assigned
                .month(november)
                .categories
                .first { it.id == "c-power" }
                .budgetedMinor,
        )
        assertEquals(
            "Power",
            assigned
                .month(november)
                .categories
                .first { it.id == "c-power" }
                .name,
        )
        assertTrue(
            assigned
                .month(november)
                .shell.groups
                .any { it.name == "Bills" },
        )
    }

    @Test
    fun hiddenCategoryLeavesTheGroupTotalAndComesBack() {
        val assigned = apply(MonthEdits.assign(funded(10_000), october, "c-g", 4_000))
        assertEquals(
            4_000L,
            assigned
                .month(october)
                .shell.groups
                .single { it.id == "g" }
                .availableMinor,
        )
        val hidden = apply(MonthEdits.hidden(assigned, "c-g", true))
        val page = hidden.month(october)
        assertTrue(page.categories.single().hidden)
        assertEquals(
            0L,
            page.shell.groups
                .single { it.id == "g" }
                .availableMinor,
        )
        assertEquals(assigned.month(october).shell.headerMinor, page.shell.headerMinor)
        val shown = apply(MonthEdits.hidden(hidden, "c-g", false))
        assertEquals(
            4_000L,
            shown
                .month(october)
                .shell.groups
                .single { it.id == "g" }
                .availableMinor,
        )
    }

    @Test
    fun deleteRefusesMoneyOrHistoryAndTombstonesAnEmptyCategory() {
        val assigned = apply(MonthEdits.assign(funded(10_000), october, "c-g", 1_000))
        val refused = MonthEdits.deleteCategory(assigned, "c-g")
        assertEquals(ShellCopy.MOVE_FIRST, (refused as MonthResult.Refused).reason)
        val withHistory = funded(0).copy(categorized = setOf("c-g"))
        assertTrue(MonthEdits.deleteCategory(withHistory, "c-g") is MonthResult.Refused)
        val empty = apply(MonthEdits.addCategory(funded(0), "g", "Spare", "c-spare"))
        val deleted = apply(MonthEdits.deleteCategory(empty, "c-spare"))
        assertTrue(deleted.categories.first { it.id == "c-spare" }.tombstone)
        assertTrue(deleted.month(october).categories.none { it.id == "c-spare" })
        val groupRefused = MonthEdits.deleteGroup(assigned, "g")
        assertTrue(groupRefused is MonthResult.Refused)
        val emptyGroup = apply(MonthEdits.deleteCategory(empty, "c-g"))
        val removed = apply(MonthEdits.deleteGroup(emptyGroup, "g"))
        assertTrue(removed.groups.first { it.id == "g" }.tombstone)
    }

    @Test
    fun renameAndReorderRoundTripThroughTheFile() {
        val file = File.createTempFile("month", ".sqlite")
        file.delete()
        JdbcSessionOpener().use(file) { session ->
            ActualSchema.create(session)
            session.exec("INSERT INTO preferences (id, value) VALUES (?, ?)", listOf("budgetType", "envelope"))
            val renamed = apply(MonthEdits.renameGroup(apply(MonthEdits.addGroup(emptyBook(), "Bills", "g-bills")), "g-bills", "Home"))
            val ordered = apply(MonthEdits.moveGroup(renamed, "g-bills", -1))
            BudgetBookStore.save(session, ordered)
            val loaded = BudgetBookStore.load(session, BudgetMetadata("home", "Home"))
            assertEquals("Home", loaded.groups.first { it.id == "g-bills" }.name)
            val names =
                loaded
                    .month(october)
                    .shell.groups
                    .map { it.name }
            assertEquals(listOf("Home", "Living"), names)
            assertEquals(
                "envelope",
                session.query("SELECT value FROM preferences WHERE id = ?", listOf("budgetType")).single().str("value"),
            )
        }
    }

    @Test
    fun trackingAssignDoesNotFlipModeAndCarryoverRolls() {
        val file = File.createTempFile("track", ".sqlite")
        file.delete()
        val tracking =
            emptyBook().copy(
                mode = BudgetMode.TRACKING,
                spent = mapOf((202609 to "c-g") to -4_000L),
                assignments = listOf(ActualMonthMath.AssignmentFact(202609, "c-g", 10_000L, false)),
            )
        val stayed = apply(MonthEdits.assign(tracking, october, "c-g", 1_000))
        assertEquals(BudgetMode.TRACKING, stayed.mode)
        assertEquals(
            0L,
            stayed
                .month(october)
                .categories
                .single()
                .availableMinor - 1_000L,
        )
        val rolling = apply(MonthEdits.carryover(tracking, YearMonth.of(2026, 9), "c-g", true))
        assertEquals(
            6_000L,
            rolling
                .month(october)
                .categories
                .single()
                .availableMinor,
        )
        JdbcSessionOpener().use(file) { session ->
            ActualSchema.create(session)
            session.exec("INSERT INTO preferences (id, value) VALUES (?, ?)", listOf("budgetType", "tracking"))
            BudgetBookStore.save(session, stayed)
            val loaded = BudgetBookStore.load(session, BudgetMetadata("home", "Home"))
            assertEquals(BudgetMode.TRACKING, loaded.mode)
            assertEquals(
                "tracking",
                session.query("SELECT value FROM preferences WHERE id = ?", listOf("budgetType")).single().str("value"),
            )
            assertEquals(0, session.query("SELECT id FROM zero_budgets").size)
            assertEquals(2, session.query("SELECT id FROM reflect_budgets").size)
        }
    }

    @Test
    fun assigningWithNoIncomeGoesNegative() {
        val assigned = apply(MonthEdits.assign(funded(0), october, "c-g", 2_000))
        assertEquals(-2_000L, assigned.month(october).shell.headerMinor)
        assertTrue(assigned.month(october).shell.headerMinor < 0)
    }

    @Test
    fun threeHundredCategoriesRecomputeQuickly() {
        val categories =
            (0 until 300).map { index ->
                CategoryRecord(
                    "c$index",
                    "category $index",
                    "g",
                    isIncome = false,
                    hidden = false,
                    sortOrder = index.toDouble(),
                    tombstone = false,
                )
            }
        val book = funded(0).copy(categories = funded(0).categories + categories)
        val started = System.nanoTime()
        val assigned = apply(MonthEdits.assign(book, october, "c0", 100))
        val page = assigned.month(october)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(100L, page.categories.first { it.id == "c0" }.availableMinor)
        assertTrue("300 categories took ${elapsedMs}ms", elapsedMs < 100)
    }

    private fun apply(result: MonthResult): BudgetBook = (result as MonthResult.Applied).book

    private fun emptyBook(): BudgetBook =
        BudgetBook(
            id = "home",
            name = "Home",
            mode = BudgetMode.ENVELOPE,
            currency = usd,
            groups =
                listOf(
                    GroupRecord("g-inc", "Income", isIncome = true, hidden = false, sortOrder = 0.0, tombstone = false),
                    GroupRecord("g", "Living", isIncome = false, hidden = false, sortOrder = 1.0, tombstone = false),
                ),
            categories =
                listOf(
                    CategoryRecord("c-sal", "Salary", "g-inc", isIncome = true, hidden = false, sortOrder = 0.0, tombstone = false),
                    CategoryRecord("c-g", "Groceries", "g", isIncome = false, hidden = false, sortOrder = 1.0, tombstone = false),
                ),
            spent = emptyMap(),
            categorized = emptySet(),
            assignments = emptyList(),
            buffers = emptyMap(),
            accounts = emptyList(),
            inbox = emptyList(),
        )

    private fun funded(
        income: Long,
        eating: Boolean = false,
    ): BudgetBook {
        val book = emptyBook()
        val categories =
            if (eating) {
                book.categories +
                    CategoryRecord("c-e", "Eating Out", "g", isIncome = false, hidden = false, sortOrder = 2.0, tombstone = false)
            } else {
                book.categories
            }
        val spent = if (income == 0L) emptyMap() else mapOf((202610 to "c-sal") to income)
        return book.copy(categories = categories, spent = spent)
    }
}
