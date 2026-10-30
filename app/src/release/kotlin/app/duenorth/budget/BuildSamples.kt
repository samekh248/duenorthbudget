package app.duenorth.budget

import app.duenorth.budget.core.SampleBudgets

/** Release builds carry no sample budgets: :core:samples is a debug-only dependency. */
internal val buildSamples: SampleBudgets? = null
