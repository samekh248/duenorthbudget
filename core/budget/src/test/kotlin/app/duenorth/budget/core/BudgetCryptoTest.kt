package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetCryptoTest {
    @Test
    fun fileAndMessageRoundTripAndRejectTheWrongPassword() {
        val salt = BudgetCrypto.newSalt()
        val key = BudgetCrypto.derive("violet-quartz-991", salt)
        val blob = BudgetCrypto.encrypt("budget".toByteArray(), key)
        assertTrue(blob.copyOfRange(0, 6).contentEquals("DNENC1".toByteArray()))
        assertTrue(BudgetCrypto.decrypt(blob, key).contentEquals("budget".toByteArray()))
        val other = BudgetCrypto.derive("nope", salt)
        var failed = false
        try {
            BudgetCrypto.decrypt(blob, other)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)

        val cell = SyncProto.encodeMessage(CellMessage("payees", "p", "name", "Cafe"))
        val wrapped = BudgetCrypto.encryptMessage(cell, key)
        assertTrue(BudgetCrypto.decryptMessage(wrapped, key).contentEquals(cell))

        val test = BudgetCrypto.testBlob("violet-quartz-991", salt)
        assertNotNull(BudgetCrypto.verify("violet-quartz-991", BudgetCrypto.saltText(salt), test))
        assertNull(BudgetCrypto.verify("nope", BudgetCrypto.saltText(salt), test))
        val exported = BudgetCrypto.import(BudgetCrypto.export(key))
        assertEquals("AES", exported.algorithm)
        assertFalse(BudgetCrypto.export(key).contains("violet-quartz-991"))
    }
}
