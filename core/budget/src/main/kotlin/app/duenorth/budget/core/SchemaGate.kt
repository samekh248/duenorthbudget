package app.duenorth.budget.core

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Skips the schema checks ([ActualSchema.ensure], [SyncSchema.ensure]) when nothing has written to the
 * budget file since they last ran.
 *
 * Every library call opens the file and used to run those checks first: about a dozen PRAGMA and
 * sqlite_master reads, a write transaction, and the link repair's scan over every category and payee.
 * One save then read the budget back through about nine calls, so most of a save was schema checks.
 * The checks only ever change the file the first time they meet it, after a write, or after the
 * file is replaced, so a file that is the same as when they last finished needs none of them.
 *
 * A file counts as the same when:
 * - no statement that can write has run on it through a [track]ed session since the checks finished
 *   (every exec, and any query that is not a plain read, counts as a write), and
 * - its size, modified time and file key (inode) match, which catches a file replaced on disk, as a
 *   download, restore or import does.
 *
 * Checks run inside an outer transaction are never recorded, since that transaction can still roll
 * them back. Sessions that are not [track]ed (tests that open a connection by hand) always run the
 * checks, as before.
 */
object SchemaGate {
    enum class Check { ACTUAL, SYNC }

    private class FileState {
        /** Bumped after every write through a tracked session. */
        val writes = AtomicLong()

        /** The [writes] count and file stamp each check last finished at. */
        val done = ConcurrentHashMap<Check, Pair<Long, Stamp?>>()
    }

    private data class Stamp(
        val length: Long,
        val modified: Long,
        val key: Any?,
    )

    private val files = ConcurrentHashMap<String, FileState>()

    /** How deep this thread is in schema checks; their own writes do not count against the file. */
    private val checking = ThreadLocal.withInitial { 0 }

    /** Wraps [session] on [file] so its writes are counted. */
    fun track(
        file: File,
        session: SqlSession,
    ): SqlSession = Tracked(file, stateFor(file), session)

    /** Forgets that [file] was checked, so the next open checks it again. */
    fun forget(file: File) {
        files.remove(key(file))
    }

    /** Runs [body], the schema check [check] for [session], unless the file is unchanged since it last finished. */
    internal fun run(
        session: SqlSession,
        check: Check,
        body: () -> Unit,
    ) {
        val tracked = session as? Tracked
        if (tracked == null) {
            body()
            return
        }
        val state = tracked.state
        val before = state.writes.get()
        val last = state.done[check]
        if (last != null && last.first == before && last.second == stamp(tracked.file)) return
        checking.set(checking.get() + 1)
        try {
            body()
        } finally {
            checking.set(checking.get() - 1)
        }
        // Only record a check that is on disk, and only when no other thread wrote while it ran.
        if (tracked.depth == 0 && state.writes.get() == before) {
            state.done[check] = before to stamp(tracked.file)
        }
    }

    private fun stateFor(file: File): FileState = files.computeIfAbsent(key(file)) { FileState() }

    private fun key(file: File): String = file.absoluteFile.normalize().path

    private fun stamp(file: File): Stamp? =
        runCatching {
            val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
            Stamp(attributes.size(), attributes.lastModifiedTime().toMillis(), attributes.fileKey())
        }.getOrNull()

    private fun isRead(sql: String): Boolean {
        val head = sql.trimStart().take(24).uppercase()
        return head.startsWith("SELECT") ||
            head.startsWith("WITH") ||
            head.startsWith("PRAGMA TABLE_INFO") ||
            head.startsWith("PRAGMA INDEX_LIST")
    }

    private class Tracked(
        val file: File,
        val state: FileState,
        private val inner: SqlSession,
    ) : SqlSession {
        /** Open transactions on this session; a check inside one is not recorded. */
        @Volatile
        var depth = 0

        override fun exec(sql: String) = counted { inner.exec(sql) }

        override fun exec(
            sql: String,
            args: List<Any?>,
        ) = counted { inner.exec(sql, args) }

        override fun query(
            sql: String,
            args: List<Any?>,
        ): List<SqlRow> = if (isRead(sql)) inner.query(sql, args) else counted { inner.query(sql, args) }

        override fun transaction(body: () -> Unit) {
            depth++
            try {
                inner.transaction(body)
            } finally {
                depth--
            }
        }

        private inline fun <T> counted(block: () -> T): T =
            try {
                block()
            } finally {
                // Counted after the write, so a check that finished before it can't be recorded as covering it.
                if (checking.get() == 0) state.writes.incrementAndGet()
            }
    }
}
