package com.wwwescape.deviceinfox.data.notifications

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FakeNotificationCard(val title: String, val body: String)

/**
 * Backs the Dashboard's fake notification card — a stealth-layer echo of whatever the real
 * console push a tapped notification belonged to, so anyone who opens the plain "Device Info X"
 * app after seeing (and tapping) a notification finds an in-app artifact that corroborates it
 * instead of nothing at all.
 *
 * Populated at notification-*tap*-time, not push-*receive*-time —
 * [MainActivity][com.wwwescape.deviceinfox.MainActivity]'s `handleNotificationTap` resolves the
 * card's title/body from the tapped notification's
 * [com.wwwescape.deviceinfox.console.push.PushCategory] (there's no deep linking of any kind here,
 * so that category is the only signal a tap carries) via the same
 * [com.wwwescape.deviceinfox.console.push.resolveTitleAndBody] wording table the real notification
 * itself used. This used to be captured earlier, inside
 * [ConsoleFcmService][com.wwwescape.deviceinfox.console.push.ConsoleFcmService] right when the
 * push arrived — but that service is a background-woken FCM receiver, not a foreground service,
 * so Android reclaiming that process again in the (very plausible, often several-minute) gap
 * before the user actually got around to tapping would silently wipe an in-memory card before it
 * was ever seen, indistinguishable from the feature just not working. Regenerating the card at tap
 * time instead of carrying it across that gap removes the gap entirely: by the point
 * [MainActivity][com.wwwescape.deviceinfox.MainActivity] reads the category, the process is
 * already alive and this activity is already resuming, so in-memory state is all that's needed —
 * nothing has to survive a process death that already happened.
 *
 * [current] going back to null is how the card "closes": the card's own DISMISS button,
 * [MainActivity][com.wwwescape.deviceinfox.MainActivity]'s `onStop()` (the app being
 * backgrounded/closed — the same boundary `ConsoleActivity` already enforces for its own
 * auto-exit), and the moment the private console is entered
 * ([DeviceInfoXApp][com.wwwescape.deviceinfox.ui.DeviceInfoXApp]'s
 * `PinGateDialog.onAuthenticated`) all call [dismiss].
 */
@Singleton
class FakeNotificationCardState @Inject constructor() {
    private val _current = MutableStateFlow<FakeNotificationCard?>(null)
    val current: StateFlow<FakeNotificationCard?> = _current.asStateFlow()

    fun show(title: String, body: String) {
        _current.value = FakeNotificationCard(title, body)
    }

    fun dismiss() {
        _current.value = null
    }
}

/** Escape hatch for plain `@Composable` call sites (the Dashboard's fake card) and non-Hilt
 * callback contexts (`DeviceInfoXApp`'s `PinGateDialog.onAuthenticated`), none of which are
 * Hilt-supported injection points — see `BatteryRepositoryEntryPoint` for the same pattern. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface FakeNotificationCardStateEntryPoint {
    fun fakeNotificationCardState(): FakeNotificationCardState
}

fun Context.fakeNotificationCardStateEntryPoint(): FakeNotificationCardState =
    EntryPointAccessors.fromApplication(applicationContext, FakeNotificationCardStateEntryPoint::class.java)
        .fakeNotificationCardState()
