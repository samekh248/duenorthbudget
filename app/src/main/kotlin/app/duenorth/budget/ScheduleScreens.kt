package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountRow
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.RuleValues
import app.duenorth.budget.core.ScheduleDraft
import app.duenorth.budget.core.ScheduleEnd
import app.duenorth.budget.core.ScheduleRepeat
import app.duenorth.budget.core.UpcomingScheduleRow
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroSectionHeading
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

@Composable
fun ScheduleDuplicateScreen(
    onPostAnyway: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmPage(
        title = ScheduleScreenCopy.DUPLICATE_TITLE,
        body = ScheduleScreenCopy.DUPLICATE_BODY,
        yes = ScheduleScreenCopy.POST_ANYWAY,
        onYes = onPostAnyway,
        onKeep = onCancel,
        tag = "schedule-duplicate",
        modifier = modifier,
    )
}

/** Words for the schedule editor (`contracts/schedules-rules.md`). */
object ScheduleScreenCopy {
    const val SCHEDULES = "schedules"
    const val DUPLICATE_TITLE = "post it again?"
    const val DUPLICATE_BODY = "a similar transaction is already in this account within two days of the due date"
    const val POST_ANYWAY = "post anyway"
    const val UPCOMING = "upcoming"
    const val ORDER_HINT = "hold a schedule and drag it to change the order of ones due the same day"
    const val ALL = "all"
    const val NEW = "new"
    const val LIST = "list"
    const val CALENDAR = "calendar"
    const val POST = "post"
    const val POST_EARLY = "post early"
    const val SKIP = "skip"
    const val EDIT_ACTION = "edit"
    const val NEXT_30_DAYS = "next 30 days"
    const val COMING_IN = "coming in"
    const val DUE_TODAY = "due today"
    const val DUE_ON = "due"
    const val NOTHING_COMING = "nothing coming up in the next 30 days"
    const val NOTHING_ON_DAY = "nothing scheduled"
    const val NO_SCHEDULES = "no schedules yet"
    const val ENDED = "Ended"
    const val ENDED_LINE = "ended"
    const val NEXT = "next"
    const val REPEATS = "repeats"
    const val COMING_UP = "coming up"
    const val POSTED = "posted"
    const val NOTHING_POSTED = "nothing posted yet"
    const val ADDS = "adds the transaction"
    const val ADDS_YES = "yes, on its date"
    const val ADDS_NO = "no, waits for post"
    const val ADDS_ITSELF = "adds itself"
    const val ADD = "new schedule"
    const val EDIT = "schedule"
    const val DELETE = "delete schedule"
    const val NAME = "name"
    const val PAYEE = "payee"
    const val AMOUNT = "amount"
    const val AMOUNT_TO = "up to"
    const val EXACTLY = "exactly"
    const val ABOUT = "about"
    const val BETWEEN = "between"
    const val ACCOUNT = "account"
    const val CATEGORY = "category"
    const val NO_CATEGORY = "none"
    const val START = "date (yyyy-mm-dd)"
    const val EVERY = "every"
    const val ENDS = "ends"
    const val NEVER = "never"
    const val AFTER = "after"
    const val ON_DATE = "on"
    const val TIMES = "times"
    const val END_DATE = "end date (yyyy-mm-dd)"
    const val AUTO_POST = "add the transaction automatically"
    const val KEPT = "kept from the file: day pattern and weekend move"
    const val ENTER_AMOUNT = "enter an amount"

    /** "every month", "every 2 weeks", or "once". */
    fun every(
        repeat: ScheduleRepeat,
        interval: Int,
    ): String {
        val unit =
            when (repeat) {
                ScheduleRepeat.ONCE -> return "once"
                ScheduleRepeat.DAILY -> "day"
                ScheduleRepeat.WEEKLY -> "week"
                ScheduleRepeat.MONTHLY -> "month"
                ScheduleRepeat.YEARLY -> "year"
            }
        return if (interval <= 1) "every $unit" else "every $interval ${unit}s"
    }

    /** How the amount is matched: exactly, about, or between. */
    fun amountKind(op: String): String =
        when (op) {
            "isapprox" -> ABOUT
            "isbetween" -> BETWEEN
            else -> EXACTLY
        }

    fun repeat(value: ScheduleRepeat): String =
        when (value) {
            ScheduleRepeat.ONCE -> "once"
            ScheduleRepeat.DAILY -> "daily"
            ScheduleRepeat.WEEKLY -> "weekly"
            ScheduleRepeat.MONTHLY -> "monthly"
            ScheduleRepeat.YEARLY -> "yearly"
        }
}

/** The editor's typed fields; [toDraft] turns them into a draft, or null when an amount does not parse. */
data class ScheduleForm(
    val name: String,
    val accountId: String,
    val payee: String,
    val amount: String,
    val amountOp: String,
    val amount2: String,
    val categoryId: String?,
    val repeat: ScheduleRepeat,
    val interval: String,
    val start: String,
    val end: ScheduleEnd,
    val endCount: String,
    val endDate: String,
    val postsTransaction: Boolean,
) {
    /** The typed amounts and dates rewritten from the person's format into the form [toDraft] reads. */
    fun read(
        prefs: FormatPrefs,
        decimals: Int,
    ): ScheduleForm =
        copy(
            amount = prefs.readAmount(amount, decimals),
            amount2 = prefs.readAmount(amount2, decimals),
            start = prefs.readDate(start),
            endDate = prefs.readDate(endDate),
        )

    fun toDraft(
        base: ScheduleDraft,
        decimals: Int,
    ): ScheduleDraft? {
        val low = MoneyFormat.parse(amount, decimals) ?: return null
        val high = if (amountOp == "isbetween") MoneyFormat.parse(amount2, decimals) ?: return null else 0L
        return base.copy(
            name = name.trim(),
            accountId = accountId,
            payeeName = payee.trim(),
            amountMinor = low,
            amountOp = amountOp,
            amount2Minor = high,
            categoryId = categoryId,
            repeat = repeat,
            interval = interval.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1,
            startIso = start.trim(),
            end = end,
            endOccurrences = endCount.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1,
            endDateIso = endDate.trim(),
            postsTransaction = postsTransaction,
        )
    }

    companion object {
        /** Without [prefs] the amount stays as "-25.00", the form tests and older callers type. */
        private fun amountText(
            minor: Long,
            decimals: Int,
            prefs: FormatPrefs?,
        ): String = prefs?.editSignedAmount(minor, decimals) ?: RuleValues.amountText(minor, decimals)

        fun from(
            draft: ScheduleDraft,
            decimals: Int,
            prefs: FormatPrefs? = null,
        ): ScheduleForm =
            ScheduleForm(
                name = draft.name,
                accountId = draft.accountId,
                payee = draft.payeeName,
                amount = if (draft.id == null && draft.amountMinor == 0L) "" else amountText(draft.amountMinor, decimals, prefs),
                amountOp = draft.amountOp,
                amount2 = if (draft.amountOp == "isbetween") amountText(draft.amount2Minor, decimals, prefs) else "",
                categoryId = draft.categoryId,
                repeat = draft.repeat,
                interval = draft.interval.toString(),
                start = prefs?.showDate(draft.startIso) ?: draft.startIso,
                end = draft.end,
                endCount = draft.endOccurrences.toString(),
                endDate = prefs?.showDate(draft.endDateIso) ?: draft.endDateIso,
                postsTransaction = draft.postsTransaction,
            )
    }
}

/**
 * Add or edit a schedule: account, payee, amount, category, repeat, end, and auto-post.
 * A day pattern or weekend move from desktop is shown and kept. Tag `schedule-editor`.
 */
@Composable
fun ScheduleEditorScreen(
    draft: ScheduleDraft,
    accounts: List<AccountRow>,
    categories: List<CategoryChoice>,
    decimals: Int,
    error: String?,
    onSave: (ScheduleForm) -> Unit,
    modifier: Modifier = Modifier,
    payees: List<String> = emptyList(),
) {
    val prefs = LocalFormatPrefs.current
    var form by remember(draft.id, prefs) { mutableStateOf(ScheduleForm.from(draft, decimals, prefs)) }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(MetroDimens.Gutter)
            .testTag("schedule-editor"),
        verticalArrangement = Arrangement.spacedBy(MetroDimens.Row),
    ) {
        MetroText(if (draft.id == null) ScheduleScreenCopy.ADD else ScheduleScreenCopy.EDIT, Metro.typography.header)
        MetroField(form.name, { form = form.copy(name = it) }, ScheduleScreenCopy.NAME, Modifier.testTag("schedule-name"))
        PayeeField(form.payee, { form = form.copy(payee = it) }, payees, ScheduleScreenCopy.PAYEE, "schedule-payee")
        Heading(ScheduleScreenCopy.AMOUNT)
        Choices {
            listOf("is" to ScheduleScreenCopy.EXACTLY, "isapprox" to ScheduleScreenCopy.ABOUT, "isbetween" to ScheduleScreenCopy.BETWEEN)
                .forEach { (op, label) -> Choice(label, form.amountOp == op, "schedule-amount-$op") { form = form.copy(amountOp = op) } }
        }
        MetroField(form.amount, { form = form.copy(amount = it) }, ScheduleScreenCopy.AMOUNT, Modifier.testTag("schedule-amount"))
        if (form.amountOp == "isbetween") {
            MetroField(form.amount2, { form = form.copy(amount2 = it) }, ScheduleScreenCopy.AMOUNT_TO, Modifier.testTag("schedule-amount2"))
        }
        Heading(ScheduleScreenCopy.ACCOUNT)
        Choices {
            accounts.filter { !it.closed }.forEach { account ->
                Choice(account.name, form.accountId == account.id, "schedule-account-${account.id}") {
                    form = form.copy(accountId = account.id)
                }
            }
        }
        Heading(ScheduleScreenCopy.CATEGORY)
        Choices {
            Choice(
                ScheduleScreenCopy.NO_CATEGORY,
                form.categoryId == null,
                "schedule-category-none",
            ) { form = form.copy(categoryId = null) }
            categories.forEach { category ->
                Choice(category.name, form.categoryId == category.id, "schedule-category-${category.id}") {
                    form = form.copy(categoryId = category.id)
                }
            }
        }
        MetroField(form.start, { form = form.copy(start = it) }, ScheduleScreenCopy.START, Modifier.testTag("schedule-start"))
        Heading(ScheduleScreenCopy.EVERY)
        Choices {
            ScheduleRepeat.entries.forEach { repeat ->
                Choice(ScheduleScreenCopy.repeat(repeat), form.repeat == repeat, "schedule-repeat-${repeat.name.lowercase()}") {
                    form = form.copy(repeat = repeat)
                }
            }
        }
        if (form.repeat != ScheduleRepeat.ONCE) {
            MetroField(form.interval, { form = form.copy(interval = it) }, ScheduleScreenCopy.EVERY, Modifier.testTag("schedule-interval"))
            Heading(ScheduleScreenCopy.ENDS)
            Choices {
                listOf(
                    ScheduleEnd.NEVER to ScheduleScreenCopy.NEVER,
                    ScheduleEnd.AFTER to ScheduleScreenCopy.AFTER,
                    ScheduleEnd.ON_DATE to ScheduleScreenCopy.ON_DATE,
                ).forEach { (end, label) -> Choice(label, form.end == end, "schedule-end-${end.mode}") { form = form.copy(end = end) } }
            }
            when (form.end) {
                ScheduleEnd.AFTER ->
                    MetroField(
                        form.endCount,
                        { form = form.copy(endCount = it) },
                        ScheduleScreenCopy.TIMES,
                        Modifier.testTag("schedule-end-count"),
                    )
                ScheduleEnd.ON_DATE ->
                    MetroField(
                        form.endDate,
                        { form = form.copy(endDate = it) },
                        ScheduleScreenCopy.END_DATE,
                        Modifier.testTag("schedule-end-date"),
                    )
                ScheduleEnd.NEVER -> Unit
            }
            if (draft.hasKeptDateParts) {
                MetroText(ScheduleScreenCopy.KEPT, Metro.typography.caption, Modifier.testTag("schedule-kept"), color = Metro.accent.text)
            }
        }
        Choice(
            ScheduleScreenCopy.AUTO_POST + if (form.postsTransaction) "  on" else "",
            form.postsTransaction,
            "schedule-posts",
        ) { form = form.copy(postsTransaction = !form.postsTransaction) }
        if (error != null) MetroText(error, Metro.typography.body, Modifier.testTag("schedule-error"), color = Metro.accent.text)
        RememberFormSave { onSave(form.read(prefs, decimals)) }
        if (!formSaveOnBar()) {
            MetroButton("save", Modifier.testTag("schedule-save")) { onSave(form.read(prefs, decimals)) }
        }
    }
}

@Composable
private fun Heading(text: String) {
    MetroSectionHeading(text)
}

@Composable
private fun Choices(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
