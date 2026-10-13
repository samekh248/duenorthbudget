package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 010 T064: bank link against a fake provider on the local server (`contracts/bank-link.md`). */
class BankLinkBookTest {
    @Test
    fun statusListsOnlyConfiguredProviders() {
        withPhone { server, phone, _ ->
            server.configured["gocardless"] = true
            server.configured["pluggyai"] = false
            assertEquals(listOf(BankProvider.GOCARDLESS), phone.sync.bankCall { it.configured() }.getOrThrow())
        }
    }

    @Test
    fun simpleFinTokenGoesOnlyToTheSecretRoute() {
        withPhone { server, phone, budgetId ->
            phone.sync.bankCall { it.saveSimpleFinToken(" $SETUP_TOKEN ") }.getOrThrow()
            assertEquals(SETUP_TOKEN, server.secrets["simplefin_token"])
            assertEquals(listOf(BankProvider.SIMPLEFIN), phone.sync.bankCall { it.configured() }.getOrThrow())
            phone.sync.sync(budgetId)
            val leaks =
                phone.root
                    .walkTopDown()
                    .filter { it.isFile && it.readBytes().toString(Charsets.ISO_8859_1).contains(SETUP_TOKEN) }
                    .toList()
            assertEquals(emptyList<java.io.File>(), leaks)
            val zip = phone.library.exportZip(budgetId)!!
            assertFalse(zip.toString(Charsets.ISO_8859_1).contains(SETUP_TOKEN))
            assertTrue(server.plainMessages(budgetId).none { it.value.contains(SETUP_TOKEN) })
        }
    }

    @Test
    fun linkWritesTheBankAndTheAccountColumnsAsOneStep() {
        withPhone { server, phone, budgetId ->
            server.providerData["/simplefin/accounts"] = SIMPLEFIN_ACCOUNTS
            val accounts = phone.sync.bankCall { it.accounts(BankProvider.SIMPLEFIN) }.getOrThrow()
            assertEquals(ExternalAccount("ACT-1", "Everyday", "First Bank", "firstbank.example", 123_456), accounts[0])
            assertEquals(BankLinkResult.Saved, phone.sync.linkBankAccount(budgetId, "checking", BankProvider.SIMPLEFIN, accounts[0]))
            assertEquals(UndoCopy.BANK_LINK, phone.library.undoLabel(budgetId))
            val account = row(phone, budgetId, "SELECT * FROM accounts WHERE id = 'checking'")
            assertEquals("ACT-1", account["account_id"])
            assertEquals("simpleFin", account["account_sync_source"])
            assertEquals(123_456L, account["balance_current"])
            assertNull(account["last_sync"])
            val bank = row(phone, budgetId, "SELECT * FROM banks WHERE id = '${account["bank"]}'")
            assertEquals("firstbank.example", bank["bank_id"])
            assertEquals("First Bank", bank["name"])
            assertEquals(BankProvider.SIMPLEFIN to "First Bank", phone.sync.bankLinkOf(budgetId, "checking"))
            // Spec 007's fetch now sees the account as linked.
            assertEquals(
                "ACT-1",
                phone.library
                    .useDatabase(budgetId) {
                        BankSyncBook(it, FakeBankSyncTransport(emptyList())).linkedAccount("checking")
                    }?.bankAccountId,
            )
            phone.sync.sync(budgetId)
            assertTrue(
                server.plainMessages(budgetId).any {
                    it.dataset == "accounts" && it.column == "account_sync_source" &&
                        it.value == "S:simpleFin"
                },
            )
        }
    }

    @Test
    fun replaceOverwritesAndReusesTheBankRow() {
        withPhone { server, phone, budgetId ->
            server.providerData["/simplefin/accounts"] = SIMPLEFIN_ACCOUNTS
            val accounts = phone.sync.bankCall { it.accounts(BankProvider.SIMPLEFIN) }.getOrThrow()
            phone.sync.linkBankAccount(budgetId, "checking", BankProvider.SIMPLEFIN, accounts[0])
            phone.sync.linkBankAccount(budgetId, "checking", BankProvider.SIMPLEFIN, accounts[1])
            assertEquals("ACT-2", row(phone, budgetId, "SELECT account_id FROM accounts WHERE id = 'checking'")["account_id"])
            assertEquals(1L, row(phone, budgetId, "SELECT COUNT(*) AS n FROM banks")["n"])
        }
    }

    @Test
    fun goCardlessLinksThroughARequisition() {
        withPhone { server, phone, budgetId ->
            server.providerData["/gocardless/get-banks"] = """[{"id":"BANK_GB","name":"Bank of Test"}]"""
            server.providerData["/gocardless/create-web-token"] = """{"link":"https://bank.example/consent","requisitionId":"req-1"}"""
            server.providerData["/gocardless/get-accounts"] =
                """{"accounts":[{"account_id":"gc-1","name":"Current","balance":10.5,"institution":{"name":"Bank of Test","id":"BANK_GB"}}]}"""
            val external =
                phone.sync
                    .bankCall { client ->
                        val bank = client.goCardlessBanks("gb").single()
                        val link = client.goCardlessStart(bank.id)
                        assertEquals("https://bank.example/consent", link.link)
                        client.goCardlessAccounts(link.requisitionId).single()
                    }.getOrThrow()
            assertEquals(ExternalAccount("gc-1", "Current", "Bank of Test", "req-1", 1_050), external)
            phone.sync.linkBankAccount(budgetId, "checking", BankProvider.GOCARDLESS, external)
            assertEquals(
                "goCardless",
                row(phone, budgetId, "SELECT account_sync_source FROM accounts WHERE id = 'checking'")["account_sync_source"],
            )
            assertEquals("req-1", row(phone, budgetId, "SELECT bank_id FROM banks")["bank_id"])
        }
    }

    @Test
    fun removeClearsTheLinkAndUndoRestoresIt() {
        withPhone { server, phone, budgetId ->
            server.providerData["/simplefin/accounts"] = SIMPLEFIN_ACCOUNTS
            val accounts = phone.sync.bankCall { it.accounts(BankProvider.SIMPLEFIN) }.getOrThrow()
            phone.sync.linkBankAccount(budgetId, "checking", BankProvider.SIMPLEFIN, accounts[0])
            assertEquals(BankLinkResult.Saved, phone.sync.unlinkBankAccount(budgetId, "checking"))
            val cleared = row(phone, budgetId, "SELECT account_id, bank, account_sync_source FROM accounts WHERE id = 'checking'")
            assertEquals(listOf(null, null, null), cleared.values.toList())
            assertNull(phone.sync.bankLinkOf(budgetId, "checking"))
            assertEquals(UndoResult.Undone(UndoCopy.BANK_LINK), phone.sync.undo(budgetId))
            assertEquals(BankProvider.SIMPLEFIN to "First Bank", phone.sync.bankLinkOf(budgetId, "checking"))
        }
    }

    @Test
    fun unreachableServerWritesNothing() {
        withPhone { server, phone, budgetId ->
            server.providerData["/simplefin/accounts"] = SIMPLEFIN_ACCOUNTS
            val before = row(phone, budgetId, "SELECT * FROM accounts WHERE id = 'checking'")
            server.online = false
            val failed = phone.sync.bankCall { it.accounts(BankProvider.SIMPLEFIN) }
            assertEquals(BankLinkCopy.UNREACHABLE, failed.exceptionOrNull()?.message)
            assertEquals(
                BankLinkCopy.UNREACHABLE,
                phone.sync
                    .bankCall { it.configured() }
                    .exceptionOrNull()
                    ?.message,
            )
            assertEquals(before, row(phone, budgetId, "SELECT * FROM accounts WHERE id = 'checking'"))
            assertEquals(0L, row(phone, budgetId, "SELECT COUNT(*) AS n FROM banks")["n"])
            assertNull(phone.library.undoLabel(budgetId))
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

    private fun row(
        phone: SyncPhone,
        budgetId: String,
        sql: String,
    ): Map<String, Any?> =
        phone.library.useDatabase(budgetId) { session ->
            session
                .query(sql)
                .single()
                .values
                .mapValues { (_, v) -> if (v is Int) v.toLong() else v }
        }!!

    private companion object {
        const val SETUP_TOKEN = "aHR0cHM6Ly9icmlkZ2Uuc2ltcGxlZmluLm9yZy9zaW1wbGVmaW4vY2xhaW0vVEVTVA"
        val SIMPLEFIN_ACCOUNTS =
            """
            {"accounts":[
              {"id":"ACT-1","name":"Everyday","balance":"1234.56","org":{"name":"First Bank","domain":"firstbank.example"}},
              {"id":"ACT-2","name":"Bills","balance":"-20.00","org":{"name":"First Bank","domain":"firstbank.example"}}
            ]}
            """.trimIndent()
    }
}
