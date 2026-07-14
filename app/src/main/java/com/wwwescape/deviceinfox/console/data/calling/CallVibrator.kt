package com.wwwescape.deviceinfox.console.data.calling

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val PULSE_PATTERN = longArrayOf(0, 500, 500)

/** Drives the "incoming call" vibration — repeating, callee-side only, only while ringing, per
 * CALLING_PLAN.md §3/§13: no ringtone ever, under any circumstance. Stopping is the caller's
 * responsibility at every call site (see [CallRoomViewModel]/[CallRoomScreen]) — this class only
 * ever does exactly what it's told, it has no opinion on *when* that should be, since "stop
 * immediately when either partner exits the app" needs to happen both locally (this device's own
 * `onStop`) and reactively (a received `call.ended`), neither of which this class can see itself. */
@Singleton
class CallVibrator @Inject constructor(@param:ApplicationContext context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun startRinging() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(PULSE_PATTERN, 1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(PULSE_PATTERN, 1)
        }
    }

    fun stop() {
        vibrator.cancel()
    }
}
