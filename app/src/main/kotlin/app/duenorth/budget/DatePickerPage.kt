package app.duenorth.budget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.duenorth.budget.core.DateColumn
import app.duenorth.budget.core.DatePick
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroPickerColumn
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import java.time.LocalDate
import java.time.Month
import java.util.Locale
import java.time.format.TextStyle as DateTextStyle

/** The WP8.1 date picker page: looping month, day and year columns in the settings order (FR-005–FR-007). */
@Composable
internal fun DatePickerPage(
    date: Int,
    dateFormat: String,
    onChange: (Int) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    val done by rememberUpdatedState(onDone)
    val cancel by rememberUpdatedState(onCancel)
    val bar =
        remember {
            listOf(
                ScreenAction("picker-done", AppGlyph.Save, TransactionFormCopy.DONE) { done() },
                ScreenAction("picker-cancel", AppGlyph.Cancel, TransactionFormCopy.CANCEL) { cancel() },
            )
        }
    RememberBarOverride(bar)
    val years = DatePick.yearRange(date)
    Column(
        Modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(horizontal = MetroDimens.Gutter)
            .testTag("transaction-date-picker"),
    ) {
        MetroText(TransactionFormCopy.CHOOSE_DATE, Metro.typography.header, maxLines = 1)
        Row(
            Modifier.fillMaxWidth().padding(top = MetroDimens.Grid),
            horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter),
        ) {
            DatePick.columns(dateFormat).forEach { column ->
                val cell = Modifier.weight(1f)
                when (column) {
                    DateColumn.MONTH ->
                        MetroPickerColumn(
                            count = 12,
                            position = DatePick.month(date) - 1,
                            label = { at ->
                                "%02d".format(at + 1) to
                                    Month.of(at + 1).getDisplayName(DateTextStyle.SHORT, Locale.ENGLISH).lowercase()
                            },
                            onPick = { at -> onChange(DatePick.withMonth(date, at + 1)) },
                            tag = "date-column-month",
                            modifier = cell,
                        )
                    DateColumn.DAY -> {
                        val days = DatePick.daysIn(DatePick.year(date), DatePick.month(date))
                        MetroPickerColumn(
                            count = days,
                            position = DatePick.day(date) - 1,
                            label = { at ->
                                val weekday =
                                    LocalDate
                                        .of(DatePick.year(date), DatePick.month(date), at + 1)
                                        .dayOfWeek
                                        .getDisplayName(DateTextStyle.FULL, Locale.ENGLISH)
                                        .lowercase()
                                "%02d".format(at + 1) to weekday
                            },
                            onPick = { at -> onChange(DatePick.withDay(date, at + 1)) },
                            tag = "date-column-day",
                            modifier = cell,
                        )
                    }
                    DateColumn.YEAR ->
                        MetroPickerColumn(
                            count = years.last - years.first + 1,
                            position = DatePick.year(date) - years.first,
                            label = { at -> (years.first + at).toString() to "" },
                            onPick = { at -> onChange(DatePick.withYear(date, years.first + at)) },
                            tag = "date-column-year",
                            modifier = cell,
                        )
                }
            }
        }
    }
}
