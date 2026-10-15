package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

/** The Home pivot's headers, in order; [ShellUiState.homeSection] indexes them. */
object HomeSections {
    const val BUDGET = "budget"
    const val ACCOUNTS = "accounts"
    const val DUE = "due"
    const val REVIEW = "review"
    const val NET_WORTH = "net worth"
    const val INBOX = "inbox"

    val headers = listOf(BUDGET, ACCOUNTS, DUE, REVIEW, NET_WORTH, INBOX)
}

object SectionTitleCopy {
    const val APP = "DUE NORTH BUDGET"
    const val SETTINGS = "SETTINGS"
}

/**
 * The small all-caps line over a drill-in page's title, naming the area the page sits
 * under (Windows Phone 8.1 title area). Pivots carry their own, so they get none.
 */
object SectionTitle {
    fun of(state: ShellUiState): String? {
        val route = state.route
        if (route in OWN_TITLE) return null
        if (state.settingsReturn) return SectionTitleCopy.SETTINGS
        val fixed =
            when (route) {
                is ShellRoute.Register,
                is ShellRoute.Reconcile,
                is ShellRoute.ImportPreviewRoute,
                is ShellRoute.ImportReviewRoute,
                is ShellRoute.TransferMoney,
                is ShellRoute.BankLink,
                is ShellRoute.EditAccount,
                is ShellRoute.CloseAccount,
                ShellRoute.Accounts,
                ShellRoute.AddAccount,
                -> HomeSections.ACCOUNTS
                is ShellRoute.CategoryBudget,
                is ShellRoute.MoveCategory,
                is ShellRoute.ToBudget,
                ShellRoute.HoldMonth,
                ShellRoute.Breakdown,
                ShellRoute.ManageCategories,
                ShellRoute.Assign,
                ShellRoute.Templates,
                -> HomeSections.BUDGET
                is ShellRoute.ScheduleEditor -> HomeSections.DUE
                is ShellRoute.ReviewCategory -> HomeSections.REVIEW
                ShellRoute.Budgets,
                ShellRoute.Create,
                ShellRoute.SampleBudgets,
                ShellRoute.Server,
                ShellRoute.BudgetPassword,
                ShellRoute.ConfirmSwitch,
                ShellRoute.ConfirmReplaceBudget,
                ShellRoute.ConfirmInstanceSwitch,
                ShellRoute.Conflicts,
                ShellRoute.ConfirmReplace,
                is ShellRoute.EncryptionPassword,
                is ShellRoute.ConfirmRemoveInstance,
                -> return SectionTitleCopy.APP
                // Transactions, rules and payees open from several areas: name the one showing.
                else -> HomeSections.headers.getOrNull(state.homeSection) ?: return SectionTitleCopy.APP
            }
        return fixed.uppercase()
    }

    private val OWN_TITLE: Set<ShellRoute> =
        setOf(
            ShellRoute.Home,
            ShellRoute.Settings,
            ShellRoute.Reports,
            ShellRoute.Welcome,
            ShellRoute.OnboardingAddress,
            ShellRoute.OnboardingSignIn,
            ShellRoute.OnboardingFiles,
        )
}

/** A drill-in page under its [title] line, when it has one. */
@Composable
fun SectionTitled(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (title == null) {
        Box(modifier) { content() }
        return
    }
    Column(modifier.background(Metro.colors.background)) {
        MetroText(
            title,
            Metro.typography.pageTitle,
            Modifier
                .padding(start = MetroDimens.Gutter, top = MetroDimens.Row, end = MetroDimens.Gutter)
                .testTag("section-title"),
            maxLines = 1,
            softWrap = false,
        )
        Box(Modifier.weight(1f).fillMaxSize()) { content() }
    }
}
