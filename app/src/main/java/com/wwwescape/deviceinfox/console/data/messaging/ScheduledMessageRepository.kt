package com.wwwescape.deviceinfox.console.data.messaging

import com.wwwescape.deviceinfox.console.data.db.ScheduledMessageDao
import com.wwwescape.deviceinfox.console.data.db.ScheduledMessageEntity
import com.wwwescape.deviceinfox.console.data.network.ConsoleWebSocketClient
import com.wwwescape.deviceinfox.console.data.network.ScheduledMessagesApi
import com.wwwescape.deviceinfox.console.data.network.WsEvent
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import com.wwwescape.deviceinfox.console.data.network.dto.ScheduledMessageCreateDto
import com.wwwescape.deviceinfox.console.data.network.dto.ScheduledMessageOutDto
import com.wwwescape.deviceinfox.console.data.network.dto.isoDateTimeToEpochMillis
import com.wwwescape.deviceinfox.console.data.network.dto.toIsoDateTimeString
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Scheduled Messages — a staged text message held server-side until its `scheduled_at`, then
 * delivered as a real [com.wwwescape.deviceinfox.console.data.messaging.MessageRepository]
 * message by the server's own 60s sweep (or sooner, via "Send Now"). Always this device's own
 * account — the server's own `GET /messages/scheduled` only ever returns the caller's own rows
 * (`scheduled_message_service.list_scheduled` scopes by `sender_id == current_user.id`, no
 * partner fallback, matching Notepad's `PRIVATE` entries), so this device never has a partner's
 * pending scheduled message to accidentally cache or leak — a scheduled "surprise" message stays
 * a surprise until it actually arrives as a real message.
 *
 * Create/cancel/send-now all push over the WebSocket (`scheduled_message_service.py`'s `_push`,
 * sender's own other devices only) — deltas are applied directly, same as
 * `NotepadRepository`/`VaultRepository`. [refresh] still runs once eagerly on construction as a
 * catch-up fetch, since WS delivery only reaches a live connection.
 */
@Singleton
class ScheduledMessageRepository @Inject constructor(
    private val scheduledMessageDao: ScheduledMessageDao,
    private val scheduledMessagesApi: ScheduledMessagesApi,
    private val webSocketClient: ConsoleWebSocketClient,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch { runCatching { refresh() } }
        scope.launch {
            webSocketClient.events.collect { event -> runCatching { handleWsEvent(event) } }
        }
    }

    val scheduledMessages: Flow<List<ScheduledMessage>> =
        scheduledMessageDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /** Full sync: fetches everything visible, applies each row the same way a WS delta would,
     * then prunes any local row no longer present remotely — covers a delete/send that happened
     * while this device's console was closed, which WS alone can't (it only reaches a live
     * connection). */
    suspend fun refresh() {
        val remote = consoleApiCall { scheduledMessagesApi.list() }
        remote.forEach { applyRemote(it) }
        val remoteIds = remote.map { it.id }.toSet()
        scheduledMessageDao.getAllSnapshot().filter { it.id !in remoteIds }.forEach { scheduledMessageDao.delete(it.id) }
    }

    suspend fun create(body: String, scheduledAtEpochMillis: Long): ScheduledMessage {
        val dto = consoleApiCall {
            scheduledMessagesApi.create(ScheduledMessageCreateDto(body, scheduledAtEpochMillis.toIsoDateTimeString()))
        }
        applyRemote(dto)
        return dto.toDomain()
    }

    suspend fun cancel(id: String) {
        consoleApiCall { scheduledMessagesApi.cancel(id) }
        scheduledMessageDao.delete(id)
    }

    /** Fires the message immediately rather than waiting for the server's sweep to find it
     * naturally due — the row disappears from this list the same way a cancel does, just because
     * it was sent, not discarded. */
    suspend fun sendNow(id: String) {
        consoleApiCall { scheduledMessagesApi.sendNow(id) }
        scheduledMessageDao.delete(id)
    }

    private suspend fun handleWsEvent(event: WsEvent) {
        when (event.type) {
            "scheduled_message.created" -> {
                val dto = event.data["scheduled_message"]?.let { json.decodeFromJsonElement<ScheduledMessageOutDto>(it) } ?: return
                applyRemote(dto)
            }
            "scheduled_message.deleted", "scheduled_message.sent" -> {
                val id = event.data.stringField("scheduled_message_id") ?: return
                scheduledMessageDao.delete(id)
            }
        }
    }

    private fun JsonObject.stringField(key: String): String? = this[key]?.jsonPrimitive?.content

    private suspend fun applyRemote(dto: ScheduledMessageOutDto) {
        scheduledMessageDao.upsert(dto.toEntity())
    }

    private fun ScheduledMessageOutDto.toEntity() = ScheduledMessageEntity(
        id = id,
        body = body,
        scheduledAtEpochMillis = scheduledAt.isoDateTimeToEpochMillis() ?: 0L,
        createdAtEpochMillis = createdAt.isoDateTimeToEpochMillis() ?: 0L,
    )

    private fun ScheduledMessageOutDto.toDomain() = ScheduledMessage(
        id = id,
        body = body,
        scheduledAtEpochMillis = scheduledAt.isoDateTimeToEpochMillis() ?: 0L,
        createdAtEpochMillis = createdAt.isoDateTimeToEpochMillis() ?: 0L,
    )

    private fun ScheduledMessageEntity.toDomain() = ScheduledMessage(
        id = id,
        body = body,
        scheduledAtEpochMillis = scheduledAtEpochMillis,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}
