package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.duenorth.budget.core.ReportsCopy
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

/** The Home pivot's headers, in order; [ShellUiState.homeSection] indexes them. */
object HomeSections {
    const val BUDGET = "budget"
    const val ACCOUNTS = "accounts"
    const val INBOX = "inbox"
    const val REPORTS = ReportsCopy.TITLE

    // The inbox leads, so the app opens on what needs filing.
    val headers = listOf(INBOX, BUDGET, ACCOUNTS, REPORTS)
}

object SectionTitleCopy {
    const val APP = "DUE NORTH BUDGET"
    const val SETTINGS = "SETTINGS"
    const val RULES = "RULES"
    const val SCHEDULES = "SCHEDULES"
}

/**
 * The small all-caps line over a drill-in page's title, naming the area the page sits
 * under (Windows Phone 8.1 title area). Pivots carry their own, and top-level views
 * (the lists the app bar opens) are an area themselves, so neither gets one.
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
                is ShellRoute.BankLink,
                is ShellRoute.EditAccount,
                is ShellRoute.CloseAccount,
                ShellRoute.AddAccount,
                ShellRoute.BankSyncReport,
                -> HomeSections.ACCOUNTS
                is ShellRoute.CategoryBudget,
                is ShellRoute.TemplateKinds,
                is ShellRoute.TemplateEditor,
                is ShellRoute.MoveCategory,
                is ShellRoute.ToBudget,
                ShellRoute.HoldMonth,
                ShellRoute.StarterCategories,
                -> HomeSections.BUDGET
                is ShellRoute.RuleEditor -> return SectionTitleCopy.RULES
                is ShellRoute.ScheduleEditor,
                is ShellRoute.ScheduleDetails,
                -> return SectionTitleCopy.SCHEDULES
                // Reports is a home pivot item, and the pages it opens sit under it.
                ShellRoute.NetWorth,
                ShellRoute.MonthReview,
                is ShellRoute.ReviewCategory,
                -> HomeSections.REPORTS
                is ShellRoute.ReportPivot -> return null
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
                is ShellRoute.ExperimentalOffer,
                -> return SectionTitleCopy.APP
                // Transactions open from several areas: name the one showing.
                else -> HomeSections.headers.getOrNull(state.homeSection) ?: return SectionTitleCopy.APP
            }
        return fixed.uppercase()
    }

    private val OWN_TITLE: Set<ShellRoute> =
        setOf(
            ShellRoute.Home,
            ShellRoute.Settings,
            ShellRoute.Welcome,
            ShellRoute.OnboardingAddress,
            ShellRoute.OnboardingSignIn,
            ShellRoute.OnboardingFiles,
            // Top-level views.
            ShellRoute.Rules,
            ShellRoute.Payees,
            ShellRoute.Templates,
            ShellRoute.ManageCategories,
            ShellRoute.Schedules,
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
