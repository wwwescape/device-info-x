package com.wwwescape.deviceinfox.console.data.calling

import com.wwwescape.deviceinfox.console.data.network.ConsoleWebSocketClient
import com.wwwescape.deviceinfox.console.data.network.WsEvent
import com.wwwescape.deviceinfox.console.data.network.dto.isoDateTimeToEpochMillis
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** WebSocket-backed state for the shared Call Room (Messages header's call icon) — mirrors
 * [com.wwwescape.deviceinfox.console.data.game.GameRoomRepository]'s shape exactly: a `@Singleton`
 * holding the room's live state, re-announcing this device's own screen presence on every
 * reconnect, no REST fallback (the room only exists at all while at least one partner is actively
 * looking at it or a call is ringing/active — see `call_room_service.py`'s own module doc
 * comment).
 *
 * `startCall`/`acceptCall`/etc. are fire-and-forget sends, same as
 * [com.wwwescape.deviceinfox.console.data.game.GameRoomRepository.makeMove] — the server silently
 * validates and no-ops anything invalid (a race, a stale tap after the state already moved on)
 * rather than surfacing an error, so there's nothing for the client to await or handle failure
 * for. */
@Singleton
class CallRoomRepository @Inject constructor(
    private val webSocketClient: ConsoleWebSocketClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _roomState = MutableStateFlow(CallRoomState())
    val roomState: StateFlow<CallRoomState> = _roomState.asStateFlow()

    /** One-shot — fired once per `call.ended` broadcast, never folded into [roomState] itself, so
     * a later recomposition/reconnect can't re-trigger a stale status line from a StateFlow's
     * still-current value. Same reasoning
     * [com.wwwescape.deviceinfox.console.data.game.GameRoomRepository.roundResultEvent] documents. */
    private val _callEndedEvent = MutableSharedFlow<CallEndReason>(extraBufferCapacity = 1)
    val callEndedEvent: SharedFlow<CallEndReason> = _callEndedEvent.asSharedFlow()

    /** Raw WebRTC signaling from the partner — see [CallSignalingEvent]'s own doc comment for why
     * this is a separate stream from [roomState]. Buffered a little deeper than the other
     * one-shot flows here, since ICE candidates can arrive in a quick burst. */
    private val _signalingEvent = MutableSharedFlow<CallSignalingEvent>(extraBufferCapacity = 16)
    val signalingEvent: SharedFlow<CallSignalingEvent> = _signalingEvent.asSharedFlow()

    /** This device's own "am I on the Call Room screen" state, mirrored locally so it can be
     * re-sent on every reconnect — same reasoning
     * [com.wwwescape.deviceinfox.console.data.game.GameRoomRepository]'s own `_selfInRoom` doc
     * comment gives: without this, entering the room right as the socket is still finishing its
     * handshake would silently drop the enter message. */
    private val _selfInRoom = MutableStateFlow(false)

    init {
        scope.launch {
            webSocketClient.events.collect { event -> runCatching { handleEvent(event) } }
        }
        scope.launch {
            webSocketClient.connected.collect {
                if (_selfInRoom.value) webSocketClient.send(type = "screen.call_room_enter")
            }
        }
    }

    private fun handleEvent(event: WsEvent) {
        when (event.type) {
            "call.state" -> _roomState.value = event.data.toCallRoomState()
            "call.ended" -> event.data.toCallEndReasonOrNull()?.let { _callEndedEvent.tryEmit(it) }
            "call.offer" -> event.data["sdp"]?.jsonPrimitive?.contentOrNull?.let {
                _signalingEvent.tryEmit(CallSignalingEvent.Offer(it))
            }
            "call.answer" -> event.data["sdp"]?.jsonPrimitive?.contentOrNull?.let {
                _signalingEvent.tryEmit(CallSignalingEvent.Answer(it))
            }
            "call.ice_candidate" -> event.data.toRemoteIceCandidateOrNull()?.let { _signalingEvent.tryEmit(it) }
        }
    }

    fun enterRoom() {
        _selfInRoom.value = true
        webSocketClient.send(type = "screen.call_room_enter")
    }

    /** Also clears local state immediately, rather than waiting for the server's own reset to
     * round-trip back — mirrors [com.wwwescape.deviceinfox.console.data.game.GameRoomRepository.leaveRoom]'s
     * identical reasoning. Note this does *not* mean an in-flight call is wiped without a trace:
     * the server's own `leave_room` already resolved it (broadcasting `call.ended` + the now-idle
     * `call.state`) before this ever runs, since leaving the screen is what triggered the
     * resolution in the first place. */
    fun leaveRoom() {
        _selfInRoom.value = false
        webSocketClient.send(type = "screen.call_room_leave")
        _roomState.value = CallRoomState()
    }

    fun startCall(callType: CallType) {
        webSocketClient.send(
            type = "call.start",
            data = buildJsonObject { put("type", if (callType == CallType.VOICE) "voice" else "video") },
        )
    }

    fun acceptCall() = webSocketClient.send(type = "call.accept")
    fun declineCall() = webSocketClient.send(type = "call.decline")
    fun cancelCall() = webSocketClient.send(type = "call.cancel")
    fun endCall() = webSocketClient.send(type = "call.end")

    /** Client-detected real GSM call — see `call_room_service.interrupt_call`'s own doc comment
     * for why this is a distinct action from [cancelCall]/[declineCall]/[endCall] rather than
     * just calling whichever of those would otherwise apply. */
    fun interruptCall() = webSocketClient.send(type = "call.interrupt")

    fun sendOffer(sdp: String) = webSocketClient.send(type = "call.offer", data = buildJsonObject { put("sdp", sdp) })
    fun sendAnswer(sdp: String) = webSocketClient.send(type = "call.answer", data = buildJsonObject { put("sdp", sdp) })

    fun sendIceCandidate(sdpMid: String?, sdpMLineIndex: Int, candidate: String) {
        webSocketClient.send(
            type = "call.ice_candidate",
            data = buildJsonObject {
                put("sdp_mid", sdpMid)
                put("sdp_m_line_index", sdpMLineIndex)
                put("candidate", candidate)
            },
        )
    }

    fun sendControl(muted: Boolean? = null, cameraOn: Boolean? = null) {
        webSocketClient.send(
            type = "call.control",
            data = buildJsonObject {
                muted?.let { put("muted", it) }
                cameraOn?.let { put("camera_on", it) }
            },
        )
    }
}

private fun JsonObject.toCallRoomState(): CallRoomState {
    val present = this["present"]?.jsonObject.orEmpty().mapValues { (_, v) -> v.jsonPrimitive.boolean }
    val status = when (this["status"]?.jsonPrimitive?.contentOrNull) {
        "ringing" -> CallStatus.RINGING
        "active" -> CallStatus.ACTIVE
        else -> CallStatus.IDLE
    }
    val callType = when (this["call_type"]?.jsonPrimitive?.contentOrNull) {
        "voice" -> CallType.VOICE
        "video" -> CallType.VIDEO
        else -> null
    }
    val muted = this["muted"]?.jsonObject.orEmpty().mapValues { (_, v) -> v.jsonPrimitive.boolean }
    val cameraOn = this["camera_on"]?.jsonObject.orEmpty().mapValues { (_, v) -> v.jsonPrimitive.boolean }
    return CallRoomState(
        present = present,
        status = status,
        callType = callType,
        callerId = this["caller_id"]?.jsonPrimitive?.contentOrNull,
        calleeId = this["callee_id"]?.jsonPrimitive?.contentOrNull,
        startedAtEpochMillis = this["started_at"]?.jsonPrimitive?.contentOrNull?.isoDateTimeToEpochMillis(),
        muted = muted,
        cameraOn = cameraOn,
    )
}

private fun JsonObject.toCallEndReasonOrNull(): CallEndReason? {
    val reason = this["reason"]?.jsonPrimitive?.contentOrNull ?: return null
    val by = this["by"]?.jsonPrimitive?.contentOrNull
    return when (reason) {
        "cancelled" -> by?.let { CallEndReason.Cancelled(it) }
        "declined" -> by?.let { CallEndReason.Declined(it) }
        "no_response" -> CallEndReason.NoResponse(by)
        "ended" -> by?.let { CallEndReason.Ended(it) }
        "left" -> by?.let { CallEndReason.Left(it) }
        "interrupted" -> CallEndReason.Interrupted(by)
        else -> null
    }
}

private fun JsonObject.toRemoteIceCandidateOrNull(): CallSignalingEvent.RemoteIceCandidate? {
    val candidate = this["candidate"]?.jsonPrimitive?.contentOrNull ?: return null
    val sdpMLineIndex = this["sdp_m_line_index"]?.jsonPrimitive?.int ?: return null
    val sdpMid = this["sdp_mid"]?.jsonPrimitive?.contentOrNull
    return CallSignalingEvent.RemoteIceCandidate(sdpMid = sdpMid, sdpMLineIndex = sdpMLineIndex, candidate = candidate)
}

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
