package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Expected values come from `@actual-app/crdt` 3.1.4 and the `murmurhash` package it uses. */
class SyncMerkleTest {
    @Test
    fun murmurMatchesTheServersHash() {
        assertEquals(613153351, SyncMerkle.murmur3("hello"))
        assertEquals(659690545, SyncMerkle.murmur3(STAMPS[0]))
        assertEquals(0, SyncMerkle.murmur3(""))
    }

    @Test
    fun builtTrieIsTheOneTheServerSends() {
        assertEquals(SERVER_TRIE, SyncMerkle.build(STAMPS).toJson())
        assertEquals(SERVER_TRIE, SyncMerkle.parse(SERVER_TRIE)!!.toJson())
    }

    @Test
    fun diffFindsWhereAMissingMessageStarts() {
        val full = SyncMerkle.parse(SERVER_TRIE)!!
        val missing = SyncMerkle.build(STAMPS.filterIndexed { index, _ -> index != 2 })
        val from = SyncMerkle.diff(full, missing)
        assertEquals(1_791_385_380_000L, from)
        assertEquals(Instant.parse("2026-10-07T15:03:00Z").toEpochMilli(), from)
        assertTrue(SyncMerkle.since(from!!) < STAMPS[2])
        assertNull(SyncMerkle.diff(full, SyncMerkle.build(STAMPS.reversed())))
        assertEquals(0L, SyncMerkle.diff(full, SyncMerkle.parse("{}")!!))
    }

    @Test
    fun emptyOrBrokenMerkleTextIsNotATrie() {
        assertNull(SyncMerkle.parse(""))
        assertNull(SyncMerkle.parse("not json"))
        assertNotNull(SyncMerkle.parse("{}"))
        assertNull(SyncMerkle.diff(SyncMerkle.parse("{}")!!, SyncMerkle.build(emptyList())))
    }

    @Test
    fun clockKeepsCountingWhenAheadOfThePhone() {
        val now = Instant.parse("2026-10-07T15:04:00Z").toEpochMilli()
        val clock = SyncClock("AAAAAAAAAAAAAAAA") { now }
        clock.restoreIfBehind("2026-10-19T01:51:22.000Z-FFFF-SERVER0000000001")
        val first = clock.send()
        val second = clock.send()
        assertEquals("2026-10-19T01:51:22.001Z-0000-AAAAAAAAAAAAAAAA", first)
        assertTrue(second > first)
    }

    private companion object {
        val STAMPS =
            listOf(
                "2026-10-07T15:04:00.000Z-0000-AAAAAAAAAAAAAAAA",
                "2026-10-07T15:04:00.000Z-0001-AAAAAAAAAAAAAAAA",
                "2026-10-07T15:05:12.345Z-0000-BBBBBBBBBBBBBBBB",
                "2026-10-08T09:00:00.000Z-0000-AAAAAAAAAAAAAAAA",
                "2026-10-19T01:51:22.000Z-0003-SERVER0000000001",
                "2025-01-01T00:00:00.000Z-0000-CCCCCCCCCCCCCCCC",
            )

        const val SERVER_TRIE =
            """{"2":{"0":{"0":{"0":{"1":{"0":{"2":{"2":{"0":{"0":{"2":{"2":{"2":{"1":{"0":{"0":{"hash":2061246401},""" +
                """"hash":2061246401},"hash":2061246401},"hash":2061246401},"hash":2061246401},"hash":2061246401},""" +
                """"hash":2061246401},"hash":2061246401},"hash":2061246401},"hash":2061246401},"hash":2061246401},""" +
                """"hash":2061246401},"hash":2061246401},"2":{"0":{"1":{"1":{"2":{"1":{"2":{"0":{"2":{"2":{"1":{"1":""" +
                """{"1":{"hash":1269299762},"2":{"hash":1673442909},"hash":672746607},"hash":672746607},"hash":672746607},""" +
                """"hash":672746607},"hash":672746607},"hash":672746607},"hash":672746607},"2":{"0":{"2":{"1":{"0":{"1":""" +
                """{"0":{"0":{"hash":-1073506227},"hash":-1073506227},"hash":-1073506227},"hash":-1073506227},""" +
                """"hash":-1073506227},"hash":-1073506227},"hash":-1073506227},"hash":-1073506227},"hash":-400894942},""" +
                """"hash":-400894942},"2":{"2":{"0":{"0":{"2":{"2":{"1":{"0":{"1":{"0":{"hash":-178943923},""" +
                """"hash":-178943923},"hash":-178943923},"hash":-178943923},"hash":-178943923},"hash":-178943923},""" +
                """"hash":-178943923},"hash":-178943923},"hash":-178943923},"hash":-178943923},"hash":491740271},""" +
                """"hash":491740271},"hash":491740271},"hash":1737704366},"hash":1737704366},"hash":1737704366},""" +
                """"hash":1737704366}"""
    }
}
