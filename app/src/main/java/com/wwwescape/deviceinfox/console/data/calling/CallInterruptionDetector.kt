package com.wwwescape.deviceinfox.console.data.calling

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Detects a real GSM/carrier call arriving mid-VoIP-call, so it can be ended with the dedicated
 * `interrupted` reason (CALLING_PLAN.md §3/§11) rather than left to silently fight the real call
 * for audio output. Silently does nothing if `READ_PHONE_STATE` isn't granted — this is a
 * secondary nicety (without it, a real call still takes over audio at the OS level regardless;
 * this class only makes the VoIP side end cleanly and tell the partner why, instead of just going
 * dead), not worth a dedicated permission prompt of its own on top of `RECORD_AUDIO`/`CAMERA`. */
@Singleton
class CallInterruptionDetector @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private var legacyListener: PhoneStateListener? = null
    private var modernCallback: TelephonyCallback? = null

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    fun start(onRealCallRinging: () -> Unit) {
        if (legacyListener != null || modernCallback != null) return
        val telephony = telephonyManager ?: return
        if (!hasPermission()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    if (state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK) {
                        onRealCallRinging()
                    }
                }
            }
            modernCallback = callback
            runCatching { telephony.registerTelephonyCallback(context.mainExecutor, callback) }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    if (state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK) {
                        onRealCallRinging()
                    }
                }
            }
            legacyListener = listener
            runCatching { telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE) }
        }
    }

    @Suppress("DEPRECATION")
    fun stop() {
        val telephony = telephonyManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modernCallback?.let { runCatching { telephony.unregisterTelephonyCallback(it) } }
        }
        modernCallback = null
        legacyListener?.let { runCatching { telephony.listen(it, PhoneStateListener.LISTEN_NONE) } }
        legacyListener = null
    }
}
