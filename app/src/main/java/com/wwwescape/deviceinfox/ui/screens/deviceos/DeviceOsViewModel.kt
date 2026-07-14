package com.wwwescape.deviceinfox.ui.screens.deviceos

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.data.deviceos.DeviceOsInfo
import com.wwwescape.deviceinfox.data.deviceos.DeviceOsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DeviceOsViewModel @Inject constructor(
    deviceOsRepository: DeviceOsRepository,
) : ViewModel() {

    val deviceOsInfo: DeviceOsInfo = deviceOsRepository.collectStatic()

    val uptimeMillis: StateFlow<Long> = deviceOsRepository.uptimeMillis()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SystemClock.elapsedRealtime(),
        )
}
