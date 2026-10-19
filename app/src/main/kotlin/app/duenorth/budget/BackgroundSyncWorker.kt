package app.duenorth.budget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Syncs the open budget about once an hour while the app is away, with a network, so desktop
 * changes are already on the phone when it opens. The screen syncs on its own while shown.
 */
class BackgroundSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? DueNorthApplication ?: return Result.success()
        if (app.onScreen) return Result.success()
        withContext(Dispatchers.IO) { runCatching { app.sync.syncUnattended() } }
        // A failed sync is recorded on the budget; the next run tries again, so the job never retries early.
        return Result.success()
    }

    companion object {
        private const val NAME = "background-sync"
        private const val EVERY_MINUTES = 60L

        /** Keeps one hourly job; scheduling again on each launch leaves the running schedule as it is. */
        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<BackgroundSyncWorker>(EVERY_MINUTES, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
