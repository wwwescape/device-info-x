package com.wwwescape.deviceinfox.console.data.whatsnew

import com.wwwescape.deviceinfox.console.data.network.UsersApi
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
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
import kotlinx.coroutines.launch

/** Server-side "seen" state for [WHATS_NEW_ENTRIES] — same shape as
 * [com.wwwescape.deviceinfox.console.data.tour.FeatureTourRepository]'s `seenTourKeys`, per
 * account rather than per device, surviving an uninstall/reinstall. Unlike the tour system, the
 * entries themselves are a static client-side list rather than something targets register at
 * runtime, so [unseenEntries] can compute the diff directly instead of needing a separate
 * candidate-registration step. */
@Singleton
class WhatsNewRepository @Inject constructor(
    private val usersApi: UsersApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _seenTags = MutableStateFlow<Set<String>>(emptySet())

    private val _isLoaded = MutableStateFlow(false)

    /** Gated on [_isLoaded] for the same reason
     * [com.wwwescape.deviceinfox.console.data.tour.FeatureTourCoordinator.activeTour] gates on
     * [com.wwwescape.deviceinfox.console.data.tour.FeatureTourRepository.isLoaded] — an empty
     * [_seenTags] reads identically whether nothing's been seen yet or the initial fetch just
     * hasn't resolved, and trusting it as-is would flash already-dismissed entries in the popup
     * for the instant between construction and that fetch landing. */
    val unseenEntries: StateFlow<List<WhatsNewEntry>> = combine(_seenTags, _isLoaded) { seen, loaded ->
        if (!loaded) emptyList() else WHATS_NEW_ENTRIES.filter { it.tag !in seen }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch { refresh() }
    }

    private suspend fun refresh() {
        val response = runCatching { consoleApiCall { usersApi.seenWhatsNew() } }.getOrNull()
        if (response != null) {
            _seenTags.value = response.seenTags.toSet()
        }
        _isLoaded.value = true
    }

    /** Optimistic local update first, same as
     * [com.wwwescape.deviceinfox.console.data.tour.FeatureTourRepository.markSeen] — so a
     * dismissed entry can't reappear later this session even if the network call is still in
     * flight or fails transiently. One fire-and-forget POST per tag (no bulk endpoint — mirrors
     * `seen-tours`' one-key-at-a-time shape exactly rather than inventing a batch variant); each
     * is independently idempotent, so a partial failure just means that one tag re-shows next
     * time, not a corrupted state. */
    fun dismiss(tags: List<String>) {
        _seenTags.value = _seenTags.value + tags
        tags.forEach { tag ->
            scope.launch { runCatching { consoleApiCall { usersApi.markWhatsNewSeen(tag) } } }
        }
    }
}
