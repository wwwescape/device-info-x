package com.wwwescape.deviceinfox.console.ui.tour

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.console.data.tour.PendingTourTarget
import com.wwwescape.deviceinfox.console.data.tour.featureTourCoordinatorEntryPoint

/**
 * Wraps any composable to make it a guided-tour anchor. **This is the entire integration surface
 * for adding a new tour anywhere in the console** — Messages, Calendar, Period Tracker, Settings,
 * anywhere. Pick a unique [tourKey], write one line of [description], wrap the target:
 * ```
 * FeatureTourTarget(tourKey = "some_new_feature", description = stringResource(R.string.…)) {
 *     IconButton(onClick = { … }) { … }
 * }
 * ```
 * Nothing else needs registering anywhere else — `FeatureTourCoordinator` picks it up
 * automatically the moment this enters composition, and `FeatureTourOverlay` (mounted once, at
 * the console's root) draws it if it's the tour currently eligible to show. If the target sits
 * inside a scrollable container (e.g. Settings' scrollable `Column`) and isn't currently in the
 * viewport when it becomes the active tour, it's scrolled into view automatically via
 * [BringIntoViewRequester.bringIntoView] with no custom region override — deliberately the plain,
 * well-tested "bring this element's own bounds into view" call, not a hand-computed rect padded
 * beyond the element's own size (an earlier version tried that, to reserve extra room for the
 * tooltip in advance, and it broke scrolling outright for a multi-row target like a whole
 * Settings subsection — passing a region that extends past the element's own bounds isn't really
 * what this API is built for). [FeatureTourOverlay] already picks whichever side (above/below)
 * has more room and clamps the tooltip within the screen once the real post-scroll position is
 * known, so it doesn't actually need scroll-time clearance reserved in advance. Works uniformly
 * across whatever ancestor scroll container the target happens to sit in (`verticalScroll`,
 * `LazyColumn`, …), since this is Compose's own relocation API, not anything screen-specific.
 *
 * Just a plain [Box] with no `clickable`/consuming pointer input of its own — this file never
 * listens for taps at all, so [content] behaves exactly as if this wrapper weren't there. That
 * doesn't mean the target is tappable while its tour is active, though: `FeatureTourOverlay`
 * layers an invisible tap-blocking region exactly over the cutout while a tour is showing, so a
 * tap on the spotlighted target is swallowed rather than reaching [content]'s own click handling
 * — the whole screen is inert during a tour, dismissible only via the tooltip's own close button
 * (see `FeatureTourOverlay`'s class doc). Once the tour moves on or ends, that blocking region is
 * gone and the target is exactly as interactive as it always was.
 */
@Composable
fun FeatureTourTarget(
    tourKey: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val coordinator = remember(context) { context.featureTourCoordinatorEntryPoint() }
    val activeTour by coordinator.activeTour.collectAsStateWithLifecycle()
    val isActiveTour = activeTour?.tourKey == tourKey

    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                coordinator.registerTarget(PendingTourTarget(tourKey, description, coordinates.boundsInRoot()))
            }
            .bringIntoViewRequester(bringIntoViewRequester),
    ) {
        content()
    }

    LaunchedEffect(isActiveTour) {
        if (isActiveTour) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    DisposableEffect(tourKey) {
        onDispose { coordinator.unregisterTarget(tourKey) }
    }
}
