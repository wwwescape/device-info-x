package com.wwwescape.deviceinfox.ui.screens.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.data.battery.BatteryInfo
import com.wwwescape.deviceinfox.data.battery.BatteryRepository
import com.wwwescape.deviceinfox.data.battery.ThermalInfo
import com.wwwescape.deviceinfox.data.battery.ThermalStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class BatteryViewModel @Inject constructor(
    batteryRepository: BatteryRepository,
) : ViewModel() {

    // Event-driven (broadcast/listener), not a polling loop, so it stays live for the
    // whole ViewModel lifetime rather than pausing when the screen isn't observed.
    val batteryInfo: StateFlow<BatteryInfo> = batteryRepository.batteryUpdates()
        .stateIn(viewModelScope, SharingStarted.Eagerly, batteryRepository.currentBatteryInfo())

    val thermalInfo: StateFlow<ThermalInfo> = batteryRepository.thermalUpdates()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThermalInfo(ThermalStatus.UNAVAILABLE))
}
