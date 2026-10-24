package app.duenorth.budget

import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.TemplateError
import app.duenorth.budget.core.TemplateResult
import java.time.YearMonth

/** Fixed inputs for the spec 010 US5 template screens. */
internal object TemplateSamples {
    val october: YearMonth = YearMonth.of(2026, 10)

    val invalid =
        TemplateResult.Invalid(
            listOf(
                TemplateError("Fixed", "#template fifty dollars"),
                TemplateError("Car", "#template 600 by 2027-03 repeat every 1 years"),
            ),
        )

    val shell =
        MonthShell(
            budgetId = "b",
            budgetName = "Home",
            mode = BudgetMode.ENVELOPE,
            currency = Currencies.byCode("USD")!!,
            month = october,
            headerLabel = "to budget",
            headerMinor = 55_200,
            groups =
                listOf(
                    GroupRow("g-liv", "Living", 70_000, listOf(CategoryRow("c-food", "Food", 220_000, -150_000, 70_000, false))),
                ),
            accounts = emptyList(),
            inbox = emptyList(),
        )

    const val NOTE = "groceries and takeout\n#template 2200 up to 3000"

    /** A note with several templates, one that does not read, and the person's own words. */
    const val TILES =
        "weekly shop on sunday\n#template-1 400 up to 600\n#template-2 10% of previous all income\n#goal 500\n" +
            "#template fifty dollars"

    val tileAmounts: List<Long?> = listOf(40_000, 16_000, null, null)
}
