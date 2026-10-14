package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAddressTest {
    @Test
    fun bareHostTriesSecureFirst() {
        val address = ServerAddress.parse("  budget.example.com/ ")!!
        assertEquals(listOf("https://budget.example.com", "http://budget.example.com"), address.candidates)
        assertEquals("budget.example.com", address.host)
    }

    @Test
    fun typedSchemeIsKeptAndSubPathSurvives() {
        val address = ServerAddress.parse("http://192.168.1.20:5006/actual/")!!
        assertEquals(listOf("http://192.168.1.20:5006/actual"), address.candidates)
        assertEquals("192.168.1.20", address.host)
    }

    @Test
    fun defaultLabelDropsWww() {
        assertEquals("example.com", ServerAddress.parse("https://www.example.com")!!.defaultLabel)
    }

    @Test
    fun blankSpacesAndOtherSchemesAreRejected() {
        assertNull(ServerAddress.parse(""))
        assertNull(ServerAddress.parse("my server"))
        assertNull(ServerAddress.parse("ftp://example.com"))
    }
}
