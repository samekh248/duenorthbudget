package app.duenorth.budget

import app.duenorth.budget.core.SampleBudgets
import app.duenorth.budget.core.ScheduleDetails
import app.duenorth.budget.core.TemplateKind

sealed interface ShellRoute {
    data object Home : ShellRoute

    data object Create : ShellRoute

    data object ConfirmSwitch : ShellRoute

    /** A server budget was picked while the phone holds others: names what gets cleared. */
    data object ConfirmReplaceBudget : ShellRoute

    data object ConfirmInstanceSwitch : ShellRoute

    data object Server : ShellRoute

    data object BudgetPassword : ShellRoute

    data object Conflicts : ShellRoute

    data object ConfirmReplace : ShellRoute

    data class Register(
        val accountId: String,
    ) : ShellRoute

    data class EditTransaction(
        val accountId: String,
        val transactionId: String?,
    ) : ShellRoute

    /** The category picker for [transactionId], plus [others] when several inbox rows are filed together. */
    data class PickCategory(
        val transactionId: String,
        val others: List<String> = emptyList(),
    ) : ShellRoute

    data class ImportPreviewRoute(
        val accountId: String,
    ) : ShellRoute

    data class ImportReviewRoute(
        val accountId: String,
    ) : ShellRoute

    data class Reconcile(
        val accountId: String,
    ) : ShellRoute

    data class ReviewCategory(
        val categoryId: String,
    ) : ShellRoute

    data class CategoryBudget(
        val categoryId: String,
    ) : ShellRoute

    data class MoveCategory(
        val fromCategoryId: String,
    ) : ShellRoute

    data object HoldMonth : ShellRoute

    data class ToBudget(
        val categoryId: String,
    ) : ShellRoute

    data object ManageCategories : ShellRoute

    /** Get started → set up categories, while the budget has none: a starter set to trim and add. */
    data object StarterCategories : ShellRoute, StackRoute

    data object Payees : ShellRoute

    /** The bank sync tile's full page: how each linked account went, with retry. */
    data object BankSyncReport : ShellRoute

    data object Settings : ShellRoute

    /** A budget just opened that has never had experimental features chosen: try them or keep them off. */
    data class ExperimentalOffer(
        val budgetId: String,
        /** What the budget already has, when it was chosen before (asked again at onboarding); null if never. */
        val current: Boolean? = null,
    ) : ShellRoute

    /** Settings → advanced → encryption: one page per password action (spec 012). */
    data class EncryptionPassword(
        val mode: EncryptionMode,
    ) : ShellRoute

    /** Settings → server → remove asks first (spec 012). */
    data class ConfirmRemoveInstance(
        val instanceId: String,
    ) : ShellRoute

    data object SampleBudgets : ShellRoute

    // Spec 011: first-run onboarding.
    data object Welcome : ShellRoute

    data object OnboardingAddress : ShellRoute

    data object OnboardingSignIn : ShellRoute

    data object OnboardingFiles : ShellRoute

    // Spec 010 US2: account management.
    data object AddAccount : ShellRoute, StackRoute

    data class EditAccount(
        val accountId: String,
    ) : ShellRoute,
        StackRoute

    data class CloseAccount(
        val accountId: String,
    ) : ShellRoute,
        StackRoute

    // Spec 010 US3: schedules and rules.
    data class ScheduleEditor(
        val scheduleId: String?,
    ) : ShellRoute,
        StackRoute

    data object Rules : ShellRoute, StackRoute

    data class RuleEditor(
        val ruleId: String?,
    ) : ShellRoute,
        StackRoute

    /** Accounts → schedules: a pivot of what is coming up (a list or a calendar) and every schedule. */
    data object Schedules : ShellRoute, StackRoute

    /** Schedules → one schedule: its details, next dates and what it has posted. */
    data class ScheduleDetails(
        val scheduleId: String,
    ) : ShellRoute,
        StackRoute

    // Spec 010 US4. The home pivot's reports item is the reporting hub; each report opens from it.

    /** Reports → month review: the open month's income and spending by category. */
    data object MonthReview : ShellRoute, StackRoute

    /** Reports → net worth: today's on and off budget totals. */
    data object NetWorth : ShellRoute, StackRoute

    /** Reports → a report over a month range, opened at its pivot [item]. */
    data class ReportPivot(
        val item: Int,
    ) : ShellRoute,
        StackRoute

    // Spec 010 US5.
    data object Templates : ShellRoute, StackRoute

    /** Category page → add template: the grid of kinds; with [index], what a line that does not read becomes. */
    data class TemplateKinds(
        val categoryId: String,
        val index: Int? = null,
    ) : ShellRoute,
        StackRoute

    /** Category page → a template tile (or a kind just picked): its form; [index] is null for a new one. */
    data class TemplateEditor(
        val categoryId: String,
        val index: Int?,
        val kind: TemplateKind,
    ) : ShellRoute,
        StackRoute

    // Spec 010 US6.
    data class BankLink(
        val accountId: String,
    ) : ShellRoute,
        StackRoute
}
