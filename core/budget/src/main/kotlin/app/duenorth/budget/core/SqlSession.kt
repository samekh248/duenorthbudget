package app.duenorth.budget.core

/** One open SQLite file. Callers close it by leaving [SessionOpener.use]. */
interface SqlSession {
    fun exec(sql: String)

    fun exec(
        sql: String,
        args: List<Any?>,
    )

    fun query(
        sql: String,
        args: List<Any?> = emptyList(),
    ): List<SqlRow>

    fun transaction(body: () -> Unit)
}

interface SessionOpener {
    fun <T> use(
        file: java.io.File,
        block: (SqlSession) -> T,
    ): T
}

class SqlRow(
    val values: Map<String, Any?>,
) {
    fun str(column: String): String? = values[column]?.toString()

    fun long(column: String): Long {
        val value = values[column] ?: return 0L
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Short -> value.toLong()
            is Double -> value.toLong()
            is Float -> value.toLong()
            is Boolean -> if (value) 1L else 0L
            is Number -> value.toLong()
            else -> value.toString().toLongOrNull() ?: 0L
        }
    }

    fun bool(column: String): Boolean = long(column) != 0L
}
