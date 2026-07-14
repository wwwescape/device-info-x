package com.wwwescape.deviceinfox.ui.screens.sensors

import android.hardware.Sensor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.data.sensors.SensorInfo
import com.wwwescape.deviceinfox.data.sensors.SensorReading
import com.wwwescape.deviceinfox.data.sensors.SensorsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.math.sqrt
import javax.inject.Inject

private const val MAX_ACCEL_HISTORY = 30

@HiltViewModel
class SensorsViewModel @Inject constructor(
    sensorsRepository: SensorsRepository,
) : ViewModel() {
    val sensors: List<SensorInfo> = sensorsRepository.listSensors()
    val accelerometer: Sensor? = sensorsRepository.defaultAccelerometer()
    val accelerometerInfo: SensorInfo? = sensors.firstOrNull { it.sensor == accelerometer }

    private val _accelHistory = MutableStateFlow<List<Float>>(emptyList())
    val accelHistory: StateFlow<List<Float>> = _accelHistory.asStateFlow()

    // The accelerometer listener is only registered while the screen observes this (it stops 5s
    // after the app is backgrounded or another destination covers it) — a sensor left running at
    // UI rate is one of the most expensive things an app can do to the battery. The sparkline
    // history is kept across pauses since it's fed as a side effect of the same subscription.
    val accelReading: StateFlow<SensorReading?> = (accelerometer?.let { sensorsRepository.readings(it) } ?: emptyFlow())
        .onEach { reading ->
            val magnitude = sqrt(reading.values.sumOf { (it * it).toDouble() }).toFloat()
            _accelHistory.update { history -> (history + magnitude).takeLast(MAX_ACCEL_HISTORY) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
