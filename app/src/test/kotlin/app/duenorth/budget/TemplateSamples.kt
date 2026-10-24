package app.duenorth.budget

import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.Template
import app.duenorth.budget.core.TemplateChip
import app.duenorth.budget.core.TemplateError
import app.duenorth.budget.core.TemplateLane
import app.duenorth.budget.core.TemplatePlan
import app.duenorth.budget.core.TemplateResult
import java.math.BigDecimal
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

    private fun chip(
        id: String,
        name: String,
        template: Template?,
        line: String,
        asks: Long?,
        given: Long?,
        index: Int = 0,
    ) = TemplateChip(id, name, index, template, line, asks, given)

    private val food = Template.Simple(BigDecimal("400"), BigDecimal("600"), 1)
    private val share = Template.Percentage(BigDecimal("10"), false, "all income", 1)

    /** A month's lanes: rent and power first, food and fun next, gas short of what it asks, a remainder and a goal. */
    val plan =
        TemplatePlan(
            start = 300_000,
            lanes =
                listOf(
                    TemplateLane(
                        TemplateLane.Kind.PRIORITY,
                        0,
                        listOf(
                            chip("c-rent", "Rent", Template.Schedule("Rent", false, 0), "#template schedule Rent", 150_000, 150_000),
                            chip("c-power", "Power", Template.Average(3, 0), "#template average 3 months", 9_000, 9_000),
                        ),
                        141_000,
                    ),
                    TemplateLane(
                        TemplateLane.Kind.PRIORITY,
                        1,
                        listOf(
                            chip("c-food", "Food", food, "#template-1 400 up to 600", 40_000, 40_000),
                            chip("c-fun", "Fun", share, "#template-1 10% of all income", 50_000, 50_000),
                        ),
                        51_000,
                    ),
                    TemplateLane(
                        TemplateLane.Kind.PRIORITY,
                        2,
                        listOf(chip("c-gas", "Gas", Template.Simple(BigDecimal("600"), null, 2), "#template-2 600", 60_000, 51_000)),
                        0,
                    ),
                    TemplateLane(
                        TemplateLane.Kind.REMAINDER,
                        null,
                        listOf(chip("c-rainy", "Rainy day", Template.Remainder(BigDecimal.ONE), "#template remainder", null, 0)),
                        0,
                    ),
                    TemplateLane(
                        TemplateLane.Kind.GOAL,
                        null,
                        listOf(chip("c-car", "Car", Template.Goal(BigDecimal("5000")), "#goal 5000", null, null)),
                        null,
                    ),
                ),
            unreadable = listOf(chip("c-gym", "Gym", null, "#template fifty dollars", null, null)),
        )

    val applyPreview =
        TemplateApplyPreview(
            overwrite = true,
            rows =
                listOf(
                    TemplateApplyRow("c-food", "Food", 220_000, 40_000),
                    TemplateApplyRow("c-gas", "Gas", 0, 51_000),
                ),
        )
}
