package com.wwwescape.deviceinfox.console.data.presence

import com.wwwescape.deviceinfox.console.data.network.ConsoleWebSocketClient
import com.wwwescape.deviceinfox.console.data.network.WsEvent
import com.wwwescape.deviceinfox.console.data.network.dto.isoDateTimeToEpochMillis
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Real, WebSocket-backed [PresenceRepository] — replaces [MockPresenceRepository]'s Hilt binding
 * (see `PresenceModule`). Mirrors `ws/router.py`'s `presence` (online/offline + last-seen),
 * `presence.screen` (the "Here" tier — this device's own Messages screen enter/leave, forwarded
 * to the partner), and `typing` (start/stop) events exactly.
 *
 * There's no REST fallback for "current" presence — the server only ever *pushes* it — but unlike
 * before this needing a fix, a fresh connection isn't left guessing: `presence_service
 * .send_snapshot` sends this device a one-off `presence`/`presence.screen` pair describing the
 * partner's state right after this device's own connect, over the exact same `handleEvent` path
 * as a live delta. Until that snapshot (or the first real delta) arrives, [partnerPresence]
 * reports offline/not-here/not-typing.
 */
@Singleton
class ServerPresenceRepository @Inject constructor(
    private val webSocketClient: ConsoleWebSocketClient,
) : PresenceRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _partnerPresence = MutableStateFlow(
        PartnerPresence(isOnline = false, lastSeenEpochMillis = null, isInMessagesScreen = false, isTyping = false),
    )
    override val partnerPresence: Flow<PartnerPresence> = _partnerPresence.asStateFlow()

    /** Flips to `false` for good the moment the first real `"presence"` event is handled (the
     * snapshot or a live delta, whichever comes first — see [handleEvent]) — never flips back to
     * `true` afterward, same one-shot-per-session shape as `MessageRepository.isInitialSyncing`. */
    private val _isInitialPresenceLoading = MutableStateFlow(true)
    override val isInitialPresenceLoading: Flow<Boolean> = _isInitialPresenceLoading.asStateFlow()

    /** This device's own "am I on the Messages screen" state, mirrored locally so it can be
     * re-sent on every reconnect (see the `connected` collector below) — without that, a
     * `reportSelfInMessagesScreen(true)` call that raced ahead of the socket actually finishing
     * its handshake (very plausible: `HomeScreen` is often the very first screen shown, right as
     * the connection is still being established) would silently drop, leaving the partner seeing
     * "Online" instead of "Here" for the entire visit rather than just a brief startup gap. */
    private val _selfInMessagesScreen = MutableStateFlow(false)
    override val isSelfInMessagesScreen: Flow<Boolean> = _selfInMessagesScreen.asStateFlow()

    init {
        scope.launch {
            webSocketClient.events.collect { event -> runCatching { handleEvent(event) } }
        }
        scope.launch {
            webSocketClient.connected.collect {
                if (_selfInMessagesScreen.value) webSocketClient.send(type = "screen.messages_enter")
            }
        }
    }

    private fun handleEvent(event: WsEvent) {
        when (event.type) {
            // Both a real online/offline transition (mark_online/mark_offline, fired off this
            // device's own connect/disconnect) *and* the one-off snapshot the server sends right
            // after this device connects (presence_service.send_snapshot) use this same event
            // shape — see that function's doc comment for why the snapshot needs to exist at all
            // (without it, presence only ever updates from a delta that requires both devices to
            // be connected at the same instant, which in practice could go a very long time
            // without happening).
            "presence" -> {
                val status = event.data["status"]?.jsonPrimitive?.content ?: return
                val lastSeenAt = event.data["last_seen_at"]?.jsonPrimitive?.contentOrNull
                val isOnline = status == "online"
                _partnerPresence.value = _partnerPresence.value.copy(
                    isOnline = isOnline,
                    lastSeenEpochMillis = lastSeenAt?.isoDateTimeToEpochMillis(),
                    // Enforces PartnerPresence's own documented invariant (isInMessagesScreen implies
                    // isOnline) at the one place it could otherwise be violated: the server does also
                    // push an explicit presence.screen(false) alongside this same offline transition
                    // (presence_service.mark_offline), but that's a second, independently-delivered WS
                    // message — if it's ever lost while this one isn't, "Here" would otherwise be
                    // stuck stale for the rest of the session instead of falling back to Last seen.
                    isInMessagesScreen = _partnerPresence.value.isInMessagesScreen && isOnline,
                    // Same reasoning, same fix shape, for the typing indicator — the server also
                    // pushes an explicit typing/stop alongside this offline transition
                    // (presence_service.mark_offline), but going offline should always mean "not
                    // typing" regardless of whether that second, independent message actually lands.
                    // Reported bug this fixes: partner backgrounds the app mid-draft → status flips
                    // to "Last seen" correctly, but the typing bubble stayed stuck until this
                    // device's own app was force-closed and reopened (a fresh ServerPresenceRepository
                    // resetting isTyping to its initial false, not anything self-healing).
                    isTyping = _partnerPresence.value.isTyping && isOnline,
                )
                _isInitialPresenceLoading.value = false
            }
            "presence.screen" -> {
                val inMessages = event.data["in_messages"]?.jsonPrimitive?.boolean ?: return
                _partnerPresence.value = _partnerPresence.value.copy(isInMessagesScreen = inMessages)
            }
            "typing" -> {
                val state = event.data["state"]?.jsonPrimitive?.contentOrNull ?: return
                _partnerPresence.value = _partnerPresence.value.copy(isTyping = state == "start")
            }
        }
    }

    /** Client→server and server→client use different envelope shapes for typing, per
     * `ws/router.py`: incoming client messages are matched by literal `type` —
     * `"typing.start"`/`"typing.stop"`, no `data` needed (`_handle_client_message`'s
     * `elif msg_type == "typing.start"` / `"typing.stop"`) — while the server's own outgoing
     * broadcast to the partner is `type: "typing"` with `data: {state: ...}` (`_forward_typing`),
     * which is what [handleEvent] above parses. A previous edit here incorrectly unified both
     * directions to the broadcast shape, so nothing this device sent ever matched a branch
     * server-side — the WS frame arrived and was silently dropped, no log line, no forwarding,
     * partner's indicator never lit up. */
    override suspend fun reportSelfTyping(isTyping: Boolean) {
        webSocketClient.send(type = if (isTyping) "typing.start" else "typing.stop")
    }

    override suspend fun reportSelfInMessagesScreen(inMessagesScreen: Boolean) {
        _selfInMessagesScreen.value = inMessagesScreen
        webSocketClient.send(type = if (inMessagesScreen) "screen.messages_enter" else "screen.messages_leave")
    }
}
