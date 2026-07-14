package com.wwwescape.deviceinfox.console.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors `app/models/call_log.py`'s `CallLogType`. */
@Serializable
enum class CallLogTypeDto {
    @SerialName("voice") VOICE,
    @SerialName("video") VIDEO,
}

/** Mirrors `app/models/call_log.py`'s `CallLogStatus`. */
@Serializable
enum class CallLogStatusDto {
    @SerialName("answered") ANSWERED,
    @SerialName("missed") MISSED,
    @SerialName("declined") DECLINED,
    @SerialName("cancelled") CANCELLED,
}

/** Mirrors `app/schemas/call_log.py`'s `CallLogOut` — one resolved call attempt, metadata only
 * (who/when/type/status/duration), never content. See `call_room_service.py`'s module doc comment
 * for why this exists despite `CALLING_PLAN.md`'s original "no history, ever" non-goal. */
@Serializable
data class CallLogOutDto(
    val id: String,
    @SerialName("caller_id") val callerId: String,
    @SerialName("callee_id") val calleeId: String,
    @SerialName("call_type") val callType: CallLogTypeDto,
    val status: CallLogStatusDto,
    @SerialName("started_at") val startedAt: String,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
)

@Serializable
data class CallLogPageDto(
    val items: List<CallLogOutDto>,
)
