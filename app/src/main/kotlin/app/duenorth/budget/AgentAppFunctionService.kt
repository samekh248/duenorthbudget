package app.duenorth.budget

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import app.duenorth.budget.core.AgentActions
import app.duenorth.budget.core.AgentAddedTransaction
import app.duenorth.budget.core.AgentCategoryBalance
import app.duenorth.budget.core.AgentInbox
import app.duenorth.budget.core.AgentOverview
import app.duenorth.budget.core.AgentProblem
import app.duenorth.budget.core.BudgetClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The budget's AppFunctions: what Gemini (or another assistant the system trusts) can ask Due North on Android 16+.
 * The KSP compiler turns this into the concrete `AgentAppFunctionService` the manifest names, and writes the
 * function schema the system indexes. The work itself is [AgentActions] in :core:budget.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "AgentAppFunctionService",
    appFunctionXmlFileName = "agent_app_function_service",
)
abstract class BaseAgentAppFunctionService : AppFunctionService() {
    private val actions: AgentActions by lazy {
        val app = application as DueNorthApplication
        AgentActions(app.library, BudgetClock.System)
    }

    /**
     * Gets an overview of the open budget for this month: the money left to budget (or overbudgeted), the
     * categories that are overspent, how many transactions wait in the inbox for a category, what the budget
     * assistant found, and the names of the open accounts.
     *
     * @return The month's overview.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getBudgetOverview(): BudgetOverview = run { actions.overview().toResult() }

    /**
     * Gets how much money a budget category has available this month, with what was budgeted and spent.
     * Use it for questions like "how much is left for groceries?".
     *
     * @param categoryName The category's name as the user said it. Case is ignored and a unique start or part of
     *   the name is enough. If it fits none or several, the error lists the names to ask the user about.
     * @return The category's balance this month.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getCategoryBalance(categoryName: String): CategoryBalance = run { actions.categoryBalance(categoryName).toResult() }

    /**
     * Lists every visible expense category with its available balance this month, in budget order.
     *
     * @return The categories and their balances.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun listCategoryBalances(): List<CategoryBalance> = run { actions.categoryBalances().map { it.toResult() } }

    /**
     * Gets the inbox: transactions that still need a category, newest first.
     *
     * @param limit How many transactions to list, from 1 to 50. The count covers all of them either way.
     * @return The inbox count and its newest transactions.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getInbox(limit: Int = 10): Inbox = run { actions.inbox(limit).toResult() }

    /**
     * Adds a transaction to the open budget, as if the user typed it into Due North.
     * Confirm the payee and amount with the user before calling it.
     *
     * @param payee Who the money went to or came from, such as "Trader Joe's".
     * @param amount The amount as a positive number, such as 42.50.
     * @param isIncome True for money coming in; false (the default) for spending.
     * @param accountName The account's name. Leave it out when the budget has a single on-budget account; when it
     *   has several, the error lists them so the user can pick.
     * @param categoryName The category's name. Leave it out to put the transaction in the inbox for later.
     * @param date The date as yyyy-MM-dd, or "today" or "yesterday". Leave it out for today.
     * @param note A note to keep with the transaction.
     * @return What was added, including whether it waits in the inbox.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun addTransaction(
        payee: String,
        amount: Double,
        isIncome: Boolean = false,
        accountName: String? = null,
        categoryName: String? = null,
        date: String? = null,
        note: String? = null,
    ): AddedTransaction = run { actions.addTransaction(payee, amount, isIncome, accountName, categoryName, date, note).toResult() }

    /** Runs [block] off the main thread and turns an [AgentProblem] into the AppFunction error an agent expects. */
    private suspend fun <T> run(block: () -> T): T =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (problem: AgentProblem.Invalid) {
                throw AppFunctionInvalidArgumentException(problem.message)
            } catch (problem: AgentProblem.NotFound) {
                throw AppFunctionElementNotFoundException(problem.message)
            }
        }
}

/** The open budget's month at a glance. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class BudgetOverview(
    /** The budget's name. */
    val budgetName: String,
    /** The month, as yyyy-MM. */
    val month: String,
    /** The budget's currency, such as USD. */
    val currencyCode: String,
    /** A sentence to read out, such as "To budget: $120.00" or "Overbudgeted: $40.00". */
    val headline: String,
    /** The categories that spent more than they had this month. */
    val overspent: List<CategoryBalance>,
    /** How many transactions wait in the inbox for a category. */
    val inboxCount: Int,
    /** How many bank transactions may match ones entered by hand. */
    val possibleMatches: Int,
    /** What the budget assistant found, one line per kind, such as "overspent: 2". */
    val assistantFindings: List<String>,
    /** The names of the open accounts. */
    val accounts: List<String>,
)

/** A category's money this month. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CategoryBalance(
    /** The category's name. */
    val category: String,
    /** The group the category is in. */
    val group: String,
    /** What is left to spend, as a decimal such as "123.45"; negative when overspent. */
    val available: String,
    /** What was budgeted this month, as a decimal. */
    val budgeted: String,
    /** What was spent this month, as a decimal; negative when refunds outweigh spending. */
    val spent: String,
    /** The budget's currency, such as USD. */
    val currencyCode: String,
    /** The available amount formatted for reading out, such as "$123.45". */
    val display: String,
    /** True when the category spent more than it had. */
    val overspent: Boolean,
)

/** Transactions that need a category. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class Inbox(
    /** How many transactions wait in the inbox. */
    val count: Int,
    /** How many bank transactions may match ones entered by hand. */
    val possibleMatches: Int,
    /** The newest of them. */
    val items: List<InboxItem>,
)

/** One transaction waiting in the inbox. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class InboxItem(
    /** Who it was with. */
    val payee: String,
    /** The amount as a decimal; negative for spending. */
    val amount: String,
    /** The amount formatted for reading out. */
    val display: String,
    /** The date, as yyyy-MM-dd. */
    val date: String,
    /** The account it is in. */
    val account: String,
    /** The category this payee usually goes to, if there is one. */
    val suggestedCategory: String?,
)

/** A transaction that was just added. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AddedTransaction(
    /** The new transaction's id. */
    val transactionId: String,
    /** Who it was with. */
    val payee: String,
    /** The amount as a decimal; negative for spending. */
    val amount: String,
    /** The amount formatted for reading out. */
    val display: String,
    /** The date, as yyyy-MM-dd. */
    val date: String,
    /** The account it went to. */
    val account: String,
    /** Its category, if one was given. */
    val category: String?,
    /** True when it has no category yet and waits in the inbox. */
    val inInbox: Boolean,
)

private fun AgentOverview.toResult() =
    BudgetOverview(
        budgetName,
        month,
        currencyCode,
        headline,
        overspent.map { it.toResult() },
        inboxCount,
        possibleMatches,
        assistantFindings,
        accounts,
    )

private fun AgentCategoryBalance.toResult() = CategoryBalance(category, group, available, budgeted, spent, currencyCode, display, overspent)

private fun AgentInbox.toResult() =
    Inbox(
        count,
        possibleMatches,
        items.map { InboxItem(it.payee, it.amount, it.display, it.date, it.account, it.suggestedCategory) },
    )

private fun AgentAddedTransaction.toResult() = AddedTransaction(transactionId, payee, amount, display, date, account, category, inInbox)
