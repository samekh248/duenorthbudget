package app.duenorth.budget

import android.database.Cursor
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteProgram
import app.duenorth.budget.core.SessionOpener
import app.duenorth.budget.core.SqlRow
import app.duenorth.budget.core.SqlSession
import java.io.File

class AndroidSqlSession(
    private val database: SQLiteDatabase,
) : SqlSession {
    override fun exec(sql: String) {
        database.execSQL(sql)
    }

    override fun exec(
        sql: String,
        args: List<Any?>,
    ) {
        val statement = database.compileStatement(sql)
        statement.use {
            statement.bind(args)
            val verb = sql.trimStart().take(6).uppercase()
            if (verb.startsWith("INSERT")) statement.executeInsert() else statement.executeUpdateDelete()
        }
    }

    override fun query(
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

    override fun transaction(body: () -> Unit) {
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

class AndroidSessionOpener : SessionOpener {
    override fun <T> use(
        file: File,
        block: (SqlSession) -> T,
    ): T {
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        database.use { return block(AndroidSqlSession(it)) }
    }
}
