package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProtoTest {
    @Test
    fun messageRequestAndResponseRoundTrip() {
        val cell = CellMessage("zero_budgets", "202610-c-food", "amount", "2500")
        assertEquals(cell, SyncProto.decodeMessage(SyncProto.encodeMessage(cell)))
        val envelope = Envelope("2015-04-24T22:23:42.123Z-1000-0123456789ABCDEF", false, SyncProto.encodeMessage(cell))
        val request =
            SyncProto.encodeRequest(
                fileId = "file-1",
                groupId = "group-1",
                keyId = "",
                since = SyncClock.ZERO,
                messages = listOf(envelope),
            )
        val decoded = SyncProto.decodeRequest(request)
        assertEquals("file-1", decoded.fileId)
        assertEquals("group-1", decoded.groupId)
        assertEquals("", decoded.keyId)
        assertEquals(SyncClock.ZERO, decoded.since)
        assertEquals(cell, SyncProto.decodeMessage(decoded.messages.single().content))
        assertFalse(decoded.messages.single().isEncrypted)

        val response = SyncProto.encodeResponse("""{"hash":1}""", listOf(envelope))
        val back = SyncProto.decodeResponse(response)
        assertEquals("""{"hash":1}""", back.merkle)
        assertEquals(envelope.timestamp, back.messages.single().timestamp)
    }

    @Test
    fun unknownFieldsAreSkipped() {
        val message = SyncProto.encodeMessage(CellMessage("accounts", "a", "name", "checking"))
        val withExtra = message + byteArrayOf(72, 1)
        val decoded = SyncProto.decodeMessage(withExtra)
        assertEquals("checking", decoded.value)
        assertEquals("accounts", decoded.dataset)
        assertTrue(decoded.row == "a")
    }
}
