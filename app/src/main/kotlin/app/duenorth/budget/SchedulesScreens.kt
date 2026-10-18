package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.ScheduleDetails
import app.duenorth.budget.core.ScheduleSummary
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroPivot
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.PivotItem
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import java.time.LocalDate
import java.time.YearMonth

/** The schedules pivot's items, in order; [ShellUiState.schedulesSection] indexes them. */
object SchedulesSection {
    const val UPCOMING = 0
    const val ALL = 1
}

/** One date a schedule falls on, as upcoming and the calendar list it. */
data class ScheduleOccurrence(
    val schedule: ScheduleSummary,
    val date: Int,
) {
    /** The schedule's next date: the one post and skip act on. */
    val isNext: Boolean get() = date == schedule.nextDate
}

/** What upcoming and the calendar are built from: every open schedule's dates, in date order. */
object ScheduleOccurrences {
    fun of(schedules: List<ScheduleSummary>): List<ScheduleOccurrence> =
        schedules
            .filter { !it.completed }
            .flatMap { schedule -> schedule.dates.map { ScheduleOccurrence(schedule, it) } }
            .sortedWith(compareBy({ it.date }, { it.schedule.title }))

    /** The next 30 days from [today], with anything overdue still waiting on post or skip. */
    fun upcoming(
        schedules: List<ScheduleSummary>,
        today: Int,
    ): List<ScheduleOccurrence> {
        val end = toInt(fromInt(today).plusDays(30))
        return of(schedules).filter { it.date <= end && (it.date >= today || it.isNext) }
    }

    fun fromInt(date: Int): LocalDate = LocalDate.of(date / 10000, (date / 100) % 100, date % 100)

    fun toInt(date: LocalDate): Int = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
}

/**
 * Accounts → schedules: a pivot of upcoming (the next 30 days as a list, or a month calendar)
 * and all (every schedule by account, ended ones folded away). Tag `schedules-screen`.
 */
@Composable
fun SchedulesScreen(
    schedules: List<ScheduleSummary>,
    currency: CurrencySpec,
    today: Int,
    section: Int,
    calendar: Boolean,
    onSection: (Int) -> Unit,
    onOpen: (String) -> Unit,
    onPost: (String) -> Unit,
    onSkip: (String) -> Unit,
    onGesture: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    MetroPivot(
        title = SectionTitleCopy.SCHEDULES,
        items =
            listOf(
                PivotItem(ScheduleScreenCopy.UPCOMING) {
                    if (calendar) {
                        ScheduleCalendar(schedules, currency, today, onOpen, onPost, onSkip)
                    } else {
                        UpcomingList(schedules, currency, today, onOpen, onPost, onSkip, onGesture)
                    }
                },
                PivotItem(ScheduleScreenCopy.ALL) { AllSchedules(schedules, currency, onOpen, onGesture) },
            ),
        modifier = modifier.background(Metro.colors.background).testTag("schedules-screen"),
        initialItem = section,
        onGesture = onGesture,
        onSelected = onSection,
    )
}

@Composable
private fun UpcomingList(
    schedules: List<ScheduleSummary>,
    currency: CurrencySpec,
    today: Int,
    onOpen: (String) -> Unit,
    onPost: (String) -> Unit,
    onSkip: (String) -> Unit,
    onGesture: (Boolean) -> Unit,
) {
    val rows = ScheduleOccurrences.upcoming(schedules, today)
    val options = formatOptions()
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onGesture(it) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter).testTag("schedules-upcoming")) {
        Row(Modifier.fillMaxWidth()) {
            BudgetTotal(
                label = ScheduleScreenCopy.NEXT_30_DAYS,
                amount =
                    MoneyFormat.format(
                        rows.filter { it.schedule.amountMinor < 0 }.sumOf { it.schedule.amountMinor },
                        currency,
                        options,
                    ),
                amountColor = Metro.accent.text,
                amountTag = "schedules-out",
                modifier = Modifier.weight(1f),
            )
            BudgetTotal(
                label = ScheduleScreenCopy.COMING_IN,
                amount =
                    MoneyFormat.format(
                        rows.filter { it.schedule.amountMinor > 0 }.sumOf { it.schedule.amountMinor },
                        currency,
                        options,
                    ),
                amountColor = Metro.colors.foreground,
                amountTag = "schedules-in",
                alignEnd = true,
            )
        }
        if (rows.isEmpty()) {
            MetroText(
                if (schedules.isEmpty()) ScheduleScreenCopy.NO_SCHEDULES else ScheduleScreenCopy.NOTHING_COMING,
                Metro.typography.body,
                Modifier.padding(top = MetroDimens.Grid),
                color = Metro.colors.secondary,
            )
            return
        }
        // What is due today or overdue leads in the featured panel, to post or skip in place.
        val due = rows.filter { it.isNext && it.date <= today }
        val later = rows - due.toSet()
        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = MetroDimens.Row), state = list) {
            items(due, key = { "due-${it.schedule.id}" }) { DuePanel(it, currency, today, onOpen, onPost, onSkip) }
            occurrenceDays(later, currency, onOpen)
        }
    }
}

/** Day headings over each day's schedules, the way the inbox and register head their days. */
private fun LazyListScope.occurrenceDays(
    rows: List<ScheduleOccurrence>,
    currency: CurrencySpec,
    onOpen: (String) -> Unit,
) {
    rows.groupBy { it.date }.forEach { (date, day) ->
        item(key = "day-$date") { DayHeading(RegisterEntry.registerDateLabel(date)) }
        day.forEach { row ->
            item(key = "row-${row.schedule.id}-$date") {
                ScheduleLine(
                    title = row.schedule.title,
                    detail = upcomingDetail(row.schedule),
                    amount = scheduleAmount(row.schedule, currency),
                    income = row.schedule.amountMinor > 0,
                    tag = "schedule-row-${row.schedule.id}-$date",
                    onClick = { onOpen(row.schedule.id) },
                )
            }
        }
    }
}

@Composable
private fun DuePanel(
    row: ScheduleOccurrence,
    currency: CurrencySpec,
    today: Int,
    onOpen: (String) -> Unit,
    onPost: (String) -> Unit,
    onSkip: (String) -> Unit,
) {
    val schedule = row.schedule
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = MetroDimens.Row)
            .ledgerFill(Metro.colors.chrome)
            .padding(vertical = 14.dp)
            .testTag("schedule-due-${schedule.id}"),
    ) {
        Column(Modifier.fillMaxWidth().metroPress { onOpen(schedule.id) }) {
            MetroText(
                if (row.date == today) {
                    ScheduleScreenCopy.DUE_TODAY
                } else {
                    "${ScheduleScreenCopy.DUE_ON} ${RegisterEntry.registerDateLabel(row.date)}"
                },
                Metro.typography.caption,
                color = Metro.accent.text,
            )
            Row(verticalAlignment = Alignment.Top) {
                MetroText(schedule.title, Metro.typography.groupHeader, Modifier.weight(1f).padding(end = MetroDimens.Gutter), maxLines = 2)
                MetroText(scheduleAmount(schedule, currency), Metro.typography.groupHeader, color = Metro.accent.text, maxLines = 1)
            }
            MetroText(upcomingDetail(schedule), Metro.typography.caption, color = Metro.colors.secondary, maxLines = 2)
        }
        MetroButtonGrid(Modifier.padding(top = 12.dp)) {
            MetroButton(ScheduleScreenCopy.POST, Modifier.testTag("schedule-post-${schedule.id}")) { onPost(schedule.id) }
            MetroButton(ScheduleScreenCopy.SKIP, Modifier.testTag("schedule-skip-${schedule.id}")) { onSkip(schedule.id) }
        }
    }
}

/**
 * Upcoming as a month: a square for each day, filled in the accent where the picked day is, with a
 * mark where something is scheduled; today is outlined. The picked day's schedules list underneath.
 */
@Composable
private fun ScheduleCalendar(
    schedules: List<ScheduleSummary>,
    currency: CurrencySpec,
    today: Int,
    onOpen: (String) -> Unit,
    onPost: (String) -> Unit,
    onSkip: (String) -> Unit,
) {
    val todayDate = ScheduleOccurrences.fromInt(today)
    var monthIndex by rememberSaveable { mutableIntStateOf(todayDate.year * 12 + todayDate.monthValue - 1) }
    var picked by rememberSaveable { mutableIntStateOf(today) }
    val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)
    val byDay = ScheduleOccurrences.of(schedules).groupBy { it.date }
    val firstDay = LocalFormatPrefs.current.firstDayOfWeekIdx.coerceIn(0, 6)
    val names = listOf("s", "m", "t", "w", "t", "f", "s")
    // Sunday is 0, as Actual counts the first day of the week.
    val lead = (month.atDay(1).dayOfWeek.value % 7 - firstDay + 7) % 7
    val cells = List(lead) { null } + (1..month.lengthOfMonth()).toList()
    val showMonth = { delta: Int ->
        monthIndex += delta
        val shown = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)
        picked = if (YearMonth.from(todayDate) == shown) today else ScheduleOccurrences.toInt(shown.atDay(1))
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter).testTag("schedules-calendar")) {
        item(key = "month") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetroText(monthLabelOf(month), Metro.typography.subheader, Modifier.weight(1f).testTag("calendar-month"))
                MonthIconButton(AppGlyph.ChevronLeft, "calendar-previous") { showMonth(-1) }
                MonthIconButton(AppGlyph.ChevronRight, "calendar-next") { showMonth(1) }
            }
        }
        item(key = "weekdays") {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                (0 until 7).forEach { i ->
                    MetroText(
                        names[(firstDay + i) % 7],
                        Metro.typography.caption.copy(textAlign = TextAlign.Center),
                        Modifier.weight(1f),
                        color = Metro.colors.secondary,
                    )
                }
            }
        }
        cells.chunked(7).forEachIndexed { week, days ->
            item(key = "week-$week") {
                Row(Modifier.fillMaxWidth().padding(top = CELL_GAP), horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                    (0 until 7).forEach { i ->
                        val day = days.getOrNull(i)
                        if (day == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val date = ScheduleOccurrences.toInt(month.atDay(day))
                            DayCell(
                                day = day,
                                marked = byDay.containsKey(date),
                                picked = date == picked,
                                today = date == today,
                                past = date < today,
                                modifier = Modifier.weight(1f),
                            ) { picked = date }
                        }
                    }
                }
            }
        }
        item(key = "picked") {
            DayHeading(RegisterEntry.registerDateLabel(picked))
        }
        val rows = byDay[picked].orEmpty()
        if (rows.isEmpty()) {
            item(key = "picked-empty") {
                MetroText(ScheduleScreenCopy.NOTHING_ON_DAY, Metro.typography.body, color = Metro.colors.secondary)
            }
        }
        rows.forEach { row ->
            item(key = "picked-${row.schedule.id}") {
                Column {
                    ScheduleLine(
                        title = row.schedule.title,
                        detail = upcomingDetail(row.schedule),
                        amount = scheduleAmount(row.schedule, currency),
                        income = row.schedule.amountMinor > 0,
                        tag = "calendar-row-${row.schedule.id}",
                        onClick = { onOpen(row.schedule.id) },
                    )
                    // Post and skip act on a schedule's next date only.
                    if (row.isNext) {
                        MetroButtonGrid(Modifier.padding(vertical = 8.dp)) {
                            MetroButton(if (row.date <= today) ScheduleScreenCopy.POST else ScheduleScreenCopy.POST_EARLY) {
                                onPost(row.schedule.id)
                            }
                            MetroButton(ScheduleScreenCopy.SKIP) { onSkip(row.schedule.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    marked: Boolean,
    picked: Boolean,
    today: Boolean,
    past: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .aspectRatio(1f)
            .background(if (picked) Metro.accent.fill else Metro.colors.chrome)
            .then(if (today) Modifier.border(MetroDimens.Stroke, Metro.accent.fill) else Modifier)
            .metroPress(onClick)
            .testTag("calendar-day-$day"),
    ) {
        MetroText(
            "$day",
            Metro.typography.caption,
            Modifier.align(Alignment.BottomStart).padding(4.dp),
            color =
                when {
                    picked -> Metro.accent.onFill
                    past -> Metro.colors.secondary
                    else -> Metro.colors.foreground
                },
        )
        if (marked) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(8.dp)
                    .background(if (picked) Metro.accent.onFill else Metro.accent.fill)
                    .testTag("calendar-mark-$day"),
            )
        }
    }
}

/** Every schedule, grouped by account, with ended ones folded under their own heading. */
@Composable
private fun AllSchedules(
    schedules: List<ScheduleSummary>,
    currency: CurrencySpec,
    onOpen: (String) -> Unit,
    onGesture: (Boolean) -> Unit,
) {
    if (schedules.isEmpty()) {
        MetroText(
            ScheduleScreenCopy.NO_SCHEDULES,
            Metro.typography.body,
            Modifier.padding(MetroDimens.Gutter),
            color = Metro.colors.secondary,
        )
        return
    }
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onGesture(it) }
    }
    var showEnded by rememberSaveable { mutableStateOf(false) }
    val groups = schedules.filter { !it.completed }.groupBy { it.accountName }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    val ended = schedules.filter { it.completed }
    val position = (groups.values.flatten() + ended).withIndex().associate { it.value.id to it.index }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter).testTag("schedules-all"), state = list) {
        groups.forEach { (account, rows) ->
            item(key = "group-$account") {
                GroupHeading(account, "schedule-group", account) {
                    MetroText("${rows.size}", Metro.typography.groupHeader, it)
                }
            }
            items(rows, key = { it.id }) { schedule ->
                ScheduleLine(
                    title = schedule.title,
                    detail =
                        "${ScheduleScreenCopy.every(schedule.repeat, schedule.interval)} · " +
                            "${ScheduleScreenCopy.NEXT} ${RegisterEntry.registerDateLabel(schedule.nextDate)}",
                    amount = scheduleAmount(schedule, currency),
                    income = schedule.amountMinor > 0,
                    tag = "schedule-${schedule.id}",
                    banded = ledgerBanded(position.getValue(schedule.id)),
                    onClick = { onOpen(schedule.id) },
                )
            }
            item(key = "gap-$account") { GroupGap() }
        }
        if (ended.isNotEmpty()) {
            item(key = "group-ended") {
                GroupHeading(
                    ScheduleScreenCopy.ENDED,
                    "schedule-group",
                    "ended",
                    Modifier.metroPress { showEnded = !showEnded },
                ) {
                    Box(it.graphicsLayer { rotationZ = if (showEnded) 180f else 0f }) {
                        AppGlyphIcon(AppGlyph.ChevronDown)
                    }
                }
            }
            if (showEnded) {
                items(ended, key = { it.id }) { schedule ->
                    ScheduleLine(
                        title = schedule.title,
                        detail = "${schedule.accountName} · ${ScheduleScreenCopy.ENDED_LINE}",
                        amount = scheduleAmount(schedule, currency),
                        income = false,
                        tag = "schedule-${schedule.id}",
                        banded = ledgerBanded(position.getValue(schedule.id)),
                        dim = true,
                        onClick = { onOpen(schedule.id) },
                    )
                }
            }
        }
    }
}

/**
 * Schedules → one schedule: what it posts and when, its next few dates, and the transactions it has
 * posted. Post, skip and edit are on the bar. Tag `schedule-details`.
 */
@Composable
fun ScheduleDetailsScreen(
    details: ScheduleDetails,
    currency: CurrencySpec,
    modifier: Modifier = Modifier,
) {
    val schedule = details.summary
    val options = formatOptions()
    LazyColumn(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(horizontal = MetroDimens.Gutter)
            .testTag("schedule-details"),
    ) {
        item(key = "title") {
            MetroText(schedule.title, Metro.typography.header, Modifier.testTag("schedule-details-title"), maxLines = 2)
        }
        item(key = "totals") {
            Row(Modifier.fillMaxWidth().padding(top = MetroDimens.Row, bottom = MetroDimens.Grid)) {
                BudgetTotal(
                    label =
                        if (schedule.completed) {
                            ScheduleScreenCopy.ENDED_LINE
                        } else {
                            "${ScheduleScreenCopy.NEXT}, ${RegisterEntry.registerDateLabel(schedule.nextDate)}"
                        },
                    amount = scheduleAmount(schedule, currency),
                    amountColor = Metro.accent.text,
                    amountTag = "schedule-details-amount",
                    modifier = Modifier.weight(1f),
                )
                BudgetTotal(
                    label = ScheduleScreenCopy.REPEATS,
                    amount = ScheduleScreenCopy.every(schedule.repeat, schedule.interval),
                    amountColor = Metro.colors.foreground,
                    amountTag = "schedule-details-repeat",
                    alignEnd = true,
                )
            }
        }
        item(key = "facts") {
            Column {
                if (schedule.name.isNotBlank() && schedule.payee != schedule.name) Fact(ScheduleScreenCopy.PAYEE, schedule.payee)
                Fact(ScheduleScreenCopy.ACCOUNT, schedule.accountName)
                Fact(ScheduleScreenCopy.CATEGORY, schedule.categoryLabel)
                Fact(
                    ScheduleScreenCopy.AMOUNT,
                    "${ScheduleScreenCopy.amountKind(schedule.amountOp)} ${MoneyFormat.format(schedule.amountMinor, currency, options)}",
                )
                Fact(ScheduleScreenCopy.ADDS, if (schedule.postsTransaction) ScheduleScreenCopy.ADDS_YES else ScheduleScreenCopy.ADDS_NO)
            }
        }
        if (schedule.dates.isNotEmpty()) {
            item(key = "coming") { DayHeading(ScheduleScreenCopy.COMING_UP) }
            schedule.dates.take(COMING_DATES).forEachIndexed { i, date ->
                item(key = "coming-$date") {
                    ScheduleLine(
                        title = RegisterEntry.registerDateLabel(date),
                        detail = null,
                        amount = scheduleAmount(schedule, currency),
                        income = false,
                        tag = "schedule-coming-$date",
                        banded = ledgerBanded(i),
                    )
                }
            }
        }
        item(key = "posted") { DayHeading(ScheduleScreenCopy.POSTED) }
        if (details.posted.isEmpty()) {
            item(key = "posted-empty") {
                MetroText(ScheduleScreenCopy.NOTHING_POSTED, Metro.typography.body, color = Metro.colors.secondary)
            }
        }
        details.posted.forEachIndexed { i, posted ->
            item(key = "posted-${posted.id}") {
                ScheduleLine(
                    title = RegisterEntry.registerDateLabel(posted.date),
                    detail = posted.categoryLabel,
                    amount = MoneyFormat.format(posted.amountMinor, currency, options),
                    income = false,
                    tag = "schedule-posted-${posted.id}",
                    banded = ledgerBanded(i),
                    dim = true,
                )
            }
        }
        item(key = "end") { Spacer(Modifier.height(MetroDimens.Grid)) }
    }
}

@Composable
private fun Fact(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        MetroText(label, Metro.typography.body, Modifier.padding(end = MetroDimens.Gutter), color = Metro.colors.secondary)
        MetroText(value, Metro.typography.body.copy(textAlign = TextAlign.End), Modifier.weight(1f))
    }
}

@Composable
private fun DayHeading(label: String) {
    MetroText(label, Metro.typography.caption, Modifier.padding(top = 14.dp, bottom = 4.dp), color = Metro.accent.text)
}

/** A schedule (or one of its dates) on a ledger line: title and detail, amount at the end. */
@Composable
private fun ScheduleLine(
    title: String,
    detail: String?,
    amount: String,
    income: Boolean,
    tag: String,
    banded: Boolean = false,
    dim: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .ledgerFill(if (banded) ledgerBand else Color.Transparent)
            .then(if (onClick != null) Modifier.metroPress(onClick) else Modifier)
            .padding(vertical = 6.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            MetroText(title, Metro.typography.body, color = if (dim) Metro.colors.secondary else Metro.colors.foreground, maxLines = 2)
            if (detail != null) MetroText(detail, Metro.typography.caption, color = Metro.colors.secondary, maxLines = 2)
        }
        MetroText(
            amount,
            Metro.typography.body,
            Modifier.widthIn(max = 160.dp),
            color =
                when {
                    dim -> Metro.colors.secondary
                    income -> Metro.accent.text
                    else -> Metro.colors.foreground
                },
            maxLines = 1,
        )
    }
}

/** Account · category, then "about" or "adds itself" when they apply. */
private fun upcomingDetail(schedule: ScheduleSummary): String =
    listOfNotNull(
        schedule.accountName.ifBlank { null },
        schedule.categoryLabel.ifBlank { null },
        if (schedule.amountOp == "isapprox") ScheduleScreenCopy.ABOUT else null,
        if (schedule.postsTransaction) ScheduleScreenCopy.ADDS_ITSELF else null,
    ).joinToString(" · ")

@Composable
private fun scheduleAmount(
    schedule: ScheduleSummary,
    currency: CurrencySpec,
    options: FormatOptions = formatOptions(),
): String = MoneyFormat.format(schedule.amountMinor, currency, options)

private fun monthLabelOf(month: YearMonth): String = "${month.month.name.lowercase()} ${month.year}"

private val CELL_GAP = 4.dp
private const val COMING_DATES = 3
