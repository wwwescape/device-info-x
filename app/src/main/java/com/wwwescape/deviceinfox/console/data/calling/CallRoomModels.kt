package com.wwwescape.deviceinfox.console.data.calling

enum class CallType { VOICE, VIDEO }

enum class CallStatus { IDLE, RINGING, ACTIVE }

/** Mirrors the server's `call.state` payload (`app/services/call_room_service.py`) exactly — a
 * full snapshot every time, never a delta, same reasoning
 * [com.wwwescape.deviceinfox.console.data.game.GameRoomState] already documents. */
data class CallRoomState(
    val present: Map<String, Boolean> = emptyMap(),
    val status: CallStatus = CallStatus.IDLE,
    val callType: CallType? = null,
    val callerId: String? = null,
    val calleeId: String? = null,
    val startedAtEpochMillis: Long? = null,
    val muted: Map<String, Boolean> = emptyMap(),
    val cameraOn: Map<String, Boolean> = emptyMap(),
)

/** A raw `call.ended` one-shot event, resolved from the server's `{reason, by}` shape — see
 * [CallRoomRepository.callEndedEvent]'s own doc comment for why this stays separate from
 * [CallRoomState]. Kept close to the wire shape here; resolving "does this concern me" (e.g. a
 * caller shouldn't see their own cancel echoed back as a banner) is
 * [com.wwwescape.deviceinfox.console.ui.calling.CallRoomViewModel]'s job, same layering
 * [com.wwwescape.deviceinfox.console.ui.game.GameRoomViewModel] already uses for
 * [com.wwwescape.deviceinfox.console.data.game.GameResult]. */
sealed interface CallEndReason {
    data class Cancelled(val byUserId: String) : CallEndReason
    data class Declined(val byUserId: String) : CallEndReason
    data class NoResponse(val byUserId: String?) : CallEndReason
    data class Ended(val byUserId: String) : CallEndReason
    data class Left(val byUserId: String) : CallEndReason
    data class Interrupted(val byUserId: String?) : CallEndReason
}

/** Raw WebRTC signaling relayed over the same WS connection everything else here uses — the
 * server never inspects any of this (`call_room_service.relay_signal`), it's pure payload. Kept
 * as its own event stream on [CallRoomRepository], separate from [CallRoomState]/[CallEndReason],
 * since `CallMediaEngine`/the peer connection (Phase 2) is the only consumer — folding SDP/ICE
 * strings into the room snapshot would mean every other observer of [CallRoomRepository.roomState]
 * re-evaluates on every ICE candidate for no reason. */
sealed interface CallSignalingEvent {
    data class Offer(val sdp: String) : CallSignalingEvent
    data class Answer(val sdp: String) : CallSignalingEvent
    data class RemoteIceCandidate(val sdpMid: String?, val sdpMLineIndex: Int, val candidate: String) : CallSignalingEvent
}

enum class CallLogStatus { ANSWERED, MISSED, DECLINED, CANCELLED }

/** One resolved call, as shown in the Log tab — mirrors `app/schemas/call_log.py`'s `CallLogOut`.
 * [isOutgoing] is resolved client-side ([CallLogRepository]) by comparing [callerId] against this
 * device's own id, same "resolve self/partner here, not in the DTO layer" split every other
 * calling model in this file already follows. */
data class CallLogEntry(
    val id: String,
    val callerId: String,
    val calleeId: String,
    val callType: CallType,
    val status: CallLogStatus,
    val startedAtEpochMillis: Long,
    val durationSeconds: Int?,
    val isOutgoing: Boolean,
)
