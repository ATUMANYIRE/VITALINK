package org.vitalink.app

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as VitalinkApplication
        return try { if (app.repository.synchronize()) Result.retry() else Result.success() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
    }
    companion object {
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("vitalink-outbox", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        }
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("vitalink-status-poll", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(network).build())
        }
    }
}
