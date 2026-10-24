package com.nackie.ownexpenses.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nackie.ownexpenses.domain.model.ThemeMode
import com.nackie.ownexpenses.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** App-wide UI settings for the root composable. */
class AppViewModel(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> =
        settingsRepository.settings
            .map { it.themeMode }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
}
