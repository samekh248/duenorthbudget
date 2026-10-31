package app.duenorth.budget

import android.database.Cursor
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteProgram
import app.duenorth.budget.core.SessionOpener
import app.duenorth.budget.core.SqlRow
import app.duenorth.budget.core.SqlSession
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class AndroidSqlSession(
    private val database: SQLiteDatabase,
    /** Shared by every connection to the same file; see [AndroidSessionOpener]. */
    private val fileLock: ReentrantLock = ReentrantLock(),
) : SqlSession {
    override fun exec(sql: String) {
        fileLock.withLock { database.execSQL(sql) }
    }

    override fun exec(
        sql: String,
        args: List<Any?>,
    ) = fileLock.withLock {
        val statement = database.compileStatement(sql)
        statement.use {
            statement.bind(args)
            val verb = sql.trimStart().take(6).uppercase()
            if (verb.startsWith("INSERT")) statement.executeInsert() else statement.executeUpdateDelete()
        }
        Unit
    }

    override fun query(
        sql: String,
        args: List<Any?>,
    ): List<SqlRow> = fileLock.withLock { read(sql, args) }

    private fun read(
        sql: String,
        args: List<Any?>,
    ): List<SqlRow> {
        // Typed binds, as for exec: rawQuery's string arguments never equal a number worked out in SQL,
        // so "t.date / 100 = ?" matched no rows.
        val cursor =
            database.rawQueryWithFactory(
                { _, driver, table, query ->
                    query.bind(args)
                    SQLiteCursor(driver, table, query)
                },
                sql,
                null,
                "",
            )
        cursor.use {
            val names = it.columnNames
            val rows = mutableListOf<SqlRow>()
            while (it.moveToNext()) {
                val values = linkedMapOf<String, Any?>()
                for (index in names.indices) {
                    values[names[index]] =
                        when (it.getType(index)) {
                            Cursor.FIELD_TYPE_NULL -> null
                            Cursor.FIELD_TYPE_INTEGER -> it.getLong(index)
                            Cursor.FIELD_TYPE_FLOAT -> it.getDouble(index)
                            else -> it.getString(index)
                        }
                }
                rows.add(SqlRow(values))
            }
            return rows
        }
    }

    override fun transaction(body: () -> Unit) =
        fileLock.withLock {
            database.beginTransaction()
            try {
                body()
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
}

private fun SQLiteProgram.bind(args: List<Any?>) {
    args.forEachIndexed { index, arg ->
        val column = index + 1
        when (arg) {
            null -> bindNull(column)
            is Int -> bindLong(column, arg.toLong())
            is Long -> bindLong(column, arg)
            is Double -> bindDouble(column, arg)
            is Float -> bindDouble(column, arg.toDouble())
            is Boolean -> bindLong(column, if (arg) 1 else 0)
            else -> bindString(column, arg.toString())
        }
    }
}

/**
 * Opens a budget file, one connection per block.
 *
 * Android starts every transaction as BEGIN EXCLUSIVE, and a connection that meets another one's lock
 * gives up with SQLITE_BUSY after 2.5 seconds. A sync applying a long reply (or a big payee merge) on one
 * thread then crashed a screen reload opening the same file on another. Every connection to one file now
 * shares a lock that each statement and each whole transaction holds, so they take turns inside the app
 * instead of racing in SQLite. The lock is not held between statements, so a block that waits on the
 * network (bank sync) does not hold up readers.
 */
class AndroidSessionOpener : SessionOpener {
    override fun <T> use(
        file: File,
        block: (SqlSession) -> T,
    ): T {
        val lock = lockFor(file)
        // Opening sets the journal mode, which needs the file unlocked too.
        val database = lock.withLock { SQLiteDatabase.openOrCreateDatabase(file, null) }
        try {
            return block(AndroidSqlSession(database, lock))
        } finally {
            lock.withLock { database.close() }
        }
    }

    private companion object {
        private val locks = ConcurrentHashMap<String, ReentrantLock>()

        fun lockFor(file: File): ReentrantLock = locks.computeIfAbsent(file.absoluteFile.normalize().path) { ReentrantLock() }
    }
}
