package app.duenorth.budget

import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.RemoteFile

/** One entry in settings → budget's open-budget box: a budget on this phone, or one only on the server. */
sealed interface BudgetChoice {
    /** Unique within the box, and the end of the entry's test tag. */
    val key: String
    val label: String

    data class Phone(
        val budget: BudgetSummary,
    ) : BudgetChoice {
        override val key = "phone-${budget.id}"
        override val label = budget.name
    }

    data class Server(
        val file: RemoteFile,
        val server: String,
    ) : BudgetChoice {
        override val key = "server-${file.fileId}"
        override val label = "${file.name.ifBlank { file.fileId.take(6) }} · $server"
    }

    companion object {
        /**
         * The phone's budgets, then the server's that the phone does not already hold. A server file is held when it was
         * downloaded (same id) or is where the open budget syncs ([cloudFileId]).
         */
        fun of(
            budgets: List<BudgetSummary>,
            remoteFiles: List<RemoteFile>,
            server: String?,
            cloudFileId: String?,
        ): List<BudgetChoice> {
            val held = budgets.map { it.id }.toSet() + listOfNotNull(cloudFileId)
            val phone = budgets.map(::Phone)
            if (server == null) return phone
            return phone + remoteFiles.filter { it.fileId !in held }.map { Server(it, server) }
        }
    }
}
