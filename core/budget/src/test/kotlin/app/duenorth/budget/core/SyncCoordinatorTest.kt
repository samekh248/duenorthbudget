package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SyncCoordinatorTest {
    @Test
    fun signInListsThatServersFilesAndThePanoramaMatches() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            LocalActualServer().use { other ->
                other.addFile("Somewhere else", budgetBytes())
                val result = sync.connect(server.baseUrl, "server-secret")
                assertNull(result.error)
                assertEquals(listOf(remote.fileId), result.files.map { it.fileId })
                assertEquals(OpenResult.Opened, sync.open(remote))
                assertEquals(10_000L, header(remote.fileId))
                assertEquals("synced 3:04 pm", sync.status(remote.fileId))
            }
        }
    }

    @Test
    fun wrongSignInReplacesNothing() {
        scenario {
            val kept = (library.create("Trip", "JPY") as CreateResult.Created).id
            val before = File(root, "$kept/db.sqlite").readBytes()
            val directories = budgetDirs()
            val badAddress = sync.connect("not a url", "violet-quartz-991")
            assertEquals(SyncCopy.ADDRESS_FAILED, badAddress.error)
            assertFalse(sync.signedIn())
            val refused = sync.connect("http://127.0.0.1:1", "violet-quartz-991")
            assertEquals(SyncCopy.ADDRESS_FAILED, refused.error)
            val rejected = sync.connect(server.baseUrl, "violet-quartz-991")
            assertEquals(SyncCopy.SIGN_IN_FAILED, rejected.error)
            assertFalse(sync.signedIn())
            assertEquals(directories, budgetDirs())
            assertTrue(File(root, "$kept/db.sqlite").readBytes().contentEquals(before))
            assertPasswordAbsent("violet-quartz-991")
        }
    }

    @Test
    fun downloadedBudgetReopensWithTheServerOff() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            server.online = false
            val restarted = restart()
            assertEquals(10_000L, header(remote.fileId))
            assertEquals(SyncCopy.LAST_FAILED, restarted.sync(remote.fileId).status)
            assertEquals(SyncCopy.LAST_FAILED, restarted.status(remote.fileId))
        }
    }

    @Test
    fun offlineAssignSurvivesRestartThenSyncs() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            server.online = false
            assertTrue(sync.assign(remote.fileId, "c-food", YearMonth.of(2026, 10), "25") is EditResult.Saved)
            assertEquals(2_500L, assigned(remote.fileId))
            assertEquals(7_500L, header(remote.fileId))
            val restarted = restart()
            assertEquals(2_500L, assigned(remote.fileId))
            assertEquals(7_500L, header(remote.fileId))
            server.online = true
            assertEquals("synced 3:04 pm", restarted.sync(remote.fileId).status)
            val messages = server.plainMessages(remote.fileId)
            assertTrue(messages.any { it.row == "202610-c-food" && it.column == "amount" && it.value == "2500" })
            val other = freshPhone()
            assertNull(other.sync.connect(server.baseUrl, "server-secret").error)
            assertEquals(OpenResult.Opened, other.sync.open(remote))
            assertEquals(7_500L, other.header(remote.fileId))
            other.close()
        }
    }

    @Test
    fun collisionKeepsBothAmounts() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            server.online = false
            assertTrue(sync.assign(remote.fileId, "c-food", YearMonth.of(2026, 10), "25") is EditResult.Saved)
            server.appendChange(remote.fileId, "zero_budgets", "202610-c-food", "amount", "4000")
            server.online = true
            sync.sync(remote.fileId)
            val conflicts = sync.conflicts(remote.fileId)
            assertEquals(1, conflicts.size)
            assertEquals("Groceries", conflicts.single().label)
            assertEquals("$25.00", conflicts.single().localText)
            assertEquals("$40.00", conflicts.single().remoteText)
            assertEquals(2_500L, assigned(remote.fileId))
            val values = cellValues(remote.fileId, "202610-c-food", "amount")
            assertTrue(values.contains("2500"))
            assertTrue(values.contains("4000"))
            sync.markConflictsSeen(remote.fileId)
            assertEquals("$25.00", sync.conflicts(remote.fileId).single().localText)
        }
    }

    @Test
    fun duplicateTimestampDoesNotApplyTheEditTwice() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            assertTrue(sync.assign(remote.fileId, "c-food", YearMonth.of(2026, 10), "25") is EditResult.Saved)
            sync.sync(remote.fileId)
            val before = messageCount(remote.fileId)
            library.useDatabase(remote.fileId) { session ->
                SyncSchema.putState(session, "lastRemote", SyncClock.ZERO)
            }
            sync.sync(remote.fileId)
            assertEquals(before, messageCount(remote.fileId))
            assertEquals(2_500L, assigned(remote.fileId))
        }
    }

    @Test
    fun assignDuringSyncDoesNotWaitOnTheDatabase() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            server.beforeSync = {
                started.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
            val worker =
                Thread {
                    sync.sync(remote.fileId)
                }
            worker.start()
            assertTrue(started.await(10, TimeUnit.SECONDS))
            val saved = sync.assign(remote.fileId, "c-food", YearMonth.of(2026, 10), "25")
            assertTrue(saved is EditResult.Saved)
            assertEquals(2_500L, assigned(remote.fileId))
            release.countDown()
            worker.join(10_000)
            assertFalse(worker.isAlive)
            sync.sync(remote.fileId)
            assertTrue(
                server.plainMessages(remote.fileId).any { it.column == "amount" && it.value == "2500" },
            )
        }
    }

    @Test
    fun wrongBudgetPasswordOpensNothing() {
        scenario {
            val remote = server.addEncrypted("Secret", budgetBytes(), "budget-pass")
            val kept = (library.create("Trip", "JPY") as CreateResult.Created).id
            val before = File(root, "$kept/db.sqlite").readBytes()
            assertNull(sync.connect(server.baseUrl, "server-secret").error)
            val failed = sync.unlock(remote, "violet-quartz-991", askEachTime = true)
            assertTrue(failed is UnlockResult.Failed)
            assertEquals(SyncCopy.PASSWORD_WRONG, (failed as UnlockResult.Failed).reason)
            assertEquals(OpenResult.NeedsPassword, sync.open(remote))
            assertFalse(File(root, remote.fileId).exists())
            assertTrue(File(root, "$kept/db.sqlite").readBytes().contentEquals(before))
            assertPasswordAbsent("violet-quartz-991")
            assertNull(secrets.get("budget.key.${remote.fileId}"))
        }
    }

    @Test
    fun rightPasswordOpensTheEncryptedBudgetAndAskEachTimeLocksIt() {
        scenario {
            val remote = server.addEncrypted("Secret", budgetBytes(), "violet-quartz-991")
            assertNull(sync.connect(server.baseUrl, "server-secret").error)
            assertTrue(sync.unlock(remote, "violet-quartz-991", askEachTime = true) is UnlockResult.Unlocked)
            assertEquals(OpenResult.Opened, sync.open(remote))
            assertEquals(10_000L, header(remote.fileId))
            assertPasswordAbsent("violet-quartz-991")
            assertNull(secrets.get("budget.key.${remote.fileId}"))
            assertFalse(sync.needsPassword(remote.fileId))
            sync.lock(remote.fileId)
            assertTrue(sync.needsPassword(remote.fileId))
            val restarted = restart()
            assertTrue(restarted.needsPassword(remote.fileId))
            val again = restarted.unlock(remote, "violet-quartz-991", askEachTime = false)
            assertTrue(again is UnlockResult.Unlocked)
            assertFalse(restarted.needsPassword(remote.fileId))
            restarted.lock(remote.fileId)
            assertFalse(restarted.needsPassword(remote.fileId))
            assertNotEquals(null, secrets.get("budget.key.${remote.fileId}"))
        }
    }

    @Test
    fun signOutDropsTheServerListAndLeavesThePhoneBudget() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            val phone = (library.create("Phone", "USD") as CreateResult.Created).id
            assertNull(sync.connect(server.baseUrl, "server-secret").error)
            assertTrue(sync.remoteFiles.isNotEmpty())
            sync.signOut()
            assertFalse(sync.signedIn())
            assertTrue(sync.remoteFiles.isEmpty())
            assertEquals("Phone", library.readShell(phone)!!.budgetName)
            assertEquals(OpenResult.Failed(SyncCopy.SIGN_IN_AGAIN), sync.open(remote))
        }
    }

    @Test
    fun uploadCreatesANewFileUnlessThePersonPickedOne() {
        scenario {
            val desktop = server.addFile("Desktop", budgetBytes())
            val before = server.bytes(desktop.fileId)
            val phone = (library.create("Phone only", "USD") as CreateResult.Created).id
            assertNull(sync.connect(server.baseUrl, "server-secret").error)
            val created = sync.uploadNew(phone)
            assertTrue(created is UploadResult.Uploaded)
            val createdId = (created as UploadResult.Uploaded).fileId
            assertNotEquals(desktop.fileId, createdId)
            assertTrue(server.bytes(desktop.fileId).contentEquals(before))
            assertTrue(server.bytes(createdId).isNotEmpty())
            val replaced = sync.uploadOnto(phone, desktop)
            assertTrue(replaced is UploadResult.Uploaded)
            assertEquals(desktop.fileId, (replaced as UploadResult.Uploaded).fileId)
            assertFalse(server.bytes(desktop.fileId).contentEquals(before))
        }
    }

    @Test
    fun switchingFilesDoesNotMergeThem() {
        scenario {
            val home = server.addFile("Home", budgetBytes())
            val trip = server.addFile("Trip", emptyBytes("JPY"))
            assertEquals(OpenResult.Opened, openSignedIn(home))
            assertEquals(OpenResult.Opened, sync.open(trip))
            assertEquals("Trip", library.readShell(trip.fileId)!!.budgetName)
            assertTrue(library.readShell(trip.fileId)!!.groups.isEmpty())
            assertEquals(
                "Groceries",
                library.useDatabase(home.fileId) { session ->
                    session.query("SELECT name FROM categories WHERE id = ?", listOf("c-food")).first().str("name")
                },
            )
            assertEquals(listOf("Home", "Trip"), library.list().map { it.name })
        }
    }

    @Test
    fun emptyServerListAndRejectedTokenAskToSignInAgain() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            server.rejectToken = true
            assertEquals(SyncCopy.SIGN_IN_AGAIN, sync.sync(remote.fileId).status)
            assertFalse(sync.signedIn())
            assertEquals(SyncCopy.SIGN_IN_AGAIN, sync.status(remote.fileId))
            val empty = LocalActualServer()
            try {
                val fresh = freshPhone()
                val listed = fresh.sync.connect(empty.baseUrl, "server-secret")
                assertNull(listed.error)
                assertTrue(listed.files.isEmpty())
                fresh.close()
            } finally {
                empty.close()
            }
        }
    }

    @Test
    fun downloadProgressIgnoresASmallerCountUntilFailure() {
        val tracker = DownloadTracker()
        tracker.onBytes(10)
        tracker.onBytes(4)
        assertEquals(10L, tracker.received)
        tracker.onBytes(40)
        assertEquals(40L, tracker.received)
        tracker.fail()
        assertEquals(0L, tracker.received)
        assertEquals(0.4f, syncFraction(40, 100), 0.001f)
    }

    private fun openSignedIn(file: RemoteFile): OpenResult {
        val listed = sync.connect(server.baseUrl, "server-secret")
        assertNull(listed.error)
        return sync.open(file)
    }

    private fun header(id: String): Long = library.readShell(id)!!.headerMinor

    private fun assigned(id: String): Long =
        library.useDatabase(id) { session ->
            session
                .query("SELECT amount FROM zero_budgets WHERE id = ?", listOf("202610-c-food"))
                .firstOrNull()
                ?.long("amount") ?: 0L
        } ?: 0L

    private fun cellValues(
        id: String,
        row: String,
        column: String,
    ): List<String> =
        library.useDatabase(id) { session ->
            session
                .query(
                    "SELECT value FROM messages WHERE row = ? AND column = ?",
                    listOf(row, column),
                ).map { it.str("value").orEmpty() }
        } ?: emptyList()

    private fun messageCount(id: String): Int =
        library.useDatabase(id) { session ->
            session
                .query("SELECT COUNT(*) AS n FROM messages")
                .first()
                .long("n")
                .toInt()
        } ?: 0

    private fun budgetDirs(): Set<String> =
        root
            .listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()

    private fun assertPasswordAbsent(password: String) {
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            val text = file.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(file.path, text.contains(password))
        }
        secrets.snapshot().values.forEach { value ->
            assertFalse(value.contains(password))
        }
    }

    private lateinit var root: File
    private lateinit var secrets: MemorySecretStore
    private lateinit var server: LocalActualServer
    private lateinit var library: BudgetLibrary
    private lateinit var sync: SyncCoordinator
    private lateinit var opened: Scenario
    private val extras = mutableListOf<Scenario>()

    private fun restart(): SyncCoordinator {
        sync = opened.coordinator()
        return sync
    }

    private fun scenario(block: SyncCoordinatorTest.() -> Unit) {
        opened = Scenario()
        root = opened.root
        secrets = opened.secrets
        server = opened.server
        library = opened.library()
        sync = opened.coordinator()
        try {
            block()
        } finally {
            extras.forEach { it.close() }
            opened.close()
        }
    }

    private fun freshPhone(): Scenario {
        val extra = Scenario(server)
        extras.add(extra)
        return extra
    }

    private class Scenario(
        shared: LocalActualServer? = null,
    ) : AutoCloseable {
        val server = shared ?: LocalActualServer()
        private val ownsServer = shared == null
        val root: File =
            File.createTempFile("sync", "").apply {
                delete()
                mkdirs()
            }
        val secrets = MemorySecretStore()
        private val today = LocalDate.of(2026, 10, 7)
        private val now = Instant.parse("2026-10-07T15:04:00Z").toEpochMilli()

        fun library(): BudgetLibrary = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { today })

        fun coordinator(): SyncCoordinator =
            SyncCoordinator(
                library(),
                HttpActualTransport(),
                secrets,
                BudgetClock { today },
                ZoneOffset.UTC,
                now = { now },
            )

        val sync: SyncCoordinator by lazy { coordinator() }

        fun header(id: String): Long = library().readShell(id)!!.headerMinor

        override fun close() {
            if (ownsServer) server.close()
            root.deleteRecursively()
        }
    }
}

private fun budgetBytes(): ByteArray = sqliteBytes(income = true, currency = "USD")

private fun emptyBytes(currency: String): ByteArray = sqliteBytes(income = false, currency = currency)

private fun sqliteBytes(
    income: Boolean,
    currency: String,
): ByteArray {
    val file = File.createTempFile("budget", ".sqlite")
    file.delete()
    JdbcSessionOpener().use(file) { session ->
        ActualSchema.create(session)
        session.exec("INSERT INTO preferences (id, value) VALUES (?, ?)", listOf("budgetType", "envelope"))
        session.exec("INSERT INTO preferences (id, value) VALUES (?, ?)", listOf("currency", currency))
        if (income) {
            session.exec(
                "INSERT INTO category_groups (id, name, is_income, sort_order) VALUES (?, ?, ?, ?)",
                listOf("g-inc", "Income", 1, 0),
            )
            session.exec(
                "INSERT INTO category_groups (id, name, is_income, sort_order) VALUES (?, ?, ?, ?)",
                listOf("g-food", "Food", 0, 1),
            )
            session.exec(
                "INSERT INTO categories (id, name, is_income, cat_group) VALUES (?, ?, ?, ?)",
                listOf("c-sal", "Salary", 1, "g-inc"),
            )
            session.exec(
                "INSERT INTO categories (id, name, is_income, cat_group) VALUES (?, ?, ?, ?)",
                listOf("c-food", "Groceries", 0, "g-food"),
            )
            session.exec(
                "INSERT INTO accounts (id, name, offbudget) VALUES (?, ?, ?)",
                listOf("checking", "Checking", 0),
            )
            session.exec(
                """
                INSERT INTO transactions (id, acct, category, amount, date)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                listOf("t-pay", "checking", "c-sal", 10_000L, 20261007),
            )
        }
    }
    return file.readBytes().also { file.delete() }
}
