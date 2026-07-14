package com.wwwescape.deviceinfox.ui.screens.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.data.memory.MemoryLiveStats
import com.wwwescape.deviceinfox.data.memory.MemoryRepository
import com.wwwescape.deviceinfox.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryViewModel @Inject constructor(
    memoryRepository: MemoryRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val refreshIntervalMillis = settingsRepository.settingsFlow
        .map { it.refreshInterval.memoryMillis }
        .distinctUntilChanged()

    val liveStats: StateFlow<MemoryLiveStats> = refreshIntervalMillis
        .flatMapLatest { intervalMillis -> memoryRepository.liveStats(intervalMillis) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MemoryLiveStats(
                ram = memoryRepository.readRamInfo(),
                storageVolumes = emptyList(),
            ),
        )
}
