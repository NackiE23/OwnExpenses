package com.nackie.ownexpenses.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

internal fun createSettingsDataStore(context: Context): DataStore<Preferences> =
    createSettingsDataStore {
        context.applicationContext.filesDir
            .resolve("datastore/$SETTINGS_FILE_NAME")
            .absolutePath
    }
