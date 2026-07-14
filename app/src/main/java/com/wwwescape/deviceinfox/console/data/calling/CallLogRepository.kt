package com.wwwescape.deviceinfox.console.data.calling

import com.wwwescape.deviceinfox.console.data.network.CallLogApi
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import com.wwwescape.deviceinfox.console.data.network.dto.CallLogOutDto
import com.wwwescape.deviceinfox.console.data.network.dto.CallLogStatusDto
import com.wwwescape.deviceinfox.console.data.network.dto.CallLogTypeDto
import com.wwwescape.deviceinfox.console.data.network.dto.isoDateTimeToEpochMillis
import javax.inject.Inject
import javax.inject.Singleton

/** Fetches the couple's call history (`GET /calls/log`, `app/api/v1/routers/calls.py`) — plain
 * request/response, no WebSocket push, since the log is only ever consulted by opening the Log
 * tab rather than needing to update live like [CallRoomRepository]'s room state does. [selfId] is
 * passed in rather than resolved here so this stays a stateless wrapper around the API, same split
 * [CallRoomViewModel] already uses for resolving self/partner. */
@Singleton
class CallLogRepository @Inject constructor(private val callLogApi: CallLogApi) {

    suspend fun fetchLog(selfId: String): List<CallLogEntry> {
        val page = consoleApiCall { callLogApi.getLog() }
        return page.items.map { it.toCallLogEntry(selfId) }
    }
}

private fun CallLogOutDto.toCallLogEntry(selfId: String): CallLogEntry = CallLogEntry(
    id = id,
    callerId = callerId,
    calleeId = calleeId,
    callType = when (callType) {
        CallLogTypeDto.VOICE -> CallType.VOICE
        CallLogTypeDto.VIDEO -> CallType.VIDEO
    },
    status = when (status) {
        CallLogStatusDto.ANSWERED -> CallLogStatus.ANSWERED
        CallLogStatusDto.MISSED -> CallLogStatus.MISSED
        CallLogStatusDto.DECLINED -> CallLogStatus.DECLINED
        CallLogStatusDto.CANCELLED -> CallLogStatus.CANCELLED
    },
    startedAtEpochMillis = startedAt.isoDateTimeToEpochMillis(),
    durationSeconds = durationSeconds,
    isOutgoing = callerId == selfId,
)
