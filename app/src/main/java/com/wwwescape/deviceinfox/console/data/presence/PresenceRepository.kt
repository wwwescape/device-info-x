package com.wwwescape.deviceinfox.console.data.presence

import kotlinx.coroutines.flow.Flow

/**
 * Partner online/last-seen/typing state — live over the WebSocket as of Phase 11.5
 * ([ServerPresenceRepository]).
 */
interface PresenceRepository {
    val partnerPresence: Flow<PartnerPresence>

    /** True only until the partner's real status is actually known (the server's one-off
     * snapshot, or any live delta, whichever lands first) — see the implementation's own doc
     * comment for why [partnerPresence] alone can't distinguish "not resolved yet" from
     * "resolved to nothing." Mirrors `MessageRepository.isInitialSyncing`'s exact shape/reasoning,
     * one level down: that gates a full-screen "Loading…", this backs a much smaller one under
     * the partner's name (`HomeScreen.presenceSubtitle`). */
    val isInitialPresenceLoading: Flow<Boolean>

    /** This device's own "am I on the Messages screen right now" state — distinct from
     * [partnerPresence] (which describes the *other* device). Used to suppress a redundant
     * notification for a message that just arrived live over the WebSocket while its conversation
     * is already on screen (see `MessageRepository.handleWsEvent`), the same "don't notify what's
     * already visible" rule [reportSelfInMessagesScreen] exists to tell the *partner's* device
     * about via the "Here" presence tier — this is the local, same-device equivalent. */
    val isSelfInMessagesScreen: Flow<Boolean>

    suspend fun reportSelfTyping(isTyping: Boolean)

    /** Called by `HomeScreen` on enter/leave (a `DisposableEffect`, since `HomeViewModel` itself
     * survives tab switches and can't be used as the "did I just enter/leave" signal). */
    suspend fun reportSelfInMessagesScreen(inMessagesScreen: Boolean)
}
