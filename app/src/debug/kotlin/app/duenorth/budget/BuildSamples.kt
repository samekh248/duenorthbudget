package app.duenorth.budget

import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.SampleBudgets

/** Debug builds offer the sample budgets of :core:samples. The release build's copy of this file has none. */
internal val buildSamples: SampleBudgets? = MockBudgets
