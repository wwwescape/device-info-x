package com.wwwescape.deviceinfox.console.data.calling

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import com.wwwescape.deviceinfox.console.session.ConsoleSessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Turns the screen off near the ear during an active **voice** call, the same way the platform
 * dialer does, without that screen-off tearing down the whole console session the way any other
 * screen-off would (`ConsoleActivity.onStop()`'s default behavior). Two things happen together,
 * for two different reasons:
 *
 * 1. A `PROXIMITY_SCREEN_OFF_WAKE_LOCK` — tells the OS to let the proximity sensor manage the
 *    screen itself. This is what actually turns the screen off/on; nothing else in this class
 *    does that directly.
 * 2. A plain [Sensor.TYPE_PROXIMITY] listener, held for the exact same duration — this is *not*
 *    what controls the screen (the wake lock above already does that); it's how this class knows
 *    *precisely* when "near" is currently true, so [ConsoleSessionManager.beginProximityScreenOff]
 *    is only set during the real near-ear window rather than for the whole call. That precision
 *    matters: without it, a deliberate power-button press *during* a voice call would be wrongly
 *    protected too, for as long as the call lasted, defeating the entire "leaving ends it" rule
 *    (CALLING_PLAN.md §8.1) rather than narrowly carving out just the proximity case.
 *
 * Flagged in `CALLING_PLAN.md` §8.1 as the one piece of this whole feature worth a real on-device
 * check before trusting — `PROXIMITY_SCREEN_OFF_WAKE_LOCK`'s exact interaction with `onStop`
 * timing across Android versions/OEM skins isn't something knowable from reading code alone. */
@Singleton
class CallProximityController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionManager: ConsoleSessionManager,
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var wakeLock: PowerManager.WakeLock? = null
    private var sensorListener: SensorEventListener? = null

    fun start() {
        if (wakeLock != null) return
        val sensor = proximitySensor ?: return
        if (!powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return

        @Suppress("DEPRECATION")
        val lock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "DeviceInfoX:CallProximity")
        lock.setReferenceCounted(false)
        lock.acquire()
        wakeLock = lock

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val isNear = event.values.isNotEmpty() && event.values[0] < sensor.maximumRange
                if (isNear) sessionManager.beginProximityScreenOff() else sessionManager.endProximityScreenOff()
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensorListener = listener
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        sensorListener?.let { sensorManager.unregisterListener(it) }
        sensorListener = null
        sessionManager.endProximityScreenOff()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
