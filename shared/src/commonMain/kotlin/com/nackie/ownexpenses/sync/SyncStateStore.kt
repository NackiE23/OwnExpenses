package com.nackie.ownexpenses.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/** Why the last sync did not go through. [key] is how it is stored and must never change. */
enum class SyncError(
    val key: String,
) {
    /** No answer from the server: offline, wrong address, timeout. Retried later on its own. */
    NO_CONNECTION("no_connection"),

    /** The device token is wrong or was replaced on the server. Needs a new token. */
    UNAUTHORIZED("unauthorized"),

    /** The server refused the data (400, 409). Retrying the same data will not help. */
    REJECTED("rejected"),

    /** Server error or rate limit (5xx, 429). Retried later on its own. */
    SERVER("server"),
    ;

    val isRetryable: Boolean get() = this == NO_CONNECTION || this == SERVER

    companion object {
        fun fromKey(key: String?): SyncError? = entries.firstOrNull { it.key == key }
    }
}

/** Sync settings and the outcome of the last attempt, kept in the settings DataStore. */
internal data class SyncState(
    val token: String? = null,
    val lastSuccessAt: Instant? = null,
    val lastError: SyncError? = null,
    /** Server's explanation for [SyncError.REJECTED], or what failed for [SyncError.NO_CONNECTION]; shortened. */
    val lastErrorMessage: String? = null,
)

internal class SyncStateStore(
    private val dataStore: DataStore<Preferences>,
) {
    val state: Flow<SyncState> =
        dataStore.data
            .map { preferences ->
                SyncState(
                    token = preferences[TOKEN]?.takeIf { it.isNotBlank() },
                    lastSuccessAt = preferences[LAST_SUCCESS_AT]?.let { Instant.fromEpochMilliseconds(it) },
                    lastError = SyncError.fromKey(preferences[LAST_ERROR]),
                    lastErrorMessage = preferences[LAST_ERROR_MESSAGE],
                )
            }.distinctUntilChanged()

    suspend fun token(): String? = state.first().token

    /** A new token belongs to a new device on the server, which has none of the data yet. */
    suspend fun setToken(token: String?) {
        dataStore.edit { preferences ->
            val value = token?.trim().orEmpty()
            if (value.isEmpty()) preferences.remove(TOKEN) else preferences[TOKEN] = value
            preferences.remove(LAST_ERROR)
            preferences.remove(LAST_ERROR_MESSAGE)
            preferences.remove(CATEGORIES_SENT)
        }
    }

    /** Fingerprint of the category list the server has, so unchanged categories alone do not trigger a sync. */
    suspend fun sentCategories(): String? = dataStore.data.first()[CATEGORIES_SENT]

    suspend fun setSentCategories(fingerprint: String) {
        dataStore.edit { it[CATEGORIES_SENT] = fingerprint }
    }

    suspend fun recordSuccess(at: Instant) {
        dataStore.edit { preferences ->
            preferences[LAST_SUCCESS_AT] = at.toEpochMilliseconds()
            preferences.remove(LAST_ERROR)
            preferences.remove(LAST_ERROR_MESSAGE)
        }
    }

    suspend fun recordError(
        error: SyncError,
        message: String?,
    ) {
        dataStore.edit { preferences ->
            preferences[LAST_ERROR] = error.key
            if (message.isNullOrBlank()) {
                preferences.remove(LAST_ERROR_MESSAGE)
            } else {
                preferences[LAST_ERROR_MESSAGE] = message.take(MAX_MESSAGE_LENGTH)
            }
        }
    }

    private companion object {
        const val MAX_MESSAGE_LENGTH = 300

        val TOKEN = stringPreferencesKey("sync_token")
        val LAST_SUCCESS_AT = longPreferencesKey("sync_last_success_at")
        val LAST_ERROR = stringPreferencesKey("sync_last_error")
        val LAST_ERROR_MESSAGE = stringPreferencesKey("sync_last_error_message")
        val CATEGORIES_SENT = stringPreferencesKey("sync_categories_sent")
    }
}
