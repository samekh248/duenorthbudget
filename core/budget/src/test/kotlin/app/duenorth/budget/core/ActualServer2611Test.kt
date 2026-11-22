package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What Actual server 26.11 changed for the phone: bank credentials kept per budget (`X-Actual-File-Id`), a session
 * per sign-in that sign-out now ends (`/account/logout`), and 403s that name their reason.
 */
class ActualServer2611Test {
    @Test
    fun bankCallsNameTheOpenBudget() {
        withPhone { server, phone, budgetId ->
            server.configured["simplefin"] = true
            server.providerData["/simplefin/accounts"] = """{"accounts":[]}"""
            phone.sync.bankCall { it.configured() }.getOrThrow()
            phone.sync.bankCall { it.accounts(BankProvider.SIMPLEFIN) }.getOrThrow()
            assertTrue(server.providerFileIds.isNotEmpty())
            assertEquals(listOf(budgetId), server.providerFileIds.distinct())
        }
    }

    @Test
    fun bankFetchNamesTheBudget() {
        withPhone { server, phone, _ ->
            server.providerData["/simplefin/transactions"] = """{"transactions":{"all":[]}}"""
            HttpBankSyncTransport().forFile("budget-a").fetchTransactions(server.baseUrl, phone.sync.syncToken()!!, "ACT-1", "2026-08-01")
            HttpBankSyncTransport().fetchTransactions(server.baseUrl, phone.sync.syncToken()!!, "ACT-1", "2026-08-01")
            assertEquals(listOf("budget-a", null), server.providerFileIds.toList())
        }
    }

    @Test
    fun unattendedBankSyncNamesTheBudget() {
        withPhone { server, phone, budgetId ->
            server.providerData["/simplefin/accounts"] =
                """{"accounts":[{"id":"ACT-1","name":"Everyday","balance":"1.00","org":{"name":"First Bank","domain":"fb.example"}}]}"""
            server.providerData["/simplefin/transactions"] = """{"ACT-1":{"transactions":{"all":[]}}}"""
            val external =
                phone.sync
                    .bankCall { it.accounts(BankProvider.SIMPLEFIN) }
                    .getOrThrow()
                    .single()
            phone.sync.linkBankAccount(budgetId, "checking", BankProvider.SIMPLEFIN, external)
            server.providerFileIds.clear()
            server.providerCalls.clear()
            phone.sync.syncBanks(budgetId)
            val fetches = server.providerCalls.indices.filter { server.providerCalls[it].first == "/simplefin/transactions" }
            assertEquals(1, fetches.size)
            assertEquals(budgetId, server.providerFileIds[fetches.single()])
        }
    }

    @Test
    fun simpleFinTokenGoesServerWideByDefault() {
        withPhone { server, phone, _ ->
            phone.sync.bankCall { it.saveSimpleFinToken(TOKEN) }.getOrThrow()
            assertEquals(TOKEN, server.secrets["simplefin_token"])
            assertTrue(server.fileSecrets.isEmpty())
        }
    }

    @Test
    fun simpleFinTokenStaysWithTheBudgetThatHasItsOwn() {
        withPhone { server, phone, budgetId ->
            server.simpleFinSource = "per-budget-file"
            server.secrets["simplefin_token"] = "server-wide"
            phone.sync.bankCall { it.saveSimpleFinToken(TOKEN) }.getOrThrow()
            assertEquals(TOKEN, server.fileSecrets["$budgetId/simplefin_token"])
            assertEquals("server-wide", server.secrets["simplefin_token"])
        }
    }

    @Test
    fun simpleFinTokenGoesToTheBudgetWhenOnlyTheAdminMayChangeServerWide() {
        withPhone { server, phone, budgetId ->
            server.secretsAdminOnly = true
            phone.sync.bankCall { it.saveSimpleFinToken(TOKEN) }.getOrThrow()
            assertEquals(TOKEN, server.fileSecrets["$budgetId/simplefin_token"])
            assertNull(server.secrets["simplefin_token"])
        }
    }

    @Test
    fun aRefusedBudgetSaysSoInsteadOfSignInAgain() {
        withPhone { server, phone, budgetId ->
            server.deniedFiles += budgetId
            val linking = phone.sync.bankCall { it.accounts(BankProvider.SIMPLEFIN) }
            assertEquals(BankAccessCopy.FILE_DENIED, linking.exceptionOrNull()?.message)
            val fetching =
                runCatching {
                    HttpBankSyncTransport()
                        .forFile(budgetId)
                        .fetchTransactions(server.baseUrl, phone.sync.syncToken()!!, "ACT-1", "2026-08-01")
                }.exceptionOrNull() as BankSyncException
            assertEquals(BankAccessCopy.FILE_DENIED, fetching.message)
            assertNull(fetching.fix)
            assertTrue(phone.sync.signedIn())
        }
    }

    @Test
    fun signOutEndsTheSessionOnTheServer() {
        withPhone { server, phone, _ ->
            val token = phone.sync.syncToken()!!
            phone.sync.signOut()
            assertEquals(listOf(token), server.logouts.toList())
            assertFalse(phone.sync.signedIn())
        }
    }

    @Test
    fun signOutStillWorksOnAnOlderServerOrOffline() {
        withPhone { server, phone, _ ->
            server.logoutRoute = false
            phone.sync.signOut()
            assertFalse(phone.sync.signedIn())
        }
        withPhone { server, phone, _ ->
            server.online = false
            phone.sync.signOut()
            assertFalse(phone.sync.signedIn())
        }
    }

    @Test
    fun removingTheServerEndsItsSession() {
        withPhone { server, phone, _ ->
            val token = phone.sync.syncToken()!!
            val id = phone.library.settings().activeInstanceId!!
            phone.sync.removeInstance(id)
            assertEquals(listOf(token), server.logouts.toList())
        }
    }

    private fun withPhone(block: (LocalActualServer, SyncPhone, String) -> Unit) {
        LocalActualServer().use { server ->
            val remote = server.addFile("Home", phoneFile())
            SyncPhone(server).use { phone ->
                phone.openSignedIn(remote)
                block(server, phone, remote.fileId)
            }
        }
    }

    private companion object {
        const val TOKEN = "aHR0cHM6Ly9icmlkZ2Uuc2ltcGxlZmluLm9yZy9zaW1wbGVmaW4vY2xhaW0vVEVTVA"
    }
}
