package app.duenorth.budget

import android.app.Activity
import android.os.Bundle
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.createFromMock

/**
 * Writes one sample budget and opens it, so a cold start lands on a real month instead of onboarding.
 * Only the benchmark build types have it; the :benchmark module starts it through the shell before measuring.
 * It does nothing when a budget is already there, so every iteration measures the same file.
 */
class BenchmarkSeedActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val library = (application as DueNorthApplication).library
        if (library.list().isEmpty()) {
            library.createFromMock(intent.getStringExtra(EXTRA_DATASET) ?: MockBudgets.WIDE)
        }
        finish()
    }

    companion object {
        const val EXTRA_DATASET = "dataset"
    }
}
