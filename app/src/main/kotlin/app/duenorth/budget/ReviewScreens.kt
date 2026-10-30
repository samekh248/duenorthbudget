package app.duenorth.budget

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.CategoryMonthPage
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MonthReviewPage
import app.duenorth.budget.core.NetWorthPage
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.ReviewCategoryRow
import app.duenorth.budget.core.ReviewCopy
import app.duenorth.budget.core.ReviewTransactionRow
import app.duenorth.budget.core.monthLabel
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import kotlin.math.abs

@Composable
fun MonthReviewSection(
    review: MonthReviewPage?,
    loading: Boolean,
    onListGesture: (Boolean) -> Unit,
    onCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (review == null) {
        if (loading) Placeholder()
        return
    }
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onListGesture(it) }
    }
    Column(modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) {
        MetroText(monthLabel(review.month), Metro.typography.caption, color = Metro.colors.secondary)
        MetroText(ReviewCopy.SPENDING, Metro.typography.header)
        Row(
            Modifier.fillMaxWidth().padding(top = MetroDimens.Grid, bottom = MetroDimens.Row),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetroText(ReviewCopy.INCOME, Metro.typography.subheader, Modifier.weight(1f))
            MetroText(
                MoneyFormat.format(review.incomeMinor, review.currency, formatOptions()),
                Metro.typography.subheader,
                modifier = Modifier.testTag("review-income"),
            )
        }
        if (review.categories.isEmpty()) {
            MetroText("no spending", Metro.typography.body, Modifier.padding(top = MetroDimens.Grid))
        } else {
            LazyColumn(Modifier.weight(1f), state = list) {
                items(review.categories, key = { it.id }) { row ->
                    ReviewCategoryLine(row, review, onCategory)
                }
            }
        }
    }
}

@Composable
private fun ReviewCategoryLine(
    row: ReviewCategoryRow,
    review: MonthReviewPage,
    onCategory: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress { onCategory(row.id) }
            .padding(vertical = MetroDimens.Row)
            .testTag("review-category-${row.id}"),
        verticalAlignment = Alignment.Top,
    ) {
        MetroText(row.name, Metro.typography.subheader, Modifier.weight(1f).padding(end = 12.dp), maxLines = 3)
        MetroText(
            MoneyFormat.format(-abs(row.spentMinor), review.currency, formatOptions()),
            Metro.typography.subheader,
            Modifier.widthIn(max = 148.dp),
        )
    }
}

@Composable
fun CategoryReviewScreen(
    page: CategoryMonthPage,
    onGesture: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onGesture(it) }
    }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(horizontal = MetroDimens.Gutter),
    ) {
        MetroText(page.categoryName, Metro.typography.header)
        Column(Modifier.padding(top = MetroDimens.Gutter)) {
            MetroText(monthLabel(page.month), Metro.typography.caption, color = Metro.colors.secondary)
            MetroText(
                MoneyFormat.format(-abs(page.totalMinor), page.currency, formatOptions()),
                Metro.typography.subheader,
                Modifier.testTag("category-review-total"),
            )
        }
        LazyColumn(Modifier.weight(1f).padding(top = MetroDimens.Grid), state = list) {
            items(page.rows, key = { it.id }) { row -> CategoryReviewLine(row, page) }
        }
    }
}

@Composable
private fun CategoryReviewLine(
    row: ReviewTransactionRow,
    page: CategoryMonthPage,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .padding(vertical = MetroDimens.Row),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            MetroText(row.payee, Metro.typography.subheader, maxLines = 2)
            MetroText(RegisterEntry.registerDateLabel(row.date, dateFormat()), Metro.typography.caption, color = Metro.colors.secondary)
        }
        MetroText(
            MoneyFormat.format(row.amountMinor, page.currency, formatOptions()),
            Metro.typography.subheader,
            Modifier.widthIn(max = 120.dp),
        )
    }
}
