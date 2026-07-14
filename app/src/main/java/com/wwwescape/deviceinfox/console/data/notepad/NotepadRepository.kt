package com.wwwescape.deviceinfox.console.data.notepad

import com.wwwescape.deviceinfox.console.data.db.NotepadEntryDao
import com.wwwescape.deviceinfox.console.data.db.NotepadEntryEntity
import com.wwwescape.deviceinfox.console.data.db.NotepadEntryType
import com.wwwescape.deviceinfox.console.data.network.ConsoleWebSocketClient
import com.wwwescape.deviceinfox.console.data.network.NotepadApi
import com.wwwescape.deviceinfox.console.data.network.WsEvent
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import com.wwwescape.deviceinfox.console.data.network.dto.NotepadEntryCreateDto
import com.wwwescape.deviceinfox.console.data.network.dto.NotepadEntryOutDto
import com.wwwescape.deviceinfox.console.data.network.dto.NotepadEntryTypeDto
import com.wwwescape.deviceinfox.console.data.network.dto.NotepadEntryUpdateDto
import com.wwwescape.deviceinfox.console.data.network.dto.isoDateTimeToEpochMillis
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
 * Real, server-backed Notepad — `Room is what the UI sees` still holds: [privateEntries]/
 * [sharedEntries] are the same Room-backed `Flow`s `NotepadViewModel` observes.
 *
 * `PRIVATE` is genuinely private, not merely owner-edits-only like Safe Locker: the server's own
 * `GET /notepad/private` only ever returns the caller's own entries (`notepad_service.list_private`
 * scopes by `owner_id == current_user.id`, no partner fallback), and `notepad_service._get_authorized`
 * 404s a `PRIVATE` entry for anyone but its owner — so this device never has the partner's private
 * entries to accidentally cache or leak, by construction, not by a client-side filter.
 *
 * Both create/update/delete push over the WebSocket (`notepad_service.py` calls
 * `notification_service.notify_user` on every mutation, scoped per-type — see that service's own
 * `_push` doc comment) — deltas are applied directly, same as `VaultRepository`/`MessageRepository`.
 * [refresh] still runs once eagerly on construction as a catch-up fetch, since WS delivery only
 * reaches a live connection.
 */
@Singleton
class NotepadRepository @Inject constructor(
    private val notepadEntryDao: NotepadEntryDao,
    private val notepadApi: NotepadApi,
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

    val privateEntries: Flow<List<NotepadEntry>> =
        notepadEntryDao.observeByType(NotepadEntryType.PRIVATE).map { entities -> entities.map { it.toDomain() } }
    val sharedEntries: Flow<List<NotepadEntry>> =
        notepadEntryDao.observeByType(NotepadEntryType.SHARED).map { entities -> entities.map { it.toDomain() } }

    suspend fun getById(id: String): NotepadEntry? = notepadEntryDao.getById(id)?.toDomain()

    fun observeById(id: String): Flow<NotepadEntry?> = notepadEntryDao.observeById(id).map { it?.toDomain() }

    /** Full sync, both scopes: fetches everything visible, applies each row the same way a WS
     * delta would, then prunes any local row no longer present remotely — covers a delete that
     * happened while this device's console was closed, which WS alone can't (it only reaches a
     * live connection). */
    suspend fun refresh() {
        syncType(NotepadEntryType.PRIVATE) { consoleApiCall { notepadApi.listPrivate() } }
        syncType(NotepadEntryType.SHARED) { consoleApiCall { notepadApi.listShared() } }
    }

    private suspend fun syncType(type: NotepadEntryType, fetch: suspend () -> List<NotepadEntryOutDto>) {
        val remote = fetch()
        remote.forEach { applyRemote(it) }
        val remoteIds = remote.map { it.id }.toSet()
        notepadEntryDao.getAllSnapshotByType(type).filter { it.id !in remoteIds }.forEach { notepadEntryDao.delete(it.id) }
    }

    suspend fun createEntry(type: NotepadEntryType): NotepadEntry {
        val dto = consoleApiCall { notepadApi.create(NotepadEntryCreateDto(type = type.toDto())) }
        applyRemote(dto)
        return dto.toDomain()
    }

    /** [expectedUpdatedAtRaw] must be exactly what this device last read for this entry (usually
     * `NotepadEntry.updatedAtRaw` from whenever the editor opened it) — see
     * `NotepadEntryEntity.updatedAtRaw`'s own doc comment. Throws `ConsoleApiException` with
     * `httpStatusCode == 409` if the entry has changed since; the caller (`NoteEditorViewModel`)
     * is expected to catch that specifically and surface a conflict prompt rather than a generic
     * error toast. */
    suspend fun saveEntry(id: String, title: String, body: String, expectedUpdatedAtRaw: String): NotepadEntry {
        val dto = consoleApiCall { notepadApi.update(id, NotepadEntryUpdateDto(title, body, expectedUpdatedAtRaw)) }
        applyRemote(dto)
        return dto.toDomain()
    }

    suspend fun deleteEntry(id: String) {
        consoleApiCall { notepadApi.delete(id) }
        notepadEntryDao.delete(id)
    }

    /** Stays within its own type — duplicating a shared note produces another shared note, not a
     * copy into this device's private list (matches the server's own `duplicate_entry`). */
    suspend fun duplicateEntry(id: String): NotepadEntry {
        val dto = consoleApiCall { notepadApi.duplicate(id) }
        applyRemote(dto)
        return dto.toDomain()
    }

    private suspend fun handleWsEvent(event: WsEvent) {
        when (event.type) {
            "notepad.entry.created", "notepad.entry.updated" -> {
                val dto = event.data["entry"]?.let { json.decodeFromJsonElement<NotepadEntryOutDto>(it) } ?: return
                applyRemote(dto)
            }
            "notepad.entry.deleted" -> {
                val id = event.data.stringField("entry_id") ?: return
                notepadEntryDao.delete(id)
            }
        }
    }

    private fun JsonObject.stringField(key: String): String? = this[key]?.jsonPrimitive?.content

    private suspend fun applyRemote(dto: NotepadEntryOutDto) {
        notepadEntryDao.upsert(dto.toEntity())
    }

    private fun NotepadEntryTypeDto.toLocal() = when (this) {
        NotepadEntryTypeDto.PRIVATE -> NotepadEntryType.PRIVATE
        NotepadEntryTypeDto.SHARED -> NotepadEntryType.SHARED
    }

    private fun NotepadEntryType.toDto() = when (this) {
        NotepadEntryType.PRIVATE -> NotepadEntryTypeDto.PRIVATE
        NotepadEntryType.SHARED -> NotepadEntryTypeDto.SHARED
    }

    private fun NotepadEntryOutDto.toEntity() = NotepadEntryEntity(
        id = id,
        type = type.toLocal(),
        ownerId = ownerId,
        title = title,
        body = body,
        createdAtEpochMillis = createdAt.isoDateTimeToEpochMillis() ?: 0L,
        updatedAtEpochMillis = updatedAt.isoDateTimeToEpochMillis() ?: 0L,
        updatedAtRaw = updatedAt,
        updatedBy = updatedBy,
    )

    private fun NotepadEntryOutDto.toDomain() = NotepadEntry(
        id = id,
        type = type.toLocal(),
        title = title,
        body = body,
        createdAtEpochMillis = createdAt.isoDateTimeToEpochMillis() ?: 0L,
        updatedAtEpochMillis = updatedAt.isoDateTimeToEpochMillis() ?: 0L,
        updatedAtRaw = updatedAt,
        updatedBy = updatedBy,
    )

    private fun NotepadEntryEntity.toDomain() = NotepadEntry(
        id = id,
        type = type,
        title = title,
        body = body,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        updatedAtRaw = updatedAtRaw,
        updatedBy = updatedBy,
    )
}
