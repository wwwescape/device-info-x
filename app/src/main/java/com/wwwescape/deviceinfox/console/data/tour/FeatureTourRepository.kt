package com.wwwescape.deviceinfox.console.data.tour

import com.wwwescape.deviceinfox.console.data.network.UsersApi
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Server-side "seen" state for guided feature tours — which [FeatureTourCoordinator] `tourKey`s
 * this account has already dismissed. The one piece of "seen this before" state in the console
 * that has to survive an uninstall/reinstall, unlike every other such flag
 * (`ConsoleSettingsRepository`'s local `DataStore`, wiped on uninstall) — a tour should only ever
 * show once per account, for the account's whole lifetime, not once per install.
 *
 * Same fetch-once shape as `ServerPairingRepository`: a singleton-scoped [CoroutineScope] (not
 * tied to any one ViewModel) fetches the seen set the moment this repository is first
 * constructed — which happens the first time anything inside the console needs it, i.e. as soon
 * as the console's own UI starts composing after PIN unlock — rather than only reacting to
 * something already available. */
@Singleton
class FeatureTourRepository @Inject constructor(
    private val usersApi: UsersApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _seenTourKeys = MutableStateFlow<Set<String>>(emptySet())

    /** Empty until the initial fetch resolves. Deliberately *not* read as "confirmed nothing's
     * been seen yet" by anything — see [isLoaded]: a tour that's actually already been dismissed
     * (in-app, or via `app.cli disable-tours`) would otherwise flash visible for the instant
     * between a `FeatureTourTarget` mounting and this fetch resolving, since an empty set here
     * looks identical to "genuinely never seen." [FeatureTourCoordinator.activeTour] gates on
     * [isLoaded] instead of trusting this being non-empty. */
    val seenTourKeys: StateFlow<Set<String>> = _seenTourKeys.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)

    /** True once the initial fetch has resolved (success *or* failure — same "don't strand the UI
     * blocked forever on a network hiccup" reasoning as `MessageRepository.isInitialSyncing`).
     * Nothing here blocks waiting for this; it only exists so [FeatureTourCoordinator.activeTour]
     * can tell "not seen yet, for real" apart from "haven't heard back from the server yet." */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    init {
        scope.launch { refresh() }
    }

    private suspend fun refresh() {
        val response = runCatching { consoleApiCall { usersApi.seenTours() } }.getOrNull()
        if (response != null) {
            _seenTourKeys.value = response.seenTourKeys.toSet()
        }
        _isLoaded.value = true
    }

    /** Optimistic local update first, so the same tour can't reappear later this session even if
     * the network call is still in flight or fails transiently — then a fire-and-forget POST,
     * same shape as `MessageRepository.sendOnlinePing()`. The server call is idempotent
     * (`feature_tour_seen_repo.mark_seen`), so a duplicate call from a second dismissal, or a
     * retry, is harmless. */
    fun markSeen(tourKey: String) {
        _seenTourKeys.value = _seenTourKeys.value + tourKey
        scope.launch { runCatching { consoleApiCall { usersApi.markTourSeen(tourKey) } } }
    }
}
