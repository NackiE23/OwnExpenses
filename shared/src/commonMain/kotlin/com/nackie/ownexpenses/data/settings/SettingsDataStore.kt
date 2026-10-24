package com.nackie.ownexpenses.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

internal const val SETTINGS_FILE_NAME = "settings.preferences_pb"

/** Must be created once per process: DataStore forbids two instances on one file. */
internal fun createSettingsDataStore(producePath: () -> String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { producePath().toPath() })
