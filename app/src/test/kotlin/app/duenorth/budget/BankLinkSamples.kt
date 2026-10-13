package app.duenorth.budget

import app.duenorth.budget.core.BankProvider
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.ExternalAccount

/** Fixed inputs for the spec 010 US6 bank link screen. */
internal object BankLinkSamples {
    val usd = Currencies.byCode("USD")!!

    val accounts =
        listOf(
            ExternalAccount("ACT-1", "Everyday", "First Bank", "firstbank.example", 123_456),
            ExternalAccount("ACT-2", "Bills", "First Bank", "firstbank.example", -2_000),
        )

    fun ui(
        configured: List<BankProvider> = emptyList(),
        provider: BankProvider? = null,
        accounts: List<ExternalAccount>? = null,
    ) = BankLinkUi(
        accountId = "checking",
        accountName = "Checking",
        loading = false,
        configured = configured,
        provider = provider,
        accounts = accounts,
    )

    fun linked() =
        ui(configured = listOf(BankProvider.SIMPLEFIN), provider = BankProvider.SIMPLEFIN, accounts = accounts)
            .copy(linked = BankProvider.SIMPLEFIN to "First Bank")
}
