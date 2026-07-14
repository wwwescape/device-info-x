package com.wwwescape.deviceinfox.console.data.messaging

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Bridges a tap in the Starred/Pinned messages screens back to `HomeScreen`'s scroll position.
 * `HomeScreen` has no `NavController` reference to receive a nav result from those screens
 * directly (it only ever takes plain lambdas, e.g. `onSettingsClick`) — this plays the same
 * `@Singleton` escape-hatch role `FakeNotificationCardState` does elsewhere in this app, just
 * between two Hilt ViewModels instead of a non-Hilt callback context.
 *
 * [DROP_OLDEST] with capacity 1: only the most recent jump request matters, and requesting one
 * must never suspend the tapping screen's own click handler. */
@Singleton
class MessageJumpRequester @Inject constructor() {
    private val _requests = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val requests: SharedFlow<String> = _requests.asSharedFlow()

    fun requestJumpTo(messageId: String) {
        _requests.tryEmit(messageId)
    }
}
