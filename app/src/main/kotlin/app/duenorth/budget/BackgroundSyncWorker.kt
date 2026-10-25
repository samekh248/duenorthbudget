package app.duenorth.budget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Syncs the open budget about once an hour while the app is away, with a network, so desktop
 * changes are already on the phone when it opens. The screen syncs on its own while shown.
 * A one-off run as the app closes sends changes the phone could not send then, once there is a network.
 */
class BackgroundSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? DueNorthApplication ?: return Result.success()
        if (app.onScreen) return Result.success()
        withContext(Dispatchers.IO) {
            runCatching {
                if (inputData.getBoolean(UNSENT_ONLY, false)) {
                    val id = app.library.settings().openBudgetId
                    if (id != null) app.sync.sendUnsent(id)
                } else {
                    app.sync.syncUnattended()
                }
            }
        }
        // A failed sync is recorded on the budget; the next run tries again, so the job never retries early.
        return Result.success()
    }

    companion object {
        private const val NAME = "background-sync"
        private const val EVERY_MINUTES = 60L
        private const val UNSENT = "send-unsent"
        private const val UNSENT_ONLY = "unsentOnly"

        /** Keeps one hourly job; scheduling again on each launch leaves the running schedule as it is. */
        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<BackgroundSyncWorker>(EVERY_MINUTES, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /**
         * As the app closes: one run that sends unsent changes as soon as there is a network, in
         * case the send the app starts itself is offline or cut short. Sends nothing when none are left.
         */
        fun sendUnsent(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
                    .setInputData(workDataOf(UNSENT_ONLY to true))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNSENT, ExistingWorkPolicy.KEEP, request)
        }
    }
}
