package com.wwwescape.deviceinfox.console.ui.nav

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    /** The actual enforcement point for "exit private mode the moment it's backgrounded or the
     * screen locks." onStop rather than onPause — a merely transient overlap (e.g. a system
     * permission dialog) only pauses this Activity, not stops it, so it won't false-trigger
     * exit; a genuine loss of visibility (Home, app switch, screen off) does.
     *
     * Exception: a launched system picker (e.g. the profile photo picker) also stops this
     * Activity while it's in front — [ConsoleSessionManager.isExpectingTransientResult] is how
     * that expected round trip is told apart from actually leaving the app.
     *
     * Second exception, added for Voice/Video Calling: the Call Room's own proximity-sensor
     * screen-off during an active voice call also stops this Activity, and must not exit the
     * console either — see [ConsoleSessionManager.isInProximityScreenOff]/`CallProximityController`. */
    override fun onStop() {
        super.onStop()
        if (sessionManager.isExpectingTransientResult.value) return
        if (sessionManager.isInProximityScreenOff.value) return
        webSocketClient.disconnect()
        sessionManager.clear()
        finish()
    }
}
