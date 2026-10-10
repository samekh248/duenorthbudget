package app.duenorth.budget.core

import java.time.DateTimeException
import java.time.LocalDate
import java.time.Month
import java.util.UUID

object RegisterCopy {
    const val ENTER_PAYEE = "enter a payee"
    const val ENTER_AMOUNT = "enter an amount"
    const val AMOUNT_ZERO = "amount cannot be zero"
    const val ENTER_DATE = "enter a date"
    const val CHOOSE_CATEGORY = "choose a category"
    const val CHOOSE_ACCOUNT = "choose an account"
    const val SPLIT_MUST_ADD = "split must add up"
    const val RECONCILE_WARN = "this reconciliation will no longer match"
    const val NO_CATEGORY = "no category"
    const val SPLIT = "split"
    const val TRANSFER = "transfer"
    const val OWED = "owed"
    const val NO_TRANSACTIONS = "no transactions"
}

data class RegisterAccount(
    val id: String,
    val name: String,
    val offBudget: Boolean,
    val type: String,
    val closed: Boolean,
)

data class RegisterPart(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val amountMinor: Long,
)

data class RegisterRow(
    val id: String,
    val date: Int,
    val payee: String,
    val categoryId: String?,
    val categoryLabel: String,
    val amountMinor: Long,
    val note: String,
    val reconciled: Boolean,
    val transferId: String?,
    val partnerOffBudget: Boolean?,
    val partnerCategoryId: String?,
    val parent: Boolean,
    val parts: List<RegisterPart>,
)

data class CategoryChoice(
    val id: String,
    val name: String,
    val groupName: String,
    val income: Boolean,
)

data class AccountChoice(
    val id: String,
    val name: String,
    val offBudget: Boolean,
    val type: String,
    val closed: Boolean,
)

data class RegisterPage(
    val account: RegisterAccount,
    val balanceMinor: Long,
    val currency: CurrencySpec,
    val rows: List<RegisterRow>,
    val categories: List<CategoryChoice>,
    val accounts: List<AccountChoice>,
) {
    fun matching(payeeQuery: String): List<RegisterRow> {
        val query = payeeQuery.trim()
        if (query.isEmpty()) return rows
        return rows.filter { it.payee.contains(query, ignoreCase = true) }
    }
}

data class CategoryTarget(
    val transactionId: String,
    val accountId: String,
    val payee: String,
    val amountMinor: Long,
    val currency: CurrencySpec,
    val categories: List<CategoryChoice>,
)

data class TransactionDraft(
    val id: String,
    val accountId: String,
    val date: Int,
    val payee: String,
    val amountMinor: Long,
    val categoryId: String?,
    val note: String?,
    val force: Boolean = false,
    val scheduleId: String? = null,
)

data class TransferDraft(
    val id: String,
    val fromAccountId: String,
    val toAccountId: String,
    val date: Int,
    val amountMinor: Long,
    val categoryId: String?,
    val note: String?,
    val force: Boolean = false,
)

data class SplitPart(
    val categoryId: String,
    val amountMinor: Long,
)

sealed interface ForcedWrite {
    data class Save(
        val draft: TransactionDraft,
    ) : ForcedWrite

    data class Delete(
        val transactionId: String,
    ) : ForcedWrite

    data class Category(
        val transactionId: String,
        val categoryId: String?,
    ) : ForcedWrite

    data class Split(
        val transactionId: String,
        val parts: List<SplitPart>,
    ) : ForcedWrite

    data class Unsplit(
        val transactionId: String,
    ) : ForcedWrite

    data class Transfer(
        val draft: TransferDraft,
    ) : ForcedWrite
}

sealed interface WriteResult {
    data object Saved : WriteResult

    data class Rejected(
        val reason: String,
    ) : WriteResult

    data class Confirm(
        val reason: String,
        val retry: ForcedWrite,
    ) : WriteResult
}

sealed interface EntryResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : EntryResult<T>

    data class Rejected(
        val reason: String,
    ) : EntryResult<Nothing>
}

object MoneyParse {
    fun parseMagnitude(
        text: String,
        decimals: Int,
    ): Long? {
        val body =
            text
                .trim()
                .replace(",", "")
                .removePrefix("+")
                .removePrefix("-")
        if (body.isEmpty()) return null
        if (body.any { it != '.' && !it.isDigit() }) return null
        if (body.count { it == '.' } > 1) return null
        val parts = body.split('.')
        if (decimals == 0) {
            if (parts.size > 1) return null
            return parts[0].toLongOrNull()
        }
        val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
        if (parts.size == 1) return whole * scale(decimals)
        if (parts[1].length > decimals) return null
        val fraction = parts[1].padEnd(decimals, '0').ifEmpty { "0" }.toLongOrNull() ?: return null
        return whole * scale(decimals) + fraction
    }

    fun formatMagnitude(
        minor: Long,
        decimals: Int,
    ): String {
        val absolute = if (minor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minor)
        if (decimals == 0) return absolute.toString()
        val scale = scale(decimals)
        val whole = absolute / scale
        val fraction = (absolute % scale).toString().padStart(decimals, '0')
        return "$whole.$fraction"
    }

    private fun scale(decimals: Int): Long {
        var value = 1L
        repeat(decimals) { value *= 10 }
        return value
    }
}

object RegisterEntry {
    fun todayIso(clock: BudgetClock = BudgetClock.System): String = formatIso(clock.today().toActualDate())

    fun formatIso(date: Int): String {
        val year = date / 10000
        val month = (date / 100) % 100
        val day = date % 100
        return "%04d-%02d-%02d".format(year, month, day)
    }

    fun registerDateLabel(date: Int): String {
        val month = (date / 100) % 100
        val day = date % 100
        if (month !in 1..12) return date.toString()
        return "${Month.of(month).name.lowercase().take(3)} $day"
    }

    fun transaction(
        id: String,
        accountId: String,
        dateText: String,
        payee: String,
        amountText: String,
        decimals: Int,
        expense: Boolean,
        categoryId: String?,
        note: String,
        force: Boolean = false,
    ): EntryResult<TransactionDraft> {
        val date = parseDate(dateText) ?: return EntryResult.Rejected(RegisterCopy.ENTER_DATE)
        if (payee.trim().isEmpty()) return EntryResult.Rejected(RegisterCopy.ENTER_PAYEE)
        val magnitude =
            MoneyParse.parseMagnitude(amountText, decimals) ?: return EntryResult.Rejected(RegisterCopy.ENTER_AMOUNT)
        if (magnitude == 0L) return EntryResult.Rejected(RegisterCopy.AMOUNT_ZERO)
        val signed = if (expense) -magnitude else magnitude
        return EntryResult.Ok(
            TransactionDraft(
                id = id,
                accountId = accountId,
                date = date,
                payee = payee.trim(),
                amountMinor = signed,
                categoryId = categoryId?.ifBlank { null },
                note = note.trim().ifEmpty { null },
                force = force,
            ),
        )
    }

    fun transfer(
        id: String,
        fromAccountId: String,
        toAccountId: String,
        dateText: String,
        amountText: String,
        decimals: Int,
        categoryId: String?,
        note: String,
        force: Boolean = false,
    ): EntryResult<TransferDraft> {
        if (toAccountId.isBlank() || toAccountId == fromAccountId) {
            return EntryResult.Rejected(RegisterCopy.CHOOSE_ACCOUNT)
        }
        val date = parseDate(dateText) ?: return EntryResult.Rejected(RegisterCopy.ENTER_DATE)
        val magnitude =
            MoneyParse.parseMagnitude(amountText, decimals) ?: return EntryResult.Rejected(RegisterCopy.ENTER_AMOUNT)
        if (magnitude == 0L) return EntryResult.Rejected(RegisterCopy.AMOUNT_ZERO)
        return EntryResult.Ok(
            TransferDraft(
                id = id,
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                date = date,
                amountMinor = magnitude,
                categoryId = categoryId?.ifBlank { null },
                note = note.trim().ifEmpty { null },
                force = force,
            ),
        )
    }

    fun split(
        total: Long,
        decimals: Int,
        lines: List<Pair<String, String>>,
    ): EntryResult<List<SplitPart>> {
        if (lines.size < 2) return EntryResult.Rejected(RegisterCopy.SPLIT_MUST_ADD)
        val sign = if (total < 0) -1L else 1L
        var sum = 0L
        val parts =
            lines.map { (category, amountText) ->
                if (category.isBlank()) return EntryResult.Rejected(RegisterCopy.CHOOSE_CATEGORY)
                val magnitude =
                    MoneyParse.parseMagnitude(amountText, decimals)
                        ?: return EntryResult.Rejected(RegisterCopy.ENTER_AMOUNT)
                if (magnitude == 0L) return EntryResult.Rejected(RegisterCopy.AMOUNT_ZERO)
                val signed = magnitude * sign
                sum += signed
                SplitPart(category, signed)
            }
        if (sum != total) return EntryResult.Rejected(RegisterCopy.SPLIT_MUST_ADD)
        return EntryResult.Ok(parts)
    }

    fun parseDate(text: String): Int? {
        val trimmed = text.trim()
        val date =
            try {
                when {
                    trimmed.length == 8 && trimmed.all { it.isDigit() } ->
                        LocalDate.of(
                            trimmed.substring(0, 4).toInt(),
                            trimmed.substring(4, 6).toInt(),
                            trimmed.substring(6, 8).toInt(),
                        )
                    else -> LocalDate.parse(trimmed)
                }
            } catch (_: DateTimeException) {
                null
            } catch (_: NumberFormatException) {
                null
            } ?: return null
        return date.toActualDate()
    }
}

private fun LocalDate.toActualDate(): Int = year * 10000 + monthValue * 100 + dayOfMonth

class RegisterBook(
    private val session: SqlSession,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) {
    fun read(accountId: String): RegisterPage? {
        val account = account(accountId) ?: return null
        if (account.tombstone) return null
        val currency = currency()
        val balance =
            session
                .query(
                    """
                    SELECT COALESCE(SUM(t.amount), 0) AS balance
                    FROM transactions t
                    WHERE t.acct = ? AND $ALIVE
                    """.trimIndent(),
                    listOf(accountId),
                ).first()
                .long("balance")
        val parts = loadParts(accountId)
        val rows =
            session
                .query(
                    """
                    SELECT
                        t.id AS id,
                        t.date AS date,
                        t.amount AS amount,
                        t.notes AS notes,
                        t.category AS category,
                        t.isParent AS isParent,
                        t.reconciled AS reconciled,
                        t.transferred_id AS transferred_id,
                        COALESCE(p.name, '') AS payee,
                        COALESCE(c.name, '') AS category_name,
                        other.category AS partner_category,
                        partner.offbudget AS partner_off
                    FROM transactions t
                    LEFT JOIN payees p
                        ON p.id = t.description AND IFNULL(p.tombstone, 0) = 0
                    LEFT JOIN categories c
                        ON c.id = t.category AND IFNULL(c.tombstone, 0) = 0
                    LEFT JOIN transactions other
                        ON other.id = t.transferred_id AND IFNULL(other.tombstone, 0) = 0
                    LEFT JOIN accounts partner
                        ON partner.id = other.acct
                    WHERE t.acct = ?
                        AND IFNULL(t.tombstone, 0) = 0
                        AND IFNULL(t.isChild, 0) = 0
                    ORDER BY t.date DESC, t.sort_order DESC, t.id DESC
                    """.trimIndent(),
                    listOf(accountId),
                ).map { row ->
                    val id = row.str("id").orEmpty()
                    val parent = row.bool("isParent")
                    val categoryId = row.str("category")
                    val categoryName = row.str("category_name").orEmpty()
                    val transferId = row.str("transferred_id")
                    val label =
                        when {
                            parent -> RegisterCopy.SPLIT
                            categoryName.isNotBlank() -> categoryName
                            transferId != null -> RegisterCopy.TRANSFER
                            else -> RegisterCopy.NO_CATEGORY
                        }
                    RegisterRow(
                        id = id,
                        date = row.long("date").toInt(),
                        payee = row.str("payee").orEmpty().ifBlank { ShellCopy.NO_PAYEE },
                        categoryId = categoryId,
                        categoryLabel = label,
                        amountMinor = row.long("amount"),
                        note = row.str("notes").orEmpty(),
                        reconciled = row.bool("reconciled"),
                        transferId = transferId,
                        partnerOffBudget = row.optionalBool("partner_off"),
                        partnerCategoryId = row.str("partner_category"),
                        parent = parent,
                        parts = parts[id].orEmpty(),
                    )
                }
        return RegisterPage(
            account =
                RegisterAccount(
                    id = account.id,
                    name = account.name,
                    offBudget = account.offBudget,
                    type = account.type,
                    closed = account.closed,
                ),
            balanceMinor = balance,
            currency = currency,
            rows = rows,
            categories = categories(),
            accounts = accounts(),
        )
    }

    fun categoryTarget(transactionId: String): CategoryTarget? {
        val txn = find(transactionId) ?: return null
        if (txn.tombstone) return null
        val payee =
            txn.payeeId?.let { payeeId ->
                session
                    .query(
                        "SELECT name FROM payees WHERE id = ? AND IFNULL(tombstone, 0) = 0",
                        listOf(payeeId),
                    ).firstOrNull()
                    ?.str("name")
            }
        return CategoryTarget(
            transactionId = txn.id,
            accountId = txn.accountId,
            payee = payee?.ifBlank { null } ?: ShellCopy.NO_PAYEE,
            amountMinor = txn.amount,
            currency = currency(),
            categories = categories(),
        )
    }

    fun save(draft: TransactionDraft): WriteResult {
        if (draft.payee.trim().isEmpty()) return WriteResult.Rejected(RegisterCopy.ENTER_PAYEE)
        if (draft.amountMinor == 0L) return WriteResult.Rejected(RegisterCopy.AMOUNT_ZERO)
        if (!validDate(draft.date)) return WriteResult.Rejected(RegisterCopy.ENTER_DATE)
        val note = draft.note?.trim()?.ifEmpty { null }
        return write {
            val destination = account(draft.accountId)
            if (destination == null || destination.tombstone) return@write WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)
            if (draft.categoryId != null && !categoryAlive(draft.categoryId)) {
                return@write WriteResult.Rejected(RegisterCopy.CHOOSE_CATEGORY)
            }
            val existing = find(draft.id)
            if (existing != null && existing.tombstone) return@write WriteResult.Saved
            if (existing == null) {
                val ruled = applyRules(draft, note)
                insert(
                    id = ruled.id,
                    accountId = ruled.accountId,
                    categoryId = ruled.categoryId,
                    amount = ruled.amountMinor,
                    payeeId = payeeFor(ruled.payee),
                    note = ruled.note,
                    date = ruled.date,
                    sort = nextSort(),
                    scheduleId = ruled.scheduleId,
                )
                return@write WriteResult.Saved
            }
            if (existing.child) return@write WriteResult.Rejected(RegisterCopy.SPLIT_MUST_ADD)
            val partner = existing.transferId?.let { find(it) }?.takeIf { !it.tombstone }
            guard(listOfNotNull(existing, partner), draft.force, ForcedWrite.Save(draft.copy(force = true)))?.let {
                return@write it
            }
            if (existing.parent) {
                if (draft.amountMinor != existing.amount || draft.categoryId != null) {
                    return@write WriteResult.Rejected(RegisterCopy.SPLIT_MUST_ADD)
                }
                val payeeId = payeeFor(draft.payee)
                updateFields(existing.id, draft.accountId, null, existing.amount, payeeId, note, draft.date)
                propagateChildren(existing.id, draft.accountId, payeeId, draft.date)
                return@write WriteResult.Saved
            }
            if (partner != null) {
                return@write saveTransferSide(existing, partner, draft, note)
            }
            val ruled = applyRules(draft, note)
            updateFields(
                existing.id,
                ruled.accountId,
                ruled.categoryId,
                ruled.amountMinor,
                payeeFor(ruled.payee),
                ruled.note,
                ruled.date,
            )
            WriteResult.Saved
        }
    }

    private fun applyRules(
        draft: TransactionDraft,
        note: String?,
    ): TransactionDraft {
        if (draft.scheduleId != null) return draft
        val payeeId = payeeFor(draft.payee)
        val view =
            RuleTransactionView(
                account = draft.accountId,
                payee = payeeId,
                payeeName = draft.payee.trim(),
                category = draft.categoryId,
                amount = draft.amountMinor,
                notes = note,
                dateIso = ScheduleDates.toIso(draft.date),
            )
        val ruled = RulesBook(session, ids).applyRules(view)
        val payeeName =
            ruled.payee?.let { id ->
                session
                    .query("SELECT name FROM payees WHERE id = ?", listOf(id))
                    .firstOrNull()
                    ?.str("name")
            } ?: ruled.payeeName
        return draft.copy(
            payee = payeeName.ifBlank { draft.payee },
            categoryId = ruled.category,
            amountMinor = ruled.amount,
            note = ruled.notes ?: note,
        )
    }

    fun setCategory(
        transactionId: String,
        categoryId: String?,
        force: Boolean,
    ): WriteResult {
        if (categoryId != null && !categoryAlive(categoryId)) return WriteResult.Rejected(RegisterCopy.CHOOSE_CATEGORY)
        return write {
            val txn = find(transactionId) ?: return@write WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)
            if (txn.tombstone) return@write WriteResult.Saved
            if (txn.parent) return@write WriteResult.Rejected(RegisterCopy.SPLIT_MUST_ADD)
            val partner = txn.transferId?.let { find(it) }?.takeIf { !it.tombstone }
            guard(
                listOfNotNull(txn, partner),
                force,
                ForcedWrite.Category(transactionId, categoryId),
            )?.let { return@write it }
            if (partner != null) {
                val side = account(txn.accountId) ?: return@write WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)
                val other = account(partner.accountId) ?: return@write WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)
                val placed = placeCategory(side, other, categoryId) ?: return@write WriteResult.Rejected(RegisterCopy.CHOOSE_CATEGORY)
                updateFields(txn.id, txn.accountId, placed.first, txn.amount, txn.payeeId, txn.note, txn.date)
                updateFields(
                    partner.id,
                    partner.accountId,
                    placed.second,
                    partner.amount,
                    partner.payeeId,
                    partner.note,
                    partner.date,
                )
                return@write WriteResult.Saved
            }
            updateFields(txn.id, txn.accountId, categoryId, txn.amount, txn.payeeId, txn.note, txn.date)
            WriteResult.Saved
        }
    }

    fun delete(
        transactionId: String,
        force: Boolean,
    ): WriteResult =
        write {
            val txn = find(transactionId) ?: return@write WriteResult.Saved
            if (txn.tombstone) return@write WriteResult.Saved
            val partner = txn.transferId?.let { find(it) }?.takeIf { !it.tombstone }
            val kids = if (txn.parent) children(txn.id) else emptyList()
            guard(
                listOfNotNull(txn, partner) + kids,
                force,
                ForcedWrite.Delete(transactionId),
            )?.let { return@write it }
            tombstone(txn.id)
            partner?.let { tombstone(it.id) }
            kids.forEach { tombstone(it.id) }
            WriteResult.Saved
        }

    fun split(
        transactionId: String,
        parts: List<SplitPart>,
        force: Boolean,
    ): WriteResult {
        if (parts.size < 2 || parts.any { it.amountMinor == 0L }) {
            return WriteResult.Rejected(if (parts.any { it.amountMinor == 0L }) RegisterCopy.AMOUNT_ZERO else RegisterCopy.SPLIT_MUST_ADD)
        }
        return write {
            val txn = find(transactionId) ?: return@write WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)
            if (txn.tombstone || txn.child || txn.transferId != null) {
                return@write WriteResult.Rejected(RegisterCopy.SPLIT_MUST_ADD)
            }
            if (parts.sumOf { it.amountMinor } != txn.amount) return@write WriteResult.Rejected(RegisterCopy.SPLIT_MUST_ADD)
            if (parts.any { !categoryAlive(it.categoryId) }) return@write WriteResult.Rejected(RegisterCopy.CHOOSE_CATEGORY)
            val kids = children(txn.id)
            guard(listOf(txn) + kids, force, ForcedWrite.Split(transactionId, parts))?.let { return@write it }
            kids.forEach { tombstone(it.id) }
            session.exec(
                "UPDATE transactions SET isParent = 1, isChild = 0, category = NULL WHERE id = ?",
                listOf(txn.id),
            )
            parts.forEachIndexed { index, part ->
                insert(
                    id = ids(),
                    child = true,
                    parentId = txn.id,
                    accountId = txn.accountId,
                    categoryId = part.categoryId,
                    amount = part.amountMinor,
                    payeeId = txn.payeeId,
                    note = txn.note,
                    date = txn.date,
                    sort = txn.sort + (index + 1) * 0.01,
                )
            }
            WriteResult.Saved
        }
    }

    fun unsplit(
        transactionId: String,
        force: Boolean,
    ): WriteResult =
        write {
            val txn = find(transactionId) ?: return@write WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)
            if (txn.tombstone || !txn.parent) return@write WriteResult.Saved
            val kids = children(txn.id)
            guard(listOf(txn) + kids, force, ForcedWrite.Unsplit(transactionId))?.let { return@write it }
            val category = kids.firstOrNull()?.categoryId
            kids.forEach { tombstone(it.id) }
            session.exec(
                "UPDATE transactions SET isParent = 0, category = ? WHERE id = ?",
                listOf(category, txn.id),
            )
            WriteResult.Saved
        }

    fun transfer(draft: TransferDraft): WriteResult {
        if (draft.fromAccountId == draft.toAccountId || draft.toAccountId.isBlank()) {
            return WriteResult.Rejected(RegisterCopy.CHOOSE_ACCOUNT)
        }
        if (draft.amountMinor <= 0L) {
            return WriteResult.Rejected(if (draft.amountMinor == 0L) RegisterCopy.AMOUNT_ZERO else RegisterCopy.ENTER_AMOUNT)
        }
        if (!validDate(draft.date)) return WriteResult.Rejected(RegisterCopy.ENTER_DATE)
        val note = draft.note?.trim()?.ifEmpty { null }
        return write {
            val from = account(draft.fromAccountId)
            val to = account(draft.toAccountId)
            if (from == null || to == null || from.tombstone || to.tombstone) {
                return@write WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)
            }
            val placed = placeCategory(from, to, draft.categoryId) ?: return@write WriteResult.Rejected(RegisterCopy.CHOOSE_CATEGORY)
            val toId = "${draft.id}-to"
            val existing = find(draft.id)
            val partner = find(toId)
            if (existing != null && existing.tombstone) return@write WriteResult.Saved
            guard(
                listOfNotNull(existing, partner).filter { !it.tombstone },
                draft.force,
                ForcedWrite.Transfer(draft.copy(force = true)),
            )?.let { return@write it }
            val fromPayee = transferPayee(to.id)
            val toPayee = transferPayee(from.id)
            if (existing == null) {
                insert(
                    id = draft.id,
                    accountId = from.id,
                    categoryId = placed.first,
                    amount = -draft.amountMinor,
                    payeeId = fromPayee,
                    note = note,
                    date = draft.date,
                    transferId = toId,
                    sort = nextSort(),
                )
                insert(
                    id = toId,
                    accountId = to.id,
                    categoryId = placed.second,
                    amount = draft.amountMinor,
                    payeeId = toPayee,
                    note = note,
                    date = draft.date,
                    transferId = draft.id,
                    sort = nextSort(),
                )
            } else {
                updateFields(draft.id, from.id, placed.first, -draft.amountMinor, fromPayee, note, draft.date)
                if (partner == null || partner.tombstone) {
                    insert(
                        id = toId,
                        accountId = to.id,
                        categoryId = placed.second,
                        amount = draft.amountMinor,
                        payeeId = toPayee,
                        note = note,
                        date = draft.date,
                        transferId = draft.id,
                        sort = nextSort(),
                    )
                } else {
                    updateFields(toId, to.id, placed.second, draft.amountMinor, toPayee, note, draft.date)
                }
                session.exec(
                    "UPDATE transactions SET transferred_id = ? WHERE id = ?",
                    listOf(toId, draft.id),
                )
                session.exec(
                    "UPDATE transactions SET transferred_id = ? WHERE id = ?",
                    listOf(draft.id, toId),
                )
            }
            WriteResult.Saved
        }
    }

    private fun saveTransferSide(
        side: Txn,
        partner: Txn,
        draft: TransactionDraft,
        note: String?,
    ): WriteResult {
        val sideAccount = account(draft.accountId) ?: return WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)
        val partnerAccount = account(partner.accountId) ?: return WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)
        val placed =
            placeCategory(sideAccount, partnerAccount, draft.categoryId)
                ?: return WriteResult.Rejected(RegisterCopy.CHOOSE_CATEGORY)
        val sidePayee = transferPayee(partnerAccount.id)
        val partnerPayee = transferPayee(sideAccount.id)
        updateFields(side.id, sideAccount.id, placed.first, draft.amountMinor, sidePayee, note, draft.date)
        updateFields(
            partner.id,
            partner.accountId,
            placed.second,
            -draft.amountMinor,
            partnerPayee,
            note,
            partner.date,
        )
        return WriteResult.Saved
    }

    private fun placeCategory(
        from: AccountInfo,
        to: AccountInfo,
        requested: String?,
    ): Pair<String?, String?>? {
        if (from.offBudget == to.offBudget) return null to null
        if (requested.isNullOrBlank() || !categoryAlive(requested)) return null
        return if (!from.offBudget) requested to null else null to requested
    }

    private fun guard(
        rows: List<Txn>,
        force: Boolean,
        retry: ForcedWrite,
    ): WriteResult? {
        if (force || rows.none { it.reconciled }) return null
        return WriteResult.Confirm(RegisterCopy.RECONCILE_WARN, retry)
    }

    private fun write(block: () -> WriteResult): WriteResult {
        val box = arrayOf<WriteResult>(WriteResult.Saved)
        session.transaction { box[0] = block() }
        return box[0]
    }

    private fun loadParts(accountId: String): Map<String, List<RegisterPart>> {
        val grouped = linkedMapOf<String, MutableList<RegisterPart>>()
        session
            .query(
                """
                SELECT t.id AS id, t.parent_id AS parent_id, t.amount AS amount, t.category AS category,
                       COALESCE(c.name, '') AS category_name
                FROM transactions t
                LEFT JOIN categories c ON c.id = t.category AND IFNULL(c.tombstone, 0) = 0
                WHERE t.acct = ?
                    AND IFNULL(t.tombstone, 0) = 0
                    AND IFNULL(t.isChild, 0) = 1
                ORDER BY t.sort_order, t.id
                """.trimIndent(),
                listOf(accountId),
            ).forEach { row ->
                val parentId = row.str("parent_id").orEmpty()
                val part =
                    RegisterPart(
                        id = row.str("id").orEmpty(),
                        categoryId = row.str("category").orEmpty(),
                        categoryName = row.str("category_name").orEmpty().ifBlank { RegisterCopy.NO_CATEGORY },
                        amountMinor = row.long("amount"),
                    )
                grouped.getOrPut(parentId) { mutableListOf() }.add(part)
            }
        return grouped
    }

    private fun categories(): List<CategoryChoice> =
        session
            .query(
                """
                SELECT c.id AS id, c.name AS name, c.is_income AS is_income, COALESCE(g.name, '') AS group_name
                FROM categories c
                LEFT JOIN category_groups g ON g.id = c.cat_group
                WHERE IFNULL(c.tombstone, 0) = 0
                ORDER BY g.sort_order, c.sort_order, c.id
                """.trimIndent(),
            ).map { row ->
                CategoryChoice(
                    id = row.str("id").orEmpty(),
                    name = row.str("name").orEmpty(),
                    groupName = row.str("group_name").orEmpty(),
                    income = row.bool("is_income"),
                )
            }

    private fun accounts(): List<AccountChoice> =
        session
            .query(
                """
                SELECT id, name, offbudget, closed, type
                FROM accounts
                WHERE IFNULL(tombstone, 0) = 0
                ORDER BY offbudget, sort_order, name, id
                """.trimIndent(),
            ).map { row ->
                AccountChoice(
                    id = row.str("id").orEmpty(),
                    name = row.str("name").orEmpty(),
                    offBudget = row.bool("offbudget"),
                    type = row.str("type").orEmpty().ifBlank { "checking" },
                    closed = row.bool("closed"),
                )
            }

    private fun currency(): CurrencySpec {
        val code =
            session
                .query("SELECT value FROM preferences WHERE id = ?", listOf("currency"))
                .firstOrNull()
                ?.str("value")
                .orEmpty()
        return Currencies.byCode(code) ?: Currencies.byCode("USD")!!
    }

    private fun payeeFor(name: String): String {
        val trimmed = name.trim()
        val existing =
            session
                .query(
                    """
                    SELECT id FROM payees
                    WHERE IFNULL(tombstone, 0) = 0
                        AND transfer_acct IS NULL
                        AND lower(name) = lower(?)
                    LIMIT 1
                    """.trimIndent(),
                    listOf(trimmed),
                ).firstOrNull()
                ?.str("id")
        if (existing != null) return existing
        val id = ids()
        session.exec(
            "INSERT INTO payees (id, name, tombstone) VALUES (?, ?, 0)",
            listOf(id, trimmed),
        )
        return id
    }

    private fun transferPayee(accountId: String): String {
        val existing =
            session
                .query(
                    """
                    SELECT id FROM payees
                    WHERE IFNULL(tombstone, 0) = 0 AND transfer_acct = ?
                    LIMIT 1
                    """.trimIndent(),
                    listOf(accountId),
                ).firstOrNull()
                ?.str("id")
        if (existing != null) return existing
        val named = account(accountId)?.name ?: accountId
        val id = ids()
        session.exec(
            "INSERT INTO payees (id, name, transfer_acct, tombstone) VALUES (?, ?, ?, 0)",
            listOf(id, named, accountId),
        )
        return id
    }

    private fun insert(
        id: String,
        parent: Boolean = false,
        child: Boolean = false,
        parentId: String? = null,
        accountId: String,
        categoryId: String?,
        amount: Long,
        payeeId: String?,
        note: String?,
        date: Int,
        transferId: String? = null,
        sort: Double,
        scheduleId: String? = null,
    ) {
        session.exec(
            """
            INSERT INTO transactions (
                id, isParent, isChild, parent_id, acct, category, amount, description, notes, date,
                starting_balance_flag, transferred_id, sort_order, cleared, reconciled, tombstone, schedule
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, 1, 0, 0, ?)
            """.trimIndent(),
            listOf(
                id,
                if (parent) 1 else 0,
                if (child) 1 else 0,
                parentId,
                accountId,
                categoryId,
                amount,
                payeeId,
                note,
                date,
                transferId,
                sort,
                scheduleId,
            ),
        )
    }

    private fun updateFields(
        id: String,
        accountId: String,
        categoryId: String?,
        amount: Long,
        payeeId: String?,
        note: String?,
        date: Int,
    ) {
        session.exec(
            """
            UPDATE transactions
            SET acct = ?, category = ?, amount = ?, description = ?, notes = ?, date = ?
            WHERE id = ?
            """.trimIndent(),
            listOf(accountId, categoryId, amount, payeeId, note, date, id),
        )
    }

    private fun propagateChildren(
        parentId: String,
        accountId: String,
        payeeId: String,
        date: Int,
    ) {
        session.exec(
            """
            UPDATE transactions
            SET acct = ?, description = ?, date = ?
            WHERE parent_id = ? AND IFNULL(tombstone, 0) = 0
            """.trimIndent(),
            listOf(accountId, payeeId, date, parentId),
        )
    }

    private fun tombstone(id: String) {
        session.exec("UPDATE transactions SET tombstone = 1 WHERE id = ?", listOf(id))
    }

    private fun nextSort(): Double =
        session.query("SELECT COALESCE(MAX(sort_order), 0) AS sort FROM transactions").first().double("sort") + 1.0

    fun applyRulesForImport(draft: TransactionDraft): TransactionDraft =
        applyRules(draft, draft.note?.trim()?.ifEmpty { null })

    fun payeeIdForName(name: String): String = payeeFor(name)

    fun nextSortOrder(): Double = nextSort()

    fun insertImportedTransaction(
        id: String,
        accountId: String,
        categoryId: String?,
        amount: Long,
        payeeId: String?,
        note: String?,
        date: Int,
        financialId: String?,
        importedPayee: String?,
    ) {
        val hasFinancial = transactionColumns().contains("financial_id")
        if (!hasFinancial) {
            insert(
                id = id,
                accountId = accountId,
                categoryId = categoryId,
                amount = amount,
                payeeId = payeeId,
                note = note,
                date = date,
                sort = nextSort(),
            )
            return
        }
        session.exec(
            """
            INSERT INTO transactions (
                id, isParent, isChild, parent_id, acct, category, amount, description, notes, date,
                starting_balance_flag, transferred_id, sort_order, cleared, reconciled, tombstone, schedule,
                financial_id, imported_description
            ) VALUES (?, 0, 0, NULL, ?, ?, ?, ?, ?, ?, 0, NULL, ?, 1, 0, 0, NULL, ?, ?)
            """.trimIndent(),
            listOf(
                id,
                accountId,
                categoryId,
                amount,
                payeeId,
                note,
                date,
                nextSort(),
                financialId,
                importedPayee,
            ),
        )
    }

    private fun transactionColumns(): Set<String> =
        session.query("PRAGMA table_info(transactions)").mapNotNull { it.str("name") }.toSet()

    private fun categoryAlive(id: String): Boolean =
        session
            .query(
                "SELECT id FROM categories WHERE id = ? AND IFNULL(tombstone, 0) = 0",
                listOf(id),
            ).isNotEmpty()

    private fun children(parentId: String): List<Txn> =
        session
            .query(
                """
                SELECT id, isParent, isChild, parent_id, acct, category, amount, description, notes, date,
                       transferred_id, reconciled, tombstone, sort_order
                FROM transactions
                WHERE parent_id = ? AND IFNULL(tombstone, 0) = 0 AND IFNULL(isChild, 0) = 1
                ORDER BY sort_order, id
                """.trimIndent(),
                listOf(parentId),
            ).map { it.toTxn() }

    private fun find(id: String): Txn? =
        session
            .query(
                """
                SELECT id, isParent, isChild, parent_id, acct, category, amount, description, notes, date,
                       transferred_id, reconciled, tombstone, sort_order
                FROM transactions WHERE id = ?
                """.trimIndent(),
                listOf(id),
            ).firstOrNull()
            ?.toTxn()

    private fun account(id: String): AccountInfo? =
        session
            .query(
                """
                SELECT id, name, offbudget, closed, type, tombstone
                FROM accounts WHERE id = ?
                """.trimIndent(),
                listOf(id),
            ).firstOrNull()
            ?.let { row ->
                AccountInfo(
                    id = row.str("id").orEmpty(),
                    name = row.str("name").orEmpty(),
                    offBudget = row.bool("offbudget"),
                    type = row.str("type").orEmpty().ifBlank { "checking" },
                    closed = row.bool("closed"),
                    tombstone = row.bool("tombstone"),
                )
            }

    private fun validDate(date: Int): Boolean {
        val year = date / 10000
        val month = (date / 100) % 100
        val day = date % 100
        return try {
            LocalDate.of(year, month, day)
            true
        } catch (_: DateTimeException) {
            false
        }
    }

    private data class AccountInfo(
        val id: String,
        val name: String,
        val offBudget: Boolean,
        val type: String,
        val closed: Boolean,
        val tombstone: Boolean,
    )

    private data class Txn(
        val id: String,
        val parent: Boolean,
        val child: Boolean,
        val accountId: String,
        val categoryId: String?,
        val amount: Long,
        val payeeId: String?,
        val note: String?,
        val date: Int,
        val transferId: String?,
        val reconciled: Boolean,
        val tombstone: Boolean,
        val sort: Double,
    )

    private fun SqlRow.toTxn(): Txn =
        Txn(
            id = str("id").orEmpty(),
            parent = bool("isParent"),
            child = bool("isChild"),
            accountId = str("acct").orEmpty(),
            categoryId = str("category"),
            amount = long("amount"),
            payeeId = str("description"),
            note = str("notes"),
            date = long("date").toInt(),
            transferId = str("transferred_id"),
            reconciled = bool("reconciled"),
            tombstone = bool("tombstone"),
            sort = double("sort_order"),
        )

    private companion object {
        val ALIVE =
            """
            IFNULL(t.tombstone, 0) = 0
            AND IFNULL(t.isParent, 0) = 0
            AND NOT (
                IFNULL(t.isChild, 0) = 1
                AND EXISTS (
                    SELECT 1 FROM transactions p
                    WHERE p.id = t.parent_id AND IFNULL(p.tombstone, 0) = 1
                )
            )
            """.trimIndent()
    }
}
