package com.nackie.ownexpenses.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nackie.ownexpenses.sync.SyncOutcome
import com.nackie.ownexpenses.sync.SyncService
import com.nackie.ownexpenses.sync.SyncStatus
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SyncEvent {
    /** Result of a sync or connection check the user started. */
    data class Finished(
        val outcome: SyncOutcome,
        val wasCheck: Boolean,
    ) : SyncEvent
}

/** Sync section of the "More" tab: device token, status line, "sync now" and "check connection". */
class SyncViewModel(
    private val syncService: SyncService,
) : ViewModel() {
    val status: StateFlow<SyncStatus?> =
        syncService.status.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val eventChannel = Channel<SyncEvent>(Channel.BUFFERED)
    val events: Flow<SyncEvent> = eventChannel.receiveAsFlow()

    fun syncNow() {
        viewModelScope.launch {
            // Leaving the screen must not cut a batch off halfway; the server would just see it again.
            val outcome = withContext(NonCancellable) { syncService.sync() }
            eventChannel.send(SyncEvent.Finished(outcome, wasCheck = false))
        }
    }

    fun checkConnection() {
        viewModelScope.launch {
            eventChannel.send(SyncEvent.Finished(syncService.checkConnection(), wasCheck = true))
        }
    }

    /** Saves the token (blank removes it) and sends everything to the server right away. */
    fun saveToken(token: String) {
        viewModelScope.launch {
            withContext(NonCancellable) { syncService.setToken(token) }
            if (token.isNotBlank()) syncNow()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
