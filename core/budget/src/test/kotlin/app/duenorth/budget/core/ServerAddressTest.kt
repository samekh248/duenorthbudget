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

    @Test
    fun entrySplitsTheSchemeForTheToggle() {
        assertEquals(ServerAddress.Entry(true, "budget.example.com"), ServerAddress.entry("https://budget.example.com"))
        assertEquals(ServerAddress.Entry(false, "192.168.1.20:5006"), ServerAddress.entry("HTTP://192.168.1.20:5006"))
        assertEquals(ServerAddress.Entry(true, "budget.example.com"), ServerAddress.entry("budget.example.com"))
        assertEquals(ServerAddress.Entry(true, ""), ServerAddress.entry(""))
        assertEquals(ServerAddress.Entry(false, ""), ServerAddress.entry("http://"))
    }

    @Test
    fun joinPutsThePrefixOnWhatIsTyped() {
        assertEquals("https://budget.example.com", ServerAddress.join(true, "budget.example.com"))
        assertEquals("http://budget.lan", ServerAddress.join(false, "budget.lan"))
        assertEquals("", ServerAddress.join(true, ""))
        assertEquals("http://", ServerAddress.join(false, ""))
    }

    @Test
    fun pastedSchemeMovesTheToggle() {
        assertEquals("http://budget.lan:5006", ServerAddress.join(true, "http://budget.lan:5006"))
        assertEquals("https://budget.example.com", ServerAddress.join(false, " HTTPS://budget.example.com"))
        assertEquals(ServerAddress.Entry(false, "budget.lan:5006"), ServerAddress.entry(ServerAddress.join(true, "http://budget.lan:5006")))
    }
}
