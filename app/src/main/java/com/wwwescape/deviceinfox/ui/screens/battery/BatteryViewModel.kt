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

    // Event-driven (broadcast/listener), but ACTION_BATTERY_CHANGED still fires often while
    // charging — unregister once the screen stops being observed (backgrounded, or covered by
    // another destination) so this ViewModel never wakes the app up while it isn't visible.
    val batteryInfo: StateFlow<BatteryInfo> = batteryRepository.batteryUpdates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), batteryRepository.currentBatteryInfo())

    val thermalInfo: StateFlow<ThermalInfo> = batteryRepository.thermalUpdates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThermalInfo(ThermalStatus.UNAVAILABLE))
}
