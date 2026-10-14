package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BankLinkCopy
import app.duenorth.budget.core.BankProvider
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.ExternalAccount
import app.duenorth.budget.core.GoCardlessBank
import app.duenorth.budget.core.GoCardlessLink
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

/** The bank link screen's state for one phone account (spec 010 US6). */
data class BankLinkUi(
    val accountId: String,
    val accountName: String = "",
    val loading: Boolean = true,
    /** Providers the server reports as configured. */
    val configured: List<BankProvider> = emptyList(),
    val linked: Pair<BankProvider, String>? = null,
    val provider: BankProvider? = null,
    val goCardlessBanks: List<GoCardlessBank> = emptyList(),
    val goCardless: GoCardlessLink? = null,
    val accounts: List<ExternalAccount>? = null,
    val error: String? = null,
) {
    /** Configured providers, plus SimpleFIN while it still needs its setup token. */
    val offered: List<BankProvider>
        get() = BankProvider.entries.filter { it in configured || it == BankProvider.SIMPLEFIN }
}

/** Register `•••` → bank link: pick a provider, sign in or send a token, then pick the bank account. */
@Composable
fun BankLinkScreen(
    ui: BankLinkUi,
    currency: CurrencySpec,
    onProvider: (BankProvider) -> Unit,
    onToken: (String) -> Unit,
    onCountry: (String) -> Unit,
    onInstitution: (String) -> Unit,
    onOpenBank: (String) -> Unit,
    onSignedIn: () -> Unit,
    onLink: (ExternalAccount) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(MetroDimens.Gutter)
            .testTag("bank-link-screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText(BankLinkCopy.TITLE, Metro.typography.header)
        if (ui.accountName.isNotEmpty()) MetroText(ui.accountName, Metro.typography.caption, color = Metro.colors.secondary)
        ui.linked?.let { (provider, bank) ->
            MetroText("${BankLinkCopy.LINKED} $bank · ${provider.label}", Metro.typography.subheader, Modifier.testTag("bank-linked"))
            MetroButton(BankLinkCopy.REMOVE, Modifier.testTag("bank-link-remove"), onClick = onRemove)
        }
        if (ui.error != null) {
            MetroText(ui.error, Metro.typography.body, Modifier.testTag("bank-link-error"), color = Metro.accent.text)
        }
        if (ui.loading) {
            MetroText("…", Metro.typography.subheader, color = Metro.colors.secondary)
            return@Column
        }
        MetroText(BankLinkCopy.PROVIDERS, Metro.typography.caption, color = Metro.colors.secondary)
        for (provider in ui.offered) {
            Pick(provider.label, selected = provider == ui.provider, tag = "bank-provider-${provider.syncSource}") { onProvider(provider) }
        }
        when (ui.provider) {
            BankProvider.SIMPLEFIN -> if (BankProvider.SIMPLEFIN !in ui.configured) TokenEntry(onToken)
            BankProvider.GOCARDLESS -> GoCardlessSteps(ui, onCountry, onInstitution, onOpenBank, onSignedIn)
            else -> Unit
        }
        val accounts = ui.accounts
        if (accounts != null) {
            MetroText(BankLinkCopy.PICK_ACCOUNT, Metro.typography.caption, color = Metro.colors.secondary)
            if (accounts.isEmpty()) MetroText(BankLinkCopy.NO_ACCOUNTS, Metro.typography.body)
            for (account in accounts) {
                val balance = account.balanceMinor?.let { "  ${MoneyFormat.format(it, currency)}" }.orEmpty()
                Pick(
                    "${account.name}$balance",
                    selected = false,
                    tag = "bank-account-${account.accountId}",
                    caption = account.institution,
                ) {
                    onLink(account)
                }
            }
        }
    }
}

/** The SimpleFIN setup token: masked, sent once, then cleared and never shown again. */
@Composable
private fun TokenEntry(onToken: (String) -> Unit) {
    var token by remember { mutableStateOf("") }
    MetroField(token, { token = it }, BankLinkCopy.TOKEN, Modifier.testTag("bank-token"), conceal = true)
    MetroButton(BankLinkCopy.SEND_TOKEN, Modifier.testTag("bank-token-send")) {
        val sent = token
        token = ""
        if (sent.isNotBlank()) onToken(sent)
    }
}

@Composable
private fun GoCardlessSteps(
    ui: BankLinkUi,
    onCountry: (String) -> Unit,
    onInstitution: (String) -> Unit,
    onOpenBank: (String) -> Unit,
    onSignedIn: () -> Unit,
) {
    val link = ui.goCardless
    if (link != null) {
        MetroButtonGrid {
            MetroButton(BankLinkCopy.SIGN_IN, Modifier.testTag("bank-signin")) { onOpenBank(link.link) }
            MetroButton(BankLinkCopy.SIGNED_IN, Modifier.testTag("bank-signed-in"), onClick = onSignedIn)
        }
        return
    }
    var country by remember { mutableStateOf("") }
    MetroField(country, { country = it }, BankLinkCopy.COUNTRY, Modifier.testTag("bank-country"))
    MetroButton(BankLinkCopy.FIND_BANKS, Modifier.testTag("bank-country-find")) { onCountry(country) }
    for (bank in ui.goCardlessBanks) {
        Pick(bank.name, selected = false, tag = "bank-institution-${bank.id}") { onInstitution(bank.id) }
    }
}

@Composable
private fun Pick(
    label: String,
    selected: Boolean,
    tag: String,
    caption: String? = null,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .metroPress(onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag(tag),
    ) {
        MetroText(label, Metro.typography.subheader, color = if (selected) Metro.accent.text else Metro.colors.foreground)
        if (caption != null) MetroText(caption, Metro.typography.caption, color = Metro.colors.secondary)
    }
}
