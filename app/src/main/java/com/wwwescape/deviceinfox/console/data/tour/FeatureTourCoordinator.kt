package com.wwwescape.deviceinfox.console.data.tour

import android.content.Context
import androidx.compose.ui.geometry.Rect
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** One coach-mark target currently mounted and present in composition — reported in by
 * `FeatureTourTarget`, read by `FeatureTourOverlay` for whichever tour is currently active. */
data class PendingTourTarget(
    val tourKey: String,
    val description: String,
    val boundsInRoot: Rect,
)

/**
 * Arbitrates which guided tour (if any) is eligible to show right now, across however many
 * `FeatureTourTarget`s happen to be mounted at once — only ever one overlay on screen at a time.
 *
 * **Adding a new tour anywhere in the console — Messages, Calendar, Period Tracker, Settings —
 * never touches this file.** Wrap the target composable in `FeatureTourTarget` with a new
 * `tourKey` and one line of copy; that's the entire integration surface. This class only ever
 * needs to change if the *arbitration rule itself* changes (e.g. priority beyond "first
 * registered, in composition order").
 *
 * `@Singleton`, not a per-screen `ViewModel`: `FeatureTourTarget` call sites are plain,
 * non-Hilt-scoped `@Composable`s scattered across unrelated screens and nav destinations, and the
 * overlay is mounted once, above all of them, at `ConsoleNavHost`'s level. A `hiltViewModel()`
 * resolved from inside a `NavHost`'s `composable {}` block would be scoped to that one
 * back-stack entry, not shared with either the overlay or a sibling screen — only a true
 * singleton, reached via [FeatureTourCoordinatorEntryPoint] (same escape-hatch shape as
 * `BatteryRepositoryEntryPoint`), gives every target and the overlay the same instance.
 */
@Singleton
class FeatureTourCoordinator @Inject constructor(
    private val repository: FeatureTourRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Registration order preserved (LinkedHashMap) so that if more than one candidate target is
    // ever mounted and unseen at once, which one shows is deterministic — first-registered wins,
    // not an unspecified Map iteration order.
    private val _candidates = MutableStateFlow(LinkedHashMap<String, PendingTourTarget>())

    /** The one tour eligible to show right now, or null. Recomputes whenever a target
     * registers/unregisters/moves, or the seen-set changes (e.g. once the initial server fetch
     * resolves, or immediately after [dismiss]).
     *
     * Gated on [FeatureTourRepository.isLoaded] — a `FeatureTourTarget` can mount and register
     * itself before that initial fetch resolves, and [FeatureTourRepository.seenTourKeys] reads
     * as empty in the meantime (indistinguishable from "genuinely never seen" if trusted as-is).
     * Without this gate, a tour already dismissed (in-app or via `app.cli disable-tours`) would
     * flash visible for that brief window, then vanish the instant the real seen-set arrives —
     * exactly the bug this gate exists to prevent. */
    val activeTour: StateFlow<PendingTourTarget?> =
        combine(_candidates, repository.seenTourKeys, repository.isLoaded) { candidates, seen, loaded ->
            if (!loaded) return@combine null
            candidates.values.firstOrNull { it.tourKey !in seen }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    fun registerTarget(target: PendingTourTarget) {
        _candidates.update { current -> LinkedHashMap(current).apply { put(target.tourKey, target) } }
    }

    fun unregisterTarget(tourKey: String) {
        _candidates.update { current -> LinkedHashMap(current).apply { remove(tourKey) } }
    }

    fun dismiss(tourKey: String) {
        repository.markSeen(tourKey)
    }
}

/** Escape hatch for `FeatureTourTarget`/`FeatureTourOverlay` — plain `@Composable` call sites
 * scattered across unrelated screens, none of which are Hilt-supported injection points on their
 * own. See `BatteryRepositoryEntryPoint` for the same pattern. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface FeatureTourCoordinatorEntryPoint {
    fun featureTourCoordinator(): FeatureTourCoordinator
}

fun Context.featureTourCoordinatorEntryPoint(): FeatureTourCoordinator =
    EntryPointAccessors.fromApplication(applicationContext, FeatureTourCoordinatorEntryPoint::class.java)
        .featureTourCoordinator()
