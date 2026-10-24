package com.nackie.ownexpenses.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.nackie.ownexpenses.data.documentDirectoryPath

internal fun createSettingsDataStore(): DataStore<Preferences> =
    createSettingsDataStore { "${documentDirectoryPath()}/$SETTINGS_FILE_NAME" }
