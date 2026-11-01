package app.duenorth.budget.core

import java.time.DateTimeException
import java.time.LocalDate
import java.time.Month
import java.util.UUID

object RegisterEntry {
    fun todayIso(clock: BudgetClock = BudgetClock.System): String = formatIso(clock.today().toActualDate())

    fun formatIso(date: Int): String {
        val year = date / 10000
        val month = (date / 100) % 100
        val day = date % 100
        return "%04d-%02d-%02d".format(year, month, day)
    }

    fun registerDateLabel(
        date: Int,
        dateFormat: String? = null,
    ): String {
        if (dateFormat != null) return DateFormats.format(date, dateFormat)
        val month = (date / 100) % 100
        val day = date % 100
        if (month !in 1..12) return date.toString()
        return "${Month.of(month).name.lowercase().take(3)} $day"
    }

    /** A transaction whose date was picked, not typed (spec 013), so only payee and amount can be rejected. */
    fun transaction(
        id: String,
        accountId: String,
        date: Int,
        payee: String,
        amountText: String,
        decimals: Int,
        expense: Boolean,
        categoryId: String?,
        note: String,
        force: Boolean = false,
        numberFormat: String = FormatOptions.NUMBER_COMMA_DOT,
        transferAccountId: String? = null,
    ): EntryResult<TransactionDraft> =
        transaction(
            id,
            accountId,
            formatIso(date),
            payee,
            amountText,
            decimals,
            expense,
            categoryId,
            note,
            force,
            numberFormat,
            transferAccountId,
        )

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
        numberFormat: String = FormatOptions.NUMBER_COMMA_DOT,
        transferAccountId: String? = null,
    ): EntryResult<TransactionDraft> {
        val date = parseDate(dateText) ?: return EntryResult.Rejected(RegisterCopy.ENTER_DATE)
        if (transferAccountId == null && payee.trim().isEmpty()) return EntryResult.Rejected(RegisterCopy.ENTER_PAYEE)
        // A typed sum is worked out first; its result is already in canonical form.
        val math = MoneyMath.isMath(amountText)
        val magnitude =
            MoneyParse.parseMagnitude(
                if (math) MoneyParse.canonical(amountText, numberFormat, decimals) else amountText,
                decimals,
                if (math) FormatOptions.NUMBER_COMMA_DOT else numberFormat,
            ) ?: return EntryResult.Rejected(RegisterCopy.ENTER_AMOUNT)
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
                transferAccountId = transferAccountId,
            ),
        )
    }

    /**
     * The amount box's text with a leading minus taken off, and whether it had one. The expense/income toggle
     * is the only sign, so a minus typed at the start means expense rather than being dropped on save.
     */
    fun leadingMinus(amountText: String): Pair<String, Boolean> {
        val trimmed = amountText.trimStart()
        return if (trimmed.startsWith("-")) trimmed.removePrefix("-") to true else amountText to false
    }

    /** The amount box's text as a signed amount, as [transaction] reads it; null while it isn't one yet. */
    fun signedAmount(
        amountText: String,
        decimals: Int,
        expense: Boolean,
        numberFormat: String = FormatOptions.NUMBER_COMMA_DOT,
    ): Long? {
        val math = MoneyMath.isMath(amountText)
        val magnitude =
            MoneyParse.parseMagnitude(
                if (math) MoneyParse.canonical(amountText, numberFormat, decimals) else amountText,
                decimals,
                if (math) FormatOptions.NUMBER_COMMA_DOT else numberFormat,
            ) ?: return null
        if (magnitude == 0L) return null
        return if (expense) -magnitude else magnitude
    }

    /** A transfer's payee line, as Actual's phone app words it: money out goes "to" the account, money in comes "from" it. */
    fun transferLabel(
        amountMinor: Long,
        accountName: String,
    ): String = "${if (amountMinor > 0) RegisterCopy.TRANSFER_FROM else RegisterCopy.TRANSFER_TO} $accountName"

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
                        t.cleared AS cleared,
                        t.raw_synced_data AS raw_synced_data,
                        t.transferred_id AS transferred_id,
                        COALESCE(p.name, '') AS payee,
                        p.transfer_acct AS transfer_acct,
                        COALESCE(target.name, '') AS transfer_name,
                        COALESCE(c.name, '') AS category_name,
                        other.category AS partner_category,
                        partner.offbudget AS partner_off
                    FROM transactions t
                    LEFT JOIN payees p
                        ON p.id = t.description AND IFNULL(p.tombstone, 0) = 0
                    LEFT JOIN accounts target
                        ON target.id = p.transfer_acct
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
                    val transferAccount = row.str("transfer_acct")
                    val amount = row.long("amount")
                    RegisterRow(
                        id = id,
                        date = row.long("date").toInt(),
                        payee =
                            if (transferAccount != null) {
                                RegisterEntry.transferLabel(
                                    amount,
                                    row.str("transfer_name").orEmpty().ifBlank { row.str("payee").orEmpty() },
                                )
                            } else {
                                row.str("payee").orEmpty().ifBlank { ShellCopy.NO_PAYEE }
                            },
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
                        cleared = row.bool("cleared"),
                        transferAccountId = transferAccount,
                        pending = BankPending.isPending(row.str("raw_synced_data"), row.bool("cleared"), row.bool("reconciled")),
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
            matches = MatchFinder.find(session, accountId),
            suggestions = CategorySuggester(session).index(),
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
            categoryId = txn.categoryId,
            suggested = CategorySuggester(session).forTransaction(txn.id),
        )
    }

    fun save(draft: TransactionDraft): WriteResult {
        val parts = draft.parts?.takeIf { it.isNotEmpty() }
        if (parts != null) {
            if (draft.transferAccountId != null || parts.size < 2) return WriteResult.Rejected(RegisterCopy.SPLIT_MUST_ADD)
            if (parts.any { it.amountMinor == 0L }) return WriteResult.Rejected(RegisterCopy.AMOUNT_ZERO)
            if (parts.sumOf { it.amountMinor } != draft.amountMinor) return WriteResult.Rejected(RegisterCopy.SPLIT_MUST_ADD)
        }
        if (draft.cleared == null && parts == null) return saveFields(draft)
        // The split and the cleared flag join the same write, so the save is one undo step.
        return write {
            if (parts?.any { !categoryAlive(it.categoryId) } == true) return@write WriteResult.Rejected(RegisterCopy.CHOOSE_CATEGORY)
            val result = saveFields(draft)
            if (result !is WriteResult.Saved || find(draft.id)?.tombstone != false) return@write result
            // The fields were saved (and any reconciled warning answered), so the split goes ahead.
            if (parts != null) split(draft.id, parts, force = true)
            draft.cleared?.let { cleared ->
                (listOf(draft.id) + children(draft.id).map { it.id }).forEach { setColumns(it, "cleared" to cleared) }
            }
            result
        }
    }

    private fun saveFields(draft: TransactionDraft): WriteResult {
        if (draft.transferAccountId != null) return saveTransfer(draft)
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
                val ruled = keepSplit(draft, applyRules(draft, note))
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
            if (existing.parent && draft.parts != null) {
                // The form edits the split itself: its lines are rewritten after this save, or it goes back to one category.
                if (draft.parts.isEmpty()) {
                    children(existing.id).forEach { tombstone(it.id) }
                    setColumns(existing.id, "isParent" to 0)
                }
            } else if (existing.parent) {
                if (draft.amountMinor != existing.amount || draft.categoryId != null) {
                    return@write WriteResult.Rejected(RegisterCopy.SPLIT_MUST_ADD)
                }
                val payeeId = payeeFor(draft.payee)
                updateFields(existing.id, draft.accountId, null, existing.amount, payeeId, note, draft.date)
                propagateChildren(existing.id, draft.accountId, payeeId, draft.date)
                return@write WriteResult.Saved
            }
            if (partner != null) {
                // A transfer given an ordinary payee stops being a transfer: its partner goes, as in Actual.
                tombstone(partner.id)
                setColumns(existing.id, "transferred_id" to null)
            }
            val ruled = keepSplit(draft, applyRules(draft, note))
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

    /** A split keeps the amount its lines add up to and leaves the category to them. */
    private fun keepSplit(
        draft: TransactionDraft,
        ruled: TransactionDraft,
    ): TransactionDraft = if (draft.parts.isNullOrEmpty()) ruled else ruled.copy(amountMinor = draft.amountMinor, categoryId = null)

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
    ): WriteResult = setCategories(mapOf(transactionId to categoryId), force, ForcedWrite.Category(transactionId, categoryId))

    /** Files several transactions at once, as one write: all of them, or none when one is refused. */
    fun setCategories(
        assignments: Map<String, String?>,
        force: Boolean,
        retry: ForcedWrite = ForcedWrite.Categories(assignments),
    ): WriteResult {
        if (assignments.values.any { it != null && !categoryAlive(it) }) return WriteResult.Rejected(RegisterCopy.CHOOSE_CATEGORY)
        return write {
            val found = assignments.keys.map { find(it) ?: return@write WriteResult.Rejected(ShellCopy.NO_ACCOUNTS) }
            val live = found.filter { !it.tombstone }
            if (live.any { it.parent }) return@write WriteResult.Rejected(RegisterCopy.SPLIT_MUST_ADD)
            val partners = live.associate { txn -> txn.id to txn.transferId?.let { find(it) }?.takeIf { !it.tombstone } }
            guard(live + partners.values.filterNotNull(), force, retry)?.let { return@write it }
            // Place every transfer before writing anything, so a refusal leaves the whole batch untouched.
            val placements =
                live.associate { txn ->
                    val partner = partners[txn.id] ?: return@associate txn.id to null
                    val side = account(txn.accountId) ?: return@write WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)
                    val other = account(partner.accountId) ?: return@write WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)
                    txn.id to
                        (placeCategory(side, other, assignments[txn.id]) ?: return@write WriteResult.Rejected(RegisterCopy.CHOOSE_CATEGORY))
                }
            live.forEach { txn ->
                val partner = partners[txn.id]
                val placed = placements[txn.id]
                if (partner != null && placed != null) {
                    updateFields(txn.id, txn.accountId, placed.first, txn.amount, txn.payeeId, txn.note, txn.date)
                    updateFields(partner.id, partner.accountId, placed.second, partner.amount, partner.payeeId, partner.note, partner.date)
                } else {
                    updateFields(txn.id, txn.accountId, assignments[txn.id], txn.amount, txn.payeeId, txn.note, txn.date)
                }
            }
            WriteResult.Saved
        }
    }

    fun delete(
        transactionId: String,
        force: Boolean,
    ): WriteResult = delete(listOf(transactionId), force, ForcedWrite.Delete(transactionId))

    /** Deletes [transactionIds] together, each with its transfer partner and split parts. */
    fun deleteAll(
        transactionIds: List<String>,
        force: Boolean,
    ): WriteResult = delete(transactionIds, force, ForcedWrite.Deletes(transactionIds))

    private fun delete(
        transactionIds: List<String>,
        force: Boolean,
        retry: ForcedWrite,
    ): WriteResult =
        write {
            val rows =
                transactionIds.mapNotNull { find(it) }.filter { !it.tombstone }.flatMap { txn ->
                    val partner = txn.transferId?.let { find(it) }?.takeIf { !it.tombstone }
                    val kids = if (txn.parent) children(txn.id) else emptyList()
                    listOfNotNull(txn, partner) + kids
                }
            if (rows.isEmpty()) return@write WriteResult.Saved
            guard(rows, force, retry)?.let { return@write it }
            rows.map { it.id }.distinct().forEach { tombstone(it) }
            WriteResult.Saved
        }

    /**
     * Clears or unclears [transactionIds] together, with a split's parts following their
     * parent. Unclearing a reconciled transaction asks first and then unreconciles it, as Actual does.
     */
    fun setCleared(
        transactionIds: List<String>,
        cleared: Boolean,
        force: Boolean,
    ): WriteResult =
        write {
            val rows = transactionIds.mapNotNull { find(it) }.filter { !it.tombstone }
            if (!cleared) {
                guard(rows, force, ForcedWrite.Cleared(transactionIds, cleared))?.let { return@write it }
            }
            rows.forEach { txn ->
                val targets = listOf(txn) + if (txn.parent) children(txn.id) else emptyList()
                targets.forEach { target ->
                    if (cleared) {
                        setColumns(target.id, "cleared" to 1)
                    } else {
                        setColumns(target.id, "cleared" to 0, "reconciled" to 0)
                    }
                }
            }
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
            setColumns(txn.id, "isParent" to 1, "isChild" to 0, "category" to null)
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
            setColumns(txn.id, "isParent" to 0, "category" to category)
            WriteResult.Saved
        }

    /**
     * Saves [draft] as one side of a transfer with its `transferAccountId`, as Actual does when the payee is an
     * account's transfer payee: a new transaction gets a partner in that account for the opposite amount; an
     * ordinary one gains a partner; a transfer keeps its partner in step, or moves it when the account changed.
     */
    private fun saveTransfer(draft: TransactionDraft): WriteResult {
        if (draft.amountMinor == 0L) return WriteResult.Rejected(RegisterCopy.AMOUNT_ZERO)
        if (!validDate(draft.date)) return WriteResult.Rejected(RegisterCopy.ENTER_DATE)
        val note = draft.note?.trim()?.ifEmpty { null }
        return write {
            val side = account(draft.accountId)
            val other = draft.transferAccountId?.let { account(it) }
            if (side == null || side.tombstone) return@write WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)
            if (other == null || other.tombstone || other.id == side.id) return@write WriteResult.Rejected(RegisterCopy.CHOOSE_ACCOUNT)
            val existing = find(draft.id)
            if (existing != null && existing.tombstone) return@write WriteResult.Saved
            if (existing != null && (existing.parent || existing.child)) return@write WriteResult.Rejected(RegisterCopy.SPLIT_MUST_ADD)
            val partner = existing?.transferId?.let { find(it) }?.takeIf { !it.tombstone }
            guard(listOfNotNull(existing, partner), draft.force, ForcedWrite.Save(draft.copy(force = true)))?.let {
                return@write it
            }
            val placed = placeCategory(side, other, draft.categoryId) ?: return@write WriteResult.Rejected(RegisterCopy.CHOOSE_CATEGORY)
            val sidePayee = transferPayee(other.id)
            val partnerPayee = transferPayee(side.id)
            if (partner != null && partner.accountId == other.id) {
                updateFields(draft.id, side.id, placed.first, draft.amountMinor, sidePayee, note, draft.date)
                updateFields(partner.id, other.id, placed.second, -draft.amountMinor, partnerPayee, note, partner.date)
                return@write WriteResult.Saved
            }
            partner?.let { tombstone(it.id) }
            val partnerId = ids()
            if (existing == null) {
                insert(
                    id = draft.id,
                    accountId = side.id,
                    categoryId = placed.first,
                    amount = draft.amountMinor,
                    payeeId = sidePayee,
                    note = note,
                    date = draft.date,
                    transferId = partnerId,
                    sort = nextSort(),
                )
            } else {
                updateFields(draft.id, side.id, placed.first, draft.amountMinor, sidePayee, note, draft.date)
                setColumns(draft.id, "transferred_id" to partnerId)
            }
            insert(
                id = partnerId,
                accountId = other.id,
                categoryId = placed.second,
                amount = -draft.amountMinor,
                payeeId = partnerPayee,
                note = note,
                date = draft.date,
                transferId = draft.id,
                sort = nextSort(),
            )
            WriteResult.Saved
        }
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
                SELECT c.id AS id, c.name AS name, c.is_income AS is_income, COALESCE(g.name, '') AS group_name,
                    CASE WHEN IFNULL(c.hidden, 0) = 1 OR IFNULL(g.hidden, 0) = 1 THEN 1 ELSE 0 END AS hidden
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
                    hidden = row.bool("hidden"),
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
        SyncSchema.writeRow(session, "payees", id, "name" to trimmed, "tombstone" to 0)
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
        SyncSchema.writeRow(session, "payees", id, "name" to named, "transfer_acct" to accountId, "tombstone" to 0)
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
        financialId: String? = null,
        importedPayee: String? = null,
        cleared: Boolean = true,
        rawSyncedData: String? = null,
    ) {
        val raw: Array<Pair<String, Any?>> = if (rawSyncedData != null) arrayOf("raw_synced_data" to rawSyncedData) else emptyArray()
        setColumns(
            id,
            "acct" to accountId,
            "amount" to amount,
            "description" to payeeId,
            "notes" to note,
            "date" to date,
            "category" to categoryId,
            "isParent" to parent,
            "isChild" to child,
            "parent_id" to parentId,
            "starting_balance_flag" to 0,
            "transferred_id" to transferId,
            "sort_order" to sort,
            "cleared" to if (cleared) 1 else 0,
            "reconciled" to 0,
            "tombstone" to 0,
            "schedule" to scheduleId,
            "financial_id" to financialId,
            "imported_description" to importedPayee,
            *raw,
        )
    }

    /** Every transaction write goes through the sync funnel (spec 010). */
    private fun setColumns(
        id: String,
        vararg values: Pair<String, Any?>,
    ) {
        SyncSchema.writeRow(session, "transactions", id, *values)
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
        setColumns(
            id,
            "acct" to accountId,
            "category" to categoryId,
            "amount" to amount,
            "description" to payeeId,
            "notes" to note,
            "date" to date,
        )
    }

    private fun propagateChildren(
        parentId: String,
        accountId: String,
        payeeId: String,
        date: Int,
    ) {
        children(parentId).forEach { child ->
            setColumns(child.id, "acct" to accountId, "description" to payeeId, "date" to date)
        }
    }

    private fun tombstone(id: String) {
        setColumns(id, "tombstone" to 1)
    }

    private fun nextSort(): Double =
        session.query("SELECT COALESCE(MAX(sort_order), 0) AS sort FROM transactions").first().double("sort") + 1.0

    fun applyRulesForImport(draft: TransactionDraft): TransactionDraft = applyRules(draft, draft.note?.trim()?.ifEmpty { null })

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
        cleared: Boolean = true,
        rawSyncedData: String? = null,
    ) {
        insert(
            id = id,
            accountId = accountId,
            categoryId = categoryId,
            amount = amount,
            payeeId = payeeId,
            note = note,
            date = date,
            sort = nextSort(),
            financialId = financialId,
            importedPayee = importedPayee,
            cleared = cleared,
            rawSyncedData = rawSyncedData,
        )
    }

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
