package app.duenorth.budget.core

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.math.abs

object AssistantCopy {
    const val TITLE = "assistant"
    const val ALL_CLEAR = "nothing needs you right now"
    const val ALL_CLEAR_DETAIL = "No duplicates, nothing in the wrong account, the budget is covered, and every account looks current."
    const val MAYBE = "maybe"
    const val TO_LOOK_AT = "to look at"

    /** What a finding says in place of its buttons while its fix is being saved. */
    fun working(action: FindingAction): String =
        when (action) {
            is FindingAction.MergePayees -> "merging payees\u2026"
            is FindingAction.Merge, is FindingAction.Match, is FindingAction.MoveAndMatch, is FindingAction.AdoptAndMatch ->
                "merging\u2026"
            is FindingAction.Delete -> "deleting\u2026"
            is FindingAction.MoveTo -> "moving\u2026"
            is FindingAction.LinkTransfer -> "linking\u2026"
            is FindingAction.MakeRule -> "making the rule\u2026"
            is FindingAction.MakeSchedule -> "making the schedule\u2026"
            is FindingAction.PostSchedule -> "entering the bill\u2026"
            else -> "saving\u2026"
        }

    const val BANK_SYNC = "bank sync"
    const val FILE_IMPORT = "file import"
    const val ENTERED = "you entered"

    const val MERGE = "merge"
    const val NOT_DUPLICATES = "not duplicates"
    const val MOVE_AND_MERGE = "move & merge"
    const val ITS_RIGHT = "it's right"
    const val OPEN = "open"
    const val DELETE = "delete"
    const val KEEP_WAITING = "keep waiting"
    const val USE_BANK_AMOUNT = "use bank amount"
    const val NOT_THE_SAME = "not the same"
    const val LINK_TRANSFER = "link as transfer"
    const val NOT_A_TRANSFER = "not a transfer"
    const val SYNC_NOW = "sync now"
    const val RECONCILE = "reconcile"
    const val LATER = "later"
    const val REVIEW = "review"
    const val BUDGET_MORE = "budget more"
    const val HOLD = "hold for next month"
    const val SEE_BILLS = "see bills"
    const val GOT_IT = "got it"
    const val MAKE_SCHEDULE = "make a schedule"
    const val NOT_A_BILL = "not a bill"
    const val POST_IT = "post it"
    const val SKIP_IT = "skip it"
    const val USE_NEW_PRICE = "use new price"
    const val KEEP_IT = "keep it"
    const val MAKE_RULE = "make a rule"
    const val NO_THANKS = "no thanks"
    const val MERGE_PAYEES = "merge payees"

    const val LAST_PAID = "last paid"
    const val AMOUNT = "amount"
    const val SCHEDULE_SAYS = "schedule says"
    const val LAST_TWO = "last two"

    fun recurring(
        payee: String,
        times: Int,
    ): String = "$payee has come about once a month, $times times in a row, with no schedule."

    fun nextAround(day: String): String = "next around $day"

    fun missed(
        schedule: String,
        day: String,
    ): String = "$schedule was due $day and nothing has posted."

    fun priceChange(schedule: String): String = "$schedule cost the same new amount the last two times."

    fun ruleIdea(
        payee: String,
        category: String,
        times: Int,
    ): String = "$payee went to $category all $times times. A rule could do it for you."

    fun lookalike(
        payee: String,
        other: String,
    ): String = "$payee and $other look like the same payee."

    fun usedTimes(
        payee: String,
        times: Int,
    ): String = if (times == 1) "$payee once" else "$payee $times times"

    const val BUDGETED = "budgeted"
    const val SPENT = "spent"
    const val AVAILABLE = "available"
    const val GOAL = "goal"
    const val TO_BUDGET = "to budget"
    const val AT_THE_BANK = "at the bank"
    const val IN_REGISTER = "in your register"
    const val BALANCE_NOW = "balance now"
    const val LOWEST = "lowest"

    fun overspent(category: String): String = "$category has spent more than it had this month."

    fun underfunded(
        category: String,
        longGoal: Boolean,
    ): String =
        if (longGoal) {
            "$category hasn't reached its goal yet."
        } else {
            "$category is budgeted under its goal this month."
        }

    fun toBudget(
        month: String,
        over: Boolean,
    ): String =
        if (over) {
            "You've budgeted more in $month than you have."
        } else {
            "Some of $month's money isn't budgeted yet."
        }

    fun billsAhead(
        account: String,
        day: String,
        days: Int,
    ): String = "$account could run short around $day. Bills in the next $days days add up to more than it has."

    fun dueIn(days: Int): String = "due in $days days"

    fun bankBalance(account: String): String = "The bank's balance for $account doesn't match the register."

    const val SAME_AMOUNT = "same amount"
    const val SAME_ACCOUNT = "same account"
    const val SAME_PAYEE = "same payee"
    const val SAME_BANK_ID = "same bank id"
    const val BOTH_UNMATCHED = "both unmatched"
    const val NOTHING_FROM_BANK = "nothing from the bank"
    const val OPPOSITE_AMOUNTS = "money out, same money in"
    const val DIFFERENT_ACCOUNTS = "different accounts"
    const val GONE = "This is already sorted out. Pull to refresh."

    fun daysOld(days: Int): String = "$days days old"

    fun duplicate(account: String): String = "These look like the same transaction twice in $account."

    fun wrongAccount(
        from: String,
        to: String,
    ): String = "You entered this in $from, but the bank shows the same charge in $to."

    fun moveTo(account: String): String = "move to $account"

    fun nearDuplicate(
        entered: String,
        bank: String,
    ): String = "You entered $entered, and the bank sent $bank for what looks like the same purchase."

    fun offBy(amount: String): String = "off by $amount"

    fun transfer(
        from: String,
        to: String,
    ): String = "This looks like money moved from $from to $to, but it's counted as spending and income."

    fun payeeHabit(
        payee: String,
        usual: String,
        times: Int,
        of: Int,
        here: String,
    ): String = "$payee is in $usual $times of $of times, but this one is in $here."

    fun usually(
        account: String,
        times: Int,
        of: Int,
    ): String = "$times of $of in $account"

    fun staleSync(
        account: String,
        days: Int?,
    ): String =
        when (days) {
            null -> "$account hasn't synced with the bank yet."
            1 -> "$account last synced with the bank yesterday."
            else -> "$account last synced with the bank $days days ago."
        }

    fun reconcile(
        account: String,
        last: String?,
        since: Int,
    ): String =
        if (last == null) {
            "$account has never been reconciled. $since cleared transactions are waiting."
        } else {
            "$account was last reconciled around $last. $since cleared transactions since."
        }

    fun oldUncleared(
        account: String,
        count: Int,
        days: Int,
    ): String =
        if (count == 1) {
            "1 transaction in $account is still uncleared after $days days."
        } else {
            "$count transactions in $account are still uncleared after $days days."
        }

    fun noBankMatch(
        account: String,
        days: Int,
    ): String = "You entered this in $account $days days ago and the bank still has nothing like it."
}

/** The headings the assistant's findings sit under, in the order they show. */
enum class FindingGroup(
    val title: String,
) {
    TRANSACTIONS("transactions"),
    BUDGET("budget"),
    SCHEDULES("schedules"),
    ACCOUNTS("accounts"),
    TIDY("tidy up"),
}

/** What a check found, which decides the heading and the buttons. */
enum class FindingKind(
    val group: FindingGroup,
    val title: String,
    /** One line under the title on the kind's collapsed card. */
    val blurb: String,
) {
    DUPLICATE(FindingGroup.TRANSACTIONS, "possible duplicates", "the same transaction twice"),
    WRONG_ACCOUNT(FindingGroup.TRANSACTIONS, "wrong account?", "entered in one account, at the bank in another"),
    NEAR_DUPLICATE(FindingGroup.TRANSACTIONS, "different amount?", "your entry and the bank's differ by a tip or fee"),
    TRANSFER(FindingGroup.TRANSACTIONS, "unlinked transfers", "money moved between your accounts"),
    NO_BANK_MATCH(FindingGroup.TRANSACTIONS, "not at the bank yet", "entries the bank never sent"),
    OVERSPENT(FindingGroup.BUDGET, "overspent", "categories that spent more than they had"),
    UNDERFUNDED(FindingGroup.BUDGET, "below goal", "categories budgeted under their goal"),
    TO_BUDGET(FindingGroup.BUDGET, "to budget", "money with no job yet, or budgeted twice"),
    BILLS_AHEAD(FindingGroup.BUDGET, "bills ahead", "bills due before the money is there"),
    MISSED_SCHEDULE(FindingGroup.SCHEDULES, "missed bills", "a schedule's day went by with nothing posted"),
    PRICE_CHANGE(FindingGroup.SCHEDULES, "price changed", "bills that now cost something else"),
    RECURRING(FindingGroup.SCHEDULES, "no schedule yet", "payments that come every month"),
    BANK_BALANCE(FindingGroup.ACCOUNTS, "balance mismatch", "the bank's balance and yours differ"),
    STALE_SYNC(FindingGroup.ACCOUNTS, "bank sync stale", "linked accounts that stopped syncing"),
    RECONCILE(FindingGroup.ACCOUNTS, "time to reconcile", "not checked against a statement lately"),
    OLD_UNCLEARED(FindingGroup.ACCOUNTS, "old uncleared", "not cleared after a month"),
    RULE_IDEA(FindingGroup.TIDY, "rule ideas", "payees that always get the same category"),
    LOOKALIKE_PAYEE(FindingGroup.TIDY, "look-alike payees", "one payee under two names"),
}

/** How sure a check is: sure findings lead their kind, the rest are labelled maybe. */
enum class FindingSure { HIGH, MAYBE }

/** One transaction in a finding, with where it came from (bank sync, a file import, or entered by hand). */
data class FindingSide(
    val source: String,
    val side: MatchSide,
)

/** A labelled amount on a finding, such as "available" or "at the bank". */
data class FindingFigure(
    val label: String,
    val amountMinor: Long,
)

/** A button on a finding. The first one a finding lists is its accent button. */
sealed interface FindingAction {
    val label: String

    /** A bank transaction and the entry it duplicates: the inbox's match, so the entry's details are kept. */
    data class Match(
        val bankId: String,
        val enteredId: String,
    ) : FindingAction {
        override val label = AssistantCopy.MERGE
    }

    /** Desktop Actual's merge of two transactions in one account. */
    data class Merge(
        val transactionIds: List<String>,
    ) : FindingAction {
        override val label = AssistantCopy.MERGE
    }

    /** Moves the entry into the bank transaction's account, then matches the two. */
    data class MoveAndMatch(
        val enteredId: String,
        val bankId: String,
    ) : FindingAction {
        override val label = AssistantCopy.MOVE_AND_MERGE
    }

    /** Moves the transaction into [accountId], keeping everything else. */
    data class MoveTo(
        val transactionId: String,
        val accountId: String,
        val accountName: String,
    ) : FindingAction {
        override val label = AssistantCopy.moveTo(accountName)
    }

    /** Gives the entry the bank's amount, then matches the two. */
    data class AdoptAndMatch(
        val enteredId: String,
        val bankId: String,
    ) : FindingAction {
        override val label = AssistantCopy.USE_BANK_AMOUNT
    }

    /** Makes two transactions in different accounts one transfer, as Actual's "make transfer" does. */
    data class LinkTransfer(
        val fromId: String,
        val toId: String,
    ) : FindingAction {
        override val label = AssistantCopy.LINK_TRANSFER
    }

    /** Opens the account's register. */
    data class OpenAccount(
        val accountId: String,
    ) : FindingAction {
        override val label = AssistantCopy.REVIEW
    }

    /** Starts a reconcile of the account. */
    data class Reconcile(
        val accountId: String,
    ) : FindingAction {
        override val label = AssistantCopy.RECONCILE
    }

    /** Opens the category's budget page, to budget more into it. */
    data class BudgetCategory(
        val categoryId: String,
    ) : FindingAction {
        override val label = AssistantCopy.BUDGET_MORE
    }

    /** Opens hold for next month, for money this month does not need. */
    data object HoldForNext : FindingAction {
        override val label = AssistantCopy.HOLD
    }

    /** Opens the schedules. */
    data object OpenSchedules : FindingAction {
        override val label = AssistantCopy.SEE_BILLS
    }

    /** Makes a monthly schedule from the latest payment, next due on [nextDateIso]. */
    data class MakeSchedule(
        val transactionId: String,
        val nextDateIso: String,
    ) : FindingAction {
        override val label = AssistantCopy.MAKE_SCHEDULE
    }

    /** Posts the schedule's missed occurrence. */
    data class PostSchedule(
        val scheduleId: String,
    ) : FindingAction {
        override val label = AssistantCopy.POST_IT
    }

    /** Skips the schedule's missed occurrence. */
    data class SkipSchedule(
        val scheduleId: String,
    ) : FindingAction {
        override val label = AssistantCopy.SKIP_IT
    }

    /** Sets the schedule's amount to what it has cost lately. */
    data class UpdateScheduleAmount(
        val scheduleId: String,
        val amountMinor: Long,
    ) : FindingAction {
        override val label = AssistantCopy.USE_NEW_PRICE
    }

    /** A rule: this payee always gets this category. */
    data class MakeRule(
        val payeeId: String,
        val categoryId: String,
    ) : FindingAction {
        override val label = AssistantCopy.MAKE_RULE
    }

    /** Merges the payee into the one named [intoName], with a rule so the bank's name lands there next time. */
    data class MergePayees(
        val payeeId: String,
        val intoName: String,
    ) : FindingAction {
        override val label = AssistantCopy.MERGE_PAYEES
    }

    /** Fetches every linked account from the bank. */
    data object SyncBank : FindingAction {
        override val label = AssistantCopy.SYNC_NOW
    }

    /** Opens the transaction's form. */
    data class Open(
        val accountId: String,
        val transactionId: String,
    ) : FindingAction {
        override val label = AssistantCopy.OPEN
    }

    data class Delete(
        val transactionId: String,
    ) : FindingAction {
        override val label = AssistantCopy.DELETE
    }

    /** Never shows this finding again, on any phone. */
    data class Dismiss(
        override val label: String,
    ) : FindingAction
}

data class Finding(
    /** Stable across reads: what a dismissal remembers. */
    val key: String,
    val kind: FindingKind,
    val sure: FindingSure,
    /** One sentence on what the assistant thinks is wrong. */
    val headline: String,
    val sides: List<FindingSide>,
    /** Short reasons, such as "same amount" or "2 days apart". */
    val reasons: List<String>,
    val actions: List<FindingAction>,
    /** The account a fix lands in, so the shell reloads it. */
    val accountId: String,
    /** For a near duplicate: the bank's amount less the entry's, shown as "off by". */
    val difference: Long? = null,
    /** Amounts that explain the finding, one per line. */
    val figures: List<FindingFigure> = emptyList(),
)

data class AssistantReport(
    val findings: List<Finding>,
) {
    val count: Int get() = findings.size

    /** The findings of each kind that has any, in [FindingKind] order. */
    fun byKind(): Map<FindingKind, List<Finding>> =
        FindingKind.entries.associateWith { kind -> findings.filter { it.kind == kind } }.filterValues { it.isNotEmpty() }

    companion object {
        val EMPTY = AssistantReport(emptyList())
    }
}

/**
 * Looks through the budget for transactions that are probably wrong. Every check reads the phone's copy only and
 * changes nothing; [AssistantBook] carries out what the person picks. A transaction sits in at most one finding, and
 * the checks run in order of how sure they are: matches, duplicates, the wrong account, then nothing at the bank.
 */
object AssistantReader {
    /** How far back the checks look. */
    const val LOOKBACK_DAYS = 120L

    /** Two bank copies or two entries count as one purchase only this close together. */
    const val DUPLICATE_DAYS = 3

    /** An entry and a bank transaction in another account, as close as a match. */
    const val TWIN_DAYS = MatchFinder.WINDOW_DAYS

    /** An entry in an account the bank keeps current is missing once it is this old. */
    const val NO_BANK_MATCH_DAYS = 10

    /** Older entries are left alone: they were settled long ago, one way or another. */
    const val NO_BANK_MATCH_MAX_DAYS = 90

    /** An entry and a bank copy whose amounts differ by at most this share (a tip, a fee). */
    const val NEAR_SHARE = 0.25

    /** A card payment or savings move clears within this many days. */
    const val TRANSFER_DAYS = 3

    /** The payee-habit check looks at entries this recent. */
    const val HABIT_DAYS = 60L

    /** A payee's habit counts once it has this many other transactions... */
    const val HABIT_MIN = 5

    /** ...and this share of them in one other account. */
    const val HABIT_SHARE = 0.8

    /** Leading letters two payee names share to count as alike. */
    private const val ALIKE = 4

    fun read(
        session: SqlSession,
        today: LocalDate,
    ): AssistantReport {
        val dismissed = AssistantBook.dismissed(session)
        val rows = rows(session, today)
        val byId = rows.associateBy { it.id }
        val taken = mutableSetOf<String>()
        val found = mutableListOf<Draft>()

        // 1. A bank transaction and an entry: the inbox's possible matches.
        MatchFinder.find(session).forEach { pair ->
            val bank = byId[pair.bank.id] ?: return@forEach
            val entered = byId[pair.entered.id] ?: return@forEach
            taken += pair.ids
            found += Draft.Match(bank, entered)
        }
        // A transaction that would match in its own account is not missing, and not in the wrong account.
        val matchable = matchableIds(session)

        // 2. Two bank copies, or the same thing entered twice.
        duplicates(rows.filter { it.bank }, taken) { a, b -> bankTwins(a, b) }.forEach { found += it }
        duplicates(rows.filter { !it.bank }, taken) { a, b -> enteredTwins(a, b) }.forEach { found += it }

        // 3. An entry with no bank copy, and a bank transaction with no entry, in different accounts.
        val fed = rows.filter { it.bank }.map { it.accountId }.toSet()
        val loneEntries = rows.filter { !it.bank && it.id !in taken && it.id !in matchable }
        val loneBank = rows.filter { it.bank && it.id !in taken && it.id !in matchable }
        loneEntries
            .sortedByDescending { it.date }
            .forEach { entry ->
                val twin =
                    loneBank
                        .filter {
                            it.id !in taken && it.accountId != entry.accountId && it.amount == entry.amount &&
                                days(it.date, entry.date) <= TWIN_DAYS
                        }.minWithOrNull(compareBy<Row> { days(it.date, entry.date) }.thenBy { if (samePayee(it, entry)) 0 else 1 })
                        ?: return@forEach
                taken += setOf(entry.id, twin.id)
                found += Draft.Twin(entry, twin, sure = entry.accountId in fed || samePayee(entry, twin))
            }

        // 3b. An entry and a bank copy of one payee whose amounts differ by a tip or a fee.
        nearDuplicates(rows, taken, matchable).forEach { found += it }

        // 3c. Money out of one account and the same money into another, neither a transfer.
        transfers(rows, taken).forEach { found += it }

        // 3d. An entry in an account its payee is almost never in.
        payeeHabits(session, rows, taken, matchable, today).forEach { found += it }

        // 4. An entry, old enough and not yet reconciled, in an account the bank keeps current, with nothing like it there.
        val todayInt = today.toActualDate()
        rows
            .filter { !it.bank && it.id !in taken && it.id !in matchable && it.accountId in fed && it.date <= todayInt }
            .forEach { entry ->
                val age = days(entry.date, todayInt)
                if (age < NO_BANK_MATCH_DAYS || age > NO_BANK_MATCH_MAX_DAYS) return@forEach
                taken += entry.id
                found += Draft.NoBank(entry, age)
            }

        val drafts = found.filter { it.key !in dismissed }
        val names = accountNames(session)
        drafts.filterIsInstance<Draft.Habit>().forEach { it.usualName = names[it.usualAccountId].orEmpty() }
        val sides = if (drafts.isEmpty()) emptyMap() else MatchFinder.sides(session, drafts.flatMap { it.ids }.toSet())
        val findings =
            drafts.mapNotNull { it.finding(sides) } +
                (
                    BudgetChecks.read(session, today) +
                        ScheduleChecks.read(session, today) +
                        TidyChecks.read(session, today) +
                        AccountChecks.read(session, today, fed, rows.filter { !it.cleared }.map { it.accountId to it.date })
                ).filter { it.key !in dismissed }
        if (findings.isEmpty()) return AssistantReport.EMPTY
        return AssistantReport(
            findings.sortedWith(
                compareBy<Finding> { it.kind.ordinal }
                    .thenBy { it.sure.ordinal }
                    .thenByDescending { f -> f.sides.maxOfOrNull { it.side.date } ?: 0 },
            ),
        )
    }

    /** Pairs [rows] of one account and amount that [twins] calls the same purchase, closest dates first. */
    private fun duplicates(
        rows: List<Row>,
        taken: MutableSet<String>,
        twins: (Row, Row) -> FindingSure?,
    ): List<Draft> {
        val out = mutableListOf<Draft>()
        rows
            .filter { it.id !in taken }
            .groupBy { it.accountId to it.amount }
            .values
            .filter { it.size > 1 }
            .forEach { group ->
                val pairs =
                    group.indices.flatMap { i ->
                        (i + 1 until group.size).mapNotNull { j ->
                            val (a, b) = group[i] to group[j]
                            if (days(a.date, b.date) > DUPLICATE_DAYS) return@mapNotNull null
                            twins(a, b)?.let { Triple(a, b, it) }
                        }
                    }
                pairs
                    .sortedWith(compareBy({ it.third.ordinal }, { days(it.first.date, it.second.date) }))
                    .forEach { (a, b, sure) ->
                        if (a.id in taken || b.id in taken) return@forEach
                        taken += setOf(a.id, b.id)
                        // The bank-sync copy first, then the file import, then the earlier: the one merge keeps.
                        val (kept, other) = listOf(a, b).sortedWith(compareBy({ it.rank }, { it.date })).let { it[0] to it[1] }
                        out += Draft.Duplicate(kept, other, sure)
                    }
            }
        return out
    }

    /** Two bank copies: the same bank id, or a sync and a file import, or one source twice under the same payee. */
    private fun bankTwins(
        a: Row,
        b: Row,
    ): FindingSure? =
        when {
            a.financialId != null && a.financialId == b.financialId -> FindingSure.HIGH
            a.synced != b.synced -> if (samePayee(a, b) || a.date == b.date) FindingSure.HIGH else FindingSure.MAYBE
            samePayee(a, b) -> FindingSure.MAYBE
            else -> null
        }

    /** The same payee entered twice: sure on one day, maybe a day or few apart. */
    private fun enteredTwins(
        a: Row,
        b: Row,
    ): FindingSure? =
        when {
            !samePayee(a, b) -> null
            a.date == b.date -> FindingSure.HIGH
            else -> FindingSure.MAYBE
        }

    /** An entry and a bank copy in one account, a few days apart, one payee, amounts within [NEAR_SHARE] of each other. */
    private fun nearDuplicates(
        rows: List<Row>,
        taken: MutableSet<String>,
        matchable: Set<String>,
    ): List<Draft> {
        val out = mutableListOf<Draft>()
        val bank = rows.filter { it.bank && it.id !in matchable }
        rows
            .filter { !it.bank && it.id !in taken && it.id !in matchable }
            .forEach { entry ->
                val twin =
                    bank
                        .filter {
                            it.id !in taken && it.accountId == entry.accountId && it.amount != entry.amount &&
                                (it.amount < 0) == (entry.amount < 0) && days(it.date, entry.date) <= DUPLICATE_DAYS &&
                                abs(it.amount - entry.amount) <= abs(entry.amount) * NEAR_SHARE &&
                                (samePayee(it, entry) || alike(it.payeeName, entry.payeeName))
                        }.minWithOrNull(compareBy<Row> { abs(it.amount - entry.amount) }.thenBy { days(it.date, entry.date) })
                        ?: return@forEach
                taken += setOf(entry.id, twin.id)
                out += Draft.Near(entry, twin, sure = samePayee(entry, twin))
            }
        return out
    }

    /** Opposite amounts in two accounts a few days apart, closest first; sure when the bank sent both. */
    private fun transfers(
        rows: List<Row>,
        taken: MutableSet<String>,
    ): List<Draft> {
        val out = mutableListOf<Draft>()
        val open = rows.filter { it.id !in taken }
        open
            .filter { it.amount < 0 }
            .forEach { from ->
                if (from.id in taken) return@forEach
                val to =
                    open
                        .filter {
                            it.id !in taken && it.accountId != from.accountId && it.amount == -from.amount &&
                                days(it.date, from.date) <= TRANSFER_DAYS
                        }.minByOrNull { days(it.date, from.date) }
                        ?: return@forEach
                taken += setOf(from.id, to.id)
                out += Draft.Transfer(from, to, sure = from.bank && to.bank)
            }
        return out
    }

    /** Entries of the last [HABIT_DAYS] whose payee is in one other account at least [HABIT_SHARE] of the time. */
    private fun payeeHabits(
        session: SqlSession,
        rows: List<Row>,
        taken: MutableSet<String>,
        matchable: Set<String>,
        today: LocalDate,
    ): List<Draft> {
        val since = today.minusDays(HABIT_DAYS).toActualDate()
        val entries = rows.filter { !it.bank && it.id !in taken && it.id !in matchable && it.payeeId != null && it.date >= since }
        if (entries.isEmpty()) return emptyList()
        val payees = entries.mapNotNull { it.payeeId }.toSet()
        val counts =
            session
                .query(
                    """
                    SELECT t.description AS payee, t.acct AS acct, COUNT(*) AS n
                    FROM transactions t
                    JOIN accounts a ON a.id = t.acct AND IFNULL(a.tombstone, 0) = 0 AND IFNULL(a.closed, 0) = 0
                    WHERE IFNULL(t.tombstone, 0) = 0 AND IFNULL(t.isChild, 0) = 0 AND t.transferred_id IS NULL
                        AND t.description IN (${payees.joinToString(",") { "?" }})
                    GROUP BY t.description, t.acct
                    """.trimIndent(),
                    payees.toList(),
                ).groupBy({ it.str("payee").orEmpty() }, { it.str("acct").orEmpty() to it.long("n").toInt() })
        return entries.mapNotNull { entry ->
            val byAccount = counts[entry.payeeId].orEmpty().toMap()
            // The entry itself is one of the counts; leave it out.
            val others = byAccount.mapValues { (acct, n) -> if (acct == entry.accountId) n - 1 else n }
            val total = others.values.sum()
            val (usual, times) = others.maxByOrNull { it.value } ?: return@mapNotNull null
            if (usual == entry.accountId || total < HABIT_MIN || times < total * HABIT_SHARE) return@mapNotNull null
            taken += entry.id
            Draft.Habit(entry, usual, times, total)
        }
    }

    /** Payee names that start alike once case and punctuation go: "Luigi's" and "LUIGIS TRATT". */
    private fun alike(
        a: String,
        b: String,
    ): Boolean {
        val x = a.lowercase().filter { it.isLetterOrDigit() }
        val y = b.lowercase().filter { it.isLetterOrDigit() }
        return x.length >= ALIKE && y.length >= ALIKE && x.take(ALIKE) == y.take(ALIKE)
    }

    private fun samePayee(
        a: Row,
        b: Row,
    ): Boolean =
        (a.payeeId != null && a.payeeId == b.payeeId) ||
            (a.payeeName.isNotBlank() && a.payeeName.equals(b.payeeName, ignoreCase = true))

    /** Ids that have a partner of the other kind (bank or entered) in their own account, dismissed or not. */
    private fun matchableIds(session: SqlSession): Set<String> =
        session
            .query(
                """
                SELECT b.id AS b_id, e.id AS e_id, b.date AS b_date, e.date AS e_date
                FROM transactions b
                JOIN transactions e
                    ON e.acct = b.acct AND e.amount = b.amount AND e.id <> b.id
                WHERE ${MatchFinder.candidate("b")} AND ${MatchFinder.candidate("e")}
                    AND (b.financial_id IS NOT NULL OR b.imported_description IS NOT NULL)
                    AND e.financial_id IS NULL AND e.imported_description IS NULL
                """.trimIndent(),
            ).filter { days(it.long("b_date").toInt(), it.long("e_date").toInt()) <= MatchFinder.WINDOW_DAYS }
            .flatMap { listOfNotNull(it.str("b_id"), it.str("e_id")) }
            .toSet()

    private fun accountNames(session: SqlSession): Map<String, String> =
        session
            .query("SELECT id, name FROM accounts WHERE IFNULL(tombstone, 0) = 0")
            .associate { it.str("id").orEmpty() to it.str("name").orEmpty() }

    private fun rows(
        session: SqlSession,
        today: LocalDate,
    ): List<Row> =
        session
            .query(
                """
                SELECT t.id AS id, t.acct AS acct, t.amount AS amount, t.date AS date, t.description AS payee_id,
                    COALESCE(p.name, '') AS payee_name, t.financial_id AS financial_id,
                    t.imported_description AS imported, IFNULL(t.cleared, 0) AS cleared
                FROM transactions t
                JOIN accounts a ON a.id = t.acct AND IFNULL(a.tombstone, 0) = 0 AND IFNULL(a.closed, 0) = 0
                LEFT JOIN payees p ON p.id = t.description AND IFNULL(p.tombstone, 0) = 0
                WHERE ${MatchFinder.candidate("t")} AND t.date >= ? AND IFNULL(t.amount, 0) <> 0
                ORDER BY t.date DESC, t.sort_order DESC, t.id DESC
                """.trimIndent(),
                listOf(today.minusDays(LOOKBACK_DAYS).toActualDate()),
            ).map { row ->
                Row(
                    id = row.str("id").orEmpty(),
                    accountId = row.str("acct").orEmpty(),
                    amount = row.long("amount"),
                    date = row.long("date").toInt(),
                    payeeId = row.str("payee_id"),
                    payeeName = row.str("payee_name").orEmpty().ifBlank { row.str("imported").orEmpty() },
                    financialId = row.str("financial_id")?.ifBlank { null },
                    imported = row.str("imported")?.ifBlank { null },
                    cleared = row.bool("cleared"),
                )
            }

    internal fun days(
        a: Int,
        b: Int,
    ): Int = MatchFinder.daysBetween(a, b)

    private data class Row(
        val id: String,
        val accountId: String,
        val amount: Long,
        val date: Int,
        val payeeId: String?,
        val payeeName: String,
        val financialId: String?,
        val imported: String?,
        val cleared: Boolean,
    ) {
        val synced: Boolean get() = financialId != null

        /** Came from the bank: a sync or a file import. */
        val bank: Boolean get() = financialId != null || imported != null

        /** The order merge keeps them in: a bank sync, then a file import, then an entry. */
        val rank: Int get() =
            if (synced) {
                0
            } else if (bank) {
                1
            } else {
                2
            }

        val source: String get() =
            if (synced) {
                AssistantCopy.BANK_SYNC
            } else if (bank) {
                AssistantCopy.FILE_IMPORT
            } else {
                AssistantCopy.ENTERED
            }
    }

    /** A finding before its transactions are read for display. */
    private sealed interface Draft {
        val key: String
        val ids: List<String>

        fun finding(sides: Map<String, MatchSide>): Finding?

        data class Match(
            val bank: Row,
            val entered: Row,
        ) : Draft {
            override val key = AssistantBook.matchKey(bank.id, entered.id)
            override val ids = listOf(bank.id, entered.id)

            override fun finding(sides: Map<String, MatchSide>): Finding? {
                val b = sides[bank.id] ?: return null
                val e = sides[entered.id] ?: return null
                return Finding(
                    key = key,
                    kind = FindingKind.DUPLICATE,
                    sure = FindingSure.HIGH,
                    headline = AssistantCopy.duplicate(b.accountName),
                    sides = listOf(FindingSide(bank.source, b), FindingSide(entered.source, e)),
                    reasons =
                        listOfNotNull(
                            AssistantCopy.SAME_ACCOUNT,
                            AssistantCopy.SAME_AMOUNT,
                            MatchCopy.apart(days(bank.date, entered.date)),
                            AssistantCopy.SAME_PAYEE.takeIf { samePayee(bank, entered) },
                        ),
                    actions = listOf(FindingAction.Match(bank.id, entered.id), FindingAction.Dismiss(AssistantCopy.NOT_DUPLICATES)),
                    accountId = bank.accountId,
                )
            }
        }

        data class Duplicate(
            val kept: Row,
            val other: Row,
            val sure: FindingSure,
        ) : Draft {
            override val key = AssistantBook.key("dup", kept.id, other.id)
            override val ids = listOf(kept.id, other.id)

            override fun finding(sides: Map<String, MatchSide>): Finding? {
                val k = sides[kept.id] ?: return null
                val o = sides[other.id] ?: return null
                return Finding(
                    key = key,
                    kind = FindingKind.DUPLICATE,
                    sure = sure,
                    headline = AssistantCopy.duplicate(k.accountName),
                    sides = listOf(FindingSide(kept.source, k), FindingSide(other.source, o)),
                    reasons =
                        listOfNotNull(
                            AssistantCopy.SAME_ACCOUNT,
                            AssistantCopy.SAME_AMOUNT,
                            MatchCopy.apart(days(kept.date, other.date)),
                            AssistantCopy.SAME_PAYEE.takeIf { samePayee(kept, other) },
                            AssistantCopy.SAME_BANK_ID.takeIf { kept.financialId != null && kept.financialId == other.financialId },
                        ),
                    actions = listOf(FindingAction.Merge(listOf(kept.id, other.id)), FindingAction.Dismiss(AssistantCopy.NOT_DUPLICATES)),
                    accountId = kept.accountId,
                )
            }
        }

        data class Twin(
            val entered: Row,
            val bank: Row,
            val sure: Boolean,
        ) : Draft {
            override val key = AssistantBook.key("wrong", entered.id, bank.id)
            override val ids = listOf(entered.id, bank.id)

            override fun finding(sides: Map<String, MatchSide>): Finding? {
                val e = sides[entered.id] ?: return null
                val b = sides[bank.id] ?: return null
                return Finding(
                    key = key,
                    kind = FindingKind.WRONG_ACCOUNT,
                    sure = if (sure) FindingSure.HIGH else FindingSure.MAYBE,
                    headline = AssistantCopy.wrongAccount(e.accountName, b.accountName),
                    sides = listOf(FindingSide(entered.source, e), FindingSide(bank.source, b)),
                    reasons =
                        listOfNotNull(
                            AssistantCopy.SAME_AMOUNT,
                            MatchCopy.apart(days(entered.date, bank.date)),
                            AssistantCopy.SAME_PAYEE.takeIf { samePayee(entered, bank) },
                            AssistantCopy.BOTH_UNMATCHED,
                        ),
                    actions =
                        listOf(
                            FindingAction.MoveAndMatch(entered.id, bank.id),
                            FindingAction.Dismiss(AssistantCopy.ITS_RIGHT),
                        ),
                    accountId = bank.accountId,
                )
            }
        }

        data class Near(
            val entered: Row,
            val bank: Row,
            val sure: Boolean,
        ) : Draft {
            override val key = AssistantBook.key("near", entered.id, bank.id)
            override val ids = listOf(entered.id, bank.id)

            override fun finding(sides: Map<String, MatchSide>): Finding? {
                val e = sides[entered.id] ?: return null
                val b = sides[bank.id] ?: return null
                return Finding(
                    key = key,
                    kind = FindingKind.NEAR_DUPLICATE,
                    sure = if (sure) FindingSure.HIGH else FindingSure.MAYBE,
                    headline = AssistantCopy.nearDuplicate(e.payee, b.payee),
                    sides = listOf(FindingSide(entered.source, e), FindingSide(bank.source, b)),
                    reasons =
                        listOfNotNull(
                            AssistantCopy.SAME_ACCOUNT,
                            MatchCopy.apart(days(entered.date, bank.date)),
                            AssistantCopy.SAME_PAYEE.takeIf { sure },
                        ),
                    actions =
                        listOf(
                            FindingAction.AdoptAndMatch(entered.id, bank.id),
                            FindingAction.Dismiss(AssistantCopy.NOT_THE_SAME),
                        ),
                    accountId = bank.accountId,
                    difference = bank.amount - entered.amount,
                )
            }
        }

        data class Transfer(
            val from: Row,
            val to: Row,
            val sure: Boolean,
        ) : Draft {
            override val key = AssistantBook.key("transfer", from.id, to.id)
            override val ids = listOf(from.id, to.id)

            override fun finding(sides: Map<String, MatchSide>): Finding? {
                val f = sides[from.id] ?: return null
                val t = sides[to.id] ?: return null
                return Finding(
                    key = key,
                    kind = FindingKind.TRANSFER,
                    sure = if (sure) FindingSure.HIGH else FindingSure.MAYBE,
                    headline = AssistantCopy.transfer(f.accountName, t.accountName),
                    sides = listOf(FindingSide(from.source, f), FindingSide(to.source, t)),
                    reasons =
                        listOf(
                            AssistantCopy.OPPOSITE_AMOUNTS,
                            MatchCopy.apart(days(from.date, to.date)),
                            AssistantCopy.DIFFERENT_ACCOUNTS,
                        ),
                    actions = listOf(FindingAction.LinkTransfer(from.id, to.id), FindingAction.Dismiss(AssistantCopy.NOT_A_TRANSFER)),
                    accountId = from.accountId,
                )
            }
        }

        data class Habit(
            val entered: Row,
            val usualAccountId: String,
            val times: Int,
            val of: Int,
        ) : Draft {
            override val key = AssistantBook.key("habit", entered.id)
            override val ids = listOf(entered.id)

            /** Reads the usual account's name with the sides, so the button can name it. */
            var usualName: String = ""

            override fun finding(sides: Map<String, MatchSide>): Finding? {
                val e = sides[entered.id] ?: return null
                val usual = usualName.ifBlank { return null }
                return Finding(
                    key = key,
                    kind = FindingKind.WRONG_ACCOUNT,
                    sure = FindingSure.MAYBE,
                    headline = AssistantCopy.payeeHabit(e.payee, usual, times, of, e.accountName),
                    sides = listOf(FindingSide(entered.source, e)),
                    reasons = listOf(AssistantCopy.usually(usual, times, of)),
                    actions =
                        listOf(
                            FindingAction.MoveTo(entered.id, usualAccountId, usual),
                            FindingAction.Dismiss(AssistantCopy.ITS_RIGHT),
                        ),
                    accountId = usualAccountId,
                )
            }
        }

        data class NoBank(
            val entered: Row,
            val age: Int,
        ) : Draft {
            override val key = AssistantBook.key("nobank", entered.id)
            override val ids = listOf(entered.id)

            override fun finding(sides: Map<String, MatchSide>): Finding? {
                val e = sides[entered.id] ?: return null
                return Finding(
                    key = key,
                    kind = FindingKind.NO_BANK_MATCH,
                    sure = FindingSure.HIGH,
                    headline = AssistantCopy.noBankMatch(e.accountName, age),
                    sides = listOf(FindingSide(entered.source, e)),
                    reasons = listOf(AssistantCopy.daysOld(age), AssistantCopy.NOTHING_FROM_BANK),
                    actions =
                        listOf(
                            FindingAction.Open(entered.accountId, entered.id),
                            FindingAction.Dismiss(AssistantCopy.KEEP_WAITING),
                            FindingAction.Delete(entered.id),
                        ),
                    accountId = entered.accountId,
                )
            }
        }
    }
}

/**
 * Carries out the assistant's fixes that are its own (moving an entry to the bank's account and matching it), and
 * remembers findings the person called fine, in a synced preference so other phones skip them too.
 */
class AssistantBook(
    private val session: SqlSession,
    private val today: LocalDate,
    private val ids: () -> String = {
        java.util.UUID
            .randomUUID()
            .toString()
    },
) {
    /**
     * Moves [enteredId] into [bankId]'s account, then matches the two: the bank's copy stays with the entry's payee,
     * category and note. An entry moved into an off-budget account drops its category, as Actual requires.
     */
    fun moveAndMatch(
        enteredId: String,
        bankId: String,
    ): WriteResult {
        val box = arrayOf<WriteResult>(WriteResult.Saved)
        session.transaction {
            val sides = MatchFinder.sides(session, setOf(enteredId, bankId))
            val entered = sides[enteredId]
            val bank = sides[bankId]
            if (entered == null || bank == null || !plain(enteredId) || !plain(bankId)) {
                box[0] = WriteResult.Rejected(AssistantCopy.GONE)
                return@transaction
            }
            val offBudget =
                session
                    .query("SELECT offbudget FROM accounts WHERE id = ?", listOf(bank.accountId))
                    .firstOrNull()
                    ?.bool("offbudget") ?: false
            SyncSchema.writeRow(session, "transactions", enteredId, "acct" to bank.accountId)
            val kept = if (offBudget) entered.copy(categoryId = null) else entered
            val bankSide = if (offBudget) bank.copy(categoryId = null) else bank
            MatchBook(session, today).settle(bankSide, kept, movedFrom = entered.accountName)
        }
        return box[0]
    }

    /** Moves [transactionId] into [accountId]; one moved off budget drops its category, as Actual requires. */
    fun moveTo(
        transactionId: String,
        accountId: String,
    ): WriteResult {
        if (!plain(transactionId) || !accountOpen(accountId)) return WriteResult.Rejected(AssistantCopy.GONE)
        session.transaction {
            SyncSchema.writeRow(session, "transactions", transactionId, "acct" to accountId)
            if (offBudget(accountId)) SyncSchema.writeRow(session, "transactions", transactionId, "category" to null)
        }
        return WriteResult.Saved
    }

    /** Gives [enteredId] the bank copy's amount, then matches the two: the bank's copy stays with the entry's details. */
    fun adoptAndMatch(
        enteredId: String,
        bankId: String,
    ): WriteResult {
        val box = arrayOf<WriteResult>(WriteResult.Saved)
        session.transaction {
            val sides = MatchFinder.sides(session, setOf(enteredId, bankId))
            val entered = sides[enteredId]
            val bank = sides[bankId]
            if (entered == null || bank == null || !plain(enteredId) || !plain(bankId) || entered.accountId != bank.accountId) {
                box[0] = WriteResult.Rejected(AssistantCopy.GONE)
                return@transaction
            }
            SyncSchema.writeRow(session, "transactions", enteredId, "amount" to bank.amountMinor)
            MatchBook(session, today).settle(bank, entered.copy(amountMinor = bank.amountMinor))
        }
        return box[0]
    }

    /**
     * Makes [fromId] and [toId] one transfer, as Actual's "make transfer" does: each takes the other account's
     * transfer payee and points at the other. Between two budget accounts neither keeps a category; to or from an
     * off-budget account the budget side keeps its own.
     */
    fun linkTransfer(
        fromId: String,
        toId: String,
    ): WriteResult {
        val box = arrayOf<WriteResult>(WriteResult.Saved)
        session.transaction {
            val sides = MatchFinder.sides(session, setOf(fromId, toId))
            val from = sides[fromId]
            val to = sides[toId]
            if (from == null || to == null || !plain(fromId) || !plain(toId) || from.accountId == to.accountId ||
                from.amountMinor != -to.amountMinor
            ) {
                box[0] = WriteResult.Rejected(AssistantCopy.GONE)
                return@transaction
            }
            val fromOff = offBudget(from.accountId)
            val toOff = offBudget(to.accountId)
            SyncSchema.writeRow(
                session,
                "transactions",
                fromId,
                "description" to transferPayee(to.accountId),
                "transferred_id" to toId,
                "category" to if (fromOff || !toOff) null else from.categoryId,
            )
            SyncSchema.writeRow(
                session,
                "transactions",
                toId,
                "description" to transferPayee(from.accountId),
                "transferred_id" to fromId,
                "category" to if (toOff || !fromOff) null else to.categoryId,
            )
        }
        return box[0]
    }

    private fun offBudget(accountId: String): Boolean =
        session
            .query("SELECT offbudget FROM accounts WHERE id = ?", listOf(accountId))
            .firstOrNull()
            ?.bool("offbudget") ?: false

    private fun accountOpen(accountId: String): Boolean =
        session
            .query(
                "SELECT id FROM accounts WHERE id = ? AND IFNULL(tombstone, 0) = 0 AND IFNULL(closed, 0) = 0",
                listOf(accountId),
            ).isNotEmpty()

    private fun transferPayee(accountId: String): String =
        session
            .query("SELECT id FROM payees WHERE transfer_acct = ? AND IFNULL(tombstone, 0) = 0 LIMIT 1", listOf(accountId))
            .firstOrNull()
            ?.str("id")
            ?: ids().also {
                SyncSchema.writeRow(session, "payees", it, "name" to "", "transfer_acct" to accountId, "tombstone" to false)
            }

    /** Remembers [key] as fine, and forgets keys whose transactions are gone or whose month or day has passed. */
    fun dismiss(key: String): WriteResult {
        session.transaction {
            val alive =
                dismissed(session)
                    .filter { known -> !aboutTransactions(known) || idsOf(known).all(::isAlive) }
                    .filterNot { known -> passed(known, today) }
                    .toSet()
            SyncSchema.writeRow(session, "preferences", DISMISSED_PREF, "value" to encode(alive + key))
        }
        return WriteResult.Saved
    }

    private fun plain(id: String): Boolean =
        session
            .query("SELECT id FROM transactions t WHERE t.id = ? AND ${MatchFinder.candidate("t")}", listOf(id))
            .isNotEmpty()

    private fun isAlive(id: String): Boolean =
        session
            .query("SELECT id FROM transactions WHERE id = ? AND IFNULL(tombstone, 0) = 0", listOf(id))
            .isNotEmpty()

    companion object {
        /** A synced preference holding the findings called fine. */
        const val DISMISSED_PREF = "duenorth.assistantDismissed"

        private val json = Json
        private val keys = ListSerializer(String.serializer())

        fun key(
            kind: String,
            vararg ids: String,
        ): String = "$kind:${ids.joinToString("|")}"

        /** A possible match's key: dismissing it is the inbox's not a match, so both places forget it. */
        fun matchKey(
            bankId: String,
            enteredId: String,
        ): String = key(MATCH, bankId, enteredId)

        /** The bank and entered ids of a possible match's [key]; null for any other finding. */
        fun matchIds(key: String): Pair<String, String>? {
            if (!key.startsWith("$MATCH:")) return null
            val ids = idsOf(key)
            return if (ids.size == 2) ids[0] to ids[1] else null
        }

        fun dismissed(session: SqlSession): Set<String> {
            val raw =
                session
                    .query("SELECT value FROM preferences WHERE id = ?", listOf(DISMISSED_PREF))
                    .firstOrNull()
                    ?.str("value")
                    ?: return emptySet()
            return runCatching { json.decodeFromString(keys, raw).toSet() }.getOrDefault(emptySet())
        }

        internal fun encode(values: Collection<String>): String = json.encodeToString(keys, values.sorted())

        /** Keys of findings about transactions, which go once their transactions do; account keys stay. */
        private fun aboutTransactions(key: String): Boolean = key.substringBefore(':') in TRANSACTION_KINDS

        private val TRANSACTION_KINDS = setOf(MATCH, "dup", "wrong", "nobank", "near", "transfer", "habit")

        /** Budget keys lead with their month (`YYYYMM`) or, for bills ahead, their day (`YYYYMMDD`). */
        private val MONTH_KINDS =
            setOf(BudgetChecks.OVERSPENT_KEY, BudgetChecks.UNDERFUNDED_KEY, BudgetChecks.TO_BUDGET_KEY)

        private fun passed(
            key: String,
            today: LocalDate,
        ): Boolean {
            val lead = idsOf(key).firstOrNull()?.toIntOrNull() ?: return false
            return when (key.substringBefore(':')) {
                in MONTH_KINDS -> lead < today.year * 100 + today.monthValue
                BudgetChecks.BILLS_KEY -> lead < today.year * 10000 + today.monthValue * 100 + today.dayOfMonth
                else -> false
            }
        }

        private fun idsOf(key: String): List<String> = key.substringAfter(':').split('|').filter { it.isNotBlank() }

        private const val MATCH = "match"
    }
}

private fun LocalDate.toActualDate(): Int = year * 10000 + monthValue * 100 + dayOfMonth
