package com.nackie.ownexpenses.sync

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.local.dao.PendingTransaction
import com.nackie.ownexpenses.data.local.dao.SyncCategory
import com.nackie.ownexpenses.data.local.entity.SyncDeletionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Where the OwnBudget server is. Comes from the build (env `OWNEXPENSES_SYNC_URL`), not from the
 * user: a build without it has no sync at all.
 */
data class SyncConfig(
    val serverUrl: String,
    val appVersion: String,
) {
    val isAvailable: Boolean get() = serverUrl.isNotBlank()
}

sealed interface SyncOutcome {
    /** No server in this build, or no device token yet. */
    data object NotConfigured : SyncOutcome

    data class Success(
        /** Records and deletions the server received in this run. */
        val sent: Int,
    ) : SyncOutcome

    data class Failed(
        val error: SyncError,
    ) : SyncOutcome
}

/** Everything the sync section of the settings shows. */
data class SyncStatus(
    val isAvailable: Boolean,
    val serverUrl: String,
    val hasToken: Boolean,
    val lastSuccessAt: Instant?,
    val lastError: SyncError?,
    val lastErrorMessage: String?,
    /** Records and deletions not on the server yet. */
    val pendingCount: Int,
    val isRunning: Boolean,
)

/**
 * One-way sync to the OwnBudget server: the phone sends, the server only receives.
 *
 * Each run sends the full category list plus the records changed since the last successful run and
 * the deletions since then, in batches. A record stops being "pending" only if it did not change
 * while its batch was on the way. Every batch carries a new id, so the server applies it once even
 * if the same request arrives twice, and records carry their permanent [newSyncId], so a record
 * sent again never becomes a duplicate.
 */
class SyncService internal constructor(
    private val database: AppDatabase,
    private val stateStore: SyncStateStore,
    private val api: SyncApi,
    private val config: SyncConfig,
    private val clock: Clock,
) {
    private val syncDao = database.syncDao()
    private val mutex = Mutex()
    private val running = MutableStateFlow(false)

    val isRunning: StateFlow<Boolean> = running.asStateFlow()

    val status: Flow<SyncStatus> =
        combine(stateStore.state, syncDao.observePendingCount(), running) { state, pending, isRunning ->
            SyncStatus(
                isAvailable = config.isAvailable,
                serverUrl = config.serverUrl,
                hasToken = state.token != null,
                lastSuccessAt = state.lastSuccessAt,
                lastError = state.lastError,
                lastErrorMessage = state.lastErrorMessage,
                pendingCount = pending,
                isRunning = isRunning,
            )
        }

    /** Emits after every change a sync would pick up; the platform schedules a sync on it. */
    val changes: Flow<Unit> = syncDao.observeChanges().map { }

    suspend fun isReady(): Boolean = config.isAvailable && stateStore.token() != null

    suspend fun setToken(token: String?) {
        mutex.withLock {
            stateStore.setToken(token)
            syncDao.markAllPendingKeepingTimes()
        }
    }

    /** Checks the address and token without sending anything. */
    suspend fun checkConnection(): SyncOutcome {
        val token = stateStore.token()
        if (!config.isAvailable || token == null) return SyncOutcome.NotConfigured
        return when (val result = api.ping(config.serverUrl, token)) {
            is ApiResult.Response -> if (result.isSuccess) SyncOutcome.Success(0) else SyncOutcome.Failed(result.error())
            is ApiResult.NoConnection -> SyncOutcome.Failed(SyncError.NO_CONNECTION)
        }
    }

    suspend fun sync(): SyncOutcome =
        mutex.withLock {
            val token = stateStore.token()
            if (!config.isAvailable || token == null) return@withLock SyncOutcome.NotConfigured
            running.value = true
            try {
                syncBatches(token)
            } finally {
                running.value = false
            }
        }

    private suspend fun syncBatches(token: String): SyncOutcome {
        var sent = 0
        repeat(MAX_BATCHES_PER_RUN) {
            val categories = syncDao.getCategories()
            val operations = syncDao.getPendingTransactions(MAX_OPERATIONS)
            val deletions = syncDao.getDeletions(MAX_DELETIONS)
            val fingerprint = categories.fingerprint()
            if (operations.isEmpty() && deletions.isEmpty() && fingerprint == stateStore.sentCategories()) {
                stateStore.recordSuccess(clock.now())
                return SyncOutcome.Success(sent)
            }

            val request =
                SyncRequest(
                    batchId = newSyncId(),
                    appVersion = config.appVersion,
                    categories = categories.map { it.toDto() },
                    operations = operations.map { it.toDto() },
                    deletedOperations = deletions.map { it.toDto() },
                )
            val error = sendWithRetry(token, request)
            if (error != null) {
                stateStore.recordError(error.first, error.second)
                return SyncOutcome.Failed(error.first)
            }
            acknowledge(operations, deletions)
            stateStore.setSentCategories(fingerprint)
            sent += operations.size + deletions.size
        }
        // More than a run's worth of changes: what is left goes with the next run.
        stateStore.recordSuccess(clock.now())
        return SyncOutcome.Success(sent)
    }

    /**
     * Returns null on success, else the error and the server's explanation. A request that got no
     * answer is repeated once with the same batch id: if the first one did arrive, the server
     * recognises the batch and does not apply it twice.
     */
    private suspend fun sendWithRetry(
        token: String,
        request: SyncRequest,
    ): Pair<SyncError, String?>? {
        var result = api.send(config.serverUrl, token, request)
        if (result is ApiResult.NoConnection) result = api.send(config.serverUrl, token, request)
        return when (result) {
            is ApiResult.NoConnection -> SyncError.NO_CONNECTION to null
            is ApiResult.Response -> if (result.isSuccess) null else result.error() to result.body
        }
    }

    private suspend fun acknowledge(
        operations: List<PendingTransaction>,
        deletions: List<SyncDeletionEntity>,
    ) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                operations.forEach { syncDao.markSent(it.syncId, it.updatedAt) }
                deletions.forEach { syncDao.deleteSentDeletion(it.syncId, it.deletedAt) }
            }
        }
    }

    private companion object {
        /** Server limits per request (OwnBudget README). */
        const val MAX_OPERATIONS = 500
        const val MAX_DELETIONS = 1000

        /** Caps one run at 25 000 records; the rest goes with the next run. */
        const val MAX_BATCHES_PER_RUN = 50
    }
}

private fun ApiResult.Response.error(): SyncError =
    when (status) {
        HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> SyncError.UNAUTHORIZED
        HTTP_TOO_MANY_REQUESTS -> SyncError.SERVER
        in HTTP_CLIENT_ERRORS -> SyncError.REJECTED
        else -> SyncError.SERVER
    }

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429
private val HTTP_CLIENT_ERRORS = 400..499

/** Changes whenever anything the server stores about categories changes. */
private fun List<SyncCategory>.fingerprint(): String {
    val text =
        joinToString("\n") {
            "${it.syncId}|${it.parentSyncId}|${it.name}|${it.type}|${it.isArchived}|${it.sortOrder}"
        }
    return "$size:${text.hashCode()}"
}
