package com.wwwescape.deviceinfox.ui.screens.cpu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.data.cpu.CpuInfo
import com.wwwescape.deviceinfox.data.cpu.CpuLiveStats
import com.wwwescape.deviceinfox.data.cpu.CpuRepository
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
class CpuViewModel @Inject constructor(
    cpuRepository: CpuRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val cpuInfo: CpuInfo = cpuRepository.collectStatic()

    private val refreshIntervalMillis = settingsRepository.settingsFlow
        .map { it.refreshInterval.cpuMillis }
        .distinctUntilChanged()

    val liveStats: StateFlow<CpuLiveStats> = refreshIntervalMillis
        .flatMapLatest { intervalMillis -> cpuRepository.liveStats(cpuInfo.coreCount, intervalMillis) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CpuLiveStats(loadPercent = null, coreFrequenciesMhz = List(cpuInfo.coreCount) { null }),
        )
}
