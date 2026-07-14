package com.wwwescape.deviceinfox.console.ui.tour

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.console.data.tour.featureTourCoordinatorEntryPoint

/**
 * Opt-in, one-line-per-screen companion to [FeatureTourTarget] — call once from any scrollable
 * screen that hosts one or more tours, passing that screen's own [ScrollState]. Hands scrolling
 * over to the guided tour entirely while one is in progress:
 * - Returns `false` while any tour is active, meant to be passed straight to that screen's own
 *   `Modifier.verticalScroll(scrollState, enabled = ...)` — the user's own manual scroll gesture
 *   is disabled for the duration, so the only thing moving the screen is the tour itself (each
 *   [FeatureTourTarget] already scrolls *itself* into view when it becomes active via
 *   `BringIntoViewRequester`, which isn't gated by `verticalScroll`'s `enabled` flag — that flag
 *   only affects the drag/fling gesture recognizer, not programmatic scrolling).
 * - The moment every tour on this screen has been dismissed (transitioning from "a tour was
 *   active" to "no tour is active anymore"), scrolls back to the top and returns `true` again —
 *   putting the screen back where the user found it and handing scrolling back to them, rather
 *   than leaving them both stranded wherever the last tour happened to scroll to *and* locked out
 *   of scrolling back themselves.
 *
 * Deliberately screen-owned rather than folded into [FeatureTourCoordinator]: only the screen
 * that created a given [ScrollState] can scroll it, and "go back to the top" / "who owns
 * scrolling right now" are screen-level notions with no generic Compose API standing in for them
 * the way [FeatureTourTarget]'s `BringIntoViewRequester` usage generically handles "scroll me
 * into view" for any ancestor container. Still a single call, not per-tour plumbing: works
 * unchanged no matter how many [FeatureTourTarget]s the screen adds or removes over time.
 */
@Composable
fun rememberFeatureTourScrollControl(scrollState: ScrollState): Boolean {
    val context = LocalContext.current
    val coordinator = remember(context) { context.featureTourCoordinatorEntryPoint() }
    val activeTour by coordinator.activeTour.collectAsStateWithLifecycle()
    var hasSeenActiveTour by remember { mutableStateOf(false) }

    LaunchedEffect(activeTour) {
        if (activeTour != null) {
            hasSeenActiveTour = true
        } else if (hasSeenActiveTour) {
            hasSeenActiveTour = false
            scrollState.animateScrollTo(0)
        }
    }

    return activeTour == null
}
