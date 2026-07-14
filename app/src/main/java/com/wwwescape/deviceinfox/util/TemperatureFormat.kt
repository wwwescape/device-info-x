package com.wwwescape.deviceinfox.util

import com.wwwescape.deviceinfox.data.settings.TemperatureUnit

fun formatTemperature(celsius: Float, unit: TemperatureUnit): String = when (unit) {
    TemperatureUnit.CELSIUS -> "%.1f°C".format(celsius)
    TemperatureUnit.FAHRENHEIT -> "%.1f°F".format(celsius * 9f / 5f + 32f)
}
