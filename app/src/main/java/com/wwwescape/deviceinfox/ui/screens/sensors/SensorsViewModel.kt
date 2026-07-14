package com.wwwescape.deviceinfox.ui.screens.sensors

import android.hardware.Sensor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.data.sensors.SensorInfo
import com.wwwescape.deviceinfox.data.sensors.SensorReading
import com.wwwescape.deviceinfox.data.sensors.SensorsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private val _accelReading = MutableStateFlow<SensorReading?>(null)
    val accelReading: StateFlow<SensorReading?> = _accelReading.asStateFlow()

    private val _accelHistory = MutableStateFlow<List<Float>>(emptyList())
    val accelHistory: StateFlow<List<Float>> = _accelHistory.asStateFlow()

    init {
        accelerometer?.let { sensor ->
            viewModelScope.launch {
                sensorsRepository.readings(sensor).collect { reading ->
                    _accelReading.value = reading
                    val magnitude = sqrt(reading.values.sumOf { (it * it).toDouble() }).toFloat()
                    _accelHistory.value = (_accelHistory.value + magnitude).takeLast(MAX_ACCEL_HISTORY)
                }
            }
        }
    }
}
