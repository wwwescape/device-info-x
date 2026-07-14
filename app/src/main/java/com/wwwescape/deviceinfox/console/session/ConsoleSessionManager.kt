package com.wwwescape.deviceinfox.console.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory-only authenticated state for the private console — deliberately never persisted,
 * so a process restart always requires the PIN again.
 *
 * Lifecycle-driven auto-clear (exit on background/screen-off) is wired in [ConsoleActivity]'s
 * onStop; this class only holds the state itself.
 */
@Singleton
class ConsoleSessionManager @Inject constructor() {

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    /** True while a system picker/chooser launched from inside the console (e.g. the Photo
     * Picker for a profile photo) is expected to hand control straight back — that trip through
     * another Activity triggers [ConsoleActivity]'s onStop the same as genuinely backgrounding,
     * so it must not be treated as "the user left the app" or the picker would be unusable. */
    private val _isExpectingTransientResult = MutableStateFlow(false)
    val isExpectingTransientResult: StateFlow<Boolean> = _isExpectingTransientResult.asStateFlow()

    /** True while the screen is off specifically because of the Call Room's own proximity-sensor
     * wake lock during an active voice call (`CallProximityController`) — screen-off is expected
     * there (phone held to the ear), and must not be treated as "the user left" the way any other
     * screen-off is. Same carve-out shape as [isExpectingTransientResult] above, see
     * `CallProximityController`'s own doc comment for why this needs a live sensor reading rather
     * than just being held for a call's whole duration. */
    private val _isInProximityScreenOff = MutableStateFlow(false)
    val isInProximityScreenOff: StateFlow<Boolean> = _isInProximityScreenOff.asStateFlow()

    fun markAuthenticated() {
        _isAuthenticated.value = true
    }

    fun clear() {
        _isAuthenticated.value = false
    }

    fun beginExpectingTransientResult() {
        _isExpectingTransientResult.value = true
    }

    fun endExpectingTransientResult() {
        _isExpectingTransientResult.value = false
    }

    fun beginProximityScreenOff() {
        _isInProximityScreenOff.value = true
    }

    fun endProximityScreenOff() {
        _isInProximityScreenOff.value = false
    }
}
