package com.nackie.ownexpenses.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Runs one sync in the background. Failures that may pass on their own are retried with backoff. */
class SyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters),
    KoinComponent {
    private val syncService: SyncService by inject()

    override suspend fun doWork(): Result =
        when (val outcome = syncService.sync()) {
            SyncOutcome.NotConfigured, is SyncOutcome.Success -> Result.success()
            // A wrong token or refused data will not fix itself; the settings screen shows the error.
            is SyncOutcome.Failed -> if (outcome.error.isRetryable) Result.retry() else Result.success()
        }
}
