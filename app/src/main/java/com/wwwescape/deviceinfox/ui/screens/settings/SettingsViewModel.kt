package com.wwwescape.deviceinfox.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.data.settings.AppSettings
import com.wwwescape.deviceinfox.data.settings.ColorTheme
import com.wwwescape.deviceinfox.data.settings.RefreshInterval
import com.wwwescape.deviceinfox.data.settings.SettingsRepository
import com.wwwescape.deviceinfox.data.settings.TemperatureUnit
import com.wwwescape.deviceinfox.data.settings.ThemeContrast
import com.wwwescape.deviceinfox.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setColorTheme(theme: ColorTheme) {
        viewModelScope.launch { settingsRepository.setColorTheme(theme) }
    }

    fun setThemeContrast(contrast: ThemeContrast) {
        viewModelScope.launch { settingsRepository.setThemeContrast(contrast) }
    }

    fun setPureDark(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPureDark(enabled) }
    }

    fun setAbsoluteDark(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAbsoluteDark(enabled) }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch { settingsRepository.setTemperatureUnit(unit) }
    }

    fun setRefreshInterval(interval: RefreshInterval) {
        viewModelScope.launch { settingsRepository.setRefreshInterval(interval) }
    }
}
