package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class GetStartedTest {
    private val today = LocalDate.of(2026, 10, 7)
    private val month = YearMonth.of(2026, 10)

    @Test
    fun aNewBudgetStartsWithTheIncomeGroupActualMakes() {
        val library = library()
        val id = (library.create("Home", "USD") as CreateResult.Created).id
        val shell = library.readShell(id)!!
        assertTrue(shell.groups.isEmpty())
        assertEquals(listOf(NewBudgetCopy.INCOME), shell.incomeGroups.map { it.name })
        assertEquals(
            listOf(NewBudgetCopy.INCOME, NewBudgetCopy.STARTING_BALANCES),
            shell.incomeGroups
                .single()
                .categories
                .map { it.name },
        )
    }

    @Test
    fun aStartingBalanceCountsTowardToBudget() {
        val library = library()
        val id = (library.create("Home", "USD") as CreateResult.Created).id
        library.createAccount(id, "Checking", offBudget = false, balanceMinor = 2_140_00)
        assertEquals(2_140_00L, library.readShell(id)!!.headerMinor)
    }

    @Test
    fun eachLineIsDoneWhenTheBudgetShowsIt() {
        val library = library()
        val sync = coordinator(library)
        val id = (library.create("Home", "USD") as CreateResult.Created).id
        assertEquals(List(4) { false }, done(library, id))

        library.createAccount(id, "Checking", offBudget = false, balanceMinor = 500_00)
        assertEquals(listOf(true, false, false, false), done(library, id))

        val picked = StarterCategories.all.filter { it.picked }
        assertEquals(EditResult.Saved, sync.addStarterCategories(id, picked))
        val shell = library.readShell(id, month)!!
        assertEquals(picked.map { it.name }, shell.groups.map { it.name })
        assertEquals(
            picked.first().categories,
            shell.groups
                .first()
                .categories
                .map { it.name },
        )
        assertEquals(listOf(true, true, false, false), done(library, id))

        // Part of the money assigned leaves the line open; all of it closes it.
        val rent =
            shell.groups
                .first()
                .categories
                .first()
                .id
        sync.assign(id, rent, month, "300")
        assertEquals(listOf(true, true, false, false), done(library, id))
        sync.assign(id, rent, month, "500")
        assertEquals(listOf(true, true, true, false), done(library, id))

        val lines = GetStarted.lines(library.readShell(id, month)!!, listOf(schedule()))
        assertTrue(lines.all { it.done })
    }

    @Test
    fun noGroupsPickedAddsNothing() {
        val library = library()
        val id = (library.create("Home", "USD") as CreateResult.Created).id
        val result = coordinator(library).addStarterCategories(id, emptyList())
        assertEquals(EditResult.Rejected(StarterCategoriesCopy.PICK_ONE), result)
        assertTrue(library.readShell(id)!!.groups.isEmpty())
    }

    @Test
    fun theListShowsOnlyForItsBudgetAndUntilEveryLineIsDone() {
        val library = library()
        val id = (library.create("Home", "USD") as CreateResult.Created).id
        val shell = library.readShell(id, month)!!
        assertNull(GetStarted.showing(PhoneSettings(), shell, emptyList()))
        val settings = PhoneSettings(getStarted = listOf(id))
        assertEquals(4, GetStarted.showing(settings, shell, emptyList())!!.size)
        assertNull(GetStarted.showing(settings, null, emptyList()))
    }

    @Test
    fun theBrandNewSampleIsTheFileReleaseBuildsMake() {
        val library = library()
        val id = (library.createFromMock(MockBudgets.BRAND_NEW) as CreateResult.Created).id
        val shell = library.readShell(id)!!
        assertEquals("brand new", shell.budgetName)
        assertEquals("USD", shell.currency.code)
        assertTrue(shell.groups.isEmpty())
        assertTrue(shell.accounts.isEmpty())
        assertEquals(1, shell.incomeGroups.size)
    }

    private fun done(
        library: BudgetLibrary,
        id: String,
    ): List<Boolean> = GetStarted.lines(library.readShell(id, month)!!, emptyList()).map { it.done }

    private fun schedule() =
        ScheduleSummary(
            id = "s",
            name = "Rent",
            accountId = "a",
            accountName = "Checking",
            payee = "Landlord",
            amountMinor = -1_200_00,
            amountOp = "is",
            categoryLabel = "Rent or mortgage",
            nextDate = 20261101,
            completed = false,
            postsTransaction = false,
            repeat = ScheduleRepeat.MONTHLY,
            interval = 1,
            dates = emptyList(),
        )

    private fun library() =
        BudgetLibrary(
            File.createTempFile("budgets", "").apply {
                delete()
                mkdirs()
            },
            JdbcSessionOpener(),
            BudgetClock { today },
        )

    private fun coordinator(library: BudgetLibrary) =
        SyncCoordinator(library, HttpActualTransport(), MemorySecretStore(), BudgetClock { today }, ZoneOffset.UTC)
}
