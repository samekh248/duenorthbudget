package app.duenorth.budget.core

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types

class JdbcSqlSession(
    private val connection: Connection,
) : SqlSession {
    override fun exec(sql: String) {
        connection.createStatement().use { it.execute(sql) }
    }

    override fun exec(
        sql: String,
        args: List<Any?>,
    ) {
        connection.prepareStatement(sql).use { statement ->
            bind(statement, args)
            statement.executeUpdate()
        }
    }

    override fun query(
        sql: String,
        args: List<Any?>,
    ): List<SqlRow> {
        connection.prepareStatement(sql).use { statement ->
            bind(statement, args)
            statement.executeQuery().use { result ->
                val meta = result.metaData
                val rows = mutableListOf<SqlRow>()
                while (result.next()) {
                    val values = linkedMapOf<String, Any?>()
                    for (index in 1..meta.columnCount) {
                        values[meta.getColumnLabel(index)] = result.getObject(index)
                    }
                    rows.add(SqlRow(values))
                }
                return rows
            }
        }
    }

    override fun transaction(body: () -> Unit) {
        val previous = connection.autoCommit
        connection.autoCommit = false
        try {
            body()
            connection.commit()
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = previous
        }
    }

    private fun bind(
        statement: java.sql.PreparedStatement,
        args: List<Any?>,
    ) {
        args.forEachIndexed { index, arg ->
            val column = index + 1
            when (arg) {
                null -> statement.setNull(column, Types.NULL)
                is Int -> statement.setLong(column, arg.toLong())
                is Long -> statement.setLong(column, arg)
                is Double -> statement.setDouble(column, arg)
                is Float -> statement.setDouble(column, arg.toDouble())
                is Boolean -> statement.setLong(column, if (arg) 1 else 0)
                else -> statement.setString(column, arg.toString())
            }
        }
    }
}

class JdbcSessionOpener : SessionOpener {
    override fun <T> use(
        file: File,
        block: (SqlSession) -> T,
    ): T {
        Class.forName("org.sqlite.JDBC")
        val connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
        connection.use {
            return block(JdbcSqlSession(it))
        }
    }
}
