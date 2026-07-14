package com.wwwescape.deviceinfox.console.ui.nav

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.wwwescape.deviceinfox.console.data.network.ConsoleWebSocketClient
import com.wwwescape.deviceinfox.console.session.ConsoleSessionManager
import com.wwwescape.deviceinfox.ui.theme.DeviceInfoXTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Dedicated Activity for the private console, launched only from [DeviceInfoXApp]'s long-press
 * trigger after [ConsoleSessionManager] reports an authenticated session. Kept as a separate
 * Activity class (rather than a second NavHost inside MainActivity) specifically so FLAG_SECURE
 * and the auto-exit-on-background behavior apply for this Activity's whole lifetime instead of
 * needing to be toggled on a shared one — but deliberately shares MainActivity's *task* (see the
 * manifest entry and [DeviceInfoXApp]'s launch `Intent`), so Recents only ever shows one
 * "Device Info X" card rather than a second, permanently-blank one whenever this is open.
 *
 * Also the sole owner of the [ConsoleWebSocketClient] connection's lifecycle (Phase 11.5): per
 * that class's own doc comment, it only ever connects while this Activity is foregrounded and
 * the session is authenticated — no foreground service keeps it alive in the background,
 * matching the "nothing runs once it's not open" philosophy from Phase 3.
 */
@AndroidEntryPoint
class ConsoleActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: ConsoleSessionManager

    @Inject
    lateinit var webSocketClient: ConsoleWebSocketClient

    /** Exits on screen-off even while this Activity is already stopped behind a system
     * picker/camera — [onStop] has already run (and deliberately skipped exiting) by then, so
     * without this nothing would ever re-check. Registered for this Activity's whole lifetime,
     * not just while started, for exactly that reason. The proximity carve-out matches [onStop]'s. */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (sessionManager.isInProximityScreenOff.value) return
            exitConsole()
        }
    }
    private var isScreenOffReceiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        // Reached without a live authenticated session — e.g. the OS recreating this Activity
        // from a saved task after process death. Never show private content unauthenticated;
        // bounce straight back rather than prompting for a PIN from inside this Activity.
        if (!sessionManager.isAuthenticated.value) {
            finish()
            return
        }

        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        isScreenOffReceiverRegistered = true

        webSocketClient.connect()

        enableEdgeToEdge()
        setContent {
            DeviceInfoXTheme {
                ConsoleNavHost(
                    onLock = {
                        sessionManager.clear()
                        finish()
                    },
                )
            }
        }
    }

    /** Coming back from a picker/camera round trip that ran past
     * [ConsoleSessionManager.isTransientResultOverdue]'s cap — treated as "the user left the app
     * from inside the picker", which Android gives no callback for. Runs before the picker's
     * result is delivered, so [onActivityResult] below drops it. */
    override fun onRestart() {
        super.onRestart()
        if (sessionManager.isTransientResultOverdue()) exitConsole()
    }

    /** The actual enforcement point for "exit private mode the moment it's backgrounded or the
     * screen locks." onStop rather than onPause — a merely transient overlap (e.g. a system
     * permission dialog) only pauses this Activity, not stops it, so it won't false-trigger
     * exit; a genuine loss of visibility (Home, app switch, screen off) does.
     *
     * Exception: a launched system picker (e.g. the profile photo picker) also stops this
     * Activity while it's in front — [ConsoleSessionManager.isExpectingTransientResult] is how
     * that expected round trip is told apart from actually leaving the app. Leaving the app *from
     * inside* that picker is then caught by [screenOffReceiver] and [onRestart] instead.
     *
     * Second exception, added for Voice/Video Calling: the Call Room's own proximity-sensor
     * screen-off during an active voice call also stops this Activity, and must not exit the
     * console either — see [ConsoleSessionManager.isInProximityScreenOff]/`CallProximityController`. */
    override fun onStop() {
        super.onStop()
        if (sessionManager.isExpectingTransientResult.value) return
        if (sessionManager.isInProximityScreenOff.value) return
        exitConsole()
    }

    /** Every Activity Result API callback (pickers, camera) is dispatched from here, so this is
     * the one place to drop a result that arrives after the session was cleared — e.g. a photo
     * picked after the screen went off mid-pick must never be sent/uploaded. */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!sessionManager.isAuthenticated.value) return
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        if (isScreenOffReceiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            isScreenOffReceiverRegistered = false
        }
        super.onDestroy()
    }

    /** Safe to call more than once (e.g. screen-off firing alongside [onStop]). */
    private fun exitConsole() {
        webSocketClient.disconnect()
        sessionManager.clear()
        finish()
    }
}
