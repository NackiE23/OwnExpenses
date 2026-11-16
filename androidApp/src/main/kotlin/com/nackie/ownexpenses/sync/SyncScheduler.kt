package com.nackie.ownexpenses.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit

/**
 * Sends changes to the OwnBudget server in the background: shortly after the user stops editing,
 * and periodically as a safety net. WorkManager waits for a network and retries with backoff, so
 * records entered offline go out once the phone is online again.
 */
class SyncScheduler(
    private val context: Context,
    private val syncService: SyncService,
) {
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        // Every change restarts the countdown, so a burst of edits becomes one sync. The first value
        // (app start) also schedules one, which sends whatever was left over from last time.
        syncService.changes
            .debounce(DEBOUNCE_MILLIS)
            .onEach { if (syncService.isReady()) enqueueSoon() }
            .launchIn(scope)

        val periodic =
            PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_HOURS, TimeUnit.HOURS)
                .setConstraints(networkConstraint)
                .build()
        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, periodic)
    }

    private fun enqueueSoon() {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        // Replacing a waiting request keeps a single one queued however long the phone is offline.
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val ONE_TIME_WORK = "sync"
        const val PERIODIC_WORK = "sync-periodic"
        const val DEBOUNCE_MILLIS = 10_000L
        const val PERIODIC_HOURS = 6L
        const val BACKOFF_SECONDS = 60L

        val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
}
