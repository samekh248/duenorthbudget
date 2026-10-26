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
            assertTrue(messages.any { it.row == "202610-c-food" && it.column == "amount" && it.value == "N:2500" })
            val other = freshPhone()
            assertNull(other.sync.connect(server.baseUrl, "server-secret").error)
            assertEquals(OpenResult.Opened, other.sync.open(remote))
            assertEquals(7_500L, other.header(remote.fileId))
            other.close()
        }
    }

    @Test
    fun collisionKeepsBothAmountsAndShowsTheNewerOneLikeDesktop() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            server.online = false
            assertTrue(sync.assign(remote.fileId, "c-food", YearMonth.of(2026, 10), "25") is EditResult.Saved)
            // The server's change is the later one, so Actual on desktop shows 40.
            server.appendChange(remote.fileId, "zero_budgets", "202610-c-food", "amount", "N:4000")
            server.online = true
            sync.sync(remote.fileId)
            val conflicts = sync.conflicts(remote.fileId)
            assertEquals(1, conflicts.size)
            assertEquals("Groceries", conflicts.single().label)
            assertEquals("$25.00", conflicts.single().localText)
            assertEquals("$40.00", conflicts.single().remoteText)
            assertFalse(conflicts.single().phoneKept)
            assertEquals(4_000L, assigned(remote.fileId))
            val values = cellValues(remote.fileId, "202610-c-food", "amount")
            assertTrue(values.contains("N:2500"))
            assertTrue(values.contains("N:4000"))
            sync.markConflictsSeen(remote.fileId)
            assertEquals("$25.00", sync.conflicts(remote.fileId).single().localText)
            // The server stored the phone's 25; it is not sent again on every sync.
            assertEquals(0, pendingCount(remote.fileId))
            sync.sync(remote.fileId)
            assertTrue(
                server.syncRequests
                    .last()
                    .messages
                    .isEmpty(),
            )
            val other = freshPhone()
            assertNull(other.sync.connect(server.baseUrl, "server-secret").error)
            assertEquals(OpenResult.Opened, other.sync.open(remote))
            assertEquals(4_000L, other.library().useDatabase(remote.fileId) { amountOf(it) })
        }
    }

    @Test
    fun collisionWithAnOlderServerChangeKeepsThePhoneAmount() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            assertTrue(sync.assign(remote.fileId, "c-food", YearMonth.of(2026, 10), "25") is EditResult.Saved)
            server.appendChangeAt(remote.fileId, EARLIER, "zero_budgets", "202610-c-food", "amount", "N:4000")
            sync.sync(remote.fileId)
            val conflict = sync.conflicts(remote.fileId).single()
            assertTrue(conflict.phoneKept)
            assertEquals(2_500L, assigned(remote.fileId))
            assertEquals(0, pendingCount(remote.fileId))
        }
    }

    @Test
    fun backgroundSyncPicksUpAnotherDevicesChange() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            server.appendChange(remote.fileId, "zero_budgets", "202610-c-food", "amount", "N:1234")
            assertEquals("synced 3:04 pm", sync.syncUnattended()?.status)
            assertEquals(1_234L, assigned(remote.fileId))
        }
    }

    @Test
    fun backgroundSyncFetchesLinkedBanksThenSendsThemToTheServer() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            library.useDatabase(remote.fileId) { session ->
                session.exec(
                    "UPDATE accounts SET account_sync_source = ?, account_id = ? WHERE id = ?",
                    listOf("simpleFin", "bank-checking", "checking"),
                )
            }
            opened.bank.rows =
                listOf(
                    ParsedImportRow(20261005, "Corner Shop", -1_250L, financialId = "sf-1"),
                    ParsedImportRow(20261006, "Bakery", -800L, financialId = "sf-2"),
                )
            assertEquals("synced 3:04 pm", sync.syncUnattended()?.status)
            assertEquals(listOf("bank-checking"), opened.bank.asked)
            assertEquals(setOf("sf-1", "sf-2"), financialIds(remote.fileId))
            val sent = server.plainMessages(remote.fileId)
            assertTrue(sent.any { it.dataset == "transactions" && it.column == "financial_id" && it.value == "S:sf-1" })
            assertEquals(0, pendingCount(remote.fileId))
            // The next run finds both already there and adds nothing.
            sync.syncUnattended()
            assertEquals(setOf("sf-1", "sf-2"), financialIds(remote.fileId))
            assertEquals(2, opened.bank.asked.size)
        }
    }

    @Test
    fun aFailedBankFetchStillSyncsTheBudget() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            library.useDatabase(remote.fileId) { session ->
                session.exec(
                    "UPDATE accounts SET account_sync_source = ?, account_id = ? WHERE id = ?",
                    listOf("simpleFin", "bank-checking", "checking"),
                )
            }
            opened.bank.fails = true
            server.appendChange(remote.fileId, "zero_budgets", "202610-c-food", "amount", "N:1234")
            assertEquals("synced 3:04 pm", sync.syncUnattended()?.status)
            assertEquals(1_234L, assigned(remote.fileId))
        }
    }

    @Test
    fun backgroundSyncLeavesAPhoneOnlyBudgetAlone() {
        scenario {
            val kept = (library.create("Trip", "JPY") as CreateResult.Created).id
            assertNull(sync.syncUnattended())
            assertNull(sync.syncBanksThenBudget(kept))
            assertTrue(server.syncRequests.isEmpty())
            assertTrue(opened.bank.asked.isEmpty())
            assertEquals("", sync.status(kept))
        }
    }

    @Test
    fun backgroundSyncWaitsForTheBudgetPassword() {
        scenario {
            val remote = server.addEncrypted("Secret", budgetBytes(), "violet-quartz-991")
            assertNull(sync.connect(server.baseUrl, "server-secret").error)
            assertTrue(sync.unlock(remote, "violet-quartz-991", askEachTime = true) is UnlockResult.Unlocked)
            assertEquals(OpenResult.Opened, sync.open(remote))
            val before = server.syncRequests.size
            sync.lock(remote.fileId)
            assertNull(sync.syncUnattended())
            assertEquals(before, server.syncRequests.size)
        }
    }

    @Test
    fun aLocalChangeTellsTheAppAndIsSentOnce() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            val told = mutableListOf<String>()
            library.onLocalChange = { told.add(it) }
            val opened = server.syncRequests.size
            assertNull(sync.sendUnsent(remote.fileId))
            assertEquals(opened, server.syncRequests.size)
            assigned(remote.fileId)
            assertEquals(emptyList<String>(), told)
            library.saveCategoryNote(remote.fileId, "c-food", "#template 50")
            assertEquals(listOf(remote.fileId), told)
            assertEquals("synced 3:04 pm", sync.sendUnsent(remote.fileId)?.status)
            val sent = server.syncRequests.size
            assertTrue(sent > opened)
            assertEquals(0, pendingCount(remote.fileId))
            assertNull(sync.sendUnsent(remote.fileId))
            assertEquals(sent, server.syncRequests.size)
        }
    }

    @Test
    fun sendUnsentLeavesPhoneOnlyAndLockedBudgetsAlone() {
        scenario {
            val kept = (library.create("Trip", "JPY") as CreateResult.Created).id
            library.saveCategoryNote(kept, "c-food", "#template 50")
            assertNull(sync.sendUnsent(kept))
            val remote = server.addEncrypted("Secret", budgetBytes(), "violet-quartz-991")
            assertNull(sync.connect(server.baseUrl, "server-secret").error)
            assertTrue(sync.unlock(remote, "violet-quartz-991", askEachTime = true) is UnlockResult.Unlocked)
            assertEquals(OpenResult.Opened, sync.open(remote))
            assertTrue(sync.locksWhenAway(remote.fileId))
            assertTrue(sync.assign(remote.fileId, "c-food", YearMonth.of(2026, 10), "25") is EditResult.Saved)
            val before = server.syncRequests.size
            sync.lock(remote.fileId)
            assertNull(sync.sendUnsent(remote.fileId))
            assertEquals(before, server.syncRequests.size)
        }
    }

    @Test
    fun changeAnOfflineDeviceSendsLateStillArrives() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            // Another device syncs a change; the phone has now seen up to its stamp.
            server.appendChange(remote.fileId, "zero_budgets", "202611-c-food", "amount", "N:100")
            assertEquals("synced 3:04 pm", sync.sync(remote.fileId).status)
            // A third device was offline and only now sends a change it made earlier.
            server.appendChangeAt(remote.fileId, EARLIER, "zero_budgets", "202610-c-food", "amount", "N:1234")
            assertEquals("synced 3:04 pm", sync.sync(remote.fileId).status)
            assertEquals(1_234L, assigned(remote.fileId))
            assertEquals(server.timestamps(remote.fileId).toSet(), allTimestamps(remote.fileId).toSet())
        }
    }

    @Test
    fun lateOlderChangeNeverOverwritesANewerAmount() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            assertTrue(sync.assign(remote.fileId, "c-food", YearMonth.of(2026, 10), "25") is EditResult.Saved)
            sync.sync(remote.fileId)
            server.appendChangeAt(remote.fileId, EARLIER, "zero_budgets", "202610-c-food", "amount", "N:9900")
            sync.sync(remote.fileId)
            assertEquals(2_500L, assigned(remote.fileId))
            assertTrue(cellValues(remote.fileId, "202610-c-food", "amount").contains("N:9900"))
            assertTrue(sync.conflicts(remote.fileId).isEmpty())
        }
    }

    @Test
    fun messagesTheServerLostAreSentAgain() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            assertTrue(sync.assign(remote.fileId, "c-food", YearMonth.of(2026, 10), "25") is EditResult.Saved)
            sync.sync(remote.fileId)
            // Restored from a backup taken before the phone's change reached it.
            server.forgetMessages(remote.fileId) { false }
            assertEquals("synced 3:04 pm", sync.sync(remote.fileId).status)
            assertTrue(server.plainMessages(remote.fileId).any { it.row == "202610-c-food" && it.value == "N:2500" })
        }
    }

    @Test
    fun droppedReplyIsRetriedAndStoredOnce() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            assertTrue(sync.assign(remote.fileId, "c-food", YearMonth.of(2026, 10), "25") is EditResult.Saved)
            // Two, since HttpURLConnection itself tries a POST once more when the connection drops.
            server.dropReplies = 2
            assertEquals("synced 3:04 pm", sync.sync(remote.fileId).status)
            assertEquals(listOf(1_000L), opened.pauses)
            assertEquals(0, pendingCount(remote.fileId))
            val stamps = server.timestamps(remote.fileId)
            assertEquals(stamps.size, stamps.toSet().size)
            assertEquals(1, server.plainMessages(remote.fileId).count { it.row == "202610-c-food" && it.column == "amount" })
        }
    }

    @Test
    fun repliesLostEveryTimeKeepTheChangeUntilTheNextSync() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            assertTrue(sync.assign(remote.fileId, "c-food", YearMonth.of(2026, 10), "25") is EditResult.Saved)
            val waiting = pendingCount(remote.fileId)
            server.dropReplies = Int.MAX_VALUE
            assertEquals(SyncCopy.LAST_FAILED, sync.sync(remote.fileId).status)
            assertEquals(listOf(1_000L, 3_000L), opened.pauses)
            assertEquals(waiting, pendingCount(remote.fileId))
            assertEquals(2_500L, assigned(remote.fileId))
            server.dropReplies = 0
            restart()
            assertEquals("synced 3:04 pm", sync.sync(remote.fileId).status)
            assertEquals(0, pendingCount(remote.fileId))
            val stamps = server.timestamps(remote.fileId)
            assertEquals(stamps.size, stamps.toSet().size)
        }
    }

    @Test
    fun badGatewayIsRetried() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            assertTrue(sync.assign(remote.fileId, "c-food", YearMonth.of(2026, 10), "25") is EditResult.Saved)
            server.badGateways = 1
            assertEquals("synced 3:04 pm", sync.sync(remote.fileId).status)
            assertEquals(1, opened.pauses.size)
            assertEquals(0, pendingCount(remote.fileId))
        }
    }

    @Test
    fun budgetResetOnTheServerSaysSoAndKeepsTheChange() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            assertTrue(sync.assign(remote.fileId, "c-food", YearMonth.of(2026, 10), "25") is EditResult.Saved)
            server.resetGroup(remote.fileId)
            assertEquals(SyncCopy.SERVER_RESET, sync.sync(remote.fileId).status)
            assertEquals(SyncCopy.SERVER_RESET, sync.status(remote.fileId))
            assertTrue(opened.pauses.isEmpty())
            assertTrue(pendingCount(remote.fileId) > 0)
            assertEquals(2_500L, assigned(remote.fileId))
        }
    }

    @Test
    fun editsStillSaveAfterAMessageFromAFastClock() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            // The server's clock is days ahead of this phone's.
            server.appendChange(remote.fileId, "zero_budgets", "202610-c-food", "amount", "N:4000")
            sync.sync(remote.fileId)
            assertEquals(4_000L, assigned(remote.fileId))
            assertTrue(sync.assign(remote.fileId, "c-food", YearMonth.of(2026, 10), "25") is EditResult.Saved)
            assertEquals("synced 3:04 pm", sync.sync(remote.fileId).status)
            assertEquals(2_500L, assigned(remote.fileId))
            val other = freshPhone()
            assertNull(other.sync.connect(server.baseUrl, "server-secret").error)
            assertEquals(OpenResult.Opened, other.sync.open(remote))
            assertEquals(2_500L, other.library().useDatabase(remote.fileId) { amountOf(it) })
        }
    }

    @Test
    fun openingTheSameBudgetAgainKeepsChangesTheServerHasNotSeen() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            server.online = false
            assertTrue(sync.assign(remote.fileId, "c-food", YearMonth.of(2026, 10), "25") is EditResult.Saved)
            server.online = true
            server.dropReplies = Int.MAX_VALUE
            // The download works; the sync after it does not reach the server.
            assertEquals(OpenResult.Opened, sync.open(remote, replace = true))
            assertEquals(2_500L, assigned(remote.fileId))
            assertTrue(pendingCount(remote.fileId) > 0)
            server.dropReplies = 0
            assertEquals("synced 3:04 pm", sync.sync(remote.fileId).status)
            assertTrue(server.plainMessages(remote.fileId).any { it.row == "202610-c-food" && it.value == "N:2500" })
        }
    }

    @Test
    fun uploadWhoseReplyWasLostDoesNotMakeASecondCopy() {
        scenario {
            val local = (library.create("Trip", "USD") as CreateResult.Created).id
            assertNull(sync.connect(server.baseUrl, "server-secret").error)
            server.dropUploads = 1
            assertTrue(sync.uploadNew(local) is UploadResult.Failed)
            val again = sync.uploadNew(local)
            assertTrue(again is UploadResult.Uploaded)
            assertEquals(1, sync.refreshRemote().files.size)
            assertEquals((again as UploadResult.Uploaded).fileId, sync.remoteFiles.single().fileId)
            assertEquals("synced 3:04 pm", sync.sync(local).status)
        }
    }

    @Test
    fun overlappingSyncsSendEachChangeOnce() {
        scenario {
            val remote = server.addFile("Home", budgetBytes())
            assertEquals(OpenResult.Opened, openSignedIn(remote))
            assertTrue(sync.assign(remote.fileId, "c-food", YearMonth.of(2026, 10), "25") is EditResult.Saved)
            val workers = List(4) { Thread { sync.sync(remote.fileId) } }
            workers.forEach(Thread::start)
            workers.forEach { it.join(10_000) }
            val carrying = server.syncRequests.count { request -> request.messages.isNotEmpty() }
            assertEquals(1, carrying)
            assertEquals(0, pendingCount(remote.fileId))
            assertEquals("synced 3:04 pm", sync.status(remote.fileId))
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
                server.plainMessages(remote.fileId).any { it.column == "amount" && it.value == "N:2500" },
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
    fun pickingAServerFileReplacesTheBudgetsOnThePhone() {
        scenario {
            val local = (library.create("Trip", "JPY") as CreateResult.Created).id
            val home = server.addFile("Home", budgetBytes())
            assertNull(sync.connect(server.baseUrl, "server-secret").error)
            assertEquals(OpenResult.Opened, sync.open(home, replace = true))
            assertEquals(listOf("Home"), library.list().map { it.name })
            assertFalse(File(root, local).exists())
            assertEquals(home.fileId, library.settings().openBudgetId)
            // The server keeps its own copy of what was opened.
            assertTrue(server.bytes(home.fileId).isNotEmpty())
        }
    }

    @Test
    fun actualsZippedFileOpensAndUploadsGoBackZipped() {
        scenario {
            // Actual's own client stores a zip of db.sqlite and metadata.json on the server.
            val zipped = ActualFileFormat.pack(budgetBytes(), "desktop-id", "Home", "unused", null)
            val home = server.addFile("Home", zipped)
            assertNull(sync.connect(server.baseUrl, "server-secret").error)
            assertEquals(OpenResult.Opened, sync.open(home, replace = true))
            assertEquals("Home", library.readShell(home.fileId)!!.budgetName)

            val sent = sync.uploadNew(home.fileId) as UploadResult.Uploaded
            val uploaded = server.bytes(sent.fileId)
            assertTrue(ActualFileFormat.isZip(uploaded))
            assertTrue(ActualFileFormat.database(uploaded).isNotEmpty())
        }
    }

    @Test
    fun failedDownloadClearsNothing() {
        scenario {
            val local = (library.create("Trip", "JPY") as CreateResult.Created).id
            val home = server.addFile("Home", budgetBytes())
            assertNull(sync.connect(server.baseUrl, "server-secret").error)
            server.online = false
            assertEquals(OpenResult.Failed(SyncCopy.ADDRESS_FAILED), sync.open(home, replace = true))
            assertEquals(listOf("Trip"), library.list().map { it.name })
            assertEquals(local, library.settings().openBudgetId)
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

    private fun financialIds(id: String): Set<String> =
        library
            .useDatabase(id) { session ->
                session
                    .query("SELECT financial_id FROM transactions WHERE financial_id IS NOT NULL AND IFNULL(tombstone, 0) = 0")
                    .mapNotNull { it.str("financial_id") }
                    .toSet()
            }.orEmpty()

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

    private fun pendingCount(id: String): Int =
        library.useDatabase(id) { session ->
            session
                .query("SELECT COUNT(*) AS n FROM messages WHERE pending = 1")
                .first()
                .long("n")
                .toInt()
        } ?: 0

    private fun allTimestamps(id: String): List<String> =
        library.useDatabase(id) { session ->
            session.query("SELECT timestamp FROM messages").mapNotNull { it.str("timestamp") }
        } ?: emptyList()

    private fun amountOf(session: SqlSession): Long =
        session
            .query("SELECT amount FROM zero_budgets WHERE id = ?", listOf("202610-c-food"))
            .firstOrNull()
            ?.long("amount") ?: 0L

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

        /** Each wait before a retried sync, instead of sleeping. */
        val pauses: MutableList<Long> = java.util.Collections.synchronizedList(mutableListOf())
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
                pause = { pauses.add(it) },
                bankTransport = bank,
            )

        /** What the bank provider returns to the unattended bank sync. */
        val bank = RecordingBankTransport()

        val sync: SyncCoordinator by lazy { coordinator() }

        fun header(id: String): Long = library().readShell(id)!!.headerMinor

        override fun close() {
            if (ownsServer) server.close()
            root.deleteRecursively()
        }
    }
}

/** A bank provider that hands back [rows], or fails, and remembers which bank accounts it was asked for. */
private class RecordingBankTransport : BankSyncTransport {
    var rows: List<ParsedImportRow> = emptyList()
    var fails = false
    val asked = mutableListOf<String>()

    override fun fetchTransactions(
        serverAddress: String,
        token: String,
        bankAccountId: String,
        startDateIso: String,
    ): List<ParsedImportRow> {
        asked.add(bankAccountId)
        if (fails) throw BankSyncException(ImportCopy.FETCH_FAILED)
        return rows
    }
}

/** Before anything the phone writes at its fixed 3:04 pm, from a device that was offline. */
private const val EARLIER = "2026-10-07T14:00:00.000Z-0000-0FF11E0000000001"

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
