package app.duenorth.budget

internal fun ShellViewModel.openAccountId(): String? = state.value.openAccountId()

/** The account the page showing is about: the register's, its form's or its reconcile's. */
internal fun ShellUiState.openAccountId(): String? =
    when (val route = route) {
        is ShellRoute.Register -> route.accountId
        is ShellRoute.EditTransaction -> route.accountId
        is ShellRoute.Reconcile -> route.accountId
        else ->
            register
                ?.account
                ?.id
    }
